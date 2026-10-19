package io.github.rsgarrido.sazanami.data.preferences

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.home.HomePin
import io.github.rsgarrido.sazanami.data.home.HomePinType
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearancePreset
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkFit
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShadow
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBlurStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernDimmingStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerArrangement
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerLayout
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.modern.ModernMetadataAlignment
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarColorMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesStateTest {
    @Test
    fun landscapeChoiceDefaultsSafelyAndNeverEditsAnAppearanceRecipe() {
        val key = stringPreferencesKey("modern_landscape_layout")
        assertEquals(ModernLandscapeLayout.BALANCED, decodeAppPreferences(mutablePreferencesOf()).modernLandscapeLayout)
        assertEquals(ModernLandscapeLayout.BALANCED,
            decodeAppPreferences(mutablePreferencesOf(key to "future_layout")).modernLandscapeLayout)
        val preferences = mutablePreferencesOf(booleanPreferencesKey("smooth_play_pause_enabled") to false)
        val saved = ModernAppearancePreset.COLORFUL.appearance()
        preferences.writeModernPlayerAppearance(saved)
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MINIMAL)
        preferences.writeModernLandscapeLayout(ModernLandscapeLayout.WIDE_WAVEFORM)
        assertEquals(ModernAppearanceChoice.MINIMAL, decodeAppPreferences(preferences).activeModernAppearanceChoice)
        assertEquals(saved, decodeAppPreferences(preferences).myPlayerAppearance)
        ModernAppearanceChoice.entries.forEach { choice ->
            preferences.selectModernAppearanceChoice(choice)
            assertEquals(ModernLandscapeLayout.WIDE_WAVEFORM, decodeAppPreferences(preferences).modernLandscapeLayout)
        }
        preferences.editModernPlayerAppearance { it.copy(layout = it.layout.copy(showAudioQualityBadge = false)) }
        assertEquals(ModernLandscapeLayout.WIDE_WAVEFORM, decodeAppPreferences(preferences).modernLandscapeLayout)
        preferences.resetMyPlayerAppearance()
        val reset = decodeAppPreferences(preferences)
        assertEquals(ModernLandscapeLayout.WIDE_WAVEFORM, reset.modernLandscapeLayout)
        assertEquals(ModernPlayerAppearance.Default, reset.myPlayerAppearance)
        assertFalse(reset.smoothPlayPauseEnabled)
    }

    @Test
    fun responsiveFallbackDoesNotWriteTheStoredLandscapeChoice() {
        val preferences = mutablePreferencesOf()
        preferences.writeModernLandscapeLayout(ModernLandscapeLayout.WIDE_WAVEFORM)
        val before = preferences.asMap().toMap()
        fun layout(height: Int) = resolveDefaultPlayerLayout(1280.dp, height.dp,
            preferredLandscapeLayout = decodeAppPreferences(preferences).modernLandscapeLayout).arrangement
        assertEquals(DefaultPlayerArrangement.WideWaveform, layout(720))
        assertEquals(DefaultPlayerArrangement.Balanced, layout(440))
        assertEquals(DefaultPlayerArrangement.Vertical, layout(380))
        assertEquals(DefaultPlayerArrangement.WideWaveform, layout(720))
        assertEquals(before, preferences.asMap())
    }

    @Test
    fun experimentalUsbDirectIsDisabledBeforeLoadingAndWhenAbsentAfterUpgrade() {
        assertFalse(AppPreferencesState().experimentalUsbDirectEnabled)
        assertFalse(decodeAppPreferences(mutablePreferencesOf()).experimentalUsbDirectEnabled)

        // Existing app preferences and remembered playback intent cannot supply acknowledgment.
        val upgraded = decodeAppPreferences(mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.RETRO_RACK.id,
            booleanPreferencesKey("smooth_play_pause_enabled") to false
        ))
        assertFalse(upgraded.experimentalUsbDirectEnabled)
        assertEquals(PlayerTheme.RETRO_RACK, upgraded.selectedPlayerTheme)
        assertFalse(upgraded.smoothPlayPauseEnabled)
    }

    @Test
    fun experimentalUsbDirectDecodesOnlyItsExplicitPersistedOptIn() {
        val key = booleanPreferencesKey("experimental_usb_direct_enabled")
        assertTrue(decodeAppPreferences(mutablePreferencesOf(key to true)).experimentalUsbDirectEnabled)
        assertFalse(decodeAppPreferences(mutablePreferencesOf(key to false)).experimentalUsbDirectEnabled)
    }

    @Test
    fun appAppearanceUsesStableValuesAndFallsBackToDark() {
        val key = stringPreferencesKey("app_appearance")
        assertEquals(AppAppearance.DARK, decodeAppPreferences(mutablePreferencesOf()).appAppearance)
        assertEquals(AppAppearance.DARK, decodeAppPreferences(mutablePreferencesOf(key to "unknown")).appAppearance)
        AppAppearance.entries.forEach { appearance ->
            assertEquals(appearance.name, appearance.storageValue)
            assertEquals(
                appearance,
                decodeAppPreferences(mutablePreferencesOf(key to appearance.storageValue)).appAppearance
            )
        }
    }

    @Test
    fun systemAppearanceResolvesFromDeviceMode() {
        assertEquals(false, AppAppearance.SYSTEM.isDark(systemIsDark = false))
        assertEquals(true, AppAppearance.SYSTEM.isDark(systemIsDark = true))
        assertEquals(false, AppAppearance.LIGHT.isDark(systemIsDark = true))
        assertEquals(true, AppAppearance.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun missingChoiceKeepsExistingAppearanceAsMyPlayer() {
        val saved = ModernPlayerAppearance.Default.copy(
            background = ModernPlayerAppearance.Default.background.copy(
                solidColorArgb = 0xFF204060L
            )
        )
        val preferences = mutablePreferencesOf()
        preferences.writeModernPlayerAppearance(saved)

        val state = decodeAppPreferences(preferences)

        assertEquals(ModernAppearanceChoice.MY_PLAYER, state.activeModernAppearanceChoice)
        assertEquals(saved, state.myPlayerAppearance)
        assertEquals(saved, state.modernPlayerAppearance)
        assertEquals(
            ModernAppearanceChoice.MY_PLAYER,
            decodeAppPreferences(mutablePreferencesOf()).activeModernAppearanceChoice
        )
        assertEquals(
            ModernAppearanceChoice.MY_PLAYER,
            ModernAppearanceChoice.fromStorageValue("unknown")
        )
    }

    @Test
    fun builtInSelectionPreservesMyPlayerAndReturningRestoresIt() {
        val saved = ModernPlayerAppearance.Default.copy(
            background = ModernPlayerAppearance.Default.background.copy(solidColorArgb = 0xFF204060L)
        )
        val preferences = mutablePreferencesOf()
        preferences.writeModernPlayerAppearance(saved)
        val savedKeys = preferences.asMap()

        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MINIMAL)
        val minimal = decodeAppPreferences(preferences)
        assertEquals(savedKeys, preferences.asMap().filterKeys { it in savedKeys.keys })
        assertEquals(saved, minimal.myPlayerAppearance)
        assertEquals(ModernAppearancePreset.MINIMAL.appearance(), minimal.modernPlayerAppearance)

        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MY_PLAYER)
        val restored = decodeAppPreferences(preferences)
        assertEquals(saved, restored.modernPlayerAppearance)
        assertEquals(ModernAppearanceChoice.MY_PLAYER, restored.activeModernAppearanceChoice)
    }

    @Test
    fun exactRecipeMatchDoesNotChangeMyPlayerIdentity() {
        val preferences = mutablePreferencesOf()
        preferences.writeModernPlayerAppearance(ModernAppearancePreset.MINIMAL.appearance())

        val state = decodeAppPreferences(preferences)

        assertEquals(ModernAppearanceChoice.MY_PLAYER, state.activeModernAppearanceChoice)
        assertEquals(ModernAppearancePreset.MINIMAL.appearance(), state.modernPlayerAppearance)
    }

    @Test
    fun editingBuiltInStartsFromLatestRecipeAndKeepsRecipeImmutable() {
        val preferences = mutablePreferencesOf()
        val minimalRecipe = ModernAppearancePreset.MINIMAL.appearance()
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MINIMAL)

        preferences.editModernPlayerAppearance { current ->
            current.copy(artwork = current.artwork.copy(size = ModernArtworkSize.LARGE))
        }
        val edited = decodeAppPreferences(preferences)
        val expected = minimalRecipe.copy(
            artwork = minimalRecipe.artwork.copy(size = ModernArtworkSize.LARGE)
        )
        assertEquals(ModernAppearanceChoice.MY_PLAYER, edited.activeModernAppearanceChoice)
        assertEquals(expected, edited.myPlayerAppearance)
        assertEquals(expected, edited.modernPlayerAppearance)
        assertEquals(minimalRecipe, ModernAppearancePreset.MINIMAL.appearance())

        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MINIMAL)
        assertEquals(minimalRecipe, decodeAppPreferences(preferences).modernPlayerAppearance)
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MY_PLAYER)
        assertEquals(expected, decodeAppPreferences(preferences).modernPlayerAppearance)
    }

    @Test
    fun orderedActionsUseLatestEffectiveAppearanceForEachEdit() {
        val preferences = mutablePreferencesOf()
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MINIMAL)
        preferences.editModernPlayerAppearance { current ->
            current.copy(artwork = current.artwork.copy(size = ModernArtworkSize.LARGE))
        }
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.COLORFUL)
        preferences.editModernPlayerAppearance { current ->
            current.copy(controls = current.controls.copy(size = ModernControlSize.LARGE))
        }

        val colorful = ModernAppearancePreset.COLORFUL.appearance()
        val state = decodeAppPreferences(preferences)
        assertEquals(ModernAppearanceChoice.MY_PLAYER, state.activeModernAppearanceChoice)
        assertEquals(
            colorful.copy(controls = colorful.controls.copy(size = ModernControlSize.LARGE)),
            state.modernPlayerAppearance
        )
    }

    @Test
    fun resetClearsMyPlayerAndSelectsItWithoutTouchingOtherAppearanceSettings() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.RETRO_RACK.id,
            stringPreferencesKey("artwork_transition_style") to "cover_flow",
            stringPreferencesKey("retro_rack.accent") to "#FFAABBCC"
        )
        preferences.writeModernPlayerAppearance(ModernAppearancePreset.MINIMAL.appearance())
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.COLORFUL)

        preferences.resetMyPlayerAppearance()

        val state = decodeAppPreferences(preferences)
        assertEquals(ModernAppearanceChoice.MY_PLAYER, state.activeModernAppearanceChoice)
        assertEquals(ModernPlayerAppearance.Default, state.myPlayerAppearance)
        assertEquals(ModernPlayerAppearance.Default, state.modernPlayerAppearance)
        assertEquals(PlayerTheme.RETRO_RACK, state.selectedPlayerTheme)
        assertEquals(ModernArtworkTransitionStyle.COVER_FLOW, state.modernArtworkTransitionStyle)
        assertEquals(
            Color(0xFFAABBCC.toInt()),
            state.playerThemeTokenOverrides[PlayerTheme.RETRO_RACK]?.accentColor
        )
    }

    @Test
    fun invalidEnumsAndGridCountsFallBackSafely() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("app_font") to "missing-font",
            stringPreferencesKey("selected_player_theme") to "missing-theme",
            stringPreferencesKey("replay_gain_mode") to "LOUDER_THAN_INFINITY",
            stringPreferencesKey("audio_offload_preference") to "REQUIRED",
            stringPreferencesKey("artwork_transition_style") to "missing-transition",
            stringPreferencesKey("seekbar_style") to "missing-seekbar",
            stringPreferencesKey("modern_waveform_size") to "enormous",
            stringPreferencesKey("modern_waveform_density") to "infinite",
            stringPreferencesKey("modern_seekbar_color_mode") to "rainbow",
            stringPreferencesKey("modern_background_style") to "transparent",
            stringPreferencesKey("modern_blur_strength") to "extreme",
            stringPreferencesKey("modern_dimming_strength") to "opaque",
            longPreferencesKey("modern_solid_color_argb") to -1L,
            stringPreferencesKey("modern_artwork_shape") to "circle",
            stringPreferencesKey("modern_artwork_size") to "giant",
            stringPreferencesKey("modern_artwork_fit") to "stretch",
            stringPreferencesKey("modern_artwork_shadow") to "neon",
            stringPreferencesKey("modern_control_style") to "skeuomorphic",
            stringPreferencesKey("modern_control_size") to "tiny",
            stringPreferencesKey("modern_control_accent") to "rainbow",
            stringPreferencesKey("modern_layout_density") to "packed",
            stringPreferencesKey("modern_metadata_alignment") to "diagonal",
            intPreferencesKey("songs_view_mode_columns") to 99
        )

        val state = decodeAppPreferences(preferences)

        assertEquals(AppFont.SAZANAMI, state.appFont)
        assertEquals(PlayerTheme.DEFAULT, state.selectedPlayerTheme)
        assertEquals(ReplayGainMode.OFF, state.replayGainMode)
        assertEquals(AudioOffloadPreference.DISABLED, state.audioOffloadPreference)
        assertTrue(state.smoothPlayPauseEnabled)
        assertFalse(state.crossfadeEnabled)
        assertEquals(5_000, state.crossfadeDurationMs)
        assertTrue(state.preserveAlbumTransitions)
        assertEquals(ModernArtworkTransitionStyle.SLIDE, state.modernArtworkTransitionStyle)
        assertEquals(ModernPlayerAppearance.Default, state.modernPlayerAppearance)
        assertEquals(ModernSeekbarStyle.WAVEFORM_PREVIEW, state.modernSeekbarStyle)
        assertEquals(2, state.songsGridColumnCount)
        assertTrue(state.isLoaded)
    }

    @Test
    fun missingAppFontUsesSazanami() {
        assertEquals(
            AppFont.SAZANAMI,
            decodeAppPreferences(mutablePreferencesOf()).appFont
        )
    }

    @Test
    fun deviceAppFontRoundTripsWithoutChangingOtherAppearanceSettings() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.POCKET_FLIP.id,
            stringPreferencesKey("artwork_transition_style") to "cover_flow"
        )

        preferences.writeAppFont(AppFont.DEVICE)
        val restored = decodeAppPreferences(preferences)

        assertEquals(AppFont.DEVICE, restored.appFont)
        assertEquals(PlayerTheme.POCKET_FLIP, restored.selectedPlayerTheme)
        assertEquals(
            ModernArtworkTransitionStyle.COVER_FLOW,
            restored.modernArtworkTransitionStyle
        )
    }

    @Test
    fun absentModernSeekbarUsesNewBaselineButExplicitLegacyChoiceIsPreserved() {
        assertEquals(
            ModernSeekbarStyle.WAVEFORM_PREVIEW,
            decodeAppPreferences(mutablePreferencesOf()).modernSeekbarStyle
        )

        val explicitlyClassic = decodeAppPreferences(
            mutablePreferencesOf(
                stringPreferencesKey("seekbar_style") to "classic_bar"
            )
        )

        assertEquals(ModernSeekbarStyle.CLASSIC_BAR, explicitlyClassic.modernSeekbarStyle)
    }

    @Test
    fun modernAppearanceValuesRoundTripAndDoNotAffectRetroThemePreferences() {
        val expected = ModernPlayerAppearance(
            seekbar = ModernSeekbarAppearance(
                style = ModernSeekbarStyle.WAVEFORM_GLOW,
                waveformSize = ModernWaveformSize.TALL,
                waveformDensity = ModernWaveformDensity.DETAILED,
                colorMode = ModernSeekbarColorMode.APP_ACCENT
            ),
            background = ModernBackgroundAppearance(
                style = ModernBackgroundStyle.DETAILED_ARTWORK,
                blurStrength = ModernBlurStrength.LOW,
                dimmingStrength = ModernDimmingStrength.HIGH,
                solidColorArgb = 0xFF204060
            ),
            artwork = ModernArtworkAppearance(
                shape = ModernArtworkShape.EXTRA_ROUNDED,
                size = ModernArtworkSize.LARGE,
                fit = ModernArtworkFit.SHOW_FULL,
                shadow = ModernArtworkShadow.STRONG
            ),
            controls = ModernControlAppearance(
                style = ModernControlStyle.TONAL,
                size = ModernControlSize.LARGE,
                accent = ModernControlAccent.ALBUM_DERIVED
            ),
            layout = ModernLayoutAppearance(
                density = ModernLayoutDensity.RELAXED,
                metadataAlignment = ModernMetadataAlignment.CENTER,
                showAudioQualityBadge = false
            )
        )
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.RETRO_RACK.id,
            stringPreferencesKey("retro_rack.accent") to "#FF123456"
        )

        preferences.writeModernPlayerAppearance(expected)
        val state = decodeAppPreferences(preferences)

        assertEquals(expected, state.modernPlayerAppearance)
        assertEquals(PlayerTheme.RETRO_RACK, state.selectedPlayerTheme)
        assertEquals(
            Color(0xFF123456.toInt()),
            state.playerThemeTokenOverrides[PlayerTheme.RETRO_RACK]?.accentColor
        )
    }

    @Test
    fun resettingModernAppearanceClearsOnlyModernAppearanceKeys() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.POCKET_FLIP.id,
            stringPreferencesKey("artwork_transition_style") to "cover_flow",
            stringPreferencesKey("retro_rack.accent") to "#FFAABBCC"
        )
        preferences.writeModernPlayerAppearance(
            ModernPlayerAppearance(
                seekbar = ModernSeekbarAppearance(
                    style = ModernSeekbarStyle.SEGMENTED,
                    waveformSize = ModernWaveformSize.COMPACT,
                    waveformDensity = ModernWaveformDensity.SPARSE,
                    colorMode = ModernSeekbarColorMode.APP_ACCENT
                ),
                background = ModernBackgroundAppearance(
                    style = ModernBackgroundStyle.PURE_BLACK,
                    blurStrength = ModernBlurStrength.HIGH,
                    dimmingStrength = ModernDimmingStrength.LOW
                )
            )
        )

        preferences.clearModernPlayerAppearance()
        val reset = decodeAppPreferences(preferences)

        assertEquals(ModernPlayerAppearance.Default, reset.modernPlayerAppearance)
        assertEquals(PlayerTheme.POCKET_FLIP, reset.selectedPlayerTheme)
        assertEquals(ModernArtworkTransitionStyle.COVER_FLOW, reset.modernArtworkTransitionStyle)
        assertEquals(
            Color(0xFFAABBCC.toInt()),
            reset.playerThemeTokenOverrides[PlayerTheme.RETRO_RACK]?.accentColor
        )
    }

    @Test
    fun argbTokensFolderSelectionsAndLibraryAppearanceRoundTripFromMigratedKeys() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("retro_rack.accent") to "#80123456",
            stringSetPreferencesKey("selected_folders") to setOf("Music", "Card/Music"),
            stringPreferencesKey("songs_view_mode") to "grid",
            intPreferencesKey("songs_view_mode_columns") to 3,
            stringPreferencesKey("playlists_view_mode") to "grid",
            intPreferencesKey("playlists_view_mode_columns") to 4
        )

        val state = decodeAppPreferences(preferences)

        assertEquals(
            Color(0x80123456.toInt()),
            state.playerThemeTokenOverrides[PlayerTheme.RETRO_RACK]?.accentColor
        )
        assertEquals(setOf("Music", "Card/Music"), state.selectedLibraryFolders)
        assertEquals(FolderSelectionMode.CUSTOM, state.folderSelectionMode)
        assertEquals(LibraryViewMode.GRID, state.songsViewMode)
        assertEquals(3, state.songsGridColumnCount)
        assertEquals(LibraryViewMode.GRID, state.playlistsViewMode)
        assertEquals(4, state.playlistsGridColumnCount)
    }

    @Test
    fun absentModeMigratesEmptyLegacySelectionToAll() {
        val state = decodeAppPreferences(
            mutablePreferencesOf(
                stringSetPreferencesKey("selected_folders") to emptySet()
            )
        )

        assertEquals(FolderSelectionMode.ALL, state.folderSelectionMode)
        assertTrue(state.selectedLibraryFolders.isEmpty())
    }

    @Test
    fun explicitCustomModePreservesIntentionalEmptySelection() {
        val state = decodeAppPreferences(
            mutablePreferencesOf(
                stringPreferencesKey("folder_selection_mode") to "CUSTOM",
                stringSetPreferencesKey("selected_folders") to emptySet()
            )
        )

        assertEquals(FolderSelectionMode.CUSTOM, state.folderSelectionMode)
        assertTrue(state.selectedLibraryFolders.isEmpty())
    }

    @Test
    fun storedDefaultModeAloneKeepsAnEmptyAllFolderPreference() {
        val state = decodeAppPreferences(
            mutablePreferencesOf(
                stringPreferencesKey("folder_selection_mode") to "ALL"
            )
        )

        assertEquals(FolderSelectionMode.ALL, state.folderSelectionMode)
        assertTrue(state.selectedLibraryFolders.isEmpty())
    }

    @Test
    fun automaticOffloadPreferenceDecodesWithoutChangingOtherSettings() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("audio_offload_preference") to "AUTOMATIC",
            stringPreferencesKey("selected_player_theme") to PlayerTheme.RETRO_RACK.id
        )

        val state = decodeAppPreferences(preferences)

        assertEquals(AudioOffloadPreference.AUTOMATIC, state.audioOffloadPreference)
        assertEquals(PlayerTheme.RETRO_RACK, state.selectedPlayerTheme)
        assertEquals(ReplayGainMode.OFF, state.replayGainMode)
    }

    @Test
    fun smoothPlayPauseCanBePersistedDisabled() {
        val state = decodeAppPreferences(
            mutablePreferencesOf(
                booleanPreferencesKey("smooth_play_pause_enabled") to false
            )
        )

        assertFalse(state.smoothPlayPauseEnabled)
    }

    @Test
    fun crossfadePreferencesDecodeAndClampDuration() {
        val enabled = decodeAppPreferences(
            mutablePreferencesOf(
                booleanPreferencesKey("crossfade_enabled") to true,
                intPreferencesKey("crossfade_duration_ms") to 12_000,
                booleanPreferencesKey("preserve_album_transitions") to false
            )
        )
        assertTrue(enabled.crossfadeEnabled)
        assertEquals(12_000, enabled.crossfadeDurationMs)
        assertFalse(enabled.preserveAlbumTransitions)

        assertEquals(
            1_000,
            decodeAppPreferences(
                mutablePreferencesOf(
                    intPreferencesKey("crossfade_duration_ms") to -500
                )
            ).crossfadeDurationMs
        )
        assertEquals(
            12_000,
            decodeAppPreferences(
                mutablePreferencesOf(
                    intPreferencesKey("crossfade_duration_ms") to 99_000
                )
            ).crossfadeDurationMs
        )
    }

    @Test
    fun equalizerDefaultsAndStoredNumericBandsDecodeInStableOrder() {
        val defaults = decodeAppPreferences(
            mutablePreferencesOf()
        ).equalizerPreferences
        assertTrue(!defaults.enabled)
        assertEquals(0.0, defaults.preampDb, 0.0)
        assertTrue(defaults.automaticHeadroomEnabled)
        assertEquals(List(10) { 0.0 }, defaults.bandGainsDb)

        val stored = mutablePreferencesOf(
            booleanPreferencesKey("equalizer_enabled") to true,
            doublePreferencesKey("equalizer_preamp_db") to -2.26,
            booleanPreferencesKey(
                "equalizer_automatic_headroom"
            ) to false,
            *Array(10) { index ->
                doublePreferencesKey(
                    "equalizer_band_${index}_db"
                ) to index / 10.0
            }
        )
        val equalizer = decodeAppPreferences(stored)
            .equalizerPreferences

        assertTrue(equalizer.enabled)
        assertEquals(-2.3, equalizer.preampDb, 0.0)
        assertTrue(!equalizer.automaticHeadroomEnabled)
        assertEquals(
            List(10) { index -> index / 10.0 },
            equalizer.bandGainsDb
        )
    }
    @Test
    fun homeCustomizationDefaultsToVisibleRecentlyAddedAndNoPins() {
        val state = decodeAppPreferences(mutablePreferencesOf())

        assertTrue(state.homePins.isEmpty())
        assertTrue(state.showRecentlyAddedOnHome)
    }

    @Test
    fun notCountedPresentationDefaultsToHiddenAndDecodesStoredVisibility() {
        assertFalse(decodeAppPreferences(mutablePreferencesOf()).showNotCountedPlays)

        val visible = decodeAppPreferences(
            mutablePreferencesOf(
                booleanPreferencesKey("show_not_counted_plays") to true
            )
        )

        assertTrue(visible.showNotCountedPlays)
    }

    @Test
    fun homePinsDecodeInOrderAreCappedAtFourAndRecentlyAddedCanBeHidden() {
        val pins = (1..5).map { index ->
            HomePin(
                id = "pin-$index",
                type = HomePinType.ALBUM,
                title = "Album $index",
                subtitle = "Artist $index",
                anchor = SongReference(
                    relativePath = "Music/Album$index/",
                    displayName = "track$index.flac"
                )
            )
        }
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("home_pins_json") to Json.encodeToString(pins),
            booleanPreferencesKey("show_recently_added_on_home") to false
        )

        val state = decodeAppPreferences(preferences)

        assertEquals(listOf("pin-1", "pin-2", "pin-3", "pin-4"), state.homePins.map { it.id })
        assertFalse(state.showRecentlyAddedOnHome)
    }

}
