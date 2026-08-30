package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphDistanceThresholdFraction
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphVelocityThresholdPxPerSecond
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.selectPlayerMorphTargetForThreshold
import io.github.rsgarrido.sazanami.ui.player.classicwheel.PlayerMorphRenderer
import io.github.rsgarrido.sazanami.ui.player.classicwheel.playerMorphRendererFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroRackPlayerMorphGeometryTest {
    @Test fun `routing keeps only completed themes on morph renderers`() {
        assertEquals(PlayerMorphRenderer.DEFAULT, playerMorphRendererFor(PlayerTheme.DEFAULT))
        assertEquals(PlayerMorphRenderer.CLASSIC_WHEEL, playerMorphRendererFor(PlayerTheme.CLASSIC_WHEEL))
        assertEquals(PlayerMorphRenderer.RETRO_RACK, playerMorphRendererFor(PlayerTheme.RETRO_RACK))
        assertEquals(PlayerMorphRenderer.ENDPOINT, playerMorphRendererFor(PlayerTheme.POCKET_FLIP))
        assertEquals(PlayerMorphRenderer.ENDPOINT, playerMorphRendererFor(PlayerTheme.POCKET_CASSETTE))
    }

    @Test fun `shell reaches both endpoints and reverses through valid geometry`() {
        val bounds = endpoints()
        assertEquals(Rect(12f, 700f, 388f, 776f), resolveRetroRackMorphGeometry(0f, bounds)!!.shell)
        assertEquals(Rect(6f, 350f, 394f, 788f), resolveRetroRackMorphGeometry(.5f, bounds)!!.shell)
        assertEquals(Rect(0f, 0f, 400f, 800f), resolveRetroRackMorphGeometry(1f, bounds)!!.shell)
        assertEquals(resolveRetroRackMorphGeometry(.35f, bounds), resolveRetroRackMorphGeometry(.35f, bounds))
    }

    @Test fun `missing and stale shell measurements fail safely`() {
        val bounds = endpoints()
        bounds.markMiniStale()
        assertNull(resolveRetroRackMorphGeometry(.5f, bounds))
        assertEquals(RetroRackMorphSpec.minimumDragRangePx, retroRackMorphTravelDistance(bounds))
    }

    @Test fun `shared elements reach every measured endpoint`() {
        val b = sharedBounds()
        val start = resolveRetroRackSharedGeometry(0f, b)!!
        val end = resolveRetroRackSharedGeometry(1f, b)!!
        assertEquals(Rect(16f, 710f, 58f, 752f), start.artwork)
        assertEquals(Rect(10f, 40f, 86f, 116f), end.artwork)
        assertEquals(Rect(66f, 710f, 250f, 728f), start.title)
        assertEquals(Rect(94f, 42f, 360f, 58f), end.title)
        assertEquals(Rect(66f, 730f, 250f, 746f), start.artist)
        assertEquals(Rect(94f, 62f, 360f, 76f), end.artist)
        assertEquals(Rect(66f, 748f, 250f, 750f), start.progress)
        assertEquals(Rect(10f, 122f, 390f, 142f), end.progress)
        assertEquals(Rect(330f, 706f, 378f, 754f), start.play)
        assertEquals(Rect(174f, 148f, 222f, 182f), end.play)
        assertTrue(resolveRetroRackSharedGeometry(.5f, b)!!.play.width > 0f)
    }

    @Test fun `invalid measurements retain last valid shared anchors`() {
        val b = sharedBounds()
        val valid = b.miniArtwork
        b.updateMiniArtwork(Rect.Zero)
        b.updateExpandedTitle(Rect(Float.NaN, 0f, 1f, 1f))
        assertEquals(valid, b.miniArtwork)
        assertEquals(Rect(94f, 42f, 360f, 58f), b.expandedTitle)
    }

    @Test fun `shared ownership is deterministic at endpoints`() {
        assertEquals(RetroRackSharedOwner.MINI, retroRackSharedOwner(0f, true))
        assertEquals(RetroRackSharedOwner.MINI, retroRackSharedOwner(.5f, false))
        assertEquals(RetroRackSharedOwner.TRANSITION, retroRackSharedOwner(.5f, true))
        assertEquals(RetroRackSharedOwner.EXPANDED, retroRackSharedOwner(1f, true))
    }

    @Test fun `expanded sections and expensive work use stable thresholds`() {
        assertEquals(0f, retroRackDeckReveal(0f))
        assertEquals(1f, retroRackDeckReveal(1f))
        assertEquals(0f, retroRackSpectrumReveal(0f))
        assertEquals(1f, retroRackSpectrumReveal(1f))
        assertEquals(0f, retroRackQueueReveal(0f))
        assertEquals(1f, retroRackQueueReveal(1f))
        assertFalse(shouldRunRetroRackExpandedWork(.1f))
        assertTrue(shouldRunRetroRackExpandedWork(1f))
        assertFalse(retroRackExpandedInputEnabled(.9f))
        assertTrue(retroRackExpandedInputEnabled(1f))
    }

    @Test fun `explicit non interactive rack regions start collapse`() {
        assertTrue(retroRackCanStartCollapse(RetroRackGestureRegion.SAFE_HEADER))
        assertTrue(retroRackCanStartCollapse(RetroRackGestureRegion.ARTWORK))
        assertTrue(retroRackCanStartCollapse(RetroRackGestureRegion.METADATA))
        assertTrue(retroRackCanStartCollapse(RetroRackGestureRegion.SPECTRUM_BODY))
        assertFalse(retroRackCanStartCollapse(RetroRackGestureRegion.BUTTON))
        assertFalse(retroRackCanStartCollapse(RetroRackGestureRegion.SEEK))
        assertFalse(retroRackCanStartCollapse(RetroRackGestureRegion.QUEUE))
        assertFalse(retroRackCanStartCollapse(RetroRackGestureRegion.SPECTRUM_CONTROL))
    }

    @Test fun `rack collapse thresholds are modestly more sensitive without changing shared defaults`() {
        assertEquals(.26f, PlayerMorphDistanceThresholdFraction)
        assertEquals(1_400f, PlayerMorphVelocityThresholdPxPerSecond)
        assertEquals(.22f, RetroRackMorphSpec.collapseDistanceThresholdFraction)
        assertEquals(1_150f, RetroRackMorphSpec.collapseVelocityThresholdPxPerSecond)
        assertEquals(220f, retroRackDistanceThreshold(1_000f))
        assertEquals(
            PlayerPresentation.Expanded,
            selectPlayerMorphTargetForThreshold(
                PlayerPresentation.Expanded, 100f, 220f, 700f,
                RetroRackMorphSpec.collapseVelocityThresholdPxPerSecond
            )
        )
        assertEquals(
            PlayerPresentation.Collapsed,
            selectPlayerMorphTargetForThreshold(
                PlayerPresentation.Expanded, 10f, 220f, 1_200f,
                RetroRackMorphSpec.collapseVelocityThresholdPxPerSecond
            )
        )
        assertEquals(
            PlayerPresentation.Expanded,
            selectPlayerMorphTargetForThreshold(
                PlayerPresentation.Expanded, 10f, 260f, 1_200f
            )
        )
    }

    private fun endpoints() = PlayerEndpointBounds().also {
        it.updateMini(Rect(12f, 700f, 388f, 776f))
        it.updateExpanded(Rect(0f, 0f, 400f, 800f))
    }

    private fun sharedBounds() = RetroRackMorphBounds().also {
        it.updateMiniArtwork(Rect(16f, 710f, 58f, 752f)); it.updateExpandedArtwork(Rect(10f, 40f, 86f, 116f))
        it.updateMiniTitle(Rect(66f, 710f, 250f, 728f)); it.updateExpandedTitle(Rect(94f, 42f, 360f, 58f))
        it.updateMiniArtist(Rect(66f, 730f, 250f, 746f)); it.updateExpandedArtist(Rect(94f, 62f, 360f, 76f))
        it.updateMiniProgress(Rect(66f, 748f, 250f, 750f)); it.updateExpandedProgress(Rect(10f, 122f, 390f, 142f))
        it.updateMiniPlay(Rect(330f, 706f, 378f, 754f)); it.updateExpandedPlay(Rect(174f, 148f, 222f, 182f))
    }
}
