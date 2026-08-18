package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.controller.DefaultSpotifyListeningHistoryImportOperations
import com.example.cdplaya.controller.ListeningHistoryImportFile
import com.example.cdplaya.controller.SpotifyImportUiError
import com.example.cdplaya.controller.SpotifyImportUiState
import com.example.cdplaya.controller.SpotifyListeningHistoryImportController
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
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifyListeningHistoryImportFlowTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningImportRepository
    private lateinit var controller: SpotifyListeningHistoryImportController
    private lateinit var operations: DefaultSpotifyListeningHistoryImportOperations
    private lateinit var executor: SpotifyListeningHistoryImportExecutor
    private lateinit var scope: CoroutineScope
    private val batchSequence = AtomicInteger()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningImportRepository(database, nowMillis = { NOW })
        val sourceProfiles = SpotifyImportSourceProfileService(repository) { NOW }
        val parser = SpotifyExtendedStreamingParser(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
        )
        executor = SpotifyListeningHistoryImportExecutor(
            repository = repository,
            sourceProfiles = sourceProfiles,
            parser = parser,
            nowMillis = { NOW },
            batchUuid = { "ui-flow-${batchSequence.incrementAndGet()}" },
            createdAppVersion = "test",
            chunkSize = 2
        )
        operations = DefaultSpotifyListeningHistoryImportOperations(
            repository = repository,
            previewer = SpotifyListeningHistoryImportPreviewer(repository, sourceProfiles, parser),
            executor = executor,
            nowMillis = { NOW }
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        controller = SpotifyListeningHistoryImportController(
            operations = operations,
            scope = scope,
            workDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun freshImportThenSameFilesAgainPublishesOnceAndShowsZeroNewPreview() = runBlocking {
        val files = listOf(asset("spotify_extended_reexport_initial.json"))
        controller.selectFiles(files)
        controller.analyze()
        val preview = awaitState<SpotifyImportUiState.Preview>()
        assertEquals(2L, preview.preview.dedupe.newOccurrences)

        controller.importHistory()
        val result = awaitState<SpotifyImportUiState.Success>()
        assertEquals(2L, result.result.newPublished)
        assertEquals(2L, ListeningStatsRepository(database)
            .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)

        controller.reset()
        controller.selectFiles(files)
        controller.analyze()
        val repeat = awaitState<SpotifyImportUiState.Preview>()
        assertEquals(0L, repeat.preview.dedupe.newOccurrences)
        assertEquals(2L, repeat.preview.dedupe.alreadyImportedOccurrences)
        controller.importHistory()
        assertTrue(controller.state.value is SpotifyImportUiState.Preview)
    }

    @Test
    fun completedImportSurvivesRepositoryAndControllerRecreationWithoutStaleRecovery() = runBlocking {
        controller.selectFiles(listOf(asset("spotify_extended_reexport_initial.json")))
        controller.analyze()
        awaitState<SpotifyImportUiState.Preview>()
        controller.importHistory()
        awaitState<SpotifyImportUiState.Success>()

        val recreatedStats = ListeningStatsRepository(database)
        assertEquals(
            2L,
            recreatedStats.getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount
        )
        controller = SpotifyListeningHistoryImportController(
            operations = operations,
            scope = scope,
            workDispatcher = Dispatchers.Unconfined
        )
        controller.enterWorkflow()

        awaitState<SpotifyImportUiState.Landing>()
        val topTracks = recreatedStats.getTopTracksByQualifiedPlays(10)
        assertEquals(2, topTracks.size)
        assertTrue(topTracks.all { it.binding == null })
    }

    @Test
    fun overlapAndLaterReexportUseMaximumMultiplicityAndOnlyAddNewHistory() = runBlocking {
        controller.selectFiles(listOf(
            asset("spotify_extended_overlap_a.json"),
            asset("spotify_extended_overlap_b.json")
        ))
        controller.analyze()
        val overlapPreview = awaitState<SpotifyImportUiState.Preview>()
        assertEquals(3L, overlapPreview.preview.dedupe.newOccurrences)
        assertEquals(1L, overlapPreview.preview.dedupe.overlappingOccurrencesSuppressed)
        controller.importHistory()
        val overlapResult = awaitState<SpotifyImportUiState.Success>()
        assertEquals(3L, overlapResult.result.newPublished)
        assertEquals(1L, overlapResult.result.overlappingOccurrencesSuppressed)

        controller.reset()
        controller.selectFiles(listOf(asset("spotify_extended_reexport_initial.json")))
        controller.analyze()
        awaitState<SpotifyImportUiState.Preview>()
        controller.importHistory()
        awaitState<SpotifyImportUiState.Success>()
        controller.reset()
        controller.selectFiles(listOf(asset("spotify_extended_reexport_later.json")))
        controller.analyze()
        val later = awaitState<SpotifyImportUiState.Preview>()
        assertEquals(1L, later.preview.dedupe.newOccurrences)
        assertEquals(2L, later.preview.dedupe.alreadyImportedOccurrences)
    }

    @Test
    fun unsupportedAccountDataAndMalformedSelectionsUseSpecificFatalErrors() = runBlocking {
        controller.selectFiles(listOf(memoryFile("account.json", """[
            {"endTime":"2024-01-01 00:00","artistName":"Artist","trackName":"Song","msPlayed":1000}
        ]""")))
        controller.analyze()
        assertEquals(
            SpotifyImportUiError.ACCOUNT_DATA_FORMAT,
            awaitState<SpotifyImportUiState.Error>().error
        )

        controller.selectFiles(listOf(memoryFile("broken.json", "[{\"ts\":")))
        controller.analyze()
        assertEquals(
            SpotifyImportUiError.MALFORMED_JSON,
            awaitState<SpotifyImportUiState.Error>().error
        )
    }

    @Test
    fun stalePendingBatchIsDetectedAndSafeCleanupPreservesPublishedStatistics() = runBlocking {
        val publishedSource = com.example.cdplaya.data.importing.spotify.ListeningImportStreamSource {
            ByteArrayInputStream("""[{
                "ts":"2024-07-01T00:00:00Z","ms_played":31000,
                "master_metadata_track_name":"Published","master_metadata_album_artist_name":"Artist",
                "master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:published"
            }]""".toByteArray())
        }
        assertEquals(1L, executor.execute(listOf(publishedSource)).newPublished)
        val publishedStats = ListeningStatsRepository(database)
            .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount
        val profile = SpotifyImportSourceProfileService(repository) { NOW }.getOrCreateDefault()
        val batchId = repository.createBatch(
            ListeningImportBatchEntity(
                stableUuid = "unfinished-ui-flow",
                sourceProfileId = profile.id,
                status = ListeningImportBatchStatus.PENDING,
                parserVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                qualificationRuleVersion = 1,
                startedAt = NOW,
                completedAt = null,
                sourceRangeStart = null,
                sourceRangeEnd = null,
                createdAppVersion = "test"
            )
        )
        assertEquals(1, repository.persistSpotifyChunk(batchId, listOf(preparedPending())).newPending)
        assertEquals(publishedStats, ListeningStatsRepository(database)
            .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)

        controller.enterWorkflow()
        assertEquals(1, awaitState<SpotifyImportUiState.StaleImportRecovery>().pendingBatchCount)
        controller.cleanStaleImport()
        awaitState<SpotifyImportUiState.Landing>()
        assertTrue(repository.getPendingBatchIdsForSourceProfile(profile.id).isEmpty())
        assertEquals(publishedStats, ListeningStatsRepository(database)
            .getAllTimeOverview(includeLegacyBaseline = false).detailedEventCount)
        assertEquals(1L, database.listeningEventDao().count())
    }

    private suspend inline fun <reified T : SpotifyImportUiState> awaitState(): T =
        withTimeout(5_000) { controller.state.first { it is T } as T }

    private fun asset(name: String) = object : ListeningHistoryImportFile {
        override val transientKey = name
        override val displayName = name
        override fun openStream(): InputStream = InstrumentationRegistry.getInstrumentation()
            .context.assets.open("importing/spotify/$name")
    }

    private fun memoryFile(name: String, json: String) = object : ListeningHistoryImportFile {
        override val transientKey = name
        override val displayName = name
        override fun openStream(): InputStream = ByteArrayInputStream(json.toByteArray())
    }

    private fun preparedPending(): PreparedListeningOccurrence {
        val record = ImportedListeningRecord(
            provider = ImportProvider.SPOTIFY,
            externalMediaId = "pending-only",
            mediaType = ImportedMediaType.MUSIC_TRACK,
            trackTitle = "Pending",
            trackArtist = "Artist",
            albumTitle = "Album",
            albumArtist = "Artist",
            sourceStartedAt = null,
            sourceEndedAt = Instant.parse("2024-08-01T00:00:00Z"),
            timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
            listenedMs = 31_000,
            skippedEvidence = ImportedTriState.UNKNOWN,
            completionEvidence = ImportedCompletionEvidence.UNKNOWN,
            providerReasonStart = null,
            providerReasonEnd = null
        )
        val fingerprint = SpotifyListeningImportFingerprint.create(record)
        return PreparedListeningOccurrence(
            key = ImportOccurrenceKey(fingerprint.fingerprintVersion, fingerprint.fingerprint, 0),
            record = record,
            policy = SpotifyImportPolicy.evaluate(record)
        )
    }

    companion object {
        private const val NOW = 2_000_000_000_000L
    }
}
