package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTimelineNavigationTest {
    @Test
    fun previousAndNextKeepTheRequestedAdjacentMedia3TimelineIndices() {
        val previous = resolveMediaTimelineSeek(
            currentMediaItemIndex = 2,
            currentPositionMs = 1_000L,
            requestedMediaItemIndex = 1,
            requestedPositionMs = 0L,
            requestedSeekCommand = Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
        val next = resolveMediaTimelineSeek(
            currentMediaItemIndex = 2,
            currentPositionMs = 1_000L,
            requestedMediaItemIndex = 3,
            requestedPositionMs = 0L,
            requestedSeekCommand = Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )

        assertEquals(1, previous.mediaItemIndex)
        assertEquals(3, next.mediaItemIndex)
    }

    @Test
    fun previousRestartsCurrentAfterThresholdForEverySessionController() {
        val resolved = resolveMediaTimelineSeek(
            currentMediaItemIndex = 2,
            currentPositionMs = MEDIA_PREVIOUS_RESTART_THRESHOLD_MS + 1L,
            requestedMediaItemIndex = 1,
            requestedPositionMs = 0L,
            requestedSeekCommand = Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )

        assertEquals(2, resolved.mediaItemIndex)
        assertEquals(0L, resolved.positionMs)
        assertEquals(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, resolved.seekCommand)
    }
}
