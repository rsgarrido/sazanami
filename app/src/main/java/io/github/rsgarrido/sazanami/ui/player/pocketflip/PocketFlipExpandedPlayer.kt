package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.waveform.WaveformData
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens

@Composable
fun PocketFlipExpandedPlayer(
    currentSong: Song?,
    waveformData: WaveformData? = null,
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
    tokens: PlayerThemeTokens = PocketFlipDefaultTokens,
    renderShell: Boolean = true,
    displayReveal: Float = 1f,
    hingeReveal: Float = 1f,
    controlsReveal: Float = 1f,
    inputEnabled: Boolean = true,
    collapseGestureEnabled: Boolean = false,
    morphBounds: PocketFlipMorphBounds? = null,
    sharedOwner: PocketFlipSharedOwner = PocketFlipSharedOwner.EXPANDED,
    onMorphDragStart: () -> Unit = {},
    onMorphDragBy: (Float) -> Unit = {},
    onMorphDragEnd: (Float) -> Unit = {},
    onMorphDragCancel: () -> Unit = {}
) {
    val palette = remember(tokens) { PocketFlipPalette.from(tokens) }
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 700 || configuration.screenWidthDp < 360
    val safeCollapseDragModifier = Modifier.pocketFlipDownwardCollapseGesture(
        enabled = collapseGestureEnabled,
        onDragStart = onMorphDragStart,
        onDragBy = onMorphDragBy,
        onDragEnd = onMorphDragEnd,
        onDragCancel = onMorphDragCancel
    )

    CompositionLocalProvider(LocalPocketFlipPalette provides palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (renderShell) Modifier.pocketFlipShellFinish() else Modifier)
                .padding(
                    horizontal = if (compact) 10.dp else 16.dp,
                    vertical = if (compact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            PocketFlipDisplayHalf(
                currentSong = currentSong,
                waveformData = waveformData,
                isVisualizerWorkAllowed = isVisualizerWorkAllowed,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onSeekChange = onSeekChange,
                compact = compact,
                modifier = Modifier.weight(if (compact) 0.54f else 0.57f),
                displayReveal = displayReveal,
                inputEnabled = inputEnabled,
                morphBounds = morphBounds,
                sharedOwner = sharedOwner,
                collapseDragModifier = safeCollapseDragModifier
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = hingeReveal.coerceIn(0f, 1f) }
                    .then(safeCollapseDragModifier)
            ) {
                PocketFlipHinge(compact = compact)
            }

            PocketFlipControlHalf(
                currentSong = currentSong,
                isPlaying = isPlaying,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isCurrentSongFavorite = isCurrentSongFavorite,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                onOpenUpNextClick = onOpenUpNextClick,
                onCollapseClick = onCollapseClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                compact = compact,
                modifier = Modifier.weight(if (compact) 0.46f else 0.43f),
                controlsReveal = controlsReveal,
                inputEnabled = inputEnabled,
                morphBounds = morphBounds,
                sharedOwner = sharedOwner,
                deckDetailsDragModifier = safeCollapseDragModifier
            )
        }
    }
}

/**
 * Claims only downward vertical drags. Upward gestures remain available to the parent lyrics
 * gesture, while a drag that has started downward may still reverse upward before release.
 */
private fun Modifier.pocketFlipDownwardCollapseGesture(
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
                // Do not consume upward intent: the parent can continue opening lyrics.
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
