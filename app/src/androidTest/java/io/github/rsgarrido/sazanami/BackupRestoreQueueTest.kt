package io.github.rsgarrido.sazanami

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.FavoritesRepository
import io.github.rsgarrido.sazanami.data.ListeningHistoryRepository
import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.PlaylistsRepository
import io.github.rsgarrido.sazanami.data.backup.AppBackup
import io.github.rsgarrido.sazanami.data.backup.BackupListeningHistorySummary
import io.github.rsgarrido.sazanami.data.backup.BackupListeningHistoryV2
import io.github.rsgarrido.sazanami.data.backup.BackupListeningTrackIdentity
import io.github.rsgarrido.sazanami.data.backup.BackupRepository
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreQueueTest {
    private lateinit var database: AppDatabase
    private lateinit var queues: PlaybackQueueRepository
    private lateinit var backups: BackupRepository
    private lateinit var preferencesJob: Job
    private lateinit var preferencesFile: File

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val preferencesFileName = "backup_restore_queue_${System.nanoTime()}.preferences_pb"
        preferencesFile = context.preferencesDataStoreFile(preferencesFileName)
        preferencesJob = SupervisorJob()
        val preferences = AppPreferencesRepository.create(
            context = context,
            scope = CoroutineScope(preferencesJob + Dispatchers.IO),
            dataStoreFileName = preferencesFileName,
            legacyStores = emptyList()
        )
        withTimeout(5_000) { preferences.awaitLoadedState() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        queues = PlaybackQueueRepository(database)
        backups = BackupRepository(
            context = context,
            favoritesRepository = FavoritesRepository(database.favoriteSongDao()),
            playlistsRepository = PlaylistsRepository(database.playlistDao()),
            listeningHistoryRepository = ListeningHistoryRepository(database.songPlayStatsDao()),
            appDatabase = database,
            appPreferencesRepository = preferences
        )
    }

    @After
    fun tearDown() = runBlocking {
        try {
            if (::database.isInitialized) database.close()
        } finally {
            if (::preferencesJob.isInitialized) preferencesJob.cancelAndJoin()
            if (::preferencesFile.isInitialized) {
                check(!preferencesFile.exists() || preferencesFile.delete()) {
                    "Could not delete the test-owned preferences file"
                }
            }
        }
    }

    @Test
    fun restoreReplacesHistoryAndClearsActiveAndNamedQueues() = runBlocking {
        seedQueues()

        backups.restoreBackup(replacementBackup())

        assertTrue(queues.listQueues().isEmpty())
        assertNull(queues.getActiveQueueId())
        assertTrue(database.playbackQueueDao().getEntriesInPlaybackOrder("active").isEmpty())
        assertTrue(database.playbackQueueDao().getEntriesInPlaybackOrder("named").isEmpty())
        assertEquals(listOf("Restored"), database.listeningTrackIdentityDao().getAll().map { it.titleSnapshot })
    }

    @Test
    fun failedHistoryWriteRollsBackQueueCleanup() = runBlocking {
        seedQueues()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_backup_identity BEFORE INSERT ON listening_track_identities " +
                "BEGIN SELECT RAISE(ABORT, 'forced restore failure'); END"
        )

        val restore = runCatching { backups.restoreBackup(replacementBackup()) }
        assertTrue(restore.isFailure)
        assertTrue(
            "Restore must fail at the forced history write",
            generateSequence(restore.exceptionOrNull()) { it.cause }.any {
                it.message?.contains("forced restore failure") == true
            }
        )

        assertEquals(setOf("active", "named"), queues.listQueues().map { it.queueId }.toSet())
        assertEquals("active", queues.getActiveQueueId())
        assertEquals(1, queues.loadQueue("active")?.entries?.size)
        assertEquals(1, queues.loadQueue("named")?.entries?.size)
        assertEquals(listOf("Before"), database.listeningTrackIdentityDao().getAll().map { it.titleSnapshot })
    }

    private suspend fun seedQueues() {
        val identityId = database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = "Before", artistSnapshot = "Artist", albumSnapshot = "Album",
                albumArtistSnapshot = null, durationMsSnapshot = 1_000,
                normalizedTitle = "before", normalizedArtist = "artist", normalizedAlbum = "album",
                metadataKey = null, metadataKeyVersion = 1, createdAt = 1, updatedAt = 1
            )
        )
        queues.createQueue(
            queueId = "active", displayName = "Current",
            entries = listOf(PlaybackQueueEntryDraft(identityId, baseOrder = 0, playbackOrder = 0))
        )
        queues.createQueue(
            queueId = "named", displayName = "Saved",
            entries = listOf(PlaybackQueueEntryDraft(identityId, baseOrder = 0, playbackOrder = 0))
        )
        queues.setActiveQueue("active")
    }

    private fun replacementBackup() = AppBackup(
        createdAt = 2,
        canonicalListeningHistory = BackupListeningHistoryV2(
            identities = listOf(
                BackupListeningTrackIdentity(
                    backupIdentityId = 1, titleSnapshot = "Restored", artistSnapshot = "Artist",
                    albumSnapshot = "Album", albumArtistSnapshot = null, durationMsSnapshot = 1_000,
                    normalizedTitle = "restored", normalizedArtist = "artist", normalizedAlbum = "album",
                    metadataKey = null, metadataKeyVersion = 1, createdAt = 2, updatedAt = 2
                )
            ),
            summary = BackupListeningHistorySummary(identityCount = 1)
        )
    )
}
