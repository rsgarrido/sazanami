package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten

internal val ClassicWheelDefaultTokens = PlayerThemeTokens(
    shellColor = Color(0xFFF1EDE0),
    accentColor = Color(0xFFC8C6BC),
    displayBackgroundColor = Color(0xFFF7F7F2),
    displayTextColor = Color.Black,
    secondaryAccentColor = Color(0xFFF1EDE0)
)

internal class ClassicWheelPalette private constructor(tokens: PlayerThemeTokens) {
    val shell = tokens.shellColor
    val wheel = tokens.accentColor
    val wheelContent = tokens.accentColor.lighten(1f)
    val centerButton = tokens.secondaryAccentColor ?: tokens.shellColor
    val screenBackground = tokens.displayBackgroundColor
    val screenText = tokens.displayTextColor
    val screenTextMuted = tokens.displayTextColor.lighten(0.267f)
    val screenBezel = tokens.displayTextColor
    val statusBarBackground = tokens.displayBackgroundColor.darken(0.076f)
    val selectionAccent = ClassicWheelDecorativeColors.selectionAccent
    val selectionContent = tokens.displayBackgroundColor.lighten(1f)

    companion object {
        fun from(tokens: PlayerThemeTokens): ClassicWheelPalette =
            ClassicWheelPalette(tokens)
    }
}

internal val ClassicWheelDefaultPalette =
    ClassicWheelPalette.from(ClassicWheelDefaultTokens)

internal val LocalClassicWheelPalette = staticCompositionLocalOf {
    ClassicWheelDefaultPalette
}

internal val ClassicWheelColors: ClassicWheelPalette
    @Composable get() = LocalClassicWheelPalette.current

private object ClassicWheelDecorativeColors {
    // Kept stable so physical-device customization does not recolor the display UI.
    val selectionAccent = Color(0xFF2F80D8)
}
