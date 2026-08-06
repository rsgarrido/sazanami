package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.backup.ListeningHistoryBackupRepository
import com.example.cdplaya.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportBackupRoundTripTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningHistoryBackupRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        repository = ListeningHistoryBackupRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun backup9_roundTripsPublishedImportDeduplicationStateAndReplacesLaterRows() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity("backed"))
        val sourceId = database.listeningImportSourceDao().insert(source("profile-backed"))
        val batchId = database.listeningImportBatchDao().insert(batch(sourceId, "batch-backed"))
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(trackIdentityId=identityId, sourceType=ListeningSource.SPOTIFY_IMPORT,
                externalId="catalog-id", createdAt=1, lastSeenAt=2))
        val eventId = database.listeningEventDao().insert(event(identityId, "event-backed"))
        database.importedListeningEventEvidenceDao().insert(
            ImportedListeningEventEvidenceEntity(eventId, sourceId, 2, "fingerprint", 3,
                "reason-start", "reason-end", ImportedListeningSkippedState.FALSE,
                ImportedListeningMatchDisposition.EXACT))
        database.listeningImportBatchEventDao().insert(ListeningImportBatchEventEntity(batchId, eventId))
        val backup = repository.export()

        database.listeningTrackIdentityDao().insert(identity("post-backup"))
        repository.restore(backup)
        val restored = repository.export()

        assertEquals(1, restored.identities.size)
        assertEquals("backed", restored.identities.single().titleSnapshot)
        assertEquals("profile-backed", restored.importSources.single().stableUuid)
        assertEquals("batch-backed", restored.importBatches.single().stableUuid)
        assertEquals("catalog-id", restored.externalTrackIds.single().externalId)
        assertEquals("fingerprint", restored.importedEventEvidence.single().fingerprint)
        assertEquals(3, restored.importedEventEvidence.single().duplicateOrdinal)
        assertEquals("event-backed", restored.batchEventObservations.single().eventUuid)
        assertEquals(1_000L, restored.events.single().attributionAt)
        assertEquals("source_end_only", restored.events.single().timestampEvidence)
    }

    private fun identity(title: String) = ListeningTrackIdentityEntity(
        titleSnapshot=title, artistSnapshot="Artist", albumSnapshot="Album", albumArtistSnapshot=null,
        durationMsSnapshot=1_000, normalizedTitle=title, normalizedArtist="artist", normalizedAlbum="album",
        metadataKey=null, metadataKeyVersion=1, createdAt=1, updatedAt=1)

    private fun source(uuid: String) = ListeningImportSourceEntity(
        stableUuid=uuid, sourceType=ListeningSource.SPOTIFY_IMPORT, displayLabel="Imported history",
        accountIdentityDigest="sha256:test", createdAt=1, updatedAt=2)

    private fun batch(sourceId: Long, uuid: String) = ListeningImportBatchEntity(
        stableUuid=uuid, sourceProfileId=sourceId, status=ListeningImportBatchStatus.PUBLISHED,
        parserVersion=2, qualificationPolicy=ListeningQualificationPolicy.SPOTIFY,
        qualificationRuleVersion=1, startedAt=1, completedAt=2, sourceRangeStart=1_000,
        sourceRangeEnd=1_000, parsedCount=1, insertedCount=1, exactMatchCount=1,
        qualifiedCount=1, createdAppVersion="test")

    private fun event(identityId: Long, uuid: String) = ListeningEventEntity(
        eventUuid=uuid, source=ListeningSource.SPOTIFY_IMPORT, trackIdentityId=identityId,
        localTrackBindingId=null, playbackSessionId=null, startedAt=null, endedAt=1_000,
        attributionAt=1_000, timestampEvidence=ListeningTimestampEvidence.SOURCE_END_ONLY,
        listenedMs=500, trackDurationMs=1_000, qualifiedAsPlay=true,
        qualificationReason=ListeningQualificationReason.TIME_THRESHOLD, qualificationRuleVersion=1,
        qualificationPolicy=ListeningQualificationPolicy.SPOTIFY, endReason=null,
        completionClassification=ListeningCompletionClassification.NONE,
        publicationState=ListeningEventPublicationState.IMPORT_PUBLISHED,
        sourceEventKey=null, importBatchId=null, createdAt=1_001)
}
