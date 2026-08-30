package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.Song

class UpcomingPlaylistBuilder(
    private val shuffleSongs: (List<Song>) -> List<Song> = { songs -> songs.shuffled() }
) {

    fun buildUpcomingPlaylistAfterCurrent(
        startSong: Song,
        playbackSourceSongs: List<Song>,
        queuedSongsAfterCurrent: List<Song>,
        currentUpcomingSongs: List<Song>,
        shuffleMode: PlaybackShuffleMode,
        repeatMode: RepeatMode,
        preserveExistingShuffleOrder: Boolean
    ): List<Song> {
        val excludedSongIds = mutableSetOf<Long>()
        excludedSongIds.add(startSong.id)
        excludedSongIds.addAll(
            queuedSongsAfterCurrent.map { song ->
                song.id
            }
        )

        val startIndex = playbackSourceSongs.indexOfFirst { song ->
            song.id == startSong.id
        }
        var shouldCreateNewShuffleCycle = false

        val songsAfterCurrent = when {
            startIndex == -1 -> {
                getRemainingSongsFromExistingUpcoming(
                    startSong = startSong,
                    currentUpcomingSongs = currentUpcomingSongs
                )
            }

            preserveExistingShuffleOrder && shuffleMode.usesDynamicSongShuffle -> {
                val remainingExistingUpcomingSongs = getRemainingSongsFromExistingUpcoming(
                    startSong = startSong,
                    currentUpcomingSongs = currentUpcomingSongs
                )

                if (
                    remainingExistingUpcomingSongs.isEmpty() &&
                    repeatMode == RepeatMode.ALL &&
                    playbackSourceSongs.size > 1
                ) {
                    shouldCreateNewShuffleCycle = true
                    playbackSourceSongs.filter { song ->
                        song.id != startSong.id
                    }
                } else {
                    remainingExistingUpcomingSongs
                }
            }

            else -> {
                playbackSourceSongs.drop(startIndex + 1) + playbackSourceSongs.take(startIndex)
            }
        }

        val remainingContextSongs = songsAfterCurrent.filter { song ->
            song.id !in excludedSongIds
        }

        val shouldCreateNewShuffleOrder =
            shuffleMode.usesDynamicSongShuffle &&
                    startIndex != -1 &&
                    (
                            !preserveExistingShuffleOrder ||
                                    currentUpcomingSongs.isEmpty() && repeatMode == RepeatMode.ALL ||
                                    shouldCreateNewShuffleCycle
                            )

        val orderedRemainingSongs = if (shouldCreateNewShuffleOrder) {
            shuffleSongs(remainingContextSongs)
        } else {
            remainingContextSongs
        }

        return queuedSongsAfterCurrent + orderedRemainingSongs
    }

    private fun getRemainingSongsFromExistingUpcoming(
        startSong: Song,
        currentUpcomingSongs: List<Song>
    ): List<Song> {
        val currentSongIndexInUpcoming = currentUpcomingSongs.indexOfFirst { song ->
            song.id == startSong.id
        }

        return if (currentSongIndexInUpcoming == -1) {
            currentUpcomingSongs
        } else {
            currentUpcomingSongs.drop(currentSongIndexInUpcoming + 1)
        }
    }
}
