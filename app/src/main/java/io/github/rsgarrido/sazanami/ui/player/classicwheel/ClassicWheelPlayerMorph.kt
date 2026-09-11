package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
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
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.RetainedArtworkImage
import io.github.rsgarrido.sazanami.ui.player.mini.normalizedMiniPlayerProgress
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
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
    miniChromeGeometry: ClassicWheelMiniChromeGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    sharedPlayPauseAlpha: Float,
    tokens: PlayerThemeTokens,
    content: @Composable (screenAlpha: Float, wheelAlpha: Float, controlsActive: Boolean) -> Unit
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val transitionVisible = safeProgress > 0f
    val miniChromeAlpha = classicWheelMiniChromeAlpha(safeProgress)
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        if (geometry != null && transitionVisible) {
            val shell = geometry.shell
            val radius = (18f * (1f - safeProgress)).dp
            val shape = RoundedCornerShape(radius)
            Box(
                Modifier
                    .offset { IntOffset(shell.left.roundToInt(), shell.top.roundToInt()) }
                    .size(
                        with(density) { shell.width.coerceAtLeast(1f).toDp() },
                        with(density) { shell.height.coerceAtLeast(1f).toDp() }
                    )
                    .shadow((10f * miniChromeAlpha).dp, shape = shape, clip = false)
                    .clip(shape)
                    .background(tokens.shellColor)
                    .border(
                        width = 1.dp,
                        color = tokens.shellColor.darken(0.35f).copy(
                            alpha = tokens.shellColor.alpha * miniChromeAlpha
                        ),
                        shape = shape
                    )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipClassicWheelShell(geometry, safeProgress)
                .graphicsLayer { alpha = if (transitionVisible) 1f else 0f }
        ) {
            content(
                classicWheelScreenReveal(safeProgress),
                classicWheelWheelReveal(safeProgress),
                classicWheelExpandedControlsActive(safeProgress)
            )
            if (sharedGeometry != null && currentSong != null && transitionVisible) {
                ClassicWheelMorphSharedContent(
                    geometry = sharedGeometry,
                    progress = safeProgress,
                    song = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    playPauseAlpha = sharedPlayPauseAlpha,
                    tokens = tokens
                )
            }
            if (miniChromeGeometry != null && miniChromeAlpha > 0f && transitionVisible) {
                ClassicWheelMorphMiniChrome(
                    geometry = miniChromeGeometry,
                    alpha = miniChromeAlpha,
                    tokens = tokens
                )
            }
        }
    }
}

@Composable
private fun ClassicWheelMorphMiniChrome(
    geometry: ClassicWheelMiniChromeGeometry,
    alpha: Float,
    tokens: PlayerThemeTokens
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    geometry.queue.left.roundToInt(),
                    geometry.queue.top.roundToInt()
                )
            }
            .size(
                with(density) { geometry.queue.width.toDp() },
                with(density) { geometry.queue.height.toDp() }
            )
            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.QueueMusic,
            contentDescription = null,
            tint = tokens.displayTextColor,
            modifier = Modifier.size(24.dp)
        )
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
    currentPosition: Int,
    duration: Int,
    playPauseAlpha: Float,
    tokens: PlayerThemeTokens
) {
    val density = LocalDensity.current
    val artworkRadius = (8f - 5f * progress).coerceAtLeast(3f).dp
    RetainedArtworkImage(
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
            fontWeight = if (progress < .5f) FontWeight.SemiBold else FontWeight.Bold,
            color = lerp(tokens.displayTextColor, ClassicWheelColors.screenText, progress),
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
        color = lerp(
            tokens.displayTextColor.copy(alpha = 0.72f),
            ClassicWheelColors.screenTextMuted,
            progress
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    val safePlayPauseAlpha = playPauseAlpha.coerceIn(0f, 1f)
    if (safePlayPauseAlpha > 0f) {
        val miniChromeAlpha = classicWheelMiniChromeAlpha(progress)
        val centerColor = tokens.secondaryAccentColor ?: tokens.accentColor.lighten(0.2f)
        Box(
            modifier = Modifier
                .offset { IntOffset(geometry.playPause.left.roundToInt(), geometry.playPause.top.roundToInt()) }
                .size(with(density) { geometry.playPause.width.toDp() }, with(density) { geometry.playPause.height.toDp() })
                .graphicsLayer { alpha = safePlayPauseAlpha }
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    tokens.accentColor.copy(alpha = 1f - .30f * progress)
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            if (miniChromeAlpha > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = tokens.accentColor.lighten(0.22f).copy(alpha = miniChromeAlpha),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawArc(
                        color = centerColor.copy(alpha = miniChromeAlpha),
                        startAngle = -90f,
                        sweepAngle = 360f * normalizedMiniPlayerProgress(
                            currentPosition,
                            duration
                        ),
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawCircle(
                        color = centerColor.copy(alpha = 0.38f * miniChromeAlpha),
                        radius = size.minDimension * 0.22f
                    )
                }
            }
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = lerp(tokens.displayTextColor, ClassicWheelColors.wheelContent, progress),
                modifier = Modifier.size((24f + 18f * progress).dp)
            )
        }
    }
}
