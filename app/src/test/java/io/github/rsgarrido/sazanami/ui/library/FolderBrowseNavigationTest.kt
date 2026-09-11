package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import io.github.rsgarrido.sazanami.data.FolderId
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.buildFolderBrowseIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class FolderBrowseNavigationTest {
    @Test
    fun nestedSelectionRoundTripsThroughSaveableRepresentation() {
        val selected = FolderId(
            volumeName = "external_primary",
            normalizedPath = "music/artist/album"
        )

        assertEquals(selected, restoreFolderBrowseSelection(saveFolderBrowseSelection(selected)))
        assertNull(restoreFolderBrowseSelection(saveFolderBrowseSelection(null)))
    }

    @Test
    fun backMovesToParentThenFolderRootView() {
        val index = buildFolderBrowseIndex(
            listOf(song(relativePath = "Music/Artist/Album/"))
        )
        val album = id("music/artist/album")
        val artist = id("music/artist")
        val music = id("music")

        assertEquals(artist, folderBrowseBackDestination(index, album))
        assertEquals(music, folderBrowseBackDestination(index, artist))
        assertNull(folderBrowseBackDestination(index, music))
    }

    @Test
    fun removedSelectionFallsBackToNearestExistingAncestor() {
        val initial = buildFolderBrowseIndex(
            listOf(
                song(id = 1, relativePath = "Music/Artist/Retained/"),
                song(id = 2, relativePath = "Music/Artist/Removed/Album/")
            )
        )
        val removedAlbum = id("music/artist/removed/album")
        assertEquals(removedAlbum, resolveFolderBrowseSelection(initial, removedAlbum))

        val refreshed = buildFolderBrowseIndex(
            listOf(song(id = 1, relativePath = "Music/Artist/Retained/"))
        )

        assertEquals(id("music/artist"), resolveFolderBrowseSelection(refreshed, removedAlbum))
        assertNull(resolveFolderBrowseSelection(refreshed, id("audiobooks/missing")))
    }

    private fun id(path: String) = FolderId("external_primary", path)

    private fun song(
        id: Long = 1,
        relativePath: String
    ): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 1_000,
        uri = mock(Uri::class.java),
        filePath = "/storage/emulated/0/$relativePath/song-$id.flac",
        folderPath = "/storage/emulated/0/$relativePath",
        albumArtUri = null,
        volumeName = "external_primary",
        displayName = "song-$id.flac",
        relativePath = relativePath
    )
}
