package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class LibrarySelectionResolutionTest {
    @Test
    fun `visible song order wins and hidden selections use deterministic fallback`() {
        val alpha = song(1, "Alpha", "/music/a", 1, null)
        val beta = song(2, "Beta", "/music/b", 1, null)
        val gamma = song(3, "Gamma", "/music/c", 1, null)
        val selected = setOf(alpha.membershipKey(), beta.membershipKey(), gamma.membershipKey())

        val resolved = resolveSelectedSongs(selected, listOf(gamma, alpha), listOf(beta, gamma, alpha))

        assertEquals(listOf(3L, 1L, 2L), resolved.map(Song::id))
    }

    @Test
    fun `selected albums follow current order and retain multi disc track order`() {
        val discSongs = listOf(
            song(4, "Album", "/music/Artist/Album/CD2", 2, 2),
            song(2, "Album", "/music/Artist/Album/CD1", 2, 1),
            song(3, "Album", "/music/Artist/Album/CD2", 1, 2),
            song(1, "Album", "/music/Artist/Album/CD1", 1, 1)
        )
        val multiDisc = buildLibraryAlbumGroups(discSongs).single()
        val other = buildLibraryAlbumGroups(
            listOf(song(9, "Other", "/music/Other", 1, null))
        ).single()

        val resolved = resolveSelectedAlbums(
            selectedKeys = setOf(multiDisc.key, other.key),
            displayedAlbums = listOf(other),
            fallbackAlbums = listOf(multiDisc, other)
        )

        assertTrue(multiDisc.key.startsWith("multi-disc:"))
        assertEquals(listOf(other.key, multiDisc.key), resolved.map(LibraryAlbumGroup::key))
        assertEquals(listOf(9L, 1L, 2L, 3L, 4L), resolved.flatMap { it.songs }.map(Song::id))
    }

    private fun song(
        id: Long,
        album: String,
        folder: String,
        track: Int,
        disc: Int?
    ): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = album,
            trackNumber = track,
            duration = 1_000L,
            uri = uri,
            filePath = "$folder/$id.flac",
            folderPath = folder,
            albumArtUri = null,
            albumArtist = "Artist",
            discNumber = disc,
            discTotal = disc?.let { 2 }
        )
    }
}
