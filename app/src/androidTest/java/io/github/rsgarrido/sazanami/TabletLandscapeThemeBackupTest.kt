package io.github.rsgarrido.sazanami

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.FavoritesRepository
import io.github.rsgarrido.sazanami.data.ListeningHistoryRepository
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PlaylistsRepository
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.backup.AppBackupJson
import io.github.rsgarrido.sazanami.data.backup.BackupRepository
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabletLandscapeThemeBackupTest {
    private lateinit var database: AppDatabase
    private lateinit var preferences: AppPreferencesRepository
    private lateinit var backups: BackupRepository
    private lateinit var preferencesJob: Job
    private lateinit var preferencesFile: File
    private val colors = PlayerThemeTokenOverrides(shellColor = Color(0xFF123456), accentColor = Color(0xFF456789))

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val filename = "tablet_landscape_backup_" + System.nanoTime() + ".preferences_pb"
        preferencesFile = context.preferencesDataStoreFile(filename)
        preferencesJob = SupervisorJob()
        preferences = AppPreferencesRepository.create(context, CoroutineScope(preferencesJob + Dispatchers.IO), filename, emptyList())
        withTimeout(5_000) { preferences.awaitLoadedState() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        backups = BackupRepository(context, FavoritesRepository(database.favoriteSongDao()),
            PlaylistsRepository(database.playlistDao()), ListeningHistoryRepository(database.songPlayStatsDao()),
            database, preferences)
        preferences.setSelectedPlayerTheme(PlayerTheme.CLASSIC_WHEEL)
        preferences.setThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL, colors)
        withTimeout(5_000) { preferences.state.first {
            it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL && it.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL] == colors
        } }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        try { if (::database.isInitialized) database.close() }
        finally {
            if (::preferencesJob.isInitialized) preferencesJob.cancelAndJoin()
            if (::preferencesFile.isInitialized) preferencesFile.delete()
        }
    }

    @Test
    fun actualExportAndImportRoundTripEachLandscapeChoiceAlongsideNormalThemeColors() = runBlocking {
        for (selection in TabletLandscapeTheme.entries) {
            preferences.setTabletLandscapeTheme(selection)
            withTimeout(5_000) { preferences.state.first { it.tabletLandscapeTheme == selection } }
            val exported = backups.createBackup()
            assertEquals(selection.id, exported.preferences.tabletLandscapeThemeId)
            assertEquals(PlayerTheme.CLASSIC_WHEEL.id, exported.preferences.selectedPlayerThemeId)
            val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(exported))
            preferences.setSelectedPlayerTheme(PlayerTheme.POCKET_CASSETTE)
            preferences.clearThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL)
            val other = if (selection == TabletLandscapeTheme.POCKET_ADVANCE) TabletLandscapeTheme.NORMAL_PLAYER else TabletLandscapeTheme.POCKET_ADVANCE
            preferences.setTabletLandscapeTheme(other)
            withTimeout(5_000) { preferences.state.first {
                it.selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE && it.tabletLandscapeTheme == other && it.playerThemeTokenOverrides.isEmpty()
            } }
            backups.restoreBackup(decoded)
            val restored = withTimeout(5_000) { preferences.state.first {
                it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL && it.tabletLandscapeTheme == selection &&
                    it.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL] == colors
            } }
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }

    @Test
    fun missingOrUnknownBackupFieldRestoresNormalPlayerWithoutLosingPrimaryThemeColors() = runBlocking {
        val encoded = AppBackupJson.encodeBackup(backups.createBackup())
        for (unknown in listOf(null, "future_theme")) {
            val json = JSONObject(encoded)
            val fields = json.getJSONObject("preferences")
            if (unknown == null) fields.remove("tabletLandscapeThemeId") else fields.put("tabletLandscapeThemeId", unknown)
            val decoded = AppBackupJson.decodeBackup(json.toString())
            preferences.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_ADVANCE)
            withTimeout(5_000) { preferences.state.first { it.tabletLandscapeTheme == TabletLandscapeTheme.POCKET_ADVANCE } }
            backups.restoreBackup(decoded)
            val restored = withTimeout(5_000) { preferences.state.first { it.tabletLandscapeTheme == TabletLandscapeTheme.NORMAL_PLAYER } }
            assertEquals(PlayerTheme.CLASSIC_WHEEL, restored.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }
}
