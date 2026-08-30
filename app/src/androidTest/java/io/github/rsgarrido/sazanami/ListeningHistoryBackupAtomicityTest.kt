package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.backup.AppBackupJson
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupRepository
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningHistoryBackupAtomicityTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningHistoryBackupRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningHistoryBackupRepository(database)
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        database.listeningEventDao().insert(event(identityId))
        Unit
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun everyPreflightFailure_leavesExistingCanonicalHistoryUntouched() = runBlocking {
        val original = repository.export()
        val malformed = listOf(
            original.copy(identities = original.identities + original.identities.first()),
            original.copy(events = original.events.map { it.copy(trackIdentityBackupId = 999) }),
            original.copy(events = original.events + original.events.first()),
            original.copy(events = original.events.map { it.copy(listenedMs = -1) }),
            original.copy(events = original.events.map { it.copy(endedAt = requireNotNull(it.startedAt) - 1) }),
            original.copy(events = original.events.map { it.copy(source = "unsupported") }),
            original.copy(summary = original.summary.copy(eventCount = 99))
        )
        malformed.forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.restore(invalid) }
            }
            assertEquals(original.semanticSnapshot(), repository.export().semanticSnapshot())
        }
    }

    @Test
    fun malformedJsonAndUnsupportedTopLevelVersion_failBeforeDatabaseMutation() = runBlocking {
        val original = repository.export().semanticSnapshot()
        assertThrows(IllegalArgumentException::class.java) { AppBackupJson.decodeBackup("not-json") }
        assertThrows(IllegalArgumentException::class.java) {
            AppBackupJson.decodeBackup("{\"schemaVersion\":8,\"createdAt\":1}")
        }
        assertEquals(original, repository.export().semanticSnapshot())
    }

    private fun identity() = ListeningTrackIdentityEntity(
        titleSnapshot = "Existing", artistSnapshot = "Artist", albumSnapshot = "Album",
        albumArtistSnapshot = null, durationMsSnapshot = 1_000, normalizedTitle = "existing",
        normalizedArtist = "artist", normalizedAlbum = "album", metadataKey = "metadata",
        metadataKeyVersion = 1, createdAt = 1, updatedAt = 2
    )

    private fun event(identityId: Long) = ListeningEventEntity(
        eventUuid = "existing-event", source = ListeningSource.NATIVE,
        trackIdentityId = identityId, localTrackBindingId = null,
        playbackSessionId = "existing-session", startedAt = 10, endedAt = 20,
        listenedMs = 10, trackDurationMs = 1_000, qualifiedAsPlay = true,
        qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
        qualificationRuleVersion = 1, endReason = ListeningEndReason.STOPPED,
        sourceEventKey = null, importBatchId = null, createdAt = 21
    )
}

private fun io.github.rsgarrido.sazanami.data.backup.BackupListeningHistoryV2.semanticSnapshot() = copy(
    identities = identities.mapIndexed { index, identity -> identity.copy(backupIdentityId = index + 1L) },
    events = events.map { it.copy(trackIdentityBackupId = 1L) }
)
