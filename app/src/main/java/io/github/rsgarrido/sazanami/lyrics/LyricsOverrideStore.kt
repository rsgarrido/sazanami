package io.github.rsgarrido.sazanami.lyrics

import androidx.room.withTransaction
import io.github.rsgarrido.sazanami.data.*
import io.github.rsgarrido.sazanami.data.local.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun interface LyricsOverrideReader {
    suspend fun find(song: SongLyricsIdentity): LyricsLookupResult?
}

@Serializable
data class LyricsDraftState(
    val epoch: String,
    val originId: String,
    val originRevision: String,
    val overrideId: String? = null,
    val overrideRevision: String? = null,
    val baselineChecksum: String,
    val originalOffsetMs: Long = 0L,
    val mode: LyricsEditorMode = LyricsEditorMode.RAW,
    val selectedLine: Int = 0,
    val rowIds: List<String> = emptyList(),
    val pendingRowId: String? = null,
    val pendingText: String = "",
    val pendingTimes: String = ""
)

sealed interface LyricsCommitResult {
    data class Saved(val row: LyricsOverrideEntity) : LyricsCommitResult
    data class Failed(val reason: LyricsEditError) : LyricsCommitResult
}

interface LyricsEditorPersistence {
    val epochs: Flow<String?> get() = emptyFlow()
    suspend fun epoch(): String
    suspend fun associated(song: SongLyricsIdentity): List<LyricsOverrideEntity>
    suspend fun commit(target: SongLyricsIdentity, source: LyricsSourceDocument, baseline: LyricsDraftState): LyricsCommitResult
    suspend fun remove(target: SongLyricsIdentity, baseline: LyricsDraftState): LyricsEditError?
    suspend fun loadDraft(target: SongLyricsIdentity): LyricsDraftEntity?
    suspend fun listDrafts(): List<LyricsDraftEntity>
    suspend fun checkpoint(target: SongLyricsIdentity, bytes: ByteArray, state: LyricsDraftState)
    suspend fun discardDraft(key: String)
}

/** Reuses the app resolver, but user content never binds on a title/artist-only fallback. */
internal fun resolveLyricsReference(reference: SongReference, index: SongReferenceIndex): SongReferenceResolution {
    var result = index.resolve(reference)
    if (result !is SongReferenceResolution.Resolved) return result
    val song = result.song
    if (result.matchType in setOf(SongReferenceMatchType.PORTABLE, SongReferenceMatchType.LEGACY)) return SongReferenceResolution.NotFound
    if (result.matchType == SongReferenceMatchType.LOCAL &&
        (reference.displayName.isBlank() || !reference.displayName.equals(song.displayName, true) ||
            !reference.relativePath.equals(song.relativePath, true))) {
        // MediaStore IDs can be reused after a rescan. Discard contradicted local evidence
        // and retry the established resolver with file evidence, never a metadata-only tier.
        result = index.resolve(reference.copy(mediaStoreId = null, contentUri = ""))
        if (result is SongReferenceResolution.Resolved &&
            result.matchType in setOf(SongReferenceMatchType.PORTABLE, SongReferenceMatchType.LEGACY)) return SongReferenceResolution.NotFound
    }
    return result
}

class LyricsOverrideStore(private val database: AppDatabase) : LyricsOverrideReader, LyricsEditorPersistence {
    private val dao get() = database.lyricsDao()
    private val parser = LrcParser()
    private val editor = LyricsDocumentEditor(parser)
    @Volatile private var catalog = SongReferenceIndex.EMPTY

    suspend fun updateLibrary(songs: List<Song>) = withContext(Dispatchers.IO) {
        val nextCatalog = SongReferenceIndex.build(songs)
        database.withTransaction {
            val rows = dao.bindings()
            val resolutions = rows.associate { row -> row.id to runCatching {
                resolveLyricsReference(decodeReference(row.referenceJson), nextCatalog)
            }.getOrNull() }
            val keys = resolutions.mapValues { (_, resolution) ->
                (resolution as? SongReferenceResolution.Resolved)?.song?.toLyricsIdentity()?.trackKey
            }
            val duplicateKeys = keys.values.filterNotNull().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            rows.forEach { row ->
                val resolution = resolutions.getValue(row.id)
                val key = keys[row.id]
                val status = when {
                    resolution == null -> "INVALID_REFERENCE"
                    (key != null && key in duplicateKeys) || resolution is SongReferenceResolution.Ambiguous -> "AMBIGUOUS"
                    key == null -> "MISSING"
                    else -> "BOUND"
                }
                val reference = (resolution as? SongReferenceResolution.Resolved)?.song?.toSongReference()
                val next = row.copy(boundTrackKey = key?.takeUnless { it in duplicateKeys }, reconciliation = status,
                    referenceJson = if (status == "BOUND" && reference != null) encodeReference(reference) else row.referenceJson)
                if (next.boundTrackKey != row.boundTrackKey || next.reconciliation != row.reconciliation || next.referenceJson != row.referenceJson)
                    dao.bind(row.id, next.referenceJson, next.boundTrackKey, next.reconciliation)
            }
            catalog = nextCatalog
        }
    }

