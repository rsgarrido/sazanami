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
    POCKET_CASSETTE("pocket_cassette"),
    CLASSIC_WHEEL("classic_wheel"),
    POCKET_FLIP("pocket_flip"),
    POCKET_DISC("pocket_disc");

    companion object {
        fun fromStorageValue(value: String?): WidgetAppearanceMode =
            entries.firstOrNull { mode -> mode.storageValue == value } ?: FOLLOW_PLAYER_THEME
    }
}

/** Renderer families stay separate from persisted choices so Follow mode can resolve explicitly. */
internal enum class WidgetAppearanceRenderer {
    SAZANAMI_DEFAULT,
    SYSTEM_DYNAMIC,
    RETRO_RACK,
    POCKET_CASSETTE,
    CLASSIC_WHEEL,
    POCKET_FLIP,
    POCKET_DISC
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
    POCKET_CASSETTE_STANDARD,
    CLASSIC_WHEEL_COMPACT,
    CLASSIC_WHEEL_STANDARD,
    POCKET_FLIP_COMPACT,
    POCKET_FLIP_STANDARD,
    POCKET_DISC_COMPACT,
    POCKET_DISC_STANDARD
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
    WidgetAppearanceRenderer.CLASSIC_WHEEL -> when (layout) {
        NowPlayingWidgetLayout.COMPACT -> WidgetRendererLayout.CLASSIC_WHEEL_COMPACT
        NowPlayingWidgetLayout.STANDARD -> WidgetRendererLayout.CLASSIC_WHEEL_STANDARD
    }
    WidgetAppearanceRenderer.POCKET_FLIP -> when (layout) {
        NowPlayingWidgetLayout.COMPACT -> WidgetRendererLayout.POCKET_FLIP_COMPACT
        NowPlayingWidgetLayout.STANDARD -> WidgetRendererLayout.POCKET_FLIP_STANDARD
    }
    WidgetAppearanceRenderer.POCKET_DISC -> when (layout) {
        NowPlayingWidgetLayout.COMPACT -> WidgetRendererLayout.POCKET_DISC_COMPACT
        NowPlayingWidgetLayout.STANDARD -> WidgetRendererLayout.POCKET_DISC_STANDARD
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
    WidgetAppearanceMode.CLASSIC_WHEEL -> WidgetAppearanceRenderer.CLASSIC_WHEEL
    WidgetAppearanceMode.POCKET_FLIP -> WidgetAppearanceRenderer.POCKET_FLIP
    WidgetAppearanceMode.POCKET_DISC -> WidgetAppearanceRenderer.POCKET_DISC
    WidgetAppearanceMode.FOLLOW_PLAYER_THEME -> when (selectedPlayerTheme) {
        PlayerTheme.DEFAULT -> WidgetAppearanceRenderer.SAZANAMI_DEFAULT
        PlayerTheme.CLASSIC_WHEEL -> WidgetAppearanceRenderer.CLASSIC_WHEEL
        PlayerTheme.RETRO_RACK -> WidgetAppearanceRenderer.RETRO_RACK
        PlayerTheme.POCKET_FLIP -> WidgetAppearanceRenderer.POCKET_FLIP
        PlayerTheme.POCKET_CASSETTE -> WidgetAppearanceRenderer.POCKET_CASSETTE
        PlayerTheme.POCKET_DISC -> WidgetAppearanceRenderer.POCKET_DISC
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
    WidgetAppearanceRenderer.CLASSIC_WHEEL -> classicWheelWidgetAppearance(preferences)
    WidgetAppearanceRenderer.POCKET_FLIP -> pocketFlipWidgetAppearance(preferences)
    WidgetAppearanceRenderer.POCKET_DISC -> pocketDiscWidgetAppearance(preferences)
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

private fun classicWheelWidgetAppearance(
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance {
    val tokens = resolvedWidgetThemeTokens(PlayerTheme.CLASSIC_WHEEL, preferences)
    val shell = tokens.shellColor
    val wheel = tokens.accentColor
    val display = tokens.displayBackgroundColor
    val displayText = tokens.displayTextColor
    val centerButton = tokens.secondaryAccentColor ?: shell
    return NowPlayingWidgetAppearance(
        renderer = WidgetAppearanceRenderer.CLASSIC_WHEEL,
        background = WidgetColorToken.Fixed(shell),
        artworkSurface = WidgetColorToken.Fixed(display),
        primaryText = WidgetColorToken.Fixed(displayText),
        secondaryText = WidgetColorToken.Fixed(displayText.lighten(0.267f)),
        metadataSurface = WidgetColorToken.Fixed(display),
        metadataPrimaryText = WidgetColorToken.Fixed(displayText),
        metadataSecondaryText = WidgetColorToken.Fixed(displayText.lighten(0.267f)),
        panelSurface = WidgetColorToken.Fixed(centerButton),
        panelOutline = WidgetColorToken.Fixed(displayText),
        artworkPlaceholderTint = WidgetColorToken.Fixed(displayText.lighten(0.267f)),
        accent = WidgetColorToken.Fixed(displayText),
        disabled = WidgetColorToken.Fixed(displayText.lighten(0.55f)),
        controlSurface = WidgetColorToken.Fixed(wheel),
        controlForeground = WidgetColorToken.Fixed(displayText),
        widgetCornerRadiusDp = 14,
        artworkCornerRadiusDp = 3,
        panelCornerRadiusDp = 5,
        controlCornerRadiusDp = 20
    )
}

private fun pocketFlipWidgetAppearance(
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance {
    val tokens = resolvedWidgetThemeTokens(PlayerTheme.POCKET_FLIP, preferences)
    val shell = tokens.shellColor
    val buttons = tokens.accentColor
    val display = tokens.displayBackgroundColor
    val displayText = tokens.displayTextColor
    val secondaryAccent = tokens.secondaryAccentColor ?: shell.darken(0.28f)
    return NowPlayingWidgetAppearance(
        renderer = WidgetAppearanceRenderer.POCKET_FLIP,
        background = WidgetColorToken.Fixed(shell),
        artworkSurface = WidgetColorToken.Fixed(display.darken(0.534f)),
        primaryText = WidgetColorToken.Fixed(displayText),
        secondaryText = WidgetColorToken.Fixed(displayText.darken(0.286f)),
        metadataSurface = WidgetColorToken.Fixed(display),
        metadataPrimaryText = WidgetColorToken.Fixed(displayText),
        metadataSecondaryText = WidgetColorToken.Fixed(displayText.darken(0.286f)),
        panelSurface = WidgetColorToken.Fixed(display.darken(0.534f)),
        panelOutline = WidgetColorToken.Fixed(secondaryAccent.darken(0.287f)),
        artworkPlaceholderTint = WidgetColorToken.Fixed(displayText.darken(0.286f)),
        accent = WidgetColorToken.Fixed(buttons),
        disabled = WidgetColorToken.Fixed(displayText.darken(0.55f)),
        controlSurface = WidgetColorToken.Fixed(display.darken(0.20f)),
        controlForeground = WidgetColorToken.Fixed(displayText),
        widgetCornerRadiusDp = 10,
        artworkCornerRadiusDp = 3,
        panelCornerRadiusDp = 6,
        controlCornerRadiusDp = 20
    )
}

private fun pocketDiscWidgetAppearance(
    preferences: AppPreferencesState
): NowPlayingWidgetAppearance {
    val tokens = resolvedWidgetThemeTokens(PlayerTheme.POCKET_DISC, preferences)
    val shell = tokens.shellColor
    val lcdGlow = tokens.accentColor
    val display = tokens.displayBackgroundColor
    val displayText = tokens.displayTextColor
    val active = tokens.secondaryAccentColor ?: lcdGlow
    return NowPlayingWidgetAppearance(
        renderer = WidgetAppearanceRenderer.POCKET_DISC,
        background = WidgetColorToken.Fixed(shell),
        artworkSurface = WidgetColorToken.Fixed(shell.darken(0.43f)),
        primaryText = WidgetColorToken.Fixed(displayText),
        secondaryText = WidgetColorToken.Fixed(displayText.darken(0.26f)),
        metadataSurface = WidgetColorToken.Fixed(display),
        metadataPrimaryText = WidgetColorToken.Fixed(displayText),
        metadataSecondaryText = WidgetColorToken.Fixed(displayText.darken(0.26f)),
        panelSurface = WidgetColorToken.Fixed(shell.darken(0.12f)),
        panelOutline = WidgetColorToken.Fixed(shell.lighten(0.34f)),
        artworkPlaceholderTint = WidgetColorToken.Fixed(lcdGlow.darken(0.32f)),
        accent = WidgetColorToken.Fixed(active),
        disabled = WidgetColorToken.Fixed(displayText.darken(0.55f)),
        controlSurface = WidgetColorToken.Fixed(shell.darken(0.28f)),
        controlForeground = WidgetColorToken.Fixed(displayText.lighten(0.18f)),
        widgetCornerRadiusDp = 8,
        artworkCornerRadiusDp = 40,
        panelCornerRadiusDp = 7,
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
