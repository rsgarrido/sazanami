package com.example.cdplaya.ui.library

import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.sortSongsByAlbumOrder

data class LibraryAlbumGroup(
    val key: String,
    val title: String,
    val artistText: String,
    val songs: List<Song>
)

/**
 * Returns the exact songs owned by this folder-backed album group. Album titles are deliberately
 * not queried again because titles are not unique and may change during metadata editing.
 */
internal fun LibraryAlbumGroup.metadataEditingSongs(): List<Song> =
    songs.distinctBy(Song::membershipKey)

internal fun isAlbumGroupAvailable(albumKey: String, songs: List<Song>): Boolean =
    songs.any { song -> song.folderPath == albumKey }

fun buildLibraryAlbumGroups(
    songs: List<Song>
): List<LibraryAlbumGroup> {
    return songs
        .groupBy { song ->
            song.folderPath
        }
        .mapNotNull { entry ->
            val albumSongs = sortSongsByAlbumOrder(entry.value)
            val firstSong = albumSongs.firstOrNull() ?: return@mapNotNull null

            LibraryAlbumGroup(
                key = entry.key,
                title = firstSong.album.ifBlank { "Unknown Album" },
                artistText = buildLibraryAlbumArtistText(albumSongs),
                songs = albumSongs
            )
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

private val featuredArtistPattern = Regex(
    pattern = """\s+[\(\[]?\s*(feat\.?|ft\.?|featuring|with)\s+.*$""",
    option = RegexOption.IGNORE_CASE
)
