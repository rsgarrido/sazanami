package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

class AndroidAutoSearchResolverTest {
    private val leftBehind = song(1, "Left Behind", "Slipknot", "Iowa", 8)
    private val peopleEquals = song(2, "People = Shit", "Slipknot", "Iowa", 1)
    private val warning = song(3, "S!CK", "The Warning", "Keep Me Fed", 2)
    private val catalog = AndroidAutoCatalogSnapshot(
        songs = listOf(leftBehind, peopleEquals, warning),
        playlists = listOf(
            AutoPlaylistEntry(
                playlistId = 44,
                name = "Heavy Rotation",
                songs = listOf(warning, leftBehind)
            )
        ),
        artistArtworkUris = emptyMap()
    )

    @Test
    fun `title by artist voice query resolves exact song`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "play Left Behind by Slipknot"),
            catalog
        )

        assertNotNull(match)
        assertEquals(leftBehind.id, match!!.selectedSong.id)
        assertEquals(listOf(peopleEquals.id, leftBehind.id), match.songs.map(Song::id))
    }

    @Test
    fun `structured artist query resolves artist library context`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(artist = "Slipknot"),
            catalog
        )

        assertNotNull(match)
        assertEquals(setOf(leftBehind.id, peopleEquals.id), match!!.songs.map(Song::id).toSet())
    }

    @Test
    fun `playlist voice query resolves playlist order`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(playlist = "Heavy Rotation"),
            catalog
        )

        assertEquals(listOf(warning.id, leftBehind.id), match!!.songs.map(Song::id))
        assertEquals(warning.id, match.selectedSong.id)
    }

    @Test
    fun `browser search ranks exact song title first`() {
        val results = AndroidAutoSearchResolver.searchSongs("Left Behind", catalog)

        assertEquals(leftBehind.id, results.first().id)
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        track: Int
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = track,
        duration = 240_000,
        uri = mock(Uri::class.java),
        filePath = "/music/$artist/$album/$title.flac",
        folderPath = "/music/$artist/$album",
        albumArtUri = null
    )
}
