package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedPlayerDragStateTest {
    @Test
    fun retroRackLyricsRegionEndsBeforePlaybackRack() {
        val region = measuredLyricsRegion(top = 20f, bottom = 430f)

        assertTrue(region.contains(120f))
        assertTrue(region.contains(420f))
        assertFalse(region.contains(431f))
        assertFalse(region.contains(760f))
    }

    @Test
    fun pocketFlipLyricsRegionEndsWithMainPlaybackButtons() {
        val region = measuredLyricsRegion(top = 16f, bottom = 610f)

        assertTrue(region.contains(500f))
        assertFalse(region.contains(650f))
        assertFalse(region.contains(790f))
    }

    @Test
    fun pocketCassetteLyricsRegionExcludesLowerSeamAndBottomEdge() {
        val region = measuredLyricsRegion(top = 12f, bottom = 704f)

        assertTrue(region.contains(680f))
        assertFalse(region.contains(730f))
        assertFalse(region.contains(799f))
    }

    @Test
    fun pocketDiscLyricsRegionEndsAboveLevelMeterAndBottomEdge() {
        val region = measuredLyricsRegion(top = 11f, bottom = 682f)

        assertTrue(region.contains(660f))
        assertFalse(region.contains(720f))
        assertFalse(region.contains(799f))
    }

    @Test
    fun lyricsTransitionCanExplicitlyResetExpandedPlayerOffset() {
        val state = PlayerMorphState(
            initialPresentation = PlayerPresentation.Expanded,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        state.beginDrag(1_000f)
        state.dragBy(240f)

        state.snapTo(PlayerPresentation.Expanded)

        assertEquals(1f, state.progress, 0f)
    }

    @Test
    fun distancePastThresholdCollapses() {
        assertTrue(
            shouldCollapseExpandedPlayer(
                offsetY = 261f,
                containerHeightPx = 1_000f,
                velocityY = 0f
            )
        )
    }

    @Test
    fun downwardVelocityCollapsesBeforeDistanceThreshold() {
        assertTrue(
            shouldCollapseExpandedPlayer(
                offsetY = 80f,
                containerHeightPx = 1_000f,
                velocityY = ExpandedPlayerCollapseVelocityPxPerSecond
            )
        )
    }

    @Test
    fun shortSlowDragSnapsBack() {
        assertFalse(
            shouldCollapseExpandedPlayer(
                offsetY = 120f,
                containerHeightPx = 1_000f,
                velocityY = 500f
            )
        )
    }

    @Test
    fun upwardFlingNeverTriggersVelocityCollapse() {
        assertFalse(
            shouldCollapseExpandedPlayer(
                offsetY = 80f,
                containerHeightPx = 1_000f,
                velocityY = -2_000f
            )
        )
    }

    @Test
    fun deliberateUpwardDistanceOpensLyrics() {
        assertTrue(
            shouldOpenLyrics(
                offsetY = -181f,
                containerHeightPx = 1_000f,
                velocityY = 0f
            )
        )
    }

    @Test
    fun fastUpwardFlingOpensLyricsBeforeDistanceThreshold() {
        assertTrue(
            shouldOpenLyrics(
                offsetY = -40f,
                containerHeightPx = 1_000f,
                velocityY = ExpandedPlayerLyricsVelocityPxPerSecond
            )
        )
    }

    @Test
    fun shortUpwardMovementDoesNotOpenLyrics() {
        assertFalse(
            shouldOpenLyrics(
                offsetY = -100f,
                containerHeightPx = 1_000f,
                velocityY = -500f
            )
        )
    }

    private fun measuredLyricsRegion(top: Float, bottom: Float) =
        PlayerLyricsGestureRegion().also { region ->
            region.updateTop(Rect(0f, top, 400f, top + 80f))
            region.updateBottom(Rect(0f, bottom - 80f, 400f, bottom))
        }
}
