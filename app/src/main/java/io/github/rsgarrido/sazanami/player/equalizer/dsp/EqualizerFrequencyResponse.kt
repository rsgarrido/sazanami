package io.github.rsgarrido.sazanami.player.equalizer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

internal data class EqualizerResponsePoint(
    val frequencyHz: Double,
    val magnitudeDb: Double
)

/**
 * Deterministic analytical response for the same coefficients used by the DSP
 * engine. The magnitude floor prevents `log10(0)` at numerical nulls.
 */
internal object EqualizerFrequencyResponse {
    private const val MAGNITUDE_FLOOR = 1e-300

    fun calculate(
        configuration: EqualizerConfiguration,
        sampleRateHz: Int,
        frequenciesHz: DoubleArray,
        automaticHeadroomDb: Double = 0.0
    ): List<EqualizerResponsePoint> {
        require(sampleRateHz > 0) {
            "sampleRateHz must be greater than 0"
        }
        require(automaticHeadroomDb.isFinite() && automaticHeadroomDb >= 0.0) {
            "automaticHeadroomDb must be finite and non-negative"
        }
        frequenciesHz.forEach { frequencyHz ->
            validateResponseFrequency(
                frequencyHz = frequencyHz,
                sampleRateHz = sampleRateHz
            )
        }

        if (configuration.isEffectivelyFlat) {
            return frequenciesHz.map { frequencyHz ->
                EqualizerResponsePoint(
                    frequencyHz = frequencyHz,
                    magnitudeDb = 0.0
                )
            }
        }

        val effectivePreampDb =
            configuration.preampDb - automaticHeadroomDb
        require(effectivePreampDb.isFinite()) {
            "effective preamp must be finite"
        }
        val coefficients = configuration.filters
            .asSequence()
            .filter { filter -> filter.hasAudibleEffect }
            .map { filter ->
                BiquadDesigner.design(
                    filter = filter,
                    sampleRateHz = sampleRateHz
                )
            }
            .toList()

        return frequenciesHz.map { frequencyHz ->
            var magnitudeDb = effectivePreampDb
            coefficients.forEach { coefficient ->
                magnitudeDb += magnitudeDb(
                    coefficients = coefficient,
                    frequencyHz = frequencyHz,
                    sampleRateHz = sampleRateHz
                )
            }
            require(magnitudeDb.isFinite()) {
                "Combined response must be finite"
            }
            EqualizerResponsePoint(
                frequencyHz = frequencyHz,
                magnitudeDb = magnitudeDb
            )
        }
    }

    fun magnitudeDb(
        coefficients: BiquadCoefficients,
        frequencyHz: Double,
        sampleRateHz: Int
    ): Double {
        validateResponseFrequency(
            frequencyHz = frequencyHz,
            sampleRateHz = sampleRateHz
        )

        val omega = 2.0 * PI * frequencyHz / sampleRateHz
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val cosTwoOmega = cos(2.0 * omega)
        val sinTwoOmega = sin(2.0 * omega)

        val numeratorMagnitude = max(
            MAGNITUDE_FLOOR,
            hypot(
                coefficients.b0 +
                    coefficients.b1 * cosOmega +
                    coefficients.b2 * cosTwoOmega,
                -coefficients.b1 * sinOmega -
                    coefficients.b2 * sinTwoOmega
            )
        )
        val denominatorMagnitude = max(
            MAGNITUDE_FLOOR,
            hypot(
                1.0 +
                    coefficients.a1 * cosOmega +
                    coefficients.a2 * cosTwoOmega,
                -coefficients.a1 * sinOmega -
                    coefficients.a2 * sinTwoOmega
            )
        )
        val responseDb =
            20.0 * (log10(numeratorMagnitude) - log10(denominatorMagnitude))
        require(responseDb.isFinite()) {
            "Biquad response must be finite"
        }
        return responseDb
    }

    private fun validateResponseFrequency(
        frequencyHz: Double,
        sampleRateHz: Int
    ) {
        require(sampleRateHz > 0) {
            "sampleRateHz must be greater than 0"
        }
        require(isEqualizerFrequencySupported(frequencyHz, sampleRateHz)) {
            "frequencyHz must be finite, greater than 0, and below Nyquist"
        }
    }
}
