package io.github.rsgarrido.sazanami.ui

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity

internal enum class SystemBarSurface {
    SHELL,
    MODERN_PLAYER,
    LYRICS
}

internal val LocalSystemBarSurfaceSetter =
    staticCompositionLocalOf<(SystemBarSurface) -> Unit> { {} }

internal fun shouldUseDarkSystemBarIcons(
    shellIsDark: Boolean,
    surface: SystemBarSurface
): Boolean = !shellIsDark && surface == SystemBarSurface.SHELL

internal fun playerSystemBarSurface(
    presentation: PlayerPresentationIdentity,
    isExpanded: Boolean,
    hasSong: Boolean,
    lyricsVisible: Boolean
): SystemBarSurface = when (presentation) {
    is PlayerPresentationIdentity.Existing -> playerSystemBarSurface(
        presentation.theme, isExpanded, hasSong, lyricsVisible
    )
    PlayerPresentationIdentity.PocketAdvance, PlayerPresentationIdentity.PocketHorizon -> when {
        !isExpanded || !hasSong -> SystemBarSurface.SHELL
        lyricsVisible -> SystemBarSurface.LYRICS
        else -> SystemBarSurface.MODERN_PLAYER
    }
}

internal fun playerSystemBarSurface(
    theme: PlayerTheme,
    isExpanded: Boolean,
    hasSong: Boolean,
    lyricsVisible: Boolean
): SystemBarSurface = when {
    !isExpanded || !hasSong -> SystemBarSurface.SHELL
    lyricsVisible -> SystemBarSurface.LYRICS
    theme == PlayerTheme.DEFAULT -> SystemBarSurface.MODERN_PLAYER
    else -> SystemBarSurface.SHELL
}

@Composable
internal fun SystemBarIconAppearanceEffect(window: Window, darkIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
