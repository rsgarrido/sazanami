package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import io.github.rsgarrido.sazanami.ui.player.RetainedArtworkImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.fillRetroRackSpectrum
import io.github.rsgarrido.sazanami.ui.player.rememberSpectrumVisualizerState
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.VisualizerPerformanceCounters
import io.github.rsgarrido.sazanami.performance.tracePerformance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput
import kotlin.math.abs

@Composable
fun RetroRackExpandedPlayer(
    currentSong: Song?,
    isVisualizerWorkAllowed: Boolean = true,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    isCurrentSongFavorite: Boolean,
    upcomingSongs: List<Song>,
    activeQueueSongs: List<Song> = listOfNotNull(currentSong) + upcomingSongs,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    tokens: PlayerThemeTokens = RetroRackDefaultTokens,
    deckReveal: Float = 1f,
    spectrumReveal: Float = 1f,
    queueReveal: Float = 1f,
    controlsReveal: Float = 1f,
    inputEnabled: Boolean = true,
    morphBounds: RetroRackMorphBounds? = null,
    sharedOwner: RetroRackSharedOwner = RetroRackSharedOwner.EXPANDED,
    onMorphDragStart: () -> Unit = {},
    onMorphDragBy: (Float) -> Unit = {},
    onMorphDragEnd: (Float) -> Unit = {},
    onMorphDragCancel: () -> Unit = {}
) {
    val palette = remember(tokens) { RetroRackPalette.from(tokens) }
    val playbackContext = activeQueueSongs
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val layoutProfile = remember(
        configuration.screenHeightDp,
        configuration.screenWidthDp,
        fontScale
    ) {
        buildRetroRackLayoutProfile(
            screenHeightDp = configuration.screenHeightDp,
            screenWidthDp = configuration.screenWidthDp,
            fontScale = fontScale
        )
    }
    val compact = layoutProfile.compact
    val visualProfile = remember(
        currentSong?.artist,
        currentSong?.album
    ) {
        buildRetroRackVisualProfile(
            artist = currentSong?.artist,
            album = currentSong?.album
        )
    }
    val safeHeaderGesture = Modifier.retroRackSafeCollapseGesture(
        onMorphDragStart, onMorphDragBy, onMorphDragEnd, onMorphDragCancel
    )

    CompositionLocalProvider(LocalRetroRackPalette provides palette) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (sharedOwner == RetroRackSharedOwner.EXPANDED) RackBackground
                else Color.Transparent
            )
            .padding(
                horizontal = if (compact) 5.dp else 8.dp,
                vertical = if (compact) 6.dp else 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)
    ) {
        RackModule(
            title = "MAIN DECK",
            modifier = Modifier
                .height(layoutProfile.mainDeckHeightDp.dp)
                .graphicsLayer { alpha = deckReveal },
            titleModifier = safeHeaderGesture,
            trailingAction = {
                RackIconButton(
                    icon = Icons.Filled.Close,
                    label = "CLOSE",
                    compact = true,
                    dense = true,
                    onClick = onCollapseClick,
                    modifier = Modifier.playerEndpointInput(inputEnabled)
                )
            }
        ) {
            MainDeck(
                currentSong = currentSong,
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
                onToggleFavoriteClick = onToggleFavoriteClick,
                compact = compact,
                controlsReveal = controlsReveal,
                inputEnabled = inputEnabled,
                morphBounds = morphBounds,
                sharedOwner = sharedOwner,
                displayHeight = layoutProfile.displayHeightDp.dp,
                modifier = safeHeaderGesture
            )
        }

        RackModule(
            title = "SPECTRUM MONITOR",
            modifier = Modifier
                .height(layoutProfile.spectrumHeightDp.dp)
                .graphicsLayer {
                    alpha = spectrumReveal
                    scaleY = .92f + .08f * spectrumReveal
                },
            titleModifier = safeHeaderGesture,
            trailingAction = {
                RackIndicator(color = visualProfile.accent)
            }
        ) {
            DecorativeSpectrum(
                profile = visualProfile,
                isVisualizerWorkAllowed = isVisualizerWorkAllowed,
                compact = compact,
                modifier = Modifier.fillMaxSize().then(safeHeaderGesture)
            )
        }

        RackModule(
            title = "PLAYBACK RACK // ${playbackContext.size.toString().padStart(2, '0')} TRACKS",
            modifier = Modifier.weight(1f).graphicsLayer { alpha = queueReveal; scaleY = .9f + .1f * queueReveal },
            titleModifier = safeHeaderGesture,
            trailingAction = {
                RackIconButton(
                    icon = Icons.Filled.List,
                    label = "QUEUE",
                    contentDescription = "Open queues",
                    active = true,
                    compact = true,
                    dense = true,
                    onClick = onOpenUpNextClick,
                    modifier = Modifier.playerEndpointInput(inputEnabled && queueReveal > .99f)
                )
            }
        ) {
            RackPlaylist(
                playbackContext = playbackContext,
                onSongClick = onSongClick,
                inputEnabled = inputEnabled && queueReveal > .99f
            )
        }
    }
    }
}

