package io.github.rsgarrido.sazanami.lyrics

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.*
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class LyricsEditorControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @After fun closeScope() { scope.cancel() }

    @Test fun createsPlainOverrideAndClearsDraftOnlyAfterSuccessfulCommit() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Original synthetic text")
        f.controller.flushDraft()
        assertNotNull(f.storage.draft)
        f.controller.save()
        assertEquals("Original synthetic text", f.storage.row?.sourceBytes?.toString(Charsets.UTF_8))
        assertNull(f.storage.draft)
        assertTrue(f.controller.state.value.saved)
        assertFalse(f.controller.state.value.dirty)
        assertEquals(2, f.repository.invalidationCount)
        assertEquals(0, f.repository.rescans)
    }

    @Test fun failedSaveAndCheckpointKeepPreviousVersionAndWorkingText() = runBlocking {
        val f = Fixture("Saved source")
        f.controller.open(f.song)
        f.controller.setText("Changed draft")
        f.storage.failCommit = true
        f.controller.save()
        assertEquals(LyricsEditError.STORAGE, f.controller.state.value.error)
        assertEquals("Saved source", f.storage.row?.sourceBytes?.toString(Charsets.UTF_8))
        assertNotNull(f.storage.draft)
        assertEquals("Changed draft", f.controller.state.value.text)
        f.storage.failCheckpoint = true
        f.controller.setText("Still recoverable in editor")
        f.controller.save()
        assertEquals("Saved source", f.storage.row?.sourceBytes?.toString(Charsets.UTF_8))
        assertTrue(f.controller.state.value.dirty)
    }

    @Test fun repeatedSaveIsSerializedAndRevisionConflictRetainsDraft() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("New lyrics")
        f.storage.gate = CompletableDeferred()
        f.controller.save()
        assertTrue(f.controller.state.value.busy)
        f.controller.save()
        assertEquals(1, f.storage.commits)
        f.storage.gate?.complete(Unit)
        withTimeout(1000) { f.controller.state.first { !it.busy } }
        assertEquals(1, f.storage.commits)
        f.controller.setText("Second edit")
        f.storage.row = f.storage.row!!.copy(revision = "another-writer")
        f.controller.save()
        assertEquals(LyricsEditError.REVISION_CONFLICT, f.controller.state.value.error)
        assertNotNull(f.storage.draft)
    }

    @Test fun recoverDraftRestoresSourceModeSelectionAndPendingTimestampEdit() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("First\nSecond")
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        f.controller.select(1)
        f.controller.editRow(f.controller.state.value.source!!.lines[1].id)
        f.controller.setRowText("Second corrected")
        f.controller.setRowTimes("00:02.500")
        f.controller.flushDraft()
        val recreated = f.newController()
        recreated.open(f.song)
        assertNotNull(recreated.state.value.recovery)
        recreated.recoverDraft()
        assertEquals("First\nSecond", recreated.state.value.text)
        assertEquals(LyricsEditorMode.STRUCTURED, recreated.state.value.mode)
        assertEquals(1, recreated.state.value.selectedLine)
        assertEquals("Second corrected", recreated.state.value.rowText)
        assertEquals("00:02.500", recreated.state.value.rowTimes)
        recreated.applyRow()
        assertEquals("[00:02.500]Second corrected", recreated.state.value.source!!.lines[1].rawText)
    }

    @Test fun dirtyNavigationOffersSaveDiscardAndKeepWhileCleanCloseNeedsNoPrompt() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.requestClose()
        assertFalse(f.controller.state.value.visible)
        f.controller.open(f.song)
        f.controller.setText("Work")
        f.controller.requestClose()
        assertEquals(LyricsEditorConfirmation.LEAVE, f.controller.state.value.confirmation)
        f.controller.dismissConfirmation()
        assertTrue(f.controller.state.value.visible)
        f.controller.requestClose()
        f.controller.save(closeAfter = true)
        assertFalse(f.controller.state.value.visible)
        assertNotNull(f.storage.row)
        f.controller.open(f.song)
        f.controller.setText("Discarded work")
        f.controller.flushDraft()
        f.controller.discardAndClose()
        assertNull(f.storage.draft)
        assertEquals("Work", f.storage.row!!.sourceBytes.toString(Charsets.UTF_8))
    }

    @Test fun stampingCapturesOnTapSubtractsOffsetAdvancesRestampsAndUndoes() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("[offset:250]\nFirst\nSecond\nThird")
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        f.controller.select(1)
        f.positionMs = 1500
        f.controller.stamp()
        assertEquals(1250L, f.controller.state.value.source!!.lines[1].timestamps.single().timestampMs)
        assertEquals(2, f.controller.state.value.selectedLine)
        f.positionMs = 2500
        f.controller.stamp()
        assertEquals(3, f.controller.state.value.selectedLine)
        f.controller.undo()
        assertTrue(f.controller.state.value.source!!.lines[2].timestamps.isEmpty())
        assertEquals(2, f.controller.state.value.selectedLine)
        f.controller.select(1)
        f.positionMs = 1600
        f.controller.stamp()
        assertEquals(1350L, f.controller.state.value.source!!.lines[1].timestamps.single().timestampMs)
        f.controller.skip()
        assertEquals(3, f.controller.state.value.selectedLine)
        assertEquals(0, f.playPauseCalls)
        assertEquals(0, f.seekCalls)
    }

    @Test fun trackChangeRetainsOriginalDraftAndRejectsStampingAgainstAnotherTrack() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Keep original draft")
        f.controller.flushDraft()
        val bytes = f.storage.draft!!.sourceBytes.copyOf()
        f.playback.value = PlaybackUiState(currentSong = f.song.copy(volumeName = "different-volume"))
        assertFalse(f.controller.state.value.canStamp)
        f.controller.stamp()
        assertEquals(LyricsEditError.TRACK_MISMATCH, f.controller.state.value.error)
        assertEquals(f.song.toLyricsIdentity(), f.controller.state.value.target)
        assertArrayEquals(bytes, f.storage.draft!!.sourceBytes)
    }

    @Test fun snapshotPolicyRequiresReadinessQualifiedTrackAndConfirmedSeek() {
        val confirmed = LyricsPositionSnapshot("qualified", 1000, ready = true, direct = true)
        assertEquals(1000L, confirmed.positionFor("qualified"))
        listOf(confirmed.copy(ready = false), confirmed.copy(seeking = true), confirmed.copy(confirmed = false)).forEach { snapshot ->
            try { snapshot.positionFor("qualified"); fail("Expected unsupported capture") }
            catch (failure: LyricsEditException) { assertEquals(LyricsEditError.POSITION_UNAVAILABLE, failure.reason) }
        }
        try { confirmed.positionFor("other-volume"); fail("Expected mismatch") }
        catch (failure: LyricsEditException) { assertEquals(LyricsEditError.TRACK_MISMATCH, failure.reason) }
    }

    @Test fun importReplacementRequiresConfirmationAndExportNeverCommits() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Existing draft")
        f.controller.importBytes("[00:01]<00:01>Imported".toByteArray())
        assertEquals(LyricsEditorConfirmation.IMPORT, f.controller.state.value.confirmation)
        assertEquals("Existing draft", f.controller.state.value.text)
        f.controller.dismissConfirmation()
        assertEquals("Existing draft", f.controller.state.value.text)
        f.controller.importBytes("[00:01]<00:01>Imported".toByteArray())
        f.controller.confirmImport()
        assertTrue(f.controller.state.value.dirty)
        val bytes = f.controller.exportBytes()
        assertEquals("[00:01]<00:01>Imported", bytes.toString(Charsets.UTF_8))
        f.controller.exported()
        assertTrue(f.controller.state.value.dirty)
        assertNull(f.storage.row)
        f.controller.undo()
        assertEquals("Existing draft", f.controller.state.value.text)
        f.controller.importBytes(byteArrayOf(0xff.toByte()))
        assertEquals("Existing draft", f.controller.state.value.text)
        assertEquals(LyricsEditError.ENCODING, f.controller.state.value.error)
    }

    @Test fun restoreEpochAndSourceChangesPreventStaleCommitWithoutDestroyingDraft() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Before restore")
        f.storage.epochValue = "restored"
        f.controller.onRestore()
        f.controller.save()
        assertEquals(LyricsEditError.RESTORE_CONFLICT, f.controller.state.value.error)
        assertEquals("Before restore", f.storage.draft!!.sourceBytes.toString(Charsets.UTF_8))
        assertNull(f.storage.row)
    }

    @Test fun removeWithDirtyContentResolvesDraftBeforeSeparateRemovalConfirmation() = runBlocking {
        val f = Fixture("Saved version")
        f.controller.open(f.song)
        f.controller.setText("Unsaved correction")
        f.controller.requestRemove()
        assertTrue(f.controller.state.value.removeAfterDirty)
        assertEquals(LyricsEditorConfirmation.LEAVE, f.controller.state.value.confirmation)
        f.controller.discardChangesChoice()
        assertEquals("Saved version", f.controller.state.value.text)
        assertEquals(LyricsEditorConfirmation.REMOVE, f.controller.state.value.confirmation)
        assertNotNull(f.storage.row)
        f.controller.removeOverride()
        assertNull(f.storage.row)
        assertFalse(f.controller.state.value.visible)
    }

    @Test fun failedExportRetainsDraftAndNeverCommitsTheReviewedBytes() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Export this draft")
        val bytes = f.controller.exportBytes()
        f.controller.holdExport(bytes)
        assertTrue(f.controller.state.value.busy)
        f.controller.completeExport { throw LyricsEditException(LyricsEditError.EXPORT_WRITE) }
        assertEquals(LyricsEditError.EXPORT_WRITE, f.controller.state.value.error)
        assertEquals("Export this draft", f.controller.state.value.text)
        assertTrue(f.controller.state.value.dirty)
        assertNull(f.storage.row)
        assertNotNull(f.storage.draft)
    }

    @Test fun changedExternalSourceIsNotSilentlyOverriddenByStaleDraft() = runBlocking {
        val f = Fixture()
        fun external(text: String): SourcedLyrics {
            val source = LrcParser().parseSource(text)
            return SourcedLyrics(LrcParser().project(source), LyricsSource.LocalSidecar("content://synthetic-sidecar", "track.lrc"), source)
        }
        f.repository.external = external("[00:01]Original")
        f.controller.open(f.song)
        f.controller.setText("[00:01]My correction")
        f.repository.external = external("[00:01]Changed elsewhere")
        f.controller.save()
        assertEquals(LyricsEditError.REVISION_CONFLICT, f.controller.state.value.error)
        assertEquals(0, f.storage.commits)
        assertEquals("[00:01]My correction", f.storage.draft!!.sourceBytes.toString(Charsets.UTF_8))
    }

    @Test fun invalidRawBufferCannotSwitchModesOrSaveThePreviousParsedVersion() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setMode(LyricsEditorMode.RAW)
        f.controller.setText("Invalid\u0000raw buffer")
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        assertEquals(LyricsEditorMode.RAW, f.controller.state.value.mode)
        f.controller.save()
        assertEquals(0, f.storage.commits)
        assertEquals("Invalid\u0000raw buffer", f.controller.state.value.text)
        f.controller.importBytes("Valid replacement".toByteArray())
        f.controller.confirmImport()
        f.controller.undo()
        assertEquals("Invalid\u0000raw buffer", f.controller.state.value.text)
        assertEquals(LyricsEditorMode.RAW, f.controller.state.value.mode)
    }

    @Test fun backFromChangedRowKeepsPendingTextUntilExplicitApplyOrDiscard() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("First line")
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        f.controller.editRow(f.controller.state.value.source!!.lines.first().id)
        f.controller.setRowText("Pending correction")
        f.controller.requestRowClose()
        assertTrue(f.controller.state.value.rowCloseConfirmation)
        f.controller.keepRowEditing()
        assertEquals("Pending correction", f.controller.state.value.rowText)
        f.controller.applyRow()
        assertEquals("Pending correction", f.controller.state.value.text)
        assertFalse(f.controller.state.value.rowCloseConfirmation)
    }

    @Test fun dismissingRecoveryOrAnUnverifiedOpenNeverDeletesUnreviewedDraft() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Unfinished work")
        f.controller.flushDraft()
        val reopened = f.newController()
        reopened.open(f.song)
        assertNotNull(reopened.state.value.recovery)
        reopened.requestClose()
        assertNotNull(f.storage.draft)
        f.storage.failLoad = LyricsEditError.TARGET_MISSING
        val beforeLibraryReady = f.newController()
        beforeLibraryReady.open(f.song)
        assertNull(beforeLibraryReady.state.value.source)
        assertEquals(LyricsEditError.TARGET_MISSING, beforeLibraryReady.state.value.error)
        beforeLibraryReady.requestClose()
        assertNotNull(f.storage.draft)
    }

    @Test fun sequentialStampUndoHistoryIsBounded() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText((0 until 30).joinToString("\n") { "Original line $it" })
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        repeat(30) { f.positionMs = it * 1000L; f.controller.stamp() }
        repeat(30) { f.controller.undo() }
        assertEquals(30 - LyricsEditingLimits.UNDO, f.controller.state.value.source!!.lines.count { it.timestamps.isNotEmpty() })
    }

    @Test fun laterRawEditsCannotBeDiscardedByUndoingAnOlderStamp() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("Original line")
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        f.controller.stamp()
        assertTrue(f.controller.state.value.canUndo)
        f.controller.setMode(LyricsEditorMode.RAW)
        f.controller.setText("[00:01]Later raw correction")
        assertFalse(f.controller.state.value.canUndo)
        f.controller.undo()
        assertEquals("[00:01]Later raw correction", f.controller.state.value.text)
    }

    @Test fun selectionFollowsStableRowIdentityAcrossOffsetInsertionAndReordering() = runBlocking {
        val f = Fixture()
        f.controller.open(f.song)
        f.controller.setText("First\nSecond")
        f.controller.setMode(LyricsEditorMode.STRUCTURED)
        val id = f.controller.state.value.source!!.lines.first().id
        f.controller.shiftOffset(100)
        assertEquals(id, f.controller.state.value.source!!.lines[f.controller.state.value.selectedLine].id)
        f.controller.moveLine(id, 1)
        assertEquals(id, f.controller.state.value.source!!.lines[f.controller.state.value.selectedLine].id)
        f.controller.stamp()
        val stamped = f.controller.state.value.source!!.lines.single { it.id == id }
        assertEquals(900L, stamped.timestamps.single().timestampMs)
    }

    private inner class Fixture(initial: String? = null) {
        val uri = mock(Uri::class.java).also { doReturn("content://media/primary/audio/1").`when`(it).toString() }
        val song = Song(1, "Synthetic", "Author", "Album", 1, 10_000, uri, "", "", null,
            volumeName = "primary", displayName = "track.mp3", relativePath = "Music/")
        val storage = MemoryPersistence(song.toLyricsIdentity(), initial)
        val playback = MutableStateFlow(PlaybackUiState(currentSong = song))
        val repository = EditorRepository(storage)
        var positionMs = 1000L
        var playPauseCalls = 0
        var seekCalls = 0
        fun newController() = LyricsEditorController(storage, repository,
            LyricsPositionCapture { LyricsPositionSnapshot(it.trackKey, positionMs, true) }, scope, playback,
            { playPauseCalls++ }, { seekCalls++ }, Dispatchers.Unconfined)
        val controller = newController()
    }
}

