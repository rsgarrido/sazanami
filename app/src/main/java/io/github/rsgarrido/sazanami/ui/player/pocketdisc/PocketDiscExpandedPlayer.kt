package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.waveform.WaveformData
import io.github.rsgarrido.sazanami.ui.player.RETRO_VISUALIZER_CADENCE_HZ
import io.github.rsgarrido.sazanami.ui.player.fillRetroMeterLevels
import io.github.rsgarrido.sazanami.ui.player.isRetroMeterEffectivelySilent
import io.github.rsgarrido.sazanami.ui.player.rememberBoundedVisualizerPhase
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.getDisplayTrackNumber
import kotlin.math.sin
import java.util.Locale

@Composable
fun PocketDiscExpandedPlayer(
    currentSong: Song?,
    activeQueueName: String,
    activeQueuePosition: Int,
    activeQueueCount: Int,
    waveformData: WaveformData?,
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
    onOpenQueueHubClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    tokens: PlayerThemeTokens = PocketDiscDefaultTokens,
    renderShell: Boolean = true,
    headerReveal: Float = 1f,
    mediaReveal: Float = 1f,
    panelReveal: Float = 1f,
    controlsReveal: Float = 1f,
    inputEnabled: Boolean = true,
    collapseGestureEnabled: Boolean = false,
    morphBounds: PocketDiscMorphBounds? = null,
    sharedOwner: PocketDiscSharedOwner = PocketDiscSharedOwner.EXPANDED,
    onMorphDragStart: () -> Unit = {},
    onMorphDragBy: (Float) -> Unit = {},
    onMorphDragEnd: (Float) -> Unit = {},
    onMorphDragCancel: () -> Unit = {}
) {
    val palette = remember(tokens) { PocketDiscPalette.from(tokens) }
    val safeCollapseDragModifier = Modifier.pocketDiscDownwardCollapseGesture(
        enabled = collapseGestureEnabled,
        onDragStart = onMorphDragStart,
        onDragBy = onMorphDragBy,
        onDragEnd = onMorphDragEnd,
        onDragCancel = onMorphDragCancel
    )

    CompositionLocalProvider(LocalPocketDiscPalette provides palette) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(if (renderShell) PocketDiscColors.shell else Color.Transparent)
        ) {
            val compact = maxHeight < 710.dp || maxWidth < 360.dp
            val horizontalPadding = if (compact) 10.dp else 16.dp
            val gap = if (compact) 8.dp else 12.dp
            val availableMediaWidth = maxWidth - horizontalPadding * 2 - gap
            val mediaItemSize = (availableMediaWidth / 2)
                .coerceAtMost(if (compact) 158.dp else 210.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = if (compact) 7.dp else 11.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
            ) {
                PocketDiscHeader(
                    activeQueueName = activeQueueName,
                    activeQueuePosition = activeQueuePosition,
                    activeQueueCount = activeQueueCount,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    onCollapseClick = onCollapseClick,
                    enabled = inputEnabled,
                    compact = compact,
                    modifier = safeCollapseDragModifier.graphicsLayer {
                        alpha = headerReveal.coerceIn(0f, 1f)
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = mediaReveal.coerceIn(0f, 1f)
                            scaleX = 0.96f + 0.04f * mediaReveal.coerceIn(0f, 1f)
                            scaleY = scaleX
                        }
                        .then(safeCollapseDragModifier),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketDiscArtwork(
                        song = currentSong,
                        sharedOwner = sharedOwner,
                        morphBounds = morphBounds,
                        modifier = Modifier.size(mediaItemSize)
                    )
                    PocketDiscCartridge(
                        compact = compact,
                        modifier = Modifier.size(mediaItemSize)
                    )
                }

                PocketDiscMetadataPanel(
                    currentSong = currentSong,
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeekChange = onSeekChange,
                    enabled = inputEnabled,
                    morphBounds = morphBounds,
                    sharedOwner = sharedOwner,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = panelReveal.coerceIn(0f, 1f) }
                )

                PocketDiscTransportControls(
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    enabled = inputEnabled,
                    sharedOwner = sharedOwner,
                    morphBounds = morphBounds,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = controlsReveal.coerceIn(0f, 1f) }
                )

                PocketDiscUtilityControls(
                    currentSong = currentSong,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isCurrentSongFavorite = isCurrentSongFavorite,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onOpenQueueHubClick = onOpenQueueHubClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    enabled = inputEnabled,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = controlsReveal.coerceIn(0f, 1f) }
                )

                Spacer(modifier = Modifier.weight(1f))

                PocketDiscLevelMeter(
                    currentSong = currentSong,
                    waveformData = waveformData,
                    isVisualizerWorkAllowed = isVisualizerWorkAllowed,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = controlsReveal.coerceIn(0f, 1f) }
                        .then(safeCollapseDragModifier)
                )
            }
        }
    }
}

