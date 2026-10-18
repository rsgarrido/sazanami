package io.github.rsgarrido.sazanami.ui.lyrics

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.ActiveLyricGroup
import io.github.rsgarrido.sazanami.lyrics.LyricCue
import io.github.rsgarrido.sazanami.lyrics.LyricCueContent
import io.github.rsgarrido.sazanami.lyrics.LyricsDocument
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.lyrics.LyricsUnavailableReason
import io.github.rsgarrido.sazanami.lyrics.StaticLyricLine
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in logical windows fit on phone test devices without changing production sizing policy. */
class LyricsAdaptiveLayoutTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readingWidthChangesKeepTheFullSurfaceActiveLineScrollPositionAndActionSinks() {
        val fixture = Fixture(syncedState())
        composeRule.setContent { fixture.Content() }
        val transition = fixture.transition
        assertReadingWidth(840.dp)
        val surface = bounds(LyricsScreenTag)
        assertEquals(1200f * TestDensity, surface.width, 1f)
        assertEquals(surface.width, bounds(LyricsHeaderTag).width, 1f)

        composeRule.onNodeWithTag(LyricsListTag).performScrollToIndex(8)
        composeRule.onNodeWithText(line(8)).assertIsSelected()
        val scroll = scrollPosition()
        assertTrue(scroll > 0f)
        for (width in listOf(412.dp, 1400.dp, 600.dp)) {
            composeRule.runOnIdle { fixture.viewport.value = DpSize(width, 720.dp) }
            assertReadingWidth(minOf(width, 840.dp))
            composeRule.onNodeWithText(line(8)).assertIsSelected()
            assertEquals(scroll, scrollPosition(), 0.01f)
            composeRule.runOnIdle {
                assertSame(transition, fixture.transition)
                assertEquals(8_000L, (fixture.state.value as LyricsPlaybackUiState.Synced)
                    .activeGroup!!.timestampMs)
                assertTrue(fixture.calls.isEmpty())
                assertTrue(fixture.seeks.isEmpty())
            }
        }

        composeRule.onNodeWithText(line(9)).performClick()
        composeRule.onNodeWithText(line(9)).assertIsSelected()
        composeRule.onNodeWithTag(LyricsPlayPauseTag).performClick()
        composeRule.onNodeWithTag(LyricsBackTag).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(9_000), fixture.seeks)
            assertEquals(listOf("return", "play-pause", "back"), fixture.calls)
        }
    }

    @Test
    fun plainLyricsUseFontScaledReadableWidthAndFitNarrowWindowsWithoutSeekActions() {
        val text = "静かな海 — A long multilingual lyric remains readable across window sizes. ".repeat(4)
        val fixture = Fixture(LyricsPlaybackUiState.Unsynced(song,
            LyricsDocument.Unsynced(listOf(StaticLyricLine(text))))).apply {
            viewport.value = DpSize(1600.dp, 720.dp)
            fontScale.value = 1.5f
        }
        composeRule.setContent { fixture.Content() }
        assertReadingWidth(1260.dp)
        assertEquals(1600f * TestDensity, bounds(LyricsScreenTag).width, 1f)
        composeRule.onNodeWithText(text).assertHasNoClickAction()

        composeRule.runOnIdle { fixture.viewport.value = DpSize(360.dp, 720.dp) }
        assertReadingWidth(360.dp)
        composeRule.onNodeWithText(text).assertHasNoClickAction()
        composeRule.runOnIdle {
            assertTrue(fixture.seeks.isEmpty())
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun unavailableActionsScrollAndWrapInAShortLargeTextWindowWhileHeaderStaysReachable() {
        val fixture = Fixture(LyricsPlaybackUiState.Unavailable(song, LyricsUnavailableReason.NotFound))
            .apply {
                viewport.value = DpSize(360.dp, 260.dp)
                fontScale.value = 1.6f
            }
        composeRule.setContent { fixture.Content() }
        val rescan = composeRule.activity.getString(R.string.lyrics_rescan)
        val settings = composeRule.activity.getString(R.string.lyrics_settings)
        composeRule.onNodeWithText(rescan).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText(settings).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(LyricsBackTag).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("rescan", "settings", "back"), fixture.calls)
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun loadingLyricsKeepFullWindowInputCoverageOutsideTheBoundedReadingPane() {
        val fixture = Fixture(LyricsPlaybackUiState.Loading(song))
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag("lyrics_underlying_player").assertExists()
        composeRule.onNodeWithTag(LyricsScreenTag).performTouchInput {
            // The reading pane begins 45 physical pixels from this 300-pixel host's left edge.
            click(Offset(8f, center.y))
        }
        composeRule.runOnIdle {
            assertEquals(0, fixture.underlyingClicks)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
        assertEquals(1200f * TestDensity, bounds(LyricsScreenTag).width, 1f)
    }

    private fun assertReadingWidth(expected: Dp) {
        val list = bounds(LyricsListTag)
        val surface = bounds(LyricsScreenTag)
        assertEquals(expected.value * TestDensity, list.width, 1f)
        assertEquals(surface.center.x, list.center.x, 1f)
    }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun scrollPosition(): Float = composeRule.onNodeWithTag(LyricsListTag)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private class Fixture(initial: LyricsPlaybackUiState) {
        val state = mutableStateOf(initial)
        val viewport = mutableStateOf(DpSize(1200.dp, 720.dp))
        val fontScale = mutableStateOf(1f)
        val calls = mutableListOf<String>()
        val seeks = mutableListOf<Int>()
        var underlyingClicks = 0
        lateinit var transition: PlayerLyricsTransitionState

        @Composable
        fun Content() {
            CompositionLocalProvider(LocalDensity provides Density(TestDensity, fontScale.value)) {
                MaterialTheme {
                    transition = rememberPlayerLyricsTransitionState(true) {}
                    Box(Modifier.requiredSize(viewport.value.width, viewport.value.height)
                        .consumeWindowInsets(WindowInsets.safeDrawing)) {
                        Box(Modifier.fillMaxSize().clickable { underlyingClicks++ }
                            .testTag("lyrics_underlying_player"))
                        LyricsScreen(
                            state = state.value,
                            isPlaying = false,
                            transitionState = transition,
                            interactive = true,
                            onBack = { calls += "back" },
                            onPlayPause = { calls += "play-pause" },
                            onSeek = { seeks += it },
                            onSuspendAutoFollow = { calls += "suspend" },
                            onReturnToCurrentLine = { calls += "return" },
                            onRescan = { calls += "rescan" },
                            onOpenSettings = { calls += "settings" }
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TestDensity = 0.25f
        val song = Song(
            id = 1, title = "Song", artist = "Artist", album = "Album", trackNumber = 1,
            duration = 180_000, uri = Uri.parse("content://song"),
            filePath = "/Music/track.flac", folderPath = "/Music", albumArtUri = null,
            displayName = "track.flac", relativePath = "Music/"
        )

        fun line(index: Int) = "Timed line $index — 行 $index"

        fun syncedState(): LyricsPlaybackUiState.Synced {
            val cues = (0 until 30).map { index ->
                LyricCue(index * 1000L, LyricCueContent.Text(line(index)))
            }
            return LyricsPlaybackUiState.Synced(song, LyricsDocument.Synced(cues),
                ActiveLyricGroup(8_000, listOf(line(8))), autoFollowEnabled = false)
        }
    }
}
