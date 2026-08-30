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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import java.util.Locale

@Composable
fun PocketCassetteMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    tokens: PlayerThemeTokens,
    modifier: Modifier = Modifier,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
    morphBounds: PocketCassetteMorphBounds? = null
) {
    val panel = tokens.accentColor
    val ink = tokens.displayTextColor
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
        borderColor = panel.lighten(0.18f),
        shape = RoundedCornerShape(12.dp)
    ) { displayedState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(morphDragModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CassetteWindow(
                    state = displayedState,
                    tokens = tokens,
                    morphOwnsVisuals = morphOwnsVisuals,
                    morphBounds = morphBounds
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayedState.currentSong.miniTitle.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink,
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
                                if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                                else Modifier
                            )
                    )
                    Text(
                        text = displayedState.currentSong.miniArtist.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.7f),
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
                                if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                                else Modifier
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!morphOwnsVisuals) {
                    MiniPlayerPlayPauseButton(
                        isPlaying = displayedState.isPlaying,
                        onClick = callbacks.onPlayPauseClick,
                        iconTint = ink,
                        decoration = {
                            Box(
                                modifier = Modifier
                                    .size(width = 34.dp, height = 28.dp)
                                    .onGloballyPositioned { coordinates ->
                                        morphBounds?.updateMiniPlay(coordinates.boundsInRoot())
                                    }
                                    .background(
                                        tokens.secondaryAccentColor ?: panel.darken(0.25f),
                                        RoundedCornerShape(5.dp)
                                    )
                                    .border(
                                        1.dp,
                                        panel.lighten(0.28f),
                                        RoundedCornerShape(5.dp)
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
private fun CassetteWindow(
    state: MiniPlayerState,
    tokens: PlayerThemeTokens,
    morphOwnsVisuals: Boolean,
    morphBounds: PocketCassetteMorphBounds?
) {
    val progress = normalizedMiniPlayerProgress(state.currentPosition, state.duration)
    val sharedAlpha = if (morphOwnsVisuals) 0f else 1f

    Row(
        modifier = Modifier
            .width(92.dp)
            .height(44.dp)
            .background(tokens.displayBackgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, tokens.accentColor.darken(0.35f), RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateMiniArtwork(coordinates.boundsInRoot())
                }
                .then(
                    if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                    else Modifier
                )
        ) {
            if (!morphOwnsVisuals) {
                MiniPlayerArtwork(
                    song = state.currentSong,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sharedAlpha }
            ) {
                val reelRadius = size.minDimension * 0.27f
                val left = Offset(size.width * 0.27f, size.height * 0.46f)
                val right = Offset(size.width * 0.73f, size.height * 0.46f)
                drawLine(
                    color = tokens.displayTextColor.copy(alpha = 0.48f),
                    start = left,
                    end = right,
                    strokeWidth = 2.dp.toPx()
                )
                listOf(left, right).forEach { center ->
                    drawCircle(
                        color = tokens.displayTextColor.copy(alpha = 0.72f),
                        radius = reelRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = tokens.accentColor,
                        radius = reelRadius * 0.34f,
                        center = center
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateMiniProgress(coordinates.boundsInRoot())
                    }
                    .graphicsLayer { alpha = sharedAlpha }
                    .then(
                        if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                        else Modifier
                    )
            ) {
                drawLine(
                    color = tokens.displayTextColor.copy(alpha = 0.16f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = tokens.secondaryAccentColor ?: Color.White,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width * progress, size.height / 2f),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