@Composable
private fun PocketDiscHeader(
    activeQueueName: String,
    activeQueuePosition: Int,
    activeQueueCount: Int,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onCollapseClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketDiscColors
    val total = activeQueueCount.coerceAtLeast(1)
    val queuePosition = activeQueuePosition.coerceIn(1, total)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 42.dp else 48.dp)
            .background(colors.panel, RoundedCornerShape(7.dp))
            .border(1.dp, colors.edge.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketDiscBatteryIndicator()
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))
        Text(
            text = when {
                repeatMode == RepeatMode.ONE -> "REPEAT 1"
                repeatMode != RepeatMode.OFF -> "REPEAT ALL"
                isShuffleEnabled -> "SHUFFLE"
                else -> "STEREO"
            },
            color = colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 8.sp else 9.sp
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))
        Text(
            text = "Q: ${activeQueueName.uppercase(Locale.ROOT)}",
            color = colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 7.sp else 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${queuePosition.toString().padStart(2, '0')}/${total.toString().padStart(2, '0')}",
            color = colors.lcdText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 9.sp else 10.sp
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        PocketDiscButton(
            onClick = onCollapseClick,
            enabled = enabled,
            active = false,
            modifier = Modifier.size(if (compact) 38.dp else 42.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Collapse player",
                tint = colors.buttonIcon,
                modifier = Modifier.size(if (compact) 17.dp else 19.dp)
            )
        }
    }
}

