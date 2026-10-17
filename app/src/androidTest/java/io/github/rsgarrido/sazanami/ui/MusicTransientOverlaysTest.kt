package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.player.NowPlayingMoreDialogTag
import io.github.rsgarrido.sazanami.ui.player.rememberNowPlayingMoreActionDispatcher
import io.github.rsgarrido.sazanami.ui.player.rememberNowPlayingWorkflowOpeners
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.ratings.SongRatingUiEnvironment
import io.github.rsgarrido.sazanami.ui.state.SLEEP_TIMER_OPTIONS_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicTransientOverlaysTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun moreMountsForItsCapturedTargetAndBackRestoresUnderlyingInput() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.runOnIdle { fixture.overlays.openNowPlayingMore(fixture.first) }
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertExists()
        composeRule.onNodeWithTag(PlayerActionTag).assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerActionTag).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("more-dismiss"), fixture.calls)
            assertEquals(1, fixture.playerClicks)
            assertNull(fixture.overlays.nowPlayingMoreTarget)
        }
    }

    @Test
    fun moreReplacesItselfWithTheExistingPickerAndKeepsTheSelectedSong() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle { fixture.overlays.openNowPlayingMore(fixture.first) }
        composeRule.onNodeWithText(text(R.string.library_song_add_to_playlist)).performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.playlist_add_to_title)).assertExists()
        composeRule.runOnIdle {
            assertSame(fixture.first, fixture.singleTarget.value)
            fixture.current.value = fixture.second
        }
        composeRule.onNodeWithText(fixture.playlists.value.single().name).performClick()
        composeRule.runOnIdle {
            assertSame(fixture.first, fixture.added.single().songs.single())
            assertNull(fixture.singleTarget.value)
            assertEquals(listOf("playlist-open", "add-single", "single-dismiss"), fixture.calls)
        }
    }

    @Test
    fun moreSleepTimerReplacementDismissesMoreBeforeTheTimerOpens() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle { fixture.overlays.openNowPlayingMore(fixture.first) }
        composeRule.onNodeWithContentDescription(text(R.string.sleep_timer_title)).performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.sleep_timer_title)).assertExists()
        composeRule.runOnIdle {
            assertEquals(listOf("timer-open"), fixture.calls)
            assertTrue(fixture.overlays.isSleepTimerDialogVisible.value)
            assertNull(fixture.overlays.nowPlayingMoreTarget)
        }
    }

    @Test
    fun upNextUsesTheExistingQueueAndDismissalCallbacks() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle { fixture.overlays.isExpandedUpNextSheetVisible.value = true }
        composeRule.onNodeWithText(fixture.second.title).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.queue_remove_named, fixture.second.title)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.queue_back)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("remove-index:0", "up-next-dismiss"), fixture.calls)
            assertFalse(fixture.overlays.isExpandedUpNextSheetVisible.value)
        }
        composeRule.onNodeWithTag(PlayerActionTag).performClick()
        composeRule.runOnIdle { assertEquals(1, fixture.playerClicks) }
    }

    @Test
    fun queueHubUsesLatestCardsAndForwardsSelectSwitchCreateAndDismiss() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle { fixture.overlays.isQueueHubVisible.value = true }
        composeRule.onNodeWithText("Saved queue").performClick()
        composeRule.onNodeWithText(text(R.string.queue_hub_switch)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_new_from_current)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_close)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("select:B", "switch", "create-queue", "queue-hub-dismiss", "clear-undo"), fixture.calls)
            assertEquals("B", fixture.queueState.value.selectedQueueId)
            assertFalse(fixture.overlays.isQueueHubVisible.value)
        }
    }

    @Test
    fun playlistCreationRetainsFolderContextAndCallsCreateBeforeDismissal() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle {
            fixture.folderId.value = 42L
            fixture.overlays.isCreatePlaylistDialogVisible.value = true
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("New playlist")
        composeRule.onNodeWithText(text(R.string.playlist_create_action)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("create-playlist:New playlist:42", "create-playlist-dismiss"), fixture.calls)
            assertNull(fixture.folderId.value)
            assertFalse(fixture.overlays.isCreatePlaylistDialogVisible.value)
        }
    }

    @Test
    fun singlePickerCreationEntryKeepsItsTargetAcrossPlaybackChanges() {
        val fixture = fixture()
        fixture.singleTarget.value = fixture.first
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.playlist_create_new)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("From picker")
        composeRule.runOnIdle { fixture.current.value = fixture.second }
        composeRule.onNodeWithText(text(R.string.playlist_create_action)).performClick()
        composeRule.runOnIdle {
            assertEquals("From picker", fixture.createdWithSongs.single().first)
            assertSame(fixture.first, fixture.createdWithSongs.single().second.single())
            assertNull(fixture.singleTarget.value)
            assertEquals(listOf("create-with-songs", "single-dismiss"), fixture.calls)
        }
    }

    @Test
    fun bulkPickerPreservesMissingSongFilteringAndCallbackOrder() {
        val fixture = fixture()
        fixture.bulkTargets.value = listOf(fixture.first, fixture.first.copy(title = "Same identity"), fixture.second)
        fixture.playlists.value = listOf(fixture.playlists.value.single().copy(
            songCount = 1, songMembershipKeys = setOf(fixture.first.membershipKey())
        ))
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(fixture.playlists.value.single().name).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(fixture.second), fixture.added.single().songs)
            assertEquals(listOf("add-bulk", "bulk-dismiss"), fixture.calls)
            assertTrue(fixture.bulkTargets.value.isEmpty())
        }
    }

    @Test
    fun sleepTimerUsesLiveDisplayAndStartCancelCallbacksBeforeDismissal() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle { fixture.overlays.isSleepTimerDialogVisible.value = true }
        val minutes = SLEEP_TIMER_OPTIONS_MINUTES.first()
        val option = composeRule.activity.resources.getQuantityString(R.plurals.sleep_timer_minutes_option, minutes, minutes)
        composeRule.onNodeWithText(option).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("timer-start:$minutes", "timer-dismiss"), fixture.calls)
            fixture.timerDisplay.value = "Remaining 12:34"
            fixture.overlays.isSleepTimerDialogVisible.value = true
        }
        composeRule.onNodeWithText("Remaining 12:34").assertExists()
        composeRule.onNodeWithText(text(R.string.sleep_timer_cancel)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("timer-start:$minutes", "timer-dismiss", "timer-cancel", "timer-dismiss"), fixture.calls)
            assertFalse(fixture.timerActive.value)
            assertFalse(fixture.overlays.isSleepTimerDialogVisible.value)
        }
    }

    @Test
    fun independentPendingPickersKeepTheirOriginalOrderAboveSleepTimer() {
        val fixture = fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle {
            fixture.overlays.isSleepTimerDialogVisible.value = true
            fixture.singleTarget.value = fixture.first
            fixture.bulkTargets.value = listOf(fixture.second)
        }
        // These independent owners were never universally exclusive. Bulk was composed last.
        composeRule.onAllNodesWithText(text(R.string.playlist_add_to_title)).assertCountEquals(2)
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertTrue(fixture.bulkTargets.value.isEmpty())
            assertSame(fixture.first, fixture.singleTarget.value)
            assertTrue(fixture.overlays.isSleepTimerDialogVisible.value)
        }
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertNull(fixture.singleTarget.value)
            assertTrue(fixture.overlays.isSleepTimerDialogVisible.value)
        }
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertFalse(fixture.overlays.isSleepTimerDialogVisible.value)
            assertEquals(listOf("bulk-dismiss", "single-dismiss", "timer-dismiss"), fixture.calls)
        }
    }

    @Test
    fun playerWorkAndInputPoliciesRetainExactlyTheirExistingVisibilityConditions() {
        fun work(flags: List<Boolean>) = shouldAllowExpandedPlayerVisualizerWork(
            isLyricsVisible = flags[0], isExpandedUpNextSheetVisible = flags[1], isQueueHubVisible = flags[2],
            isNowPlayingMorePresented = flags[3], isArtworkViewerVisible = flags[4],
            isSleepTimerDialogVisible = flags[5], isCreatePlaylistDialogVisible = flags[6],
            hasPendingPlaylistAdd = flags[7], hasPendingBulkPlaylistAdd = flags[8]
        )
        assertTrue(work(List(9) { false }))
        repeat(9) { active -> assertFalse(work(List(9) { it == active })) }
        assertFalse(work(List(9) { true }))
        assertFalse(shouldBlockExpandedPlayerInput(false, false, false))
        assertTrue(shouldBlockExpandedPlayerInput(true, false, false))
        assertTrue(shouldBlockExpandedPlayerInput(false, true, false))
        assertTrue(shouldBlockExpandedPlayerInput(false, false, true))
    }

    private fun fixture() = Fixture(song(1), song(2))
    private fun text(resource: Int, vararg args: Any) = composeRule.activity.getString(resource, *args)
    private fun song(id: Long) = Song(
        id, "Track $id", "Artist", "Album", 1, 120_000L,
        Uri.parse("content://media/external/audio/media/$id"), "/music/$id.flac", "/music", null,
        volumeName = "external"
    )

    private data class PlaylistAddition(val playlist: Playlist, val songs: List<Song>)

    private class Fixture(val first: Song, val second: Song) {
        val current = mutableStateOf<Song?>(first)
        val singleTarget = mutableStateOf<Song?>(null)
        val bulkTargets = mutableStateOf(emptyList<Song>())
        val folderId = mutableStateOf<Long?>(null)
        val playlists = mutableStateOf(listOf(Playlist(7, "Manual playlist", 0)))
        val timerActive = mutableStateOf(false)
        val timerDisplay = mutableStateOf("Inactive timer")
        val lyricsVisible = mutableStateOf(false)
        val ratingTarget = mutableStateOf<Song?>(null)
        val calls = mutableListOf<String>()
        val added = mutableListOf<PlaylistAddition>()
        val createdWithSongs = mutableListOf<Pair<String, List<Song>>>()
        val queueState = mutableStateOf(PlaybackQueueHubUiState(
            isLoading = false, queues = listOf(card("A", "Live queue", true), card("B", "Saved queue", false)),
            activeQueueId = "A", selectedQueueId = "A"
        ))
        lateinit var overlays: MusicOverlayState
        var playerClicks = 0

        private fun replacedMore(name: String) {
            assertFalse("More must dismiss before $name", overlays.isNowPlayingMoreVisible.value)
            assertNull(overlays.nowPlayingMoreTarget)
            calls += name
        }

        @Composable
        fun Content() {
            overlays = rememberMusicOverlayState()
            val moreTarget = overlays.currentNowPlayingMoreTarget(current.value)
            LaunchedEffect(current.value?.membershipKey(), overlays.nowPlayingMoreTarget) {
                overlays.reconcileNowPlayingMore(current.value, canPresent = true)
            }
            CompositionLocalProvider(LocalSongRatingUi provides SongRatingUiEnvironment(onOpen = {
                replacedMore("rating-open"); ratingTarget.value = it
            })) {
                val workflows = rememberNowPlayingWorkflowOpeners(current.value, true) {
                    replacedMore("playlist-open"); singleTarget.value = it
                }
                val moreAction = rememberNowPlayingMoreActionDispatcher(
                    currentSong = current.value, librarySongs = listOf(first, second),
                    favoriteMembershipKeys = emptySet(), canPresent = true, overlayState = overlays,
                    onToggleFavorite = { replacedMore("favorite") }, onOpenAlbum = { replacedMore("album") },
                    onOpenLyrics = { replacedMore("lyrics"); lyricsVisible.value = true }, onFavoriteFeedback = {},
                    onOpenArtist = { replacedMore("artist") },
                    onTrackInfoClick = { replacedMore("info"); overlays.openTrackInformation(it) },
                    onAddToPlaylist = workflows.addToPlaylist, onRateSong = workflows.rateSong,
                    onOpenSleepTimer = { replacedMore("timer-open"); overlays.isSleepTimerDialogVisible.value = true }
                )
                MaterialTheme {
                    Box(Modifier.blockPlayerInput(shouldBlockExpandedPlayerInput(
                        lyricsOwnsInput = lyricsVisible.value, isNowPlayingMorePresented = moreTarget != null,
                        isArtworkViewerVisible = overlays.isArtworkViewerVisible.value
                    ))) {
                        Button(onClick = { playerClicks++ }, modifier = Modifier.testTag(PlayerActionTag)) {
                            Text("Player action")
                        }
                    }
                    MusicTransientOverlays(
                        nowPlayingMoreTarget = moreTarget, favoriteMembershipKeys = emptySet(), songs = listOf(first, second),
                        onDismissNowPlayingMore = { calls += "more-dismiss"; overlays.dismissNowPlayingMore() },
                        onNowPlayingMoreAction = moreAction,
                        isExpandedUpNextSheetVisible = overlays.isExpandedUpNextSheetVisible.value,
                        queuedSongs = listOf(second), upcomingSongs = emptyList(), isShuffleEnabled = false,
                        onDismissExpandedUpNextSheet = { calls += "up-next-dismiss"; overlays.isExpandedUpNextSheetVisible.value = false },
                        onRemoveFromQueueClick = { calls += "remove-index:$it" },
                        onMoveQueueItemUpClick = { calls += "move-up:$it" },
                        onMoveQueueItemDownClick = { calls += "move-down:$it" }, onClearQueueClick = { calls += "clear" },
                        isQueueHubVisible = overlays.isQueueHubVisible.value, playbackQueueHubUiState = queueState.value,
                        onDismissQueueHub = { calls += "queue-hub-dismiss"; overlays.isQueueHubVisible.value = false },
                        onPlaybackQueueSelected = { id ->
                            calls += "select:$id"
                            queueState.value = queueState.value.copy(selectedQueueId = id,
                                queues = queueState.value.queues.map { it.copy(isSelected = it.queueId == id) })
                        },
                        onSwitchSelectedPlaybackQueue = { calls += "switch" },
                        onCreatePlaybackQueueFromCurrent = { calls += "create-queue" },
                        onRenamePlaybackQueue = { id, name -> calls += "rename:$id:$name" },
                        onDeletePlaybackQueue = { calls += "delete:$it" },
                        onRemovePlaybackQueueEntry = { id, entry -> calls += "remove-entry:$id:$entry" },
                        onPlayPlaybackQueueEntry = { id, entry -> calls += "play-entry:$id:$entry" },
                        onUndoPlaybackQueueEntryRemoval = { calls += "undo" },
                        onClearPlaybackQueueEntryRemovalUndo = { calls += "clear-undo" },
                        onReorderPlaybackQueueEntry = { id, entry, index -> calls += "reorder:$id:$entry:$index" },
                        onClearPlaybackQueueMessage = { calls += "clear-message" },
                        isCreatePlaylistDialogVisible = overlays.isCreatePlaylistDialogVisible.value,
                        createPlaylistFolderId = folderId.value, playlists = playlists.value,
                        onDismissCreatePlaylistDialog = {
                            calls += "create-playlist-dismiss"; overlays.isCreatePlaylistDialogVisible.value = false; folderId.value = null
                        },
                        onCreatePlaylistClick = { name, folder -> calls += "create-playlist:$name:$folder" },
                        onCreatePlaylistWithSongsClick = { name, targets ->
                            calls += "create-with-songs"; createdWithSongs += name to targets
                        },
                        songPendingPlaylistAdd = singleTarget.value,
                        onDismissAddToPlaylistDialog = { calls += "single-dismiss"; singleTarget.value = null },
                        onAddSongToPlaylistClick = { playlist, song ->
                            calls += "add-single"; added += PlaylistAddition(playlist, listOf(song))
                        },
                        songsPendingPlaylistAdd = bulkTargets.value,
                        onDismissBulkAddToPlaylistDialog = { calls += "bulk-dismiss"; bulkTargets.value = emptyList() },
                        onAddSongsToPlaylistClick = { playlist, targets ->
                            calls += "add-bulk"; added += PlaylistAddition(playlist, targets)
                        },
                        isSleepTimerDialogVisible = overlays.isSleepTimerDialogVisible.value,
                        isSleepTimerActive = timerActive.value, sleepTimerDisplayText = timerDisplay.value,
                        onStartSleepTimerClick = { calls += "timer-start:$it"; timerActive.value = true },
                        onCancelSleepTimerClick = { calls += "timer-cancel"; timerActive.value = false },
                        onDismissSleepTimerDialog = { calls += "timer-dismiss"; overlays.isSleepTimerDialogVisible.value = false }
                    )
                }
            }
        }

        private fun card(id: String, name: String, active: Boolean) = PlaybackQueueCardUiState(
            queueId = id, name = name, entryCount = 0, currentPosition = null, currentTrack = null,
            representativeTrack = null, lastActiveAt = 1L, isActive = active, isSelected = active
        )
    }

    companion object {
        private const val PlayerActionTag = "transient-boundary-player-action"
    }
}
