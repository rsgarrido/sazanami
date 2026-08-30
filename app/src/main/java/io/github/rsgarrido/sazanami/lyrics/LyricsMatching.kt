package io.github.rsgarrido.sazanami.lyrics

import java.text.Normalizer
import java.util.Locale

fun normalizeFileStem(fileName: String): String {
    val trimmed = fileName.trim()
    val finalSeparator = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    val name = trimmed.substring(finalSeparator + 1)
    val finalDot = name.lastIndexOf('.')
    val stem = if (finalDot > 0) name.substring(0, finalDot) else name
    return Normalizer.normalize(stem.trim(), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
}

private val leadingTrackNumber = Regex(
    """^\s*(?:(?:\d{1,2}[-.])?\d{1,3})(?:\s*[-._]\s*|\s+)(?=\S).+$"""
)
private val leadingTrackNumberValue = Regex(
    """^\s*(?:(?:\d{1,2}[-.])?\d{1,3})(?:\s*[-._]\s*|\s+)(\S.*)$"""
)
private val separatorVariant = Regex("""\s+[\u2013\u2014]\s+""")
private val unknownPeople = setOf(
    "unknown", "unknown artist", "<unknown>", "<unknown artist>", "[unknown]"
)

fun stripLeadingTrackNumber(stem: String): String {
    if (!leadingTrackNumber.matches(stem)) return stem.trim()
    return leadingTrackNumberValue.matchEntire(stem)?.groupValues?.get(1)?.trim()
        ?: stem.trim()
}

fun generateLyricsNameCandidates(song: SongLyricsIdentity): List<LyricsNameCandidate> {
    val audioStem = rawFileStem(song.audioFileName)
    val strippedStem = stripLeadingTrackNumber(audioStem)
    val title = song.title.trim()
    val artist = song.artist.meaningfulPerson()
    val albumArtist = song.albumArtist.meaningfulPerson()
        ?.takeUnless { it.equals(artist, ignoreCase = true) }
    val values = buildList {
        add(Triple(audioStem, LyricsNameCandidateSource.AUDIO_STEM, 0))
        add(Triple(title, LyricsNameCandidateSource.TITLE, 1))
        add(Triple(strippedStem, LyricsNameCandidateSource.TRACK_NUMBER_STRIPPED_AUDIO_STEM, 2))
        if (artist != null && title.isNotBlank()) {
            add(Triple("$artist - $title", LyricsNameCandidateSource.ARTIST_TITLE, 3))
            add(Triple("$title - $artist", LyricsNameCandidateSource.TITLE_ARTIST, 4))
        }
        if (albumArtist != null && title.isNotBlank()) {
            add(Triple("$albumArtist - $title", LyricsNameCandidateSource.ALBUM_ARTIST_TITLE, 5))
            add(Triple("$title - $albumArtist", LyricsNameCandidateSource.TITLE_ALBUM_ARTIST, 6))
        }
    }
    val seen = mutableSetOf<String>()
    return values.mapNotNull { (display, source, priority) ->
        val normalized = normalizeLyricsCandidateStem(display)
        if (display.isBlank() || normalized.isBlank() || !seen.add(normalized)) null
        else LyricsNameCandidate(display.trim(), normalized, source, priority)
    }
}

fun normalizeLyricsCandidateStem(value: String): String =
    Normalizer.normalize(stripLrcExtension(value).trim(), Normalizer.Form.NFC)
        .replace(separatorVariant, " - ")
        .lowercase(Locale.ROOT)

private fun stripLrcExtension(value: String): String {
    val trimmed = value.trim()
    val finalSeparator = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    val name = trimmed.substring(finalSeparator + 1)
    return if (name.endsWith(".lrc", ignoreCase = true)) name.dropLast(4) else name
}

private fun rawFileStem(fileName: String): String {
    val trimmed = fileName.trim()
    val finalSeparator = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    val name = trimmed.substring(finalSeparator + 1)
    val finalDot = name.lastIndexOf('.')
    return if (finalDot > 0) name.substring(0, finalDot).trim() else name.trim()
}

private fun String.meaningfulPerson(): String? = trim()
    .takeIf(String::isNotEmpty)
    ?.takeUnless { it.lowercase(Locale.ROOT) in unknownPeople }

fun hasLrcExtension(fileName: String): Boolean {
    val trimmed = fileName.trim()
    val finalSeparator = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    val name = trimmed.substring(finalSeparator + 1)
    val finalDot = name.lastIndexOf('.')
    return finalDot > 0 && name.substring(finalDot + 1).equals("lrc", ignoreCase = true)
}

fun normalizeLyricsPath(path: String?): String {
    if (path.isNullOrBlank()) return ""
    return Normalizer.normalize(path, Normalizer.Form.NFC)
        .replace('\\', '/')
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("/")
        .lowercase(Locale.ROOT)
}

fun directorySuffixMatchDepth(first: String, second: String): Int {
    val firstSegments = normalizeLyricsPath(first).pathSegments()
    val secondSegments = normalizeLyricsPath(second).pathSegments()
    if (firstSegments.isEmpty() || secondSegments.isEmpty()) return 0

    val shorterSize = minOf(firstSegments.size, secondSegments.size)
    var matched = 0
    while (
        matched < shorterSize &&
        firstSegments[firstSegments.lastIndex - matched] ==
        secondSegments[secondSegments.lastIndex - matched]
    ) {
        matched++
    }
    return if (matched == shorterSize) matched else 0
}

object LocalLyricsMatcher {
    fun match(
        song: SongLyricsIdentity,
        files: List<IndexedLyricsFile>
    ): LyricsMatchResult {
        val nameCandidates = generateLyricsNameCandidates(song)
        if (nameCandidates.isEmpty()) return LyricsMatchResult.NotFound
        val candidatesByStem = nameCandidates.associateBy(LyricsNameCandidate::normalizedStem)
        val candidates = files
            .asSequence()
            .mapNotNull { file ->
                candidatesByStem[normalizeLyricsCandidateStem(file.displayName)]
                    ?.let { candidate -> MatchedFile(file, candidate) }
            }
            .distinctBy { it.file.documentUri }
            .sortedWith { first, second ->
                indexedLyricsFileComparator.compare(first.file, second.file)
            }
            .toList()
        if (candidates.isEmpty()) return LyricsMatchResult.NotFound

        val scored = candidates.map { matched ->
            ScoredMatch(
                matched = matched,
                directoryScore = directoryScore(song, matched.file)
            )
        }
        val strongestDirectory = scored.maxOf(ScoredMatch::directoryScore)
        val directoryWinners = if (strongestDirectory > 0) {
            scored.filter { it.directoryScore == strongestDirectory }
        } else {
            scored
        }
        val strongestPriority = directoryWinners.minOf { it.matched.candidate.priority }
        val strongest = directoryWinners
            .filter { it.matched.candidate.priority == strongestPriority }
            .map { it.matched.file }

        if (strongestDirectory == 0 &&
            strongestPriority > LyricsNameCandidateSource.TITLE.ordinal &&
            strongest.size != 1
        ) {
            return LyricsMatchResult.Ambiguous(strongest)
        }
        return if (strongest.size == 1) {
            LyricsMatchResult.Match(strongest.single())
        } else {
            LyricsMatchResult.Ambiguous(strongest)
        }
    }

    private fun directoryScore(
        song: SongLyricsIdentity,
        file: IndexedLyricsFile
    ): Int {
        val songVolume = normalizeVolumeId(song.volumeId)
        val fileVolume = normalizeVolumeId(file.rootVolumeId)
        if (songVolume != null && fileVolume != null && songVolume != fileVolume) return 0

        val relativeDepth = directorySuffixMatchDepth(
            song.relativeDirectory,
            file.relativeDirectory
        )
        val fallbackDepth = directorySuffixMatchDepth(
            song.fallbackDirectory,
            file.relativeDirectory
        )
        val depth = maxOf(relativeDepth, fallbackDepth)
        if (depth == 0) return 0

        val exactBonus = if (
            normalizeLyricsPath(song.relativeDirectory) ==
            normalizeLyricsPath(file.relativeDirectory)
        ) {
            10_000
        } else {
            0
        }
        val volumeBonus = if (songVolume != null && songVolume == fileVolume) 1_000 else 0
        return exactBonus + volumeBonus + depth
    }

    private data class MatchedFile(
        val file: IndexedLyricsFile,
        val candidate: LyricsNameCandidate
    )

    private data class ScoredMatch(
        val matched: MatchedFile,
        val directoryScore: Int
    )
}

internal val indexedLyricsFileComparator =
    compareBy<IndexedLyricsFile>(
        { normalizeLyricsPath(it.rootUri) },
        { normalizeLyricsPath(it.relativeDirectory) },
        { it.normalizedStem },
        { it.documentUri }
    )

private fun String.pathSegments(): List<String> =
    takeIf(String::isNotEmpty)?.split('/') ?: emptyList()
