package io.github.rsgarrido.sazanami.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import com.google.common.util.concurrent.ListenableFuture

internal const val MEDIA_PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

internal data class LivePlaybackSnapshot(
    val currentSong: Song,
    val playlist: List<Song>,
    val currentPlaylistIndex: Int,
    val currentPosition: Int,
    val duration: Int,
    val isPlaying: Boolean,
    val repeatMode: RepeatMode
) {
    val upcomingSongs: List<Song>
        get() = playlist.drop(currentPlaylistIndex + 1)
}

class MusicPlayer(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var currentSong: Song? = null
    private var currentPlaylist: List<Song> = emptyList()
    private var pendingPublishedTimelineMediaIds: List<String>? = null

    var onSongCompleted: (() -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onCurrentSongChanged: ((Long?) -> Unit)? = null
    var onTimelineChanged: (() -> Unit)? = null

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

                        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                            val expectedMediaIds = pendingPublishedTimelineMediaIds
                            if (
                                expectedMediaIds == null ||
                                timelineMediaIds() == expectedMediaIds
                            ) {
                                pendingPublishedTimelineMediaIds = null
                                this@MusicPlayer.onTimelineChanged?.invoke()
                            }
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

    internal fun adoptLiveSession(librarySongs: List<Song>): LivePlaybackSnapshot? {
        val playerController = controller ?: return null
        val currentSongId = playerController.currentMediaItem
            ?.mediaId
            ?.toLongOrNull()
            ?: return null
        val songsById = librarySongs.associateBy(Song::id)
        val resolvedTimeline = (0 until playerController.mediaItemCount)
            .mapNotNull { index ->
                playerController.getMediaItemAt(index).mediaId
                    .toLongOrNull()
                    ?.let(songsById::get)
                    ?.let { song -> index to song }
            }
        val resolvedCurrent = resolvedTimeline.firstOrNull { (index) ->
            index == playerController.currentMediaItemIndex
        } ?: resolvedTimeline.firstOrNull { (_, song) -> song.id == currentSongId }
            ?: return null
        val livePlaylist = resolvedTimeline.map { (_, song) -> song }
        val liveCurrentIndex = resolvedTimeline.indexOf(resolvedCurrent)
        val liveCurrentSong = resolvedCurrent.second

        currentPlaylist = livePlaylist
        currentSong = liveCurrentSong

        val playerDuration = playerController.duration
        val liveDuration = if (playerDuration > 0L) {
            playerDuration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            liveCurrentSong.duration.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        val liveRepeatMode = when (playerController.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

        return LivePlaybackSnapshot(
            currentSong = liveCurrentSong,
            playlist = livePlaylist,
            currentPlaylistIndex = liveCurrentIndex,
            currentPosition = playerController.currentPosition
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt(),
            duration = liveDuration,
            isPlaying = playerController.isPlaying,
            repeatMode = liveRepeatMode
        )
    }

    internal fun currentItemInstanceId(): String? =
        controller?.currentMediaItem?.listeningEvidence()?.itemInstanceId

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

        if (playerController.currentPosition > MEDIA_PREVIOUS_RESTART_THRESHOLD_MS) {
            playerController.seekTo(0)
        } else {
            playerController.seekToPreviousMediaItem()
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = false
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        controller?.repeatMode = repeatMode.toPlayerRepeatMode()
    }

    internal fun synchronizeNavigationPolicy(
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        origin: ControllerSynchronizationOrigin
    ) {
        val playerController = controller ?: return
        val effectiveShuffleEnabled = false
        val playerRepeatMode = repeatMode.toPlayerRepeatMode()
        val transaction = if (
            origin == ControllerSynchronizationOrigin.CROSSFADE_HANDOFF_INTERNAL
        ) {
            currentSong?.let { song ->
                LogicalNavigationPolicyTransactions.begin(song.id.toString())
            }
        } else {
            null
        }
        if (transaction != null) {
            CrossfadeTrace.log(
                "NAV_POLICY INTERNAL_BEGIN id=${transaction.id} " +
                        "requestedShuffle=$shuffleEnabled " +
                        "effectiveShuffle=$effectiveShuffleEnabled " +
                        "repeatMode=${navigationRepeatModeTraceValue(playerRepeatMode)}"
            )
        }

        try {
            transaction?.let { token ->
                LogicalNavigationPolicyTransactions.expectShuffleMode(
                    token,
                    effectiveShuffleEnabled
                )
            }
            playerController.shuffleModeEnabled = effectiveShuffleEnabled

            transaction?.let { token ->
                LogicalNavigationPolicyTransactions.expectRepeatMode(
                    token,
                    playerRepeatMode
                )
            }
            playerController.repeatMode = playerRepeatMode
        } finally {
            transaction?.let(LogicalNavigationPolicyTransactions::seal)
        }
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    internal fun updateUpcomingPlaylist(
        upcomingSongs: List<Song>,
        origin: ControllerSynchronizationOrigin =
            ControllerSynchronizationOrigin.EXTERNAL
    ) {
        val playerController = controller ?: return

        val currentIndex = playerController.currentMediaItemIndex

        if (currentIndex < 0) {
            return
        }

        val current = currentSong ?: return
        val requestedUpcomingIds = upcomingSongs.map { song -> song.id.toString() }
        val requestedTimelineIds = listOf(current.id.toString()) + requestedUpcomingIds
        currentPlaylist = listOf(current) + upcomingSongs
        if (currentIndex == 0 && timelineMediaIds() == requestedTimelineIds) return

        val reusableUpcomingEvidence = (0 until playerController.mediaItemCount)
            .filter { index -> index != currentIndex }
            .mapNotNull { index ->
                playerController.getMediaItemAt(index).listeningEvidence()
            }
            .toMutableList()
        pendingPublishedTimelineMediaIds = requestedTimelineIds
        val transaction = if (
            origin == ControllerSynchronizationOrigin.CROSSFADE_HANDOFF_INTERNAL
        ) {
            LogicalPlaylistMutationTransactions.begin(current.id.toString())
        } else {
            null
        }

        try {
            if (currentIndex > 0) {
                transaction?.let { token ->
                    LogicalPlaylistMutationTransactions.expectRemovePrefix(
                        token = token,
                        fromIndex = 0,
                        toIndex = currentIndex
                    )
                }
                playerController.removeMediaItems(0, currentIndex)
            }

            val existingUpcomingIds = (1 until playerController.mediaItemCount).map { index ->
                playerController.getMediaItemAt(index).mediaId
            }
            if (existingUpcomingIds == requestedUpcomingIds) {
                return
            }

            val upcomingMediaItems = upcomingSongs.map { song ->
                val referenceKey = song.membershipKey()
                val reusableIndex = reusableUpcomingEvidence.indexOfFirst { evidence ->
                    evidence.referenceKey == referenceKey
                }
                val entryId = if (reusableIndex >= 0) {
                    reusableUpcomingEvidence.removeAt(reusableIndex).itemInstanceId
                } else {
                    java.util.UUID.randomUUID().toString()
                }
                song.toPlayableMediaItem(itemInstanceId = entryId)
            }
            val replaceToIndex = playerController.mediaItemCount
            transaction?.let { token ->
                LogicalPlaylistMutationTransactions.expectReplaceUpcoming(
                    token = token,
                    fromIndex = 1,
                    toIndex = replaceToIndex,
                    mediaIds = requestedUpcomingIds
                )
            }

            playerController.replaceMediaItems(
                1,
                replaceToIndex,
                upcomingMediaItems
            )
        } catch (error: Exception) {
            pendingPublishedTimelineMediaIds = null
            throw error
        } finally {
            transaction?.let(LogicalPlaylistMutationTransactions::seal)
        }
    }

    private fun timelineMediaIds(): List<String> {
        val playerController = controller ?: return emptyList()
        return (0 until playerController.mediaItemCount).map { index ->
            playerController.getMediaItemAt(index).mediaId
        }
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
