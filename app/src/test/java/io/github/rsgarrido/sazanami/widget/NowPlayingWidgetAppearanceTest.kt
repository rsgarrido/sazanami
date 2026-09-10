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
import org.junit.Assert.assertTrue
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
        val rackActive = Color(0xFFF2B84B)
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
                    displayTextColor = rackText,
                    secondaryAccentColor = rackActive
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
        assertEquals(rackActive, rackTokens.secondaryAccentColor)
        assertEquals(cassetteShell, cassetteTokens.shellColor)
        assertEquals(cassetteDisplay, cassetteTokens.displayBackgroundColor)
        assertEquals(cassetteText, cassetteTokens.displayTextColor)
        assertEquals(cassetteWarmAccent, cassetteTokens.secondaryAccentColor)
        assertSame(WidgetAppearanceRenderer.RETRO_RACK, rackAppearance.renderer)
        assertEquals(WidgetColorToken.Fixed(rackShell), rackAppearance.panelSurface)
        assertEquals(WidgetColorToken.Fixed(rackDisplay), rackAppearance.metadataSurface)
        assertEquals(WidgetColorToken.Fixed(rackAccent), rackAppearance.metadataPrimaryText)
        assertEquals(WidgetColorToken.Fixed(rackText), rackAppearance.controlForeground)
        assertEquals(WidgetColorToken.Fixed(rackActive), rackAppearance.accent)
        assertSame(WidgetAppearanceRenderer.POCKET_CASSETTE, cassetteAppearance.renderer)
        assertEquals(WidgetColorToken.Fixed(cassetteShell), cassetteAppearance.background)
        assertEquals(
            WidgetColorToken.Fixed(cassetteDisplay),
            cassetteAppearance.metadataSurface
        )
        assertEquals(
            WidgetColorToken.Fixed(cassetteText),
            cassetteAppearance.metadataPrimaryText
        )
        assertEquals(WidgetColorToken.Fixed(cassetteWarmAccent), cassetteAppearance.accent)
    }

    @Test
    fun remainingRetroAppearancesReuseTheirOwnPersistedThemeTokenOverrides() {
        val classicShell = Color(0xFF34373C)
        val classicWheel = Color(0xFF101114)
        val classicDisplay = Color(0xFFF4F1E7)
        val classicText = Color(0xFF17191C)
        val classicCenter = Color(0xFFE84855)
        val flipShell = Color(0xFF274A78)
        val flipButtons = Color(0xFFE2B84D)
        val flipDisplay = Color(0xFF15251F)
        val flipText = Color(0xFFEAF3DF)
        val flipAccent = Color(0xFF34658E)
        val discShell = Color(0xFF4A273B)
        val discGlow = Color(0xFF82F0C2)
        val discDisplay = Color(0xFF101D23)
        val discText = Color(0xFFD8F4EF)
        val discActive = Color(0xFFF07C8F)
        val preferences = AppPreferencesState(
            playerThemeTokenOverrides = mapOf(
                PlayerTheme.CLASSIC_WHEEL to PlayerThemeTokenOverrides(
                    shellColor = classicShell,
                    accentColor = classicWheel,
                    displayBackgroundColor = classicDisplay,
                    displayTextColor = classicText,
                    secondaryAccentColor = classicCenter
                ),
                PlayerTheme.POCKET_FLIP to PlayerThemeTokenOverrides(
                    shellColor = flipShell,
                    accentColor = flipButtons,
                    displayBackgroundColor = flipDisplay,
                    displayTextColor = flipText,
                    secondaryAccentColor = flipAccent
                ),
                PlayerTheme.POCKET_DISC to PlayerThemeTokenOverrides(
                    shellColor = discShell,
                    accentColor = discGlow,
                    displayBackgroundColor = discDisplay,
                    displayTextColor = discText,
                    secondaryAccentColor = discActive
                )
            ),
            isLoaded = true
        )

        val classicTokens = resolvedWidgetThemeTokens(PlayerTheme.CLASSIC_WHEEL, preferences)
        val classic = resolveWidgetAppearance(WidgetAppearanceMode.CLASSIC_WHEEL, preferences)
        val flipTokens = resolvedWidgetThemeTokens(PlayerTheme.POCKET_FLIP, preferences)
        val flip = resolveWidgetAppearance(WidgetAppearanceMode.POCKET_FLIP, preferences)
        val discTokens = resolvedWidgetThemeTokens(PlayerTheme.POCKET_DISC, preferences)
        val disc = resolveWidgetAppearance(WidgetAppearanceMode.POCKET_DISC, preferences)

        assertSame(WidgetAppearanceRenderer.CLASSIC_WHEEL, classic.renderer)
        assertEquals(classicShell, classicTokens.shellColor)
        assertEquals(classicWheel, classicTokens.accentColor)
        assertEquals(classicDisplay, classicTokens.displayBackgroundColor)
        assertEquals(classicText, classicTokens.displayTextColor)
        assertEquals(classicCenter, classicTokens.secondaryAccentColor)
        assertEquals(WidgetColorToken.Fixed(classicShell), classic.background)
        assertEquals(WidgetColorToken.Fixed(classicWheel), classic.controlSurface)
        assertEquals(WidgetColorToken.Fixed(classicCenter), classic.panelSurface)
        assertEquals(WidgetColorToken.Fixed(classicDisplay), classic.metadataSurface)
        assertEquals(WidgetColorToken.Fixed(classicText), classic.metadataPrimaryText)
        assertSame(WidgetAppearanceRenderer.POCKET_FLIP, flip.renderer)
        assertEquals(flipShell, flipTokens.shellColor)
        assertEquals(flipButtons, flipTokens.accentColor)
        assertEquals(flipDisplay, flipTokens.displayBackgroundColor)
        assertEquals(flipText, flipTokens.displayTextColor)
        assertEquals(flipAccent, flipTokens.secondaryAccentColor)
        assertEquals(WidgetColorToken.Fixed(flipShell), flip.background)
        assertEquals(WidgetColorToken.Fixed(flipButtons), flip.accent)
        assertEquals(WidgetColorToken.Fixed(flipDisplay), flip.metadataSurface)
        assertEquals(WidgetColorToken.Fixed(flipText), flip.metadataPrimaryText)
        assertSame(WidgetAppearanceRenderer.POCKET_DISC, disc.renderer)
        assertEquals(discShell, discTokens.shellColor)
        assertEquals(discGlow, discTokens.accentColor)
        assertEquals(discDisplay, discTokens.displayBackgroundColor)
        assertEquals(discText, discTokens.displayTextColor)
        assertEquals(discActive, discTokens.secondaryAccentColor)
        assertEquals(WidgetColorToken.Fixed(discShell), disc.background)
        assertEquals(WidgetColorToken.Fixed(discDisplay), disc.metadataSurface)
        assertEquals(WidgetColorToken.Fixed(discText), disc.metadataPrimaryText)
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
    fun pocketCassetteLayoutsKeepCassetteIdentityAtBothResponsiveSizes() {
        val compact = pocketCassetteWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
        val standard = pocketCassetteWidgetCompositionFor(
            layout = NowPlayingWidgetLayout.STANDARD,
            widgetHeightDp = 200f,
            widgetWidthDp = 250f
        )

        assertEquals(false, compact.showReelDecoration)
        assertEquals(false, standard.showReelDecoration)
        assertEquals(false, compact.usesFullWidthTransportDeck)
        assertEquals(true, standard.usesFullWidthTransportDeck)
        assertEquals(false, compact.outerFrameRetained)
        assertEquals(true, standard.outerFrameRetained)
        assertEquals(false, compact.showHardwareDetails)
        assertEquals(true, standard.showHardwareDetails)
        assertEquals(32, compact.controlEdgeDp)
        assertEquals(32, standard.controlEdgeDp)
        assertEquals(36, compact.transportDeckHeightDp)
        assertEquals(40, standard.transportDeckHeightDp)
        assertEquals(30, compact.artworkEdgeDp)
        assertEquals(130, standard.artworkEdgeDp)
    }

    @Test
    fun retroRackLayoutsUseDistinctRackModuleCompositions() {
        val compact = retroRackWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
        val standard = retroRackWidgetCompositionFor(
            layout = NowPlayingWidgetLayout.STANDARD,
            widgetHeightDp = 200f,
            widgetWidthDp = 250f
        )

        assertEquals(false, compact.headerAboveArtwork)
        assertEquals(true, standard.headerAboveArtwork)
        assertEquals(6, standard.headerHorizontalInsetDp)
        assertEquals(2, standard.artworkFrameInsetDp)
        assertEquals(true, standard.outerBezelRetained)
        assertEquals(false, compact.showHardwareDetails)
        assertEquals(true, standard.showHardwareDetails)
        assertEquals(32, compact.controlEdgeDp)
        assertEquals(32, standard.controlEdgeDp)
        assertEquals(28, compact.artworkEdgeDp)
        assertEquals(130, standard.artworkEdgeDp)
    }

    @Test
    fun pocketFlipLayoutsKeepScreenAndControlHalvesJoinedByResponsiveHinges() {
        val compact = pocketFlipWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
        val standard = pocketFlipWidgetCompositionFor(
            layout = NowPlayingWidgetLayout.STANDARD,
            widgetHeightDp = 200f,
            widgetWidthDp = 250f
        )

        assertEquals(true, compact.usesCompressedVerticalHinge)
        assertEquals(false, standard.usesCompressedVerticalHinge)
        assertEquals(false, compact.showDeckDetails)
        assertEquals(true, standard.showDeckDetails)
        assertEquals(true, standard.outerBezelRetained)
        assertEquals(32, compact.controlEdgeDp)
        assertEquals(32, standard.controlEdgeDp)
        assertEquals(40, compact.controlDeckHeightDp)
        assertEquals(42, standard.controlDeckHeightDp)
        assertTrue(standard.controlDeckHeightDp > standard.controlEdgeDp)
        assertEquals(30, compact.artworkEdgeDp)
        assertEquals(130, standard.artworkEdgeDp)
    }

    @Test
    fun classicWheelLayoutsKeepAFramedScreenAndWheelControlRegion() {
        val compact = classicWheelWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
        val standard = classicWheelWidgetCompositionFor(
            layout = NowPlayingWidgetLayout.STANDARD,
            widgetHeightDp = 200f,
            widgetWidthDp = 250f
        )

        assertEquals(false, compact.headerAboveArtwork)
        assertEquals(true, standard.headerAboveArtwork)
        assertEquals(6, standard.statusHorizontalInsetDp)
        assertEquals(true, standard.outerBezelRetained)
        assertEquals(102, compact.wheelWidthDp)
        assertEquals(190, standard.wheelWidthDp)
        assertEquals(40, compact.wheelHeightDp)
        assertEquals(36, standard.wheelHeightDp)
        assertEquals(40, standard.controlRegionHeightDp)
        assertTrue(standard.controlRegionHeightDp > standard.wheelHeightDp)
        assertEquals(0, compact.controlSpacingDp)
        assertEquals(18, standard.controlSpacingDp)
        assertEquals(false, compact.showCenterRing)
        assertEquals(false, standard.showCenterRing)
        assertEquals(32, compact.controlEdgeDp)
        assertEquals(32, standard.controlEdgeDp)
        assertEquals(30, compact.artworkEdgeDp)
        assertEquals(130, standard.artworkEdgeDp)
    }

    @Test
    fun pocketDiscLayoutsKeepArtworkInsideAResponsiveDiscCartridge() {
        val compact = pocketDiscWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
        val standard = pocketDiscWidgetCompositionFor(NowPlayingWidgetLayout.STANDARD)

        assertEquals(false, compact.showMoldedDetails)
        assertEquals(true, standard.showMoldedDetails)
        assertEquals(32, compact.controlEdgeDp)
        assertEquals(32, standard.controlEdgeDp)
        assertEquals(40, compact.cartridgeEdgeDp)
        assertEquals(34, compact.discWindowEdgeDp)
        assertEquals(28, compact.artworkEdgeDp)
        assertEquals(88, standard.cartridgeEdgeDp)
        assertEquals(76, standard.discWindowEdgeDp)
        assertEquals(68, standard.artworkEdgeDp)
        assertTrue(compact.discWindowEdgeDp > compact.artworkEdgeDp)
        assertTrue(standard.discWindowEdgeDp > standard.artworkEdgeDp)
        assertTrue(standard.cartridgeEdgeDp > compact.cartridgeEdgeDp)
    }

    @Test
    fun retroMetadataWrapsExpandedTitlesWithoutChangingCompactPolicy() {
        assertEquals(
            WidgetMetadataLinePolicy(titleMaxLines = 1, artistMaxLines = 1),
            retroWidgetMetadataLinePolicyFor(NowPlayingWidgetLayout.COMPACT)
        )
        assertEquals(
            WidgetMetadataLinePolicy(titleMaxLines = 2, artistMaxLines = 1),
            retroWidgetMetadataLinePolicyFor(NowPlayingWidgetLayout.STANDARD)
        )
        assertEquals(
            WidgetMetadataLinePolicy(titleMaxLines = 3, artistMaxLines = 1),
            retroWidgetMetadataLinePolicyFor(
                layout = NowPlayingWidgetLayout.STANDARD,
                standardTitleMaxLines = 3
            )
        )
    }

    @Test
    fun expandedRetroContentInsetsReserveHeightForMediaAndHardwareRegions() {
        WidgetAppearanceRenderer.entries.forEach { renderer ->
            assertEquals(
                8,
                widgetContentPaddingDpFor(renderer, NowPlayingWidgetLayout.COMPACT)
            )
        }
        assertEquals(
            3,
            widgetContentPaddingDpFor(
                WidgetAppearanceRenderer.CLASSIC_WHEEL,
                NowPlayingWidgetLayout.STANDARD
            )
        )
        assertEquals(
            3,
            widgetContentPaddingDpFor(
                WidgetAppearanceRenderer.RETRO_RACK,
                NowPlayingWidgetLayout.STANDARD
            )
        )
        assertEquals(
            3,
            widgetContentPaddingDpFor(
                WidgetAppearanceRenderer.POCKET_FLIP,
                NowPlayingWidgetLayout.STANDARD
            )
        )
        assertEquals(
            3,
            widgetContentPaddingDpFor(
                WidgetAppearanceRenderer.POCKET_CASSETTE,
                NowPlayingWidgetLayout.STANDARD
            )
        )
        assertEquals(
            8,
            widgetContentPaddingDpFor(
                WidgetAppearanceRenderer.POCKET_DISC,
                NowPlayingWidgetLayout.STANDARD
            )
        )
        assertEquals(
            8,
            widgetContentPaddingDpFor(
                WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
                NowPlayingWidgetLayout.STANDARD
            )
        )
    }

    @Test
    fun expandedArtworkSizingFollowsAvailableHeightAndPreservesMetadataWidth() {
        assertEquals(
            54,
            expandedArtworkEdgeDpFor(
                widgetHeightDp = 120f,
                widgetWidthDp = 250f,
                reservedVerticalSpaceDp = 66,
                minimumEdgeDp = 36
            )
        )
        assertEquals(
            94,
            expandedArtworkEdgeDpFor(
                widgetHeightDp = 160f,
                widgetWidthDp = 250f,
                reservedVerticalSpaceDp = 66,
                minimumEdgeDp = 36
            )
        )
        assertEquals(
            130,
            expandedArtworkEdgeDpFor(
                widgetHeightDp = 240f,
                widgetWidthDp = 250f,
                reservedVerticalSpaceDp = 66,
                minimumEdgeDp = 36
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
