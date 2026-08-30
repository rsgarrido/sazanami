package io.github.rsgarrido.sazanami.ui.statistics

import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import kotlin.math.max
import kotlin.math.roundToInt

internal const val TREND_MINIMUM_SLOT_WIDTH_DP = 10f
internal const val TREND_MAXIMUM_BUCKET_COUNT = 400

internal fun ListeningTrendBucket.valueFor(metric: ListeningTrendMetric): Long = when (metric) {
    ListeningTrendMetric.RECORDED_LISTENING_TIME -> listenedMs
    ListeningTrendMetric.QUALIFIED_PLAYS -> qualifiedPlayCount
}

internal fun trendMaximum(
    buckets: List<ListeningTrendBucket>,
    metric: ListeningTrendMetric
): Long = buckets.maxOfOrNull { it.valueFor(metric) } ?: 0L

internal fun trendTotal(
    buckets: List<ListeningTrendBucket>,
    metric: ListeningTrendMetric
): Long = buckets.fold(0L) { total, bucket ->
    val value = bucket.valueFor(metric)
    if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
}

internal fun trendPeak(
    buckets: List<ListeningTrendBucket>,
    metric: ListeningTrendMetric
): ListeningTrendBucket? = buckets
    .asSequence()
    .filter { it.valueFor(metric) > 0L }
    .maxWithOrNull(
        compareBy<ListeningTrendBucket> { it.valueFor(metric) }
            .thenBy { it.startInclusive }
    )

internal fun normalizedTrendHeight(
    value: Long,
    maximum: Long,
    availableHeightPx: Float,
    minimumVisibleHeightPx: Float
): Float {
    if (value <= 0L || maximum <= 0L || availableHeightPx <= 0f) return 0f
    val scaled = (value.toDouble() / maximum.toDouble()) * availableHeightPx.toDouble()
    return scaled.toFloat().coerceIn(
        minimumVisibleHeightPx.coerceAtMost(availableHeightPx),
        availableHeightPx
    )
}

internal fun trendContentWidthDp(
    bucketCount: Int,
    viewportWidthDp: Float,
    minimumSlotWidthDp: Float = TREND_MINIMUM_SLOT_WIDTH_DP
): Float {
    val viewport = viewportWidthDp.coerceAtLeast(0f)
    if (bucketCount <= 30) return viewport
    return max(
        viewport,
        bucketCount.coerceIn(0, TREND_MAXIMUM_BUCKET_COUNT) * minimumSlotWidthDp.coerceAtLeast(1f)
    )
}

internal fun isTrendHorizontallyScrollable(
    bucketCount: Int,
    viewportWidthDp: Float,
    minimumSlotWidthDp: Float = TREND_MINIMUM_SLOT_WIDTH_DP
): Boolean = trendContentWidthDp(bucketCount, viewportWidthDp, minimumSlotWidthDp) > viewportWidthDp

internal fun selectTrendLabelIndices(bucketCount: Int, maximumLabels: Int): List<Int> {
    if (bucketCount <= 0 || maximumLabels <= 0) return emptyList()
    if (bucketCount <= maximumLabels) return List(bucketCount) { it }
    if (maximumLabels == 1) return listOf(0)
    return (0 until maximumLabels)
        .map { position ->
            (position.toDouble() * (bucketCount - 1) / (maximumLabels - 1)).roundToInt()
        }
        .distinct()
}
