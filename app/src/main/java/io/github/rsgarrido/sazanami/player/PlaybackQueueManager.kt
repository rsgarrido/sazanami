package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.Song

class PlaybackQueueManager {
    internal val playbackQueue = mutableListOf<Song>()

    fun addSongToQueue(song: Song) {
        playbackQueue.add(song)
    }

    fun addSongToPlayNext(song: Song) {
        playbackQueue.add(0, song)
    }

    fun removeSongFromQueue(index: Int): Boolean {
        if (index !in playbackQueue.indices) {
            return false
        }

        playbackQueue.removeAt(index)
        return true
    }

    fun moveQueuedSongUp(index: Int): Boolean {
        if (index <= 0 || index !in playbackQueue.indices) {
            return false
        }

        val song = playbackQueue.removeAt(index)
        playbackQueue.add(index - 1, song)
        return true
    }

    fun moveQueuedSongDown(index: Int): Boolean {
        if (index < 0 || index >= playbackQueue.lastIndex) {
            return false
        }

        val song = playbackQueue.removeAt(index)
        playbackQueue.add(index + 1, song)
        return true
    }

    fun clearQueue(): Boolean {
        if (playbackQueue.isEmpty()) {
            return false
        }

        playbackQueue.clear()
        return true
    }

    fun addSongsToPlayNext(songs: List<Song>): Boolean {
        if (songs.isEmpty()) {
            return false
        }

        playbackQueue.addAll(0, songs)
        return true
    }

    fun addSongsToQueue(songs: List<Song>): Boolean {
        if (songs.isEmpty()) {
            return false
        }

        playbackQueue.addAll(songs)
        return true
    }

    fun removeFirstMatchingSongsFromQueue(songs: List<Song>): Boolean {
        var removedAnySong = false

        songs.forEach { song ->
            val index = playbackQueue.indexOfFirst { queuedSong ->
                queuedSong.id == song.id
            }

            if (index != -1) {
                playbackQueue.removeAt(index)
                removedAnySong = true
            }
        }

        return removedAnySong
    }

    fun removeLastMatchingSongsFromQueue(songs: List<Song>): Boolean {
        var removedAnySong = false

        songs.asReversed().forEach { song ->
            for (index in playbackQueue.lastIndex downTo 0) {
                if (playbackQueue[index].id == song.id) {
                    playbackQueue.removeAt(index)
                    removedAnySong = true
                    break
                }
            }
        }

        return removedAnySong
    }

    fun removeLastMatchingSongFromQueue(song: Song): Boolean {
        for (index in playbackQueue.lastIndex downTo 0) {
            if (playbackQueue[index].id == song.id) {
                playbackQueue.removeAt(index)
                return true
            }
        }

        return false
    }

    fun removeFirstMatchingSongFromQueue(song: Song): Boolean {
        val index = playbackQueue.indexOfFirst { queuedSong ->
            queuedSong.id == song.id
        }

        if (index == -1) {
            return false
        }

        playbackQueue.removeAt(index)
        return true
    }

    fun removeInvalidSongs(validSongIds: Set<Long>): Boolean {
        val originalSize = playbackQueue.size

        playbackQueue.removeAll { queuedSong ->
            queuedSong.id !in validSongIds
        }

        return playbackQueue.size != originalSize
    }

    fun removeNextQueuedSong(): Song? {
        if (playbackQueue.isEmpty()) {
            return null
        }

        return playbackQueue.removeAt(0)
    }

    fun replaceQueue(songs: List<Song>) {
        playbackQueue.clear()
        playbackQueue.addAll(songs)
    }

    fun getQueuedSongIds(): List<Long> {
        return playbackQueue.map { song ->
            song.id
        }
    }

    fun getQueuedSongCountExcludingCurrent(currentSongId: Long?): Int {
        return playbackQueue.size
    }

    fun getQueuedSongsAfterCurrent(currentSongId: Long): List<Song> {
        return playbackQueue.toList()
    }
}
