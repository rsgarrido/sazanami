package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningIdentityReconciliationCandidateService
import com.example.cdplaya.data.ListeningIdentityReconciliationLinkResult
import com.example.cdplaya.data.ListeningIdentityReconciliationRepository
import com.example.cdplaya.data.ReconciliationCandidateCategory
import com.example.cdplaya.data.ReconciliationCandidateDisposition
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningIdentityReconciliationEntity
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningTrackExternalIdEntity
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningIdentityReconciliationCandidateServiceTest {
    private lateinit var database: AppDatabase
    private lateinit var service: ListeningIdentityReconciliationCandidateService
    private lateinit var reconciliation: ListeningIdentityReconciliationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        service = ListeningIdentityReconciliationCandidateService(database)
        reconciliation = ListeningIdentityReconciliationRepository(database) { 900L }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun sourceQueryReturnsOnlyUnmatchedPublishedImportedIdentitiesAndGroupsMetrics() = runBlocking {
        val reviewable = identity("Reviewable", "Artist", "Album")
        importedEvent(reviewable, 1, publication = ListeningEventPublicationState.IMPORT_PUBLISHED,
            qualified = true, listenedMs = 100, at = 1_000,
            completion = ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL)
        importedEvent(reviewable, 2, publication = ListeningEventPublicationState.IMPORT_PUBLISHED,
            qualified = false, listenedMs = 250, at = 2_000)
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(
                trackIdentityId = reviewable,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                externalId = "spotify:reviewable",
                createdAt = 1,
                lastSeenAt = 2
            )
        )

        val pendingOnly = identity("Pending", "Artist", "Album")
        importedEvent(pendingOnly, 3, publication = ListeningEventPublicationState.IMPORT_PENDING)
        val nativeOnly = identity("Native", "Artist", "Album")
        nativeEvent(nativeOnly, 4)
        val boundHistorical = identity("Bound historical", "Artist", "Album")
        importedEvent(boundHistorical, 5)
        binding(boundHistorical, missingSince = null)
        val linked = identity("Linked", "Artist", "Album")
        importedEvent(linked, 6)
        val linkedTarget = local("Linked target", "Artist", "Album")
        assertTrue(reconciliation.link(linked, linkedTarget) is ListeningIdentityReconciliationLinkResult.Linked)

        val rows = database.listeningIdentityReconciliationCandidateDao()
            .getReviewableHistoricalSources()

        assertEquals(listOf(reviewable), rows.map { it.identityId })
        val row = rows.single()
        assertEquals(2, row.importedEventCount)
        assertEquals(1, row.qualifiedPlayCount)
        assertEquals(350, row.recordedListeningMs)
        assertEquals(1, row.completedCount)
        assertEquals(1_000, row.firstListenedAt)
        assertEquals(2_000, row.lastListenedAt)
        assertEquals(1, row.externalIdCount)
        assertEquals("spotify_import", row.providerStorageValues)
    }

    @Test
    fun targetQueryReturnsOneActiveBoundProjectionAndNeverHistoricalOrUnavailableTargets() =
        runBlocking {
            val active = identity("Active", "Artist", "Album")
            val firstBinding = binding(active, missingSince = null, suffix = "first")
            binding(active, missingSince = null, suffix = "second")
            val unavailable = identity("Unavailable", "Artist", "Album")
            binding(unavailable, missingSince = 500, suffix = "missing")
            val historical = identity("Historical", "Artist", "Album")
            importedEvent(historical, 1)

            val rows = database.listeningIdentityReconciliationCandidateDao()
                .getEligibleLocalTargets()

            assertEquals(listOf(active), rows.map { it.identityId })
            val row = rows.single()
            assertEquals(firstBinding, row.localBindingId)
            assertEquals("Active.flac", row.displayName)
            assertEquals("Music/Fictional/", row.relativePath)
            assertEquals(180_000L, row.durationMs)
        }

    @Test
    fun confirmedLinkRemovesSourceAndUnlinkMakesItDiscoverableAgain() = runBlocking {
        val source = identity("It's Me", "Artist", "Album")
        importedEvent(source, 1)
        val target = local("It’s Me", "Artist", "Album")

        val before = service.discoverCandidates()
        assertEquals(ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT,
            before.items.single().candidates.single().evidence.category)

        assertTrue(reconciliation.link(source, target) is ListeningIdentityReconciliationLinkResult.Linked)
        assertTrue(service.discoverCandidates().items.isEmpty())

        assertTrue(reconciliation.unlink(source))
        assertEquals(source, service.discoverCandidates().items.single().source.identityId)
    }

    @Test
    fun severalHistoricalSourcesSuggestSameTargetWithoutMergingOrMutatingPersistence() = runBlocking {
        val sourceA = identity("50Mila", "Nina Fiction", "Nina Fiction")
        val sourceB = identity("50Mila", "Nina Fiction", "Nina Fiction")
        importedEvent(sourceA, 1)
        importedEvent(sourceB, 2)
        val target = local("50Mila", "Nina Fiction", "Nina Fiction")
        val providerA = identity("Revenge of B", "Fictional Band", "Album")
        val providerB = identity("Revenge of B", "Fictional Band", "Album")
        importedEvent(providerA, 3)
        importedEvent(providerB, 4)
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(
                trackIdentityId = providerA,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                externalId = "spotify:provider-a",
                createdAt = 1,
                lastSeenAt = 1
            )
        )
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(
                trackIdentityId = providerB,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                externalId = "spotify:provider-b",
                createdAt = 1,
                lastSeenAt = 1
            )
        )
        val providerTarget = local("Revenge of B", "Fictional Band", "Album")
        val identitiesBefore = database.listeningTrackIdentityDao().getAll()
        val eventCountBefore = database.listeningEventDao().count()
        val tableCountsBefore = protectedTableCounts()

        val result = service.discoverCandidates()

        assertEquals(setOf(sourceA, sourceB, providerA, providerB),
            result.items.map { it.source.identityId }.toSet())
        assertEquals(setOf(target), result.items.filter { it.source.identityId in setOf(sourceA, sourceB) }.map {
            it.candidates.single().target.identityId
        }.toSet())
        assertEquals(setOf(providerTarget), result.items.filter {
            it.source.identityId in setOf(providerA, providerB)
        }.map { it.candidates.single().target.identityId }.toSet())
        assertTrue(result.items.filter { it.source.identityId in setOf(providerA, providerB) }
            .all { it.source.hasStableExternalId })
        assertTrue(result.items.filter { it.source.identityId in setOf(sourceA, sourceB) }
            .none { it.source.hasStableExternalId })
        assertTrue(result.items.all {
            it.disposition == ReconciliationCandidateDisposition.SUGGESTED
        })
        assertTrue(reconciliation.listLinks().isEmpty())
        assertEquals(eventCountBefore, database.listeningEventDao().count())
        assertEquals(identitiesBefore, database.listeningTrackIdentityDao().getAll())
        assertEquals(tableCountsBefore, protectedTableCounts())
    }

    @Test
    fun reconciledSourceIsExcludedEvenWhenTargetLaterBecomesUnavailable() = runBlocking {
        val source = identity("Source", "Artist", "Album")
        importedEvent(source, 1)
        val target = local("Source", "Artist", "Album")
        database.listeningIdentityReconciliationDao().insert(
            ListeningIdentityReconciliationEntity(source, target, 1)
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE local_track_bindings SET missingSince = 99 WHERE trackIdentityId = ?",
            arrayOf(target)
        )

        val result = service.discoverCandidates()

        assertTrue(result.items.isEmpty())
        assertFalse(database.listeningIdentityReconciliationDao().getAll().isEmpty())
    }

    private suspend fun identity(title: String, artist: String, album: String): Long =
        database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = title,
                artistSnapshot = artist,
                albumSnapshot = album,
                albumArtistSnapshot = null,
                durationMsSnapshot = 180_000,
                normalizedTitle = title.lowercase(),
                normalizedArtist = artist.lowercase(),
                normalizedAlbum = album.lowercase(),
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1,
                updatedAt = 1
            )
        )

    private suspend fun local(title: String, artist: String, album: String): Long {
        val id = identity(title, artist, album)
        binding(id, missingSince = null)
        return id
    }

    private suspend fun binding(
        identityId: Long,
        missingSince: Long?,
        suffix: String = identityId.toString()
    ): Long = database.localTrackBindingDao().insert(
        LocalTrackBindingEntity(
            trackIdentityId = identityId,
            referenceKey = "local-$identityId-$suffix",
            mediaStoreId = identityId,
            volumeName = "external",
            contentUri = "content://fictional/$identityId/$suffix",
            relativePath = "Music/Fictional/",
            displayName = "${database.listeningTrackIdentityDao().getById(identityId)!!.titleSnapshot}.flac",
            absolutePath = null,
            fileSizeBytes = 1_000,
            dateModifiedEpochSeconds = 1,
            durationMsSnapshot = 180_000,
            legacyStableKey = null,
            portableKey = "portable-$identityId-$suffix",
            portableKeyVersion = 1,
            firstSeenAt = 1,
            lastSeenAt = 2,
            missingSince = missingSince
        )
    )

    private suspend fun importedEvent(
        identityId: Long,
        ordinal: Int,
        publication: ListeningEventPublicationState = ListeningEventPublicationState.IMPORT_PUBLISHED,
        qualified: Boolean = true,
        listenedMs: Long = 60_000,
        at: Long = 1_000L + ordinal,
        completion: ListeningCompletionClassification = ListeningCompletionClassification.NONE
    ) = database.listeningEventDao().insert(
        ListeningEventEntity(
            eventUuid = "import-$identityId-$ordinal",
            source = ListeningSource.SPOTIFY_IMPORT,
            trackIdentityId = identityId,
            localTrackBindingId = null,
            playbackSessionId = null,
            startedAt = null,
            endedAt = at,
            attributionAt = at,
            timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
            listenedMs = listenedMs,
            trackDurationMs = null,
            qualifiedAsPlay = qualified,
            qualificationReason = if (qualified) ListeningQualificationReason.TIME_THRESHOLD
                else ListeningQualificationReason.NONE,
            qualificationRuleVersion = 1,
            qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
            endReason = null,
            completionClassification = completion,
            publicationState = publication,
            sourceEventKey = "source-$identityId-$ordinal",
            importBatchId = null,
            createdAt = at
        )
    )

    private suspend fun nativeEvent(identityId: Long, ordinal: Int) =
        database.listeningEventDao().insert(
            ListeningEventEntity(
                eventUuid = "native-$identityId-$ordinal",
                source = ListeningSource.CDPLAYA,
                trackIdentityId = identityId,
                localTrackBindingId = null,
                playbackSessionId = "session-$identityId-$ordinal",
                startedAt = 1_000,
                endedAt = 2_000,
                listenedMs = 1_000,
                trackDurationMs = 180_000,
                qualifiedAsPlay = false,
                qualificationReason = ListeningQualificationReason.NONE,
                qualificationRuleVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.CDPLAYA,
                endReason = null,
                completionClassification = ListeningCompletionClassification.NONE,
                publicationState = ListeningEventPublicationState.NATIVE,
                sourceEventKey = null,
                importBatchId = null,
                createdAt = 2_000
            )
        )

    private fun protectedTableCounts() = listOf(
        count("listening_track_identities"),
        count("local_track_bindings"),
        count("listening_events"),
        count("listening_identity_reconciliations"),
        count("song_ratings"),
        count("listening_track_external_ids"),
        count("imported_listening_event_evidence")
    )

    private fun count(table: String): Long = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
