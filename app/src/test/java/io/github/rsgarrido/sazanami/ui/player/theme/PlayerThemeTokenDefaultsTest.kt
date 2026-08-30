package io.github.rsgarrido.sazanami.ui.player.theme

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelDefaultTokens
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteDefaultTokens
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipDefaultTokens
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackDefaultTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlayerThemeTokenDefaultsTest {
    @Test
    fun everyPlayerTheme_hasDefaultTokens() {
        PlayerTheme.values().forEach { theme ->
            assertNotNull(theme.defaultTokens())
        }
    }

    @Test
    fun defaultTokens_returnsThemeSpecificDefaults() {
        assertEquals(DefaultPlayerThemeTokens, PlayerTheme.DEFAULT.defaultTokens())
        assertEquals(ClassicWheelDefaultTokens, PlayerTheme.CLASSIC_WHEEL.defaultTokens())
        assertEquals(RetroRackDefaultTokens, PlayerTheme.RETRO_RACK.defaultTokens())
        assertEquals(PocketFlipDefaultTokens, PlayerTheme.POCKET_FLIP.defaultTokens())
        assertEquals(PocketCassetteDefaultTokens, PlayerTheme.POCKET_CASSETTE.defaultTokens())
    }
}