@Composable
private fun MainDeck(
    currentSong: Song?,
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
    onToggleFavoriteClick: (Song) -> Unit,
    compact: Boolean,
    controlsReveal: Float,
    inputEnabled: Boolean,
    morphBounds: RetroRackMorphBounds?,
    sharedOwner: RetroRackSharedOwner,
    displayHeight: Dp,
    modifier: Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(displayHeight),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(displayHeight)
                    .background(DisplayBlack)
                    .rackBevel()
                    .padding(2.dp)
                    .onGloballyPositioned { morphBounds?.updateExpandedArtwork(it.boundsInRoot()) }
                    .then(modifier)
            ) {
                if (sharedOwner == RetroRackSharedOwner.EXPANDED) {
                    RetainedArtworkImage(
                        model = currentSong?.albumArtUri,
                        contentDescription = "Current album artwork",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(DisplayBlack)
                    .rackBevel()
                    .padding(
                        horizontal = if (compact) 7.dp else 9.dp,
                        vertical = if (compact) 4.dp else 6.dp
                    )
                    .then(modifier),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentSong?.title?.uppercase() ?: "NO TRACK LOADED",
                    color = LcdGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 13.sp else 15.sp,
                    lineHeight = if (compact) 15.sp else 17.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .onGloballyPositioned { morphBounds?.updateExpandedTitle(it.boundsInRoot()) }
                        .sharedEndpointVisual(sharedOwner == RetroRackSharedOwner.EXPANDED)
                        .then(
                            if (sharedOwner == RetroRackSharedOwner.EXPANDED) {
                                Modifier.basicMarquee()
                            } else {
                                Modifier
                            }
                        )
                )
                Text(
                    text = currentSong?.artist?.uppercase().orEmpty(),
                    color = LcdGreenDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = if (compact) 12.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { morphBounds?.updateExpandedArtist(it.boundsInRoot()) }
                        .sharedEndpointVisual(sharedOwner == RetroRackSharedOwner.EXPANDED)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LcdLabel(text = if (isPlaying) "PLAY" else "PAUSE")
                    LcdLabel(text = "320K")
                    LcdLabel(text = "44.1K")
                    Text(
                        text = "${formatRackTime(currentPosition)} / ${formatRackTime(duration)}",
                        color = LcdGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 10.dp else 12.dp))

        RetroRackSeekControl(
            currentPosition = currentPosition,
            duration = duration,
            onSeekChange = onSeekChange,
            inputEnabled = inputEnabled && sharedOwner == RetroRackSharedOwner.EXPANDED,
            visualVisible = sharedOwner == RetroRackSharedOwner.EXPANDED,
            onVisualBoundsChanged = { bounds ->
                morphBounds?.updateExpandedProgress(bounds)
            }
        )

        Spacer(modifier = Modifier.height(if (compact) 7.dp else 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = controlsReveal }.playerEndpointInput(inputEnabled && controlsReveal > .99f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RackIconButton(
                icon = Icons.Filled.Shuffle,
                label = "SHUF",
                active = isShuffleEnabled,
                compact = compact,
                onClick = onShuffleClick
            )
            Spacer(modifier = Modifier.weight(1f))
            RackIconButton(
                icon = Icons.Filled.KeyboardArrowLeft,
                label = "PREV",
                compact = compact,
                onClick = onPreviousClick
            )
            RackIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) "PAUSE" else "PLAY",
                active = true,
                compact = compact,
                primary = true,
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .onGloballyPositioned { morphBounds?.updateExpandedPlay(it.boundsInRoot()) }
                    .sharedEndpointVisual(sharedOwner == RetroRackSharedOwner.EXPANDED)
            )
            RackIconButton(
                icon = Icons.Filled.KeyboardArrowRight,
                label = "NEXT",
                compact = compact,
                onClick = onNextClick
            )
            Spacer(modifier = Modifier.weight(1f))
            RackIconButton(
                icon = Icons.Filled.Repeat,
                label = when (repeatMode) {
                    RepeatMode.OFF -> "REP"
                    RepeatMode.ALL -> "ALL"
                    RepeatMode.ONE -> "ONE"
                },
                active = repeatMode != RepeatMode.OFF,
                compact = compact,
                onClick = onRepeatClick
            )
            RackIconButton(
                icon = if (isCurrentSongFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Filled.FavoriteBorder
                },
                label = "FAV",
                active = isCurrentSongFavorite,
                compact = compact,
                onClick = { currentSong?.let(onToggleFavoriteClick) }
            )
        }
    }
}

