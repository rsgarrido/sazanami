package io.github.rsgarrido.sazanami.player.equalizer.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicEqualizerDefaultsTest {
    @Test
    fun standardFrequenciesAndQualityFactorMatchDefinition() {
        assertEquals(
            listOf(
                31.0,
                62.0,
                125.0,
                250.0,
                500.0,
                1_000.0,
                2_000.0,
                4_000.0,
                8_000.0,
                16_000.0
            ),
            GraphicEqualizerDefaults.frequenciesHz
        )
        assertTrue(
            GraphicEqualizerDefaults.createFlatFilters().all { filter ->
                filter.q == 1.41
            }
        )
    }

    @Test
    fun returnedDefinitionsCannotMutateDefaults() {
        val returned = GraphicEqualizerDefaults.frequenciesHz
        @Suppress("UNCHECKED_CAST")
        (returned as MutableList<Double>).clear()

        assertEquals(10, GraphicEqualizerDefaults.frequenciesHz.size)
        assertEquals(10, GraphicEqualizerDefaults.createFlatFilters().size)
    }

    @Test
    fun sixteenKilohertzIsValidatedAgainstNyquistWithoutClamping() {
        val band = GraphicEqualizerDefaults.createFlatFilters().last()

        assertTrue(isEqualizerFrequencySupported(16_000.0, 44_100))
        assertFalse(isEqualizerFrequencySupported(16_000.0, 32_000))
        assertEquals(
            BiquadCoefficients.UNITY,
            BiquadDesigner.design(band, sampleRateHz = 44_100)
        )
        assertThrows(IllegalArgumentException::class.java) {
            BiquadDesigner.design(band, sampleRateHz = 32_000)
        }
        assertEquals(16_000.0, band.frequencyHz, 0.0)
    }
}
