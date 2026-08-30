package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackQueueManagerTest {
    @Test
    fun duplicatesArePreservedAndSingleRemovalTargetsRequestedOccurrence() {
        val manager = PlaybackQueueManager()
        val duplicate = song(1)
        manager.addSongsToQueue(listOf(duplicate, song(2), duplicate))

        assertTrue(manager.removeFirstMatchingSongFromQueue(duplicate))
        assertEquals(listOf(2L, 1L), manager.getQueuedSongIds())
        assertTrue(manager.removeLastMatchingSongFromQueue(duplicate))
        assertEquals(listOf(2L), manager.getQueuedSongIds())
    }

    @Test
    fun playNextAndBulkOperationsPreserveInsertionOrder() {
        val manager = PlaybackQueueManager()
        manager.addSongToQueue(song(4))
        manager.addSongsToPlayNext(listOf(song(1), song(2), song(3)))
        manager.addSongsToQueue(listOf(song(5), song(6)))

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), manager.getQueuedSongIds())
    }

    @Test
    fun moveBoundariesAndInvalidIndexesDoNotMutateQueue() {
        val manager = PlaybackQueueManager()
        manager.addSongsToQueue(listOf(song(1), song(2), song(3)))

        assertFalse(manager.moveQueuedSongUp(0))
        assertFalse(manager.moveQueuedSongDown(2))
        assertFalse(manager.removeSongFromQueue(9))
        assertTrue(manager.moveQueuedSongUp(2))
        assertTrue(manager.moveQueuedSongDown(0))

        assertEquals(listOf(3L, 1L, 2L), manager.getQueuedSongIds())
    }

    @Test
    fun invalidSongsCountsUpcomingAndReplacementAreReliable() {
        val manager = PlaybackQueueManager()
        manager.addSongsToQueue(listOf(song(1), song(2), song(1), song(3)))

        assertTrue(manager.removeInvalidSongs(setOf(1L, 3L)))
        assertEquals(listOf(1L, 1L, 3L), manager.getQueuedSongIds())
        assertEquals(1, manager.getQueuedSongCountExcludingCurrent(1L))
        assertEquals(listOf(3L), manager.getQueuedSongsAfterCurrent(1L).map { it.id })

        manager.replaceQueue(listOf(song(8), song(8), song(9)))
        assertEquals(listOf(8L, 8L, 9L), manager.getQueuedSongIds())
    }

    private fun song(id: Long) = Song(
        id, "Song $id", "Artist", "Album", id.toInt(), 180_000L,
        mock(Uri::class.java), "/music/$id.mp3", "/music", null
    )
}
