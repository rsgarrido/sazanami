package com.example.cdplaya.data

import androidx.room.withTransaction
import com.example.cdplaya.data.local.AlbumListeningStatsRow
import com.example.cdplaya.data.local.ArtistListeningStatsRow
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningOverviewRow
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningStatsDao
import com.example.cdplaya.data.local.ListeningStatsQueries
import com.example.cdplaya.data.local.ListeningStatsQuerySpec
import com.example.cdplaya.data.local.ListeningTrendBucketRow
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.RecentListeningEventRow
import com.example.cdplaya.data.local.TrackListeningStatsRow
import com.example.cdplaya.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

class ListeningStatsRepository(
    private val database: AppDatabase
) : ListeningAnalyticsDataSource {
    private val dao: ListeningStatsDao = database.listeningStatsDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeProductionHistory(): Flow<ProductionListeningHistoryProjections> =
        database.invalidationTracker.createFlow(
            "listening_events",
            "legacy_listening_baselines",
            "listening_track_identities",
            "local_track_bindings",
            emitInitialState = true
        )
            .conflate()
            .mapLatest { loadProductionHistory() }

    override fun observeAnalyticsInvalidations(): Flow<Unit> =
        database.invalidationTracker.createFlow(
            "listening_events",
            "legacy_listening_baselines",
            "listening_track_identities",
            "local_track_bindings",
            emitInitialState = true
        )
            .conflate()
            .map { Unit }

    override suspend fun getAnalyticsSnapshot(
        resolvedRange: ResolvedAnalyticsRange,
        sources: Set<ListeningSource>
    ): ListeningAnalyticsSnapshot {
        val explicitSources = sources.takeIf(Set<ListeningSource>::isNotEmpty)
        val filter = ListeningStatsFilter(
            dateRange = resolvedRange.eventRange,
            sources = explicitSources,
            includeLegacyBaseline = resolvedRange.canIncludeLegacyBaseline && explicitSources == null
        )
        val spec = filter.toSpec()
        val finiteBoundaries = if (resolvedRange.isAllTime) {
            null
        } else {
            ListeningAnalyticsBucketBuilder.build(resolvedRange)
        }
        val rows = database.withTransaction {
            val boundsRow = dao.getDetailedEventBounds(ListeningStatsQueries.detailedEventBounds(spec))
            val bounds = if (boundsRow.earliestStartedAt == null) {
                null
            } else {
                DetailedListeningEventBounds(
                    requireNotNull(boundsRow.earliestStartedAt),
                    requireNotNull(boundsRow.latestStartedAt)
                )
            }
            val boundaries = finiteBoundaries
                ?: ListeningAnalyticsBucketBuilder.build(resolvedRange, bounds)
            AnalyticsSnapshotRows(
                overview = dao.getOverview(ListeningStatsQueries.overview(spec)),
                trend = dao.getTrendBuckets(
                    ListeningStatsQueries.trend(boundaries, spec.sourceStorageValues)
                ),
                boundaries = boundaries,
                tracks = dao.getTrackStats(
                    ListeningStatsQueries.tracks(spec, orderByListeningTime = false, qualifiedOnly = true, limit = TOP_TRACK_LIMIT)
                ),
                albums = dao.getAlbumStats(ListeningStatsQueries.albums(spec, TOP_ALBUM_LIMIT)),
                artists = dao.getArtistStats(ListeningStatsQueries.artists(spec, TOP_ARTIST_LIMIT)),
                bounds = bounds
            )
        }
        val overview = rows.overview.toDomain()
        return ListeningAnalyticsSnapshot(
            resolvedRange = resolvedRange,
            overview = overview,
            trend = rows.trend.mapIndexed { index, row -> row.toDomain(rows.boundaries[index]) },
            topTracks = rows.tracks.map(TrackListeningStatsRow::toDomain),
            topAlbums = rows.albums.map(AlbumListeningStatsRow::toDomain),
            topArtists = rows.artists.map(ArtistListeningStatsRow::toDomain),
            coverage = ListeningAnalyticsCoverage(
                selectionCanIncludeLegacyPlays = filter.effectiveIncludeLegacy,
                hasLegacyPlays = overview.playCounts.legacyPlayCount > 0L,
                legacyQualifiedPlayCount = overview.playCounts.legacyPlayCount,
                detailedQualifiedPlayCount = overview.qualifiedDetailedPlayCount,
                hasDetailedEvents = rows.bounds != null,
                earliestDetailedEventAt = rows.bounds?.earliestStartedAt,
                latestDetailedEventAt = rows.bounds?.latestStartedAt
            )
        )
    }

    suspend fun getAllTimeOverview(
        sources: Set<ListeningSource>? = null,
        includeLegacyBaseline: Boolean = true
    ): ListeningOverview = getOverview(
        ListeningStatsFilter(
            sources = sources,
            includeLegacyBaseline = includeLegacyBaseline
        )
    )

    suspend fun getDetailedOverview(
        range: ListeningDateRange,
        sources: Set<ListeningSource>? = null
    ): ListeningOverview = getOverview(
        ListeningStatsFilter(
            dateRange = range,
            sources = sources,
            includeLegacyBaseline = false
        )
    )

    suspend fun getOverview(filter: ListeningStatsFilter): ListeningOverview =
        dao.getOverview(ListeningStatsQueries.overview(filter.toSpec())).toDomain()

    suspend fun getTopTracksByQualifiedPlays(
        limit: Int,
        filter: ListeningStatsFilter = ListeningStatsFilter()
    ): List<TrackListeningStats> = dao.getTrackStats(
        ListeningStatsQueries.tracks(
            spec = filter.toSpec(),
            orderByListeningTime = false,
            qualifiedOnly = true,
            limit = checkedLimit(limit)
        )
    ).map(TrackListeningStatsRow::toDomain)

    suspend fun getTopTracksByListeningTime(
        limit: Int,
        filter: ListeningStatsFilter = ListeningStatsFilter(includeLegacyBaseline = false)
    ): List<TrackListeningStats> = dao.getTrackStats(
        ListeningStatsQueries.tracks(
            spec = filter.toSpec(),
            orderByListeningTime = true,
            qualifiedOnly = false,
            limit = checkedLimit(limit)
        )
    ).map(TrackListeningStatsRow::toDomain)

    suspend fun getTopAlbums(
        limit: Int,
        filter: ListeningStatsFilter = ListeningStatsFilter()
    ): List<AlbumListeningStats> = dao.getAlbumStats(
        ListeningStatsQueries.albums(filter.toSpec(), checkedLimit(limit))
    ).map(AlbumListeningStatsRow::toDomain)

    suspend fun getTopArtists(
        limit: Int,
        filter: ListeningStatsFilter = ListeningStatsFilter()
    ): List<ArtistListeningStats> = dao.getArtistStats(
        ListeningStatsQueries.artists(filter.toSpec(), checkedLimit(limit))
    ).map(ArtistListeningStatsRow::toDomain)

    suspend fun getRecentlyPlayed(
        limit: Int,
        filter: ListeningStatsFilter = ListeningStatsFilter()
    ): List<RecentlyPlayedProjection> = dao.getTrackStats(
        ListeningStatsQueries.recentlyPlayed(filter.toSpec(), checkedLimit(limit))
    ).map { RecentlyPlayedProjection(it.toDomain()) }

    suspend fun getMostPlayed(
        limit: Int,
        filter: ListeningStatsFilter = ListeningStatsFilter()
    ): List<MostPlayedProjection> = dao.getTrackStats(
        ListeningStatsQueries.tracks(
            spec = filter.toSpec(),
            orderByListeningTime = false,
            qualifiedOnly = true,
            limit = checkedLimit(limit)
        )
    ).map { MostPlayedProjection(it.toDomain()) }

    suspend fun getRecentDetailedEvents(
        limit: Int,
        range: ListeningDateRange? = null,
        sources: Set<ListeningSource>? = null
    ): List<RecentListeningEvent> {
        val filter = ListeningStatsFilter(
            dateRange = range,
            sources = sources,
            includeLegacyBaseline = false
        )
        return dao.getRecentEvents(
            ListeningStatsQueries.recentEvents(filter.toSpec(), checkedLimit(limit))
        ).map(RecentListeningEventRow::toDomain)
    }

    private suspend fun loadProductionHistory(): ProductionListeningHistoryProjections {
        val filter = ListeningStatsFilter()
        val spec = filter.toSpec()
        val snapshot = database.withTransaction {
            ProductionHistoryRows(
                recentlyPlayed = dao.getTrackStats(
                    ListeningStatsQueries.recentlyPlayed(spec, Int.MAX_VALUE)
                ),
                mostPlayed = dao.getTrackStats(
                    ListeningStatsQueries.tracks(
                        spec = spec,
                        orderByListeningTime = false,
                        qualifiedOnly = true,
                        limit = Int.MAX_VALUE
                    )
                ),
                bindings = dao.getAllBindingsForProjectionResolution()
            )
        }
        val bindingsByIdentity = snapshot.bindings
            .groupBy(LocalTrackBindingEntity::trackIdentityId)
            .mapValues { (_, bindings) -> bindings.map(LocalTrackBindingEntity::toDomain) }
        return ProductionListeningHistoryProjections(
            recentlyPlayed = snapshot.recentlyPlayed.map { row ->
                RecentlyPlayedProjection(row.toDomain(bindingsByIdentity[row.trackIdentityId]))
            },
            mostPlayed = snapshot.mostPlayed.map { row ->
                MostPlayedProjection(row.toDomain(bindingsByIdentity[row.trackIdentityId]))
            }
        )
    }

    private fun ListeningStatsFilter.toSpec() = ListeningStatsQuerySpec(
        startInclusive = dateRange?.startInclusive,
        endExclusive = dateRange?.endExclusive,
        sourceStorageValues = (sources ?: ListeningSource.entries.toSet())
            .map(ListeningSource::storageValue)
            .sorted(),
        includeLegacyBaseline = effectiveIncludeLegacy
    )

    private fun checkedLimit(limit: Int): Int {
        require(limit in 1..MAX_RESULT_LIMIT) { "Result limit must be between 1 and $MAX_RESULT_LIMIT" }
        return limit
    }

    private companion object {
        const val MAX_RESULT_LIMIT = 10_000
        const val TOP_TRACK_LIMIT = 10
        const val TOP_ALBUM_LIMIT = 5
        const val TOP_ARTIST_LIMIT = 5
    }
}

