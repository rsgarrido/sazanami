package io.github.rsgarrido.sazanami.player.equalizer.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedBiquadDesignerTest {
    private val sampleRates =
        listOf(32_000, 44_100, 48_000, 96_000, 192_000)

    @Test
    fun newTypesProduceFiniteCoefficientsAtSupportedSampleRates() {
        sampleRates.forEach { sampleRate ->
            val center = minOf(1_000.0, sampleRate / 4.0)
            listOf(
                EqualizerFilterSpec.LowPass(center, 0.71),
                EqualizerFilterSpec.HighPass(center, 0.71),
                EqualizerFilterSpec.Notch(center, 4.0),
                EqualizerFilterSpec.BandPass(center, 1.0)
            ).forEach { filter ->
                val coefficients =
                    BiquadDesigner.design(filter, sampleRate)
                listOf(
                    coefficients.b0,
                    coefficients.b1,
                    coefficients.b2,
                    coefficients.a1,
                    coefficients.a2
                ).forEach { value -> assertTrue(value.isFinite()) }
            }
        }
    }

    @Test
    fun lowPassAndHighPassHaveExpectedCutoffDirection() {
        val sampleRate = 48_000
        val lowPass = BiquadDesigner.designLowPass(
            2_000.0, 0.71, sampleRate
        )
        val highPass = BiquadDesigner.designHighPass(
            2_000.0, 0.71, sampleRate
        )

        assertTrue(response(lowPass, 200.0, sampleRate) > -0.2)
        assertTrue(response(lowPass, 10_000.0, sampleRate) < -20.0)
        assertTrue(response(highPass, 200.0, sampleRate) < -20.0)
        assertTrue(response(highPass, 10_000.0, sampleRate) > -0.2)
    }

    @Test
    fun notchRejectsCenterAndBandPassHasZeroDbCenterPeak() {
        val sampleRate = 48_000
        val notch = BiquadDesigner.designNotch(
            1_000.0, 4.0, sampleRate
        )
        val bandPass = BiquadDesigner.designBandPass(
            1_000.0, 4.0, sampleRate
        )

        assertTrue(response(notch, 1_000.0, sampleRate) < -100.0)
        assertTrue(response(notch, 100.0, sampleRate) > -0.1)
        assertEquals(
            0.0,
            response(bandPass, 1_000.0, sampleRate),
            1.0e-9
        )
        assertTrue(response(bandPass, 100.0, sampleRate) < -20.0)
        assertTrue(response(bandPass, 10_000.0, sampleRate) < -20.0)
    }

    @Test
    fun qControlsResonanceAndBandwidth() {
        val sampleRate = 48_000
        val resonant = BiquadDesigner.designLowPass(
            2_000.0, 8.0, sampleRate
        )
        val damped = BiquadDesigner.designLowPass(
            2_000.0, 0.71, sampleRate
        )
        assertTrue(
            response(resonant, 2_000.0, sampleRate) >
                response(damped, 2_000.0, sampleRate) + 10.0
        )

        val narrow = BiquadDesigner.designBandPass(
            2_000.0, 10.0, sampleRate
        )
        val wide = BiquadDesigner.designBandPass(
            2_000.0, 0.5, sampleRate
        )
        assertTrue(
            response(narrow, 1_000.0, sampleRate) <
                response(wide, 1_000.0, sampleRate)
        )
    }

    @Test
    fun nearNyquistAndInvalidQAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BiquadDesigner.designLowPass(
                24_000.0, 0.71, 48_000
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BiquadDesigner.designBandPass(
                1_000.0, Double.NaN, 48_000
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BiquadDesigner.designHighPass(
                1_000.0, 0.0, 48_000
            )
        }
    }

    private fun response(
        coefficients: BiquadCoefficients,
        frequencyHz: Double,
        sampleRateHz: Int
    ): Double = EqualizerFrequencyResponse.magnitudeDb(
        coefficients = coefficients,
        frequencyHz = frequencyHz,
        sampleRateHz = sampleRateHz
    )
}
