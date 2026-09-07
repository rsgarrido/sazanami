package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkSourceScope
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDetailChromePolicyTest {
    @Test
    fun libraryArtworkDetailsUseChromeChoreography() {
        assertTrue(policy(tab = LibraryTab.ALBUMS, albumKey = "album"))
        assertTrue(policy(tab = LibraryTab.ARTISTS, artistName = "artist"))
        assertTrue(policy(tab = LibraryTab.PLAYLISTS, playlistId = 42L))
    }

    @Test
    fun collectionStateKeepsChromeVisible() {
        assertFalse(policy(tab = LibraryTab.ALBUMS))
        assertFalse(policy(tab = LibraryTab.ARTISTS))
        assertFalse(policy(tab = LibraryTab.PLAYLISTS))
    }

    @Test
    fun homePinnedDetailsDoNotChangeTheirExistingChromeBehavior() {
        val homePinned = LibrarySharedArtworkSourceScope.HOME_PINNED

        assertFalse(
            policy(tab = LibraryTab.ALBUMS, albumKey = "album", albumScope = homePinned)
        )
        assertFalse(
            policy(tab = LibraryTab.ARTISTS, artistName = "artist", artistScope = homePinned)
        )
        assertFalse(
            policy(tab = LibraryTab.PLAYLISTS, playlistId = 42L, playlistScope = homePinned)
        )
    }

    @Test
    fun searchOriginDoesNotUseLibraryChromeChoreography() {
        assertFalse(
            policy(
                destination = MainDestination.SEARCH,
                tab = LibraryTab.ALBUMS,
                albumKey = "album"
            )
        )
    }

    private fun policy(
        destination: MainDestination = MainDestination.LIBRARY,
        tab: LibraryTab,
        artistName: String? = null,
        albumKey: String? = null,
        playlistId: Long? = null,
        artistScope: LibrarySharedArtworkSourceScope = libraryScope,
        albumScope: LibrarySharedArtworkSourceScope = libraryScope,
        playlistScope: LibrarySharedArtworkSourceScope = libraryScope
    ): Boolean = shouldChoreographLibraryDetailChrome(
        destination = destination,
        selectedLibraryTab = tab,
        selectedArtistName = artistName,
        selectedAlbumKey = albumKey,
        selectedPlaylistId = playlistId,
        artistSourceScope = artistScope,
        albumSourceScope = albumScope,
        playlistSourceScope = playlistScope
    )

    private companion object {
        val libraryScope = LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
    }
}
