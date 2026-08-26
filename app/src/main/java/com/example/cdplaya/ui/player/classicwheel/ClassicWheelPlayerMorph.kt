package com.example.cdplaya.ui.player.classicwheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.data.Song
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Transition-owned device shell. Endpoint content remains the existing Classic Wheel
 * implementation, so playback and wheel state are never duplicated.
 */
@Composable
internal fun ClassicWheelPlayerMorph(
    progress: Float,
    geometry: ClassicWheelMorphGeometry?,
    sharedGeometry: ClassicWheelSharedGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    sharedPlayPauseAlpha: Float,
    tokens: PlayerThemeTokens,
    content: @Composable (screenAlpha: Float, wheelAlpha: Float, controlsActive: Boolean) -> Unit
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        if (geometry != null) {
            val shell = geometry.shell
            val radius = (18f * (1f - safeProgress)).dp
            Box(
                Modifier
                    .offset { IntOffset(shell.left.roundToInt(), shell.top.roundToInt()) }
                    .size(
                        with(density) { shell.width.coerceAtLeast(1f).toDp() },
                        with(density) { shell.height.coerceAtLeast(1f).toDp() }
                    )
                    .clip(RoundedCornerShape(radius))
                    .background(tokens.shellColor)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipClassicWheelShell(geometry, safeProgress)
        ) {
            content(
                classicWheelScreenReveal(safeProgress),
                classicWheelWheelReveal(safeProgress),
                classicWheelExpandedControlsActive(safeProgress)
            )
            if (sharedGeometry != null && currentSong != null) {
                ClassicWheelMorphSharedContent(
                    geometry = sharedGeometry,
                    progress = safeProgress,
                    song = currentSong,
                    isPlaying = isPlaying,
                    playPauseAlpha = sharedPlayPauseAlpha
                )
            }
        }
    }
}

private fun Modifier.clipClassicWheelShell(
    geometry: ClassicWheelMorphGeometry?, progress: Float
): Modifier = drawWithContent {
    val shell = geometry?.shell ?: return@drawWithContent
    val radius = 18.dp.toPx() * (1f - progress.coerceIn(0f, 1f))
    val path = Path().apply {
        addRoundRect(RoundRect(shell, CornerRadius(radius, radius)))
    }
    clipPath(path) { this@drawWithContent.drawContent() }
}

@Composable
private fun ClassicWheelMorphSharedContent(
    geometry: ClassicWheelSharedGeometry,
    progress: Float,
    song: Song,
    isPlaying: Boolean,
    playPauseAlpha: Float
) {
    val density = LocalDensity.current
    val artworkRadius = (8f - 5f * progress).coerceAtLeast(3f).dp
    AsyncImage(
        model = song.albumArtUri,
        contentDescription = "Album art for ${song.title}",
        modifier = Modifier
            .offset { IntOffset(geometry.artwork.left.roundToInt(), geometry.artwork.top.roundToInt()) }
            .size(with(density) { geometry.artwork.width.toDp() }, with(density) { geometry.artwork.height.toDp() })
            .clip(RoundedCornerShape(artworkRadius)),
        contentScale = ContentScale.Crop,
        error = painterResource(android.R.drawable.ic_media_play),
        placeholder = painterResource(android.R.drawable.ic_media_play)
    )
    Column(
        Modifier
            .offset { IntOffset(geometry.title.left.roundToInt(), geometry.title.top.roundToInt()) }
            .size(with(density) { geometry.title.width.toDp() }, with(density) { geometry.title.height.toDp() })
    ) {
        Text(
            text = song.title.ifBlank { "Unknown Title" },
            style = if (progress < .5f) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ClassicWheelColors.screenText,
            maxLines = if (progress < .6f) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    Text(
        text = song.artist.ifBlank { "Unknown Artist" },
        modifier = Modifier
            .offset { IntOffset(geometry.artist.left.roundToInt(), geometry.artist.top.roundToInt()) }
            .size(with(density) { geometry.artist.width.toDp() }, with(density) { geometry.artist.height.toDp() }),
        style = if (progress < .5f) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
        color = ClassicWheelColors.screenTextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    val safePlayPauseAlpha = playPauseAlpha.coerceIn(0f, 1f)
    if (safePlayPauseAlpha > 0f) {
        Box(
            modifier = Modifier
                .offset { IntOffset(geometry.playPause.left.roundToInt(), geometry.playPause.top.roundToInt()) }
                .size(with(density) { geometry.playPause.width.toDp() }, with(density) { geometry.playPause.height.toDp() })
                .graphicsLayer { alpha = safePlayPauseAlpha }
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    ClassicWheelColors.wheel.copy(alpha = 1f - .30f * progress)
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = ClassicWheelColors.wheelContent,
                modifier = Modifier.size((24f + 18f * progress).dp)
            )
        }
    }
}
