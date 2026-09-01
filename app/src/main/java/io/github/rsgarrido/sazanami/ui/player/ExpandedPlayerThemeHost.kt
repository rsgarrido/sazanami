package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.findLibraryAlbumGroupForSong
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelPlayerMorph
import io.github.rsgarrido.sazanami.ui.player.classicwheel.allowsLyricsSwipe
import io.github.rsgarrido.sazanami.ui.player.classicwheel.classicWheelPlayPauseVisualOwnership
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ownsNowPlayingMorphContent
import io.github.rsgarrido.sazanami.ui.player.classicwheel.playerMorphRendererFor
import io.github.rsgarrido.sazanami.ui.player.classicwheel.PlayerMorphRenderer
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.classicwheel.classicWheelMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.selectNearbyWaveformSongs
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassettePlayerMorph
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphSpec
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.resolvePocketCassetteMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.resolvePocketCassetteSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.pocketCassetteSharedOwner
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.pocketCassetteMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.pocketCassetteDistanceThreshold
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.shouldRunPocketCassetteExpandedWork
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscPlayerMorph
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphSpec
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.resolvePocketDiscMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.resolvePocketDiscSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.pocketDiscSharedOwner
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.pocketDiscMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.pocketDiscDistanceThreshold
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.shouldRunPocketDiscExpandedWork
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipPlayerMorph
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphSpec
import io.github.rsgarrido.sazanami.ui.player.pocketflip.resolvePocketFlipMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketflip.resolvePocketFlipSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketflip.pocketFlipSharedOwner
import io.github.rsgarrido.sazanami.ui.player.pocketflip.pocketFlipMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketflip.pocketFlipDistanceThreshold
import io.github.rsgarrido.sazanami.ui.player.pocketflip.shouldRunPocketFlipExpandedWork
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackPlayerMorph
import io.github.rsgarrido.sazanami.ui.player.retrorack.resolveRetroRackMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.retrorack.shouldRunRetroRackExpandedWork
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.retrorack.resolveRetroRackSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.retrorack.retroRackSharedOwner
import io.github.rsgarrido.sazanami.ui.player.retrorack.retroRackMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphSpec
import io.github.rsgarrido.sazanami.ui.player.retrorack.retroRackDistanceThreshold
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorph
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerDefaults
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.modern.rememberModernArtworkCarouselPresentation
import io.github.rsgarrido.sazanami.ui.player.modern.shouldRunDefaultExpandedWork
import io.github.rsgarrido.sazanami.ui.player.modern.defaultMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.modern.rememberModernArtworkPalette
import io.github.rsgarrido.sazanami.ui.player.modern.ModernExpandedArtworkPreloader
import io.github.rsgarrido.sazanami.ui.player.modern.modernArtworkPreloadPolicy
import kotlin.math.roundToInt

