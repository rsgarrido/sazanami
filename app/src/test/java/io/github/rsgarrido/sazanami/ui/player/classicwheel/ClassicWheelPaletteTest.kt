package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClassicWheelPaletteTest {
    @Test
    fun defaultPalette_keepsLegacyDeviceAndScreenColors() {
        assertColorNear(Color(0xFFF1EDE0), ClassicWheelDefaultPalette.shell)
        assertColorNear(Color(0xFFC8C6BC), ClassicWheelDefaultPalette.wheel)
        assertColorNear(Color(0xFFF1EDE0), ClassicWheelDefaultPalette.centerButton)
        assertColorNear(Color(0xFFF7F7F2), ClassicWheelDefaultPalette.screenBackground)
        assertColorNear(Color.Black, ClassicWheelDefaultPalette.screenText)
        assertColorNear(Color.DarkGray, ClassicWheelDefaultPalette.screenTextMuted)
        assertColorNear(Color(0xFFE4E4E0), ClassicWheelDefaultPalette.statusBarBackground)
        assertColorNear(Color(0xFF2F80D8), ClassicWheelDefaultPalette.selectionAccent)
    }

    @Test
    fun from_appliesCustomPhysicalDeviceTokens() {
        val tokens = PlayerThemeTokens(
            shellColor = Color.Red,
            accentColor = Color.Green,
            displayBackgroundColor = Color.Blue,
            displayTextColor = Color.White,
            secondaryAccentColor = Color.Magenta
        )

        val palette = ClassicWheelPalette.from(tokens)

        assertEquals(tokens.shellColor, palette.shell)
        assertEquals(tokens.accentColor, palette.wheel)
        assertEquals(tokens.secondaryAccentColor, palette.centerButton)
        assertEquals(tokens.displayBackgroundColor, palette.screenBackground)
        assertEquals(tokens.displayTextColor, palette.screenText)
        assertNotEquals(ClassicWheelDefaultPalette.statusBarBackground, palette.statusBarBackground)
        assertEquals(ClassicWheelDefaultPalette.selectionAccent, palette.selectionAccent)
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.green, actual.green, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.blue, actual.blue, LEGACY_COLOR_TOLERANCE)
        assertEquals(expected.alpha, actual.alpha, LEGACY_COLOR_TOLERANCE)
    }

    private companion object {
        const val LEGACY_COLOR_TOLERANCE = 1f / 255f
    }
}
