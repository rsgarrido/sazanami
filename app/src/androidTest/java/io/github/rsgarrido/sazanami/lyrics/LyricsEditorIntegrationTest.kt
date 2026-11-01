package io.github.rsgarrido.sazanami.lyrics

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.backup.LyricsBackup
import io.github.rsgarrido.sazanami.ui.lyrics.*
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsEditorIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val storage = LyricsOverrideStore(database)
    private val playback = MutableStateFlow(PlaybackUiState())
    private val progress = MutableStateFlow(PlaybackProgressUiState())
    private var workspaceOnly = false
    private var fontScale = 1f
    private var importCalls = 0
    private var exportCalls = 0
    private var playPauseCalls = 0
    private var lastSeek: Int? = null
    private val audio = File(context.cacheDir, "lyrics-editor-${UUID.randomUUID()}.mp3")
    private lateinit var model: EditorTestModel
    private lateinit var factory: ViewModelProvider.Factory
    private lateinit var lyricsPlayback: LyricsPlaybackController
    private var captureCalls = 0
    private val originalSong = Song(777, "Synthetic editor song", "Original author", "Album", 1, 10_000,
        LyricsFixtureProvider.uri("missing"), "", "", null,
        volumeName = "primary", displayName = "synthetic.mp3", relativePath = "Music/", fileSizeBytes = 100)

    @After fun tearDown() {
        if (::model.isInitialized) model.viewModelScope.cancel()
        database.close()
        audio.delete()
    }

    private fun mount(embedded: Boolean = false) {
        val song = if (embedded) {
            InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic.mp3").use { input ->
                audio.outputStream().use { input.copyTo(it) }
            }
            originalSong.copy(filePath = audio.absolutePath)
        } else originalSong
        runBlocking { storage.updateLibrary(listOf(song)) }
        val roots = object : LyricsRootStore {
            override val roots = MutableStateFlow(emptyList<LyricsRoot>())
            override suspend fun addRoot(root: LyricsRoot) { roots.value += root }
            override suspend fun removeRoot(rootUri: String) { roots.value = emptyList() }
        }
        val index = object : LyricsIndexStore {
            var snapshot: LyricsIndexSnapshot? = LyricsIndexSnapshot(emptyList(), emptySet(), generatedAtEpochMs = 1)
            override suspend fun load() = snapshot
            override suspend fun save(snapshot: LyricsIndexSnapshot) { this.snapshot = snapshot }
            override suspend fun clear() { snapshot = null }
        }
        val tree = object : LyricsTreeDataSource { override suspend fun scanRoot(root: LyricsRoot) = LyricsTreeScanResult.Success(emptyList()) }
        val repository = DefaultLocalLyricsRepository(roots, index, LyricsIndexer(roots, tree, index, Dispatchers.IO),
            AndroidLyricsDocumentReader(context.contentResolver), LrcParser(), Dispatchers.IO,
            AndroidEmbeddedLyricsReader(context.contentResolver, Dispatchers.IO), localOverrides = storage)
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorTestModel { scope ->
                LyricsEditorController(storage, repository, LyricsPositionCapture {
                    captureCalls++
                    LyricsPositionSnapshot(it.trackKey, 1500, ready = !progress.value.isDirect,
                        direct = progress.value.isDirect, confirmed = !progress.value.isDirect)
                }, scope, playback, {
                    playPauseCalls++
                    playback.value = playback.value.copy(isPlaying = !playback.value.isPlaying)
                }, { lastSeek = it }, progress = progress)
            } as T
        }
        compose.runOnUiThread {
            playback.value = PlaybackUiState(currentSong = song)
            model = ViewModelProvider(compose.activity, factory)[EditorTestModel::class.java]
            lyricsPlayback = LyricsPlaybackController(repository, playback, LyricsPositionSource { 1500 }, model.viewModelScope)
            lyricsPlayback.setVisible(true)
        }
        compose.setContent { Content() }
        compose.waitUntil(5_000) { lyricsPlayback.uiState.value !is LyricsPlaybackUiState.Loading && lyricsPlayback.uiState.value !is LyricsPlaybackUiState.Hidden }
    }

    @Composable private fun Content() {
        val state by lyricsPlayback.uiState.collectAsState()
        CompositionLocalProvider(LocalLyricsEditor provides model.editor) {
            MaterialTheme {
                if (workspaceOnly) {
                    val editorState by model.editor.state.collectAsState()
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        Box(Modifier.width(320.dp).fillMaxHeight()) {
                            LyricsEditorWorkspace(model.editor, editorState, onImport = { importCalls++ }, onExport = { exportCalls++ })
                        }
                    }
                } else {
                    LyricsScreen(state, false, rememberPlayerLyricsTransitionState(true) {}, interactive = true,
                        onBack = {}, onPlayPause = {}, onSeek = {}, onSuspendAutoFollow = {}, onReturnToCurrentLine = {}, onRescan = {}, onOpenSettings = {})
                    LyricsEditorHost(model.editor)
                }
            }
        }
    }
    private fun openEditor(create: Boolean) {
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_editor_actions)).performClick()
        compose.onNodeWithText(context.getString(if (create) R.string.lyrics_editor_create else R.string.lyrics_editor_edit)).performClick()
        compose.waitUntil(5_000) { model.editor.state.value.source != null && !model.editor.state.value.busy }
    }

    private fun openWorkspace(text: String, largeFont: Boolean = false) {
        workspaceOnly = true
        fontScale = if (largeFont) 1.5f else 1f
        mount()
        compose.runOnUiThread { model.editor.open(originalSong) }
        compose.waitUntil(5_000) { model.editor.state.value.source != null && !model.editor.state.value.busy }
        compose.onNodeWithTag("lyrics_editor_text").performTextReplacement(text)
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
    }

    private fun menuAction(resource: Int) {
        compose.onNodeWithTag("lyrics_editor_more").performClick()
        compose.onNodeWithText(context.getString(resource)).performClick()
    }

    @Test fun compactLargeFontWorkspaceKeepsModesAndSynchronizationActionsDiscoverable() {
        openWorkspace("First\nSecond\nLast", largeFont = true)
        compose.onNodeWithTag("lyrics_editor_cancel").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_editor_save").assertIsDisplayed().assertIsEnabled()
        LyricsEditorMode.entries.forEach { compose.onNodeWithTag("lyrics_editor_mode_${it.name}").assertIsDisplayed() }
        compose.onNodeWithTag("lyrics_editor_dock").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_editor_mode_STRUCTURED").performClick().assertIsSelected()
        listOf("stamp", "play_pause", "replay", "skip", "undo").forEach {
            compose.onNodeWithTag("lyrics_editor_$it").assertIsDisplayed()
        }
        compose.onNodeWithTag("lyrics_editor_replay").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_undo").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_stamp").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.selectedLine == 1 }
        assertEquals(1500L, model.editor.state.value.source!!.lines.first().timestamps.single().timestampMs)
        compose.onNodeWithTag("lyrics_editor_undo").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.selectedLine == 0 }
        assertTrue(model.editor.state.value.source!!.lines.first().timestamps.isEmpty())
        compose.onNodeWithTag("lyrics_editor_stamp").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy }
        val first = model.editor.state.value.source!!.lines.first()
        compose.onNodeWithTag("lyrics_editor_row_${first.id}").performClick().assertIsSelected()
        compose.onNodeWithTag("lyrics_editor_stamp").assertTextContains(context.getString(R.string.lyrics_editor_restamp))
        compose.onNodeWithTag("lyrics_editor_replay").performClick()
        assertEquals(1500, lastSeek)
        compose.onNodeWithTag("lyrics_editor_stamp").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.selectedLine == 1 }
        assertEquals(1500L, model.editor.state.value.source!!.lines.first().timestamps.single().timestampMs)
        compose.onNodeWithTag("lyrics_editor_play_pause").performClick()
        compose.waitUntil(5_000) { model.editor.state.value.isPlaying }
        assertEquals(1, playPauseCalls)
        compose.onNodeWithTag("lyrics_editor_play_pause").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.isPlaying }
        assertEquals(2, playPauseCalls)
        compose.onNodeWithTag("lyrics_editor_skip").performClick()
        assertEquals(2, model.editor.state.value.selectedLine)
        menuAction(R.string.lyrics_editor_import)
        menuAction(R.string.lyrics_editor_export)
        assertEquals(1, importCalls)
        assertEquals(1, exportCalls)
        menuAction(R.string.lyrics_editor_manage_drafts)
        compose.waitUntil(5_000) { model.editor.state.value.otherDrafts != null && !model.editor.state.value.busy }
        compose.runOnUiThread { model.editor.closeDraftManager(); progress.value = PlaybackProgressUiState(isDirect = true) }
        compose.waitUntil(5_000) { model.editor.state.value.isDirectPlayback }
        compose.onNodeWithTag("lyrics_editor_stamp").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_replay").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_direct").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_editor_direct").performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_direct_detail)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_done)).performClick()
        compose.runOnUiThread { model.editor.holdExport(model.editor.state.value.source!!.serializeUnchanged()) }
        compose.onNodeWithTag("lyrics_editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_stamp").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_mode_RAW").assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_editor_more").performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_import)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_export)).assertIsNotEnabled()
        compose.runOnUiThread { model.editor.completeExport { null } }
        compose.waitUntil(5_000) { !model.editor.state.value.busy }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_save_info)).performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_local_notice)).assertIsDisplayed()
    }

    @Test fun selectedIdentityIsRevealedWithoutFollowingPlaybackOrOverridingManualReview() {
        openWorkspace((1..80).joinToString("\n") { "Line text $it" })
        compose.onNodeWithTag("lyrics_editor_mode_STRUCTURED").performClick()
        compose.runOnUiThread { model.editor.select(60) }
        val selectedId = model.editor.state.value.source!!.lines[60].id
        compose.onNodeWithTag("lyrics_editor_row_$selectedId").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("lyrics_editor_rows").performScrollToIndex(0)
        compose.runOnUiThread { playback.value = playback.value.copy(isPlaying = true) }
        compose.waitUntil(5_000) { model.editor.state.value.isPlaying }
        val firstId = model.editor.state.value.source!!.lines.first().id
        compose.onNodeWithTag("lyrics_editor_row_$firstId").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_editor_stamp").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.selectedLine == 61 }
        val advancedId = model.editor.state.value.source!!.lines[61].id
        compose.onNodeWithTag("lyrics_editor_row_$advancedId").assertIsDisplayed().assertIsSelected()
        compose.runOnUiThread { model.editor.select(79) }
        val lastId = model.editor.state.value.source!!.lines.last().id
        compose.onNodeWithTag("lyrics_editor_row_$lastId").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_editor_skip").performClick()
        assertEquals(79, model.editor.state.value.selectedLine)
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { Content() } }
        compose.onNodeWithTag("lyrics_editor_row_$lastId").assertIsDisplayed().assertIsSelected()
    }

    @Test fun timingDialogAndRowMenusPreserveSourceUntilAnExplicitAction() {
        openWorkspace("First\nSecond\nLast")
        compose.onNodeWithTag("lyrics_editor_mode_STRUCTURED").performClick()
        val bytes = model.editor.state.value.source!!.serializeUnchanged()
        compose.onNodeWithTag("lyrics_editor_timing").performClick()
        assertArrayEquals(bytes, model.editor.state.value.source!!.serializeUnchanged())
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_done)).performClick()
        assertArrayEquals(bytes, model.editor.state.value.source!!.serializeUnchanged())
        compose.onNodeWithTag("lyrics_editor_timing").performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_earlier)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.metadata.offsetMs == -100L }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_later)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.metadata.offsetMs == 0L }
        compose.onNode(hasSetTextAction()).performTextReplacement("200")
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_apply)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.metadata.offsetMs == 200L }
        compose.onNodeWithTag("lyrics_editor_timing").performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_zero)).performScrollTo().performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.metadata.offsetMs == 0L }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_original)).performScrollTo().performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_done)).performClick()
        val selectedId = model.editor.state.value.source!!.lines[model.editor.state.value.selectedLine].id
        val index = model.editor.state.value.selectedLine
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_editor_row_actions, index + 1)).performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_down, index + 1)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy }
        assertEquals(selectedId, model.editor.state.value.source!!.lines[model.editor.state.value.selectedLine].id)
        val moved = model.editor.state.value.selectedLine
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_editor_row_actions, moved + 1)).performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_delete_line, moved + 1)).performClick()
        assertTrue(model.editor.state.value.source!!.lines.any { it.id == selectedId })
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_delete_confirm)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.lines.none { it.id == selectedId } }
        compose.onNodeWithTag("lyrics_editor_undo").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.lines.any { it.id == selectedId } }
        compose.onNodeWithTag("lyrics_editor_row_$selectedId").assertIsDisplayed().assertIsSelected()
        val restoredIndex = model.editor.state.value.selectedLine
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_editor_row_actions, restoredIndex + 1)).performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_up, restoredIndex + 1)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.selectedLine == restoredIndex - 1 }
        assertEquals(selectedId, model.editor.state.value.source!!.lines[model.editor.state.value.selectedLine].id)
        val count = model.editor.state.value.source!!.lines.size
        compose.onNodeWithTag("lyrics_editor_insert").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.lines.size == count + 1 }
        assertEquals(selectedId, model.editor.state.value.source!!.lines[model.editor.state.value.selectedLine].id)
        compose.onNodeWithTag("lyrics_editor_undo").performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.source!!.lines.size == count }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun modeChangesKeepRawTextAndTimingRestrictionHasAnAccurateExplanation() {
        openWorkspace("[00:01.000]First\n[00:02.000]Second")
        compose.onNodeWithTag("lyrics_editor_mode_STRUCTURED").performClick()
        val bytes = model.editor.state.value.source!!.serializeUnchanged()
        compose.onNodeWithTag("lyrics_editor_mode_PLAIN").performClick()
        assertEquals(LyricsEditorMode.STRUCTURED, model.editor.state.value.mode)
        compose.onNodeWithTag("lyrics_editor_error").performClick()
        compose.onAllNodesWithText(context.getString(R.string.lyrics_editor_plain_timing)).onLast().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_done)).performClick()
        compose.onNodeWithTag("lyrics_editor_mode_RAW").performClick()
        compose.onNodeWithTag("lyrics_editor_dock").assertDoesNotExist()
        assertArrayEquals(bytes, model.editor.state.value.source!!.serializeUnchanged())
        val raw = "[ar:Original]\r\n[00:01.000]<00:01.000>Raw <00:01.500>words"
        compose.onNodeWithTag("lyrics_editor_text").performTextReplacement(raw)
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
        val rawBytes = model.editor.state.value.source!!.serializeUnchanged()
        val rawEditor = compose.onNodeWithTag("lyrics_editor_text")
        rawEditor.performTextInputSelection(TextRange(3, 8))
        rawEditor.assertIsFocused()
        rawEditor.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3, 8)))
        compose.onNodeWithTag("lyrics_editor_mode_STRUCTURED").performClick()
        rawEditor.assertDoesNotExist()
        compose.onNodeWithTag("lyrics_editor_mode_RAW").performClick()
        assertEquals(raw, model.editor.state.value.text)
        assertArrayEquals(rawBytes, model.editor.state.value.source!!.serializeUnchanged())
        rawEditor.assertTextContains(raw)
        rawEditor.assertIsNotFocused()
        rawEditor.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3, 8)))
        assertTrue(model.editor.state.value.dirty)

        // Refocusing is an explicit user action, not a side effect of restoring the range.
        rawEditor.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rawEditor.assertIsFocused()
        rawEditor.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3, 8)))
        rawEditor.performTextInput(":Edit")
        val edited = raw.replaceRange(3, 8, ":Edit")
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
        assertEquals(edited, model.editor.state.value.text)
        assertEquals(edited, model.editor.state.value.source!!.originalText)
        rawEditor.assertTextContains(edited)
        rawEditor.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(8, 8)))
        assertTrue(model.editor.state.value.dirty)
    }

    @Test fun createWithoutFoldersSaveRefreshSharedScreenAndRemoveOverride() {
        mount()
        assertTrue(lyricsPlayback.uiState.value is LyricsPlaybackUiState.Unavailable)
        openEditor(create = true)
        compose.onNodeWithTag("lyrics_editor_text").performTextReplacement("Paper stars\nOriginal synthetic verse")
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
        compose.onNodeWithTag("lyrics_editor_save").performClick()
        compose.waitUntil(5_000) { model.editor.state.value.saved && !model.editor.state.value.busy }
        compose.waitUntil(5_000) { (lyricsPlayback.uiState.value as? LyricsPlaybackUiState.Unsynced)?.sourcedLyrics?.source is LyricsSource.LocalOverride }
        val committed = runBlocking { storage.find(requireNotNull(model.editor.state.value.target)) } as LyricsLookupResult.Found
        assertEquals("Paper stars\nOriginal synthetic verse", committed.lyrics.sourceDocument!!.originalText)
        compose.onNodeWithTag("lyrics_editor_save").assertIsNotEnabled()
        compose.runOnUiThread { model.editor.requestClose() }
        compose.waitUntil(5_000) { !model.editor.state.value.visible }
        compose.onNodeWithText("Paper stars").assertIsDisplayed()
        openEditor(create = false)
        menuAction(R.string.lyrics_editor_remove)
        compose.onAllNodesWithText(context.getString(R.string.lyrics_editor_remove)).onLast().performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.visible }
        assertTrue(runBlocking { database.lyricsDao().overrides() }.isEmpty())
        compose.waitUntil(5_000) { lyricsPlayback.uiState.value is LyricsPlaybackUiState.Unavailable }
    }

    @Test fun embeddedEditingRequiresWordTimingChoiceAndNeverWritesAudio() {
        mount(embedded = true)
        val bytes = audio.readBytes()
        assertTrue(lyricsPlayback.uiState.value is LyricsPlaybackUiState.Synced)
        openEditor(create = false)
        val before = model.editor.state.value.source!!
        val row = before.lines.first { it.segments.isNotEmpty() }
        val rowIndex = before.lines.indexOfFirst { it.id == row.id }
        compose.onNodeWithTag("lyrics_editor_rows").performScrollToIndex(rowIndex)
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_editor_edit_line, rowIndex + 1)).performClick()
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_line_text)).performTextReplacement("Corrected original words")
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_timestamps)).performTextReplacement("00:03.500")
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_apply)).performClick()
        compose.waitUntil(5_000) { model.editor.state.value.confirmation == LyricsEditorConfirmation.WORD_TIMINGS }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_remove_word_timings)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.rowId == null }
        val edited = model.editor.state.value.source!!
        assertTrue(edited.lines.first { it.id == row.id }.segments.isEmpty())
        assertEquals(3500L, edited.lines.first { it.id == row.id }.timestamps.single().timestampMs)
        before.lines.filter { it.id != row.id }.forEach { untouched ->
            assertEquals(untouched.rawText, edited.lines.first { it.id == untouched.id }.rawText)
        }
        compose.onNodeWithTag("lyrics_editor_save").performClick()
        compose.waitUntil(5_000) { model.editor.state.value.saved && !model.editor.state.value.busy }
        assertArrayEquals(bytes, audio.readBytes())
    }

    @Test fun activityRecreationRetainsPendingRowDraftAndTrackSwitchDisablesStamp() {
        mount()
        openEditor(create = true)
        compose.onNodeWithTag("lyrics_editor_text").performTextReplacement("First draft line\nSecond draft line")
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
        compose.runOnUiThread {
            model.editor.setMode(LyricsEditorMode.STRUCTURED)
            model.editor.editRow(model.editor.state.value.source!!.lines[1].id)
            model.editor.setRowText("Second corrected draft line")
            model.editor.setRowTimes("00:02.500")
            model.editor.flushDraft()
        }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            assertSame(model, ViewModelProvider(activity, factory)[EditorTestModel::class.java])
            activity.setContent { Content() }
        }
        compose.onNodeWithText("Second corrected draft line").assertIsDisplayed()
        compose.runOnUiThread {
            model.editor.cancelRow()
            playback.value = PlaybackUiState(currentSong = originalSong.copy(id = 888, volumeName = "card"))
        }
        compose.waitUntil(5_000) { model.editor.state.value.target?.trackKey != model.editor.state.value.activeTrackKey }
        compose.onNodeWithTag("lyrics_editor_stamp").assertIsNotEnabled()
        assertEquals(0, captureCalls)
        assertEquals(originalSong.id, model.editor.state.value.song!!.id)
        assertTrue(model.editor.state.value.dirty)
    }

    @Test fun dirtyBackKeepsDraftUntilExplicitDiscardAndSpanishLabelsAreAvailable() {
        mount()
        openEditor(create = true)
        compose.onNodeWithTag("lyrics_editor_text").performTextReplacement("Recoverable work")
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
        compose.runOnUiThread { model.editor.requestClose() }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_keep_editing)).performClick()
        compose.onNodeWithTag("lyrics_editor_text").assertTextContains("Recoverable work")
        compose.runOnUiThread { model.editor.requestClose() }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_discard)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.visible }
        assertTrue(runBlocking { database.lyricsDao().drafts() }.isEmpty())
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale("es")) }
        val spanish = context.createConfigurationContext(configuration).resources
        assertEquals("Guardar en la app", spanish.getString(R.string.lyrics_editor_save))
        assertEquals("Marcar / volver a marcar línea", spanish.getString(R.string.lyrics_editor_stamp))
        assertEquals("Guardar", spanish.getString(R.string.lyrics_editor_save_short))
        assertEquals("Más acciones del editor", spanish.getString(R.string.lyrics_editor_more))
    }

    @Test fun databaseRestoreNotifiesDirtyEditorAndKeepsItsSourceAndDraft() {
        mount()
        openEditor(create = true)
        compose.onNodeWithTag("lyrics_editor_text").performTextReplacement("Work before restoration")
        compose.waitUntil(5_000) { !model.editor.state.value.parsing }
        compose.runOnUiThread { model.editor.flushDraft() }
        compose.waitUntil(5_000) { runBlocking { database.lyricsDao().drafts().isNotEmpty() } }
        runBlocking { database.withTransaction { LyricsBackup.restoreWithinTransaction(database, emptyList()) } }
        compose.waitUntil(5_000) { model.editor.state.value.error == LyricsEditError.RESTORE_CONFLICT }
        assertEquals("Work before restoration", model.editor.state.value.text)
        assertTrue(model.editor.state.value.dirty)
        assertTrue(runBlocking { database.lyricsDao().drafts().isNotEmpty() })
    }

    class EditorTestModel(factory: (CoroutineScope) -> LyricsEditorController) : ViewModel() {
        val editor = factory(viewModelScope)
    }
}
