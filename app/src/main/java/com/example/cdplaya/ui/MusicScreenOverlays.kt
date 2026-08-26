package com.example.cdplaya.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.player.RepeatMode
import com.example.cdplaya.ui.player.ExpandedPlayerThemeHost
import com.example.cdplaya.ui.player.PlayerLyricsTransitionState
import com.example.cdplaya.ui.player.PlayerMorphState
import com.example.cdplaya.ui.player.PlayerEndpointBounds
import com.example.cdplaya.ui.player.WarmCurrentSongWaveform
import com.example.cdplaya.ui.player.shouldLoadExpandedPlayerWaveform
import com.example.cdplaya.ui.player.modern.DefaultPlayerMorphBounds
import com.example.cdplaya.ui.player.classicwheel.ClassicWheelMorphBounds
import com.example.cdplaya.ui.player.classicwheel.ClassicWheelMenuState
import com.example.cdplaya.ui.player.retrorack.RetroRackMorphBounds
import com.example.cdplaya.ui.player.pocketflip.PocketFlipMorphBounds
import com.example.cdplaya.ui.player.pocketcassette.PocketCassetteMorphBounds
import com.example.cdplaya.ui.player.lyricsVisualAlpha
import com.example.cdplaya.ui.player.playerVisualAlpha
import com.example.cdplaya.ui.player.ImmersiveSystemBarsEffect
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.playlist.AddToPlaylistDialog
import com.example.cdplaya.ui.playlist.PlaylistNameDialog
import com.example.cdplaya.ui.queue.QueueScreen
import com.example.cdplaya.ui.settings.SleepTimerDialog
import com.example.cdplaya.ui.state.PlaybackProgress
import com.example.cdplaya.ui.state.PlaybackProgressUiState
import com.example.cdplaya.lyrics.LyricsPlaybackUiState
import com.example.cdplaya.ui.lyrics.LyricsScreen
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreenOverlays(
    playerMorphState: PlayerMorphState,
    isLyricsVisible: Boolean,
    lyricsTransitionState: PlayerLyricsTransitionState,
    currentSong: Song?,
    previousPreviewSong: Song?,
    nextPreviewSong: Song?,
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    playbackProgressUiState: StateFlow<PlaybackProgressUiState>,
    favoriteMembershipKeys: Set<String>,
    isExpandedUpNextSheetVisible: Boolean,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    isCreatePlaylistDialogVisible: Boolean,
    createPlaylistFolderId: Long?,
    songPendingPlaylistAdd: Song?,
    playlists: List<Playlist>,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    lyricsPlaybackUiState: LyricsPlaybackUiState,
    onSuspendLyricsAutoFollow: () -> Unit,
    onReturnLyricsToCurrentLine: () -> Unit,
    onRescanLyrics: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCollapseExpandedPlayer: () -> Unit,
    onShowExpandedUpNextSheet: () -> Unit,
    onShowExpandedSleepTimer: () -> Unit,
    onShowExpandedMore: () -> Unit,
    onDismissExpandedUpNextSheet: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onDismissCreatePlaylistDialog: () -> Unit,
    onCreatePlaylistClick: (String, Long?) -> Unit,
    onCreatePlaylistWithSongsClick: (String, List<Song>) -> Unit,
    onDismissAddToPlaylistDialog: () -> Unit,
    onAddSongToPlaylistClick: (Playlist, Song) -> Unit,
    onAddSongsToPlaylistClick: (Playlist, List<Song>) -> Unit,
    songsPendingPlaylistAdd: List<Song>,
    onDismissBulkAddToPlaylistDialog: () -> Unit,
    isSleepTimerDialogVisible: Boolean,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onStartSleepTimerClick: (Int) -> Unit,
    onCancelSleepTimerClick: () -> Unit,
    onDismissSleepTimerDialog: () -> Unit,
    selectedPlayerTheme: PlayerTheme,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    selectedModernSeekbarStyle: ModernSeekbarStyle,
    playerEndpointBounds: PlayerEndpointBounds,
    defaultMorphBounds: DefaultPlayerMorphBounds,
    classicMorphBounds: ClassicWheelMorphBounds,
    classicWheelMenuState: ClassicWheelMenuState,
    retroRackMorphBounds: RetroRackMorphBounds,
    pocketFlipMorphBounds: PocketFlipMorphBounds,
    pocketCassetteMorphBounds: PocketCassetteMorphBounds
) {
    val isPlayerExpanded = playerMorphState.shouldComposeExpanded
    val shouldWarmCurrentWaveform = shouldLoadExpandedPlayerWaveform(
        selectedPlayerTheme = selectedPlayerTheme,
        modernSeekbarStyle = selectedModernSeekbarStyle
    )
    WarmCurrentSongWaveform(
        currentSong = currentSong,
        shouldWarm = shouldWarmCurrentWaveform
    )

    ImmersiveSystemBarsEffect(
        isImmersive = shouldUseImmersivePlayerSystemBars(
            selectedPlayerTheme,
            isPlayerExpanded,
            isLyricsVisible
        )
    )

    if (isPlayerExpanded && currentSong != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = lyricsTransitionState.progress
                    translationY = -56.dp.toPx() * progress
                    val scale = 1f - 0.025f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = playerVisualAlpha(progress)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .blockPlayerInput(lyricsTransitionState.lyricsOwnsInput)
        ) {
            PlaybackProgress(playbackProgressUiState) { progress ->
                ExpandedPlayerThemeHost(
                    selectedPlayerTheme = selectedPlayerTheme,
                    tokens = selectedPlayerThemeTokens,
                    modernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                    modernSeekbarStyle = selectedModernSeekbarStyle,
                    isVisualizerWorkAllowed = !isLyricsVisible &&
                            !isExpandedUpNextSheetVisible &&
                            !isSleepTimerDialogVisible &&
                            !isCreatePlaylistDialogVisible &&
                            songPendingPlaylistAdd == null &&
                            songsPendingPlaylistAdd.isEmpty(),
                    currentSong = currentSong,
                    previousPreviewSong = previousPreviewSong,
                    nextPreviewSong = nextPreviewSong,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    currentPosition = progress.currentPosition,
                    duration = progress.duration,
                    isCurrentSongFavorite = currentSong.membershipKey() in favoriteMembershipKeys,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onCollapseClick = onCollapseExpandedPlayer,
                    playerMorphState = playerMorphState,
                    lyricsTransitionState = lyricsTransitionState,
                    onOpenUpNextClick = onShowExpandedUpNextSheet,
                    onOpenSleepTimerClick = onShowExpandedSleepTimer,
                    onOpenMoreClick = onShowExpandedMore,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    songs = songs,
                    upcomingSongs = upcomingSongs,
                    onSongClick = onSongClick,
                    endpointBounds = playerEndpointBounds,
                    defaultMorphBounds = defaultMorphBounds,
                    classicMorphBounds = classicMorphBounds,
                    classicWheelMenuState = classicWheelMenuState,
                    retroRackMorphBounds = retroRackMorphBounds,
                    pocketFlipMorphBounds = pocketFlipMorphBounds,
                    pocketCassetteMorphBounds = pocketCassetteMorphBounds
                )
            }
        }
    }

    if (isPlayerExpanded && lyricsTransitionState.lyricsComposed && currentSong != null) {
        LyricsScreen(
            state = lyricsPlaybackUiState,
            isPlaying = isPlaying,
            transitionState = lyricsTransitionState,
            interactive = lyricsTransitionState.lyricsInteractive,
            onBack = lyricsTransitionState::returnToExpanded,
            onPlayPause = onPlayPauseClick,
            onSeek = onSeekChange,
            onSuspendAutoFollow = onSuspendLyricsAutoFollow,
            onReturnToCurrentLine = onReturnLyricsToCurrentLine,
            onRescan = onRescanLyrics,
            onOpenSettings = onOpenLyricsSettings,
            modifier = Modifier.graphicsLayer {
                val progress = lyricsTransitionState.progress
                alpha = lyricsVisualAlpha(progress)
                translationY = (1f - progress) * 88.dp.toPx()
            }
        )
    }

    if (isExpandedUpNextSheetVisible) {
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

    if (isCreatePlaylistDialogVisible) {
        PlaylistNameDialog(
            title = "Create Playlist",
            confirmButtonText = "Create",
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

internal fun shouldUseImmersivePlayerSystemBars(
    theme: PlayerTheme,
    isPlayerExpanded: Boolean,
    isLyricsVisible: Boolean
): Boolean = isPlayerExpanded && !isLyricsVisible && when (theme) {
    PlayerTheme.CLASSIC_WHEEL,
    PlayerTheme.RETRO_RACK,
    PlayerTheme.POCKET_FLIP,
    PlayerTheme.POCKET_CASSETTE -> true
    PlayerTheme.DEFAULT -> false
}

internal fun Modifier.blockPlayerInput(blocked: Boolean): Modifier =
    if (!blocked) {
        this
    } else {
        this.then(
            Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .forEach { it.consume() }
                        }
                    }
                }
        )
    }
