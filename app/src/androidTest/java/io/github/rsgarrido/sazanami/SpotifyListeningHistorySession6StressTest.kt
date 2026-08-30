package io.github.rsgarrido.sazanami

import android.content.Context
import android.os.Debug
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.AnalyticsZoneIdProvider
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsRangeResolver
import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupRepository
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionPhase
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionProgress
import io.github.rsgarrido.sazanami.data.importing.spotify.ListeningImportStreamSource
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyExtendedStreamingParser
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyImportSourceProfileService
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportPreviewer
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in, production-path Session 6 stress coverage. Inputs are generated into the test app cache
 * and deleted after each test; no large fixture is checked into the repository.
 */
@RunWith(AndroidJUnit4::class)
class SpotifyListeningHistorySession6StressTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningImportRepository
    private lateinit var sourceProfiles: SpotifyImportSourceProfileService
    private lateinit var parser: SpotifyExtendedStreamingParser
    private val generatedFiles = mutableListOf<File>()
    private val batchSequence = AtomicInteger()
    private val eventSequence = AtomicLong()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
        repository = ListeningImportRepository(
            database = database,
            nowMillis = { NOW },
            eventUuid = { "session6-event-${eventSequence.incrementAndGet()}" }
        )
        sourceProfiles = SpotifyImportSourceProfileService(repository) { NOW }
        parser = SpotifyExtendedStreamingParser(
            Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        database.close()
        generatedFiles.forEach(File::delete)
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun hundredThousandHighReuse_importReimportLaterExportAnalyticsAndBackup() = runBlocking {
        requireEnabled(ARG_STRESS_100K)
        val initial = generateHistory("high-reuse-100k", 100_000, uniqueTracks = 100)
        val executor = executor()
        val initialMetrics = PhaseMetrics()
        lateinit var initialResult: io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
        val initialElapsed = measureTimeMillis {
            initialResult = executor.execute(listOf(initial.source()), initialMetrics::accept)
        }

        assertEquals(100_000L, initialResult.selectedOccurrences)
        assertEquals(100_000L, initialResult.newPublished)
        assertEquals(0L, initialResult.alreadyImported)
        assertDatabaseCounts(events = 100_000, identities = 100, externalIds = 100)
        assertEquals(200, initialMetrics.chunks)

        val statsRepository = ListeningStatsRepository(database)
        val analyticsStarted = System.currentTimeMillis()
        val snapshot = statsRepository.getAnalyticsSnapshot(
            ListeningAnalyticsRangeResolver(
                Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC),
                AnalyticsZoneIdProvider { ZoneId.of("UTC") }
            ).resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME))
        )
        val analyticsElapsed = System.currentTimeMillis() - analyticsStarted
        assertEquals(100_000L, snapshot.overview.detailedEventCount)
        assertEquals(100_000L, snapshot.overview.qualifiedDetailedPlayCount)
        assertEquals(100_000L, snapshot.trend.sumOf { it.totalAttemptCount })
        assertEquals(10, snapshot.topTracks.size)
        assertTrue(snapshot.topArtists.isNotEmpty())
        assertTrue(snapshot.topAlbums.isNotEmpty())
        assertNotNull(snapshot.coverage.earliestDetailedEventAt)
        assertNotNull(snapshot.coverage.latestDetailedEventAt)

        lateinit var backup: io.github.rsgarrido.sazanami.data.backup.BackupListeningHistoryV2
        val backupElapsed = measureTimeMillis {
            backup = ListeningHistoryBackupRepository(database).export()
        }
        assertEquals(100_000, backup.events.size)
        assertEquals(100_000, backup.importedEventEvidence.size)
        assertEquals(100_000, backup.batchEventObservations.size)
        assertTrue(backup.events.none { it.publicationState == "import_pending" })

        lateinit var repeatResult: io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
        val repeatElapsed = measureTimeMillis {
            repeatResult = executor.execute(listOf(initial.source()))
        }
        assertEquals(0L, repeatResult.newPublished)
        assertEquals(100_000L, repeatResult.alreadyImported)
        assertDatabaseCounts(
            events = 100_000,
            identities = 100,
            externalIds = 100,
            observations = 200_000
        )

        val later = generateHistory("high-reuse-120k", 120_000, uniqueTracks = 100)
        lateinit var laterResult: io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
        val laterElapsed = measureTimeMillis {
            laterResult = executor.execute(listOf(later.source()))
        }
        assertEquals(20_000L, laterResult.newPublished)
        assertEquals(100_000L, laterResult.alreadyImported)
        assertDatabaseCounts(
            events = 120_000,
            identities = 100,
            externalIds = 100,
            evidence = 120_000,
            observations = 320_000
        )
        assertEquals(
            120_000L,
            statsRepository.getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount
        )

        val databaseBytes = checkpointAndDatabaseBytes()
        println(
            "session6 100k-high-reuse totalMs=$initialElapsed " +
                "analysisMs=${initialMetrics.analysisMs} persistenceMs=${initialMetrics.persistenceMs} " +
                "publicationMs=${initialMetrics.publicationMs} chunks=${initialMetrics.chunks} " +
                "analyticsMs=$analyticsElapsed backupMs=$backupElapsed backupEvents=${backup.events.size} " +
                "reimportMs=$repeatElapsed later120kMs=$laterElapsed databaseBytes=$databaseBytes"
        )
    }

    @Test
    fun hundredThousandHighCardinality_productionPathCreatesExpectedIdentityGraph() = runBlocking {
        requireEnabled(ARG_STRESS_100K)
        val input = generateHistory("high-cardinality-100k", 100_000, uniqueTracks = 100_000)
        lateinit var result: io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
        val metrics = PhaseMetrics()
        val elapsed = measureTimeMillis {
            result = executor().execute(listOf(input.source()), metrics::accept)
        }

        assertEquals(100_000L, result.newPublished)
        assertDatabaseCounts(events = 100_000, identities = 100_000, externalIds = 100_000)
        assertEquals(200, metrics.chunks)
        val queryElapsed = measureTimeMillis {
            val stats = ListeningStatsRepository(database)
            assertEquals(
                100_000L,
                stats.getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount
            )
            assertEquals(10, stats.getTopTracksByQualifiedPlays(10).size)
            assertTrue(stats.getTopArtists(5).isNotEmpty())
            assertTrue(stats.getTopAlbums(5).isNotEmpty())
        }
        println(
            "session6 100k-high-cardinality totalMs=$elapsed analysisMs=${metrics.analysisMs} " +
                "persistenceMs=${metrics.persistenceMs} publicationMs=${metrics.publicationMs} " +
                "chunks=${metrics.chunks} queryMs=$queryElapsed " +
                "databaseBytes=${checkpointAndDatabaseBytes()}"
        )
    }

    @Test
    fun hundredThousandCancellation_afterManyCommittedChunksCleansAllPendingState() = runBlocking {
        requireEnabled(ARG_CANCEL_STRESS)
        val input = generateHistory("cancel-100k", 100_000, uniqueTracks = 100)
        var requestedAt = 0L
        var cancellationObserved = false
        val elapsed = measureTimeMillis {
            try {
                executor().execute(listOf(input.source())) { progress ->
                    if (progress.phase == ListeningImportExecutionPhase.IMPORTING &&
                        progress.chunksCompleted == CANCEL_AFTER_CHUNKS
                    ) {
                        requestedAt = System.currentTimeMillis()
                        throw CancellationException("synthetic cancellation")
                    }
                }
            } catch (_: CancellationException) {
                cancellationObserved = true
            }
        }
        val cleanupElapsed = System.currentTimeMillis() - requestedAt

        assertTrue(cancellationObserved)
        assertDatabaseCounts(events = 0, identities = 0, externalIds = 0, evidence = 0, observations = 0)
        val profile = sourceProfiles.getOrCreateDefault()
        assertTrue(repository.getPendingBatchIdsForSourceProfile(profile.id).isEmpty())
        assertEquals(
            0L,
            ListeningStatsRepository(database)
                .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount
        )
        println(
            "session6 cancel-100k totalMs=$elapsed cancellationChunk=$CANCEL_AFTER_CHUNKS " +
                "pendingEventsAtRequest=${CANCEL_AFTER_CHUNKS * 500} cleanupMs=$cleanupElapsed"
        )
    }

    @Test
    fun hundredThousandFailure_afterManyCommittedChunksCleansAndRetainsSafeFailedBatch() = runBlocking {
        requireEnabled(ARG_FAILURE_STRESS)
        val input = generateHistory("failure-100k", 100_000, uniqueTracks = 100)
        var requestedAt = 0L
        val elapsed = measureTimeMillis {
            val failure = runCatching {
                executor().execute(listOf(input.source())) { progress ->
                    if (progress.phase == ListeningImportExecutionPhase.IMPORTING &&
                        progress.chunksCompleted == CANCEL_AFTER_CHUNKS
                    ) {
                        requestedAt = System.currentTimeMillis()
                        error("synthetic private failure detail")
                    }
                }
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        }
        val cleanupElapsed = System.currentTimeMillis() - requestedAt

        assertDatabaseCounts(events = 0, identities = 0, externalIds = 0, evidence = 0, observations = 0)
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM listening_import_batches WHERE status = 'failed'"))
        assertEquals(
            1L,
            queryLong(
                "SELECT COUNT(*) FROM listening_import_batches " +
                    "WHERE status = 'failed' AND failureCategory = 'persistence'"
            )
        )
        assertEquals(
            0L,
            queryLong(
                "SELECT COUNT(*) FROM listening_import_batches " +
                    "WHERE failureCategory LIKE '%private%'"
            )
        )
        println(
            "session6 failure-100k totalMs=$elapsed failureChunk=$CANCEL_AFTER_CHUNKS " +
                "pendingEventsAtFailure=${CANCEL_AFTER_CHUNKS * 500} cleanupMs=$cleanupElapsed"
        )
    }

    @Test
    fun fiveHundredThousandUnique_previewCompletesWithinObservedHeap() = runBlocking {
        requireEnabled(ARG_STRESS_500K)
        val input = generateHistory("unique-preview-500k", 500_000, uniqueTracks = 500_000)
        val runtime = Runtime.getRuntime()
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val peakHeap = AtomicLong(heapBefore)
        val sampling = AtomicBoolean(true)
        val sampler = Thread {
            while (sampling.get()) {
                val used = runtime.totalMemory() - runtime.freeMemory()
                peakHeap.accumulateAndGet(used, ::maxOf)
                Thread.sleep(20L)
            }
        }.apply { start() }
        lateinit var preview: io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportPreview
        val elapsed = try {
            measureTimeMillis {
                preview = SpotifyListeningHistoryImportPreviewer(repository, sourceProfiles, parser)
                    .preview(listOf(input.source()))
            }
        } finally {
            sampling.set(false)
            sampler.join()
        }

        assertEquals(500_000L, preview.analysis.validMusicRecords)
        assertEquals(null, preview.analysis.uniqueExternalTrackIds)
        assertEquals(500_000L, preview.dedupe.newOccurrences)
        assertEquals(0L, preview.dedupe.alreadyImportedOccurrences)
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM listening_import_batches"))
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM listening_events"))
        println(
            "session6 preview-500k elapsedMs=$elapsed heapBeforeBytes=$heapBefore " +
                "observedPeakHeapBytes=${peakHeap.get()} maxHeapBytes=${runtime.maxMemory()} " +
                "nativeHeapBytes=${Debug.getNativeHeapAllocatedSize()} distinctFingerprints=500000"
        )
    }

    @Test
    fun fiveHundredThousandHighReuse_productionPathWhenExplicitlyEnabled() = runBlocking {
        requireEnabled(ARG_ROOM_500K)
        val input = generateHistory("room-high-reuse-500k", 500_000, uniqueTracks = 100)
        val metrics = PhaseMetrics()
        lateinit var result: io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
        val elapsed = measureTimeMillis {
            result = executor().execute(listOf(input.source()), metrics::accept)
        }
        assertEquals(500_000L, result.newPublished)
        assertDatabaseCounts(events = 500_000, identities = 100, externalIds = 100)
        assertEquals(1_000, metrics.chunks)
        val queryElapsed = measureTimeMillis {
            assertEquals(
                500_000L,
                ListeningStatsRepository(database)
                    .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount
            )
        }
        println(
            "session6 room-500k totalMs=$elapsed analysisMs=${metrics.analysisMs} " +
                "persistenceMs=${metrics.persistenceMs} publicationMs=${metrics.publicationMs} " +
                "chunks=${metrics.chunks} queryMs=$queryElapsed " +
                "databaseBytes=${checkpointAndDatabaseBytes()}"
        )
    }

    private fun executor() = SpotifyListeningHistoryImportExecutor(
        repository = repository,
        sourceProfiles = sourceProfiles,
        parser = parser,
        nowMillis = { NOW },
        batchUuid = { "session6-batch-${batchSequence.incrementAndGet()}" },
        createdAppVersion = "session6-stress"
    )

    private fun generateHistory(name: String, records: Int, uniqueTracks: Int): File {
        require(uniqueTracks in 1..records)
        return File(context.cacheDir, "$name-${System.nanoTime()}.json").also { file ->
            generatedFiles += file
            file.outputStream().buffered(64 * 1024).bufferedWriter().use { writer ->
                writer.append('[')
                repeat(records) { index ->
                    if (index > 0) writer.append(',')
                    val track = index % uniqueTracks
                    writer.append("{\"ts\":\"")
                    writer.append(Instant.ofEpochSecond(BASE_EPOCH_SECONDS + index).toString())
                    writer.append("\",\"ms_played\":31000")
                    writer.append(",\"master_metadata_track_name\":\"Track ").append(track.toString()).append('"')
                    writer.append(",\"master_metadata_album_artist_name\":\"Synthetic Artist\"")
                    writer.append(",\"master_metadata_album_album_name\":\"Synthetic Album\"")
                    writer.append(",\"spotify_track_uri\":\"spotify:track:")
                    writer.append(String.format(Locale.ROOT, "%022d", track)).append('"')
                    writer.append(",\"reason_end\":\"trackdone\",\"skipped\":false}")
                }
                writer.append(']')
            }
        }
    }

    private fun File.source() = ListeningImportStreamSource(::inputStream)

    private fun assertDatabaseCounts(
        events: Long,
        identities: Long,
        externalIds: Long,
        evidence: Long = events,
        observations: Long = events
    ) {
        assertEquals(events, queryLong("SELECT COUNT(*) FROM listening_events"))
        assertEquals(identities, queryLong("SELECT COUNT(*) FROM listening_track_identities"))
        assertEquals(externalIds, queryLong("SELECT COUNT(*) FROM listening_track_external_ids"))
        assertEquals(evidence, queryLong("SELECT COUNT(*) FROM imported_listening_event_evidence"))
        assertEquals(observations, queryLong("SELECT COUNT(*) FROM listening_import_batch_events"))
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM listening_events WHERE publicationState = 'import_pending'"))
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM listening_import_batches WHERE status = 'pending'"))
    }

    private fun queryLong(sql: String): Long = database.query(SimpleSQLiteQuery(sql)).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun checkpointAndDatabaseBytes(): Long {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val main = context.getDatabasePath(DATABASE_NAME)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm"))
            .filter(File::exists)
            .sumOf(File::length)
    }

    private fun requireEnabled(argument: String) {
        assumeTrue(
            "$argument must be true",
            InstrumentationRegistry.getArguments().getString(argument) == "true"
        )
    }

    private class PhaseMetrics {
        private val startedAt = System.currentTimeMillis()
        private var analysisEndedAt = 0L
        private var persistenceEndedAt = 0L
        private var completedAt = 0L
        var chunks: Int = 0
            private set

        val analysisMs: Long get() = analysisEndedAt - startedAt
        val persistenceMs: Long get() = persistenceEndedAt - analysisEndedAt
        val publicationMs: Long get() = completedAt - persistenceEndedAt

        fun accept(progress: ListeningImportExecutionProgress) {
            chunks = maxOf(chunks, progress.chunksCompleted)
            when (progress.phase) {
                ListeningImportExecutionPhase.ANALYZING -> analysisEndedAt = System.currentTimeMillis()
                ListeningImportExecutionPhase.PUBLISHING -> persistenceEndedAt = System.currentTimeMillis()
                ListeningImportExecutionPhase.COMPLETED -> completedAt = System.currentTimeMillis()
                ListeningImportExecutionPhase.IMPORTING -> Unit
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "session6-listening-import-stress.db"
        private const val ARG_STRESS_100K = "spotify.importStress100k"
        private const val ARG_STRESS_500K = "spotify.importStress500k"
        private const val ARG_ROOM_500K = "spotify.importRoomStress500k"
        private const val ARG_CANCEL_STRESS = "spotify.importCancelStress"
        private const val ARG_FAILURE_STRESS = "spotify.importFailureStress"
        private const val CANCEL_AFTER_CHUNKS = 40
        private const val BASE_EPOCH_SECONDS = 1_500_000_000L
        private const val NOW = 2_100_000_000_000L
    }
}
