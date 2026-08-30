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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EqualizerDspRuntimeTest {
    @Before
    fun setUp() {
        EqualizerRuntimeBridge.release()
    }

    @After
    fun tearDown() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun processorsOwnIndependentFilterLimiterFormatAndBufferState() {
        val runtimeA = EqualizerRuntimeBridge.createRuntime()
        val runtimeB = EqualizerRuntimeBridge.createRuntime()
        val processorA = EqualizerAudioProcessor(runtimeA)
        val processorB = EqualizerAudioProcessor(runtimeB)
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = activeConfiguration(preampDb = -1.5, gainDb = 4.0),
            automaticHeadroomEnabled = false,
            limiterConfiguration = LimiterConfiguration(
                enabled = true,
                ceilingDbfs = -1.0
            )
        )
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)

        processorA.configure(format)
        processorA.flush(StreamMetadata.DEFAULT)
        processorB.configure(format)
        processorB.flush(StreamMetadata.DEFAULT)
        processorA.queueInput(pcmBuffer(frameCount = 600, channelCount = 2))
        processorA.output
        processorB.queueInput(pcmBuffer(frameCount = 600, channelCount = 2))
        processorB.output

        val stateA = processorA.bufferReuseSnapshot()
        val stateBBeforeReset = processorB.bufferReuseSnapshot()
        val engineA = requireNotNull(stateA.currentEngineCapacity)
        val engineB = requireNotNull(stateBBeforeReset.currentEngineCapacity)
        val limiterA = requireNotNull(stateA.limiterCapacity)
        val limiterB = requireNotNull(stateBBeforeReset.limiterCapacity)
        assertNotSame(runtimeA, runtimeB)
        assertNotEquals(
            engineA.stateArrayIdentity,
            engineB.stateArrayIdentity
        )
        assertNotEquals(
            limiterA.audioDelayIdentity,
            limiterB.audioDelayIdentity
        )
        assertNotEquals(
            stateA.inputScratchIdentity,
            stateBBeforeReset.inputScratchIdentity
        )

        processorA.configure(
            AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        )
        processorA.flush(StreamMetadata.DEFAULT)
        runtimeA.publishStateForTest()
        runtimeB.publishStateForTest()
        assertEquals(44_100, runtimeA.state.value.sampleRateHz)
        assertEquals(48_000, runtimeB.state.value.sampleRateHz)

        processorA.flush(StreamMetadata.DEFAULT)
        assertEquals(
            stateBBeforeReset.currentEngineCapacity?.stateArrayIdentity,
            processorB.bufferReuseSnapshot().currentEngineCapacity?.stateArrayIdentity
        )
        processorA.reset()
        runtimeA.publishStateForTest()
        runtimeB.publishStateForTest()

        val stateBAfterReset = processorB.bufferReuseSnapshot()
        assertEquals(
            stateBBeforeReset.currentEngineCapacity?.stateArrayIdentity,
            stateBAfterReset.currentEngineCapacity?.stateArrayIdentity
        )
        assertEquals(
            stateBBeforeReset.limiterCapacity?.audioDelayIdentity,
            stateBAfterReset.limiterCapacity?.audioDelayIdentity
        )
        assertEquals(null, runtimeA.state.value.sampleRateHz)
        assertEquals(48_000, runtimeB.state.value.sampleRateHz)
        assertTrue(runtimeB.state.value.limiterPrimed)
    }

    @Test
    fun sharedRequestsProduceDistinctPreparedStateInEveryRuntime() {
        val runtimeA = EqualizerRuntimeBridge.createRuntime()
        val runtimeB = EqualizerRuntimeBridge.createRuntime()
        val first = EqualizerRuntimeBridge.requestConfiguration(
            configuration = activeConfiguration(preampDb = -2.0, gainDb = 3.5),
            automaticHeadroomEnabled = true,
            mode = EqualizerMode.PARAMETRIC,
            limiterConfiguration = LimiterConfiguration(
                enabled = true,
                ceilingDbfs = -2.0
            )
        )
        val format = EqualizerProcessorFormat(
            sampleRateHz = 48_000,
            channelCount = 2,
            pcmEncoding = C.ENCODING_PCM_16BIT
        )

        assertSame(first, runtimeA.requestedSnapshot())
        assertSame(first, runtimeB.requestedSnapshot())
        assertEquals(-2.0, runtimeA.requestedSnapshot().configuration.preampDb, 0.0)
        assertEquals(
            3.5,
            (runtimeB.requestedSnapshot().configuration.filters.single()
                as EqualizerFilterSpec.Peaking).gainDb,
            0.0
        )
        val pathA = runtimeA.prepareForProcessorFormat(format)
        val pathB = runtimeB.prepareForProcessorFormat(format)
        val limiterA = runtimeA.latestCompatibleLimiterConfiguration(format)
        val limiterB = runtimeB.latestCompatibleLimiterConfiguration(format)

        assertNotSame(pathA, pathB)
        assertNotSame(pathA.plan, pathB.plan)
        assertNotSame(limiterA, limiterB)
        assertEquals(first.version, pathA.plan.sourceSnapshotVersion)
        assertEquals(first.version, pathB.plan.sourceSnapshotVersion)
        assertTrue(requireNotNull(limiterA).enabled)
        assertTrue(requireNotNull(limiterB).enabled)

        val second = EqualizerRuntimeBridge.requestConfiguration(
            configuration = activeConfiguration(preampDb = -4.0, gainDb = 6.0),
            automaticHeadroomEnabled = false,
            limiterConfiguration = LimiterConfiguration(enabled = false)
        )

        assertSame(second, runtimeA.requestedSnapshot())
        assertSame(second, runtimeB.requestedSnapshot())
        assertTrue(second.version > first.version)
        assertFalse(second.limiterConfiguration.enabled)

        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = true,
            bypassed = true
        )
        runtimeA.publishStateForTest()
        runtimeB.publishStateForTest()
        assertTrue(runtimeA.state.value.comparisonSessionActive)
        assertTrue(runtimeA.state.value.comparisonBypassed)
        assertTrue(runtimeB.state.value.comparisonSessionActive)
        assertTrue(runtimeB.state.value.comparisonBypassed)

        val disabled = EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = false,
                preampDb = 0.0,
                filters = emptyList()
            ),
            automaticHeadroomEnabled = false,
            limiterConfiguration = LimiterConfiguration(enabled = false)
        )
        assertSame(disabled, runtimeA.requestedSnapshot())
        assertSame(disabled, runtimeB.requestedSnapshot())
        assertFalse(runtimeA.requestedSnapshot().configuration.enabled)
        assertFalse(runtimeB.requestedSnapshot().configuration.enabled)
    }

    @Test
    fun onlySelectedRuntimePublishesApplicationTelemetry() {
        val runtimeA = EqualizerRuntimeBridge.createRuntime()
        val runtimeB = EqualizerRuntimeBridge.createRuntime()
        val formatA = EqualizerProcessorFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        val formatB = EqualizerProcessorFormat(96_000, 2, C.ENCODING_PCM_16BIT)
        runtimeA.publishProcessorFormat(formatA)
        runtimeA.publishProcessorConfigured(configured = true, bypassed = true)
        runtimeA.publishStateForTest()
        runtimeB.publishProcessorFormat(formatB)
        runtimeB.publishProcessorConfigured(configured = true, bypassed = true)
        runtimeB.publishStateForTest()

        EqualizerRuntimeBridge.selectTelemetryRuntime(runtimeA)
        assertSame(runtimeA, EqualizerRuntimeBridge.selectedTelemetryRuntime())
        assertEquals(44_100, EqualizerRuntimeBridge.state.value.sampleRateHz)

        EqualizerRuntimeBridge.selectTelemetryRuntime(runtimeB)
        assertSame(runtimeB, EqualizerRuntimeBridge.selectedTelemetryRuntime())
        assertEquals(96_000, EqualizerRuntimeBridge.state.value.sampleRateHz)

        runtimeA.publishTransitionStarted(totalFrameCount = 2_000, sampleRateHz = 44_100)
        runtimeA.publishStateForTest()
        assertEquals(96_000, EqualizerRuntimeBridge.state.value.sampleRateHz)
        assertFalse(EqualizerRuntimeBridge.state.value.transitionInProgress)

        runtimeB.publishTransitionStarted(totalFrameCount = 2_000, sampleRateHz = 96_000)
        runtimeB.publishStateForTest()
        assertTrue(EqualizerRuntimeBridge.state.value.transitionInProgress)
    }

    private fun activeConfiguration(
        preampDb: Double,
        gainDb: Double
    ): EqualizerConfiguration = EqualizerConfiguration(
        enabled = true,
        preampDb = preampDb,
        filters = listOf(
            EqualizerFilterSpec.Peaking(
                frequencyHz = 1_000.0,
                gainDb = gainDb,
                q = 1.2
            )
        )
    )

    private fun pcmBuffer(frameCount: Int, channelCount: Int): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(frameCount * channelCount * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        repeat(frameCount * channelCount) { sampleIndex ->
            buffer.putShort(if (sampleIndex % 11 == 0) 20_000 else 4_000)
        }
        buffer.flip()
        return buffer
    }
}
