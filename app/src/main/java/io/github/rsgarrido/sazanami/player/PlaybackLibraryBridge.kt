package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Shares the live phone library/state with the playback service when the UI process is active. */
object PlaybackLibraryBridge {
    private var playbackController: PlaybackController? = null
    private var playbackPolicyListener: ((Boolean, RepeatMode) -> Unit)? = null

    private val publication = MutableStateFlow(0L)
    val catalogPublication = publication.asStateFlow()

    @Volatile
    var songs: List<Song> = emptyList()
        private set

    fun register(controller: PlaybackController) {
        playbackController = controller
        val state = controller.uiState.value
        notifyPlaybackPolicyChanged(state.isShuffleEnabled, state.repeatMode)
    }

    fun unregister(controller: PlaybackController) {
        if (playbackController === controller) {
            playbackController = null
        }
    }

    fun updateSongs(filteredSongs: List<Song>) {
        songs = filteredSongs
        publication.update { it + 1 }
    }

    fun hasPlaybackController(): Boolean = playbackController != null

    fun prepareExternalPlaybackSelection(song: Song, playbackContext: List<Song>): Boolean {
        val controller = playbackController ?: return false
        controller.prepareExternalPlaybackSelection(song, playbackContext)
        return true
    }

    fun preparePersistentQueueSwitch() {
        playbackController?.preparePersistentQueueSwitch()
    }

    fun setSongShuffleEnabled(enabled: Boolean): Boolean {
        val controller = playbackController ?: return false
        controller.setSongShuffleEnabled(enabled)
        return true
    }

    fun setRepeatAllEnabled(enabled: Boolean): Boolean {
        val controller = playbackController ?: return false
        controller.setRepeatModeFromExternalController(
            if (enabled) RepeatMode.ALL else RepeatMode.OFF
        )
        return true
    }

    fun currentShuffleEnabled(): Boolean? =
        playbackController?.uiState?.value?.isShuffleEnabled

    fun currentRepeatMode(): RepeatMode? =
        playbackController?.uiState?.value?.repeatMode

    fun currentPlaybackContextSongs(): List<Song> =
        playbackController?.playbackContextSongsForPersistence().orEmpty()

    fun registerPlaybackPolicyListener(listener: (Boolean, RepeatMode) -> Unit) {
        playbackPolicyListener = listener
        playbackController?.uiState?.value?.let { state ->
            listener(state.isShuffleEnabled, state.repeatMode)
        }
    }

    fun unregisterPlaybackPolicyListener() {
        playbackPolicyListener = null
    }

    fun notifyPlaybackPolicyChanged(shuffleEnabled: Boolean, repeatMode: RepeatMode) {
        playbackPolicyListener?.invoke(shuffleEnabled, repeatMode)
    }

    fun prepareReplayGainBaseline(
        mediaId: String,
        onPrepared: (Float) -> Unit
    ): Boolean {
        val songId = mediaId.toLongOrNull() ?: return false
        return playbackController?.prepareReplayGainBaseline(songId, onPrepared) == true
    }
}
