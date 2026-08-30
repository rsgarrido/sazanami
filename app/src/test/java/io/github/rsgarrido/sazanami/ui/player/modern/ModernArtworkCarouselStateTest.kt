package io.github.rsgarrido.sazanami.ui.player.modern

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ModernArtworkCarouselStateTest {

    @Test
    fun transitionDurations_keepNavigationVisibleAndSnapBackResponsive() {
        assertEquals(400, MODERN_ARTWORK_BUTTON_TRANSITION_DURATION_MILLIS)
        assertEquals(280, MODERN_ARTWORK_ACCEPTED_DRAG_DURATION_MILLIS)
        assertEquals(180, MODERN_ARTWORK_CANCELLED_DRAG_DURATION_MILLIS)
    }

    @Test
    fun leftDragPastDistanceThresholdMovesNext() {
        assertEquals(
            ModernCarouselDirection.NEXT,
            resolveModernArtworkSwipe(
                offsetX = -251f,
                artworkWidthPx = 1_000f,
                velocityX = 0f
            )
        )
    }

    @Test
    fun rightDragPastDistanceThresholdMovesPrevious() {
        assertEquals(
            ModernCarouselDirection.PREVIOUS,
            resolveModernArtworkSwipe(
                offsetX = 250f,
                artworkWidthPx = 1_000f,
                velocityX = 0f
            )
        )
    }

    @Test
    fun shortDragCancels() {
        assertEquals(
            ModernCarouselDirection.NONE,
            resolveModernArtworkSwipe(
                offsetX = -249f,
                artworkWidthPx = 1_000f,
                velocityX = 899f
            )
        )
    }

    @Test
    fun fastFlingUsesVelocityDirection() {
        assertEquals(
            ModernCarouselDirection.PREVIOUS,
            resolveModernArtworkSwipe(
                offsetX = -100f,
                artworkWidthPx = 1_000f,
                velocityX = 901f
            )
        )
        assertEquals(
            ModernCarouselDirection.NEXT,
            resolveModernArtworkSwipe(
                offsetX = 100f,
                artworkWidthPx = 1_000f,
                velocityX = -901f
            )
        )
    }

    @Test
    fun previousIntentIsIgnoredWhenSongIdDoesNotChange() {
        val coroutineScope = CoroutineScope(Job())
        val state = ModernArtworkCarouselState(
            coroutineScope = coroutineScope,
            onPrevious = {},
            onNext = {}
        )

        state.recordButtonNavigation(
            direction = ModernCarouselDirection.PREVIOUS,
            sourceSongId = 10L
        )

        assertNull(state.consumeTransitionForSongChange(newSongId = 10L))
        coroutineScope.cancel()
    }

    @Test
    fun buttonIntentIsConsumedOnlyByDifferentSong() {
        val coroutineScope = CoroutineScope(Job())
        val state = ModernArtworkCarouselState(
            coroutineScope = coroutineScope,
            onPrevious = {},
            onNext = {}
        )

        state.recordButtonNavigation(
            direction = ModernCarouselDirection.NEXT,
            sourceSongId = 10L
        )
        val transition = state.consumeTransitionForSongChange(newSongId = 11L)

        assertEquals(ModernCarouselDirection.NEXT, transition?.direction)
        assertFalse(transition?.startedFromDrag ?: true)
        coroutineScope.cancel()
    }
}
