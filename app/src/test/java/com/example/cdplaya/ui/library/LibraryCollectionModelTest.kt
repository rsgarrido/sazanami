package com.example.cdplaya.ui.library

import com.example.cdplaya.ui.state.LibraryAppearanceUiState
import com.example.cdplaya.ui.state.LibraryCategoryAppearance
import com.example.cdplaya.ui.state.gridColumnCountFor
import com.example.cdplaya.ui.state.modeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionModelTest {
    @Test
    fun ratedIsASecondarySongsCollection() {
        assertTrue(LibraryTab.RATED in songCollectionTabs)
        assertEquals(LibraryTab.SONGS, LibraryTab.RATED.primaryBrowseTab())
    }

    @Test
    fun songCollectionsShareTheSongsViewPreference() {
        assertEquals(LibraryViewCategory.SONGS, LibraryTab.SONGS.viewCategory())
        assertEquals(LibraryViewCategory.SONGS, LibraryTab.FAVORITES.viewCategory())
        assertEquals(LibraryViewCategory.SONGS, LibraryTab.RATED.viewCategory())
        assertEquals(LibraryViewCategory.SONGS, LibraryTab.RECENTLY_ADDED.viewCategory())

        val appearance = LibraryAppearanceUiState(
            songs = LibraryCategoryAppearance(
                viewMode = LibraryViewMode.GRID,
                gridColumnCount = 3
            )
        )
        listOf(
            LibraryTab.SONGS,
            LibraryTab.FAVORITES,
            LibraryTab.RATED,
            LibraryTab.RECENTLY_ADDED
        ).forEach { tab ->
            assertEquals(LibraryViewMode.GRID, appearance.modeFor(tab))
            assertEquals(3, appearance.gridColumnCountFor(tab))
        }
    }

    @Test
    fun ratingControlsAndSortsAreCollectionAware() {
        assertFalse(LibraryTab.SONGS.showsQuickRateAction())
        assertTrue(LibraryTab.RATED.showsQuickRateAction())
        assertFalse(LibraryTab.FAVORITES.showsQuickRateAction())

        val allOptions = librarySortOptionsFor(LibraryTab.SONGS)
        val ratedOptions = librarySortOptionsFor(LibraryTab.RATED)
        val favoriteOptions = librarySortOptionsFor(LibraryTab.FAVORITES)
        assertFalse(LibrarySortOption.RATING in allOptions)
        assertTrue(LibrarySortOption.RATING in ratedOptions)
        assertFalse(LibrarySortOption.DATE_ADDED in allOptions)
        assertTrue(LibrarySortOption.YEAR in allOptions)
        assertFalse(LibrarySortOption.DATE_ADDED in ratedOptions)
        assertFalse(LibrarySortOption.YEAR in ratedOptions)
        assertFalse(LibrarySortOption.DATE_ADDED in favoriteOptions)
        assertEquals(
            listOf(LibrarySortOption.DATE_ADDED),
            librarySortOptionsFor(LibraryTab.RECENTLY_ADDED)
        )
        assertEquals(
            "Date added",
            LibrarySortOption.DATE_ADDED.displayTitleFor(LibraryTab.RECENTLY_ADDED)
        )
    }

    @Test
    fun genresAreAPrimaryListOnlyCategoryWithoutSortControls() {
        assertEquals(
            listOf(
                LibraryTab.SONGS,
                LibraryTab.ALBUMS,
                LibraryTab.ARTISTS,
                LibraryTab.PLAYLISTS,
                LibraryTab.GENRES
            ),
            primaryLibraryTabs
        )
        assertEquals(LibraryTab.GENRES, LibraryTab.GENRES.primaryBrowseTab())
        assertEquals(null, LibraryTab.GENRES.viewCategory())
        assertTrue(librarySortOptionsFor(LibraryTab.GENRES).isEmpty())
    }

    @Test
    fun sortChangeResetTrackerSkipsInitialAndRestoredStateButDetectsRealChanges() {
        val initial = LibrarySortState(
            LibrarySortOption.TITLE,
            LibrarySortDirection.ASCENDING
        )
        val tracker = SortChangeResetTracker(initial)

        assertFalse(tracker.shouldReset(initial))
        assertTrue(
            tracker.shouldReset(initial.copy(direction = LibrarySortDirection.DESCENDING))
        )
        assertTrue(
            tracker.shouldReset(
                LibrarySortState(
                    LibrarySortOption.ARTIST,
                    LibrarySortDirection.DESCENDING
                )
            )
        )

        val restoredState = LibrarySortState(
            LibrarySortOption.ARTIST,
            LibrarySortDirection.DESCENDING
        )
        assertFalse(SortChangeResetTracker(restoredState).shouldReset(restoredState))
    }

    @Test
    fun changingDirectionDoesNotChangeTheSelectedSortField() {
        val titleAscending = LibrarySortState(
            option = LibrarySortOption.TITLE,
            direction = LibrarySortDirection.ASCENDING
        )

        assertEquals(
            LibrarySortState(
                option = LibrarySortOption.TITLE,
                direction = LibrarySortDirection.DESCENDING
            ),
            titleAscending.toggleDirection()
        )
        assertEquals(
            LibrarySortDirection.ASCENDING,
            titleAscending.select(LibrarySortOption.ARTIST).direction
        )
    }

    @Test
    fun playlistGridUsesAResponsiveViewChoice() {
        assertEquals(
            listOf(LibraryViewOption.LIST, LibraryViewOption.GRID_2),
            libraryViewOptions(adaptiveGrid = true)
        )
        assertEquals("Grid (responsive)", LibraryViewOption.GRID_2.displayLabel(true))
    }

    @Test
    fun playlistsHaveTheirOwnPersistedViewCategory() {
        assertEquals(LibraryViewCategory.PLAYLISTS, LibraryTab.PLAYLISTS.viewCategory())
        assertEquals(LibraryViewMode.LIST, LibraryViewMode.fromStorageValue("missing"))
        assertEquals(LibraryViewMode.GRID, LibraryViewMode.fromStorageValue("grid"))
    }
}
