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
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.applyOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import io.github.rsgarrido.sazanami.ui.theme.SazanamiAccent
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOnSurface
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOnSurfaceVariant
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOutline
import io.github.rsgarrido.sazanami.ui.theme.SazanamiSurface
import io.github.rsgarrido.sazanami.ui.theme.SazanamiSurfaceHigh

enum class WidgetAppearanceMode(val storageValue: String) {
    FOLLOW_PLAYER_THEME("follow_player_theme"),
    SAZANAMI_DEFAULT("sazanami_default"),
    SYSTEM_DYNAMIC("system_dynamic"),
    RETRO_RACK("retro_rack"),
    POCKET_CASSETTE("pocket_cassette");

    companion object {
        fun fromStorageValue(value: String?): WidgetAppearanceMode =
            entries.firstOrNull { mode -> mode.storageValue == value } ?: FOLLOW_PLAYER_THEME
    }
}

/** Renderer families are intentionally separate from persisted choices for future retro widgets. */
internal enum class WidgetAppearanceRenderer {
    SAZANAMI_DEFAULT,
    SYSTEM_DYNAMIC,
    RETRO_RACK,
    POCKET_CASSETTE
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
    val metadataSurface: WidgetColorToken,
    val metadataPrimaryText: WidgetColorToken,
    val metadataSecondaryText: WidgetColorToken,
    val panelSurface: WidgetColorToken,
    val panelOutline: WidgetColorToken,
    val artworkPlaceholderTint: WidgetColorToken,
    val accent: WidgetColorToken,
    val disabled: WidgetColorToken,
    val controlSurface: WidgetColorToken?,
    val controlForeground: WidgetColorToken,
    val widgetCornerRadiusDp: Int,
    val artworkCornerRadiusDp: Int,
    val panelCornerRadiusDp: Int,
    val controlCornerRadiusDp: Int
)

internal enum class WidgetRendererLayout {
    NEUTRAL_COMPACT,
    NEUTRAL_STANDARD,
    RETRO_RACK_COMPACT,
    RETRO_RACK_STANDARD,
    POCKET_CASSETTE_COMPACT,
    POCKET_CASSETTE_STANDARD
}

internal fun widgetRendererLayoutFor(
    renderer: WidgetAppearanceRenderer,
    layout: NowPlayingWidgetLayout
): WidgetRendererLayout = when (renderer) {
    WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
    WidgetAppearanceRenderer.SYSTEM_DYNAMIC -> when (layout) {
        NowPlayingWidgetLayout.COMPACT -> WidgetRendererLayout.NEUTRAL_COMPACT
        NowPlayingWidgetLayout.STANDARD -> WidgetRendererLayout.NEUTRAL_STANDARD
    }
    WidgetAppearanceRenderer.RETRO_RACK -> when (layout) {
        NowPlayingWidgetLayout.COMPACT -> WidgetRendererLayout.RETRO_RACK_COMPACT
        NowPlayingWidgetLayout.STANDARD -> WidgetRendererLayout.RETRO_RACK_STANDARD
    }
    WidgetAppearanceRenderer.POCKET_CASSETTE -> when (layout) {
        NowPlayingWidgetLayout.COMPACT -> WidgetRendererLayout.POCKET_CASSETTE_COMPACT
        NowPlayingWidgetLayout.STANDARD -> WidgetRendererLayout.POCKET_CASSETTE_STANDARD
    }
}

internal fun widgetAppearanceRendererFor(
    mode: WidgetAppearanceMode,
    selectedPlayerTheme: PlayerTheme
): WidgetAppearanceRenderer = when (mode) {
    WidgetAppearanceMode.SAZANAMI_DEFAULT -> WidgetAppearanceRenderer.SAZANAMI_DEFAULT
    WidgetAppearanceMode.SYSTEM_DYNAMIC -> WidgetAppearanceRenderer.SYSTEM_DYNAMIC
    WidgetAppearanceMode.RETRO_RACK -> WidgetAppearanceRenderer.RETRO_RACK
    WidgetAppearanceMode.POCKET_CASSETTE -> WidgetAppearanceRenderer.POCKET_CASSETTE
    WidgetAppearanceMode.FOLLOW_PLAYER_THEME -> when (selectedPlayerTheme) {
        PlayerTheme.DEFAULT -> WidgetAppearanceRenderer.SAZANAMI_DEFAULT
        PlayerTheme.RETRO_RACK -> WidgetAppearanceRenderer.RETRO_RACK
        PlayerTheme.POCKET_CASSETTE -> WidgetAppearanceRenderer.POCKET_CASSETTE
        PlayerTheme.CLASSIC_WHEEL,
        PlayerTheme.POCKET_FLIP,
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
    WidgetAppearanceRenderer.RETRO_RACK -> retroRackWidgetAppearance(preferences)
    WidgetAppearanceRenderer.POCKET_CASSETTE -> pocketCassetteWidgetAppearance(preferences)
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
        metadataSurface = WidgetColorToken.Fixed(SazanamiSurface),
        metadataPrimaryText = WidgetColorToken.Fixed(SazanamiOnSurface),
        metadataSecondaryText = WidgetColorToken.Fixed(SazanamiOnSurfaceVariant),
        panelSurface = WidgetColorToken.Fixed(SazanamiSurface),
        panelOutline = WidgetColorToken.Fixed(SazanamiOutline),
        artworkPlaceholderTint = WidgetColorToken.Fixed(SazanamiOnSurfaceVariant),
        accent = WidgetColorToken.Fixed(accent),
        disabled = WidgetColorToken.Fixed(SazanamiOutline),
        controlSurface = null,
        controlForeground = WidgetColorToken.Fixed(SazanamiOnSurface),
        widgetCornerRadiusDp = 16,
        artworkCornerRadiusDp = artworkCornerRadiusDp,
        panelCornerRadiusDp = 10,
        controlCornerRadiusDp = 8
    )
}

