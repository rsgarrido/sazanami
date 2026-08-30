package io.github.rsgarrido.sazanami.ui.state

import io.github.rsgarrido.sazanami.data.AlbumListeningStats
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.ArtistListeningStats
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsCoverage
import io.github.rsgarrido.sazanami.data.ListeningOverview
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.ResolvedAnalyticsRange
import io.github.rsgarrido.sazanami.data.TrackListeningStats

enum class ListeningAnalyticsErrorKind {
    RANGE_RESOLUTION,
    SNAPSHOT_LOAD
}

data class ListeningAnalyticsError(
    val kind: ListeningAnalyticsErrorKind,
    val retryable: Boolean,
    val cause: Throwable
)

data class ListeningAnalyticsUiState(
    val selectedRange: AnalyticsRangeSelection = AnalyticsRangeSelection.Default,
    val resolvedRange: ResolvedAnalyticsRange? = null,
    val overview: ListeningOverview? = null,
    val trend: List<ListeningTrendBucket> = emptyList(),
    val topTracks: List<TrackListeningStats> = emptyList(),
    val topAlbums: List<AlbumListeningStats> = emptyList(),
    val topArtists: List<ArtistListeningStats> = emptyList(),
    val coverage: ListeningAnalyticsCoverage? = null,
    val trendMetric: ListeningTrendMetric = ListeningTrendMetric.RECORDED_LISTENING_TIME,
    val rankingCategory: ListeningRankingCategory = ListeningRankingCategory.TRACKS,
    val isActive: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: ListeningAnalyticsError? = null
)
