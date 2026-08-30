package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.resolveAlbumDiscNumbers
import io.github.rsgarrido.sazanami.data.trackNumberWithinDisc
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.compareKnownPositiveLong
import io.github.rsgarrido.sazanami.ui.library.compareKnownPresence
import io.github.rsgarrido.sazanami.ui.library.compareLibraryText
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.library.SongRatingFilter

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

    return buildLibraryAlbumGroups(songs)
        .filter { album ->
            album.title.contains(query, ignoreCase = true) ||
                    album.artistText.contains(query, ignoreCase = true) ||
                    album.songs.any { song ->
                        song.artist.ifBlank { "Unknown Artist" }
                            .contains(query, ignoreCase = true)
                    }
        }
        .flatMap { album -> album.songs }
        .distinctBy(Song::membershipKey)
}

fun sortSongsByAlbumOrder(songs: List<Song>): List<Song> {
    val resolvedDiscNumbers = resolveAlbumDiscNumbers(songs)
    return songs.sortedWith(
        compareBy<Song> { song ->
            resolvedDiscNumbers[song.membershipKey()] ?: Int.MAX_VALUE
        }.thenBy { song ->
            song.trackNumberWithinDisc() ?: Int.MAX_VALUE
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
    val resolvedDiscNumbers = if (
        sortOption == LibrarySortOption.ARTIST || sortOption == LibrarySortOption.ALBUM
    ) {
        resolveAlbumDiscNumbers(songs)
    } else {
        emptyMap()
    }

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
                    ?: compareKnownInts(
                        resolvedDiscNumbers[left.membershipKey()],
                        resolvedDiscNumbers[right.membershipKey()],
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: compareKnownInts(
                        left.trackNumberWithinDisc(),
                        right.trackNumberWithinDisc(),
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
                    ?: compareKnownInts(
                        resolvedDiscNumbers[left.membershipKey()],
                        resolvedDiscNumbers[right.membershipKey()],
                        LibrarySortDirection.ASCENDING
                    ).takeUnless { it == 0 }
                    ?: compareKnownInts(
                        left.trackNumberWithinDisc(),
                        right.trackNumberWithinDisc(),
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
    val resolvedDiscNumbers = resolveAlbumDiscNumbers(songs)
    return songs.sortedWith(
        compareBy<Song> { song ->
            song.album.ifBlank { "Unknown Album" }.lowercase()
        }.thenBy { song ->
            resolvedDiscNumbers[song.membershipKey()] ?: Int.MAX_VALUE
        }.thenBy { song ->
            song.trackNumberWithinDisc() ?: Int.MAX_VALUE
        }.thenBy { song ->
            song.title.lowercase()
        }
    )
}
