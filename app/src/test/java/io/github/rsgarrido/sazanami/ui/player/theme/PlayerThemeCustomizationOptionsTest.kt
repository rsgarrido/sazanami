package io.github.rsgarrido.sazanami.ui.player.theme

import io.github.rsgarrido.sazanami.data.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerThemeCustomizationOptionsTest {
    @Test
    fun retroThemes_returnExpectedCustomizationOptions() {
        assertOptions(
            PlayerTheme.CLASSIC_WHEEL,
            PlayerThemeTokenField.SHELL to "Shell",
            PlayerThemeTokenField.ACCENT to "Wheel",
            PlayerThemeTokenField.SECONDARY_ACCENT to "Center button"
        )
        assertOptions(
            PlayerTheme.POCKET_FLIP,
            PlayerThemeTokenField.SHELL to "Shell",
            PlayerThemeTokenField.ACCENT to "Buttons",
            PlayerThemeTokenField.SECONDARY_ACCENT to "Accent"
        )
        assertOptions(
            PlayerTheme.POCKET_CASSETTE,
            PlayerThemeTokenField.SHELL to "Shell",
            PlayerThemeTokenField.ACCENT to "Panel",
            PlayerThemeTokenField.SECONDARY_ACCENT to "Active accent"
        )
        assertOptions(
            PlayerTheme.RETRO_RACK,
            PlayerThemeTokenField.SHELL to "Body",
            PlayerThemeTokenField.ACCENT to "LCD glow",
            PlayerThemeTokenField.SECONDARY_ACCENT to "Active accent"
        )
    }

    @Test
    fun defaultTheme_returnsNoCustomizationOptions() {
        assertEquals(emptyList<PlayerThemeCustomizationOption>(), PlayerTheme.DEFAULT.customizationOptions())
    }

    private fun assertOptions(
        playerTheme: PlayerTheme,
        vararg expected: Pair<PlayerThemeTokenField, String>
    ) {
        val actual = playerTheme.customizationOptions()

        assertEquals(expected.toList(), actual.map { option -> option.field to option.displayName })
        assertTrue(actual.all { option -> option.description.isNotBlank() })
    }
}
