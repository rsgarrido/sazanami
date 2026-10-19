package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
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

    @Test
    fun rootAlignedHostPreservesEveryEndpointAndIntermediateRectangle() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)

        for (progress in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(
                expectedFramedGeometry(progress),
                resolveDefaultPlayerMorphGeometry(progress, endpoints, elements)
            )
        }
    }

    @Test
    fun translatedHostConvertsAllRectanglesExactlyOnceBeforeInterpolation() {
        for (origin in listOf(Offset(96f, 54f), Offset(-20f, 32f))) {
            val endpoints = framedEndpoints(origin)
            val elements = framedElements(endpoints)
            publishMini(elements, origin)
            publishExpanded(elements, origin)

            for (progress in listOf(0f, 0.25f, 0.5f, 1f)) {
                assertEquals(
                    expectedFramedGeometry(progress),
                    resolveDefaultPlayerMorphGeometry(progress, endpoints, elements)
                )
            }
            // Source storage remains root-relative; drawing geometry is host-relative.
            assertEquals(frameMiniSurface.translate(origin), elements.miniSurface)
            assertEquals(
                frameSurface,
                resolveDefaultPlayerMorphGeometry(1f, endpoints, elements)?.surface
            )
        }
    }

    @Test
    fun resizeKeepsCompleteEndpointsUntilAllNewMeasurementsArrive() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)
        assertEquals(expectedFramedGeometry(0.4f),
            resolveDefaultPlayerMorphGeometry(0.4f, endpoints, elements))

        val resizedSurface = Rect(0f, 0f, 720f, 600f)
        val origin = Offset(80f, 30f)
        updateFrame(endpoints, origin, resizedSurface)
        publishMini(elements, origin)
        endpoints.updateMini(frameMiniSurface.translate(origin))
        elements.updateExpandedArtwork(frameExpandedArtwork.translate(origin))

        // Both directions still interpolate the last coherent endpoints, not a frozen fraction
        // or the resized surface combined with the old metadata/control destinations.
        for (progress in listOf(0.7f, 0.2f, 1f, 0f)) {
            assertEquals(expectedFramedGeometry(progress),
                resolveDefaultPlayerMorphGeometry(progress, endpoints, elements))
        }
        // Unchanged element rectangles must also be re-stamped for the new frame.
        publishExpanded(elements, origin)
        assertEquals(
            expectedFramedGeometry(0.5f).copy(
                surface = interpolateMorphRect(frameMiniSurface, resizedSurface, 0.5f)
            ),
            resolveDefaultPlayerMorphGeometry(0.5f, endpoints, elements)
        )
    }

    @Test
    fun staleSourceCannotCompleteANewLayoutWithoutFreshMiniMeasurements() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)

        val origin = Offset(70f, 20f)
        updateFrame(endpoints, origin)
        publishExpanded(elements, origin)
        assertNull(resolveDefaultPlayerMorphGeometry(0.5f, endpoints, elements))

        endpoints.updateMini(frameMiniSurface.translate(origin))
        // Updating just the generic mini endpoint must not validate cached Default elements.
        assertNull(resolveDefaultPlayerMorphGeometry(0.5f, endpoints, elements))
        publishMini(elements, origin)
        assertEquals(expectedFramedGeometry(0.5f),
            resolveDefaultPlayerMorphGeometry(0.5f, endpoints, elements))
    }

    @Test
    fun invalidPlaceholderKeepsOnlyThePreviousCompleteSnapshotUntilItRecovers() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)
        resolveDefaultPlayerMorphGeometry(1f, endpoints, elements)

        val origin = Offset(80f, 30f)
        updateFrame(endpoints, origin)
        publishMini(elements, origin)
        endpoints.updateMini(frameMiniSurface.translate(origin))
        elements.updateExpandedArtwork(Rect.Zero)
        val newText = frameExpandedText.translate(0f, 24f)
        elements.updateExpandedText(newText.translate(origin))
        elements.updateExpandedPlayPause(frameExpandedPlayPause.translate(origin))
        // Keep the existing last-valid rectangle API, without accepting it as a fresh layout.
        assertEquals(frameExpandedArtwork, elements.expandedArtwork)
        assertEquals(expectedFramedGeometry(1f),
            resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))

        elements.updateExpandedArtwork(frameExpandedArtwork.translate(origin))
        assertEquals(expectedFramedGeometry(1f).copy(text = newText),
            resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
    }

    @Test
    fun returningToAnEarlierViewportDoesNotReviveItsOldMeasurementGeneration() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)
        resolveDefaultPlayerMorphGeometry(1f, endpoints, elements)

        updateFrame(endpoints, Offset(20f, 10f))
        elements.updateExpandedArtwork(frameExpandedArtwork.translate(20f, 10f))
        updateFrame(endpoints, Offset.Zero)
        elements.updateExpandedArtwork(frameExpandedArtwork.translate(0f, 20f))
        assertEquals(expectedFramedGeometry(1f),
            resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
    }

    @Test
    fun clearingExpandedMeasurementsAlsoClearsThePreviousVisualSnapshot() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)
        assertTrue(resolveDefaultPlayerMorphGeometry(1f, endpoints, elements) != null)

        elements.clearExpanded()
        assertNull(resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
        publishExpanded(elements, Offset.Zero)
        assertEquals(expectedFramedGeometry(1f),
            resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
    }

    @Test
    fun sameSizeShellSwitchRequiresFreshDestinationsEvenWhenTheyHaveNotMoved() {
        val endpoints = framedEndpoints(Offset.Zero)
        val elements = framedElements(endpoints)
        publishMini(elements, Offset.Zero)
        publishExpanded(elements, Offset.Zero)
        resolveDefaultPlayerMorphGeometry(0.5f, endpoints, elements)

        endpoints.updateLayoutFrame(frameSurface, Offset.Zero, IntSize(560, 900), 1f, 1f, "rail")
        val newSource = Offset(98f, 82f)
        endpoints.updateMini(frameMiniSurface.translate(newSource))
        publishMini(elements, newSource)
        elements.updateExpandedArtwork(frameExpandedArtwork)
        // Live reversals still use the previous complete set until *all* unchanged placeholders
        // have re-reported within the new boundary. No mixed source/destination set is promoted.
        for (progress in listOf(0.8f, 0.2f, 1f, 0f)) {
            assertEquals(expectedFramedGeometry(progress),
                resolveDefaultPlayerMorphGeometry(progress, endpoints, elements))
        }
        publishExpanded(elements, Offset.Zero)
        val progress = 0.5f
        assertEquals(DefaultPlayerMorphGeometry(
            interpolateMorphRect(frameMiniSurface.translate(newSource), frameSurface, progress),
            interpolateMorphRect(frameMiniArtwork.translate(newSource), frameExpandedArtwork, progress),
            interpolateMorphRect(frameMiniText.translate(newSource), frameExpandedText, progress),
            interpolateMorphRect(frameMiniPlayPause.translate(newSource), frameExpandedPlayPause, progress)
        ), resolveDefaultPlayerMorphGeometry(progress, endpoints, elements))
    }

    @Test
    fun sameViewportPlayerArrangementPromotesAllBalancedDestinationsTogetherInHostCoordinates() {
        val origin = Offset(48f, 24f)
        val surface = Rect(0f, 0f, 960f, 600f)
        val mini = DefaultPlayerMorphGeometry(
            surface = Rect(144f, 508f, 936f, 576f),
            artwork = Rect(152f, 516f, 204f, 568f),
            text = Rect(216f, 518f, 812f, 560f),
            playPause = Rect(880f, 518f, 928f, 566f)
        )
        val vertical = DefaultPlayerMorphGeometry(
            surface = surface,
            artwork = Rect(338f, 24f, 622f, 308f),
            text = Rect(24f, 332f, 936f, 396f),
            playPause = Rect(439f, 444f, 521f, 526f)
        )
        val balanced = DefaultPlayerMorphGeometry(
            surface = surface,
            artwork = Rect(36f, 116f, 404f, 484f),
            text = Rect(448f, 100f, 924f, 164f),
            playPause = Rect(653f, 380f, 735f, 462f)
        )
        val endpoints = PlayerEndpointBounds()
        val elements = framedElements(endpoints)
        fun frame(arrangement: String) = endpoints.updateLayoutFrame(
            surface.translate(origin), origin, IntSize(1100, 700), 1f, 1f, arrangement
        )
        fun publishSource() {
            endpoints.updateMini(mini.surface.translate(origin))
            elements.updateMiniSurface(mini.surface.translate(origin))
            elements.updateMiniArtwork(mini.artwork.translate(origin))
            elements.updateMiniText(mini.text.translate(origin))
            elements.updateMiniPlayPause(mini.playPause.translate(origin))
        }
        fun expected(destination: DefaultPlayerMorphGeometry, progress: Float) =
            DefaultPlayerMorphGeometry(
                interpolateMorphRect(mini.surface, destination.surface, progress),
                interpolateMorphRect(mini.artwork, destination.artwork, progress),
                interpolateMorphRect(mini.text, destination.text, progress),
                interpolateMorphRect(mini.playPause, destination.playPause, progress)
            )
        fun assertSnapshot(destination: DefaultPlayerMorphGeometry) {
            // Fractions go forward, backward, and to both endpoints while a replacement is pending.
            for (progress in listOf(0.8f, 0.2f, 1f, 0f, 0.6f)) {
                assertEquals(expected(destination, progress),
                    resolveDefaultPlayerMorphGeometry(progress, endpoints, elements))
            }
        }

        frame("vertical")
        publishSource()
        elements.updateExpandedArtwork(vertical.artwork.translate(origin))
        elements.updateExpandedText(vertical.text.translate(origin))
        elements.updateExpandedPlayPause(vertical.playPause.translate(origin))
        assertSnapshot(vertical)
        val initialGeneration = endpoints.layoutFrame!!.generation

        // Only the Default arrangement changes: the full host, origin, and mini source stay put.
        frame("balanced")
        assertTrue(endpoints.layoutFrame!!.generation > initialGeneration)
        publishSource()
        elements.updateExpandedArtwork(balanced.artwork.translate(origin))
        elements.updateExpandedText(balanced.text.translate(origin))
        // The old central play/pause cannot be combined with the new left/right destinations.
        assertSnapshot(vertical)
        elements.updateExpandedPlayPause(balanced.playPause.translate(origin))
        assertSnapshot(balanced)
        assertEquals(balanced, resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
        assertTrue(balanced.artwork.right < balanced.text.left)
        assertTrue(balanced.artwork.right < balanced.playPause.left)

        val balancedGeneration = endpoints.layoutFrame!!.generation
        frame("vertical")
        assertTrue(endpoints.layoutFrame!!.generation > balancedGeneration)
        publishSource()
        elements.updateExpandedArtwork(vertical.artwork.translate(origin))
        // Returning to an earlier arrangement still requires new reports from every destination.
        assertSnapshot(balanced)
        elements.updateExpandedText(vertical.text.translate(origin))
        assertSnapshot(balanced)
        elements.updateExpandedPlayPause(vertical.playPause.translate(origin))
        assertSnapshot(vertical)
    }

    @Test
    fun wideArrangementNeedsFreshSourceAndAllDestinationsBeforePromotingDuringReversal() {
        val origin = Offset(48f, 24f)
        val endpoints = framedEndpoints(origin)
        val elements = framedElements(endpoints)
        publishMini(elements, origin)
        publishExpanded(elements, origin)
        endpoints.updateMini(elements.miniSurface!!)
        val previous = resolveDefaultPlayerMorphGeometry(1f, endpoints, elements)!!
        val oldFrame = endpoints.layoutFrame!!
        endpoints.updateLayoutFrame(oldFrame.boundsInRoot, origin, oldFrame.rootSize,
            oldFrame.density, oldFrame.fontScale, DefaultPlayerArrangement.WideWaveform)
        val wideArt = Rect(24f, 40f, 184f, 200f)
        val wideText = Rect(208f, 40f, 384f, 100f)
        val widePlay = Rect(260f, 140f, 342f, 222f)
        elements.updateExpandedArtwork(wideArt.translate(origin))
        elements.updateExpandedText(wideText.translate(origin))
        assertEquals(previous, resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
        elements.updateExpandedPlayPause(widePlay.translate(origin))
        assertEquals(previous, resolveDefaultPlayerMorphGeometry(1f, endpoints, elements))
        publishMini(elements, origin)
        endpoints.updateMini(elements.miniSurface!!)
        val expanded = resolveDefaultPlayerMorphGeometry(1f, endpoints, elements)!!
        assertEquals(wideArt, expanded.artwork)
        assertEquals(wideText, expanded.text)
        assertEquals(widePlay, expanded.playPause)
        assertTrue(expanded.artwork.right < expanded.text.left)
        val mini = resolveDefaultPlayerMorphGeometry(0f, endpoints, elements)!!
        for (progress in listOf(0.8f, 0.2f, 1f, 0f, 0.6f)) {
            val geometry = resolveDefaultPlayerMorphGeometry(progress, endpoints, elements)!!
            assertEquals(interpolateMorphRect(mini.artwork, wideArt, progress), geometry.artwork)
            assertEquals(interpolateMorphRect(mini.text, wideText, progress), geometry.text)
            assertEquals(interpolateMorphRect(mini.playPause, widePlay, progress), geometry.playPause)
        }
        assertTrue(endpoints.layoutFrame!!.generation > oldFrame.generation)
    }

    @Test
    fun firstDragUsesTheMeasuredSurfaceTravelBeforeDestinationsExist() {
        val origin = Offset(80f, 30f)
        val endpoints = framedEndpoints(origin)
        val elements = framedElements(endpoints)
        publishMini(elements, origin)

        assertNull(resolveDefaultPlayerMorphGeometry(0f, endpoints, elements))
        assertEquals(700f, defaultMorphTravelDistance(endpoints, elements), 0f)
    }

    private fun framedEndpoints(origin: Offset) = PlayerEndpointBounds().apply {
        updateFrame(this, origin)
        updateMini(frameMiniSurface.translate(origin))
    }

    private fun updateFrame(
        endpoints: PlayerEndpointBounds,
        origin: Offset,
        surface: Rect = frameSurface
    ) {
        endpoints.updateLayoutFrame(
            surface.translate(origin), origin,
            IntSize(surface.width.toInt() + 200, surface.height.toInt() + 100),
            density = 1f, fontScale = 1f
        )
    }

    private fun framedElements(endpoints: PlayerEndpointBounds) =
        DefaultPlayerMorphBounds { endpoints.layoutFrame }

    private fun publishMini(elements: DefaultPlayerMorphBounds, origin: Offset) {
        elements.updateMiniSurface(frameMiniSurface.translate(origin))
        elements.updateMiniArtwork(frameMiniArtwork.translate(origin))
        elements.updateMiniText(frameMiniText.translate(origin))
        elements.updateMiniPlayPause(frameMiniPlayPause.translate(origin))
    }

    private fun publishExpanded(elements: DefaultPlayerMorphBounds, origin: Offset) {
        elements.updateExpandedArtwork(frameExpandedArtwork.translate(origin))
        elements.updateExpandedText(frameExpandedText.translate(origin))
        elements.updateExpandedPlayPause(frameExpandedPlayPause.translate(origin))
    }

    private fun expectedFramedGeometry(progress: Float) = DefaultPlayerMorphGeometry(
        interpolateMorphRect(frameMiniSurface, frameSurface, progress),
        interpolateMorphRect(frameMiniArtwork, frameExpandedArtwork, progress),
        interpolateMorphRect(frameMiniText, frameExpandedText, progress),
        interpolateMorphRect(frameMiniPlayPause, frameExpandedPlayPause, progress)
    )

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
        val frameSurface = Rect(0f, 0f, 360f, 800f)
        val frameMiniSurface = Rect(12f, 700f, 348f, 768f)
        val frameMiniArtwork = Rect(20f, 708f, 72f, 760f)
        val frameMiniText = Rect(84f, 710f, 240f, 752f)
        val frameMiniPlayPause = Rect(292f, 710f, 340f, 758f)
        val frameExpandedArtwork = Rect(46f, 24f, 314f, 292f)
        val frameExpandedText = Rect(16f, 320f, 344f, 384f)
        val frameExpandedPlayPause = Rect(139f, 680f, 221f, 762f)
        val collapsedRect = Rect(0f, 20f, 280f, 100f)
        val expandedRect = Rect(100f, 200f, 240f, 740f)
    }
}
