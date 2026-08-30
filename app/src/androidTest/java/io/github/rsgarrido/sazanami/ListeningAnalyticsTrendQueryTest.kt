package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.AnalyticsZoneIdProvider
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsRangeResolver
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LegacyListeningBaselineEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningAnalyticsTrendQueryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningStatsRepository
    private val zone = ZoneId.of("UTC")
    private val resolver = ListeningAnalyticsRangeResolver(
        Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), zone),
        AnalyticsZoneIdProvider { zone }
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningStatsRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun oneQueryPreservesEmptyBucketsHalfOpenBoundariesAndStartAttribution() = runBlocking {
        val track = insertIdentity("Boundary", "Clock", "Calendar")
        val start = epoch("2026-01-01T00:00:00Z")
        val day = 86_400_000L
        database.listeningEventDao().insert(
            listOf(
                event("before", track, start - 1L, 50L, true),
                event("start-natural", track, start, 100L, true, endReason = ListeningEndReason.NATURAL_END),
                event("cross-midnight", track, start + day - 1_000L, 7_200_000L, true),
                event("boundary-nonqualified", track, start + day, 200L, false),
                event("last-ms", track, start + 2L * day - 1L, 300L, true, endReason = ListeningEndReason.ERROR),
                event("range-end", track, start + 3L * day, 400L, true)
            )
        )
        val snapshot = repository.getAnalyticsSnapshot(
            resolver.resolve(
                AnalyticsRangeSelection.Custom(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"))
            )
        )

        assertEquals(3, snapshot.trend.size)
        val first = snapshot.trend[0]
        assertEquals(7_200_100L, first.listenedMs)
        assertEquals(2L, first.qualifiedPlayCount)
        assertEquals(2L, first.totalAttemptCount)
        assertEquals(1L, first.naturalCompletionCount)
        val second = snapshot.trend[1]
        assertEquals(500L, second.listenedMs)
        assertEquals(1L, second.qualifiedPlayCount)
        assertEquals(2L, second.totalAttemptCount)
        assertEquals(0L, second.naturalCompletionCount)
        val empty = snapshot.trend[2]
        assertEquals(0L, empty.listenedMs)
        assertEquals(0L, empty.qualifiedPlayCount)
        assertEquals(0L, empty.totalAttemptCount)
        assertEquals(listOf(0, 1, 2), snapshot.trend.map { it.index })
        assertEquals(4L, snapshot.overview.detailedEventCount)
        assertEquals(start, snapshot.coverage.earliestDetailedEventAt)
        assertEquals(start + 2L * day - 1L, snapshot.coverage.latestDetailedEventAt)
    }

    @Test
    fun legacyAndSourceRulesStayConsistentAcrossEverySnapshotSection() = runBlocking {
        val native = insertIdentity("Native", "One", "Native Album")
        val imported = insertIdentity("Imported", "Two", "Import Album")
        database.legacyListeningBaselineDao().insert(
            LegacyListeningBaselineEntity(native, 7, 1L, 2L, "legacy:native", 3L)
        )
        database.listeningEventDao().insert(
            listOf(
                event("native", native, epoch("2025-01-01T00:00:00Z"), 1_000L, true),
                event("spotify", imported, epoch("2025-02-01T00:00:00Z"), 2_000L, true, ListeningSource.SPOTIFY_IMPORT)
            )
        )
        val allTime = resolver.resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME))
        val unfiltered = repository.getAnalyticsSnapshot(allTime)
        assertEquals(9L, unfiltered.overview.playCounts.totalPlayCount)
        assertEquals(7L, unfiltered.coverage.legacyQualifiedPlayCount)
        assertEquals(2L, unfiltered.trend.sumOf { it.qualifiedPlayCount })
        assertEquals(3_000L, unfiltered.trend.sumOf { it.listenedMs })
        assertTrue(unfiltered.coverage.selectionCanIncludeLegacyPlays)
        assertTrue(unfiltered.coverage.hasLegacyPlays)

        val spotify = repository.getAnalyticsSnapshot(allTime, setOf(ListeningSource.SPOTIFY_IMPORT))
        assertEquals(1L, spotify.overview.playCounts.totalPlayCount)
        assertEquals(0L, spotify.overview.playCounts.legacyPlayCount)
        assertEquals(1L, spotify.trend.sumOf { it.qualifiedPlayCount })
        assertEquals(2_000L, spotify.trend.sumOf { it.listenedMs })
        assertEquals(listOf(imported), spotify.topTracks.map { it.trackIdentityId })
        assertEquals("Import Album", spotify.topAlbums.single().album)
        assertEquals("Two", spotify.topArtists.single().artist)
        assertFalse(spotify.coverage.selectionCanIncludeLegacyPlays)
        assertFalse(spotify.coverage.hasLegacyPlays)
        assertEquals(epoch("2025-02-01T00:00:00Z"), spotify.coverage.earliestDetailedEventAt)

        val baselineOnlyRange = repository.getAnalyticsSnapshot(
            resolver.resolve(
                AnalyticsRangeSelection.Custom(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-02"))
            )
        )
        assertEquals(0L, baselineOnlyRange.overview.playCounts.totalPlayCount)
        assertTrue(baselineOnlyRange.trend.all { it.totalAttemptCount == 0L })
        assertNull(baselineOnlyRange.coverage.earliestDetailedEventAt)
    }

    @Test
    fun mixedNativeAndImportedHistoryCoversEveryPresetHistoricalBoundsRankingsAndCompletion() =
        runBlocking {
            val archiveOne = insertIdentity("Archive One", "Archive Artist", "Archive Album")
            val archiveTwo = insertIdentity("Archive Two", "Archive Artist", "Archive Album")
            val recentImport = insertIdentity("Recent Import", "Current Artist", "Current Album")
            val native = insertIdentity("Native Today", "Local Artist", "Local Album")
            database.listeningEventDao().insert(
                listOf(
                    importedEvent(
                        "spotify-2023",
                        archiveOne,
                        epoch("2023-12-31T23:59:00Z"),
                        31_000L,
                        qualified = true,
                        completion = ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL
                    ),
                    importedEvent(
                        "spotify-2024",
                        archiveTwo,
                        epoch("2024-06-15T12:00:00Z"),
                        5_000L,
                        qualified = false
                    ),
                    importedEvent(
                        "spotify-last-30",
                        recentImport,
                        epoch("2025-12-15T12:00:00Z"),
                        31_000L,
                        qualified = true
                    ),
                    importedEvent(
                        "spotify-last-7",
                        recentImport,
                        epoch("2025-12-30T12:00:00Z"),
                        31_000L,
                        qualified = true
                    ),
                    event(
                        "native-today",
                        native,
                        epoch("2026-01-02T10:00:00Z"),
                        1_000L,
                        qualified = true,
                        endReason = ListeningEndReason.NATURAL_END
                    ),
                    importedEvent(
                        "spotify-pending",
                        archiveOne,
                        epoch("2026-01-02T11:00:00Z"),
                        99_000L,
                        qualified = true,
                        completion = ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL,
                        publication = ListeningEventPublicationState.IMPORT_PENDING
                    )
                )
            )

            val expectedAttempts = mapOf(
                AnalyticsRangePreset.TODAY to 1L,
                AnalyticsRangePreset.LAST_7_DAYS to 2L,
                AnalyticsRangePreset.LAST_30_DAYS to 3L,
                AnalyticsRangePreset.THIS_MONTH to 1L,
                AnalyticsRangePreset.THIS_YEAR to 1L,
                AnalyticsRangePreset.ALL_TIME to 5L
            )
            expectedAttempts.forEach { (preset, expected) ->
                val snapshot = repository.getAnalyticsSnapshot(
                    resolver.resolve(AnalyticsRangeSelection.Preset(preset))
                )
                assertEquals(preset.name, expected, snapshot.overview.detailedEventCount)
                assertEquals(preset.name, expected, snapshot.trend.sumOf { it.totalAttemptCount })
            }

            val allTime = repository.getAnalyticsSnapshot(
                resolver.resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME))
            )
            assertEquals(4L, allTime.overview.qualifiedDetailedPlayCount)
            assertEquals(1L, allTime.overview.nonQualifiedAttemptCount)
            assertEquals(2L, allTime.overview.naturalCompletionCount)
            assertEquals(99_000L, allTime.overview.listeningTime.confirmedDetailedListeningMs)
            assertEquals(epoch("2023-12-31T23:59:00Z"), allTime.coverage.earliestDetailedEventAt)
            assertEquals(epoch("2026-01-02T10:00:00Z"), allTime.coverage.latestDetailedEventAt)
            assertEquals(5L, allTime.trend.sumOf { it.totalAttemptCount })
            assertEquals(LocalDate.parse("2023-12-01"), allTime.trend.first().localStartDate())
            assertEquals(LocalDate.parse("2026-01-01"), allTime.trend.last().localStartDate())
            assertEquals(1L, allTime.trend.single {
                it.localStartDate() == LocalDate.parse("2023-12-01")
            }.qualifiedPlayCount)
            assertEquals(1L, allTime.trend.single {
                it.localStartDate() == LocalDate.parse("2024-06-01")
            }.totalAttemptCount)

            assertEquals(recentImport, allTime.topTracks.first().trackIdentityId)
            assertTrue(allTime.topTracks.none { it.trackIdentityId == archiveTwo })
            val archiveAlbum = allTime.topAlbums.single { it.album == "Archive Album" }
            assertEquals(1L, archiveAlbum.playCounts.totalPlayCount)
            assertEquals(2L, archiveAlbum.trackCount)
            assertEquals(36_000L, archiveAlbum.confirmedDetailedListeningMs)
            val archiveArtist = allTime.topArtists.single { it.artist == "Archive Artist" }
            assertEquals(1L, archiveArtist.playCounts.totalPlayCount)
            assertEquals(2L, archiveArtist.distinctTrackCount)
        }

    @Test
    fun importedUtcEndTimestampUsesTheExistingLocalMidnightBucketBoundary() = runBlocking {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val localResolver = ListeningAnalyticsRangeResolver(
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC")),
            AnalyticsZoneIdProvider { losAngeles }
        )
        val imported = insertIdentity("Midnight Import", "Boundary Artist", "Boundary Album")
        database.listeningEventDao().insert(
            listOf(
                importedEvent(
                    "before-local-midnight",
                    imported,
                    epoch("2026-01-01T07:59:59.999Z"),
                    1_000L,
                    qualified = true
                ),
                importedEvent(
                    "at-local-midnight",
                    imported,
                    epoch("2026-01-01T08:00:00Z"),
                    2_000L,
                    qualified = true
                )
            )
        )

        val today = repository.getAnalyticsSnapshot(
            localResolver.resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.TODAY))
        )

        assertEquals(1L, today.overview.detailedEventCount)
        assertEquals(2_000L, today.overview.listeningTime.confirmedDetailedListeningMs)
        assertEquals(epoch("2026-01-01T08:00:00Z"), today.coverage.earliestDetailedEventAt)
        assertEquals(1L, today.trend.sumOf { it.totalAttemptCount })
    }

    @Test
    fun snapshotAppliesExplicitRankingLimitsAndKeepsIdentityRowsSeparate() = runBlocking {
        repeat(12) { index ->
            val id = insertIdentity("Same Title", "Artist $index", "Album $index")
            database.listeningEventDao().insert(
                event("rank-$index", id, epoch("2025-01-01T00:00:00Z") + index, 100L, true)
            )
        }
        val snapshot = repository.getAnalyticsSnapshot(
            resolver.resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME))
        )
        assertEquals(10, snapshot.topTracks.size)
        assertEquals(5, snapshot.topAlbums.size)
        assertEquals(5, snapshot.topArtists.size)
        assertEquals(10, snapshot.topTracks.map { it.trackIdentityId }.distinct().size)
    }

    @Test
    fun canonicalEventInsertInvalidatesAnalyticsObservation() = runBlocking {
        val track = insertIdentity("Reactive", "Room", "Invalidation")
        val emissions = Channel<Unit>(Channel.UNLIMITED)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeAnalyticsInvalidations().collect { emissions.send(Unit) }
        }
        try {
            withTimeout(3_000L) { emissions.receive() }
            database.listeningEventDao().insert(
                event("reactive", track, epoch("2026-01-01T00:00:00Z"), 100L, true)
            )
            withTimeout(3_000L) { emissions.receive() }
        } finally {
            observer.cancelAndJoin()
            emissions.close()
        }
    }

    @Test
    fun snapshotReadsRemainCoherentWhenAWriterIsQueuedMidTransaction() = runBlocking {
        val track = insertIdentity("Coherent", "Room", "Transaction")
        database.listeningEventDao().insert(
            event("before-snapshot", track, epoch("2026-01-01T00:00:00Z"), 100L, true)
        )
        val resolved = resolver.resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-01"))
        )
        val transactionAcquired = CompletableDeferred<Unit>()
        val allowSnapshotReads = CompletableDeferred<Unit>()
        val writerStarted = CompletableDeferred<Unit>()
        val snapshot = async(Dispatchers.IO) {
            database.withTransaction {
                transactionAcquired.complete(Unit)
                allowSnapshotReads.await()
                repository.getAnalyticsSnapshot(resolved)
            }
        }
        transactionAcquired.await()
        val writer = async(Dispatchers.IO) {
            writerStarted.complete(Unit)
            database.listeningEventDao().insert(
                event("after-snapshot", track, epoch("2026-01-01T01:00:00Z"), 200L, true)
            )
        }
        writerStarted.await()
        allowSnapshotReads.complete(Unit)

        val captured = snapshot.await()
        writer.await()
        assertEquals(1L, captured.overview.detailedEventCount)
        assertEquals(1L, captured.trend.sumOf { it.totalAttemptCount })
        assertEquals(1L, captured.topTracks.single().detailedEventCount)
        assertEquals(1L, captured.coverage.detailedQualifiedPlayCount)

        val afterWrite = repository.getAnalyticsSnapshot(resolved)
        assertEquals(2L, afterWrite.overview.detailedEventCount)
        assertEquals(2L, afterWrite.trend.sumOf { it.totalAttemptCount })
    }

    private suspend fun insertIdentity(title: String, artist: String, album: String): Long =
        database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = title,
                artistSnapshot = artist,
                albumSnapshot = album,
                albumArtistSnapshot = artist,
                durationMsSnapshot = 180_000L,
                normalizedTitle = title.lowercase(),
                normalizedArtist = artist.lowercase(),
                normalizedAlbum = album.lowercase(),
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

    private fun event(
        uuid: String,
        trackId: Long,
        startedAt: Long,
        listenedMs: Long,
        qualified: Boolean,
        source: ListeningSource = ListeningSource.NATIVE,
        endReason: ListeningEndReason = ListeningEndReason.STOPPED
    ) = ListeningEventEntity(
        eventUuid = uuid,
        source = source,
        trackIdentityId = trackId,
        localTrackBindingId = null,
        playbackSessionId = null,
        startedAt = startedAt,
        endedAt = startedAt + listenedMs,
        listenedMs = listenedMs,
        trackDurationMs = 180_000L,
        qualifiedAsPlay = qualified,
        qualificationReason = if (qualified) ListeningQualificationReason.TIME_THRESHOLD else ListeningQualificationReason.NONE,
        qualificationRuleVersion = 1,
        endReason = endReason,
        sourceEventKey = null,
        importBatchId = null,
        createdAt = startedAt + listenedMs
    )

    private fun importedEvent(
        uuid: String,
        trackId: Long,
        attributionAt: Long,
        listenedMs: Long,
        qualified: Boolean,
        completion: ListeningCompletionClassification = ListeningCompletionClassification.NONE,
        publication: ListeningEventPublicationState = ListeningEventPublicationState.IMPORT_PUBLISHED
    ) = ListeningEventEntity(
        eventUuid = uuid,
        source = ListeningSource.SPOTIFY_IMPORT,
        trackIdentityId = trackId,
        localTrackBindingId = null,
        playbackSessionId = null,
        startedAt = null,
        endedAt = attributionAt,
        attributionAt = attributionAt,
        timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = listenedMs,
        trackDurationMs = null,
        qualifiedAsPlay = qualified,
        qualificationReason = if (qualified) {
            ListeningQualificationReason.TIME_THRESHOLD
        } else {
            ListeningQualificationReason.NONE
        },
        qualificationRuleVersion = 1,
        qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
        endReason = null,
        completionClassification = completion,
        publicationState = publication,
        sourceEventKey = null,
        importBatchId = null,
        createdAt = attributionAt + 1L
    )

    private fun epoch(value: String): Long = Instant.parse(value).toEpochMilli()

    private fun io.github.rsgarrido.sazanami.data.ListeningTrendBucket.localStartDate(): LocalDate =
        Instant.ofEpochMilli(startInclusive).atZone(zone).toLocalDate()
}
