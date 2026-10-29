package io.github.rsgarrido.sazanami.data.preferences

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketAdvanceAppearancePreferencesTest {
    private val console = stringPreferencesKey("pocket_advance_console_finish")
    private val lcd = stringPreferencesKey("pocket_advance_lcd_palette")
    private val controls = stringPreferencesKey("pocket_advance_controls_finish")

    @Test
    fun missingAppearanceDefaultsBeforeLoadingAndInPreviouslySavedPreferences() {
        assertEquals(PocketAdvanceAppearance.Default, AppPreferencesState().pocketAdvanceAppearance)
        assertFalse(AppPreferencesState().isLoaded)
        val loaded = decodeAppPreferences(mutablePreferencesOf())
        assertTrue(loaded.isLoaded)
        assertEquals(PocketAdvanceAppearance.Default, loaded.pocketAdvanceAppearance)
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, loaded.tabletLandscapeTheme)
    }

    @Test
    fun eachCategoryDecodesIndependently() {
        val values = mutablePreferencesOf(console to PocketAdvanceConsoleFinish.OCEAN_BLUE.id)
        assertEquals(PocketAdvanceAppearance(consoleFinish = PocketAdvanceConsoleFinish.OCEAN_BLUE),
            decodeAppPreferences(values).pocketAdvanceAppearance)
        values[lcd] = PocketAdvanceLcdPalette.WARM_AMBER.id
        values[controls] = PocketAdvanceControlsFinish.LIGHT_GRAY.id
        assertEquals(PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
            PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY), decodeAppPreferences(values).pocketAdvanceAppearance)
    }

    @Test
    fun aCorruptedCategoryDoesNotDiscardOtherValidCategories() {
        for (invalid in listOf("", "future_preset", "ORIGINAL")) {
            fun valid() = mutablePreferencesOf(console to PocketAdvanceConsoleFinish.BERRY.id,
                lcd to PocketAdvanceLcdPalette.CLASSIC_GREEN.id, controls to PocketAdvanceControlsFinish.DEEP_NAVY.id)
            val badConsole = valid().apply { this[console] = invalid }
            assertEquals(PocketAdvanceAppearance(PocketAdvanceConsoleFinish.ORIGINAL_PURPLE,
                PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.DEEP_NAVY), decodeAppPreferences(badConsole).pocketAdvanceAppearance)
            val badLcd = valid().apply { this[lcd] = invalid }
            assertEquals(PocketAdvanceAppearance(PocketAdvanceConsoleFinish.BERRY,
                PocketAdvanceLcdPalette.ORIGINAL, PocketAdvanceControlsFinish.DEEP_NAVY), decodeAppPreferences(badLcd).pocketAdvanceAppearance)
            val badControls = valid().apply { this[controls] = invalid }
            assertEquals(PocketAdvanceAppearance(PocketAdvanceConsoleFinish.BERRY,
                PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.ORIGINAL_DARK), decodeAppPreferences(badControls).pocketAdvanceAppearance)
        }
    }

    @Test
    fun disabledAppearanceNeverChangesTheNormalThemeOrItsTokenOverrides() {
        val loaded = decodeAppPreferences(mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.CLASSIC_WHEEL.id,
            stringPreferencesKey("tablet_landscape_theme") to TabletLandscapeTheme.NORMAL_PLAYER.id,
            stringPreferencesKey("classic_wheel.shell") to "#FF123456",
            console to PocketAdvanceConsoleFinish.FOREST_GREEN.id,
            lcd to PocketAdvanceLcdPalette.WARM_AMBER.id,
            controls to PocketAdvanceControlsFinish.LIGHT_GRAY.id
        ))
        assertEquals(PlayerTheme.CLASSIC_WHEEL, loaded.selectedPlayerTheme)
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, loaded.tabletLandscapeTheme)
        assertEquals(Color(0xFF123456), loaded.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL]?.shellColor)
        assertEquals(PocketAdvanceAppearance(PocketAdvanceConsoleFinish.FOREST_GREEN,
            PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY), loaded.pocketAdvanceAppearance)
    }
}
