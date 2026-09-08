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
    fun followPlayerThemeMapsDefaultPlayerToDefaultWidgetRenderer() {
        assertSame(
            WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
            widgetAppearanceRendererFor(
                WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
                PlayerTheme.DEFAULT
            )
        )
    }

    @Test
    fun followPlayerThemeDeliberatelyFallsBackForEveryRetroTheme() {
        PlayerTheme.entries
            .filterNot { theme -> theme == PlayerTheme.DEFAULT }
            .forEach { theme ->
                assertSame(
                    WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
                    widgetAppearanceRendererFor(
                        WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
                        theme
                    )
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
}
