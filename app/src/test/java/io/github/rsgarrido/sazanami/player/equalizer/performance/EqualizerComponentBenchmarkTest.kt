package io.github.rsgarrido.sazanami.player.equalizer.performance

import androidx.media3.common.C
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPlanPreparer
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerProcessorFormat
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeSnapshot
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerTransitionState
import io.github.rsgarrido.sazanami.player.equalizer.Pcm16SampleConversion
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileExporter
import io.github.rsgarrido.sazanami.player.equalizer.interchange.SazanamiPresetFile
import io.github.rsgarrido.sazanami.player.equalizer.interchange.SazanamiPresetFileJson
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileLimits
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileParser
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterMath
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterPreparedConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterTelemetryAccumulator
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LookaheadLimiterEngine
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerAnalysisCalculator
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerAnalysisRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test

class EqualizerComponentBenchmarkTest {
    @After
    fun releaseBridge() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun reportsSeparatedComponentCosts() {
        assumeTrue(
            "Run with -Dequalizer.performance=true",
            java.lang.Boolean.getBoolean("equalizer.performance")
        )
        val results = listOf(
            measurePcmConversion(),
            measurePcmInputConversion(),
            measurePcmOutputQuantization(),
            measureDspOnly(),
            measureRealisticParametricWithoutLimiter(),
            measureTransitionOnly(),
            measureLimiterOnly(),
            measureFormerPerFrameGainTelemetryModel(),
            measureBufferedGainTelemetry(),
            measureFormerPerSamplePeakTelemetryModel(),
            measurePreLimiterTelemetry(),
            measureLimiterNoReduction(),
            measureLimiterFrequentReduction(),
            measureFlatEqWithLimiter(),
            measureParametricWithLimiter(),
            measureLimiterWithOuterTimingOnly(),
            measurePostLimiterTelemetry(),
            measureHighQSilenceAfterImpulse(),
            measurePlanPreparation(),
            measureSynchronousFormatPreparation(),
            measureAnalysis(),
            measureImport(),
            measureMaximumDeclarationImport(),
            measureNearMaximumMalformedImport(),
            measureTwoThousandLineImport(),
            measureNativePresetJson(),
            measureExport()
        )
        println(
            "PHASE_F_COMPONENT_METRICS " +
                "component,median_ms,p90_ms,p95_ms,p99_ms,max_ms"
        )
        results.forEach { result ->
            println(
                "PHASE_F_COMPONENT_METRICS " +
                    "${result.name},${result.medianMillis}," +
                    "${result.p90Millis},${result.p95Millis}," +
                    "${result.p99Millis},${result.maximumMillis}"
            )
            assertTrue(
                "${result.name} returned an invalid measurement",
                result.medianMillis >= 0.0 &&
                    result.maximumMillis >= result.medianMillis
            )
        }
    }