@Composable
private fun PocketDiscArtwork(
    song: Song?,
    sharedOwner: PocketDiscSharedOwner,
    morphBounds: PocketDiscMorphBounds?,
    modifier: Modifier = Modifier
) {
    val colors = PocketDiscColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(colors.panelDeep)
            .border(1.dp, colors.edge, RoundedCornerShape(7.dp))
            .padding(4.dp)
            .onGloballyPositioned { coordinates ->
                morphBounds?.updateExpandedArtwork(coordinates.boundsInRoot())
            }
    ) {
        if (sharedOwner != PocketDiscSharedOwner.TRANSITION) {
            AsyncImage(
                model = song?.albumArtUri,
                contentDescription = "Current album artwork",
                contentScale = ContentScale.Fit,
                error = painterResource(R.drawable.ic_media_play),
                placeholder = painterResource(R.drawable.ic_media_play),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PocketDiscMetadataPanel(
    currentSong: Song?,
    currentPosition: Int,
    duration: Int,
    onSeekChange: (Int) -> Unit,
    enabled: Boolean,
    morphBounds: PocketDiscMorphBounds?,
    sharedOwner: PocketDiscSharedOwner,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketDiscColors
    val sharedAlpha = if (sharedOwner == PocketDiscSharedOwner.TRANSITION) 0f else 1f
    val remaining = (duration - currentPosition).coerceAtLeast(0)
    val discLabel = currentSong?.discNumber?.takeIf { it > 0 }?.let { number ->
        val total = currentSong?.discTotal?.takeIf { it > 0 }
        if (total != null) "DISC $number/$total" else "DISC $number"
    } ?: "LOCAL DISC"
    // Queue identity and position are shown in the device header; this panel keeps
    // album track/disc metadata separate so multi-queue position is never confused
    // with embedded track numbering.
    val trackNumber = currentSong?.trackNumber
        ?.takeIf { it > 0 }
        ?.let(::getDisplayTrackNumber)
        ?: "–"

    Column(
        modifier = modifier
            .background(colors.lcdBackground, RoundedCornerShape(7.dp))
            .border(1.dp, colors.lcdGlowDim.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (currentSong == null) "NO DISC" else "▶",
                color = colors.active,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = if (compact) 12.sp else 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentSong?.title?.ifBlank { "Unknown title" } ?: "No track loaded",
                color = colors.lcdText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 14.sp else 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateExpandedTitle(coordinates.boundsInRoot())
                    }
                    .graphicsLayer { alpha = sharedAlpha }
            )
            Text(
                text = "TRACK $trackNumber  $discLabel",
                color = colors.lcdTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 7.sp else 8.sp
            )
        }
        Text(
            text = currentSong?.artist?.ifBlank { "Unknown artist" } ?: "",
            color = colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 9.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateExpandedArtist(coordinates.boundsInRoot())
                }
                .graphicsLayer { alpha = sharedAlpha }
        )
        Text(
            text = currentSong?.album?.ifBlank { "Unknown album" } ?: "",
            color = colors.lcdTextMuted.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 8.sp else 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPocketDiscTime(currentPosition),
                color = colors.lcdText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 10.sp else 12.sp
            )
            PocketDiscSegmentedProgress(
                progress = normalizedPocketDiscProgress(currentPosition, duration),
                segmentCount = if (compact) 22 else 30,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 9.dp)
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateExpandedProgress(coordinates.boundsInRoot())
                    }
                    .graphicsLayer { alpha = sharedAlpha }
                    .pointerInput(duration, enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset ->
                            if (duration > 0 && size.width > 0) {
                                val progress = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeekChange((duration * progress).toInt())
                            }
                        }
                    }
            )
            Text(
                text = formatPocketDiscTime(remaining, negative = true),
                color = colors.lcdText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 10.sp else 12.sp
            )
        }
    }
}

@Composable
private fun PocketDiscTransportControls(
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    enabled: Boolean,
    sharedOwner: PocketDiscSharedOwner,
    morphBounds: PocketDiscMorphBounds?,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketDiscColors
    val sharedAlpha = if (sharedOwner == PocketDiscSharedOwner.TRANSITION) 0f else 1f
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketDiscTransportButton(Modifier.weight(1f), enabled, onPreviousClick) {
            Icon(Icons.Filled.SkipPrevious, "Previous", tint = colors.buttonIcon)
        }
        PocketDiscTransportButton(Modifier.weight(1f), enabled, {
            onSeekChange((currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
        }) {
            Icon(Icons.Filled.FastRewind, "Seek backward", tint = colors.buttonIcon)
        }
        Box(
            modifier = Modifier
                .weight(1.18f)
                .height(if (compact) 52.dp else 60.dp)
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateExpandedPlay(coordinates.boundsInRoot())
                }
                .graphicsLayer { alpha = sharedAlpha }
        ) {
            PocketDiscTransportButton(
                modifier = Modifier.fillMaxSize(),
                enabled = enabled && sharedOwner != PocketDiscSharedOwner.TRANSITION,
                onClick = onPlayPauseClick,
                active = true
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = colors.lcdText,
                    modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                )
            }
        }
        PocketDiscTransportButton(Modifier.weight(1f), enabled, {
            val upper = duration.coerceAtLeast(0)
            onSeekChange((currentPosition + SEEK_STEP_MS).coerceAtMost(upper))
        }) {
            Icon(Icons.Filled.FastForward, "Seek forward", tint = colors.buttonIcon)
        }
        PocketDiscTransportButton(Modifier.weight(1f), enabled, onNextClick) {
            Icon(Icons.Filled.SkipNext, "Next", tint = colors.buttonIcon)
        }
    }
}

