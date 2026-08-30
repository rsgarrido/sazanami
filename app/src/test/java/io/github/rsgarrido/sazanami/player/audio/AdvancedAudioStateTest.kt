package io.github.rsgarrido.sazanami.player.audio

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAudioStateTest {
    @Test
    fun disconnectedDefaultsAreConservativeAndValid() {
        val state = AudioOutputUiState()

        assertFalse(state.isPlayerConnected)
        assertNull(state.sourceFormat)
        assertEquals(AudioRouteCategory.UNKNOWN, state.routeInfo.category)
        assertEquals(AudioOffloadPreference.DISABLED, state.offloadState.requestedPreference)
        assertEquals(AudioOffloadActualState.INACTIVE, state.offloadState.actualState)
    }

    @Test
    fun invalidPreferenceFallsBackToDisabled() {
        assertEquals(
            AudioOffloadPreference.DISABLED,
            AudioOffloadPreference.fromStorageValue("REQUIRED")
        )
        assertEquals(
            AudioOffloadPreference.DISABLED,
            AudioOffloadPreference.fromStorageValue(null)
        )
    }

    @Test
    fun requestedAndActualOffloadRemainSeparate() {
        val state = AudioOffloadRuntimeState.create(
            requestedPreference = AudioOffloadPreference.AUTOMATIC,
            isOffloadedPlayback = false,
            isSleepingForOffload = false
        )

        assertEquals(AudioOffloadPreference.AUTOMATIC, state.requestedPreference)
        assertEquals(AudioOffloadActualState.INACTIVE, state.actualState)
        assertEquals(AudioOffloadStatus.REQUESTED_NOT_ACTIVE, state.status)
    }

    @Test
    fun sleepingCannotBeReportedWithoutActiveOffload() {
        val state = AudioOffloadRuntimeState.create(
            requestedPreference = AudioOffloadPreference.AUTOMATIC,
            isOffloadedPlayback = false,
            isSleepingForOffload = true
        )

        assertFalse(state.isSleepingForOffload)
        assertEquals(AudioOffloadActualState.INACTIVE, state.actualState)
    }

    @Test
    fun sourceFormatUsesUnknownValuesSafely() {
        val format = AudioSourceFormat(
            sampleRateHz = sanitizeKnownPositive(-1),
            channelCount = sanitizeKnownPositive(0),
            sampleMimeType = sanitizeKnownText(" ")
        )

        assertFalse(format.hasKnownValue)
        assertNull(format.sampleRateHz)
        assertNull(format.channelCount)
        assertNull(format.sampleMimeType)
    }

    @Test
    fun structuralEqualitySuppressesIdenticalSnapshots() {
        val first = AudioOutputRuntimeSnapshot()
        val second = AudioOutputRuntimeSnapshot()

        assertEquals(first, second)
        assertTrue(first === first)
    }

    @Test
    fun publicAudioStateContainsNoAndroidOrMedia3Objects() {
        val stateClasses = listOf(
            AudioOffloadRuntimeState::class.java,
            AudioSourceFormat::class.java,
            AudioRouteInfo::class.java,
            EqualizerRuntimeState::class.java,
            AudioOutputUiState::class.java
        )

        stateClasses.flatMap { type ->
            type.declaredFields.filterNot { field -> Modifier.isStatic(field.modifiers) }
        }.forEach { field ->
            assertFalse(field.type.name.startsWith("android."))
            assertFalse(field.type.name.startsWith("androidx.media3."))
        }
    }

    @Test
    fun equalizerStateDoesNotChangeReplayGainFields() {
        val before = AudioOutputUiState(
            replayGainDb = -7.0f,
            appliedVolumeMultiplier = 0.45f
        )

        val after = before.copy(
            equalizerRuntimeState = EqualizerRuntimeState(
                requestedEnabled = true,
                effectivelyActive = true,
                bypassed = false,
                requiresDecodedPcm = true
            )
        )

        assertEquals(before.replayGainDb, after.replayGainDb)
        assertEquals(
            before.appliedVolumeMultiplier,
            after.appliedVolumeMultiplier
        )
    }
}
