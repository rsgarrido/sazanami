package io.github.rsgarrido.sazanami.lyrics

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
                    LyricsPositionSnapshot(it.trackKey, 1500, ready = true)
                }, scope, playback, {}, {})
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
                LyricsScreen(state, false, rememberPlayerLyricsTransitionState(true) {}, interactive = true,
                    onBack = {}, onPlayPause = {}, onSeek = {}, onSuspendAutoFollow = {}, onReturnToCurrentLine = {}, onRescan = {}, onOpenSettings = {})
                LyricsEditorHost(model.editor)
            }
        }
    }
    private fun openEditor(create: Boolean) {
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_editor_actions)).performClick()
        compose.onNodeWithText(context.getString(if (create) R.string.lyrics_editor_create else R.string.lyrics_editor_edit)).performClick()
        compose.waitUntil(5_000) { model.editor.state.value.source != null && !model.editor.state.value.busy }
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
        compose.runOnUiThread { model.editor.requestClose() }
        compose.waitUntil(5_000) { !model.editor.state.value.visible }
        compose.onNodeWithText("Paper stars").assertIsDisplayed()
        openEditor(create = false)
        compose.runOnUiThread { model.editor.requestRemove() }
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
        compose.runOnUiThread { model.editor.editRow(row.id) }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_line_text)).performTextReplacement("Corrected original words")
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_apply)).performClick()
        compose.waitUntil(5_000) { model.editor.state.value.confirmation == LyricsEditorConfirmation.WORD_TIMINGS }
        compose.onNodeWithText(context.getString(R.string.lyrics_editor_remove_word_timings)).performClick()
        compose.waitUntil(5_000) { !model.editor.state.value.busy && model.editor.state.value.rowId == null }
        val edited = model.editor.state.value.source!!
        assertTrue(edited.lines.first { it.id == row.id }.segments.isEmpty())
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
