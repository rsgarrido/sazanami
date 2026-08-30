package io.github.rsgarrido.sazanami.ui.lyrics

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.ActiveLyricGroup
import io.github.rsgarrido.sazanami.lyrics.LyricCue
import io.github.rsgarrido.sazanami.lyrics.LyricCueContent
import io.github.rsgarrido.sazanami.lyrics.LyricsDocument
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.lyrics.LyricsUnavailableReason
import io.github.rsgarrido.sazanami.lyrics.StaticLyricLine
import io.github.rsgarrido.sazanami.ui.blockPlayerInput
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerSurfaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LyricsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingStateRenders() {
        setContent(LyricsPlaybackUiState.Loading(song()))
        composeRule.onNodeWithText("Loading local lyrics…").assertExists()

    }

    @Test
    fun permissionStateRenders() {
        setContent(
            LyricsPlaybackUiState.Unavailable(
                song(),
                LyricsUnavailableReason.PermissionLost("content://root")
            )
        )
        composeRule.onNodeWithText("Lyrics folder access unavailable").assertExists()
    }

    @Test
    fun syncedLyricsRenderActiveSemanticsAndTapSeeks() {
        var seekPosition = -1
        val document = LyricsDocument.Synced(
            listOf(
                LyricCue(1_000, LyricCueContent.Text("First line")),
                LyricCue(2_000, LyricCueContent.Text("Second line"))
            )
        )
        setContent(
            LyricsPlaybackUiState.Synced(
                song = song(),
                lyrics = document,
                activeGroup = ActiveLyricGroup(1_000, listOf("First line")),
                autoFollowEnabled = true
            ),
            onSeek = { seekPosition = it }
        )

        composeRule.onNodeWithText("First line")
            .assertIsSelected()
            .assertHasClickAction()
        composeRule.onNodeWithText("Second line").performClick()
        composeRule.onNodeWithText("Second line").assertIsSelected()
        composeRule.runOnIdle { assertEquals(2_000, seekPosition) }
    }

    @Test
    fun unsyncedLyricsRenderWithoutSeekAction() {
        var seekCalled = false
        setContent(
            LyricsPlaybackUiState.Unsynced(
                song(),
                LyricsDocument.Unsynced(listOf(StaticLyricLine("Static lyric")))
            ),
            onSeek = { seekCalled = true }
        )

        composeRule.onNodeWithText("Unsynced lyrics").assertExists()
        composeRule.onNodeWithText("Static lyric").assertHasNoClickAction()
        composeRule.runOnIdle { assertTrue(!seekCalled) }
    }

    @Test
    fun manualModeShowsReturnAction() {
        var returned = false
        setContent(
            LyricsPlaybackUiState.Synced(
                song(),
                LyricsDocument.Synced(
                    listOf(LyricCue(1_000, LyricCueContent.Text("Line")))
                ),
                ActiveLyricGroup(1_000, listOf("Line")),
                autoFollowEnabled = false
            ),
            onReturn = { returned = true }
        )

        composeRule.onNodeWithTag(LyricsReturnTag).performClick()
        composeRule.runOnIdle { assertTrue(returned) }
    }

    @Test
    fun returnToCurrentLineAnchorsMeasuredRowNearFortyTwoPercent() {
        val cues = (0 until 20).map { index ->
            LyricCue(
                timestampMs = index * 1_000L,
                content = LyricCueContent.Text(
                    if (index == 12) "Anchor target" else "Lyric line $index"
                )
            )
        }
        setContent(
            LyricsPlaybackUiState.Synced(
                song(),
                LyricsDocument.Synced(cues),
                ActiveLyricGroup(12_000, listOf("Anchor target")),
                autoFollowEnabled = false
            )
        )

        composeRule.onNodeWithTag(LyricsReturnTag).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Anchor target")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitForIdle()

        val listBounds = composeRule.onNodeWithTag(LyricsListTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val itemBounds = composeRule.onNodeWithText("Anchor target")
            .fetchSemanticsNode()
            .boundsInRoot
        val desiredCenter = listBounds.top + listBounds.height * 0.42f
        val actualCenter = itemBounds.top + itemBounds.height / 2f
        val tolerancePx = 16f * composeRule.activity.resources.displayMetrics.density
        assertTrue(
            "Expected active center $actualCenter near anchor $desiredCenter",
            abs(actualCenter - desiredCenter) <= tolerancePx
        )
    }

    @Test
    fun headerBackAndPlayPauseCallbacksWork() {
        var back = false
        var toggled = false
        setContent(
            LyricsPlaybackUiState.Loading(song()),
            onBack = { back = true },
            onPlayPause = { toggled = true }
        )

        composeRule.onNodeWithTag(LyricsPlayPauseTag).performClick()
        composeRule.onNodeWithTag(LyricsBackTag).performClick()
        composeRule.runOnIdle {
            assertTrue(back)
            assertTrue(toggled)
        }
    }

    @Test
    fun noMatchShowsMultipleDeterministicNames() {
        setContent(
            LyricsPlaybackUiState.Unavailable(
                song(),
                LyricsUnavailableReason.NotFound
            )
        )

        composeRule.onNodeWithText("No local lyrics found").assertExists()
        composeRule.onNodeWithText("track.lrc", substring = true).assertExists()
        composeRule.onNodeWithText("Song.lrc", substring = true).assertExists()
        composeRule.onNodeWithText("Artist - Song.lrc", substring = true).assertExists()
    }

    @Test
    fun notFoundStateBlocksUnderlyingInputAndKeepsActionsClickable() {
        var underlyingClicks = 0
        var rescans = 0
        var settingsOpens = 0
        setModalContent(
            state = LyricsPlaybackUiState.Unavailable(
                song(),
                LyricsUnavailableReason.NotFound
            ),
            onUnderlyingClick = { underlyingClicks++ },
            onRescan = { rescans++ },
            onOpenSettings = { settingsOpens++ }
        )

        tapUnderlyingControl()
        composeRule.onNodeWithText("Rescan").performClick()
        composeRule.onNodeWithText("Local Lyrics settings").performClick()

        composeRule.runOnIdle {
            assertEquals(0, underlyingClicks)
            assertEquals(1, rescans)
            assertEquals(1, settingsOpens)
        }
    }

    @Test
    fun loadingStateBlocksUnderlyingInput() {
        assertSparseStateBlocksUnderlying(LyricsPlaybackUiState.Loading(song()))
    }

    @Test
    fun permissionLossStateBlocksUnderlyingInput() {
        assertSparseStateBlocksUnderlying(
            LyricsPlaybackUiState.Unavailable(
                song(),
                LyricsUnavailableReason.PermissionLost("content://root")
            )
        )
    }

    @Test
    fun invalidLyricsStateBlocksUnderlyingInput() {
        assertSparseStateBlocksUnderlying(
            LyricsPlaybackUiState.Unavailable(
                song(),
                LyricsUnavailableReason.InvalidLyrics("content://lyrics")
            )
        )
    }

    @Test
    fun shortUnsyncedLyricsBlockUnderlyingInputOutsideText() {
        assertSparseStateBlocksUnderlying(
            LyricsPlaybackUiState.Unsynced(
                song(),
                LyricsDocument.Unsynced(listOf(StaticLyricLine("Short lyric")))
            )
        )
    }

    @Test
    fun syncedLyricsContinueBlockingUnderlyingInput() {
        assertSparseStateBlocksUnderlying(
            LyricsPlaybackUiState.Synced(
                song(),
                LyricsDocument.Synced(
                    listOf(LyricCue(1_000, LyricCueContent.Text("Timed lyric")))
                ),
                ActiveLyricGroup(1_000, listOf("Timed lyric")),
                autoFollowEnabled = true
            )
        )
    }

    @Test
    fun blockedPlayerSemanticsAreRemovedAndRestored() {
        val blocked = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { }
                        .blockPlayerInput(blocked.value)
                ) {
                    androidx.compose.material3.Text("Underlying player action")
                }
            }
        }

        composeRule.onAllNodesWithText("Underlying player action").assertCountEquals(0)
        composeRule.runOnIdle { blocked.value = false }
        composeRule.onNodeWithText("Underlying player action").assertExists()
    }

    @Test
    fun headerDownwardDragSettlesAtExpanded() {
        lateinit var transitionState: PlayerLyricsTransitionState
        composeRule.setContent {
            MaterialTheme {
                transitionState = rememberPlayerLyricsTransitionState(true) {}
                LyricsScreen(
                    state = LyricsPlaybackUiState.Loading(song()),
                    isPlaying = false,
                    transitionState = transitionState,
                    interactive = true,
                    onBack = {},
                    onPlayPause = {},
                    onSeek = {},
                    onSuspendAutoFollow = {},
                    onReturnToCurrentLine = {},
                    onRescan = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag(LyricsHeaderTag).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 1_000f),
                durationMillis = 700
            )
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnIdle {
            assertEquals(PlayerSurfaceState.EXPANDED, transitionState.settledSurface)
            assertEquals(0f, transitionState.progress, 0.01f)
        }
    }

    private fun setContent(
        state: LyricsPlaybackUiState,
        onBack: () -> Unit = {},
        onPlayPause: () -> Unit = {},
        onSeek: (Int) -> Unit = {},
        onReturn: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                val transitionState = rememberPlayerLyricsTransitionState(true) {}
                LyricsScreen(
                    state = state,
                    isPlaying = false,
                    transitionState = transitionState,
                    interactive = true,
                    onBack = onBack,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSuspendAutoFollow = {},
                    onReturnToCurrentLine = onReturn,
                    onRescan = {},
                    onOpenSettings = {}
                )
            }
        }
    }

    private fun assertSparseStateBlocksUnderlying(state: LyricsPlaybackUiState) {
        var underlyingClicks = 0
        setModalContent(
            state = state,
            onUnderlyingClick = { underlyingClicks++ }
        )

        tapUnderlyingControl()
        composeRule.runOnIdle { assertEquals(0, underlyingClicks) }
    }

    private fun setModalContent(
        state: LyricsPlaybackUiState,
        onUnderlyingClick: () -> Unit,
        onRescan: () -> Unit = {},
        onOpenSettings: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onUnderlyingClick)
                            .testTag(UnderlyingPlayerTag)
                    )
                    val transitionState = rememberPlayerLyricsTransitionState(true) {}
                    LyricsScreen(
                        state = state,
                        isPlaying = false,
                        transitionState = transitionState,
                        interactive = true,
                        onBack = {},
                        onPlayPause = {},
                        onSeek = {},
                        onSuspendAutoFollow = {},
                        onReturnToCurrentLine = {},
                        onRescan = onRescan,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }
    }

    private fun tapUnderlyingControl() {
        composeRule.onNodeWithTag(UnderlyingPlayerTag).performTouchInput {
            down(bottomLeft + Offset(24f, -24f))
            up()
        }
    }

    private fun song() = Song(
        id = 1,
        title = "Song",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 10_000,
        uri = Uri.parse("content://song"),
        filePath = "/Music/track.flac",
        folderPath = "/Music",
        albumArtUri = null,
        displayName = "track.flac",
        relativePath = "Music/"
    )

    private companion object {
        const val UnderlyingPlayerTag = "underlying_player_control"
    }
}
