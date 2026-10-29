package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsColors

/** Original LCD keeps the exact inherited shared-overlay colors, including the primary app accent. */
internal fun pocketAdvanceOverlayColorScheme(
    base: ColorScheme,
    palette: PocketAdvancePalette,
    active: Boolean,
    inverse: Boolean = false
): ColorScheme {
    if (!active || palette.lcdPalette == PocketAdvanceLcdPalette.ORIGINAL) return base
    val background = if (inverse) palette.Ink else palette.Screen
    val foreground = if (inverse) palette.Screen else palette.Ink
    val panel = if (inverse) palette.MutedInk else palette.ScreenShade
    return base.copy(
        primary = foreground, onPrimary = background,
        primaryContainer = panel, onPrimaryContainer = foreground,
        secondary = foreground, onSecondary = background,
        secondaryContainer = panel, onSecondaryContainer = foreground,
        tertiary = foreground, onTertiary = background,
        tertiaryContainer = panel, onTertiaryContainer = foreground,
        background = background, onBackground = foreground,
        surface = background, onSurface = foreground,
        surfaceVariant = panel, onSurfaceVariant = if (inverse) palette.ScreenShade else palette.MutedInk,
        surfaceContainerLowest = background, surfaceContainerLow = background,
        surfaceContainer = background, surfaceContainerHigh = panel, surfaceContainerHighest = panel,
        inverseSurface = foreground, inverseOnSurface = background, inversePrimary = background,
        outline = if (inverse) palette.ScreenShade else palette.MutedInk,
        outlineVariant = panel, surfaceTint = foreground
    )
}

internal fun pocketAdvanceLyricsColors(palette: PocketAdvancePalette, active: Boolean): LyricsColors? =
    if (!active || palette.lcdPalette == PocketAdvanceLcdPalette.ORIGINAL) null else LyricsColors(
        background = palette.Ink, foreground = palette.Screen,
        returnContainer = palette.ScreenShade, returnContent = palette.Ink,
        // Colored LCD ink is lighter than black; retain contrast even over very bright artwork.
        backdropTopAlpha = 0.97f, backdropMiddleAlpha = 0.96f, backdropBottomAlpha = 0.98f,
        headerScrimAlpha = 0.80f, pastLineAlpha = 0.90f, upcomingLineAlpha = 0.82f,
        secondaryTextAlpha = 0.82f
    )

/** Scoped to player-owned overlays. It never modifies the app-shell accent or the outer app theme. */
@Composable
internal fun PocketAdvanceOverlayTheme(
    active: Boolean,
    inverse: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = LocalPocketAdvancePalette.current
    val inherited = MaterialTheme.colorScheme
    val scheme = remember(inherited, palette, active, inverse) {
        pocketAdvanceOverlayColorScheme(inherited, palette, active, inverse)
    }
    // One stable content position across color changes preserves list/dialog state.
    MaterialTheme(colorScheme = scheme, content = content)
}
