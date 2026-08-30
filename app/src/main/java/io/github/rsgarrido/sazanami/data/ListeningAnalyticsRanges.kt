package io.github.rsgarrido.sazanami.data

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.ceil

fun interface AnalyticsZoneIdProvider {
    fun currentZoneId(): ZoneId
}

class ListeningAnalyticsRangeResolver(
    private val clock: Clock,
    private val zoneIdProvider: AnalyticsZoneIdProvider
) {
    fun resolve(selection: AnalyticsRangeSelection): ResolvedAnalyticsRange {
        val resolvedAt = clock.instant()
        val zoneId = zoneIdProvider.currentZoneId()
        val today = resolvedAt.atZone(zoneId).toLocalDate()
        val dates = when (selection) {
            is AnalyticsRangeSelection.Custom -> selection.startDate to
                    selection.endDateInclusive.plusDays(1L)
            is AnalyticsRangeSelection.Preset -> when (selection.preset) {
                AnalyticsRangePreset.TODAY -> today to today.plusDays(1L)
                AnalyticsRangePreset.LAST_7_DAYS -> today.minusDays(6L) to today.plusDays(1L)
                AnalyticsRangePreset.LAST_30_DAYS -> today.minusDays(29L) to today.plusDays(1L)
                // Calendar presets are to-date, while keeping the current local day open for new plays.
                AnalyticsRangePreset.THIS_MONTH -> today.withDayOfMonth(1) to today.plusDays(1L)
                AnalyticsRangePreset.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to today.plusDays(1L)
                AnalyticsRangePreset.ALL_TIME -> null
            }
        }
        val range = dates?.let { (start, end) ->
            ListeningDateRange(
                startInclusive = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                endExclusive = end.atStartOfDay(zoneId).toInstant().toEpochMilli()
            )
        }
        return ResolvedAnalyticsRange(selection, range, zoneId, resolvedAt)
    }
}

object ListeningAnalyticsBucketBuilder {
    const val MAX_BUCKET_COUNT = 400

    fun build(
        range: ResolvedAnalyticsRange,
        allTimeBounds: DetailedListeningEventBounds? = null
    ): List<AnalyticsBucketBoundary> {
        if (range.isAllTime && allTimeBounds == null) return emptyList()
        val zone = range.zoneId
        val (start, end, granularity) = when (val selection = range.selection) {
            is AnalyticsRangeSelection.Preset -> when (selection.preset) {
                AnalyticsRangePreset.TODAY -> ranged(range, AnalyticsBucketGranularity.HOUR)
                AnalyticsRangePreset.LAST_7_DAYS,
                AnalyticsRangePreset.LAST_30_DAYS,
                AnalyticsRangePreset.THIS_MONTH -> ranged(range, AnalyticsBucketGranularity.DAY)
                AnalyticsRangePreset.THIS_YEAR -> ranged(range, AnalyticsBucketGranularity.MONTH)
                AnalyticsRangePreset.ALL_TIME -> {
                    val bounds = requireNotNull(allTimeBounds)
                    val earliest = java.time.Instant.ofEpochMilli(bounds.earliestStartedAt).atZone(zone)
                    val latest = java.time.Instant.ofEpochMilli(bounds.latestStartedAt).atZone(zone)
                    val monthStart = earliest.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
                    val monthEnd = latest.toLocalDate().withDayOfMonth(1).plusMonths(1L).atStartOfDay(zone)
                    if (!monthEnd.isAfter(monthStart.plusMonths(36L))) {
                        Triple(monthStart, monthEnd, AnalyticsBucketGranularity.MONTH)
                    } else {
                        Triple(
                            LocalDate.of(earliest.year, 1, 1).atStartOfDay(zone),
                            LocalDate.of(latest.year + 1, 1, 1).atStartOfDay(zone),
                            AnalyticsBucketGranularity.YEAR
                        )
                    }
                }
            }
            is AnalyticsRangeSelection.Custom -> {
                val resolved = requireNotNull(range.eventRange)
                val startAt = java.time.Instant.ofEpochMilli(resolved.startInclusive).atZone(zone)
                val endAt = java.time.Instant.ofEpochMilli(resolved.endExclusive).atZone(zone)
                val inclusiveDays = java.time.temporal.ChronoUnit.DAYS.between(
                    selection.startDate,
                    selection.endDateInclusive
                ) + 1L
                val granularity = when {
                    inclusiveDays <= 90L -> AnalyticsBucketGranularity.DAY
                    !selection.endDateInclusive.plusDays(1L).isAfter(selection.startDate.plusMonths(36L)) ->
                        AnalyticsBucketGranularity.MONTH
                    else -> AnalyticsBucketGranularity.YEAR
                }
                Triple(startAt, endAt, granularity)
            }
        }
        return buildBoundaries(start, end, granularity)
    }

