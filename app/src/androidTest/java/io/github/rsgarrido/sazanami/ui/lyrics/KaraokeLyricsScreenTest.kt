package io.github.rsgarrido.sazanami.ui.lyrics

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.*
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class KaraokeLyricsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val parser = LrcParser()
    private val song = Song(901, "Original karaoke fixture", "Synthetic author", "Test", 1, 200_000,
        Uri.parse("content://synthetic/audio/901"), "", "", null, volumeName = "primary", displayName = "fixture.flac")
    private val playback = MutableStateFlow(PlaybackUiState(currentSong = song))
    private val progress = MutableStateFlow(PlaybackProgressUiState(isDirect = true))
    private lateinit var controller: LyricsPlaybackController
    private lateinit var sourced: SourcedLyrics
    private var manualScrollEvents = 0
    private val seeks = mutableListOf<Int>()
    @After fun close() { scope.cancel() }

    private fun mount(raw: String, position: Int = 1000, largeText: Boolean = false) {
        val source = parser.parseSource(raw)
        sourced = SourcedLyrics(parser.project(source), LyricsSource.LocalSidecar("content://synthetic/lyrics", "fixture.lrc"), source)
        val repository = object : LocalLyricsRepository {
            override val roots = MutableStateFlow(emptyList<LyricsRoot>())
            override suspend fun findLyrics(song: SongLyricsIdentity) = LyricsLookupResult.Found(sourced)
            override suspend fun loadCachedIndexSummary(): LyricsIndexSummary? = null
            override suspend fun addRoot(root: LyricsRoot): LyricsIndexResult = error("unused")
            override suspend fun removeRoot(rootUri: String): LyricsIndexResult = error("unused")
            override suspend fun refreshIndex() = LyricsIndexResult(LyricsIndexSnapshot(emptyList(), emptySet(), generatedAtEpochMs = 0))
        }
        compose.runOnUiThread {
            progress.value = progress.value.copy(currentPosition = position)
            controller = LyricsPlaybackController(repository, playback, LyricsPositionSource { error("Direct must use confirmed progress") },
                scope, authoritativeProgress = progress)
            controller.setVisible(true)
        }
        compose.setContent {
            val state by controller.uiState.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        LyricsScreen(state, false, rememberPlayerLyricsTransitionState(true) {}, interactive = true,
                            onBack = {}, onPlayPause = {}, onSeek = { seeks += it; controller.onSeek(it.toLong()) },
                            onSuspendAutoFollow = { manualScrollEvents++; controller.suspendAutoFollow() },
                            onReturnToCurrentLine = controller::returnToCurrentLine, onRescan = controller::rescan, onOpenSettings = {})
                    }
                }
            }
        }
        compose.waitUntil(5_000) { controller.uiState.value is LyricsPlaybackUiState.Synced || controller.uiState.value is LyricsPlaybackUiState.Unsynced }
    }

    private fun advance(position: Int, seeking: Boolean = false) {
        compose.runOnUiThread { progress.value = progress.value.copy(currentPosition = position, isSeeking = seeking) }
        compose.waitForIdle()
    }

    private fun rendered(text: String): AnnotatedString {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        return results.single().layoutInput.text
    }

    private fun AnnotatedString.style(start: Int, end: Int): SpanStyle =
        spanStyles.last { it.start == start && it.end == end }.item

    @Test fun enhancedWordsStepWithoutChangingTextAccessibilityOrLinePosition() {
        mount("[00:01]<00:01>Stay <00:01.4>with <00:01.75>me\n[00:05]Next")
        val text = "Stay with me"
        val first = rendered(text)
        assertEquals(TextDecoration.Underline, first.style(0, 5).textDecoration)
        assertEquals(FontWeight.SemiBold, first.style(0, 5).fontWeight)
        assertEquals(FontWeight.SemiBold, first.style(5, 10).fontWeight)
        assertEquals(TextDecoration.None, first.style(5, 10).textDecoration)
        val top = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        advance(1400)
        val middle = rendered(text)
        assertEquals(text, middle.text)
        assertEquals(FontWeight.SemiBold, middle.style(0, 5).fontWeight)
        assertEquals(TextDecoration.Underline, middle.style(5, 10).textDecoration)
        assertEquals(TextDecoration.None, middle.style(10, 12).textDecoration)
        assertNotEquals(middle.style(0, 5).color, middle.style(5, 10).color)
        assertNotEquals(middle.style(0, 5).color, middle.style(10, 12).color)
        assertEquals(top, compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top, 1f)
        advance(1750)
        assertEquals(TextDecoration.Underline, rendered(text).style(10, 12).textDecoration)
        advance(1100)
        assertEquals(TextDecoration.Underline, rendered(text).style(0, 5).textDecoration)
        val node = compose.onNodeWithText(text).assertIsSelected().assertHasClickAction().fetchSemanticsNode()
        assertEquals(listOf(AnnotatedString(text)), node.config[SemanticsProperties.Text])
        assertFalse(node.config.contains(SemanticsProperties.LiveRegion))
        compose.onAllNodesWithText(text).assertCountEquals(1)
        assertFalse(rendered(text).text.contains('<'))
        advance(5000)
        compose.onNodeWithText("Next").assertIsSelected()
        assertTrue(rendered(text).spanStyles.isEmpty())
    }

    @Test fun standardPlainAndMalformedLyricsKeepTheirExistingPresentation() {
        mount("[00:01]Standard\n[00:02]<00:03>Decreasing <00:02>timing\n[00:04]")
        compose.onNodeWithText("Standard").assertIsSelected()
        assertTrue(rendered("Standard").spanStyles.isEmpty())
        advance(2000)
        compose.onNodeWithText("Decreasing timing").assertIsSelected()
        assertTrue(rendered("Decreasing timing").spanStyles.isEmpty())
        advance(4000)
        assertNull((controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup)
        assertTrue((controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value.isEmpty())
        replaceSource("Plain original text", LyricsSource.LocalOverride("plain"))
        compose.onNodeWithText("Plain original text").assertHasNoClickAction().assertIsDisplayed()
    }

    private fun replaceSource(raw: String, origin: LyricsSource) {
        compose.runOnUiThread {
            val source = parser.parseSource(raw)
            sourced = SourcedLyrics(parser.project(source), origin, source)
            controller.rescan()
        }
        compose.waitUntil(5_000) {
            when (val state = controller.uiState.value) {
                is LyricsPlaybackUiState.Synced -> state.sourcedLyrics == sourced
                is LyricsPlaybackUiState.Unsynced -> state.sourcedLyrics == sourced
                else -> false
            }
        }
    }

    @Test fun allResolvedSourceKindsUseTheSameKaraokeAndOffsetPath() {
        val raw = "[offset:250]\n[00:01]<00:01>Luz <00:02>azul"
        mount(raw, position = 1250)
        val origins = listOf(LyricsSource.LocalSidecar("content://external", "fixture.lrc"),
            LyricsSource.Embedded(song.toLyricsIdentity().trackKey, "USLT"), LyricsSource.LocalOverride("saved"))
        origins.forEach { origin ->
            replaceSource(raw, origin)
            assertEquals(origin, (controller.uiState.value as LyricsPlaybackUiState.Synced).sourcedLyrics?.source)
            assertEquals(TextDecoration.Underline, rendered("Luz azul").style(0, 4).textDecoration)
            advance(2250)
            assertEquals(TextDecoration.Underline, rendered("Luz azul").style(4, 8).textDecoration)
            advance(1250)
        }
    }

    @Test fun multilingualLargeTextWrapsAsOneReadableLineWithExactRanges() {
        val first = "¡Luz! e\u0301 🙂 日本語 한국어 中文، "
        val last = "窓辺の光 🌊 ".repeat(12)
        val text = first + last
        mount("[00:01]<00:01>$first<00:02>$last", largeText = true)
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.single().lineCount > 1)
        assertEquals(text, results.single().layoutInput.text.text)
        assertEquals(TextDecoration.Underline, results.single().layoutInput.text.style(0, first.length).textDecoration)
        advance(2000)
        assertEquals(text, rendered(text).text)
        assertEquals(TextDecoration.Underline, rendered(text).style(first.length, text.length).textDecoration)
        compose.onAllNodesWithText(text).assertCountEquals(1)
    }

    @Test fun simultaneousEnhancedAndStandardLinesKeepNewlinesAndIndependentRanges() {
        mount("[00:01]<00:01>Uno <00:02>dos\n[00:01]<00:01.5>日本語🙂\n[00:01]Standard")
        val text = "Uno dos\n日本語🙂\nStandard"
        val initial = rendered(text)
        assertEquals(text, initial.text)
        assertEquals(TextDecoration.Underline, initial.style(0, 4).textDecoration)
        assertEquals(TextDecoration.None, initial.style(8, 13).textDecoration)
        assertFalse(initial.spanStyles.any { it.start >= 14 })
        advance(1500)
        val both = rendered(text)
        assertEquals(text, both.text)
        assertEquals(TextDecoration.Underline, both.style(0, 4).textDecoration)
        assertEquals(TextDecoration.Underline, both.style(8, 13).textDecoration)
        compose.onAllNodesWithText(text).assertCountEquals(1)
    }

    @Test fun pendingRejectedAndConfirmedDirectLineSeeksNeverUseRequestedHighlight() {
        mount("[00:01]<00:01>First <00:01.5>line\n[00:05]<00:05>Second <00:05.5>line")
        compose.onNodeWithText("Second line").performClick()
        assertEquals(listOf(5000), seeks)
        compose.onNodeWithText("First line").assertIsSelected()
        compose.onNodeWithText("Second line").assertIsNotSelected()
        assertEquals(TextDecoration.Underline, rendered("First line").style(0, 6).textDecoration)
        advance(5500, seeking = true)
        compose.onNodeWithText("First line").assertIsSelected()
        assertTrue(rendered("Second line").spanStyles.isEmpty())
        advance(1000) // Failed seek keeps the original position and word.
        compose.onNodeWithText("First line").assertIsSelected()
        advance(5500)
        compose.onNodeWithText("Second line").assertIsSelected()
        assertEquals(TextDecoration.Underline, rendered("Second line").style(7, 11).textDecoration)
        advance(1000)
        compose.onNodeWithText("First line").assertIsSelected()
    }

    @Test fun returnDismissesImmediatelyOnStationaryCueAndProgrammaticScrollKeepsFollowing() {
        val raw = (0 until 24).joinToString("\n") { index ->
            "[${LyricsDocumentEditor.formatTime(index * 5000L)}]Original line $index"
        }
        mount(raw, position = 60_000) // Paused, deliberately unchanged throughout both Return actions.
        val active = (controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup
        fun manualScroll() {
            compose.onNodeWithTag(LyricsListTag).performTouchInput {
                swipeUp(durationMillis = 500)
            }
        }
        manualScroll()
        compose.onNodeWithTag(LyricsReturnTag).assertIsDisplayed()
        assertFalse((controller.uiState.value as LyricsPlaybackUiState.Synced).autoFollowEnabled)
        val events = manualScrollEvents
        compose.onNodeWithTag(LyricsReturnTag).performClick()
        compose.onNodeWithTag(LyricsReturnTag).assertDoesNotExist()
        assertTrue((controller.uiState.value as LyricsPlaybackUiState.Synced).autoFollowEnabled)
        assertEquals(active, (controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup)
        compose.onNodeWithText("Original line 12").assertIsDisplayed().assertIsSelected()
        assertEquals(events, manualScrollEvents)
        val list = compose.onNodeWithTag(LyricsListTag).fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithText("Original line 12").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(line.center.y - (list.top + list.height * 0.42f)) <= 16f * compose.activity.resources.displayMetrics.density)
        manualScroll()
        compose.onNodeWithTag(LyricsReturnTag).assertIsDisplayed()
        assertTrue(manualScrollEvents > events)
        compose.onNodeWithTag(LyricsReturnTag).performClick()
        compose.onNodeWithTag(LyricsReturnTag).assertDoesNotExist()
        advance(65_000)
        compose.onNodeWithText("Original line 13").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag(LyricsReturnTag).assertDoesNotExist()
    }
}
