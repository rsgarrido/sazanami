package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.dsp.GraphicEqualizerDefaults
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter

internal fun EqualizerPreferencesState.toDspConfiguration(
    enabledOverride: Boolean = enabled
): EqualizerConfiguration = when (mode) {
    EqualizerMode.GRAPHIC ->
        toGraphicDspConfiguration(enabledOverride)
    EqualizerMode.PARAMETRIC ->
        toParametricDspConfiguration(enabledOverride)
}

/**
 * Phase C graphic conversion remains isolated and numerically unchanged.
 */
private fun EqualizerPreferencesState.toGraphicDspConfiguration(
    enabledOverride: Boolean
): EqualizerConfiguration = EqualizerConfiguration(
    enabled = enabledOverride,
    preampDb = preampDb,
    filters = GraphicEqualizerDefaults.frequenciesHz.mapIndexed {
            index,
            frequencyHz ->
        EqualizerFilterSpec.Peaking(
            frequencyHz = frequencyHz,
            gainDb = bandGainsDb[index],
            q = GraphicEqualizerDefaults.Q
        )
    }
)

private fun EqualizerPreferencesState.toParametricDspConfiguration(
    enabledOverride: Boolean
): EqualizerConfiguration {
    val parametric = parametricState
    return EqualizerConfiguration(
        enabled = enabledOverride,
        preampDb = parametric.preampDb,
        filters = parametric.filters.map { filter ->
            filter.toDspFilterSpec()
        }
    )
}

private fun ParametricFilter.toDspFilterSpec(): EqualizerFilterSpec =
    when (this) {
        is ParametricFilter.Peaking -> EqualizerFilterSpec.Peaking(
            frequencyHz = frequencyHz,
            gainDb = gainDb,
            q = q,
            enabled = enabled
        )

        is ParametricFilter.LowShelf -> EqualizerFilterSpec.LowShelf(
            frequencyHz = frequencyHz,
            gainDb = gainDb,
            slope = slope,
            enabled = enabled
        )

        is ParametricFilter.HighShelf -> EqualizerFilterSpec.HighShelf(
            frequencyHz = frequencyHz,
            gainDb = gainDb,
            slope = slope,
            enabled = enabled
        )

        is ParametricFilter.LowPass -> EqualizerFilterSpec.LowPass(
            frequencyHz = frequencyHz,
            q = q,
            enabled = enabled
        )

        is ParametricFilter.HighPass -> EqualizerFilterSpec.HighPass(
            frequencyHz = frequencyHz,
            q = q,
            enabled = enabled
        )

        is ParametricFilter.Notch -> EqualizerFilterSpec.Notch(
            frequencyHz = frequencyHz,
            q = q,
            enabled = enabled
        )

        is ParametricFilter.BandPass -> EqualizerFilterSpec.BandPass(
            frequencyHz = frequencyHz,
            q = q,
            enabled = enabled
        )
    }

internal val EqualizerPreferencesState.activeAutomaticHeadroomEnabled: Boolean
    get() = when (mode) {
        EqualizerMode.GRAPHIC -> automaticHeadroomEnabled
        EqualizerMode.PARAMETRIC ->
            parametricState.automaticHeadroomEnabled
    }

internal fun EqualizerPreferencesState.applyPreset(
    preset: BuiltInEqualizerPreset
): EqualizerPreferencesState = withCurve(
    preampDb = preset.preampDb,
    automaticHeadroomEnabled =
        preset.automaticHeadroomEnabled,
    bandGainsDb = preset.bandGainsDb
)

internal fun EqualizerPreferencesState.applyPreset(
    preset: UserEqualizerPreset
): EqualizerPreferencesState = withCurve(
    preampDb = preset.preampDb,
    automaticHeadroomEnabled =
        preset.automaticHeadroomEnabled,
    bandGainsDb = preset.bandGainsDb
)