    private fun ranged(
        range: ResolvedAnalyticsRange,
        granularity: AnalyticsBucketGranularity
    ): Triple<ZonedDateTime, ZonedDateTime, AnalyticsBucketGranularity> {
        val resolved = requireNotNull(range.eventRange)
        return Triple(
            java.time.Instant.ofEpochMilli(resolved.startInclusive).atZone(range.zoneId),
            java.time.Instant.ofEpochMilli(resolved.endExclusive).atZone(range.zoneId),
            granularity
        )
    }

    private fun buildBoundaries(
        start: ZonedDateTime,
        end: ZonedDateTime,
        granularity: AnalyticsBucketGranularity
    ): List<AnalyticsBucketBoundary> {
        require(start.isBefore(end))
        var yearStride = if (granularity == AnalyticsBucketGranularity.YEAR) {
            val estimated = end.year.toLong() - start.year.toLong() + 2L
            ceil(estimated.coerceAtLeast(1L).toDouble() / MAX_BUCKET_COUNT).toLong()
                .coerceAtLeast(1L)
        } else 1L
        while (true) {
            val result = mutableListOf<AnalyticsBucketBoundary>()
            var cursor = start
            while (cursor.isBefore(end) && result.size <= MAX_BUCKET_COUNT) {
                val candidate = nextBoundary(cursor, granularity, yearStride)
                val next = if (candidate.isAfter(end)) end else candidate
                check(next.isAfter(cursor)) { "Analytics bucket boundaries must increase" }
                result += AnalyticsBucketBoundary(
                    index = result.size,
                    startInclusive = cursor.toInstant().toEpochMilli(),
                    endExclusive = next.toInstant().toEpochMilli(),
                    localStart = cursor,
                    granularity = granularity
                )
                cursor = next
            }
            if (result.size <= MAX_BUCKET_COUNT && cursor == end) return result
            check(granularity == AnalyticsBucketGranularity.YEAR) {
                "Analytics bucket policy exceeded $MAX_BUCKET_COUNT buckets"
            }
            yearStride++
        }
    }

    private fun nextBoundary(
        current: ZonedDateTime,
        granularity: AnalyticsBucketGranularity,
        yearStride: Long
    ): ZonedDateTime = when (granularity) {
        AnalyticsBucketGranularity.HOUR -> current.plusHours(1L)
        AnalyticsBucketGranularity.DAY -> current.plusDays(1L)
        AnalyticsBucketGranularity.MONTH -> {
            val isMonthBoundary = current.toLocalTime() == LocalTime.MIDNIGHT &&
                    current.dayOfMonth == 1
            if (isMonthBoundary) current.plusMonths(1L)
            else YearMonth.from(current).plusMonths(1L).atDay(1).atStartOfDay(current.zone)
        }
        AnalyticsBucketGranularity.YEAR -> {
            val firstBoundaryYear = if (
                current.toLocalTime() == LocalTime.MIDNIGHT &&
                current.monthValue == 1 &&
                current.dayOfMonth == 1
            ) current.year.toLong() + yearStride else current.year.toLong() + 1L
            LocalDate.of(Math.toIntExact(firstBoundaryYear), 1, 1).atStartOfDay(current.zone)
        }
    }
}
