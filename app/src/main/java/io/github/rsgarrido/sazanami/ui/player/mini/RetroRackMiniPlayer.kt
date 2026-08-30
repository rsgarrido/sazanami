package io.github.rsgarrido.sazanami.ui.player.mini

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import java.util.Locale

@Composable
fun RetroRackMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    tokens: PlayerThemeTokens,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
    morphBounds: RetroRackMorphBounds? = null,
    modifier: Modifier = Modifier
) {
    val panelColor = tokens.shellColor
    val displayColor = tokens.displayBackgroundColor
    val meterColor = tokens.accentColor

    MiniPlayerScaffold(
        state = state,
        callbacks = callbacks,
        modifier = modifier.graphicsLayer { alpha = if (morphOwnsVisuals) 0f else 1f },
        containerColor = panelColor.darken(0.35f),
        borderColor = panelColor.lighten(0.32f),
        shape = RoundedCornerShape(8.dp),
        defaultMorphCallbacks = morphCallbacks
    ) { displayedState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .onGloballyPositioned { morphBounds?.updateMiniArtwork(it.boundsInRoot()) }
            ) {
                if (!morphOwnsVisuals) {
                    MiniPlayerArtwork(song = displayedState.currentSong, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.width(7.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(displayColor, RoundedCornerShape(3.dp))
                    .border(1.dp, panelColor.lighten(0.18f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = displayedState.currentSong.miniTitle.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = meterColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    ,modifier = Modifier.onGloballyPositioned { morphBounds?.updateMiniTitle(it.boundsInRoot()) }
                )
                Text(
                    text = displayedState.currentSong.miniArtist.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = meterColor.copy(alpha = 0.62f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    ,modifier = Modifier.onGloballyPositioned { morphBounds?.updateMiniArtist(it.boundsInRoot()) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .onGloballyPositioned { morphBounds?.updateMiniProgress(it.boundsInRoot()) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                normalizedMiniPlayerProgress(
                                    displayedState.currentPosition,
                                    displayedState.duration
                                )
                            )
                            .height(2.dp)
                            .background(meterColor)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            MiniPlayerPlayPauseButton(
                isPlaying = displayedState.isPlaying,
                onClick = callbacks.onPlayPauseClick,
                modifier = Modifier.onGloballyPositioned { morphBounds?.updateMiniPlay(it.boundsInRoot()) },
                iconTint = tokens.displayTextColor,
                decoration = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(panelColor, RoundedCornerShape(3.dp))
                            .border(1.dp, panelColor.lighten(0.36f), RoundedCornerShape(3.dp))
                    )
                }
            )
        }
    }
}
