package io.github.rsgarrido.sazanami.ui.player.pocketcassette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens

@Composable
fun PocketCassetteExpandedPlayer(
    currentSong: Song?,
    isVisualizerWorkAllowed: Boolean = true,
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
    onOpenUpNextClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    tokens: PlayerThemeTokens = PocketCassetteDefaultTokens,
    renderShell: Boolean = true,
    headerReveal: Float = 1f,
    windowReveal: Float = 1f,
    mechanismReveal: Float = 1f,
    controlsReveal: Float = 1f,
    inputEnabled: Boolean = true,
    collapseGestureEnabled: Boolean = false,
    morphBounds: PocketCassetteMorphBounds? = null,
    sharedOwner: PocketCassetteSharedOwner = PocketCassetteSharedOwner.EXPANDED,
    onMorphDragStart: () -> Unit = {},
    onMorphDragBy: (Float) -> Unit = {},
    onMorphDragEnd: (Float) -> Unit = {},
    onMorphDragCancel: () -> Unit = {}
) {
    val palette = remember(tokens) { PocketCassettePalette.from(tokens) }
    val safeCollapseDragModifier = Modifier.pocketCassetteDownwardCollapseGesture(
        enabled = collapseGestureEnabled,
        onDragStart = onMorphDragStart,
        onDragBy = onMorphDragBy,
        onDragEnd = onMorphDragEnd,
        onDragCancel = onMorphDragCancel
    )

    CompositionLocalProvider(LocalPocketCassettePalette provides palette) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (renderShell) Modifier.pocketCassetteShellFinish() else Modifier)
        ) {
            val compact = maxHeight < 700.dp || maxWidth < 360.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (compact) 9.dp else 14.dp,
                        vertical = if (compact) 7.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
            ) {
                PocketCassetteDeviceHeader(
                    isPlaying = isPlaying,
                    onCollapseClick = onCollapseClick,
                    compact = compact,
                    inputEnabled = inputEnabled,
                    headerReveal = headerReveal,
                    safeDragModifier = safeCollapseDragModifier
                )

                PocketCassetteWindow(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isVisualizerWorkAllowed = isVisualizerWorkAllowed,
                    currentPosition = currentPosition,
                    duration = duration,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    windowReveal = windowReveal,
                    mechanismReveal = mechanismReveal,
                    morphBounds = morphBounds,
                    sharedOwner = sharedOwner,
                    collapseDragModifier = safeCollapseDragModifier
                )

                PocketCassetteControls(
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
                    onOpenUpNextClick = onOpenUpNextClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    compact = compact,
                    controlsReveal = controlsReveal,
                    inputEnabled = inputEnabled,
                    morphBounds = morphBounds,
                    sharedOwner = sharedOwner
                )

                PocketCassetteLowerSeam(
                    compact = compact,
                    modifier = safeCollapseDragModifier
                )
            }
        }
    }
}

@Composable
private fun PocketCassetteDeviceHeader(
    isPlaying: Boolean,
    onCollapseClick: () -> Unit,
    compact: Boolean,
    inputEnabled: Boolean,
    headerReveal: Float,
    safeDragModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 48.dp else 54.dp)
            .graphicsLayer { alpha = headerReveal.coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketCassetteScrew(size = if (compact) 10.dp else 12.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .then(safeDragModifier)
                .padding(start = if (compact) 7.dp else 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Pocket Cassette // Audio",
                color = PocketCassetteColors.shellInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = if (compact) 13.sp else 15.sp,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "STEREO  •  LOCAL  •  TAPE MODE",
                color = PocketCassetteColors.shellInk.copy(alpha = 0.66f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 7.sp else 8.sp,
                letterSpacing = 0.55.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PocketCassetteStatusLamp(isPlaying = isPlaying, compact = compact)
        PocketCassetteBattery(compact = compact)
        PocketCassetteCloseButton(
            onClick = onCollapseClick,
            compact = compact,
            enabled = inputEnabled,
            modifier = Modifier.padding(start = if (compact) 6.dp else 8.dp)
        )
    }
}

@Composable
private fun PocketCassetteStatusLamp(isPlaying: Boolean, compact: Boolean) {
    val colors = PocketCassetteColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = if (compact) 5.dp else 8.dp)
    ) {
        Canvas(modifier = Modifier.size(if (compact) 8.dp else 9.dp)) {
            drawCircle(color = if (isPlaying) colors.statusGreen else Color(0xFF6C5B50))
            drawCircle(
                color = Color.White.copy(alpha = 0.34f),
                radius = size.minDimension * 0.22f,
                center = Offset(size.width * 0.36f, size.height * 0.34f)
            )
        }
        Text(
            text = "RUN",
            color = PocketCassetteColors.shellInk.copy(alpha = 0.66f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp
        )
    }
}

@Composable
private fun PocketCassetteBattery(compact: Boolean) {
    val colors = PocketCassetteColors
    Canvas(
        modifier = Modifier.size(
            width = if (compact) 26.dp else 30.dp,
            height = if (compact) 13.dp else 15.dp
        )
    ) {
        val terminalWidth = 2.dp.toPx()
        drawRoundRect(
            color = colors.shellInk,
            topLeft = Offset(0f, 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - terminalWidth, size.height - 2.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        drawRect(
            color = colors.shellInk,
            topLeft = Offset(size.width - terminalWidth, size.height * 0.34f),
            size = androidx.compose.ui.geometry.Size(terminalWidth, size.height * 0.32f)
        )
        drawRect(
            color = colors.orange,
            topLeft = Offset(3.dp.toPx(), 4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - terminalWidth - 6.dp.toPx(),
                height = size.height - 8.dp.toPx()
            )
        )
    }
}

@Composable
private fun PocketCassetteCloseButton(
    onClick: () -> Unit,
    compact: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(if (compact) 44.dp else 48.dp)
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
                onClickLabel = "Collapse player",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Collapse player",
            tint = PocketCassetteColors.buttonIcon,
            modifier = Modifier.size(if (compact) 20.dp else 22.dp)
        )
    }
}

@Composable
private fun PocketCassetteLowerSeam(
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketCassetteColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketCassetteScrew(size = if (compact) 9.dp else 10.dp)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .padding(horizontal = 8.dp)
        ) {
            drawLine(
                color = colors.seam,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.42f),
                start = Offset(0f, size.height / 2f + 1.dp.toPx()),
                end = Offset(size.width, size.height / 2f + 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
        Text(
            text = "DC // 01",
            color = PocketCassetteColors.shellInk.copy(alpha = 0.55f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp
        )
        PocketCassetteScrew(
            size = if (compact) 9.dp else 10.dp,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/**
 * Claims only downward vertical drags. Upward intent remains available to the parent lyrics gesture;
 * after a downward collapse drag starts, the same gesture may reverse upward before release.
 */
private fun Modifier.pocketCassetteDownwardCollapseGesture(
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
