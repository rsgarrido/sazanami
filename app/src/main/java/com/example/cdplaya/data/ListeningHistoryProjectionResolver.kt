package com.example.cdplaya.data

data class ResolvedProductionListeningHistory(
    val recentlyPlayed: List<Song>,
    val mostPlayed: List<Song>,
    val duplicateResolutions: List<DuplicateListeningHistoryResolution> = emptyList()
)

enum class ListeningHistoryCollection {
    RECENTLY_PLAYED,
    MOST_PLAYED
}

data class DuplicateListeningHistoryResolution(
    val collection: ListeningHistoryCollection,
    val retainedTrackIdentityId: Long,
    val duplicateTrackIdentityId: Long,
    val retainedSong: Song,
    val duplicateSong: Song
)

/** Maps identity projections to one immutable, folder-filtered library snapshot. */
object ListeningHistoryProjectionResolver {
    fun resolve(
        projections: ProductionListeningHistoryProjections,
        index: SongReferenceIndex,
        visibleMembershipKeys: Set<String>
    ): ResolvedProductionListeningHistory {
        val recentlyPlayed = resolveDistinctSongs(
            tracks = projections.recentlyPlayed.map(RecentlyPlayedProjection::track),
            collection = ListeningHistoryCollection.RECENTLY_PLAYED,
            index = index,
            visibleMembershipKeys = visibleMembershipKeys
        )
        val mostPlayed = resolveDistinctSongs(
            tracks = projections.mostPlayed.map(MostPlayedProjection::track),
            collection = ListeningHistoryCollection.MOST_PLAYED,
            index = index,
            visibleMembershipKeys = visibleMembershipKeys
        )
        return ResolvedProductionListeningHistory(
            recentlyPlayed = recentlyPlayed.songs,
            mostPlayed = mostPlayed.songs,
            duplicateResolutions = recentlyPlayed.duplicates + mostPlayed.duplicates
        )
    }

    private fun resolveDistinctSongs(
        tracks: List<TrackListeningStats>,
        collection: ListeningHistoryCollection,
        index: SongReferenceIndex,
        visibleMembershipKeys: Set<String>
    ): DistinctResolution {
        val retainedByStableKey = linkedMapOf<String, ResolvedTrack>()
        val duplicates = mutableListOf<DuplicateListeningHistoryResolution>()

        tracks.forEach { track ->
            val song = resolveTrack(track, index, visibleMembershipKeys) ?: return@forEach
            val stableKey = song.stableUiKey()
            val retained = retainedByStableKey[stableKey]
            if (retained == null) {
                retainedByStableKey[stableKey] = ResolvedTrack(track.trackIdentityId, song)
            } else {
                duplicates += DuplicateListeningHistoryResolution(
                    collection = collection,
                    retainedTrackIdentityId = retained.trackIdentityId,
                    duplicateTrackIdentityId = track.trackIdentityId,
                    retainedSong = retained.song,
                    duplicateSong = song
                )
            }
        }

        return DistinctResolution(
            songs = retainedByStableKey.values.map(ResolvedTrack::song),
            duplicates = duplicates
        )
    }

    private fun resolveTrack(
        track: TrackListeningStats,
        index: SongReferenceIndex,
        visibleMembershipKeys: Set<String>
    ): Song? {
        val bindings = track.knownBindings.ifEmpty { listOfNotNull(track.binding) }
        val preferred = bindings.firstOrNull() ?: return null
        when (val resolution = index.resolve(preferred.toReference(track))) {
            is SongReferenceResolution.Resolved -> return resolution.song
                .takeIf { it.membershipKey() in visibleMembershipKeys }
            is SongReferenceResolution.Ambiguous -> return null
            SongReferenceResolution.NotFound -> Unit
        }

        var resolvedSong: Song? = null
        for (binding in bindings.drop(1)) {
            when (val resolution = index.resolve(binding.toReference(track))) {
                is SongReferenceResolution.Resolved -> {
                    val previous = resolvedSong
                    if (previous != null && previous != resolution.song) return null
                    resolvedSong = resolution.song
                }
                is SongReferenceResolution.Ambiguous -> return null
                SongReferenceResolution.NotFound -> Unit
            }
        }
        return resolvedSong?.takeIf { it.membershipKey() in visibleMembershipKeys }
    }

    private fun ListeningBindingSnapshot.toReference(track: TrackListeningStats) = SongReference(
        mediaStoreId = mediaStoreId,
        volumeName = volumeName.orEmpty(),
        contentUri = contentUri.orEmpty(),
        relativePath = relativePath.orEmpty(),
        displayName = displayName.orEmpty(),
        fileSizeBytes = fileSizeBytes ?: 0L,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds ?: 0L,
        duration = durationMs ?: track.durationMs ?: 0L,
        title = track.title,
        artist = track.artist,
        album = track.album,
        albumArtist = track.albumArtist.orEmpty(),
        legacyStableKey = legacyStableKey.orEmpty(),
        portableKey = portableKey.orEmpty(),
        portableKeyVersion = portableKeyVersion ?: SongIdentity.PORTABLE_KEY_VERSION
    )

    private data class ResolvedTrack(
        val trackIdentityId: Long,
        val song: Song
    )

    private data class DistinctResolution(
        val songs: List<Song>,
        val duplicates: List<DuplicateListeningHistoryResolution>
    )
}
