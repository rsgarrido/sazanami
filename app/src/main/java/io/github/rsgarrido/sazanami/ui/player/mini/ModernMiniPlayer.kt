package io.github.rsgarrido.sazanami.ui.player.mini

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds

@Composable
fun ModernMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    modifier: Modifier = Modifier,
    morphBounds: DefaultPlayerMorphBounds? = null,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false
) {
    MiniPlayerScaffold(
        state = state,
        callbacks = callbacks,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f),
        tonalElevation = 4.dp,
        defaultMorphCallbacks = morphCallbacks,
        onSurfaceBoundsChanged = { bounds ->
            morphBounds?.updateMiniSurface(bounds)
        }
    ) { displayedState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val artworkModifier = Modifier
                .fillMaxHeight()
                .width(displayedState.albumArtSize)
                .clip(RoundedCornerShape(10.dp))
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateMiniArtwork(coordinates.boundsInRoot())
                }
            if (morphOwnsVisuals) {
                Box(modifier = artworkModifier)
            } else {
                MiniPlayerArtwork(
                    song = displayedState.currentSong,
                    modifier = artworkModifier
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateMiniText(coordinates.boundsInRoot())
                    }
                    .graphicsLayer { alpha = if (morphOwnsVisuals) 0f else 1f }
            ) {
                Text(
                    text = displayedState.currentSong.miniTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = displayedState.currentSong.miniArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            MiniPlayerPlayPauseButton(
                isPlaying = displayedState.isPlaying,
                onClick = callbacks.onPlayPauseClick,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateMiniPlayPause(coordinates.boundsInRoot())
                    }
                    .graphicsLayer { alpha = if (morphOwnsVisuals) 0f else 1f }
            )
        }
    }
}
