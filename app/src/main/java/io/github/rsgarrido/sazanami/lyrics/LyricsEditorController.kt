package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.LyricsDraftEntity
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

enum class LyricsEditorConfirmation { LEAVE, IMPORT, REMOVE, WORD_TIMINGS }
data class LyricsDraftSummary(val key: String, val title: String?, val updatedAt: Long)

data class LyricsEditorUiState(
    val sessionId: String = UUID.randomUUID().toString(),
    val song: Song? = null,
    val target: SongLyricsIdentity? = null,
    val source: LyricsSourceDocument? = null,
    val text: String = "",
    val baseline: LyricsDraftState? = null,
    val mode: LyricsEditorMode = LyricsEditorMode.RAW,
    val selectedLine: Int = 0,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val parsing: Boolean = false,
    val error: LyricsEditError? = null,
    val confirmation: LyricsEditorConfirmation? = null,
    val recovery: LyricsDraftEntity? = null,
    val rowId: String? = null,
    val rowText: String = "",
    val rowTimes: String = "",
    val activeTrackKey: String? = null,
    val previewPosition: Long? = null,
    val exported: Boolean = false,
    val saved: Boolean = false,
    val removeAfterDirty: Boolean = false,
    val exportedName: String? = null,
    val otherDrafts: List<LyricsDraftSummary>? = null,
    val discardDraft: LyricsDraftSummary? = null,
    val rowCloseConfirmation: Boolean = false,
    val canUndo: Boolean = false
) {
    val visible get() = song != null
    val canStamp get() = !busy && !parsing && rowId == null && target?.trackKey == activeTrackKey && source != null
    val hasOverride get() = baseline?.overrideId != null
}

