package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.dsp.GraphicEqualizerDefaults
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPreferencesStateTest {
    @Test
    fun defaultsAreDisabledFlatAndAutomaticallyHeadroomProtected() {
        val state = EqualizerPreferencesState()

        assertFalse(state.enabled)
        assertEquals(0.0, state.preampDb, 0.0)
        assertTrue(state.automaticHeadroomEnabled)
        assertEquals(
            GraphicEqualizerDefaults.BAND_COUNT,
            state.bandGainsDb.size
        )
        state.bandGainsDb.forEach { gain ->
            assertEquals(0.0, gain, 0.0)
        }
        assertTrue(state.userPresets.isEmpty())
        assertFalse(state.limiterEnabled)
        assertEquals(-1.0, state.limiterCeilingDbfs, 0.0)
    }

    @Test
    fun editsNormalizeToOneDecimalAndPreserveOtherValues() {
        val state = EqualizerPreferencesState(enabled = true)
            .withPreampDb(-3.26)
            .withBandGainDb(4, 2.24)

        assertTrue(state.enabled)
        assertEquals(-3.3, state.preampDb, 0.0)
        assertEquals(2.2, state.bandGainsDb[4], 0.0)
        assertEquals(0.0, state.bandGainsDb[3], 0.0)
    }

    @Test
    fun constructorsNormalizeAndDefensivelyCopyCollections() {
        val mutableGains = MutableList(10) { 0.04 }
        val preset = UserEqualizerPreset(
            id = "id",
            name = "  Saved Curve  ",
            preampDb = -1.26,
            automaticHeadroomEnabled = false,
            bandGainsDb = mutableGains
        )
        val mutablePresets = mutableListOf(preset)
        val state = EqualizerPreferencesState(
            preampDb = -2.24,
            bandGainsDb = mutableGains,
            userPresets = mutablePresets
        )

        mutableGains[0] = 10.0
        mutablePresets.clear()

        assertEquals(-2.2, state.preampDb, 0.0)
        assertEquals(0.0, state.bandGainsDb[0], 0.0)
        assertEquals("Saved Curve", preset.name)
        assertEquals(-1.3, preset.preampDb, 0.0)
        assertEquals(1, state.userPresets.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (state.bandGainsDb as MutableList<Double>).add(1.0)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (state.userPresets as MutableList<UserEqualizerPreset>)
                .clear()
        }
    }

    @Test
    fun invalidCountsIndicesAndRangesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerPreferencesState(
                bandGainsDb = List(9) { 0.0 }
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerPreferencesState()
                .withBandGainDb(10, 0.0)
        }
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            -12.1,
            12.1
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                EqualizerPreferencesState()
                    .withBandGainDb(0, invalid)
            }
        }
        listOf(-15.1, 6.1).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                EqualizerPreferencesState()
                    .withPreampDb(invalid)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerPreferencesState(
                preampDb = Double.NaN
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserEqualizerPreset(
                id = "bad",
                name = "Flat",
                preampDb = 0.0,
                automaticHeadroomEnabled = true,
                bandGainsDb = List(9) { 0.0 }
            )
        }
    }

    @Test
    fun dspConversionUsesAuthoritativeFrequenciesAndQ() {
        val state = EqualizerPreferencesState(
            enabled = true
        ).withBandGainDb(2, 4.0)
        val configuration = state.toDspConfiguration()

        assertTrue(configuration.enabled)
        assertEquals(
            GraphicEqualizerDefaults.frequenciesHz,
            configuration.filters.map { filter ->
                filter.frequencyHz
            }
        )
        configuration.filters.forEach { filter ->
            assertEquals(
                GraphicEqualizerDefaults.Q,
                (filter as EqualizerFilterSpec.Peaking).q,
                0.0
            )
        }
    }

    @Test
    fun limiterSettingsNormalizeAndRemainGlobalAcrossCurveChanges() {
        val state = EqualizerPreferencesState(
            limiterEnabled = true,
            limiterCeilingDbfs = -1.26
        )
            .withBandGainDb(0, 4.0)
            .withPreampDb(-2.0)

        assertTrue(state.limiterEnabled)
        assertEquals(-1.3, state.limiterCeilingDbfs, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            state.withLimiterCeilingDbfs(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.withLimiterCeilingDbfs(-3.1)
        }
    }
}
