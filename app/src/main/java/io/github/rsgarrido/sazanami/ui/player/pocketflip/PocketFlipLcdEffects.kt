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
import io.github.rsgarrido.sazanami.player.waveform.WaveformData
import io.github.rsgarrido.sazanami.ui.player.fillRetroMeterLevels
import io.github.rsgarrido.sazanami.ui.player.isRetroMeterEffectivelySilent
import io.github.rsgarrido.sazanami.ui.player.rememberBoundedVisualizerPhase
import io.github.rsgarrido.sazanami.ui.player.RETRO_VISUALIZER_CADENCE_HZ
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.VisualizerPerformanceCounters
import io.github.rsgarrido.sazanami.performance.tracePerformance
import kotlin.math.PI
import kotlin.math.sin

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
    currentSong: Song?,
    waveformData: WaveformData?,
    isVisualizerWorkAllowed: Boolean,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = PocketFlipColors
    val songKey = remember(currentSong?.id, currentSong?.title, currentSong?.artist) {
        buildMeterKey(currentSong)
    }
    val isSilent = isRetroMeterEffectivelySilent(
        amplitudes = waveformData?.amplitudes,
        currentPositionMs = currentPosition.toLong(),
        durationMs = duration.toLong()
    )
    val phase = rememberBoundedVisualizerPhase(
        animationEnabled = isPlaying && isVisualizerWorkAllowed && !isSilent,
        targetCadenceHz = RETRO_VISUALIZER_CADENCE_HZ,
        cycleDurationMillis = 900,
        updateTraceName = PerformanceTraceNames.POCKET_FLIP_UPDATE
    )
    val meterLevels = remember { FloatArray(POCKET_FLIP_METER_LINE_COUNT) }

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

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 17.dp else 21.dp)
        ) {
            tracePerformance(PerformanceTraceNames.POCKET_FLIP_DRAW) {
            VisualizerPerformanceCounters.onDraw()
            val currentPhase = phase.value
            val isEnergyDriven = fillRetroMeterLevels(
                output = meterLevels,
                amplitudes = waveformData?.amplitudes,
                currentPositionMs = currentPosition.toLong(),
                durationMs = duration.toLong(),
                animationPhase = currentPhase,
                isPlaying = isPlaying,
                songSeed = songKey.toLong()
            )
            val firstLevel = meterLevels.takeIf { isEnergyDriven }?.get(0) ?:
                meterLevel(songKey, channel = 0, phase = currentPhase)
            val secondLevel = meterLevels.takeIf { isEnergyDriven }?.get(1) ?:
                meterLevel(songKey, channel = 1, phase = currentPhase)
            drawLcdMeterLine(
                level = firstLevel,
                animationPhase = currentPhase,
                channel = 0,
                isEnergyDriven = isEnergyDriven,
                top = 0f,
                height = size.height * 0.42f,
                colors = colors
            )
            drawLcdMeterLine(
                level = secondLevel,
                animationPhase = currentPhase,
                channel = 1,
                isEnergyDriven = isEnergyDriven,
                top = size.height * 0.58f,
                height = size.height * 0.42f,
                colors = colors
            )
            }
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
    animationPhase: Float,
    channel: Int,
    isEnergyDriven: Boolean,
    top: Float,
    height: Float,
    colors: PocketFlipPalette
) {
    val segmentCount = 24
    val gap = 1.dp.toPx()
    val segmentWidth = (size.width - gap * (segmentCount - 1)) / segmentCount
    val litSegments = (level.coerceIn(0f, 1f) * segmentCount).toInt()
    val pulsePosition = ((animationPhase + channel * 0.37f) % 1f) * (segmentCount - 1)
    val inactiveAlpha = if (isEnergyDriven) {
        0.16f + level.coerceIn(0f, 1f) * 0.25f
    } else {
        0.48f
    }

    repeat(segmentCount) { index ->
        val pulseStrength = (
                1f - kotlin.math.abs(index - pulsePosition) / 2f
                ).coerceIn(0f, 1f) * level.coerceIn(0f, 1f)
        drawRect(
            color = when {
                index < litSegments -> colors.screenAccent.copy(
                    alpha = (0.52f + level * 0.28f + pulseStrength * 0.12f)
                        .coerceAtMost(0.92f)
                )
                pulseStrength > 0f -> colors.screenAccent.copy(alpha = pulseStrength * 0.24f)
                else -> colors.seekInactive.copy(alpha = inactiveAlpha)
            },
            topLeft = Offset(index * (segmentWidth + gap), top),
            size = Size(segmentWidth, height)
        )
    }
}

private fun meterLevel(songKey: Int, channel: Int, phase: Float): Float {
    val shifted = songKey ushr (channel * 8)
    val base = 0.48f + (shifted and 0xFF) / 255f * 0.30f
    val offset = (shifted ushr 4 and 0x0F) / 15f
    val movement = sin((phase + offset) * (2f * PI.toFloat())) * 0.10f
    return (base + movement).coerceIn(0.22f, 0.92f)
}

private fun buildMeterKey(song: Song?): Int {
    if (song == null) {
        return 0x5046
    }
    var result = song.id.hashCode()
    result = 31 * result + song.title.hashCode()
    result = 31 * result + song.artist.hashCode()
    return result
}

internal const val POCKET_FLIP_METER_LINE_COUNT = 2
