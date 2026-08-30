package io.github.rsgarrido.sazanami.player.equalizer.limiter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiterTelemetryTest {
    @Test
    fun boundedPublicationRetainsPeakAcrossMultipleProcessorCalls() {
        val telemetry = LimiterTelemetryAccumulator()
        val exchange = LimiterTelemetryExchange()
        telemetry.beginProcessingCall()
        telemetry.observePreLimiterSample(1.25f)
        telemetry.observePostLimiterSample(0.9f)
        telemetry.beginProcessingCall()
        telemetry.observePreLimiterSample(0.2f)
        telemetry.observePostLimiterSample(0.1f)

        telemetry.publishTo(exchange)

        val meter = exchange.snapshot().meter
        assertEquals(
            LimiterMath.linearToDbfs(1.25),
            meter.preLimiterPeakDbfs,
            1e-9
        )
        assertEquals(
            LimiterMath.linearToDbfs(0.9),
            meter.postLimiterPeakDbfs,
            1e-6
        )
    }

    @Test
    fun exchangeRetainsMaximumWhenCoordinatorMissesAPublication() {
        val exchange = LimiterTelemetryExchange()
        exchange.publish(
            LimiterMeterSnapshot(
                preLimiterPeakDbfs = -1.0,
                postLimiterPeakDbfs = -2.0,
                maximumGainReductionDb = 5.0
            )
        )
        exchange.publish(
            LimiterMeterSnapshot(
                preLimiterPeakDbfs = -12.0,
                postLimiterPeakDbfs = -14.0,
                maximumGainReductionDb = 1.0
            )
        )

        val meter = exchange.snapshot().meter

        assertEquals(-1.0, meter.preLimiterPeakDbfs, 0.0)
        assertEquals(-2.0, meter.postLimiterPeakDbfs, 0.0)
        assertEquals(5.0, meter.maximumGainReductionDb, 0.0)
    }

    @Test
    fun peaksCountsAndGainReductionAreMeasuredPrecisely() {
        val telemetry = LimiterTelemetryAccumulator()
        telemetry.beginProcessingCall()
        val prepared = LimiterPreparedConfiguration.prepare(
            LimiterConfiguration(enabled = true),
            sampleRateHz = 48_000,
            channelCount = 2,
            configurationVersion = 1L
        )
        val engine = LookaheadLimiterEngine(prepared, telemetry)
        val input = FloatArray(1_000 * 2) { index ->
            if (index % 2 == 0) 1.5f else -0.5f
        }
        val output = FloatArray(input.size)
        var frames = engine.process(
            input = input,
            inputOffset = 0,
            frameCount = 1_000,
            output = output,
            outputOffset = 0
        )
        frames += engine.drain(
            output = output,
            outputOffset = frames * 2,
            maximumFrameCount = 1_000 - frames
        )
        val snapshot = telemetry.snapshot()

        assertEquals(1_000, frames)
        assertEquals(
            LimiterMath.linearToDbfs(1.5),
            snapshot.preLimiterPeakDbfs,
            1.0e-9
        )
        assertTrue(snapshot.postLimiterPeakDbfs <= -1.0 + 1.0e-5)
        assertEquals(1_000L, snapshot.overRangeSampleCount)
        assertEquals(1_000L, snapshot.limiterActiveFrameCount)
        assertTrue(snapshot.limiterReducedFrameCount > 0L)
        assertTrue(snapshot.maximumGainReductionDb > 0.0)
    }

    @Test
    fun saturationIsCountedSeparatelyAndResetClearsAllState() {
        val telemetry = LimiterTelemetryAccumulator()
        telemetry.beginProcessingCall()
        telemetry.observePreLimiterSample(1.2f)
        telemetry.observePostLimiterSample(1.1f)
        telemetry.observeSaturatedSample()
        telemetry.observeLimiterFrame(0.5)

        val beforeReset = telemetry.snapshot()
        assertEquals(1L, beforeReset.overRangeSampleCount)
        assertEquals(1L, beforeReset.saturatedSampleCount)

        telemetry.reset()
        val afterReset = telemetry.snapshot()
        assertEquals(LimiterMeterSnapshot(), afterReset)
    }

    @Test
    fun countersAreAggregatedAsOrdinaryFields() {
        val telemetry = LimiterTelemetryAccumulator()
        telemetry.beginProcessingCall()
        repeat(10_000) {
            telemetry.observePreLimiterSample(1.1f)
        }

        assertEquals(
            10_000L,
            telemetry.snapshot().overRangeSampleCount
        )
    }
}
