package com.example.cdplaya.data

data class LibraryRefreshResult(
    val songs: List<Song>,
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val removedCount: Int = 0,
    val movedCount: Int = 0,
    val reusedCount: Int = 0,
    val artworkRepairCount: Int = 0,
    val successfulCompleteScan: Boolean = true
) {
    val enrichmentCount: Int get() = addedCount + updatedCount
    val requiresCacheWrite: Boolean
        get() = addedCount > 0 || updatedCount > 0 || removedCount > 0 || movedCount > 0
}

object LibraryRefreshEngine {
    fun fallbackForIncompleteScan(
        cachedSongs: List<Song>,
        indexSongs: List<Song>?
    ): LibraryRefreshResult? {
        if (indexSongs != null && (indexSongs.isNotEmpty() || cachedSongs.isEmpty())) return null
        return LibraryRefreshResult(
            songs = cachedSongs,
            reusedCount = cachedSongs.size,
            successfulCompleteScan = false
        )
    }

    fun refresh(
        cachedSongs: List<Song>,
        indexSongs: List<Song>,
        requiresEnrichment: (cached: Song, current: Song) -> Boolean =
            { cached, current -> cached.requiresArtworkRepair(current) },
        enrich: (Song) -> Song
    ): LibraryRefreshResult {
        val unmatchedCached = cachedSongs.toMutableSet()
        val unmatchedIndex = indexSongs.toMutableSet()
        val refreshedByIndex = mutableMapOf<Song, Song>()
        var reused = 0
        var updated = 0
        var moved = 0

        indexSongs.forEach { indexSong ->
            val candidates = unmatchedCached.filter { cached ->
                hasExactLocalOrSourceMatch(cached, indexSong)
            }
            if (candidates.size == 1) {
                val cached = candidates.single()
                val output = if (
                    sourceFileChanged(cached, indexSong) ||
                    requiresEnrichment(cached, indexSong)
                ) {
                    updated += 1
                    enrich(indexSong.withPreservedDateAdded(cached))
                } else {
                    reused += 1
                    cached.withCurrentSource(indexSong)
                }
                refreshedByIndex[indexSong] = output
                unmatchedCached -= cached
                unmatchedIndex -= indexSong
            }
        }

        cachedSongs.filter { it in unmatchedCached }.forEach { cached ->
            val remaining = indexSongs.filter { it in unmatchedIndex }
            val resolution = SongReferenceResolver.resolve(cached.toSongReference(), remaining)
            if (resolution is SongReferenceResolution.Resolved) {
                val indexSong = resolution.song
                val output = if (
                    sourceFileChanged(cached, indexSong) ||
                    requiresEnrichment(cached, indexSong)
                ) {
                    updated += 1
                    enrich(indexSong.withPreservedDateAdded(cached))
                } else {
                    reused += 1
                    cached.withCurrentSource(indexSong)
                }
                moved += 1
                refreshedByIndex[indexSong] = output
                unmatchedCached -= cached
                unmatchedIndex -= indexSong
            }
        }

        unmatchedIndex.forEach { indexSong -> refreshedByIndex[indexSong] = enrich(indexSong) }

        return LibraryRefreshResult(
            songs = indexSongs.mapNotNull(refreshedByIndex::get),
            addedCount = unmatchedIndex.size,
            updatedCount = updated,
            removedCount = unmatchedCached.size,
            movedCount = moved,
            reusedCount = reused
        )
    }

    private fun hasExactLocalOrSourceMatch(first: Song, second: Song): Boolean {
        val firstIdentity = first.songIdentity()
        val secondIdentity = second.songIdentity()
        return firstIdentity.localKey != null && firstIdentity.localKey == secondIdentity.localKey ||
            firstIdentity.sourceKey != null && firstIdentity.sourceKey == secondIdentity.sourceKey
    }

    private fun sourceFileChanged(cached: Song, current: Song): Boolean {
        if (cached.fileSizeBytes > 0L && current.fileSizeBytes > 0L &&
            cached.fileSizeBytes != current.fileSizeBytes
        ) return true
        if (cached.dateModifiedEpochSeconds > 0L && current.dateModifiedEpochSeconds > 0L &&
            cached.dateModifiedEpochSeconds != current.dateModifiedEpochSeconds
        ) return true
        if (current.year != null && cached.year != current.year) return true
        return cached.duration > 0L && current.duration > 0L &&
            kotlin.math.abs(cached.duration - current.duration) > 2_000L
    }

    private fun Song.withPreservedDateAdded(cached: Song): Song = copy(
        dateAddedEpochSeconds = dateAddedEpochSeconds.takeIf { it > 0L }
            ?: cached.dateAddedEpochSeconds
    )

    private fun Song.withCurrentSource(current: Song): Song = copy(
        id = current.id,
        uri = current.uri,
        filePath = current.filePath,
        folderPath = current.folderPath,
        volumeName = current.volumeName,
        displayName = current.displayName,
        relativePath = current.relativePath,
        fileSizeBytes = current.fileSizeBytes.takeIf { it > 0L } ?: fileSizeBytes,
        dateAddedEpochSeconds = current.dateAddedEpochSeconds.takeIf { it > 0L }
            ?: dateAddedEpochSeconds,
        dateModifiedEpochSeconds = current.dateModifiedEpochSeconds.takeIf { it > 0L }
            ?: dateModifiedEpochSeconds,
        year = current.year ?: year
    )
}

internal fun Song.requiresArtworkRepair(current: Song): Boolean {
    if (artworkEnrichmentVersion < CURRENT_ARTWORK_ENRICHMENT_VERSION) return true
    if (displayName.isBlank() && current.displayName.isNotBlank()) return true
    if (volumeName.isBlank() && current.volumeName.isNotBlank()) return true
    if (fileSizeBytes <= 0L && current.fileSizeBytes > 0L) return true
    if (dateModifiedEpochSeconds <= 0L && current.dateModifiedEpochSeconds > 0L) return true
    return !EmbeddedArtworkContract.isCurrentReferenceFor(albumArtUri, current)
}
