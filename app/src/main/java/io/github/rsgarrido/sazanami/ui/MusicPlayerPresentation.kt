package io.github.rsgarrido.sazanami.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMiniVisualOwner
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.classicwheel.classicWheelMiniVisualOwner
import io.github.rsgarrido.sazanami.ui.player.classicwheel.classicWheelMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ownsNowPlayingMorphContent
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelMiniChromeGeometry
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.mini.DefaultMiniPlayerMorphCallbacks
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultMorphMetadataOwner
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.defaultMorphMetadataOwner
import io.github.rsgarrido.sazanami.ui.player.modern.defaultMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.pocketCassetteMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.resolvePocketCassetteMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.resolvePocketCassetteSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.pocketDiscMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.resolvePocketDiscMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.resolvePocketDiscSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.pocketFlipMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketflip.resolvePocketFlipMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketflip.resolvePocketFlipSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.retrorack.resolveRetroRackMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.retrorack.retroRackMorphOwnsVisuals
import io.github.rsgarrido.sazanami.ui.player.retrorack.retroRackMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgress
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Prepares player presentation before PlayerMorphHost. The host supplies only endpoint bounds and
 * BoxScope to the returned slot; screen policy and destination actions stay with MusicScreen.
 * Bounds/menu remembers and endpoint effects run at the unconditional host-content position.
 */
