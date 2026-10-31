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
import org.junit.Test

class TabletLandscapeThemePreferencesTest {
    @Test
    fun registeredChoicesDecodeWithoutChangingPrimaryCustomizationOrAdvanceAppearance() {
        val landscapeKey = stringPreferencesKey("tablet_landscape_theme")
        val saved = mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to "classic_wheel",
            stringPreferencesKey("classic_wheel.shell") to "#FF123456",
            stringPreferencesKey("pocket_advance_console_finish") to "forest_green",
            stringPreferencesKey("pocket_advance_lcd_palette") to "warm_amber",
            stringPreferencesKey("pocket_advance_controls_finish") to "light_gray"
        )
        val appearance = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.FOREST_GREEN,
            PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY)
        // Include returning from the reserved theme to Advance; no appearance belongs to Horizon yet.
        listOf("normal_player" to TabletLandscapeTheme.NORMAL_PLAYER,
            "pocket_advance" to TabletLandscapeTheme.POCKET_ADVANCE,
            "pocket_horizon" to TabletLandscapeTheme.POCKET_HORIZON,
            "pocket_advance" to TabletLandscapeTheme.POCKET_ADVANCE).forEach { (id, expected) ->
            saved[landscapeKey] = id
            val loaded = decodeAppPreferences(saved)
            assertEquals(expected, loaded.tabletLandscapeTheme)
            assertEquals(id, loaded.tabletLandscapeTheme.id)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, loaded.selectedPlayerTheme)
            assertEquals(Color(0xFF123456), loaded.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL]?.shellColor)
            assertEquals(appearance, loaded.pocketAdvanceAppearance)
        }
    }
}
