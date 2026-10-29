package io.github.rsgarrido.sazanami.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.player.NowPlayingAction
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.LocalPocketAdvancePalette
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceOverlayTheme
import io.github.rsgarrido.sazanami.ui.player.NowPlayingMoreDialog
import io.github.rsgarrido.sazanami.ui.player.nowPlayingActions
import io.github.rsgarrido.sazanami.ui.playlist.AddToPlaylistDialog
import io.github.rsgarrido.sazanami.ui.playlist.PlaylistNameDialog
import io.github.rsgarrido.sazanami.ui.queue.QueueHubSheet
import io.github.rsgarrido.sazanami.ui.queue.QueueScreen
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.settings.SleepTimerDialog

/** Presentation only; existing destinations and pending workflow targets remain with their owners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicTransientOverlays(
    nowPlayingMoreTarget: Song?,
    favoriteMembershipKeys: Set<String>,
    songs: List<Song>,
    onDismissNowPlayingMore: () -> Unit,
    onNowPlayingMoreAction: (NowPlayingAction, Song) -> Unit,
    isExpandedUpNextSheetVisible: Boolean,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    isShuffleEnabled: Boolean,
    onDismissExpandedUpNextSheet: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    isQueueHubVisible: Boolean,
    playbackQueueHubUiState: PlaybackQueueHubUiState,
    onDismissQueueHub: () -> Unit,
    onPlaybackQueueSelected: (String) -> Unit,
    onSwitchSelectedPlaybackQueue: () -> Unit,
    onCreatePlaybackQueueFromCurrent: () -> Unit,
    onRenamePlaybackQueue: (String, String) -> Unit,
    onDeletePlaybackQueue: (String) -> Unit,
    onRemovePlaybackQueueEntry: (String, String) -> Unit,
    onPlayPlaybackQueueEntry: (String, String) -> Unit,
    onUndoPlaybackQueueEntryRemoval: () -> Unit,
    onClearPlaybackQueueEntryRemovalUndo: () -> Unit,
    onReorderPlaybackQueueEntry: (String, String, Int) -> Unit,
    onClearPlaybackQueueMessage: () -> Unit,
    isCreatePlaylistDialogVisible: Boolean,
    createPlaylistFolderId: Long?,
    playlists: List<Playlist>,
    onDismissCreatePlaylistDialog: () -> Unit,
    onCreatePlaylistClick: (String, Long?) -> Unit,
    onCreatePlaylistWithSongsClick: (String, List<Song>) -> Unit,
    songPendingPlaylistAdd: Song?,
    onDismissAddToPlaylistDialog: () -> Unit,
    onAddSongToPlaylistClick: (Playlist, Song) -> Unit,
    songsPendingPlaylistAdd: List<Song>,
    onDismissBulkAddToPlaylistDialog: () -> Unit,
    onAddSongsToPlaylistClick: (Playlist, List<Song>) -> Unit,
    isSleepTimerDialogVisible: Boolean,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onStartSleepTimerClick: (Int) -> Unit,
    onCancelSleepTimerClick: () -> Unit,
    onDismissSleepTimerDialog: () -> Unit,
    playerPresentation: PlayerPresentationIdentity? = null
) {
    val advance = playerPresentation == PlayerPresentationIdentity.PocketAdvance
    val palette = LocalPocketAdvancePalette.current
    val overlayAccent = if (advance && palette.lcdPalette != PocketAdvanceLcdPalette.ORIGINAL) palette.Ink else null
    if (nowPlayingMoreTarget != null) {
        val ratings = LocalSongRatingUi.current.state.ratingsByReferenceKey
        val isRated = (ratings[nowPlayingMoreTarget.membershipKey()] ?: 0) in 1..5
        val actions = remember(nowPlayingMoreTarget, favoriteMembershipKeys, songs, isRated, isSleepTimerActive) {
            nowPlayingActions(nowPlayingMoreTarget, favoriteMembershipKeys, songs, isRated, isSleepTimerActive)
        }
        PocketAdvanceOverlayTheme(active = advance) {
            NowPlayingMoreDialog(
                accentOverride = overlayAccent,
                target = nowPlayingMoreTarget,
                actions = actions,
                onDismiss = onDismissNowPlayingMore,
                onAction = { action -> onNowPlayingMoreAction(action, nowPlayingMoreTarget) }
            )
        }
    }

    if (isExpandedUpNextSheetVisible) {
        PocketAdvanceOverlayTheme(active = advance) {
            ModalBottomSheet(
                onDismissRequest = onDismissExpandedUpNextSheet
            ) {
                QueueScreen(
                    queuedSongs = queuedSongs,
                    upcomingSongs = upcomingSongs,
                    isShuffleEnabled = isShuffleEnabled,
                    onBackClick = onDismissExpandedUpNextSheet,
                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                    onMoveQueueItemUpClick = onMoveQueueItemUpClick,
                    onMoveQueueItemDownClick = onMoveQueueItemDownClick,
                    onClearQueueClick = onClearQueueClick,
                    modifier = Modifier.fillMaxHeight(0.86f)
                )
            }
        }
    }

    if (isQueueHubVisible) {
        PocketAdvanceOverlayTheme(active = advance) {
            QueueHubSheet(
                state = playbackQueueHubUiState,
                onDismiss = onDismissQueueHub,
                onQueueSelected = onPlaybackQueueSelected,
                onSwitchSelected = onSwitchSelectedPlaybackQueue,
                onCreateFromCurrent = onCreatePlaybackQueueFromCurrent,
                onRename = onRenamePlaybackQueue,
                onDelete = onDeletePlaybackQueue,
                onRemoveEntry = onRemovePlaybackQueueEntry,
                onPlayEntry = onPlayPlaybackQueueEntry,
                onUndoRemove = onUndoPlaybackQueueEntryRemoval,
                onUndoDismissed = onClearPlaybackQueueEntryRemovalUndo,
                onReorderEntry = onReorderPlaybackQueueEntry,
                onMessageDismissed = onClearPlaybackQueueMessage
            )
        }
    }

    if (isCreatePlaylistDialogVisible) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_create_title),
            confirmButtonText = stringResource(R.string.playlist_create_action),
            existingPlaylistNames = playlists.map { playlist ->
                playlist.name
            },
            onDismiss = onDismissCreatePlaylistDialog,
            onConfirmClick = { playlistName ->
                onCreatePlaylistClick(playlistName, createPlaylistFolderId)
                onDismissCreatePlaylistDialog()
            }
        )
    }

    if (isSleepTimerDialogVisible) {
        SleepTimerDialog(
            isTimerActive = isSleepTimerActive,
            sleepTimerDisplayText = sleepTimerDisplayText,
            onStartTimerClick = onStartSleepTimerClick,
            onCancelTimerClick = onCancelSleepTimerClick,
            onDismiss = onDismissSleepTimerDialog
        )
    }

    if (songPendingPlaylistAdd != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            songsToAdd = listOf(songPendingPlaylistAdd),
            onDismiss = onDismissAddToPlaylistDialog,
            onPlaylistSelected = { playlist, songs ->
                songs.singleOrNull()?.let { onAddSongToPlaylistClick(playlist, it) }
                onDismissAddToPlaylistDialog()
            },
            onCreatePlaylist = onCreatePlaylistWithSongsClick
        )
    }

    if (songsPendingPlaylistAdd.isNotEmpty()) {
        AddToPlaylistDialog(
            playlists = playlists,
            songsToAdd = songsPendingPlaylistAdd,
            onDismiss = onDismissBulkAddToPlaylistDialog,
            onPlaylistSelected = { playlist, songs ->
                onAddSongsToPlaylistClick(playlist, songs)
                onDismissBulkAddToPlaylistDialog()
            },
            onCreatePlaylist = onCreatePlaylistWithSongsClick
        )
    }
}
