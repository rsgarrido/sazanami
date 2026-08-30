package io.github.rsgarrido.sazanami.ui.statistics

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.ListeningOverview
import io.github.rsgarrido.sazanami.data.ListeningPlayCountBreakdown
import io.github.rsgarrido.sazanami.data.AnalyticsBucketGranularity
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsCoverage
import io.github.rsgarrido.sazanami.data.ListeningDateRange
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTimeBreakdown
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.ResolvedAnalyticsRange
import io.github.rsgarrido.sazanami.data.TrackListeningStats
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsError
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsErrorKind
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overviewFormatsMetricsAndDefaultRangeSemantics() {
        setStatisticsContent(
            state = ListeningAnalyticsUiState(overview = overview())
        )

        composeRule.onNodeWithContentDescription("Last 30 days").assertIsSelected()
        composeRule.onNodeWithText("3 hr 18 min").assertExists()
        composeRule.onNodeWithText("1,234").assertExists()
        composeRule.onNodeWithText("52").assertExists()
        composeRule.onNodeWithText("9").assertExists()
        composeRule.onNodeWithContentDescription(
            "Not counted, 9. Attempts below the play threshold"
        ).assertExists()
    }

    @Test
    fun presetAndCustomIntentsAreNarrowAndInclusive() {
        var preset: AnalyticsRangePreset? = null
        var custom: Pair<LocalDate, LocalDate>? = null
        setStatisticsContent(
            state = ListeningAnalyticsUiState(overview = overview()),
            onPresetSelected = { preset = it },
            onCustomRangeSelected = { start, end -> custom = start to end }
        )

        composeRule.onNodeWithText("Today").performClick()
        composeRule.runOnIdle { assertEquals(AnalyticsRangePreset.TODAY, preset) }
        composeRule.onNodeWithText("Custom").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(null, custom) }
    }

    @Test
    fun loadingErrorsRefreshAndEmptyStatesRemainExplicit() {
        val screenState = mutableStateOf(ListeningAnalyticsUiState(isInitialLoading = true))
        var retried = false
        composeRule.setContent {
            MaterialTheme {
                StatisticsScreen(
                    state = screenState.value,
                    onBackClick = {},
                    onPresetSelected = {},
                    onCustomRangeSelected = { _, _ -> },
                    onRetry = { retried = true },
                    listState = remember { LazyListState() }
                )
            }
        }
        composeRule.onNodeWithText("Loading listening statistics…").assertExists()

        composeRule.runOnIdle {
            screenState.value = ListeningAnalyticsUiState(
                error = ListeningAnalyticsError(
                    ListeningAnalyticsErrorKind.SNAPSHOT_LOAD,
                    retryable = true,
                    cause = IllegalStateException("not shown")
                )
            )
        }
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertTrue(retried) }

        composeRule.runOnIdle {
            screenState.value = ListeningAnalyticsUiState(overview = overview(), isRefreshing = true)
        }
        composeRule.onNodeWithTag("statistics_refresh_indicator").assertExists()
        composeRule.onNodeWithText("Refreshing this range.", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("1,234").assertExists()

        composeRule.runOnIdle {
            screenState.value = ListeningAnalyticsUiState(
                selectedRange = AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME),
                overview = overview(plays = 0L, detailedEvents = 0L)
            )
        }
        composeRule.onNodeWithText("Your listening activity will appear here as you play music.")
            .assertExists()

        composeRule.runOnIdle {
            screenState.value = ListeningAnalyticsUiState(
                overview = overview(plays = 0L, detailedEvents = 0L)
            )
        }
        composeRule.onNodeWithText("No listening activity in this range.").assertExists()
    }

    @Test
    fun refreshUsesReservedSlotWithoutMovingMetrics() {
        val screenState = mutableStateOf(ListeningAnalyticsUiState(overview = overview()))
        composeRule.setContent {
            MaterialTheme {
                StatisticsScreen(
                    state = screenState.value,
                    onBackClick = {},
                    onPresetSelected = {},
                    onCustomRangeSelected = { _, _ -> },
                    onRetry = {},
                    listState = remember { LazyListState() }
                )
            }
        }

        composeRule.onNodeWithTag("statistics_refresh_slot").assertExists()
        composeRule.onNodeWithTag("statistics_refresh_indicator").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Last 30 days").assertIsSelected()
        val metric = composeRule.onNodeWithContentDescription(
            "Recorded listening, 3 hours, 18 minutes. Detailed history only"
        )
        val idleTop = metric.fetchSemanticsNode().boundsInRoot.top

        composeRule.runOnIdle {
            screenState.value = screenState.value.copy(isRefreshing = true)
        }

        composeRule.onNodeWithTag("statistics_refresh_indicator").assertExists()
        composeRule.onNodeWithText("Refreshing this range.", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("1,234").assertExists()
        val refreshingTop = metric.fetchSemanticsNode().boundsInRoot.top
        assertEquals(idleTop, refreshingTop, 0.5f)
    }

    @Test
    fun historyDialogDoesNotReplaceRememberedListState() {
        lateinit var listState: LazyListState
        composeRule.setContent {
            MaterialTheme {
                listState = remember { LazyListState() }
                StatisticsScreen(
                    state = ListeningAnalyticsUiState(overview = overview()),
                    onBackClick = {},
                    onPresetSelected = {},
                    onCustomRangeSelected = { _, _ -> },
                    onRetry = {},
                    listState = listState,
                    modifier = Modifier.height(300.dp)
                )
            }
        }
        composeRule.runOnIdle { listState.requestScrollToItem(3) }
        composeRule.waitForIdle()
        val indexBeforeDialog = listState.firstVisibleItemIndex
        val offsetBeforeDialog = listState.firstVisibleItemScrollOffset
        assertTrue(indexBeforeDialog > 0 || offsetBeforeDialog > 0)
        composeRule.onNodeWithContentDescription("About listening history coverage").performClick()
        composeRule.onNodeWithText("About your listening history").assertExists()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.runOnIdle {
            assertEquals(indexBeforeDialog, listState.firstVisibleItemIndex)
            assertEquals(offsetBeforeDialog, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun incompletePickerCannotConfirm() {
        composeRule.setContent {
            MaterialTheme {
                StatisticsDateRangeDialog(
                    initialStartDate = null,
                    initialEndDateInclusive = null,
                    onDismiss = {},
                    onConfirm = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test
    fun narrowTwoTimesFontKeepsCriticalStatisticsControlsAndValuesReachable() {
        val narrowState = ListeningAnalyticsUiState(
            overview = overview(),
            trend = listOf(
                ListeningTrendBucket(
                    index = 0,
                    startInclusive = 0L,
                    endExclusive = 86_400_000L,
                    granularity = AnalyticsBucketGranularity.DAY,
                    listenedMs = 60_000L,
                    qualifiedPlayCount = 1L,
                    totalAttemptCount = 1L,
                    naturalCompletionCount = 1L
                )
            ),
            topTracks = listOf(
                TrackListeningStats(
                    trackIdentityId = 1L,
                    title = "Narrow track",
                    artist = "Artist",
                    album = "Album",
                    albumArtist = null,
                    durationMs = null,
                    binding = null,
                    playCounts = ListeningPlayCountBreakdown(1L, 0L, 1L),
                    confirmedDetailedListeningMs = 60_000L,
                    detailedEventCount = 1L,
                    naturalCompletionCount = 1L,
                    nonQualifiedAttemptCount = 0L,
                    firstKnownPlayAt = null,
                    latestKnownPlayAt = null,
                    latestDetailedEventAt = null
                )
            ),
            coverage = ListeningAnalyticsCoverage(
                selectionCanIncludeLegacyPlays = false,
                hasLegacyPlays = false,
                legacyQualifiedPlayCount = 0L,
                detailedQualifiedPlayCount = 1L,
                hasDetailedEvents = true,
                earliestDetailedEventAt = 0L,
                latestDetailedEventAt = 0L
            )
        )
        lateinit var listState: LazyListState
        composeRule.setContent {
            MaterialTheme {
                val currentConfiguration = LocalConfiguration.current
                val currentDensity = LocalDensity.current
                val largeConfiguration = Configuration(currentConfiguration).apply {
                    fontScale = 2f
                    screenWidthDp = 280
                }
                CompositionLocalProvider(
                    LocalConfiguration provides largeConfiguration,
                    LocalDensity provides Density(currentDensity.density, fontScale = 2f)
                ) {
                    Box(Modifier.width(280.dp).height(700.dp)) {
                        listState = remember { LazyListState() }
                        StatisticsScreen(
                            state = narrowState,
                            onBackClick = {},
                            onPresetSelected = {},
                            onCustomRangeSelected = { _, _ -> },
                            onRetry = {},
                            listState = listState
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Last 30 days")
            .performScrollTo()
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("3 hr 18 min").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { listState.requestScrollToItem(3) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Listening time").assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle { listState.requestScrollToItem(4) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tracks").assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle { listState.requestScrollToItem(5) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Narrow track").assertIsDisplayed()
    }

    @Test
    fun trendAndRankingsFollowCoverageAndSelectionsPreserveRangeAndScroll() {
        val selected = AnalyticsRangeSelection.Preset(AnalyticsRangePreset.LAST_7_DAYS)
        val screenState = mutableStateOf(
            ListeningAnalyticsUiState(
                selectedRange = selected,
                resolvedRange = ResolvedAnalyticsRange(
                    selection = selected,
                    eventRange = ListeningDateRange(1L, 2L),
                    zoneId = ZoneId.of("UTC"),
                    resolvedAt = Instant.EPOCH
                ),
                overview = overview(),
                trend = List(7) { index ->
                    ListeningTrendBucket(
                        index = index,
                        startInclusive = index * 86_400_000L,
                        endExclusive = (index + 1L) * 86_400_000L,
                        granularity = AnalyticsBucketGranularity.DAY,
                        listenedMs = (index + 1L) * 60_000L,
                        qualifiedPlayCount = index + 1L,
                        totalAttemptCount = index + 1L,
                        naturalCompletionCount = index.toLong()
                    )
                },
                coverage = ListeningAnalyticsCoverage(
                    selectionCanIncludeLegacyPlays = false,
                    hasLegacyPlays = false,
                    legacyQualifiedPlayCount = 0L,
                    detailedQualifiedPlayCount = 28L,
                    hasDetailedEvents = true,
                    earliestDetailedEventAt = 1L,
                    latestDetailedEventAt = 2L
                )
            )
        )
        lateinit var listState: LazyListState
        composeRule.setContent {
            MaterialTheme {
                listState = remember { LazyListState() }
                StatisticsScreen(
                    state = screenState.value,
                    onBackClick = {},
                    onPresetSelected = {},
                    onCustomRangeSelected = { _, _ -> },
                    onRetry = {},
                    onTrendMetricSelected = {
                        screenState.value = screenState.value.copy(trendMetric = it)
                    },
                    onRankingCategorySelected = {
                        screenState.value = screenState.value.copy(rankingCategory = it)
                    },
                    listState = listState,
                    modifier = Modifier.height(420.dp)
                )
            }
        }

        composeRule.runOnIdle { listState.requestScrollToItem(3) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Listening trend").assertExists()
        val trendIndex = listState.firstVisibleItemIndex
        val trendOffset = listState.firstVisibleItemScrollOffset
        composeRule.onNode(hasText("Plays") and hasClickAction()).performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(ListeningTrendMetric.QUALIFIED_PLAYS, screenState.value.trendMetric)
            assertEquals(selected, screenState.value.selectedRange)
            assertEquals(trendIndex, listState.firstVisibleItemIndex)
            assertEquals(trendOffset, listState.firstVisibleItemScrollOffset)
        }

        composeRule.runOnIdle { listState.requestScrollToItem(4) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Top listening").assertExists()
        val rankingIndex = listState.firstVisibleItemIndex
        composeRule.onNodeWithText("Artists").performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(ListeningRankingCategory.ARTISTS, screenState.value.rankingCategory)
            assertEquals(selected, screenState.value.selectedRange)
            assertEquals(rankingIndex, listState.firstVisibleItemIndex)
        }
        composeRule.onNodeWithText("No top artists in this range.").performScrollTo().assertExists()
    }

    private fun setStatisticsContent(
        state: ListeningAnalyticsUiState,
        onPresetSelected: (AnalyticsRangePreset) -> Unit = {},
        onCustomRangeSelected: (LocalDate, LocalDate) -> Unit = { _, _ -> },
        onRetry: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                StatisticsScreen(
                    state = state,
                    onBackClick = {},
                    onPresetSelected = onPresetSelected,
                    onCustomRangeSelected = onCustomRangeSelected,
                    onRetry = onRetry,
                    listState = remember { LazyListState() }
                )
            }
        }
    }

    private fun overview(
        plays: Long = 1_234L,
        detailedEvents: Long = 70L
    ) = ListeningOverview(
        playCounts = ListeningPlayCountBreakdown(plays, 100L.coerceAtMost(plays), (plays - 100L).coerceAtLeast(0L)),
        listeningTime = ListeningTimeBreakdown(
            confirmedDetailedListeningMs = (3L * 60L + 18L) * 60_000L,
            legacyPlayCountWithoutKnownDuration = 100L.coerceAtMost(plays)
        ),
        qualifiedDetailedPlayCount = (plays - 100L).coerceAtLeast(0L),
        naturalCompletionCount = 52L,
        nonQualifiedAttemptCount = 9L,
        detailedEventCount = detailedEvents,
        firstDetailedEventAt = null,
        latestDetailedEventAt = null,
        firstKnownPlayAt = null,
        latestKnownPlayAt = null,
        hasLegacyBaseline = plays > 0L
    )
}
