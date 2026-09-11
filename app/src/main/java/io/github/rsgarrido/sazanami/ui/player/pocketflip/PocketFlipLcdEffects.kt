package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.aggregateSpectrumRegionRms
import io.github.rsgarrido.sazanami.ui.player.calibrateSpectrumMeterLevel
import io.github.rsgarrido.sazanami.ui.player.contiguousMeterFillCount
import io.github.rsgarrido.sazanami.ui.player.rememberSpectrumVisualizerState
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.VisualizerPerformanceCounters
import io.github.rsgarrido.sazanami.performance.tracePerformance

@Composable
internal fun PocketFlipLcdStatusRow(
    currentSong: Song?,
    isPlaying: Boolean,
    compact: Boolean
) {
    val fileType = remember(currentSong?.filePath) {
        currentSong?.filePath
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.uppercase()
            ?.takeIf { extension ->
                extension.length in 2..5 && extension.all { character -> character.isLetterOrDigit() }
            }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketFlipStatusChip(
            text = if (isPlaying) "PLAY" else "PAUSE",
            active = isPlaying,
            compact = compact
        )
        Spacer(modifier = Modifier.width(3.dp))
        if (fileType != null) {
            PocketFlipStatusChip(
                text = "FORMAT $fileType",
                compact = compact
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        PocketFlipStatusChip(text = "LOCAL", compact = compact)
    }
}

@Composable
private fun PocketFlipStatusChip(
    text: String,
    compact: Boolean,
    active: Boolean = false
) {
    Text(
        text = text,
        color = if (active) {
            PocketFlipColors.screenAccent
        } else {
            PocketFlipColors.screenTextMuted
        },
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = if (compact) 7.sp else 8.sp,
        letterSpacing = 0.4.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(PocketFlipColors.lcdBand, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
internal fun PocketFlipLcdMeter(
    isVisualizerWorkAllowed: Boolean,
    isPlaying: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketFlipColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.lcdBand, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRACK DATA",
                color = colors.screenTextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isPlaying) "RUN" else "HOLD",
                color = if (isPlaying) {
                    colors.screenAccent
                } else {
                    colors.screenTextMuted
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                letterSpacing = 0.5.sp
            )
        }

        PocketFlipSpectrumCanvas(
            isVisualizerWorkAllowed = isVisualizerWorkAllowed,
            compact = compact,
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PocketFlipSpectrumCanvas(
    isVisualizerWorkAllowed: Boolean,
    compact: Boolean,
    colors: PocketFlipPalette,
    modifier: Modifier = Modifier
) {
    val spectrumState = rememberSpectrumVisualizerState(isVisualizerWorkAllowed)

    Canvas(modifier = modifier.height(if (compact) 17.dp else 21.dp)) {
        val frame = spectrumState.value
        val lowerToMidLevel = calibrateSpectrumMeterLevel(
            aggregateLevel = aggregateSpectrumRegionRms(
                frame = frame,
                startBand = POCKET_FLIP_LOWER_START_BAND,
                endBandExclusive = POCKET_FLIP_LOWER_END_BAND_EXCLUSIVE
            ),
            displayGain = POCKET_FLIP_METER_DISPLAY_GAIN,
            responseExponent = POCKET_FLIP_METER_RESPONSE_EXPONENT
        )
        val upperMidToHighLevel = calibrateSpectrumMeterLevel(
            aggregateLevel = aggregateSpectrumRegionRms(
                frame = frame,
                startBand = POCKET_FLIP_UPPER_START_BAND,
                endBandExclusive = frame.bandCount
            ),
            displayGain = POCKET_FLIP_METER_DISPLAY_GAIN,
            responseExponent = POCKET_FLIP_METER_RESPONSE_EXPONENT
        )
        tracePerformance(PerformanceTraceNames.POCKET_FLIP_DRAW) {
            VisualizerPerformanceCounters.onDraw()
            drawLcdMeterLine(
                level = lowerToMidLevel,
                top = 0f,
                height = size.height * 0.42f,
                colors = colors
            )
            drawLcdMeterLine(
                level = upperMidToHighLevel,
                top = size.height * 0.58f,
                height = size.height * 0.42f,
                colors = colors
            )
        }
    }
}

@Composable
internal fun PocketFlipLcdOverlay(modifier: Modifier = Modifier) {
    val colors = PocketFlipColors
    Canvas(modifier = modifier) {
        drawRect(color = colors.lcdTint)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to colors.lcdGlow,
                    0.72f to Color.Transparent,
                    1f to Color.Transparent
                ),
                center = center,
                radius = size.maxDimension * 0.68f
            )
        )

        val pixelStep = 5.dp.toPx()
        var x = pixelStep
        while (x < size.width) {
            drawLine(
                color = colors.lcdGrid,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += pixelStep
        }

        val scanlineStep = 4.dp.toPx()
        var y = scanlineStep
        while (y < size.height) {
            drawLine(
                color = colors.lcdScanline,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += scanlineStep
        }

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.58f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.20f)
                ),
                center = center,
                radius = size.maxDimension * 0.72f
            )
        )
    }
}

@Composable
internal fun PocketFlipArtworkLcdTreatment(modifier: Modifier = Modifier) {
    val colors = PocketFlipColors
    Canvas(modifier = modifier) {
        drawRect(color = colors.artworkLcdTint)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.68f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.12f)
                ),
                center = center,
                radius = size.maxDimension * 0.72f
            )
        )
    }
}

private fun DrawScope.drawLcdMeterLine(
    level: Float,
    top: Float,
    height: Float,
    colors: PocketFlipPalette
) {
    val gap = 1.dp.toPx()
    val segmentWidth = (size.width - gap * (POCKET_FLIP_METER_SEGMENT_COUNT - 1)) /
            POCKET_FLIP_METER_SEGMENT_COUNT
    val filledCount = contiguousMeterFillCount(level, POCKET_FLIP_METER_SEGMENT_COUNT)

    repeat(POCKET_FLIP_METER_SEGMENT_COUNT) { index ->
        val active = index < filledCount
        val frontier = active && index == filledCount - 1
        drawRect(
            color = if (active) {
                colors.screenAccent.copy(alpha = if (frontier) 1f else 0.82f)
            } else {
                colors.seekInactive.copy(alpha = 0.10f)
            },
            topLeft = Offset(index * (segmentWidth + gap), top),
            size = Size(segmentWidth, height)
        )
    }
}

internal const val POCKET_FLIP_METER_SEGMENT_COUNT = 24
internal const val POCKET_FLIP_LOWER_START_BAND = 0
internal const val POCKET_FLIP_LOWER_END_BAND_EXCLUSIVE = 16
internal const val POCKET_FLIP_UPPER_START_BAND = 16
internal const val POCKET_FLIP_METER_DISPLAY_GAIN = 1.05f
internal const val POCKET_FLIP_METER_RESPONSE_EXPONENT = 1.65f
