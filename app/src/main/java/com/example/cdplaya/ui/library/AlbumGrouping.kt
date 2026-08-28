package com.example.cdplaya.ui.library

import com.example.cdplaya.data.Song
import com.example.cdplaya.data.knownDiscNumber
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.data.resolveAlbumDiscNumbers
import com.example.cdplaya.ui.sortSongsByAlbumOrder
import java.util.Locale

data class LibraryAlbumGroup(
    val key: String,
    val title: String,
    val artistText: String,
    val songs: List<Song>
) {
    val folderPaths: Set<String>
        get() = songs.mapTo(linkedSetOf(), Song::folderPath)
}

data class LibraryAlbumDiscSection(
    val discNumber: Int?,
    val songs: List<Song>
)

/** Returns the exact songs owned by this resolved album group. */
internal fun LibraryAlbumGroup.metadataEditingSongs(): List<Song> =
    songs.distinctBy(Song::membershipKey)

internal fun LibraryAlbumGroup.containsSong(song: Song): Boolean {
    val membershipKey = song.membershipKey()
    return songs.any { candidate -> candidate.membershipKey() == membershipKey }
}

internal fun findLibraryAlbumGroupForSong(
    song: Song,
    albums: List<LibraryAlbumGroup>
): LibraryAlbumGroup? = albums.firstOrNull { album -> album.containsSong(song) }

internal fun isAlbumGroupAvailable(albumKey: String, songs: List<Song>): Boolean =
    buildLibraryAlbumGroups(songs).any { album -> album.key == albumKey }

fun buildLibraryAlbumGroups(
    songs: List<Song>
): List<LibraryAlbumGroup> {
    val folderGroups = songs
        .groupBy(Song::folderPath)
        .mapNotNull { (folderPath, folderSongs) ->
            buildFolderAlbumGroup(folderPath, folderSongs)
        }

    val mergeBuckets = folderGroups
        .mapNotNull { album ->
            multiDiscMergeIdentity(album)?.let { identity -> identity to album }
        }
        .groupBy(
            keySelector = { (identity, _) -> identity },
            valueTransform = { (_, album) -> album }
        )

    val mergedByFolderKey = mutableMapOf<String, LibraryAlbumGroup>()
    mergeBuckets.values.forEach { candidates ->
        val merged = mergeMultiDiscCandidates(candidates) ?: return@forEach
        candidates.forEach { candidate ->
            mergedByFolderKey[candidate.key] = merged
        }
    }

    val emittedKeys = mutableSetOf<String>()
    return buildList {
        folderGroups.forEach { folderGroup ->
            val resolved = mergedByFolderKey[folderGroup.key] ?: folderGroup
            if (emittedKeys.add(resolved.key)) {
                add(resolved)
            }
        }
    }
}

fun buildLibraryAlbumDiscSections(
    songs: List<Song>
): List<LibraryAlbumDiscSection> {
    val orderedSongs = sortSongsByAlbumOrder(songs)
    if (orderedSongs.isEmpty()) return emptyList()

    val resolvedDiscNumbers = resolveAlbumDiscNumbers(orderedSongs)
    val sections = linkedMapOf<Int?, MutableList<Song>>()
    orderedSongs.forEach { song ->
        val discNumber = resolvedDiscNumbers[song.membershipKey()]
        sections.getOrPut(discNumber) { mutableListOf() }.add(song)
    }

    return sections.map { (discNumber, sectionSongs) ->
        LibraryAlbumDiscSection(discNumber = discNumber, songs = sectionSongs)
    }
}

fun buildLibraryAlbumArtistText(
    albumSongs: List<Song>
): String {
    val albumArtistText = chooseMostRepresentativeArtist(
        albumSongs.map { song ->
            song.albumArtist
        }
    )

    if (albumArtistText != null) {
        return albumArtistText
    }

    return chooseMostRepresentativeArtist(
        albumSongs.map { song ->
            song.artist
        }
    ) ?: "Various Artists"
}

private fun buildFolderAlbumGroup(
    folderPath: String,
    songs: List<Song>
): LibraryAlbumGroup? {
    val albumSongs = sortSongsByAlbumOrder(songs)
    val firstSong = albumSongs.firstOrNull() ?: return null
    return LibraryAlbumGroup(
        key = folderPath,
        title = firstSong.album.ifBlank { "Unknown Album" },
        artistText = buildLibraryAlbumArtistText(albumSongs),
        songs = albumSongs
    )
}

private data class MultiDiscMergeIdentity(
    val parentPath: String,
    val albumTitle: String
)

