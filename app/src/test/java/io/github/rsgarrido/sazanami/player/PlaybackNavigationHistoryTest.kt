package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackNavigationHistoryTest {

    @Test
    fun previousAndNextFollowActualPlaybackPath() {
        val history = PlaybackNavigationHistory()
        val songA = song(1)
        val songC = song(3)
        val songB = song(2)

        history.addPreviousSong(songA)
        history.addPreviousSong(songC)

        assertEquals(songC, history.peekPreviousSong())
        assertEquals(songC, history.popPreviousSongAndPushCurrent(songB))
        assertEquals(songA, history.peekPreviousSong())
        assertEquals(songB, history.peekNextSong())
        assertEquals(songA, history.popPreviousSongAndPushCurrent(songC))
        assertEquals(songC, history.popNextSong())
        assertEquals(songB, history.popNextSong())
        assertNull(history.popNextSong())
    }

    @Test
    fun consecutiveDuplicatePreviousSongsAreIgnored() {
        val history = PlaybackNavigationHistory()
        val songA = song(1)

        history.addPreviousSong(songA)
        history.addPreviousSong(songA)

        assertEquals(listOf(songA.id), history.getPreviousSongIds())
    }

    @Test
    fun clearAllRemovesBackAndForwardHistoryForNewContext() {
        val history = PlaybackNavigationHistory()
        val songA = song(1)
        val songB = song(2)

        history.addPreviousSong(songA)
        history.popPreviousSongAndPushCurrent(songB)
        history.clearAll()

        assertEquals(emptyList<Long>(), history.getPreviousSongIds())
        assertEquals(emptyList<Long>(), history.getNextSongIds())
    }

    @Test
    fun invalidSongsAreRemovedFromBothDirections() {
        val history = PlaybackNavigationHistory()
        history.replacePreviousSongs(listOf(song(1), song(2), song(3)))
        history.replaceNextSongs(listOf(song(4), song(2), song(5)))

        history.removeInvalidSongs(setOf(1L, 4L, 5L))

        assertEquals(listOf(1L), history.getPreviousSongIds())
        assertEquals(listOf(4L, 5L), history.getNextSongIds())
    }

    @Test
    fun previousPushesDepartedSongsOntoForwardHistoryInOrder() {
        val history = PlaybackNavigationHistory()
        history.replacePreviousSongs(listOf(song(1), song(2)))

        assertEquals(2L, history.popPreviousSongAndPushCurrent(song(3))?.id)
        assertEquals(1L, history.popPreviousSongAndPushCurrent(song(2))?.id)
        assertEquals(listOf(3L, 2L), history.getNextSongIds())
        assertEquals(2L, history.popNextSong()?.id)
        assertEquals(3L, history.popNextSong()?.id)
    }

    @Test
    fun replacementPreservesDuplicatesAndExactOrder() {
        val history = PlaybackNavigationHistory()
        history.replacePreviousSongs(listOf(song(1), song(2), song(1)))
        history.replaceNextSongs(listOf(song(3), song(3), song(4)))

        assertEquals(listOf(1L, 2L, 1L), history.getPreviousSongIds())
        assertEquals(listOf(3L, 3L, 4L), history.getNextSongIds())
    }

    private fun song(id: Long): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            trackNumber = id.toInt(),
            duration = 180_000,
            uri = mock(Uri::class.java),
            filePath = "/music/$id.mp3",
            folderPath = "/music",
            albumArtUri = null
        )
    }
}
