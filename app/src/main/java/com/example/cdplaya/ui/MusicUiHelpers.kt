package com.example.cdplaya.ui

import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.library.LibrarySortDirection
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.ui.library.compareKnownPositiveLong
import com.example.cdplaya.ui.library.compareKnownPresence
import com.example.cdplaya.ui.library.compareLibraryText
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.library.SongRatingFilter

fun filterSongsForSearch(
    songs: List<Song>,
    searchQuery: String
): List<Song> {
    val query = searchQuery.trim()

    if (query.isBlank()) {
        return songs
    }

    return songs.filter { song ->
        song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
    }
}

fun filterSongsByArtistSearch(
    songs: List<Song>,
    searchQuery: String
): List<Song> {
    val query = searchQuery.trim()

    if (query.isBlank()) {
        return songs
    }

    val matchingArtists = songs
        .filter { song ->
            song.artist.ifBlank { "Unknown Artist" }
                .contains(query, ignoreCase = true)
        }
        .map { song ->
            song.artist.ifBlank { "Unknown Artist" }
        }
        .toSet()

    return songs.filter { song ->
        song.artist.ifBlank { "Unknown Artist" } in matchingArtists
    }
}

fun filterSongsByAlbumSearch(
    songs: List<Song>,
    searchQuery: String
): List<Song> {
    val query = searchQuery.trim()

    if (query.isBlank()) {
        return songs
    }

    val matchingAlbumFolders = songs
        .filter { song ->
            song.album.ifBlank { "Unknown Album" }
                .contains(query, ignoreCase = true) ||
                    song.artist.ifBlank { "Unknown Artist" }
                        .contains(query, ignoreCase = true)
        }
        .map { song ->
            song.folderPath
        }
        .toSet()

    return songs.filter { song ->
        song.folderPath in matchingAlbumFolders
    }
}

fun sortSongsByAlbumOrder(songs: List<Song>): List<Song> {
    return songs.sortedWith(
        compareBy<Song> { song ->
            if (song.trackNumber > 0) song.trackNumber else Int.MAX_VALUE
        }.thenBy { song ->
            song.title.lowercase()
        }
    )
}

fun getDisplayTrackNumber(trackNumber: Int): String {
    if (trackNumber <= 0) {
        return "–"
    }

    val normalizedTrackNumber = trackNumber % 1000

    return if (normalizedTrackNumber > 0) {
        normalizedTrackNumber.toString()
    } else {
        trackNumber.toString()
    }
}

fun formatDuration(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}

fun sortSongsForLibrary(
    songs: List<Song>,
    sortOption: LibrarySortOption,
    sortDirection: LibrarySortDirection = LibrarySortDirection.ASCENDING,
    ratingsByReferenceKey: Map<String, Int> = emptyMap()
): List<Song> {
    return when (sortOption) {
        LibrarySortOption.TITLE,
        LibrarySortOption.NAME -> {
            songs.sortedWith { left, right ->
                compareLibraryText(left.title, right.title, sortDirection)
                    .takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.artist,
                        right.artist,
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.album,
                        right.album,
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: left.membershipKey().compareTo(right.membershipKey())
            }
        }

        LibrarySortOption.ARTIST -> {
            songs.sortedWith { left, right ->
                compareLibraryText(left.artist, right.artist, sortDirection)
                    .takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.album,
                        right.album,
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: compareKnownPositiveInt(
                        left.trackNumber,
                        right.trackNumber,
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.title,
                        right.title,
                        LibrarySortDirection.ASCENDING
                    )
            }
        }

        LibrarySortOption.ALBUM -> {
            songs.sortedWith { left, right ->
                compareLibraryText(left.album, right.album, sortDirection)
                    .takeUnless { it == 0 }
                    ?: compareKnownPositiveInt(
                        left.trackNumber,
                        right.trackNumber,
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.title,
                        right.title,
                        LibrarySortDirection.ASCENDING
                    )
            }
        }

        LibrarySortOption.YEAR -> songs.sortedWith { left, right ->
            compareKnownInts(left.year, right.year, sortDirection)
                .takeUnless { it == 0 }
                ?: compareLibraryText(
                    left.title,
                    right.title,
                    LibrarySortDirection.ASCENDING
                ).takeUnless { it == 0 }
                ?: left.membershipKey().compareTo(right.membershipKey())
        }

        LibrarySortOption.RATING -> songs.sortedWith { left, right ->
            compareKnownInts(
                ratingsByReferenceKey[left.membershipKey()],
                ratingsByReferenceKey[right.membershipKey()],
                sortDirection
            ).takeUnless { it == 0 }
                ?: compareLibraryText(
                    left.title,
                    right.title,
                    LibrarySortDirection.ASCENDING
                ).takeUnless { it == 0 }
                ?: left.membershipKey().compareTo(right.membershipKey())
        }

        LibrarySortOption.SONG_COUNT -> {
            songs
        }

        LibrarySortOption.DATE_ADDED -> songs.sortedWith { left, right ->
            compareKnownPositiveLong(
                left.dateAddedEpochSeconds,
                right.dateAddedEpochSeconds,
                sortDirection
            ).takeUnless { it == 0 }
                ?: compareLibraryText(
                    left.title,
                    right.title,
                    LibrarySortDirection.ASCENDING
                ).takeUnless { it == 0 }
                ?: left.id.compareTo(right.id)
        }
    }
}

private fun compareKnownInts(
    left: Int?,
    right: Int?,
    direction: LibrarySortDirection
): Int {
    val knownComparison = compareKnownPresence(left != null, right != null)
    if (knownComparison != 0 || left == null) return knownComparison
    return direction.applyTo(left.compareTo(requireNotNull(right)))
}

private fun compareKnownPositiveInt(
    left: Int,
    right: Int,
    direction: LibrarySortDirection
): Int {
    val knownComparison = compareKnownPresence(left > 0, right > 0)
    if (knownComparison != 0 || left <= 0) return knownComparison
    return direction.applyTo(left.compareTo(right))
}

fun filterSongsByRating(
    songs: List<Song>,
    filter: SongRatingFilter,
    ratingsByReferenceKey: Map<String, Int>
): List<Song> = when (filter) {
    SongRatingFilter.ALL -> songs
    SongRatingFilter.RATED -> songs.filter { song ->
        ratingsByReferenceKey[song.membershipKey()] in 1..5
    }
    SongRatingFilter.UNRATED -> songs.filter { song ->
        ratingsByReferenceKey[song.membershipKey()] !in 1..5
    }
}

fun sortSongsForArtistDetail(songs: List<Song>): List<Song> {
    return songs.sortedWith(
        compareBy<Song> { song ->
            song.album.ifBlank { "Unknown Album" }.lowercase()
        }.thenBy { song ->
            if (song.trackNumber > 0) song.trackNumber else Int.MAX_VALUE
        }.thenBy { song ->
            song.title.lowercase()
        }
    )
}
