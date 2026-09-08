package io.github.rsgarrido.sazanami.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesState
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import io.github.rsgarrido.sazanami.ui.player.modern.sanitizeModernSolidColorArgb
import io.github.rsgarrido.sazanami.ui.theme.SazanamiAccent
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOnSurface
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOnSurfaceVariant
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOutline
import io.github.rsgarrido.sazanami.ui.theme.SazanamiSurface
import io.github.rsgarrido.sazanami.ui.theme.SazanamiSurfaceHigh

enum class WidgetAppearanceMode(val storageValue: String) {
    FOLLOW_PLAYER_THEME("follow_player_theme"),
    SAZANAMI_DEFAULT("sazanami_default"),
    SYSTEM_DYNAMIC("system_dynamic");

    companion object {
        fun fromStorageValue(value: String?): WidgetAppearanceMode =
            entries.firstOrNull { mode -> mode.storageValue == value } ?: FOLLOW_PLAYER_THEME
    }
}

/** Renderer families are intentionally separate from persisted choices for future retro widgets. */
internal enum class WidgetAppearanceRenderer {
    SAZANAMI_DEFAULT,
    SYSTEM_DYNAMIC
}

internal sealed interface WidgetColorToken {
    data class Fixed(val day: Color, val night: Color = day) : WidgetColorToken
    data object SystemBackground : WidgetColorToken
    data object SystemSurface : WidgetColorToken
    data object SystemPrimaryText : WidgetColorToken
    data object SystemSecondaryText : WidgetColorToken
    data object SystemAccent : WidgetColorToken
    data object SystemDisabled : WidgetColorToken
}

internal data class NowPlayingWidgetAppearance(
    val renderer: WidgetAppearanceRenderer,
    val background: WidgetColorToken,
    val artworkSurface: WidgetColorToken,
    val primaryText: WidgetColorToken,
    val secondaryText: WidgetColorToken,
    val accent: WidgetColorToken,
    val disabled: WidgetColorToken,
    val widgetCornerRadiusDp: Int,
    val artworkCornerRadiusDp: Int
)

internal fun widgetAppearanceRendererFor(
    mode: WidgetAppearanceMode,
    selectedPlayerTheme: PlayerTheme
): WidgetAppearanceRenderer = when (mode) {
    WidgetAppearanceMode.SAZANAMI_DEFAULT -> WidgetAppearanceRenderer.SAZANAMI_DEFAULT
    WidgetAppearanceMode.SYSTEM_DYNAMIC -> WidgetAppearanceRenderer.SYSTEM_DYNAMIC
    WidgetAppearanceMode.FOLLOW_PLAYER_THEME -> when (selectedPlayerTheme) {
        PlayerTheme.DEFAULT -> WidgetAppearanceRenderer.SAZANAMI_DEFAULT
        // Session 4 can add renderer families here without changing persisted widget modes.
        PlayerTheme.CLASSIC_WHEEL,
        PlayerTheme.RETRO_RACK,
        PlayerTheme.POCKET_FLIP,
        PlayerTheme.POCKET_CASSETTE,
        PlayerTheme.POCKET_DISC -> WidgetAppearanceRenderer.SAZANAMI_DEFAULT
    }
}

internal fun resolveWidgetAppearance(
    mode: WidgetAppearanceMode,
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance = when (
    widgetAppearanceRendererFor(mode, preferences.selectedPlayerTheme)
) {
    WidgetAppearanceRenderer.SAZANAMI_DEFAULT -> sazanamiDefaultWidgetAppearance(preferences)
    WidgetAppearanceRenderer.SYSTEM_DYNAMIC -> systemDynamicWidgetAppearance()
}

private fun sazanamiDefaultWidgetAppearance(
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance {
    val modern = preferences.modernPlayerAppearance
    val background = when (modern.background.style) {
        ModernBackgroundStyle.SOLID_COLOR ->
            Color(sanitizeModernSolidColorArgb(modern.background.solidColorArgb).toInt())
        ModernBackgroundStyle.PURE_BLACK -> Color.Black
        ModernBackgroundStyle.BLURRED_ARTWORK,
        ModernBackgroundStyle.DETAILED_ARTWORK,
        ModernBackgroundStyle.ALBUM_GRADIENT -> SazanamiSurface
    }
    val accent = when (modern.controls.accent) {
        ModernControlAccent.WHITE -> SazanamiOnSurface
        ModernControlAccent.APP_ACCENT,
        // Album palette extraction is deliberately not duplicated in the widget.
        ModernControlAccent.ALBUM_DERIVED -> SazanamiAccent
    }
    val artworkCornerRadiusDp = when (modern.artwork.shape) {
        ModernArtworkShape.SQUARE -> 0
        ModernArtworkShape.SUBTLE_ROUNDED -> 6
        ModernArtworkShape.ROUNDED -> 10
        ModernArtworkShape.EXTRA_ROUNDED -> 16
    }
    return NowPlayingWidgetAppearance(
        renderer = WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
        background = WidgetColorToken.Fixed(background),
        artworkSurface = WidgetColorToken.Fixed(SazanamiSurfaceHigh),
        primaryText = WidgetColorToken.Fixed(SazanamiOnSurface),
        secondaryText = WidgetColorToken.Fixed(SazanamiOnSurfaceVariant),
        accent = WidgetColorToken.Fixed(accent),
        disabled = WidgetColorToken.Fixed(SazanamiOutline),
        widgetCornerRadiusDp = 16,
        artworkCornerRadiusDp = artworkCornerRadiusDp
    )
}

/** Glance supplies dynamic system roles where available and Material baseline colors otherwise. */
private fun systemDynamicWidgetAppearance() = NowPlayingWidgetAppearance(
    renderer = WidgetAppearanceRenderer.SYSTEM_DYNAMIC,
    background = WidgetColorToken.SystemBackground,
    artworkSurface = WidgetColorToken.SystemSurface,
    primaryText = WidgetColorToken.SystemPrimaryText,
    secondaryText = WidgetColorToken.SystemSecondaryText,
    accent = WidgetColorToken.SystemAccent,
    disabled = WidgetColorToken.SystemDisabled,
    widgetCornerRadiusDp = 16,
    artworkCornerRadiusDp = 10
)

@Composable
internal fun WidgetColorToken.asGlanceColorProvider(): GlanceColorProvider = when (this) {
    is WidgetColorToken.Fixed -> ColorProvider(day = day, night = night)
    WidgetColorToken.SystemBackground -> GlanceTheme.colors.widgetBackground
    WidgetColorToken.SystemSurface -> GlanceTheme.colors.surfaceVariant
    WidgetColorToken.SystemPrimaryText -> GlanceTheme.colors.onSurface
    WidgetColorToken.SystemSecondaryText -> GlanceTheme.colors.onSurfaceVariant
    WidgetColorToken.SystemAccent -> GlanceTheme.colors.primary
    WidgetColorToken.SystemDisabled -> GlanceTheme.colors.outline
}
