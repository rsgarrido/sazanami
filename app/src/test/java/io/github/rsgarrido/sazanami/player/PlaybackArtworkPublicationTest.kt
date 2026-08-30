package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackArtworkPublicationTest {
    @Test
    fun repairedCurrentSongUpdatesArtworkWithoutRestartingPlayback() {
        val repairedArtwork = mock(Uri::class.java)
        val current = song(albumArtUri = null)
        val repaired = song(albumArtUri = repairedArtwork)

        val replacement = replacementSong(current, listOf(repaired))

        assertSame(repaired, replacement)
        assertSame(repairedArtwork, replacement?.albumArtUri)
    }

    @Test
    fun repairedReferencesKeepQueueOrderAndDuplicates() {
        val repaired = song(albumArtUri = mock(Uri::class.java))

        val result = replaceSongReferences(listOf(song(null), song(null)), listOf(repaired))

        assertEquals(listOf(repaired, repaired), result)
    }

    private fun song(albumArtUri: Uri?) = Song(
        id = 1L,
        title = "Track",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 100L,
        uri = mock(Uri::class.java),
        filePath = "/music/track.flac",
        folderPath = "/music",
        albumArtUri = albumArtUri,
        volumeName = "external_primary",
        artworkEnrichmentVersion = 1
    )
}
