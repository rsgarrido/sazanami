package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import kotlin.math.roundToInt

/**
 * Transparent root plus a physically growing Pocket Flip shell.
 *
 * The expanded endpoint is still composed for its real layout and interactions, but it is clipped
 * to [geometry] and suppresses artwork/metadata/progress/play while this renderer owns them.
 */
@Composable
internal fun PocketFlipPlayerMorph(
    progress: Float,
    geometry: PocketFlipMorphGeometry?,
    sharedGeometry: PocketFlipSharedGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens,
    content: @Composable (
        displayReveal: Float,
        hingeReveal: Float,
        controlsReveal: Float,
        inputEnabled: Boolean
    ) -> Unit
) {
    val palette = remember(tokens) { PocketFlipPalette.from(tokens) }
    val density = LocalDensity.current
    val p = progress.coerceIn(0f, 1f)

    CompositionLocalProvider(LocalPocketFlipPalette provides palette) {
        Box(modifier = Modifier.fillMaxSize()) {
            geometry?.shell?.let { shell ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = shell.left.roundToInt(),
                                y = shell.top.roundToInt()
                            )
                        }
                        .size(
                            width = with(density) { shell.width.toDp() },
                            height = with(density) { shell.height.toDp() }
                        )
                        .clip(RoundedCornerShape((10f * (1f - p)).dp))
                        .pocketFlipShellFinish()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipPocketFlipShell(geometry = geometry, progress = p)
            ) {
                content(
                    pocketFlipDisplayReveal(p),
                    pocketFlipHingeReveal(p),
                    pocketFlipControlsReveal(p),
                    pocketFlipExpandedInputEnabled(p)
                )

                if (
                    p > 0f &&
                    p < 1f &&
                    sharedGeometry != null &&
                    currentSong != null
                ) {
                    PocketFlipSharedContent(
                        geometry = sharedGeometry,
                        progress = p,
                        song = currentSong,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        onPlayPauseClick = onPlayPauseClick,
                        tokens = tokens
                    )
                }
            }
        }
    }
}

@Composable
private fun PocketFlipSharedContent(
    geometry: PocketFlipSharedGeometry,
    progress: Float,
    song: Song,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens
) {
    val density = LocalDensity.current

    fun Modifier.at(rect: Rect): Modifier = this
        .offset {
            IntOffset(
                x = rect.left.roundToInt(),
                y = rect.top.roundToInt()
            )
        }
        .size(
            width = with(density) { rect.width.toDp() },
            height = with(density) { rect.height.toDp() }
        )

    Box(
        modifier = Modifier
            .at(geometry.artwork)
            .clip(RoundedCornerShape((2f + progress * 2f).dp))
            .background(PocketFlipColors.artworkWell)
            .pocketFlipArtworkFrameFinish(),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "Current album artwork",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding((1f + progress * 4f).dp)
            )
            PocketFlipArtworkLcdTreatment(
                modifier = Modifier
                    .matchParentSize()
                    .padding((1f + progress * 4f).dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = PocketFlipColors.screenTextMuted,
                modifier = Modifier.size(with(density) {
                    (minOf(geometry.artwork.width, geometry.artwork.height) * 0.42f).toDp()
                })
            )
        }
    }

    Text(
        text = song.title.ifBlank { "Unknown title" },
        color = PocketFlipColors.screenText,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = (12f + 2f * progress).sp,
        lineHeight = (14f + 3f * progress).sp,
        maxLines = if (progress >= 0.72f) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(geometry.title)
    )

    Text(
        text = song.artist.ifBlank { "Unknown artist" },
        color = PocketFlipColors.screenText.copy(alpha = 0.76f),
        fontFamily = FontFamily.Monospace,
        fontSize = (9f + progress).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.at(geometry.artist)
    )

    PocketFlipTransitionProgress(
        progress = normalizedProgress(currentPosition, duration),
        modifier = Modifier.at(geometry.progress)
    )

    val miniButtonColor = tokens.secondaryAccentColor ?: tokens.shellColor.darken(0.3f)
    val controlColor = lerp(
        start = miniButtonColor,
        stop = PocketFlipColors.action,
        fraction = progress
    )
    val cornerRadius = (6f + progress * 28f).dp

    Box(
        modifier = Modifier
            .at(geometry.play)
            .clip(RoundedCornerShape(cornerRadius))
            .background(controlColor),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = lerp(
                    start = tokens.displayTextColor,
                    stop = PocketFlipColors.actionIcon,
                    fraction = progress
                ),
                modifier = Modifier.fillMaxSize(0.52f)
            )
        }
    }
}

@Composable
private fun PocketFlipTransitionProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val seekHousingColor = PocketFlipColors.seekHousing
    val screenAccentColor = PocketFlipColors.screenAccent
    val seekInactiveColor = PocketFlipColors.seekInactive

    Canvas(modifier = modifier) {
        val segmentCount = 18
        val gap = (size.width * 0.012f).coerceAtLeast(1f)
        val inset = (size.height * 0.18f).coerceAtLeast(1f)
        val usableWidth = (size.width - inset * 2f).coerceAtLeast(1f)
        val segmentWidth = (
                usableWidth - gap * (segmentCount - 1)
                ).coerceAtLeast(1f) / segmentCount

        drawRoundRect(
            color = seekHousingColor,
            cornerRadius = CornerRadius(size.height * 0.18f)
        )

        repeat(segmentCount) { index ->
            val segmentProgress = (index + 1).toFloat() / segmentCount
            drawRect(
                color = if (segmentProgress <= progress) {
                    screenAccentColor
                } else {
                    seekInactiveColor
                },
                topLeft = Offset(
                    x = inset + index * (segmentWidth + gap),
                    y = inset
                ),
                size = Size(
                    width = segmentWidth,
                    height = (size.height - inset * 2f).coerceAtLeast(1f)
                )
            )
        }
    }
}

private fun normalizedProgress(position: Int, duration: Int): Float =
    if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)

private fun Modifier.clipPocketFlipShell(
    geometry: PocketFlipMorphGeometry?,
    progress: Float
): Modifier = drawWithContent {
    val shell = geometry?.shell ?: return@drawWithContent
    val radius = 10.dp.toPx() * (1f - progress.coerceIn(0f, 1f))
    val path = Path().apply {
        addRoundRect(
            RoundRect(shell, CornerRadius(radius, radius))
        )
    }
    clipPath(path) { this@drawWithContent.drawContent() }
}