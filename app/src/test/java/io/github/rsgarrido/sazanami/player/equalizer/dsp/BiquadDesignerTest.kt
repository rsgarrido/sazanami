package io.github.rsgarrido.sazanami.player.equalizer.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadDesignerTest {
    @Test
    fun allFilterTypesProduceFiniteNormalizedCoefficientsAtSampleRateMatrix() {
        TEST_SAMPLE_RATES.forEach { sampleRateHz ->
            val filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = 6.0,
                    q = 1.41
                ),
                EqualizerFilterSpec.LowShelf(
                    frequencyHz = 200.0,
                    gainDb = -4.0,
                    slope = 0.8
                ),
                EqualizerFilterSpec.HighShelf(
                    frequencyHz = 4_000.0,
                    gainDb = 3.0,
                    slope = 1.0
                )
            )

            filters.forEach { filter ->
                val coefficients = BiquadDesigner.design(
                    filter = filter,
                    sampleRateHz = sampleRateHz
                )
                assertAllFinite(coefficients, sampleRateHz)
            }
        }
    }

    @Test
    fun zeroGainProducesExactUnityForEveryFilterType() {
        val filters = listOf(
            EqualizerFilterSpec.Peaking(1_000.0, 0.0, 1.0),
            EqualizerFilterSpec.LowShelf(200.0, 0.0, 0.5),
            EqualizerFilterSpec.HighShelf(4_000.0, 0.0, 1.0)
        )

        filters.forEach { filter ->
            assertEquals(
                BiquadCoefficients.UNITY,
                BiquadDesigner.design(filter, sampleRateHz = 48_000)
            )
        }
    }

    @Test
    fun coefficientRepresentationRejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException::class.java) {
            BiquadCoefficients(
                b0 = Double.NaN,
                b1 = 0.0,
                b2 = 0.0,
                a1 = 0.0,
                a2 = 0.0
            )
        }
    }

    @Test
    fun peakingCenterResponseMatchesRequestedGain() {
        listOf(-12.0, -6.0, 3.0, 9.0).forEach { gainDb ->
            val coefficients = BiquadDesigner.designPeaking(
                frequencyHz = 1_000.0,
                gainDb = gainDb,
                q = 1.41,
                sampleRateHz = 48_000
            )

            assertEquals(
                gainDb,
                EqualizerFrequencyResponse.magnitudeDb(
                    coefficients = coefficients,
                    frequencyHz = 1_000.0,
                    sampleRateHz = 48_000
                ),
                RESPONSE_DB_TOLERANCE
            )
        }
    }

    @Test
    fun positiveAndNegativePeakingGainsMoveInCorrectDirection() {
        val boost = BiquadDesigner.designPeaking(
            frequencyHz = 2_000.0,
            gainDb = 6.0,
            q = 1.0,
            sampleRateHz = 48_000
        )
        val cut = BiquadDesigner.designPeaking(
            frequencyHz = 2_000.0,
            gainDb = -6.0,
            q = 1.0,
            sampleRateHz = 48_000
        )

        assertTrue(responseAtCenter(boost) > 0.0)
        assertTrue(responseAtCenter(cut) < 0.0)
    }

    @Test
    fun shelvesAffectTheIntendedSideOfTheSpectrum() {
        val lowShelf = BiquadDesigner.designLowShelf(
            frequencyHz = 500.0,
            gainDb = 9.0,
            slope = 1.0,
            sampleRateHz = 48_000
        )
        val highShelf = BiquadDesigner.designHighShelf(
            frequencyHz = 4_000.0,
            gainDb = 9.0,
            slope = 1.0,
            sampleRateHz = 48_000
        )

        val lowShelfAtLowFrequency = response(lowShelf, 40.0)
        val lowShelfAtHighFrequency = response(lowShelf, 12_000.0)
        val highShelfAtLowFrequency = response(highShelf, 100.0)
        val highShelfAtHighFrequency = response(highShelf, 16_000.0)

        assertTrue(lowShelfAtLowFrequency > lowShelfAtHighFrequency + 6.0)
        assertTrue(highShelfAtHighFrequency > highShelfAtLowFrequency + 6.0)
    }

    @Test
    fun coefficientsAreRecalculatedForEachSampleRate() {
        val filter = EqualizerFilterSpec.Peaking(
            frequencyHz = 1_000.0,
            gainDb = 6.0,
            q = 1.41
        )
        val coefficients = TEST_SAMPLE_RATES.map { sampleRateHz ->
            BiquadDesigner.design(filter, sampleRateHz)
        }

        coefficients.zipWithNext().forEach { pair ->
            assertNotEquals(pair.first, pair.second)
        }
    }

    @Test
    fun invalidSampleRateAndFrequencyAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BiquadDesigner.designPeaking(1_000.0, 3.0, 1.0, 0)
        }
        listOf(0.0, -1.0, 24_000.0, 25_000.0).forEach { frequencyHz ->
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designPeaking(
                    frequencyHz = frequencyHz,
                    gainDb = 3.0,
                    q = 1.0,
                    sampleRateHz = 48_000
                )
            }
        }
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        ).forEach { frequencyHz ->
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designPeaking(
                    frequencyHz = frequencyHz,
                    gainDb = 3.0,
                    q = 1.0,
                    sampleRateHz = 48_000
                )
            }
        }
    }

    @Test
    fun invalidGainAndShapeParametersAreRejected() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designPeaking(1_000.0, invalid, 1.0, 48_000)
            }
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designPeaking(1_000.0, 3.0, invalid, 48_000)
            }
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designLowShelf(200.0, 3.0, invalid, 48_000)
            }
        }
        listOf(0.0, -1.0).forEach { invalidQ ->
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designPeaking(
                    1_000.0,
                    3.0,
                    invalidQ,
                    48_000
                )
            }
        }
        listOf(0.0, -1.0, 1.000_001).forEach { invalidSlope ->
            assertThrows(IllegalArgumentException::class.java) {
                BiquadDesigner.designHighShelf(
                    4_000.0,
                    3.0,
                    invalidSlope,
                    48_000
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BiquadDesigner.designPeaking(
                1_000.0,
                Double.MAX_VALUE,
                1.0,
                48_000
            )
        }
    }

    private fun assertAllFinite(
        coefficients: BiquadCoefficients,
        sampleRateHz: Int
    ) {
        assertTrue(
            "non-finite coefficients at $sampleRateHz Hz",
            listOf(
                coefficients.b0,
                coefficients.b1,
                coefficients.b2,
                coefficients.a1,
                coefficients.a2
            ).all(Double::isFinite)
        )
    }

    private fun responseAtCenter(
        coefficients: BiquadCoefficients
    ): Double {
        return EqualizerFrequencyResponse.magnitudeDb(
            coefficients,
            frequencyHz = 2_000.0,
            sampleRateHz = 48_000
        )
    }

    private fun response(
        coefficients: BiquadCoefficients,
        frequencyHz: Double
    ): Double {
        return EqualizerFrequencyResponse.magnitudeDb(
            coefficients,
            frequencyHz,
            sampleRateHz = 48_000
        )
    }
}
