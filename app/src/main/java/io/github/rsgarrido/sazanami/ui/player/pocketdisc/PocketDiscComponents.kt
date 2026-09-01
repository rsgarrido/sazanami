package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.ui.player.classicwheel.rememberBatteryLevel

@Composable
internal fun PocketDiscBatteryIndicator(
    modifier: Modifier = Modifier
) {
    val level = rememberBatteryLevel().value.coerceIn(0, 100)
    val colors = PocketDiscColors
    Canvas(modifier = modifier.size(width = 35.dp, height = 16.dp)) {
        val terminalWidth = 3.dp.toPx()
        val bodyWidth = size.width - terminalWidth - 1.dp.toPx()
        val stroke = 1.2.dp.toPx()
        drawRoundRect(
            color = colors.lcdTextMuted,
            topLeft = Offset.Zero,
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke)
        )
        drawRoundRect(
            color = colors.lcdTextMuted,
            topLeft = Offset(bodyWidth + 1.dp.toPx(), size.height * 0.30f),
            size = Size(terminalWidth, size.height * 0.40f),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        val innerLeft = 3.dp.toPx()
        val innerTop = 3.dp.toPx()
        val innerWidth = bodyWidth - innerLeft * 2
        val innerHeight = size.height - innerTop * 2
        val segmentCount = 4
        val gap = 1.3.dp.toPx()
        val segmentWidth = (innerWidth - gap * (segmentCount - 1)) / segmentCount
        val filled = ((level / 100f) * segmentCount).let { value ->
            if (level > 0) value.coerceAtLeast(0.5f) else 0f
        }
        repeat(segmentCount) { index ->
            val alpha = when {
                index + 1 <= filled -> 1f
                index < filled -> (filled - index).coerceIn(0f, 1f)
                else -> 0.16f
            }
            drawRect(
                color = colors.lcdGlow.copy(alpha = alpha),
                topLeft = Offset(innerLeft + index * (segmentWidth + gap), innerTop),
                size = Size(segmentWidth, innerHeight)
            )
        }
    }
}

@Composable
internal fun PocketDiscCartridge(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = PocketDiscColors
    val radius = if (compact) 7.dp else 10.dp
    Box(
        modifier = modifier
            .background(colors.shellMid, RoundedCornerShape(radius))
            .border(1.dp, colors.edge, RoundedCornerShape(radius))
            .padding(if (compact) 7.dp else 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIGITAL DISC",
                    color = colors.lcdTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 7.sp else 9.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "74MIN",
                    color = colors.lcdTextMuted.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 6.sp else 8.sp
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val windowSize = size.minDimension * 0.62f
                    val left = size.width * 0.10f
                    val top = size.height * 0.22f
                    drawRoundRect(
                        color = colors.panelDeep,
                        topLeft = Offset(left, top),
                        size = Size(windowSize, windowSize),
                        cornerRadius = CornerRadius(windowSize * 0.08f)
                    )
                    val discCenter = Offset(left + windowSize * 0.58f, top + windowSize * 0.50f)
                    drawCircle(
                        color = colors.shellLight.copy(alpha = 0.72f),
                        radius = windowSize * 0.34f,
                        center = discCenter
                    )
                    drawCircle(
                        color = colors.panelDeep,
                        radius = windowSize * 0.11f,
                        center = discCenter
                    )
                    drawCircle(
                        color = colors.lcdGlowDim.copy(alpha = 0.75f),
                        radius = windowSize * 0.045f,
                        center = discCenter
                    )
                    val shutterWidth = size.width * 0.28f
                    val shutterHeight = size.height * 0.46f
                    val shutterLeft = size.width - shutterWidth - size.width * 0.05f
                    val shutterTop = size.height * 0.31f
                    drawRoundRect(
                        color = colors.shellDark,
                        topLeft = Offset(shutterLeft, shutterTop),
                        size = Size(shutterWidth, shutterHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    repeat(4) { line ->
                        val y = shutterTop + shutterHeight * (0.25f + line * 0.15f)
                        drawLine(
                            color = colors.edge.copy(alpha = 0.54f),
                            start = Offset(shutterLeft + shutterWidth * 0.18f, y),
                            end = Offset(shutterLeft + shutterWidth * 0.82f, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
            Text(
                text = "▲ INSERT THIS END",
                color = colors.lcdTextMuted.copy(alpha = 0.65f),
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 6.sp else 7.sp
            )
        }
    }
}

@Composable
internal fun PocketDiscSegmentedProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    segmentCount: Int = 28,
    activeColor: Color = PocketDiscColors.lcdGlow,
    inactiveColor: Color = PocketDiscColors.lcdGlowDim.copy(alpha = 0.22f)
) {
    Canvas(modifier = modifier.height(10.dp)) {
        val safeCount = segmentCount.coerceAtLeast(1)
        val gap = 2.dp.toPx()
        val segmentWidth = ((size.width - gap * (safeCount - 1)) / safeCount).coerceAtLeast(1f)
        val filled = progress.coerceIn(0f, 1f) * safeCount
        repeat(safeCount) { index ->
            val alpha = when {
                index + 1 <= filled -> 1f
                index < filled -> (filled - index).coerceIn(0f, 1f)
                else -> 0f
            }
            drawRoundRect(
                color = if (alpha > 0f) activeColor.copy(alpha = alpha) else inactiveColor,
                topLeft = Offset(index * (segmentWidth + gap), 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

internal fun normalizedPocketDiscProgress(position: Int, duration: Int): Float =
    if (duration <= 0) 0f else (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

internal fun formatPocketDiscTime(milliseconds: Int, negative: Boolean = false): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val prefix = if (negative) "-" else ""
    return "%s%02d:%02d".format(prefix, minutes, seconds)
}
