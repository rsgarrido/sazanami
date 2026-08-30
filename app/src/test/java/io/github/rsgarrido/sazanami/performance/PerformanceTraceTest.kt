package io.github.rsgarrido.sazanami.performance

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceTraceTest {
    @Test
    fun traceWrapperPreservesReturnValue() {
        PerformanceTracing.bypassForTests = true
        val result = try {
            tracePerformance(PerformanceTraceNames.LIBRARY_CLASSIFICATION) { 42 }
        } finally {
            PerformanceTracing.bypassForTests = false
        }

        assertEquals(42, result)
    }

    @Test
    fun disabledVisualizerCountersRemainIdle() {
        VisualizerPerformanceCounters.enabled = false
        VisualizerPerformanceCounters.reset()

        VisualizerPerformanceCounters.onLoopStarted(30)
        VisualizerPerformanceCounters.onUpdate()
        VisualizerPerformanceCounters.onDraw()

        assertEquals(
            VisualizerCounterSnapshot(0, 0, 0, 0, 0),
            VisualizerPerformanceCounters.snapshot()
        )
    }
}