@Composable
internal fun prepareMusicPlayerPresentation(
    songs: List<Song>,
    currentSong: Song?,
    previousPreviewSong: Song?,
    nextPreviewSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    playbackProgressUiState: StateFlow<PlaybackProgressUiState>,
    lyricsPlaybackUiState: LyricsPlaybackUiState,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onSuspendLyricsAutoFollow: () -> Unit,
    onReturnLyricsToCurrentLine: () -> Unit,
    onRescanLyrics: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    playbackQueueHubUiState: PlaybackQueueHubUiState,
    favoriteMembershipKeys: Set<String>,
    onToggleFavoriteClick: (Song) -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    selectedPlayerTheme: PlayerTheme,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    selectedModernPlayerAppearance: ModernPlayerAppearance,
    playerMorphState: PlayerMorphState,
    lyricsTransitionState: PlayerLyricsTransitionState,
    isLyricsVisible: Boolean,
    isExpandedUpNextSheetVisible: Boolean,
    isQueueHubVisible: Boolean,
    isNowPlayingMorePresented: Boolean,
    isCreatePlaylistDialogVisible: Boolean,
    hasPendingPlaylistAdd: Boolean,
    hasPendingBulkPlaylistAdd: Boolean,
    isSleepTimerDialogVisible: Boolean,
    isArtworkViewerVisible: Boolean,
    shouldShowBottomMiniPlayer: Boolean,
    shouldShowMetadataEditor: Boolean,
    shouldComposePlayerOverlays: Boolean,
    targetBottomContentPadding: Dp,
    bodyPresentation: @Composable (Dp) -> Unit,
    metadataEditorContent: @Composable () -> Unit,
    metadataDialogContent: @Composable () -> Unit,
    transientOverlayContent: @Composable () -> Unit,
    bottomNavigationContent: @Composable BoxScope.() -> Unit,
    onOpenMiniUpNext: () -> Unit,
    onShowQueueHub: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onShowExpandedMore: () -> Unit,
    onOpenCurrentAlbumClick: (Song) -> Unit,
    onOpenCurrentArtistClick: (Song) -> Unit,
    onTrackInfoClick: (Song) -> Unit,
    onViewArtwork: (Song) -> Unit,
    shellLayout: MusicShellLayout = MusicShellLayout(),
): @Composable BoxScope.(PlayerEndpointBounds) -> Unit {
    return { playerEndpointBounds ->
        val defaultMorphBounds = rememberMusicPlayerDefaultMorphBounds(playerEndpointBounds)
        val classicMorphBounds = rememberMusicPlayerClassicMorphBounds()
        val classicWheelMenuState = rememberMusicPlayerClassicWheelMenuState(
            selectedPlayerTheme,
            playerMorphState
        )
        val retroRackMorphBounds = rememberMusicPlayerRetroRackMorphBounds()
        val pocketFlipMorphBounds = rememberMusicPlayerPocketFlipMorphBounds()
        val pocketCassetteMorphBounds = rememberMusicPlayerPocketCassetteMorphBounds()
        val pocketDiscMorphBounds = rememberMusicPlayerPocketDiscMorphBounds()
        val defaultMorphGeometry = resolveDefaultPlayerMorphGeometry(
            progress = playerMorphState.progress,
            endpointBounds = playerEndpointBounds,
            elementBounds = defaultMorphBounds
        )
        val defaultMorphOwnsVisuals =
            selectedPlayerTheme == PlayerTheme.DEFAULT &&
                    defaultMorphMetadataOwner(
                        isMorphActive = !playerMorphState.isCollapsedAndIdle,
                        geometryReady = defaultMorphGeometry != null
                    ) == DefaultMorphMetadataOwner.Morph
        val classicWheelShellGeometry = resolveClassicWheelMorphGeometry(
            playerMorphState.progress,
            playerEndpointBounds,
            classicMorphBounds
        )
        val classicWheelSharedGeometry = resolveClassicWheelSharedGeometry(
            playerMorphState.progress,
            classicMorphBounds
        )
        val classicWheelMiniChromeGeometry = resolveClassicWheelMiniChromeGeometry(
            classicMorphBounds
        )
        val classicWheelMorphOwnsVisuals =
            selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL &&
                    classicWheelMiniVisualOwner(
                        progress = playerMorphState.progress,
                        shellGeometryReady = classicWheelShellGeometry != null,
                        sharedGeometryReady = classicWheelSharedGeometry != null,
                        miniChromeGeometryReady = classicWheelMiniChromeGeometry != null,
                        ownsNowPlayingContent = classicWheelMenuState.currentScreen
                            .ownsNowPlayingMorphContent()
                    ) == ClassicWheelMiniVisualOwner.TRANSITION
        val retroRackMorphOwnsVisuals = selectedPlayerTheme == PlayerTheme.RETRO_RACK &&
                retroRackMorphOwnsVisuals(
                    progress = playerMorphState.progress,
                    geometryReady = resolveRetroRackMorphGeometry(
                        playerMorphState.progress,
                        playerEndpointBounds
                    ) != null
                )
        val pocketFlipMorphOwnsVisuals =
            selectedPlayerTheme == PlayerTheme.POCKET_FLIP &&
                    !playerMorphState.isCollapsedAndIdle &&
                    resolvePocketFlipMorphGeometry(
                        playerMorphState.progress,
                        playerEndpointBounds
                    ) != null &&
                    resolvePocketFlipSharedGeometry(
                        playerMorphState.progress,
                        pocketFlipMorphBounds
                    ) != null
        val pocketCassetteMorphOwnsVisuals =
            selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE &&
                    !playerMorphState.isCollapsedAndIdle &&
                    resolvePocketCassetteMorphGeometry(
                        playerMorphState.progress,
                        playerEndpointBounds
                    ) != null &&
                    resolvePocketCassetteSharedGeometry(
                        playerMorphState.progress,
                        pocketCassetteMorphBounds
                    ) != null
        val pocketDiscMorphOwnsVisuals =
            selectedPlayerTheme == PlayerTheme.POCKET_DISC &&
                    !playerMorphState.isCollapsedAndIdle &&
                    resolvePocketDiscMorphGeometry(
                        playerMorphState.progress,
                        playerEndpointBounds
                    ) != null &&
                    resolvePocketDiscSharedGeometry(
                        playerMorphState.progress,
                        pocketDiscMorphBounds
                    ) != null
        val classicMiniMorphCallbacks = remember(
            playerMorphState,
            playerEndpointBounds,
            classicMorphBounds
        ) {
            DefaultMiniPlayerMorphCallbacks(
                onDragStart = {
                    playerMorphState.beginDragWithRange(
                        classicWheelMorphTravelDistance(
                            playerEndpointBounds,
                            classicMorphBounds
                        )
                    )
                },
                onDragBy = playerMorphState::dragBy,
                onDragEnd = playerMorphState::endDrag,
                onDragCancel = playerMorphState::cancelDrag
            )
        }
        val retroRackMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
            DefaultMiniPlayerMorphCallbacks(
                onDragStart = { playerMorphState.beginDragWithRange(retroRackMorphTravelDistance(playerEndpointBounds)) },
                onDragBy = playerMorphState::dragBy,
                onDragEnd = playerMorphState::endDrag,
                onDragCancel = playerMorphState::cancelDrag
            )
        }
        val pocketFlipMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
            DefaultMiniPlayerMorphCallbacks(
                onDragStart = {
                    playerMorphState.beginDragWithRange(
                        pocketFlipMorphTravelDistance(playerEndpointBounds)
                    )
                },
                onDragBy = playerMorphState::dragBy,
                onDragEnd = playerMorphState::endDrag,
                onDragCancel = playerMorphState::cancelDrag
            )
        }
        val pocketCassetteMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
            DefaultMiniPlayerMorphCallbacks(
                onDragStart = {
                    playerMorphState.beginDragWithRange(
                        pocketCassetteMorphTravelDistance(playerEndpointBounds)
                    )
                },
                onDragBy = playerMorphState::dragBy,
                onDragEnd = playerMorphState::endDrag,
                onDragCancel = playerMorphState::cancelDrag
            )
        }
        val pocketDiscMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
            DefaultMiniPlayerMorphCallbacks(
                onDragStart = {
                    playerMorphState.beginDragWithRange(
                        pocketDiscMorphTravelDistance(playerEndpointBounds)
                    )
                },
                onDragBy = playerMorphState::dragBy,
                onDragEnd = playerMorphState::endDrag,
                onDragCancel = playerMorphState::cancelDrag
            )
        }
        val defaultMiniMorphCallbacks = remember(
            playerMorphState,
            playerEndpointBounds
        ) {
            DefaultMiniPlayerMorphCallbacks(
                onDragStart = {
                    playerMorphState.beginDragWithRange(
                        progressRangePx = defaultMorphTravelDistance(
                            playerEndpointBounds, defaultMorphBounds
                        )
                    )
                },
                onDragBy = playerMorphState::dragBy,
                onDragEnd = playerMorphState::endDrag,
                onDragCancel = playerMorphState::cancelDrag
            )
        }
        LaunchedEffect(shouldShowBottomMiniPlayer) {
            if (!shouldShowBottomMiniPlayer) {
                playerEndpointBounds.markMiniStale()
            }
        }
        LaunchedEffect(selectedPlayerTheme) {
            playerEndpointBounds.markMiniStale()
            defaultMorphBounds.clearExpanded()
        }
        val bottomContentPadding by animateDpAsState(
            targetValue = targetBottomContentPadding,
            animationSpec = tween(220),
            label = "libraryChromeBottomPadding"
        )

        // Always use the same pane/slot, across breakpoints and while chrome yields to the player.
        // Only destination content is inset. The player overlays below still fill the logical host.
        Box(Modifier.fillMaxSize().musicContentPane(shellLayout)) {
            if (shouldShowMetadataEditor) {
                metadataEditorContent()
            } else {
                bodyPresentation(bottomContentPadding)
            }
        }

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).then(
                if (shellLayout.usesRail) {
                    Modifier.fillMaxWidth().absolutePadding(
                        left = shellLayout.contentLeft,
                        right = shellLayout.contentRight,
                        bottom = shellLayout.insets.bottom + shellLayout.miniPlayerBottomGap
                    )
                } else {
                    Modifier.navigationBarsPadding().padding(bottom = AppBottomNavigationHeight)
                }
            ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = shouldShowBottomMiniPlayer,
                enter = slideInVertically(tween(220)) { height -> height } +
                    fadeIn(tween(160)),
                exit = slideOutVertically(tween(200)) { height -> height } +
                    fadeOut(tween(140)),
                modifier = Modifier.widthIn(max = shellLayout.miniPlayerMaxWidth).fillMaxWidth()
            ) {
                PlaybackProgress(playbackProgressUiState) { progress ->
                    MiniPlayerSection(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        currentPosition = progress.currentPosition,
                        duration = progress.duration,
                        selectedPlayerTheme = selectedPlayerTheme,
                        selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                        playerMorphState = playerMorphState,
                        favoriteMembershipKeys = favoriteMembershipKeys,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekChange = onSeekChange,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                        onExpandClick = {
                            playerMorphState.expand()
                        },
                        onOpenUpNextClick = onOpenMiniUpNext,
                        onOpenQueueHubClick = onShowQueueHub,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        isSleepTimerActive = isSleepTimerActive,
                        sleepTimerDisplayText = sleepTimerDisplayText,
                        onSleepTimerClick = onOpenSleepTimer,
                        onMiniPlayerBoundsChanged = playerEndpointBounds::updateMini,
                        defaultMorphBounds = defaultMorphBounds,
                        classicMorphBounds = classicMorphBounds,
                        retroRackMorphBounds = retroRackMorphBounds,
                        pocketFlipMorphBounds = pocketFlipMorphBounds,
                        pocketCassetteMorphBounds = pocketCassetteMorphBounds,
                        pocketDiscMorphBounds = pocketDiscMorphBounds,
                        defaultMorphCallbacks = when (selectedPlayerTheme) {
                            PlayerTheme.DEFAULT -> defaultMiniMorphCallbacks
                            PlayerTheme.CLASSIC_WHEEL -> classicMiniMorphCallbacks
                            PlayerTheme.RETRO_RACK -> retroRackMiniMorphCallbacks
                            PlayerTheme.POCKET_FLIP -> pocketFlipMiniMorphCallbacks
                            PlayerTheme.POCKET_CASSETTE -> pocketCassetteMiniMorphCallbacks
                            PlayerTheme.POCKET_DISC -> pocketDiscMiniMorphCallbacks
                        },
                        morphOwnsVisuals = defaultMorphOwnsVisuals ||
                                classicWheelMorphOwnsVisuals ||
                                retroRackMorphOwnsVisuals ||
                                pocketFlipMorphOwnsVisuals ||
                                pocketCassetteMorphOwnsVisuals ||
                                pocketDiscMorphOwnsVisuals,
                        modifier = Modifier.playerEndpointInput(
                            playerMorphState.isCollapsedAndIdle
                        )
                    )
                }
            }
        }

        bottomNavigationContent()

        metadataDialogContent()

        if (shouldComposePlayerOverlays) {
            MusicScreenOverlays(
                playerMorphState = playerMorphState,
                isLyricsVisible = isLyricsVisible,
                lyricsTransitionState = lyricsTransitionState,
                currentSong = currentSong,
                previousPreviewSong = previousPreviewSong,
                nextPreviewSong = nextPreviewSong,
                isPlaying = isPlaying,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                playbackProgressUiState = playbackProgressUiState,
                favoriteMembershipKeys = favoriteMembershipKeys,
                isExpandedUpNextSheetVisible = isExpandedUpNextSheetVisible,
                isQueueHubVisible = isQueueHubVisible,
                isNowPlayingMorePresented = isNowPlayingMorePresented,
                playbackQueueHubUiState = playbackQueueHubUiState,
                queuedSongs = queuedSongs,
                upcomingSongs = upcomingSongs,
                isCreatePlaylistDialogVisible = isCreatePlaylistDialogVisible,
                hasPendingPlaylistAdd = hasPendingPlaylistAdd,
                hasPendingBulkPlaylistAdd = hasPendingBulkPlaylistAdd,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onSeekChange = onSeekChange,
                lyricsPlaybackUiState = lyricsPlaybackUiState,
                onSuspendLyricsAutoFollow = onSuspendLyricsAutoFollow,
                onReturnLyricsToCurrentLine = onReturnLyricsToCurrentLine,
                onRescanLyrics = onRescanLyrics,
                onOpenLyricsSettings = onOpenLyricsSettings,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                onCollapseExpandedPlayer = {
                    dismissExpandedPlayerPresentation(
                        resetLyricsPresentation = lyricsTransitionState::snapToExpanded,
                        collapsePlayer = playerMorphState::collapse
                    )
                },
                onShowQueueHub = onShowQueueHub,
                onShowExpandedMore = onShowExpandedMore,
                onToggleFavoriteClick = onToggleFavoriteClick,
                isSleepTimerDialogVisible = isSleepTimerDialogVisible,
                selectedPlayerTheme = selectedPlayerTheme,
                selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                selectedModernPlayerAppearance = selectedModernPlayerAppearance,
                playerEndpointBounds = playerEndpointBounds,
                defaultMorphBounds = defaultMorphBounds,
                classicMorphBounds = classicMorphBounds,
                classicWheelMenuState = classicWheelMenuState,
                retroRackMorphBounds = retroRackMorphBounds,
                pocketFlipMorphBounds = pocketFlipMorphBounds,
                pocketCassetteMorphBounds = pocketCassetteMorphBounds,
                pocketDiscMorphBounds = pocketDiscMorphBounds,
                songs = songs,
                onSongClick = onSongClick,
                onOpenCurrentAlbumClick = onOpenCurrentAlbumClick,
                onOpenCurrentArtistClick = onOpenCurrentArtistClick,
                onTrackInfoClick = onTrackInfoClick,
                onViewArtwork = onViewArtwork,
                isArtworkViewerVisible = isArtworkViewerVisible
            )
            transientOverlayContent()
        }
    }
}

