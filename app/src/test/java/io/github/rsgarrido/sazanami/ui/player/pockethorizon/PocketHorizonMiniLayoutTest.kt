package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonMiniLayoutTest {
    @Test fun rejectsInvalidAndInsufficientWidthsRatherThanShrinkingTouchTargets() {
        for (width in listOf(Float.NaN.dp, Float.POSITIVE_INFINITY.dp, 0.dp, 100.dp,
            PocketHorizonMiniMetrics.MinimumSurfaceWidth - 1.dp)) {
            assertNull(resolvePocketHorizonMiniLayout(width, 1f))
        }
        assertNotNull(resolvePocketHorizonMiniLayout(PocketHorizonMiniMetrics.MinimumSurfaceWidth, 1f))
        assertNull(resolvePocketHorizonMiniLayout(600.dp, 1f, 47.dp))
        assertNull(resolvePocketHorizonMiniLayout(600.dp, 1f, Float.NaN.dp))
        assertNull(resolvePocketHorizonMiniLayout(600.dp, 3f, 68.dp))
        assertNotNull(resolvePocketHorizonMiniLayout(600.dp, 3f, 80.dp))
    }

    @Test fun normalCompactHeightFitsControlsAndLargeTextYieldsArtistBeforeTouchTargets() {
        val normal = requireNotNull(resolvePocketHorizonMiniLayout(600.dp, 1f))
        val large = requireNotNull(resolvePocketHorizonMiniLayout(600.dp, 2f))
        assertTrue(normal.showArtist)
        assertFalse(large.showArtist)
        assertEquals(normal.surfaceHeight, large.surfaceHeight)
        assertTrue(large.contentHeight >= PocketHorizonMiniMetrics.TouchTarget)
        for (scale in listOf(1f, 1.3f, 2f, 3f, 3.5f)) {
            val layout = requireNotNull(resolvePocketHorizonMiniLayout(600.dp, scale))
            assertTrue(layout.contentHeight >= (20f * scale).dp)
            if (layout.showArtist) assertTrue(layout.contentHeight >= (36f * scale + 2f).dp)
        }
    }

    @Test fun optionalTrackControlsLeaveAnExplicitMetadataBudgetAtEachFontScale() {
        assertFalse(requireNotNull(resolvePocketHorizonMiniLayout(240.dp, 1f)).showTrackButtons)
        assertTrue(requireNotNull(resolvePocketHorizonMiniLayout(600.dp, 2f)).showTrackButtons)
        // Invalid platform font facts use the same safe normalization as the expanded shell.
        assertEquals(resolvePocketHorizonMiniLayout(360.dp, 1f), resolvePocketHorizonMiniLayout(360.dp, Float.NaN))
    }
}
