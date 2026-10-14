package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.UiMessage

/** A projection of service evidence only; it never selects, prepares or controls a source. */
internal fun directPlaybackUiState(
    previous: PlaybackUiState,
    direct: IntegratedDirectPlaybackState,
    librarySongs: List<Song>
): PlaybackUiState {
    if (!direct.enabled) return previous.copy(playPauseIntent = null, playbackStatus = null)
    val song = direct.libraryId?.let { id ->
        librarySongs.firstOrNull { it.id == id } ?: previous.currentSong?.takeIf { it.id == id }
    }
    return previous.copy(
        currentSong = song,
        isPlaying = direct.switching == null && direct.phase == IntegratedDirectPhase.PLAYING,
        playPauseIntent = direct.switching == null && direct.busy &&
            direct.phase != IntegratedDirectPhase.STOPPING && direct.playWhenReady,
        playbackStatus = directPlaybackStatus(direct)
    )
}

internal fun directPlaybackProgress(direct: IntegratedDirectPlaybackState) = PlaybackProgressUiState(
    currentPosition = direct.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    duration = direct.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    isSeeking = direct.phase == IntegratedDirectPhase.SEEKING,
    isDirect = direct.enabled
)

private fun directPlaybackStatus(direct: IntegratedDirectPlaybackState): UiMessage? {
    val resource = when {
        direct.switching == DirectOutputSwitch.TO_USB -> R.string.usb_settings_activating
        direct.switching == DirectOutputSwitch.TO_ANDROID -> R.string.usb_settings_returning
        direct.phase == IntegratedDirectPhase.ERROR -> when {
            !direct.cleanupSafe -> R.string.usb_settings_cleanup_failed
            direct.error == "UNSUPPORTED_TRACK" -> R.string.usb_settings_unsupported_track
            direct.error == "DIRECT_USB_DEVICE_UNAVAILABLE" -> R.string.usb_settings_disconnected
            direct.error == "DIRECT_USB_PERMISSION_FAILED" -> R.string.usb_settings_permission_required
            else -> R.string.player_playback_failed
        }
        direct.phase == IntegratedDirectPhase.PREPARING -> R.string.external_audio_preparing
        direct.phase == IntegratedDirectPhase.PAUSING -> R.string.player_playback_pausing
        direct.phase == IntegratedDirectPhase.RESUMING -> R.string.player_playback_resuming
        direct.phase == IntegratedDirectPhase.SEEKING -> R.string.player_playback_seeking
        direct.phase == IntegratedDirectPhase.STOPPING -> R.string.player_playback_stopping
        direct.phase == IntegratedDirectPhase.STOPPED -> R.string.player_playback_stopped
        direct.phase == IntegratedDirectPhase.ENDED -> R.string.player_playback_ended
        else -> return null
    }
    return UiMessage.Text(resource)
}