private fun ListeningTrendBucketRow.toDomain(boundary: AnalyticsBucketBoundary): ListeningTrendBucket {
    check(bucketIndex == boundary.index)
    check(startInclusive == boundary.startInclusive)
    check(endExclusive == boundary.endExclusive)
    return ListeningTrendBucket(
        index = bucketIndex,
        startInclusive = startInclusive,
        endExclusive = endExclusive,
        granularity = boundary.granularity,
        listenedMs = listenedMs,
        qualifiedPlayCount = qualifiedPlayCount,
        totalAttemptCount = totalAttemptCount,
        naturalCompletionCount = naturalCompletionCount
    )
}

private fun ListeningOverviewRow.toDomain(): ListeningOverview {
    val total = safeAdd(legacyPlayCount, detailedQualifiedPlayCount)
    return ListeningOverview(
        playCounts = ListeningPlayCountBreakdown(total, legacyPlayCount, detailedQualifiedPlayCount),
        listeningTime = ListeningTimeBreakdown(
            confirmedDetailedListeningMs = detailedListeningMs,
            legacyPlayCountWithoutKnownDuration = legacyPlayCount
        ),
        qualifiedDetailedPlayCount = detailedQualifiedPlayCount,
        naturalCompletionCount = naturalCompletionCount,
        nonQualifiedAttemptCount = nonQualifiedAttemptCount,
        detailedEventCount = detailedEventCount,
        firstDetailedEventAt = firstDetailedEventAt,
        latestDetailedEventAt = latestDetailedEventAt,
        firstKnownPlayAt = firstKnownPlayAt,
        latestKnownPlayAt = latestKnownPlayAt,
        hasLegacyBaseline = legacyIdentityCount > 0L
    )
}

