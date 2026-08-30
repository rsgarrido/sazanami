package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsDataSource
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsRangeResolver
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsSnapshot
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsError
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsErrorKind
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningAnalyticsController(
    private val repository: ListeningAnalyticsDataSource,
    private val rangeResolver: ListeningAnalyticsRangeResolver,
    private val scope: CoroutineScope,
    initialRange: AnalyticsRangeSelection = AnalyticsRangeSelection.Default,
    private val sources: Set<ListeningSource> = emptySet()
) {
    private val _state = MutableStateFlow(ListeningAnalyticsUiState(selectedRange = initialRange))
    val state: StateFlow<ListeningAnalyticsUiState> = _state.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val versionLock = Any()
    private var requestVersion = 0L
    private var activeJob: Job? = null

    fun setActive(active: Boolean) {
        if (_state.value.isActive == active) return
        if (!active) {
            invalidatePendingRequests()
            activeJob?.cancel()
            activeJob = null
            _state.update {
                it.copy(isActive = false, isInitialLoading = false, isRefreshing = false)
            }
            return
        }

        _state.update {
            it.copy(
                isActive = true,
                isInitialLoading = it.resolvedRange == null,
                isRefreshing = it.resolvedRange != null,
                error = null
            )
        }
        activeJob = scope.launch {
            refreshFlow().conflate().collectLatest { loadLatestSelection() }
        }
    }

    fun selectRange(selection: AnalyticsRangeSelection) {
        if (_state.value.selectedRange == selection) return
        invalidatePendingRequests()
        _state.update {
            it.copy(
                selectedRange = selection,
                isInitialLoading = it.isActive && it.resolvedRange == null,
                isRefreshing = it.isActive && it.resolvedRange != null,
                error = null
            )
        }
        if (_state.value.isActive) refreshRequests.tryEmit(Unit)
    }

    fun retry() {
        if (!_state.value.isActive) return
        invalidatePendingRequests()
        _state.update {
            it.copy(
                isInitialLoading = it.resolvedRange == null,
                isRefreshing = it.resolvedRange != null,
                error = null
            )
        }
        refreshRequests.tryEmit(Unit)
    }

    fun selectTrendMetric(metric: ListeningTrendMetric) {
        _state.update { it.copy(trendMetric = metric) }
    }

    fun selectRankingCategory(category: ListeningRankingCategory) {
        _state.update { it.copy(rankingCategory = category) }
    }

    fun release() {
        setActive(false)
    }

    private fun refreshFlow(): Flow<Unit> = kotlinx.coroutines.flow.merge(
        refreshRequests,
        repository.observeAnalyticsInvalidations()
    )

    private suspend fun loadLatestSelection() {
        val selection = _state.value.selectedRange
        val version = nextRequestVersion()
        _state.update {
            if (!it.isActive || it.selectedRange != selection) it else it.copy(
                isInitialLoading = it.resolvedRange == null,
                isRefreshing = it.resolvedRange != null,
                error = null
            )
        }
        val resolved = try {
            rangeResolver.resolve(selection)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            publishError(version, selection, ListeningAnalyticsErrorKind.RANGE_RESOLUTION, error)
            return
        }
        val snapshot = try {
            repository.getAnalyticsSnapshot(resolved, sources)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            publishError(version, selection, ListeningAnalyticsErrorKind.SNAPSHOT_LOAD, error)
            return
        }
        if (!isCurrent(version, selection)) return
        publishSnapshot(snapshot)
    }

    private fun publishSnapshot(snapshot: ListeningAnalyticsSnapshot) {
        _state.update {
            it.copy(
                resolvedRange = snapshot.resolvedRange,
                overview = snapshot.overview,
                trend = snapshot.trend,
                topTracks = snapshot.topTracks,
                topAlbums = snapshot.topAlbums,
                topArtists = snapshot.topArtists,
                coverage = snapshot.coverage,
                isInitialLoading = false,
                isRefreshing = false,
                error = null
            )
        }
    }

    private fun publishError(
        version: Long,
        selection: AnalyticsRangeSelection,
        kind: ListeningAnalyticsErrorKind,
        cause: Throwable
    ) {
        if (!isCurrent(version, selection)) return
        _state.update {
            it.copy(
                isInitialLoading = false,
                isRefreshing = false,
                error = ListeningAnalyticsError(kind, retryable = true, cause = cause)
            )
        }
    }

    private fun nextRequestVersion(): Long = synchronized(versionLock) { ++requestVersion }

    private fun invalidatePendingRequests() {
        synchronized(versionLock) { requestVersion++ }
    }

    private fun isCurrent(version: Long, selection: AnalyticsRangeSelection): Boolean =
        synchronized(versionLock) { version == requestVersion } &&
            _state.value.isActive &&
            _state.value.selectedRange == selection
}