@Composable
private fun PocketDiscTransportButton(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    PocketDiscButton(
        modifier = modifier.height(56.dp),
        onClick = onClick,
        enabled = enabled,
        active = active,
        content = content
    )
}

@Composable
private fun PocketDiscUtilityControls(
    currentSong: Song?,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isCurrentSongFavorite: Boolean,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onOpenQueueHubClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)
    ) {
        PocketDiscUtilityButton(
            label = "SHUFFLE",
            active = isShuffleEnabled,
            enabled = enabled,
            onClick = onShuffleClick,
            modifier = Modifier.weight(1f)
        ) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = it) }
        PocketDiscUtilityButton(
            label = when (repeatMode) {
                RepeatMode.ONE -> "REPEAT 1"
                else -> "REPEAT"
            },
            active = repeatMode != RepeatMode.OFF,
            enabled = enabled,
            onClick = onRepeatClick,
            modifier = Modifier.weight(1f)
        ) { tint ->
            Icon(
                if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                "Repeat",
                tint = tint
            )
        }
        PocketDiscUtilityButton(
            label = "QUEUES",
            active = false,
            enabled = enabled,
            onClick = onOpenQueueHubClick,
            modifier = Modifier.weight(1f)
        ) { Icon(Icons.Filled.QueueMusic, "Open queues", tint = it) }
        PocketDiscUtilityButton(
            label = "FAVORITE",
            active = isCurrentSongFavorite,
            enabled = enabled && currentSong != null,
            onClick = { currentSong?.let(onToggleFavoriteClick) },
            modifier = Modifier.weight(1f)
        ) { tint ->
            Icon(
                if (isCurrentSongFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                "Favorite",
                tint = tint
            )
        }
    }
}

