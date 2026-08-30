package io.github.rsgarrido.sazanami.ui.statistics

import io.github.rsgarrido.sazanami.data.AnalyticsBucketGranularity
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningTrendGeometryTest {
    @Test
    fun zeroSeriesHasNoMaximumTotalPeakOrHeight() {
        val buckets = listOf(bucket(0, 0L), bucket(1, 0L))
        assertEquals(0L, trendMaximum(buckets, ListeningTrendMetric.RECORDED_LISTENING_TIME))
        assertEquals(0L, trendTotal(buckets, ListeningTrendMetric.RECORDED_LISTENING_TIME))
        assertEquals(null, trendPeak(buckets, ListeningTrendMetric.RECORDED_LISTENING_TIME))
        assertEquals(0f, normalizedTrendHeight(0L, 0L, 100f, 2f), 0f)
    }

    @Test
    fun normalizedHeightsShareOneScaleAndKeepSmallNonzeroValuesVisible() {
        assertEquals(100f, normalizedTrendHeight(100L, 100L, 100f, 2f), 0.001f)
        assertEquals(50f, normalizedTrendHeight(50L, 100L, 100f, 2f), 0.001f)
        assertEquals(2f, normalizedTrendHeight(1L, 10_000L, 100f, 2f), 0.001f)
        assertEquals(0f, normalizedTrendHeight(0L, 10_000L, 100f, 2f), 0f)
    }

    @Test
    fun longMaximumScalesWithoutOverflowAndTotalSaturates() {
        val buckets = listOf(bucket(0, Long.MAX_VALUE), bucket(1, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, trendMaximum(buckets, ListeningTrendMetric.RECORDED_LISTENING_TIME))
        assertEquals(Long.MAX_VALUE, trendTotal(buckets, ListeningTrendMetric.RECORDED_LISTENING_TIME))
        assertEquals(80f, normalizedTrendHeight(Long.MAX_VALUE, Long.MAX_VALUE, 80f, 2f), 0.001f)
    }

    @Test
    fun peakUsesSelectedMetricAndMostRecentTieBreak() {
        val buckets = listOf(
            bucket(0, listened = 10L, plays = 9L),
            bucket(1, listened = 20L, plays = 1L),
            bucket(2, listened = 20L, plays = 3L)
        )
        assertEquals(2, trendPeak(buckets, ListeningTrendMetric.RECORDED_LISTENING_TIME)?.index)
        assertEquals(0, trendPeak(buckets, ListeningTrendMetric.QUALIFIED_PLAYS)?.index)
    }

    @Test
    fun widthFitsCommonSeriesAndScrollsDenseSeriesWithinBound() {
        assertEquals(320f, trendContentWidthDp(30, 320f), 0f)
        assertFalse(isTrendHorizontallyScrollable(30, 320f))
        assertEquals(900f, trendContentWidthDp(90, 320f), 0f)
        assertTrue(isTrendHorizontallyScrollable(90, 320f))
        assertEquals(4_000f, trendContentWidthDp(400, 320f), 0f)
        assertEquals(4_000f, trendContentWidthDp(4_000, 320f), 0f)
    }

    @Test
    fun labelSelectionAlwaysIncludesFirstAndLastWithBoundedIntermediates() {
        assertEquals(listOf(0, 1, 2), selectTrendLabelIndices(3, 8))
        val dense = selectTrendLabelIndices(90, 6)
        assertEquals(6, dense.size)
        assertEquals(0, dense.first())
        assertEquals(89, dense.last())
    }

    private fun bucket(
        index: Int,
        listened: Long,
        plays: Long = 0L
    ) = ListeningTrendBucket(
        index = index,
        startInclusive = index * 3_600_000L,
        endExclusive = (index + 1L) * 3_600_000L,
        granularity = AnalyticsBucketGranularity.HOUR,
        listenedMs = listened,
        qualifiedPlayCount = plays,
        totalAttemptCount = 0L,
        naturalCompletionCount = 0L
    )
}
