package io.github.rsgarrido.sazanami.ui.player.theme

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.data.PlayerTheme

data class PlayerThemeColorPreset(
    val name: String,
    val color: Color
)

fun PlayerTheme.colorPresetsFor(
    field: PlayerThemeTokenField
): List<PlayerThemeColorPreset> {
    if (customizationOptions().none { option -> option.field == field }) {
        return emptyList()
    }

    val originalColor = defaultTokens().colorFor(field) ?: return emptyList()
    return listOf(PlayerThemeColorPreset("Original", originalColor)) + when (this to field) {
        PlayerTheme.CLASSIC_WHEEL to PlayerThemeTokenField.SHELL -> listOf(
            preset("Graphite", 0xFF34373C),
            preset("Pearl", 0xFFF3F0E8),
            preset("Cherry", 0xFF9E2A2B),
            preset("Navy", 0xFF263B59),
            preset("Forest", 0xFF355544)
        )

        PlayerTheme.CLASSIC_WHEEL to PlayerThemeTokenField.ACCENT -> listOf(
            preset("White", 0xFFF7F7F5),
            preset("Graphite", 0xFF34373C),
            preset("Silver", 0xFFC4C6C8),
            preset("Cream", 0xFFE9DFC5),
            preset("Slate", 0xFF65707D)
        )

        PlayerTheme.CLASSIC_WHEEL to PlayerThemeTokenField.SECONDARY_ACCENT -> listOf(
            preset("White", 0xFFF7F7F5),
            preset("Graphite", 0xFF34373C),
            preset("Silver", 0xFFC4C6C8),
            preset("Cream", 0xFFE9DFC5),
            preset("Accent", 0xFFD19A3C)
        )

        PlayerTheme.POCKET_FLIP to PlayerThemeTokenField.SHELL -> listOf(
            preset("Red", 0xFF9C3038),
            preset("Black", 0xFF202124),
            preset("Blue", 0xFF345A78),
            preset("Purple", 0xFF635078),
            preset("Cream", 0xFFE8DFC8)
        )

        PlayerTheme.POCKET_FLIP to PlayerThemeTokenField.ACCENT -> listOf(
            preset("Dark", 0xFF25272B),
            preset("Light", 0xFFE8E5DE),
            preset("Red", 0xFF9D3540),
            preset("Blue", 0xFF386D91),
            preset("Silver", 0xFFADB2B5)
        )

        PlayerTheme.POCKET_FLIP to PlayerThemeTokenField.SECONDARY_ACCENT -> listOf(
            preset("Pink", 0xFFD65B88),
            preset("Amber", 0xFFD68A2B),
            preset("Cyan", 0xFF2E9EAA),
            preset("Green", 0xFF4D8B62),
            preset("Violet", 0xFF7558A6)
        )

        PlayerTheme.POCKET_CASSETTE to PlayerThemeTokenField.SHELL -> listOf(
            preset("Silver", 0xFFB8BDC0),
            preset("Smoke", 0xFF646B70),
            preset("Sand", 0xFFC0AA84),
            preset("Red", 0xFF963A35),
            preset("Navy", 0xFF33495E)
        )

        PlayerTheme.POCKET_CASSETTE to PlayerThemeTokenField.ACCENT -> listOf(
            preset("Blue", 0xFF3D6D91),
            preset("Teal", 0xFF347C78),
            preset("Purple", 0xFF66527D),
            preset("Gray", 0xFF777D80),
            preset("Black", 0xFF26282B)
        )

        PlayerTheme.POCKET_CASSETTE to PlayerThemeTokenField.SECONDARY_ACCENT -> listOf(
            preset("Amber", 0xFFD98B2B),
            preset("Green", 0xFF4C9361),
            preset("Red", 0xFFC94C42),
            preset("Blue", 0xFF3F7DB2),
            preset("Cyan", 0xFF35A2A8)
        )

        PlayerTheme.RETRO_RACK to PlayerThemeTokenField.SHELL -> listOf(
            preset("Black", 0xFF17191B),
            preset("Graphite", 0xFF2D3034),
            preset("Charcoal", 0xFF3C4145),
            preset("Navy", 0xFF273744),
            preset("Steel", 0xFF596168)
        )

        PlayerTheme.RETRO_RACK to PlayerThemeTokenField.ACCENT -> listOf(
            preset("Green", 0xFF62B46F),
            preset("Amber", 0xFFD7A23D),
            preset("Cyan", 0xFF4BB8BA),
            preset("Blue", 0xFF5B8FC4),
            preset("Red", 0xFFC95C53)
        )

        PlayerTheme.RETRO_RACK to PlayerThemeTokenField.SECONDARY_ACCENT -> listOf(
            preset("Green", 0xFF61A968),
            preset("Amber", 0xFFD49A39),
            preset("Red", 0xFFC6534B),
            preset("Blue", 0xFF4C7FAE),
            preset("Cyan", 0xFF43A5A5)
        )

        PlayerTheme.POCKET_DISC to PlayerThemeTokenField.SHELL -> listOf(
            preset("Slate", 0xFF23374A),
            preset("Graphite", 0xFF343C46),
            preset("Navy", 0xFF243B58),
            preset("Silver", 0xFF747F88),
            preset("Plum", 0xFF51445E)
        )

        PlayerTheme.POCKET_DISC to PlayerThemeTokenField.ACCENT -> listOf(
            preset("Ice", 0xFF87D8F4),
            preset("Green", 0xFF8ED0A0),
            preset("Amber", 0xFFE1B65D),
            preset("Blue", 0xFF79AEE8),
            preset("Violet", 0xFFB4A0E4)
        )

        PlayerTheme.POCKET_DISC to PlayerThemeTokenField.SECONDARY_ACCENT -> listOf(
            preset("Cyan", 0xFF4AA8D1),
            preset("Green", 0xFF52A66B),
            preset("Amber", 0xFFC9963C),
            preset("Blue", 0xFF4D7FB8),
            preset("Violet", 0xFF7D69B5)
        )
        else -> emptyList()
    }
}

private fun PlayerThemeTokens.colorFor(field: PlayerThemeTokenField): Color? = when (field) {
    PlayerThemeTokenField.SHELL -> shellColor
    PlayerThemeTokenField.ACCENT -> accentColor
    PlayerThemeTokenField.DISPLAY_BACKGROUND -> displayBackgroundColor
    PlayerThemeTokenField.DISPLAY_TEXT -> displayTextColor
    PlayerThemeTokenField.SECONDARY_ACCENT -> secondaryAccentColor
}

private fun preset(name: String, argb: Long): PlayerThemeColorPreset {
    return PlayerThemeColorPreset(name = name, color = Color(argb.toInt()))
}
