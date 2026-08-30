package io.github.rsgarrido.sazanami.player.equalizer.dsp

import kotlin.math.exp
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerFrequencyResponseTest {
    @Test
    fun callerSuppliedFrequenciesAreReturnedInOrderWithFiniteResponse() {
        val frequenciesHz = doubleArrayOf(8_000.0, 100.0, 1_000.0)
        val response = EqualizerFrequencyResponse.calculate(
            configuration = multiFilterConfiguration(),
            sampleRateHz = 48_000,
            frequenciesHz = frequenciesHz
        )

        assertEquals(frequenciesHz.toList(), response.map { it.frequencyHz })
        assertTrue(response.all { point -> point.magnitudeDb.isFinite() })
    }

    @Test
    fun equalPeakingBoostAndCutCancelAcrossSpectrum() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_500.0,
                    gainDb = 9.0,
                    q = 2.0
                ),
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_500.0,
                    gainDb = -9.0,
                    q = 2.0
                )
            )
        )
        val frequenciesHz = logarithmicFrequencies(
            lowestHz = 20.0,
            highestHz = 20_000.0,
            count = 96
        )

        EqualizerFrequencyResponse.calculate(
            configuration,
            sampleRateHz = 48_000,
            frequenciesHz = frequenciesHz
        ).forEach { point ->
            assertEquals(
                "cancellation at ${point.frequencyHz} Hz",
                0.0,
                point.magnitudeDb,
                1e-9
            )
        }
    }

    @Test
    fun analyticalResponseMatchesProcessedImpulse() {
        val configurations = listOf(
            "flat" to flatConfiguration(),
            "peaking" to EqualizerConfiguration(
                true,
                0.0,
                listOf(EqualizerFilterSpec.Peaking(1_500.0, 6.0, 1.2))
            ),
            "low shelf" to EqualizerConfiguration(
                true,
                0.0,
                listOf(EqualizerFilterSpec.LowShelf(500.0, 5.0, 0.8))
            ),
            "high shelf" to EqualizerConfiguration(
                true,
                0.0,
                listOf(EqualizerFilterSpec.HighShelf(4_000.0, -4.0, 1.0))
            ),
            "cascade" to multiFilterConfiguration(),
            "preamp" to EqualizerConfiguration(
                true,
                3.0,
                emptyList()
            )
        )
        val sampleRateHz = 48_000
        val frequenciesHz = doubleArrayOf(
            100.0,
            750.0,
            2_500.0,
            10_000.0
        )
        val impulse = FloatArray(65_536).also { samples ->
            samples[0] = 1.0f
        }

        configurations.forEach { (name, configuration) ->
            val impulseResponse = OfflineEqualizerProcessor().process(
                input = impulse,
                sampleRateHz = sampleRateHz,
                channelCount = 1,
                configuration = configuration
            )
            val analytical = EqualizerFrequencyResponse.calculate(
                configuration = configuration,
                sampleRateHz = sampleRateHz,
                frequenciesHz = frequenciesHz
            )

            analytical.forEach { point ->
                val measuredDb = measuredMagnitudeDb(
                    impulseResponse = impulseResponse,
                    frequencyHz = point.frequencyHz,
                    sampleRateHz = sampleRateHz
                )
                assertEquals(
                    "$name at ${point.frequencyHz} Hz",
                    point.magnitudeDb,
                    measuredDb,
                    0.06
                )
            }
        }
    }

    @Test
    fun responseUsesNewCoefficientsAcrossSampleRateMatrix() {
        TEST_SAMPLE_RATES.forEach { sampleRateHz ->
            val configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = 0.0,
                filters = listOf(
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 1_000.0,
                        gainDb = 7.0,
                        q = 1.41
                    )
                )
            )

            val centerResponse = EqualizerFrequencyResponse.calculate(
                configuration,
                sampleRateHz,
                doubleArrayOf(1_000.0)
            ).single()

            assertEquals(
                "center response at $sampleRateHz Hz",
                7.0,
                centerResponse.magnitudeDb,
                RESPONSE_DB_TOLERANCE
            )
        }
    }

    @Test
    fun automaticHeadroomIsSubtractedFromEffectivePreamp() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 4.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(1_000.0, 6.0, 1.0)
            )
        )
        val withoutHeadroom = EqualizerFrequencyResponse.calculate(
            configuration,
            48_000,
            doubleArrayOf(1_000.0)
        ).single()
        val withHeadroom = EqualizerFrequencyResponse.calculate(
            configuration,
            48_000,
            doubleArrayOf(1_000.0),
            automaticHeadroomDb = 3.5
        ).single()

        assertEquals(
            withoutHeadroom.magnitudeDb - 3.5,
            withHeadroom.magnitudeDb,
            COEFFICIENT_TOLERANCE
        )
    }

    @Test
    fun invalidResponseInputsAreRejected() {
        listOf(
            0.0,
            -1.0,
            24_000.0,
            30_000.0,
            Double.NaN,
            Double.POSITIVE_INFINITY
        ).forEach { frequencyHz ->
            assertThrows(IllegalArgumentException::class.java) {
                EqualizerFrequencyResponse.calculate(
                    flatConfiguration(),
                    sampleRateHz = 48_000,
                    frequenciesHz = doubleArrayOf(frequencyHz)
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerFrequencyResponse.calculate(
                flatConfiguration(),
                sampleRateHz = 0,
                frequenciesHz = doubleArrayOf(1_000.0)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerFrequencyResponse.calculate(
                multiFilterConfiguration(),
                sampleRateHz = 48_000,
                frequenciesHz = doubleArrayOf(1_000.0),
                automaticHeadroomDb = Double.NaN
            )
        }
    }

    private fun multiFilterConfiguration(): EqualizerConfiguration {
        return EqualizerConfiguration(
            enabled = true,
            preampDb = 1.5,
            filters = listOf(
                EqualizerFilterSpec.LowShelf(120.0, 2.0, 0.8),
                EqualizerFilterSpec.Peaking(1_000.0, 4.0, 1.41),
                EqualizerFilterSpec.HighShelf(6_000.0, -3.0, 1.0)
            )
        )
    }

    private fun logarithmicFrequencies(
        lowestHz: Double,
        highestHz: Double,
        count: Int
    ): DoubleArray {
        val range = ln(highestHz / lowestHz)
        return DoubleArray(count) { index ->
            lowestHz * exp(range * index / (count - 1))
        }
    }
}
