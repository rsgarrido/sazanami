package io.github.rsgarrido.sazanami.ui.player.mini

import android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscSegmentedProgress
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.normalizedPocketDiscProgress
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import java.util.Locale

@Composable
fun PocketDiscMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    tokens: PlayerThemeTokens,
    modifier: Modifier = Modifier,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
    morphBounds: PocketDiscMorphBounds? = null
) {
    val ink = tokens.displayTextColor
    val accent = tokens.accentColor
    val sharedAlpha = if (morphOwnsVisuals) 0f else 1f

    MiniPlayerScaffold(
        state = state,
        callbacks = callbacks,
        modifier = modifier,
        containerColor = tokens.shellColor,
        borderColor = tokens.shellColor.lighten(0.28f),
        shape = RoundedCornerShape(9.dp),
        defaultMorphCallbacks = morphCallbacks
    ) { displayedState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            PocketDiscMiniMediaBay(
                state = displayedState,
                tokens = tokens,
                morphOwnsVisuals = morphOwnsVisuals,
                morphBounds = morphBounds
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayedState.currentSong.miniTitle.uppercase(Locale.ROOT),
                    color = ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
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
                    color = ink.copy(alpha = 0.66f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            morphBounds?.updateMiniArtist(coordinates.boundsInRoot())
                        }
                        .graphicsLayer { alpha = sharedAlpha }
                        .then(
                            if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                            else Modifier
                        )
                )
                PocketDiscSegmentedProgress(
                    progress = normalizedPocketDiscProgress(
                        displayedState.currentPosition,
                        displayedState.duration
                    ),
                    segmentCount = 18,
                    activeColor = accent,
                    inactiveColor = accent.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(5.dp)
                        .onGloballyPositioned { coordinates ->
                            morphBounds?.updateMiniProgress(coordinates.boundsInRoot())
                        }
                        .graphicsLayer { alpha = sharedAlpha }
                        .then(
                            if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                            else Modifier
                        )
                )
            }

            MiniPlayerQueueButton(
                onClick = callbacks.onQueueHubClick,
                iconTint = ink,
                modifier = if (morphOwnsVisuals) Modifier.clearAndSetSemantics { } else Modifier
            )

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
                                    .size(width = 36.dp, height = 32.dp)
                                    .onGloballyPositioned { coordinates ->
                                        morphBounds?.updateMiniPlay(coordinates.boundsInRoot())
                                    }
                                    .background(
                                        tokens.secondaryAccentColor ?: accent.darken(0.25f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, accent.lighten(0.18f), RoundedCornerShape(4.dp))
                            )
                        }
                    )
                } else {
                    // Keep the destination measurement alive while the shared morph owns the visual.
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 32.dp)
                            .onGloballyPositioned { coordinates ->
                                morphBounds?.updateMiniPlay(coordinates.boundsInRoot())
                            }
                    )
                }
            }
        }
    }
}

/**
 * Compact Pocket Disc identity for the mini player: uncropped album artwork plus a tiny
 * cartridge/disc window. The artwork itself remains an independent shared morph anchor.
 */
@Composable
private fun PocketDiscMiniMediaBay(
    state: MiniPlayerState,
    tokens: PlayerThemeTokens,
    morphOwnsVisuals: Boolean,
    morphBounds: PocketDiscMorphBounds?
) {
    val sharedAlpha = if (morphOwnsVisuals) 0f else 1f
    Row(
        modifier = Modifier
            .width(66.dp)
            .height(44.dp)
            .background(tokens.displayBackgroundColor, RoundedCornerShape(5.dp))
            .border(1.dp, tokens.accentColor.darken(0.34f), RoundedCornerShape(5.dp))
            .graphicsLayer { alpha = sharedAlpha }
            .then(
                if (morphOwnsVisuals) Modifier.clearAndSetSemantics { }
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.displayBackgroundColor)
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateMiniArtwork(coordinates.boundsInRoot())
                }
        ) {
            if (!morphOwnsVisuals) {
                AsyncImage(
                    model = state.currentSong.albumArtUri,
                    contentDescription = "Album art for ${state.currentSong.miniTitle}",
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.drawable.ic_media_play),
                    placeholder = painterResource(R.drawable.ic_media_play),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Box(
            modifier = Modifier
                .width(24.dp)
                .height(34.dp)
                .background(tokens.shellColor.darken(0.20f), RoundedCornerShape(3.dp))
                .border(1.dp, tokens.shellColor.lighten(0.22f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(17.dp)) {
                drawCircle(
                    color = tokens.accentColor.copy(alpha = 0.72f),
                    radius = size.minDimension * 0.43f,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawCircle(
                    color = tokens.displayTextColor.copy(alpha = 0.72f),
                    radius = size.minDimension * 0.12f,
                    center = center
                )
                drawLine(
                    color = tokens.accentColor.copy(alpha = 0.55f),
                    start = Offset(size.width * 0.18f, size.height * 0.78f),
                    end = Offset(size.width * 0.82f, size.height * 0.78f),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