private fun multiDiscMergeIdentity(album: LibraryAlbumGroup): MultiDiscMergeIdentity? {
    val normalizedTitle = album.title.normalizedAlbumIdentityText()
    if (normalizedTitle.isEmpty() || normalizedTitle == "unknown album") return null

    val normalizedFolder = album.key.normalizedAlbumPath()
    val parentPath = normalizedFolder.substringBeforeLast('/', missingDelimiterValue = "")
    if (parentPath.isBlank()) return null

    return MultiDiscMergeIdentity(
        parentPath = parentPath,
        albumTitle = normalizedTitle
    )
}

private fun mergeMultiDiscCandidates(
    candidates: List<LibraryAlbumGroup>
): LibraryAlbumGroup? {
    if (candidates.size < 2) return null

    val discNumbers = candidates.map { album -> folderDiscNumber(album) }
    if (discNumbers.any { it == null }) return null
    val knownDiscNumbers = discNumbers.filterNotNull()
    if (knownDiscNumbers.distinct().size != candidates.size) return null

    val discTotals = candidates
        .flatMap(LibraryAlbumGroup::songs)
        .mapNotNull(Song::discTotal)
        .filter { it > 0 }
        .distinct()
    if (discTotals.size > 1) return null
    discTotals.singleOrNull()?.let { total ->
        if (knownDiscNumbers.any { disc -> disc > total }) return null
    }

    val artistEvidence = candidates.mapNotNull(::albumArtistIdentityEvidence).distinct()
    if (artistEvidence.size > 1) return null

    val mergedSongs = sortSongsByAlbumOrder(candidates.flatMap(LibraryAlbumGroup::songs))
    val firstSong = mergedSongs.firstOrNull() ?: return null
    val folderPaths = candidates
        .map(LibraryAlbumGroup::key)
        .sortedBy(String::normalizedAlbumPath)

    return LibraryAlbumGroup(
        key = MULTI_DISC_ALBUM_KEY_PREFIX + folderPaths.joinToString(MULTI_DISC_KEY_SEPARATOR),
        title = firstSong.album.ifBlank { "Unknown Album" },
        artistText = buildLibraryAlbumArtistText(mergedSongs),
        songs = mergedSongs
    )
}

private fun folderDiscNumber(album: LibraryAlbumGroup): Int? = album.songs
    .mapNotNull(Song::knownDiscNumber)
    .distinct()
    .singleOrNull()

private fun albumArtistIdentityEvidence(album: LibraryAlbumGroup): String? =
    chooseMostRepresentativeArtist(album.songs.map(Song::albumArtist))
        ?.normalizedAlbumIdentityText()
        ?.takeIf { value -> value.isNotEmpty() }

private fun chooseMostRepresentativeArtist(
    artists: List<String>
): String? {
    val cleanedArtists = artists
        .map { artist ->
            artist.trim()
        }
        .filter { artist ->
            isUsableArtistText(artist)
        }

    if (cleanedArtists.isEmpty()) {
        return null
    }

    val exactArtists = cleanedArtists.distinctBy { artist ->
        artist.lowercase()
    }

    if (exactArtists.size == 1) {
        return exactArtists.first()
    }

    val primaryArtists = cleanedArtists
        .map { artist ->
            extractPrimaryArtist(artist)
        }
        .filter { artist ->
            isUsableArtistText(artist)
        }

    if (primaryArtists.isEmpty()) {
        return null
    }

    val artistGroups = primaryArtists.groupBy { artist ->
        artist.lowercase()
    }

    val largestArtistGroup = artistGroups.maxByOrNull { entry ->
        entry.value.size
    } ?: return null

    val requiredCount = if (primaryArtists.size <= 2) {
        2
    } else {
        primaryArtists.size / 2 + 1
    }

    return if (largestArtistGroup.value.size >= requiredCount) {
        largestArtistGroup.value.first()
    } else {
        null
    }
}

private fun extractPrimaryArtist(
    artist: String
): String {
    return artist
        .trim()
        .replace(featuredArtistPattern, "")
        .trim()
        .trimEnd(
            ' ',
            '-',
            '–',
            '—',
            ',',
            '(',
            '['
        )
        .trim()
}

private fun isUsableArtistText(
    artist: String
): Boolean {
    return artist.isNotBlank() &&
            !artist.equals("Unknown Artist", ignoreCase = true) &&
            !artist.equals("<unknown>", ignoreCase = true)
}

private fun String.normalizedAlbumIdentityText(): String = trim().lowercase(Locale.ROOT)

private fun String.normalizedAlbumPath(): String =
    trim().replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)

private val featuredArtistPattern = Regex(
    pattern = """\s+[\(\[]?\s*(feat\.?|ft\.?|featuring|with)\s+.*$""",
    option = RegexOption.IGNORE_CASE
)

private const val MULTI_DISC_ALBUM_KEY_PREFIX = "multi-disc:"
private const val MULTI_DISC_KEY_SEPARATOR = "\u001F"
