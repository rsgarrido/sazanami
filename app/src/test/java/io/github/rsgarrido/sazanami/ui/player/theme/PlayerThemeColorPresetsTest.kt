package io.github.rsgarrido.sazanami.ui.player.theme

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.data.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerThemeColorPresetsTest {
    @Test
    fun exposedRetroOptions_haveOriginalDefaultColorFirst() {
        PlayerTheme.values()
            .filter { playerTheme -> playerTheme != PlayerTheme.DEFAULT }
            .forEach { playerTheme ->
                playerTheme.customizationOptions().forEach { option ->
                    val presets = playerTheme.colorPresetsFor(option.field)

                    assertTrue("${playerTheme.id}.${option.field} has no presets", presets.isNotEmpty())
                    assertEquals("Original", presets.first().name)
                    assertEquals(
                        playerTheme.defaultTokens().colorFor(option.field),
                        presets.first().color
                    )
                    assertTrue(presets.size in 6..10)
                    assertTrue(presets.all { preset -> preset.name.isNotBlank() })
                    assertTrue(presets.all { preset -> preset.color.alpha == 1f })
                }
            }
    }

    @Test
    fun unsupportedFields_returnNoPresets() {
        PlayerThemeTokenField.values().forEach { field ->
            assertTrue(PlayerTheme.DEFAULT.colorPresetsFor(field).isEmpty())
        }

        listOf(
            PlayerThemeTokenField.DISPLAY_BACKGROUND,
            PlayerThemeTokenField.DISPLAY_TEXT
        ).forEach { field ->
            PlayerTheme.values()
                .filter { playerTheme -> playerTheme != PlayerTheme.DEFAULT }
                .forEach { playerTheme ->
                    assertTrue(playerTheme.colorPresetsFor(field).isEmpty())
                }
        }
    }

    private fun PlayerThemeTokens.colorFor(field: PlayerThemeTokenField): Color? = when (field) {
        PlayerThemeTokenField.SHELL -> shellColor
        PlayerThemeTokenField.ACCENT -> accentColor
        PlayerThemeTokenField.DISPLAY_BACKGROUND -> displayBackgroundColor
        PlayerThemeTokenField.DISPLAY_TEXT -> displayTextColor
        PlayerThemeTokenField.SECONDARY_ACCENT -> secondaryAccentColor
    }
}
