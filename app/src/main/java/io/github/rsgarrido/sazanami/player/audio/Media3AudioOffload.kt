package io.github.rsgarrido.sazanami.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal fun AudioOffloadPreference.toMedia3AudioOffloadPreferences():
    TrackSelectionParameters.AudioOffloadPreferences {
    val mode = when (this) {
        AudioOffloadPreference.DISABLED ->
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        AudioOffloadPreference.AUTOMATIC ->
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
    }
    return TrackSelectionParameters.AudioOffloadPreferences.Builder()
        .setAudioOffloadMode(mode)
        .setIsGaplessSupportRequired(true)
        .setIsSpeedChangeSupportRequired(false)
        .build()
}

@OptIn(UnstableApi::class)
internal fun TrackSelectionParameters.withAudioOffloadPreference(
    preference: AudioOffloadPreference
): TrackSelectionParameters = buildUpon()
    .setAudioOffloadPreferences(preference.toMedia3AudioOffloadPreferences())
    .build()
