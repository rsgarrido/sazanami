package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.ListeningDateRange
import io.github.rsgarrido.sazanami.data.ListeningStatsFilter
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.AnalyticsBucketBoundary
import io.github.rsgarrido.sazanami.data.AnalyticsBucketGranularity
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LegacyListeningBaselineEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState
import io.github.rsgarrido.sazanami.data.local.ListeningStatsQueries
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningStatsRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ListeningStatsRepository
    private lateinit var fixture: Fixture

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ListeningStatsRepository(database)
        fixture = insertFixture()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun allTimeOverviewCombinesFrozenBaselineAndAuthoritativeDetailedFacts() = runBlocking {
        val overview = repository.getAllTimeOverview()

        assertEquals(11L, overview.playCounts.totalPlayCount)
        assertEquals(6L, overview.playCounts.legacyPlayCount)
        assertEquals(5L, overview.playCounts.detailedPlayCount)
        assertEquals(9_200L, overview.listeningTime.confirmedDetailedListeningMs)
        assertEquals(6L, overview.listeningTime.legacyPlayCountWithoutKnownDuration)
        assertEquals(2L, overview.naturalCompletionCount)
        assertEquals(3L, overview.nonQualifiedAttemptCount)
        assertEquals(8L, overview.detailedEventCount)
        assertEquals(500L, overview.firstDetailedEventAt)
        assertEquals(950L, overview.latestDetailedEventAt)
        assertEquals(100L, overview.firstKnownPlayAt)
        assertEquals(850L, overview.latestKnownPlayAt)
        assertTrue(overview.hasLegacyBaseline)
    }

    @Test
    fun rangesAreHalfOpenDetailedOnlyAndSourceFiltersNeverClaimLegacy() = runBlocking {
        val range = repository.getDetailedOverview(ListeningDateRange(600L, 850L))
        assertEquals(3L, range.playCounts.totalPlayCount)
        assertEquals(0L, range.playCounts.legacyPlayCount)
        assertEquals(4L, range.detailedEventCount)
        assertEquals(6_200L, range.listeningTime.confirmedDetailedListeningMs)
        assertEquals(1L, range.nonQualifiedAttemptCount)
        assertEquals(600L, range.firstDetailedEventAt)
        assertEquals(800L, range.latestDetailedEventAt)
        assertFalse(range.hasLegacyBaseline)

        val spotify = repository.getAllTimeOverview(
            sources = setOf(ListeningSource.SPOTIFY_IMPORT),
            includeLegacyBaseline = true
        )
        assertEquals(1L, spotify.playCounts.totalPlayCount)
        assertEquals(0L, spotify.playCounts.legacyPlayCount)
        assertEquals(2_000L, spotify.listeningTime.confirmedDetailedListeningMs)
        assertFalse(spotify.hasLegacyBaseline)

        val cdplaya = repository.getAllTimeOverview(
            sources = setOf(ListeningSource.CDPLAYA),
            includeLegacyBaseline = true
        )
        assertEquals(4L, cdplaya.playCounts.totalPlayCount)
        assertEquals(0L, cdplaya.playCounts.legacyPlayCount)
        assertEquals(6_800L, cdplaya.listeningTime.confirmedDetailedListeningMs)
    }

    @Test
    fun halfOpenBoundariesRemainExactAcrossDayMonthAndYearTransitions() = runBlocking {
        val trackId = identity("Boundary", "Clock", "Calendar", "Clock")
        val boundaries = listOf(
            86_400_000L to 172_800_000L,
            2_678_400_000L to 5_097_600_000L,
            31_536_000_000L to 63_072_000_000L
        )
        boundaries.forEachIndexed { index, (start, end) ->
            database.listeningEventDao().insert(
                listOf(
                    event("boundary-$index-before", trackId, start - 1L, 1L, true),
                    event("boundary-$index-start", trackId, start, 2L, true),
                    event("boundary-$index-last", trackId, end - 1L, 3L, true),
                    event("boundary-$index-end", trackId, end, 4L, true)
                )
            )
            val overview = repository.getDetailedOverview(ListeningDateRange(start, end))
            assertEquals(2L, overview.detailedEventCount)
            assertEquals(2L, overview.qualifiedDetailedPlayCount)
            assertEquals(5L, overview.listeningTime.confirmedDetailedListeningMs)
            assertEquals(start, overview.firstDetailedEventAt)
            assertEquals(end - 1L, overview.latestDetailedEventAt)
        }
    }

    @Test
    fun trackGroupingPreservesIdentitiesBindingsOrderingAndLimits() = runBlocking {
        val tracks = repository.getTopTracksByQualifiedPlays(limit = 20)

        assertEquals(
            listOf(fixture.both, fixture.baselineOnly, fixture.compilation, fixture.detailedOnly, fixture.duplicateMetadata),
            tracks.map { it.trackIdentityId }
        )
        val both = tracks.first()
        assertEquals(4L, both.playCounts.totalPlayCount)
        assertEquals(2L, both.playCounts.legacyPlayCount)
        assertEquals(2L, both.playCounts.detailedPlayCount)
        assertEquals(3_500L, both.confirmedDetailedListeningMs)
        assertEquals(1L, both.naturalCompletionCount)
        assertEquals(1L, both.nonQualifiedAttemptCount)
        assertEquals(200L, both.firstKnownPlayAt)
        assertEquals(700L, both.latestKnownPlayAt)
        assertEquals("local:both:available", both.binding?.referenceKey)
        assertTrue(requireNotNull(both.binding).isCurrentlyAvailable)

        val duplicate = tracks.single { it.trackIdentityId == fixture.duplicateMetadata }
        assertEquals(tracks[1].title, duplicate.title)
        assertNull(duplicate.binding)
        assertEquals(2, repository.getTopTracksByQualifiedPlays(limit = 2).size)

        val byTime = repository.getTopTracksByListeningTime(limit = 20)
        assertEquals(fixture.both, byTime[0].trackIdentityId)
        assertEquals(fixture.detailedOnly, byTime[1].trackIdentityId)
        assertTrue(byTime.any { it.trackIdentityId == fixture.unknownAttemptOnly })
    }

    @Test
    fun albumAndArtistReportsGroupForReportingWithoutMergingTrackIdentities() = runBlocking {
        val albums = repository.getTopAlbums(limit = 10)
        val alphaOne = albums.first()
        assertEquals("One", alphaOne.album)
        assertEquals("Alpha", alphaOne.albumArtist)
        assertEquals(8L, alphaOne.playCounts.totalPlayCount)
        assertEquals(3L, alphaOne.trackCount)
        assertEquals(4_200L, alphaOne.confirmedDetailedListeningMs)
        assertEquals("Compilation", albums[1].album)
        assertEquals("Various Artists", albums[1].albumArtist)

        val artists = repository.getTopArtists(limit = 10)
        val alpha = artists.first()
        assertEquals("Alpha", alpha.artist)
        assertEquals(9L, alpha.playCounts.totalPlayCount)
        assertEquals(4L, alpha.distinctTrackCount)
        assertEquals(2L, alpha.distinctAlbumCount)
        assertEquals("Guest", artists[1].artist)

        val detailedCd = ListeningStatsFilter(
            sources = setOf(ListeningSource.CDPLAYA),
            includeLegacyBaseline = false
        )
        assertEquals(2L, repository.getTopAlbums(10, detailedCd).first().playCounts.totalPlayCount)

        val alternateDisplay = identity("Alternate", "ALPHA", "ONE", "ALPHA")
        database.listeningEventDao().insert(
            event("alternate-display", alternateDisplay, 1_000L, 200L, true)
        )
        val deterministicAlbum = repository.getTopAlbums(10).first()
        val deterministicArtist = repository.getTopArtists(10).first()
        assertEquals("ONE", deterministicAlbum.album)
        assertEquals("ALPHA", deterministicAlbum.albumArtist)
        assertEquals("ALPHA", deterministicArtist.artist)

        val unknown = identity("Unknown Group", "", "", null)
        database.listeningEventDao().insert(event("unknown-qualified", unknown, 1_100L, 100L, true))
        assertTrue(repository.getTopAlbums(20).any { it.groupingKey.endsWith("|<unknown-album>") && it.album == "Unknown Album" })
        assertTrue(repository.getTopArtists(20).any { it.groupingKey == "<unknown-artist>" && it.artist == "Unknown Artist" })
    }

    @Test
    fun projectionsUseQualifiedFactsAndStableTieBreakers() = runBlocking {
        val recent = repository.getRecentlyPlayed(limit = 20)
        assertEquals(
            listOf(fixture.compilation, fixture.detailedOnly, fixture.duplicateMetadata, fixture.both, fixture.baselineOnly),
            recent.map { it.track.trackIdentityId }
        )
        assertFalse(recent.any { it.track.trackIdentityId == fixture.unknownAttemptOnly })

        val most = repository.getMostPlayed(limit = 20)
        assertEquals(
            listOf(fixture.both, fixture.baselineOnly, fixture.compilation, fixture.detailedOnly, fixture.duplicateMetadata),
            most.map { it.track.trackIdentityId }
        )
    }

    @Test
    fun pendingImportedEventIsExcludedFromOverviewTrendTopAndHistoryProjections() = runBlocking {
        val pendingTrack = identity("Pending", "Imported", "Hidden", "Imported")
        val overviewBefore = repository.getAllTimeOverview()
        val topBefore = repository.getTopTracksByQualifiedPlays(20)
        val recentBefore = repository.getRecentlyPlayed(20)
        val mostBefore = repository.getMostPlayed(20)
        val detailedBefore = repository.getRecentDetailedEvents(20)

        database.listeningEventDao().insert(
            ListeningEventEntity(
                eventUuid = "pending-hidden", source = ListeningSource.SPOTIFY_IMPORT,
                trackIdentityId = pendingTrack, localTrackBindingId = null, playbackSessionId = null,
                startedAt = null, endedAt = 50_000, attributionAt = 50_000,
                timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
                listenedMs = 30_000, trackDurationMs = 60_000, qualifiedAsPlay = true,
                qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
                qualificationRuleVersion = 1, qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                endReason = null, completionClassification = ListeningCompletionClassification.NONE,
                publicationState = ListeningEventPublicationState.IMPORT_PENDING,
                sourceEventKey = "pending-source", importBatchId = null, createdAt = 50_001
            )
        )

        assertEquals(overviewBefore, repository.getAllTimeOverview())
        assertEquals(topBefore, repository.getTopTracksByQualifiedPlays(20))
        assertEquals(recentBefore, repository.getRecentlyPlayed(20))
        assertEquals(mostBefore, repository.getMostPlayed(20))
        assertEquals(detailedBefore, repository.getRecentDetailedEvents(20))
        val bounded = repository.getDetailedOverview(ListeningDateRange(49_000, 51_000))
        assertEquals(0L, bounded.detailedEventCount)
        assertNull(bounded.firstDetailedEventAt)
        assertNull(bounded.latestDetailedEventAt)
        val trend = database.listeningStatsDao().getTrendBuckets(
            ListeningStatsQueries.trend(
                listOf(AnalyticsBucketBoundary(
                    0, 49_000, 51_000, ZonedDateTime.now(), AnalyticsBucketGranularity.DAY
                )),
                listOf(ListeningSource.SPOTIFY_IMPORT.storageValue)
            )
        )
        assertEquals(0L, trend.single().totalAttemptCount)
    }

    @Test
    fun qualifiedDetailedInsertionReactivelyUpdatesBothProductionProjectionsWithoutAggregateWrite() =
        runBlocking {
            val trackId = identity("Reactive", "Native", "Flow", "Native")
            binding(trackId, "local:reactive", missingSince = null, lastSeenAt = 5_000L)
            val aggregateRowsBefore = songPlayStatsCount()
            val updated = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000L) {
                    repository.observeProductionHistory().first { snapshot ->
                        snapshot.recentlyPlayed.any { it.track.trackIdentityId == trackId }
                    }
                }
            }

            database.listeningEventDao().insert(
                event("reactive-qualified", trackId, 5_000L, 30_000L, true)
            )
            val snapshot = updated.await()

            assertEquals(trackId, snapshot.recentlyPlayed.first().track.trackIdentityId)
            val mostPlayed = snapshot.mostPlayed.single { it.track.trackIdentityId == trackId }
            assertEquals(1L, mostPlayed.track.playCounts.totalPlayCount)
            assertEquals(aggregateRowsBefore, songPlayStatsCount())
        }

    @Test
    fun nonQualifiedInsertionInvalidatesButDoesNotChangeProductionOrderingOrCounts() = runBlocking {
        val before = repository.observeProductionHistory().first()

        database.listeningEventDao().insert(
            event("reactive-nonqualified", fixture.detailedOnly, 6_000L, 10L, false)
        )
        val storedEvent = requireNotNull(
            database.listeningEventDao().getByUuid("reactive-nonqualified")
        )
        val recentlyPlayedAfter = repository.getRecentlyPlayed(limit = 20)
        val mostPlayedAfter = repository.getMostPlayed(limit = 20)

        assertFalse(storedEvent.qualifiedAsPlay)
        assertEquals(
            before.recentlyPlayed.map { it.track.trackIdentityId },
            recentlyPlayedAfter.map { it.track.trackIdentityId }
        )
        assertEquals(
            before.mostPlayed.map { it.track.trackIdentityId to it.track.playCounts.totalPlayCount },
            mostPlayedAfter.map { it.track.trackIdentityId to it.track.playCounts.totalPlayCount }
        )
        assertFalse(recentlyPlayedAfter.any { it.track.latestKnownPlayAt == 6_000L })
    }

    @Test
    fun bindingInsertionInvalidatesAndPublishesAllKnownBindingsPreferredFirst() = runBlocking {
        val trackId = identity("Binding", "Native", "Flow", "Native")
        val duplicateMetadataTrackId = identity("Binding", "Native", "Flow", "Native")
        baseline(trackId, 1, 1L, 2L)
        baseline(duplicateMetadataTrackId, 1, 1L, 2L)
        binding(trackId, "local:binding:preferred", missingSince = null, lastSeenAt = 10L)
        binding(
            duplicateMetadataTrackId,
            "local:binding:duplicate-identity",
            missingSince = null,
            lastSeenAt = 10L
        )
        val emissions = Channel<io.github.rsgarrido.sazanami.data.ProductionListeningHistoryProjections>(
            Channel.UNLIMITED
        )
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeProductionHistory().collect(emissions::send)
        }

        try {
            val before = withTimeout(5_000L) { emissions.receive() }
            val beforeTrack = before.recentlyPlayed.single { it.track.trackIdentityId == trackId }
            assertEquals(
                listOf("local:binding:preferred"),
                beforeTrack.track.knownBindings.map { it.referenceKey }
            )

            binding(
                trackId,
                "local:binding:new-missing",
                missingSince = 20L,
                lastSeenAt = 20L
            )
            val storedBindings = database.localTrackBindingDao().getForTrackIdentity(trackId)
            assertEquals(
                setOf("local:binding:preferred", "local:binding:new-missing"),
                storedBindings.mapTo(mutableSetOf()) { it.referenceKey }
            )

            val after = withTimeout(5_000L) { emissions.receive() }
            val afterTrack = after.recentlyPlayed.single { it.track.trackIdentityId == trackId }
            val duplicateMetadataTrack = after.recentlyPlayed.single {
                it.track.trackIdentityId == duplicateMetadataTrackId
            }

            assertEquals("local:binding:preferred", afterTrack.track.binding?.referenceKey)
            assertEquals(
                listOf("local:binding:preferred", "local:binding:new-missing"),
                afterTrack.track.knownBindings.map { it.referenceKey }
            )
            assertEquals(
                listOf("local:binding:duplicate-identity"),
                duplicateMetadataTrack.track.knownBindings.map { it.referenceKey }
            )
            assertEquals(
                setOf(trackId, duplicateMetadataTrackId),
                after.recentlyPlayed
                    .filter { it.track.title == "Binding" }
                    .mapTo(mutableSetOf()) { it.track.trackIdentityId }
            )
        } finally {
            collector.cancelAndJoin()
            emissions.close()
        }
    }

    @Test
    fun rapidQualifiedInsertsConflateToACompleteLatestSnapshot() = runBlocking {
        val trackId = identity("Rapid", "Native", "Flow", "Native")
        binding(trackId, "local:rapid", missingSince = null, lastSeenAt = 10L)
        val latest = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) {
                repository.observeProductionHistory().first { snapshot ->
                    snapshot.mostPlayed
                        .firstOrNull { it.track.trackIdentityId == trackId }
                        ?.track?.playCounts?.detailedPlayCount == 25L
                }
            }
        }

        database.listeningEventDao().insert(
            (0 until 25).map { index ->
                event("rapid-$index", trackId, 10_000L + index, 1_000L, true)
            }
        )
        val snapshot = latest.await()

        assertEquals(
            25L,
            snapshot.mostPlayed.single { it.track.trackIdentityId == trackId }
                .track.playCounts.detailedPlayCount
        )
    }

    @Test
    fun cancellingProductionObservationStopsTheCollectorCleanly() = runBlocking {
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeProductionHistory().collect { }
        }

        assertTrue(collector.isActive)
        collector.cancelAndJoin()

        assertFalse(collector.isActive)
        database.listeningEventDao().insert(
            event("after-cancel", fixture.detailedOnly, 20_000L, 1_000L, true)
        )
        assertFalse(collector.isActive)
    }

    @Test
    fun recentDetailedEventsIncludeEveryAttemptWithFiltersAndStableLimits() = runBlocking {
        val latest = repository.getRecentDetailedEvents(limit = 3)
        assertEquals(listOf("unknown-nonqualified", "lastfm-nonqualified", "compilation-natural"), latest.map { it.eventUuid })
        assertFalse(latest.first().qualifiedAsPlay)

        val cdplaya = repository.getRecentDetailedEvents(
            limit = 20,
            range = ListeningDateRange(600L, 900L),
            sources = setOf(ListeningSource.CDPLAYA)
        )
        assertEquals(
            listOf("compilation-natural", "duplicate-qualified", "detailed-qualified", "both-nonqualified"),
            cdplaya.map { it.eventUuid }
        )
        assertTrue(cdplaya.none { it.source != ListeningSource.CDPLAYA })
    }

    @Test
    fun boundedQueriesHandleLargeDetailedHistoryWithoutReturningTheFullTable() = runBlocking {
        val events = (0 until 1_500).map { index ->
            event(
                uuid = "bulk-$index",
                trackIdentityId = fixture.detailedOnly,
                startedAt = 10_000L + index,
                listenedMs = 10L,
                qualified = index % 2 == 0
            )
        }
        database.listeningEventDao().insert(events)

        val recent = repository.getRecentDetailedEvents(limit = 25)
        assertEquals(25, recent.size)
        val overview = repository.getDetailedOverview(ListeningDateRange(10_000L, 11_500L))
        assertEquals(1_500L, overview.detailedEventCount)
        assertEquals(750L, overview.qualifiedDetailedPlayCount)
        assertEquals(15_000L, overview.listeningTime.confirmedDetailedListeningMs)
    }

    @Test
    fun keyDateAndSourceQueryUsesExistingCompositeIndex() {
        val plan = database.query(
            SimpleSQLiteQuery(
                "EXPLAIN QUERY PLAN SELECT id FROM listening_events " +
                    "WHERE source = 'cdplaya' AND publicationState != 'import_pending' AND attributionAt >= 100 AND attributionAt < 200"
            )
        ).use { cursor ->
            buildList {
                val detailColumn = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) add(cursor.getString(detailColumn))
            }
        }
        assertTrue(plan.joinToString().contains("index_listening_events_source_publicationState_attributionAt"))
    }

    private suspend fun insertFixture(): Fixture {
        val baselineOnly = identity("Twin", "Alpha", "One", "Alpha")
        val both = identity("Twin", "Alpha", "One", "Alpha")
        val detailedOnly = identity("Detailed", "Alpha", "Two", "Alpha")
        val compilation = identity("Compilation Track", "Guest", "Compilation", "Various Artists")
        val duplicateMetadata = identity("Twin", "Alpha", "One", "Alpha")
        val unknownAttemptOnly = identity("Unknown Attempt", "", "", null)

        baseline(baselineOnly, 3, 100L, 300L)
        baseline(both, 2, 200L, 400L)
        baseline(compilation, 1, 250L, 450L)
        binding(baselineOnly, "local:baseline", missingSince = null, lastSeenAt = 100L)
        binding(both, "local:both:missing", missingSince = 900L, lastSeenAt = 1_000L)
        binding(both, "local:both:available", missingSince = null, lastSeenAt = 500L)

        database.listeningEventDao().insert(
            listOf(
                event("both-natural", both, 500L, 1_000L, true, ListeningSource.CDPLAYA, ListeningEndReason.NATURAL_END),
                event("both-nonqualified", both, 600L, 500L, false),
                event("both-spotify", both, 700L, 2_000L, true, ListeningSource.SPOTIFY_IMPORT),
                event("detailed-qualified", detailedOnly, 800L, 3_000L, true, endReason = ListeningEndReason.ERROR),
                event("lastfm-nonqualified", detailedOnly, 900L, 400L, false, ListeningSource.LASTFM_IMPORT, ListeningEndReason.ERROR),
                event("compilation-natural", compilation, 850L, 1_500L, true, endReason = ListeningEndReason.NATURAL_END),
                event("duplicate-qualified", duplicateMetadata, 800L, 700L, true),
                event("unknown-nonqualified", unknownAttemptOnly, 950L, 100L, false)
            )
        )
        return Fixture(baselineOnly, both, detailedOnly, compilation, duplicateMetadata, unknownAttemptOnly)
    }

    private suspend fun identity(title: String, artist: String, album: String, albumArtist: String?): Long =
        database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = title,
                artistSnapshot = artist,
                albumSnapshot = album,
                albumArtistSnapshot = albumArtist,
                durationMsSnapshot = 60_000L,
                normalizedTitle = title.lowercase(),
                normalizedArtist = artist.lowercase(),
                normalizedAlbum = album.lowercase(),
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

    private suspend fun baseline(trackId: Long, count: Int, first: Long, latest: Long) {
        database.legacyListeningBaselineDao().insert(
            LegacyListeningBaselineEntity(trackId, count, first, latest, "legacy:$trackId", 1L)
        )
    }

    private suspend fun binding(trackId: Long, key: String, missingSince: Long?, lastSeenAt: Long) {
        database.localTrackBindingDao().insert(
            LocalTrackBindingEntity(
                trackIdentityId = trackId,
                referenceKey = key,
                mediaStoreId = trackId,
                volumeName = "external",
                contentUri = "content://media/$trackId/$key",
                relativePath = "Music/",
                displayName = "$trackId.flac",
                absolutePath = null,
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 2_000L,
                durationMsSnapshot = 60_000L,
                legacyStableKey = "legacy-$trackId",
                portableKey = null,
                portableKeyVersion = 1,
                firstSeenAt = 1L,
                lastSeenAt = lastSeenAt,
                missingSince = missingSince
            )
        )
    }

    private fun event(
        uuid: String,
        trackIdentityId: Long,
        startedAt: Long,
        listenedMs: Long,
        qualified: Boolean,
        source: ListeningSource = ListeningSource.CDPLAYA,
        endReason: ListeningEndReason = ListeningEndReason.STOPPED
    ) = ListeningEventEntity(
        eventUuid = uuid,
        source = source,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = null,
        playbackSessionId = "session:$uuid",
        startedAt = startedAt,
        endedAt = startedAt + 50L,
        listenedMs = listenedMs,
        trackDurationMs = 60_000L,
        qualifiedAsPlay = qualified,
        qualificationReason = when {
            endReason == ListeningEndReason.NATURAL_END -> ListeningQualificationReason.NATURAL_END
            qualified -> ListeningQualificationReason.TIME_THRESHOLD
            else -> ListeningQualificationReason.NONE
        },
        qualificationRuleVersion = 1,
        endReason = endReason,
        sourceEventKey = if (source == ListeningSource.CDPLAYA) null else "source:$uuid",
        importBatchId = null,
        createdAt = startedAt + 50L
    )

    private fun songPlayStatsCount(): Long = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM song_play_stats")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private data class Fixture(
        val baselineOnly: Long,
        val both: Long,
        val detailedOnly: Long,
        val compilation: Long,
        val duplicateMetadata: Long,
        val unknownAttemptOnly: Long
    )
}
