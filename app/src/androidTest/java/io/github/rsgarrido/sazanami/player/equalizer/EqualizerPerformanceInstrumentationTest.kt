package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.interchange.SazanamiPresetFile
import io.github.rsgarrido.sazanami.player.equalizer.interchange.SazanamiPresetFileJson
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EqualizerPerformanceInstrumentationTest {
    @After
    fun releaseBridge() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun samsungProcessorCountersAreBoundedAndResettable() {
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = -6.0,
                filters = List(10) { index ->
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 100.0 + index * 900.0,
                        gainDb =
                            if (index % 2 == 0) 6.0 else -6.0,
                        q = 2.0
                    )
                }
            ),
            automaticHeadroomEnabled = true,
            limiterConfiguration = LimiterConfiguration(
                enabled = true
            )
        )
        val processor = EqualizerAudioProcessor()
        processor.configure(
            AudioFormat(
                48_000,
                2,
                C.ENCODING_PCM_16BIT
            )
        )
        processor.flush(StreamMetadata.DEFAULT)
        val input = signalBuffer(frameCount = 256)
        repeat(20) { process(processor, input) }
        val warm = processor.bufferReuseSnapshot()

        EqualizerRuntimeBridge
            .setProcessorPerformanceTelemetryEnabled(true)
        repeat(500) { process(processor, input) }
        EqualizerRuntimeBridge.publishStateForTest()
        val state = EqualizerRuntimeBridge.state.value
        val after = processor.bufferReuseSnapshot()

        assertEquals(
            500L,
            state.processorPerformance.totalCallCount
        )
        assertEquals(
            500L * 256,
            state.processorPerformance.totalFrameCount
        )
        assertTrue(
            state.processorPerformance.windowSampleCount <= 256
        )
        assertEquals(
            warm.scratchBufferGrowthCount,
            after.scratchBufferGrowthCount
        )
        assertEquals(
            warm.inputScratchIdentity,
            after.inputScratchIdentity
        )
        assertEquals(
            warm.limiterCapacity,
            after.limiterCapacity
        )

        EqualizerRuntimeBridge
            .requestProcessorPerformanceTelemetryReset()
        process(processor, input)
        EqualizerRuntimeBridge.publishStateForTest()
        val completed =
            EqualizerRuntimeBridge.state.value
                .processorPerformance
        assertEquals(
            1L,
            completed.totalCallCount
        )

        EqualizerRuntimeBridge
            .setProcessorPerformanceTelemetryEnabled(false)
        process(processor, input)
        EqualizerRuntimeBridge.publishStateForTest()
        assertFalse(
            EqualizerRuntimeBridge.state.value
                .processorPerformanceTelemetryEnabled
        )
        assertEquals(
            completed,
            EqualizerRuntimeBridge.state.value
                .processorPerformance
        )
    }

    @Test
    fun packagedNativePresetSerializationRoundTripsAllTypes() {
        val file = SazanamiPresetFile(
            name = "Instrumentation",
            preampDb = -6.0,
            automaticHeadroomEnabled = true,
            filters = listOf(
                ParametricFilter.Peaking(
                    "1", true, 1_000.0, 3.0, 1.4
                ),
                ParametricFilter.LowShelf(
                    "2", true, 90.0, 3.0, 0.8
                ),
                ParametricFilter.HighShelf(
                    "3", true, 9_000.0, -3.0, 0.8
                ),
                ParametricFilter.LowPass(
                    "4", true, 14_000.0, 0.71
                ),
                ParametricFilter.HighPass(
                    "5", true, 35.0, 0.71
                ),
                ParametricFilter.Notch(
                    "6", true, 3_200.0, 8.0
                ),
                ParametricFilter.BandPass(
                    "7", true, 650.0, 1.1
                )
            )
        )

        assertEquals(
            file,
            SazanamiPresetFileJson.decode(
                SazanamiPresetFileJson.encode(file)
            )
        )
    }

    private fun signalBuffer(frameCount: Int): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(
                frameCount * 2 * Short.SIZE_BYTES
            )
            .order(ByteOrder.nativeOrder())
        repeat(frameCount) { frame ->
            val sample = ((frame * 977) % 20_000 - 10_000)
                .toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }

    private fun process(
        processor: EqualizerAudioProcessor,
        input: ByteBuffer
    ) {
        input.position(0)
        processor.queueInput(input)
        val output = processor.output
        output.position(output.limit())
    }
}