@Composable
private fun PocketDiscUtilityButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val colors = PocketDiscColors
    Column(
        modifier = modifier
            .height(54.dp)
            .background(
                if (active) colors.activeDim else colors.panel,
                RoundedCornerShape(5.dp)
            )
            .border(
                1.dp,
                if (active) colors.active else colors.edge.copy(alpha = 0.42f),
                RoundedCornerShape(5.dp)
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val tint = if (active) colors.lcdText else colors.buttonIcon
        Box(modifier = Modifier.size(21.dp), contentAlignment = Alignment.Center) { icon(tint) }
        Text(
            text = label,
            color = if (active) colors.lcdText else colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PocketDiscLevelMeter(
    currentSong: Song?,
    waveformData: WaveformData?,
    isVisualizerWorkAllowed: Boolean,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketDiscColors
    val isSilent = isRetroMeterEffectivelySilent(
        amplitudes = waveformData?.amplitudes,
        currentPositionMs = currentPosition.toLong(),
        durationMs = duration.toLong()
    )
    val phase = rememberBoundedVisualizerPhase(
        animationEnabled = isPlaying && isVisualizerWorkAllowed && !isSilent,
        targetCadenceHz = RETRO_VISUALIZER_CADENCE_HZ,
        cycleDurationMillis = 1_500,
        updateTraceName = "PocketDiscMeterUpdate"
    )
    val levels = remember { FloatArray(2) }
    val rowHeight = if (compact) 10.dp else 13.dp

    Column(
        modifier = modifier
            .height(if (compact) 58.dp else 72.dp)
            .background(colors.panel, RoundedCornerShape(7.dp))
            .border(1.dp, colors.edge.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .padding(horizontal = if (compact) 9.dp else 12.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
    ) {
        Text(
            text = "LEVEL       -30       -15        -6       -3       0 dB",
            color = colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 6.sp else 7.sp,
            maxLines = 1
        )
        PocketDiscMeterRow(
            label = "L",
            channelIndex = 0,
            levels = levels,
            waveformData = waveformData,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            songSeed = currentSong?.id ?: 0L,
            phase = phase,
            compact = compact,
            modifier = Modifier.fillMaxWidth().height(rowHeight)
        )
        PocketDiscMeterRow(
            label = "R",
            channelIndex = 1,
            levels = levels,
            waveformData = waveformData,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            songSeed = currentSong?.id ?: 0L,
            phase = phase,
            compact = compact,
            modifier = Modifier.fillMaxWidth().height(rowHeight)
        )
    }
}

@Composable
private fun PocketDiscMeterRow(
    label: String,
    channelIndex: Int,
    levels: FloatArray,
    waveformData: WaveformData?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    songSeed: Long,
    phase: State<Float>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketDiscColors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 7.sp else 8.sp,
            modifier = Modifier.width(14.dp)
        )
        Canvas(modifier = Modifier.weight(1f).fillMaxSize()) {
            val visualizerPhase = phase.value
            val energyDriven = fillRetroMeterLevels(
                output = levels,
                amplitudes = waveformData?.amplitudes,
                currentPositionMs = currentPosition.toLong(),
                durationMs = duration.toLong(),
                animationPhase = visualizerPhase,
                isPlaying = isPlaying,
                songSeed = songSeed
            )
            val fallbackOffset = if (channelIndex == 0) 0f else 1.7f
            val fallbackAmplitude = if (channelIndex == 0) 0.17f else 0.15f
            val fallbackCeiling = if (channelIndex == 0) 0.82f else 0.78f
            val level = when {
                energyDriven -> levels[channelIndex.coerceIn(0, 1)]
                isPlaying -> (
                        0.48f + sin(visualizerPhase * 6.283f + fallbackOffset) * fallbackAmplitude
                        ).coerceIn(0.05f, fallbackCeiling)
                else -> 0f
            }
            val gap = 2.dp.toPx()
            val segmentCount = if (compact) 24 else 32
            val segmentWidth = (size.width - gap * (segmentCount - 1)) / segmentCount
            val activeSegments = (level * segmentCount).toInt().coerceAtLeast(if (isPlaying) 1 else 0)
            repeat(segmentCount) { segment ->
                drawRoundRect(
                    color = if (segment < activeSegments) {
                        colors.lcdGlow
                    } else {
                        colors.lcdGlowDim.copy(alpha = 0.16f)
                    },
                    topLeft = Offset(segment * (segmentWidth + gap), 0f),
                    size = Size(segmentWidth, size.height),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun PocketDiscButton(
    onClick: () -> Unit,
    enabled: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = PocketDiscColors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        pressed -> colors.buttonPressed
        active -> colors.activeDim
        else -> colors.button
    }
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(5.dp))
            .border(
                1.dp,
                if (active) colors.active else colors.buttonEdge.copy(alpha = 0.62f),
                RoundedCornerShape(5.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Claims downward drags for collapse while leaving upward intent to the lyrics gesture. */
private fun Modifier.pocketDiscDownwardCollapseGesture(
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, onDragStart, onDragBy, onDragEnd, onDragCancel) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            var started = false
            val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop > 0f) {
                    started = true
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    onDragStart()
                    onDragBy(overSlop)
                }
            }
            if (!started || slopChange == null) {
                if (started) onDragCancel()
                return@awaitEachGesture
            }
            var active = true
            while (active) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val deltaY = change.positionChange().y
                if (deltaY != 0f) {
                    change.consume()
                    onDragBy(deltaY)
                }
                active = change.pressed
            }
            onDragEnd(velocityTracker.calculateVelocity().y)
        }
    }
}

private const val SEEK_STEP_MS = 10_000
