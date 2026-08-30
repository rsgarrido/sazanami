package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PocketFlipPaletteTest {
    @Test
    fun defaultPalette_keepsProminentColorsNearLegacyValues() {
        assertColorNear(Color(0xFF982E3B), PocketFlipDefaultPalette.shell)
        assertColorNear(Color(0xFF76212D), PocketFlipDefaultPalette.shellBottom)
        assertColorNear(Color(0xFF263029), PocketFlipDefaultPalette.display)
        assertColorNear(Color(0xFFE0E7D8), PocketFlipDefaultPalette.screenText)
        assertColorNear(Color(0xFFA5C980), PocketFlipDefaultPalette.screenAccent)
        assertColorNear(Color(0xFF18191D), PocketFlipDefaultPalette.buttonPressed)
        assertColorNear(Color(0xFF6D203C), PocketFlipDefaultPalette.actionActive)
        assertColorNear(Color(0xFF35101F), PocketFlipDefaultPalette.actionPressed)
    }

    @Test
    fun from_derivesPaletteFromCustomizableBaseTokens() {
        val tokens = PlayerThemeTokens(
            shellColor = Color.Red,
            accentColor = Color.Green,
            displayBackgroundColor = Color.Blue,
            displayTextColor = Color.White,
            secondaryAccentColor = Color.Magenta
        )

        val palette = PocketFlipPalette.from(tokens)

        assertEquals(tokens.shellColor, palette.shell)
        assertEquals(tokens.accentColor, palette.screenAccent)
        assertEquals(tokens.displayBackgroundColor, palette.display)
        assertEquals(tokens.displayTextColor, palette.screenText)
        assertEquals(tokens.secondaryAccentColor, palette.actionActive)
        assertNotEquals(PocketFlipDefaultPalette.shellBottom, palette.shellBottom)
        assertNotEquals(PocketFlipDefaultPalette.seekInactive, palette.seekInactive)
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.green, actual.green, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.blue, actual.blue, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.alpha, actual.alpha, LEGACY_COLOR_TOLERANCE)
    }

    private companion object {
        const val LEGACY_COLOR_TOLERANCE = 5f / 255f
    }
}
