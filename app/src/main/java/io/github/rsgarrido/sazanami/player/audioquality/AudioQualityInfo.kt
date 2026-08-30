package io.github.rsgarrido.sazanami.player.audioquality

import java.util.Locale

data class AudioQualityInfo(
    val format: String?,
    val bitDepth: Int?,
    val sampleRateHz: Int?,
    val bitrateKbps: Int?
)

fun AudioQualityInfo.toDisplayText(): String? {
    val displayParts = buildList {
        bitDepth
            ?.takeIf { value -> value > 0 }
            ?.let { value -> add("$value bit") }

        sampleRateHz
            ?.takeIf { value -> value > 0 }
            ?.let { value -> add("${formatSampleRate(value)} kHz") }

        bitrateKbps
            ?.takeIf { value -> value > 0 }
            ?.let { value -> add("$value kbps") }

        normalizeAudioFormat(format)?.let(::add)
    }

    return displayParts
        .takeIf { parts -> parts.isNotEmpty() }
        ?.joinToString(separator = "  ")
}

internal fun normalizeAudioFormat(rawFormat: String?): String? {
    val normalizedFormat = rawFormat
        ?.trim()
        ?.removePrefix(".")
        ?.uppercase(Locale.ROOT)
        ?.takeIf { value -> value.isNotBlank() }
        ?: return null

    return when {
        "FLAC" in normalizedFormat -> "FLAC"
        normalizedFormat == "MP3" ||
                ("MPEG" in normalizedFormat && "LAYER 3" in normalizedFormat) -> "MP3"
        normalizedFormat == "AAC" || "ADVANCED AUDIO CODING" in normalizedFormat -> "AAC"
        normalizedFormat == "M4A" || "MPEG-4" in normalizedFormat -> "M4A"
        normalizedFormat == "OGG" || "VORBIS" in normalizedFormat -> "OGG"
        normalizedFormat == "WAV" || "WAVE" in normalizedFormat -> "WAV"
        normalizedFormat == "AIFF" || normalizedFormat == "AIF" -> "AIFF"
        normalizedFormat == "ALAC" || "APPLE LOSSLESS" in normalizedFormat -> "ALAC"
        normalizedFormat == "OPUS" -> "OPUS"
        normalizedFormat == "WMA" || "WINDOWS MEDIA AUDIO" in normalizedFormat -> "WMA"
        normalizedFormat == "APE" || "MONKEY'S AUDIO" in normalizedFormat -> "APE"
        else -> null
    }
}

internal fun resolveAudioFormat(
    headerFormat: String?,
    encodingType: String?,
    fileExtension: String?
): String? {
    return normalizeAudioFormat(fileExtension)
        ?: normalizeAudioFormat(encodingType)
        ?: normalizeAudioFormat(headerFormat)
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val wholeKilohertz = sampleRateHz / HERTZ_PER_KILOHERTZ
    val remainingHertz = sampleRateHz % HERTZ_PER_KILOHERTZ

    if (remainingHertz == 0) {
        return wholeKilohertz.toString()
    }

    val fractionalKilohertz = remainingHertz
        .toString()
        .padStart(length = 3, padChar = '0')
        .trimEnd('0')

    return "$wholeKilohertz.$fractionalKilohertz"
}

private const val HERTZ_PER_KILOHERTZ = 1_000