// Each call keeps its own remember group. Theme/menu resets must never replace these bounds.
@Composable
internal fun rememberMusicPlayerDefaultMorphBounds(
    endpointBounds: PlayerEndpointBounds? = null
): DefaultPlayerMorphBounds = remember(endpointBounds) {
    DefaultPlayerMorphBounds { endpointBounds?.layoutFrame }
}

@Composable
internal fun rememberMusicPlayerClassicMorphBounds(): ClassicWheelMorphBounds =
    remember { ClassicWheelMorphBounds() }

@Composable
internal fun rememberMusicPlayerRetroRackMorphBounds(): RetroRackMorphBounds =
    remember { RetroRackMorphBounds() }

@Composable
internal fun rememberMusicPlayerPocketFlipMorphBounds(): PocketFlipMorphBounds =
    remember { PocketFlipMorphBounds() }

@Composable
internal fun rememberMusicPlayerPocketCassetteMorphBounds(): PocketCassetteMorphBounds =
    remember { PocketCassetteMorphBounds() }

@Composable
internal fun rememberMusicPlayerPocketDiscMorphBounds(): PocketDiscMorphBounds =
    remember { PocketDiscMorphBounds() }

@Composable
internal fun rememberMusicPlayerClassicWheelMenuState(
    selectedPlayerTheme: PlayerTheme,
    playerMorphState: PlayerMorphState
): ClassicWheelMenuState = remember(selectedPlayerTheme, playerMorphState.shouldComposeExpanded) {
    ClassicWheelMenuState()
}
