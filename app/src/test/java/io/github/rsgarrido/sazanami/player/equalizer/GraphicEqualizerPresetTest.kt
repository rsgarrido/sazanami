package io.github.rsgarrido.sazanami.player.equalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicEqualizerPresetTest {
    @Test
    fun builtInsHaveRequiredNamesOrderAndExactValues() {
        val presets = GraphicEqualizerPresets.builtIns

        assertEquals(
            listOf(
                "Flat",
                "Bass Lift",
                "Treble Lift",
                "Vocal Focus",
                "Warm",
                "Reduced Bass"
            ),
            presets.map { preset -> preset.name }
        )
        assertEquals(
            listOf(
                4.0,
                3.5,
                2.5,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0
            ),
            presets[1].bandGainsDb
        )
        presets.forEach { preset ->
            assertEquals(0.0, preset.preampDb, 0.0)
            assertTrue(preset.automaticHeadroomEnabled)
            assertEquals(10, preset.bandGainsDb.size)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (presets[0].bandGainsDb as MutableList<Double>)
                .add(1.0)
        }
    }

    @Test
    fun applyingPresetRetainsGlobalEnabledState() {
        val state = EqualizerPreferencesState(enabled = false)
        val applied = state.applyPreset(
            GraphicEqualizerPresets.builtIns[1]
        )

        assertFalse(applied.enabled)
        assertEquals(4.0, applied.bandGainsDb.first(), 0.0)
    }

    @Test
    fun userPresetNamesAreValidatedAndIdsAreStable() {
        val state = EqualizerPreferencesState()
            .withBandGainDb(0, 3.0)
        val first = GraphicEqualizerPresets.createUserPreset(
            name = " My Curve ",
            state = state,
            id = "stable-id"
        )
        val second = GraphicEqualizerPresets.createUserPreset(
            name = "Other Curve",
            state = state
        )

        assertEquals("stable-id", first.id)
        assertEquals("My Curve", first.name)
        assertNotEquals(first.id, second.id)
        assertThrows(IllegalArgumentException::class.java) {
            GraphicEqualizerPresets.createUserPreset(
                name = "bass lift",
                state = state
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EqualizerPreferencesState(
                userPresets = listOf(
                    UserEqualizerPreset(
                        id = "reserved",
                        name = "Flat",
                        preampDb = 0.0,
                        automaticHeadroomEnabled = true,
                        bandGainsDb = List(10) { 0.0 }
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraphicEqualizerPresets.createUserPreset(
                name = "my curve",
                state = state.copy(userPresets = listOf(first))
            )
        }
    }

    @Test
    fun renameAndDeleteDoNotChangeTheActiveCurve() {
        val active = EqualizerPreferencesState()
            .withBandGainDb(3, 2.0)
        val preset = GraphicEqualizerPresets.createUserPreset(
            name = "First",
            state = active,
            id = "id"
        )
        val renamed = GraphicEqualizerPresets.renameUserPreset(
            presetId = "id",
            newName = "Second",
            userPresets = listOf(preset)
        ).single()

        assertEquals("id", renamed.id)
        assertEquals(preset.bandGainsDb, renamed.bandGainsDb)
        assertEquals(2.0, active.bandGainsDb[3], 0.0)
    }
}
