package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.Song

class PlaybackNavigationHistory {
    private val previousSongs = mutableListOf<Song>()
    private val nextSongs = mutableListOf<Song>()

    fun addPreviousSong(song: Song) {
        if (previousSongs.lastOrNull()?.id == song.id) {
            return
        }

        previousSongs.add(song)
    }

    fun clearForwardHistory() {
        nextSongs.clear()
    }

    fun clearAll() {
        previousSongs.clear()
        nextSongs.clear()
    }

    fun removeInvalidSongs(validSongIds: Set<Long>) {
        previousSongs.removeAll { song ->
            song.id !in validSongIds
        }

        nextSongs.removeAll { song ->
            song.id !in validSongIds
        }
    }

    fun getPreviousSongIds(): List<Long> {
        return previousSongs.map { song ->
            song.id
        }
    }

    fun getNextSongIds(): List<Long> {
        return nextSongs.map { song ->
            song.id
        }
    }

    fun getPreviousSongs(): List<Song> = previousSongs.toList()

    fun getNextSongs(): List<Song> = nextSongs.toList()

    fun peekPreviousSong(): Song? {
        return previousSongs.lastOrNull()
    }

    fun peekNextSong(): Song? {
        return nextSongs.lastOrNull()
    }

    fun replacePreviousSongs(songs: List<Song>) {
        previousSongs.clear()
        previousSongs.addAll(songs)
    }

    fun replaceNextSongs(songs: List<Song>) {
        nextSongs.clear()
        nextSongs.addAll(songs)
    }

    fun popNextSong(): Song? {
        if (nextSongs.isEmpty()) {
            return null
        }

        return nextSongs.removeAt(nextSongs.lastIndex)
    }

    fun popPreviousSongAndPushCurrent(currentSong: Song?): Song? {
        if (previousSongs.isEmpty()) {
            return null
        }

        if (currentSong != null) {
            nextSongs.add(currentSong)
        }

        return previousSongs.removeAt(previousSongs.lastIndex)
    }
}