@Composable
private fun RetroRackSeekControl(
    currentPosition: Int,
    duration: Int,
    onSeekChange: (Int) -> Unit,
    inputEnabled: Boolean,
    visualVisible: Boolean,
    onVisualBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    val safeDuration = duration.coerceAtLeast(1)
    val safePosition = currentPosition.coerceIn(0, safeDuration)
    val progress = safePosition.toFloat() / safeDuration.toFloat()
    val frameColor = RackShadow
    val channelColor = InactiveTrack
    val progressColor = LcdGreen
    val markerColor = ControlSilver

    val inputModifier = if (inputEnabled) {
        Modifier.pointerInput(safeDuration) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                fun seekTo(x: Float) {
                    val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekChange((fraction * safeDuration).toInt())
                }

                seekTo(down.position.x)
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    pressed = change.pressed
                    if (pressed) {
                        seekTo(change.position.x)
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier
    }
    val semanticsModifier = if (inputEnabled) {
        Modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = safePosition.toFloat(),
                range = 0f..safeDuration.toFloat()
            )
            setProgress { target ->
                onSeekChange(target.coerceIn(0f, safeDuration.toFloat()).toInt())
                true
            }
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(inputModifier)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .onGloballyPositioned { onVisualBoundsChanged(it.boundsInRoot()) }
                .graphicsLayer { alpha = if (visualVisible) 1f else 0f }
        ) {
            val frameInset = 1.dp.toPx()
            val channelInset = 4.dp.toPx()
            val channelHeight = 4.dp.toPx()
            val channelTop = (size.height - channelHeight) / 2f
            val channelWidth = (size.width - channelInset * 2f).coerceAtLeast(1f)

            drawRect(color = frameColor)
            drawRect(
                color = Color.Black,
                topLeft = Offset(frameInset, frameInset),
                size = Size(
                    width = (size.width - frameInset * 2f).coerceAtLeast(1f),
                    height = (size.height - frameInset * 2f).coerceAtLeast(1f)
                )
            )
            drawRect(
                color = channelColor,
                topLeft = Offset(channelInset, channelTop),
                size = Size(channelWidth, channelHeight)
            )
            drawRect(
                color = progressColor,
                topLeft = Offset(channelInset, channelTop),
                size = Size(channelWidth * progress, channelHeight)
            )

            repeat(9) { index ->
                val tickX = channelInset + channelWidth * (index + 1f) / 10f
                drawLine(
                    color = markerColor.copy(alpha = 0.18f),
                    start = Offset(tickX, channelTop),
                    end = Offset(tickX, channelTop + channelHeight),
                    strokeWidth = 1f
                )
            }

            val markerWidth = 5.dp.toPx()
            val markerLeft = (channelInset + channelWidth * progress - markerWidth / 2f)
                .coerceIn(frameInset, size.width - frameInset - markerWidth)
            drawRect(
                color = markerColor,
                topLeft = Offset(markerLeft, frameInset),
                size = Size(markerWidth, size.height - frameInset * 2f)
            )
        }
    }
}

@Composable
private fun DecorativeSpectrum(
    profile: RetroRackVisualProfile,
    isVisualizerWorkAllowed: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val gridColor = LcdGreenDim.copy(alpha = 0.24f)
    val spectrumState = rememberSpectrumVisualizerState(isVisualizerWorkAllowed)
    val meterLevels = remember { FloatArray(RETRO_RACK_VISUALIZER_COLUMN_COUNT) }
    Canvas(
        modifier = modifier
            .background(DisplayBlack)
            .border(1.dp, RackShadow)
            .rackBevel()
            .padding(if (compact) 8.dp else 10.dp)
    ) {
        fillRetroRackSpectrum(spectrumState.value, meterLevels)
        tracePerformance(PerformanceTraceNames.RETRO_RACK_DRAW) {
        VisualizerPerformanceCounters.onDraw()
        repeat(3) { index ->
            val y = size.height * (index + 1f) / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
        val displayLevelCount = meterLevels.size
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (displayLevelCount - 1)) / displayLevelCount
        val segmentGap = (if (compact) 1.5.dp else 2.dp).toPx()
        val segmentHeight = (if (compact) 2.5.dp else 3.dp).toPx()
        repeat(displayLevelCount) { index ->
            val height = size.height * meterLevels[index].coerceIn(0f, 0.98f)
            val segmentStep = segmentHeight + segmentGap
            val segmentCount = (height / segmentStep).toInt().coerceAtLeast(0)
            repeat(segmentCount) { segmentIndex ->
                val segmentTop = size.height - (segmentIndex + 1) * segmentStep
                val isPeak = segmentTop < size.height * 0.18f
                drawRect(
                    color = if (isPeak) profile.peak else profile.accent,
                    topLeft = Offset(index * (barWidth + gap), segmentTop),
                    size = Size(barWidth, segmentHeight)
                )
            }
        }
        }
    }
}

