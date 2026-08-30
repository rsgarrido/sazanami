package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.AnalyticsZoneIdProvider
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsCoverage
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsDataSource
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsRangeResolver
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsSnapshot
import io.github.rsgarrido.sazanami.data.ListeningOverview
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningPlayCountBreakdown
import io.github.rsgarrido.sazanami.data.ListeningTimeBreakdown
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.ResolvedAnalyticsRange
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsErrorKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningAnalyticsControllerTest {
    @Test
    fun defaultSelectionIsInactiveAndActivationLoadsOneAtomicSnapshot() = runBlocking {
        fixture().use { test ->
            assertEquals(
                AnalyticsRangeSelection.Preset(AnalyticsRangePreset.LAST_30_DAYS),
                test.controller.state.value.selectedRange
            )
            assertFalse(test.controller.state.value.isActive)
            assertEquals(
                ListeningTrendMetric.RECORDED_LISTENING_TIME,
                test.controller.state.value.trendMetric
            )
            assertEquals(
                ListeningRankingCategory.TRACKS,
                test.controller.state.value.rankingCategory
            )
            assertEquals(0, test.repository.calls.get())

            test.controller.setActive(true)
            val request = test.repository.requests.receiveWithin()
            assertEquals(AnalyticsRangePreset.LAST_30_DAYS, request.preset())
            val state = test.controller.state.await { it.overview != null && !it.isInitialLoading }
            assertNotNull(state.resolvedRange)
            assertNotNull(state.coverage)
            assertNull(state.error)
        }
    }

    @Test
    fun deactivationCancelsInFlightWorkAndReactivationResolvesFreshly() = runBlocking {
        fixture().use { test ->
            test.repository.blockNext = true
            test.controller.setActive(true)
            test.repository.requests.receiveWithin()
            test.controller.setActive(false)
            test.repository.cancellations.receiveWithin()
            assertFalse(test.controller.state.value.isActive)
            assertFalse(test.controller.state.value.isInitialLoading)

            test.repository.blockNext = false
            test.controller.setActive(true)
            test.repository.requests.receiveWithin()
            test.controller.state.await { it.overview != null }
            assertEquals(2, test.repository.calls.get())
        }
    }

    @Test
    fun rangeChangeCancelsOldLoadAndOnlyPublishesNewestRange() = runBlocking {
        fixture().use { test ->
            test.repository.blockNext = true
            test.controller.setActive(true)
            assertEquals(AnalyticsRangePreset.LAST_30_DAYS, test.repository.requests.receiveWithin().preset())

            test.repository.blockNext = false
            val newest = AnalyticsRangeSelection.Preset(AnalyticsRangePreset.TODAY)
            test.controller.selectRange(newest)
            assertEquals(newest, test.controller.state.value.selectedRange)
            test.repository.cancellations.receiveWithin()
            assertEquals(AnalyticsRangePreset.TODAY, test.repository.requests.receiveWithin().preset())
            val state = test.controller.state.await { it.resolvedRange?.selection == newest && !it.isRefreshing }
            assertEquals(newest, state.selectedRange)
            assertEquals(newest, state.resolvedRange?.selection)
        }
    }

    @Test
    fun invalidationRefreshesOnlyWhileActiveAndMetricChangesNeverReadDatabase() = runBlocking {
        fixture().use { test ->
            test.controller.setActive(true)
            test.repository.requests.receiveWithin()
            test.controller.state.await { it.overview != null }
            val initialCalls = test.repository.calls.get()

            test.controller.selectTrendMetric(ListeningTrendMetric.QUALIFIED_PLAYS)
            test.controller.selectRankingCategory(ListeningRankingCategory.ALBUMS)
            assertEquals(ListeningTrendMetric.QUALIFIED_PLAYS, test.controller.state.value.trendMetric)
            assertEquals(ListeningRankingCategory.ALBUMS, test.controller.state.value.rankingCategory)
            assertEquals(initialCalls, test.repository.calls.get())

            test.repository.invalidations.emit(Unit)
            test.repository.requests.receiveWithin()
            test.controller.state.await { !it.isRefreshing }
            assertEquals(initialCalls + 1, test.repository.calls.get())

            test.controller.setActive(false)
            test.repository.invalidations.emit(Unit)
            repeat(20) { yield() }
            assertEquals(initialCalls + 1, test.repository.calls.get())
            assertNotNull(test.controller.state.value.overview)
            assertEquals(ListeningTrendMetric.QUALIFIED_PLAYS, test.controller.state.value.trendMetric)
            assertEquals(ListeningRankingCategory.ALBUMS, test.controller.state.value.rankingCategory)
        }
    }

    @Test
    fun rangeChangePreservesMetricAndRankingCategoryWithoutExtraSelectionQueries() = runBlocking {
        fixture().use { test ->
            test.controller.setActive(true)
            test.repository.requests.receiveWithin()
            test.controller.state.await { it.overview != null }
            test.controller.selectTrendMetric(ListeningTrendMetric.QUALIFIED_PLAYS)
            test.controller.selectRankingCategory(ListeningRankingCategory.ARTISTS)
            val beforeRangeChange = test.repository.calls.get()

            test.controller.selectRange(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.TODAY))
            test.repository.requests.receiveWithin()
            val state = test.controller.state.await {
                it.resolvedRange?.selection ==
                    AnalyticsRangeSelection.Preset(AnalyticsRangePreset.TODAY) && !it.isRefreshing
            }

            assertEquals(beforeRangeChange + 1, test.repository.calls.get())
            assertEquals(ListeningTrendMetric.QUALIFIED_PLAYS, state.trendMetric)
            assertEquals(ListeningRankingCategory.ARTISTS, state.rankingCategory)
        }
    }

    @Test
    fun initialAndRefreshErrorsAreRetryableAndPreserveLastSnapshot() = runBlocking {
        fixture().use { test ->
            test.repository.failNext = true
            test.controller.setActive(true)
            test.repository.requests.receiveWithin()
            val initialError = test.controller.state.await { it.error != null }
            assertNull(initialError.overview)
            assertEquals(ListeningAnalyticsErrorKind.SNAPSHOT_LOAD, initialError.error?.kind)

            test.controller.retry()
            test.repository.requests.receiveWithin()
            test.controller.state.await { it.overview != null && it.error == null }

            test.repository.failNext = true
            test.repository.invalidations.emit(Unit)
            test.repository.requests.receiveWithin()
            val refreshError = test.controller.state.await { it.error != null }
            assertNotNull(refreshError.overview)
            assertFalse(refreshError.isRefreshing)

            test.controller.retry()
            test.repository.requests.receiveWithin()
            val recovered = test.controller.state.await { it.error == null && !it.isRefreshing }
            assertNotNull(recovered.overview)
        }
    }

    private fun fixture(): ControllerFixture {
        val repository = FakeAnalyticsRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resolver = ListeningAnalyticsRangeResolver(
            Clock.fixed(Instant.parse("2026-03-18T19:00:00Z"), ZoneId.of("UTC")),
            AnalyticsZoneIdProvider { ZoneId.of("America/Los_Angeles") }
        )
        return ControllerFixture(repository, scope, ListeningAnalyticsController(repository, resolver, scope))
    }
}

