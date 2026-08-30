package io.github.rsgarrido.sazanami.player.equalizer.performance

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerAudioProcessor
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerProcessorFormat
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class EqualizerProcessorLongRunTest {
    @After
    fun releaseBridge() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun processesSixtyMinutesOfGeneratedAudioWithoutGrowthOrStateLoss() {
        assumeTrue(
            "Run with -Dequalizer.longRun=true",
            java.lang.Boolean.getBoolean("equalizer.longRun")
        )
        val sampleRateHz = 48_000
        val channelCount = 2
        val frameCount = 4_096
        val totalFrames =
            sampleRateHz.toLong() * SIXTY_MINUTES_SECONDS
        val callCount = ceil(
            totalFrames.toDouble() / frameCount
        ).toInt()
        val configurations = listOf(
            EqualizerPerformanceFixtures.namedConfigurations
                .getValue("parametric-realistic"),
            EqualizerPerformanceFixtures.namedConfigurations
                .getValue("graphic-moderate")
        )
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = configurations.first(),
            automaticHeadroomEnabled = true,
            limiterConfiguration = LimiterConfiguration(
                enabled = true
            )
        )
        val processor = EqualizerAudioProcessor()
        val audioFormat = AudioFormat(
            sampleRateHz,
            channelCount,
            C.ENCODING_PCM_16BIT
        )
        val processorFormat = EqualizerProcessorFormat(
            sampleRateHz,
            channelCount,
            C.ENCODING_PCM_16BIT
        )
        processor.configure(audioFormat)
        processor.flush(StreamMetadata.DEFAULT)
        val input = generatedInput(frameCount, channelCount)
        repeat(20) {
            processOneBuffer(processor, input)
        }
        processor.flush(StreamMetadata.DEFAULT)
        val warm = processor.bufferReuseSnapshot()

        var selectedConfiguration = 0
        var producedFrameCount = 0L
        repeat(callCount) { callIndex ->
            if (
                callIndex > 0 &&
                callIndex % CONFIGURATION_CHANGE_INTERVAL == 0
            ) {
                selectedConfiguration =
                    (selectedConfiguration + 1) %
                        configurations.size
                EqualizerRuntimeBridge.requestConfiguration(
                    configuration =
                        configurations[selectedConfiguration],
                    automaticHeadroomEnabled = true,
                    limiterConfiguration = LimiterConfiguration(
                        enabled = true
                    )
                )
                EqualizerRuntimeBridge.prepareForProcessorFormat(
                    processorFormat
                )
            }
            producedFrameCount +=
                processOneBuffer(processor, input)
        }

        processor.queueEndOfStream()
        while (!processor.isEnded) {
            val output = processor.output
            producedFrameCount +=
                consumeFrames(output, channelCount)
        }
        val after = processor.bufferReuseSnapshot()
        assertEquals(
            warm.scratchBufferGrowthCount,
            after.scratchBufferGrowthCount
        )
        assertEquals(
            warm.inputScratchIdentity,
            after.inputScratchIdentity
        )
        assertEquals(
            warm.currentOutputScratchIdentity,
            after.currentOutputScratchIdentity
        )
        assertEquals(
            warm.limiterOutputScratchIdentity,
            after.limiterOutputScratchIdentity
        )
        assertEquals(
            warm.limiterCapacity,
            after.limiterCapacity
        )
        assertEquals(
            callCount.toLong() * frameCount,
            producedFrameCount
        )
        assertTrue(
            processor.transitionDiagnosticsSnapshot()
                .unexpectedExactBypassBufferCount == 0L
        )
        println(
            "PHASE_F_LONG_RUN equivalent_seconds=" +
                SIXTY_MINUTES_SECONDS +
                " calls=$callCount frames=${callCount.toLong() * frameCount}"
        )
    }

    private fun generatedInput(
        frameCount: Int,
        channelCount: Int
    ): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(
                frameCount * channelCount * Short.SIZE_BYTES
            )
            .order(ByteOrder.nativeOrder())
        repeat(frameCount * channelCount) { index ->
            val sample = (((index * 977) % 24_000) - 12_000)
                .toShort()
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }

    private fun processOneBuffer(
        processor: EqualizerAudioProcessor,
        input: ByteBuffer
    ): Long {
        input.position(0)
        processor.queueInput(input)
        return consumeFrames(
            processor.output,
            channelCount = 2
        )
    }

    private fun consumeFrames(
        output: ByteBuffer,
        channelCount: Int
    ): Long {
        val frames = output.remaining() /
            (channelCount * Short.SIZE_BYTES)
        while (output.hasRemaining()) {
            output.short
        }
        return frames.toLong()
    }

    private companion object {
        const val SIXTY_MINUTES_SECONDS = 3_600L
        const val CONFIGURATION_CHANGE_INTERVAL = 2_000
    }
}
