package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.GraphicEqualizerPresets
import io.github.rsgarrido.sazanami.player.equalizer.applyPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerScreenStateTest {
    @Test
    fun settingsSummaryUsesOffPresetAndCustomTruthfully() {
        val off = EqualizerScreenState(
            editablePreferences = EqualizerPreferencesState()
        )
        val bass = EqualizerPreferencesState(enabled = true)
            .applyPreset(
                GraphicEqualizerPresets.builtIns[1]
            )
        val custom = bass.withBandGainDb(0, 3.0)

        assertEquals("Off", off.settingsSummary)
        assertEquals(
            "Graphic \u00b7 Bass Lift",
            EqualizerScreenState(
                editablePreferences = bass,
                presetMatch = presetMatchFor(bass)
            ).settingsSummary
        )
        assertEquals(
            "Graphic \u00b7 Custom",
            EqualizerScreenState(
                editablePreferences = custom,
                presetMatch = presetMatchFor(custom)
            ).settingsSummary
        )
    }

    @Test
    fun comparisonRequiresEnabledNonFlatCurve() {
        val offActive = EqualizerPreferencesState()
            .withBandGainDb(0, 4.0)
        val onFlat = EqualizerPreferencesState(enabled = true)
        val onActive = offActive.withEnabled(true)

        assertFalse(
            EqualizerScreenState(
                editablePreferences = offActive
            ).comparisonAvailable
        )
        assertFalse(
            EqualizerScreenState(
                editablePreferences = onFlat
            ).comparisonAvailable
        )
        assertTrue(
            EqualizerScreenState(
                editablePreferences = onActive
            ).comparisonAvailable
        )
    }

    @Test
    fun limiterMakesExactComparisonUnavailableUntilDisabled() {
        val active = EqualizerPreferencesState(
            enabled = true,
            limiterEnabled = true
        ).withBandGainDb(0, 4.0)

        assertFalse(
            EqualizerScreenState(
                editablePreferences = active
            ).comparisonAvailable
        )
        assertTrue(
            EqualizerScreenState(
                editablePreferences =
                    active.withLimiterEnabled(false)
            ).comparisonAvailable
        )
    }
}
