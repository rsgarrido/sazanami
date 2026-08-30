package io.github.rsgarrido.sazanami.player.audio

import androidx.media3.common.TrackSelectionParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3AudioOffloadTest {
    @Test
    fun disabledMapsToMedia3DisabledWithGaplessPriority() {
        val preferences = AudioOffloadPreference.DISABLED.toMedia3AudioOffloadPreferences()

        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
            preferences.audioOffloadMode
        )
        assertTrue(preferences.isGaplessSupportRequired)
        assertFalse(preferences.isSpeedChangeSupportRequired)
    }

    @Test
    fun automaticMapsToEnabledAndNeverRequired() {
        val preferences = AudioOffloadPreference.AUTOMATIC.toMedia3AudioOffloadPreferences()

        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
            preferences.audioOffloadMode
        )
        assertTrue(
            preferences.audioOffloadMode !=
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_REQUIRED
        )
    }

    @Test
    fun livePreferenceChangePreservesUnrelatedTrackSelectionParameters() {
        val before = TrackSelectionParameters.Builder()
            .setMaxAudioBitrate(320_000)
            .setMaxAudioChannelCount(2)
            .build()

        val after = before.withAudioOffloadPreference(AudioOffloadPreference.AUTOMATIC)

        assertEquals(before.maxAudioBitrate, after.maxAudioBitrate)
        assertEquals(before.maxAudioChannelCount, after.maxAudioChannelCount)
        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
            after.audioOffloadPreferences.audioOffloadMode
        )
    }
}
