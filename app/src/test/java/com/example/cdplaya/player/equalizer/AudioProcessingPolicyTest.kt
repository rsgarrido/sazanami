package com.example.cdplaya.player.equalizer

import com.example.cdplaya.player.audio.AudioOffloadPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioProcessingPolicyTest {
    @Test
    fun crossfadeForcesDecodedPlaybackOnlyWhileEnabled() {
        assertEquals(
            AudioOffloadPreference.AUTOMATIC,
            CrossfadeOffloadPolicy.effectivePreference(
                normalPreference = AudioOffloadPreference.AUTOMATIC,
                crossfadeEnabled = false
            )
        )
        assertEquals(
            AudioOffloadPreference.DISABLED,
            CrossfadeOffloadPolicy.effectivePreference(
                normalPreference = AudioOffloadPreference.AUTOMATIC,
                crossfadeEnabled = true
            )
        )
    }
    @Test
    fun bypassPreservesDisabledPreference() {
        val decision = AudioProcessingPolicy.evaluate(
            userOffloadPreference = AudioOffloadPreference.DISABLED,
            equalizerEffectivelyActive = false
        )

        assertEquals(
            AudioProcessingPathRequirement.USER_OFFLOAD_PREFERENCE_ALLOWED,
            decision.pathRequirement
        )
        assertEquals(
            AudioOffloadPreference.DISABLED,
            decision.effectiveOffloadPreference
        )
    }

    @Test
    fun bypassAllowsAutomaticPreference() {
        val decision = AudioProcessingPolicy.evaluate(
            userOffloadPreference = AudioOffloadPreference.AUTOMATIC,
            equalizerEffectivelyActive = false
        )

        assertEquals(
            AudioProcessingPathRequirement.USER_OFFLOAD_PREFERENCE_ALLOWED,
            decision.pathRequirement
        )
        assertEquals(
            AudioOffloadPreference.AUTOMATIC,
            decision.effectiveOffloadPreference
        )
    }

    @Test
    fun activeEqualizerRequiresDecodedPcmForEveryPreference() {
        AudioOffloadPreference.entries.forEach { preference ->
            val decision = AudioProcessingPolicy.evaluate(
                userOffloadPreference = preference,
                equalizerEffectivelyActive = true
            )

            assertEquals(
                AudioProcessingPathRequirement.DECODED_PCM_REQUIRED,
                decision.pathRequirement
            )
            assertEquals(
                AudioOffloadPreference.DISABLED,
                decision.effectiveOffloadPreference
            )
        }
    }

    @Test
    fun bypassRestoresOriginalUserPreferenceWithoutMutation() {
        val persistedPreference = AudioOffloadPreference.AUTOMATIC
        val active = AudioProcessingPolicy.evaluate(
            persistedPreference,
            equalizerEffectivelyActive = true
        )
        val bypassed = AudioProcessingPolicy.evaluate(
            persistedPreference,
            equalizerEffectivelyActive = false
        )

        assertEquals(
            AudioOffloadPreference.DISABLED,
            active.effectiveOffloadPreference
        )
        assertEquals(
            AudioOffloadPreference.AUTOMATIC,
            bypassed.effectiveOffloadPreference
        )
        assertEquals(
            AudioOffloadPreference.AUTOMATIC,
            persistedPreference
        )
    }

    @Test
    fun identicalInputsProduceStructurallyIdenticalDecision() {
        val first = AudioProcessingPolicy.evaluate(
            AudioOffloadPreference.AUTOMATIC,
            equalizerEffectivelyActive = true
        )
        val second = AudioProcessingPolicy.evaluate(
            AudioOffloadPreference.AUTOMATIC,
            equalizerEffectivelyActive = true
        )

        assertEquals(first, second)
    }

    @Test
    fun everyDspCombinationUsesDecodedPcmExactlyWhenRequired() {
        AudioOffloadPreference.entries.forEach { preference ->
            listOf(false, true).forEach { equalizer ->
                listOf(false, true).forEach { limiter ->
                    listOf(false, true).forEach { comparison ->
                        val decision = AudioProcessingPolicy.evaluate(
                            userOffloadPreference = preference,
                            equalizerEffectivelyActive = equalizer,
                            limiterEffectivelyActive = limiter,
                            comparisonSessionActive = comparison
                        )
                        val requiresPcm =
                            equalizer || limiter || comparison

                        assertEquals(
                            if (requiresPcm) {
                                AudioProcessingPathRequirement
                                    .DECODED_PCM_REQUIRED
                            } else {
                                AudioProcessingPathRequirement
                                    .USER_OFFLOAD_PREFERENCE_ALLOWED
                            },
                            decision.pathRequirement
                        )
                        assertEquals(
                            if (requiresPcm) {
                                AudioOffloadPreference.DISABLED
                            } else {
                                preference
                            },
                            decision.effectiveOffloadPreference
                        )
                    }
                }
            }
        }
    }
}
