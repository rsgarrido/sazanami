package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketAdvancePlayerMorphGeometryTest {
    @Test
    fun firstPairRequiresMeasuredMiniAndEveryVisibleAnchor() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        assertNull(bounds.resolveGeometry(0.5f, endpoints))
        publishMini(bounds)
        publishExpanded(bounds, includePlay = false)
        assertNull(bounds.resolveGeometry(0.5f, endpoints))
        bounds.updateExpandedPlayPause(expanded.playPause)
        assertEquals(expected(0.5f), bounds.resolveGeometry(0.5f, endpoints))
    }

    @Test
    fun genericEndpointDoesNotSubstituteForVisibleDeviceFootprint() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        assertEquals(mini.shell, bounds.resolveGeometry(0f, endpoints)?.shell)
        assertEquals(expanded.shell, bounds.resolveGeometry(1f, endpoints)?.shell)
        assertTrue(expanded.shell != host)
        // The generic mini includes layout padding, while the device surface excludes it.
        assertEquals(mini.shell.top - expanded.shell.top, bounds.travelDistance(endpoints), 0f)
    }

    @Test
    fun displayBackingReadinessPreventsMetadataTravellingWithoutItsLcd() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        bounds.updateExpandedDisplay(Rect.Zero)
        assertNull(bounds.resolveGeometry(0.1f, endpoints))
        assertFalse(pocketAdvanceMorphVisualState(0.1f, false).hideMiniVisuals)
        bounds.updateExpandedDisplay(expanded.display)
        assertEquals(interpolatePocketAdvanceRect(mini.display, expanded.display, 0.1f),
            bounds.resolveGeometry(0.1f, endpoints)?.display)
        assertTrue(pocketAdvanceMorphVisualState(0.1f, true).hideMiniVisuals)
    }

    @Test
    fun translatedHostsConvertEveryAnchorOnceBeforeInterpolation() {
        for (origin in listOf(Offset(72f, 34f), Offset(-24f, 12f))) {
            val endpoints = endpoints(origin)
            val bounds = bounds(endpoints)
            publishMini(bounds, origin)
            publishExpanded(bounds, origin)
            for (progress in listOf(0f, 0.25f, 0.8f, 1f)) {
                assertEquals(expected(progress), bounds.resolveGeometry(progress, endpoints))
            }
            assertEquals(mini.shell.translate(origin), bounds.miniSurface)
        }
    }

    @Test
    fun incompleteResizeRetainsWholePairAndRespondsToReversal() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        assertNotNull(bounds.resolveGeometry(0.4f, endpoints))

        val origin = Offset(40f, 20f)
        frame(endpoints, origin, boundary = "resized")
        endpoints.updateMini(mini.shell.translate(origin))
        publishMini(bounds, origin)
        bounds.updateExpandedSurface(expanded.shell.translate(origin))
        bounds.updateExpandedArtwork(expanded.artwork.translate(origin))
        for (progress in listOf(0.8f, 0.2f, 1f, 0f, 0.6f)) {
            assertEquals(expected(progress), bounds.resolveGeometry(progress, endpoints))
        }
        val movedTitle = expanded.title.translate(0f, 16f)
        publishExpanded(bounds, origin)
        bounds.updateExpandedTitle(movedTitle.translate(origin))
        assertEquals(expanded.copy(title = movedTitle), bounds.resolveGeometry(1f, endpoints))
    }

    @Test
    fun equalViewportWithNewFrameNeedsFreshUnmovedAnchors() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        // No complete pair was previously resolved, so there is no permissible retained snapshot.
        val initialGeneration = endpoints.layoutFrame!!.generation
        frame(endpoints, Offset.Zero, boundary = "new_arrangement")
        assertTrue(endpoints.layoutFrame!!.generation > initialGeneration)
        publishExpanded(bounds)
        assertNull(bounds.resolveGeometry(0.5f, endpoints))
        endpoints.updateMini(mini.shell)
        assertNull(bounds.resolveGeometry(0.5f, endpoints))
        publishMini(bounds)
        assertEquals(expected(0.5f), bounds.resolveGeometry(0.5f, endpoints))
    }

    @Test
    fun returningToEarlierViewportCannotReviveItsOldReports() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        frame(endpoints, Offset(20f, 10f))
        publishExpanded(bounds, Offset(20f, 10f))
        frame(endpoints, Offset.Zero)
        endpoints.updateMini(mini.shell)
        publishMini(bounds)
        assertNull(bounds.resolveGeometry(0.5f, endpoints))
        publishExpanded(bounds)
        assertEquals(expected(0.5f), bounds.resolveGeometry(0.5f, endpoints))
    }

    @Test
    fun identityChangeInvalidatesCacheEvenWhenItReturnsToAdvance() {
        val endpoints = endpoints()
        var identity: PlayerPresentationIdentity = PlayerPresentationIdentity.PocketAdvance
        val bounds = PocketAdvancePlayerMorphBounds({ endpoints.layoutFrame }, { identity })
        publishMini(bounds)
        publishExpanded(bounds)
        assertNotNull(bounds.resolveGeometry(0.4f, endpoints))

        identity = PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL)
        assertNull(bounds.resolveGeometry(0.4f, endpoints))
        // Late outgoing callbacks are not allowed to qualify measurements for another identity.
        publishMini(bounds)
        publishExpanded(bounds)
        assertNull(bounds.resolveGeometry(1f, endpoints))
        identity = PlayerPresentationIdentity.PocketAdvance
        assertNull(bounds.resolveGeometry(0.4f, endpoints))
        publishMini(bounds)
        assertNull(bounds.resolveGeometry(0.4f, endpoints))
        publishExpanded(bounds)
        assertEquals(expected(0.4f), bounds.resolveGeometry(0.4f, endpoints))
    }

    @Test
    fun explicitLifetimeClearInvalidatesBothEndpointsAndSnapshot() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        assertNotNull(bounds.resolveGeometry(1f, endpoints))
        bounds.clear()
        assertNull(bounds.resolveGeometry(1f, endpoints))
        publishExpanded(bounds)
        assertNull(bounds.resolveGeometry(1f, endpoints))
        publishMini(bounds)
        assertEquals(expanded, bounds.resolveGeometry(1f, endpoints))
    }

    @Test
    fun lyricsPresentationTransformCannotReplaceLogicalAnchors() {
        val endpoints = endpoints()
        var canMeasure = true
        val bounds = PocketAdvancePlayerMorphBounds(
            currentLayoutFrame = { endpoints.layoutFrame },
            canMeasure = { canMeasure }
        )
        publishMini(bounds)
        publishExpanded(bounds)
        assertEquals(expanded, bounds.resolveGeometry(1f, endpoints))
        canMeasure = false
        // Reports during the parent lyrics scale/translation would be in the wrong visual frame.
        publishExpanded(bounds, Offset(120f, 80f))
        assertEquals(expanded, bounds.resolveGeometry(1f, endpoints))
        assertEquals(expected(0.3f), bounds.resolveGeometry(0.3f, endpoints))
        canMeasure = true
        publishExpanded(bounds)
        assertEquals(expanded, bounds.resolveGeometry(1f, endpoints))
    }

    @Test
    fun invalidFreshAnchorCannotPromoteMixedReplacement() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        bounds.resolveGeometry(1f, endpoints)
        frame(endpoints, Offset.Zero, boundary = "new")
        endpoints.updateMini(mini.shell)
        publishMini(bounds)
        publishExpanded(bounds)
        bounds.updateExpandedArtwork(Rect(Float.NaN, 0f, 32f, 32f))
        bounds.updateExpandedTitle(expanded.title.translate(0f, 16f))
        assertEquals(expanded, bounds.resolveGeometry(1f, endpoints))
        bounds.updateExpandedArtwork(expanded.artwork)
        assertEquals(expanded.copy(title = expanded.title.translate(0f, 16f)),
            bounds.resolveGeometry(1f, endpoints))
    }

    @Test
    fun firstDragUsesCurrentSourceWithoutRequiringDestination() {
        val endpoints = endpoints(Offset(60f, 40f))
        val bounds = bounds(endpoints)
        publishMini(bounds, Offset(60f, 40f))
        assertNull(bounds.resolveGeometry(0f, endpoints))
        assertEquals(mini.shell.top, bounds.travelDistance(endpoints), 0f)
        endpoints.markMiniStale()
        assertEquals(PocketAdvanceMinimumMorphTravelPx, bounds.travelDistance(endpoints), 0f)
    }

    @Test
    fun ownershipWaitsForReadinessThenPersistsThroughExpandedEndpoint() {
        for (p in listOf(0f, 0.4f, 0.9f)) {
            val missing = pocketAdvanceMorphVisualState(p, false)
            assertFalse(missing.morphOwnsSharedVisuals)
            assertFalse(missing.inputEnabled)
            assertFalse(missing.hideMiniVisuals)
            assertEquals(0f, missing.hardwareAlpha, 0f)
        }
        assertFalse(pocketAdvanceMorphVisualState(0f, true).morphOwnsSharedVisuals)
        assertTrue(pocketAdvanceMorphVisualState(0.01f, true).morphOwnsSharedVisuals)
        assertTrue(pocketAdvanceMorphVisualState(1f, true).morphOwnsSharedVisuals)
        assertFalse(pocketAdvanceMorphVisualState(0.9f, true).inputEnabled)
        assertTrue(pocketAdvanceMorphVisualState(1f, true).inputEnabled)
    }

    @Test
    fun restoredExpandedEndpointOwnsPixelsUntilCompletePairCanTakeOver() {
        val restored = pocketAdvanceMorphVisualState(1f, false)
        assertTrue(restored.endpointOwnsVisuals)
        assertTrue(restored.hideMiniVisuals)
        assertFalse(restored.morphOwnsSharedVisuals)
        assertTrue(restored.inputEnabled)
        assertEquals(1f, restored.shellAlpha, 0f)
        assertEquals(1f, restored.bezelAlpha, 0f)
        assertEquals(1f, restored.hardwareAlpha, 0f)
        assertEquals(1f, restored.displayDetailsAlpha, 0f)

        val measured = pocketAdvanceMorphVisualState(1f, true)
        assertFalse(measured.endpointOwnsVisuals)
        assertTrue(measured.hideMiniVisuals)
        assertTrue(measured.morphOwnsSharedVisuals)
        // At no point do both native endpoint and persistent overlay claim the shared pixels.
        assertTrue(restored.endpointOwnsVisuals != restored.morphOwnsSharedVisuals)
        assertTrue(measured.endpointOwnsVisuals != measured.morphOwnsSharedVisuals)
    }

    @Test
    fun choreographyUsesOnlySharedProgressAndNaturallyReverses() {
        val opening = pocketAdvanceMorphVisualState(0.76f, true)
        val reversing = pocketAdvanceMorphVisualState(0.24f, true)
        assertTrue(reversing.bezelAlpha < opening.bezelAlpha)
        assertTrue(reversing.hardwareAlpha < opening.hardwareAlpha)
        assertTrue(reversing.displayDetailsAlpha < opening.displayDetailsAlpha)
        assertFalse(reversing.collapseGestureEnabled)
        assertEquals(mini, expected(-1f))
        assertEquals(expanded, expected(2f))
        assertEquals(mini, expected(Float.NaN))
    }

    @Test
    fun sourceChromeMatchesNativeMiniPixelsThenYieldsBeforeHardwareReveal() {
        val source = pocketAdvanceMiniChromeGeometry(mini, 0f, 1f)!!
        assertEquals(Rect(188f, 530f, 212f, 554f), source.cross)
        // Native Canvas draws the 3dp line centered on its own top edge, after the artist.
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        assertEquals(Rect(272f, 555f, 900f, 558f), bounds.resolveGeometry(0f, endpoints)?.progressTrack)
        assertEquals(1f, source.alpha, 0f)
        val moving = pocketAdvanceMiniChromeGeometry(expected(0.09f), 0.09f, 1f)!!
        assertEquals(0.5f, moving.alpha, 0.0001f)
        assertTrue(moving.cross.top < source.cross.top)
        assertEquals(0f, pocketAdvanceMiniChromeGeometry(expected(0.18f), 0.18f, 1f)!!.alpha, 0f)
        assertEquals(0f, pocketAdvanceMorphVisualState(0.18f, true).hardwareAlpha, 0f)
        assertEquals(0f, pocketAdvanceMiniChromeGeometry(expanded, 1f, 1f)!!.alpha, 0f)
    }

    @Test
    fun sourceChromeNeverBorrowsMissingGeometryAndUsesDensityForTinyDetails() {
        assertNull(pocketAdvanceMiniChromeGeometry(null, 0.1f, 2f))
        assertNull(pocketAdvanceMiniChromeGeometry(mini, 0.1f, Float.NaN))
        val scaledDetails = pocketAdvanceMiniChromeGeometry(mini, 0f, 2f)!!
        assertEquals(48f, scaledDetails.cross.width, 0f)
    }

    @Test
    fun progressRequiresBothVisibleAnchorsBeforeSharedOwnership() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        bounds.updateMiniProgress(Rect.Zero)
        bounds.updateExpandedProgress(Rect.Zero)
        assertNull(bounds.resolveGeometry(.5f, endpoints))
        assertFalse(pocketAdvanceMorphVisualState(.5f, false).morphOwnsSharedVisuals)
        bounds.updateMiniProgress(mini.progressTrack)
        assertNull(bounds.resolveGeometry(.5f, endpoints))
        bounds.updateExpandedProgress(expanded.progressTrack)
        for (p in listOf(0f, .1f, .5f, 1f, .75f, .1f, 0f)) {
            assertEquals(expected(p), bounds.resolveGeometry(p, endpoints))
            assertEquals(p > 0f, pocketAdvanceMorphVisualState(p, true).morphOwnsSharedVisuals)
        }
    }

    @Test
    fun progressNeverMixesFreshArtworkWithAStaleTrack() {
        val endpoints = endpoints()
        val bounds = bounds(endpoints)
        publishMini(bounds)
        publishExpanded(bounds)
        assertEquals(expanded, bounds.resolveGeometry(1f, endpoints))
        frame(endpoints, Offset.Zero, boundary = "new-progress-layout")
        endpoints.updateMini(mini.shell)
        publishMini(bounds)
        // All expanded reports except progress are fresh. Retain the entire old pair.
        bounds.updateExpandedSurface(expanded.shell)
        bounds.updateExpandedDisplay(expanded.display)
        bounds.updateExpandedArtwork(expanded.artwork.translate(12f, 0f))
        bounds.updateExpandedTitle(expanded.title)
        bounds.updateExpandedArtist(expanded.artist)
        bounds.updateExpandedPlayPause(expanded.playPause)
        assertEquals(expected(.4f), bounds.resolveGeometry(.4f, endpoints))
        bounds.updateExpandedProgress(Rect(Float.NaN, 0f, 10f, 8f))
        assertEquals(expected(.2f), bounds.resolveGeometry(.2f, endpoints))
        bounds.updateExpandedProgress(expanded.progressTrack.translate(0f, 8f))
        assertEquals(expanded.copy(artwork = expanded.artwork.translate(12f, 0f),
            progressTrack = expanded.progressTrack.translate(0f, 8f)), bounds.resolveGeometry(1f, endpoints))
    }

    @Test
    fun lyricsRequiresIntentionalUpwardTravelOrAFlingAndRejectsInvalidInputs() {
        assertTrue(shouldOpenPocketAdvanceLyrics(-64f, 0f, 1f))
        assertTrue(shouldOpenPocketAdvanceLyrics(-48f, -1500f, 2f))
        assertFalse(shouldOpenPocketAdvanceLyrics(-63f, 0f, 1f))
        assertFalse(shouldOpenPocketAdvanceLyrics(-23f, -2000f, 1f))
        assertFalse(shouldOpenPocketAdvanceLyrics(100f, -2000f, 1f))
        assertFalse(shouldOpenPocketAdvanceLyrics(-64f, 0f, 2f))
        assertFalse(shouldOpenPocketAdvanceLyrics(Float.NaN, 0f, 1f))
        assertFalse(shouldOpenPocketAdvanceLyrics(-64f, 0f, 0f))
    }

    private fun endpoints(origin: Offset = Offset.Zero) = PlayerEndpointBounds().apply {
        frame(this, origin)
        updateMini(Companion.mini.shell.translate(origin).inflate(4f))
    }

    private fun frame(
        endpoints: PlayerEndpointBounds,
        origin: Offset,
        boundary: String = "advance"
    ) = endpoints.updateLayoutFrame(
        host.translate(origin), origin, IntSize(1200, 800), 1f, 1f, boundary
    )

    private fun bounds(endpoints: PlayerEndpointBounds) =
        PocketAdvancePlayerMorphBounds({ endpoints.layoutFrame })

    private fun publishMini(bounds: PocketAdvancePlayerMorphBounds, origin: Offset = Offset.Zero) {
        bounds.updateMiniSurface(mini.shell.translate(origin))
        bounds.updateMiniDisplay(mini.display.translate(origin))
        bounds.updateMiniArtwork(mini.artwork.translate(origin))
        bounds.updateMiniTitle(mini.title.translate(origin))
        bounds.updateMiniArtist(mini.artist.translate(origin))
        bounds.updateMiniPlayPause(mini.playPause.translate(origin))
        bounds.updateMiniProgress(mini.progressTrack.translate(origin))
    }

    private fun publishExpanded(
        bounds: PocketAdvancePlayerMorphBounds,
        origin: Offset = Offset.Zero,
        includePlay: Boolean = true
    ) {
        bounds.updateExpandedSurface(expanded.shell.translate(origin))
        bounds.updateExpandedDisplay(expanded.display.translate(origin))
        bounds.updateExpandedArtwork(expanded.artwork.translate(origin))
        bounds.updateExpandedTitle(expanded.title.translate(origin))
        bounds.updateExpandedArtist(expanded.artist.translate(origin))
        if (includePlay) bounds.updateExpandedPlayPause(expanded.playPause.translate(origin))
        bounds.updateExpandedProgress(expanded.progressTrack.translate(origin))
    }

    private fun expected(p: Float) = PocketAdvanceMorphGeometry(
        interpolatePocketAdvanceRect(mini.shell, expanded.shell, p),
        interpolatePocketAdvanceRect(mini.artwork, expanded.artwork, p),
        interpolatePocketAdvanceRect(mini.title, expanded.title, p),
        interpolatePocketAdvanceRect(mini.artist, expanded.artist, p),
        interpolatePocketAdvanceRect(mini.playPause, expanded.playPause, p),
        interpolatePocketAdvanceRect(mini.display, expanded.display, p),
        interpolatePocketAdvanceRect(mini.progressTrack, expanded.progressTrack, p)
    )

    private companion object {
        val host = Rect(0f, 0f, 1000f, 600f)
        val mini = PocketAdvanceMorphGeometry(
            Rect(180f, 508f, 970f, 576f), Rect(228f, 524f, 264f, 560f),
            Rect(272f, 524.5f, 900f, 542.5f), Rect(272f, 542.5f, 900f, 556.5f),
            Rect(919f, 523f, 957f, 561f), Rect(222f, 518f, 906f, 566f), Rect(272f, 555f, 900f, 558f)
        )
        val expanded = PocketAdvanceMorphGeometry(
            Rect(40f, 88f, 960f, 512f), Rect(280f, 172f, 448f, 340f),
            Rect(472f, 182f, 730f, 220f), Rect(472f, 230f, 730f, 258f),
            Rect(818f, 232f, 870f, 284f), Rect(256f, 144f, 760f, 416f), Rect(272f, 380f, 744f, 388f)
        )
    }
}
