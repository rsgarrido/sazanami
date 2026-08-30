package io.github.rsgarrido.sazanami.ui.state

import android.net.Uri
import io.github.rsgarrido.sazanami.data.LibraryFolder
import io.github.rsgarrido.sazanami.data.LibraryRefreshResult
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class UiStateContractsTest {
    @Test
    fun defaultStates_areValidAndMatchExistingBehavior() {
        assertEquals(emptyList<Song>(), LibraryUiState.Empty.songs)
        assertFalse(PlaybackUiState.Disconnected.isConnected)
        assertEquals(RepeatMode.OFF, PlaybackUiState.Disconnected.repeatMode)
        assertEquals(0, PlaybackProgressUiState.Empty.currentPosition)
        assertFalse(PlayerAppearanceUiState().isLoaded)
        assertFalse(LibraryAppearanceUiState().isLoaded)
        assertEquals("No sleep timer", SleepTimerUiState.Inactive.displayText())
        assertNull(LibraryUiState.Empty.errorMessage)
    }

    @Test
    fun libraryMapping_preservesOrderingDuplicatesAndDefensivelyCopiesSources() {
        val first = song(1)
        val second = song(2)
        val songs = mutableListOf(first, second, first)
        val folders = mutableListOf(LibraryFolder("Music", "Music", 3))
        val selected = mutableSetOf("Music")

        val state = libraryUiState(
            songs = songs,
            folders = folders,
            selectedFolders = selected,
            lastRefreshResult = LibraryRefreshResult(
                songs = songs,
                updatedCount = 2,
                artworkRepairCount = 1
            ),
            unresolvedFavoriteCount = 3,
            unresolvedPlaylistRowCount = 4,
            unresolvedListeningHistoryCount = 5
        )
        songs.clear()
        folders.clear()
        selected.clear()

        assertEquals(listOf(first, second, first), state.songs)
        assertEquals(listOf(LibraryFolder("Music", "Music", 3)), state.folders)
        assertEquals(setOf("Music"), state.selectedFolders)
        assertEquals(3, state.unresolvedFavoriteCount)
        assertEquals(4, state.unresolvedPlaylistRowCount)
        assertEquals(5, state.unresolvedListeningHistoryCount)
        assertEquals(2, state.lastRefreshSummary?.updatedCount)
        assertEquals(1, state.lastRefreshSummary?.artworkRepairCount)
    }

    @Test
    fun progressChanges_doNotAlterStructuralPlaybackState() {
        val structural = playbackUiState(
            isConnected = true,
            currentSong = song(1),
            isPlaying = true,
            isShuffleEnabled = false,
            repeatMode = RepeatMode.ALL,
            queuedSongs = listOf(song(2), song(2)),
            upcomingSongs = listOf(song(3)),
            previousHistoryCount = 1,
            forwardHistoryCount = 0,
            previousPreviewSong = null,
            nextPreviewSong = song(2)
        )

        val before = PlaybackProgressUiState(currentPosition = 10, duration = 100)
        val after = before.copy(currentPosition = 11)

        assertEquals(structural, structural.copy())
        assertEquals(listOf(2L, 2L), structural.queuedSongs.map(Song::id))
        assertEquals(1, after.currentPosition - before.currentPosition)
    }

    private fun song(id: Long): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 100L,
        uri = mock(Uri::class.java),
        filePath = "/music/$id.mp3",
        folderPath = "/music",
        albumArtUri = null
    )
}
