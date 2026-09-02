package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class UpcomingPlaylistBuilderTest {

    @Test
    fun authoritativeReconciliationDropsRemovedIdsBeforeShuffleRebuild() {
        val songs = (1L..7L).map(::song)
        val survivingSongs = songs.take(2)
        val baseSongs = baseOrderedActiveQueueSongs(
            timelineSongs = survivingSongs,
            timelineEntryIds = listOf("one", "two"),
            baseEntryIds = listOf("one", "two", "removed-3", "removed-4")
        )

        val shuffledUpcoming = UpcomingPlaylistBuilder { candidates -> candidates.reversed() }
            .buildUpcomingPlaylistAfterCurrent(
                startSong = survivingSongs.first(),
                playbackSourceSongs = baseSongs,
                queuedSongsAfterCurrent = emptyList(),
                currentUpcomingSongs = songs.drop(1),
                shuffleMode = PlaybackShuffleMode.SONGS,
                repeatMode = RepeatMode.OFF,
                preserveExistingShuffleOrder = false
            )

        assertEquals(listOf(1L, 2L), (listOf(survivingSongs.first()) + shuffledUpcoming).map(Song::id))
    }

    @Test
    fun authoritativeReconciliationRestoresBaseOrderWithoutChangingShuffledMembership() {
        val songs = (1L..5L).map(::song)
        val timelineSongs = listOf(songs[0], songs[3], songs[1], songs[4], songs[2])
        val timelineIds = listOf("one", "four", "two", "five", "three")

        val baseSongs = baseOrderedActiveQueueSongs(
            timelineSongs = timelineSongs,
            timelineEntryIds = timelineIds,
            baseEntryIds = listOf("one", "two", "three", "four", "five")
        )

        assertEquals((1L..5L).toList(), baseSongs.map(Song::id))
        assertEquals(timelineSongs.map(Song::id).toSet(), baseSongs.map(Song::id).toSet())
    }

    @Test
    fun preservingShuffleKeepsRemainingExistingOrder() {
        val builder = UpcomingPlaylistBuilder { songs -> songs.reversed() }
        val songA = song(1)
        val songB = song(2)
        val songC = song(3)
        val songD = song(4)

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            startSong = songC,
            playbackSourceSongs = listOf(songA, songB, songC, songD),
            queuedSongsAfterCurrent = emptyList(),
            currentUpcomingSongs = listOf(songC, songB, songD),
            shuffleMode = PlaybackShuffleMode.SONGS,
            repeatMode = RepeatMode.ALL,
            preserveExistingShuffleOrder = true
        )

        assertEquals(listOf(songB, songD), upcoming)
    }

    @Test
    fun exhaustedPreservedShuffleCreatesShuffledRepeatAllCycle() {
        val builder = UpcomingPlaylistBuilder { songs -> songs.reversed() }
        val songA = song(1)
        val songB = song(2)
        val songC = song(3)
        val songD = song(4)

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            startSong = songD,
            playbackSourceSongs = listOf(songA, songB, songC, songD),
            queuedSongsAfterCurrent = emptyList(),
            currentUpcomingSongs = listOf(songC, songB, songD),
            shuffleMode = PlaybackShuffleMode.SONGS,
            repeatMode = RepeatMode.ALL,
            preserveExistingShuffleOrder = true
        )

        assertEquals(listOf(songC, songB, songA), upcoming)
    }

    @Test
    fun explicitQueuePrecedesNormalUpcomingContext() {
        val builder = UpcomingPlaylistBuilder()
        val songs = (1L..5L).map(::song)

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            startSong = songs[1],
            playbackSourceSongs = songs.take(4),
            queuedSongsAfterCurrent = listOf(songs[4], songs[0]),
            currentUpcomingSongs = emptyList(),
            shuffleMode = PlaybackShuffleMode.OFF,
            repeatMode = RepeatMode.OFF,
            preserveExistingShuffleOrder = false
        )

        assertEquals(listOf(songs[4], songs[0], songs[2], songs[3]), upcoming)
    }

    @Test
    fun repeatOffUsesRemainingContextOnceAndRepeatAllWrapsOrder() {
        val builder = UpcomingPlaylistBuilder()
        val songs = (1L..4L).map(::song)

        val repeatOff = builder.buildUpcomingPlaylistAfterCurrent(
            songs[2], songs, emptyList(), emptyList(), PlaybackShuffleMode.OFF, RepeatMode.OFF, false
        )
        val repeatAll = builder.buildUpcomingPlaylistAfterCurrent(
            songs[2], songs, emptyList(), emptyList(), PlaybackShuffleMode.OFF, RepeatMode.ALL, false
        )
        val repeatOne = builder.buildUpcomingPlaylistAfterCurrent(
            songs[2], songs, emptyList(), emptyList(), PlaybackShuffleMode.OFF, RepeatMode.ONE, false
        )

        assertEquals(listOf(songs[3], songs[0], songs[1]), repeatOff)
        assertEquals(repeatOff, repeatAll)
        assertEquals(repeatOff, repeatOne)
    }

    @Test
    fun exhaustedMissingContextPreservesExistingUpcoming() {
        val builder = UpcomingPlaylistBuilder()
        val current = song(99)
        val existing = listOf(song(2), song(3))

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            current, listOf(song(1)), emptyList(), existing, PlaybackShuffleMode.OFF, RepeatMode.OFF, true
        )

        assertEquals(existing, upcoming)
    }

    @Test
    fun duplicateQueueEntriesArePreservedAndExcludedFromContextByOccurrence() {
        val builder = UpcomingPlaylistBuilder()
        val queued = song(2)

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            startSong = song(1),
            playbackSourceSongs = listOf(song(1), song(2), song(2), song(3)),
            queuedSongsAfterCurrent = listOf(queued, queued),
            currentUpcomingSongs = emptyList(),
            shuffleMode = PlaybackShuffleMode.OFF,
            repeatMode = RepeatMode.OFF,
            preserveExistingShuffleOrder = false
        )

        assertEquals(listOf(2L, 2L, 3L), upcoming.map { it.id })
    }

    @Test
    fun shuffleRebuildPreservesAdditionalInstancesOfTheCurrentSong() {
        val builder = UpcomingPlaylistBuilder { songs -> songs.reversed() }
        val duplicate = song(2)
        val source = listOf(song(1), duplicate, duplicate, song(3))

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            startSong = duplicate,
            playbackSourceSongs = source,
            queuedSongsAfterCurrent = emptyList(),
            currentUpcomingSongs = emptyList(),
            shuffleMode = PlaybackShuffleMode.SONGS,
            repeatMode = RepeatMode.OFF,
            preserveExistingShuffleOrder = false
        )

        assertEquals(listOf(2L, 1L, 3L, 2L), (listOf(duplicate) + upcoming).map(Song::id))
    }

    @Test
    fun albumShuffleModesPreserveProvidedGroupedOrder() {
        val builder = UpcomingPlaylistBuilder { songs -> songs.reversed() }
        val songs = (1L..4L).map(::song)

        val upcoming = builder.buildUpcomingPlaylistAfterCurrent(
            startSong = songs[0],
            playbackSourceSongs = songs,
            queuedSongsAfterCurrent = emptyList(),
            currentUpcomingSongs = emptyList(),
            shuffleMode = PlaybackShuffleMode.ALBUMS,
            repeatMode = RepeatMode.OFF,
            preserveExistingShuffleOrder = false
        )

        assertEquals(listOf(songs[1], songs[2], songs[3]), upcoming)
    }

    private fun song(id: Long): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            trackNumber = id.toInt(),
            duration = 180_000,
            uri = mock(Uri::class.java),
            filePath = "/music/$id.mp3",
            folderPath = "/music",
            albumArtUri = null
        )
    }
}