    override suspend fun epoch(): String = withContext(Dispatchers.IO) { dao.epoch() ?: "initial" }
    override val epochs: Flow<String?> get() = dao.observeEpoch()

    override suspend fun associated(song: SongLyricsIdentity): List<LyricsOverrideEntity> = withContext(Dispatchers.IO) {
        matchingRows(song, catalog)
    }
    private suspend fun matchingRows(song: SongLyricsIdentity, index: SongReferenceIndex): List<LyricsOverrideEntity> {
        // Resolve against the current complete library, never a single-song candidate list.
        return dao.bindingsFor(song.trackKey).filter { row ->
            val result = runCatching { resolveLyricsReference(decodeReference(row.referenceJson), index) }.getOrNull()
            (result as? SongReferenceResolution.Resolved)?.song?.toLyricsIdentity()?.trackKey == song.trackKey
        }.mapNotNull { dao.overrideById(it.id) }
    }

    override suspend fun find(song: SongLyricsIdentity): LyricsLookupResult? = withContext(Dispatchers.IO) {
        try {
            val rows = associated(song)
            if (rows.isEmpty()) return@withContext null
            if (rows.size != 1) return@withContext LyricsLookupResult.Unavailable(listOf(
                LyricsSourceIssue(song.trackKey, LyricsSourceIssue.Kind.AMBIGUOUS)))
            val row = rows.single()
            val source = editor.parse(row.sourceBytes)
            editor.validate(source)
            check(source.revision == row.checksum)
            LyricsLookupResult.Found(SourcedLyrics(parser.project(source), LyricsSource.LocalOverride(row.id), source, row.revision))
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { LyricsLookupResult.Unavailable(listOf(LyricsSourceIssue(song.trackKey, LyricsSourceIssue.Kind.READ_FAILURE))) }
    }

    override suspend fun commit(target: SongLyricsIdentity, source: LyricsSourceDocument, baseline: LyricsDraftState): LyricsCommitResult = withContext(Dispatchers.IO) {
        try {
            editor.validate(source)
            val reference = target.reference ?: throw LyricsEditException(LyricsEditError.TARGET_MISSING)
            val index = catalog
            val resolution = resolveLyricsReference(reference, index)
            if (resolution is SongReferenceResolution.Ambiguous) throw LyricsEditException(LyricsEditError.TARGET_AMBIGUOUS)
            val resolved = (resolution as? SongReferenceResolution.Resolved)?.song ?: throw LyricsEditException(LyricsEditError.TARGET_MISSING)
            if (resolved.toLyricsIdentity().trackKey != target.trackKey) throw LyricsEditException(LyricsEditError.TARGET_MISSING)
            database.withTransaction {
                if (index !== catalog || (dao.epoch() ?: "initial") != baseline.epoch) throw LyricsEditException(LyricsEditError.RESTORE_CONFLICT)
                val existing = matchingRows(target, index)
                val previous = LyricsCommitPolicy.previous(existing, baseline)
                val now = System.currentTimeMillis()
                val row = LyricsOverrideEntity(previous?.id ?: UUID.randomUUID().toString(), encodeReference(resolved.toSongReference()),
                    target.trackKey, source.serializeUnchanged(), UUID.randomUUID().toString(), source.revision,
                    SourcedLyrics(parser.project(source), LyricsSource.LocalOverride("")).timing.name,
                    baseline.originId, baseline.originRevision, previous?.createdAt ?: now, now, "BOUND")
                val budget = dao.overrideBudget(row.id)
                LyricsCommitPolicy.checkBudget(budget.count, budget.bytes, row.sourceBytes.size)
                dao.putOverride(row)
                dao.deleteDraft(target.trackKey)
                LyricsCommitResult.Saved(row)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: LyricsEditException) { LyricsCommitResult.Failed(failure.reason) }
          catch (_: Exception) { LyricsCommitResult.Failed(LyricsEditError.STORAGE) }
    }

    override suspend fun remove(target: SongLyricsIdentity, baseline: LyricsDraftState): LyricsEditError? = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                if ((dao.epoch() ?: "initial") != baseline.epoch) throw LyricsEditException(LyricsEditError.RESTORE_CONFLICT)
                val rows = matchingRows(target, catalog)
                if (rows.size > 1) throw LyricsEditException(LyricsEditError.TARGET_AMBIGUOUS)
                val row = rows.singleOrNull() ?: throw LyricsEditException(LyricsEditError.REVISION_CONFLICT)
                if (row.id != baseline.overrideId || row.revision != baseline.overrideRevision) throw LyricsEditException(LyricsEditError.REVISION_CONFLICT)
                dao.deleteOverride(row.id)
                dao.deleteDraft(target.trackKey)
            }
            null
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: LyricsEditException) { failure.reason }
          catch (_: Exception) { LyricsEditError.STORAGE }
    }

    override suspend fun loadDraft(target: SongLyricsIdentity): LyricsDraftEntity? = withContext(Dispatchers.IO) {
        val exact = dao.draft(target.trackKey)
        if (exact != null) {
            if (sameTarget(exact, target)) return@withContext exact
            val result = resolveLyricsReference(decodeReference(exact.referenceJson), catalog)
            throw LyricsEditException(if (result is SongReferenceResolution.Ambiguous) LyricsEditError.TARGET_AMBIGUOUS else LyricsEditError.TARGET_MISSING)
        }
        val matching = dao.drafts().filter { sameTarget(it, target) }
        if (matching.size > 1) throw LyricsEditException(LyricsEditError.TARGET_AMBIGUOUS)
        matching.singleOrNull()
    }
    override suspend fun listDrafts(): List<LyricsDraftEntity> = withContext(Dispatchers.IO) { dao.drafts() }
    private fun sameTarget(draft: LyricsDraftEntity, target: SongLyricsIdentity): Boolean =
        runCatching { (resolveLyricsReference(decodeReference(draft.referenceJson), catalog) as? SongReferenceResolution.Resolved)
            ?.song?.toLyricsIdentity()?.trackKey == target.trackKey }.getOrDefault(false)

    override suspend fun checkpoint(target: SongLyricsIdentity, bytes: ByteArray, state: LyricsDraftState) = withContext(Dispatchers.IO) {
        if (bytes.size > LyricsEditingLimits.DOCUMENT_BYTES) throw LyricsEditException(LyricsEditError.TOO_LARGE)
        val json = Json.encodeToString(state)
        val stateBytes = json.toByteArray(Charsets.UTF_8).size
        if (stateBytes > 1024 * 1024) throw LyricsEditException(LyricsEditError.TOO_LARGE)
        database.withTransaction {
            val remaining = dao.drafts().filterNot { it.trackKey == target.trackKey }
            // Never silently evict recoverable work. The UI reports a storage-limit error.
            if (remaining.size >= LyricsEditingLimits.DRAFTS || remaining.sumOf { it.sourceBytes.size.toLong() + it.stateJson.toByteArray(Charsets.UTF_8).size } + bytes.size + stateBytes > LyricsEditingLimits.TOTAL_DRAFT_BYTES)
                throw LyricsEditException(LyricsEditError.TOO_LARGE)
            dao.putDraft(LyricsDraftEntity(target.trackKey, encodeReference(requireNotNull(target.reference)), bytes, json, System.currentTimeMillis()))
        }
    }
    override suspend fun discardDraft(key: String) = withContext(Dispatchers.IO) { dao.deleteDraft(key) }

    companion object {
        internal fun encodeReference(reference: SongReference): String {
            // Descriptive snapshots may be bounded without weakening file identity. Never
            // truncate identity evidence into a different filename, path or URI.
            if (listOf(reference.displayName, reference.relativePath, reference.volumeName, reference.portableKey).any { it.length > 4096 } ||
                reference.contentUri.length > 16_384) throw LyricsEditException(LyricsEditError.TOO_LARGE)
            return Json.encodeToString(reference.copy(title = reference.title.take(4096), artist = reference.artist.take(4096),
                album = reference.album.take(4096), albumArtist = reference.albumArtist.take(4096), legacyStableKey = reference.legacyStableKey.take(4096)))
        }
        internal fun decodeReference(value: String): SongReference = Json.decodeFromString(value)
    }
}
