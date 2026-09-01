package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import io.github.rsgarrido.sazanami.ui.player.theme.withAlpha

internal val PocketDiscDefaultTokens = PlayerThemeTokens(
    shellColor = Color(0xFF23374A),
    accentColor = Color(0xFF87D8F4),
    displayBackgroundColor = Color(0xFF111D28),
    displayTextColor = Color(0xFFC7ECF6),
    secondaryAccentColor = Color(0xFF4AA8D1)
)

internal class PocketDiscPalette private constructor(tokens: PlayerThemeTokens) {
    private val baseShell = tokens.shellColor
    private val lcdAccent = tokens.accentColor
    private val display = tokens.displayBackgroundColor
    private val text = tokens.displayTextColor
    private val activeAccent = tokens.secondaryAccentColor ?: lcdAccent

    val shell = baseShell
    val shellLight = baseShell.lighten(0.18f)
    val shellMid = baseShell.darken(0.12f)
    val shellDark = baseShell.darken(0.36f)
    val panel = baseShell.darken(0.22f)
    val panelDeep = baseShell.darken(0.43f)
    val edge = baseShell.lighten(0.34f).withAlpha(0.72f)
    val seam = baseShell.darken(0.62f)

    val lcdBackground = display
    val lcdBackgroundLight = display.lighten(0.09f)
    val lcdText = text
    val lcdTextMuted = text.darken(0.26f)
    val lcdGlow = lcdAccent
    val lcdGlowDim = lcdAccent.darken(0.32f)
    val active = activeAccent
    val activeDim = activeAccent.darken(0.28f)

    val button = baseShell.darken(0.28f)
    val buttonPressed = baseShell.darken(0.43f)
    val buttonEdge = baseShell.lighten(0.24f)
    val buttonIcon = text.lighten(0.18f)

    companion object {
        fun from(tokens: PlayerThemeTokens): PocketDiscPalette = PocketDiscPalette(tokens)
    }
}

internal val PocketDiscDefaultPalette = PocketDiscPalette.from(PocketDiscDefaultTokens)
internal val LocalPocketDiscPalette = staticCompositionLocalOf { PocketDiscDefaultPalette }
internal val PocketDiscColors: PocketDiscPalette
    @Composable get() = LocalPocketDiscPalette.current
