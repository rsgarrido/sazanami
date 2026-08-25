package com.example.cdplaya.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.cdplaya.data.Song
import com.google.common.util.concurrent.ListenableFuture

class MusicPlayer(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var currentSong: Song? = null
    private var currentPlaylist: List<Song> = emptyList()

    var onSongCompleted: (() -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onCurrentSongChanged: ((Long?) -> Unit)? = null

    fun connect(onConnected: (() -> Unit)? = null) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        val future = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture = future

        future.addListener(
            {
                controller = future.get()

                controller?.addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                onSongCompleted?.invoke()
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            onPlaybackStateChanged?.invoke(isPlaying)
                        }

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            val songId = mediaItem?.mediaId?.toLongOrNull()

                            currentSong = currentPlaylist.firstOrNull { song ->
                                song.id == songId
                            }

                            onCurrentSongChanged?.invoke(songId)
                        }
                    }
                )

                onConnected?.invoke()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun playSong(
        song: Song,
        shouldStart: Boolean = true,
        startPosition: Int = 0,
        playlist: List<Song> = listOf(song)
    ) {
        val playerController = controller ?: return

        val safePlaylist = if (playlist.isEmpty()) {
            listOf(song)
        } else {
            playlist
        }

        val startIndex = safePlaylist.indexOfFirst { playlistSong ->
            playlistSong.id == song.id
        }.let { index ->
            if (index == -1) 0 else index
        }

        currentPlaylist = safePlaylist
        currentSong = safePlaylist[startIndex]

        val mediaItems = safePlaylist.map { playlistSong ->
            playlistSong.toPlayableMediaItem()
        }

        playerController.setMediaItems(
            mediaItems,
            startIndex,
            startPosition.toLong()
        )

        playerController.prepare()

        if (shouldStart) {
            playerController.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun stop() {
        controller?.stop()
        controller?.volume = 1f
        currentSong = null
    }

    fun skipToNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val playerController = controller ?: return

        if (playerController.currentPosition > 3_000) {
            playerController.seekTo(0)
        } else {
            playerController.seekToPreviousMediaItem()
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = false
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        controller?.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun updateUpcomingPlaylist(upcomingSongs: List<Song>) {
        val playerController = controller ?: return

        val currentIndex = playerController.currentMediaItemIndex

        if (currentIndex < 0) {
            return
        }

        if (currentIndex > 0) {
            playerController.removeMediaItems(0, currentIndex)
        }

        val current = currentSong ?: return
        val existingUpcomingIds = (1 until playerController.mediaItemCount).map { index ->
            playerController.getMediaItemAt(index).mediaId
        }
        val requestedUpcomingIds = upcomingSongs.map { song -> song.id.toString() }

        currentPlaylist = listOf(current) + upcomingSongs

        if (existingUpcomingIds == requestedUpcomingIds) {
            return
        }

        val upcomingMediaItems = upcomingSongs.map { song ->
            song.toPlayableMediaItem()
        }

        playerController.replaceMediaItems(
            1,
            playerController.mediaItemCount,
            upcomingMediaItems
        )
    }

    fun updateCurrentSongMetadata(song: Song) {
        currentSong = song
        currentPlaylist = currentPlaylist.map { existing ->
            if (existing.id == song.id) song else existing
        }

        val playerController = controller ?: return
        val currentIndex = playerController.currentMediaItemIndex
        if (currentIndex < 0) return
        val existingItem = playerController.getMediaItemAt(currentIndex)
        val updatedItem = song.toPlayableMediaItem(
            itemInstanceId = existingItem.listeningEvidence()?.itemInstanceId
                ?: java.util.UUID.randomUUID().toString()
        )
        if (playerController.getMediaItemAt(currentIndex) == updatedItem) return
        playerController.replaceMediaItem(currentIndex, updatedItem)
    }

    fun isPlaying(): Boolean {
        return controller?.isPlaying == true
    }

    fun isPlayWhenReady(): Boolean {
        return controller?.playWhenReady == true
    }

    fun getCurrentSong(): Song? {
        return currentSong
    }

    fun getCurrentPosition(): Int {
        return controller?.currentPosition?.toInt() ?: 0
    }

    fun getDuration(): Int {
        val playerDuration = controller?.duration ?: 0L

        return if (playerDuration > 0) {
            playerDuration.toInt()
        } else {
            currentSong?.duration?.toInt() ?: 0
        }
    }

    fun seekTo(position: Int) {
        controller?.seekTo(position.toLong())
    }

    fun setVolume(volumeMultiplier: Float) {
        controller?.volume = volumeMultiplier.coerceIn(
            minimumValue = 0f,
            maximumValue = 1f
        )
    }

    fun release() {
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }

        controllerFuture = null
        controller = null
    }

    fun updatePlaylistKeepingCurrent(
        currentSong: Song?,
        playlist: List<Song>,
        currentPosition: Int,
        shouldStart: Boolean
    ) {
        val song = currentSong ?: return
        val playerController = controller ?: return

        val safePlaylist = if (playlist.isEmpty()) {
            listOf(song)
        } else {
            playlist
        }

        val startIndex = safePlaylist.indexOfFirst { playlistSong ->
            playlistSong.id == song.id
        }.let { index ->
            if (index == -1) 0 else index
        }

        currentPlaylist = safePlaylist
        this.currentSong = safePlaylist[startIndex]

        val mediaItems = safePlaylist.map { playlistSong ->
            playlistSong.toPlayableMediaItem()
        }

        playerController.shuffleModeEnabled = false

        playerController.setMediaItems(
            mediaItems,
            startIndex,
            currentPosition.toLong()
        )

        playerController.prepare()

        if (shouldStart) {
            playerController.play()
        } else {
            playerController.pause()
        }
    }
}
