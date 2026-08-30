package io.github.rsgarrido.sazanami.player.equalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqualizerPresetMatcherTest {
    @Test
    fun flatAndEveryBuiltInMatchRegardlessOfEnabledState() {
        GraphicEqualizerPresets.builtIns.forEach { preset ->
            val state = EqualizerPreferencesState(enabled = false)
                .applyPreset(preset)

            assertEquals(
                preset.name,
                EqualizerPresetMatcher.match(state)?.name
            )
            assertEquals(
                preset.name,
                EqualizerPresetMatcher
                    .match(state.copy(enabled = true))
                    ?.name
            )
        }
    }

    @Test
    fun modifiedCurvePreampOrHeadroomIsCustom() {
        val flat = EqualizerPreferencesState()

        assertNull(
            EqualizerPresetMatcher.match(
                flat.withBandGainDb(0, 0.1)
            )
        )
        assertNull(
            EqualizerPresetMatcher.match(
                flat.withPreampDb(-0.1)
            )
        )
        assertNull(
            EqualizerPresetMatcher.match(
                flat.withAutomaticHeadroomEnabled(false)
            )
        )
    }

    @Test
    fun builtInWinsBeforeIdenticalDeterministicUserMatch() {
        val flat = EqualizerPreferencesState()
        val identicalFlat = UserEqualizerPreset(
            id = "user-flat",
            name = "User Flat",
            preampDb = 0.0,
            automaticHeadroomEnabled = true,
            bandGainsDb = List(10) { 0.0 }
        )

        assertEquals(
            "Flat",
            EqualizerPresetMatcher
                .match(flat.copy(userPresets = listOf(identicalFlat)))
                ?.name
        )
    }

    @Test
    fun userMatchesByDeterministicNameOrdering() {
        val curve = EqualizerPreferencesState()
            .withBandGainDb(0, 1.0)
        val zed = GraphicEqualizerPresets.createUserPreset(
            "Zed",
            curve,
            "2"
        )
        val alpha = GraphicEqualizerPresets.createUserPreset(
            "Alpha",
            curve,
            "1"
        )

        assertEquals(
            "Alpha",
            EqualizerPresetMatcher.match(
                curve.copy(userPresets = listOf(zed, alpha))
            )?.name
        )
    }
}