    private fun measurePcmConversion(): ComponentResult {
        val sampleCount = 4_096 * 6
        val input = ByteBuffer
            .allocateDirect(sampleCount * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        repeat(sampleCount) { input.putShort((it % 20_000).toShort()) }
        input.flip()
        val floats = FloatArray(sampleCount)
        val output = ByteBuffer
            .allocateDirect(sampleCount * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        return measure("pcm16-conversion", 400) {
            input.position(0)
            output.clear()
            Pcm16SampleConversion.decode(input, floats, sampleCount)
            Pcm16SampleConversion.encode(floats, output, sampleCount)
        }
    }

    private fun measurePcmInputConversion(): ComponentResult {
        val sampleCount = 4_096 * 2
        val input = ByteBuffer
            .allocateDirect(sampleCount * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        repeat(sampleCount) { input.putShort((it % 20_000).toShort()) }
        input.flip()
        val floats = FloatArray(sampleCount)
        return measure("stage-pcm16-input-conversion", 500) {
            input.position(0)
            Pcm16SampleConversion.decode(input, floats, sampleCount)
        }
    }

    private fun measurePcmOutputQuantization(): ComponentResult {
        val sampleCount = 4_096 * 2
        val floats = FloatArray(sampleCount) { index ->
            ((index % 2_000) - 1_000) / 1_000.0f
        }
        val output = ByteBuffer
            .allocateDirect(sampleCount * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        return measure("stage-pcm16-output-quantization", 500) {
            output.clear()
            Pcm16SampleConversion.encode(floats, output, sampleCount)
        }
    }

    private fun measureDspOnly(): ComponentResult {
        val plan = EqualizerPlanPreparer.prepare(
            snapshot = EqualizerRuntimeSnapshot(
                version = 1L,
                configuration = EqualizerPerformanceFixtures
                    .namedConfigurations
                    .getValue("graphic-worst"),
                automaticHeadroomEnabled = false
            ),
            processorFormat = EqualizerProcessorFormat(
                sampleRateHz = 192_000,
                channelCount = 6,
                pcmEncoding = C.ENCODING_PCM_16BIT
            )
        )
        val path = plan.createProcessingPath()
        val input = EqualizerPerformanceFixtures.pcmSignal(
            frameCount = 4_096,
            channelCount = 6,
            sampleRateHz = 192_000
        )
        val output = FloatArray(input.size)
        return measure("dsp-worst", 200) {
            path.process(input, output, 4_096)
        }
    }

    private fun measureRealisticParametricWithoutLimiter():
        ComponentResult {
        val path = realisticParametricPath()
        val input = EqualizerPerformanceFixtures.pcmSignal(
            frameCount = 4_096,
            channelCount = 2,
            sampleRateHz = 44_100
        )
        val output = FloatArray(input.size)
        return measure(
            "stage-eq-cascade-parametric-ten-filter",
            400
        ) {
            path.process(input, output, 4_096)
        }
    }

    private fun measureTransitionOnly(): ComponentResult {
        val transition = EqualizerTransitionState()
        var sink = 0.0
        return measure("transition-192k-six-channel", 400) {
            transition.start(192_000)
            while (transition.isActive) {
                val progress = transition.progressForNextFrame()
                repeat(6) {
                    sink += progress * 0.000_000_001
                }
                transition.advanceFrame()
            }
        }.also {
            assertTrue(sink.isFinite())
        }
    }

    private fun measureLimiterOnly(): ComponentResult {
        val telemetry = LimiterTelemetryAccumulator()
        val engine = LookaheadLimiterEngine(
            preparedConfiguration =
                LimiterPreparedConfiguration.prepare(
                    configuration = LimiterConfiguration(
                        enabled = true
                    ),
                    sampleRateHz = 192_000,
                    channelCount = 6,
                    configurationVersion = 1L
                ),
            telemetry = telemetry
        )
        val input = FloatArray(4_096 * 6) { index ->
            if (index % 17 == 0) 1.25f else 0.4f
        }
        val output = FloatArray(input.size)
        return measure("limiter-192k-six-channel", 300) {
            telemetry.beginProcessingCall()
            engine.process(input, 0, 4_096, output, 0)
        }
    }

    private fun measurePreLimiterTelemetry(): ComponentResult {
        val telemetry = LimiterTelemetryAccumulator()
        val input = frequentReductionInput()
        return measure("stage-pre-limiter-telemetry", 500) {
            telemetry.beginProcessingCall()
            var peak = 0.0
            var overRange = 0L
            var index = 0
            while (index < input.size) {
                val magnitude = abs(input[index].toDouble())
                peak = max(peak, magnitude)
                if (magnitude > 1.0) overRange++
                index++
            }
            telemetry.observePreLimiterBlock(peak, overRange)
        }
    }

    private fun measureFormerPerFrameGainTelemetryModel():
        ComponentResult {
        val gains = DoubleArray(4_096) { index ->
            0.55 + (index % 400) / 1_000.0
        }
        var sink = 0.0
        return measure(
            "former-per-frame-gain-db-telemetry-model",
            500
        ) {
            var current = 0.0
            var maximum = 0.0
            var index = 0
            while (index < gains.size) {
                val reduction =
                    LimiterMath.gainReductionDb(gains[index])
                current = reduction
                maximum = max(maximum, reduction)
                index++
            }
            sink += current + maximum
        }.also {
            assertTrue(sink > 0.0)
        }
    }

    private fun measureBufferedGainTelemetry(): ComponentResult {
        val telemetry = LimiterTelemetryAccumulator()
        return measure("buffered-linear-gain-telemetry", 500) {
            telemetry.beginProcessingCall()
            telemetry.observeLimiterBlock(
                finalLinearGain = 0.75,
                minimumLinearGain = 0.55,
                activeFrames = 4_096,
                reducedFrames = 4_096
            )
        }
    }

    private fun measureFormerPerSamplePeakTelemetryModel():
        ComponentResult {
        val telemetry = LimiterTelemetryAccumulator()
        val input = frequentReductionInput()
        return measure(
            "former-per-sample-pre-post-telemetry-model",
            500
        ) {
            telemetry.beginProcessingCall()
            var index = 0
            while (index < input.size) {
                telemetry.observePreLimiterSample(input[index])
                telemetry.observePostLimiterSample(input[index] * 0.7f)
                index++
            }
        }
    }

    private fun measurePostLimiterTelemetry(): ComponentResult {
        val telemetry = LimiterTelemetryAccumulator()
        val output = frequentReductionInput()
        return measure("stage-post-limiter-telemetry", 500) {
            var peak = 0.0
            var index = 0
            while (index < output.size) {
                peak = max(peak, abs(output[index].toDouble()))
                index++
            }
            telemetry.observePostLimiterBlock(peak)
        }
    }

    private fun measureLimiterNoReduction(): ComponentResult =
        measureLimiterVariant(
            name = "stage-limiter-no-gain-reduction",
            input = FloatArray(4_096 * 2) { 0.2f },
            telemetry = LimiterTelemetryAccumulator()
        )

    private fun measureLimiterFrequentReduction(): ComponentResult =
        measureLimiterVariant(
            name = "stage-limiter-frequent-gain-reduction",
            input = frequentReductionInput(),
            telemetry = LimiterTelemetryAccumulator()
        )

    private fun measureFlatEqWithLimiter(): ComponentResult =
        measureLimiterVariant(
            name = "flat-eq-plus-limiter",
            input = EqualizerPerformanceFixtures.pcmSignal(
                frameCount = 4_096,
                channelCount = 2,
                sampleRateHz = 44_100
            ),
            telemetry = LimiterTelemetryAccumulator()
        )

    private fun measureParametricWithLimiter(): ComponentResult {
        val path = realisticParametricPath()
        val telemetry = LimiterTelemetryAccumulator()
        val engine = limiterEngine(telemetry)
        val input = frequentReductionInput()
        val equalized = FloatArray(input.size)
        val limited = FloatArray(input.size)
        return measure("parametric-ten-filter-plus-limiter", 400) {
            path.process(input, equalized, 4_096)
            telemetry.beginProcessingCall()
            engine.process(equalized, 0, 4_096, limited, 0)
        }
    }

    private fun measureLimiterWithOuterTimingOnly(): ComponentResult =
        measureLimiterVariant(
            name = "limiter-frequent-outer-timing-only",
            input = frequentReductionInput(),
            telemetry = null
        )

    private fun measureLimiterVariant(
        name: String,
        input: FloatArray,
        telemetry: LimiterTelemetryAccumulator?
    ): ComponentResult {
        val engine = limiterEngine(telemetry)
        val output = FloatArray(input.size)
        return measure(name, 500) {
            telemetry?.beginProcessingCall()
            engine.process(input, 0, 4_096, output, 0)
        }
    }

    private fun limiterEngine(
        telemetry: LimiterTelemetryAccumulator?
    ): LookaheadLimiterEngine {
        return LookaheadLimiterEngine(
            preparedConfiguration =
                LimiterPreparedConfiguration.prepare(
                    configuration = LimiterConfiguration(enabled = true),
                    sampleRateHz = 44_100,
                    channelCount = 2,
                    configurationVersion = 1L
                ),
            telemetry = telemetry
        )
    }

    private fun frequentReductionInput(): FloatArray =
        FloatArray(4_096 * 2) { index ->
            if (index % 17 == 0) 1.25f else 0.4f
        }

    private fun realisticParametricPath() =
        EqualizerPlanPreparer.prepare(
            snapshot = EqualizerRuntimeSnapshot(
                version = 1L,
                configuration = EqualizerPerformanceFixtures
                    .namedConfigurations
                    .getValue("parametric-realistic"),
                automaticHeadroomEnabled = false
            ),
            processorFormat = EqualizerProcessorFormat(
                44_100,
                2,
                C.ENCODING_PCM_16BIT
            )
        ).createProcessingPath()

    private fun measureHighQSilenceAfterImpulse():
        ComponentResult {
        val plan = EqualizerPlanPreparer.prepare(
            snapshot = EqualizerRuntimeSnapshot(
                version = 1L,
                configuration = EqualizerPerformanceFixtures
                    .namedConfigurations
                    .getValue("parametric-high-q"),
                automaticHeadroomEnabled = true
            ),
            processorFormat = EqualizerProcessorFormat(
                48_000,
                2,
                C.ENCODING_PCM_16BIT
            )
        )
        val path = plan.createProcessingPath()
        val impulse = FloatArray(512 * 2)
        impulse[0] = 0.8f
        impulse[1] = 0.8f
        val silence = FloatArray(impulse.size)
        val output = FloatArray(impulse.size)
        path.process(impulse, output, 512)
        repeat(2_000) {
            path.process(silence, output, 512)
        }
        return measure("high-q-silence-after-impulse", 500) {
            path.process(silence, output, 512)
        }.also {
            assertTrue(output.all(Float::isFinite))
        }
    }

    private fun measurePlanPreparation(): ComponentResult {
        var version = 0L
        return measure("plan-preparation-high-q", 300) {
            EqualizerPlanPreparer.prepare(
                snapshot = EqualizerRuntimeSnapshot(
                    version = ++version,
                    configuration = EqualizerPerformanceFixtures
                        .namedConfigurations
                        .getValue("parametric-high-q"),
                    automaticHeadroomEnabled = true
                ),
                processorFormat = EqualizerProcessorFormat(
                    192_000,
                    6,
                    C.ENCODING_PCM_16BIT
                )
            ).createProcessingPath()
        }
    }

    private fun measureSynchronousFormatPreparation():
        ComponentResult {
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerPerformanceFixtures
                .namedConfigurations
                .getValue("parametric-high-q"),
            automaticHeadroomEnabled = true,
            limiterConfiguration = LimiterConfiguration(
                enabled = true
            )
        )
        var formatIndex = 0
        val formats = EqualizerPerformanceFixtures.sampleRates
        return measure("synchronous-format-preparation", 300) {
            val sampleRateHz =
                formats[formatIndex++ % formats.size]
            EqualizerRuntimeBridge.prepareForProcessorFormat(
                EqualizerProcessorFormat(
                    sampleRateHz,
                    if (formatIndex % 2 == 0) 2 else 6,
                    C.ENCODING_PCM_16BIT
                )
            )
        }
    }

    private fun measureAnalysis(): ComponentResult {
        val preferences = parametricPreferences()
        var requestVersion = 0L
        return measure("response-analysis", 300) {
            EqualizerAnalysisCalculator.calculate(
                EqualizerAnalysisRequest(
                    preferences = preferences,
                    currentSampleRateHz = 192_000,
                    requestVersion = ++requestVersion
                )
            )
        }
    }

    private fun measureImport(): ComponentResult {
        val text = EqualizerProfileExporter.exportText(
            parametricPreferences().parametricState
        )
        var id = 0
        return measure("profile-text-import", 500) {
            EqualizerProfileParser.parse(
                input = text,
                sourceName = "phase-f.txt",
                idFactory = { "filter-${++id}" }
            )
        }
    }

    private fun measureExport(): ComponentResult {
        val state = parametricPreferences().parametricState
        return measure("profile-text-export", 500) {
            EqualizerProfileExporter.exportText(state)
        }
    }

    private fun measureMaximumDeclarationImport():
        ComponentResult {
        val input = (1..256).joinToString("\n") { index ->
            "Filter $index: ON PK Fc " +
                "${100 + index} Hz Gain 1 dB Q 1"
        }
        var id = 0
        return measure("profile-256-declarations", 100) {
            EqualizerProfileParser.parse(
                input = input,
                sourceName = "maximum.txt",
                idFactory = { "maximum-${++id}" }
            )
        }
    }

    private fun measureNearMaximumMalformedImport():
        ComponentResult {
        val input = "x".repeat(
            EqualizerProfileLimits.MAX_INPUT_BYTES - 1
        )
        return measure("profile-near-256k-malformed", 40) {
            EqualizerProfileParser.parse(input)
        }
    }

    private fun measureTwoThousandLineImport():
        ComponentResult {
        val input = List(2_000) { "# comment" }
            .joinToString("\n")
        return measure("profile-2000-lines", 100) {
            EqualizerProfileParser.parse(input)
        }
    }

    private fun measureNativePresetJson(): ComponentResult {
        val state = parametricPreferences().parametricState
        val file = SazanamiPresetFile(
            name = "Phase F",
            preampDb = state.preampDb,
            automaticHeadroomEnabled =
                state.automaticHeadroomEnabled,
            filters = state.filters
        )
        return measure("native-sazeq-json-round-trip", 500) {
            val encoded = SazanamiPresetFileJson.encode(file)
            SazanamiPresetFileJson.decode(encoded)
        }
    }

    private fun parametricPreferences(): EqualizerPreferencesState {
        val filters = listOf(
            ParametricFilter.Peaking("1", true, 1_000.0, 4.0, 1.4),
            ParametricFilter.LowShelf("2", true, 90.0, 3.0, 0.8),
            ParametricFilter.HighShelf("3", true, 9_000.0, -3.0, 0.8),
            ParametricFilter.LowPass("4", true, 14_000.0, 0.71),
            ParametricFilter.HighPass("5", true, 35.0, 0.71),
            ParametricFilter.Notch("6", true, 3_200.0, 8.0),
            ParametricFilter.BandPass("7", true, 650.0, 1.1)
        )
        return EqualizerPreferencesState(
            enabled = true,
            mode = EqualizerMode.PARAMETRIC,
            parametricState = ParametricEqualizerState(
                preampDb = -6.0,
                automaticHeadroomEnabled = true,
                filters = filters
            )
        )
    }

    private fun measure(
        name: String,
        iterationCount: Int,
        block: () -> Unit
    ): ComponentResult {
        repeat(50) { block() }
        val durations = LongArray(iterationCount)
        repeat(iterationCount) { index ->
            val started = System.nanoTime()
            block()
            durations[index] = System.nanoTime() - started
        }
        durations.sort()
        return ComponentResult(
            name = name,
            medianMillis = durations.percentile(0.50).toMillis(),
            p90Millis = durations.percentile(0.90).toMillis(),
            p95Millis = durations.percentile(0.95).toMillis(),
            p99Millis = durations.percentile(0.99).toMillis(),
            maximumMillis = durations.last().toMillis()
        )
    }

    private fun LongArray.percentile(percentile: Double): Long {
        val index = (ceil(percentile * size).toInt() - 1)
            .coerceIn(0, lastIndex)
        return this[index]
    }

    private fun Long.toMillis(): Double = this / 1_000_000.0

    private data class ComponentResult(
        val name: String,
        val medianMillis: Double,
        val p90Millis: Double,
        val p95Millis: Double,
        val p99Millis: Double,
        val maximumMillis: Double
    )
}
