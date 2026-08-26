package com.example.cdplaya.player.equalizer

import com.example.cdplaya.player.audio.AudioOffloadPreference

internal enum class AudioProcessingPathRequirement {
    USER_OFFLOAD_PREFERENCE_ALLOWED,
    DECODED_PCM_REQUIRED
}
internal data class AudioProcessingPolicyDecision(
    val pathRequirement: AudioProcessingPathRequirement,
    val effectiveOffloadPreference: AudioOffloadPreference
)

internal object AudioProcessingPolicy {
    fun evaluate(
        userOffloadPreference: AudioOffloadPreference,
        equalizerEffectivelyActive: Boolean,
        limiterEffectivelyActive: Boolean = false,
        comparisonSessionActive: Boolean = false
    ): AudioProcessingPolicyDecision {
        return if (
            equalizerEffectivelyActive ||
            limiterEffectivelyActive ||
            comparisonSessionActive
        ) {
            AudioProcessingPolicyDecision(
                pathRequirement =
                    AudioProcessingPathRequirement.DECODED_PCM_REQUIRED,
                effectiveOffloadPreference = AudioOffloadPreference.DISABLED
            )
        } else {
            AudioProcessingPolicyDecision(
                pathRequirement =
                    AudioProcessingPathRequirement.USER_OFFLOAD_PREFERENCE_ALLOWED,
                effectiveOffloadPreference = userOffloadPreference
            )
        }
    }
}

internal object CrossfadeOffloadPolicy {
    fun effectivePreference(
        normalPreference: AudioOffloadPreference,
        crossfadeEnabled: Boolean
    ): AudioOffloadPreference = if (crossfadeEnabled) {
        AudioOffloadPreference.DISABLED
    } else {
        normalPreference
    }
}
