package io.github.rsgarrido.sazanami.ui.statistics

import io.github.rsgarrido.sazanami.data.AnalyticsBucketGranularity
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningTrendFormattingTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun formatsHourlyDailyMonthlyAndYearlyLabels() {
        val hour = bucket(ZonedDateTime.of(2026, 8, 4, 6, 0, 0, 0, zone), AnalyticsBucketGranularity.HOUR)
        assertEquals("6 AM", formatTrendBucketLabel(hour, listOf(hour), zone, Locale.US))

        val week = (0..6).map { day ->
            bucket(ZonedDateTime.of(2026, 8, 3 + day, 0, 0, 0, 0, zone), AnalyticsBucketGranularity.DAY)
        }
        assertEquals("Mon", formatTrendBucketLabel(week.first(), week, zone, Locale.US))

        val month = bucket(ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone), AnalyticsBucketGranularity.MONTH)
        assertEquals("Aug", formatTrendBucketLabel(month, listOf(month), zone, Locale.US))
        val year = bucket(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone), AnalyticsBucketGranularity.YEAR)
        assertEquals("2026", formatTrendBucketLabel(year, listOf(year), zone, Locale.US))
    }

    @Test
    fun crossYearMonthsIncludeYearAndLocaleChangesNames() {
        val december = bucket(ZonedDateTime.of(2025, 12, 1, 0, 0, 0, 0, zone), AnalyticsBucketGranularity.MONTH)
        val january = bucket(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone), AnalyticsBucketGranularity.MONTH)
        assertEquals("Dec 2025", formatTrendBucketLabel(december, listOf(december, january), zone, Locale.US))
        assertNotEquals(
            formatTrendBucketLabel(january, listOf(december, january), zone, Locale.US),
            formatTrendBucketLabel(january, listOf(december, january), zone, Locale.FRANCE)
        )
    }

    @Test
    fun springForwardOmitsMissingHourAndFallBackDistinguishesRepeatedHour() {
        val springInstants = generateSequence(
            ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone).toInstant()
        ) { it.plusSeconds(3_600L) }.take(23).toList()
        val spring = springInstants.map { bucket(it.atZone(zone), AnalyticsBucketGranularity.HOUR) }
        val springLabels = spring.map { formatTrendBucketLabel(it, spring, zone, Locale.US) }
        assertTrue("2 AM" !in springLabels)

        val first = bucket(ZonedDateTime.parse("2026-11-01T01:00:00-07:00[America/Los_Angeles]"), AnalyticsBucketGranularity.HOUR)
        val second = bucket(ZonedDateTime.parse("2026-11-01T01:00:00-08:00[America/Los_Angeles]"), AnalyticsBucketGranularity.HOUR)
        val labels = listOf(first, second).map { formatTrendBucketLabel(it, listOf(first, second), zone, Locale.US) }
        assertNotEquals(labels[0], labels[1])
        assertTrue(labels.all { it.startsWith("1 AM GMT") })
    }

    @Test
    fun compactDurationAndCountsAreDeterministicAndLocaleAware() {
        assertEquals(CompactDurationValue(0L, CompactDurationUnit.MINUTES), compactDurationValue(0L))
        assertEquals(CompactDurationValue(1L, CompactDurationUnit.MINUTES), compactDurationValue(1L))
        assertEquals(CompactDurationValue(2L, CompactDurationUnit.HOURS), compactDurationValue(7_200_000L))
        assertEquals(CompactDurationValue(2L, CompactDurationUnit.DAYS), compactDurationValue(172_800_000L))
        assertEquals("1,234", formatTrendCount(1_234L, Locale.US))
        assertNotEquals(formatTrendCount(1_234L, Locale.US), formatTrendCount(1_234L, Locale.GERMANY))
    }

    @Test
    fun peakPeriodUsesResolvedZoneAndGranularity() {
        val day = bucket(ZonedDateTime.of(2026, 8, 3, 0, 0, 0, 0, zone), AnalyticsBucketGranularity.DAY)
        assertEquals("Aug 3", formatTrendPeakPeriod(day, zone, Locale.US))
    }

    private fun bucket(
        start: ZonedDateTime,
        granularity: AnalyticsBucketGranularity
    ): ListeningTrendBucket {
        val end = when (granularity) {
            AnalyticsBucketGranularity.HOUR -> start.plusHours(1)
            AnalyticsBucketGranularity.DAY -> start.plusDays(1)
            AnalyticsBucketGranularity.MONTH -> start.plusMonths(1)
            AnalyticsBucketGranularity.YEAR -> start.plusYears(1)
        }
        return ListeningTrendBucket(
            index = 0,
            startInclusive = start.toInstant().toEpochMilli(),
            endExclusive = end.toInstant().toEpochMilli(),
            granularity = granularity,
            listenedMs = 1L,
            qualifiedPlayCount = 1L,
            totalAttemptCount = 1L,
            naturalCompletionCount = 0L
        )
    }
}
