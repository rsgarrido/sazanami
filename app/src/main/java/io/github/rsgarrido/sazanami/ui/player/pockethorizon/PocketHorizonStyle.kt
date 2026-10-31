package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import io.github.rsgarrido.sazanami.data.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Theme-local visual values, independent of primary tokens and Advance appearance preferences. */
@Immutable
internal data class PocketHorizonStyle(
    val hardware: PocketHorizonHardwareStyle = PocketHorizonHardwareStyle(),
    val waveIntensity: Float = 1f,
    val shell: Color = Color(0xFF171D27),
    val shellHighlight: Color = Color(0xFF35404F),
    val bezel: Color = Color(0xFF080E18),
    val displayTop: Color = Color(0xFF142A43),
    val displayBottom: Color = Color(0xFF201C38),
    val horizon: Color = Color(0xFF425B78),
    val surface: Color = Color(0xFF25344D),
    val outline: Color = Color(0xFF61708A),
    val text: Color = Color(0xFFF4F5F4),
    val secondaryText: Color = Color(0xFFCAD3E2),
    val accent: Color = Color(0xFFA6E7DA),
    val onAccent: Color = Color(0xFF102E30),
    val disabled: Color = Color(0xFF778295)
) {
    companion object {
        val Default = PocketHorizonStyle()
        fun from(appearance: PocketHorizonAppearance): PocketHorizonStyle {
            val hardware = when (appearance.consoleFinish) {
                PocketHorizonConsoleFinish.GRAPHITE -> PocketHorizonHardwareStyle()
                PocketHorizonConsoleFinish.SILVER -> PocketHorizonHardwareStyle(
                    Color(0xFFB6BEC8), Color(0xFFE4E9EE), Color(0xFF8793A2), Color(0xFF162333),
                    Color(0xFF9AA6B5), Color(0xFF162333), Color(0xFFB7E2CF), Color(0xFF162333), Color(0xFFC7D2DD))
                PocketHorizonConsoleFinish.MIDNIGHT_BLUE -> PocketHorizonHardwareStyle(
                    Color(0xFF152C45), Color(0xFF354F6B), Color(0xFF081424), Color(0xFFF0F4FA),
                    Color(0xFF768CA8), Color(0xFFD0DCEC), Color(0xFFB1D8EF), Color(0xFFF0F4FA), Color(0xFF354F6B))
                PocketHorizonConsoleFinish.DEEP_BURGUNDY -> PocketHorizonHardwareStyle(
                    Color(0xFF3B1E30), Color(0xFF654154), Color(0xFF1B0D18), Color(0xFFF9EFF4),
                    Color(0xFFA18396), Color(0xFFEAD4E1), Color(0xFFEABACF), Color(0xFFF9EFF4), Color(0xFF654154))
            }
            val lcd = when (appearance.lcdPalette) {
                PocketHorizonLcdPalette.BLUE_VIOLET -> Default
                PocketHorizonLcdPalette.EMERALD_TEAL -> Default.copy(
                    displayTop = Color(0xFF102F30), displayBottom = Color(0xFF142927), horizon = Color(0xFF35645C),
                    surface = Color(0xFF203E39), outline = Color(0xFF6E9288), accent = Color(0xFFA8E6C9),
                    onAccent = Color(0xFF102E24), secondaryText = Color(0xFFC9DFD6), disabled = Color(0xFF81988E))
                PocketHorizonLcdPalette.WARM_AMBER -> Default.copy(
                    displayTop = Color(0xFF322619), displayBottom = Color(0xFF29202B), horizon = Color(0xFF6A5035),
                    surface = Color(0xFF403326), outline = Color(0xFF9A866A), accent = Color(0xFFF0CE91),
                    onAccent = Color(0xFF302211), secondaryText = Color(0xFFE5D5BD), disabled = Color(0xFFA29480))
            }
            return lcd.copy(hardware = hardware, shell = hardware.shell, shellHighlight = hardware.highlight,
                waveIntensity = when (appearance.waveTreatment) {
                    PocketHorizonWaveTreatment.STANDARD -> 1f
                    PocketHorizonWaveTreatment.SUBTLE -> .35f
                    PocketHorizonWaveTreatment.OFF -> 0f
                })
        }
    }
}

/** Hardware roles stay independent of LCD text and accents. Defaults map every original draw site. */
@Immutable
internal data class PocketHorizonHardwareStyle(
    val shell: Color = Color(0xFF171D27),
    val highlight: Color = Color(0xFF35404F),
    val shade: Color = Color(0xFF080E18),
    val ink: Color = Color(0xFFF4F5F4),
    val outline: Color = Color(0xFF61708A),
    val mutedInk: Color = Color(0xFFCAD3E2),
    val pressed: Color = Color(0xFFA6E7DA),
    val bumperInk: Color = Color(0xFFF4F5F4),
    val controlHighlight: Color = Color(0xFF35404F)
)
internal val LocalPocketHorizonStyle = staticCompositionLocalOf { PocketHorizonStyle.Default }
