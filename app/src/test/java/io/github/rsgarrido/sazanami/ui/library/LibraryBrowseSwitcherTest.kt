package io.github.rsgarrido.sazanami.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun overflowAffordancesTrackStartMiddleEndAndFullyFittingStates() {
        val start = libraryTabOverflowAffordances(
            hasMeasuredContent = true,
            canScrollBackward = false,
            canScrollForward = true
        )
        val middle = libraryTabOverflowAffordances(
            hasMeasuredContent = true,
            canScrollBackward = true,
            canScrollForward = true
        )
        val end = libraryTabOverflowAffordances(
            hasMeasuredContent = true,
            canScrollBackward = true,
            canScrollForward = false
        )
        val fullyFitting = libraryTabOverflowAffordances(
            hasMeasuredContent = true,
            canScrollBackward = false,
            canScrollForward = false
        )
        val notMeasured = libraryTabOverflowAffordances(
            hasMeasuredContent = false,
            canScrollBackward = true,
            canScrollForward = true
        )

        assertFalse(start.showStart)
        assertTrue(start.showEnd)
        assertTrue(middle.showStart)
        assertTrue(middle.showEnd)
        assertTrue(end.showStart)
        assertFalse(end.showEnd)
        assertFalse(fullyFitting.showStart)
        assertFalse(fullyFitting.showEnd)
        assertFalse(notMeasured.showStart)
        assertFalse(notMeasured.showEnd)
    }

    @Test
    fun primaryLibraryMotionFollowsPrimaryTabOrder() {
        assertEquals(
            LibraryContentMotion(1, LibraryContentMotionLevel.PRIMARY),
            libraryContentMotion(LibraryTab.SONGS, LibraryTab.PLAYLISTS)
        )
        assertEquals(
            LibraryContentMotion(-1, LibraryContentMotionLevel.PRIMARY),
            libraryContentMotion(LibraryTab.GENRES, LibraryTab.ALBUMS)
        )
    }

    @Test
    fun songCollectionMotionIsLighterAndFollowsFilterOrder() {
        assertEquals(
            LibraryContentMotion(1, LibraryContentMotionLevel.SECONDARY),
            libraryContentMotion(LibraryTab.SONGS, LibraryTab.RECENTLY_PLAYED)
        )
        assertEquals(
            LibraryContentMotion(-1, LibraryContentMotionLevel.SECONDARY),
            libraryContentMotion(LibraryTab.MOST_PLAYED, LibraryTab.FAVORITES)
        )
    }

    @Test
    fun crossingFromSongCollectionToAnotherPrimaryUsesPrimaryMotion() {
        assertEquals(
            LibraryContentMotion(1, LibraryContentMotionLevel.PRIMARY),
            libraryContentMotion(LibraryTab.RATED, LibraryTab.ARTISTS)
        )
        assertNull(libraryContentMotion(LibraryTab.QUEUE, LibraryTab.SONGS))
    }
}
