package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.audioquality.AudioQualityRepository
import io.github.rsgarrido.sazanami.player.waveform.WaveformData
import io.github.rsgarrido.sazanami.player.waveform.WaveformRepository
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState

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
    onOpenAlbumClick: (() -> Unit)? = null,
    onOpenArtistClick: (() -> Unit)? = null,
    onTrackInfoClick: (() -> Unit)? = null,
    onViewArtwork: ((Song) -> Unit)? = null,
    playerMorphState: PlayerMorphState,
    lyricsTransitionState: PlayerLyricsTransitionState,
    onOpenUpNextClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onOpenMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    style: ModernPlayerStyle = ModernPlayerDefaults.style(),
    albumArtSize: Dp = ModernPlayerDefaults.MaximumArtworkSize,
    defaultPlayerLayout: DefaultPlayerLayout? = null,
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

    val verticalScrollState = rememberScrollState()
    var containerHeightPx by remember { mutableFloatStateOf(1f) }
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
        val foregroundAlbumArtSize = defaultPlayerLayout?.artworkSize
            ?: verticalDefaultArtworkSize(maxWidth, maxHeight, appearance, albumArtSize)
        val balanced = defaultPlayerLayout?.arrangement == DefaultPlayerArrangement.Balanced

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
                .then(
                    rememberModernPlayerVerticalDragModifier(
                        playerMorphState, lyricsTransitionState,
                        containerHeightPx, defaultMorphDragRangePx
                    )
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

            // Layout slots share the existing callbacks, carousel and visual-ownership policies.
            val artwork: @Composable () -> Unit = {
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
                                coordinates.defaultPlayerDestinationBounds(defaultPlayerLayout?.scrollVertical == true)
                            )
                        }
                        .hiddenFromDefaultMorph(morphOwnsPersistentContent),
                    gesturesEnabled = !lyricsTransitionState.lyricsInteractive,
                    renderArtwork = defaultMorphVisualState == null,
                    onViewArtwork = modernArtworkClickCallback(
                        onViewArtwork, displayedCarouselSongs.current, currentSong,
                        playerMorphState, lyricsTransitionState, carouselState,
                        hasVisibleOwner = defaultMorphVisualState == null
                    )
                )
            }
            val metadata: @Composable () -> Unit = {
                ModernPlayerMetadataCarousel(
                    carouselSongs = displayedCarouselSongs,
                    carouselState = carouselState,
                    audioQualityRepository = audioQualityRepository,
                    transitionStyle = artworkTransitionStyle,
                    style = style,
                    layoutAppearance = appearance.layout,
                    unclippedDestinationBounds = defaultPlayerLayout?.scrollVertical == true,
                    modifier = Modifier.fillMaxWidth(),
                    onPersistentContentBoundsChanged = { bounds ->
                        defaultMorphBounds?.updateExpandedText(bounds)
                    },
                    hidePersistentContent = morphOwnsPersistentContent,
                    onOpenAlbumClick = onOpenAlbumClick,
                    onTrackInfoClick = onTrackInfoClick.takeIf {
                        canOpenModernMore(
                            playerMorphState, lyricsTransitionState, carouselState.offsetX,
                            hasExpandedContent = defaultMorphVisualState == null ||
                                    defaultMorphVisualState.isReady && defaultMorphVisualState.metadataAlpha == 1f,
                            isCurrentTrackDisplayed = displayedCarouselSongs.current.membershipKey() ==
                                    currentSong.membershipKey()
                        )
                    },
                    onOpenArtistClick = modernArtistClickCallback(
                        onClick = onOpenArtistClick,
                        playerMorphState = playerMorphState,
                        lyricsTransitionState = lyricsTransitionState,
                        carouselOffsetX = carouselState.offsetX,
                        hasVisibleContent = defaultMorphVisualState == null,
                        isCurrentTrackDisplayed = displayedCarouselSongs.current.membershipKey() ==
                                currentSong.membershipKey()
                    ),
                    expandedContentAlpha =
                        defaultMorphVisualState?.metadataAlpha ?: 1f,
                    loadExpandedMetadata =
                        defaultMorphVisualState?.expensiveContentActive ?: true
                )
            }
            val actions: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .suppressDefaultMorphSemantics(
                            defaultMorphVisualState != null &&
                                    playerMorphState.settledPresentation !=
                                    PlayerPresentation.Expanded
                        ),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onOpenMoreClick != null) {
                        ModernMoreButton(
                            onClick = onOpenMoreClick,
                            enabled = canOpenModernMore(
                                playerMorphState = playerMorphState,
                                lyricsTransitionState = lyricsTransitionState,
                                carouselOffsetX = carouselState.offsetX,
                                hasExpandedContent = defaultMorphVisualState?.isReady != false,
                                isCurrentTrackDisplayed = displayedCarouselSongs.current.membershipKey() ==
                                        currentSong.membershipKey()
                            ),
                            tint = style.contentColor
                        )
                    }
                    ModernQueueHubButton(
                        onClick = onOpenUpNextClick,
                        enabled = defaultMorphVisualState == null ||
                                playerMorphState.settledPresentation ==
                                PlayerPresentation.Expanded,
                        tint = style.contentColor
                    )
                }
            }
            val flexibleGap: @Composable ColumnScope.() -> Unit = {
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
            }
            val seekbar: @Composable () -> Unit = {
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
            }
            val controls: @Composable () -> Unit = {
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
                                coordinates.defaultPlayerDestinationBounds(defaultPlayerLayout?.scrollVertical == true)
                            )
                        }
                        .hiddenFromDefaultMorph(morphOwnsPersistentContent),
                    expandedControlsAlpha =
                        defaultMorphVisualState?.controlsAlpha ?: 1f,
                    controlsEnabled = defaultMorphVisualState == null ||
                            playerMorphState.settledPresentation ==
                            PlayerPresentation.Expanded
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (defaultMorphVisualState == null) {
                            alpha = 1f - dragProgress * 0.18f
                            translationY = dragProgress * 14.dp.toPx()
                        }
                    }
                    .then(if (balanced || defaultPlayerLayout?.scrollVertical == true) {
                        Modifier.padding(requireNotNull(defaultPlayerLayout).safePadding)
                    } else {
                        Modifier.statusBarsPadding().navigationBarsPadding()
                    })
                    .padding(
                        horizontal = ModernPlayerDefaults.ContentHorizontalPadding,
                        vertical = ModernPlayerDefaults.ContentVerticalPadding
                    )
            ) {
                if (balanced) {
                    val layout = requireNotNull(defaultPlayerLayout)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(layout.regionGap)
                    ) {
                        artwork()
                        Column(
                            modifier = Modifier.width(layout.detailsWidth).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            metadata()
                            actions()
                            lyricsContent()
                            // Density retains its flexible-gap intent inside the right region.
                            flexibleGap()
                            seekbar()
                            Spacer(Modifier.height(18.dp))
                            Box(Modifier.width(minOf(layout.detailsWidth,
                                (appearance.controls.size.primarySizeDp +
                                    4 * appearance.controls.size.modeContainerSizeDp + 96).dp))) {
                                controls()
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().then(
                            if (defaultPlayerLayout?.scrollVertical == true)
                                Modifier.verticalScroll(verticalScrollState) else Modifier
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        artwork()
                        Spacer(Modifier.height(24.dp))
                        metadata()
                        actions()
                        lyricsContent()
                        flexibleGap()
                        seekbar()
                        Spacer(Modifier.height(18.dp))
                        controls()
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

internal fun canOpenModernMore(
    playerMorphState: PlayerMorphState,
    lyricsTransitionState: PlayerLyricsTransitionState,
    carouselOffsetX: Float,
    hasExpandedContent: Boolean,
    isCurrentTrackDisplayed: Boolean
): Boolean = hasExpandedContent && isCurrentTrackDisplayed &&
        playerMorphState.targetPresentation == PlayerPresentation.Expanded &&
        playerMorphState.settledPresentation == PlayerPresentation.Expanded &&
        playerMorphState.progress == 1f && !playerMorphState.isDragging &&
        !playerMorphState.isAnimating && !lyricsTransitionState.isDragging &&
        !lyricsTransitionState.lyricsOwnsInput && carouselOffsetX == 0f

@Composable
internal fun ModernMoreButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    // Keep Queue Hub's existing 42 dp row geometry while giving More a 48 dp touch target.
    // Reserve the slot during transitions without exposing an inactive action.
    Box(
        modifier = modifier.size(width = 48.dp, height = 42.dp),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            IconButton(onClick = onClick, modifier = Modifier.requiredSize(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.player_more_actions),
                    tint = tint
                )
            }
        }
    }
}

@Composable
internal fun ModernQueueHubButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.QueueMusic,
            contentDescription = stringResource(R.string.player_open_queues),
            tint = tint
        )
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
