package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
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

@Composable
internal fun PocketDiscPlayerMorph(
    progress: Float,
    geometry: PocketDiscMorphGeometry?,
    sharedGeometry: PocketDiscSharedGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens,
    content: @Composable (
        headerReveal: Float,
        mediaReveal: Float,
        panelReveal: Float,
        controlsReveal: Float,
        inputEnabled: Boolean
    ) -> Unit
) {
    val palette = remember(tokens) { PocketDiscPalette.from(tokens) }
    val density = LocalDensity.current
    val p = progress.coerceIn(0f, 1f)

    CompositionLocalProvider(LocalPocketDiscPalette provides palette) {
        Box(modifier = Modifier.fillMaxSize()) {
            geometry?.shell?.let { shell ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset(shell.left.roundToInt(), shell.top.roundToInt()) }
                        .size(
                            width = with(density) { shell.width.toDp() },
                            height = with(density) { shell.height.toDp() }
                        )
                        .clip(RoundedCornerShape((9f * (1f - p)).dp))
                        .background(PocketDiscColors.shell)
                        .border(
                            1.dp,
                            PocketDiscColors.edge.copy(alpha = 0.72f),
                            RoundedCornerShape((9f * (1f - p)).dp)
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipPocketDiscShell(geometry, p)
            ) {
                if (p > 0f && p < 1f && sharedGeometry != null && currentSong != null) {
                    SharedPocketDiscArtwork(
                        rect = sharedGeometry.artwork,
                        song = currentSong,
                        progress = p
                    )
                }

                content(
                    pocketDiscHeaderReveal(p),
                    pocketDiscMediaReveal(p),
                    pocketDiscPanelReveal(p),
                    pocketDiscControlsReveal(p),
                    pocketDiscExpandedInputEnabled(p)
                )

                if (p > 0f && p < 1f && sharedGeometry != null && currentSong != null) {
                    SharedPocketDiscForeground(
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
private fun SharedPocketDiscArtwork(
    rect: Rect,
    song: Song,
    progress: Float
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .atPocketDiscRect(rect, density)
            .clip(RoundedCornerShape((4f + progress * 3f).dp))
            .background(PocketDiscColors.panelDeep)
            .border(1.dp, PocketDiscColors.edge, RoundedCornerShape((4f + progress * 3f).dp))
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = "Current album artwork",
            contentScale = ContentScale.Fit,
            error = painterResource(R.drawable.ic_media_play),
            placeholder = painterResource(R.drawable.ic_media_play),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SharedPocketDiscForeground(
    geometry: PocketDiscSharedGeometry,
    progress: Float,
    song: Song,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    tokens: PlayerThemeTokens
) {
    val density = LocalDensity.current
    val colors = PocketDiscColors

    Text(
        text = song.title.ifBlank { "Unknown title" },
        color = lerp(tokens.displayTextColor, colors.lcdText, progress),
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = (10f + 7f * progress).sp,
            lineHeight = (12f + 8f * progress).sp
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.atPocketDiscRect(geometry.title, density)
    )

    Text(
        text = song.artist.ifBlank { "Unknown artist" },
        color = lerp(tokens.displayTextColor.copy(alpha = 0.66f), colors.lcdTextMuted, progress),
        fontFamily = FontFamily.Monospace,
        fontSize = (8f + 2f * progress).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.atPocketDiscRect(geometry.artist, density)
    )

    PocketDiscSegmentedProgress(
        progress = normalizedPocketDiscProgress(currentPosition, duration),
        segmentCount = (18 + 12 * progress).roundToInt(),
        activeColor = colors.lcdGlow,
        inactiveColor = colors.lcdGlowDim.copy(alpha = 0.16f),
        modifier = Modifier.atPocketDiscRect(geometry.progress, density)
    )

    Box(
        modifier = Modifier
            .atPocketDiscRect(geometry.play, density)
            .clip(RoundedCornerShape((4f + progress).dp))
            .background(
                lerp(
                    tokens.secondaryAccentColor ?: tokens.accentColor.darken(0.25f),
                    colors.activeDim,
                    progress
                )
            )
            .border(1.dp, colors.active, RoundedCornerShape((4f + progress).dp))
            .clickable(onClick = onPlayPauseClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = colors.lcdText,
            modifier = Modifier.fillMaxSize(0.48f)
        )
    }
}

private fun Modifier.atPocketDiscRect(
    rect: Rect,
    density: androidx.compose.ui.unit.Density
): Modifier = this
    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
    .size(
        width = with(density) { rect.width.toDp() },
        height = with(density) { rect.height.toDp() }
    )

private fun Modifier.clipPocketDiscShell(
    geometry: PocketDiscMorphGeometry?,
    progress: Float
): Modifier = drawWithContent {
    val shell = geometry?.shell ?: return@drawWithContent
    val radius = 9.dp.toPx() * (1f - progress.coerceIn(0f, 1f))
    val path = Path().apply { addRoundRect(RoundRect(shell, CornerRadius(radius, radius))) }
    clipPath(path) { this@drawWithContent.drawContent() }
}
