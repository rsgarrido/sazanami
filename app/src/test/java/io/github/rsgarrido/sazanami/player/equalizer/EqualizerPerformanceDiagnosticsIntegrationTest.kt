package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EqualizerPerformanceDiagnosticsIntegrationTest {
    @Before
    fun resetBefore() {
        EqualizerRuntimeBridge.release()
    }

    @After
    fun resetAfter() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun processorTimingIsExplicitBoundedResettableAndRetainedAfterStop() {
        val processor = configuredProcessor(limiterEnabled = true)
        val input = shortBuffer(ShortArray(64) { 12_000 })

        repeat(10) { process(processor, input) }
        EqualizerRuntimeBridge.publishStateForTest()
        assertFalse(
            EqualizerRuntimeBridge.state.value
                .processorPerformanceTelemetryEnabled
        )
        assertEquals(
            0L,
            EqualizerRuntimeBridge.state.value
                .processorPerformance.totalCallCount
        )

        EqualizerRuntimeBridge
            .setProcessorPerformanceTelemetryEnabled(true)
        repeat(50) { process(processor, input) }
        EqualizerRuntimeBridge.publishStateForTest()
        val measured = EqualizerRuntimeBridge.state.value
        assertTrue(measured.processorPerformanceTelemetryEnabled)
        assertEquals(
            50L,
            measured.processorPerformance.totalCallCount
        )
        assertEquals(
            50L * 64,
            measured.processorPerformance.totalFrameCount
        )
        assertEquals(
            50L,
            measured.processorPerformance.equalizedCallCount
        )
        assertEquals(
            50L,
            measured.processorPerformance.limiterCallCount
        )

        EqualizerRuntimeBridge
            .requestProcessorPerformanceTelemetryReset()
        process(processor, input)
        EqualizerRuntimeBridge.publishStateForTest()
        assertEquals(
            1L,
            EqualizerRuntimeBridge.state.value
                .processorPerformance.totalCallCount
        )

        repeat(3) {
            EqualizerRuntimeBridge.performanceTelemetry()
                .recordProcessingCall(
                    durationNanos = 1_000_000L,
                    frameCount = 1,
                    sampleRateHz = 192_000,
                    exactBypass = false,
                    equalized = true,
                    transitioning = false,
                    limiterActive = true
                )
        }
        EqualizerRuntimeBridge.publishStateForTest()
        val completed =
            EqualizerRuntimeBridge.state.value.processorPerformance
        assertEquals(4L, completed.totalCallCount)
        assertEquals(67L, completed.totalFrameCount)
        assertTrue(completed.deadlineMissCount >= 3L)
        assertTrue(completed.medianProcessingMillis > 0.0)
        assertTrue(completed.p95ProcessingMillis > 0.0)
        assertTrue(completed.p99ProcessingMillis > 0.0)
        assertTrue(completed.maximumProcessingMillis > 0.0)
        assertTrue(completed.medianRealTimeFactor > 0.0)
        assertTrue(completed.p95RealTimeFactor > 0.0)
        assertTrue(completed.p99RealTimeFactor > 0.0)
        assertTrue(completed.maximumRealTimeFactor > 0.0)

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
            EqualizerRuntimeBridge.state.value.processorPerformance
        )

        EqualizerRuntimeBridge
            .setProcessorPerformanceTelemetryEnabled(true)
        EqualizerRuntimeBridge.publishStateForTest()
        assertEquals(
            EqualizerProcessorPerformanceSnapshot(),
            EqualizerRuntimeBridge.state.value.processorPerformance
        )
        EqualizerRuntimeBridge
            .setProcessorPerformanceTelemetryEnabled(false)
        processor.reset()
    }

    @Test
    fun timingContinuesWithoutAnOpenDiagnosticsScreen() {
        val processor = configuredProcessor(limiterEnabled = false)
        val input = shortBuffer(ShortArray(64) { 4_000 })

        EqualizerRuntimeBridge
            .setProcessorPerformanceTelemetryEnabled(true)
        repeat(25) { process(processor, input) }
        // State publication represents the coordinator cadence. Nothing in
        // diagnostics-screen composition owns or stops the bridge flag.
        EqualizerRuntimeBridge.publishStateForTest()
        repeat(25) { process(processor, input) }
        EqualizerRuntimeBridge.publishStateForTest()

        assertTrue(
            EqualizerRuntimeBridge.state.value
                .processorPerformanceTelemetryEnabled
        )
        assertEquals(
            50L,
            EqualizerRuntimeBridge.state.value
                .processorPerformance.totalCallCount
        )
        processor.reset()
    }

    @Test
    fun limiterPublicationIsCadenceBoundInsteadOfPerBuffer() {
        val processor = configuredProcessor(limiterEnabled = true)
        val input = shortBuffer(shortArrayOf(12_000))
        val started = System.nanoTime()

        repeat(1_000) { process(processor, input) }

        val elapsed = System.nanoTime() - started
        val publications = processor.bufferReuseSnapshot()
            .limiterTelemetryPublicationCount
        val maximumExpected =
            elapsed / LIMITER_PUBLICATION_INTERVAL_NANOS + 2L
        assertTrue(
            "publications=$publications elapsed=$elapsed",
            publications <= maximumExpected
        )
        assertTrue(publications < 1_000L)
        processor.reset()
    }

    private fun configuredProcessor(
        limiterEnabled: Boolean
    ): EqualizerAudioProcessor {
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = -3.0,
                filters = listOf(
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 1_000.0,
                        gainDb = 6.0,
                        q = 1.4
                    )
                )
            ),
            automaticHeadroomEnabled = true,
            limiterConfiguration = LimiterConfiguration(
                enabled = limiterEnabled
            )
        )
        return EqualizerAudioProcessor().also { processor ->
            processor.configure(
                AudioFormat(
                    48_000,
                    1,
                    C.ENCODING_PCM_16BIT
                )
            )
            processor.flush(StreamMetadata.DEFAULT)
        }
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

    private fun shortBuffer(samples: ShortArray): ByteBuffer {
        return ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .also { buffer ->
                samples.forEach(buffer::putShort)
                buffer.flip()
            }
    }

    private companion object {
        const val LIMITER_PUBLICATION_INTERVAL_NANOS =
            20_000_000L
    }
}
