package io.github.rsgarrido.sazanami.lyrics

import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class LyricsRoot(
    val uri: String,
    val displayName: String,
    val volumeId: String? = null
)

@Serializable
data class IndexedLyricsFile(
    val documentUri: String,
    val rootUri: String,
    val displayName: String,
    val normalizedStem: String,
    val relativeDirectory: String,
    val rootVolumeId: String? = null,
    val sizeBytes: Long? = null,
    val lastModifiedEpochMs: Long? = null
)

@Serializable
data class LyricsIndexSnapshot(
    val files: List<IndexedLyricsFile>,
    val indexedRootUris: Set<String>,
    val issues: List<LyricsRootIssue> = emptyList(),
    val generatedAtEpochMs: Long
)

@Serializable
data class LyricsRootIssue(
    val rootUri: String,
    val kind: Kind
) {
    enum class Kind {
        PERMISSION_LOST,
        SCAN_FAILED
    }
}

sealed interface LyricsTreeScanResult {
    data class Success(val files: List<IndexedLyricsFile>) : LyricsTreeScanResult
    data class PermissionLost(val rootUri: String) : LyricsTreeScanResult
    data class Failed(val rootUri: String) : LyricsTreeScanResult
}

data class LyricsIndexResult(
    val snapshot: LyricsIndexSnapshot
)

data class LyricsIndexSummary(
    val fileCount: Int,
    val indexedRootUris: Set<String>
)

data class SongLyricsIdentity(
    val audioFileName: String,
    val title: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val relativeDirectory: String,
    val fallbackDirectory: String,
    val volumeId: String?
)

enum class LyricsNameCandidateSource {
    AUDIO_STEM,
    TITLE,
    TRACK_NUMBER_STRIPPED_AUDIO_STEM,
    ARTIST_TITLE,
    TITLE_ARTIST,
    ALBUM_ARTIST_TITLE,
    TITLE_ALBUM_ARTIST
}

data class LyricsNameCandidate(
    val displayStem: String,
    val normalizedStem: String,
    val source: LyricsNameCandidateSource,
    val priority: Int
)

data class LyricsCandidate(
    val documentUri: String,
    val rootUri: String,
    val displayName: String,
    val relativeDirectory: String
)

sealed interface LyricsMatchResult {
    data class Match(val file: IndexedLyricsFile) : LyricsMatchResult
    data class Ambiguous(val candidates: List<IndexedLyricsFile>) : LyricsMatchResult
    data object NotFound : LyricsMatchResult
}

sealed interface LyricsLookupResult {
    data class Found(val lyrics: SourcedLyrics) : LyricsLookupResult
    data object NoRootsConfigured : LyricsLookupResult
    data object NotFound : LyricsLookupResult
    data class Ambiguous(val candidates: List<LyricsCandidate>) : LyricsLookupResult
    data class PermissionLost(val rootUri: String) : LyricsLookupResult
    data class RootScanError(val rootUri: String) : LyricsLookupResult
    data class StaleFile(val documentUri: String) : LyricsLookupResult
    data class ReadError(val documentUri: String) : LyricsLookupResult
    data class InvalidLyrics(val documentUri: String) : LyricsLookupResult
}

sealed interface LyricsDocumentReadResult {
    data class Success(val bytes: ByteArray) : LyricsDocumentReadResult
    data object Missing : LyricsDocumentReadResult
    data object PermissionLost : LyricsDocumentReadResult
    data object Failed : LyricsDocumentReadResult
}

interface LyricsTreeDataSource {
    suspend fun scanRoot(root: LyricsRoot): LyricsTreeScanResult
}

interface LyricsRootStore {
    val roots: StateFlow<List<LyricsRoot>>
    suspend fun addRoot(root: LyricsRoot)
    suspend fun removeRoot(rootUri: String)
}

interface LyricsIndexStore {
    suspend fun load(): LyricsIndexSnapshot?
    suspend fun save(snapshot: LyricsIndexSnapshot)
    suspend fun clear()
}

interface LyricsDocumentReader {
    suspend fun read(documentUri: String): LyricsDocumentReadResult
}

interface LocalLyricsRepository {
    val roots: StateFlow<List<LyricsRoot>>
    suspend fun loadCachedIndexSummary(): LyricsIndexSummary?
    suspend fun addRoot(root: LyricsRoot): LyricsIndexResult
    suspend fun removeRoot(rootUri: String): LyricsIndexResult
    suspend fun refreshIndex(): LyricsIndexResult
    suspend fun findLyrics(song: SongLyricsIdentity): LyricsLookupResult
}

internal fun IndexedLyricsFile.toCandidate() = LyricsCandidate(
    documentUri = documentUri,
    rootUri = rootUri,
    displayName = displayName,
    relativeDirectory = relativeDirectory
)

internal fun normalizeVolumeId(value: String?): String? {
    val normalized = value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.lowercase(Locale.ROOT)
        ?: return null
    return when (normalized) {
        "external_primary", "primary" -> "primary"
        else -> normalized
    }
}
