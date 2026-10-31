package io.github.rsgarrido.sazanami.data.preferences

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketHorizonAppearancePreferencesTest {
    private val console = stringPreferencesKey("pocket_horizon_console_finish")
    private val lcd = stringPreferencesKey("pocket_horizon_lcd_palette")
    private val controls = stringPreferencesKey("pocket_horizon_wave_treatment")

    @Test
    fun missingAppearanceDefaultsBeforeLoadingAndInPreviouslySavedPreferences() {
        assertEquals(PocketHorizonAppearance.Default, AppPreferencesState().pocketHorizonAppearance)
        assertFalse(AppPreferencesState().isLoaded)
        val loaded = decodeAppPreferences(mutablePreferencesOf())
        assertTrue(loaded.isLoaded)
        assertEquals(PocketHorizonAppearance.Default, loaded.pocketHorizonAppearance)
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, loaded.tabletLandscapeTheme)
    }

    @Test
    fun eachCategoryDecodesIndependently() {
        val values = mutablePreferencesOf(console to PocketHorizonConsoleFinish.MIDNIGHT_BLUE.id)
        assertEquals(PocketHorizonAppearance(consoleFinish = PocketHorizonConsoleFinish.MIDNIGHT_BLUE),
            decodeAppPreferences(values).pocketHorizonAppearance)
        values[lcd] = PocketHorizonLcdPalette.WARM_AMBER.id
        values[controls] = PocketHorizonWaveTreatment.SUBTLE.id
        assertEquals(PocketHorizonAppearance(PocketHorizonConsoleFinish.MIDNIGHT_BLUE,
            PocketHorizonLcdPalette.WARM_AMBER, PocketHorizonWaveTreatment.SUBTLE), decodeAppPreferences(values).pocketHorizonAppearance)
    }

    @Test
    fun aCorruptedCategoryDoesNotDiscardOtherValidCategories() {
        for (invalid in listOf("", "future_preset", "ORIGINAL")) {
            fun valid() = mutablePreferencesOf(console to PocketHorizonConsoleFinish.DEEP_BURGUNDY.id,
                lcd to PocketHorizonLcdPalette.EMERALD_TEAL.id, controls to PocketHorizonWaveTreatment.OFF.id)
            val badConsole = valid().apply { this[console] = invalid }
            assertEquals(PocketHorizonAppearance(PocketHorizonConsoleFinish.GRAPHITE,
                PocketHorizonLcdPalette.EMERALD_TEAL, PocketHorizonWaveTreatment.OFF), decodeAppPreferences(badConsole).pocketHorizonAppearance)
            val badLcd = valid().apply { this[lcd] = invalid }
            assertEquals(PocketHorizonAppearance(PocketHorizonConsoleFinish.DEEP_BURGUNDY,
                PocketHorizonLcdPalette.BLUE_VIOLET, PocketHorizonWaveTreatment.OFF), decodeAppPreferences(badLcd).pocketHorizonAppearance)
            val badControls = valid().apply { this[controls] = invalid }
            assertEquals(PocketHorizonAppearance(PocketHorizonConsoleFinish.DEEP_BURGUNDY,
                PocketHorizonLcdPalette.EMERALD_TEAL, PocketHorizonWaveTreatment.STANDARD), decodeAppPreferences(badControls).pocketHorizonAppearance)
        }
    }

    @Test
    fun disabledAppearanceNeverChangesTheNormalThemeOrItsTokenOverrides() {
        val loaded = decodeAppPreferences(mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.CLASSIC_WHEEL.id,
            stringPreferencesKey("tablet_landscape_theme") to TabletLandscapeTheme.NORMAL_PLAYER.id,
            stringPreferencesKey("classic_wheel.shell") to "#FF123456",
            console to PocketHorizonConsoleFinish.SILVER.id,
            lcd to PocketHorizonLcdPalette.WARM_AMBER.id,
            controls to PocketHorizonWaveTreatment.SUBTLE.id
        ))
        assertEquals(PlayerTheme.CLASSIC_WHEEL, loaded.selectedPlayerTheme)
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, loaded.tabletLandscapeTheme)
        assertEquals(Color(0xFF123456), loaded.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL]?.shellColor)
        assertEquals(PocketHorizonAppearance(PocketHorizonConsoleFinish.SILVER,
            PocketHorizonLcdPalette.WARM_AMBER, PocketHorizonWaveTreatment.SUBTLE), loaded.pocketHorizonAppearance)
    }
}
