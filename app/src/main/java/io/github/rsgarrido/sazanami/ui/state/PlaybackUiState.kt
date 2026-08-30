package io.github.rsgarrido.sazanami.ui.state

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode

/**
 * Structural playback state is deliberately separate from [PlaybackProgressUiState].
 * A 500 ms position tick must not invalidate the library, navigation, or app shell.
 */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queuedSongs: List<Song> = emptyList(),
    val upcomingSongs: List<Song> = emptyList(),
    val previousHistoryCount: Int = 0,
    val forwardHistoryCount: Int = 0,
    val previousPreviewSong: Song? = null,
    val nextPreviewSong: Song? = null
) {
    companion object {
        val Disconnected = PlaybackUiState()
    }
}

data class PlaybackProgressUiState(
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val bufferedPosition: Int = 0,
    val isSeeking: Boolean = false
) {
    companion object {
        val Empty = PlaybackProgressUiState()
    }
}

fun playbackUiState(
    isConnected: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    queuedSongs: Collection<Song>,
    upcomingSongs: Collection<Song>,
    previousHistoryCount: Int,
    forwardHistoryCount: Int,
    previousPreviewSong: Song?,
    nextPreviewSong: Song?
): PlaybackUiState = PlaybackUiState(
    isConnected = isConnected,
    currentSong = currentSong,
    isPlaying = isPlaying,
    isShuffleEnabled = isShuffleEnabled,
    repeatMode = repeatMode,
    queuedSongs = queuedSongs.toList(),
    upcomingSongs = upcomingSongs.toList(),
    previousHistoryCount = previousHistoryCount,
    forwardHistoryCount = forwardHistoryCount,
    previousPreviewSong = previousPreviewSong,
    nextPreviewSong = nextPreviewSong
)