@Composable
private fun RackPlaylist(
    playbackContext: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    inputEnabled: Boolean
) {
    val rows = playbackContext
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .playerEndpointInput(inputEnabled)
            .background(DisplayBlack)
            .rackBevel()
            .padding(vertical = 2.dp)
    ) {
        itemsIndexed(
            items = rows,
            key = { index, song -> "${song.id}:$index" }
        ) { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song, playbackContext) }
                    .background(if (index == 0) SelectedRow else Color.Transparent)
                    .then(
                        if (index == 0) Modifier.border(1.dp, LcdGreenDim)
                        else Modifier
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(if (index == 0) LcdGreen else Color.Transparent)
                )
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = LcdGreenDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 5.dp)
                )
                Text(
                    text = "${song.artist} — ${song.title}",
                    color = if (index == 0) ControlSilver else LcdGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .padding(start = 6.dp)
                ) {
                    Text(
                        text = formatRackTime(song.duration.toInt()),
                        color = if (index == 0) ControlSilver else LcdGreenDim,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun RackModule(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelDark)
            .rackBevel()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(PanelHeader, PanelHeaderEnd, PanelHeader)
                    )
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(RackShadow)
                    .rackBevel(pressed = true)
            )
            Text(
                text = title,
                color = ControlSilver,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.35.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 5.dp)
                    .then(titleModifier)
            )
            trailingAction?.invoke()
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun RackIconButton(
    icon: ImageVector,
    label: String,
    contentDescription: String = label,
    active: Boolean = false,
    compact: Boolean = false,
    primary: Boolean = false,
    dense: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val borderColor = when {
        isPressed -> ControlSilver.copy(alpha = 0.72f)
        active -> LcdGreen
        else -> RackShadow
    }
    val minWidth = when {
        dense -> 36.dp
        primary && compact -> 48.dp
        primary -> 54.dp
        compact -> 40.dp
        else -> 44.dp
    }
    val minHeight = when {
        dense -> 30.dp
        primary && compact -> 42.dp
        primary -> 46.dp
        compact -> 36.dp
        else -> 40.dp
    }
    val iconSize = when {
        dense -> 14.dp
        primary && compact -> 20.dp
        primary -> 22.dp
        compact -> 16.dp
        else -> 18.dp
    }
    Column(
        modifier = modifier
            .sizeIn(
                minWidth = minWidth,
                minHeight = minHeight
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                when {
                    isPressed -> ButtonPressed
                    active -> ActiveButton
                    else -> ButtonFace
                }
            )
            .border(1.dp, borderColor)
            .rackBevel(pressed = isPressed)
            .padding(
                horizontal = if (dense) 4.dp else 5.dp,
                vertical = if (dense) 2.dp else 3.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active && !isPressed) DisplayBlack else ControlSilver,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = label,
            color = if (active && !isPressed) DisplayBlack else ControlSilver,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (dense) 6.sp else 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Modifier.sharedEndpointVisual(visible: Boolean): Modifier =
    if (visible) this else graphicsLayer { alpha = 0f }
        .clearAndSetSemantics { }
        .playerEndpointInput(false)

private fun Modifier.retroRackSafeCollapseGesture(
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
    onCancel: () -> Unit
): Modifier = pointerInput(onStart, onDrag, onEnd, onCancel) {
    var totalX = 0f
    var totalY = 0f
    var owns = false
    val velocity = VelocityTracker()
    detectDragGestures(
        onDragStart = { totalX = 0f; totalY = 0f; owns = false; velocity.resetTracking() },
        onDrag = { change, amount ->
            totalX += amount.x
            totalY += amount.y
            velocity.addPosition(change.uptimeMillis, change.position)
            if (!owns && abs(totalY) > abs(totalX)) { owns = true; onStart() }
            if (owns) { change.consume(); onDrag(amount.y) }
        },
        onDragEnd = { if (owns) onEnd(velocity.calculateVelocity().y) },
        onDragCancel = { if (owns) onCancel() }
    )
}

@Composable
private fun RackIndicator(color: Color) {
    Box(
        modifier = Modifier
            .padding(end = 3.dp)
            .size(width = 18.dp, height = 6.dp)
            .background(color)
            .rackBevel(pressed = true)
    )
}

@Composable
private fun LcdLabel(text: String) {
    Text(
        text = text,
        color = DisplayBlack,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .background(LcdGreenDim)
            .padding(horizontal = 3.dp, vertical = 1.dp)
    )
}

private fun formatRackTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
