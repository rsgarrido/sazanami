package com.example.cdplaya

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningDateRange
import com.example.cdplaya.data.AnalyticsBucketBoundary
import com.example.cdplaya.data.AnalyticsBucketGranularity
import com.example.cdplaya.data.ListeningIdentityReconciliationLinkResult
import com.example.cdplaya.data.ListeningIdentityReconciliationRepository
import com.example.cdplaya.data.ListeningStatsFilter
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.LegacyListeningBaselineEntity
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningIdentityReconciliationEntity
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningStatsQueries
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningTrackExternalIdEntity
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.SongRatingEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
class ListeningStatsReconciliationTest {
    private lateinit var database: AppDatabase
    private lateinit var stats: ListeningStatsRepository
    private lateinit var reconciliation: ListeningIdentityReconciliationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        stats = ListeningStatsRepository(database)
        reconciliation = ListeningIdentityReconciliationRepository(database) { 50_000L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun canonicalizesBeforeGroupingRankingAndLimitWithoutChangingFacts() = runBlocking {
        val target = identity("It's Me", "Canonical Artist", "Keep Me Fed", "Canonical Artist")
        val sourceA = identity("It’s Me", "Provider Artist", "Provider Album", "Provider Artist")
        val sourceB = identity("ITS ME", "Other Provider Artist", "Live Snapshot", null)
        binding(target, "local:target:first")
        binding(target, "local:target:second", lastSeenAt = 2L)
        database.songRatingDao().insert(
            listOf(
                SongRatingEntity(sourceA, 5, 1L, 1L),
                SongRatingEntity(target, 4, 2L, 2L)
            )
        )
        baseline(target, 2, 20L, 30L)
        baseline(sourceA, 3, 10L, 25L)
        events(target, "native", 5, 1_000L, ListeningSource.CDPLAYA)
        events(sourceA, "source-a", 20, 100L, ListeningSource.SPOTIFY_IMPORT)
        events(sourceB, "source-b", 30, 500L, ListeningSource.SPOTIFY_IMPORT)
        database.listeningEventDao().insert(
            event(
                uuid = "source-a-short",
                trackIdentityId = sourceA,
                at = 350L,
                listenedMs = 28_500L,
                qualified = false,
                source = ListeningSource.SPOTIFY_IMPORT
            )
        )
        val other = identity("Other", "Other Artist", "Other Album", "Other Artist")
        events(other, "other", 50, 2_000L, ListeningSource.CDPLAYA)

        val overviewBefore = stats.getAllTimeOverview()
        val customRangeBefore = stats.getDetailedOverview(ListeningDateRange(0L, 10_000L))
        val trendQuery = ListeningStatsQueries.trend(
            listOf(
                AnalyticsBucketBoundary(
                    0,
                    0L,
                    10_000L,
                    ZonedDateTime.now(),
                    AnalyticsBucketGranularity.DAY
                )
            ),
            ListeningSource.entries.map(ListeningSource::storageValue)
        )
        val trendBefore = database.listeningStatsDao().getTrendBuckets(trendQuery)
        val rawBefore = stats.getRecentDetailedEvents(1_000).associateBy { it.eventUuid }
        assertTrue(reconciliation.link(sourceA, target) is ListeningIdentityReconciliationLinkResult.Linked)
        assertTrue(reconciliation.link(sourceB, target) is ListeningIdentityReconciliationLinkResult.Linked)

        val ranked = stats.getTopTracksByQualifiedPlays(10)
        assertEquals(target, ranked.first().trackIdentityId)
        assertEquals("It's Me", ranked.first().title)
        assertEquals("Canonical Artist", ranked.first().artist)
        assertEquals("Keep Me Fed", ranked.first().album)
        assertEquals(60L, ranked.first().playCounts.totalPlayCount)
        assertEquals(55L, ranked.first().playCounts.detailedPlayCount)
        assertEquals(5L, ranked.first().playCounts.legacyPlayCount)
        assertEquals(4, ranked.first().effectiveRating)
        assertEquals(56L, ranked.first().detailedEventCount)
        assertEquals(1L, ranked.first().nonQualifiedAttemptCount)

        val canonicalAlbum = stats.getTopAlbums(10).first()
        assertEquals("Keep Me Fed", canonicalAlbum.album)
        assertEquals("Canonical Artist", canonicalAlbum.albumArtist)
        assertEquals(60L, canonicalAlbum.playCounts.totalPlayCount)
        val canonicalArtist = stats.getTopArtists(10).first()
        assertEquals("Canonical Artist", canonicalArtist.artist)
        assertEquals(60L, canonicalArtist.playCounts.totalPlayCount)

        assertEquals(overviewBefore, stats.getAllTimeOverview())
        assertEquals(customRangeBefore, stats.getDetailedOverview(ListeningDateRange(0L, 10_000L)))
        assertEquals(trendBefore, database.listeningStatsDao().getTrendBuckets(trendQuery))
        val rawAfter = stats.getRecentDetailedEvents(1_000).associateBy { it.eventUuid }
        assertEquals(rawBefore, rawAfter)
        assertEquals(sourceA, rawAfter.getValue("source-a-0").trackIdentityId)
        assertEquals(ListeningSource.SPOTIFY_IMPORT, rawAfter.getValue("source-a-0").source)
        assertEquals(56L, database.listeningEventDao().countForTrackIdentity(sourceA) +
            database.listeningEventDao().countForTrackIdentity(sourceB) +
            database.listeningEventDao().countForTrackIdentity(target))

        assertTrue(reconciliation.unlink(sourceA))
        val afterPartialUnlink = stats.getTopTracksByQualifiedPlays(10)
        val restoredSource = afterPartialUnlink.single { it.trackIdentityId == sourceA }
        val remainingCanonical = afterPartialUnlink.single { it.trackIdentityId == target }
        assertEquals("It’s Me", restoredSource.title)
        assertEquals("Provider Artist", restoredSource.artist)
        assertEquals("Provider Album", restoredSource.album)
        assertEquals(23L, restoredSource.playCounts.totalPlayCount)
        assertEquals(5, restoredSource.effectiveRating)
        assertEquals(37L, remainingCanonical.playCounts.totalPlayCount)
        assertEquals(4, remainingCanonical.effectiveRating)
        assertTrue(stats.getTopArtists(10).any { it.artist == "Provider Artist" })
        assertTrue(stats.getTopAlbums(10).any { it.album == "Provider Album" })
        assertEquals(overviewBefore, stats.getAllTimeOverview())
    }

    @Test
    fun canonicalMetricsAreBatchableRangeAwareRatedAndKeepZeroPlayableRows() = runBlocking {
        val target = identity("Target", "Target Artist", "Target Album", "Target Artist")
        val neverPlayed = identity("Never", "Quiet", "None", "Quiet")
        val unavailable = identity("Unavailable", "Quiet", "None", "Quiet")
        val source = identity("Historical", "Provider", "Old Album", "Provider")
        binding(target, "local:target")
        binding(neverPlayed, "local:never")
        binding(unavailable, "local:missing", missingSince = 99L)
        database.songRatingDao().upsert(SongRatingEntity(source, 5, 1L, 1L))
        database.listeningTrackExternalIdDao().insert(
            ListeningTrackExternalIdEntity(
                trackIdentityId = source,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                externalId = "spotify:historical",
                createdAt = 1L,
                lastSeenAt = 1L
            )
        )
        events(source, "old-import", 2, 100L, ListeningSource.SPOTIFY_IMPORT)
        database.listeningEventDao().insert(
            event("old-short", source, 150L, 20_000L, false, ListeningSource.SPOTIFY_IMPORT)
        )
        database.listeningEventDao().insert(
            event(
                "native-complete",
                target,
                300L,
                60_000L,
                true,
                ListeningSource.CDPLAYA,
                ListeningCompletionClassification.NATIVE_NATURAL
            )
        )
        assertTrue(reconciliation.link(source, target) is ListeningIdentityReconciliationLinkResult.Linked)

        val lifetime = stats.getCanonicalListeningMetricsForPlayableTracks()
        assertEquals(setOf(target, neverPlayed), lifetime.keys)
        val targetMetrics = lifetime.getValue(target)
        assertEquals(3L, targetMetrics.playCounts.totalPlayCount)
        assertEquals(4L, targetMetrics.detailedAttemptCount)
        assertEquals(1L, targetMetrics.nonQualifiedAttemptCount)
        assertEquals(1L, targetMetrics.naturalCompletionCount)
        assertEquals(200_000L, targetMetrics.confirmedDetailedListeningMs)
        assertEquals(100L, targetMetrics.firstPlayedAt)
        assertEquals(300L, targetMetrics.lastPlayedAt)
        assertEquals(300L, targetMetrics.latestDetailedEventAt)
        assertNull(targetMetrics.effectiveRating)
        assertEquals(0L, lifetime.getValue(neverPlayed).playCounts.totalPlayCount)
        assertNull(lifetime.getValue(neverPlayed).firstPlayedAt)
        assertNull(stats.getCanonicalListeningMetricsForPlayableTrack(source))

        database.songRatingDao().upsert(SongRatingEntity(target, 4, 2L, 2L))
        assertEquals(4, stats.getCanonicalListeningMetricsForPlayableTrack(target)?.effectiveRating)
        val ranged = stats.getCanonicalListeningMetricsForPlayableTracks(
            ListeningStatsFilter(
                dateRange = ListeningDateRange(250L, 400L),
                includeLegacyBaseline = false
            )
        )
        assertEquals(1L, ranged.getValue(target).playCounts.totalPlayCount)
        assertEquals(0L, ranged.getValue(neverPlayed).playCounts.totalPlayCount)
        val importedOnly = stats.getCanonicalListeningMetricsForPlayableTrack(
            target,
            ListeningStatsFilter(
                sources = setOf(ListeningSource.SPOTIFY_IMPORT),
                includeLegacyBaseline = false
            )
        )
        assertEquals(2L, importedOnly?.playCounts?.totalPlayCount)
        assertEquals(3L, importedOnly?.detailedAttemptCount)

        database.listeningEventDao().insert(
            event("later-export", source, 500L, 60_000L, true, ListeningSource.SPOTIFY_IMPORT)
        )
        assertEquals(source, database.listeningEventDao().getByUuid("later-export")?.trackIdentityId)
        assertEquals(
            source,
            database.listeningTrackExternalIdDao().find(
                ListeningSource.SPOTIFY_IMPORT,
                "spotify:historical"
            )?.trackIdentityId
        )
        assertEquals(
            4L,
            stats.getCanonicalListeningMetricsForPlayableTrack(target)?.playCounts?.totalPlayCount
        )
    }

    @Test
    fun linkUnlinkAndPartialUnlinkRefreshWithoutRepositoryRecreation() = runBlocking {
        val target = identity("Target", "Target Artist", "Target Album", "Target Artist")
        val sourceA = identity("A", "Artist A", "Album A", "Artist A")
        val sourceB = identity("B", "Artist B", "Album B", "Artist B")
        binding(target, "local:target")
        events(target, "target", 10, 1_000L, ListeningSource.CDPLAYA)
        events(sourceA, "a", 20, 2_000L, ListeningSource.SPOTIFY_IMPORT)
        events(sourceB, "b", 30, 3_000L, ListeningSource.SPOTIFY_IMPORT)
        val globalBefore = stats.getAllTimeOverview()

        val linkedEmission = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) {
                stats.observeProductionHistory().first { snapshot ->
                    snapshot.mostPlayed.singleOrNull { it.track.trackIdentityId == target }
                        ?.track?.playCounts?.totalPlayCount == 60L
                }
            }
        }
        assertTrue(reconciliation.link(sourceA, target) is ListeningIdentityReconciliationLinkResult.Linked)
        assertTrue(reconciliation.link(sourceB, target) is ListeningIdentityReconciliationLinkResult.Linked)
        assertEquals(60L, linkedEmission.await().mostPlayed.first().track.playCounts.totalPlayCount)
        val linkedTopTracks = stats.getTopTracksByQualifiedPlays(10)
        assertEquals(1, linkedTopTracks.size)
        assertEquals(target, linkedTopTracks.single().trackIdentityId)
        assertEquals(60L, linkedTopTracks.single().playCounts.totalPlayCount)
        assertEquals(globalBefore, stats.getAllTimeOverview())

