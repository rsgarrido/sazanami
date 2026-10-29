package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.ActiveLyricGroup
import io.github.rsgarrido.sazanami.lyrics.LyricCue
import io.github.rsgarrido.sazanami.lyrics.LyricCueContent
import io.github.rsgarrido.sazanami.lyrics.LyricsDocument
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.ui.MusicTransientOverlays
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsListTag
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsScreen
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsScreenTag
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class PocketAdvanceOverlayAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val song = Song(1, "Overlay song", "Overlay artist", "Album", 1, 180_000,
        Uri.EMPTY, "", "", null)

    @Test
    fun changingTheLcdUpdatesSyncedLyricsWithoutReplacingItsControllerSelectionOrViewport() {
        val lcd = mutableStateOf(PocketAdvanceLcdPalette.CLASSIC_GREEN)
        val document = LyricsDocument.Synced((0..29).map { LyricCue(it * 1_000L, LyricCueContent.Text("Line $it")) })
        val state = LyricsPlaybackUiState.Synced(song, document, ActiveLyricGroup(15_000, listOf("Line 15")), false)
        lateinit var controller: PlayerLyricsTransitionState
        val seeks = mutableListOf<Int>()
        var returns = 0
        compose.setContent {
            val palette = remember(lcd.value) { PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd.value)) }
            controller = rememberPlayerLyricsTransitionState(initiallyLyricsVisible = true, onCompositionVisibilityChanged = {})
            MaterialTheme {
                CompositionLocalProvider(LocalPocketAdvancePalette provides palette) {
                    PocketAdvanceOverlayTheme(active = true, inverse = true) {
                        LyricsScreen(state, false, controller, interactive = true,
                            onBack = {}, onPlayPause = {}, onSeek = { seeks += it },
                            onSuspendAutoFollow = {}, onReturnToCurrentLine = { returns++ },
                            onRescan = {}, onOpenSettings = {}, colors = pocketAdvanceLyricsColors(palette, true))
                    }
                }
            }
        }
        compose.onNodeWithTag(LyricsListTag).performScrollToIndex(15)
        val selected = compose.onNodeWithText("Line 15").assertIsSelected()
        val bounds = selected.fetchSemanticsNode().boundsInRoot
        val originalController = controller
        val green = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd.value))
        assertContainsColor(selected, green.Screen)
        assertContainsColor(compose.onNodeWithTag(LyricsScreenTag), green.Ink)
        compose.runOnIdle { lcd.value = PocketAdvanceLcdPalette.WARM_AMBER }
        val amber = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = PocketAdvanceLcdPalette.WARM_AMBER))
        compose.onNodeWithText("Line 15").assertIsSelected()
        assertContainsColor(selected, amber.Screen)
        assertContainsColor(compose.onNodeWithTag(LyricsScreenTag), amber.Ink)
        val restoredBounds = selected.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertSame(originalController, controller)
            assertEquals(bounds, restoredBounds)
            assertTrue(seeks.isEmpty()); assertEquals(0, returns)
        }
        selected.performClick()
        compose.runOnIdle { assertEquals(listOf(15_000), seeks); assertEquals(1, returns) }
    }

    @Test
    fun queueOverlayUsesItsLcdAndReturnsToInheritedColorsWhenAdvanceIsInactive() {
        val lcd = mutableStateOf(PocketAdvanceLcdPalette.CLASSIC_GREEN)
        val identity = mutableStateOf<PlayerPresentationIdentity>(PlayerPresentationIdentity.PocketAdvance)
        val inherited = darkColorScheme()
        var dismissals = 0
        var actions = 0
        compose.setContent {
            val palette = remember(lcd.value) { PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd.value)) }
            MaterialTheme(colorScheme = inherited) {
                CompositionLocalProvider(LocalPocketAdvancePalette provides palette) {
                    MusicTransientOverlays(nowPlayingMoreTarget = null, favoriteMembershipKeys = emptySet(), songs = emptyList(),
                        onDismissNowPlayingMore = {}, onNowPlayingMoreAction = { _, _ -> },
                        isExpandedUpNextSheetVisible = false, queuedSongs = emptyList(), upcomingSongs = emptyList(),
                        isShuffleEnabled = false, onDismissExpandedUpNextSheet = {}, onRemoveFromQueueClick = {},
                        onMoveQueueItemUpClick = {}, onMoveQueueItemDownClick = {}, onClearQueueClick = {},
                        isQueueHubVisible = true, playbackQueueHubUiState = PlaybackQueueHubUiState(isLoading = false),
                        onDismissQueueHub = { dismissals++ }, onPlaybackQueueSelected = { actions++ },
                        onSwitchSelectedPlaybackQueue = { actions++ }, onCreatePlaybackQueueFromCurrent = { actions++ },
                        onRenamePlaybackQueue = { _, _ -> actions++ }, onDeletePlaybackQueue = { actions++ },
                        onRemovePlaybackQueueEntry = { _, _ -> actions++ }, onPlayPlaybackQueueEntry = { _, _ -> actions++ },
                        onUndoPlaybackQueueEntryRemoval = {}, onClearPlaybackQueueEntryRemovalUndo = {},
                        onReorderPlaybackQueueEntry = { _, _, _ -> actions++ }, onClearPlaybackQueueMessage = {},
                        isCreatePlaylistDialogVisible = false, createPlaylistFolderId = null, playlists = emptyList(),
                        onDismissCreatePlaylistDialog = {}, onCreatePlaylistClick = { _, _ -> }, onCreatePlaylistWithSongsClick = { _, _ -> },
                        songPendingPlaylistAdd = null, onDismissAddToPlaylistDialog = {}, onAddSongToPlaylistClick = { _, _ -> },
                        songsPendingPlaylistAdd = emptyList(), onDismissBulkAddToPlaylistDialog = {}, onAddSongsToPlaylistClick = { _, _ -> },
                        isSleepTimerDialogVisible = false, isSleepTimerActive = false, sleepTimerDisplayText = "",
                        onStartSleepTimerClick = {}, onCancelSleepTimerClick = {}, onDismissSleepTimerDialog = {},
                        playerPresentation = identity.value)
                }
            }
        }
        val title = compose.onNodeWithText(compose.activity.getString(R.string.queue_hub_title))
        for (choice in listOf(PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceLcdPalette.WARM_AMBER)) {
            compose.runOnIdle { lcd.value = choice }
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = choice))
            assertContainsColor(title, palette.Ink)
            assertContainsColor(title, palette.Screen)
        }
        compose.runOnIdle { identity.value = PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL) }
        assertContainsColor(title, inherited.onSurface)
        assertContainsColor(title, inherited.surfaceContainer)
        compose.runOnIdle { assertEquals(0, actions); assertEquals(0, dismissals) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.queue_hub_close)).performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, actions) }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun assertContainsColor(node: SemanticsNodeInteraction, expected: Color) {
        val pixels = node.captureToImage().toPixelMap()
        var closest = Float.MAX_VALUE
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val actual = pixels[x, y]
            val distance = abs(actual.red - expected.red) + abs(actual.green - expected.green) + abs(actual.blue - expected.blue)
            closest = minOf(closest, distance)
        }
        assertTrue("Rendered pixels must contain $expected; nearest RGB distance was $closest", closest < .04f)
    }
}
