package io.github.rsgarrido.sazanami.widget

import androidx.compose.ui.graphics.Color
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesState
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import io.github.rsgarrido.sazanami.ui.theme.SazanamiAccent
import io.github.rsgarrido.sazanami.ui.theme.SazanamiOnSurface
import io.github.rsgarrido.sazanami.ui.theme.SazanamiSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NowPlayingWidgetAppearanceTest {
    @Test
    fun storageValueRoundTripsAndUnknownValuesDefaultToFollow() {
        WidgetAppearanceMode.entries.forEach { mode ->
            assertSame(mode, WidgetAppearanceMode.fromStorageValue(mode.storageValue))
        }

        assertSame(
            WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
            WidgetAppearanceMode.fromStorageValue(null)
        )
        assertSame(
            WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
            WidgetAppearanceMode.fromStorageValue("future_mode")
        )
    }

    @Test
    fun followPlayerThemeMapsEveryCurrentPlayerThemeToItsWidgetRenderer() {
        mapOf(
            PlayerTheme.DEFAULT to WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
            PlayerTheme.CLASSIC_WHEEL to WidgetAppearanceRenderer.CLASSIC_WHEEL,
            PlayerTheme.RETRO_RACK to WidgetAppearanceRenderer.RETRO_RACK,
            PlayerTheme.POCKET_FLIP to WidgetAppearanceRenderer.POCKET_FLIP,
            PlayerTheme.POCKET_CASSETTE to WidgetAppearanceRenderer.POCKET_CASSETTE,
            PlayerTheme.POCKET_DISC to WidgetAppearanceRenderer.POCKET_DISC
        ).forEach { (theme, expectedRenderer) ->
            assertSame(
                expectedRenderer,
                widgetAppearanceRendererFor(WidgetAppearanceMode.FOLLOW_PLAYER_THEME, theme)
            )
        }
    }

    @Test
    fun fixedRetroModesIgnoreThePlayerThemeUsedInsideTheApp() {
        mapOf(
            WidgetAppearanceMode.CLASSIC_WHEEL to WidgetAppearanceRenderer.CLASSIC_WHEEL,
            WidgetAppearanceMode.RETRO_RACK to WidgetAppearanceRenderer.RETRO_RACK,
            WidgetAppearanceMode.POCKET_FLIP to WidgetAppearanceRenderer.POCKET_FLIP,
            WidgetAppearanceMode.POCKET_CASSETTE to WidgetAppearanceRenderer.POCKET_CASSETTE,
            WidgetAppearanceMode.POCKET_DISC to WidgetAppearanceRenderer.POCKET_DISC
        ).forEach { (mode, expectedRenderer) ->
            assertSame(
                expectedRenderer,
                widgetAppearanceRendererFor(mode, PlayerTheme.DEFAULT)
            )
        }
    }

    @Test
    fun defaultAppearanceMapsOnlyWidgetRelevantPlayerCustomization() {
        val customColor = 0xFF213243L
        val preferences = AppPreferencesState(
            modernPlayerAppearance = ModernPlayerAppearance(
                background = ModernBackgroundAppearance(
                    style = ModernBackgroundStyle.SOLID_COLOR,
                    solidColorArgb = customColor
                ),
                artwork = ModernArtworkAppearance(
                    shape = ModernArtworkShape.EXTRA_ROUNDED
                ),
                controls = ModernControlAppearance(
                    accent = ModernControlAccent.APP_ACCENT
                )
            ),
            isLoaded = true
        )

        val appearance = resolveWidgetAppearance(
            WidgetAppearanceMode.SAZANAMI_DEFAULT,
            preferences
        )

        assertSame(WidgetAppearanceRenderer.SAZANAMI_DEFAULT, appearance.renderer)
        assertEquals(
            WidgetColorToken.Fixed(Color(customColor.toInt())),
            appearance.background
        )
        assertEquals(WidgetColorToken.Fixed(SazanamiAccent), appearance.accent)
        assertEquals(16, appearance.artworkCornerRadiusDp)
    }

    @Test
    fun defaultAppearanceUsesStableFallbacksForNonPortableArtworkBackgrounds() {
        val preferences = AppPreferencesState(
            modernPlayerAppearance = ModernPlayerAppearance(
                background = ModernBackgroundAppearance(
                    style = ModernBackgroundStyle.ALBUM_GRADIENT
                ),
                artwork = ModernArtworkAppearance(
                    shape = ModernArtworkShape.SQUARE
                ),
                controls = ModernControlAppearance(
                    accent = ModernControlAccent.ALBUM_DERIVED
                )
            ),
            isLoaded = true
        )

        val appearance = resolveWidgetAppearance(
            WidgetAppearanceMode.SAZANAMI_DEFAULT,
            preferences
        )

        assertEquals(WidgetColorToken.Fixed(SazanamiSurface), appearance.background)
        assertEquals(WidgetColorToken.Fixed(SazanamiAccent), appearance.accent)
        assertEquals(0, appearance.artworkCornerRadiusDp)
    }

    @Test
    fun defaultAppearanceMapsPureBlackAndWhiteControlAccent() {
        val preferences = AppPreferencesState(
            modernPlayerAppearance = ModernPlayerAppearance(
                background = ModernBackgroundAppearance(
                    style = ModernBackgroundStyle.PURE_BLACK
                ),
                controls = ModernControlAppearance(
                    accent = ModernControlAccent.WHITE
                )
            ),
            isLoaded = true
        )

        val appearance = resolveWidgetAppearance(
            WidgetAppearanceMode.SAZANAMI_DEFAULT,
            preferences
        )

        assertEquals(WidgetColorToken.Fixed(Color.Black), appearance.background)
        assertEquals(WidgetColorToken.Fixed(SazanamiOnSurface), appearance.accent)
    }

    @Test
    fun systemDynamicUsesGlanceSystemRolesWithItsBuiltInOlderDeviceFallback() {
        val appearance = resolveWidgetAppearance(
            WidgetAppearanceMode.SYSTEM_DYNAMIC,
            AppPreferencesState(isLoaded = true)
        )

        assertSame(WidgetAppearanceRenderer.SYSTEM_DYNAMIC, appearance.renderer)
        assertSame(WidgetColorToken.SystemBackground, appearance.background)
        assertSame(WidgetColorToken.SystemSurface, appearance.artworkSurface)
        assertSame(WidgetColorToken.SystemPrimaryText, appearance.primaryText)
        assertSame(WidgetColorToken.SystemSecondaryText, appearance.secondaryText)
        assertSame(WidgetColorToken.SystemAccent, appearance.accent)
        assertSame(WidgetColorToken.SystemDisabled, appearance.disabled)
    }

    @Test
    fun differentStoredModesResolveIndependentAppearanceFamilies() {
        val preferences = AppPreferencesState(isLoaded = true)
        val first = resolveWidgetAppearance(
            WidgetAppearanceMode.SAZANAMI_DEFAULT,
            preferences
        )
        val second = resolveWidgetAppearance(
            WidgetAppearanceMode.SYSTEM_DYNAMIC,
            preferences
        )

        assertNotEquals(first.renderer, second.renderer)
        assertNotEquals(first.background, second.background)
    }

    @Test
    fun retroAppearancesReuseTheirOwnPersistedThemeTokenOverrides() {
        val rackShell = Color(0xFF181A20)
        val rackAccent = Color(0xFF44DD77)
        val rackDisplay = Color(0xFF020403)
        val rackText = Color(0xFFE8ECEF)
        val cassetteShell = Color(0xFFD5C8B8)
        val cassetteDisplay = Color(0xFF111315)
        val cassetteText = Color(0xFFF2EFE8)
        val cassetteWarmAccent = Color(0xFFEE6633)
        val preferences = AppPreferencesState(
            selectedPlayerTheme = PlayerTheme.CLASSIC_WHEEL,
            playerThemeTokenOverrides = mapOf(
                PlayerTheme.RETRO_RACK to PlayerThemeTokenOverrides(
                    shellColor = rackShell,
                    accentColor = rackAccent,
                    displayBackgroundColor = rackDisplay,
                    displayTextColor = rackText
                ),
                PlayerTheme.POCKET_CASSETTE to PlayerThemeTokenOverrides(
                    shellColor = cassetteShell,
                    displayBackgroundColor = cassetteDisplay,
                    displayTextColor = cassetteText,
                    secondaryAccentColor = cassetteWarmAccent
                )
            ),
            isLoaded = true
        )

        val rackTokens = resolvedWidgetThemeTokens(PlayerTheme.RETRO_RACK, preferences)
        val cassetteTokens = resolvedWidgetThemeTokens(PlayerTheme.POCKET_CASSETTE, preferences)
        val rackAppearance = resolveWidgetAppearance(WidgetAppearanceMode.RETRO_RACK, preferences)
        val cassetteAppearance = resolveWidgetAppearance(
            WidgetAppearanceMode.POCKET_CASSETTE,
            preferences
        )

        assertEquals(rackShell, rackTokens.shellColor)
        assertEquals(rackAccent, rackTokens.accentColor)
        assertEquals(rackDisplay, rackTokens.displayBackgroundColor)
        assertEquals(rackText, rackTokens.displayTextColor)
        assertEquals(cassetteShell, cassetteTokens.shellColor)
        assertEquals(cassetteDisplay, cassetteTokens.displayBackgroundColor)
        assertEquals(cassetteText, cassetteTokens.displayTextColor)
        assertEquals(cassetteWarmAccent, cassetteTokens.secondaryAccentColor)
        assertSame(WidgetAppearanceRenderer.RETRO_RACK, rackAppearance.renderer)
        assertSame(WidgetAppearanceRenderer.POCKET_CASSETTE, cassetteAppearance.renderer)
        assertEquals(WidgetColorToken.Fixed(cassetteWarmAccent), cassetteAppearance.accent)
    }

    @Test
    fun remainingRetroAppearancesReuseTheirOwnPersistedThemeTokenOverrides() {
        val classicShell = Color(0xFF34373C)
        val classicWheel = Color(0xFF101114)
        val classicCenter = Color(0xFFE84855)
        val flipShell = Color(0xFF274A78)
        val flipButtons = Color(0xFFE2B84D)
        val flipAccent = Color(0xFF34658E)
        val discShell = Color(0xFF4A273B)
        val discGlow = Color(0xFF82F0C2)
        val discActive = Color(0xFFF07C8F)
        val preferences = AppPreferencesState(
            playerThemeTokenOverrides = mapOf(
                PlayerTheme.CLASSIC_WHEEL to PlayerThemeTokenOverrides(
                    shellColor = classicShell,
                    accentColor = classicWheel,
                    secondaryAccentColor = classicCenter
                ),
                PlayerTheme.POCKET_FLIP to PlayerThemeTokenOverrides(
                    shellColor = flipShell,
                    accentColor = flipButtons,
                    secondaryAccentColor = flipAccent
                ),
                PlayerTheme.POCKET_DISC to PlayerThemeTokenOverrides(
                    shellColor = discShell,
                    accentColor = discGlow,
                    secondaryAccentColor = discActive
                )
            ),
            isLoaded = true
        )

        val classic = resolveWidgetAppearance(WidgetAppearanceMode.CLASSIC_WHEEL, preferences)
        val flip = resolveWidgetAppearance(WidgetAppearanceMode.POCKET_FLIP, preferences)
        val disc = resolveWidgetAppearance(WidgetAppearanceMode.POCKET_DISC, preferences)

        assertSame(WidgetAppearanceRenderer.CLASSIC_WHEEL, classic.renderer)
        assertEquals(WidgetColorToken.Fixed(classicShell), classic.background)
        assertEquals(WidgetColorToken.Fixed(classicWheel), classic.controlSurface)
        assertEquals(WidgetColorToken.Fixed(classicCenter), classic.panelSurface)
        assertSame(WidgetAppearanceRenderer.POCKET_FLIP, flip.renderer)
        assertEquals(WidgetColorToken.Fixed(flipShell), flip.background)
        assertEquals(WidgetColorToken.Fixed(flipButtons), flip.accent)
        assertSame(WidgetAppearanceRenderer.POCKET_DISC, disc.renderer)
        assertEquals(WidgetColorToken.Fixed(discShell), disc.background)
        assertEquals(WidgetColorToken.Fixed(discActive), disc.accent)
    }

    @Test
    fun bothRetroRenderersProvideCompactAndStandardLayouts() {
        assertEquals(
            WidgetRendererLayout.RETRO_RACK_COMPACT,
            widgetRendererLayoutFor(
                WidgetAppearanceRenderer.RETRO_RACK,
                NowPlayingWidgetLayout.COMPACT
            )
        )
        assertEquals(
            WidgetRendererLayout.RETRO_RACK_STANDARD,
            widgetRendererLayoutFor(
                WidgetAppearanceRenderer.RETRO_RACK,
                NowPlayingWidgetLayout.STANDARD
            )
        )
        assertEquals(
            WidgetRendererLayout.POCKET_CASSETTE_COMPACT,
            widgetRendererLayoutFor(
                WidgetAppearanceRenderer.POCKET_CASSETTE,
                NowPlayingWidgetLayout.COMPACT
            )
        )
        assertEquals(
            WidgetRendererLayout.POCKET_CASSETTE_STANDARD,
            widgetRendererLayoutFor(
                WidgetAppearanceRenderer.POCKET_CASSETTE,
                NowPlayingWidgetLayout.STANDARD
            )
        )
    }

    @Test
    fun remainingRetroRenderersProvideCompactAndStandardLayouts() {
        mapOf(
            WidgetAppearanceRenderer.CLASSIC_WHEEL to Pair(
                WidgetRendererLayout.CLASSIC_WHEEL_COMPACT,
                WidgetRendererLayout.CLASSIC_WHEEL_STANDARD
            ),
            WidgetAppearanceRenderer.POCKET_FLIP to Pair(
                WidgetRendererLayout.POCKET_FLIP_COMPACT,
                WidgetRendererLayout.POCKET_FLIP_STANDARD
            ),
            WidgetAppearanceRenderer.POCKET_DISC to Pair(
                WidgetRendererLayout.POCKET_DISC_COMPACT,
                WidgetRendererLayout.POCKET_DISC_STANDARD
            )
        ).forEach { (renderer, layouts) ->
            assertEquals(
                layouts.first,
                widgetRendererLayoutFor(renderer, NowPlayingWidgetLayout.COMPACT)
            )
            assertEquals(
                layouts.second,
                widgetRendererLayoutFor(renderer, NowPlayingWidgetLayout.STANDARD)
            )
        }
    }
}
