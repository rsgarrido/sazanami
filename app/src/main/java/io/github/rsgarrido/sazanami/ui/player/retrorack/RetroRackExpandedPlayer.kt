package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.waveform.WaveformData
import io.github.rsgarrido.sazanami.ui.player.fillRetroMeterLevels
import io.github.rsgarrido.sazanami.ui.player.isRetroMeterEffectivelySilent
import io.github.rsgarrido.sazanami.ui.player.rememberBoundedVisualizerPhase
import io.github.rsgarrido.sazanami.ui.player.RETRO_VISUALIZER_CADENCE_HZ
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.VisualizerPerformanceCounters
import io.github.rsgarrido.sazanami.performance.tracePerformance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput
import kotlin.math.sin
import kotlin.math.abs

@Composable
fun RetroRackExpandedPlayer(
    currentSong: Song?,
    waveformData: WaveformData? = null,
    isVisualizerWorkAllowed: Boolean = true,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    isCurrentSongFavorite: Boolean,
    upcomingSongs: List<Song>,
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
    val playbackContext = listOfNotNull(currentSong) + upcomingSongs
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 700 || configuration.screenWidthDp < 360
    val fontScale = LocalDensity.current.fontScale
    val mainDeckHeight = when {
        compact && fontScale > 1.15f -> 212.dp
        compact -> 202.dp
        fontScale > 1.15f -> 230.dp
        else -> 216.dp
    }
    val visualProfile = remember(
        currentSong?.id,
        currentSong?.title,
        currentSong?.artist,
        currentSong?.album
    ) {
        buildRetroRackVisualProfile(
            songId = currentSong?.id,
            title = currentSong?.title,
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
            .background(RackBackground)
            .padding(
                horizontal = if (compact) 5.dp else 8.dp,
                vertical = if (compact) 6.dp else 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)
    ) {
        RackModule(
            title = "SAZANAMI // MAIN DECK",
            modifier = Modifier.height(mainDeckHeight).graphicsLayer { alpha = deckReveal },
            titleModifier = safeHeaderGesture,
            trailingAction = {
                RackIconButton(
                    icon = Icons.Filled.Close,
                    label = "CLOSE",
                    compact = true,
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
                modifier = safeHeaderGesture
            )
        }

        RackModule(
            title = "SPECTRUM MONITOR // VISUAL",
            modifier = Modifier.height(if (compact) 72.dp else 88.dp).graphicsLayer { alpha = spectrumReveal; scaleY = .92f + .08f * spectrumReveal },
            titleModifier = safeHeaderGesture,
            trailingAction = {
                RackIndicator(color = visualProfile.accent)
            }
        ) {
            DecorativeSpectrum(
                profile = visualProfile,
                waveformData = waveformData,
                isVisualizerWorkAllowed = isVisualizerWorkAllowed && spectrumReveal > .99f,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
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
                    onClick = onOpenUpNextClick,
                    modifier = Modifier.playerEndpointInput(inputEnabled && queueReveal > .99f)
                )
            }
        ) {
            RackPlaylist(
                currentSong = currentSong,
                upcomingSongs = upcomingSongs,
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
    modifier: Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    val displayHeight = when {
        compact && fontScale > 1.15f -> 76.dp
        compact -> 68.dp
        fontScale > 1.15f -> 84.dp
        else -> 76.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
                    AsyncImage(
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
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .then(modifier),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentSong?.title?.uppercase() ?: "NO TRACK LOADED",
                    color = LcdGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = if (compact) 14.sp else 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { morphBounds?.updateExpandedTitle(it.boundsInRoot()) }
                        .sharedEndpointVisual(sharedOwner == RetroRackSharedOwner.EXPANDED)
                )
                Text(
                    text = currentSong?.artist?.uppercase().orEmpty(),
                    color = LcdGreenDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
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

        Slider(
            value = currentPosition.coerceIn(0, duration.coerceAtLeast(1)).toFloat(),
            onValueChange = { value -> onSeekChange(value.toInt()) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = ControlSilver,
                activeTrackColor = LcdGreen,
                inactiveTrackColor = InactiveTrack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onGloballyPositioned { morphBounds?.updateExpandedProgress(it.boundsInRoot()) }
                .sharedEndpointVisual(sharedOwner == RetroRackSharedOwner.EXPANDED)
        )

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
private fun DecorativeSpectrum(
    profile: RetroRackVisualProfile,
    waveformData: WaveformData?,
    isVisualizerWorkAllowed: Boolean,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    modifier: Modifier = Modifier
) {
    val isSilent = isRetroMeterEffectivelySilent(
        amplitudes = waveformData?.amplitudes,
        currentPositionMs = currentPosition.toLong(),
        durationMs = duration.toLong()
    )
    val phase = rememberBoundedVisualizerPhase(
        animationEnabled = isPlaying && isVisualizerWorkAllowed && !isSilent,
        targetCadenceHz = RETRO_VISUALIZER_CADENCE_HZ,
        cycleDurationMillis = 1_600,
        updateTraceName = PerformanceTraceNames.RETRO_RACK_UPDATE
    )
    val meterLevels = remember(profile.levels.size) { FloatArray(profile.levels.size) }
    Canvas(
        modifier = modifier
            .background(DisplayBlack)
            .rackBevel()
            .padding(8.dp)
    ) {
        tracePerformance(PerformanceTraceNames.RETRO_RACK_DRAW) {
        VisualizerPerformanceCounters.onDraw()
        val currentPhase = phase.value
        val isEnergyDriven = fillRetroMeterLevels(
            output = meterLevels,
            amplitudes = waveformData?.amplitudes,
            currentPositionMs = currentPosition.toLong(),
            durationMs = duration.toLong(),
            animationPhase = currentPhase,
            isPlaying = isPlaying,
            songSeed = profile.songSeed
        )
        val displayLevelCount = if (isEnergyDriven) meterLevels.size else profile.levels.size
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (displayLevelCount - 1)) / displayLevelCount
        val segmentGap = 2.dp.toPx()
        val segmentHeight = 3.dp.toPx()
        val playbackPhase = (currentPosition / 1_000f) * 0.22f
        repeat(displayLevelCount) { index ->
            val level = if (isEnergyDriven) meterLevels[index] else profile.levels[index]
            val movement = if (!isEnergyDriven && isPlaying) {
                sin(
                    currentPhase * 6.283f * (0.58f + index % 4 * 0.07f) +
                            playbackPhase +
                            profile.phaseOffset +
                            index * 0.73f
                ) * 0.11f
            } else {
                0f
            }
            val minimumLevel = if (!isEnergyDriven) 0.12f else 0f
            val animatedLevel = (level + movement).coerceIn(minimumLevel, 0.98f)
            val height = size.height * animatedLevel
            val segmentStep = segmentHeight + segmentGap
            val segmentCount = (height / segmentStep).toInt().coerceAtLeast(1)
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
    currentSong: Song?,
    upcomingSongs: List<Song>,
    playbackContext: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    inputEnabled: Boolean
) {
    val rows = listOfNotNull(currentSong) + upcomingSongs
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
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = LcdGreenDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Text(
                    text = "  ${song.artist} — ${song.title}",
                    color = if (index == 0) ControlSilver else LcdGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .padding(start = 6.dp)
                ) {
                    Text(
                        text = formatRackTime(song.duration.toInt()),
                        color = if (index == 0) ControlSilver else LcdGreenDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
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
                fontSize = 9.sp,
                maxLines = 1,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = modifier
            .sizeIn(
                minWidth = if (compact) 36.dp else 40.dp,
                minHeight = if (compact) 30.dp else 34.dp
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
            .rackBevel(pressed = isPressed)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active && !isPressed) DisplayBlack else ControlSilver,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Text(
            text = label,
            color = if (active && !isPressed) DisplayBlack else ControlSilver,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp,
            maxLines = 1
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
            .size(width = 14.dp, height = 6.dp)
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
