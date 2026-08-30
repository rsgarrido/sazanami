package io.github.rsgarrido.sazanami.ui.statistics

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import io.github.rsgarrido.sazanami.data.AnalyticsBucketGranularity
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ListeningTrendChartTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun sectionExposesMetricSelectionChartSemanticsAndPeakSummary() {
        val metric = mutableStateOf(ListeningTrendMetric.RECORDED_LISTENING_TIME)
        val buckets = dailyBuckets(7)
        composeRule.setContent {
            MaterialTheme {
                ListeningTrendSection(
                    buckets = buckets,
                    metric = metric.value,
                    zoneId = zone,
                    selectedRange = AnalyticsRangeSelection.Preset(AnalyticsRangePreset.LAST_7_DAYS),
                    rangeDescription = "Last 7 days",
                    hasDetailedEvents = true,
                    onMetricSelected = { metric.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Listening trend").assertExists()
        composeRule.onNodeWithText("Listening time").assertIsSelected()
        composeRule.onNodeWithContentDescription("7 periods", substring = true).assertExists()
        composeRule.onNodeWithText("Most active:", substring = true).assertExists()
        val before = composeRule.onNodeWithTag("listening_trend_chart")
            .fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithText("Plays").performClick()
        composeRule.onNodeWithText("Plays").assertIsSelected()
        val after = composeRule.onNodeWithTag("listening_trend_chart")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(before.height, after.height, 0.5f)
    }

    @Test
    fun allZeroSeriesAnnouncesEmptyStateWithoutChart() {
        val buckets = dailyBuckets(7).map {
            it.copy(listenedMs = 0L, qualifiedPlayCount = 0L)
        }
        composeRule.setContent {
            MaterialTheme {
                ListeningTrendChart(
                    buckets = buckets,
                    metric = ListeningTrendMetric.QUALIFIED_PLAYS,
                    zoneId = zone,
                    rangeDescription = "Last 7 days",
                    emptyMessage = "No trend data in this range."
                )
            }
        }
        composeRule.onNodeWithText("No trend data in this range.").assertExists()
        composeRule.onNodeWithContentDescription("No trend data", substring = true).assertExists()
        composeRule.onNodeWithTag("listening_trend_horizontal_scroll").assertDoesNotExist()
    }

    @Test
    fun denseAndDstFixturesRenderAndOnlyDenseRangesScroll() {
        val fixtures = listOf(
            hourlyBuckets(ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, zone), 24),
            hourlyBuckets(ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone), 23),
            hourlyBuckets(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone), 25),
            dailyBuckets(7),
            dailyBuckets(30),
            monthlyBuckets(12)
        )
        val current = mutableStateOf(fixtures.first())
        composeRule.setContent {
            MaterialTheme {
                ListeningTrendChart(
                    buckets = current.value,
                    metric = ListeningTrendMetric.RECORDED_LISTENING_TIME,
                    zoneId = zone,
                    rangeDescription = "Fixture",
                    emptyMessage = "Empty"
                )
            }
        }
        fixtures.forEach { fixture ->
            composeRule.runOnIdle { current.value = fixture }
            composeRule.onNodeWithTag("listening_trend_chart").assertExists()
        }

        composeRule.runOnIdle { current.value = dailyBuckets(90) }
        assertTrue(
            composeRule.onNodeWithTag("listening_trend_horizontal_scroll")
                .fetchSemanticsNode().config.contains(SemanticsActions.ScrollBy)
        )

        composeRule.runOnIdle { current.value = dailyBuckets(400) }
        composeRule.onNodeWithTag("listening_trend_chart").assertExists()
        assertTrue(
            composeRule.onNodeWithTag("listening_trend_horizontal_scroll")
                .fetchSemanticsNode().config.contains(SemanticsActions.ScrollBy)
        )
    }

    @Test
    fun largeLongValuesRenderWithoutCrash() {
        composeRule.setContent {
            MaterialTheme {
                ListeningTrendChart(
                    buckets = dailyBuckets(2).mapIndexed { index, bucket ->
                        bucket.copy(listenedMs = if (index == 0) 1L else Long.MAX_VALUE)
                    },
                    metric = ListeningTrendMetric.RECORDED_LISTENING_TIME,
                    zoneId = zone,
                    rangeDescription = "Fixture",
                    emptyMessage = "Empty"
                )
            }
        }
        composeRule.onNodeWithTag("listening_trend_chart").assertExists()
    }

    private fun hourlyBuckets(start: ZonedDateTime, count: Int): List<ListeningTrendBucket> {
        val first = start.toInstant()
        return List(count) { index ->
            bucket(index, first.plusSeconds(index * 3_600L), AnalyticsBucketGranularity.HOUR)
        }
    }

    private fun dailyBuckets(count: Int): List<ListeningTrendBucket> {
        val start = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone)
        return List(count) { index ->
            val local = start.plusDays(index.toLong())
            bucket(index, local.toInstant(), AnalyticsBucketGranularity.DAY)
        }
    }

    private fun monthlyBuckets(count: Int): List<ListeningTrendBucket> {
        val start = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        return List(count) { index ->
            bucket(index, start.plusMonths(index.toLong()).toInstant(), AnalyticsBucketGranularity.MONTH)
        }
    }

    private fun bucket(
        index: Int,
        start: Instant,
        granularity: AnalyticsBucketGranularity
    ) = ListeningTrendBucket(
        index = index,
        startInclusive = start.toEpochMilli(),
        endExclusive = start.plusSeconds(3_600L).toEpochMilli(),
        granularity = granularity,
        listenedMs = (index + 1L) * 60_000L,
        qualifiedPlayCount = index + 1L,
        totalAttemptCount = index + 1L,
        naturalCompletionCount = index.toLong()
    )
}
