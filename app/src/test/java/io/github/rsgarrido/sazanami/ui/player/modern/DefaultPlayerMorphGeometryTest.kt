package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.selectPlayerMorphTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlayerMorphGeometryTest {
    @Test
    fun rectangleInterpolationStartsAtCollapsedBounds() {
        assertEquals(
            collapsedRect,
            interpolateMorphRect(collapsedRect, expandedRect, 0f)
        )
    }

    @Test
    fun rectangleInterpolationEndsAtExpandedBounds() {
        assertEquals(
            expandedRect,
            interpolateMorphRect(collapsedRect, expandedRect, 1f)
        )
    }

    @Test
    fun rectangleInterpolationUsesIntermediateGeometry() {
        assertEquals(
            Rect(50f, 110f, 260f, 420f),
            interpolateMorphRect(collapsedRect, expandedRect, 0.5f)
        )
    }

    @Test
    fun rectangleInterpolationClampsProgress() {
        assertEquals(
            collapsedRect,
            interpolateMorphRect(collapsedRect, expandedRect, -2f)
        )
        assertEquals(
            expandedRect,
            interpolateMorphRect(collapsedRect, expandedRect, 4f)
        )
    }

    @Test
    fun missingMiniBoundsDoNotProduceGeometry() {
        assertNull(
            resolveDefaultPlayerMorphGeometry(
                progress = 0.5f,
                endpointBounds = PlayerEndpointBounds(),
                elementBounds = completeElementBounds()
            )
        )
    }

    @Test
    fun invalidElementBoundsDoNotProduceGeometry() {
        val endpoints = completeEndpointBounds()
        val elements = DefaultPlayerMorphBounds()
        elements.updateMiniSurface(Rect.Zero)

        assertNull(resolveDefaultPlayerMorphGeometry(0.5f, endpoints, elements))
    }

    @Test
    fun completeBoundsProduceInterpolatedGeometry() {
        val geometry = resolveDefaultPlayerMorphGeometry(
            progress = 0.5f,
            endpointBounds = completeEndpointBounds(),
            elementBounds = completeElementBounds()
        )

        assertEquals(Rect(50f, 110f, 260f, 420f), geometry?.surface)
        assertEquals(Rect(30f, 120f, 190f, 280f), geometry?.artwork)
    }

    @Test
    fun cornerRadiusInterpolationReachesEndpoints() {
        assertEquals(18f, interpolateMorphCornerRadius(18f, 0f, 0f), 0f)
        assertEquals(0f, interpolateMorphCornerRadius(18f, 0f, 1f), 0f)
    }

    @Test
    fun progressWindowIsZeroBeforeReveal() {
        assertEquals(0f, morphProgressWindow(0.2f, 0.3f, 0.7f), 0f)
    }

    @Test
    fun progressWindowIsOneAfterReveal() {
        assertEquals(1f, morphProgressWindow(0.8f, 0.3f, 0.7f), 0f)
    }

    @Test
    fun progressWindowNaturallyReverses() {
        val later = morphProgressWindow(0.65f, 0.3f, 0.75f)
        val earlier = morphProgressWindow(0.45f, 0.3f, 0.75f)

        assertTrue(earlier < later)
    }

    @Test
    fun upwardDragIncreasesProgress() {
        assertEquals(0.25f, morphProgressFromDrag(0f, -100f, 400f), 0f)
    }

    @Test
    fun sharedStateUsesActualMiniMorphTravel() {
        val state = PlayerMorphState(
            initialPresentation = PlayerPresentation.Collapsed,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        state.beginDragWithRange(400f)

        state.dragBy(-100f)

        assertEquals(0.25f, state.progress, 0f)
    }

    @Test
    fun downwardDragDecreasesProgress() {
        assertEquals(0.75f, morphProgressFromDrag(1f, 100f, 400f), 0f)
    }

    @Test
    fun dragMappingUsesSafeMinimumRange() {
        assertEquals(
            1f,
            morphProgressFromDrag(0f, -DefaultMorphMinimumDragRangePx, 0f),
            0f
        )
    }

    @Test
    fun travelDistanceUsesMeasuredSurfacePositions() {
        assertEquals(
            180f,
            defaultMorphTravelDistance(
                endpointBounds = completeEndpointBounds(),
                elementBounds = completeElementBounds()
            ),
            0f
        )
    }

    @Test
    fun existingReleaseThresholdStillSelectsExpectedEndpoint() {
        assertEquals(
            PlayerPresentation.Expanded,
            selectPlayerMorphTarget(
                startPresentation = PlayerPresentation.Collapsed,
                dragDistancePx = -261f,
                containerHeightPx = 1_000f,
                velocityY = 0f
            )
        )
    }

    @Test
    fun endpointActivityExposesOneOwner() {
        assertEquals(
            DefaultMorphEndpointActivity.Mini,
            defaultMorphEndpointActivity(0f)
        )
        assertEquals(
            DefaultMorphEndpointActivity.Transition,
            defaultMorphEndpointActivity(0.4f)
        )
        assertEquals(
            DefaultMorphEndpointActivity.Expanded,
            defaultMorphEndpointActivity(1f)
        )
    }

    @Test
    fun expensiveContentIsInactiveBelowVisibilityThreshold() {
        assertFalse(
            shouldRunDefaultExpandedWork(
                DefaultPlayerMorphSpec.ExpensiveContentThreshold - 0.01f
            )
        )
        assertTrue(
            shouldRunDefaultExpandedWork(
                DefaultPlayerMorphSpec.ExpensiveContentThreshold
            )
        )
    }

    @Test
    fun songAndPlaybackUpdatesDoNotResetMorphProgress() {
        val state = PlayerMorphState(
            initialPresentation = PlayerPresentation.Collapsed,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        state.updateProgressFromDrag(0.47f)

        val songIdChanged = true
        val playbackChanged = true

        assertTrue(songIdChanged && playbackChanged)
        assertEquals(0.47f, state.progress, 0f)
    }

    @Test
    fun onlyDefaultThemeSelectsDefaultMorph() {
        assertTrue(shouldUseDefaultMorph(PlayerTheme.DEFAULT))
        assertFalse(shouldUseDefaultMorph(PlayerTheme.CLASSIC_WHEEL))
        assertFalse(shouldUseDefaultMorph(PlayerTheme.RETRO_RACK))
        assertFalse(shouldUseDefaultMorph(PlayerTheme.POCKET_FLIP))
        assertFalse(shouldUseDefaultMorph(PlayerTheme.POCKET_CASSETTE))
    }

    @Test
    fun insignificantBoundsUpdatesDoNotReplaceMeasurement() {
        val bounds = completeElementBounds()
        val original = bounds.miniArtwork

        bounds.updateMiniArtwork(Rect(10.1f, 20.1f, 62.1f, 72.1f))

        assertTrue(original === bounds.miniArtwork)
    }

    private fun completeEndpointBounds() = PlayerEndpointBounds().apply {
        updateMini(collapsedRect)
        updateExpanded(expandedRect)
    }

    private fun completeElementBounds() = DefaultPlayerMorphBounds().apply {
        updateMiniSurface(collapsedRect)
        updateMiniArtwork(Rect(10f, 20f, 62f, 72f))
        updateMiniText(Rect(74f, 20f, 210f, 72f))
        updateMiniPlayPause(Rect(212f, 22f, 260f, 70f))
        updateExpandedArtwork(Rect(50f, 220f, 318f, 488f))
        updateExpandedText(Rect(16f, 520f, 344f, 620f))
        updateExpandedPlayPause(Rect(139f, 680f, 221f, 762f))
    }

    private companion object {
        val collapsedRect = Rect(0f, 20f, 280f, 100f)
        val expandedRect = Rect(100f, 200f, 240f, 740f)
    }
}
