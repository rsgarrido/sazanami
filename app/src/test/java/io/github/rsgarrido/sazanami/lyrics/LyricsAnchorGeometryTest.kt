package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsAnchorGeometryTest {
    @Test
    fun centeredItemReturnsZero() {
        assertDelta(
            expected = 0f,
            viewportStart = 0,
            viewportEnd = 1_000,
            itemOffset = 370,
            itemSize = 100
        )
    }

    @Test
    fun itemBelowAnchorReturnsPositiveDeltaToMoveItUp() {
        assertDelta(
            expected = 280f,
            viewportStart = 0,
            viewportEnd = 1_000,
            itemOffset = 600,
            itemSize = 200
        )
    }

    @Test
    fun itemAboveAnchorReturnsNegativeDeltaToMoveItDown() {
        assertDelta(
            expected = -220f,
            viewportStart = 0,
            viewportEnd = 1_000,
            itemOffset = 100,
            itemSize = 200
        )
    }

    @Test
    fun nonzeroViewportStartUsesLazyListCoordinateSpace() {
        assertDelta(
            expected = 0f,
            viewportStart = -420,
            viewportEnd = 580,
            itemOffset = -50,
            itemSize = 100
        )
    }

    @Test
    fun headerAdjustedViewportUsesReportedBounds() {
        assertDelta(
            expected = 0f,
            viewportStart = 120,
            viewportEnd = 920,
            itemOffset = 406,
            itemSize = 100
        )
    }

    @Test
    fun tallWrappedAndShortRowsUseTheirMeasuredCenters() {
        assertDelta(0f, 0, 1_000, 300, 240)
        assertDelta(0f, 0, 1_000, 400, 40)
    }

    @Test
    fun firstAndFinalRowsUseTheSameGeometry() {
        assertDelta(-370f, 0, 1_000, 0, 100)
        assertDelta(530f, 0, 1_000, 900, 100)
    }

    @Test
    fun invalidViewportOrItemReturnsZero() {
        assertDelta(0f, 100, 100, 50, 20)
        assertDelta(0f, 200, 100, 50, 20)
        assertDelta(0f, 0, 1_000, 50, 0)
    }

    @Test
    fun toleranceSuppressesTinyCorrections() {
        val geometry = LyricAnchorGeometry(0, 1_000, 374, 100)

        assertEquals(4f, calculateLyricAnchorScrollDelta(geometry), 0.01f)
        assertEquals(0f, calculateLyricAnchorScrollDelta(geometry, tolerancePx = 6f), 0.01f)
    }

    @Test
    fun anchorFractionIsClampedAndNanIsRejected() {
        assertEquals(
            -950f,
            calculateLyricAnchorScrollDelta(
                LyricAnchorGeometry(0, 1_000, 0, 100, anchorFraction = 2f)
            ),
            0.01f
        )
        assertEquals(
            0f,
            calculateLyricAnchorScrollDelta(
                LyricAnchorGeometry(0, 1_000, 0, 100, anchorFraction = Float.NaN)
            ),
            0.01f
        )
    }

    private fun assertDelta(
        expected: Float,
        viewportStart: Int,
        viewportEnd: Int,
        itemOffset: Int,
        itemSize: Int
    ) {
        assertEquals(
            expected,
            calculateLyricAnchorScrollDelta(
                LyricAnchorGeometry(
                    viewportStartPx = viewportStart,
                    viewportEndPx = viewportEnd,
                    itemOffsetPx = itemOffset,
                    itemSizePx = itemSize
                )
            ),
            0.01f
        )
    }
}
