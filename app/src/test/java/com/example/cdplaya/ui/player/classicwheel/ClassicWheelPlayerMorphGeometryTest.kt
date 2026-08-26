package com.example.cdplaya.ui.player.classicwheel

import androidx.compose.ui.geometry.Rect
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.ui.player.PlayerEndpointBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicWheelPlayerMorphGeometryTest {
    @Test fun `classic wheel selects its renderer while incomplete retro themes retain endpoints`() {
        assertEquals(PlayerMorphRenderer.DEFAULT, playerMorphRendererFor(PlayerTheme.DEFAULT))
        assertEquals(PlayerMorphRenderer.CLASSIC_WHEEL, playerMorphRendererFor(PlayerTheme.CLASSIC_WHEEL))
        assertEquals(PlayerMorphRenderer.RETRO_RACK, playerMorphRendererFor(PlayerTheme.RETRO_RACK))
        assertEquals(PlayerMorphRenderer.ENDPOINT, playerMorphRendererFor(PlayerTheme.POCKET_FLIP))
        assertEquals(PlayerMorphRenderer.ENDPOINT, playerMorphRendererFor(PlayerTheme.POCKET_CASSETTE))
    }

    @Test fun `shell reaches measured endpoints and interpolates validly`() {
        val bounds = bounds()
        val start = resolveClassicWheelMorphGeometry(0f, bounds)!!.shell
        val middle = resolveClassicWheelMorphGeometry(.5f, bounds)!!.shell
        val end = resolveClassicWheelMorphGeometry(1f, bounds)!!.shell
        assertEquals(Rect(10f, 700f, 390f, 770f), start)
        assertEquals(Rect(5f, 350f, 395f, 785f), middle)
        assertEquals(Rect(0f, 0f, 400f, 800f), end)
        assertTrue(middle.width > 0f && middle.height > 0f)
    }

    @Test fun `missing mini bounds fails safely`() {
        val bounds = PlayerEndpointBounds()
        bounds.updateExpanded(Rect(0f, 0f, 400f, 800f))
        assertNull(resolveClassicWheelMorphGeometry(.5f, bounds))
        assertEquals(ClassicWheelMorphSpec.MinimumDragRangePx, classicWheelMorphTravelDistance(bounds))
    }

    @Test fun `reveal and control ownership policies have stable endpoints`() {
        assertEquals(0f, classicWheelWheelReveal(0f))
        assertEquals(1f, classicWheelWheelReveal(1f))
        assertEquals(0f, classicWheelScreenReveal(0f))
        assertEquals(1f, classicWheelScreenReveal(1f))
        assertFalse(classicWheelExpandedControlsActive(.5f))
        assertTrue(classicWheelExpandedControlsActive(1f))
        assertEquals(1f, classicWheelMiniChromeAlpha(0f))
        assertEquals(0f, classicWheelMiniChromeAlpha(1f))
    }

    @Test fun `play pause visual ownership hands off before the expanded endpoint`() {
        val collapsed = classicWheelPlayPauseVisualOwnership(0f)
        val middle = classicWheelPlayPauseVisualOwnership(.5f)
        val handoff = classicWheelPlayPauseVisualOwnership(.91f)
        val expanded = classicWheelPlayPauseVisualOwnership(1f)

        assertEquals(1f, collapsed.sharedAlpha)
        assertEquals(0f, collapsed.expandedAlpha)
        assertEquals(1f, middle.sharedAlpha)
        assertEquals(0f, middle.expandedAlpha)
        assertTrue(handoff.sharedAlpha > 0f && handoff.sharedAlpha < 1f)
        assertTrue(handoff.expandedAlpha > 0f && handoff.expandedAlpha < 1f)
        assertEquals(1f, handoff.sharedAlpha + handoff.expandedAlpha, .0001f)
        assertEquals(0f, expanded.sharedAlpha)
        assertEquals(1f, expanded.expandedAlpha)
    }

    @Test fun `play pause ownership is deterministic across a full round trip`() {
        val firstExpanded = classicWheelPlayPauseVisualOwnership(1f)
        val collapsed = classicWheelPlayPauseVisualOwnership(0f)
        val secondExpanded = classicWheelPlayPauseVisualOwnership(1f)

        assertEquals(1f, collapsed.sharedAlpha)
        assertEquals(firstExpanded, secondExpanded)
    }

    @Test fun `shared artwork and metadata reach their measured anchors`() {
        val elements = ClassicWheelMorphBounds().also {
            it.updateMiniArtwork(Rect(16f, 710f, 60f, 754f))
            it.updateExpandedArtwork(Rect(30f, 120f, 170f, 260f))
            it.updateMiniTitle(Rect(70f, 710f, 240f, 730f))
            it.updateExpandedTitle(Rect(185f, 120f, 360f, 170f))
            it.updateMiniArtist(Rect(70f, 734f, 240f, 750f))
            it.updateExpandedArtist(Rect(185f, 176f, 360f, 205f))
            it.updateMiniPlayPause(Rect(330f, 705f, 378f, 753f))
            it.updateExpandedPlayPause(Rect(170f, 600f, 250f, 670f))
        }
        assertEquals(Rect(16f, 710f, 60f, 754f), resolveClassicWheelSharedGeometry(0f, elements)!!.artwork)
        assertEquals(Rect(30f, 120f, 170f, 260f), resolveClassicWheelSharedGeometry(1f, elements)!!.artwork)
        assertEquals(Rect(70f, 710f, 240f, 730f), resolveClassicWheelSharedGeometry(0f, elements)!!.title)
        assertEquals(Rect(185f, 176f, 360f, 205f), resolveClassicWheelSharedGeometry(1f, elements)!!.artist)
        assertEquals(Rect(330f, 705f, 378f, 753f), resolveClassicWheelSharedGeometry(0f, elements)!!.playPause)
        assertEquals(Rect(170f, 600f, 250f, 670f), resolveClassicWheelSharedGeometry(1f, elements)!!.playPause)
    }

    @Test fun `invalid measurement preserves the last valid anchor`() {
        val elements = ClassicWheelMorphBounds()
        val valid = Rect(10f, 10f, 20f, 20f)
        elements.updateMiniArtwork(valid)
        elements.updateMiniArtwork(Rect(0f, 0f, 0f, 0f))
        assertEquals(valid, elements.miniArtwork)
    }

    private fun bounds(): PlayerEndpointBounds = PlayerEndpointBounds().also {
        it.updateMini(Rect(10f, 700f, 390f, 770f))
        it.updateExpanded(Rect(0f, 0f, 400f, 800f))
    }
}
