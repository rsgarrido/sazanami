package io.github.rsgarrido.sazanami.ui.player.mini

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten

@Composable
fun PocketFlipMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    tokens: PlayerThemeTokens,
    modifier: Modifier = Modifier,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
    morphBounds: PocketFlipMorphBounds? = null
) {
    val displayText = tokens.displayTextColor
    val buttonColor = tokens.secondaryAccentColor ?: tokens.shellColor.darken(0.3f)
    val dragCallbacks = morphCallbacks
    val dragState = rememberDraggableState { delta ->
        dragCallbacks?.onDragBy?.invoke(delta)
    }
    val morphDragModifier = if (dragCallbacks == null) {
        Modifier
    } else {
        Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            enabled = true,
            startDragImmediately = false,
            onDragStarted = { dragCallbacks.onDragStart() },
            onDragStopped = { velocity -> dragCallbacks.onDragEnd(velocity) }
        )
    }
    val sharedAlpha = if (morphOwnsVisuals) 0f else 1f

    MiniPlayerScaffold(
        state = state,
        callbacks = callbacks,
        modifier = modifier,
        containerColor = tokens.shellColor,
        borderColor = tokens.shellColor.lighten(0.2f),
        shape = RoundedCornerShape(10.dp)
    ) { displayedState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(tokens.displayBackgroundColor, RoundedCornerShape(4.dp))
                    .border(
                        2.dp,
                        tokens.displayBackgroundColor.darken(0.75f),
                        RoundedCornerShape(4.dp)
                    )
                    .then(morphDragModifier)
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .onGloballyPositioned { coordinates ->
                            morphBounds?.updateMiniArtwork(coordinates.boundsInRoot())
                        }
                        .then(
                            if (morphOwnsVisuals) {
                                Modifier.clearAndSetSemantics { }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (!morphOwnsVisuals) {
                        MiniPlayerArtwork(
                            song = displayedState.currentSong,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(7.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayedState.currentSong.miniTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = displayText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                morphBounds?.updateMiniTitle(coordinates.boundsInRoot())
                            }
                            .graphicsLayer { alpha = sharedAlpha }
                            .then(
                                if (morphOwnsVisuals) {
                                    Modifier.clearAndSetSemantics { }
                                } else {
                                    Modifier
                                }
                            )
                    )
                    Text(
                        text = displayedState.currentSong.miniArtist,
                        style = MaterialTheme.typography.labelSmall,
                        color = displayText.copy(alpha = 0.68f),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                morphBounds?.updateMiniArtist(coordinates.boundsInRoot())
                            }
                            .graphicsLayer { alpha = sharedAlpha }
                            .then(
                                if (morphOwnsVisuals) {
                                    Modifier.clearAndSetSemantics { }
                                } else {
                                    Modifier
                                }
                            )
                    )
                    SegmentedProgress(
                        progress = normalizedMiniPlayerProgress(
                            displayedState.currentPosition,
                            displayedState.duration
                        ),
                        activeColor = tokens.accentColor,
                        inactiveColor = displayText.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                morphBounds?.updateMiniProgress(coordinates.boundsInRoot())
                            }
                            .graphicsLayer { alpha = sharedAlpha }
                            .then(
                                if (morphOwnsVisuals) {
                                    Modifier.clearAndSetSemantics { }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            MiniPlayerQueueButton(
                onClick = callbacks.onQueueHubClick,
                iconTint = displayText,
                modifier = if (morphOwnsVisuals) Modifier.clearAndSetSemantics { } else Modifier
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (morphOwnsVisuals) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!morphOwnsVisuals) {
                    MiniPlayerPlayPauseButton(
                        isPlaying = displayedState.isPlaying,
                        onClick = callbacks.onPlayPauseClick,
                        iconTint = displayText,
                        decoration = {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .onGloballyPositioned { coordinates ->
                                        morphBounds?.updateMiniPlay(coordinates.boundsInRoot())
                                    }
                                    .background(buttonColor, RoundedCornerShape(6.dp))
                                    .border(
                                        2.dp,
                                        buttonColor.lighten(0.22f),
                                        RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedProgress(
    progress: Float,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.height(5.dp)
    ) {
        val segmentCount = 10
        val gap = 2.dp.toPx()
        val segmentWidth = (size.width - gap * (segmentCount - 1)) / segmentCount
        val activeSegments = (progress * segmentCount).toInt()
        repeat(segmentCount) { index ->
            val left = index * (segmentWidth + gap)
            drawRect(
                color = if (index < activeSegments) activeColor else inactiveColor,
                topLeft = Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(segmentWidth, size.height)
            )
        }
    }
}
