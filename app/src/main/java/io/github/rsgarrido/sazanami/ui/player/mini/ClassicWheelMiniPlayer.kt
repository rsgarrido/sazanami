package io.github.rsgarrido.sazanami.ui.player.mini

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten

@Composable
fun ClassicWheelMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    tokens: PlayerThemeTokens,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
    morphBounds: ClassicWheelMorphBounds? = null,
    modifier: Modifier = Modifier
) {
    val wheelColor = tokens.accentColor
    val centerColor = tokens.secondaryAccentColor ?: tokens.accentColor.lighten(0.2f)
    val progress = normalizedMiniPlayerProgress(state.currentPosition, state.duration)

    MiniPlayerScaffold(
        state = state,
        callbacks = callbacks,
        modifier = modifier.graphicsLayer {
            alpha = if (morphOwnsVisuals) 0f else 1f
        },
        containerColor = tokens.shellColor,
        borderColor = tokens.shellColor.darken(0.35f),
        defaultMorphCallbacks = morphCallbacks
    ) { displayedState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniPlayerArtwork(
                song = displayedState.currentSong,
                modifier = Modifier
                .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .onGloballyPositioned { morphBounds?.updateMiniArtwork(it.boundsInRoot()) }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayedState.currentSong.miniTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.displayTextColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onGloballyPositioned { morphBounds?.updateMiniTitle(it.boundsInRoot()) }
                )
                Text(
                    text = displayedState.currentSong.miniArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.displayTextColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onGloballyPositioned { morphBounds?.updateMiniArtist(it.boundsInRoot()) }
                )
            }
            MiniPlayerQueueButton(
                onClick = callbacks.onQueueHubClick,
                iconTint = tokens.displayTextColor
            )
            MiniPlayerPlayPauseButton(
                isPlaying = displayedState.isPlaying,
                onClick = callbacks.onPlayPauseClick,
                modifier = Modifier.onGloballyPositioned {
                    morphBounds?.updateMiniPlayPause(it.boundsInRoot())
                },
                iconTint = tokens.displayTextColor,
                decoration = {
                    Canvas(
                        modifier = Modifier
                            .size(44.dp)
                            .background(wheelColor, CircleShape)
                    ) {
                        drawCircle(
                            color = wheelColor.lighten(0.22f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawArc(
                            color = centerColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx())
                        )
                        drawCircle(
                            color = centerColor.copy(alpha = 0.38f),
                            radius = size.minDimension * 0.22f
                        )
                    }
                }
            )
        }
    }
}
