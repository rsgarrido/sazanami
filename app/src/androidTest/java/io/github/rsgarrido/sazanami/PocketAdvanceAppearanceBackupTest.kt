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
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.backup.AppBackupJson
import io.github.rsgarrido.sazanami.data.backup.BackupRepository
import io.github.rsgarrido.sazanami.data.backup.toPocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesState
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
class PocketAdvanceAppearanceBackupTest {
    private lateinit var database: AppDatabase
    private lateinit var preferences: AppPreferencesRepository
    private lateinit var backups: BackupRepository
    private lateinit var preferencesJob: Job
    private lateinit var preferencesFile: File
    private val colors = PlayerThemeTokenOverrides(shellColor = Color(0xFF123456), accentColor = Color(0xFF456789))
    private val custom = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
        PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.DEEP_NAVY)
    private val appearanceFields = listOf("pocketAdvanceConsoleFinishId", "pocketAdvanceLcdPaletteId", "pocketAdvanceControlsFinishId")

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val filename = "pocket_advance_appearance_backup_" + System.nanoTime() + ".preferences_pb"
        preferencesFile = context.preferencesDataStoreFile(filename)
        preferencesJob = SupervisorJob()
        preferences = AppPreferencesRepository.create(context, CoroutineScope(preferencesJob + Dispatchers.IO), filename, emptyList())
        withTimeout(5_000) { preferences.awaitLoadedState() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        backups = BackupRepository(context, FavoritesRepository(database.favoriteSongDao()),
            PlaylistsRepository(database.playlistDao()), ListeningHistoryRepository(database.songPlayStatsDao()), database, preferences)
        preferences.setSelectedPlayerTheme(PlayerTheme.CLASSIC_WHEEL)
        preferences.setThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL, colors)
        setCustomAppearance()
        awaitState { it.pocketAdvanceAppearance == custom && it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL &&
            it.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL] == colors }
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
    fun actualExportAndRestorePreserveAllAppearanceIdsWithEitherEnablementChoice() = runBlocking {
        for (selection in TabletLandscapeTheme.entries) {
            preferences.setTabletLandscapeTheme(selection)
            awaitState { it.tabletLandscapeTheme == selection }
            val exported = backups.createBackup()
            assertEquals(custom.consoleFinish.id, exported.preferences.pocketAdvanceConsoleFinishId)
            assertEquals(custom.lcdPalette.id, exported.preferences.pocketAdvanceLcdPaletteId)
            assertEquals(custom.controlsFinish.id, exported.preferences.pocketAdvanceControlsFinishId)
            val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(exported))
            assertEquals(custom, decoded.preferences.toPocketAdvanceAppearance())
            preferences.resetPocketAdvanceAppearance()
            preferences.setSelectedPlayerTheme(PlayerTheme.POCKET_CASSETTE)
            preferences.clearThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL)
            val other = if (selection == TabletLandscapeTheme.POCKET_ADVANCE) TabletLandscapeTheme.NORMAL_PLAYER else TabletLandscapeTheme.POCKET_ADVANCE
            preferences.setTabletLandscapeTheme(other)
            awaitState { it.pocketAdvanceAppearance == PocketAdvanceAppearance.Default && it.tabletLandscapeTheme == other &&
                it.selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE && it.playerThemeTokenOverrides.isEmpty() }
            backups.restoreBackup(decoded)
            val restored = awaitState { it.pocketAdvanceAppearance == custom && it.tabletLandscapeTheme == selection &&
                it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL && it.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL] == colors }
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }

    @Test
    fun preAdvanceAndSession4BBackupsRestoreOriginalAppearanceWithTheirOwnEnablement() = runBlocking {
        val encoded = AppBackupJson.encodeBackup(backups.createBackup())
        for (hasLandscapeSelection in listOf(false, true)) {
            val json = JSONObject(encoded)
            val fields = json.getJSONObject("preferences")
            appearanceFields.forEach { fields.remove(it) }
            if (hasLandscapeSelection) fields.put("tabletLandscapeThemeId", TabletLandscapeTheme.POCKET_ADVANCE.id)
            else fields.remove("tabletLandscapeThemeId")
            val decoded = AppBackupJson.decodeBackup(json.toString())
            setCustomAppearance()
            val expected = if (hasLandscapeSelection) TabletLandscapeTheme.POCKET_ADVANCE else TabletLandscapeTheme.NORMAL_PLAYER
            preferences.setTabletLandscapeTheme(if (hasLandscapeSelection) TabletLandscapeTheme.NORMAL_PLAYER else TabletLandscapeTheme.POCKET_ADVANCE)
            awaitState { it.pocketAdvanceAppearance == custom && it.tabletLandscapeTheme != expected }
            backups.restoreBackup(decoded)
            val restored = awaitState { it.pocketAdvanceAppearance == PocketAdvanceAppearance.Default && it.tabletLandscapeTheme == expected }
            assertEquals(PlayerTheme.CLASSIC_WHEEL, restored.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }

    @Test
    fun invalidImportedCategoryUsesOnlyItsOwnOriginalDefault() = runBlocking {
        val encoded = AppBackupJson.encodeBackup(backups.createBackup())
        val expected = listOf(custom.copy(consoleFinish = PocketAdvanceConsoleFinish.ORIGINAL_PURPLE),
            custom.copy(lcdPalette = PocketAdvanceLcdPalette.ORIGINAL), custom.copy(controlsFinish = PocketAdvanceControlsFinish.ORIGINAL_DARK))
        for ((index, field) in appearanceFields.withIndex()) {
            val json = JSONObject(encoded)
            json.getJSONObject("preferences").put(field, "future_preset")
            setCustomAppearance()
            awaitState { it.pocketAdvanceAppearance == custom }
            backups.restoreBackup(AppBackupJson.decodeBackup(json.toString()))
            val restored = awaitState { it.pocketAdvanceAppearance == expected[index] }
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, restored.tabletLandscapeTheme)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, restored.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }

    private suspend fun setCustomAppearance() {
        preferences.setPocketAdvanceConsoleFinish(custom.consoleFinish)
        preferences.setPocketAdvanceLcdPalette(custom.lcdPalette)
        preferences.setPocketAdvanceControlsFinish(custom.controlsFinish)
    }
    private suspend fun awaitState(predicate: (AppPreferencesState) -> Boolean): AppPreferencesState =
        withTimeout(5_000) { preferences.state.first { it.isLoaded && predicate(it) } }
}
