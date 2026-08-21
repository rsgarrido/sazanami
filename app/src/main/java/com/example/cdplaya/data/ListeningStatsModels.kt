package com.example.cdplaya.data

import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource

data class ListeningDateRange(
    val startInclusive: Long,
    val endExclusive: Long
) {
    init {
        require(startInclusive < endExclusive) { "Listening date range must not be empty or reversed" }
    }
}

/** Null [sources] means every detailed source. An explicit set never claims legacy provenance. */
data class ListeningStatsFilter(
    val dateRange: ListeningDateRange? = null,
    val sources: Set<ListeningSource>? = null,
    val includeLegacyBaseline: Boolean = dateRange == null
) {
    init {
        require(sources == null || sources.isNotEmpty()) { "Source filter cannot be empty" }
        require(!includeLegacyBaseline || dateRange == null) {
            "Legacy baselines cannot be assigned to a detailed date range"
        }
    }

    internal val effectiveIncludeLegacy: Boolean
        get() = includeLegacyBaseline && dateRange == null && sources == null
}

data class ListeningPlayCountBreakdown(
    val totalPlayCount: Long,
    val legacyPlayCount: Long,
    val detailedPlayCount: Long
)

data class ListeningTimeBreakdown(
    val confirmedDetailedListeningMs: Long,
    val legacyPlayCountWithoutKnownDuration: Long
)

data class ListeningOverview(
    val playCounts: ListeningPlayCountBreakdown,
    val listeningTime: ListeningTimeBreakdown,
    val qualifiedDetailedPlayCount: Long,
    val naturalCompletionCount: Long,
    val nonQualifiedAttemptCount: Long,
    val detailedEventCount: Long,
    val firstDetailedEventAt: Long?,
    val latestDetailedEventAt: Long?,
    val firstKnownPlayAt: Long?,
    val latestKnownPlayAt: Long?,
    val hasLegacyBaseline: Boolean
)

data class ListeningBindingSnapshot(
    val localTrackBindingId: Long,
    val referenceKey: String,
    val mediaStoreId: Long?,
    val volumeName: String?,
    val contentUri: String?,
    val relativePath: String?,
    val displayName: String?,
    val fileSizeBytes: Long?,
    val dateModifiedEpochSeconds: Long?,
    val durationMs: Long?,
    val legacyStableKey: String?,
    val portableKey: String?,
    val portableKeyVersion: Int?,
    val missingSince: Long?
) {
    val isCurrentlyAvailable: Boolean get() = missingSince == null
}

data class TrackListeningStats(
    val trackIdentityId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val durationMs: Long?,
    val binding: ListeningBindingSnapshot?,
    val knownBindings: List<ListeningBindingSnapshot> = listOfNotNull(binding),
    val playCounts: ListeningPlayCountBreakdown,
    val confirmedDetailedListeningMs: Long,
    val detailedEventCount: Long,
    val naturalCompletionCount: Long,
    val nonQualifiedAttemptCount: Long,
    val firstKnownPlayAt: Long?,
    val latestKnownPlayAt: Long?,
    val latestDetailedEventAt: Long?,
    val effectiveRating: Int? = null
)

/** Canonical, provider-neutral history for one currently playable local identity. */
data class CanonicalListeningMetrics(
    val trackIdentityId: Long,
    val playCounts: ListeningPlayCountBreakdown,
    val detailedAttemptCount: Long,
    val confirmedDetailedListeningMs: Long,
    val naturalCompletionCount: Long,
    val nonQualifiedAttemptCount: Long,
    val firstPlayedAt: Long?,
    val lastPlayedAt: Long?,
    val latestDetailedEventAt: Long?,
    val effectiveRating: Int?
)

data class AlbumListeningStats(
    val groupingKey: String,
    val album: String,
    val albumArtist: String,
    val playCounts: ListeningPlayCountBreakdown,
    val confirmedDetailedListeningMs: Long,
    val naturalCompletionCount: Long,
    val trackCount: Long,
    val latestKnownPlayAt: Long?
)

data class ArtistListeningStats(
    val groupingKey: String,
    val artist: String,
    val playCounts: ListeningPlayCountBreakdown,
    val confirmedDetailedListeningMs: Long,
    val naturalCompletionCount: Long,
    val distinctTrackCount: Long,
    val distinctAlbumCount: Long,
    val latestKnownPlayAt: Long?
)

data class RecentListeningEvent(
    val eventUuid: String,
    val trackIdentityId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val source: ListeningSource,
    val startedAt: Long?,
    val endedAt: Long?,
    val attributionAt: Long,
    val listenedMs: Long,
    val qualifiedAsPlay: Boolean,
    val qualificationReason: ListeningQualificationReason,
    val endReason: ListeningEndReason?,
    val playbackSessionId: String?
)

data class RecentlyPlayedProjection(
    val track: TrackListeningStats
)

data class MostPlayedProjection(
    val track: TrackListeningStats
)

data class ProductionListeningHistoryProjections(
    val recentlyPlayed: List<RecentlyPlayedProjection>,
    val mostPlayed: List<MostPlayedProjection>
)

enum class TrackStatsOrder {
    QUALIFIED_PLAYS,
    LISTENING_TIME
}
