package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookaheadLimiterEngineTest {
    @Test
    fun belowCeilingSignalIsUnchangedAfterDrain() {
        val input = FloatArray(2_000) { index ->
            (0.3 * kotlin.math.sin(index * 0.07)).toFloat()
        }

        val result = limitSignal(input)

        assertArrayEquals(input, result.samples, 1.0e-6f)
        assertEquals(input.size, result.totalProducedFrames)
        assertEquals(0.0, result.meter.currentGainReductionDb, 1.0e-9)
    }

    @Test
    fun aboveCeilingSignalIsReducedWithoutOvershoot() {
        val input = FloatArray(4_000) { 1.4f }
        val ceiling = LimiterMath.dbToLinear(-1.0)

        val result = limitSignal(input)
        val maximum = result.samples.maxOf { sample ->
            abs(sample.toDouble())
        }

        assertTrue(maximum <= ceiling + 1.0e-6)
        assertTrue(result.meter.maximumGainReductionDb > 3.0)
        assertTrue(result.samples.all(Float::isFinite))
        assertTrue(result.engine.currentLinearGain in 0.0..1.0)
    }

    @Test
    fun silenceRemainsSilence() {
        val result = limitSignal(FloatArray(1_000))

        assertTrue(result.samples.all { it == 0.0f })
        assertEquals(
            LimiterMath.SILENCE_FLOOR_DBFS,
            result.meter.preLimiterPeakDbfs,
            0.0
        )
        assertEquals(
            LimiterMath.SILENCE_FLOOR_DBFS,
            result.meter.postLimiterPeakDbfs,
            0.0
        )
    }

    @Test
    fun monoStereoAndSixChannelSignalsRemainFiniteAndBounded() {
        listOf(1, 2, 6).forEach { channels ->
            val frames = 1_500
            val input = FloatArray(frames * channels) { index ->
                when (index % channels) {
                    0 -> 1.5f
                    else -> 0.4f
                }
            }

            val result = limitSignal(
                input = input,
                channelCount = channels
            )
            val ceiling = LimiterMath.dbToLinear(-1.0)

            assertEquals(frames, result.totalProducedFrames)
            assertTrue(
                result.samples.all { sample ->
                    sample.isFinite() &&
                        abs(sample.toDouble()) <= ceiling + 1.0e-6
                }
            )
        }
    }

    @Test
    fun oneChannelPeakAppliesOneLinkedGainToEveryChannel() {
        val channels = 6
        val frames = 1_000
        val input = FloatArray(frames * channels)
        repeat(frames) { frame ->
            input[frame * channels] = 1.5f
            var channel = 1
            while (channel < channels) {
                input[frame * channels + channel] =
                    (0.1f * channel)
                channel++
            }
        }

        val result = limitSignal(input, channelCount = channels)
        val settledFrame = 500
        val sourceBase = settledFrame * channels
        val outputBase = settledFrame * channels
        val linkedGain =
            result.samples[outputBase] / input[sourceBase]

        var channel = 1
        while (channel < channels) {
            assertEquals(
                linkedGain,
                result.samples[outputBase + channel] /
                    input[sourceBase + channel],
                1.0e-6f
            )
            channel++
        }
    }

    @Test
    fun silentLinkedChannelsRemainSilent() {
        val input = FloatArray(2_000 * 2)
        repeat(2_000) { frame ->
            input[frame * 2] = if (frame == 800) 1.8f else 0.2f
        }

        val result = limitSignal(input, channelCount = 2)

        assertTrue(
            result.samples.indices
                .filter { index -> index % 2 == 1 }
                .all { index -> result.samples[index] == 0.0f }
        )
    }

    @Test
    fun isolatedTransientUsesLookaheadRampAndHitsCeiling() {
        val sampleRate = 48_000
        val lookahead = LimiterMath.lookaheadFrames(sampleRate)
        val transientFrame = 1_000
        val input = FloatArray(2_000) { 0.25f }
        input[transientFrame] = 1.8f

        val result = limitSignal(input, sampleRateHz = sampleRate)
        val ceiling = LimiterMath.dbToLinear(-1.0)

        assertTrue(
            abs(result.samples[transientFrame].toDouble()) <=
                ceiling + 1.0e-6
        )
        val attackStart = transientFrame - lookahead
        val firstGain =
            result.samples[attackStart] / input[attackStart]
        val lastGain =
            result.samples[transientFrame] / input[transientFrame]
        assertTrue(firstGain < 1.0f)
        assertTrue(firstGain > lastGain)
        var largestGainStep = 0.0
        var frame = attackStart + 1
        while (frame <= transientFrame) {
            val previousGain =
                result.samples[frame - 1] / input[frame - 1]
            val gain = result.samples[frame] / input[frame]
            largestGainStep = max(
                largestGainStep,
                abs((gain - previousGain).toDouble())
            )
            frame++
        }
        assertTrue(largestGainStep < 0.02)
    }

    @Test
    fun boundedTelemetryDoesNotChangeLimiterOutputOrDrainLength() {
        val input = FloatArray(6_001 * 2) { index ->
            when {
                index % 97 == 0 -> 1.6f
                index % 31 == 0 -> -1.2f
                else -> 0.35f
            }
        }

        val withTelemetry = render(input, LimiterTelemetryAccumulator())
        val withoutTelemetry = render(input, null)

        assertArrayEquals(withoutTelemetry, withTelemetry, 0.0f)
    }

    private fun render(
        input: FloatArray,
        telemetry: LimiterTelemetryAccumulator?
    ): FloatArray {
        val channelCount = 2
        val prepared = LimiterPreparedConfiguration.prepare(
            configuration = LimiterConfiguration(enabled = true),
            sampleRateHz = 44_100,
            channelCount = channelCount,
            configurationVersion = 1L
        )
        val engine = LookaheadLimiterEngine(prepared, telemetry)
        val output = FloatArray(input.size)
        var inputFrame = 0
        var outputFrame = 0
        while (inputFrame < input.size / channelCount) {
            val frameCount =
                minOf(333, input.size / channelCount - inputFrame)
            telemetry?.beginProcessingCall()
            outputFrame += engine.process(
                input = input,
                inputOffset = inputFrame * channelCount,
                frameCount = frameCount,
                output = output,
                outputOffset = outputFrame * channelCount
            )
            inputFrame += frameCount
        }
        while (!engine.isDrained) {
            telemetry?.beginProcessingCall()
            outputFrame += engine.drain(
                output = output,
                outputOffset = outputFrame * channelCount,
                maximumFrameCount =
                    input.size / channelCount - outputFrame
            )
        }
        assertEquals(input.size / channelCount, outputFrame)
        return output
    }
}
