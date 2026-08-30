package io.github.rsgarrido.sazanami.ui.player.pocketcassette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.VisualizerPerformanceCounters
import io.github.rsgarrido.sazanami.performance.tracePerformance
import io.github.rsgarrido.sazanami.ui.player.RETRO_VISUALIZER_CADENCE_HZ
import io.github.rsgarrido.sazanami.ui.player.rememberBoundedVisualizerPhase
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun PocketCassetteWindow(
    currentSong: Song?,
    isPlaying: Boolean,
    isVisualizerWorkAllowed: Boolean,
    currentPosition: Int,
    duration: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
    windowReveal: Float = 1f,
    mechanismReveal: Float = 1f,
    morphBounds: PocketCassetteMorphBounds? = null,
    sharedOwner: PocketCassetteSharedOwner = PocketCassetteSharedOwner.EXPANDED,
    collapseDragModifier: Modifier = Modifier
) {
    val colors = PocketCassetteColors
    val playbackProgress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackLabelHeight = if (compact) 78.dp else 90.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = windowReveal.coerceIn(0f, 1f) }
            .then(collapseDragModifier)
            .pocketCassetteBluePanelFinish(10.dp)
            .padding(if (compact) 10.dp else 14.dp)
    ) {
        PocketCassetteScrew(modifier = Modifier.align(Alignment.TopStart))
        PocketCassetteScrew(modifier = Modifier.align(Alignment.TopEnd))
        PocketCassetteScrew(modifier = Modifier.align(Alignment.BottomStart))
        PocketCassetteScrew(modifier = Modifier.align(Alignment.BottomEnd))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    text = "TAPE BAY // SIDE A",
                    color = Color.White.copy(alpha = 0.82f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 8.sp else 9.sp,
                    letterSpacing = 0.7.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isPlaying) PocketCassetteColors.orange else Color(0xFF623C34),
                            RoundedCornerShape(50)
                        )
                )
                androidx.compose.material3.Text(
                    text = if (isPlaying) "  MOTION" else "  HOLD",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateExpandedArtwork(coordinates.boundsInRoot())
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) {
                            PocketCassetteColors.window
                        } else {
                            Color.Transparent
                        }
                    )
            ) {
                if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) {
                    AsyncImage(
                        model = currentSong?.albumArtUri,
                        contentDescription = "Album artwork cassette label",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.56f))
                )

                PocketCassetteMechanism(
                    isPlaying = isPlaying,
                    isVisualizerWorkAllowed = isVisualizerWorkAllowed,
                    playbackProgress = playbackProgress,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = trackLabelHeight)
                        .graphicsLayer { alpha = mechanismReveal.coerceIn(0f, 1f) }
                )

                PocketCassetteTrackLabel(
                    currentSong = currentSong,
                    currentPosition = currentPosition,
                    duration = duration,
                    compact = compact,
                    morphBounds = morphBounds,
                    sharedOwner = sharedOwner,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(trackLabelHeight)
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = colors.windowEdge,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(9.dp.toPx(), 3.dp.toPx()),
                        end = Offset(size.width - 9.dp.toPx(), 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
private fun PocketCassetteMechanism(
    isPlaying: Boolean,
    isVisualizerWorkAllowed: Boolean,
    playbackProgress: Float,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketCassetteColors
    val phase = rememberBoundedVisualizerPhase(
        animationEnabled = isPlaying && isVisualizerWorkAllowed,
        targetCadenceHz = RETRO_VISUALIZER_CADENCE_HZ,
        cycleDurationMillis = 4_800,
        updateTraceName = PerformanceTraceNames.POCKET_CASSETTE_UPDATE
    )
    Canvas(modifier = modifier) {
        tracePerformance(PerformanceTraceNames.POCKET_CASSETTE_DRAW) {
            VisualizerPerformanceCounters.onDraw()
            val rotation = phase.value * 360f
            val leftCenter = Offset(size.width * 0.29f, size.height * 0.44f)
            val rightCenter = Offset(size.width * 0.71f, size.height * 0.44f)
            val radius = min(size.width * 0.155f, size.height * if (compact) 0.27f else 0.25f)
                .coerceAtLeast(22.dp.toPx())
            val rollerY = (size.height * 0.88f).coerceAtLeast(leftCenter.y + radius * 0.95f)
            val rollerRadius = radius * 0.13f
            val emptyTapeRadius = radius * 0.42f
            val fullTapeRadius = radius * 0.89f
            val leftTapeRadius = fullTapeRadius -
                    (fullTapeRadius - emptyTapeRadius) * playbackProgress
            val rightTapeRadius = emptyTapeRadius +
                    (fullTapeRadius - emptyTapeRadius) * playbackProgress

            drawLine(
                color = colors.tape,
                start = Offset(
                    leftCenter.x - leftTapeRadius * 0.88f,
                    leftCenter.y + leftTapeRadius * 0.58f
                ),
                end = Offset(leftCenter.x - radius * 0.32f, rollerY),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = colors.tape,
                start = Offset(leftCenter.x - radius * 0.32f, rollerY),
                end = Offset(rightCenter.x + radius * 0.32f, rollerY),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = colors.tape,
                start = Offset(rightCenter.x + radius * 0.32f, rollerY),
                end = Offset(
                    rightCenter.x + rightTapeRadius * 0.88f,
                    rightCenter.y + rightTapeRadius * 0.58f
                ),
                strokeWidth = 3.dp.toPx()
            )

            fun drawRoller(rollerCenter: Offset) {
                drawCircle(
                    color = colors.reelHub,
                    radius = rollerRadius,
                    center = rollerCenter
                )
                drawCircle(
                    color = colors.reel,
                    radius = rollerRadius,
                    center = rollerCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawRoller(Offset(leftCenter.x - radius * 0.28f, rollerY))
            drawRoller(Offset(rightCenter.x + radius * 0.28f, rollerY))

            drawReel(
                center = leftCenter,
                radius = radius,
                tapeRadius = leftTapeRadius,
                rotation = rotation,
                colors = colors
            )
            drawReel(
                center = rightCenter,
                radius = radius,
                tapeRadius = rightTapeRadius,
                rotation = rotation,
                colors = colors
            )

            val headWidth = radius * 0.52f
            drawRect(
                color = colors.reelHub,
                topLeft = Offset(size.width / 2f - headWidth / 2f, rollerY - radius * 0.08f),
                size = Size(headWidth, radius * 0.18f)
            )
            drawLine(
                color = colors.orange.copy(alpha = 0.72f),
                start = Offset(size.width / 2f - headWidth * 0.35f, rollerY + radius * 0.02f),
                end = Offset(size.width / 2f + headWidth * 0.35f, rollerY + radius * 0.02f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReel(
    center: Offset,
    radius: Float,
    tapeRadius: Float,
    rotation: Float,
    colors: PocketCassettePalette
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.58f),
        radius = radius * 1.07f,
        center = center
    )
    drawCircle(
        color = colors.tape.copy(alpha = 0.9f),
        radius = tapeRadius,
        center = center
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.34f),
        radius = tapeRadius,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )
    drawCircle(
        color = colors.reel.copy(alpha = 0.24f),
        radius = radius,
        center = center
    )
    drawCircle(
        color = colors.reel,
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(
        color = colors.reelHub,
        radius = radius * 0.31f,
        center = center
    )

    rotate(degrees = rotation, pivot = center) {
        repeat(6) { index ->
            val angle = Math.toRadians((index * 60).toDouble())
            val startRadius = radius * 0.37f
            val endRadius = radius * 0.78f
            drawLine(
                color = colors.reel,
                start = Offset(
                    x = center.x + cos(angle).toFloat() * startRadius,
                    y = center.y + sin(angle).toFloat() * startRadius
                ),
                end = Offset(
                    x = center.x + cos(angle).toFloat() * endRadius,
                    y = center.y + sin(angle).toFloat() * endRadius
                ),
                strokeWidth = radius * 0.11f
            )
        }
    }
    drawCircle(
        color = colors.window,
        radius = radius * 0.13f,
        center = center
    )
}

@Composable
private fun PocketCassetteTrackLabel(
    currentSong: Song?,
    currentPosition: Int,
    duration: Int,
    compact: Boolean,
    morphBounds: PocketCassetteMorphBounds? = null,
    sharedOwner: PocketCassetteSharedOwner = PocketCassetteSharedOwner.EXPANDED,
    modifier: Modifier = Modifier
) {
    val colors = PocketCassetteColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE0192328))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = colors.windowText.copy(alpha = 0.28f),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(
                horizontal = if (compact) 8.dp else 11.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        androidx.compose.material3.Text(
            text = currentSong?.title ?: "No track loaded",
            color = PocketCassetteColors.windowText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 13.sp else 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    morphBounds?.updateExpandedTitle(coordinates.boundsInRoot())
                }
                .graphicsLayer {
                    alpha = if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) 1f else 0f
                }
                .then(
                    if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) Modifier
                    else Modifier.clearAndSetSemantics { }
                )
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = currentSong?.artist?.ifBlank { "Unknown artist" }.orEmpty(),
                color = PocketCassetteColors.windowTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 8.sp else 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        morphBounds?.updateExpandedArtist(coordinates.boundsInRoot())
                    }
                    .graphicsLayer {
                        alpha = if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) 1f else 0f
                    }
                    .then(
                        if (sharedOwner == PocketCassetteSharedOwner.EXPANDED) Modifier
                        else Modifier.clearAndSetSemantics { }
                    )
            )
            androidx.compose.material3.Text(
                text = "${formatPocketCassetteTime(currentPosition)} / ${formatPocketCassetteTime(duration)}",
                color = PocketCassetteColors.windowText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 8.sp else 9.sp
            )
        }
        androidx.compose.material3.Text(
            text = currentSong?.album?.ifBlank { "Unknown album" }.orEmpty(),
            color = PocketCassetteColors.windowTextMuted.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatPocketCassetteTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
