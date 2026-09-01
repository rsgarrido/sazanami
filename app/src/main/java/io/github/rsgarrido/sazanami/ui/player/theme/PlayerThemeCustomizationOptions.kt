package io.github.rsgarrido.sazanami.ui.player.theme

import io.github.rsgarrido.sazanami.data.PlayerTheme

enum class PlayerThemeTokenField {
    SHELL,
    ACCENT,
    DISPLAY_BACKGROUND,
    DISPLAY_TEXT,
    SECONDARY_ACCENT
}

data class PlayerThemeCustomizationOption(
    val field: PlayerThemeTokenField,
    val displayName: String,
    val description: String
)

fun PlayerTheme.customizationOptions(): List<PlayerThemeCustomizationOption> = when (this) {
    PlayerTheme.DEFAULT -> emptyList()
    PlayerTheme.CLASSIC_WHEEL -> listOf(
        option(PlayerThemeTokenField.SHELL, "Shell", "Main player body"),
        option(PlayerThemeTokenField.ACCENT, "Wheel", "Click wheel"),
        option(PlayerThemeTokenField.SECONDARY_ACCENT, "Center button", "Center select button")
    )

    PlayerTheme.POCKET_FLIP -> listOf(
        option(PlayerThemeTokenField.SHELL, "Shell", "Main player body"),
        option(PlayerThemeTokenField.ACCENT, "Buttons", "Primary controls and buttons"),
        option(PlayerThemeTokenField.SECONDARY_ACCENT, "Accent", "Secondary highlights")
    )

    PlayerTheme.POCKET_CASSETTE -> listOf(
        option(PlayerThemeTokenField.SHELL, "Shell", "Main player body"),
        option(PlayerThemeTokenField.ACCENT, "Panel", "Cassette panel and primary details"),
        option(PlayerThemeTokenField.SECONDARY_ACCENT, "Active accent", "Active controls and highlights")
    )

    PlayerTheme.RETRO_RACK -> listOf(
        option(PlayerThemeTokenField.SHELL, "Body", "Rack unit body"),
        option(PlayerThemeTokenField.ACCENT, "LCD glow", "LCD display glow"),
        option(PlayerThemeTokenField.SECONDARY_ACCENT, "Active accent", "Active controls and button highlights")
    )

    PlayerTheme.POCKET_DISC -> listOf(
        option(PlayerThemeTokenField.SHELL, "Body", "Digital player body"),
        option(PlayerThemeTokenField.ACCENT, "LCD glow", "Display text, segments, and meter glow"),
        option(PlayerThemeTokenField.SECONDARY_ACCENT, "Active accent", "Active transport and utility controls")
    )
}

private fun option(
    field: PlayerThemeTokenField,
    displayName: String,
    description: String
) = PlayerThemeCustomizationOption(
    field = field,
    displayName = displayName,
    description = description
)
