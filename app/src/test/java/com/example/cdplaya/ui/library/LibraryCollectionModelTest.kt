package com.example.cdplaya.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionModelTest {
    @Test
    fun ratedIsASecondarySongsCollection() {
        assertTrue(LibraryTab.RATED in songCollectionTabs)
        assertEquals(LibraryTab.SONGS, LibraryTab.RATED.primaryBrowseTab())
    }

    @Test
    fun playlistsHaveTheirOwnPersistedViewCategory() {
        assertEquals(LibraryViewCategory.PLAYLISTS, LibraryTab.PLAYLISTS.viewCategory())
        assertEquals(LibraryViewMode.LIST, LibraryViewMode.fromStorageValue("missing"))
        assertEquals(LibraryViewMode.GRID, LibraryViewMode.fromStorageValue("grid"))
    }
}