/** Glance supplies dynamic system roles where available and Material baseline colors otherwise. */
private fun systemDynamicWidgetAppearance() = NowPlayingWidgetAppearance(
    renderer = WidgetAppearanceRenderer.SYSTEM_DYNAMIC,
    background = WidgetColorToken.SystemBackground,
    artworkSurface = WidgetColorToken.SystemSurface,
    primaryText = WidgetColorToken.SystemPrimaryText,
    secondaryText = WidgetColorToken.SystemSecondaryText,
    metadataSurface = WidgetColorToken.SystemBackground,
    metadataPrimaryText = WidgetColorToken.SystemPrimaryText,
    metadataSecondaryText = WidgetColorToken.SystemSecondaryText,
    panelSurface = WidgetColorToken.SystemSurface,
    panelOutline = WidgetColorToken.SystemDisabled,
    artworkPlaceholderTint = WidgetColorToken.SystemSecondaryText,
    accent = WidgetColorToken.SystemAccent,
    disabled = WidgetColorToken.SystemDisabled,
    controlSurface = null,
    controlForeground = WidgetColorToken.SystemPrimaryText,
    widgetCornerRadiusDp = 16,
    artworkCornerRadiusDp = 10,
    panelCornerRadiusDp = 10,
    controlCornerRadiusDp = 8
)

internal fun resolvedWidgetThemeTokens(
    theme: PlayerTheme,
    preferences: AppPreferencesState
): PlayerThemeTokens = theme.defaultTokens().applyOverrides(
    preferences.playerThemeTokenOverrides[theme] ?: PlayerThemeTokenOverrides()
)

private fun retroRackWidgetAppearance(
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance {
    val tokens = resolvedWidgetThemeTokens(PlayerTheme.RETRO_RACK, preferences)
    val shell = tokens.shellColor
    val accent = tokens.accentColor
    val displayText = tokens.displayTextColor
    val activeAccent = tokens.secondaryAccentColor ?: accent.darken(0.30f)
    return NowPlayingWidgetAppearance(
        renderer = WidgetAppearanceRenderer.RETRO_RACK,
        background = WidgetColorToken.Fixed(shell.darken(0.628f)),
        artworkSurface = WidgetColorToken.Fixed(tokens.displayBackgroundColor),
        primaryText = WidgetColorToken.Fixed(displayText),
        secondaryText = WidgetColorToken.Fixed(accent.darken(0.289f)),
        metadataSurface = WidgetColorToken.Fixed(tokens.displayBackgroundColor),
        metadataPrimaryText = WidgetColorToken.Fixed(accent),
        metadataSecondaryText = WidgetColorToken.Fixed(accent.darken(0.289f)),
        panelSurface = WidgetColorToken.Fixed(shell),
        panelOutline = WidgetColorToken.Fixed(shell.lighten(0.535f)),
        artworkPlaceholderTint = WidgetColorToken.Fixed(accent.darken(0.289f)),
        accent = WidgetColorToken.Fixed(activeAccent),
        disabled = WidgetColorToken.Fixed(displayText.darken(0.55f)),
        controlSurface = WidgetColorToken.Fixed(shell.lighten(0.143f)),
        controlForeground = WidgetColorToken.Fixed(displayText),
        widgetCornerRadiusDp = 8,
        artworkCornerRadiusDp = 3,
        panelCornerRadiusDp = 4,
        controlCornerRadiusDp = 3
    )
}

private fun pocketCassetteWidgetAppearance(
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance {
    val tokens = resolvedWidgetThemeTokens(PlayerTheme.POCKET_CASSETTE, preferences)
    val shell = tokens.shellColor
    val accent = tokens.accentColor
    val displayText = tokens.displayTextColor
    val shellInk = shell.darken(0.756f)
    val warmAccent = tokens.secondaryAccentColor ?: accent
    return NowPlayingWidgetAppearance(
        renderer = WidgetAppearanceRenderer.POCKET_CASSETTE,
        background = WidgetColorToken.Fixed(shell),
        artworkSurface = WidgetColorToken.Fixed(tokens.displayBackgroundColor),
        primaryText = WidgetColorToken.Fixed(shellInk),
        secondaryText = WidgetColorToken.Fixed(shellInk.lighten(0.18f)),
        metadataSurface = WidgetColorToken.Fixed(tokens.displayBackgroundColor),
        metadataPrimaryText = WidgetColorToken.Fixed(displayText),
        metadataSecondaryText = WidgetColorToken.Fixed(displayText.darken(0.243f)),
        panelSurface = WidgetColorToken.Fixed(accent),
        panelOutline = WidgetColorToken.Fixed(accent.darken(0.304f)),
        artworkPlaceholderTint = WidgetColorToken.Fixed(displayText),
        accent = WidgetColorToken.Fixed(warmAccent),
        disabled = WidgetColorToken.Fixed(displayText.darken(0.55f)),
        controlSurface = WidgetColorToken.Fixed(tokens.displayBackgroundColor.lighten(0.128f)),
        controlForeground = WidgetColorToken.Fixed(displayText),
        widgetCornerRadiusDp = 12,
        artworkCornerRadiusDp = 3,
        panelCornerRadiusDp = 6,
        controlCornerRadiusDp = 5
    )
}

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