/** Owned by MusicViewModel; transient player overlays never own authoritative editor content. */
class LyricsEditorController(
    private val storage: LyricsEditorPersistence,
    private val repository: LocalLyricsRepository,
    private val position: LyricsPositionCapture,
    private val scope: CoroutineScope,
    playback: StateFlow<PlaybackUiState>,
    val playPause: () -> Unit,
    private val seek: (Int) -> Unit,
    private val worker: CoroutineDispatcher = Dispatchers.Default
) {
    private val parser = LrcParser()
    private val editor = LyricsDocumentEditor(parser)
    private val mutable = MutableStateFlow(LyricsEditorUiState())
    val state: StateFlow<LyricsEditorUiState> = mutable
    private var parseJob: Job? = null
    private var checkpointJob: Job? = null
    private val checkpointMutex = Mutex()
    private data class UndoEntry(val source: LyricsSourceDocument, val text: String, val selected: Int,
        val mode: LyricsEditorMode, val rowId: String?, val rowText: String, val rowTimes: String) {
        val weight = source.byteSize * 8L + (text.length + rowText.length + rowTimes.length) * 2L + source.lines.size * 256L +
            source.lines.sumOf { it.segments.size * 128L }
    }
    private val undo = ArrayDeque<UndoEntry>()
    private var pendingImport: ByteArray? = null
    private var preparedExport: ByteArray? = null
    private var version = 0L

    init {
        scope.launch {
            playback.collect { mutable.value = mutable.value.copy(activeTrackKey = it.currentSong?.toLyricsIdentity()?.trackKey) }
        }
        scope.launch {
            try {
                storage.epochs.collect {
                    val generation = storage.epoch()
                    val baseline = state.value.baseline
                    if (state.value.visible && baseline != null && baseline.epoch != generation) onRestore()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { if (state.value.visible) report(LyricsEditError.STORAGE) }
        }
    }

    fun open(song: Song, initial: SourcedLyrics? = null) {
        if (state.value.visible) return
        val target = song.toLyricsIdentity()
        val session = LyricsEditorUiState(song = song, target = target, busy = true, activeTrackKey = state.value.activeTrackKey)
        mutable.value = session
        scope.launch {
            try {
                val result = repository.findLyrics(target)
                val loaded = (result as? LyricsLookupResult.Found)?.lyrics ?: initial
                val source = withContext(worker) { loaded?.sourceDocument ?: parser.parseSource("") }
                val rows = storage.associated(target)
                val baseline = LyricsDraftState(storage.epoch(), loaded?.source?.stableId.orEmpty(), loaded?.revision.orEmpty(),
                    rows.singleOrNull()?.id, rows.singleOrNull()?.revision, source.revision, originalOffsetMs = source.metadata.offsetMs)
                val recovery = storage.loadDraft(target)
                if (state.value.sessionId != session.sessionId) return@launch
                mutable.value = state.value.copy(source = source, text = source.originalText, baseline = baseline,
                    mode = modeFor(source),
                    selectedLine = source.lines.indexOfFirst { editor.editable(it) && it.displayedText.isNotBlank() }.coerceAtLeast(0),
                    busy = false, recovery = recovery,
                    error = if (rows.size > 1) LyricsEditError.TARGET_AMBIGUOUS else null)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) {
                if (state.value.sessionId == session.sessionId) mutable.value = state.value.copy(busy = false, error = reason(failure))
            }
        }
    }

    fun recoverDraft() = operation {
        val draft = state.value.recovery ?: return@operation
        val (saved, source) = withContext(worker) {
            val saved = Json.decodeFromString<LyricsDraftState>(draft.stateJson)
            var parsed = parser.parseSource(draft.sourceBytes)
            if (saved.rowIds.size == parsed.lines.size && saved.rowIds.distinct().size == saved.rowIds.size)
                parsed = parser.reparseLines(parsed, parsed.lines.mapIndexed { index, row -> row.copy(id = saved.rowIds[index]) })
            saved to parsed
        }
        val epoch = storage.epoch()
        val sourceError = when {
            source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_ENCODING } -> LyricsEditError.ENCODING
            source.originalText.any { it.code < 32 && it !in "\t\r\n" } -> LyricsEditError.IMPORT_FORMAT
            else -> null
        }
        mutable.value = state.value.copy(source = source, text = source.originalText, baseline = saved,
            mode = if (sourceError != null) LyricsEditorMode.RAW else saved.mode, selectedLine = saved.selectedLine.coerceIn(source.lines.indices),
            dirty = source.revision != saved.baselineChecksum || saved.pendingRowId != null,
            recovery = null, rowId = saved.pendingRowId, rowText = saved.pendingText, rowTimes = saved.pendingTimes,
            error = if (saved.epoch != epoch) LyricsEditError.RESTORE_CONFLICT else sourceError)
        // A rescan can change the qualified key. Migrate the recovered draft without deleting it first.
        checkpointNow()
        if (draft.trackKey != state.value.target?.trackKey) storage.discardDraft(draft.trackKey)
    }

    fun discardRecovery() = operation {
        state.value.recovery?.let { storage.discardDraft(it.trackKey) }
        mutable.value = state.value.copy(recovery = null)
    }

    fun manageDrafts() = operation {
        val current = state.value.target?.trackKey?.takeIf { state.value.baseline != null }
        val summaries = storage.listDrafts().filterNot {
            it.trackKey == current || it.trackKey == state.value.recovery?.trackKey
        }.map { draft ->
            val ref = runCatching { LyricsOverrideStore.decodeReference(draft.referenceJson) }.getOrNull()
            val title = ref?.let { reference -> reference.title.ifBlank { reference.displayName } }?.takeIf { it.isNotBlank() }
            LyricsDraftSummary(draft.trackKey, title, draft.updatedAt)
        }
        mutable.value = state.value.copy(otherDrafts = summaries)
    }
    fun closeDraftManager() { mutable.value = state.value.copy(otherDrafts = null, discardDraft = null) }
    fun requestDiscardDraft(draft: LyricsDraftSummary) { mutable.value = state.value.copy(discardDraft = draft) }
    fun cancelDiscardDraft() { mutable.value = state.value.copy(discardDraft = null) }
    fun discardOtherDraft() = operation {
        val draft = state.value.discardDraft ?: return@operation
        if (draft.key == state.value.target?.trackKey && state.value.baseline != null) return@operation
        storage.discardDraft(draft.key)
        mutable.value = state.value.copy(discardDraft = null, otherDrafts = state.value.otherDrafts?.filterNot { it.key == draft.key })
    }

    fun setMode(mode: LyricsEditorMode) {
        if (state.value.busy || state.value.parsing || state.value.rowId != null) return
        val source = state.value.source ?: return
        if (state.value.text != source.originalText) { report(state.value.error ?: LyricsEditError.IMPORT_FORMAT); return }
        if (mode != LyricsEditorMode.RAW && source.originalText.any { it.code < 32 && it !in "\t\r\n" }) {
            report(LyricsEditError.IMPORT_FORMAT); return
        }
        if (mode != LyricsEditorMode.RAW && source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_ENCODING }) {
            report(LyricsEditError.ENCODING); return
        }
        if (mode == LyricsEditorMode.PLAIN && (source.lines.any { it.kind != LyricsSourceLineKind.TEXT } || source.metadata.offsetMs != 0L)) {
            mutable.value = state.value.copy(error = LyricsEditError.RAW_REQUIRED)
            return
        }
        mutable.value = state.value.copy(mode = mode, error = null)
        scheduleCheckpoint()
    }

    fun setText(text: String) {
        val snapshot = state.value
        if (snapshot.busy || snapshot.source == null) return
        if (text == snapshot.text) return
        if (text.length > LyricsEditingLimits.DOCUMENT_BYTES) { report(LyricsEditError.TOO_LARGE); return }
        // A later raw/plain edit must never be discarded by undoing an older stamp/import.
        undo.clear()
        parseJob?.cancel()
        val generation = ++version
        mutable.value = snapshot.copy(text = text, dirty = true, parsing = true, error = null, exported = false, canUndo = false)
        scheduleCheckpoint()
        parseJob = scope.launch {
            try {
                val source = withContext(worker) {
                    val context = currentCoroutineContext()
                    editor.raw(snapshot.source, text) { context.ensureActive() }
                }
                ensureActive()
                if (version != generation) return@launch
                mutable.value = state.value.copy(source = source, parsing = false,
                    dirty = source.revision != state.value.baseline?.baselineChecksum)
                scheduleCheckpoint()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { if (version == generation) mutable.value = state.value.copy(parsing = false, error = reason(failure)) }
        }
    }

    fun select(index: Int) {
        val source = state.value.source ?: return
        mutable.value = state.value.copy(selectedLine = index.coerceIn(source.lines.indices))
        scheduleCheckpoint()
    }

    fun editRow(id: String) {
        if (state.value.busy || state.value.parsing) return
        val row = state.value.source?.lines?.singleOrNull { it.id == id } ?: return
        if (!editor.editable(row)) { report(LyricsEditError.RAW_REQUIRED); return }
        mutable.value = state.value.copy(rowId = id, rowText = row.displayedText,
            selectedLine = state.value.source!!.lines.indexOfFirst { it.id == id },
            rowTimes = row.timestamps.joinToString(", ") { LyricsDocumentEditor.formatTime(it.timestampMs) }, error = null)
    }
    fun setRowText(text: String) {
        if (state.value.busy) return
        if (text.length > LyricsLimits.LINE_CHARACTERS) { report(LyricsEditError.TOO_LARGE); return }
        mutable.value = state.value.copy(rowText = text, exported = false)
        scheduleCheckpoint()
    }
    fun setRowTimes(text: String) {
        if (state.value.busy) return
        if (text.length > LyricsLimits.LINE_CHARACTERS) { report(LyricsEditError.TOO_LARGE); return }
        mutable.value = state.value.copy(rowTimes = text, exported = false)
        scheduleCheckpoint()
    }
    fun cancelRow() { mutable.value = state.value.copy(rowId = null, confirmation = null, rowCloseConfirmation = false); scheduleCheckpoint() }
    fun requestRowClose() {
        if (state.value.busy) return
        val row = state.value.source?.lines?.firstOrNull { it.id == state.value.rowId } ?: return
        val originalTimes = row.timestamps.joinToString(", ") { LyricsDocumentEditor.formatTime(it.timestampMs) }
        if (state.value.rowText == row.displayedText && state.value.rowTimes == originalTimes) cancelRow()
        else mutable.value = state.value.copy(rowCloseConfirmation = true)
    }
    fun keepRowEditing() { mutable.value = state.value.copy(rowCloseConfirmation = false) }
    fun applyRow(removeWordTimings: Boolean = false) {
        val snapshot = state.value
        mutate(closeRow = true) { source -> editor.changeRow(source, requireNotNull(snapshot.rowId), snapshot.rowText, snapshot.rowTimes, removeWordTimings) }
    }
    fun insert() = mutate { source -> editor.insert(source, source.lines.getOrNull(state.value.selectedLine)?.id) }
    fun removeLine(id: String) = mutate { editor.remove(it, id) }
    fun moveLine(id: String, delta: Int) = mutate { editor.move(it, id, delta) }
    fun setOffset(value: Long) = mutate { editor.offset(it, value) }
    fun shiftOffset(delta: Long) {
        try { setOffset(Math.addExact(state.value.source?.metadata?.offsetMs ?: 0L, delta)) }
        catch (_: ArithmeticException) { report(LyricsEditError.INVALID_OFFSET) }
    }

    fun stamp() {
        val snapshot = state.value
        if (!snapshot.canStamp) { report(LyricsEditError.TRACK_MISMATCH); return }
        try {
            val target = requireNotNull(snapshot.target)
            // Capture before launching/parsing so coroutine scheduling cannot move the timestamp.
            val captured = position.capture(target).positionFor(target.trackKey)
            val row = snapshot.source?.lines?.getOrNull(snapshot.selectedLine) ?: return
            mutate(advanceLine = true) { source -> editor.stamp(source, row.id, captured) }
        } catch (failure: LyricsEditException) { report(failure.reason) }
          catch (_: Exception) { report(LyricsEditError.POSITION_UNAVAILABLE) }
    }
    fun skip() { val source = state.value.source ?: return; select(nextEditable(source, state.value.selectedLine)) }
    private fun nextEditable(source: LyricsSourceDocument, after: Int): Int =
        source.lines.indices.firstOrNull { it > after && editor.editable(source.lines[it]) && source.lines[it].displayedText.isNotBlank() } ?: after

    fun undo() = operation {
        val previous = undo.removeLastOrNull() ?: return@operation
        publishDocument(previous.source, previous.selected)
        mutable.value = state.value.copy(text = previous.text, mode = previous.mode, rowId = previous.rowId,
            canUndo = undo.isNotEmpty(),
            rowText = previous.rowText, rowTimes = previous.rowTimes,
            dirty = previous.text != previous.source.originalText || previous.source.revision != state.value.baseline?.baselineChecksum,
            error = if (previous.text != previous.source.originalText) LyricsEditError.IMPORT_FORMAT else null)
        checkpointNow()
    }

    fun replaySelected() {
        val snapshot = state.value
        try {
            val target = requireNotNull(snapshot.target)
            val source = snapshot.source ?: return
            position.capture(target).positionFor(target.trackKey)
            val time = source.lines.getOrNull(snapshot.selectedLine)?.timestamps?.firstOrNull()?.timestampMs ?: return
            seek(effectiveLyricTime(time, source.metadata.offsetMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        } catch (failure: LyricsEditException) { report(failure.reason) }
          catch (_: Exception) { report(LyricsEditError.POSITION_UNAVAILABLE) }
    }
    fun preview() {
        val target = state.value.target ?: return
        try { mutable.value = state.value.copy(previewPosition = position.capture(target).positionFor(target.trackKey), error = null) }
        catch (failure: LyricsEditException) { report(failure.reason) }
        catch (_: Exception) { report(LyricsEditError.POSITION_UNAVAILABLE) }
    }

    fun requestClose() {
        if (state.value.busy) return
        // Dismissing a recovery offer or a failed open is not consent to discard its draft.
        if (state.value.recovery != null || state.value.source == null) { close(); return }
        if (state.value.dirty || state.value.rowId != null || state.value.parsing) mutable.value = state.value.copy(confirmation = LyricsEditorConfirmation.LEAVE)
        else discardAndClose()
    }
    fun dismissConfirmation() { pendingImport = null; mutable.value = state.value.copy(confirmation = null, removeAfterDirty = false) }
    fun requestRemove() {
        if (!state.value.hasOverride) return
        val dirty = state.value.dirty || state.value.rowId != null
        mutable.value = state.value.copy(confirmation = if (dirty) LyricsEditorConfirmation.LEAVE else LyricsEditorConfirmation.REMOVE,
            removeAfterDirty = dirty)
    }
    fun discardChangesChoice() {
        if (!state.value.removeAfterDirty) { discardAndClose(); return }
        operation {
            val target = requireNotNull(state.value.target)
            val source = (repository.findLyrics(target) as? LyricsLookupResult.Found)?.lyrics?.sourceDocument
                ?: throw LyricsEditException(LyricsEditError.REVISION_CONFLICT)
            checkpointJob?.cancelAndJoin()
            checkpointMutex.withLock { storage.discardDraft(target.trackKey) }
            mutable.value = state.value.copy(source = source, text = source.originalText, dirty = false, rowId = null,
                removeAfterDirty = false, confirmation = LyricsEditorConfirmation.REMOVE,
                baseline = state.value.baseline?.copy(baselineChecksum = source.revision))
        }
    }

    fun discardAndClose() {
        if (state.value.parsing) { ++version; parseJob?.cancel(); mutable.value = state.value.copy(parsing = false) }
        operation {
        checkpointJob?.cancelAndJoin()
        checkpointMutex.withLock { state.value.target?.let { storage.discardDraft(it.trackKey) } }
        close()
        }
    }
    fun keepDraftAndClose() = operation { checkpointNow(); close() }
    private fun close() {
        ++version
        parseJob?.cancel()
        checkpointJob?.cancel()
        undo.clear()
        pendingImport = null
        mutable.value = LyricsEditorUiState(activeTrackKey = state.value.activeTrackKey)
    }

    fun save(closeAfter: Boolean = false) {
        if (state.value.parsing || state.value.rowId != null) return
        if (state.value.text != state.value.source?.originalText) { report(state.value.error ?: LyricsEditError.IMPORT_FORMAT); return }
        operation {
            val snapshot = state.value
            val target = requireNotNull(snapshot.target)
            val source = requireNotNull(snapshot.source)
            val baseline = requireNotNull(snapshot.baseline)
            withContext(worker) { editor.validate(source) }
            checkpointJob?.cancelAndJoin()
            checkpointNow()
            // Re-read the selected source, including metadata, before accepting this edit.
            repository.invalidate(LyricsInvalidation(target.trackKey))
            val current = repository.findLyrics(target) as? LyricsLookupResult.Found
            if (current?.lyrics?.source?.stableId.orEmpty() != baseline.originId || current?.lyrics?.revision.orEmpty() != baseline.originRevision)
                throw LyricsEditException(LyricsEditError.REVISION_CONFLICT)
            when (val result = storage.commit(target, source, baseline)) {
                is LyricsCommitResult.Failed -> throw LyricsEditException(result.reason)
                is LyricsCommitResult.Saved -> {
                    repository.invalidate(LyricsInvalidation(target.trackKey))
                    undo.clear()
                    mutable.value = state.value.copy(dirty = false, confirmation = null, error = null,
                        canUndo = false,
                        saved = true,
                        baseline = baseline.copy(originId = LyricsSource.LocalOverride(result.row.id).stableId,
                            originRevision = result.row.revision, overrideId = result.row.id,
                            overrideRevision = result.row.revision, baselineChecksum = source.revision))
                    if (closeAfter) {
                        if (snapshot.removeAfterDirty) mutable.value = state.value.copy(removeAfterDirty = false, confirmation = LyricsEditorConfirmation.REMOVE)
                        else close()
                    }
                }
            }
        }
    }

    fun removeOverride() = operation {
        val snapshot = state.value
        val target = requireNotNull(snapshot.target)
        checkpointJob?.cancelAndJoin()
        storage.remove(target, requireNotNull(snapshot.baseline))?.let { throw LyricsEditException(it) }
        repository.invalidate(LyricsInvalidation(target.trackKey))
        close()
    }

    fun importBytes(bytes: ByteArray) {
        if (state.value.baseline == null) { report(state.value.error ?: LyricsEditError.TARGET_MISSING); return }
        if (state.value.parsing) {
            val session = state.value.sessionId
            scope.launch { parseJob?.join(); if (state.value.sessionId == session) importBytes(bytes) }
            return
        }
        operation {
        withContext(worker) { editor.parse(bytes).also(editor::validate) }
        if (state.value.dirty || state.value.rowId != null) {
            pendingImport = bytes.copyOf()
            mutable.value = state.value.copy(confirmation = LyricsEditorConfirmation.IMPORT)
        } else replaceImport(bytes)
        }
    }
    fun confirmImport() = operation { pendingImport?.let { replaceImport(it) }; pendingImport = null }
    private suspend fun replaceImport(bytes: ByteArray) {
        val source = withContext(worker) { editor.parse(bytes) }
        rememberUndo()
        mutable.value = state.value.copy(rowId = null, confirmation = null, selectedLine = 0, mode = modeFor(source))
        publishDocument(source, 0)
        checkpointNow()
    }

    suspend fun exportBytes(): ByteArray {
        val snapshot = state.value
        if (snapshot.parsing || snapshot.rowId != null || snapshot.source == null) throw LyricsEditException(LyricsEditError.EXPORT_WRITE)
        if (snapshot.text != snapshot.source.originalText) throw LyricsEditException(snapshot.error ?: LyricsEditError.IMPORT_FORMAT)
        return withContext(worker) { editor.validate(snapshot.source); snapshot.source.serializeUnchanged() }
    }
    fun holdExport(bytes: ByteArray) {
        flushDraft()
        preparedExport = bytes
        mutable.value = state.value.copy(busy = true)
    }
    fun completeExport(write: suspend (ByteArray) -> String?) {
        val bytes = preparedExport
        scope.launch {
            try {
                if (bytes == null) throw LyricsEditException(LyricsEditError.EXPORT_WRITE)
                exported(write(bytes))
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { report(reason(failure)) }
            finally { preparedExport = null; mutable.value = state.value.copy(busy = false) }
        }
    }
    fun exported(name: String? = null) { mutable.value = state.value.copy(exported = true, exportedName = name, error = null) }
    fun report(error: LyricsEditError) { mutable.value = state.value.copy(error = error) }
    fun onRestore() { if (state.value.visible) { report(LyricsEditError.RESTORE_CONFLICT); flushDraft() } }
    fun flushDraft() {
        if (!state.value.visible || state.value.busy) return
        val session = state.value.sessionId
        scope.launch {
            try { checkpointNow() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (state.value.sessionId == session) report(reason(failure)) }
        }
    }

    private fun mutate(closeRow: Boolean = false, advanceLine: Boolean = false, action: (LyricsSourceDocument) -> LyricsSourceDocument) = operation {
        val selected = state.value.selectedLine
        val source = state.value.source ?: return@operation
        if (state.value.text != source.originalText) throw LyricsEditException(state.value.error ?: LyricsEditError.IMPORT_FORMAT)
        val selectedId = source.lines.getOrNull(selected)?.id
        val result = withContext(worker) { action(source) }
        rememberUndo()
        val retainedSelection = result.lines.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
            ?: selected.coerceIn(result.lines.indices)
        publishDocument(result, if (advanceLine) nextEditable(result, retainedSelection) else retainedSelection)
        if (closeRow) mutable.value = state.value.copy(rowId = null, confirmation = null, rowCloseConfirmation = false)
        checkpointNow()
    }
    private fun rememberUndo() {
        val snapshot = state.value
        snapshot.source?.let {
            if (undo.size == LyricsEditingLimits.UNDO) undo.removeFirst()
            undo.addLast(UndoEntry(it, snapshot.text, snapshot.selectedLine, snapshot.mode, snapshot.rowId, snapshot.rowText, snapshot.rowTimes))
            // Retain at least the most recent action, even for a source with many tiny rows.
            while (undo.size > 1 && undo.sumOf { entry -> entry.weight } > LyricsEditingLimits.UNDO_WEIGHT) undo.removeFirst()
            mutable.value = state.value.copy(canUndo = undo.isNotEmpty())
        }
    }
    private fun publishDocument(source: LyricsSourceDocument, selected: Int) {
        mutable.value = state.value.copy(source = source, text = source.originalText, parsing = false,
            dirty = source.revision != state.value.baseline?.baselineChecksum, selectedLine = selected.coerceIn(source.lines.indices), error = null, exported = false, saved = false)
    }
    private fun modeFor(source: LyricsSourceDocument): LyricsEditorMode = when {
        source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_ENCODING } ||
            source.originalText.any { it.code < 32 && it !in "\t\r\n" } -> LyricsEditorMode.RAW
        parser.project(source) is LyricsDocument.Synced -> LyricsEditorMode.STRUCTURED
        source.lines.all { it.kind == LyricsSourceLineKind.TEXT } -> LyricsEditorMode.PLAIN
        else -> LyricsEditorMode.RAW
    }
    private fun scheduleCheckpoint() {
        checkpointJob?.cancel()
        checkpointJob = scope.launch {
            delay(500)
            try { checkpointNow() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { report(reason(failure)) }
        }
    }
    private suspend fun checkpointNow() = checkpointMutex.withLock {
        val snapshot = state.value
        val target = snapshot.target ?: return@withLock
        val source = snapshot.source ?: return@withLock
        val baseline = snapshot.baseline ?: return@withLock
        if (snapshot.recovery != null) return@withLock
        if (!snapshot.dirty && snapshot.rowId == null) {
            storage.discardDraft(target.trackKey)
            return@withLock
        }
        val bytes = if (snapshot.text == source.originalText) source.serializeUnchanged()
            else (if (source.hasBom) source.encoding.bom() else byteArrayOf()) + snapshot.text.toByteArray(source.encoding.charset)
        storage.checkpoint(target, bytes, baseline.copy(mode = snapshot.mode, selectedLine = snapshot.selectedLine,
            rowIds = if (snapshot.text == source.originalText) source.lines.map { it.id } else emptyList(),
            pendingRowId = snapshot.rowId, pendingText = snapshot.rowText, pendingTimes = snapshot.rowTimes))
    }
    private fun operation(action: suspend () -> Unit) {
        if (!state.value.visible || state.value.busy || state.value.parsing) return
        mutable.value = state.value.copy(busy = true, error = null)
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutable.value = state.value.copy(error = reason(failure), confirmation = if (reason(failure) == LyricsEditError.WORD_TIMING_CHOICE) LyricsEditorConfirmation.WORD_TIMINGS else state.value.confirmation)
            } finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    private fun reason(failure: Exception) = when (failure) {
        is LyricsEditException -> failure.reason
        is LyricsInputLimitException -> LyricsEditError.TOO_LARGE
        else -> LyricsEditError.STORAGE
    }
}
