package io.github.rsgarrido.sazanami.player.equalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerProcessorPerformanceTelemetryTest {
    @Test
    fun fixedWindowReportsNearestRankPercentilesAndDeadlineMisses() {
        val telemetry =
            EqualizerProcessorPerformanceTelemetry(windowCapacity = 4)
        listOf(1L, 2L, 3L, 20L).forEach { milliseconds ->
            telemetry.recordProcessingCall(
                durationNanos = milliseconds * 1_000_000L,
                frameCount = 480,
                sampleRateHz = 48_000,
                exactBypass = milliseconds == 1L,
                equalized = milliseconds != 1L,
                transitioning = milliseconds == 3L,
                limiterActive = milliseconds == 20L,
                configurationVersion =
                    if (milliseconds < 20L) 22L else 23L,
                configurationMode = EqualizerMode.PARAMETRIC,
                validFilterCount = 10,
                channelCount = 2
            )
        }

        val snapshot = telemetry.snapshot()

        assertEquals(4, snapshot.windowSampleCount)
        assertEquals(4L, snapshot.totalCallCount)
        assertEquals(1L, snapshot.deadlineMissCount)
        assertEquals(2.0, snapshot.medianProcessingMillis, 0.0)
        assertEquals(20.0, snapshot.p90ProcessingMillis, 0.0)
        assertEquals(20.0, snapshot.p95ProcessingMillis, 0.0)
        assertEquals(20.0, snapshot.p99ProcessingMillis, 0.0)
        assertEquals(1L, snapshot.exactBypassCallCount)
        assertEquals(3L, snapshot.equalizedCallCount)
        assertEquals(1L, snapshot.transitionCallCount)
        assertEquals(1L, snapshot.limiterCallCount)
        assertEquals(
            22L,
            snapshot.firstMeasuredConfiguration?.version
        )
        assertEquals(
            23L,
            snapshot.lastMeasuredConfiguration?.version
        )
        assertEquals(1L, snapshot.measuredConfigurationChangeCount)
    }

    @Test
    fun fixedWindowRemainsBoundedAndResetClearsAllValues() {
        val telemetry =
            EqualizerProcessorPerformanceTelemetry(windowCapacity = 3)
        repeat(20) { index ->
            telemetry.recordProcessingCall(
                durationNanos = (index + 1L) * 1_000L,
                frameCount = 128,
                sampleRateHz = 48_000,
                exactBypass = true,
                equalized = false,
                transitioning = false,
                limiterActive = false
            )
        }
        assertEquals(3, telemetry.snapshot().windowSampleCount)

        telemetry.reset()

        assertEquals(
            EqualizerProcessorPerformanceSnapshot(),
            telemetry.snapshot()
        )
        assertTrue(telemetry.snapshot().deadlineMissCount == 0L)
    }
}
