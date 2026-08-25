package com.example.cdplaya.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RecentlyAddedSongsTest {
    @Test
    fun knownDatesSortNewestFirstAndUnknownDatesSortLast() {
        val sorted = sortSongsByDateAddedDescending(
            listOf(song(1, "Unknown", 0), song(2, "Older", 100), song(3, "Newest", 200))
        )

        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun knownDatesSortOldestFirstAndUnknownDatesStillSortLast() {
        val sorted = sortSongsByDateAddedAscending(
            listOf(song(1, "Unknown", 0), song(2, "Older", 100), song(3, "Newest", 200))
        )

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun tiesUseDeterministicTitleArtistAndIdOrder() {
        val sorted = sortSongsByDateAddedDescending(
            listOf(
                song(9, "Beta", 100),
                song(2, "Alpha", 100, artist = "Zulu"),
                song(1, "Alpha", 100, artist = "Zulu")
            )
        )

        assertEquals(listOf(1L, 2L, 9L), sorted.map { it.id })
    }

    @Test
    fun shelfOmitsUnknownDatesAndHonorsLimit() {
        val shelf = recentlyAddedShelfSongs(
            (1L..10L).map { song(it, "Song $it", it) } + song(99, "Unknown", 0),
            limit = 4
        )

        assertEquals(listOf(10L, 9L, 8L, 7L), shelf.map { it.id })
        assertTrue(recentlyAddedShelfSongs(listOf(song(1, "Unknown", 0))).isEmpty())
    }

    private fun song(id: Long, title: String, added: Long, artist: String = "Artist") = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        trackNumber = 1,
        duration = 1_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/$id.mp3",
        folderPath = "/music",
        albumArtUri = null,
        dateAddedEpochSeconds = added
    )
}
