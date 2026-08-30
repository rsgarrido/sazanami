package io.github.rsgarrido.sazanami.ui.player.mini

import android.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import kotlin.math.abs

@Composable
internal fun MiniPlayerScaffold(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    modifier: Modifier = Modifier,
    containerColor: Color,
    borderColor: Color,
    shape: Shape = RoundedCornerShape(18.dp),
    tonalElevation: Dp = 0.dp,
    defaultMorphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    onSurfaceBoundsChanged: (Rect) -> Unit = {},
    content: @Composable (MiniPlayerState) -> Unit
) {
    var isNextTransition by remember { mutableStateOf(true) }

    Surface(
        onClick = callbacks.onExpandClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .onGloballyPositioned { coordinates ->
                onSurfaceBoundsChanged(coordinates.boundsInRoot())
            }
            .semantics {
                role = Role.Button
                contentDescription = miniPlayerOpenContentDescription(state.currentSong)
            }
            .miniPlayerSwipeGestures(
                onSwipeLeft = {
                    isNextTransition = true
                    callbacks.onNextClick()
                },
                onSwipeRight = {
                    isNextTransition = false
                    callbacks.onPreviousClick()
                },
                defaultMorphCallbacks = defaultMorphCallbacks
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = tonalElevation,
        shadowElevation = 10.dp
    ) {
        AnimatedContent(
            targetState = state.currentSong,
            transitionSpec = {
                val enterDirection = if (isNextTransition) 1 else -1
                val exitDirection = -enterDirection
                (slideInHorizontally(tween(190)) { width ->
                    enterDirection * width / 3
                } + fadeIn(tween(160))).togetherWith(
                    slideOutHorizontally(tween(170)) { width ->
                        exitDirection * width / 3
                    } + fadeOut(tween(140))
                )
            },
            contentKey = { song -> song.id },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(52.dp),
            label = "miniPlayerSong"
        ) { displayedSong ->
            content(state.copy(currentSong = displayedSong))
        }
    }
}

@Composable
internal fun MiniPlayerArtwork(
    song: Song,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = song.albumArtUri,
        contentDescription = "Album art for ${song.miniTitle}",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_media_play),
        placeholder = painterResource(R.drawable.ic_media_play)
    )
}

@Composable
internal fun MiniPlayerPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    decoration: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        decoration()
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = miniPlayerPlaybackContentDescription(isPlaying),
                tint = iconTint
            )
        }
    }
}

internal val Song.miniTitle: String
    get() = title.ifBlank { "Unknown Title" }

internal val Song.miniArtist: String
    get() = artist.ifBlank { "Unknown Artist" }

internal fun miniPlayerOpenContentDescription(song: Song): String =
    "Open player for ${song.miniTitle}"

internal fun miniPlayerPlaybackContentDescription(isPlaying: Boolean): String =
    if (isPlaying) "Pause" else "Play"

internal fun normalizedMiniPlayerProgress(currentPosition: Int, duration: Int): Float {
    if (duration <= 0) return 0f
    return (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

private fun Modifier.miniPlayerSwipeGestures(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    defaultMorphCallbacks: DefaultMiniPlayerMorphCallbacks?
): Modifier = pointerInput(Unit) {
    var totalDragX = 0f
    var totalDragY = 0f
    val velocityTracker = VelocityTracker()
    var ownsVerticalDrag = false

    detectDragGestures(
        onDragStart = {
            totalDragX = 0f
            totalDragY = 0f
            velocityTracker.resetTracking()
            ownsVerticalDrag = false
        },
        onDrag = { change, dragAmount ->
            totalDragX += dragAmount.x
            totalDragY += dragAmount.y
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            if (defaultMorphCallbacks == null) {
                change.consume()
            } else if (!ownsVerticalDrag &&
                abs(totalDragY) > abs(totalDragX)
            ) {
                ownsVerticalDrag = true
                defaultMorphCallbacks.onDragStart()
            }
            if (ownsVerticalDrag) {
                change.consume()
                defaultMorphCallbacks?.onDragBy(dragAmount.y)
            } else if (abs(totalDragX) > abs(totalDragY)) {
                change.consume()
            }
        },
        onDragEnd = {
            if (ownsVerticalDrag) {
                defaultMorphCallbacks?.onDragEnd(
                    velocityTracker.calculateVelocity().y
                )
            } else if (abs(totalDragX) > abs(totalDragY)) {
                val swipeThreshold = 120f
                if (totalDragX > swipeThreshold) {
                    onSwipeRight()
                } else if (totalDragX < -swipeThreshold) {
                    onSwipeLeft()
                }
            }
        },
        onDragCancel = {
            if (ownsVerticalDrag) {
                defaultMorphCallbacks?.onDragCancel()
            }
        }
    )
}