private fun TrackListeningStatsRow.toDomain(
    knownBindings: List<ListeningBindingSnapshot>? = null
): TrackListeningStats {
    val total = safeAdd(legacyPlayCount, detailedQualifiedPlayCount)
    val binding = localTrackBindingId?.let { bindingId ->
        ListeningBindingSnapshot(
            localTrackBindingId = bindingId,
            referenceKey = requireNotNull(referenceKey),
            mediaStoreId = mediaStoreId,
            volumeName = volumeName,
            contentUri = contentUri,
            relativePath = relativePath,
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            dateModifiedEpochSeconds = dateModifiedEpochSeconds,
            durationMs = bindingDurationMsSnapshot,
            legacyStableKey = legacyStableKey,
            portableKey = portableKey,
            portableKeyVersion = portableKeyVersion,
            missingSince = missingSince
        )
    }
    return TrackListeningStats(
        trackIdentityId = trackIdentityId,
        title = titleSnapshot,
        artist = artistSnapshot,
        album = albumSnapshot,
        albumArtist = albumArtistSnapshot,
        durationMs = durationMsSnapshot,
        binding = knownBindings?.firstOrNull() ?: binding,
        knownBindings = knownBindings ?: listOfNotNull(binding),
        playCounts = ListeningPlayCountBreakdown(total, legacyPlayCount, detailedQualifiedPlayCount),
        confirmedDetailedListeningMs = detailedListeningMs,
        detailedEventCount = detailedEventCount,
        naturalCompletionCount = naturalCompletionCount,
        nonQualifiedAttemptCount = nonQualifiedAttemptCount,
        firstKnownPlayAt = firstKnownPlayAt,
        latestKnownPlayAt = latestKnownPlayAt,
        latestDetailedEventAt = latestDetailedEventAt
    )
}

