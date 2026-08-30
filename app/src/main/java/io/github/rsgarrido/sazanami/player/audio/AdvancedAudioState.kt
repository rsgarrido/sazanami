package io.github.rsgarrido.sazanami.player.audio

import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState

enum class AudioOffloadPreference(
    val displayName: String
) {
    DISABLED("Disabled"),
    AUTOMATIC("Automatic");

    companion object {
        fun fromStorageValue(value: String?): AudioOffloadPreference =
            entries.firstOrNull { preference -> preference.name == value }
                ?: DISABLED
    }
}

enum class AudioOffloadActualState {
    INACTIVE,
    ACTIVE,
    SLEEPING
}

enum class AudioOffloadStatus {
    DISABLED,
    REQUESTED_NOT_ACTIVE,
    ACTIVE,
    ACTIVE_SLEEPING
}

enum class AudioCompatibilityConstraint {
    GAPLESS_SUPPORT_REQUIRED,
    PLAYBACK_SPEED_SUPPORT_NOT_REQUIRED,
    REPLAY_GAIN_USES_PLAYER_VOLUME,
    EQUALIZER_REQUIRES_DECODED_PCM
}

data class AudioOffloadRuntimeState(
    val requestedPreference: AudioOffloadPreference = AudioOffloadPreference.DISABLED,
    val actualState: AudioOffloadActualState = AudioOffloadActualState.INACTIVE,
    val isOffloadedPlayback: Boolean = false,
    val isSleepingForOffload: Boolean = false,
    val knownCompatibilityConstraints: Set<AudioCompatibilityConstraint> =
        DEFAULT_COMPATIBILITY_CONSTRAINTS
) {
    val status: AudioOffloadStatus
        get() = when {
            isSleepingForOffload -> AudioOffloadStatus.ACTIVE_SLEEPING
            isOffloadedPlayback -> AudioOffloadStatus.ACTIVE
            requestedPreference == AudioOffloadPreference.DISABLED ->
                AudioOffloadStatus.DISABLED
            else -> AudioOffloadStatus.REQUESTED_NOT_ACTIVE
        }

    companion object {
        val DEFAULT_COMPATIBILITY_CONSTRAINTS = setOf(
            AudioCompatibilityConstraint.GAPLESS_SUPPORT_REQUIRED,
            AudioCompatibilityConstraint.PLAYBACK_SPEED_SUPPORT_NOT_REQUIRED,
            AudioCompatibilityConstraint.REPLAY_GAIN_USES_PLAYER_VOLUME
        )

        fun create(
            requestedPreference: AudioOffloadPreference,
            isOffloadedPlayback: Boolean,
            isSleepingForOffload: Boolean,
            knownCompatibilityConstraints:
                Set<AudioCompatibilityConstraint> =
                    DEFAULT_COMPATIBILITY_CONSTRAINTS
        ): AudioOffloadRuntimeState {
            val sleeping = isOffloadedPlayback && isSleepingForOffload
            return AudioOffloadRuntimeState(
                requestedPreference = requestedPreference,
                actualState = when {
                    sleeping -> AudioOffloadActualState.SLEEPING
                    isOffloadedPlayback -> AudioOffloadActualState.ACTIVE
                    else -> AudioOffloadActualState.INACTIVE
                },
                isOffloadedPlayback = isOffloadedPlayback,
                isSleepingForOffload = sleeping,
                knownCompatibilityConstraints =
                    knownCompatibilityConstraints
            )
        }
    }
}

data class AudioSourceFormat(
    val sampleMimeType: String? = null,
    val codecs: String? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val bitrateBitsPerSecond: Int? = null,
    val pcmEncoding: Int? = null,
    val sourceBitDepth: Int? = null,
    val encoderDelayFrames: Int? = null,
    val encoderPaddingFrames: Int? = null
) {
    val hasKnownValue: Boolean
        get() = sampleMimeType != null ||
            codecs != null ||
            sampleRateHz != null ||
            channelCount != null ||
            bitrateBitsPerSecond != null ||
            pcmEncoding != null ||
            sourceBitDepth != null ||
            encoderDelayFrames != null ||
            encoderPaddingFrames != null
}

enum class AudioRouteCategory {
    BUILT_IN_SPEAKER,
    WIRED_HEADPHONES,
    USB,
    BLUETOOTH_CLASSIC,
    BLUETOOTH_LE,
    HDMI,
    REMOTE_CAST,
    OTHER,
    UNKNOWN
}

data class AudioRouteInfo(
    val category: AudioRouteCategory = AudioRouteCategory.UNKNOWN,
    val productName: String? = null,
    val isLocalPlayback: Boolean = true
)

data class AudioOutputUiState(
    val sourceFormat: AudioSourceFormat? = null,
    val routeInfo: AudioRouteInfo = AudioRouteInfo(),
    val offloadState: AudioOffloadRuntimeState = AudioOffloadRuntimeState(),
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val replayGainDb: Float? = null,
    val appliedVolumeMultiplier: Float? = null,
    val equalizerRuntimeState: EqualizerRuntimeState =
        EqualizerRuntimeState(),
    val audioSessionId: Int? = null,
    val isPlayerConnected: Boolean = false,
    val isGaplessSupportRequired: Boolean = true
)

internal data class AudioOutputRuntimeSnapshot(
    val sourceFormat: AudioSourceFormat? = null,
    val routeInfo: AudioRouteInfo = AudioRouteInfo(),
    val offloadState: AudioOffloadRuntimeState = AudioOffloadRuntimeState(),
    val equalizerRuntimeState: EqualizerRuntimeState =
        EqualizerRuntimeState(),
    val audioSessionId: Int? = null,
    val isPlayerConnected: Boolean = false
)

internal fun sanitizeKnownPositive(value: Int): Int? = value.takeIf { it > 0 }

internal fun sanitizeKnownText(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }
