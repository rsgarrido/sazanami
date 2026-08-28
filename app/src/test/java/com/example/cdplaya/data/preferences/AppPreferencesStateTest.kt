package com.example.cdplaya.data.preferences

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.SongReference
import com.example.cdplaya.data.home.HomePin
import com.example.cdplaya.data.home.HomePinType
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.ui.library.LibraryViewMode
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernBackgroundAppearance
import com.example.cdplaya.ui.player.modern.ModernBackgroundStyle
import com.example.cdplaya.ui.player.modern.ModernBlurStrength
import com.example.cdplaya.ui.player.modern.ModernDimmingStrength
import com.example.cdplaya.ui.player.modern.ModernPlayerAppearance
import com.example.cdplaya.ui.player.modern.ModernSeekbarAppearance
import com.example.cdplaya.ui.player.modern.ModernSeekbarColorMode
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.modern.ModernWaveformDensity
import com.example.cdplaya.ui.player.modern.ModernWaveformSize
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesStateTest {
    @Test
    fun invalidEnumsAndGridCountsFallBackSafely() {
        val preferences = mutablePreferencesOf(
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
            intPreferencesKey("songs_view_mode_columns") to 99
        )

        val state = decodeAppPreferences(preferences)

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
                dimmingStrength = ModernDimmingStrength.HIGH
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
