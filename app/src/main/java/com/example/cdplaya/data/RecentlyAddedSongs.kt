package com.example.cdplaya.data

import java.util.Locale

fun sortSongsByDateAddedDescending(songs: Collection<Song>): List<Song> {
    return songs.sortedWith(
        compareByDescending<Song> { it.dateAddedEpochSeconds > 0L }
            .thenByDescending { it.dateAddedEpochSeconds }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    )
}

fun sortSongsByDateAddedAscending(songs: Collection<Song>): List<Song> {
    return songs.sortedWith(
        compareByDescending<Song> { it.dateAddedEpochSeconds > 0L }
            .thenBy { it.dateAddedEpochSeconds }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    )
}

fun recentlyAddedShelfSongs(songs: Collection<Song>, limit: Int = 8): List<Song> {
    if (limit <= 0) return emptyList()
    return sortSongsByDateAddedDescending(songs)
        .asSequence()
        .filter { it.dateAddedEpochSeconds > 0L }
        .take(limit)
        .toList()
}
