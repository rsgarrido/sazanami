package io.github.rsgarrido.sazanami.ui.player.pocketcassette

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PocketCassettePaletteTest {
    @Test
    fun defaultPalette_keepsProminentColorsNearLegacyValues() {
        assertColorNear(Color(0xFFE4E6E5), PocketCassetteDefaultPalette.silverLight)
        assertColorNear(Color(0xFFB9BEC0), PocketCassetteDefaultPalette.silver)
        assertColorNear(Color(0xFF5F686C), PocketCassetteDefaultPalette.silverDark)
        assertColorNear(Color(0xFF456D8E), PocketCassetteDefaultPalette.blue)
        assertColorNear(Color(0xFF294B67), PocketCassetteDefaultPalette.blueDark)
        assertColorNear(Color(0xFF080B0D), PocketCassetteDefaultPalette.window)
        assertColorNear(Color(0xFFE1E6E4), PocketCassetteDefaultPalette.windowText)
        assertColorNear(Color(0xFF15191B), PocketCassetteDefaultPalette.buttonPressed)
        assertColorNear(Color(0xFFE36E3D), PocketCassetteDefaultPalette.buttonActive)
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

        val palette = PocketCassettePalette.from(tokens)

        assertEquals(tokens.shellColor, palette.silver)
        assertEquals(tokens.accentColor, palette.blue)
        assertEquals(tokens.displayBackgroundColor, palette.window)
        assertEquals(tokens.displayTextColor, palette.windowText)
        assertEquals(tokens.secondaryAccentColor, palette.orange)
        assertNotEquals(PocketCassetteDefaultPalette.silverLight, palette.silverLight)
        assertNotEquals(PocketCassetteDefaultPalette.blueDark, palette.blueDark)
        assertNotEquals(PocketCassetteDefaultPalette.buttonPressed, palette.buttonPressed)
        assertNotEquals(PocketCassetteDefaultPalette.buttonActive, palette.buttonActive)
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.green, actual.green, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.blue, actual.blue, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.alpha, actual.alpha, LEGACY_COLOR_TOLERANCE)
    }

    private companion object {
        const val LEGACY_COLOR_TOLERANCE = 7f / 255f
    }
}
