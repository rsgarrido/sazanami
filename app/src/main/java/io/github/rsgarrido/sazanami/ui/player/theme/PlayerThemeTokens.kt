package io.github.rsgarrido.sazanami.ui.player.theme

import androidx.compose.ui.graphics.Color

data class PlayerThemeTokens(
    val shellColor: Color,
    val accentColor: Color,
    val displayBackgroundColor: Color,
    val displayTextColor: Color,
    val secondaryAccentColor: Color? = null
)

/** Blends this color toward white by [amount], clamped to the 0..1 range. */
fun Color.lighten(amount: Float): Color {
    val fraction = amount.asColorFraction()
    return Color(
        red = red + (1f - red) * fraction,
        green = green + (1f - green) * fraction,
        blue = blue + (1f - blue) * fraction,
        alpha = alpha,
        colorSpace = colorSpace
    )
}

/** Blends this color toward black by [amount], clamped to the 0..1 range. */
fun Color.darken(amount: Float): Color {
    val fraction = amount.asColorFraction()
    return Color(
        red = red * (1f - fraction),
        green = green * (1f - fraction),
        blue = blue * (1f - fraction),
        alpha = alpha,
        colorSpace = colorSpace
    )
}

/** Returns this color with [alpha] clamped to the 0..1 range. */
fun Color.withAlpha(alpha: Float): Color = copy(alpha = alpha.asColorFraction())

private fun Float.asColorFraction(): Float =
    if (isNaN()) 0f else coerceIn(0f, 1f)
