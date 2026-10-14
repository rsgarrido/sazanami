package io.github.rsgarrido.sazanami.ui.player.pocketcassette

import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause

import io.github.rsgarrido.sazanami.ui.state.rememberDirectSeekGestureControl
import io.github.rsgarrido.sazanami.ui.state.directSeekPositionInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsGestureRegion
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput
import kotlinx.coroutines.delay

private const val TransportSeekStepMillis = 2_000
private const val TransportSeekRepeatMillis = 300L

@Composable
internal fun PocketCassetteControls(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onMoreClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    controlsReveal: Float = 1f,
    inputEnabled: Boolean = true,
    morphBounds: PocketCassetteMorphBounds? = null,
    sharedOwner: PocketCassetteSharedOwner = PocketCassetteSharedOwner.EXPANDED,
    lyricsGestureModifier: Modifier = Modifier,
    lyricsGestureRegion: PlayerLyricsGestureRegion? = null
) {
    var rewindTarget by remember(currentSong?.id) { mutableIntStateOf(currentPosition) }
    var forwardTarget by remember(currentSong?.id) { mutableIntStateOf(currentPosition) }

    fun clampSeekTarget(target: Long): Int {
        val upperBound = if (duration > 0) duration.toLong() else Int.MAX_VALUE.toLong()
        return target.coerceIn(0L, upperBound).toInt()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = controlsReveal.coerceIn(0f, 1f) }
            .then(if (inputEnabled) Modifier else Modifier.clearAndSetSemantics { }),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
    ) {
        PocketCassetteSeekSlot(
            currentPosition = currentPosition,
            duration = duration,
            onSeekChange = onSeekChange,
            compact = compact,
            enabled = inputEnabled && sharedOwner == PocketCassetteSharedOwner.EXPANDED,
            morphBounds = morphBounds,
            sharedOwner = sharedOwner,
            modifier = lyricsGestureModifier
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    lyricsGestureRegion?.updateBottom(coordinates.boundsInRoot())
                }
                .then(lyricsGestureModifier),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            PocketCassetteMechanicalButton(
                icon = Icons.Filled.SkipPrevious,
                label = stringResource(R.string.player_cassette_previous),
                contentDescription = stringResource(R.string.player_previous_hold),
                onClick = onPreviousClick,
                onLongPressStart = { rewindTarget = currentPosition },
                onLongPressRepeat = {
                    rewindTarget = clampSeekTarget(
                        rewindTarget.toLong() - TransportSeekStepMillis
                    )
                    onSeekChange(rewindTarget)
                },
                longClickLabel = stringResource(R.string.player_seek_backward_hold),
                compact = compact,
                enabled = inputEnabled,
                modifier = Modifier.weight(1f)
            )
            PocketCassetteMechanicalButton(
                icon = if (playbackPlayPauseShowsPause(isPlaying)) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = stringResource(if (playbackPlayPauseShowsPause(isPlaying)) R.string.player_cassette_pause else R.string.player_cassette_play),
                contentDescription = stringResource(if (playbackPlayPauseShowsPause(isPlaying)) R.string.player_pause else R.string.player_play),
                onClick = onPlayPauseClick,
                compact = compact,
                accent = true,
                enabled = inputEnabled && sharedOwner == PocketCassetteSharedOwner.EXPANDED,
                visualAlpha = if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) 1f else 0f,
                onBoundsChanged = morphBounds?.let { bounds ->
                    { rect -> bounds.updateExpandedPlay(rect) }
                },
                modifier = Modifier.weight(1.12f)
            )
            PocketCassetteMechanicalButton(
                icon = Icons.Filled.SkipNext,
                label = stringResource(R.string.player_cassette_next),
                contentDescription = stringResource(R.string.player_next_hold),
                onClick = onNextClick,
                onLongPressStart = { forwardTarget = currentPosition },
                onLongPressRepeat = {
                    forwardTarget = clampSeekTarget(
                        forwardTarget.toLong() + TransportSeekStepMillis
                    )
                    onSeekChange(forwardTarget)
                },
                longClickLabel = stringResource(R.string.player_seek_forward_hold),
                compact = compact,
                enabled = inputEnabled,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            PocketCassetteUtilityButton(
                icon = Icons.Filled.Shuffle,
                label = stringResource(R.string.player_cassette_mix),
                contentDescription = stringResource(if (isShuffleEnabled) R.string.player_disable_shuffle else R.string.player_enable_shuffle),
                active = isShuffleEnabled,
                enabled = inputEnabled,
                onClick = onShuffleClick,
                modifier = Modifier.weight(1f)
            )
            PocketCassetteUtilityButton(
                icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                label = when (repeatMode) {
                    RepeatMode.OFF -> stringResource(R.string.player_cassette_loop)
                    RepeatMode.ALL -> stringResource(R.string.player_cassette_all)
                    RepeatMode.ONE -> stringResource(R.string.player_cassette_one)
                },
                contentDescription = when (repeatMode) {
                    RepeatMode.OFF -> stringResource(R.string.player_enable_repeat_all)
                    RepeatMode.ALL -> stringResource(R.string.player_enable_repeat_one)
                    RepeatMode.ONE -> stringResource(R.string.player_disable_repeat)
                },
                active = repeatMode != RepeatMode.OFF,
                enabled = inputEnabled,
                onClick = onRepeatClick,
                modifier = Modifier.weight(1f)
            )
            PocketCassetteUtilityButton(
                icon = Icons.Filled.MoreVert,
                label = stringResource(R.string.player_more_short),
                contentDescription = stringResource(R.string.player_more_actions),
                enabled = inputEnabled && currentSong != null,
                onClick = onMoreClick,
                modifier = Modifier.weight(1f).playerEndpointInput(inputEnabled && currentSong != null)
            )
            PocketCassetteUtilityButton(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.player_cassette_queue),
                contentDescription = stringResource(R.string.player_open_queues),
                enabled = inputEnabled,
                onClick = onOpenUpNextClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PocketCassetteMechanicalButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    visualAlpha: Float = 1f,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressRepeat: (() -> Unit)? = null,
    longClickLabel: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var repeatLongPress by remember { mutableStateOf(false) }
    val currentLongPressStart by rememberUpdatedState(onLongPressStart)
    val currentLongPressRepeat by rememberUpdatedState(onLongPressRepeat)
    val shape = RoundedCornerShape(5.dp)

    LaunchedEffect(isPressed, repeatLongPress) {
        if (!isPressed) {
            repeatLongPress = false
        } else if (repeatLongPress) {
            while (true) {
                currentLongPressRepeat?.invoke()
                delay(TransportSeekRepeatMillis)
            }
        }
    }

    Box(
        modifier = modifier
            .height(if (compact) 58.dp else 68.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInRoot())
            }
            .graphicsLayer { alpha = visualAlpha.coerceIn(0f, 1f) }
            .offset {
                IntOffset(x = 0, y = if (isPressed) 2.dp.roundToPx() else 0)
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isPressed) {
                        listOf(PocketCassetteColors.buttonPressed, PocketCassetteColors.button)
                    } else {
                        listOf(PocketCassetteColors.buttonTop, PocketCassetteColors.button)
                    }
                ),
                shape = shape
            )
            .pocketCassetteBevel(radius = 5.dp, pressed = isPressed)
            .combinedClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onLongClickLabel = longClickLabel,
                onLongClick = if (onLongPressRepeat != null) {
                    {
                        currentLongPressStart?.invoke()
                        repeatLongPress = true
                    }
                } else {
                    null
                },
                onClick = onClick
            )
            .then(if (visualAlpha > 0f) Modifier else Modifier.clearAndSetSemantics { }),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (accent) PocketCassetteColors.buttonActive else PocketCassetteColors.buttonIcon,
                modifier = Modifier.size(if (compact) 25.dp else 29.dp)
            )
            Text(
                text = label,
                color = if (accent) PocketCassetteColors.buttonActive else PocketCassetteColors.buttonIcon,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 7.sp else 8.sp,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun PocketCassetteUtilityButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tint = when {
        !enabled -> PocketCassetteColors.buttonIcon.copy(alpha = 0.34f)
        active -> PocketCassetteColors.buttonActive
        else -> PocketCassetteColors.buttonIcon
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 50.dp)
            .offset {
                IntOffset(x = 0, y = if (isPressed) 1.dp.roundToPx() else 0)
            }
            .background(
                color = if (isPressed) PocketCassetteColors.buttonPressed else PocketCassetteColors.button,
                shape = RoundedCornerShape(4.dp)
            )
            .pocketCassetteBevel(radius = 4.dp, pressed = isPressed)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = tint,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PocketCassetteSeekSlot(
    currentPosition: Int,
    duration: Int,
    onSeekChange: (Int) -> Unit,
    compact: Boolean,
    enabled: Boolean,
    morphBounds: PocketCassetteMorphBounds?,
    sharedOwner: PocketCassetteSharedOwner,
    modifier: Modifier = Modifier
) {
    val colors = PocketCassetteColors
    val gesture=rememberDirectSeekGestureControl(onSeekChange)
    val safeDuration = duration.coerceAtLeast(1)
    val safePosition = currentPosition.coerceIn(0, safeDuration)
    val progress = safePosition.toFloat() / safeDuration.toFloat()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatPocketCassetteTime(safePosition),
                color = PocketCassetteColors.shellInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 9.sp else 10.sp
            )
            Text(
            text = stringResource(R.string.player_cassette_counter_position),
                color = PocketCassetteColors.shellInk.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (compact) 6.dp else 8.dp)
            )
            Text(
                text = formatPocketCassetteTime(duration),
                color = PocketCassetteColors.shellInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 9.sp else 10.sp
            )
        }

        val seekInputModifier = if (enabled) {
            Modifier.directSeekPositionInput(gesture,safeDuration)
        } else {
            Modifier
        }

        val seekSemanticsModifier = if (
            sharedOwner == PocketCassetteSharedOwner.EXPANDED && enabled
        ) {
            Modifier.semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = safePosition.toFloat(),
                    range = 0f..safeDuration.toFloat()
                )
                setProgress { target ->
                    gesture.immediate(target.coerceIn(0f, safeDuration.toFloat()).toInt())
                    true
                }
            }
        } else {
            Modifier.clearAndSetSemantics { }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 38.dp else 44.dp)
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateExpandedProgress(coordinates.boundsInRoot())
                }
                .graphicsLayer {
                    alpha = if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) 1f else 0f
                }
                .then(seekInputModifier)
                .then(seekSemanticsModifier)
        ) {
            val slotHeight = if (compact) 13.dp.toPx() else 15.dp.toPx()
            val slotTop = (size.height - slotHeight) / 2f
            val inset = 5.dp.toPx()
            val trackWidth = size.width - inset * 2

            drawRoundRect(
                color = colors.buttonEdge,
                topLeft = Offset(0f, slotTop),
                size = Size(size.width, slotHeight),
                cornerRadius = CornerRadius(slotHeight / 2f)
            )
            drawRoundRect(
                color = colors.orange.copy(alpha = 0.9f),
                topLeft = Offset(inset, slotTop + 4.dp.toPx()),
                size = Size(trackWidth * progress, slotHeight - 8.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            val thumbWidth = 12.dp.toPx()
            val thumbHeight = slotHeight + 12.dp.toPx()
            val thumbX = (inset + trackWidth * progress - thumbWidth / 2f)
                .coerceIn(0f, size.width - thumbWidth)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        colors.silverDark,
                        colors.silverLight,
                        colors.silverMid
                    )
                ),
                topLeft = Offset(thumbX, (size.height - thumbHeight) / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawLine(
                color = colors.shellInk.copy(alpha = 0.72f),
                start = Offset(thumbX + thumbWidth / 2f, (size.height - thumbHeight) / 2f + 3.dp.toPx()),
                end = Offset(thumbX + thumbWidth / 2f, (size.height + thumbHeight) / 2f - 3.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
