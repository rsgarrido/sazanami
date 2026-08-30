package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten

@Composable
internal fun Modifier.rackBevel(pressed: Boolean = false): Modifier {
    val colors = LocalRetroRackPalette.current
    return drawWithContent {
    drawContent()
    val stroke = 1f
    val topLeft = if (pressed) colors.rackShadow else colors.rackHighlight
    val bottomRight = if (pressed) colors.rackHighlight else colors.rackShadow
    drawLine(topLeft, Offset.Zero, Offset(size.width, 0f), stroke)
    drawLine(topLeft, Offset.Zero, Offset(0f, size.height), stroke)
    drawLine(bottomRight, Offset(0f, size.height), Offset(size.width, size.height), stroke)
    drawLine(bottomRight, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
    }
}

internal val RetroRackDefaultTokens = PlayerThemeTokens(
    shellColor = Color(0xFF202329),
    accentColor = Color(0xFF75F05F),
    displayBackgroundColor = Color(0xFF040705),
    displayTextColor = Color(0xFFD2D5D9),
    secondaryAccentColor = Color(0xFF6FA75F)
)

internal class RetroRackPalette private constructor(tokens: PlayerThemeTokens) {
    private val shell = tokens.shellColor
    private val accent = tokens.accentColor
    private val activeAccent = tokens.secondaryAccentColor ?: accent.darken(0.30f)

    val rackBackground = shell.darken(0.628f)
    val panelDark = shell
    val panelHeader = shell.lighten(0.199f)
    val panelHeaderEnd = shell
    val displayBlack = tokens.displayBackgroundColor
    val lcdGreen = accent
    val lcdGreenDim = accent.darken(0.289f)
    val controlSilver = tokens.displayTextColor
    val buttonFace = shell.lighten(0.143f)
    val buttonPressed = shell.lighten(0.044f)
    val activeButton = activeAccent
    val selectedRow = accent.darken(0.770f)
    val rackHighlight = shell.lighten(0.535f)
    val rackShadow = shell.darken(0.822f)
    val inactiveTrack = shell.lighten(0.076f)

    companion object {
        fun from(tokens: PlayerThemeTokens): RetroRackPalette = RetroRackPalette(tokens)
    }
}

internal val RetroRackDefaultPalette = RetroRackPalette.from(RetroRackDefaultTokens)

internal val LocalRetroRackPalette = staticCompositionLocalOf {
    RetroRackDefaultPalette
}

internal val RetroRackColors: RetroRackPalette
    @Composable get() = LocalRetroRackPalette.current

internal val RackBackground @Composable get() = RetroRackColors.rackBackground
internal val PanelDark @Composable get() = RetroRackColors.panelDark
internal val PanelHeader @Composable get() = RetroRackColors.panelHeader
internal val PanelHeaderEnd @Composable get() = RetroRackColors.panelHeaderEnd
internal val DisplayBlack @Composable get() = RetroRackColors.displayBlack
internal val LcdGreen @Composable get() = RetroRackColors.lcdGreen
internal val LcdGreenDim @Composable get() = RetroRackColors.lcdGreenDim
internal val ControlSilver @Composable get() = RetroRackColors.controlSilver
internal val ButtonFace @Composable get() = RetroRackColors.buttonFace
internal val ButtonPressed @Composable get() = RetroRackColors.buttonPressed
internal val ActiveButton @Composable get() = RetroRackColors.activeButton
internal val SelectedRow @Composable get() = RetroRackColors.selectedRow
internal val RackHighlight @Composable get() = RetroRackColors.rackHighlight
internal val RackShadow @Composable get() = RetroRackColors.rackShadow
internal val InactiveTrack @Composable get() = RetroRackColors.inactiveTrack
