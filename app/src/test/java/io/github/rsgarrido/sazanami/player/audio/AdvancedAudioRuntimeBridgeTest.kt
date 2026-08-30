package io.github.rsgarrido.sazanami.player.audio

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAudioRuntimeBridgeTest {
    @After
    fun resetBridge() {
        AdvancedAudioRuntimeBridge.disconnect()
    }

    @Test
    fun actualOffloadAndSleepingTransitionsFollowListenerFacts() {
        AdvancedAudioRuntimeBridge.onPlayerConnected(AudioOffloadPreference.AUTOMATIC)
        AdvancedAudioRuntimeBridge.updateOffloadPlayback(true)

        assertEquals(
            AudioOffloadActualState.ACTIVE,
            AdvancedAudioRuntimeBridge.state.value.offloadState.actualState
        )

        AdvancedAudioRuntimeBridge.updateSleepingForOffload(true)
        assertEquals(
            AudioOffloadActualState.SLEEPING,
            AdvancedAudioRuntimeBridge.state.value.offloadState.actualState
        )

        AdvancedAudioRuntimeBridge.updateOffloadPlayback(false)
        val inactive = AdvancedAudioRuntimeBridge.state.value.offloadState
        assertEquals(AudioOffloadActualState.INACTIVE, inactive.actualState)
        assertFalse(inactive.isSleepingForOffload)
    }

    @Test
    fun disablingPreferenceDoesNotInventAnActualListenerTransition() {
        AdvancedAudioRuntimeBridge.onPlayerConnected(AudioOffloadPreference.AUTOMATIC)
        AdvancedAudioRuntimeBridge.updateOffloadPlayback(true)
        AdvancedAudioRuntimeBridge.updateOffloadPreference(AudioOffloadPreference.DISABLED)

        val state = AdvancedAudioRuntimeBridge.state.value.offloadState
        assertEquals(AudioOffloadPreference.DISABLED, state.requestedPreference)
        assertTrue(state.isOffloadedPlayback)
        assertEquals(AudioOffloadStatus.ACTIVE, state.status)
    }

    @Test
    fun mediaAndServiceCleanupClearStaleRuntimeFacts() {
        AdvancedAudioRuntimeBridge.onPlayerConnected(AudioOffloadPreference.AUTOMATIC)
        AdvancedAudioRuntimeBridge.updateSourceFormat(
            AudioSourceFormat(sampleMimeType = "audio/flac")
        )
        AdvancedAudioRuntimeBridge.updateRouteInfo(
            AudioRouteInfo(AudioRouteCategory.USB)
        )
        AdvancedAudioRuntimeBridge.updateAudioSessionId(42)

        AdvancedAudioRuntimeBridge.updateSourceFormat(null)
        assertNull(AdvancedAudioRuntimeBridge.state.value.sourceFormat)

        AdvancedAudioRuntimeBridge.disconnect()
        val disconnected = AdvancedAudioRuntimeBridge.state.value
        assertFalse(disconnected.isPlayerConnected)
        assertNull(disconnected.sourceFormat)
        assertNull(disconnected.audioSessionId)
        assertEquals(AudioRouteCategory.UNKNOWN, disconnected.routeInfo.category)
    }

    @Test
    fun equalizerConstraintAppearsOnlyWhenDecodedPcmIsRequired() {
        AdvancedAudioRuntimeBridge.updateEqualizerRuntimeState(
            EqualizerRuntimeState(
                requestedEnabled = true,
                effectivelyActive = true,
                bypassed = false,
                requiresDecodedPcm = true
            )
        )

        assertTrue(
            AudioCompatibilityConstraint.EQUALIZER_REQUIRES_DECODED_PCM in
                AdvancedAudioRuntimeBridge
                    .state
                    .value
                    .offloadState
                    .knownCompatibilityConstraints
        )

        AdvancedAudioRuntimeBridge.updateEqualizerRuntimeState(
            EqualizerRuntimeState()
        )

        assertFalse(
            AudioCompatibilityConstraint.EQUALIZER_REQUIRES_DECODED_PCM in
                AdvancedAudioRuntimeBridge
                    .state
                    .value
                    .offloadState
                    .knownCompatibilityConstraints
        )
    }
}
