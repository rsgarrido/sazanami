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
import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.backup.AppBackupJson
import io.github.rsgarrido.sazanami.data.backup.BackupRepository
import io.github.rsgarrido.sazanami.data.backup.toPocketHorizonAppearance
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
class PocketHorizonAppearanceBackupTest {
    private lateinit var database: AppDatabase
    private lateinit var preferences: AppPreferencesRepository
    private lateinit var backups: BackupRepository
    private lateinit var preferencesJob: Job
    private lateinit var preferencesFile: File
    private val colors = PlayerThemeTokenOverrides(shellColor = Color(0xFF123456), accentColor = Color(0xFF456789))
    private val custom = PocketHorizonAppearance(PocketHorizonConsoleFinish.MIDNIGHT_BLUE,
        PocketHorizonLcdPalette.WARM_AMBER, PocketHorizonWaveTreatment.OFF)
    private val appearanceFields = listOf("pocketHorizonConsoleFinishId", "pocketHorizonLcdPaletteId", "pocketHorizonWaveTreatmentId")

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val filename = "pocket_horizon_appearance_backup_" + System.nanoTime() + ".preferences_pb"
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
        awaitState { it.pocketHorizonAppearance == custom && it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL &&
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
            assertEquals(custom.consoleFinish.id, exported.preferences.pocketHorizonConsoleFinishId)
            assertEquals(custom.lcdPalette.id, exported.preferences.pocketHorizonLcdPaletteId)
            assertEquals(custom.waveTreatment.id, exported.preferences.pocketHorizonWaveTreatmentId)
            val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(exported))
            assertEquals(custom, decoded.preferences.toPocketHorizonAppearance())
            preferences.resetPocketHorizonAppearance()
            preferences.setSelectedPlayerTheme(PlayerTheme.POCKET_CASSETTE)
            preferences.clearThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL)
            val other = if (selection == TabletLandscapeTheme.POCKET_HORIZON) TabletLandscapeTheme.NORMAL_PLAYER else TabletLandscapeTheme.POCKET_HORIZON
            preferences.setTabletLandscapeTheme(other)
            awaitState { it.pocketHorizonAppearance == PocketHorizonAppearance.Default && it.tabletLandscapeTheme == other &&
                it.selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE && it.playerThemeTokenOverrides.isEmpty() }
            backups.restoreBackup(decoded)
            val restored = awaitState { it.pocketHorizonAppearance == custom && it.tabletLandscapeTheme == selection &&
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
            if (hasLandscapeSelection) fields.put("tabletLandscapeThemeId", TabletLandscapeTheme.POCKET_HORIZON.id)
            else fields.remove("tabletLandscapeThemeId")
            val decoded = AppBackupJson.decodeBackup(json.toString())
            setCustomAppearance()
            val expected = if (hasLandscapeSelection) TabletLandscapeTheme.POCKET_HORIZON else TabletLandscapeTheme.NORMAL_PLAYER
            preferences.setTabletLandscapeTheme(if (hasLandscapeSelection) TabletLandscapeTheme.NORMAL_PLAYER else TabletLandscapeTheme.POCKET_HORIZON)
            awaitState { it.pocketHorizonAppearance == custom && it.tabletLandscapeTheme != expected }
            backups.restoreBackup(decoded)
            val restored = awaitState { it.pocketHorizonAppearance == PocketHorizonAppearance.Default && it.tabletLandscapeTheme == expected }
            assertEquals(PlayerTheme.CLASSIC_WHEEL, restored.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }

    @Test
    fun invalidImportedCategoryUsesOnlyItsOwnOriginalDefault() = runBlocking {
        val encoded = AppBackupJson.encodeBackup(backups.createBackup())
        val expected = listOf(custom.copy(consoleFinish = PocketHorizonConsoleFinish.GRAPHITE),
            custom.copy(lcdPalette = PocketHorizonLcdPalette.BLUE_VIOLET), custom.copy(waveTreatment = PocketHorizonWaveTreatment.STANDARD))
        for ((index, field) in appearanceFields.withIndex()) {
            val json = JSONObject(encoded)
            json.getJSONObject("preferences").put(field, "future_preset")
            setCustomAppearance()
            awaitState { it.pocketHorizonAppearance == custom }
            backups.restoreBackup(AppBackupJson.decodeBackup(json.toString()))
            val restored = awaitState { it.pocketHorizonAppearance == expected[index] }
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, restored.tabletLandscapeTheme)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, restored.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), restored.playerThemeTokenOverrides)
        }
    }

    private suspend fun setCustomAppearance() {
        preferences.setPocketHorizonConsoleFinish(custom.consoleFinish)
        preferences.setPocketHorizonLcdPalette(custom.lcdPalette)
        preferences.setPocketHorizonWaveTreatment(custom.waveTreatment)
    }
    private suspend fun awaitState(predicate: (AppPreferencesState) -> Boolean): AppPreferencesState =
        withTimeout(5_000) { preferences.state.first { it.isLoaded && predicate(it) } }
}
