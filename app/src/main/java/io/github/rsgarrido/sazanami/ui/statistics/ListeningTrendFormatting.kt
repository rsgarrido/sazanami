package io.github.rsgarrido.sazanami.ui.statistics

import io.github.rsgarrido.sazanami.data.AnalyticsBucketGranularity
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal enum class CompactDurationUnit {
    MINUTES,
    HOURS,
    DAYS
}

internal data class CompactDurationValue(
    val amount: Long,
    val unit: CompactDurationUnit
)

internal fun compactDurationValue(milliseconds: Long): CompactDurationValue {
    val safe = milliseconds.coerceAtLeast(0L)
    return when {
        safe >= 86_400_000L -> CompactDurationValue(safe / 86_400_000L, CompactDurationUnit.DAYS)
        safe >= 3_600_000L -> CompactDurationValue(safe / 3_600_000L, CompactDurationUnit.HOURS)
        safe == 0L -> CompactDurationValue(0L, CompactDurationUnit.MINUTES)
        else -> CompactDurationValue(1L + (safe - 1L) / 60_000L, CompactDurationUnit.MINUTES)
    }
}

internal fun formatTrendCount(value: Long, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(value.coerceAtLeast(0L))

internal fun formatTrendBucketLabel(
    bucket: ListeningTrendBucket,
    allBuckets: List<ListeningTrendBucket>,
    zoneId: ZoneId,
    locale: Locale = Locale.getDefault()
): String {
    val start = Instant.ofEpochMilli(bucket.startInclusive).atZone(zoneId)
    return when (bucket.granularity) {
        AnalyticsBucketGranularity.HOUR -> {
            val localHour = start.toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
            val repeated = allBuckets.count {
                Instant.ofEpochMilli(it.startInclusive).atZone(zoneId)
                    .toLocalDateTime().truncatedTo(ChronoUnit.HOURS) == localHour
            } > 1
            DateTimeFormatter.ofPattern(if (repeated) "h a O" else "h a", locale).format(start)
        }
        AnalyticsBucketGranularity.DAY -> DateTimeFormatter.ofPattern(
            if (allBuckets.size <= 7) "EEE" else "MMM d",
            locale
        ).format(start)
        AnalyticsBucketGranularity.MONTH -> {
            val crossesYear = allBuckets.asSequence()
                .map { Instant.ofEpochMilli(it.startInclusive).atZone(zoneId).year }
                .distinct().take(2).count() > 1
            DateTimeFormatter.ofPattern(if (crossesYear) "MMM yyyy" else "MMM", locale).format(start)
        }
        AnalyticsBucketGranularity.YEAR -> DateTimeFormatter.ofPattern("yyyy", locale).format(start)
    }
}

internal fun formatTrendPeakPeriod(
    bucket: ListeningTrendBucket,
    zoneId: ZoneId,
    locale: Locale = Locale.getDefault()
): String {
    val start = Instant.ofEpochMilli(bucket.startInclusive).atZone(zoneId)
    val pattern = when (bucket.granularity) {
        AnalyticsBucketGranularity.HOUR -> "MMM d, h a O"
        AnalyticsBucketGranularity.DAY -> "MMM d"
        AnalyticsBucketGranularity.MONTH -> "MMM yyyy"
        AnalyticsBucketGranularity.YEAR -> "yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(start)
}
