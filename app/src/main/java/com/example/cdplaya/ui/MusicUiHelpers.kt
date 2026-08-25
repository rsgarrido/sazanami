package com.example.cdplaya.ui

import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.data.sortSongsByDateAddedDescending
import com.example.cdplaya.data.sortSongsByDateAddedAscending
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
    ratingsByReferenceKey: Map<String, Int> = emptyMap()
): List<Song> {
    return when (sortOption) {
        LibrarySortOption.TITLE,
        LibrarySortOption.NAME -> {
            songs.sortedWith(
                compareBy<Song> { song ->
                    song.title.lowercase()
                }.thenBy { song ->
                    song.artist.lowercase()
                }.thenBy { song ->
                    song.album.lowercase()
                }
            )
        }

        LibrarySortOption.ARTIST -> {
            songs.sortedWith(
                compareBy<Song> { song ->
                    song.artist.ifBlank { "Unknown Artist" }.lowercase()
                }.thenBy { song ->
                    song.album.ifBlank { "Unknown Album" }.lowercase()
                }.thenBy { song ->
                    if (song.trackNumber > 0) song.trackNumber else Int.MAX_VALUE
                }.thenBy { song ->
                    song.title.lowercase()
                }
            )
        }

        LibrarySortOption.ALBUM -> {
            songs.sortedWith(
                compareBy<Song> { song ->
                    song.album.ifBlank { "Unknown Album" }.lowercase()
                }.thenBy { song ->
                    if (song.trackNumber > 0) song.trackNumber else Int.MAX_VALUE
                }.thenBy { song ->
                    song.title.lowercase()
                }
            )
        }

        LibrarySortOption.RATING_HIGH_TO_LOW -> songs.sortedWith(
            compareByDescending<Song> { song ->
                ratingsByReferenceKey[song.membershipKey()] ?: Int.MIN_VALUE
            }.thenBy { song -> song.title.trim().lowercase() }
                .thenBy { song -> song.membershipKey() }
        )

        LibrarySortOption.RATING_LOW_TO_HIGH -> songs.sortedWith(
            compareBy<Song> { song ->
                ratingsByReferenceKey[song.membershipKey()] ?: Int.MAX_VALUE
            }.thenBy { song -> song.title.trim().lowercase() }
                .thenBy { song -> song.membershipKey() }
        )

        LibrarySortOption.SONG_COUNT -> {
            songs
        }

        LibrarySortOption.DATE_ADDED_NEWEST -> sortSongsByDateAddedDescending(songs)
        LibrarySortOption.DATE_ADDED_OLDEST -> sortSongsByDateAddedAscending(songs)
    }
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
