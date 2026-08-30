package io.github.rsgarrido.sazanami.data.playlistfile

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class M3uPlaylistImportMatchingTest {
    @Test
    fun matchEntriesToLibrarySongs_preservesOrderAndReportsMissingPaths() {
        val japaneseSong = testSong(
            id = 1,
            title = "夜の歌",
            filePath = "/Music/日本/夜の歌.flac"
        )
        val accentedSong = testSong(
            id = 2,
            title = "Café del Mar",
            filePath = "/Music/Español/Café del Mar.mp3"
        )

        val result = M3uPlaylistMatcher.matchEntriesToLibrarySongs(
            entries = listOf(
                M3uEntry(location = accentedSong.filePath),
                M3uEntry(location = "/Music/Missing/Fake Song.mp3"),
                M3uEntry(location = japaneseSong.filePath)
            ),
            librarySongs = listOf(japaneseSong, accentedSong)
        )

        assertEquals(listOf(accentedSong, japaneseSong), result.matchedSongs)
        assertEquals(
            listOf(M3uEntry(location = "/Music/Missing/Fake Song.mp3")),
            result.unmatchedEntries
        )
    }

    private fun testSong(
        id: Long,
        title: String,
        filePath: String
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 180_000,
            uri = mock(Uri::class.java),
            filePath = filePath,
            folderPath = filePath.substringBeforeLast('/'),
            albumArtUri = null
        )
    }
}
