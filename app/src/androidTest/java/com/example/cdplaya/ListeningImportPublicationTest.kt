package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.AnalyticsBucketBoundary
import com.example.cdplaya.data.AnalyticsBucketGranularity
import java.time.ZonedDateTime
import com.example.cdplaya.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportPublicationTest {
    private lateinit var database: AppDatabase
    private lateinit var imports: ListeningImportRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        imports = ListeningImportRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun sourceEndOnlyPendingEvent_isInvisibleUntilAtomicPublication() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val sourceId = imports.createSourceProfile(source())
        val batchId = imports.createBatch(batch(sourceId, "batch-publish"))
        val eventId = database.listeningEventDao().insert(importedEvent(identityId, "pending"))
        imports.insertEvidence(evidence(eventId, sourceId, "fingerprint"))
        imports.observeEvent(batchId, eventId)
        val stats = ListeningStatsRepository(database)

        assertEquals(0L, stats.getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)
        imports.publishBatch(batchId, expectedPendingEventCount = 1, expectedObservedEventCount = 1, completedAt = 2_000)
        val overview = stats.getAllTimeOverview(includeLegacyBaseline = false)
        assertEquals(1L, overview.detailedEventCount)
        assertEquals(1L, overview.qualifiedDetailedPlayCount)
        assertEquals(1L, overview.naturalCompletionCount)
        val trend = database.listeningStatsDao().getTrendBuckets(
            ListeningStatsQueries.trend(
                listOf(AnalyticsBucketBoundary(0, 999, 1_001, ZonedDateTime.now(), AnalyticsBucketGranularity.DAY)),
                listOf(ListeningSource.SPOTIFY_IMPORT.storageValue)
            )
        )
        assertEquals(1L, trend.single().totalAttemptCount)
        val restored = database.listeningEventDao().getByUuid("pending")!!
        assertNull(restored.startedAt)
        assertEquals(1_000L, restored.attributionAt)
        assertEquals(ListeningEventPublicationState.IMPORT_PUBLISHED, restored.publicationState)
    }

    @Test fun failedExpectedCountRollsBackAndCancellationRemovesOnlyPendingEvent() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        database.songRatingDao().upsert(SongRatingEntity(identityId, 4, 1, 1))
        val sourceId = imports.createSourceProfile(source())
        val batchId = imports.createBatch(batch(sourceId, "batch-cancel"))
        val eventId = database.listeningEventDao().insert(importedEvent(identityId, "cancel-me"))
        imports.insertEvidence(evidence(eventId, sourceId, "cancel-fingerprint"))
        imports.observeEvent(batchId, eventId)

        runCatching { imports.publishBatch(batchId, 2, 1, 2_000) }.onSuccess { error("Expected rejection") }
        assertEquals(ListeningEventPublicationState.IMPORT_PENDING, database.listeningEventDao().getByUuid("cancel-me")!!.publicationState)
        assertEquals(1, imports.cancelPendingBatch(batchId, 2_100))
        assertNull(database.listeningEventDao().getByUuid("cancel-me"))
        assertEquals(4, database.songRatingDao().getByTrackIdentityId(identityId)?.rating)
    }

    @Test fun pendingBatchMayObserveSharedPublishedEventWithoutHidingOrDeletingIt() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val sourceId = imports.createSourceProfile(source())
        val eventId = database.listeningEventDao().insert(
            importedEvent(identityId, "already-published").copy(
                publicationState = ListeningEventPublicationState.IMPORT_PUBLISHED)
        )
        imports.insertEvidence(evidence(eventId, sourceId, "shared-fingerprint"))
        val publishBatch = imports.createBatch(batch(sourceId, "repeat-observation"))
        imports.observeEvent(publishBatch, eventId)

        assertEquals(0, imports.publishBatch(publishBatch, 0, 1, 3_000))
        assertEquals(ListeningEventPublicationState.IMPORT_PUBLISHED,
            database.listeningEventDao().getByUuid("already-published")?.publicationState)
    }

    private fun identity() = ListeningTrackIdentityEntity(
        titleSnapshot="Track", artistSnapshot="Artist", albumSnapshot="Album", albumArtistSnapshot=null,
        durationMsSnapshot=1_000, normalizedTitle="track", normalizedArtist="artist", normalizedAlbum="album",
        metadataKey=null, metadataKeyVersion=1, createdAt=1, updatedAt=1)

    private fun source() = ListeningImportSourceEntity(
        stableUuid="profile", sourceType=ListeningSource.SPOTIFY_IMPORT, displayLabel="Imported history",
        accountIdentityDigest=null, createdAt=1, updatedAt=1)

    private fun batch(sourceId: Long, uuid: String) = ListeningImportBatchEntity(
        stableUuid=uuid, sourceProfileId=sourceId, status=ListeningImportBatchStatus.PENDING,
        parserVersion=1, qualificationPolicy=ListeningQualificationPolicy.SPOTIFY,
        qualificationRuleVersion=1, startedAt=1, completedAt=null, sourceRangeStart=1_000,
        sourceRangeEnd=1_000, createdAppVersion="test")

    private fun importedEvent(identityId: Long, uuid: String) = ListeningEventEntity(
        eventUuid=uuid, source=ListeningSource.SPOTIFY_IMPORT, trackIdentityId=identityId,
        localTrackBindingId=null, playbackSessionId=null, startedAt=null, endedAt=1_000,
        attributionAt=1_000, timestampEvidence=ListeningTimestampEvidence.SOURCE_END_ONLY,
        listenedMs=500, trackDurationMs=1_000, qualifiedAsPlay=true,
        qualificationReason=ListeningQualificationReason.TIME_THRESHOLD, qualificationRuleVersion=1,
        qualificationPolicy=ListeningQualificationPolicy.SPOTIFY, endReason=null,
        completionClassification=ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL,
        publicationState=ListeningEventPublicationState.IMPORT_PENDING, sourceEventKey=null,
        importBatchId=null, createdAt=1_100)

    private fun evidence(eventId: Long, sourceId: Long, fingerprint: String) =
        ImportedListeningEventEvidenceEntity(eventId, sourceId, 1, fingerprint, 0, null, null,
            ImportedListeningSkippedState.FALSE, ImportedListeningMatchDisposition.EXACT)
}
