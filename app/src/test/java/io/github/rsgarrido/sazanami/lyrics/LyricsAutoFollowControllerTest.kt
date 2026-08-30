package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAutoFollowControllerTest {
    @Test
    fun firstActiveLineUsesImmediateAnchorPositioning() {
        val controller = LyricsAutoFollowController()

        assertEquals(LyricsScrollRequest(4, false), controller.onActiveItemChanged(4))
    }

    @Test
    fun activeChangeRequestsOneScrollAndRepeatedTicksDoNot() {
        val controller = LyricsAutoFollowController()

        controller.onActiveItemChanged(4)
        assertEquals(LyricsScrollRequest(5, true), controller.onActiveItemChanged(5))
        assertNull(controller.onActiveItemChanged(5))
    }

    @Test
    fun userScrollDisablesFollowingButProgrammaticRequestsDoNot() {
        val controller = LyricsAutoFollowController()
        controller.onActiveItemChanged(1)

        assertTrue(controller.isEnabled)
        controller.onUserScroll()
        assertFalse(controller.isEnabled)
        assertNull(controller.onActiveItemChanged(2))
    }

    @Test
    fun returnActionRestoresFollowing() {
        val controller = LyricsAutoFollowController()
        controller.onUserScroll()

        assertEquals(LyricsScrollRequest(7, true), controller.returnToCurrent(7))
        assertTrue(controller.isEnabled)
    }

    @Test
    fun largeSeekUsesImmediateRepositioning() {
        val controller = LyricsAutoFollowController(largeJumpThreshold = 3)
        controller.onActiveItemChanged(1)

        assertEquals(LyricsScrollRequest(12, false), controller.onActiveItemChanged(12))
    }

    @Test
    fun trackChangeRestoresAutomaticFollowing() {
        val controller = LyricsAutoFollowController()
        controller.onUserScroll()

        controller.onTrackChanged()

        assertTrue(controller.isEnabled)
        assertEquals(LyricsScrollRequest(2, false), controller.onActiveItemChanged(2))
    }

    @Test
    fun missingActiveItemDoesNotScroll() {
        assertNull(LyricsAutoFollowController().onActiveItemChanged(null))
    }

    @Test
    fun viewportChangeReissuesAnchorForSameCueButRepeatedTicksDoNot() {
        val controller = LyricsAutoFollowController()

        assertEquals(
            LyricsScrollRequest(4, false),
            controller.onActiveItemChanged(4, anchorRevision = 1)
        )
        assertNull(controller.onActiveItemChanged(4, anchorRevision = 1))
        assertEquals(
            LyricsScrollRequest(4, true),
            controller.onActiveItemChanged(4, anchorRevision = 2)
        )
    }
}
