package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.importing.ImportOccurrenceKey
import com.example.cdplaya.data.importing.ListeningImportFingerprint
import com.example.cdplaya.data.importing.ListeningImportSelectionPlanner
import com.example.cdplaya.data.importing.spotify.SpotifyImportSourceProfileService
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ImportedListeningEventEvidenceEntity
import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningImportSourceEntity
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportDedupeLookupTest {
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

    @Test fun defaultSpotifyProfileIsLocallyStableAndUnscoped() = runBlocking {
        var now = 10L
        val service = SpotifyImportSourceProfileService(repository) { now }
        val first = service.getOrCreateDefault()
        now = 20L
        val second = service.getOrCreateDefault()
        assertEquals(first.id, second.id)
        assertEquals(ListeningSource.SPOTIFY_IMPORT, first.sourceType)
        assertEquals(SpotifyImportSourceProfileService.DEFAULT_STABLE_UUID, first.stableUuid)
        assertNull(first.accountIdentityDigest)
        assertEquals(10, second.updatedAt)
    }

    @Test fun persistedLookupUsesMultiplicityAndIsolatesSourceProfiles() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val profileA = repository.createSourceProfile(source("profile-a"))
        val profileB = repository.createSourceProfile(source("profile-b"))
        seedEvidence(identityId, profileA, key('a', 0), "a0")
        seedEvidence(identityId, profileA, key('a', 1), "a1")
        seedEvidence(identityId, profileA, key('b', 0), "b0")
        seedEvidence(identityId, profileB, key('a', 0), "other-a0")
        val selection = selection(listOf('a', 'a', 'a', 'b', 'c'))

        val resultA = repository.planDedupe(profileA, selection)
        val resultB = repository.planDedupe(profileB, selection)
        assertEquals(3, resultA.alreadyImportedOccurrences)
        assertEquals(2, resultA.newOccurrences)
        assertEquals(1, resultB.alreadyImportedOccurrences)
        assertEquals(4, resultB.newOccurrences)
    }

    @Test fun boundedRoomLookupHandlesMoreThanSqliteVariableLimitWithoutPerRecordQueries() = runBlocking {
        val profile = repository.createSourceProfile(source("large-profile"))
        val fingerprints = (0 until 1_201).map { index ->
            ListeningImportFingerprint(1, index.toString(16).padStart(64, '0'))
        }
        val selection = ListeningImportSelectionPlanner().plan(listOf(fingerprints.asSequence()))
        val result = repository.planDedupe(profile, selection)
        assertEquals(1_201, result.newOccurrences)
        assertEquals(0, result.alreadyImportedOccurrences)
    }

    @Test fun lookupQueryPlanUsesExistingCompositeUniqueIndex() {
        val cursor = database.openHelper.readableDatabase.query(
            """EXPLAIN QUERY PLAN
                SELECT fingerprintVersion, fingerprint, duplicateOrdinal
                FROM imported_listening_event_evidence
                WHERE sourceProfileId = ? AND fingerprintVersion = ? AND fingerprint IN (?, ?)""",
            arrayOf<Any>(1L, 1, "a", "b")
        )
        val details = buildList {
            cursor.use {
                val detailColumn = it.getColumnIndexOrThrow("detail")
                while (it.moveToNext()) add(it.getString(detailColumn))
            }
        }.joinToString(" ")
        assertTrue(details, details.contains("index_imported_listening_event_evidence_"))
        assertTrue(details, details.contains("sourceProfileId=?"))
        assertTrue(details, details.contains("fingerprintVersion=?"))
    }

    @Test fun externalIdBatchLookupUsesExistingCompositeUniqueIndex() {
        val cursor = database.openHelper.readableDatabase.query(
            """EXPLAIN QUERY PLAN
                SELECT * FROM listening_track_external_ids
                WHERE sourceType = ? AND externalId IN (?, ?)""",
            arrayOf<Any>(ListeningSource.SPOTIFY_IMPORT.storageValue, "a", "b")
        )
        val details = buildList {
            cursor.use {
                val detailColumn = it.getColumnIndexOrThrow("detail")
                while (it.moveToNext()) add(it.getString(detailColumn))
            }
        }.joinToString(" ")
        assertTrue(details, details.contains("index_listening_track_external_ids_sourceType_externalId"))
        assertTrue(details, details.contains("sourceType=?"))
        assertTrue(details, details.contains("externalId=?"))
    }

    private suspend fun seedEvidence(
        identityId: Long,
        profileId: Long,
        key: ImportOccurrenceKey,
        uuid: String
    ) {
        val eventId = database.listeningEventDao().insert(event(identityId, uuid))
        database.importedListeningEventEvidenceDao().insert(
            ImportedListeningEventEvidenceEntity(
                eventId = eventId,
                sourceProfileId = profileId,
                fingerprintVersion = key.fingerprintVersion,
                fingerprint = key.fingerprint,
                duplicateOrdinal = key.duplicateOrdinal,
                normalizedReasonStart = null,
                normalizedReasonEnd = null,
                skippedState = ImportedListeningSkippedState.UNKNOWN,
                matchDispositionAtImport = ImportedListeningMatchDisposition.EXACT
            )
        )
    }

    private fun selection(values: List<Char>) = ListeningImportSelectionPlanner().plan(
        listOf(values.asSequence().map { ListeningImportFingerprint(1, it.toString().repeat(64)) })
    )

    private fun key(value: Char, ordinal: Int) =
        ImportOccurrenceKey(1, value.toString().repeat(64), ordinal)

    private fun identity() = ListeningTrackIdentityEntity(
        titleSnapshot = "Track", artistSnapshot = "Artist", albumSnapshot = "Album",
        albumArtistSnapshot = null, durationMsSnapshot = 1_000, normalizedTitle = "track",
        normalizedArtist = "artist", normalizedAlbum = "album", metadataKey = null,
        metadataKeyVersion = 1, createdAt = 1, updatedAt = 1
    )

    private fun source(uuid: String) = ListeningImportSourceEntity(
        stableUuid = uuid, sourceType = ListeningSource.SPOTIFY_IMPORT,
        displayLabel = "Spotify", accountIdentityDigest = null, createdAt = 1, updatedAt = 1
    )

    private fun event(identityId: Long, uuid: String) = ListeningEventEntity(
        eventUuid = uuid, source = ListeningSource.SPOTIFY_IMPORT, trackIdentityId = identityId,
        localTrackBindingId = null, playbackSessionId = null, startedAt = null, endedAt = 1_000,
        attributionAt = 1_000, timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = 30_000, trackDurationMs = null, qualifiedAsPlay = true,
        qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
        qualificationRuleVersion = 1, qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
        endReason = null, completionClassification = ListeningCompletionClassification.NONE,
        publicationState = ListeningEventPublicationState.IMPORT_PUBLISHED,
        sourceEventKey = null, importBatchId = null, createdAt = 1_001
    )
}
