package com.example.cdplaya

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.controller.DefaultSpotifyListeningHistoryImportOperations
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.importing.ImportOccurrenceKey
import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportedCompletionEvidence
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedMediaType
import com.example.cdplaya.data.importing.ImportedTimestampEvidence
import com.example.cdplaya.data.importing.ImportedTriState
import com.example.cdplaya.data.importing.PreparedListeningOccurrence
import com.example.cdplaya.data.importing.spotify.ListeningImportStreamSource
import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyImportPolicy
import com.example.cdplaya.data.importing.spotify.SpotifyImportSourceProfileService
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportPreviewer
import com.example.cdplaya.data.importing.spotify.SpotifyListeningImportFingerprint
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ListeningImportBatchEntity
import com.example.cdplaya.data.local.ListeningImportBatchStatus
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.SongRatingEntity
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportStaleCleanupStressTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningImportRepository
    private lateinit var profiles: SpotifyImportSourceProfileService
    private lateinit var parser: SpotifyExtendedStreamingParser
    private val eventSequence = AtomicLong()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningImportRepository(
            database,
            nowMillis = { NOW },
            eventUuid = { "stale-event-${eventSequence.incrementAndGet()}" }
        )
        profiles = SpotifyImportSourceProfileService(repository) { NOW }
        parser = SpotifyExtendedStreamingParser(
            Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun multipleLargeStaleBatches_partialCleanupAndSecondCleanupConvergeSafely() = runBlocking {
        assumeTrue(
            "spotify.importStaleStress must be true",
            InstrumentationRegistry.getArguments()
                .getString("spotify.importStaleStress") == "true"
        )
        val executor = SpotifyListeningHistoryImportExecutor(
            repository,
            profiles,
            parser,
            nowMillis = { NOW },
            batchUuid = { "stale-published" },
            createdAppVersion = "session6-stress"
        )
        assertEquals(1L, executor.execute(listOf(publishedSource())).newPublished)
        val publishedEventId = queryLong("SELECT id FROM listening_events LIMIT 1")
        val publishedIdentityId = queryLong("SELECT trackIdentityId FROM listening_events LIMIT 1")
        database.songRatingDao().upsert(SongRatingEntity(publishedIdentityId, 5, NOW, NOW))

        val profile = profiles.getOrCreateDefault()
        val batchA = createPendingBatch(profile.id, "stale-a-20k", startedAt = 30)
        val batchB = createPendingBatch(profile.id, "stale-b-10k", startedAt = 10)
        val batchC = createPendingBatch(profile.id, "stale-c-5k", startedAt = 20)
        persistRange(batchA, start = 100_000, count = 20_000)
        persistRange(batchB, start = 200_000, count = 10_000)
        persistRange(batchC, start = 300_000, count = 5_000)
        repository.observeEvent(batchC, publishedEventId)
        assertEquals(listOf(batchB, batchC, batchA), repository.getPendingBatchIdsForSourceProfile(profile.id))
        assertEquals(35_001L, queryLong("SELECT COUNT(*) FROM listening_events"))
        assertEquals(1L, ListeningStatsRepository(database)
            .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)

        // Model a prior process dying during cleanup: a prefix of B is already gone, including
        // cascaded evidence/observation rows, while the batch itself is still pending.
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM listening_events WHERE id IN (" +
                "SELECT eventId FROM listening_import_batch_events WHERE batchId = $batchB " +
                "ORDER BY eventId LIMIT 100)"
        )
        assertEquals(34_901L, queryLong("SELECT COUNT(*) FROM listening_events"))

        val operations = DefaultSpotifyListeningHistoryImportOperations(
            repository = repository,
            previewer = SpotifyListeningHistoryImportPreviewer(repository, profiles, parser),
            executor = executor,
            nowMillis = { NOW + 1 }
        )
        val cleanupElapsed = measureTimeMillis { operations.cleanUnfinishedBatches() }
        val secondCleanupElapsed = measureTimeMillis { operations.cleanUnfinishedBatches() }

        assertTrue(repository.getPendingBatchIdsForSourceProfile(profile.id).isEmpty())
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM listening_events"))
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM listening_track_identities"))
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM listening_track_external_ids"))
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM imported_listening_event_evidence"))
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM listening_import_batch_events"))
        assertEquals(3L, queryLong("SELECT COUNT(*) FROM listening_import_batches WHERE status = 'cancelled'"))
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM listening_events WHERE publicationState = 'import_pending'"))
        assertEquals(5, database.songRatingDao().getByTrackIdentityId(publishedIdentityId)?.rating)
        assertEquals(1L, ListeningStatsRepository(database)
            .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)
        println(
            "session6 stale-cleanup batches=3 pendingEvents=35000 partialRowsAlreadyRemoved=100 " +
                "cleanupMs=$cleanupElapsed secondCleanupMs=$secondCleanupElapsed publishedPreserved=1"
        )
    }

    private suspend fun createPendingBatch(sourceId: Long, uuid: String, startedAt: Long): Long =
        repository.createBatch(
            ListeningImportBatchEntity(
                stableUuid = uuid,
                sourceProfileId = sourceId,
                status = ListeningImportBatchStatus.PENDING,
                parserVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                qualificationRuleVersion = 1,
                startedAt = startedAt,
                completedAt = null,
                sourceRangeStart = null,
                sourceRangeEnd = null,
                parsedCount = 0,
                createdAppVersion = "session6-stress"
            )
        )

    private suspend fun persistRange(batchId: Long, start: Int, count: Int) {
        (start until start + count).chunked(500).forEach { indices ->
            val result = repository.persistSpotifyChunk(batchId, indices.map(::prepared))
            assertEquals(indices.size, result.newPending)
        }
    }

    private fun prepared(index: Int): PreparedListeningOccurrence {
        val record = ImportedListeningRecord(
            provider = ImportProvider.SPOTIFY,
            externalMediaId = String.format(Locale.ROOT, "%022d", index),
            mediaType = ImportedMediaType.MUSIC_TRACK,
            trackTitle = "Stale $index",
            trackArtist = "Synthetic Artist",
            albumTitle = "Synthetic Album",
            albumArtist = "Synthetic Artist",
            sourceStartedAt = null,
            sourceEndedAt = Instant.ofEpochSecond(BASE_EPOCH_SECONDS + index),
            timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
            listenedMs = 31_000,
            skippedEvidence = ImportedTriState.FALSE,
            completionEvidence = ImportedCompletionEvidence.UNKNOWN,
            providerReasonStart = null,
            providerReasonEnd = "trackdone"
        )
        val fingerprint = SpotifyListeningImportFingerprint.create(record)
        return PreparedListeningOccurrence(
            ImportOccurrenceKey(fingerprint.fingerprintVersion, fingerprint.fingerprint, 0),
            record,
            SpotifyImportPolicy.evaluate(record)
        )
    }

    private fun publishedSource() = ListeningImportStreamSource {
        ByteArrayInputStream(
            """[{"ts":"2024-01-01T00:00:00Z","ms_played":31000,
                "master_metadata_track_name":"Published","master_metadata_album_artist_name":"Safe",
                "master_metadata_album_album_name":"Shared","spotify_track_uri":"spotify:track:published",
                "reason_end":"trackdone","skipped":false}]""".toByteArray()
        )
    }

    private fun queryLong(sql: String): Long = database.query(SimpleSQLiteQuery(sql)).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    companion object {
        private const val BASE_EPOCH_SECONDS = 1_600_000_000L
        private const val NOW = 2_100_000_000_000L
    }
}