private class ControllerFixture(
    val repository: FakeAnalyticsRepository,
    private val scope: CoroutineScope,
    val controller: ListeningAnalyticsController
) : AutoCloseable {
    override fun close() {
        controller.release()
        scope.cancel()
    }
}

private class FakeAnalyticsRepository : ListeningAnalyticsDataSource {
    val calls = AtomicInteger()
    val requests = Channel<ResolvedAnalyticsRange>(Channel.UNLIMITED)
    val cancellations = Channel<Unit>(Channel.UNLIMITED)
    val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    @Volatile var blockNext = false
    @Volatile var failNext = false

    override suspend fun getAnalyticsSnapshot(
        resolvedRange: ResolvedAnalyticsRange,
        sources: Set<ListeningSource>
    ): ListeningAnalyticsSnapshot {
        calls.incrementAndGet()
        requests.send(resolvedRange)
        if (failNext) {
            failNext = false
            throw IllegalStateException("fixture failure")
        }
        if (blockNext) {
            blockNext = false
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                cancellations.trySend(Unit)
            }
        }
        val overview = ListeningOverview(
            playCounts = ListeningPlayCountBreakdown(0L, 0L, 0L),
            listeningTime = ListeningTimeBreakdown(0L, 0L),
            qualifiedDetailedPlayCount = 0L,
            naturalCompletionCount = 0L,
            nonQualifiedAttemptCount = 0L,
            detailedEventCount = 0L,
            firstDetailedEventAt = null,
            latestDetailedEventAt = null,
            firstKnownPlayAt = null,
            latestKnownPlayAt = null,
            hasLegacyBaseline = false
        )
        return ListeningAnalyticsSnapshot(
            resolvedRange = resolvedRange,
            overview = overview,
            trend = emptyList(),
            topTracks = emptyList(),
            topAlbums = emptyList(),
            topArtists = emptyList(),
            coverage = ListeningAnalyticsCoverage(false, false, 0L, 0L, hasDetailedEvents = false, earliestDetailedEventAt = null, latestDetailedEventAt = null)
        )
    }

    override fun observeAnalyticsInvalidations() = invalidations.onStart { emit(Unit) }
}

private suspend fun <T> Channel<T>.receiveWithin(): T = withTimeout(3_000L) { receive() }

private suspend fun kotlinx.coroutines.flow.StateFlow<io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState>.await(
    predicate: (io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState) -> Boolean
) = withTimeout(3_000L) { first(predicate) }

private fun ResolvedAnalyticsRange.preset(): AnalyticsRangePreset =
    (selection as AnalyticsRangeSelection.Preset).preset
