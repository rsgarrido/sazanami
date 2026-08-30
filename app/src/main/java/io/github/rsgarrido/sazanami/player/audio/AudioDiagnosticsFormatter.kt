package io.github.rsgarrido.sazanami.player.audio

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPlanApplicationMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import java.util.Locale

fun formatAudioSource(format: AudioSourceFormat?): String {
    format ?: return "Unknown"
    val parts = buildList {
        format.sampleMimeType?.let { add(friendlyCodecName(it, format.codecs)) }
            ?: format.codecs?.let(::add)
        format.sourceBitDepth?.let { add("$it-bit source") }
        format.sampleRateHz?.let { add(formatSampleRate(it)) }
        format.channelCount?.let { channels ->
            add(if (channels == 1) "1 channel" else "$channels channels")
        }
        format.bitrateBitsPerSecond?.let { bitrate ->
            add("${bitrate / 1_000} kbps source")
        }
    }
    return parts.joinToString(" · ").ifBlank { "Unknown" }
}

fun formatAudioRoute(route: AudioRouteInfo): String = when (route.category) {
    AudioRouteCategory.BUILT_IN_SPEAKER -> "Built-in speaker"
    AudioRouteCategory.WIRED_HEADPHONES -> "Wired headphones/headset"
    AudioRouteCategory.USB -> "USB audio"
    AudioRouteCategory.BLUETOOTH_CLASSIC -> "Bluetooth audio"
    AudioRouteCategory.BLUETOOTH_LE -> "Bluetooth LE audio"
    AudioRouteCategory.HDMI -> "HDMI audio"
    AudioRouteCategory.REMOTE_CAST -> "Remote/cast"
    AudioRouteCategory.OTHER -> "Other audio route"
    AudioRouteCategory.UNKNOWN -> "Unknown"
}

fun formatAudioOffloadStatus(state: AudioOffloadRuntimeState): String = when (state.status) {
    AudioOffloadStatus.DISABLED -> "Disabled"
    AudioOffloadStatus.REQUESTED_NOT_ACTIVE ->
        "Automatic requested — not active for the current playback"
    AudioOffloadStatus.ACTIVE -> "Active"
    AudioOffloadStatus.ACTIVE_SLEEPING -> "Active — processor sleeping"
}

fun formatAudioCompatibility(state: AudioOutputUiState): String {
    return buildList {
        if (state.isGaplessSupportRequired) add("Gapless support required")
        add("Playback-speed support not required")
        add("ReplayGain uses player volume")
        if (
            AudioCompatibilityConstraint.EQUALIZER_REQUIRES_DECODED_PCM in
            state.offloadState.knownCompatibilityConstraints
        ) {
            add("Equalizer requires decoded PCM")
        }
    }.joinToString(" · ")
}

fun formatEqualizerStatus(state: EqualizerRuntimeState): String {
    return when {
        !state.requestedEnabled -> "Bypassed"
        state.transitionInProgress -> "Transitioning"
        state.effectivelyActive -> "Active"
        else -> "Bypassed"
    }
}

fun formatEqualizerProcessorFormat(
    state: EqualizerRuntimeState
): String {
    val sampleRateHz = state.sampleRateHz ?: return "Unconfigured"
    val channelCount = state.channelCount ?: return "Unconfigured"
    val channels = if (channelCount == 1) {
        "1 channel"
    } else {
        "$channelCount channels"
    }
    return "PCM16, ${formatSampleRate(sampleRateHz)}, $channels"
}

fun formatEqualizerPlanApplication(
    state: EqualizerRuntimeState
): String {
    return when (state.lastPlanApplicationMode) {
        EqualizerPlanApplicationMode.NONE -> "None"
        EqualizerPlanApplicationMode.CROSSFADE -> {
            String.format(
                Locale.ROOT,
                "%.2f ms crossfade (%d frames at %s; last completed adoption)",
                state.lastTransitionDurationMillis,
                state.lastTransitionFrameCount,
                state.lastTransitionSampleRateHz
                    ?.let(::formatSampleRate)
                    ?: "unknown rate"
            )
        }
        EqualizerPlanApplicationMode.DIRECT_AFTER_FLUSH ->
            "Direct after Media3 flush"
        EqualizerPlanApplicationMode.DIRECT_BYPASS ->
            "Direct bypass update (no DSP change)"
    }
}

fun formatEqualizerPlanLatency(
    state: EqualizerRuntimeState
): String {
    val preparation = state.planPreparationLatencyMillis
        ?.let { "$it ms preparation" }
        ?: "Preparation pending"
    val application = state.planApplicationLatencyMillis
        ?.let { "$it ms to DSP application" }
        ?: "DSP application pending"
    return "$preparation · $application"
}

private fun friendlyCodecName(mimeType: String, codecs: String?): String = when (mimeType) {
    "audio/flac" -> "FLAC"
    "audio/mpeg" -> "MP3"
    "audio/mp4a-latm" -> "AAC"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/raw" -> "PCM"
    "audio/ac3" -> "AC-3"
    "audio/eac3" -> "E-AC-3"
    else -> codecs?.takeIf { it.isNotBlank() } ?: mimeType
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val kilohertz = sampleRateHz / 1_000.0
    return if (sampleRateHz % 1_000 == 0) {
        "${sampleRateHz / 1_000} kHz"
    } else {
        String.format(Locale.ROOT, "%.1f kHz", kilohertz)
    }
}
