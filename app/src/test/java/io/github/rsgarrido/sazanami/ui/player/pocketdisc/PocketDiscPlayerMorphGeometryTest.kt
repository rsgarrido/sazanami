package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDiscPlayerMorphGeometryTest {
    @Test
    fun `shell reaches mini and expanded endpoints`() {
        val bounds = endpoints()

        assertEquals(Rect(12f, 700f, 388f, 776f), resolvePocketDiscMorphGeometry(0f, bounds)!!.shell)
        assertEquals(Rect(6f, 350f, 394f, 788f), resolvePocketDiscMorphGeometry(.5f, bounds)!!.shell)
        assertEquals(Rect(0f, 0f, 400f, 800f), resolvePocketDiscMorphGeometry(1f, bounds)!!.shell)
    }

    @Test
    fun `missing endpoint measurements fail safely`() {
        val bounds = endpoints()
        bounds.markMiniStale()

        assertNull(resolvePocketDiscMorphGeometry(.5f, bounds))
        assertEquals(PocketDiscMorphSpec.minimumDragRangePx, pocketDiscMorphTravelDistance(bounds))
    }

    @Test
    fun `shared elements interpolate from mini to expanded`() {
        val bounds = sharedBounds()
        val start = resolvePocketDiscSharedGeometry(0f, bounds)!!
        val end = resolvePocketDiscSharedGeometry(1f, bounds)!!

        assertEquals(Rect(16f, 710f, 60f, 754f), start.artwork)
        assertEquals(Rect(18f, 70f, 170f, 222f), end.artwork)
        assertEquals(Rect(68f, 710f, 250f, 728f), start.title)
        assertEquals(Rect(24f, 252f, 330f, 278f), end.title)
        assertEquals(Rect(68f, 730f, 250f, 746f), start.artist)
        assertEquals(Rect(24f, 282f, 330f, 300f), end.artist)
        assertEquals(Rect(68f, 748f, 250f, 753f), start.progress)
        assertEquals(Rect(24f, 330f, 376f, 340f), end.progress)
        assertEquals(Rect(330f, 706f, 366f, 738f), start.play)
        assertEquals(Rect(164f, 390f, 236f, 450f), end.play)
    }

    @Test
    fun `core shared elements do not depend on play button measurement`() {
        val bounds = PocketDiscMorphBounds().also {
            it.updateMiniArtwork(Rect(16f, 710f, 60f, 754f))
            it.updateExpandedArtwork(Rect(18f, 70f, 170f, 222f))
            it.updateMiniTitle(Rect(68f, 710f, 250f, 728f))
            it.updateExpandedTitle(Rect(24f, 252f, 330f, 278f))
            it.updateMiniArtist(Rect(68f, 730f, 250f, 746f))
            it.updateExpandedArtist(Rect(24f, 282f, 330f, 300f))
            it.updateMiniProgress(Rect(68f, 748f, 250f, 753f))
            it.updateExpandedProgress(Rect(24f, 330f, 376f, 340f))
        }

        val coreOnly = resolvePocketDiscSharedGeometry(.5f, bounds)
        assertNotNull(coreOnly)
        assertEquals(Rect(17f, 390f, 115f, 488f), coreOnly!!.artwork)
        assertNull(coreOnly.play)

        bounds.updateMiniPlay(Rect(330f, 706f, 366f, 738f))
        bounds.updateExpandedPlay(Rect(164f, 390f, 236f, 450f))

        val shared = resolvePocketDiscSharedGeometry(.5f, bounds)
        assertNotNull(shared)
        assertEquals(Rect(17f, 390f, 115f, 488f), shared!!.artwork)
        assertEquals(Rect(247f, 548f, 301f, 594f), shared.play)
    }

    @Test
    fun `each shared element can take transition ownership independently`() {
        val bounds = PocketDiscMorphBounds().also {
            it.updateMiniArtwork(Rect(16f, 710f, 60f, 754f))
            it.updateExpandedArtwork(Rect(18f, 70f, 170f, 222f))
        }

        val availability = bounds.sharedAvailability()
        val shared = resolvePocketDiscSharedGeometry(.5f, bounds)

        assertTrue(availability.artwork)
        assertFalse(availability.title)
        assertFalse(availability.artist)
        assertFalse(availability.progress)
        assertFalse(availability.play)
        assertNotNull(shared)
        assertEquals(Rect(17f, 390f, 115f, 488f), shared!!.artwork)
        assertNull(shared.title)
        assertNull(shared.artist)
        assertNull(shared.progress)
        assertNull(shared.play)
    }

    @Test
    fun `invalid shared measurements keep the last valid anchors`() {
        val bounds = sharedBounds()
        val artwork = bounds.miniArtwork
        val title = bounds.expandedTitle

        bounds.updateMiniArtwork(Rect.Zero)
        bounds.updateExpandedTitle(Rect(Float.NaN, 0f, 1f, 1f))

        assertEquals(artwork, bounds.miniArtwork)
        assertEquals(title, bounds.expandedTitle)
    }

    @Test
    fun `shared ownership and reveal thresholds are deterministic`() {
        assertEquals(PocketDiscSharedOwner.MINI, pocketDiscSharedOwner(0f, true))
        assertEquals(PocketDiscSharedOwner.MINI, pocketDiscSharedOwner(.5f, false))
        assertEquals(PocketDiscSharedOwner.TRANSITION, pocketDiscSharedOwner(.5f, true))
        assertEquals(PocketDiscSharedOwner.EXPANDED, pocketDiscSharedOwner(1f, true))
        assertEquals(PocketDiscSharedOwner.EXPANDED, pocketDiscSharedOwner(1f, false))

        assertEquals(0f, pocketDiscHeaderReveal(0f))
        assertEquals(1f, pocketDiscHeaderReveal(1f))
        assertEquals(0f, pocketDiscMediaReveal(0f))
        assertEquals(1f, pocketDiscMediaReveal(1f))
        assertEquals(0f, pocketDiscPanelReveal(0f))
        assertEquals(1f, pocketDiscPanelReveal(1f))
        assertEquals(0f, pocketDiscControlsReveal(0f))
        assertEquals(1f, pocketDiscControlsReveal(1f))
        assertFalse(shouldRunPocketDiscExpandedWork(.1f))
        assertTrue(shouldRunPocketDiscExpandedWork(1f))
        assertFalse(pocketDiscExpandedInputEnabled(.9f))
        assertTrue(pocketDiscExpandedInputEnabled(1f))
    }

    @Test
    fun `collapse threshold follows the retro theme sensitivity`() {
        assertEquals(.22f, PocketDiscMorphSpec.collapseDistanceThresholdFraction)
        assertEquals(1_150f, PocketDiscMorphSpec.collapseVelocityThresholdPxPerSecond)
        assertEquals(220f, pocketDiscDistanceThreshold(1_000f))
    }

    private fun endpoints() = PlayerEndpointBounds().also {
        it.updateMini(Rect(12f, 700f, 388f, 776f))
        it.updateExpanded(Rect(0f, 0f, 400f, 800f))
    }

    private fun sharedBounds() = PocketDiscMorphBounds().also {
        it.updateMiniArtwork(Rect(16f, 710f, 60f, 754f))
        it.updateExpandedArtwork(Rect(18f, 70f, 170f, 222f))
        it.updateMiniTitle(Rect(68f, 710f, 250f, 728f))
        it.updateExpandedTitle(Rect(24f, 252f, 330f, 278f))
        it.updateMiniArtist(Rect(68f, 730f, 250f, 746f))
        it.updateExpandedArtist(Rect(24f, 282f, 330f, 300f))
        it.updateMiniProgress(Rect(68f, 748f, 250f, 753f))
        it.updateExpandedProgress(Rect(24f, 330f, 376f, 340f))
        it.updateMiniPlay(Rect(330f, 706f, 366f, 738f))
        it.updateExpandedPlay(Rect(164f, 390f, 236f, 450f))
    }
}
