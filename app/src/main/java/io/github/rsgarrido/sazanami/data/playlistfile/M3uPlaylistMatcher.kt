package io.github.rsgarrido.sazanami.data.playlistfile

import io.github.rsgarrido.sazanami.data.Song
import java.net.URLDecoder
import java.util.Locale

data class M3uMatchResult(
    val matchedSongs: List<Song>,
    val unmatchedEntries: List<M3uEntry>
)

object M3uPlaylistMatcher {
    fun matchEntriesToLibrarySongs(
        entries: List<M3uEntry>,
        librarySongs: List<Song>
    ): M3uMatchResult {
        val normalizedSongs = librarySongs.map { song ->
            NormalizedLibrarySong(
                song = song,
                normalizedPath = normalizeLocation(song.filePath),
                normalizedFileName = normalizeFileName(song.filePath)
            )
        }

        val songsByExactPath = normalizedSongs
            .filter { normalizedSong ->
                normalizedSong.normalizedPath.isNotBlank()
            }
            .associateBy { normalizedSong ->
                normalizedSong.normalizedPath
            }

        val songsByFileName = normalizedSongs
            .filter { normalizedSong ->
                normalizedSong.normalizedFileName.isNotBlank()
            }
            .groupBy { normalizedSong ->
                normalizedSong.normalizedFileName
            }

        val matchedSongs = mutableListOf<Song>()
        val unmatchedEntries = mutableListOf<M3uEntry>()

        entries.forEach { entry ->
            val matchedSong = findMatchingSong(
                entry = entry,
                normalizedSongs = normalizedSongs,
                songsByExactPath = songsByExactPath,
                songsByFileName = songsByFileName
            )

            if (matchedSong == null) {
                unmatchedEntries.add(entry)
            } else {
                matchedSongs.add(matchedSong)
            }
        }

        return M3uMatchResult(
            matchedSongs = matchedSongs,
            unmatchedEntries = unmatchedEntries
        )
    }

    private fun findMatchingSong(
        entry: M3uEntry,
        normalizedSongs: List<NormalizedLibrarySong>,
        songsByExactPath: Map<String, NormalizedLibrarySong>,
        songsByFileName: Map<String, List<NormalizedLibrarySong>>
    ): Song? {
        val normalizedLocation = normalizeLocation(entry.location)

        if (normalizedLocation.isBlank()) {
            return null
        }

        songsByExactPath[normalizedLocation]?.let { exactMatch ->
            return exactMatch.song
        }

        findUniqueEndingPathMatch(
            normalizedLocation = normalizedLocation,
            normalizedSongs = normalizedSongs
        )?.let { endingPathMatch ->
            return endingPathMatch.song
        }

        val normalizedFileName = normalizedLocation.substringAfterLast("/")

        val fileNameMatches = songsByFileName[normalizedFileName].orEmpty()

        return if (fileNameMatches.size == 1) {
            fileNameMatches.first().song
        } else {
            null
        }
    }

    private fun findUniqueEndingPathMatch(
        normalizedLocation: String,
        normalizedSongs: List<NormalizedLibrarySong>
    ): NormalizedLibrarySong? {
        if (!normalizedLocation.contains("/")) {
            return null
        }

        val matches = normalizedSongs.filter { normalizedSong ->
            normalizedSong.normalizedPath.endsWith("/$normalizedLocation")
        }

        return if (matches.size == 1) {
            matches.first()
        } else {
            null
        }
    }

    private fun normalizeLocation(location: String): String {
        val trimmedLocation = location
            .trim()
            .trim('"')
            .replace('\\', '/')

        val withoutFileScheme = when {
            trimmedLocation.startsWith("file://", ignoreCase = true) -> {
                trimmedLocation.removePrefixIgnoringCase("file://")
            }

            trimmedLocation.startsWith("file:", ignoreCase = true) -> {
                trimmedLocation.removePrefixIgnoringCase("file:")
            }

            else -> trimmedLocation
        }

        return decodeLocation(withoutFileScheme)
            .replace('\\', '/')
            .trim()
            .trimEnd('/')
            .lowercase(Locale.ROOT)
    }

    private fun normalizeFileName(filePath: String): String {
        return normalizeLocation(filePath).substringAfterLast("/")
    }

    private fun decodeLocation(location: String): String {
        return try {
            URLDecoder.decode(location, Charsets.UTF_8.name())
        } catch (exception: Exception) {
            location
        } catch (error: IllegalArgumentException) {
            location
        }
    }

    private fun String.removePrefixIgnoringCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) {
            drop(prefix.length)
        } else {
            this
        }
    }
}

private data class NormalizedLibrarySong(
    val song: Song,
    val normalizedPath: String,
    val normalizedFileName: String
)