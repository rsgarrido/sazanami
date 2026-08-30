package io.github.rsgarrido.sazanami.data

sealed interface SongReferenceResolution {
    data class Resolved(
        val song: Song,
        val matchType: SongReferenceMatchType
    ) : SongReferenceResolution

    data class Ambiguous(
        val candidates: List<Song>,
        val matchType: SongReferenceMatchType
    ) : SongReferenceResolution

    data object NotFound : SongReferenceResolution
}

enum class SongReferenceMatchType {
    LOCAL,
    SOURCE,
    FILE_SIGNATURE,
    PORTABLE,
    LEGACY
}

/**
 * Immutable lookup index for one library snapshot.
 *
 * Candidate lists are retained at every tier so duplicate identities remain explicitly
 * ambiguous. Song identities and deterministic ordering are computed once while the index is
 * built instead of once per persisted row.
 */
class SongReferenceIndex private constructor(
    private val localCandidates: Map<String, List<Song>>,
    private val sourceCandidates: Map<String, List<Song>>,
    private val signatureCandidates: Map<String, List<Song>>,
    private val portableCandidates: Map<String, List<Song>>,
    private val legacyCandidates: Map<String, List<Song>>
) {
    fun resolve(reference: SongReference): SongReferenceResolution {
        resolveTier(reference.localLookupKeys().flatMapTo(LinkedHashSet()) { key ->
            localCandidates[key].orEmpty()
        }.toList(), SongReferenceMatchType.LOCAL)?.let { return it }

        reference.sourceLookupKey()?.let { key ->
            resolveTier(sourceCandidates[key].orEmpty(), SongReferenceMatchType.SOURCE)
                ?.let { return it }
        }

        reference.signatureLookupKey()?.let { key ->
            val durationMatches = signatureCandidates[key].orEmpty().filter { song ->
                durationsMatch(song.duration, reference.duration)
            }
            resolveTier(durationMatches, SongReferenceMatchType.FILE_SIGNATURE)?.let { return it }
        }

        reference.effectivePortableKey()?.let { key ->
            resolveTier(portableCandidates[key].orEmpty(), SongReferenceMatchType.PORTABLE)
                ?.let { return it }
        }

        reference.legacyStableKey.takeIf { it.isNotBlank() }?.let { key ->
            resolveTier(legacyCandidates[key].orEmpty(), SongReferenceMatchType.LEGACY)
                ?.let { return it }
        }

        return SongReferenceResolution.NotFound
    }

    private fun resolveTier(
        candidates: List<Song>,
        matchType: SongReferenceMatchType
    ): SongReferenceResolution? {
        if (candidates.isEmpty()) return null
        val deterministicCandidates = if (candidates.size == 1) {
            candidates
        } else {
            candidates.distinct().sortedWith(songResolutionOrder)
        }
        return if (deterministicCandidates.size == 1) {
            SongReferenceResolution.Resolved(deterministicCandidates.single(), matchType)
        } else {
            SongReferenceResolution.Ambiguous(deterministicCandidates, matchType)
        }
    }

    companion object {
        val EMPTY: SongReferenceIndex = build(emptyList())

        fun build(songs: Collection<Song>): SongReferenceIndex {
            val local = mutableMapOf<String, MutableList<Song>>()
            val source = mutableMapOf<String, MutableList<Song>>()
            val signature = mutableMapOf<String, MutableList<Song>>()
            val portable = mutableMapOf<String, MutableList<Song>>()
            val legacy = mutableMapOf<String, MutableList<Song>>()

            songs.forEach { song ->
                val identity = song.songIdentity()
                song.localLookupKeys().forEach { key -> local.addCandidate(key, song) }
                song.sourceLookupKeys().forEach { key -> source.addCandidate(key, song) }
                song.signatureLookupKey()?.let { key -> signature.addCandidate(key, song) }
                identity.portableKey?.let { key -> portable.addCandidate(key, song) }
                legacy.addCandidate(identity.legacyKey, song)
            }

            return SongReferenceIndex(
                localCandidates = local.freezeCandidates(),
                sourceCandidates = source.freezeCandidates(),
                signatureCandidates = signature.freezeCandidates(),
                portableCandidates = portable.freezeCandidates(),
                legacyCandidates = legacy.freezeCandidates()
            )
        }
    }
}

