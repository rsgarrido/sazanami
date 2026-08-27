package com.example.cdplaya.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryBrowseSwitcherTest {
    @Test
    fun songCollectionsMapToSongsPrimaryCategory() {
        assertEquals(LibraryTab.SONGS, LibraryTab.SONGS.primaryBrowseTab())
        assertEquals(LibraryTab.SONGS, LibraryTab.FAVORITES.primaryBrowseTab())
        assertEquals(LibraryTab.SONGS, LibraryTab.RECENTLY_PLAYED.primaryBrowseTab())
        assertEquals(LibraryTab.SONGS, LibraryTab.MOST_PLAYED.primaryBrowseTab())
    }

    @Test
    fun primaryCategoriesRemainSelected() {
        assertEquals(LibraryTab.ALBUMS, LibraryTab.ALBUMS.primaryBrowseTab())
        assertEquals(LibraryTab.ARTISTS, LibraryTab.ARTISTS.primaryBrowseTab())
        assertEquals(LibraryTab.GENRES, LibraryTab.GENRES.primaryBrowseTab())
        assertEquals(LibraryTab.PLAYLISTS, LibraryTab.PLAYLISTS.primaryBrowseTab())
    }

    @Test
    fun queueDoesNotAppearInLibraryCategorySwitcher() {
        assertNull(LibraryTab.QUEUE.primaryBrowseTab())
    }
}
