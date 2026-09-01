package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.ui.player.classicwheel.rememberBatteryLevel
import kotlin.math.min

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
            .padding(if (compact) 6.dp else 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            val inset = if (compact) 2.dp.toPx() else 3.dp.toPx()

            // Molded inner seam gives the cartridge a distinct shell instead of reading
            // as a plain rectangle with two shapes placed on top of it.
            drawRoundRect(
                color = colors.edge.copy(alpha = 0.30f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(if (compact) 5.dp.toPx() else 7.dp.toPx()),
                style = Stroke(stroke)
            )

            // Small top-center handling notch.
            val notchWidth = size.width * 0.24f
            val notchHeight = size.height * 0.045f
            drawRoundRect(
                color = colors.shellDark.copy(alpha = 0.72f),
                topLeft = Offset((size.width - notchWidth) / 2f, -inset * 0.2f),
                size = Size(notchWidth, notchHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )

            // Large exposed-disc window.
            val windowLeft = size.width * 0.08f
            val windowTop = size.height * 0.28f
            val windowWidth = size.width * 0.61f
            val windowHeight = size.height * 0.48f
            drawRoundRect(
                color = colors.seam.copy(alpha = 0.82f),
                topLeft = Offset(windowLeft - stroke, windowTop - stroke),
                size = Size(windowWidth + stroke * 2, windowHeight + stroke * 2),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            drawRoundRect(
                color = colors.panelDeep,
                topLeft = Offset(windowLeft, windowTop),
                size = Size(windowWidth, windowHeight),
                cornerRadius = CornerRadius(5.dp.toPx())
            )

            val discRadius = min(windowWidth, windowHeight) * 0.38f
            val discCenter = Offset(
                windowLeft + windowWidth * 0.49f,
                windowTop + windowHeight * 0.52f
            )
            drawCircle(
                color = colors.shellLight.copy(alpha = 0.78f),
                radius = discRadius,
                center = discCenter
            )
            drawCircle(
                color = colors.shellMid.copy(alpha = 0.80f),
                radius = discRadius * 0.64f,
                center = discCenter,
                style = Stroke(if (compact) 1.dp.toPx() else 1.4.dp.toPx())
            )
            drawCircle(
                color = colors.panelDeep,
                radius = discRadius * 0.20f,
                center = discCenter
            )
            drawCircle(
                color = colors.lcdGlowDim.copy(alpha = 0.85f),
                radius = discRadius * 0.075f,
                center = discCenter
            )

            // Sliding shutter overlaps the opening, like the generated concept.
            val shutterWidth = size.width * 0.31f
            val shutterHeight = size.height * 0.45f
            val shutterLeft = size.width - shutterWidth - size.width * 0.055f
            val shutterTop = size.height * 0.32f
            drawRoundRect(
                color = colors.seam.copy(alpha = 0.68f),
                topLeft = Offset(shutterLeft - stroke, shutterTop - stroke),
                size = Size(shutterWidth + stroke * 2, shutterHeight + stroke * 2),
                cornerRadius = CornerRadius(5.dp.toPx())
            )
            drawRoundRect(
                color = colors.shellDark,
                topLeft = Offset(shutterLeft, shutterTop),
                size = Size(shutterWidth, shutterHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            repeat(6) { line ->
                val y = shutterTop + shutterHeight * (0.22f + line * 0.105f)
                drawLine(
                    color = colors.edge.copy(alpha = 0.48f),
                    start = Offset(shutterLeft + shutterWidth * 0.17f, y),
                    end = Offset(shutterLeft + shutterWidth * 0.83f, y),
                    strokeWidth = if (compact) 0.8.dp.toPx() else 1.dp.toPx()
                )
            }

            // Molded insertion mark and guide line at the bottom of the cartridge.
            val markerX = size.width * 0.08f
            val markerY = size.height * 0.88f
            val markerSize = size.minDimension * 0.035f
            val markerPath = Path().apply {
                moveTo(markerX, markerY + markerSize)
                lineTo(markerX + markerSize, markerY - markerSize)
                lineTo(markerX + markerSize * 2f, markerY + markerSize)
                close()
            }
            drawPath(markerPath, colors.lcdTextMuted.copy(alpha = 0.58f))
            drawLine(
                color = colors.edge.copy(alpha = 0.42f),
                start = Offset(size.width * 0.44f, size.height * 0.895f),
                end = Offset(size.width * 0.91f, size.height * 0.895f),
                strokeWidth = stroke
            )
        }

        Text(
            text = "DIGITAL DISC",
            color = colors.lcdTextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 7.sp else 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = if (compact) 7.dp else 10.dp, top = if (compact) 6.dp else 8.dp)
        )
        Text(
            text = "74MIN",
            color = colors.lcdTextMuted.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 6.sp else 8.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = if (compact) 7.dp else 10.dp, top = if (compact) 7.dp else 9.dp)
        )
        Text(
            text = "INSERT THIS END",
            color = colors.lcdTextMuted.copy(alpha = 0.60f),
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 5.sp else 6.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (compact) 16.dp else 21.dp, bottom = if (compact) 5.dp else 7.dp)
        )
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

/**
 * Lightweight seven-segment display used for transport times. Keeping this drawn
 * from Compose primitives avoids bundling a font and lets the digits follow the
 * theme's LCD color customization exactly.
 */
@Composable
internal fun PocketDiscSevenSegmentText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PocketDiscColors.lcdText,
    inactiveColor: Color = PocketDiscColors.lcdGlowDim.copy(alpha = 0.08f),
    digitWidth: Dp = 10.dp,
    digitHeight: Dp = 20.dp,
    spacing: Dp = 2.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEach { character ->
            Canvas(modifier = Modifier.size(width = digitWidth, height = digitHeight)) {
                val width = size.width
                val height = size.height
                val thickness = (min(width, height) * 0.16f).coerceAtLeast(1f)
                val half = height / 2f
                val horizontalWidth = (width - thickness * 1.8f).coerceAtLeast(thickness)
                val verticalHeight = (half - thickness * 1.35f).coerceAtLeast(thickness)
                val radius = CornerRadius(thickness * 0.28f)

                fun horizontal(y: Float, active: Boolean) {
                    drawRoundRect(
                        color = if (active) color else inactiveColor,
                        topLeft = Offset(thickness * 0.9f, y),
                        size = Size(horizontalWidth, thickness),
                        cornerRadius = radius
                    )
                }

                fun vertical(x: Float, y: Float, active: Boolean) {
                    drawRoundRect(
                        color = if (active) color else inactiveColor,
                        topLeft = Offset(x, y),
                        size = Size(thickness, verticalHeight),
                        cornerRadius = radius
                    )
                }

                when (character) {
                    ':' -> {
                        drawCircle(color, thickness * 0.55f, Offset(width / 2f, height * 0.34f))
                        drawCircle(color, thickness * 0.55f, Offset(width / 2f, height * 0.69f))
                    }
                    '/' -> {
                        drawLine(
                            color = color,
                            start = Offset(width * 0.18f, height * 0.91f),
                            end = Offset(width * 0.82f, height * 0.09f),
                            strokeWidth = thickness,
                        )
                    }
                    '-' -> horizontal(half - thickness / 2f, true)
                    ' ' -> Unit
                    else -> {
                        val segments = sevenSegmentMask(character)
                        horizontal(0f, segments[0])
                        vertical(width - thickness, thickness * 0.72f, segments[1])
                        vertical(width - thickness, half + thickness * 0.25f, segments[2])
                        horizontal(height - thickness, segments[3])
                        vertical(0f, half + thickness * 0.25f, segments[4])
                        vertical(0f, thickness * 0.72f, segments[5])
                        horizontal(half - thickness / 2f, segments[6])
                    }
                }
            }
        }
    }
}

private fun sevenSegmentMask(character: Char): BooleanArray {
    val active = when (character) {
        '0' -> intArrayOf(0, 1, 2, 3, 4, 5)
        '1' -> intArrayOf(1, 2)
        '2' -> intArrayOf(0, 1, 3, 4, 6)
        '3' -> intArrayOf(0, 1, 2, 3, 6)
        '4' -> intArrayOf(1, 2, 5, 6)
        '5' -> intArrayOf(0, 2, 3, 5, 6)
        '6' -> intArrayOf(0, 2, 3, 4, 5, 6)
        '7' -> intArrayOf(0, 1, 2)
        '8' -> intArrayOf(0, 1, 2, 3, 4, 5, 6)
        '9' -> intArrayOf(0, 1, 2, 3, 5, 6)
        else -> intArrayOf()
    }
    return BooleanArray(7).also { mask -> active.forEach { mask[it] = true } }
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
