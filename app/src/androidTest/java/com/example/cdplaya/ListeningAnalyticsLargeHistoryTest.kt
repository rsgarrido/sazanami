package com.example.cdplaya

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.AnalyticsBucketBoundary
import com.example.cdplaya.data.AnalyticsBucketGranularity
import com.example.cdplaya.data.AnalyticsRangeSelection
import com.example.cdplaya.data.AnalyticsRangePreset
import com.example.cdplaya.data.AnalyticsZoneIdProvider
import com.example.cdplaya.data.ListeningAnalyticsBucketBuilder
import com.example.cdplaya.data.ListeningAnalyticsRangeResolver
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningStatsQueries
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningAnalyticsLargeHistoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun tenThousandEventsAggregateExactlyThroughBoundedRealQuery() = runBlocking {
        val id = database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = "Bulk",
                artistSnapshot = "Scale",
                albumSnapshot = "History",
                albumArtistSnapshot = "Scale",
                durationMsSnapshot = 100L,
                normalizedTitle = "bulk",
                normalizedArtist = "scale",
                normalizedAlbum = "history",
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val base = Instant.parse("1500-01-01T00:00:00Z").toEpochMilli()
        val events = (0 until 10_000).map { index ->
            ListeningEventEntity(
                eventUuid = "analytics-bulk-$index",
                source = ListeningSource.CDPLAYA,
                trackIdentityId = id,
                localTrackBindingId = null,
                playbackSessionId = null,
                startedAt = base + index,
                endedAt = base + index + 10L,
                listenedMs = 10L,
                trackDurationMs = 100L,
                qualifiedAsPlay = index % 2 == 0,
                qualificationReason = if (index % 2 == 0) ListeningQualificationReason.TIME_THRESHOLD else ListeningQualificationReason.NONE,
                qualificationRuleVersion = 1,
                endReason = if (index % 4 == 0) ListeningEndReason.NATURAL_END else ListeningEndReason.STOPPED,
                sourceEventKey = null,
                importBatchId = null,
                createdAt = base + index + 10L
            )
        }
        events.chunked(1_000).forEach { database.listeningEventDao().insert(it) }
        val resolver = ListeningAnalyticsRangeResolver(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")),
            AnalyticsZoneIdProvider { ZoneId.of("UTC") }
        )
        val selection = AnalyticsRangeSelection.Custom(LocalDate.of(1200, 1, 1), LocalDate.of(2026, 12, 31))
        val resolved = resolver.resolve(selection)
        val boundaries = ListeningAnalyticsBucketBuilder.build(resolved)
        assertTrue(boundaries.size <= 400)

        val snapshot = ListeningStatsRepository(database).getAnalyticsSnapshot(resolved)
        assertEquals(10_000L, snapshot.trend.sumOf { it.totalAttemptCount })
        assertEquals(5_000L, snapshot.trend.sumOf { it.qualifiedPlayCount })
        assertEquals(2_500L, snapshot.trend.sumOf { it.naturalCompletionCount })
        assertEquals(100_000L, snapshot.trend.sumOf { it.listenedMs })
        assertEquals(boundaries.size, snapshot.trend.size)
        assertTrue(snapshot.trend.any { it.totalAttemptCount == 0L })
    }

    @Test
    fun existingCompositeIndexesCoverSourceDateAndQualifiedDatePlans() {
        val sourcePlan = queryPlan(
            "SELECT id FROM listening_events WHERE source = 'cdplaya' AND publicationState != 'import_pending' AND attributionAt >= 100 AND attributionAt < 200"
        )
        val qualifiedPlan = queryPlan(
            "SELECT id FROM listening_events WHERE qualifiedAsPlay = 1 AND publicationState != 'import_pending' AND attributionAt >= 100 AND attributionAt < 200"
        )
        val trackPlan = queryPlan(
            "SELECT id FROM listening_events WHERE trackIdentityId = 1 AND attributionAt >= 100 AND attributionAt < 200"
        )
        val bucketJoinPlan = queryPlan(
            "WITH buckets(startInclusive, endExclusive) AS (VALUES (100, 200)) " +
                "SELECT COUNT(e.id) FROM buckets b LEFT JOIN listening_events e " +
                "ON e.source = 'cdplaya' AND e.publicationState != 'import_pending' AND e.attributionAt >= b.startInclusive AND e.attributionAt < b.endExclusive"
        )
        assertTrue(sourcePlan.contains("index_listening_events_source_publicationState_attributionAt"))
        assertTrue(qualifiedPlan.contains("index_listening_events_qualifiedAsPlay_publicationState_attributionAt"))
        assertTrue(trackPlan.contains("index_listening_events_trackIdentityId_attributionAt"))
        assertTrue(bucketJoinPlan.contains("index_listening_events_source_publicationState_attributionAt"))
    }

    @Test
    fun hundredThousandEventsRemainSqlAggregatedWithBoundedSnapshotResults() = runBlocking {
        val id = database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = "Hundred thousand",
                artistSnapshot = "Scale",
                albumSnapshot = "Query plan",
                albumArtistSnapshot = "Scale",
                durationMsSnapshot = 100L,
                normalizedTitle = "hundred thousand",
                normalizedArtist = "scale",
                normalizedAlbum = "query plan",
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val base = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        repeat(100) { batchIndex ->
            val events = List(1_000) { offset ->
                val index = batchIndex * 1_000 + offset
                ListeningEventEntity(
                    eventUuid = "analytics-100k-$index",
                    source = ListeningSource.CDPLAYA,
                    trackIdentityId = id,
                    localTrackBindingId = null,
                    playbackSessionId = null,
                    startedAt = base + index,
                    endedAt = base + index + 1L,
                    listenedMs = 1L,
                    trackDurationMs = 100L,
                    qualifiedAsPlay = index % 10 == 0,
                    qualificationReason = if (index % 10 == 0) {
                        ListeningQualificationReason.TIME_THRESHOLD
                    } else {
                        ListeningQualificationReason.NONE
                    },
                    qualificationRuleVersion = 1,
                    endReason = ListeningEndReason.STOPPED,
                    sourceEventKey = null,
                    importBatchId = null,
                    createdAt = base + index + 1L
                )
            }
            database.listeningEventDao().insert(events)
        }
        val resolver = ListeningAnalyticsRangeResolver(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")),
            AnalyticsZoneIdProvider { ZoneId.of("UTC") }
        )
        val snapshot = ListeningStatsRepository(database).getAnalyticsSnapshot(
            resolver.resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME))
        )

        assertEquals(100_000L, snapshot.overview.detailedEventCount)
        assertEquals(100_000L, snapshot.overview.listeningTime.confirmedDetailedListeningMs)
        assertEquals(10_000L, snapshot.overview.qualifiedDetailedPlayCount)
        assertEquals(100_000L, snapshot.trend.sumOf { it.totalAttemptCount })
        assertTrue(snapshot.trend.size <= ListeningAnalyticsBucketBuilder.MAX_BUCKET_COUNT)
        assertEquals(1, snapshot.topTracks.size)
        assertTrue(snapshot.topAlbums.size <= 5)
        assertTrue(snapshot.topArtists.size <= 5)
    }

    @Test
    fun maximumFourHundredBucketQueryStaysWithinSqliteBindLimit() = runBlocking {
        val boundaries = (0 until ListeningAnalyticsBucketBuilder.MAX_BUCKET_COUNT).map { index ->
            AnalyticsBucketBoundary(
                index = index,
                startInclusive = index.toLong(),
                endExclusive = index.toLong() + 1L,
                localStart = Instant.ofEpochMilli(index.toLong()).atZone(ZoneId.of("UTC")),
                granularity = AnalyticsBucketGranularity.DAY
            )
        }
        val rows = database.listeningStatsDao().getTrendBuckets(
            ListeningStatsQueries.trend(
                boundaries,
                ListeningSource.entries.map(ListeningSource::storageValue)
            )
        )
        assertEquals(400, rows.size)
        assertTrue(rows.all { it.totalAttemptCount == 0L && it.listenedMs == 0L })
    }

    private fun queryPlan(sql: String): String = database.query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql")).use { cursor ->
        buildList {
            val column = cursor.getColumnIndexOrThrow("detail")
            while (cursor.moveToNext()) add(cursor.getString(column))
        }.joinToString()
    }
}
