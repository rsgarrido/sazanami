package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

class AndroidAutoBrowseTreeTest {
    @Test
    fun `empty cold catalog still exposes browsable root and categories`() {
        val root = AndroidAutoCatalogSnapshot.EMPTY.browseTree("Sazanami")
        assertEquals(true, root.isBrowsable)
        assertEquals(listOf(PLAYLISTS_ID, ALBUMS_ID, ARTISTS_ID, SONGS_ID), root.children.map { it.id })
        root.children.forEach {
            assertEquals(true, it.isBrowsable)
            assertEquals(false, it.isPlayable)
            assertEquals(emptyList<AutoBrowseNode>(), it.children)
        }
    }

    @Test
    fun `duplicate playlist tracks have distinct selectable browse ids`() {
        val repeated = song(1, "Repeated", "Artist", "Album", "/music")
        val root = buildAndroidAutoBrowseTree(listOf(repeated), "Sazanami",
            listOf(AutoPlaylistEntry(7, "Duplicates", listOf(repeated, repeated))))
        val entries = root.findNode("playlist:7")!!.children
        assertNotEquals(entries[0].id, entries[1].id)
        assertEquals(entries[1], root.findNode(entries[1].id))
        assertEquals("playlist:7", root.findParent(entries[1].id)!!.id)
    }

    @Test
    fun `builds playlist album artist grids and a song list`() {
        val included = song(1, "Included", "Artist B", "Album", "/selected/album")
        val otherArtist = song(2, "First", "Artist A", "Other", "/selected/other")
        val playlistArtwork = mock(Uri::class.java)

        val root = buildAndroidAutoBrowseTree(
            songs = listOf(included, otherArtist),
            rootTitle = "Sazanami",
            playlists = listOf(
                AutoPlaylistEntry(
                    playlistId = 7,
                    name = "Road Trip",
                    songs = listOf(otherArtist, included),
                    artworkUri = playlistArtwork
                )
            )
        )

        assertEquals("Sazanami", root.title)
        assertEquals(
            listOf("Playlists", "Albums", "Artists", "Songs"),
            root.children.map { it.title }
        )
        assertEquals(AutoBrowseContentStyle.GRID, root.findNode(PLAYLISTS_ID)?.browsableChildrenStyle)
        assertEquals(AutoBrowseContentStyle.GRID, root.findNode(ALBUMS_ID)?.browsableChildrenStyle)
        assertEquals(AutoBrowseContentStyle.GRID, root.findNode(ARTISTS_ID)?.browsableChildrenStyle)
        assertEquals(AutoBrowseContentStyle.LIST, root.findNode(SONGS_ID)?.playableChildrenStyle)
        assertEquals(listOf("Artist A", "Artist B"), root.findNode(ARTISTS_ID)?.children?.map { it.title })
        assertEquals(2, root.findNode(SONGS_ID)?.children?.size)
        assertEquals(playlistArtwork, root.findNode(PLAYLISTS_ID)?.children?.single()?.artworkUri)
        assertEquals(null, root.findNode("song:songs:99"))
    }

    @Test
    fun `playable song retains its grouping context`() {
        val first = song(1, "One", "Artist", "Album", "/music/album")
        val second = song(2, "Two", "Artist", "Album", "/music/album")
        val root = buildAndroidAutoBrowseTree(
            songs = listOf(first, second),
            rootTitle = "Sazanami"
        )
        val artist = root.findNode(ARTISTS_ID)!!.children.single()
        val selectedId = artist.children.last().id

        assertNotNull(root.findNode(selectedId)?.song)
        assertEquals(listOf(first, second), root.findParent(selectedId)?.children?.map { it.song })
    }

    @Test
    fun `album ids are stable but do not expose folder paths`() {
        val song = song(1, "One", "Artist", "Album", "/private/music/album")
        val first = buildAndroidAutoBrowseTree(listOf(song), "Sazanami")
            .findNode(ALBUMS_ID)!!.children.single().id
        val second = buildAndroidAutoBrowseTree(listOf(song), "Sazanami")
            .findNode(ALBUMS_ID)!!.children.single().id

        assertEquals(first, second)
        assertNotEquals("album:/private/music/album", first)
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        folder: String
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = id.toInt(),
        duration = 1_000,
        uri = mock(Uri::class.java),
        filePath = "$folder/$title.mp3",
        folderPath = folder,
        albumArtUri = null
    )
}
