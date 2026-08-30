package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RetroRackPaletteTest {
    @Test
    fun defaultPalette_keepsColorsNearLegacyValues() {
        assertColorNear(Color(0xFF0B0D10), RetroRackDefaultPalette.rackBackground)
        assertColorNear(Color(0xFF202329), RetroRackDefaultPalette.panelDark)
        assertColorNear(Color(0xFF4A4E57), RetroRackDefaultPalette.panelHeader)
        assertColorNear(Color(0xFF040705), RetroRackDefaultPalette.displayBlack)
        assertColorNear(Color(0xFF75F05F), RetroRackDefaultPalette.lcdGreen)
        assertColorNear(Color(0xFF51A94A), RetroRackDefaultPalette.lcdGreenDim)
        assertColorNear(Color(0xFFD2D5D9), RetroRackDefaultPalette.controlSilver)
        assertColorNear(Color(0xFF3D424B), RetroRackDefaultPalette.buttonFace)
        assertColorNear(Color(0xFF292D33), RetroRackDefaultPalette.buttonPressed)
        assertColorNear(Color(0xFF6FA75F), RetroRackDefaultPalette.activeButton)
        assertColorNear(Color(0xFF19351E), RetroRackDefaultPalette.selectedRow)
        assertColorNear(Color(0xFF9298A2), RetroRackDefaultPalette.rackHighlight)
        assertColorNear(Color(0xFF050608), RetroRackDefaultPalette.rackShadow)
        assertColorNear(Color(0xFF30343A), RetroRackDefaultPalette.inactiveTrack)
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

        val palette = RetroRackPalette.from(tokens)

        assertEquals(tokens.shellColor, palette.panelDark)
        assertEquals(tokens.accentColor, palette.lcdGreen)
        assertEquals(tokens.displayBackgroundColor, palette.displayBlack)
        assertEquals(tokens.displayTextColor, palette.controlSilver)
        assertEquals(tokens.secondaryAccentColor, palette.activeButton)
        assertNotEquals(RetroRackDefaultPalette.rackBackground, palette.rackBackground)
        assertNotEquals(RetroRackDefaultPalette.panelHeader, palette.panelHeader)
        assertNotEquals(RetroRackDefaultPalette.lcdGreenDim, palette.lcdGreenDim)
        assertNotEquals(RetroRackDefaultPalette.buttonPressed, palette.buttonPressed)
        assertNotEquals(RetroRackDefaultPalette.selectedRow, palette.selectedRow)
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.green, actual.green, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.blue, actual.blue, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.alpha, actual.alpha, LEGACY_COLOR_TOLERANCE)
    }

    private companion object {
        const val LEGACY_COLOR_TOLERANCE = 8f / 255f
    }
}
