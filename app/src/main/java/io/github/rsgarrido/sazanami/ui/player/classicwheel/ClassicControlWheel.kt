package io.github.rsgarrido.sazanami.ui.player.classicwheel

import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import kotlin.math.PI
import kotlin.math.atan2

@Composable
fun ClassicControlWheel(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onMenuClick: () -> Unit,
    onCenterClick: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    rotationItemCount: Int,
    isRotationEnabled: Boolean,
    rotationStepDegrees: Float,
    playControlAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(
                rotationItemCount,
                isRotationEnabled,
                rotationStepDegrees
            ) {
                var previousAngle: Float? = null
                var accumulatedAngleDelta = 0f
                val selectionStepDegrees = rotationStepDegrees

                detectDragGestures(
                    onDragStart = { offset ->
                        previousAngle = offset.angleDegreesFromCenter(
                            width = size.width.toFloat(),
                            height = size.height.toFloat()
                        )
                        accumulatedAngleDelta = 0f
                    },
                    onDrag = { change, _ ->
                        if (!isRotationEnabled) {
                            return@detectDragGestures
                        }

                        val currentAngle = change.position.angleDegreesFromCenter(
                            width = size.width.toFloat(),
                            height = size.height.toFloat()
                        )

                        val oldAngle = previousAngle ?: currentAngle
                        var angleDelta = currentAngle - oldAngle

                        if (angleDelta > 180f) {
                            angleDelta -= 360f
                        }

                        if (angleDelta < -180f) {
                            angleDelta += 360f
                        }

                        accumulatedAngleDelta += angleDelta

                        while (accumulatedAngleDelta >= selectionStepDegrees) {
                            onRotateClockwise()
                            accumulatedAngleDelta -= selectionStepDegrees
                        }

                        while (accumulatedAngleDelta <= -selectionStepDegrees) {
                            onRotateCounterClockwise()
                            accumulatedAngleDelta += selectionStepDegrees
                        }

                        previousAngle = currentAngle
                        change.consume()
                    },
                    onDragEnd = {
                        previousAngle = null
                        accumulatedAngleDelta = 0f
                    },
                    onDragCancel = {
                        previousAngle = null
                        accumulatedAngleDelta = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = ClassicWheelColors.wheel,
            shadowElevation = 6.dp
        ) {}

        Text(
            text = stringResource(R.string.player_wheel_menu),
            color = ClassicWheelColors.wheelContent,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .clickable {
                    onMenuClick()
                }
        )

        IconButton(
            onClick = onPreviousClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 26.dp)
                .size(68.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous_song),
                tint = ClassicWheelColors.wheelContent,
                modifier = Modifier.size(42.dp)
            )
        }

        IconButton(
            onClick = onNextClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 26.dp)
                .size(68.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next_song),
                tint = ClassicWheelColors.wheelContent,
                modifier = Modifier.size(42.dp)
            )
        }

        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ClassicWheelPlayPauseBottomPadding)
                .size(ClassicWheelPlayPauseTouchTargetSize)
                .graphicsLayer { alpha = playControlAlpha.coerceIn(0f, 1f) }
        ) {
            Icon(
                imageVector = if (playbackPlayPauseShowsPause(isPlaying)) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = stringResource(if (playbackPlayPauseShowsPause(isPlaying)) R.string.player_pause else R.string.player_play),
                tint = ClassicWheelColors.wheelContent,
                modifier = Modifier.size(ClassicWheelPlayPauseVisualSize)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize(0.28f)
                .clickable {
                    onCenterClick()
                },
            shape = CircleShape,
            color = ClassicWheelColors.centerButton,
            shadowElevation = 4.dp
        ) {}
    }
}

internal val ClassicWheelPlayPauseVisualSize = 42.dp
internal val ClassicWheelPlayPauseTouchTargetSize = 72.dp
internal val ClassicWheelPlayPauseBottomPadding = 26.dp

private fun Offset.angleDegreesFromCenter(
    width: Float,
    height: Float
): Float {
    val centerX = width / 2f
    val centerY = height / 2f

    val angleRadians = atan2(
        y = y - centerY,
        x = x - centerX
    )

    return (angleRadians * 180f / PI).toFloat()
}
