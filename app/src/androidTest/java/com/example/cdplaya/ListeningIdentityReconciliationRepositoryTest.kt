package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningIdentityReconciliationFailure
import com.example.cdplaya.data.ListeningIdentityReconciliationLinkResult
import com.example.cdplaya.data.ListeningIdentityReconciliationRatingState
import com.example.cdplaya.data.ListeningIdentityReconciliationRepository
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ImportedListeningEventEvidenceEntity
import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningTrackExternalIdEntity
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.SongRatingEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningIdentityReconciliationRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningIdentityReconciliationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningIdentityReconciliationRepository(database) { 500L }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun linkAndLinkMany_enforceEligibilityCardinalityAndRoleInvariants() = runBlocking {
        val sourceA = historical("Historical A")
        val sourceB = historical("Historical B")
        val localTarget = local("Local target", missingSince = null)
        val otherTarget = local("Other target", missingSince = null)

        assertLinked(repository.link(sourceA, localTarget))
        assertLinked(repository.link(sourceB, localTarget))
        assertEquals(listOf(sourceA, sourceB), repository.findSourcesForTarget(localTarget).map { it.sourceIdentityId })
        assertRejected(
            repository.link(sourceA, otherTarget),
            ListeningIdentityReconciliationFailure.SOURCE_ALREADY_RECONCILED
        )
        assertRejected(
            repository.link(localTarget, otherTarget),
            ListeningIdentityReconciliationFailure.SOURCE_IS_TARGET
        )
        assertRejected(
            repository.link(otherTarget, sourceA),
            ListeningIdentityReconciliationFailure.TARGET_IS_SOURCE
        )
        assertRejected(
            repository.link(otherTarget, otherTarget),
            ListeningIdentityReconciliationFailure.SAME_IDENTITY
        )
    }

    @Test
    fun invalidSourceAndTargetEvidence_areRejectedWithoutPartialBatchInsert() = runBlocking {
        val source = historical("Eligible")
        val noHistory = identity("No imported history")
        val locallyBoundSource = local("Bound source", null)
        val historicalTarget = historical("Historical target")
        val target = local("Target", null)

        assertRejected(
            repository.link(noHistory, target),
            ListeningIdentityReconciliationFailure.SOURCE_HAS_NO_IMPORTED_HISTORY
        )
        assertRejected(
            repository.link(locallyBoundSource, target),
            ListeningIdentityReconciliationFailure.SOURCE_HAS_LOCAL_BINDING
        )
        assertRejected(
            repository.link(source, historicalTarget),
            ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING
        )
        assertRejected(
            repository.linkMany(listOf(source, noHistory), target, 600L),
            ListeningIdentityReconciliationFailure.SOURCE_HAS_NO_IMPORTED_HISTORY
        )
        assertTrue(repository.listLinks().isEmpty())
        assertRejected(
            repository.linkMany(listOf(source, source), target),
            ListeningIdentityReconciliationFailure.DUPLICATE_SOURCE_ID
        )
    }

    @Test
    fun linkPersistsAcrossRepositoryRecreationAndMissingBindingState() = runBlocking {
        val source = historical("Source")
        val target = local("Target", missingSince = 400L)
        assertLinked(repository.link(source, target))

        val recreated = ListeningIdentityReconciliationRepository(database) { 999L }
        val link = recreated.findTargetForSource(source)

        assertNotNull(link)
        assertEquals(target, link?.targetIdentityId)
        assertEquals(500L, link?.reconciledAt)
    }

    @Test
    fun linkAndUnlink_areLosslessForRatingsMetadataAndProviderProvenance() = runBlocking {
        val source = historical("It’s Me")
        val target = local("It's Me", null)
        database.songRatingDao().upsert(SongRatingEntity(source, 3, 10L, 11L))
        database.songRatingDao().upsert(SongRatingEntity(target, 5, 20L, 21L))
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(
                trackIdentityId = source,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                externalId = "spotify:fictional",
                createdAt = 30L,
                lastSeenAt = 31L
            )
        )
        val eventId = database.listeningEventDao().getByIds(
            database.openHelper.writableDatabase.query(
                "SELECT id FROM listening_events WHERE trackIdentityId = ?",
                arrayOf(source)
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        ).single().id
        database.importedListeningEventEvidenceDao().insert(
            ImportedListeningEventEvidenceEntity(
                eventId = eventId,
                sourceProfileId = importSource(),
                fingerprintVersion = 1,
                fingerprint = "fictional-fingerprint",
                duplicateOrdinal = 0,
                normalizedReasonStart = null,
                normalizedReasonEnd = null,
                skippedState = ImportedListeningSkippedState.FALSE,
                matchDispositionAtImport = ImportedListeningMatchDisposition.CREATED_HISTORICAL_IDENTITY
            )
        )
        val beforeCounts = protectedCounts()

        assertLinked(repository.link(source, target, 700L))
        val ratings = repository.inspectRatings(source, target)
        assertEquals(ListeningIdentityReconciliationRatingState.CONFLICTING_RATINGS, ratings.state)
        assertEquals(
            source,
            database.listeningTrackExternalIdDao().find(
                ListeningSource.SPOTIFY_IMPORT,
                "spotify:fictional"
            )?.trackIdentityId
        )
        assertEquals(
            listOf(0),
            database.importedListeningEventEvidenceDao().findOccurrenceKeys(
                sourceProfileId = 1L,
                fingerprintVersion = 1,
                fingerprints = listOf("fictional-fingerprint")
            ).map { it.duplicateOrdinal }
        )
        assertTrue(repository.unlink(source))

        assertEquals(beforeCounts, protectedCounts())
        assertEquals(3, database.songRatingDao().getByTrackIdentityId(source)?.rating)
        assertEquals(5, database.songRatingDao().getByTrackIdentityId(target)?.rating)
        assertEquals("It’s Me", database.listeningTrackIdentityDao().getById(source)?.titleSnapshot)
        assertEquals("It's Me", database.listeningTrackIdentityDao().getById(target)?.titleSnapshot)
        assertEquals(
            source,
            database.listeningTrackExternalIdDao().find(
                ListeningSource.SPOTIFY_IMPORT,
                "spotify:fictional"
            )?.trackIdentityId
        )
        assertEquals(
            eventId,
            database.importedListeningEventEvidenceDao().find(
                sourceProfileId = 1L,
                fingerprintVersion = 1,
                fingerprint = "fictional-fingerprint",
                duplicateOrdinal = 0
            )?.eventId
        )
        assertNull(repository.findTargetForSource(source))
    }

    @Test
    fun cleanupAndForeignKeysProtectBothRolesUntilUnlinked() = runBlocking {
        val source = historical("Source")
        val target = local("Target", null)
        val unrelated = identity("Unrelated")
        assertLinked(repository.link(source, target))

        assertEquals(1, database.listeningTrackIdentityDao().deleteAllUnreferenced())
        assertNotNull(database.listeningTrackIdentityDao().getById(source))
        assertNotNull(database.listeningTrackIdentityDao().getById(target))
        assertNull(database.listeningTrackIdentityDao().getById(unrelated))
        assertDeleteRestricted(source)
        assertDeleteRestricted(target)

        assertTrue(repository.unlink(source))
        database.listeningEventDao().deleteAll()
        assertEquals(1, database.listeningTrackIdentityDao().deleteAllUnreferenced())
        assertNull(database.listeningTrackIdentityDao().getById(source))
        assertNotNull(database.listeningTrackIdentityDao().getById(target))
    }

    @Test
    fun concurrentAndBatchLinks_leaveOneDeterministicDurableState() = runBlocking {
        val contested = historical("Contested")
        val firstTarget = local("First", null)
        val secondTarget = local("Second", null)
        val results = listOf(
            async { repository.link(contested, firstTarget, 1L) },
            async { repository.link(contested, secondTarget, 2L) }
        ).awaitAll()
        assertEquals(1, results.count { it is ListeningIdentityReconciliationLinkResult.Linked })
        assertEquals(1, repository.listLinks().size)

        val fragmented = listOf(
            historical("Fragment A"),
            historical("Fragment B"),
            historical("Fragment C")
        )
        val batchTarget = local("Batch target", null)
        assertLinked(repository.linkMany(fragmented, batchTarget, 800L))
        assertEquals(fragmented.sorted(), repository.findSourcesForTarget(batchTarget).map { it.sourceIdentityId })
    }

    private suspend fun identity(title: String): Long =
        database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = title,
                artistSnapshot = "Fictional Artist",
                albumSnapshot = "Fictional Album",
                albumArtistSnapshot = null,
                durationMsSnapshot = 180_000L,
                normalizedTitle = title.lowercase(),
                normalizedArtist = "fictional artist",
                normalizedAlbum = "fictional album",
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

    private suspend fun historical(title: String): Long {
        val id = identity(title)
        database.listeningEventDao().insert(
            ListeningEventEntity(
                eventUuid = "event-$id",
                source = ListeningSource.SPOTIFY_IMPORT,
                trackIdentityId = id,
                localTrackBindingId = null,
                playbackSessionId = null,
                startedAt = 100L + id,
                endedAt = 200L + id,
                listenedMs = 100L,
                trackDurationMs = 180_000L,
                qualifiedAsPlay = true,
                qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
                qualificationRuleVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                endReason = null,
                completionClassification = ListeningCompletionClassification.NONE,
                publicationState = ListeningEventPublicationState.IMPORT_PUBLISHED,
                sourceEventKey = "source-$id",
                importBatchId = null,
                createdAt = 300L + id
            )
        )
        return id
    }

    private suspend fun local(title: String, missingSince: Long?): Long {
        val id = identity(title)
        database.localTrackBindingDao().insert(
            LocalTrackBindingEntity(
                trackIdentityId = id,
                referenceKey = "local-$id",
                mediaStoreId = id,
                volumeName = "external",
                contentUri = "content://fictional/$id",
                relativePath = "Music/Fictional/",
                displayName = "$title.flac",
                absolutePath = null,
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 1L,
                durationMsSnapshot = 180_000L,
                legacyStableKey = null,
                portableKey = "portable-$id",
                portableKeyVersion = 1,
                firstSeenAt = 1L,
                lastSeenAt = 2L,
                missingSince = missingSince
            )
        )
        return id
    }

    private suspend fun importSource(): Long {
        val existing = database.listeningImportSourceDao().getByStableUuid("source-profile")
        if (existing != null) return existing.id
        return database.listeningImportSourceDao().insert(
            com.example.cdplaya.data.local.ListeningImportSourceEntity(
                stableUuid = "source-profile",
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                displayLabel = "Fictional profile",
                accountIdentityDigest = null,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    private fun assertLinked(result: ListeningIdentityReconciliationLinkResult) {
        assertTrue(result is ListeningIdentityReconciliationLinkResult.Linked)
    }

    private fun assertRejected(
        result: ListeningIdentityReconciliationLinkResult,
        expected: ListeningIdentityReconciliationFailure
    ) {
        assertEquals(expected, (result as ListeningIdentityReconciliationLinkResult.Rejected).reason)
    }

    private fun protectedCounts(): List<Long> = listOf(
        count("listening_track_identities"),
        count("local_track_bindings"),
        count("listening_events"),
        count("song_ratings"),
        count("listening_track_external_ids"),
        count("imported_listening_event_evidence")
    )

    private fun count(table: String): Long = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun assertDeleteRestricted(identityId: Long) {
        val result = runCatching {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM listening_track_identities WHERE id = ?",
                arrayOf(identityId)
            )
        }
        assertTrue(result.isFailure)
    }
}
