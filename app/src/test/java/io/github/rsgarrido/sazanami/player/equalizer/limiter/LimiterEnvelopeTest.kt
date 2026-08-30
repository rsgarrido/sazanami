package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiterEnvelopeTest {
    @Test
    fun releaseIsMonotonicAndTimeEquivalentAcrossSampleRates() {
        listOf(32_000, 44_100, 48_000, 96_000, 192_000)
            .forEach { sampleRate ->
                val lookahead =
                    LimiterMath.lookaheadFrames(sampleRate)
                val releaseFrames = sampleRate / 5
                val input = FloatArray(
                    lookahead + releaseFrames + 2
                ) { 0.2f }
                input[lookahead] = 2.0f
                val result = limitSignal(
                    input = input,
                    sampleRateHz = sampleRate
                )
                val start = lookahead + 1
                var previousGain =
                    result.samples[start] / input[start]
                var frame = start + 1
                while (frame < input.size) {
                    val gain =
                        result.samples[frame] / input[frame]
                    assertTrue(gain + 1.0e-6f >= previousGain)
                    assertTrue(gain <= 1.0f)
                    previousGain = gain
                    frame++
                }
                val after100Ms = start + sampleRate / 10
                val gainAt100Ms =
                    result.samples[after100Ms] /
                        input[after100Ms]
                assertTrue(gainAt100Ms in 0.75f..1.0f)
            }
    }

    @Test
    fun strongerFuturePeakLowersAttackTargetWithoutOvershoot() {
        val input = FloatArray(3_000) { 0.2f }
        input[1_000] = 1.2f
        input[1_100] = 2.0f
        val result = limitSignal(input)
        val ceiling = LimiterMath.dbToLinear(-1.0)

        assertTrue(
            abs(result.samples[1_000].toDouble()) <=
                ceiling + 1.0e-6
        )
        assertTrue(
            abs(result.samples[1_100].toDouble()) <=
                ceiling + 1.0e-6
        )
        assertTrue(result.samples.all(Float::isFinite))
    }

    @Test
    fun ceilingOnlyUpdateKeepsLatencyAndBufferedFrames() {
        val original = LimiterPreparedConfiguration.prepare(
            LimiterConfiguration(
                enabled = true,
                ceilingDbfs = -1.0
            ),
            sampleRateHz = 48_000,
            channelCount = 1,
            configurationVersion = 1L
        )
        val engine = LookaheadLimiterEngine(original)
        val output = FloatArray(1_000)
        engine.process(
            input = FloatArray(100) { 0.2f },
            inputOffset = 0,
            frameCount = 100,
            output = output,
            outputOffset = 0
        )
        val pendingBefore = engine.pendingFrameCount

        engine.updateCeiling(
            LimiterPreparedConfiguration.prepare(
                LimiterConfiguration(
                    enabled = true,
                    ceilingDbfs = -3.0
                ),
                sampleRateHz = 48_000,
                channelCount = 1,
                configurationVersion = 2L
            )
        )

        assertEquals(pendingBefore, engine.pendingFrameCount)
        assertEquals(1.0, engine.currentLinearGain, 0.0)
    }
}
