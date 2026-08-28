package com.example.cdplaya.ui.player.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.RepeatMode
import com.example.cdplaya.player.audioquality.AudioQualityRepository
import com.example.cdplaya.player.waveform.WaveformData
import com.example.cdplaya.player.waveform.WaveformRepository
import com.example.cdplaya.ui.player.PlayerMorphState
import com.example.cdplaya.ui.player.PlayerPresentation
import com.example.cdplaya.ui.player.PlayerLyricsTransitionState

@Composable
internal fun ModernExpandedPlayer(
    currentSong: Song?,
    previousPreviewSong: Song? = null,
    nextPreviewSong: Song? = null,
    artworkTransitionStyle: ModernArtworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
    appearance: ModernPlayerAppearance = ModernPlayerAppearance.Default,
    waveformData: WaveformData? = null,
    artworkPalette: ModernArtworkPalette? = null,
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
    onOpenUpNextClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    style: ModernPlayerStyle = ModernPlayerDefaults.style(),
    albumArtSize: Dp = ModernPlayerDefaults.MaximumArtworkSize,
    defaultMorphBounds: DefaultPlayerMorphBounds? = null,
    defaultMorphVisualState: DefaultPlayerMorphVisualState? = null,
    defaultMorphDragRangePx: Float? = null,
    carouselPresentation: ModernArtworkCarouselPresentation? = null,
    lyricsContent: @Composable () -> Unit = {}
) {
    if (currentSong == null) {
        return
    }

    val context = LocalContext.current
    val rememberedArtworkPalette = if (artworkPalette == null) {
        rememberModernArtworkPalette(currentSong, style.accentColor)
    } else {
        null
    }
    val resolvedArtworkPalette = artworkPalette ?: requireNotNull(rememberedArtworkPalette)
    val audioQualityRepository = remember(context) { AudioQualityRepository(context) }
    val ownedCarouselPresentation =
        if (carouselPresentation == null) {
            rememberModernArtworkCarouselPresentation(
                currentSong = currentSong,
                previousPreviewSong = previousPreviewSong,
                nextPreviewSong = nextPreviewSong,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick
            )
        } else {
            null
        }
    val activeCarouselPresentation =
        carouselPresentation ?: requireNotNull(ownedCarouselPresentation)
    val carouselState = activeCarouselPresentation.state
    val displayedCarouselSongs = activeCarouselPresentation.songs

    var containerHeightPx by remember { mutableFloatStateOf(1f) }
    var isMorphDrag by remember { mutableStateOf(false) }
    val verticalDragState = rememberDraggableState { deltaY ->
        if (playerMorphState.progress < 1f &&
            lyricsTransitionState.progress == 0f
        ) {
            isMorphDrag = true
            playerMorphState.dragBy(deltaY)
        } else if (deltaY < 0f || lyricsTransitionState.progress > 0f) {
            if (lyricsTransitionState.progress == 0f) {
                lyricsTransitionState.beginOpeningDrag()
            }
            lyricsTransitionState.dragOpeningBy(deltaY, containerHeightPx)
            playerMorphState.updateProgressFromDrag(1f)
        } else {
            isMorphDrag = true
            playerMorphState.dragBy(deltaY)
        }
    }
    val dragProgress = 1f - playerMorphState.progress
    val morphOwnsPersistentContent = defaultMorphVisualState?.isReady == true

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = if (defaultMorphVisualState == null) {
                        0.24f * (1f - dragProgress)
                    } else {
                        0f
                    }
                )
            )
            .onSizeChanged { size ->
                containerHeightPx = size.height.toFloat().coerceAtLeast(1f)
            }
    ) {
        val seekbarHeightBudget = if (appearance.seekbar.style.usesWaveformData) {
            (appearance.seekbar.waveformSize.trackHeightDp + 36).dp
        } else {
            64.dp
        }
        val reservedContentHeight = 210.dp +
                appearance.controls.size.primarySizeDp.dp +
                seekbarHeightBudget +
                appearance.layout.density.minimumFlexibleGapDp.dp +
                (if (appearance.layout.showAudioQualityBadge) 36.dp else 0.dp)
        val artworkHeightBudget = (maxHeight - reservedContentHeight).coerceAtLeast(112.dp)
        val foregroundAlbumArtSize = minOf(
            albumArtSize * appearance.artwork.size.maximumScale,
            maxWidth - 32.dp,
            maxHeight * appearance.artwork.size.maximumHeightFraction,
            artworkHeightBudget
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (defaultMorphVisualState == null) {
                        translationY = dragProgress * containerHeightPx * 0.46f
                        val contentScale = 1f - dragProgress * 0.04f
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = 1f - dragProgress * 0.1f
                        shape = RoundedCornerShape(28.dp * dragProgress)
                        clip = dragProgress > 0f
                    }
                }
                .background(
                    if (defaultMorphVisualState == null) {
                        style.backgroundColor
                    } else {
                        Color.Transparent
                    }
                )
                .draggable(
                    state = verticalDragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        isMorphDrag = playerMorphState.progress < 1f
                        val morphDragRange = defaultMorphDragRangePx
                        if (morphDragRange != null) {
                            playerMorphState.beginDragWithRange(morphDragRange)
                        } else {
                            playerMorphState.beginDrag(containerHeightPx)
                        }
                    },
                    enabled = !lyricsTransitionState.lyricsInteractive,
                    onDragStopped = { velocityY ->
                        if (!isMorphDrag && (
                            lyricsTransitionState.progress > 0f ||
                            playerMorphState.progress >= 1f &&
                            velocityY <=
                            PlayerLyricsTransitionState.OPEN_VELOCITY_PX_PER_SECOND
                            )
                        ) {
                            playerMorphState.snapTo(PlayerPresentation.Expanded)
                            lyricsTransitionState.settleOpening(velocityY)
                        } else {
                            playerMorphState.endDrag(velocityY)
                        }
                        isMorphDrag = false
                    }
                )
        ) {
            if (defaultMorphVisualState == null ||
                defaultMorphVisualState.expensiveContentActive
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = defaultMorphVisualState?.backgroundAlpha ?: 1f
                        }
                ) {
                    ModernPlayerBackground(
                        currentSong = currentSong,
                        style = style,
                        appearance = appearance.background,
                        artworkPalette = resolvedArtworkPalette
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (defaultMorphVisualState == null) {
                            alpha = 1f - dragProgress * 0.18f
                            translationY = dragProgress * 14.dp.toPx()
                        }
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = ModernPlayerDefaults.ContentHorizontalPadding,
                        vertical = ModernPlayerDefaults.ContentVerticalPadding
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ModernPlayerArtwork(
                    carouselSongs = displayedCarouselSongs,
                    carouselState = carouselState,
                    artworkSize = foregroundAlbumArtSize,
                    transitionStyle = artworkTransitionStyle,
                    style = style,
                    appearance = appearance.artwork,
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            defaultMorphBounds?.updateExpandedArtwork(
                                coordinates.boundsInRoot()
                            )
                        }
                        .hiddenFromDefaultMorph(morphOwnsPersistentContent),
                    gesturesEnabled = !lyricsTransitionState.lyricsInteractive,
                    renderArtwork = defaultMorphVisualState == null
                )

                Spacer(modifier = Modifier.height(24.dp))

                ModernPlayerMetadataCarousel(
                    carouselSongs = displayedCarouselSongs,
                    carouselState = carouselState,
                    audioQualityRepository = audioQualityRepository,
                    transitionStyle = artworkTransitionStyle,
                    style = style,
                    layoutAppearance = appearance.layout,
                    modifier = Modifier.fillMaxWidth(),
                    onPersistentContentBoundsChanged = { bounds ->
                        defaultMorphBounds?.updateExpandedText(bounds)
                    },
                    hidePersistentContent = morphOwnsPersistentContent,
                    expandedContentAlpha =
                        defaultMorphVisualState?.metadataAlpha ?: 1f,
                    loadExpandedMetadata =
                        defaultMorphVisualState?.expensiveContentActive ?: true
                )

                lyricsContent()

                when (appearance.layout.density) {
                    ModernLayoutDensity.COMPACT -> Spacer(modifier = Modifier.height(16.dp))
                    ModernLayoutDensity.BALANCED -> Spacer(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(min = 18.dp, max = 72.dp)
                    )
                    ModernLayoutDensity.RELAXED -> Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 24.dp)
                    )
                }

                ModernPlayerSeekBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeekChange = if (defaultMorphVisualState == null ||
                        playerMorphState.settledPresentation ==
                        PlayerPresentation.Expanded
                    ) {
                        onSeekChange
                    } else {
                        {}
                    },
                    appearance = appearance.seekbar,
                    waveformSeed = "${currentSong.id}|${currentSong.filePath}|${currentSong.title}",
                    waveformData = waveformData,
                    artworkPalette = resolvedArtworkPalette,
                    style = style,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = defaultMorphVisualState?.metadataAlpha ?: 1f
                        }
                        .suppressDefaultMorphSemantics(
                            defaultMorphVisualState != null &&
                                    playerMorphState.settledPresentation !=
                                    PlayerPresentation.Expanded
                        )
                )

                Spacer(modifier = Modifier.height(18.dp))

                ModernPlayerControls(
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = activeCarouselPresentation.onPreviousButtonClick,
                    onNextClick = activeCarouselPresentation.onNextButtonClick,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    style = style,
                    appearance = appearance.controls,
                    artworkPalette = resolvedArtworkPalette,
                    modifier = Modifier.suppressDefaultMorphSemantics(
                        defaultMorphVisualState != null &&
                                playerMorphState.settledPresentation !=
                                PlayerPresentation.Expanded
                    ),
                    primaryControlModifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            defaultMorphBounds?.updateExpandedPlayPause(
                                coordinates.boundsInRoot()
                            )
                        }
                        .hiddenFromDefaultMorph(morphOwnsPersistentContent),
                    expandedControlsAlpha =
                        defaultMorphVisualState?.controlsAlpha ?: 1f,
                    controlsEnabled = defaultMorphVisualState == null ||
                            playerMorphState.settledPresentation ==
                            PlayerPresentation.Expanded
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

internal fun selectNearbyWaveformSongs(
    currentSong: Song,
    nextSong: Song?,
    previousSong: Song?
): List<Song> {
    return listOfNotNull(nextSong, previousSong)
        .asSequence()
        .filterNot { song ->
            song.id == currentSong.id && song.filePath == currentSong.filePath
        }
        .distinctBy { song -> song.id to song.filePath }
        .take(WaveformRepository.MAX_PREFETCH_COUNT)
        .toList()
}
