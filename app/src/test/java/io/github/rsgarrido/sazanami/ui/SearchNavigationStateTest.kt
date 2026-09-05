package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.mutableStateOf
import io.github.rsgarrido.sazanami.ui.library.*
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.navigation.PlaybackLaunchContext
import org.junit.Assert.*
import org.junit.Test

class SearchNavigationStateTest {
    @Test fun searchOriginReturnsToSearchWithQueryAndCategoryForEveryEntity() {
        SearchCategory.entries.forEach { category ->
            val state = navigation(MainDestination.SEARCH)
            state.searchCategory.value = category
            state.openAlbum("album-key")
            assertEquals(LibraryTab.ALBUMS, state.selectedLibraryTab.value)
            state.closeAlbum()
            assertNull(state.selectedAlbumKey.value)
            assertSearchPreserved(state, category)

            state.openArtist("The Warning")
            assertEquals(LibraryTab.ARTISTS, state.selectedLibraryTab.value)
            state.closeArtist()
            assertNull(state.selectedArtistName.value)
            assertSearchPreserved(state, category)

            state.openPlaylist(42)
            assertEquals(LibraryTab.PLAYLISTS, state.selectedLibraryTab.value)
            state.closePlaylist()
            assertNull(state.selectedPlaylistId.value)
            assertSearchPreserved(state, category)
        }
    }

    @Test fun libraryOriginStillReturnsToItsLibraryCollection() {
        val state = navigation(MainDestination.LIBRARY)
        state.openAlbum("album-key")
        state.closeAlbum()
        assertEquals(MainDestination.LIBRARY, state.mainDestination.value)
        assertEquals(LibraryTab.ALBUMS, state.selectedLibraryTab.value)
        state.openArtist("Artist")
        state.closeArtist()
        assertEquals(MainDestination.LIBRARY, state.mainDestination.value)
        assertEquals(LibraryTab.ARTISTS, state.selectedLibraryTab.value)
        state.openPlaylist(42)
        state.closePlaylist()
        assertEquals(MainDestination.LIBRARY, state.mainDestination.value)
        assertEquals(LibraryTab.PLAYLISTS, state.selectedLibraryTab.value)
    }

    @Test fun albumWithinSearchArtistReturnsToArtistBeforeSearch() {
        val state = navigation(MainDestination.SEARCH)
        state.openArtist("Artist")
        state.openAlbum("album-key")
        state.closeAlbum()
        assertEquals("Artist", state.selectedArtistName.value)
        assertEquals(LibraryTab.ARTISTS, state.selectedLibraryTab.value)
        state.closeArtist()
        assertSearchPreserved(state, SearchCategory.ALL)
    }

    @Test fun organizeIsSuppressedForSearchButStillOfferedInLibrary() {
        assertFalse(shouldOfferLibraryOrganize(MainDestination.SEARCH))
        assertTrue(shouldOfferLibraryOrganize(MainDestination.LIBRARY))
    }

    private fun assertSearchPreserved(state: MusicNavigationState, category: SearchCategory) {
        assertEquals(MainDestination.SEARCH, state.mainDestination.value)
        assertEquals("the", state.searchQuery.value)
        assertEquals(category, state.searchCategory.value)
    }

    private fun navigation(origin: MainDestination): MusicNavigationState {
        val sort = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING)
        return MusicNavigationState(
            mainDestination = mutableStateOf(origin),
            selectedLibraryTab = mutableStateOf(LibraryTab.SONGS),
            playbackLaunchContext = mutableStateOf(PlaybackLaunchContext.Home),
            selectedArtistName = mutableStateOf(null),
            selectedAlbumKey = mutableStateOf(null),
            selectedGenreKey = mutableStateOf(null),
            selectedPlaylistId = mutableStateOf(null),
            searchQuery = mutableStateOf("the"),
            selectedSongFilterState = mutableStateOf(LibrarySongFilterState()),
            selectedSongSortState = mutableStateOf(sort),
            selectedArtistSortState = mutableStateOf(sort),
            selectedAlbumSortState = mutableStateOf(sort),
            selectedFavoriteSortState = mutableStateOf(sort)
        )
    }
}
