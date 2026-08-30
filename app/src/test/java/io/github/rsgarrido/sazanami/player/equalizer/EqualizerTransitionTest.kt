package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EqualizerTransitionTest {
    @Before
    fun resetBridgeBeforeTest() {
        EqualizerRuntimeBridge.release()
    }

    @After
    fun resetBridgeAfterTest() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun transitionDurationUsesActualSampleRate() {
        val state = EqualizerTransitionState(durationMillis = 20)

        state.start(sampleRateHz = 48_000)

        assertEquals(960, state.totalFrameCount)
        assertEquals(1.0 / 960.0, state.progressForNextFrame(), 1e-12)
        repeat(960) {
            state.advanceFrame()
        }
        assertFalse(state.isActive)
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerTransitionState(durationMillis = 10)
        }
    }

    @Test
    fun sameVersionFormatFlushReplacesStaleCrossfadeTelemetry() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = -3.0,
            filters = emptyList()
        )
        val snapshot = EqualizerRuntimeBridge.requestConfiguration(
            configuration = configuration,
            automaticHeadroomEnabled = false
        )
        val at48Khz = plan(
            version = snapshot.version,
            configuration = configuration,
            sampleRateHz = 48_000
        )
        val at44Khz = plan(
            version = snapshot.version,
            configuration = configuration,
            sampleRateHz = 44_100
        )

        EqualizerRuntimeBridge.publishAppliedPlan(
            at48Khz,
            EqualizerPlanApplicationMode.CROSSFADE
        )
        EqualizerRuntimeBridge.publishTransitionStarted(960, 48_000)
        EqualizerRuntimeBridge.publishTransitionInProgress(false)
        EqualizerRuntimeBridge.publishAppliedPlan(
            at44Khz,
            EqualizerPlanApplicationMode.DIRECT_AFTER_FLUSH
        )
        EqualizerRuntimeBridge.publishStateForTest()

        val state = EqualizerRuntimeBridge.state.value
        assertEquals(
            EqualizerPlanApplicationMode.DIRECT_AFTER_FLUSH,
            state.lastPlanApplicationMode
        )
        assertEquals(0, state.lastTransitionFrameCount)
        assertEquals(null, state.lastTransitionSampleRateHz)
    }

    @Test
    fun bypassToEqTransitionIsFrameSynchronousAndCompletesOnce() {
        val processor = bypassProcessor(channelCount = 2)
        establishCurrentStream(processor, channelCount = 2)
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(
                version = 1L,
                channelCount = 2,
                preampDb = -6.020_599_913_279_624
            ).createProcessingPath()
        )
        val samples = ShortArray(960 * 2) { 10_000 }

        processor.queueInput(shortBuffer(samples))
        val output = processor.output.toShortArray()
        EqualizerRuntimeBridge.publishStateForTest()

        assertEquals(960, processor.transitionFrameCount())
        repeat(960) { frameIndex ->
            assertEquals(
                output[frameIndex * 2],
                output[frameIndex * 2 + 1]
            )
        }
        assertTrue(output.first() > output.last())
        output.asList().zipWithNext().forEach { (first, second) ->
            assertTrue(abs(first.toInt() - second.toInt()) < 100)
        }
        assertFalse(
            EqualizerRuntimeBridge.state.value.transitionInProgress
        )
        assertEquals(
            1L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )
        assertEquals(
            EqualizerPlanApplicationMode.CROSSFADE,
            EqualizerRuntimeBridge.state.value.lastPlanApplicationMode
        )
        assertEquals(
            960,
            EqualizerRuntimeBridge.state.value.lastTransitionFrameCount
        )
        assertEquals(
            20.0,
            EqualizerRuntimeBridge.state.value.lastTransitionDurationMillis,
            0.0
        )
    }

    @Test
    fun largeBufferCrossfadeEndsAfter20msRatherThanSpanningTheBuffer() {
        val processor = bypassProcessor(channelCount = 1)
        establishCurrentStream(processor, channelCount = 1)
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(
                version = 1L,
                preampDb = -6.020_599_913_279_624
            ).createProcessingPath()
        )

        processor.queueInput(
            shortBuffer(ShortArray(4_800) { 10_000 })
        )
        val output = processor.output.toShortArray()

        assertTrue(output[0] > output[959])
        assertEquals(5_000, output[959].toInt())
        output.drop(960).forEach { sample ->
            assertEquals(5_000, sample.toInt())
        }
        assertEquals(960, processor.transitionFrameCount())
    }

    @Test
    fun eqToBypassTransitionCompletesWithExactSubsequentBypass() {
        val processor = processorWithInitialPlan(
            requestedActivePlan(version = 1L, preampDb = -6.0)
        )
        establishCurrentStream(processor, channelCount = 1)
        val bypassPlan = requestedPlan(
            version = 2L,
            configuration = EqualizerConfiguration(
                enabled = false,
                preampDb = 0.0,
                filters = emptyList()
            )
        )
        EqualizerRuntimeBridge.installPreparedPathForTest(
            bypassPlan.createProcessingPath()
        )
        processor.queueInput(shortBuffer(ShortArray(960) { 12_000 }))
        processor.output
        val bypassBytes = ByteArray(512) { index -> index.toByte() }
        val bypassInput = directBuffer(bypassBytes)

        processor.queueInput(bypassInput)
        val bypassOutput = processor.output

        assertArrayEquals(bypassBytes, bypassOutput.toByteArray())
        EqualizerRuntimeBridge.publishStateForTest()
        assertTrue(EqualizerRuntimeBridge.state.value.bypassed)
        assertEquals(
            2L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )
        assertEquals(
            EqualizerPlanApplicationMode.CROSSFADE,
            EqualizerRuntimeBridge.state.value.lastPlanApplicationMode
        )
    }

    @Test
    fun eqAToEqBTransitionUsesLatestPreparedPath() {
        val processor = processorWithInitialPlan(
            requestedActivePlan(version = 1L, preampDb = -3.0)
        )
        establishCurrentStream(processor, channelCount = 1)
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(version = 2L, preampDb = -9.0)
                .createProcessingPath()
        )

        processor.queueInput(shortBuffer(ShortArray(960) { 16_000 }))
        val output = processor.output.toShortArray()
        EqualizerRuntimeBridge.publishStateForTest()

        assertTrue(output.first() > output.last())
        assertEquals(
            2L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )
        assertEquals(
            EqualizerPlanApplicationMode.CROSSFADE,
            EqualizerRuntimeBridge.state.value.lastPlanApplicationMode
        )
        assertEquals(
            20.0,
            EqualizerRuntimeBridge.state.value.lastTransitionDurationMillis,
            0.0
        )
    }

    @Test
    fun rapidUpdatesCoalesceToNewestVersion() {
        val processor = bypassProcessor(channelCount = 1)
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(version = 1L, preampDb = -2.0)
                .createProcessingPath()
        )

        processor.queueInput(shortBuffer(ShortArray(480) { 10_000 }))
        processor.output
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(version = 2L, preampDb = -4.0)
                .createProcessingPath()
        )
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(version = 3L, preampDb = -8.0)
                .createProcessingPath()
        )
        processor.queueInput(shortBuffer(ShortArray(480) { 10_000 }))
        processor.output
        EqualizerRuntimeBridge.publishStateForTest()
        assertEquals(
            1L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )

        processor.queueInput(shortBuffer(ShortArray(960) { 10_000 }))
        processor.output
        EqualizerRuntimeBridge.publishStateForTest()

        assertEquals(
            3L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )
        assertFalse(
            EqualizerRuntimeBridge.state.value.transitionInProgress
        )
    }

    @Test
    fun stalePlanVersionIsIgnoredAfterNewerPlanApplied() {
        val processor = processorWithInitialPlan(
            requestedActivePlan(version = 3L, preampDb = -6.0)
        )
        EqualizerRuntimeBridge.installPreparedPathForTest(
            activePlan(version = 2L, preampDb = 6.0)
                .createProcessingPath()
        )
        processor.queueInput(shortBuffer(shortArrayOf(10_000)))
        processor.output
        EqualizerRuntimeBridge.publishStateForTest()

        assertEquals(
            3L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )
    }

    @Test
    fun flushCancelsTransitionAndClearsFilterHistory() {
        val filterConfiguration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(1_000.0, 6.0, 1.41)
            )
        )
        val processor = processorWithInitialPlan(
            requestedPlan(
                version = 1L,
                configuration = filterConfiguration
            )
        )
        processor.queueInput(
            shortBuffer(
                ShortArray(128).also { samples -> samples[0] = 12_000 }
            )
        )
        processor.output
        EqualizerRuntimeBridge.installPreparedPathForTest(
            requestedActivePlan(version = 2L, preampDb = -3.0)
                .createProcessingPath()
        )
        processor.queueInput(shortBuffer(ShortArray(100) { 5_000 }))
        processor.output
        EqualizerRuntimeBridge.publishStateForTest()
        assertTrue(
            EqualizerRuntimeBridge.state.value.transitionInProgress
        )

        processor.flush(StreamMetadata.DEFAULT)
        EqualizerRuntimeBridge.publishStateForTest()

        assertFalse(
            EqualizerRuntimeBridge.state.value.transitionInProgress
        )
        assertEquals(
            2L,
            EqualizerRuntimeBridge.state.value.appliedPlanVersion
        )
        assertEquals(
            EqualizerPlanApplicationMode.DIRECT_AFTER_FLUSH,
            EqualizerRuntimeBridge.state.value.lastPlanApplicationMode
        )
    }

    @Test
    fun postFlushImpulseMatchesFreshStream() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = listOf(
                EqualizerFilterSpec.LowShelf(200.0, 6.0, 0.8)
            )
        )
        val preparedPlan = requestedPlan(1L, configuration)
        val processor = processorWithInitialPlan(preparedPlan)
        val impulse = ShortArray(512).also { samples ->
            samples[0] = 10_000
        }
        processor.queueInput(shortBuffer(impulse))
        processor.output
        processor.flush(StreamMetadata.DEFAULT)
        processor.queueInput(shortBuffer(impulse))
        val afterFlush = processor.output.toShortArray()

        EqualizerRuntimeBridge.release()
        val fresh = processorWithInitialPlan(
            requestedPlan(1L, configuration)
        )
        fresh.queueInput(shortBuffer(impulse))
        val freshOutput = fresh.output.toShortArray()

        assertArrayEquals(freshOutput, afterFlush)
    }

    @Test
    fun resetReturnsProcessorToUnconfiguredState() {
        val processor = bypassProcessor(channelCount = 1)

        processor.reset()
        EqualizerRuntimeBridge.publishStateForTest()

        assertFalse(processor.isActive)
        assertFalse(
            EqualizerRuntimeBridge.state.value.processorConfigured
        )
        assertEquals(
            null,
            EqualizerRuntimeBridge.state.value.sampleRateHz
        )
    }

    private fun bypassProcessor(
        channelCount: Int
    ): EqualizerAudioProcessor {
        val processor = EqualizerAudioProcessor()
        processor.configure(
            AudioFormat(
                48_000,
                channelCount,
                C.ENCODING_PCM_16BIT
            )
        )
        processor.flush(StreamMetadata.DEFAULT)
        return processor
    }

    private fun establishCurrentStream(
        processor: EqualizerAudioProcessor,
        channelCount: Int
    ) {
        processor.queueInput(
            shortBuffer(ShortArray(channelCount))
        )
        processor.output
    }

    private fun processorWithInitialPlan(
        preparedPlan: PreparedEqualizerPlan
    ): EqualizerAudioProcessor {
        val processor = EqualizerAudioProcessor()
        processor.configure(
            AudioFormat(
                preparedPlan.processorFormat.sampleRateHz,
                preparedPlan.processorFormat.channelCount,
                C.ENCODING_PCM_16BIT
            )
        )
        EqualizerRuntimeBridge.installPreparedPathForTest(
            preparedPlan.createProcessingPath()
        )
        processor.flush(StreamMetadata.DEFAULT)
        return processor
    }

    private fun activePlan(
        version: Long,
        channelCount: Int = 1,
        preampDb: Double
    ): PreparedEqualizerPlan {
        return plan(
            version = version,
            channelCount = channelCount,
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = preampDb,
                filters = emptyList()
            )
        )
    }

    private fun requestedActivePlan(
        version: Long,
        channelCount: Int = 1,
        preampDb: Double
    ): PreparedEqualizerPlan {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = preampDb,
            filters = emptyList()
        )
        return requestedPlan(
            version = version,
            configuration = configuration,
            channelCount = channelCount
        )
    }

    private fun requestedPlan(
        version: Long,
        configuration: EqualizerConfiguration,
        channelCount: Int = 1
    ): PreparedEqualizerPlan {
        var snapshot = EqualizerRuntimeBridge.requestedSnapshot()
        while (snapshot.version < version) {
            snapshot = EqualizerRuntimeBridge.requestConfiguration(
                configuration = configuration,
                automaticHeadroomEnabled = false
            )
        }
        assertEquals(version, snapshot.version)
        return plan(
            version = version,
            configuration = configuration,
            channelCount = channelCount
        )
    }

    private fun plan(
        version: Long,
        configuration: EqualizerConfiguration,
        channelCount: Int = 1,
        sampleRateHz: Int = 48_000
    ): PreparedEqualizerPlan {
        return EqualizerPlanPreparer.prepare(
            EqualizerRuntimeSnapshot(
                version = version,
                configuration = configuration,
                automaticHeadroomEnabled = false
            ),
            EqualizerProcessorFormat(
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                pcmEncoding = C.ENCODING_PCM_16BIT
            )
        )
    }

    private fun shortBuffer(samples: ShortArray): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach(buffer::putShort)
        buffer.flip()
        return buffer
    }

    private fun directBuffer(bytes: ByteArray): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }

    private fun ByteBuffer.toShortArray(): ShortArray {
        return ShortArray(remaining() / Short.SIZE_BYTES) {
            short
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        return ByteArray(remaining()).also(::get)
    }
}
