package io.github.rsgarrido.sazanami.player.equalizer.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerConfigurationTest {
    @Test
    fun disabledConfiguration_isEffectivelyFlat() {
        val configuration = EqualizerConfiguration(
            enabled = false,
            preampDb = 12.0,
            filters = listOf(peaking(gainDb = 8.0))
        )

        assertTrue(configuration.isEffectivelyFlat)
    }

    @Test
    fun zeroPreampAndZeroGainFilters_areEffectivelyFlat() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = EQUALIZER_DB_EPSILON,
            filters = listOf(peaking(gainDb = -EQUALIZER_DB_EPSILON))
        )

        assertTrue(configuration.isEffectivelyFlat)
    }

    @Test
    fun nonZeroPreamp_isNotFlat() {
        assertFalse(
            EqualizerConfiguration(
                enabled = true,
                preampDb = 1.0,
                filters = emptyList()
            ).isEffectivelyFlat
        )
    }

    @Test
    fun enabledNonZeroBand_isNotFlat() {
        assertFalse(
            EqualizerConfiguration(
                enabled = true,
                preampDb = 0.0,
                filters = listOf(peaking(gainDb = 3.0))
            ).isEffectivelyFlat
        )
    }

    @Test
    fun disabledNonZeroBand_doesNotMakeConfigurationActive() {
        assertTrue(
            EqualizerConfiguration(
                enabled = true,
                preampDb = 0.0,
                filters = listOf(
                    peaking(gainDb = 3.0, enabled = false)
                )
            ).isEffectivelyFlat
        )
    }

    @Test
    fun nonFinitePreamp_isRejected() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        ).forEach { preampDb ->
            assertThrows(IllegalArgumentException::class.java) {
                EqualizerConfiguration(
                    enabled = true,
                    preampDb = preampDb,
                    filters = emptyList()
                )
            }
        }
    }

    @Test
    fun filterOrderIsPreservedAndCallerMutationCannotChangeIt() {
        val first = peaking(frequencyHz = 500.0, gainDb = 2.0)
        val second = EqualizerFilterSpec.LowShelf(
            frequencyHz = 100.0,
            gainDb = -1.0
        )
        val source = mutableListOf<EqualizerFilterSpec>(first, second)
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = source
        )

        source.reverse()

        assertEquals(listOf(first, second), configuration.filters)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (configuration.filters as MutableList<EqualizerFilterSpec>).clear()
        }
    }

    private fun peaking(
        frequencyHz: Double = 1_000.0,
        gainDb: Double,
        enabled: Boolean = true
    ) = EqualizerFilterSpec.Peaking(
        frequencyHz = frequencyHz,
        gainDb = gainDb,
        q = 1.0,
        enabled = enabled
    )
}