@Composable
fun ExpandedPlayerThemeHost(
    selectedPlayerTheme: PlayerTheme,
    tokens: PlayerThemeTokens,
    currentSong: Song?,
    previousPreviewSong: Song?,
    nextPreviewSong: Song?,
    modernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    modernPlayerAppearance: ModernPlayerAppearance,
    isVisualizerWorkAllowed: Boolean,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    isCurrentSongFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCollapseClick: () -> Unit,
    playerMorphState: PlayerMorphState,
    lyricsTransitionState: PlayerLyricsTransitionState,
    onOpenQueueHubClick: () -> Unit,
    onOpenSleepTimerClick: () -> Unit,
    onOpenMoreClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    songs: List<Song>,
    upcomingSongs: List<Song>,
    activeQueueSongs: List<Song>,
    activeQueueName: String,
    activeQueuePosition: Int,
    activeQueueCount: Int,
    onSongClick: (Song, List<Song>) -> Unit,
    onOpenCurrentAlbumClick: (Song) -> Unit,
    endpointBounds: PlayerEndpointBounds,
    defaultMorphBounds: DefaultPlayerMorphBounds,
    classicMorphBounds: ClassicWheelMorphBounds,
    classicWheelMenuState: ClassicWheelMenuState,
    retroRackMorphBounds: RetroRackMorphBounds,
    pocketFlipMorphBounds: PocketFlipMorphBounds,
    pocketCassetteMorphBounds: PocketCassetteMorphBounds,
    pocketDiscMorphBounds: PocketDiscMorphBounds
) {
    val shouldLoadWaveform = shouldLoadExpandedPlayerWaveform(
        selectedPlayerTheme = selectedPlayerTheme,
        modernSeekbarStyle = modernPlayerAppearance.seekbar.style
    ) && when (selectedPlayerTheme) {
        PlayerTheme.DEFAULT -> shouldRunDefaultExpandedWork(playerMorphState.progress)
        PlayerTheme.POCKET_FLIP -> shouldRunPocketFlipExpandedWork(playerMorphState.progress)
        PlayerTheme.POCKET_DISC -> shouldRunPocketDiscExpandedWork(playerMorphState.progress)
        else -> true
    }
    val shouldPrefetchWaveforms = selectedPlayerTheme == PlayerTheme.DEFAULT &&
            modernPlayerAppearance.seekbar.style.usesWaveformData
    val nearbyWaveformSongs = remember(
        shouldPrefetchWaveforms,
        currentSong?.id,
        currentSong?.filePath,
        nextPreviewSong?.id,
        nextPreviewSong?.filePath,
        previousPreviewSong?.id,
        previousPreviewSong?.filePath
    ) {
        if (shouldPrefetchWaveforms && currentSong != null) {
            selectNearbyWaveformSongs(
                currentSong = currentSong,
                nextSong = nextPreviewSong,
                previousSong = previousPreviewSong
            )
        } else {
            emptyList()
        }
    }
    val waveformData = rememberExpandedPlayerWaveformData(
        currentSong = currentSong,
        shouldLoad = shouldLoadWaveform,
        prefetchSongs = nearbyWaveformSongs
    )

    val hostDensity = LocalDensity.current.density
    var hostWidthPx by remember { mutableFloatStateOf(1f) }
    var hostHeightPx by remember { mutableFloatStateOf(1f) }
    var hostDragOffset by remember { mutableFloatStateOf(0f) }
    val hostDragState = rememberDraggableState { delta ->
        if (delta < 0f && lyricsTransitionState.progress == 0f) {
            lyricsTransitionState.beginOpeningDrag()
        }
        hostDragOffset = (hostDragOffset + delta).coerceAtMost(0f)
        lyricsTransitionState.dragOpeningBy(delta, hostHeightPx)
    }
    val openLyricsSemanticsModifier = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction("Open lyrics") {
                lyricsTransitionState.openLyrics()
                true
            }
        )
    }
    val lyricsDragModifier = Modifier.draggable(
        state = hostDragState,
        orientation = Orientation.Vertical,
        enabled = !lyricsTransitionState.lyricsInteractive,
        onDragStarted = { hostDragOffset = 0f },
        onDragStopped = { velocity ->
            lyricsTransitionState.settleOpening(velocity)
            hostDragOffset = 0f
        }
    )
    val sharedGestureModifier = if (
        selectedPlayerTheme == PlayerTheme.DEFAULT ||
        selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL
    ) {
        Modifier
    } else {
        lyricsDragModifier
    }
    val sharedLyricsSemanticsModifier = if (
        selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL
    ) {
        Modifier
    } else {
        openLyricsSemanticsModifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                hostWidthPx = size.width.toFloat().coerceAtLeast(1f)
                hostHeightPx = size.height.toFloat().coerceAtLeast(1f)
            }
            .then(sharedLyricsSemanticsModifier)
            .then(sharedGestureModifier)
    ) {
        when (selectedPlayerTheme) {
            PlayerTheme.DEFAULT -> {
                if (currentSong != null) {
                    val modernStyle = ModernPlayerDefaults.style()
                    val geometry = resolveDefaultPlayerMorphGeometry(
                        progress = playerMorphState.progress,
                        endpointBounds = endpointBounds,
                        elementBounds = defaultMorphBounds
                    )
                    val artworkPreloadPolicy = remember(
                        hostWidthPx,
                        hostHeightPx,
                        hostDensity,
                        modernPlayerAppearance
                    ) {
                        modernArtworkPreloadPolicy(
                            viewportWidthPx = hostWidthPx.roundToInt(),
                            viewportHeightPx = hostHeightPx.roundToInt(),
                            density = hostDensity,
                            appearance = modernPlayerAppearance
                        )
                    }
                    val artworkPalette = rememberModernArtworkPalette(
                        song = currentSong,
                        fallbackAccent = modernStyle.accentColor
                    )
                    val expandedArtworkRequestSizePx =
                        defaultMorphBounds.expandedArtwork?.let { bounds ->
                            maxOf(bounds.width, bounds.height).roundToInt().coerceAtLeast(1)
                        } ?: artworkPreloadPolicy.targetSizePx
                    ModernExpandedArtworkPreloader(
                        currentSong = currentSong,
                        previousSong = previousPreviewSong,
                        nextSong = nextPreviewSong,
                        targetSizePx = expandedArtworkRequestSizePx,
                        includeCurrentSong = geometry == null
                    )
                    val carouselPresentation =
                        rememberModernArtworkCarouselPresentation(
                            currentSong = currentSong,
                            previousPreviewSong = previousPreviewSong,
                            nextPreviewSong = nextPreviewSong,
                            onPreviousClick = onPreviousClick,
                            onNextClick = onNextClick
                        )
                    DefaultPlayerMorph(
                        progress = playerMorphState.progress,
                        geometry = geometry,
                        carouselPresentation = carouselPresentation,
                        artworkTransitionStyle = modernArtworkTransitionStyle,
                        isPlaying = isPlaying,
                        onPlayPauseClick = onPlayPauseClick,
                        style = modernStyle,
                        appearance = modernPlayerAppearance,
                        artworkPalette = artworkPalette,
                        expandedArtworkRequestSizePx = expandedArtworkRequestSizePx
                    ) { visualState ->
                        ModernExpandedPlayer(
                            currentSong = currentSong,
                            previousPreviewSong = previousPreviewSong,
                            nextPreviewSong = nextPreviewSong,
                            artworkTransitionStyle = modernArtworkTransitionStyle,
                            appearance = modernPlayerAppearance,
                            waveformData = waveformData,
                            artworkPalette = artworkPalette,
                            isPlaying = isPlaying,
                            isShuffleEnabled = isShuffleEnabled,
                            repeatMode = repeatMode,
                            currentPosition = currentPosition,
                            duration = duration,
                            isCurrentSongFavorite = isCurrentSongFavorite,
                            onPlayPauseClick = onPlayPauseClick,
                            onPreviousClick = onPreviousClick,
                            onNextClick = onNextClick,
                            onSeekChange = onSeekChange,
                            onShuffleClick = onShuffleClick,
                            onRepeatClick = onRepeatClick,
                            onCollapseClick = onCollapseClick,
                            playerMorphState = playerMorphState,
                            lyricsTransitionState = lyricsTransitionState,
                            onOpenUpNextClick = onOpenQueueHubClick,
                            onToggleFavoriteClick = onToggleFavoriteClick,
                            style = modernStyle,
                            defaultMorphBounds = defaultMorphBounds,
                            defaultMorphVisualState = visualState,
                            carouselPresentation = carouselPresentation,
                            defaultMorphDragRangePx = defaultMorphTravelDistance(
                                endpointBounds = endpointBounds,
                                elementBounds = defaultMorphBounds
                            )
                        )
                    }
                }
            }

            PlayerTheme.CLASSIC_WHEEL -> {
                val ownsNowPlayingMorphContent =
                    classicWheelMenuState.currentScreen.ownsNowPlayingMorphContent()
                val geometry = resolveClassicWheelMorphGeometry(
                    playerMorphState.progress, endpointBounds
                )
                val sharedGeometry = resolveClassicWheelSharedGeometry(
                    playerMorphState.progress, classicMorphBounds
                )
                val ownedSharedGeometry = sharedGeometry.takeIf {
                    ownsNowPlayingMorphContent
                }
                val playPauseOwnership = classicWheelPlayPauseVisualOwnership(
                    playerMorphState.progress
                )
                val displayLyricsGestureModifier = if (
                    classicWheelMenuState.currentScreen.allowsLyricsSwipe()
                ) {
                    openLyricsSemanticsModifier.then(lyricsDragModifier)
                } else {
                    Modifier
                }
                ClassicWheelPlayerMorph(
                    progress = playerMorphState.progress,
                    geometry = geometry,
                    sharedGeometry = ownedSharedGeometry,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    sharedPlayPauseAlpha = playPauseOwnership.sharedAlpha,
                    tokens = tokens
                ) { screenAlpha, wheelAlpha, controlsActive -> ClassicWheelExpandedPlayer(
                    currentSong = currentSong,
                    menuState = classicWheelMenuState,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    currentPosition = currentPosition,
                    duration = duration,
                    isCurrentSongFavorite = isCurrentSongFavorite,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onCollapseClick = onCollapseClick,
                    onOpenUpNextClick = onOpenQueueHubClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    songs = songs,
                    onSongClick = onSongClick,
                    tokens = tokens,
                    screenAlpha = screenAlpha,
                    wheelAlpha = wheelAlpha,
                    wheelInputEnabled = controlsActive,
                    morphBounds = classicMorphBounds,
                    sharedContentVisible = ownedSharedGeometry == null,
                    onMorphDragStart = {
                        playerMorphState.beginDragWithRange(classicWheelMorphTravelDistance(endpointBounds))
                    },
                    onMorphDragBy = playerMorphState::dragBy,
                    onMorphDragEnd = playerMorphState::endDrag,
                    onMorphDragCancel = playerMorphState::cancelDrag,
                    wheelPlayControlAlpha = playPauseOwnership.expandedAlpha,
                    displayLyricsGestureModifier = displayLyricsGestureModifier
                ) }
            }

            PlayerTheme.RETRO_RACK -> {
                val geometry = resolveRetroRackMorphGeometry(playerMorphState.progress, endpointBounds)
                val sharedGeometry = resolveRetroRackSharedGeometry(playerMorphState.progress, retroRackMorphBounds)
                val sharedOwner = retroRackSharedOwner(playerMorphState.progress, sharedGeometry != null)
                RetroRackPlayerMorph(
                    progress = playerMorphState.progress,
                    geometry = geometry,
                    sharedGeometry = sharedGeometry,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPauseClick = onPlayPauseClick,
                    tokens = tokens
                ) { deckReveal, spectrumReveal, queueReveal, controlsReveal, inputEnabled ->
                    RetroRackExpandedPlayer(
                        currentSong = currentSong,
                        waveformData = waveformData,
                        isVisualizerWorkAllowed = isVisualizerWorkAllowed && shouldRunRetroRackExpandedWork(playerMorphState.progress),
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        currentPosition = currentPosition,
                        duration = duration,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        upcomingSongs = upcomingSongs,
                        activeQueueSongs = activeQueueSongs,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekChange = onSeekChange,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                        onCollapseClick = onCollapseClick,
                        onOpenUpNextClick = onOpenQueueHubClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        onSongClick = onSongClick,
                        tokens = tokens,
                        deckReveal = deckReveal,
                        spectrumReveal = spectrumReveal,
                        queueReveal = queueReveal,
                        controlsReveal = controlsReveal,
                        inputEnabled = inputEnabled,
                        morphBounds = retroRackMorphBounds,
                        sharedOwner = sharedOwner,
                        onMorphDragStart = {
                            val travel = retroRackMorphTravelDistance(endpointBounds)
                            playerMorphState.beginDragWithRange(
                                progressRangePx = travel,
                                distanceThresholdPx = retroRackDistanceThreshold(travel)
                            )
                        },
                        onMorphDragBy = playerMorphState::dragBy,
                        onMorphDragEnd = { velocity ->
                            playerMorphState.endDragWithVelocityThreshold(
                                velocity,
                                RetroRackMorphSpec.collapseVelocityThresholdPxPerSecond
                            )
                        },
                        onMorphDragCancel = playerMorphState::cancelDrag
                    ) }
            }

            PlayerTheme.POCKET_FLIP -> {
                val geometry = resolvePocketFlipMorphGeometry(
                    progress = playerMorphState.progress,
                    endpointBounds = endpointBounds
                )
                val sharedGeometry = resolvePocketFlipSharedGeometry(
                    progress = playerMorphState.progress,
                    bounds = pocketFlipMorphBounds
                )
                val sharedOwner = pocketFlipSharedOwner(
                    progress = playerMorphState.progress,
                    geometryReady = sharedGeometry != null
                )
                val collapseGestureEnabled =
                    playerMorphState.progress >= PocketFlipMorphSpec.collapseGestureAt ||
                            playerMorphState.isDragging

                PocketFlipPlayerMorph(
                    progress = playerMorphState.progress,
                    geometry = geometry,
                    sharedGeometry = sharedGeometry,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPauseClick = onPlayPauseClick,
                    tokens = tokens
                ) { displayReveal, hingeReveal, controlsReveal, inputEnabled ->
                    PocketFlipExpandedPlayer(
                        currentSong = currentSong,
                        waveformData = waveformData,
                        isVisualizerWorkAllowed = isVisualizerWorkAllowed &&
                                shouldRunPocketFlipExpandedWork(playerMorphState.progress),
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        currentPosition = currentPosition,
                        duration = duration,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekChange = onSeekChange,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                        onCollapseClick = onCollapseClick,
                        onOpenUpNextClick = onOpenQueueHubClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        tokens = tokens,
                        renderShell = false,
                        displayReveal = displayReveal,
                        hingeReveal = hingeReveal,
                        controlsReveal = controlsReveal,
                        inputEnabled = inputEnabled,
                        collapseGestureEnabled = collapseGestureEnabled,
                        morphBounds = pocketFlipMorphBounds,
                        sharedOwner = sharedOwner,
                        onMorphDragStart = {
                            val travel = pocketFlipMorphTravelDistance(endpointBounds)
                            playerMorphState.beginDragWithRange(
                                progressRangePx = travel,
                                distanceThresholdPx = pocketFlipDistanceThreshold(travel)
                            )
                        },
                        onMorphDragBy = playerMorphState::dragBy,
                        onMorphDragEnd = { velocity ->
                            playerMorphState.endDragWithVelocityThreshold(
                                velocityY = velocity,
                                velocityThresholdPxPerSecond =
                                    PocketFlipMorphSpec.collapseVelocityThresholdPxPerSecond
                            )
                        },
                        onMorphDragCancel = playerMorphState::cancelDrag
                    )
                }
            }

            PlayerTheme.POCKET_CASSETTE -> {
                val geometry = resolvePocketCassetteMorphGeometry(
                    progress = playerMorphState.progress,
                    endpointBounds = endpointBounds
                )
                val sharedGeometry = resolvePocketCassetteSharedGeometry(
                    progress = playerMorphState.progress,
                    bounds = pocketCassetteMorphBounds
                )
                val sharedOwner = pocketCassetteSharedOwner(
                    progress = playerMorphState.progress,
                    geometryReady = sharedGeometry != null
                )
                val collapseGestureEnabled =
                    playerMorphState.progress >= PocketCassetteMorphSpec.collapseGestureAt ||
                            playerMorphState.isDragging

                PocketCassettePlayerMorph(
                    progress = playerMorphState.progress,
                    geometry = geometry,
                    sharedGeometry = sharedGeometry,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPauseClick = onPlayPauseClick,
                    tokens = tokens
                ) { headerReveal, windowReveal, mechanismReveal, controlsReveal, inputEnabled ->
                    PocketCassetteExpandedPlayer(
                        currentSong = currentSong,
                        isVisualizerWorkAllowed = isVisualizerWorkAllowed &&
                                shouldRunPocketCassetteExpandedWork(playerMorphState.progress),
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        currentPosition = currentPosition,
                        duration = duration,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekChange = onSeekChange,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                        onCollapseClick = onCollapseClick,
                        onOpenUpNextClick = onOpenQueueHubClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        tokens = tokens,
                        renderShell = false,
                        headerReveal = headerReveal,
                        windowReveal = windowReveal,
                        mechanismReveal = mechanismReveal,
                        controlsReveal = controlsReveal,
                        inputEnabled = inputEnabled,
                        collapseGestureEnabled = collapseGestureEnabled,
                        morphBounds = pocketCassetteMorphBounds,
                        sharedOwner = sharedOwner,
                        onMorphDragStart = {
                            val travel = pocketCassetteMorphTravelDistance(endpointBounds)
                            playerMorphState.beginDragWithRange(
                                progressRangePx = travel,
                                distanceThresholdPx = pocketCassetteDistanceThreshold(travel)
                            )
                        },
                        onMorphDragBy = playerMorphState::dragBy,
                        onMorphDragEnd = { velocity ->
                            playerMorphState.endDragWithVelocityThreshold(
                                velocityY = velocity,
                                velocityThresholdPxPerSecond =
                                    PocketCassetteMorphSpec.collapseVelocityThresholdPxPerSecond
                            )
                        },
                        onMorphDragCancel = playerMorphState::cancelDrag
                    )
                }
            }

            PlayerTheme.POCKET_DISC -> {
                val albumDurationMs = remember(currentSong, songs) {
                    currentSong
                        ?.let { song ->
                            findLibraryAlbumGroupForSong(
                                song = song,
                                albums = buildLibraryAlbumGroups(songs)
                            )
                        }
                        ?.songs
                        ?.sumOf { song -> song.duration.coerceAtLeast(0L) }
                        ?: 0L
                }
                val geometry = resolvePocketDiscMorphGeometry(
                    progress = playerMorphState.progress,
                    endpointBounds = endpointBounds
                )
                val sharedGeometry = resolvePocketDiscSharedGeometry(
                    progress = playerMorphState.progress,
                    bounds = pocketDiscMorphBounds
                )
                val sharedOwner = pocketDiscSharedOwner(
                    progress = playerMorphState.progress,
                    geometryReady = sharedGeometry != null
                )
                val collapseGestureEnabled =
                    playerMorphState.progress >= PocketDiscMorphSpec.collapseGestureAt ||
                            playerMorphState.isDragging

                PocketDiscPlayerMorph(
                    progress = playerMorphState.progress,
                    geometry = geometry,
                    sharedGeometry = sharedGeometry,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPauseClick = onPlayPauseClick,
                    tokens = tokens
                ) { headerReveal, mediaReveal, panelReveal, controlsReveal, inputEnabled ->
                    PocketDiscExpandedPlayer(
                        currentSong = currentSong,
                        activeQueueName = activeQueueName,
                        activeQueuePosition = activeQueuePosition,
                        activeQueueCount = activeQueueCount,
                        albumDurationMs = albumDurationMs,
                        waveformData = waveformData,
                        isVisualizerWorkAllowed = isVisualizerWorkAllowed &&
                                shouldRunPocketDiscExpandedWork(playerMorphState.progress),
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        currentPosition = currentPosition,
                        duration = duration,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekChange = onSeekChange,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                        onCollapseClick = onCollapseClick,
                        onOpenQueueHubClick = onOpenQueueHubClick,
                        onOpenAlbumClick = {
                            currentSong?.let(onOpenCurrentAlbumClick)
                        },
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        tokens = tokens,
                        renderShell = false,
                        headerReveal = headerReveal,
                        mediaReveal = mediaReveal,
                        panelReveal = panelReveal,
                        controlsReveal = controlsReveal,
                        inputEnabled = inputEnabled,
                        collapseGestureEnabled = collapseGestureEnabled,
                        morphBounds = pocketDiscMorphBounds,
                        sharedOwner = sharedOwner,
                        onMorphDragStart = {
                            val travel = pocketDiscMorphTravelDistance(endpointBounds)
                            playerMorphState.beginDragWithRange(
                                progressRangePx = travel,
                                distanceThresholdPx = pocketDiscDistanceThreshold(travel)
                            )
                        },
                        onMorphDragBy = playerMorphState::dragBy,
                        onMorphDragEnd = { velocity ->
                            playerMorphState.endDragWithVelocityThreshold(
                                velocityY = velocity,
                                velocityThresholdPxPerSecond =
                                    PocketDiscMorphSpec.collapseVelocityThresholdPxPerSecond
                            )
                        },
                        onMorphDragCancel = playerMorphState::cancelDrag
                    )
                }
            }

        }

    }
}
