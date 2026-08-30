package io.github.rsgarrido.sazanami.ui.player.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerThemeTokenOverridesTest {
    private val baseTokens = PlayerThemeTokens(
        shellColor = Color(0xFF101010),
        accentColor = Color(0xFF202020),
        displayBackgroundColor = Color(0xFF303030),
        displayTextColor = Color(0xFF404040),
        secondaryAccentColor = Color(0xFF505050)
    )

    @Test
    fun applyOverrides_replacesOnlyProvidedFields() {
        val result = baseTokens.applyOverrides(
            PlayerThemeTokenOverrides(
                shellColor = Color(0xFFAABBCC),
                displayTextColor = Color(0xFFDDEEFF)
            )
        )

        assertEquals(Color(0xFFAABBCC), result.shellColor)
        assertEquals(baseTokens.accentColor, result.accentColor)
        assertEquals(baseTokens.displayBackgroundColor, result.displayBackgroundColor)
        assertEquals(Color(0xFFDDEEFF), result.displayTextColor)
        assertEquals(baseTokens.secondaryAccentColor, result.secondaryAccentColor)
    }

    @Test
    fun applyOverrides_withNoValuesPreservesTokens() {
        assertEquals(
            baseTokens,
            baseTokens.applyOverrides(PlayerThemeTokenOverrides())
        )
    }
}
