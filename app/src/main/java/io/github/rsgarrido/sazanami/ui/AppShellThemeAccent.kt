package io.github.rsgarrido.sazanami.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.theme.SazanamiAccent
import io.github.rsgarrido.sazanami.ui.theme.SazanamiSurfaceHigh

private const val MinimumShellAccentContrast = 4.5f

internal val LocalAppShellAccent = staticCompositionLocalOf { SazanamiAccent }

val AppShellAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppShellAccent.current

@Composable
fun rememberAppShellAccent(
    playerTheme: PlayerTheme,
    tokens: PlayerThemeTokens?,
    fallbackAccent: Color = MaterialTheme.colorScheme.primary
): Color = remember(playerTheme, tokens, fallbackAccent) {
    resolveAppShellAccent(
        playerTheme = playerTheme,
        tokens = tokens,
        fallbackAccent = fallbackAccent
    )
}

internal fun resolveAppShellAccent(
    playerTheme: PlayerTheme,
    tokens: PlayerThemeTokens?,
    fallbackAccent: Color = SazanamiAccent
): Color {
    val tokenAccent = when (playerTheme) {
        PlayerTheme.DEFAULT -> null
        PlayerTheme.CLASSIC_WHEEL -> tokens?.accentColor
        PlayerTheme.POCKET_CASSETTE -> tokens?.secondaryAccentColor ?: tokens?.accentColor
        PlayerTheme.POCKET_FLIP -> tokens?.accentColor
        PlayerTheme.RETRO_RACK -> tokens?.accentColor
    }
    return ensureReadableShellAccent(tokenAccent ?: fallbackAccent)
}

private fun ensureReadableShellAccent(accent: Color): Color {
    var readableAccent = accent.copy(alpha = 1f)
    repeat(12) {
        if (contrastRatio(readableAccent, SazanamiSurfaceHigh) >= MinimumShellAccentContrast) {
            return readableAccent
        }
        readableAccent = lerp(readableAccent, Color.White, 0.12f)
    }
    return readableAccent
}

private fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
