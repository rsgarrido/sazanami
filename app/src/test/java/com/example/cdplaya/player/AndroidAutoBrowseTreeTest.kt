package com.example.cdplaya.player

import android.net.Uri
import com.example.cdplaya.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

class AndroidAutoBrowseTreeTest {
    @Test
    fun `builds artists albums and songs from supplied filtered songs only`() {
        val included = song(1, "Included", "Artist B", "Album", "/selected/album")
        val otherArtist = song(2, "First", "Artist A", "Other", "/selected/other")

        val root = buildAndroidAutoBrowseTree(
            songs = listOf(included, otherArtist),
            rootTitle = "Sazanami"
        )

        assertEquals("Sazanami", root.title)
        assertEquals(listOf("Artists", "Albums", "Songs"), root.children.map { it.title })
        assertEquals(listOf("Artist A", "Artist B"), root.findNode(ARTISTS_ID)?.children?.map { it.title })
        assertEquals(2, root.findNode(SONGS_ID)?.children?.size)
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
