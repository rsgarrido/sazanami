package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.Song

/** Shares the phone UI's already-filtered library with the playback service process. */
object PlaybackLibraryBridge {
    private var playbackController: PlaybackController? = null

    var songs: List<Song> = emptyList()
        private set

    fun register(controller: PlaybackController) {
        playbackController = controller
    }

    fun unregister(controller: PlaybackController) {
        if (playbackController === controller) {
            playbackController = null
        }
    }

    fun updateSongs(filteredSongs: List<Song>) {
        songs = filteredSongs
    }

    fun playSelectedSong(song: Song, playbackContext: List<Song>) {
        playbackController?.playSelectedSong(song, playbackContext)
    }

    fun prepareReplayGainBaseline(
        mediaId: String,
        onPrepared: (Float) -> Unit
    ): Boolean {
        val songId = mediaId.toLongOrNull() ?: return false
        return playbackController?.prepareReplayGainBaseline(songId, onPrepared) == true
    }
}
