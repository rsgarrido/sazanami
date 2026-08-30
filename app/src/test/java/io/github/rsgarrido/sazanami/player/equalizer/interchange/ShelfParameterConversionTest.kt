package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.dsp.BiquadDesigner
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFrequencyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfParameterConversionTest {
    @Test
    fun qAndSlopeRoundTripAcrossPositiveNegativeAndZeroGain() {
        listOf(-12.0, -6.0, 0.0, 6.0, 12.0).forEach { gain ->
            listOf(0.55, 0.70, 0.70710678).forEach { q ->
                val slope =
                    ShelfParameterConversion.qToSlope(gain, q).value
                val restored =
                    ShelfParameterConversion.slopeToQ(gain, slope).value
                assertEquals(q, restored, 1e-12)
            }
        }
    }

    @Test
    fun roundedAutoEqQNearSlopeOneUsesNamedToleranceOnly() {
        val result = ShelfParameterConversion.qToSlope(6.0, 0.71)
        assertEquals(1.0, result.value, 0.0)
        assertTrue(result.boundaryNormalized)

        val error = runCatching {
            ShelfParameterConversion.qToSlope(6.0, 0.8)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun convertedShelfHasEquivalentRbjResponse() {
        listOf(-9.0, 9.0).forEach { gain ->
            val q = 0.70
            val slope =
                ShelfParameterConversion.qToSlope(gain, q).value
            val qAgain =
                ShelfParameterConversion.slopeToQ(gain, slope).value
            val first = BiquadDesigner.designLowShelf(
                105.0, gain, slope, 48_000
            )
            val secondSlope =
                ShelfParameterConversion.qToSlope(gain, qAgain).value
            val second = BiquadDesigner.designLowShelf(
                105.0, gain, secondSlope, 48_000
            )
            doubleArrayOf(20.0, 105.0, 1_000.0, 10_000.0)
                .forEach { frequency ->
                    assertEquals(
                        EqualizerFrequencyResponse.magnitudeDb(
                            first, frequency, 48_000
                        ),
                        EqualizerFrequencyResponse.magnitudeDb(
                            second, frequency, 48_000
                        ),
                        1e-10
                    )
                }
        }
    }
}
