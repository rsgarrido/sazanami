package com.example.cdplaya.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class ListeningOverviewRow(
    val legacyPlayCount: Long,
    val detailedQualifiedPlayCount: Long,
    val detailedListeningMs: Long,
    val naturalCompletionCount: Long,
    val nonQualifiedAttemptCount: Long,
    val detailedEventCount: Long,
    val firstDetailedEventAt: Long?,
    val latestDetailedEventAt: Long?,
    val firstKnownPlayAt: Long?,
    val latestKnownPlayAt: Long?,
    val legacyIdentityCount: Long
)

data class TrackListeningStatsRow(
    val trackIdentityId: Long,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val albumSnapshot: String,
    val albumArtistSnapshot: String?,
    val durationMsSnapshot: Long?,
    val localTrackBindingId: Long?,
    val referenceKey: String?,
    val mediaStoreId: Long?,
    val volumeName: String?,
    val contentUri: String?,
    val relativePath: String?,
    val displayName: String?,
    val fileSizeBytes: Long?,
    val dateModifiedEpochSeconds: Long?,
    val bindingDurationMsSnapshot: Long?,
    val legacyStableKey: String?,
    val portableKey: String?,
    val portableKeyVersion: Int?,
    val missingSince: Long?,
    val legacyPlayCount: Long,
    val detailedQualifiedPlayCount: Long,
    val detailedListeningMs: Long,
    val detailedEventCount: Long,
    val naturalCompletionCount: Long,
    val nonQualifiedAttemptCount: Long,
    val firstKnownPlayAt: Long?,
    val latestKnownPlayAt: Long?,
    val latestDetailedEventAt: Long?
)

data class AlbumListeningStatsRow(
    val groupingKey: String,
    val displayAlbum: String,
    val displayAlbumArtist: String,
    val legacyPlayCount: Long,
    val detailedQualifiedPlayCount: Long,
    val detailedListeningMs: Long,
    val naturalCompletionCount: Long,
    val trackCount: Long,
    val latestKnownPlayAt: Long?
)

data class ArtistListeningStatsRow(
    val groupingKey: String,
    val displayArtist: String,
    val legacyPlayCount: Long,
    val detailedQualifiedPlayCount: Long,
    val detailedListeningMs: Long,
    val naturalCompletionCount: Long,
    val distinctTrackCount: Long,
    val distinctAlbumCount: Long,
    val latestKnownPlayAt: Long?
)

data class RecentListeningEventRow(
    val eventUuid: String,
    val trackIdentityId: Long,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val albumSnapshot: String,
    val source: String,
    val startedAt: Long?,
    val endedAt: Long?,
    val attributionAt: Long,
    val listenedMs: Long,
    val qualifiedAsPlay: Boolean,
    val qualificationReason: String,
    val endReason: String?,
    val playbackSessionId: String?
)

data class DetailedListeningEventBoundsRow(
    val earliestStartedAt: Long?,
    val latestStartedAt: Long?
)

data class ListeningTrendBucketRow(
    val bucketIndex: Int,
    val startInclusive: Long,
    val endExclusive: Long,
    val listenedMs: Long,
    val qualifiedPlayCount: Long,
    val totalAttemptCount: Long,
    val naturalCompletionCount: Long
)

@Dao
interface ListeningStatsDao {
    @RawQuery
    suspend fun getOverview(query: SupportSQLiteQuery): ListeningOverviewRow

    @RawQuery
    suspend fun getTrackStats(query: SupportSQLiteQuery): List<TrackListeningStatsRow>

    @RawQuery
    suspend fun getAlbumStats(query: SupportSQLiteQuery): List<AlbumListeningStatsRow>

    @RawQuery
    suspend fun getArtistStats(query: SupportSQLiteQuery): List<ArtistListeningStatsRow>

    @RawQuery
    suspend fun getRecentEvents(query: SupportSQLiteQuery): List<RecentListeningEventRow>

    @RawQuery
    suspend fun getDetailedEventBounds(query: SupportSQLiteQuery): DetailedListeningEventBoundsRow

    @RawQuery
    suspend fun getTrendBuckets(query: SupportSQLiteQuery): List<ListeningTrendBucketRow>

    @Query(
        """
        SELECT * FROM local_track_bindings
        ORDER BY trackIdentityId ASC,
            CASE WHEN missingSince IS NULL THEN 0 ELSE 1 END ASC,
            lastSeenAt DESC,
            id ASC
        """
    )
    suspend fun getAllBindingsForProjectionResolution(): List<LocalTrackBindingEntity>
}