        val partialEmission = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) {
                stats.observeCanonicalListeningMetricsForPlayableTracks().first { metrics ->
                    metrics[target]?.playCounts?.totalPlayCount == 40L
                }
            }
        }
        assertTrue(reconciliation.unlink(sourceA))
        assertEquals(40L, partialEmission.await().getValue(target).playCounts.totalPlayCount)
        val partiallyUnlinked = stats.getTopTracksByQualifiedPlays(10)
        assertEquals(20L, partiallyUnlinked.single { it.trackIdentityId == sourceA }.playCounts.totalPlayCount)
        assertEquals(40L, partiallyUnlinked.single { it.trackIdentityId == target }.playCounts.totalPlayCount)
        assertEquals(globalBefore, stats.getAllTimeOverview())

        assertTrue(reconciliation.unlink(sourceB))
        val split = stats.getTopTracksByQualifiedPlays(10)
        assertEquals(10L, split.single { it.trackIdentityId == target }.playCounts.totalPlayCount)
        assertEquals(20L, split.single { it.trackIdentityId == sourceA }.playCounts.totalPlayCount)
        assertEquals(30L, split.single { it.trackIdentityId == sourceB }.playCounts.totalPlayCount)
        assertEquals(globalBefore, stats.getAllTimeOverview())
    }

    @Test
    fun fragmentedAliasesCrossTopNBeforeLimitAndScaleToOneThousandAliases() = runBlocking {
        val target = identity("Canonical", "Artist", "Album", "Artist")
        binding(target, "local:canonical")
        database.listeningEventDao().insert(event("target", target, 10_000L, 1L, true))
        repeat(10) { rank ->
            val competitor = identity("Competitor $rank", "Other", "Other", "Other")
            events(competitor, "competitor-$rank", 50 - rank, 20_000L + rank, ListeningSource.CDPLAYA)
        }
        val aliases = (0 until 1_000).map { index ->
            val source = identity("Alias $index", "Imported", "Imported", "Imported")
            database.listeningEventDao().insert(
                event("alias-$index", source, index.toLong(), 10L, true, ListeningSource.SPOTIFY_IMPORT)
            )
            ListeningIdentityReconciliationEntity(source, target, 30_000L + index)
        }
        database.listeningIdentityReconciliationDao().insert(aliases)

        val topTen = stats.getTopTracksByQualifiedPlays(10)
        assertEquals(target, topTen.first().trackIdentityId)
        assertEquals(1_001L, topTen.first().playCounts.totalPlayCount)
        assertEquals(1, topTen.count { it.trackIdentityId == target })
        assertFalse(topTen.any { row -> aliases.any { it.sourceIdentityId == row.trackIdentityId } })
    }

    @Test
    fun thousandsOfMixedCanonicalGroupsAggregateToOneRowPerResolvedIdentity() = runBlocking {
        val targets = database.listeningTrackIdentityDao().insert(
            (0 until 1_000).map { index -> identityEntity("Target $index", "Artist $index", "Album $index") }
        )
        val sources = database.listeningTrackIdentityDao().insert(
            (0 until 2_000).map { index -> identityEntity("Source $index", "Provider", "Provider Album") }
        )
        database.listeningEventDao().insert(
            targets.mapIndexed { index, target ->
                event("many-target-$index", target, index.toLong(), 1L, true)
            } + sources.mapIndexed { index, source ->
                event(
                    "many-source-$index",
                    source,
                    10_000L + index,
                    1L,
                    true,
                    ListeningSource.SPOTIFY_IMPORT
                )
            }
        )
        database.listeningIdentityReconciliationDao().insert(
            sources.mapIndexed { index, source ->
                ListeningIdentityReconciliationEntity(source, targets[index / 2], 20_000L + index)
            }
        )

        val ranked = stats.getTopTracksByQualifiedPlays(10_000)
        assertEquals(1_000, ranked.size)
        assertTrue(ranked.all { it.playCounts.totalPlayCount == 3L })
        assertEquals(targets.toSet(), ranked.mapTo(mutableSetOf()) { it.trackIdentityId })
    }

    @Test
    fun reconciliationLookupUsesSourcePrimaryKeyAndDoesNotMultiplyBindings() = runBlocking {
        val target = identity("Target", "Artist", "Album", "Artist")
        val source = identity("Source", "Provider", "Provider Album", "Provider")
        binding(target, "local:one")
        binding(target, "local:two", lastSeenAt = 2L)
        events(source, "source", 3, 1_000L, ListeningSource.SPOTIFY_IMPORT)
        assertTrue(reconciliation.link(source, target) is ListeningIdentityReconciliationLinkResult.Linked)

        val row = stats.getTopTracksByQualifiedPlays(10).single()
        assertEquals(3L, row.playCounts.totalPlayCount)
        val production = stats.observeProductionHistory().first().mostPlayed.single().track
        assertEquals(3L, production.playCounts.totalPlayCount)
        assertEquals(2, production.knownBindings.size)

        val plan = database.query(
            SimpleSQLiteQuery(
                "EXPLAIN QUERY PLAN SELECT COALESCE(r.targetIdentityId, e.trackIdentityId) " +
                    "FROM listening_events e LEFT JOIN listening_identity_reconciliations r " +
                    "ON r.sourceIdentityId = e.trackIdentityId " +
                    "WHERE e.publicationState != 'import_pending' " +
                    "GROUP BY COALESCE(r.targetIdentityId, e.trackIdentityId)"
            )
        ).use { cursor ->
            buildList {
                val detail = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }.joinToString("\n")
        }
        assertTrue(plan, plan.contains("SEARCH r USING INTEGER PRIMARY KEY"))
    }

    private suspend fun identity(
        title: String,
        artist: String,
        album: String,
        albumArtist: String?
    ): Long = database.listeningTrackIdentityDao().insert(identityEntity(title, artist, album, albumArtist))

    private fun identityEntity(
        title: String,
        artist: String,
        album: String,
        albumArtist: String? = artist
    ) = ListeningTrackIdentityEntity(
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

    private suspend fun binding(
        trackIdentityId: Long,
        referenceKey: String,
        missingSince: Long? = null,
        lastSeenAt: Long = 1L
    ) {
        database.localTrackBindingDao().insert(
            LocalTrackBindingEntity(
                trackIdentityId = trackIdentityId,
                referenceKey = referenceKey,
                mediaStoreId = trackIdentityId,
                volumeName = "external",
                contentUri = "content://media/$trackIdentityId/$referenceKey",
                relativePath = "Music/",
                displayName = "$trackIdentityId.flac",
                absolutePath = null,
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 2_000L,
                durationMsSnapshot = 60_000L,
                legacyStableKey = "legacy:$referenceKey",
                portableKey = null,
                portableKeyVersion = 1,
                firstSeenAt = 1L,
                lastSeenAt = lastSeenAt,
                missingSince = missingSince
            )
        )
    }

    private suspend fun baseline(trackIdentityId: Long, count: Int, first: Long, last: Long) {
        database.legacyListeningBaselineDao().insert(
            LegacyListeningBaselineEntity(
                trackIdentityId,
                count,
                first,
                last,
                "legacy:$trackIdentityId",
                1L
            )
        )
    }

    private suspend fun events(
        trackIdentityId: Long,
        prefix: String,
        count: Int,
        startAt: Long,
        source: ListeningSource
    ) {
        database.listeningEventDao().insert(
            (0 until count).map { index ->
                event(
                    "$prefix-$index",
                    trackIdentityId,
                    startAt + index,
                    60_000L,
                    true,
                    source
                )
            }
        )
    }

    private fun event(
        uuid: String,
        trackIdentityId: Long,
        at: Long,
        listenedMs: Long,
        qualified: Boolean,
        source: ListeningSource = ListeningSource.CDPLAYA,
        completion: ListeningCompletionClassification = ListeningCompletionClassification.NONE
    ) = ListeningEventEntity(
        eventUuid = uuid,
        source = source,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = null,
        playbackSessionId = if (source == ListeningSource.CDPLAYA) "session:$uuid" else null,
        startedAt = if (source == ListeningSource.CDPLAYA) at else null,
        endedAt = at,
        attributionAt = at,
        timestampEvidence = if (source == ListeningSource.CDPLAYA) {
            ListeningTimestampEvidence.NATIVE_EXACT
        } else {
            ListeningTimestampEvidence.SOURCE_END_ONLY
        },
        listenedMs = listenedMs,
        trackDurationMs = 60_000L,
        qualifiedAsPlay = qualified,
        qualificationReason = if (qualified) {
            ListeningQualificationReason.TIME_THRESHOLD
        } else {
            ListeningQualificationReason.NONE
        },
        qualificationRuleVersion = 1,
        qualificationPolicy = if (source == ListeningSource.SPOTIFY_IMPORT) {
            ListeningQualificationPolicy.SPOTIFY
        } else {
            ListeningQualificationPolicy.CDPLAYA
        },
        endReason = if (completion == ListeningCompletionClassification.NATIVE_NATURAL) {
            ListeningEndReason.NATURAL_END
        } else {
            null
        },
        completionClassification = completion,
        publicationState = if (source == ListeningSource.CDPLAYA) {
            ListeningEventPublicationState.NATIVE
        } else {
            ListeningEventPublicationState.IMPORT_PUBLISHED
        },
        sourceEventKey = if (source == ListeningSource.CDPLAYA) null else "source:$uuid",
        importBatchId = null,
        createdAt = at + 1L
    )
}
