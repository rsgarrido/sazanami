package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.ListeningSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AnalyticsRangePreset {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    THIS_YEAR,
    ALL_TIME
}

sealed interface AnalyticsRangeSelection {
    data class Preset(
        val preset: AnalyticsRangePreset
    ) : AnalyticsRangeSelection

    data class Custom(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate
    ) : AnalyticsRangeSelection {
        init {
            require(!endDateInclusive.isBefore(startDate)) {
                "Custom analytics range end date must not precede its start date"
            }
        }
    }

    companion object {
        val Default: AnalyticsRangeSelection = Preset(AnalyticsRangePreset.LAST_30_DAYS)
    }
}

data class ResolvedAnalyticsRange(
    val selection: AnalyticsRangeSelection,
    val eventRange: ListeningDateRange?,
    val zoneId: ZoneId,
    val resolvedAt: Instant
) {
    val isAllTime: Boolean
        get() = selection == AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME)

    val canIncludeLegacyBaseline: Boolean
        get() = isAllTime

    init {
        require(isAllTime == (eventRange == null)) {
            "Only All Time analytics may omit an event range"
        }
    }
}

enum class AnalyticsBucketGranularity {
    HOUR,
    DAY,
    MONTH,
    YEAR
}

data class AnalyticsBucketBoundary(
    val index: Int,
    val startInclusive: Long,
    val endExclusive: Long,
    val localStart: ZonedDateTime,
    val granularity: AnalyticsBucketGranularity
) {
    init {
        require(index >= 0)
        require(startInclusive < endExclusive)
    }
}

data class ListeningTrendBucket(
    val index: Int,
    val startInclusive: Long,
    val endExclusive: Long,
    val granularity: AnalyticsBucketGranularity,
    val listenedMs: Long,
    val qualifiedPlayCount: Long,
    val totalAttemptCount: Long,
    val naturalCompletionCount: Long
) {
    init {
        require(index >= 0)
        require(startInclusive < endExclusive)
        require(listenedMs >= 0L)
        require(qualifiedPlayCount >= 0L)
        require(totalAttemptCount >= 0L)
        require(naturalCompletionCount >= 0L)
    }
}

data class DetailedListeningEventBounds(
    val earliestStartedAt: Long,
    val latestStartedAt: Long
) {
    init {
        require(earliestStartedAt <= latestStartedAt)
    }
}

data class ListeningAnalyticsCoverage(
    val selectionCanIncludeLegacyPlays: Boolean,
    val hasLegacyPlays: Boolean,
    val legacyQualifiedPlayCount: Long,
    val detailedQualifiedPlayCount: Long,
    val recordedListeningTimeIsDetailedOnly: Boolean = true,
    val trendIsDetailedOnly: Boolean = true,
    val hasDetailedEvents: Boolean,
    val earliestDetailedEventAt: Long?,
    val latestDetailedEventAt: Long?
) {
    init {
        require(legacyQualifiedPlayCount >= 0L)
        require(detailedQualifiedPlayCount >= 0L)
        require((earliestDetailedEventAt == null) == (latestDetailedEventAt == null))
        require(hasDetailedEvents == (earliestDetailedEventAt != null))
    }
}

data class ListeningAnalyticsSnapshot(
    val resolvedRange: ResolvedAnalyticsRange,
    val overview: ListeningOverview,
    val trend: List<ListeningTrendBucket>,
    val topTracks: List<TrackListeningStats>,
    val topAlbums: List<AlbumListeningStats>,
    val topArtists: List<ArtistListeningStats>,
    val coverage: ListeningAnalyticsCoverage
)

enum class ListeningTrendMetric {
    RECORDED_LISTENING_TIME,
    QUALIFIED_PLAYS
}

enum class ListeningRankingCategory {
    TRACKS,
    ARTISTS,
    ALBUMS
}

interface ListeningAnalyticsDataSource {
    suspend fun getAnalyticsSnapshot(
        resolvedRange: ResolvedAnalyticsRange,
        sources: Set<ListeningSource> = emptySet()
    ): ListeningAnalyticsSnapshot

    fun observeAnalyticsInvalidations(): kotlinx.coroutines.flow.Flow<Unit>
}
