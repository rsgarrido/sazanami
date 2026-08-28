package com.example.cdplaya.ui.library

import android.net.Uri
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.filterSongsByAlbumSearch
import com.example.cdplaya.ui.sortSongsByAlbumOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MultiDiscAlbumHandlingTest {
    @Test
    fun `single folder album keeps its existing folder key`() {
        val folder = "/music/Artist/Album"
        val album = buildLibraryAlbumGroups(
            listOf(song(1, folder, track = 1, disc = null))
        ).single()

        assertEquals(folder, album.key)
        assertEquals(setOf(folder), album.folderPaths)
    }

    @Test
    fun `sibling disc folders merge into one ordered album`() {
        val songs = listOf(
            song(3, "/music/Artist/Album/CD2", track = 1, disc = 2),
            song(1, "/music/Artist/Album/CD1", track = 1, disc = 1),
            song(4, "/music/Artist/Album/CD2", track = 2, disc = 2),
            song(2, "/music/Artist/Album/CD1", track = 2, disc = 1)
        )

        val album = buildLibraryAlbumGroups(songs).single()

        assertTrue(album.key.startsWith("multi-disc:"))
        assertEquals(setOf("/music/Artist/Album/CD1", "/music/Artist/Album/CD2"), album.folderPaths)
        assertEquals(listOf(1L, 2L, 3L, 4L), album.songs.map(Song::id))
        assertEquals(listOf(1, 2), buildLibraryAlbumDiscSections(album.songs).map { it.discNumber })
        assertEquals(listOf(1L, 2L, 3L, 4L), album.metadataEditingSongs().map(Song::id))
        assertTrue(isAlbumGroupAvailable(album.key, songs))
    }

    @Test
    fun `same folder multi-disc album sorts by disc then track`() {
        val folder = "/music/Artist/Album"
        val songs = listOf(
            song(4, folder, track = 2, disc = 2),
            song(2, folder, track = 2, disc = 1),
            song(3, folder, track = 1, disc = 2),
            song(1, folder, track = 1, disc = 1)
        )

        val album = buildLibraryAlbumGroups(songs).single()

        assertEquals(folder, album.key)
        assertEquals(listOf(1L, 2L, 3L, 4L), sortSongsByAlbumOrder(songs).map(Song::id))
        assertEquals(listOf(1, 2), buildLibraryAlbumDiscSections(album.songs).map { it.discNumber })
    }

    @Test
    fun `MediaStore encoded disc track numbers remain supported`() {
        val songs = listOf(
            song(2, "/music/Artist/Album/CD2", track = 2_001, disc = null),
            song(1, "/music/Artist/Album/CD1", track = 1_001, disc = null)
        )

        val album = buildLibraryAlbumGroups(songs).single()

        assertEquals(listOf(1L, 2L), album.songs.map(Song::id))
        assertEquals(listOf(1, 2), buildLibraryAlbumDiscSections(album.songs).map { it.discNumber })
    }

    @Test
    fun `album search keeps every disc when one track matches`() {
        val songs = listOf(
            song(1, "/music/Artist/Album/CD1", track = 1, disc = 1, artist = "Guest"),
            song(2, "/music/Artist/Album/CD2", track = 1, disc = 2, artist = "Artist")
        )

        assertEquals(
            listOf(1L, 2L),
            filterSongsByAlbumSearch(songs, "Guest").map(Song::id)
        )
    }

    @Test
    fun `same title in unrelated parents does not merge`() {
        val groups = buildLibraryAlbumGroups(
            listOf(
                song(1, "/music/Artist/Album/CD1", track = 1, disc = 1),
                song(2, "/other/Artist/Album/CD2", track = 1, disc = 2)
            )
        )

        assertEquals(2, groups.size)
        assertFalse(groups.any { it.key.startsWith("multi-disc:") })
    }

    @Test
    fun `conflicting artist evidence blocks sibling-folder merge`() {
        val groups = buildLibraryAlbumGroups(
            listOf(
                song(
                    1,
                    "/music/Artist/Album/CD1",
                    track = 1,
                    disc = 1,
                    artist = "Artist One",
                    albumArtist = "Artist One"
                ),
                song(
                    2,
                    "/music/Artist/Album/CD2",
                    track = 1,
                    disc = 2,
                    artist = "Artist Two",
                    albumArtist = "Artist Two"
                )
            )
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun `duplicate sibling disc numbers stay as separate albums`() {
        val groups = buildLibraryAlbumGroups(
            listOf(
                song(1, "/music/Artist/Album/CD1-A", track = 1, disc = 1),
                song(2, "/music/Artist/Album/CD1-B", track = 2, disc = 1)
            )
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun `missing song disc inherits only unambiguous folder disc for ordering`() {
        val songs = listOf(
            song(2, "/music/Artist/Album/CD1", track = 2, disc = null),
            song(1, "/music/Artist/Album/CD1", track = 1, disc = 1),
            song(3, "/music/Artist/Album/CD2", track = 1, disc = 2)
        )

        val album = buildLibraryAlbumGroups(songs).single()

        assertEquals(listOf(1L, 2L, 3L), album.songs.map(Song::id))
        assertEquals(
            listOf(listOf(1L, 2L), listOf(3L)),
            buildLibraryAlbumDiscSections(album.songs).map { section -> section.songs.map(Song::id) }
        )
    }

    private fun song(
        id: Long,
        folderPath: String,
        track: Int,
        disc: Int?,
        artist: String = "Artist",
        albumArtist: String = "Artist",
        album: String = "Album"
    ): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$id")
        return Song(
            id = id,
            title = "Track $id",
            artist = artist,
            album = album,
            trackNumber = track,
            duration = 180_000L,
            uri = uri,
            filePath = "$folderPath/$id.flac",
            folderPath = folderPath,
            albumArtUri = null,
            albumArtist = albumArtist,
            discNumber = disc,
            discTotal = 2
        )
    }
}
