package io.github.rsgarrido.sazanami.ui.equalizer

import java.util.Locale
import kotlin.math.abs

internal fun formatEqualizerDb(
    valueDb: Double,
    includePlus: Boolean = true
): String {
    val normalized = if (abs(valueDb) < 0.05) 0.0 else valueDb
    val prefix = if (includePlus && normalized > 0.0) "+" else ""
    return String.format(
        Locale.ROOT,
        "%s%.1f dB",
        prefix,
        normalized
    )
}

internal fun formatEqualizerFrequency(
    frequencyHz: Double
): String = if (frequencyHz >= 1_000.0) {
    val kilohertz = frequencyHz / 1_000.0
    if (kilohertz % 1.0 == 0.0) {
        "${kilohertz.toInt()} kHz"
    } else {
        String.format(Locale.ROOT, "%.1f kHz", kilohertz)
    }
} else {
    "${frequencyHz.toInt()} Hz"
}

internal fun equalizerBandAccessibilityText(
    frequencyHz: Double,
    gainDb: Double,
    unavailable: Boolean
): String {
    val availability = if (unavailable) {
        ", unavailable for the current source"
    } else {
        ""
    }
    return "${formatEqualizerFrequency(frequencyHz)}, " +
        "${formatEqualizerDb(gainDb)}$availability, " +
        "range minus 12 to plus 12 decibels"
}
