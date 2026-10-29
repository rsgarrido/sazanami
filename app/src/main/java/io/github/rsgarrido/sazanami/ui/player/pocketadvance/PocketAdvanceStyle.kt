package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette

/** One immutable color snapshot shared by mini, morph, hardware and every LCD page. */
@Immutable
internal data class PocketAdvancePalette private constructor(
    val Shell: Color,
    val ShellHighlight: Color,
    val ShellShade: Color,
    val Label: Color,
    val Screen: Color,
    val ScreenShade: Color,
    val Ink: Color,
    val MutedInk: Color,
    val ScreenAccent: Color,
    val Button: Color,
    val ButtonHighlight: Color,
    val ButtonInk: Color,
    val lcdPalette: PocketAdvanceLcdPalette,
    val ActiveButtonInk: Color
) {
    // The bezel and semantic power lamp keep their approved hardware meaning for every finish.
    val Bezel: Color get() = Color(0xFF282639)
    val Led: Color get() = Color(0xFFA8E68D)

    companion object {
        fun from(appearance: PocketAdvanceAppearance): PocketAdvancePalette {
            val console = when (appearance.consoleFinish) {
                PocketAdvanceConsoleFinish.ORIGINAL_PURPLE -> ConsoleColors(0xFF6653A5, 0xFF9C87D2, 0xFF392C65, 0xFFE1D8F3)
                PocketAdvanceConsoleFinish.GRAPHITE -> ConsoleColors(0xFF41464F, 0xFF7D8795, 0xFF202631, 0xFFF0F2F4)
                PocketAdvanceConsoleFinish.OCEAN_BLUE -> ConsoleColors(0xFF315C86, 0xFF6F9FC4, 0xFF183650, 0xFFDDECF7)
                PocketAdvanceConsoleFinish.FOREST_GREEN -> ConsoleColors(0xFF426950, 0xFF85AB8D, 0xFF203A2B, 0xFFE2F0DD)
                PocketAdvanceConsoleFinish.BERRY -> ConsoleColors(0xFF864762, 0xFFBE839F, 0xFF51273C, 0xFFF8E1ED)
            }
            val lcd = when (appearance.lcdPalette) {
                PocketAdvanceLcdPalette.ORIGINAL -> LcdColors(0xFFDFE5CC, 0xFFC8D2B1, 0xFF263C38, 0xFF52655A, 0xFF547B64)
                PocketAdvanceLcdPalette.CLASSIC_GREEN -> LcdColors(0xFFC7D5A0, 0xFFA9BC7B, 0xFF23351E, 0xFF38472B, 0xFF48612F)
                PocketAdvanceLcdPalette.WARM_AMBER -> LcdColors(0xFFF2D9A0, 0xFFD9BD7D, 0xFF49321C, 0xFF614725, 0xFF795220)
            }
            val controls = when (appearance.controlsFinish) {
                PocketAdvanceControlsFinish.ORIGINAL_DARK -> ControlColors(0xFF383443, 0xFF686173, 0xFFF4EDF9)
                PocketAdvanceControlsFinish.LIGHT_GRAY -> ControlColors(0xFFBEC7D1, 0xFFE5EAF0, 0xFF1D2733)
                PocketAdvanceControlsFinish.DEEP_NAVY -> ControlColors(0xFF20374F, 0xFF516C88, 0xFFE7F0FA)
            }
            return PocketAdvancePalette(console.shell, console.highlight, console.shade, console.label,
                lcd.background, lcd.shade, lcd.ink, lcd.mutedInk, lcd.accent,
                controls.surface, controls.highlight, controls.ink, appearance.lcdPalette,
                if (appearance.controlsFinish == PocketAdvanceControlsFinish.LIGHT_GRAY) Color(0xFF245B31) else Color(0xFFA8E68D))
        }
    }
}

// Explicit curated stops preserve the original colors exactly; existing lerps/alpha/pressed finishes
// continue deriving their intermediate colors at the draw sites without changing any geometry.
private class ConsoleColors(shell: Long, highlight: Long, shade: Long, label: Long) {
    val shell = Color(shell); val highlight = Color(highlight); val shade = Color(shade); val label = Color(label)
}
private class LcdColors(background: Long, shade: Long, ink: Long, mutedInk: Long, accent: Long) {
    val background = Color(background); val shade = Color(shade); val ink = Color(ink)
    val mutedInk = Color(mutedInk); val accent = Color(accent)
}
private class ControlColors(surface: Long, highlight: Long, ink: Long) {
    val surface = Color(surface); val highlight = Color(highlight); val ink = Color(ink)
}

internal val PocketAdvanceDefaultPalette = PocketAdvancePalette.from(PocketAdvanceAppearance.Default)
internal val LocalPocketAdvancePalette = staticCompositionLocalOf { PocketAdvanceDefaultPalette }
