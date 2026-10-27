package io.github.rsgarrido.sazanami.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.ExpandedPlayerThemeHost
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsGestureRegion
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import io.github.rsgarrido.sazanami.ui.player.ResolvedPlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.WarmCurrentSongWaveform
import io.github.rsgarrido.sazanami.ui.player.shouldLoadExpandedPlayerWaveform
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerLayout
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceNavigationState
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.rememberPocketAdvanceNavigationState
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.lyricsVisualAlpha
import io.github.rsgarrido.sazanami.ui.player.playerVisualAlpha
import io.github.rsgarrido.sazanami.ui.player.ImmersiveSystemBarsEffect
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgress
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsScreen
import kotlinx.coroutines.flow.StateFlow

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
    onOpenCurrentAlbumClick: (Song) -> Unit,
    onOpenCurrentArtistClick: (Song) -> Unit,
    onTrackInfoClick: (Song) -> Unit,
    onViewArtwork: (Song) -> Unit,
    isArtworkViewerVisible: Boolean,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    playbackProgressUiState: StateFlow<PlaybackProgressUiState>,
    favoriteMembershipKeys: Set<String>,
    isExpandedUpNextSheetVisible: Boolean,
    isQueueHubVisible: Boolean,
    isNowPlayingMorePresented: Boolean,
    playbackQueueHubUiState: PlaybackQueueHubUiState,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    isCreatePlaylistDialogVisible: Boolean,
    hasPendingPlaylistAdd: Boolean,
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
    onShowQueueHub: () -> Unit,
    onShowExpandedMore: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    hasPendingBulkPlaylistAdd: Boolean,
    isSleepTimerDialogVisible: Boolean,
    playerPresentation: ResolvedPlayerPresentation,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    selectedModernPlayerAppearance: ModernPlayerAppearance,
    playerEndpointBounds: PlayerEndpointBounds,
    defaultMorphBounds: DefaultPlayerMorphBounds,
    classicMorphBounds: ClassicWheelMorphBounds,
    classicWheelMenuState: ClassicWheelMenuState,
    retroRackMorphBounds: RetroRackMorphBounds,
    pocketFlipMorphBounds: PocketFlipMorphBounds,
    pocketCassetteMorphBounds: PocketCassetteMorphBounds,
    pocketDiscMorphBounds: PocketDiscMorphBounds,
    defaultPlayerLayout: DefaultPlayerLayout? = null,
    pocketAdvanceMorphBounds: PocketAdvancePlayerMorphBounds? = null,
    playerPresentationInputEnabled: Boolean = true,
    pocketAdvanceNavigationState: PocketAdvanceNavigationState = rememberPocketAdvanceNavigationState()
) {
    val isPlayerExpanded = playerMorphState.shouldComposeExpanded
    val setSystemBarSurface = LocalSystemBarSurfaceSetter.current
    val systemBarSurface = playerSystemBarSurface(
        presentation = playerPresentation.identity,
        isExpanded = isPlayerExpanded,
        hasSong = currentSong != null,
        lyricsVisible = isLyricsVisible || lyricsTransitionState.lyricsComposed
    )
    DisposableEffect(setSystemBarSurface, systemBarSurface) {
        setSystemBarSurface(systemBarSurface)
        onDispose { setSystemBarSurface(SystemBarSurface.SHELL) }
    }
    val lyricsGestureRegion = remember(playerPresentation.identity) {
        PlayerLyricsGestureRegion()
    }
    val shouldWarmCurrentWaveform = shouldLoadExpandedPlayerWaveform(
        presentation = playerPresentation.identity,
        modernSeekbarStyle = selectedModernPlayerAppearance.seekbar.style
    )
    WarmCurrentSongWaveform(
        currentSong = currentSong,
        shouldWarm = shouldWarmCurrentWaveform
    )

    ImmersiveSystemBarsEffect(
        isImmersive = shouldUseImmersivePlayerSystemBars(
            playerPresentation.identity,
            isPlayerExpanded,
            isLyricsVisible
        )
    )

    val premeasurePocketAdvance = playerPresentation.identity == PlayerPresentationIdentity.PocketAdvance
    if ((isPlayerExpanded || premeasurePocketAdvance) && currentSong != null) {
        val activeQueueCard = playbackQueueHubUiState.queues.firstOrNull { queue -> queue.isActive }
        val activeQueueSongs = playbackQueueHubUiState.activeEntries
            .mapNotNull { entry -> entry.song }
            .ifEmpty { listOfNotNull(currentSong) + queuedSongs + upcomingSongs }
        val activeQueueName = activeQueueCard?.name
            ?.takeIf { name -> name.isNotBlank() }
            ?: stringResource(R.string.queue_current_fallback)
        val fallbackActiveQueueCount = activeQueueSongs.size.coerceAtLeast(1)
        val activeQueuePosition = activeQueueCard?.currentPosition ?: 1
        val activeQueueCount = activeQueueCard?.entryCount
            ?.takeIf { count -> count > 0 }
            ?: fallbackActiveQueueCount

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Alpha/disabled clickable nodes alone still participate in pointer hit testing.
                // Keep the warm, measured endpoint behind the real app and mini while collapsed.
                .zIndex(if (premeasurePocketAdvance && !isPlayerExpanded) -1f else 0f)
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
                    enabled = isPlayerExpanded,
                    onClick = {}
                )
                .blockPlayerInput(shouldBlockExpandedPlayerInput(
                    lyricsOwnsInput = lyricsTransitionState.lyricsOwnsInput,
                    isNowPlayingMorePresented = isNowPlayingMorePresented,
                    isArtworkViewerVisible = isArtworkViewerVisible
                ) || (isPlayerExpanded && !playerPresentationInputEnabled))
        ) {
            PlaybackProgress(playbackProgressUiState) { progress ->
                ExpandedPlayerThemeHost(
                    playerPresentation = playerPresentation,
                    modernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                    modernPlayerAppearance = selectedModernPlayerAppearance,
                    defaultPlayerLayout = defaultPlayerLayout,
                    isVisualizerWorkAllowed = shouldAllowExpandedPlayerVisualizerWork(
                        isLyricsVisible = isLyricsVisible,
                        isExpandedUpNextSheetVisible = isExpandedUpNextSheetVisible,
                        isQueueHubVisible = isQueueHubVisible,
                        isNowPlayingMorePresented = isNowPlayingMorePresented,
                        isArtworkViewerVisible = isArtworkViewerVisible,
                        isSleepTimerDialogVisible = isSleepTimerDialogVisible,
                        isCreatePlaylistDialogVisible = isCreatePlaylistDialogVisible,
                        hasPendingPlaylistAdd = hasPendingPlaylistAdd,
                        hasPendingBulkPlaylistAdd = hasPendingBulkPlaylistAdd
                    ),
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
                    lyricsGestureRegion = lyricsGestureRegion,
                    onOpenQueueHubClick = onShowQueueHub,
                    onOpenMoreClick = onShowExpandedMore,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    songs = songs,
                    upcomingSongs = upcomingSongs,
                    activeQueueSongs = activeQueueSongs,
                    activeQueueName = activeQueueName,
                    activeQueuePosition = activeQueuePosition,
                    activeQueueCount = activeQueueCount,
                    onSongClick = onSongClick,
                    onOpenCurrentAlbumClick = onOpenCurrentAlbumClick,
                    onOpenCurrentArtistClick = onOpenCurrentArtistClick,
                    onTrackInfoClick = onTrackInfoClick,
                    onViewArtwork = onViewArtwork,
                    endpointBounds = playerEndpointBounds,
                    defaultMorphBounds = defaultMorphBounds,
                    classicMorphBounds = classicMorphBounds,
                    classicWheelMenuState = classicWheelMenuState,
                    retroRackMorphBounds = retroRackMorphBounds,
                    pocketFlipMorphBounds = pocketFlipMorphBounds,
                    pocketCassetteMorphBounds = pocketCassetteMorphBounds,
                    pocketDiscMorphBounds = pocketDiscMorphBounds,
                    pocketAdvanceNavigationState = pocketAdvanceNavigationState,
                    pocketAdvanceMorphBounds = pocketAdvanceMorphBounds
                )
            }
        }
    }

    if (isPlayerExpanded && lyricsTransitionState.lyricsComposed && currentSong != null) {
        LyricsScreen(
            state = lyricsPlaybackUiState,
            isPlaying = isPlaying,
            transitionState = lyricsTransitionState,
            closeGestureRegion = lyricsGestureRegion,
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
}

internal fun shouldBlockExpandedPlayerInput(
    lyricsOwnsInput: Boolean,
    isNowPlayingMorePresented: Boolean,
    isArtworkViewerVisible: Boolean
): Boolean = lyricsOwnsInput || isNowPlayingMorePresented || isArtworkViewerVisible

// Preserve the existing work gate here with player presentation, independently of modal rendering.
internal fun shouldAllowExpandedPlayerVisualizerWork(
    isLyricsVisible: Boolean,
    isExpandedUpNextSheetVisible: Boolean,
    isQueueHubVisible: Boolean,
    isNowPlayingMorePresented: Boolean,
    isArtworkViewerVisible: Boolean,
    isSleepTimerDialogVisible: Boolean,
    isCreatePlaylistDialogVisible: Boolean,
    hasPendingPlaylistAdd: Boolean,
    hasPendingBulkPlaylistAdd: Boolean
): Boolean = !isLyricsVisible &&
        !isExpandedUpNextSheetVisible &&
        !isQueueHubVisible &&
        !isNowPlayingMorePresented &&
        !isArtworkViewerVisible &&
        !isSleepTimerDialogVisible &&
        !isCreatePlaylistDialogVisible &&
        !hasPendingPlaylistAdd &&
        !hasPendingBulkPlaylistAdd

internal fun shouldUseImmersivePlayerSystemBars(
    presentation: PlayerPresentationIdentity,
    isPlayerExpanded: Boolean,
    isLyricsVisible: Boolean
): Boolean = when (presentation) {
    is PlayerPresentationIdentity.Existing -> shouldUseImmersivePlayerSystemBars(
        presentation.theme, isPlayerExpanded, isLyricsVisible
    )
    // Advance keeps visibility-independent inset measurements while the existing controller hides bars.
    PlayerPresentationIdentity.PocketAdvance -> isPlayerExpanded && !isLyricsVisible
}

internal fun shouldUseImmersivePlayerSystemBars(
    theme: PlayerTheme,
    isPlayerExpanded: Boolean,
    isLyricsVisible: Boolean
): Boolean = isPlayerExpanded && !isLyricsVisible && when (theme) {
    PlayerTheme.CLASSIC_WHEEL,
    PlayerTheme.RETRO_RACK,
    PlayerTheme.POCKET_FLIP,
    PlayerTheme.POCKET_CASSETTE,
    PlayerTheme.POCKET_DISC -> true
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
