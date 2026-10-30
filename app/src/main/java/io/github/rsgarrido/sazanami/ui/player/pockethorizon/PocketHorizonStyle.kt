package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Theme-local visual values, independent of primary tokens and Advance appearance preferences. */
@Immutable
internal data class PocketHorizonStyle(
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
    }
}
