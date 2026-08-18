package com.example.cdplaya

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.backup.ListeningHistoryBackupRepository
import com.example.cdplaya.data.importing.ImportOccurrenceKey
import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportedCompletionEvidence
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedMediaType
import com.example.cdplaya.data.importing.ImportedTimestampEvidence
import com.example.cdplaya.data.importing.ImportedTriState
import com.example.cdplaya.data.importing.ListeningImportExecutionPhase
import com.example.cdplaya.data.importing.PreparedListeningOccurrence
import com.example.cdplaya.data.importing.spotify.ListeningImportStreamSource
import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyImportPolicy
import com.example.cdplaya.data.importing.spotify.SpotifyImportSourceProfileService
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import com.example.cdplaya.data.importing.spotify.SpotifyListeningImportFingerprint
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ListeningImportBatchEntity
import com.example.cdplaya.data.local.ListeningImportBatchStatus
import com.example.cdplaya.data.local.ListeningImportSourceEntity
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.SongRatingEntity
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifyListeningHistoryImportExecutorTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningImportRepository
    private lateinit var executor: SpotifyListeningHistoryImportExecutor
    private val batchSequence = AtomicInteger()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningImportRepository(database, nowMillis = { 2_000_000_000_000 })
        executor = newExecutor()
    }

    @After fun tearDown() = database.close()

    @Test fun exactIdsHistoricalIdentitiesReimportAndBackupRoundTrip_areConservative() = runBlocking {
        val localIdentity = database.listeningTrackIdentityDao().insert(identity("Coincidence"))
        database.localTrackBindingDao().insert(binding(localIdentity))
        val original = source(json(
            entry("2024-01-01T00:00:00Z", 31_000, "Old title", "Artist", "Album", "stable"),
            entry("2024-01-01T00:01:00Z", 5_000, "Old title", "Artist", "Album", "stable", "trackdone", false),
            entry("2024-01-01T00:02:00Z", 5_000, "Old title", "Artist", "Album", "different", "fwdbtn", true),
            entry("2024-01-01T00:03:00Z", 31_000, "Coincidence", "Artist", "Album", null),
            entry("2024-01-01T00:04:00Z", 31_000, "Coincidence", "Artist", "Album", null)
        ))

        var pendingWasInvisible = false
        val first = executor.execute(listOf(original)) { progress ->
            if (progress.phase == ListeningImportExecutionPhase.IMPORTING &&
                progress.chunksCompleted == 1) {
                assertEquals(0L, ListeningStatsRepository(database)
                    .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)
                pendingWasInvisible = true
            }
        }
        assertTrue(pendingWasInvisible)
        assertEquals(5L, first.newPublished)
        assertEquals(0L, first.alreadyImported)
        assertEquals(5, database.listeningTrackIdentityDao().getAll().size)
        assertEquals(1, database.localTrackBindingDao().getAllForBackup().size)
        assertEquals(2, database.listeningTrackExternalIdDao().getAllForBackup().size)
        val stableIdentityId = database.listeningTrackExternalIdDao()
            .find(ListeningSource.SPOTIFY_IMPORT, "stable")!!.trackIdentityId
        database.songRatingDao().upsert(SongRatingEntity(stableIdentityId, 5, 10, 10))

        val stats = ListeningStatsRepository(database)
        val overview = stats.getAllTimeOverview(includeLegacyBaseline = false)
        assertEquals(5L, overview.detailedEventCount)
        assertEquals(4L, overview.qualifiedDetailedPlayCount)
        assertEquals(1L, overview.naturalCompletionCount)
        val top = stats.getTopTracksByQualifiedPlays(10)
        assertEquals(2L, top.first { it.title == "Old title" }.playCounts.detailedPlayCount)

        val repeat = executor.execute(listOf(original))
        assertEquals(0L, repeat.newPublished)
        assertEquals(5L, repeat.alreadyImported)
        assertEquals(5, database.listeningTrackIdentityDao().getAll().size)
        assertEquals(5, database.songRatingDao().getByTrackIdentityId(stableIdentityId)?.rating)

        val renamed = source(json(
            entry("2024-02-01T00:00:00Z", 31_000, "Renamed remaster", "Artist", "Album", "stable")
        ))
        val later = executor.execute(listOf(renamed))
        assertEquals(1L, later.newPublished)
        assertEquals(5, database.listeningTrackIdentityDao().getAll().size)
        assertEquals("Old title", database.listeningTrackIdentityDao().getById(
            database.listeningTrackExternalIdDao().find(ListeningSource.SPOTIFY_IMPORT, "stable")!!.trackIdentityId
        )!!.titleSnapshot)

        val backupRepository = ListeningHistoryBackupRepository(database)
        val backup = backupRepository.exportWithRatings()
        database.withTransaction {
            val identityIds = backupRepository.restoreValidatedWithinTransaction(backup.history)
            backupRepository.restoreRatingsValidatedWithinTransaction(backup.ratings, identityIds)
        }
        val restored = backupRepository.export()
        assertEquals(6, restored.events.size)
        assertTrue(restored.events.all { it.startedAt == null && it.endedAt == it.attributionAt })
        assertTrue(restored.events.all { it.timestampEvidence == "source_end_only" })
        assertTrue(restored.events.all { it.publicationState == "import_published" })
        assertTrue(restored.events.any { it.completionClassification == "source_documented_natural" })
        assertTrue(restored.events.any { it.qualifiedAsPlay })
        assertEquals(6, restored.importedEventEvidence.size)
        assertTrue(restored.batchEventObservations.isNotEmpty())
        assertEquals("spotify-default-profile-v1", restored.importSources.single().stableUuid)
        val restoredStableId = database.listeningTrackExternalIdDao()
            .find(ListeningSource.SPOTIFY_IMPORT, "stable")!!.trackIdentityId
        assertEquals(5, database.songRatingDao().getByTrackIdentityId(restoredStableId)?.rating)
        val restoredCount = database.listeningEventDao().count()
        val afterRestore = executor.execute(listOf(original, renamed))
        assertEquals(0L, afterRestore.newPublished)
        assertEquals(6L, afterRestore.alreadyImported)
        assertEquals(restoredCount, database.listeningEventDao().count())
        assertEquals(2, database.listeningTrackExternalIdDao().getAllForBackup().size)
    }

    @Test fun productionImportBackupRestoreThenLaterReexportPreservesDedupeIdentityAndRating() =
        runBlocking {
            val initial = executor.execute(listOf(asset("spotify_extended_reexport_initial.json")))
            assertEquals(2L, initial.newPublished)
            val ratedMapping = database.listeningTrackExternalIdDao().getAllForBackup().first()
            database.songRatingDao().upsert(
                SongRatingEntity(ratedMapping.trackIdentityId, 4, 100L, 120L)
            )

            val backupRepository = ListeningHistoryBackupRepository(database)
            val backup = backupRepository.exportWithRatings()
            database.withTransaction {
                val identityIds = backupRepository.restoreValidatedWithinTransaction(backup.history)
                backupRepository.restoreRatingsValidatedWithinTransaction(backup.ratings, identityIds)
            }

            val sameAfterRestore = executor.execute(
                listOf(asset("spotify_extended_reexport_initial.json"))
            )
            assertEquals(0L, sameAfterRestore.newPublished)
            assertEquals(2L, sameAfterRestore.alreadyImported)

            val laterAfterRestore = executor.execute(
                listOf(asset("spotify_extended_reexport_later.json"))
            )
            assertEquals(1L, laterAfterRestore.newPublished)
            assertEquals(2L, laterAfterRestore.alreadyImported)
            assertEquals(3L, database.listeningEventDao().count())

            val restoredRatedIdentity = database.listeningTrackExternalIdDao()
                .find(ListeningSource.SPOTIFY_IMPORT, ratedMapping.externalId)!!
                .trackIdentityId
            assertEquals(
                4,
                database.songRatingDao().getByTrackIdentityId(restoredRatedIdentity)?.rating
            )
            assertEquals(3, database.listeningTrackIdentityDao().getAll().size)
        }

    @Test fun overlapUsesMaximumPerFile_andLaterExportAddsOnlyNewOrdinals() = runBlocking {
        val a = source(json(
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:01:00Z", 31_000, "Y", "A", "", "y")
        ))
        val b = source(json(
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:01:00Z", 31_000, "Y", "A", "", "y"),
            entry("2024-03-01T00:02:00Z", 31_000, "Z", "A", "", "z")
        ))

        val overlap = executor.execute(listOf(a, b))
        assertEquals(5L, overlap.newPublished)
        assertEquals(3L, overlap.overlappingOccurrencesSuppressed)
        assertEquals(3, database.listeningTrackIdentityDao().getAll().size)

        val later = source(json(
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x"),
            entry("2024-03-01T00:00:00Z", 31_000, "X", "A", "", "x")
        ))
        val reexport = executor.execute(listOf(later))
        assertEquals(1L, reexport.newPublished)
        assertEquals(3L, reexport.alreadyImported)
        assertEquals(6L, database.listeningEventDao().count())
    }

    @Test fun session1OverlapAndReexportFixtures_persistOnlySelectedNewOccurrences() = runBlocking {
        val initial = executor.execute(listOf(asset("spotify_extended_reexport_initial.json")))
        val later = executor.execute(listOf(asset("spotify_extended_reexport_later.json")))
        val overlap = executor.execute(listOf(
            asset("spotify_extended_overlap_a.json"),
            asset("spotify_extended_overlap_b.json")
        ))

        assertEquals(2L, initial.newPublished)
        assertEquals(1L, later.newPublished)
        assertEquals(2L, later.alreadyImported)
        assertEquals(3L, overlap.newPublished)
        assertEquals(1L, overlap.overlappingOccurrencesSuppressed)
        assertEquals(6L, database.listeningEventDao().count())
    }

    @Test fun cancellationAfterTwoChunks_removesPendingEventsMappingsAndIdentities() = runBlocking {
        val cancellingExecutor = newExecutor(chunkSize = 2)
        val input = source(json(*(0 until 6).map { index ->
            entry("2024-04-01T00:0${index}:00Z", 31_000, "Track $index", "Artist", "", "id$index")
        }.toTypedArray()))

        var cancelled = false
        try {
            cancellingExecutor.execute(listOf(input)) { progress ->
                if (progress.phase == ListeningImportExecutionPhase.IMPORTING &&
                    progress.chunksCompleted == 2) throw CancellationException("test")
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(0L, database.listeningEventDao().count())
        assertTrue(database.listeningTrackIdentityDao().getAll().isEmpty())
        assertTrue(database.listeningTrackExternalIdDao().getAllForBackup().isEmpty())
    }

    @Test fun failedChunkRollsBackIdentityExternalIdEventEvidenceAndObservation() = runBlocking {
        val failingRepository = ListeningImportRepository(
            database,
            nowMillis = { 2_000_000_000_000 },
            eventUuid = { "same-event-uuid" }
        )
        val profile = SpotifyImportSourceProfileService(failingRepository) { 1 }.getOrCreateDefault()
        val batchId = failingRepository.createBatch(batch(profile.id, "failure-batch"))
        val one = prepared("one", "2024-05-01T00:00:00Z", 0)
        val two = prepared("two", "2024-05-01T00:01:00Z", 0)

        assertTrue(runCatching { failingRepository.persistSpotifyChunk(batchId, listOf(one, two)) }.isFailure)
        assertEquals(0L, database.listeningEventDao().count())
        assertTrue(database.listeningTrackIdentityDao().getAll().isEmpty())
        assertTrue(database.listeningTrackExternalIdDao().getAllForBackup().isEmpty())
        assertNull(database.importedListeningEventEvidenceDao().getByEventId(1))
        assertEquals(0L, database.listeningImportBatchEventDao().countForBatch(batchId))
    }

    @Test fun caughtFailureAfterCommittedChunk_preservesSharedPublishedHistory() = runBlocking {
        val shared = source(json(
            entry("2024-06-01T00:00:00Z", 31_000, "Shared", "Artist", "", "shared")
        ))
        assertEquals(1L, executor.execute(listOf(shared)).newPublished)
        val failingExecutor = newExecutor(chunkSize = 2)
        val mixed = source(json(
            entry("2024-06-01T00:00:00Z", 31_000, "Shared", "Artist", "", "shared"),
            entry("2024-06-01T00:01:00Z", 31_000, "New 1", "Artist", "", "new1"),
            entry("2024-06-01T00:02:00Z", 31_000, "New 2", "Artist", "", "new2")
        ))

        assertTrue(runCatching {
            failingExecutor.execute(listOf(mixed)) { progress ->
                if (progress.phase == ListeningImportExecutionPhase.IMPORTING &&
                    progress.chunksCompleted == 1) error("controlled test failure")
            }
        }.isFailure)

        assertEquals(1L, database.listeningEventDao().count())
        assertEquals(1, database.listeningTrackIdentityDao().getAll().size)
        assertEquals(listOf("shared"), database.listeningTrackExternalIdDao().getAllForBackup()
            .map { it.externalId })
    }

    @Test fun sourceProfilesHaveSeparateDedupeButShareGlobalExactSpotifyIdentity() = runBlocking {
        val sourceA = repository.createSourceProfile(importSource("profile-a"))
        val sourceB = repository.createSourceProfile(importSource("profile-b"))
        val batchA = repository.createBatch(batch(sourceA, "profile-batch-a"))
        val batchB = repository.createBatch(batch(sourceB, "profile-batch-b"))
        val occurrence = prepared("global-id", "2024-07-01T00:00:00Z", 0)

        assertEquals(1, repository.persistSpotifyChunk(batchA, listOf(occurrence)).newPending)
        repository.publishBatch(batchA, 1, 1, 10)
        assertEquals(1, repository.persistSpotifyChunk(batchB, listOf(occurrence)).newPending)
        repository.publishBatch(batchB, 1, 1, 11)

        assertEquals(2L, database.listeningEventDao().count())
        assertEquals(1, database.listeningTrackIdentityDao().getAll().size)
        assertEquals(1, database.listeningTrackExternalIdDao().getAllForBackup().size)
    }

    private fun newExecutor(chunkSize: Int = 500): SpotifyListeningHistoryImportExecutor {
        val clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
        return SpotifyListeningHistoryImportExecutor(
            repository = repository,
            sourceProfiles = SpotifyImportSourceProfileService(repository) { 2_000_000_000_000 },
            parser = SpotifyExtendedStreamingParser(clock),
            nowMillis = { 2_000_000_000_000 },
            batchUuid = { "executor-batch-${batchSequence.incrementAndGet()}" },
            createdAppVersion = "test",
            chunkSize = chunkSize
        )
    }

    private fun source(json: String) = ListeningImportStreamSource {
        ByteArrayInputStream(json.toByteArray())
    }

    private fun asset(name: String) = ListeningImportStreamSource {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("importing/spotify/$name")
    }

    private fun json(vararg entries: String) = entries.joinToString(",", "[", "]")

    private fun entry(
        timestamp: String,
        listenedMs: Long,
        title: String,
        artist: String,
        album: String,
        id: String?,
        reasonEnd: String? = null,
        skipped: Boolean? = null
    ): String = buildString {
        append("{\"ts\":\"").append(timestamp)
        append("\",\"ms_played\":").append(listenedMs)
        append(",\"master_metadata_track_name\":\"").append(title).append('"')
        append(",\"master_metadata_album_artist_name\":\"").append(artist).append('"')
        append(",\"master_metadata_album_album_name\":\"").append(album).append('"')
        if (id != null) append(",\"spotify_track_uri\":\"spotify:track:").append(id).append('"')
        if (reasonEnd != null) append(",\"reason_end\":\"").append(reasonEnd).append('"')
        if (skipped != null) append(",\"skipped\":").append(skipped)
        append('}')
    }

    private fun identity(title: String) = ListeningTrackIdentityEntity(
        titleSnapshot = title,
        artistSnapshot = "Artist",
        albumSnapshot = "Album",
        albumArtistSnapshot = null,
        durationMsSnapshot = 180_000,
        normalizedTitle = title.lowercase(),
        normalizedArtist = "artist",
        normalizedAlbum = "album",
        metadataKey = "local-key",
        metadataKeyVersion = 1,
        createdAt = 1,
        updatedAt = 1
    )

    private fun binding(identityId: Long) = LocalTrackBindingEntity(
        trackIdentityId = identityId,
        referenceKey = "local-reference",
        mediaStoreId = 10,
        volumeName = "external",
        contentUri = "content://local/10",
        relativePath = "Music",
        displayName = "coincidence.mp3",
        absolutePath = null,
        fileSizeBytes = 1_000,
        dateModifiedEpochSeconds = 1,
        durationMsSnapshot = 180_000,
        legacyStableKey = null,
        portableKey = "local-key",
        portableKeyVersion = 1,
        firstSeenAt = 1,
        lastSeenAt = 1,
        missingSince = null
    )

    private fun batch(sourceId: Long, uuid: String) = ListeningImportBatchEntity(
        stableUuid = uuid,
        sourceProfileId = sourceId,
        status = ListeningImportBatchStatus.PENDING,
        parserVersion = 1,
        qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
        qualificationRuleVersion = 1,
        startedAt = 1,
        completedAt = null,
        sourceRangeStart = null,
        sourceRangeEnd = null,
        createdAppVersion = "test"
    )

    private fun importSource(uuid: String) = ListeningImportSourceEntity(
        stableUuid = uuid,
        sourceType = ListeningSource.SPOTIFY_IMPORT,
        displayLabel = "Spotify test",
        accountIdentityDigest = null,
        createdAt = 1,
        updatedAt = 1
    )

    private fun prepared(id: String, timestamp: String, ordinal: Int): PreparedListeningOccurrence {
        val record = ImportedListeningRecord(
            provider = ImportProvider.SPOTIFY,
            externalMediaId = id,
            mediaType = ImportedMediaType.MUSIC_TRACK,
            trackTitle = id,
            trackArtist = "Artist",
            albumTitle = null,
            albumArtist = "Artist",
            sourceStartedAt = null,
            sourceEndedAt = Instant.parse(timestamp),
            timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
            listenedMs = 31_000,
            skippedEvidence = ImportedTriState.UNKNOWN,
            completionEvidence = ImportedCompletionEvidence.UNKNOWN,
            providerReasonStart = null,
            providerReasonEnd = null
        )
        val fingerprint = SpotifyListeningImportFingerprint.create(record)
        return PreparedListeningOccurrence(
            ImportOccurrenceKey(fingerprint.fingerprintVersion, fingerprint.fingerprint, ordinal),
            record,
            SpotifyImportPolicy.evaluate(record)
        )
    }
}
