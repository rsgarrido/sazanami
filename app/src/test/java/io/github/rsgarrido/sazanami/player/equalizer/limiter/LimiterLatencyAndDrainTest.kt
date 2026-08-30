package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiterLatencyAndDrainTest {
    @Test
    fun initialFramesAreRetainedUntilLookaheadIsPrimed() {
        val prepared = LimiterPreparedConfiguration.prepare(
            LimiterConfiguration(enabled = true),
            sampleRateHz = 48_000,
            channelCount = 1,
            configurationVersion = 1L
        )
        val engine = LookaheadLimiterEngine(prepared)
        val output = FloatArray(prepared.lookaheadFrames + 1)

        val beforePrime = engine.process(
            input = FloatArray(prepared.lookaheadFrames) { 0.2f },
            inputOffset = 0,
            frameCount = prepared.lookaheadFrames,
            output = output,
            outputOffset = 0
        )
        assertEquals(0, beforePrime)
        assertFalse(engine.isPrimed)

        val primingFrame = engine.process(
            input = floatArrayOf(0.2f),
            inputOffset = 0,
            frameCount = 1,
            output = output,
            outputOffset = 0
        )
        assertEquals(1, primingFrame)
        assertTrue(engine.isPrimed)
    }

    @Test
    fun drainPreservesEverySourceFrameWithoutExtraFrames() {
        listOf(1, 20, 240, 241, 1_000).forEach { frameCount ->
            val input = FloatArray(frameCount) { index ->
                ((index % 17) - 8) / 40.0f
            }
            val result = limitSignal(input)

            assertEquals(frameCount, result.totalProducedFrames)
            assertTrue(result.engine.isDrained)
            assertArrayEquals(input, result.samples, 1.0e-6f)
        }
    }

    @Test
    fun segmentedInputEqualsOneShotInput() {
        val input = FloatArray(4_321) {
            Random(99).nextDouble(-1.4, 1.4).toFloat()
        }
        val oneShot = limitSignal(input).samples
        val prepared = LimiterPreparedConfiguration.prepare(
            LimiterConfiguration(enabled = true),
            sampleRateHz = 48_000,
            channelCount = 1,
            configurationVersion = 1L
        )
        val engine = LookaheadLimiterEngine(prepared)
        val segmented = FloatArray(input.size)
        var inputFrame = 0
        var outputFrame = 0
        val segmentSizes = intArrayOf(1, 7, 241, 33, 512, 19)
        var segmentIndex = 0
        while (inputFrame < input.size) {
            val frames = segmentSizes[segmentIndex % segmentSizes.size]
                .coerceAtMost(input.size - inputFrame)
            outputFrame += engine.process(
                input = input,
                inputOffset = inputFrame,
                frameCount = frames,
                output = segmented,
                outputOffset = outputFrame
            )
            inputFrame += frames
            segmentIndex++
        }
        while (!engine.isDrained) {
            outputFrame += engine.drain(
                output = segmented,
                outputOffset = outputFrame,
                maximumFrameCount = segmented.size - outputFrame
            )
        }

        assertEquals(input.size, outputFrame)
        assertArrayEquals(oneShot, segmented, 1.0e-6f)
    }

    @Test
    fun repeatedDrainAfterCompletionProducesNothing() {
        val result = limitSignal(FloatArray(500) { 0.2f })
        val output = FloatArray(10)

        assertEquals(
            0,
            result.engine.drain(
                output = output,
                outputOffset = 0,
                maximumFrameCount = output.size
            )
        )
    }

    @Test
    fun resetDiscardsPendingFramesAndRestoresUnityGain() {
        val prepared = LimiterPreparedConfiguration.prepare(
            LimiterConfiguration(enabled = true),
            sampleRateHz = 48_000,
            channelCount = 1,
            configurationVersion = 1L
        )
        val engine = LookaheadLimiterEngine(prepared)
        val output = FloatArray(1_000)
        engine.process(
            input = FloatArray(300) { 1.8f },
            inputOffset = 0,
            frameCount = 300,
            output = output,
            outputOffset = 0
        )

        engine.reset()

        assertTrue(engine.isDrained)
        assertFalse(engine.isPrimed)
        assertEquals(1.0, engine.currentLinearGain, 0.0)
        val freshInput = FloatArray(500) { 0.2f }
        val produced = engine.process(
            input = freshInput,
            inputOffset = 0,
            frameCount = freshInput.size,
            output = output,
            outputOffset = 0
        )
        var total = produced
        total += engine.drain(
            output = output,
            outputOffset = total,
            maximumFrameCount = freshInput.size - total
        )
        assertArrayEquals(freshInput, output.copyOf(total), 1.0e-6f)
    }
}
