package io.github.rsgarrido.sazanami.player.equalizer.performance

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerAudioProcessor
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class EqualizerProcessorBenchmarkTest {
    @After
    fun releaseBridge() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun measuredProcessorScenariosReportPercentilesAndAllocations() {
        assumeTrue(
            "Run with -Dequalizer.performance=true",
            java.lang.Boolean.getBoolean("equalizer.performance")
        )
        val scenarios = listOf(
            Scenario(
                name = "flat-bypass",
                configuration = configuration("flat"),
                sampleRateHz = 48_000,
                channelCount = 2,
                frameCount = 256
            ),
            Scenario(
                name = "graphic-moderate",
                configuration = configuration("graphic-moderate"),
                sampleRateHz = 48_000,
                channelCount = 2,
                frameCount = 256
            ),
            Scenario(
                name = "graphic-worst-high-rate-surround",
                configuration = configuration("graphic-worst"),
                sampleRateHz = 192_000,
                channelCount = 6,
                frameCount = 4_096,
                automaticHeadroomEnabled = false,
                limiterEnabled = true
            ),
            Scenario(
                name = "parametric-realistic",
                configuration = configuration("parametric-realistic"),
                sampleRateHz = 96_000,
                channelCount = 2,
                frameCount = 512
            ),
            Scenario(
                name = "parametric-high-q-small-buffer",
                configuration = configuration("parametric-high-q"),
                sampleRateHz = 48_000,
                channelCount = 2,
                frameCount = 128,
                limiterEnabled = true
            ),
            Scenario(
                name = "parametric-high-q-no-headroom",
                configuration = configuration("parametric-high-q"),
                sampleRateHz = 48_000,
                channelCount = 2,
                frameCount = 128,
                automaticHeadroomEnabled = false,
                limiterEnabled = true
            ),
            Scenario(
                name = "parametric-all-types-surround",
                configuration = configuration("parametric-all-types"),
                sampleRateHz = 48_000,
                channelCount = 6,
                frameCount = 1_024
            ),
            Scenario(
                name = "parametric-realistic-limiter",
                configuration = configuration("parametric-realistic"),
                sampleRateHz = 48_000,
                channelCount = 2,
                frameCount = 256,
                limiterEnabled = true
            )
        )

        println(
            "PHASE_F_PROCESSOR_METRICS " +
                "scenario,median_ms,p90_ms,p95_ms,p99_ms,max_ms," +
                "median_rtf,p95_rtf,p99_rtf,max_rtf," +
                "median_frames_per_second,allocated_bytes_per_call"
        )
        val results = scenarios.associateWith(::measureScenario)
        results.forEach { (scenario, result) ->
            println(
                "PHASE_F_PROCESSOR_METRICS " +
                    "${scenario.name}," +
                    "${result.medianMillis}," +
                    "${result.p90Millis}," +
                    "${result.p95Millis}," +
                    "${result.p99Millis}," +
                    "${result.maximumMillis}," +
                    "${result.medianRealTimeFactor}," +
                    "${result.p95RealTimeFactor}," +
                    "${result.p99RealTimeFactor}," +
                    "${result.maximumRealTimeFactor}," +
                    "${result.medianFramesPerSecond}," +
                    result.allocatedBytesPerCall
            )
            assertTrue(
                "${scenario.name} steady allocation was " +
                    "${result.allocatedBytesPerCall} bytes/call",
                result.allocatedBytesPerCall <=
                    MAXIMUM_STEADY_ALLOCATED_BYTES_PER_CALL
            )
        }
        val bypass = results.entries
            .single { it.key.name == "flat-bypass" }
            .value
        results.forEach { (scenario, result) ->
            val relativeMedian =
                result.medianRealTimeFactor /
                    bypass.medianRealTimeFactor
            println(
                "PHASE_F_PROCESSOR_RELATIVE " +
                    "${scenario.name}/flat-bypass=$relativeMedian"
            )
            assertTrue(
                "${scenario.name} was catastrophically slower than " +
                    "the same-run bypass baseline",
                relativeMedian < MAXIMUM_RELATIVE_MEDIAN_COST
            )
        }
    }

    private fun measureScenario(scenario: Scenario): Result {
        EqualizerRuntimeBridge.release()
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = scenario.configuration,
            automaticHeadroomEnabled =
                scenario.automaticHeadroomEnabled,
            limiterConfiguration = LimiterConfiguration(
                enabled = scenario.limiterEnabled
            )
        )
        val processor = EqualizerAudioProcessor()
        processor.configure(
            AudioFormat(
                scenario.sampleRateHz,
                scenario.channelCount,
                C.ENCODING_PCM_16BIT
            )
        )
        processor.flush(StreamMetadata.DEFAULT)
        val input = pcmInput(scenario)
        repeat(WARM_UP_CALL_COUNT) {
            processOneBuffer(processor, input)
        }
        val warmSnapshot = processor.bufferReuseSnapshot()
        val iterationCount = (
            TARGET_MEASURED_FRAMES / scenario.frameCount
            ).coerceIn(
            MINIMUM_MEASURED_CALL_COUNT,
            MAXIMUM_MEASURED_CALL_COUNT
        )
        val durations = LongArray(iterationCount)
        repeat(iterationCount) { index ->
            val started = System.nanoTime()
            processOneBuffer(processor, input)
            durations[index] = System.nanoTime() - started
        }
        val afterTimingSnapshot = processor.bufferReuseSnapshot()
        assertReusableCapacityEquals(
            warmSnapshot,
            afterTimingSnapshot
        )

        val allocationReader =
            HotSpotThreadAllocationReader.create()
        val allocatedBytesPerCall = if (
            allocationReader != null
        ) {
            repeat(WARM_UP_CALL_COUNT) {
                processOneBuffer(processor, input)
            }
            val before = allocationReader.currentThreadBytes()
            repeat(ALLOCATION_MEASURED_CALL_COUNT) {
                processOneBuffer(processor, input)
            }
            val after = allocationReader.currentThreadBytes()
            (after - before).toDouble() /
                ALLOCATION_MEASURED_CALL_COUNT
        } else {
            0.0
        }
        assertReusableCapacityEquals(
            afterTimingSnapshot,
            processor.bufferReuseSnapshot()
        )
        processor.reset()

        durations.sort()
        val audioDurationNanos =
            scenario.frameCount.toDouble() *
                NANOS_PER_SECOND / scenario.sampleRateHz
        return Result(
            medianMillis = durations.percentile(0.50).toMillis(),
            p90Millis = durations.percentile(0.90).toMillis(),
            p95Millis = durations.percentile(0.95).toMillis(),
            p99Millis = durations.percentile(0.99).toMillis(),
            maximumMillis = durations.last().toMillis(),
            medianRealTimeFactor =
                durations.percentile(0.50) / audioDurationNanos,
            p95RealTimeFactor =
                durations.percentile(0.95) / audioDurationNanos,
            p99RealTimeFactor =
                durations.percentile(0.99) / audioDurationNanos,
            maximumRealTimeFactor =
                durations.last() / audioDurationNanos,
            medianFramesPerSecond =
                scenario.frameCount /
                    (
                        durations.percentile(0.50) /
                            NANOS_PER_SECOND
                        ),
            allocatedBytesPerCall = allocatedBytesPerCall
        )
    }

    private fun processOneBuffer(
        processor: EqualizerAudioProcessor,
        input: ByteBuffer
    ) {
        input.position(0)
        processor.queueInput(input)
        val output = processor.output
        if (output.hasRemaining()) {
            output.getShort(output.position())
            output.position(output.limit())
        }
    }

    private fun pcmInput(scenario: Scenario): ByteBuffer {
        val sampleCount =
            scenario.frameCount * scenario.channelCount
        val buffer = ByteBuffer
            .allocateDirect(sampleCount * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        repeat(sampleCount) { sampleIndex ->
            val frameIndex = sampleIndex / scenario.channelCount
            val sample = (
                sin(
                    2.0 * PI * 997.0 * frameIndex /
                        scenario.sampleRateHz
                ) * 11_000.0
                ).toInt().toShort()
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }

    private fun configuration(name: String): EqualizerConfiguration =
        EqualizerPerformanceFixtures.namedConfigurations
            .getValue(name)

    private fun assertReusableCapacityEquals(
        expected:
            io.github.rsgarrido.sazanami.player.equalizer.EqualizerBufferReuseSnapshot,
        actual:
            io.github.rsgarrido.sazanami.player.equalizer.EqualizerBufferReuseSnapshot
    ) {
        assertEquals(expected.scratchCapacity, actual.scratchCapacity)
        assertEquals(
            expected.inputScratchIdentity,
            actual.inputScratchIdentity
        )
        assertEquals(
            expected.currentOutputScratchIdentity,
            actual.currentOutputScratchIdentity
        )
        assertEquals(
            expected.pendingOutputScratchIdentity,
            actual.pendingOutputScratchIdentity
        )
        assertEquals(
            expected.postEqualizerScratchIdentity,
            actual.postEqualizerScratchIdentity
        )
        assertEquals(
            expected.limiterOutputScratchIdentity,
            actual.limiterOutputScratchIdentity
        )
        assertEquals(
            expected.scratchBufferGrowthCount,
            actual.scratchBufferGrowthCount
        )
        assertEquals(expected.outputCapacity, actual.outputCapacity)
        assertEquals(
            expected.outputBufferGrowthCount,
            actual.outputBufferGrowthCount
        )
        assertEquals(
            expected.currentEngineCapacity,
            actual.currentEngineCapacity
        )
        assertEquals(
            expected.pendingEngineCapacity,
            actual.pendingEngineCapacity
        )
        assertEquals(expected.limiterCapacity, actual.limiterCapacity)
    }

    private fun LongArray.percentile(percentile: Double): Long {
        val index = (ceil(percentile * size).toInt() - 1)
            .coerceIn(0, lastIndex)
        return this[index]
    }

    private fun Long.toMillis(): Double =
        this / NANOS_PER_MILLISECOND

    private data class Scenario(
        val name: String,
        val configuration: EqualizerConfiguration,
        val sampleRateHz: Int,
        val channelCount: Int,
        val frameCount: Int,
        val automaticHeadroomEnabled: Boolean = true,
        val limiterEnabled: Boolean = false
    )

    private data class Result(
        val medianMillis: Double,
        val p90Millis: Double,
        val p95Millis: Double,
        val p99Millis: Double,
        val maximumMillis: Double,
        val medianRealTimeFactor: Double,
        val p95RealTimeFactor: Double,
        val p99RealTimeFactor: Double,
        val maximumRealTimeFactor: Double,
        val medianFramesPerSecond: Double,
        val allocatedBytesPerCall: Double
    )

    private companion object {
        const val WARM_UP_CALL_COUNT = 200
        const val TARGET_MEASURED_FRAMES = 1_000_000
        const val MINIMUM_MEASURED_CALL_COUNT = 64
        const val MAXIMUM_MEASURED_CALL_COUNT = 2_000
        const val ALLOCATION_MEASURED_CALL_COUNT = 1_000
        const val MAXIMUM_STEADY_ALLOCATED_BYTES_PER_CALL = 16.0
        const val MAXIMUM_RELATIVE_MEDIAN_COST = 5_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
