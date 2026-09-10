package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketFlipPlayerMorphGeometryTest {
    @Test
    fun `expanded seek anchor is centered on the visible housing`() {
        val normalInteractiveBounds = Rect(12f, 100f, 388f, 142f)
        val compactInteractiveBounds = Rect(12f, 200f, 388f, 238f)

        val normalVisualBounds = pocketFlipSeekVisualBounds(
            interactiveBounds = normalInteractiveBounds,
            visualHeightPx = 17f
        )
        val compactVisualBounds = pocketFlipSeekVisualBounds(
            interactiveBounds = compactInteractiveBounds,
            visualHeightPx = 15f
        )

        assertEquals(Rect(12f, 112.5f, 388f, 129.5f), normalVisualBounds)
        assertEquals(Rect(12f, 211.5f, 388f, 226.5f), compactVisualBounds)
        assertEquals(17f, normalVisualBounds.height)
        assertEquals(15f, compactVisualBounds.height)
        assertTrue(normalVisualBounds.height < normalInteractiveBounds.height)
        assertTrue(compactVisualBounds.height < compactInteractiveBounds.height)
    }

    @Test
    fun `seek geometry interpolates only between visible endpoint heights`() {
        val bounds = sharedBounds()

        val mini = resolvePocketFlipSharedGeometry(0f, bounds)!!.progress
        val middle = resolvePocketFlipSharedGeometry(.5f, bounds)!!.progress
        val expanded = resolvePocketFlipSharedGeometry(1f, bounds)!!.progress

        assertEquals(5f, mini.height)
        assertEquals(11f, middle.height)
        assertEquals(17f, expanded.height)
        assertTrue(middle.height in mini.height..expanded.height)
        assertTrue(middle.height < (mini.height + 42f) / 2f)
    }

    @Test
    fun `play geometry interpolates between visible button faces`() {
        val bounds = sharedBounds()

        val mini = resolvePocketFlipSharedGeometry(0f, bounds)!!.play
        val middle = resolvePocketFlipSharedGeometry(.5f, bounds)!!.play
        val expanded = resolvePocketFlipSharedGeometry(1f, bounds)!!.play

        assertEquals(38f, mini.width)
        assertEquals(45f, middle.width)
        assertEquals(52f, expanded.width)
        assertEquals(middle.width, middle.height)
        assertTrue(middle.width in mini.width..expanded.width)
        assertTrue(middle.width < (mini.width + 68f) / 2f)
    }

    @Test
    fun `visible shared geometry reaches both endpoints exactly`() {
        val bounds = sharedBounds()

        val mini = resolvePocketFlipSharedGeometry(0f, bounds)!!
        val expanded = resolvePocketFlipSharedGeometry(1f, bounds)!!

        assertEquals(bounds.miniProgress, mini.progress)
        assertEquals(bounds.expandedProgress, expanded.progress)
        assertEquals(bounds.miniPlay, mini.play)
        assertEquals(bounds.expandedPlay, expanded.play)
    }

    @Test
    fun `missing and invalid anchors fail safely without replacing valid geometry`() {
        val missing = PocketFlipMorphBounds().also {
            it.updateMiniProgress(Rect(68f, 748f, 250f, 753f))
            it.updateExpandedProgress(Rect(24f, 330f, 376f, 347f))
        }
        assertNull(resolvePocketFlipSharedGeometry(.5f, missing))

        val bounds = sharedBounds()
        val validProgress = bounds.expandedProgress
        val validPlay = bounds.expandedPlay

        bounds.updateExpandedProgress(Rect.Zero)
        bounds.updateExpandedPlay(Rect(Float.NaN, 0f, 1f, 1f))

        assertEquals(validProgress, bounds.expandedProgress)
        assertEquals(validPlay, bounds.expandedPlay)
    }

    private fun sharedBounds() = PocketFlipMorphBounds().also {
        it.updateMiniArtwork(Rect(16f, 710f, 48f, 742f))
        it.updateExpandedArtwork(Rect(18f, 70f, 134f, 186f))
        it.updateMiniTitle(Rect(58f, 710f, 250f, 728f))
        it.updateExpandedTitle(Rect(143f, 76f, 370f, 106f))
        it.updateMiniArtist(Rect(58f, 730f, 250f, 746f))
        it.updateExpandedArtist(Rect(143f, 108f, 370f, 126f))
        it.updateMiniProgress(Rect(58f, 748f, 250f, 753f))
        it.updateExpandedProgress(Rect(18f, 206f, 382f, 223f))
        it.updateMiniPlay(Rect(330f, 706f, 368f, 744f))
        it.updateExpandedPlay(Rect(164f, 520f, 216f, 572f))
    }
}
