package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.mutableStateOf
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterState
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.navigation.PlaybackLaunchContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailEntryOriginTest {
    @Test
    fun libraryOriginDetailsReturnToTheirLibraryCollections() {
        navigation().apply {
            openAlbum("album")
            closeAlbum()
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
            assertEquals(LibraryTab.ALBUMS, selectedLibraryTab.value)

            openArtist("artist")
            closeArtist()
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
            assertEquals(LibraryTab.ARTISTS, selectedLibraryTab.value)

            openPlaylist(42L)
            closePlaylist()
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
            assertEquals(LibraryTab.PLAYLISTS, selectedLibraryTab.value)
        }
    }

    @Test
    fun homePinnedDetailsReturnHomeForEverySupportedEntity() {
        navigation(MainDestination.HOME).apply {
            openPinnedAlbum("album")
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
            closeAlbum()
            assertEquals(MainDestination.HOME, mainDestination.value)

            openPinnedArtist("artist")
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
            closeArtist()
            assertEquals(MainDestination.HOME, mainDestination.value)

            openPinnedPlaylist(42L)
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
            closePlaylist()
            assertEquals(MainDestination.HOME, mainDestination.value)
        }
    }

    @Test
    fun closingDetailClearsSelectionAndResetsItsOrigin() {
        navigation(MainDestination.HOME).apply {
            openPinnedAlbum("home-album")
            closeAlbum()

            assertNull(selectedAlbumKey.value)
            assertEquals(DetailEntryOrigin.LIBRARY, albumDetailOrigin.value)

            mainDestination.value = MainDestination.LIBRARY
            openAlbum("library-album")
            assertEquals(DetailEntryOrigin.LIBRARY, albumDetailOrigin.value)
            closeAlbum()
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
        }
    }

    @Test
    fun discardingOneDetailCannotLeakItsHomeOriginIntoAnotherDetail() {
        navigation(MainDestination.HOME).apply {
            openPinnedAlbum("home-album")
            clearAlbum()

            mainDestination.value = MainDestination.LIBRARY
            openArtist("library-artist")

            assertEquals(DetailEntryOrigin.LIBRARY, albumDetailOrigin.value)
            assertEquals(DetailEntryOrigin.LIBRARY, artistDetailOrigin.value)
            closeArtist()
            assertEquals(MainDestination.LIBRARY, mainDestination.value)
        }
    }

    @Test
    fun albumOpenedInsidePinnedArtistReturnsToArtistThenHome() {
        navigation(MainDestination.HOME).apply {
            openPinnedArtist("artist")
            openAlbum("album")

            closeAlbum()
            assertEquals("artist", selectedArtistName.value)
            assertEquals(MainDestination.LIBRARY, mainDestination.value)

            closeArtist()
            assertEquals(MainDestination.HOME, mainDestination.value)
        }
    }

    @Test
    fun removedOrUnavailablePinnedSourceStillHasSafeHomeBackTarget() {
        assertEquals(
            MainDestination.HOME,
            detailReturnDestination(DetailEntryOrigin.HOME_PINNED)
        )
    }

    private fun navigation(
        destination: MainDestination = MainDestination.LIBRARY
    ): MusicNavigationState {
        val sort = LibrarySortState(
            LibrarySortOption.TITLE,
            LibrarySortDirection.ASCENDING
        )
        return MusicNavigationState(
            mainDestination = mutableStateOf(destination),
            selectedLibraryTab = mutableStateOf(LibraryTab.SONGS),
            playbackLaunchContext = mutableStateOf(PlaybackLaunchContext.Home),
            selectedArtistName = mutableStateOf(null),
            selectedAlbumKey = mutableStateOf(null),
            selectedGenreKey = mutableStateOf(null),
            selectedPlaylistId = mutableStateOf(null),
            searchQuery = mutableStateOf(""),
            selectedSongFilterState = mutableStateOf(LibrarySongFilterState()),
            selectedSongSortState = mutableStateOf(sort),
            selectedArtistSortState = mutableStateOf(sort),
            selectedAlbumSortState = mutableStateOf(sort),
            selectedFavoriteSortState = mutableStateOf(sort)
        )
    }
}
