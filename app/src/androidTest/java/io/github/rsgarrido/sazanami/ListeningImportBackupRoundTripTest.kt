package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupRepository
import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.importing.ListeningImportFingerprint
import io.github.rsgarrido.sazanami.data.importing.ListeningImportSelectionPlanner
import io.github.rsgarrido.sazanami.data.local.*
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

    @Test fun backup9_excludesPendingEventsEvidenceBatchesAndObservations() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity("mixed-state"))
        val sourceId = database.listeningImportSourceDao().insert(source("mixed-profile"))
        val publishedBatch = database.listeningImportBatchDao().insert(batch(sourceId, "published"))
        val pendingBatch = database.listeningImportBatchDao().insert(
            batch(sourceId, "pending").copy(
                status = ListeningImportBatchStatus.PENDING,
                completedAt = null
            )
        )
        val publishedEvent = database.listeningEventDao().insert(event(identityId, "published-event"))
        val pendingEvent = database.listeningEventDao().insert(
            event(identityId, "pending-event").copy(
                publicationState = ListeningEventPublicationState.IMPORT_PENDING
            )
        )
        database.importedListeningEventEvidenceDao().insert(
            listOf(
                ImportedListeningEventEvidenceEntity(publishedEvent, sourceId, 1, "published", 0,
                    null, null, ImportedListeningSkippedState.FALSE, ImportedListeningMatchDisposition.EXACT),
                ImportedListeningEventEvidenceEntity(pendingEvent, sourceId, 1, "pending", 0,
                    null, null, ImportedListeningSkippedState.FALSE, ImportedListeningMatchDisposition.EXACT)
            )
        )
        database.listeningImportBatchEventDao().insert(
            listOf(
                ListeningImportBatchEventEntity(publishedBatch, publishedEvent),
                ListeningImportBatchEventEntity(pendingBatch, publishedEvent),
                ListeningImportBatchEventEntity(pendingBatch, pendingEvent)
            )
        )
        val pendingOnlyIdentity = database.listeningTrackIdentityDao().insert(identity("pending-only"))
        val pendingOnlySource = database.listeningImportSourceDao().insert(
            source("pending-only-profile").copy(accountIdentityDigest = "sha256:pending-only")
        )
        val pendingOnlyBatch = database.listeningImportBatchDao().insert(
            batch(pendingOnlySource, "pending-only-batch").copy(
                status = ListeningImportBatchStatus.PENDING,
                completedAt = null
            )
        )
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(
                trackIdentityId = pendingOnlyIdentity,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                externalId = "pending-only-catalog-id",
                createdAt = 1,
                lastSeenAt = 2
            )
        )
        val pendingOnlyEvent = database.listeningEventDao().insert(
            event(pendingOnlyIdentity, "pending-only-event").copy(
                publicationState = ListeningEventPublicationState.IMPORT_PENDING
            )
        )
        database.importedListeningEventEvidenceDao().insert(
            ImportedListeningEventEvidenceEntity(
                pendingOnlyEvent, pendingOnlySource, 1, "pending-only", 0,
                null, null, ImportedListeningSkippedState.UNKNOWN,
                ImportedListeningMatchDisposition.CREATED_HISTORICAL_IDENTITY
            )
        )
        database.listeningImportBatchEventDao().insert(
            ListeningImportBatchEventEntity(pendingOnlyBatch, pendingOnlyEvent)
        )

        val backup = repository.export()

        assertEquals(listOf("mixed-state"), backup.identities.map { it.titleSnapshot })
        assertEquals(listOf("published-event"), backup.events.map { it.eventUuid })
        assertEquals(listOf("mixed-profile"), backup.importSources.map { it.stableUuid })
        assertEquals(listOf("published"), backup.importBatches.map { it.stableUuid })
        assertEquals(emptyList<String>(), backup.externalTrackIds.map { it.externalId })
        assertEquals(listOf("published"), backup.importedEventEvidence.map { it.fingerprint })
        assertEquals(
            listOf("published-event"),
            backup.batchEventObservations.map { it.eventUuid }
        )
    }

    @Test fun backup9_restoredFingerprintOrdinalIsAlreadyImported() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity("dedupe"))
        val sourceId = database.listeningImportSourceDao().insert(source("dedupe-profile"))
        val batchId = database.listeningImportBatchDao().insert(batch(sourceId, "dedupe-batch"))
        val eventId = database.listeningEventDao().insert(event(identityId, "dedupe-event"))
        val fingerprint = "d".repeat(64)
        database.importedListeningEventEvidenceDao().insert(
            ImportedListeningEventEvidenceEntity(
                eventId, sourceId, 1, fingerprint, 0, null, "trackdone",
                ImportedListeningSkippedState.FALSE, ImportedListeningMatchDisposition.EXACT
            )
        )
        database.listeningImportBatchEventDao().insert(ListeningImportBatchEventEntity(batchId, eventId))
        val backup = repository.export()

        repository.restore(backup)

        val restoredProfile = database.listeningImportSourceDao().getByStableUuid("dedupe-profile")!!
        val selection = ListeningImportSelectionPlanner().plan(
            listOf(sequenceOf(ListeningImportFingerprint(1, fingerprint)))
        )
        val dedupe = ListeningImportRepository(database).planDedupe(restoredProfile.id, selection)
        assertEquals(0, dedupe.newOccurrences)
        assertEquals(1, dedupe.alreadyImportedOccurrences)
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
