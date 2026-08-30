package io.github.rsgarrido.sazanami.data.preferences

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.FolderSelection
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesMigrationTest {
    @Test
    fun confirmingInitialEmptyFolderSelectionPersistsCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.nanoTime().toString()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = AppPreferencesRepository.create(
            context = context,
            scope = scope,
            dataStoreFileName = "initial_folder_selection_$suffix.preferences_pb",
            legacyStores = emptyList()
        )

        repository.completeInitialLibraryFolderSelection(
            FolderSelection(FolderSelectionMode.CUSTOM, emptySet())
        )
        val confirmed = withTimeout(5_000) {
            repository.state.firstMatching { it.initialLibraryFolderSelectionCompleted }
        }

        assertTrue(confirmed.initialLibraryFolderSelectionCompleted)
        assertEquals(FolderSelectionMode.CUSTOM, confirmed.folderSelectionMode)
        assertTrue(confirmed.selectedLibraryFolders.isEmpty())
        scope.cancel()
    }

    @Test
    fun existingDataStoreValueWinsOverLegacyValue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storeName = "existing_value_wins_${System.nanoTime()}"
        context.getSharedPreferences(storeName, Context.MODE_PRIVATE).edit()
            .putString("selected_player_theme", PlayerTheme.RETRO_RACK.id)
            .commit()
        val key = stringPreferencesKey("selected_player_theme")
        val currentData = mutablePreferencesOf(key to PlayerTheme.POCKET_FLIP.id)

        val migrated = SharedPreferencesMigration(context, storeName).migrate(currentData)

        assertEquals(PlayerTheme.POCKET_FLIP.id, migrated[key])
    }

    @Test
    fun legacyStoresMigrateAndDataStoreBecomesTheSingleSourceOfTruth() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.nanoTime().toString()
        val dataStoreFile = "app_preferences_migration_test_$suffix.preferences_pb"
        val legacyStores = legacyStoreNames("migration_$suffix")
        seedLegacyPreferences(context, legacyStores)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = AppPreferencesRepository.create(
            context = context,
            scope = firstScope,
            dataStoreFileName = dataStoreFile,
            legacyStores = legacyStores
        )

        val migrated = withTimeout(5_000) { repository.awaitLoadedState() }
        assertEquals(PlayerTheme.RETRO_RACK, migrated.selectedPlayerTheme)
        assertEquals(ModernArtworkTransitionStyle.SLIDE, migrated.modernArtworkTransitionStyle)
        assertEquals(ModernSeekbarStyle.THICK_CAPSULE, migrated.modernSeekbarStyle)
        assertEquals(ReplayGainMode.TRACK, migrated.replayGainMode)
        assertEquals(AudioOffloadPreference.DISABLED, migrated.audioOffloadPreference)
        assertEquals(setOf("Music", "Podcasts"), migrated.selectedLibraryFolders)
        assertTrue(migrated.initialLibraryFolderSelectionCompleted)
        assertEquals(LibraryViewMode.GRID, migrated.songsViewMode)
        assertEquals(4, migrated.songsGridColumnCount)
        assertEquals(
            Color(0xFF123456.toInt()),
            migrated.playerThemeTokenOverrides[PlayerTheme.RETRO_RACK]?.accentColor
        )

        repository.setSelectedPlayerTheme(PlayerTheme.POCKET_FLIP)
        repository.setLibraryView(LibraryViewCategory.SONGS, LibraryViewMode.GRID, 99)
        repository.setThemeTokenOverrides(
            PlayerTheme.RETRO_RACK,
            PlayerThemeTokenOverrides(accentColor = Color(0xFFA1B2C3.toInt()))
        )
        repeat(20) { index ->
            repository.setModernSeekbarStyle(
                if (index == 19) ModernSeekbarStyle.SEGMENTED else ModernSeekbarStyle.SLIM_LINE
            )
            repository.setAudioOffloadPreference(
                if (index == 19) {
                    AudioOffloadPreference.AUTOMATIC
                } else {
                    AudioOffloadPreference.DISABLED
                }
            )
        }
        val updated = withTimeout(5_000) {
            repository.state.firstMatching {
                it.selectedPlayerTheme == PlayerTheme.POCKET_FLIP &&
                    it.modernSeekbarStyle == ModernSeekbarStyle.SEGMENTED &&
                    it.audioOffloadPreference == AudioOffloadPreference.AUTOMATIC
            }
        }
        assertEquals(2, updated.songsGridColumnCount)
        assertEquals(
            Color(0xFFA1B2C3.toInt()),
            updated.playerThemeTokenOverrides[PlayerTheme.RETRO_RACK]?.accentColor
        )
        firstScope.cancel()
    }

    private fun seedLegacyPreferences(context: Context, stores: List<String>) {
        context.getSharedPreferences(stores[0], Context.MODE_PRIVATE).edit()
            .putString("selected_player_theme", PlayerTheme.RETRO_RACK.id).commit()
        context.getSharedPreferences(stores[1], Context.MODE_PRIVATE).edit()
            .putString("artwork_transition_style", "parallax")
            .putString("seekbar_style", "thick_capsule").commit()
        context.getSharedPreferences(stores[2], Context.MODE_PRIVATE).edit()
            .putString("replay_gain_mode", ReplayGainMode.TRACK.name).commit()
        context.getSharedPreferences(stores[3], Context.MODE_PRIVATE).edit()
            .putStringSet("selected_folders", setOf("Music", "Podcasts")).commit()
        context.getSharedPreferences(stores[4], Context.MODE_PRIVATE).edit()
            .putString("songs_view_mode", "grid")
            .putInt("songs_view_mode_columns", 4).commit()
        context.getSharedPreferences(stores[5], Context.MODE_PRIVATE).edit()
            .putString("retro_rack.accent", "#FF123456").commit()
    }

    private fun legacyStoreNames(prefix: String): List<String> = listOf(
        "${prefix}_player_theme",
        "${prefix}_modern_player",
        "${prefix}_replay_gain",
        "${prefix}_library",
        "${prefix}_library_view",
        "${prefix}_theme_tokens"
    )
}

private suspend fun kotlinx.coroutines.flow.StateFlow<AppPreferencesState>.firstMatching(
    predicate: (AppPreferencesState) -> Boolean
): AppPreferencesState = first(predicate)
