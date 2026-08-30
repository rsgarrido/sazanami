package io.github.rsgarrido.sazanami.ui.player.theme

import androidx.compose.ui.graphics.Color

data class PlayerThemeTokenOverrides(
    val shellColor: Color? = null,
    val accentColor: Color? = null,
    val displayBackgroundColor: Color? = null,
    val displayTextColor: Color? = null,
    val secondaryAccentColor: Color? = null
)

fun PlayerThemeTokens.applyOverrides(
    overrides: PlayerThemeTokenOverrides
): PlayerThemeTokens = copy(
    shellColor = overrides.shellColor ?: shellColor,
    accentColor = overrides.accentColor ?: accentColor,
    displayBackgroundColor = overrides.displayBackgroundColor ?: displayBackgroundColor,
    displayTextColor = overrides.displayTextColor ?: displayTextColor,
    secondaryAccentColor = overrides.secondaryAccentColor ?: secondaryAccentColor
)
