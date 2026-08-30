package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMorphStateTest {
    @Test
    fun collapsedInitializationUsesZeroProgress() {
        val state = state(PlayerPresentation.Collapsed)

        assertEquals(0f, state.progress, 0f)
        assertFalse(state.isExpandedOrTransitioning)
    }

    @Test
    fun expandedInitializationUsesOneProgress() {
        val state = state(PlayerPresentation.Expanded)

        assertEquals(1f, state.progress, 0f)
        assertTrue(state.isExpandedOrTransitioning)
    }

    @Test
    fun dragProgressClampsToValidRange() {
        val state = state(PlayerPresentation.Collapsed)

        state.updateProgressFromDrag(-4f)
        assertEquals(0f, state.progress, 0f)

        state.updateProgressFromDrag(7f)
        assertEquals(1f, state.progress, 0f)
    }

    @Test
    fun expandTargetsExpandedEndpoint() {
        val state = state(PlayerPresentation.Collapsed)

        state.expand()

        assertEquals(PlayerPresentation.Expanded, state.targetPresentation)
    }

    @Test
    fun collapseTargetsCollapsedEndpoint() {
        val state = state(PlayerPresentation.Expanded)

        state.collapse()

        assertEquals(PlayerPresentation.Collapsed, state.targetPresentation)
    }

    @Test
    fun reversingExpansionKeepsCurrentProgress() {
        val state = state(PlayerPresentation.Collapsed)
        state.updateProgressFromDrag(0.42f)

        state.collapse()

        assertEquals(0.42f, state.progress, 0f)
        assertEquals(PlayerPresentation.Collapsed, state.targetPresentation)
    }

    @Test
    fun reversingCollapseKeepsCurrentProgress() {
        val state = state(PlayerPresentation.Expanded)
        state.updateProgressFromDrag(0.58f)

        state.expand()

        assertEquals(0.58f, state.progress, 0f)
        assertEquals(PlayerPresentation.Expanded, state.targetPresentation)
    }

    @Test
    fun distanceThresholdChoosesCollapsedEndpointForDownwardDrag() {
        assertEquals(
            PlayerPresentation.Collapsed,
            selectPlayerMorphTarget(
                startPresentation = PlayerPresentation.Expanded,
                dragDistancePx = 261f,
                containerHeightPx = 1_000f,
                velocityY = 0f
            )
        )
    }

    @Test
    fun releaseVelocityFollowsVerticalDragOrientation() {
        assertEquals(
            PlayerPresentation.Collapsed,
            selectPlayerMorphTarget(
                startPresentation = PlayerPresentation.Expanded,
                dragDistancePx = 10f,
                containerHeightPx = 1_000f,
                velocityY = PlayerMorphVelocityThresholdPxPerSecond
            )
        )
        assertEquals(
            PlayerPresentation.Expanded,
            selectPlayerMorphTarget(
                startPresentation = PlayerPresentation.Collapsed,
                dragDistancePx = -10f,
                containerHeightPx = 1_000f,
                velocityY = -PlayerMorphVelocityThresholdPxPerSecond
            )
        )
    }

    @Test
    fun shortSlowReleaseReturnsToStartingEndpoint() {
        assertEquals(
            PlayerPresentation.Expanded,
            selectPlayerMorphTarget(
                startPresentation = PlayerPresentation.Expanded,
                dragDistancePx = 120f,
                containerHeightPx = 1_000f,
                velocityY = 500f
            )
        )
    }

    @Test
    fun missingMiniBoundsAreSafe() {
        val bounds = PlayerEndpointBounds()

        assertEquals(PlayerBoundsMeasurement.Missing, bounds.mini)
        bounds.updateMini(Rect.Zero)
        assertEquals(PlayerBoundsMeasurement.Missing, bounds.mini)
    }

    @Test
    fun miniBoundsUpdateReplacesPreviousMeasurementWithoutChangingProgress() {
        val state = state(PlayerPresentation.Expanded)
        val bounds = PlayerEndpointBounds()
        bounds.updateMini(Rect(0f, 10f, 100f, 80f))
        val first = bounds.mini as PlayerBoundsMeasurement.Measured

        bounds.updateMini(Rect(0f, 20f, 120f, 100f))

        val second = bounds.mini as PlayerBoundsMeasurement.Measured
        assertTrue(second.generation > first.generation)
        assertEquals(Rect(0f, 20f, 120f, 100f), second.bounds)
        assertEquals(1f, state.progress, 0f)
    }

    @Test
    fun expandedCompatibilityValueIsDerivedFromAuthoritativeState() {
        val state = state(PlayerPresentation.Collapsed)
        assertFalse(state.isExpandedOrTransitioning)

        state.expand()

        assertTrue(state.isExpandedOrTransitioning)
        assertEquals(PlayerPresentation.Expanded, state.targetPresentation)
    }

    private fun state(initial: PlayerPresentation): PlayerMorphState =
        PlayerMorphState(
            initialPresentation = initial,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
}
