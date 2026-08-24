package com.example.cdplaya.ui.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaylistLazyListKeysTest {
    @Test
    fun folderAndPlaylistWithSameDatabaseIdHaveDistinctStableKeys() {
        val folderKey = playlistFolderLazyListKey(folderId = 1L)
        val playlistKey = playlistLazyListKey(playlistId = 1L)

        assertEquals("playlist-folder:1", folderKey)
        assertEquals("playlist:1", playlistKey)
        assertNotEquals(folderKey, playlistKey)
    }

    @Test
    fun staticPlaylistKeysCannotCollideWithDatabaseBackedKeys() {
        val keys = listOf(
            CREATE_PLAYLIST_LAZY_LIST_KEY,
            EMPTY_PLAYLISTS_LAZY_LIST_KEY,
            PLAYLIST_ROOT_FOLDER_LAZY_LIST_KEY,
            playlistFolderLazyListKey(folderId = 1L),
            playlistLazyListKey(playlistId = 1L)
        )

        assertEquals(keys.size, keys.toSet().size)
    }
}
