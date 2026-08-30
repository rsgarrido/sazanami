package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.dsp.AutomaticHeadroomCalculator
import io.github.rsgarrido.sazanami.player.equalizer.dsp.AutomaticHeadroomResult
import io.github.rsgarrido.sazanami.player.equalizer.dsp.BiquadCoefficients
import io.github.rsgarrido.sazanami.player.equalizer.dsp.BiquadDesigner
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.dsp.PreparedEqualizerCascade
import io.github.rsgarrido.sazanami.player.equalizer.dsp.decibelsToLinear
import io.github.rsgarrido.sazanami.player.equalizer.dsp.hasAudibleEffect
import io.github.rsgarrido.sazanami.player.equalizer.dsp.isEqualizerFrequencySupported

internal object EqualizerPlanPreparer {
    fun prepare(
        snapshot: EqualizerRuntimeSnapshot,
        processorFormat: EqualizerProcessorFormat
    ): PreparedEqualizerPlan {
        val validFilters = ArrayList<EqualizerFilterSpec>()
        val coefficients = ArrayList<BiquadCoefficients>()
        val ignoredFilters = ArrayList<IgnoredEqualizerFilter>()

        if (snapshot.configuration.enabled) {
            snapshot.configuration.filters.forEachIndexed { index, filter ->
                if (!filter.hasAudibleEffect) {
                    return@forEachIndexed
                }
                if (
                    !isEqualizerFrequencySupported(
                        frequencyHz = filter.frequencyHz,
                        sampleRateHz = processorFormat.sampleRateHz
                    )
                ) {
                    ignoredFilters += IgnoredEqualizerFilter(
                        sourceIndex = index,
                        frequencyHz = filter.frequencyHz,
                        reason = ignoredFrequencyReason(
                            frequencyHz = filter.frequencyHz,
                            sampleRateHz = processorFormat.sampleRateHz
                        )
                    )
                    return@forEachIndexed
                }

                val coefficient = try {
                    BiquadDesigner.design(
                        filter = filter,
                        sampleRateHz = processorFormat.sampleRateHz
                    )
                } catch (_: IllegalArgumentException) {
                    ignoredFilters += IgnoredEqualizerFilter(
                        sourceIndex = index,
                        frequencyHz = filter.frequencyHz,
                        reason =
                            IgnoredEqualizerFilterReason.INVALID_PARAMETERS
                    )
                    return@forEachIndexed
                }

                validFilters += filter
                coefficients += coefficient
            }
        }

        val validatedConfiguration = EqualizerConfiguration(
            enabled = snapshot.configuration.enabled,
            preampDb = snapshot.configuration.preampDb,
            filters = validFilters
        )
        val bypassed = validatedConfiguration.isEffectivelyFlat
        val headroomResult = calculateHeadroom(
            configuration = validatedConfiguration,
            sampleRateHz = processorFormat.sampleRateHz,
            automaticHeadroomEnabled =
                snapshot.automaticHeadroomEnabled && !bypassed
        )
        val effectivePreampDb = if (bypassed) {
            0.0
        } else {
            headroomResult.effectivePreampDb
        }
        val coefficientValues = DoubleArray(
            coefficients.size * PreparedEqualizerCascade.VALUES_PER_SECTION
        )
        coefficients.forEachIndexed { sectionIndex, coefficient ->
            val offset =
                sectionIndex * PreparedEqualizerCascade.VALUES_PER_SECTION
            coefficientValues[offset] = coefficient.b0
            coefficientValues[offset + 1] = coefficient.b1
            coefficientValues[offset + 2] = coefficient.b2
            coefficientValues[offset + 3] = coefficient.a1
            coefficientValues[offset + 4] = coefficient.a2
        }

        return PreparedEqualizerPlan(
            sourceSnapshotVersion = snapshot.version,
            sourceMode = snapshot.mode,
            processorFormat = processorFormat,
            cascade = PreparedEqualizerCascade(
                sampleRateHz = processorFormat.sampleRateHz,
                channelCount = processorFormat.channelCount,
                sectionCount = coefficients.size,
                effectivePreampMultiplier =
                    decibelsToLinear(effectivePreampDb),
                coefficients = coefficientValues
            ),
            automaticHeadroomResult = headroomResult,
            validFilterCount = coefficients.size,
            ignoredFilters = ignoredFilters,
            bypassed = bypassed
        )
    }

    private fun calculateHeadroom(
        configuration: EqualizerConfiguration,
        sampleRateHz: Int,
        automaticHeadroomEnabled: Boolean
    ): AutomaticHeadroomResult {
        if (automaticHeadroomEnabled) {
            return AutomaticHeadroomCalculator.calculate(
                configuration = configuration,
                sampleRateHz = sampleRateHz
            )
        }
        return AutomaticHeadroomResult(
            maximumPredictedDb = configuration.preampDb,
            attenuationDb = 0.0,
            effectivePreampDb = configuration.preampDb
        )
    }

    private fun ignoredFrequencyReason(
        frequencyHz: Double,
        sampleRateHz: Int
    ): IgnoredEqualizerFilterReason {
        return if (
            frequencyHz.isFinite() &&
            frequencyHz >= sampleRateHz / 2.0
        ) {
            IgnoredEqualizerFilterReason.AT_OR_ABOVE_NYQUIST
        } else {
            IgnoredEqualizerFilterReason.INVALID_FREQUENCY
        }
    }
}
