package io.github.rsgarrido.sazanami.ui.player.modern

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class ModernExpandedPlayerWaveformTest {
    @Test
    fun nearbyWaveformSongs_selectsNextThenPrevious() {
        val current = song(1L)
        val next = song(2L)
        val previous = song(3L)

        assertEquals(
            listOf(next, previous),
            selectNearbyWaveformSongs(current, next, previous)
        )
    }

    @Test
    fun nearbyWaveformSongs_excludesCurrentAndDuplicates() {
        val current = song(1L)
        val nearby = song(2L)

        assertEquals(
            listOf(nearby),
            selectNearbyWaveformSongs(current, nearby, nearby)
        )
        assertEquals(
            emptyList<Song>(),
            selectNearbyWaveformSongs(current, current, current)
        )
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 180_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/song-$id.flac",
        folderPath = "/music",
        albumArtUri = null
    )
}
