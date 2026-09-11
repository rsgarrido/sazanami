package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
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

    @Test fun `shell uses the visible mini card rather than its outer layout slot`() {
        val bounds = bounds()
        val elements = shellElementBounds()
        val start = resolveClassicWheelMorphGeometry(0f, bounds, elements)!!.shell
        val middle = resolveClassicWheelMorphGeometry(.5f, bounds, elements)!!.shell
        val end = resolveClassicWheelMorphGeometry(1f, bounds, elements)!!.shell

        assertEquals(Rect(12f, 706f, 388f, 774f), start)
        assertFalse(start == Rect(0f, 700f, 400f, 780f))
        assertEquals(Rect(6f, 353f, 394f, 787f), middle)
        assertEquals(Rect(0f, 0f, 400f, 800f), end)
        assertTrue(middle.left in end.left..start.left)
        assertTrue(middle.top in end.top..start.top)
        assertTrue(middle.width in start.width..end.width)
        assertTrue(middle.height in start.height..end.height)
        assertEquals(706f, classicWheelMorphTravelDistance(bounds, elements))
    }

    @Test fun `collapse and expansion resolve the same visible mini endpoint`() {
        val bounds = bounds()
        val elements = shellElementBounds()

        val collapseEndpoint = resolveClassicWheelMorphGeometry(0f, bounds, elements)!!.shell
        val expansionStart = resolveClassicWheelMorphGeometry(0f, bounds, elements)!!.shell

        assertEquals(Rect(12f, 706f, 388f, 774f), collapseEndpoint)
        assertEquals(collapseEndpoint, expansionStart)
    }

    @Test fun `missing or stale mini bounds fail safely`() {
        val bounds = PlayerEndpointBounds()
        bounds.updateExpanded(Rect(0f, 0f, 400f, 800f))
        val elements = shellElementBounds()
        assertNull(resolveClassicWheelMorphGeometry(.5f, bounds, elements))
        assertEquals(
            ClassicWheelMorphSpec.MinimumDragRangePx,
            classicWheelMorphTravelDistance(bounds, elements)
        )

        bounds.updateMini(Rect(0f, 700f, 400f, 780f))
        bounds.markMiniStale()
        assertNull(resolveClassicWheelMorphGeometry(.5f, bounds, elements))
        assertEquals(
            ClassicWheelMorphSpec.MinimumDragRangePx,
            classicWheelMorphTravelDistance(bounds, elements)
        )
    }

    @Test fun `missing visible mini card bounds fail safely`() {
        val bounds = bounds()
        val elements = ClassicWheelMorphBounds()

        assertNull(resolveClassicWheelMorphGeometry(.5f, bounds, elements))
        assertEquals(
            ClassicWheelMorphSpec.MinimumDragRangePx,
            classicWheelMorphTravelDistance(bounds, elements)
        )
    }

    @Test fun `mini queue chrome uses its measured static bounds`() {
        val elements = ClassicWheelMorphBounds().also {
            it.updateMiniQueue(Rect(286f, 719f, 328f, 761f))
        }

        assertEquals(
            Rect(286f, 719f, 328f, 761f),
            resolveClassicWheelMiniChromeGeometry(elements)!!.queue
        )
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

    @Test fun `shared content reaches its measured visual anchors`() {
        val elements = ClassicWheelMorphBounds().also {
            it.updateMiniArtwork(Rect(16f, 710f, 60f, 754f))
            it.updateExpandedArtwork(Rect(30f, 120f, 170f, 260f))
            it.updateMiniTitle(Rect(70f, 710f, 240f, 730f))
            it.updateExpandedTitle(Rect(185f, 120f, 360f, 170f))
            it.updateMiniArtist(Rect(70f, 734f, 240f, 750f))
            it.updateExpandedArtist(Rect(185f, 176f, 360f, 205f))
            it.updateMiniPlayPause(Rect(332f, 707f, 376f, 751f))
            it.updateExpandedPlayPause(Rect(179f, 614f, 221f, 656f))
        }
        assertEquals(Rect(16f, 710f, 60f, 754f), resolveClassicWheelSharedGeometry(0f, elements)!!.artwork)
        assertEquals(Rect(30f, 120f, 170f, 260f), resolveClassicWheelSharedGeometry(1f, elements)!!.artwork)
        assertEquals(Rect(70f, 710f, 240f, 730f), resolveClassicWheelSharedGeometry(0f, elements)!!.title)
        assertEquals(Rect(185f, 176f, 360f, 205f), resolveClassicWheelSharedGeometry(1f, elements)!!.artist)
        assertEquals(Rect(332f, 707f, 376f, 751f), resolveClassicWheelSharedGeometry(0f, elements)!!.playPause)
        assertEquals(Rect(179f, 614f, 221f, 656f), resolveClassicWheelSharedGeometry(1f, elements)!!.playPause)
    }

    @Test fun `play pause morph stays within measured visible dimensions`() {
        val elements = completeElementBounds(
            miniPlayPause = Rect(332f, 707f, 376f, 751f),
            expandedPlayPause = Rect(179f, 614f, 221f, 656f)
        )

        val middle = resolveClassicWheelSharedGeometry(.5f, elements)!!.playPause

        assertEquals(43f, middle.width, 0f)
        assertEquals(43f, middle.height, 0f)
        assertTrue(middle.width in 42f..44f)
        assertTrue(middle.height in 42f..44f)
    }

    @Test fun `expanded play pause anchor uses icon dimensions rather than touch target`() {
        val measured = classicWheelExpandedPlayPauseVisualBounds(
            wheelBounds = Rect(20f, 100f, 380f, 460f),
            visibleIconSizePx = 42f,
            touchTargetSizePx = 72f,
            bottomPaddingPx = 26f
        )
        assertNotNull(measured)
        val anchor = measured!!

        assertEquals(Rect(179f, 377f, 221f, 419f), anchor)
        assertEquals(42f, anchor.width, 0f)
        assertEquals(42f, anchor.height, 0f)
        assertTrue(anchor.width < 72f)
        assertTrue(anchor.height < 72f)
    }

    @Test fun `invalid measurement preserves the last valid anchor`() {
        val elements = ClassicWheelMorphBounds()
        val valid = Rect(10f, 10f, 20f, 20f)
        elements.updateMiniArtwork(valid)
        elements.updateMiniArtwork(Rect(0f, 0f, 0f, 0f))
        assertEquals(valid, elements.miniArtwork)
    }

    private fun bounds(): PlayerEndpointBounds = PlayerEndpointBounds().also {
        it.updateMini(Rect(0f, 700f, 400f, 780f))
        it.updateExpanded(Rect(0f, 0f, 400f, 800f))
    }

    private fun shellElementBounds(): ClassicWheelMorphBounds = ClassicWheelMorphBounds().also {
        it.updateMiniShell(Rect(12f, 706f, 388f, 774f))
    }

    private fun completeElementBounds(
        miniPlayPause: Rect,
        expandedPlayPause: Rect
    ) = ClassicWheelMorphBounds().also {
        it.updateMiniArtwork(Rect(16f, 710f, 60f, 754f))
        it.updateExpandedArtwork(Rect(30f, 120f, 170f, 260f))
        it.updateMiniTitle(Rect(70f, 710f, 240f, 730f))
        it.updateExpandedTitle(Rect(185f, 120f, 360f, 170f))
        it.updateMiniArtist(Rect(70f, 734f, 240f, 750f))
        it.updateExpandedArtist(Rect(185f, 176f, 360f, 205f))
        it.updateMiniPlayPause(miniPlayPause)
        it.updateExpandedPlayPause(expandedPlayPause)
    }
}
