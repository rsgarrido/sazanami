package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportInvariantTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningImportRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningImportRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun supportedCreationRejectsNativeImportOwnershipAndMismatchedBatchPolicy() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        assertRejected {
            repository.createSourceProfile(source(ListeningSource.CDPLAYA, "native-profile"))
        }
        assertRejected {
            repository.insertExternalId(
                ListeningTrackExternalIdEntity(
                    trackIdentityId = identityId, sourceType = ListeningSource.CDPLAYA,
                    externalId = "not-external", createdAt = 1, lastSeenAt = 1
                )
            )
        }
        val spotifyId = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "spotify"))
        assertRejected {
            repository.createBatch(
                batch(spotifyId, "wrong-policy").copy(
                    qualificationPolicy = ListeningQualificationPolicy.LASTFM
                )
            )
        }
    }

    @Test fun observationAndEvidenceRejectCrossProviderAndNativeEvents() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val spotifyId = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "spotify"))
        val otherSpotifyId = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "other-spotify"))
        val lastfmId = repository.createSourceProfile(source(ListeningSource.LASTFM_IMPORT, "lastfm"))
        val batchId = repository.createBatch(batch(spotifyId, "spotify-batch"))
        val spotifyEventId = repository.insertEvent(importedEvent(identityId, "spotify-event"))
        val lastfmEventId = repository.insertEvent(
            importedEvent(identityId, "lastfm-event").copy(
                source = ListeningSource.LASTFM_IMPORT,
                qualificationPolicy = ListeningQualificationPolicy.LASTFM
            )
        )
        val nativeEventId = database.listeningEventDao().insert(nativeEvent(identityId))

        assertRejected { repository.observeEvent(batchId, lastfmEventId) }
        assertRejected { repository.observeEvent(batchId, nativeEventId) }
        assertRejected { repository.insertEvidence(evidence(spotifyEventId, lastfmId, "wrong-profile")) }

        val observedBeforeEvidence = repository.insertEvent(importedEvent(identityId, "profile-pinned"))
        repository.observeEvent(batchId, observedBeforeEvidence)
        val otherProfileBatch = repository.createBatch(batch(otherSpotifyId, "other-profile-batch"))
        assertRejected { repository.observeEvent(otherProfileBatch, observedBeforeEvidence) }
        assertRejected {
            repository.insertEvidence(evidence(observedBeforeEvidence, otherSpotifyId, "other-account"))
        }

        repository.insertEvidence(evidence(spotifyEventId, spotifyId, "correct-profile"))
        repository.observeEvent(batchId, spotifyEventId)
        assertEquals(2L, database.listeningImportBatchEventDao().countForBatch(batchId))
    }

    @Test fun publicationAggregateValidationRejectsPersistedCrossSourceAndCrossProfileRows() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val spotifyId = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "spotify"))
        val lastfmId = repository.createSourceProfile(source(ListeningSource.LASTFM_IMPORT, "lastfm"))

        val sourceBatchId = repository.createBatch(batch(spotifyId, "source-mismatch"))
        val lastfmEventId = repository.insertEvent(
            importedEvent(identityId, "lastfm-pending").copy(
                source = ListeningSource.LASTFM_IMPORT,
                qualificationPolicy = ListeningQualificationPolicy.LASTFM
            )
        )
        database.listeningImportBatchEventDao().insert(
            ListeningImportBatchEventEntity(sourceBatchId, lastfmEventId)
        )
        assertRejected { repository.publishBatch(sourceBatchId, 1, 1, 10) }

        val evidenceBatchId = repository.createBatch(batch(spotifyId, "evidence-mismatch"))
        val spotifyEventId = repository.insertEvent(importedEvent(identityId, "spotify-pending"))
        database.importedListeningEventEvidenceDao().insert(
            evidence(spotifyEventId, lastfmId, "foreign-evidence")
        )
        database.listeningImportBatchEventDao().insert(
            ListeningImportBatchEventEntity(evidenceBatchId, spotifyEventId)
        )
        assertRejected { repository.publishBatch(evidenceBatchId, 1, 1, 10) }
        assertEquals(
            ListeningEventPublicationState.IMPORT_PENDING,
            database.listeningEventDao().getById(spotifyEventId)?.publicationState
        )
    }

    @Test fun constraintsScopeExternalIdsAndFingerprintOrdinalsByProviderAndProfile() = runBlocking {
        val firstIdentity = database.listeningTrackIdentityDao().insert(identity())
        val secondIdentity = database.listeningTrackIdentityDao().insert(identity().copy(metadataKey = "second"))
        repository.insertExternalId(external(firstIdentity, ListeningSource.SPOTIFY_IMPORT, "same"))
        assertRejected {
            repository.insertExternalId(external(secondIdentity, ListeningSource.SPOTIFY_IMPORT, "same"))
        }
        repository.insertExternalId(external(secondIdentity, ListeningSource.LASTFM_IMPORT, "same"))

        val firstProfile = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "profile-one"))
        val secondProfile = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "profile-two"))
        val events = (0 until 4).map { index ->
            repository.insertEvent(importedEvent(firstIdentity, "fingerprint-$index"))
        }
        repository.insertEvidence(evidence(events[0], firstProfile, "hash", ordinal = 0))
        assertRejected { repository.insertEvidence(evidence(events[1], firstProfile, "hash", ordinal = 0)) }
        repository.insertEvidence(evidence(events[1], firstProfile, "hash", ordinal = 1))
        repository.insertEvidence(evidence(events[2], secondProfile, "hash", ordinal = 0))
        assertNotNull(repository.findEvidence(firstProfile, 1, "hash", 1))
        assertNotNull(repository.findEvidence(secondProfile, 1, "hash", 0))
    }

    @Test fun cancellingSharedPendingEventKeepsItUntilFinalObservationIsCancelled() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val sourceId = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "spotify"))
        val eventId = repository.insertEvent(importedEvent(identityId, "shared-pending"))
        repository.insertEvidence(evidence(eventId, sourceId, "shared"))
        val firstBatch = repository.createBatch(batch(sourceId, "first"))
        val secondBatch = repository.createBatch(batch(sourceId, "second"))
        repository.observeEvent(firstBatch, eventId)
        repository.observeEvent(secondBatch, eventId)

        assertEquals(0, repository.cancelPendingBatch(firstBatch, 10))
        assertNotNull(database.listeningEventDao().getById(eventId))
        assertNotNull(database.importedListeningEventEvidenceDao().getByEventId(eventId))
        assertEquals(0L, database.listeningImportBatchEventDao().countForBatch(firstBatch))
        assertEquals(1L, database.listeningImportBatchEventDao().countForBatch(secondBatch))

        assertEquals(1, repository.cancelPendingBatch(secondBatch, 11))
        assertNull(database.listeningEventDao().getById(eventId))
        assertNull(database.importedListeningEventEvidenceDao().getByEventId(eventId))
    }

    @Test fun cleanupAfterPriorEventDeletionRemovesOrphanMappingButPreservesRatedIdentity() = runBlocking {
        val orphanIdentity = database.listeningTrackIdentityDao().insert(
            identity().copy(metadataKey = "partial-orphan")
        )
        repository.insertExternalId(external(orphanIdentity, ListeningSource.SPOTIFY_IMPORT, "orphan"))
        val ratedIdentity = database.listeningTrackIdentityDao().insert(
            identity().copy(metadataKey = "rated-survivor")
        )
        database.songRatingDao().upsert(SongRatingEntity(ratedIdentity, 5, 1, 1))
        val sourceId = repository.createSourceProfile(source(ListeningSource.SPOTIFY_IMPORT, "spotify"))
        val batchId = repository.createBatch(batch(sourceId, "partially-cleaned"))

        // The pending event/evidence/observation that originally referenced orphanIdentity was
        // already removed before process death, leaving only its external mapping behind.
        assertEquals(0, repository.cancelPendingBatch(batchId, 10))

        assertNull(database.listeningTrackIdentityDao().getById(orphanIdentity))
        assertNull(repository.findExternalId(ListeningSource.SPOTIFY_IMPORT, "orphan"))
        assertNotNull(database.listeningTrackIdentityDao().getById(ratedIdentity))
        assertEquals(5, database.songRatingDao().getByTrackIdentityId(ratedIdentity)?.rating)
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.isFailure)
    }

    private fun identity() = ListeningTrackIdentityEntity(
        titleSnapshot = "Track", artistSnapshot = "Artist", albumSnapshot = "Album",
        albumArtistSnapshot = null, durationMsSnapshot = 1_000, normalizedTitle = "track",
        normalizedArtist = "artist", normalizedAlbum = "album", metadataKey = "identity",
        metadataKeyVersion = 1, createdAt = 1, updatedAt = 1
    )

    private fun source(type: ListeningSource, uuid: String) = ListeningImportSourceEntity(
        stableUuid = uuid, sourceType = type, displayLabel = "Imported history",
        accountIdentityDigest = null, createdAt = 1, updatedAt = 1
    )

    private fun batch(sourceId: Long, uuid: String) = ListeningImportBatchEntity(
        stableUuid = uuid, sourceProfileId = sourceId, status = ListeningImportBatchStatus.PENDING,
        parserVersion = 1, qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
        qualificationRuleVersion = 1, startedAt = 1, completedAt = null,
        sourceRangeStart = 1_000, sourceRangeEnd = 1_000, createdAppVersion = "test"
    )

    private fun importedEvent(identityId: Long, uuid: String) = ListeningEventEntity(
        eventUuid = uuid, source = ListeningSource.SPOTIFY_IMPORT, trackIdentityId = identityId,
        localTrackBindingId = null, playbackSessionId = null, startedAt = null, endedAt = 1_000,
        attributionAt = 1_000, timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = 500, trackDurationMs = 1_000, qualifiedAsPlay = true,
        qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
        qualificationRuleVersion = 1, qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
        endReason = null, completionClassification = ListeningCompletionClassification.NONE,
        publicationState = ListeningEventPublicationState.IMPORT_PENDING, sourceEventKey = null,
        importBatchId = null, createdAt = 1_001
    )

    private fun nativeEvent(identityId: Long) = ListeningEventEntity(
        eventUuid = "native", source = ListeningSource.CDPLAYA, trackIdentityId = identityId,
        localTrackBindingId = null, playbackSessionId = "native-session", startedAt = 1,
        endedAt = 2, listenedMs = 1, trackDurationMs = 1_000, qualifiedAsPlay = false,
        qualificationReason = ListeningQualificationReason.NONE, qualificationRuleVersion = 1,
        endReason = ListeningEndReason.STOPPED, sourceEventKey = null, importBatchId = null,
        createdAt = 2
    )

    private fun evidence(eventId: Long, sourceId: Long, fingerprint: String, ordinal: Int = 0) =
        ImportedListeningEventEvidenceEntity(
            eventId, sourceId, 1, fingerprint, ordinal, null, null,
            ImportedListeningSkippedState.FALSE, ImportedListeningMatchDisposition.EXACT
        )

    private fun external(identityId: Long, source: ListeningSource, value: String) =
        ListeningTrackExternalIdEntity(trackIdentityId = identityId,
            sourceType = source, externalId = value, createdAt = 1, lastSeenAt = 1)
}
