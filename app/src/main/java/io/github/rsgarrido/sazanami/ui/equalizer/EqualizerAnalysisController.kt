package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.activeAutomaticHeadroomEnabled
import io.github.rsgarrido.sazanami.player.equalizer.toDspConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.AutomaticHeadroomCalculator
import io.github.rsgarrido.sazanami.player.equalizer.dsp.AutomaticHeadroomResult
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFrequencyResponse
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerResponsePoint
import io.github.rsgarrido.sazanami.player.equalizer.dsp.GraphicEqualizerDefaults
import io.github.rsgarrido.sazanami.player.equalizer.dsp.isEqualizerFrequencySupported
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class EqualizerAnalysisResult(
    val sampleRateHz: Int = DEFAULT_ANALYSIS_SAMPLE_RATE_HZ,
    val usesFallbackSampleRate: Boolean = true,
    val filterResponse: List<EqualizerResponsePoint> =
        emptyList(),
    val effectiveResponse: List<EqualizerResponsePoint> =
        emptyList(),
    val automaticHeadroom: AutomaticHeadroomResult =
        AutomaticHeadroomResult(
            maximumPredictedDb = 0.0,
            attenuationDb = 0.0,
            effectivePreampDb = 0.0
        ),
    val ignoredBandIndices: Set<Int> = emptySet()
) {
    val ignoredFilterIndices: Set<Int>
        get() = ignoredBandIndices

    val predictedMaximumDb: Double
        get() = automaticHeadroom.maximumPredictedDb

    companion object {
        const val DEFAULT_ANALYSIS_SAMPLE_RATE_HZ = 48_000
    }
}

internal data class EqualizerAnalysisRequest(
    val preferences: EqualizerPreferencesState,
    val currentSampleRateHz: Int?,
    val requestVersion: Long = 0L
)

internal class EqualizerAnalysisController(
    private val scope: CoroutineScope,
    private val calculate:
        (EqualizerAnalysisRequest) -> EqualizerAnalysisResult =
        EqualizerAnalysisCalculator::calculate
) {
    private val _state =
        MutableStateFlow(EqualizerAnalysisResult())
    val state: StateFlow<EqualizerAnalysisResult> =
        _state.asStateFlow()

    private var analysisJob: Job? = null
    private var latestRequestVersion = 0L
    private var hasSubmittedRequest = false
    private var lastSubmittedPreferences:
        EqualizerPreferencesState? = null
    private var lastSubmittedSampleRateHz: Int? = null

    fun submit(
        preferences: EqualizerPreferencesState,
        currentSampleRateHz: Int?
    ) {
        if (
            hasSubmittedRequest &&
            lastSubmittedPreferences == preferences &&
            lastSubmittedSampleRateHz == currentSampleRateHz
        ) {
            return
        }
        hasSubmittedRequest = true
        lastSubmittedPreferences = preferences
        lastSubmittedSampleRateHz = currentSampleRateHz
        val requestVersion = ++latestRequestVersion
        val request = EqualizerAnalysisRequest(
            preferences = preferences,
            currentSampleRateHz = currentSampleRateHz,
            requestVersion = requestVersion
        )
        analysisJob?.cancel()
        analysisJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                calculate(request)
            }
            if (requestVersion == latestRequestVersion) {
                _state.value = result
            }
        }
    }

    fun release() {
        analysisJob?.cancel()
        analysisJob = null
        hasSubmittedRequest = false
        lastSubmittedPreferences = null
        lastSubmittedSampleRateHz = null
    }
}

internal object EqualizerAnalysisCalculator {
    private const val GRAPH_POINT_COUNT = 160
    private const val LOWEST_FREQUENCY_HZ = 20.0
    private const val HIGHEST_FREQUENCY_HZ = 20_000.0
    private const val BELOW_NYQUIST_SCALE = 1.0 - 1e-12

    fun calculate(
        request: EqualizerAnalysisRequest
    ): EqualizerAnalysisResult {
        val sampleRateHz = request.currentSampleRateHz
            ?.takeIf { rate -> rate > 0 }
            ?: EqualizerAnalysisResult
                .DEFAULT_ANALYSIS_SAMPLE_RATE_HZ
        val fullConfiguration =
            request.preferences.toDspConfiguration()
        val ignoredBandIndices =
            fullConfiguration.filters
                .mapIndexedNotNull { index, filter ->
                    index.takeUnless {
                        isEqualizerFrequencySupported(
                            filter.frequencyHz,
                            sampleRateHz
                        )
                    }
                }
                .toSet()
        val validFilters = fullConfiguration.filters
            .filterIndexed { index, _ ->
                index !in ignoredBandIndices
            }
        val validConfiguration = EqualizerConfiguration(
            enabled = fullConfiguration.enabled,
            preampDb = fullConfiguration.preampDb,
            filters = validFilters
        )
        val filterConfiguration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = validFilters
        )
        val frequencies = graphFrequencies(sampleRateHz)
        val rawHeadroom = AutomaticHeadroomCalculator.calculate(
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = validConfiguration.preampDb,
                filters = validConfiguration.filters
            ),
            sampleRateHz = sampleRateHz
        )
        val automaticHeadroom = if (
            request.preferences.activeAutomaticHeadroomEnabled
        ) {
            rawHeadroom
        } else {
            rawHeadroom.copy(
                attenuationDb = 0.0,
                effectivePreampDb =
                    validConfiguration.preampDb
            )
        }
        return EqualizerAnalysisResult(
            sampleRateHz = sampleRateHz,
            usesFallbackSampleRate =
                request.currentSampleRateHz == null,
            filterResponse =
                EqualizerFrequencyResponse.calculate(
                    configuration = filterConfiguration,
                    sampleRateHz = sampleRateHz,
                    frequenciesHz = frequencies
                ),
            effectiveResponse =
                EqualizerFrequencyResponse.calculate(
                    configuration = EqualizerConfiguration(
                        enabled = true,
                        preampDb = validConfiguration.preampDb,
                        filters = validConfiguration.filters
                    ),
                    sampleRateHz = sampleRateHz,
                    frequenciesHz = frequencies,
                    automaticHeadroomDb =
                        automaticHeadroom.attenuationDb
                ),
            automaticHeadroom = automaticHeadroom,
            ignoredBandIndices = ignoredBandIndices
        )
    }

    private fun graphFrequencies(
        sampleRateHz: Int
    ): DoubleArray {
        val highestFrequencyHz = minOf(
            HIGHEST_FREQUENCY_HZ,
            sampleRateHz / 2.0 * BELOW_NYQUIST_SCALE
        )
        val logarithmicRange =
            ln(highestFrequencyHz / LOWEST_FREQUENCY_HZ)
        return DoubleArray(GRAPH_POINT_COUNT) { index ->
            val fraction =
                index.toDouble() / (GRAPH_POINT_COUNT - 1)
            LOWEST_FREQUENCY_HZ *
                exp(logarithmicRange * fraction)
        }
    }
}
