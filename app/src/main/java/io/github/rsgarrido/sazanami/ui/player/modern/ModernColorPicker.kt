package io.github.rsgarrido.sazanami.ui.player.modern

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ModernHsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float
)

val ModernSolidColorSwatches: List<Long> = listOf(
    0xFF17191F,
    0xFF20263A,
    0xFF243B53,
    0xFF342447,
    0xFF4A2330,
    0xFF173A35,
    0xFF49351F,
    0xFF3C4048
)

fun modernArgbToHsv(argb: Long): ModernHsvColor {
    val sanitized = sanitizeModernSolidColorArgb(argb).toInt()
    val red = (sanitized ushr 16 and 0xFF) / 255f
    val green = (sanitized ushr 8 and 0xFF) / 255f
    val blue = (sanitized and 0xFF) / 255f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (maximum == 0f) 0f else delta / maximum
    return ModernHsvColor(hue, saturation, maximum)
}

fun modernHsvToArgb(hsv: ModernHsvColor): Long {
    val hue = ((hsv.hue % 360f) + 360f) % 360f
    val saturation = hsv.saturation.coerceIn(0f, 1f)
    val value = hsv.value.coerceIn(0f, 1f)
    val chroma = value * saturation
    val hueSection = hue / 60f
    val secondary = chroma * (1f - kotlin.math.abs(hueSection % 2f - 1f))
    val (rawRed, rawGreen, rawBlue) = when (floor(hueSection).toInt()) {
        0 -> Triple(chroma, secondary, 0f)
        1 -> Triple(secondary, chroma, 0f)
        2 -> Triple(0f, chroma, secondary)
        3 -> Triple(0f, secondary, chroma)
        4 -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val match = value - chroma
    val red = ((rawRed + match) * 255f).roundToInt().coerceIn(0, 255)
    val green = ((rawGreen + match) * 255f).roundToInt().coerceIn(0, 255)
    val blue = ((rawBlue + match) * 255f).roundToInt().coerceIn(0, 255)
    return 0xFF000000L or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()
}
