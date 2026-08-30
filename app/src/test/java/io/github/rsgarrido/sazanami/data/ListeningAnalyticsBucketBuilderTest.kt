package io.github.rsgarrido.sazanami.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningAnalyticsBucketBuilderTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun todayUsesElapsedLocalHoursAcrossNormalSpringAndFallDays() {
        assertToday("2026-02-01T12:00:00-08:00", expectedCount = 24)
        val spring = assertToday("2026-03-08T12:00:00-07:00", expectedCount = 23)
        assertTrue(spring.none { it.localStart.hour == 2 })

        val fall = assertToday("2026-11-01T12:00:00-08:00", expectedCount = 25)
        val repeated = fall.filter { it.localStart.hour == 1 }
        assertEquals(2, repeated.size)
        assertTrue(repeated[0].localStart.offset != repeated[1].localStart.offset)
    }

    @Test
    fun namedAndCustomPoliciesSelectDeterministicGranularities() {
        assertPresetGranularity(AnalyticsRangePreset.LAST_7_DAYS, AnalyticsBucketGranularity.DAY, 7)
        assertPresetGranularity(AnalyticsRangePreset.LAST_30_DAYS, AnalyticsBucketGranularity.DAY, 30)
        assertPresetGranularity(AnalyticsRangePreset.THIS_MONTH, AnalyticsBucketGranularity.DAY, 18)
        assertPresetGranularity(AnalyticsRangePreset.THIS_YEAR, AnalyticsBucketGranularity.MONTH, 3)

        assertCustomGranularity("2026-01-01", "2026-03-31", AnalyticsBucketGranularity.DAY, 90)
        assertCustomGranularity("2026-01-15", "2026-07-20", AnalyticsBucketGranularity.MONTH, 7)
        assertCustomGranularity("2020-06-15", "2026-07-20", AnalyticsBucketGranularity.YEAR, 7)
    }

    @Test
    fun multiDayAndMonthRangesCrossDstWithoutGapsOrFixedDayAssumptions() {
        val range = resolver("2026-03-09T12:00:00-07:00").resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2026-03-06"), LocalDate.parse("2026-03-10"))
        )
        val buckets = ListeningAnalyticsBucketBuilder.build(range)
        assertEquals(5, buckets.size)
        assertEquals(23L * 60L * 60L * 1000L, buckets[2].endExclusive - buckets[2].startInclusive)
        assertContiguous(buckets, requireNotNull(range.eventRange))

        val month = resolver("2026-03-18T12:00:00-07:00").resolve(
            AnalyticsRangeSelection.Preset(AnalyticsRangePreset.THIS_MONTH)
        )
        assertContiguous(ListeningAnalyticsBucketBuilder.build(month), requireNotNull(month.eventRange))
    }

    @Test
    fun allTimeUsesDetailedBoundsOnlyAndAdaptsFromMonthsToYears() {
        val allTime = resolver("2026-03-18T12:00:00-07:00").resolve(
            AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME)
        )
        assertTrue(ListeningAnalyticsBucketBuilder.build(allTime, null).isEmpty())

        val short = ListeningAnalyticsBucketBuilder.build(
            allTime,
            DetailedListeningEventBounds(epoch("2024-01-20T10:00:00-08:00"), epoch("2026-12-15T10:00:00-08:00"))
        )
        assertEquals(AnalyticsBucketGranularity.MONTH, short.singleGranularity())
        assertEquals(36, short.size)

        val long = ListeningAnalyticsBucketBuilder.build(
            allTime,
            DetailedListeningEventBounds(epoch("2019-12-31T10:00:00-08:00"), epoch("2026-01-01T10:00:00-08:00"))
        )
        assertEquals(AnalyticsBucketGranularity.YEAR, long.singleGranularity())
        assertEquals(8, long.size)
    }

    @Test
    fun extremeCustomHistoryIsCoarsenedAndNeverExceedsFourHundredBuckets() {
        val range = resolver("2026-03-18T12:00:00-07:00").resolve(
            AnalyticsRangeSelection.Custom(LocalDate.of(1200, 2, 3), LocalDate.of(2026, 3, 18))
        )
        val buckets = ListeningAnalyticsBucketBuilder.build(range)
        assertTrue(buckets.size <= ListeningAnalyticsBucketBuilder.MAX_BUCKET_COUNT)
        assertContiguous(buckets, requireNotNull(range.eventRange))
    }

    private fun assertToday(value: String, expectedCount: Int): List<AnalyticsBucketBoundary> {
        val range = resolver(value).resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.TODAY))
        val buckets = ListeningAnalyticsBucketBuilder.build(range)
        assertEquals(expectedCount, buckets.size)
        assertEquals(AnalyticsBucketGranularity.HOUR, buckets.singleGranularity())
        assertContiguous(buckets, requireNotNull(range.eventRange))
        return buckets
    }

    private fun assertPresetGranularity(
        preset: AnalyticsRangePreset,
        granularity: AnalyticsBucketGranularity,
        count: Int
    ) {
        val range = resolver("2026-03-18T12:00:00-07:00").resolve(AnalyticsRangeSelection.Preset(preset))
        val buckets = ListeningAnalyticsBucketBuilder.build(range)
        assertEquals(granularity, buckets.singleGranularity())
        assertEquals(count, buckets.size)
        assertContiguous(buckets, requireNotNull(range.eventRange))
    }

    private fun assertCustomGranularity(start: String, end: String, granularity: AnalyticsBucketGranularity, count: Int) {
        val range = resolver("2026-03-18T12:00:00-07:00").resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse(start), LocalDate.parse(end))
        )
        val buckets = ListeningAnalyticsBucketBuilder.build(range)
        assertEquals(granularity, buckets.singleGranularity())
        assertEquals(count, buckets.size)
        assertContiguous(buckets, requireNotNull(range.eventRange))
    }

    private fun assertContiguous(buckets: List<AnalyticsBucketBoundary>, range: ListeningDateRange) {
        assertTrue(buckets.isNotEmpty())
        assertEquals(range.startInclusive, buckets.first().startInclusive)
        assertEquals(range.endExclusive, buckets.last().endExclusive)
        buckets.forEachIndexed { index, bucket ->
            assertEquals(index, bucket.index)
            assertTrue(bucket.startInclusive < bucket.endExclusive)
            if (index > 0) assertEquals(buckets[index - 1].endExclusive, bucket.startInclusive)
        }
    }

    private fun List<AnalyticsBucketBoundary>.singleGranularity(): AnalyticsBucketGranularity =
        map { it.granularity }.distinct().single()

    private fun resolver(now: String) = ListeningAnalyticsRangeResolver(
        Clock.fixed(Instant.parse(java.time.ZonedDateTime.parse(now).toInstant().toString()), ZoneId.of("UTC")),
        AnalyticsZoneIdProvider { zone }
    )

    private fun epoch(value: String): Long = java.time.ZonedDateTime.parse(value).toInstant().toEpochMilli()
}
