package io.github.rsgarrido.sazanami

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidateService
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationFailure
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationLinkResult
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRepository
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.controller.DefaultListeningHistoryReconciliationOperations
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState
import io.github.rsgarrido.sazanami.data.local.ListeningIdentityReconciliationEntity
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence
import io.github.rsgarrido.sazanami.data.local.ListeningTrackExternalIdEntity
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
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
    fun reconciliationOperationsUseCurrentSongMetadataAndIncludeEntirePlayableCatalogReadOnly() =
        runBlocking {
            val source = identity("Six Feet Deep", "The Warning", "Keep Me Fed")
            importedEvent(source, 1)
            val current = song(
                id = 101,
                title = "Six Feet Deep",
                artist = "The Warning",
                album = "Keep Me Fed",
                displayName = "01 Six Feet Deep.flac"
            )
            val staleIdentity = identity("01 Six Feet Deep", "<unknown>", "Keep Me Fed")
            database.localTrackBindingDao().insert(
                LocalTrackBindingEntity(
                    trackIdentityId = staleIdentity,
                    referenceKey = current.membershipKey(),
                    mediaStoreId = current.id,
                    volumeName = current.volumeName,
                    contentUri = current.uri.toString(),
                    relativePath = current.relativePath,
                    displayName = current.displayName,
                    absolutePath = null,
                    fileSizeBytes = current.fileSizeBytes,
                    dateModifiedEpochSeconds = current.dateModifiedEpochSeconds,
                    durationMsSnapshot = current.duration,
                    legacyStableKey = null,
                    portableKey = null,
                    portableKeyVersion = 1,
                    firstSeenAt = 1,
                    lastSeenAt = 1,
                    missingSince = null
                )
            )
            val neverPlayed = song(
                id = 202,
                title = "Escapism",
                artist = "The Warning",
                album = "Keep Me Fed",
                displayName = "02 Escapism.flac"
            )
            val unavailableIdentity = identity("Unavailable Snapshot", "Old Artist", "Old Album")
            binding(unavailableIdentity, missingSince = 99L, suffix = "unavailable")
            val identitiesBefore = database.listeningTrackIdentityDao().getAll()
            val bindingsBefore = database.localTrackBindingDao().getAllForBackup()

            val operations = DefaultListeningHistoryReconciliationOperations(
                database = database,
                currentSongs = { listOf(current, neverPlayed) }
            )
            val snapshot = operations.load()

            assertEquals(2, snapshot.localTargets.size)
            val authoritative = snapshot.localTargets.single {
                it.referenceKey == current.membershipKey()
            }
            assertEquals(staleIdentity, authoritative.identityId)
            assertEquals("Six Feet Deep", authoritative.title)
            assertEquals("The Warning", authoritative.artist)
            assertEquals("Keep Me Fed", authoritative.album)
            assertEquals("01 Six Feet Deep.flac", authoritative.displayName)
            assertTrue(snapshot.localTargets.any {
                it.referenceKey == neverPlayed.membershipKey() && it.identityId < 0L
            })
            assertEquals("Six Feet Deep", snapshot.reviewItems.single()
                .candidates.single().target.title)
            assertEquals(identitiesBefore, database.listeningTrackIdentityDao().getAll())
            assertEquals(bindingsBefore, database.localTrackBindingDao().getAllForBackup())

            val unboundTarget = snapshot.localTargets.single {
                it.referenceKey == neverPlayed.membershipKey()
            }
            assertTrue(
                operations.linkMany(listOf(source), unboundTarget) is
                    ListeningIdentityReconciliationLinkResult.Linked
            )
            assertTrue(
                database.localTrackBindingDao().getByReferenceKey(neverPlayed.membershipKey()) != null
            )
        }

    @Test
    fun confirmationRechecksCurrentLibraryAndDoesNotCreateBindingForDisappearedTarget() =
        runBlocking {
            val source = identity("Historical", "Artist", "Album")
            importedEvent(source, 1)
            val current = song(303, "Local", "Artist", "Album", "Local.flac")
            var songs = listOf(current)
            val operations = DefaultListeningHistoryReconciliationOperations(
                database = database,
                currentSongs = { songs }
            )
            val target = operations.load().localTargets.single()
            assertTrue(target.identityId < 0L)

            songs = emptyList()
            val result = operations.linkMany(listOf(source), target)

            assertEquals(
                ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND,
                (result as ListeningIdentityReconciliationLinkResult.Rejected).reason
            )
            assertTrue(database.localTrackBindingDao().getAllForBackup().isEmpty())
            assertTrue(reconciliation.listLinks().isEmpty())
        }

    @Test
    fun rejectedTransientLinkRollsBackBindingAndIdentityCreation() = runBlocking {
        val ineligibleSource = identity("No imported history", "Artist", "Album")
        val current = song(404, "Unbound", "Artist", "Album", "Unbound.flac")
        val operations = DefaultListeningHistoryReconciliationOperations(
            database = database,
            currentSongs = { listOf(current) }
        )
        val target = operations.load().localTargets.single()
        val identitiesBefore = database.listeningTrackIdentityDao().getAll()

        val result = operations.linkMany(listOf(ineligibleSource), target)

        assertEquals(
            ListeningIdentityReconciliationFailure.SOURCE_HAS_NO_IMPORTED_HISTORY,
            (result as ListeningIdentityReconciliationLinkResult.Rejected).reason
        )
        assertEquals(identitiesBefore, database.listeningTrackIdentityDao().getAll())
        assertTrue(database.localTrackBindingDao().getAllForBackup().isEmpty())
        assertTrue(reconciliation.listLinks().isEmpty())
    }

    @Test
    fun boundTargetInvalidatedBeforeConfirmationIsNotReactivatedOrLinked() = runBlocking {
        val source = identity("Historical", "Artist", "Album")
        importedEvent(source, 1)
        val current = song(505, "Bound", "Artist", "Album", "Bound.flac")
        val targetIdentity = identity("Old Bound", "Old Artist", "Old Album")
        val bindingId = database.localTrackBindingDao().insert(
            LocalTrackBindingEntity(
                trackIdentityId = targetIdentity,
                referenceKey = current.membershipKey(),
                mediaStoreId = current.id,
                volumeName = current.volumeName,
                contentUri = current.uri.toString(),
                relativePath = current.relativePath,
                displayName = current.displayName,
                absolutePath = null,
                fileSizeBytes = current.fileSizeBytes,
                dateModifiedEpochSeconds = current.dateModifiedEpochSeconds,
                durationMsSnapshot = current.duration,
                legacyStableKey = null,
                portableKey = null,
                portableKeyVersion = 1,
                firstSeenAt = 1,
                lastSeenAt = 2,
                missingSince = null
            )
        )
        val operations = DefaultListeningHistoryReconciliationOperations(
            database = database,
            currentSongs = { listOf(current) }
        )
        val target = operations.load().localTargets.single()
        val binding = database.localTrackBindingDao().getByReferenceKey(current.membershipKey())!!
        database.localTrackBindingDao().update(binding.copy(missingSince = 99L))

        val result = operations.linkMany(listOf(source), target)

        assertEquals(
            ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING,
            (result as ListeningIdentityReconciliationLinkResult.Rejected).reason
        )
        assertEquals(
            99L,
            database.localTrackBindingDao().getAllForBackup().single { it.id == bindingId }.missingSince
        )
        assertTrue(reconciliation.listLinks().isEmpty())
    }

    @Test
    fun repeatedConfirmationNeverCreatesADuplicateBinding() = runBlocking {
        val source = identity("Historical", "Artist", "Album")
        importedEvent(source, 1)
        val current = song(606, "Unbound", "Artist", "Album", "Unbound.flac")
        val operations = DefaultListeningHistoryReconciliationOperations(
            database = database,
            currentSongs = { listOf(current) }
        )
        val target = operations.load().localTargets.single()

        assertTrue(
            operations.linkMany(listOf(source), target) is
                ListeningIdentityReconciliationLinkResult.Linked
        )
        val retry = operations.linkMany(listOf(source), target)

        assertEquals(
            ListeningIdentityReconciliationFailure.SOURCE_ALREADY_RECONCILED,
            (retry as ListeningIdentityReconciliationLinkResult.Rejected).reason
        )
        assertEquals(1, database.localTrackBindingDao().getAllForBackup().size)
        assertEquals(1, reconciliation.listLinks().size)
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
    fun newUriLessFragmentDoesNotInheritExistingMetadataLinks() = runBlocking {
        val target = local("Fragmented Song", "Artist", "Album")
        val linkedFragments = (1..3).map { index ->
            identity("Fragmented Song", "Artist", "Album").also { importedEvent(it, index) }
        }
        assertTrue(
            reconciliation.linkMany(linkedFragments, target) is
                ListeningIdentityReconciliationLinkResult.Linked
        )

        val newFragment = identity("Fragmented Song", "Artist", "Album")
        importedEvent(newFragment, 4)
        val discovery = service.discoverCandidates()

        assertEquals(listOf(newFragment), discovery.items.map { it.source.identityId })
        assertEquals(target, discovery.items.single().candidates.single().target.identityId)
        assertEquals(3, reconciliation.listLinks().size)
        val rows = ListeningStatsRepository(database).getTopTracksByQualifiedPlays(10)
        assertEquals(3L, rows.single { it.trackIdentityId == target }.playCounts.totalPlayCount)
        assertEquals(1L, rows.single { it.trackIdentityId == newFragment }.playCounts.totalPlayCount)
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

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        displayName: String
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = 1,
        duration = 180_000,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        filePath = "/storage/emulated/0/Music/The Warning/$displayName",
        folderPath = "/storage/emulated/0/Music/The Warning",
        albumArtUri = null,
        albumArtist = artist,
        volumeName = "external",
        displayName = displayName,
        relativePath = "Music/The Warning/",
        fileSizeBytes = 1_000,
        dateModifiedEpochSeconds = 2
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