private class MemoryPersistence(val target: SongLyricsIdentity, initial: String?) : LyricsEditorPersistence {
    var row: LyricsOverrideEntity? = initial?.let { makeRow(LrcParser().parseSource(it), "initial") }
    var draft: LyricsDraftEntity? = null
    var failCommit = false
    var failCheckpoint = false
    var failLoad: LyricsEditError? = null
    var epochValue = "initial"
    var commits = 0
    var gate: CompletableDeferred<Unit>? = null
    private fun makeRow(source: LyricsSourceDocument, revision: String) = LyricsOverrideEntity("override", "{}", target.trackKey,
        source.serializeUnchanged(), revision, source.revision, "PLAIN", "", "", 1, 1, "BOUND")
    override suspend fun epoch() = epochValue
    override suspend fun associated(song: SongLyricsIdentity) = listOfNotNull(row)
    override suspend fun commit(target: SongLyricsIdentity, source: LyricsSourceDocument, baseline: LyricsDraftState): LyricsCommitResult {
        commits++
        gate?.await()
        if (failCommit) return LyricsCommitResult.Failed(LyricsEditError.STORAGE)
        if (epochValue != baseline.epoch) return LyricsCommitResult.Failed(LyricsEditError.RESTORE_CONFLICT)
        LyricsCommitPolicy.previous(listOfNotNull(row), baseline)
        val saved = makeRow(source, "revision-$commits")
        row = saved
        draft = null
        return LyricsCommitResult.Saved(saved)
    }
    override suspend fun remove(target: SongLyricsIdentity, baseline: LyricsDraftState): LyricsEditError? { row = null; draft = null; return null }
    override suspend fun loadDraft(target: SongLyricsIdentity): LyricsDraftEntity? {
        failLoad?.let { throw LyricsEditException(it) }
        return draft
    }
    override suspend fun listDrafts() = listOfNotNull(draft)
    override suspend fun checkpoint(target: SongLyricsIdentity, bytes: ByteArray, state: LyricsDraftState) {
        if (failCheckpoint) throw java.io.IOException("Synthetic storage failure")
        draft = LyricsDraftEntity(target.trackKey, "{}", bytes.copyOf(), Json.encodeToString(state), 1)
    }
    override suspend fun discardDraft(key: String) { draft = null }
}

private class EditorRepository(private val storage: MemoryPersistence) : LocalLyricsRepository {
    override val roots = MutableStateFlow(emptyList<LyricsRoot>())
    var invalidationCount = 0
    var rescans = 0
    var external: SourcedLyrics? = null
    override suspend fun invalidate(change: LyricsInvalidation) { invalidationCount++ }
    override suspend fun findLyrics(song: SongLyricsIdentity): LyricsLookupResult {
        val row = storage.row ?: return external?.let(LyricsLookupResult::Found) ?: LyricsLookupResult.NoRootsConfigured
        val source = LrcParser().parseSource(row.sourceBytes)
        return LyricsLookupResult.Found(SourcedLyrics(LrcParser().project(source), LyricsSource.LocalOverride(row.id), source, row.revision))
    }
    override suspend fun loadCachedIndexSummary(): LyricsIndexSummary? = null
    override suspend fun addRoot(root: LyricsRoot): LyricsIndexResult = error("unused")
    override suspend fun removeRoot(rootUri: String): LyricsIndexResult = error("unused")
    override suspend fun refreshIndex(): LyricsIndexResult { rescans++; return LyricsIndexResult(LyricsIndexSnapshot(emptyList(), emptySet(), generatedAtEpochMs = 0)) }
}
