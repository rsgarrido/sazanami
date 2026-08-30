package io.github.rsgarrido.sazanami.player.equalizer.performance

import androidx.media3.common.C
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPlanPreparer
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerProcessorFormat
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeSnapshot
import io.github.rsgarrido.sazanami.player.equalizer.dsp.KotlinEqualizerDspEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPerformanceMatrixTest {
    @Test
    fun namedConfigurationsCoverTheRequiredFormatAndBufferMatrix() {
        EqualizerPerformanceFixtures.namedConfigurations
            .forEach { (name, configuration) ->
                EqualizerPerformanceFixtures.sampleRates
                    .forEach { sampleRateHz ->
                        EqualizerPerformanceFixtures.channelCounts
                            .forEach { channelCount ->
                                val plan = EqualizerPlanPreparer.prepare(
                                    snapshot = EqualizerRuntimeSnapshot(
                                        version = 1L,
                                        configuration = configuration,
                                        automaticHeadroomEnabled =
                                            name != "graphic-worst"
                                    ),
                                    processorFormat =
                                        EqualizerProcessorFormat(
                                            sampleRateHz = sampleRateHz,
                                            channelCount = channelCount,
                                            pcmEncoding =
                                                C.ENCODING_PCM_16BIT
                                        )
                                )
                                val path = plan.createProcessingPath()
                                val maximumFrames =
                                    EqualizerPerformanceFixtures
                                        .bufferFrameCounts
                                        .max()
                                val input =
                                    EqualizerPerformanceFixtures.pcmSignal(
                                        frameCount = maximumFrames,
                                        channelCount = channelCount,
                                        sampleRateHz = sampleRateHz
                                    )
                                val output = FloatArray(input.size)
                                val initialCapacity =
                                    path.capacitySnapshot()

                                EqualizerPerformanceFixtures
                                    .bufferFrameCounts
                                    .forEach { frameCount ->
                                        if (path.bypassed) {
                                            input.copyInto(
                                                destination = output,
                                                endIndex =
                                                    frameCount *
                                                        channelCount
                                            )
                                        } else {
                                            path.process(
                                                input = input,
                                                output = output,
                                                frameCount = frameCount
                                            )
                                        }
                                        repeat(frameCount * channelCount) {
                                            sampleIndex ->
                                            assertTrue(
                                                "$name, $sampleRateHz Hz, " +
                                                    "$channelCount channels, " +
                                                    "$frameCount frames, " +
                                                    "sample $sampleIndex",
                                                output[sampleIndex].isFinite()
                                            )
                                        }
                                    }

                                assertEquals(
                                    initialCapacity,
                                    path.capacitySnapshot()
                                )
                                if (!path.bypassed) {
                                    assertNotEquals(
                                        0,
                                        initialCapacity
                                            ?.coefficientArrayIdentity
                                    )
                                    assertNotEquals(
                                        0,
                                        initialCapacity
                                            ?.stateArrayIdentity
                                    )
                                }
                            }
                    }
            }
    }

    @Test
    fun dspEngineReusesPreparedCapacityAfterWarmUp() {
        val engine = KotlinEqualizerDspEngine()
        val configuration = EqualizerPerformanceFixtures
            .namedConfigurations
            .getValue("graphic-worst")
        engine.configure(
            configuration = configuration,
            sampleRateHz = 192_000,
            channelCount = 6,
            automaticHeadroomDb = 12.0
        )
        val input = EqualizerPerformanceFixtures.pcmSignal(
            frameCount = 4_096,
            channelCount = 6,
            sampleRateHz = 192_000
        )
        val output = FloatArray(input.size)
        engine.processInterleaved(
            input = input,
            inputOffset = 0,
            output = output,
            outputOffset = 0,
            frameCount = 4_096
        )
        val warmCapacity = engine.capacitySnapshot()

        repeat(50) {
            engine.processInterleaved(
                input = input,
                inputOffset = 0,
                output = output,
                outputOffset = 0,
                frameCount = 4_096
            )
        }

        assertEquals(warmCapacity, engine.capacitySnapshot())
    }
}
