package io.github.rsgarrido.sazanami.data

import android.net.Uri
import java.util.LinkedHashMap

internal data class ArtworkMediaFingerprint(
    val sourceUri: String,
    val dateModifiedEpochSeconds: Long,
    val fileSizeBytes: Long
) {
    companion object {
        fun from(song: Song) = ArtworkMediaFingerprint(
            sourceUri = song.uri.toString(),
            dateModifiedEpochSeconds = song.dateModifiedEpochSeconds,
            fileSizeBytes = song.fileSizeBytes
        )
    }
}

internal sealed interface ArtworkResolutionKey {
    data class AlbumRepresentative(
        val albumIdentity: String,
        val representative: ArtworkMediaFingerprint,
        val resolverNamespace: String = ""
    ) : ArtworkResolutionKey

    data class Track(
        val media: ArtworkMediaFingerprint,
        val resolverNamespace: String = ""
    ) : ArtworkResolutionKey
}

internal sealed interface ArtworkResolution {
    data class Found(val uri: Uri) : ArtworkResolution
    data object Missing : ArtworkResolution
}

/** Bounded URI/result cache. It intentionally never retains decoded bitmaps. */
internal class ArtworkResolutionCache(
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES
) {
    init {
        require(maximumEntries > 0)
    }

    private val entries = LinkedHashMap<ArtworkResolutionKey, ArtworkResolution>(16, 0.75f, true)

    @Synchronized
    fun get(key: ArtworkResolutionKey): ArtworkResolution? = entries[key]

    @Synchronized
    fun put(key: ArtworkResolutionKey, resolution: ArtworkResolution) {
        entries[key] = resolution
        while (entries.size > maximumEntries) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    @Synchronized
    fun resolve(
        key: ArtworkResolutionKey,
        probe: () -> Uri?
    ): ArtworkResolution = get(key) ?: run {
        val resolved = probe()?.let { uri -> ArtworkResolution.Found(uri) }
            ?: ArtworkResolution.Missing
        put(key, resolved)
        resolved
    }

    internal fun size(): Int = synchronized(this) { entries.size }

    private companion object {
        const val DEFAULT_MAXIMUM_ENTRIES = 512
    }
}

/**
 * Resolves one representative per clear album/folder group for initial bulk artwork. Existing
 * current track-specific results are retained. Explicit track resolution remains available for
 * operations that intentionally refresh a unique per-track cover.
 */
internal class ProgressiveArtworkEnricher(
    private val cache: ArtworkResolutionCache,
    private val resolveEmbedded: (Song) -> Uri?,
    private val resolveFolder: (Song) -> Uri?,
    private val resolverNamespace: String = "",
    private val groupsPerBatch: Int = DEFAULT_GROUPS_PER_BATCH
) {
    init {
        require(groupsPerBatch > 0)
    }

    fun batches(songs: List<Song>): Sequence<List<Song>> = sequence {
        if (songs.isEmpty()) return@sequence
        val working = songs.associateByTo(linkedMapOf(), Song::membershipKey)
        var changedGroups = 0

        artworkGroups(songs).forEach { group ->
            if (group.all(::hasCurrentArtworkResolution)) return@forEach
            val representative = chooseRepresentative(group)
            val key = artworkResolutionKey(group, representative)
            val existingArtwork = group.firstNotNullOfOrNull { song ->
                song.albumArtUri.takeIf { hasCurrentArtworkResolution(song) }
            }
            val resolution = existingArtwork
                ?.let { uri -> ArtworkResolution.Found(uri) }
                ?.also { cache.put(key, it) }
                ?: cache.resolve(key) {
                    resolveArtwork(representative)
                }

            val resolvedUri = (resolution as? ArtworkResolution.Found)?.uri
            group.forEach { song ->
                val updated = if (hasCurrentArtworkResolution(song)) {
                    song
                } else {
                    song.copy(
                        albumArtUri = resolvedUri,
                        artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION
                    )
                }
                working[song.membershipKey()] = updated
            }
            changedGroups += 1
            if (changedGroups % groupsPerBatch == 0) {
                yield(songs.map { song -> working.getValue(song.membershipKey()) })
            }
        }

        if (changedGroups % groupsPerBatch != 0) {
            yield(songs.map { song -> working.getValue(song.membershipKey()) })
        }
    }

    fun resolveTrackSpecific(song: Song): Song {
        val key = ArtworkResolutionKey.Track(
            media = ArtworkMediaFingerprint.from(song),
            resolverNamespace = resolverNamespace
        )
        val resolution = cache.resolve(key) {
            resolveArtwork(song)
        }
        return song.copy(
            albumArtUri = (resolution as? ArtworkResolution.Found)?.uri,
            artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION
        )
    }

    private fun artworkResolutionKey(
        group: List<Song>,
        representative: Song
    ): ArtworkResolutionKey {
        val albumIdentity = bulkAlbumIdentity(group.first())
        return if (albumIdentity != null) {
            ArtworkResolutionKey.AlbumRepresentative(
                albumIdentity = albumIdentity,
                representative = ArtworkMediaFingerprint.from(representative),
                resolverNamespace = resolverNamespace
            )
        } else {
            ArtworkResolutionKey.Track(
                media = ArtworkMediaFingerprint.from(representative),
                resolverNamespace = resolverNamespace
            )
        }
    }

    private fun resolveArtwork(song: Song): Uri? =
        runCatching { resolveEmbedded(song) }.getOrNull()
            ?: runCatching { resolveFolder(song) }.getOrNull()

    private companion object {
        const val DEFAULT_GROUPS_PER_BATCH = 8
    }
}

internal fun artworkGroups(songs: List<Song>): List<List<Song>> = songs
    .groupBy { song ->
        bulkAlbumIdentity(song)
            ?: "track:${ArtworkMediaFingerprint.from(song)}"
    }
    .values
    .toList()

private fun bulkAlbumIdentity(song: Song): String? {
    val album = song.album.trim().lowercase()
    if (album.isBlank() || album == "unknown album") return null
    val albumArtist = song.albumArtist.ifBlank { song.artist }.trim().lowercase()
    val folder = normalizeLibraryFolderPath(song.folderPath).lowercase()
    return listOf(folder, albumArtist, album).joinToString("|")
}

private fun chooseRepresentative(group: List<Song>): Song = group.minWithOrNull(
    compareBy<Song>({ normalizedDiscNumber(it) }, { normalizedTrackNumber(it) }, Song::id)
) ?: error("Artwork group cannot be empty.")

private fun normalizedDiscNumber(song: Song): Int = song.discNumber
    ?.takeIf { it > 0 }
    ?: 1

private fun normalizedTrackNumber(song: Song): Int = (song.trackNumber % 1_000)
    .takeIf { it > 0 }
    ?: Int.MAX_VALUE

private fun hasCurrentArtworkResolution(song: Song): Boolean =
    song.artworkEnrichmentVersion >= CURRENT_ARTWORK_ENRICHMENT_VERSION &&
            EmbeddedArtworkContract.isCurrentReferenceFor(song.albumArtUri, song)
