package io.github.rsgarrido.sazanami.ui.player.pocketcassette

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Transparent root plus a physically growing Pocket Cassette shell.
 *
 * The expanded endpoint remains composed for measurement and interaction, but suppresses its shared
 * artwork, metadata, progress, and primary control while this renderer owns them.
 */
@Composable
internal fun PocketCassettePlayerMorph(
    progress: Float,
    geometry: PocketCassetteMorphGeometry?,
    sharedGeometry: PocketCassetteSharedGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens,
    content: @Composable (
        headerReveal: Float,
        windowReveal: Float,
        mechanismReveal: Float,
        controlsReveal: Float,
        inputEnabled: Boolean
    ) -> Unit
) {
    val palette = remember(tokens) { PocketCassettePalette.from(tokens) }
    val density = LocalDensity.current
    val p = progress.coerceIn(0f, 1f)

    CompositionLocalProvider(LocalPocketCassettePalette provides palette) {
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
                        .clip(RoundedCornerShape((12f * (1f - p)).dp))
                        .pocketCassetteShellFinish()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipPocketCassetteShell(geometry = geometry, progress = p)
            ) {
                if (
                    p > 0f &&
                    p < 1f &&
                    sharedGeometry != null &&
                    currentSong != null
                ) {
                    PocketCassetteSharedArtwork(
                        rect = sharedGeometry.artwork,
                        progress = p,
                        song = currentSong
                    )
                }

                content(
                    pocketCassetteHeaderReveal(p),
                    pocketCassetteWindowReveal(p),
                    pocketCassetteMechanismReveal(p),
                    pocketCassetteControlsReveal(p),
                    pocketCassetteExpandedInputEnabled(p)
                )

                if (
                    p > 0f &&
                    p < 1f &&
                    sharedGeometry != null &&
                    currentSong != null
                ) {
                    PocketCassetteSharedForeground(
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
private fun PocketCassetteSharedArtwork(
    rect: Rect,
    progress: Float,
    song: Song
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .atPocketCassetteRect(rect, density)
            .clip(RoundedCornerShape((3f + progress * 4f).dp))
            .background(PocketCassetteColors.window),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "Current album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f * progress))
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = PocketCassetteColors.windowTextMuted,
                modifier = Modifier.size(
                    with(density) {
                        (minOf(rect.width, rect.height) * 0.42f).toDp()
                    }
                )
            )
        }
    }
}

@Composable
private fun PocketCassetteSharedForeground(
    geometry: PocketCassetteSharedGeometry,
    progress: Float,
    song: Song,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens
) {
    val density = LocalDensity.current

    Text(
        text = song.title.ifBlank { "Unknown title" },
        color = lerp(
            tokens.displayTextColor,
            PocketCassetteColors.windowText,
            progress
        ),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = (11f + 2f * progress).sp,
        lineHeight = (13f + 2f * progress).sp,
        maxLines = if (progress >= 0.72f) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.atPocketCassetteRect(geometry.title, density)
    )

    Text(
        text = song.artist.ifBlank { "Unknown artist" },
        color = lerp(
            tokens.displayTextColor.copy(alpha = 0.7f),
            PocketCassetteColors.windowTextMuted,
            progress
        ),
        fontFamily = FontFamily.Monospace,
        fontSize = (8f + progress).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.atPocketCassetteRect(geometry.artist, density)
    )

    PocketCassetteTransitionProgress(
        progress = normalizedPocketCassetteProgress(currentPosition, duration),
        morphProgress = progress,
        modifier = Modifier.atPocketCassetteRect(geometry.progress, density)
    )

    val miniButton = tokens.secondaryAccentColor ?: tokens.accentColor.darken(0.25f)
    Box(
        modifier = Modifier
            .atPocketCassetteRect(geometry.play, density)
            .clip(RoundedCornerShape((5f + 1f * progress).dp))
            .background(
                lerp(
                    miniButton,
                    PocketCassetteColors.button,
                    progress
                )
            )
            .pocketCassetteBevel(radius = (5f + progress).dp),
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
                    tokens.displayTextColor,
                    PocketCassetteColors.buttonActive,
                    progress
                ),
                modifier = Modifier.fillMaxSize(0.48f)
            )
        }
    }
}

@Composable
private fun PocketCassetteTransitionProgress(
    progress: Float,
    morphProgress: Float,
    modifier: Modifier = Modifier
) {
    val colors = PocketCassetteColors
    Canvas(modifier = modifier) {
        val p = progress.coerceIn(0f, 1f)
        val reveal = morphProgress.coerceIn(0f, 1f)
        val slotHeight = (size.height * (0.30f + reveal * 0.18f)).coerceAtLeast(2f)
        val slotTop = (size.height - slotHeight) / 2f
        val inset = (size.width * 0.018f).coerceAtLeast(1f)
        val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)

        drawRoundRect(
            color = colors.buttonEdge.copy(alpha = reveal),
            topLeft = Offset(0f, slotTop),
            size = Size(size.width, slotHeight),
            cornerRadius = CornerRadius(slotHeight / 2f)
        )
        drawRoundRect(
            color = colors.orange,
            topLeft = Offset(inset, slotTop + slotHeight * 0.28f),
            size = Size(trackWidth * p, (slotHeight * 0.44f).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(slotHeight * 0.2f)
        )

        if (reveal > 0.45f) {
            val thumbAlpha = ((reveal - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val thumbWidth = (size.width * 0.035f).coerceAtLeast(4f)
            val thumbHeight = (slotHeight + size.height * 0.28f).coerceAtMost(size.height)
            val thumbX = (inset + trackWidth * p - thumbWidth / 2f)
                .coerceIn(0f, (size.width - thumbWidth).coerceAtLeast(0f))
            drawRoundRect(
                color = colors.silverLight.copy(alpha = thumbAlpha),
                topLeft = Offset(thumbX, (size.height - thumbHeight) / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawLine(
                color = colors.shellInk.copy(alpha = 0.7f * thumbAlpha),
                start = Offset(thumbX + thumbWidth / 2f, (size.height - thumbHeight) / 2f),
                end = Offset(thumbX + thumbWidth / 2f, (size.height + thumbHeight) / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

private fun normalizedPocketCassetteProgress(position: Int, duration: Int): Float =
    if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)

private fun Modifier.atPocketCassetteRect(
    rect: Rect,
    density: androidx.compose.ui.unit.Density
): Modifier = this
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

private fun Modifier.clipPocketCassetteShell(
    geometry: PocketCassetteMorphGeometry?,
    progress: Float
): Modifier = drawWithContent {
    val shell = geometry?.shell ?: return@drawWithContent
    val radius = 12.dp.toPx() * (1f - progress.coerceIn(0f, 1f))
    val path = Path().apply {
        addRoundRect(RoundRect(shell, CornerRadius(radius, radius)))
    }
    clipPath(path) { this@drawWithContent.drawContent() }
}