/** Resolves the first matching confidence tier, but never chooses among equal-tier candidates. */
object SongReferenceResolver {
    fun resolve(reference: SongReference, index: SongReferenceIndex): SongReferenceResolution =
        index.resolve(reference)

    /** Compatibility path for isolated callers; repeated resolution should reuse an index. */
    fun resolve(
        reference: SongReference,
        songs: Collection<Song>
    ): SongReferenceResolution = SongReferenceIndex.build(songs).resolve(reference)
}

private fun Song.localLookupKeys(): List<String> = buildList(2) {
    if (id > 0L && volumeName.isNotBlank()) {
        add(localVolumeIdKey(volumeName, id))
    }
    uri.toString().takeIf { it.isNotBlank() }?.let { add("uri:$it") }
}

private fun SongReference.localLookupKeys(): List<String> = buildList(2) {
    if (mediaStoreId != null && volumeName.isNotBlank()) {
        add(localVolumeIdKey(volumeName, mediaStoreId))
    }
    contentUri.takeIf { it.isNotBlank() }?.let { add("uri:$it") }
}

private fun localVolumeIdKey(volumeName: String, mediaStoreId: Long): String =
    "volume:${volumeName.identityNormalized()}|id:$mediaStoreId"

private fun Song.sourceLookupKeys(): List<String> {
    if (relativePath.isBlank() || displayName.isBlank()) return emptyList()
    val pathAndName = normalizedPathAndName(relativePath, displayName)
    return listOf(
        "source:any|$pathAndName",
        "source:${volumeName.identityNormalized()}|$pathAndName"
    ).distinct()
}

private fun SongReference.sourceLookupKey(): String? {
    if (relativePath.isBlank() || displayName.isBlank()) return null
    val volume = if (volumeName.isBlank()) "any" else volumeName.identityNormalized()
    return "source:$volume|${normalizedPathAndName(relativePath, displayName)}"
}

private fun normalizedPathAndName(relativePath: String, displayName: String): String =
    "${relativePath.identityPathNormalized()}|${displayName.identityNormalized()}"

private fun Song.signatureLookupKey(): String? {
    if (displayName.isBlank() || fileSizeBytes <= 0L || duration <= 0L) return null
    return signatureLookupKey(displayName, fileSizeBytes)
}

private fun SongReference.signatureLookupKey(): String? {
    if (displayName.isBlank() || fileSizeBytes <= 0L || duration <= 0L) return null
    return signatureLookupKey(displayName, fileSizeBytes)
}

private fun signatureLookupKey(displayName: String, fileSizeBytes: Long): String =
    "${displayName.identityNormalized()}|$fileSizeBytes"

private fun SongReference.effectivePortableKey(): String? {
    if (portableKeyVersion != SongIdentity.PORTABLE_KEY_VERSION) return null
    return portableKey.takeIf { it.isNotBlank() }
        ?: portableMetadataKey(title, artist, album, duration)
}

private fun durationsMatch(first: Long, second: Long): Boolean =
    kotlin.math.abs(first - second) <= DURATION_TOLERANCE_MILLIS

private fun MutableMap<String, MutableList<Song>>.addCandidate(key: String, song: Song) {
    getOrPut(key) { mutableListOf() }.add(song)
}

private fun Map<String, MutableList<Song>>.freezeCandidates(): Map<String, List<Song>> =
    mapValues { (_, candidates) -> candidates.sortedWith(songResolutionOrder) }

private val songResolutionOrder = compareBy<Song>(
    { it.volumeName.identityNormalized() },
    { it.relativePath.identityPathNormalized() },
    { it.displayName.identityNormalized() },
    { it.id }
)

private const val DURATION_TOLERANCE_MILLIS = 2_000L
