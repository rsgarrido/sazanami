package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketHorizonLayoutTest {
    @Test
    fun minimumReservesReadableMetadataArtworkHeaderAndRealTouchTargets() {
        val minimum = minimumPocketHorizonSize(1f)
        assertEquals(DpSize(616.dp, 556.dp), minimum)
        val layout = requireNotNull(resolvePocketHorizonLayout(minimum, 1f))
        assertTrue(layout.fitsMinimum)
        assertTrue(layout.isWide)
        assertEquals(144.dp, layout.artworkSize)
        assertEquals(336.dp, layout.detailsWidth)
        assertEquals(112.dp, layout.headerHeight)
        assertEquals(316.dp, layout.bodyMinHeight)
        assertEquals(layout.contentViewportSize.width, layout.artworkSize +
            PocketHorizonLayoutMetrics.ContentGap + layout.detailsWidth)
        assertEquals(layout.contentViewportSize.height,
            layout.headerHeight + PocketHorizonLayoutMetrics.HeaderGap + layout.bodyMinHeight)
        assertTrue(PocketHorizonLayoutMetrics.ControlHeight >= 48.dp)
        assertTrue(PocketHorizonLayoutMetrics.SeekTarget >= 48.dp)
        assertContained(minimum, layout)
    }

    @Test
    fun typicalAndLargeHostsKeepTheDisplayDominantAndArtworkBounded() {
        for (host in listOf(DpSize(1024.dp, 600.dp), DpSize(1280.dp, 800.dp),
            DpSize(1920.dp, 1080.dp), DpSize(2560.dp, 720.dp))) {
            val layout = requireNotNull(resolvePocketHorizonLayout(host, 1f))
            assertTrue(layout.fitsMinimum)
            assertTrue(layout.isWide)
            assertTrue(layout.artworkSize in 144.dp..280.dp)
            assertTrue(layout.detailsWidth >= 336.dp)
            assertTrue(layout.artworkSize < layout.contentWidth / 2)
            assertTrue(layout.displaySize.width > layout.shellSize.width * .9f)
            assertTrue(layout.displaySize.height > layout.shellSize.height * .9f)
            assertTrue(layout.headerHeight + PocketHorizonLayoutMetrics.HeaderGap +
                layout.bodyMinHeight <= layout.contentViewportSize.height)
            assertContained(host, layout)
        }
    }

    @Test
    fun largeTextIncreasesTheRealBudgetWithoutShrinkingTouchTargetsOrArtwork() {
        val normal = minimumPocketHorizonSize(1f)
        val large = minimumPocketHorizonSize(2f)
        assertEquals(DpSize(952.dp, 748.dp), large)
        assertTrue(large.width > normal.width)
        assertTrue(large.height > normal.height)
        val layout = requireNotNull(resolvePocketHorizonLayout(large, 2f))
        assertTrue(layout.fitsMinimum)
        assertEquals(144.dp, layout.artworkSize)
        assertEquals(672.dp, layout.detailsWidth)
        assertEquals(148.dp, layout.headerHeight)
        assertEquals(472.dp, layout.bodyMinHeight)
        assertContained(large, layout)
    }

    @Test
    fun aMissingWidthOrHeightBudgetSelectsScrollableStackedContent() {
        val minimum = minimumPocketHorizonSize(1f)
        for (host in listOf(
            DpSize(minimum.width - 1.dp, minimum.height),
            DpSize(minimum.width, minimum.height - 1.dp),
            DpSize(320.dp, 220.dp), DpSize(160.dp, 80.dp), DpSize(1.dp, 1.dp)
        )) {
            val layout = requireNotNull(resolvePocketHorizonLayout(host, 1f))
            assertFalse(layout.fitsMinimum)
            assertFalse(layout.isWide)
            assertTrue(layout.contentWidth >= 336.dp)
            assertEquals(layout.contentWidth, layout.detailsWidth)
            assertTrue(layout.artworkSize >= 144.dp)
            assertTrue(layout.bodyMinHeight > layout.contentViewportSize.height)
            assertContained(host, layout)
        }
        val largeText = requireNotNull(resolvePocketHorizonLayout(DpSize(616.dp, 500.dp), 2f))
        assertFalse(largeText.fitsMinimum)
        assertTrue(largeText.contentWidth >= 672.dp)
    }

    @Test
    fun invalidBoundsCannotProduceGeometryAndInvalidTextScaleUsesTheNormalBudget() {
        for (host in listOf(DpSize.Unspecified, DpSize(0.dp, 500.dp),
            DpSize(616.dp, (-1).dp), DpSize(Dp.Infinity, 500.dp),
            DpSize(616.dp, Dp.Unspecified))) {
            assertNull(resolvePocketHorizonLayout(host, 1f))
        }
        for (scale in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f, .8f)) {
            assertEquals(minimumPocketHorizonSize(1f), minimumPocketHorizonSize(scale))
            assertEquals(resolvePocketHorizonLayout(DpSize(1280.dp, 800.dp), 1f),
                resolvePocketHorizonLayout(DpSize(1280.dp, 800.dp), scale))
        }
    }

    @Test
    fun browserFallbackBudgetsBackStatusAndAReadableTrackWithoutUnboundedLists() {
        assertEquals(216.dp, PocketHorizonLayoutMetrics.browserMinimumHeight(1f))
        assertEquals(372.dp, PocketHorizonLayoutMetrics.browserMinimumHeight(2f))
        for (scale in listOf(1f, 2f)) {
            val minimum = requireNotNull(resolvePocketHorizonLayout(minimumPocketHorizonSize(scale), scale))
            assertTrue(minimum.bodyMinHeight >= PocketHorizonLayoutMetrics.browserMinimumHeight(scale))
            assertTrue(PocketHorizonLayoutMetrics.categoryHeight(scale) >= 48.dp)
        }
    }

    private fun assertContained(host: DpSize, layout: PocketHorizonLayout) {
        val tolerance = .001f
        assertTrue(layout.shellSize.width.value + layout.outerInset.value * 2 <= host.width.value + tolerance)
        assertTrue(layout.shellSize.height.value + layout.outerInset.value * 2 <= host.height.value + tolerance)
        assertTrue(layout.displaySize.width > 0.dp)
        assertTrue(layout.displaySize.height > 0.dp)
        assertTrue(layout.contentViewportSize.width > 0.dp)
        assertTrue(layout.contentViewportSize.height > 0.dp)
        assertEquals(layout.shellSize.width.value,
            layout.displaySize.width.value + layout.bezelInset.value * 2, tolerance)
        assertEquals(layout.shellSize.height.value,
            layout.displaySize.height.value + layout.bezelInset.value * 2, tolerance)
        assertEquals(layout.displaySize.width.value,
            layout.contentViewportSize.width.value + layout.displayPadding.value * 2, tolerance)
        assertEquals(layout.displaySize.height.value,
            layout.contentViewportSize.height.value + layout.displayPadding.value * 2, tolerance)
    }
}