private fun LocalTrackBindingEntity.toDomain() = ListeningBindingSnapshot(
    localTrackBindingId = id,
    referenceKey = referenceKey,
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    contentUri = contentUri,
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    durationMs = durationMsSnapshot,
    legacyStableKey = legacyStableKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion,
    missingSince = missingSince
)

private fun AlbumListeningStatsRow.toDomain(): AlbumListeningStats {
    val total = safeAdd(legacyPlayCount, detailedQualifiedPlayCount)
    return AlbumListeningStats(
        groupingKey = groupingKey,
        album = displayAlbum,
        albumArtist = displayAlbumArtist,
        playCounts = ListeningPlayCountBreakdown(total, legacyPlayCount, detailedQualifiedPlayCount),
        confirmedDetailedListeningMs = detailedListeningMs,
        naturalCompletionCount = naturalCompletionCount,
        trackCount = trackCount,
        latestKnownPlayAt = latestKnownPlayAt
    )
}

private fun ArtistListeningStatsRow.toDomain(): ArtistListeningStats {
    val total = safeAdd(legacyPlayCount, detailedQualifiedPlayCount)
    return ArtistListeningStats(
        groupingKey = groupingKey,
        artist = displayArtist,
        playCounts = ListeningPlayCountBreakdown(total, legacyPlayCount, detailedQualifiedPlayCount),
        confirmedDetailedListeningMs = detailedListeningMs,
        naturalCompletionCount = naturalCompletionCount,
        distinctTrackCount = distinctTrackCount,
        distinctAlbumCount = distinctAlbumCount,
        latestKnownPlayAt = latestKnownPlayAt
    )
}

private fun RecentListeningEventRow.toDomain() = RecentListeningEvent(
    eventUuid = eventUuid,
    trackIdentityId = trackIdentityId,
    title = titleSnapshot,
    artist = artistSnapshot,
    album = albumSnapshot,
    source = ListeningSource.fromStorageValue(source),
    startedAt = startedAt,
    endedAt = endedAt,
    attributionAt = attributionAt,
    listenedMs = listenedMs,
    qualifiedAsPlay = qualifiedAsPlay,
    qualificationReason = ListeningQualificationReason.fromStorageValue(qualificationReason),
    endReason = endReason?.let(ListeningEndReason::fromStorageValue),
    playbackSessionId = playbackSessionId
)

private fun safeAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private data class ProductionHistoryRows(
    val recentlyPlayed: List<TrackListeningStatsRow>,
    val mostPlayed: List<TrackListeningStatsRow>,
    val bindings: List<LocalTrackBindingEntity>
)

private data class AnalyticsSnapshotRows(
    val overview: ListeningOverviewRow,
    val trend: List<ListeningTrendBucketRow>,
    val boundaries: List<AnalyticsBucketBoundary>,
    val tracks: List<TrackListeningStatsRow>,
    val albums: List<AlbumListeningStatsRow>,
    val artists: List<ArtistListeningStatsRow>,
    val bounds: DetailedListeningEventBounds?
)
