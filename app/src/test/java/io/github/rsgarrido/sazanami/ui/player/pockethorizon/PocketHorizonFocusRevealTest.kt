package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketHorizonFocusRevealTest {
    @Test fun correctionsRevealTheWholeMeasuredRowAtEitherEdgeWithoutMovingVisibleRows() {
        val start = -12
        val end = 328
        for (size in listOf(64, 88, 148, 300)) {
            for (offset in listOf(-100, -12, 0, 160, 300, 500)) {
                val delta = pocketHorizonFocusScrollDelta(offset, size, start, end)
                val corrected = offset - delta
                assertTrue(corrected >= start)
                assertTrue(corrected + size <= end)
                if (offset >= start && offset + size <= end) assertEquals(0, delta)
            }
        }
        // A tall next row needs its own bottom correction, not the short neighbour's height.
        assertEquals(120, pocketHorizonFocusScrollDelta(300, 148, start, end))
        assertEquals(0, pocketHorizonFocusScrollDelta(24, 148, start, end))
    }

    @Test fun anOversizedRowInAnInsufficientHostKeepsItsLeadingEdgeReadable() {
        assertEquals(80, pocketHorizonFocusScrollDelta(80, 500, 0, 200))
        assertEquals(0, pocketHorizonFocusScrollDelta(0, 500, 0, 200))
    }
}
