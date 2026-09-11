package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.rsgarrido.sazanami.ui.player.RetainedArtworkImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import kotlin.math.roundToInt

/** A transparent root plus a physically interpolated rack shell. */
@Composable
internal fun RetroRackPlayerMorph(
    progress: Float,
    geometry: RetroRackMorphGeometry?,
    sharedGeometry: RetroRackSharedGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens,
    content: @Composable (deckReveal: Float, spectrumReveal: Float, queueReveal: Float, controlsReveal: Float, inputEnabled: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val p = progress.coerceIn(0f, 1f)
    val miniShellColor = tokens.shellColor.darken(0.35f)
    val expandedShellColor = RetroRackPalette.from(tokens).rackBackground
    Box(Modifier.fillMaxSize()) {
        geometry?.shell?.let { shell ->
            Box(Modifier.offset { IntOffset(shell.left.roundToInt(), shell.top.roundToInt()) }
                .size(with(density) { shell.width.toDp() }, with(density) { shell.height.toDp() })
                .clip(androidx.compose.foundation.shape.RoundedCornerShape((8f * (1f - p)).dp))
                .background(lerp(miniShellColor, expandedShellColor, p)))
        }
        Box(Modifier.fillMaxSize().clipRackShell(geometry, p)) {
            content(retroRackDeckReveal(p), retroRackSpectrumReveal(p), retroRackQueueReveal(p), retroRackControlsReveal(p), retroRackExpandedInputEnabled(p))
            if (p > 0f && p < 1f && sharedGeometry != null && currentSong != null) {
                RetroRackSharedContent(
                    sharedGeometry, p, currentSong, isPlaying, currentPosition, duration,
                    onPlayPauseClick, tokens
                )
            }
        }
    }
}

@Composable
private fun RetroRackSharedContent(
    geometry: RetroRackSharedGeometry,
    progress: Float,
    song: Song,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens
) {
    val density = LocalDensity.current
    fun Modifier.at(rect: androidx.compose.ui.geometry.Rect) = this
        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
    RetainedArtworkImage(
        model = song.albumArtUri,
        contentDescription = "Album art for ${song.title}",
        contentScale = ContentScale.Crop,
        error = painterResource(android.R.drawable.ic_media_play),
        placeholder = painterResource(android.R.drawable.ic_media_play),
        modifier = Modifier.at(geometry.artwork).clip(RoundedCornerShape((3f * (1f - progress)).dp))
    )
    Text(
        text = song.title.ifBlank { "Unknown Title" }.uppercase(),
        color = tokens.accentColor,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = (12f + progress).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(geometry.title)
    )
    Text(
        text = song.artist.ifBlank { "Unknown Artist" }.uppercase(),
        color = tokens.accentColor.copy(alpha = .65f),
        fontFamily = FontFamily.Monospace,
        fontSize = (9f + progress).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(geometry.artist)
    )
    Canvas(Modifier.at(geometry.progress)) {
        val visualProgress = normalizedProgress(currentPosition, duration)
        val frameInset = 1.dp.toPx() * progress
        val channelInset = 4.dp.toPx() * progress
        val channelHeight = retroRackMorphProgressTrackHeightDp(progress).dp.toPx()
            .coerceAtMost(size.height)
        val channelTop = (size.height - channelHeight) / 2f
        val channelWidth = (size.width - channelInset * 2f).coerceAtLeast(1f)

        drawRect(tokens.displayBackgroundColor)
        if (progress > 0f) {
            drawRect(tokens.shellColor.darken(0.822f).copy(alpha = progress))
            drawRect(
                color = tokens.displayBackgroundColor,
                topLeft = Offset(frameInset, frameInset),
                size = Size(
                    width = (size.width - frameInset * 2f).coerceAtLeast(1f),
                    height = (size.height - frameInset * 2f).coerceAtLeast(1f)
                )
            )
        }
        drawRect(
            color = tokens.shellColor.lighten(0.076f),
            topLeft = Offset(channelInset, channelTop),
            size = Size(channelWidth, channelHeight)
        )
        drawRect(
            color = tokens.accentColor,
            topLeft = Offset(channelInset, channelTop),
            size = Size(channelWidth * visualProgress, channelHeight)
        )

        if (progress > 0f) {
            val markerWidth = 5.dp.toPx() * progress
            val markerLeft = (channelInset + channelWidth * visualProgress - markerWidth / 2f)
                .coerceIn(frameInset, (size.width - frameInset - markerWidth).coerceAtLeast(frameInset))
            drawRect(
                color = tokens.displayTextColor.copy(alpha = progress),
                topLeft = Offset(markerLeft, frameInset),
                size = Size(
                    markerWidth,
                    (size.height - frameInset * 2f).coerceAtLeast(1f)
                )
            )
        }
    }
    Box(
        Modifier.at(geometry.play).background(tokens.shellColor, RoundedCornerShape(3.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        IconButton(onClick = onPlayPauseClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = tokens.displayTextColor
            )
        }
    }
}

private fun normalizedProgress(position: Int, duration: Int): Float =
    if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)

private fun Modifier.clipRackShell(geometry: RetroRackMorphGeometry?, progress: Float) = drawWithContent {
    val shell = geometry?.shell ?: return@drawWithContent
    val radius = 8.dp.toPx() * (1f - progress)
    val path = Path().apply { addRoundRect(RoundRect(shell, CornerRadius(radius, radius))) }
    clipPath(path) { this@drawWithContent.drawContent() }
}
