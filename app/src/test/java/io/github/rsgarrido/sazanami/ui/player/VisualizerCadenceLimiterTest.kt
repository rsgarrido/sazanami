package io.github.rsgarrido.sazanami.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerCadenceLimiterTest {
    @Test
    fun oneHundredTwentyHertzFramesProduceAtMostThirtyOneUpdatesPerSecond() {
        val limiter = VisualizerCadenceLimiter(RETRO_VISUALIZER_CADENCE_HZ)
        var updates = 0

        repeat(120) { frame ->
            if (limiter.shouldUpdate(frame * 8_333_334L)) updates++
        }

        assertTrue(updates in 29..31)
    }

    @Test
    fun duplicateFrameTimestampDoesNotUpdateTwice() {
        val limiter = VisualizerCadenceLimiter(30)

        assertTrue(limiter.shouldUpdate(1_000L))
        assertFalse(limiter.shouldUpdate(1_000L))
    }

    @Test
    fun cadenceIsClampedToSupportedRange() {
        assertEquals(1, VisualizerCadenceLimiter(0).targetCadenceHz)
        assertEquals(60, VisualizerCadenceLimiter(120).targetCadenceHz)
    }
}
