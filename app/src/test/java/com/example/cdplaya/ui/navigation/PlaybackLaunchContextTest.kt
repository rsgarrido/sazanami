package com.example.cdplaya.ui.navigation

import com.example.cdplaya.ui.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLaunchContextTest {
    @Test
    fun capturePrefersAlbumDetailOverSearch() {
        val context = capturePlaybackLaunchContext(
            mainDestination = MainDestination.LIBRARY,
            selectedLibraryTab = LibraryTab.ALBUMS,
            selectedAlbumKey = "/music/album",
            selectedArtistName = null,
            selectedGenreKey = null,
            selectedPlaylistId = null,
            searchQuery = "track"
        )

        assertEquals(
            PlaybackLaunchContext.AlbumDetail("/music/album"),
            context
        )
    }

    @Test
    fun capturePreservesTopLevelSearchQuery() {
        val context = capturePlaybackLaunchContext(
            mainDestination = MainDestination.SEARCH,
            selectedLibraryTab = LibraryTab.SONGS,
            selectedAlbumKey = null,
            selectedArtistName = null,
            selectedGenreKey = null,
            selectedPlaylistId = null,
            searchQuery = "needle"
        )

        assertEquals(PlaybackLaunchContext.Search("needle"), context)
    }

    @Test
    fun capturePreservesEmptySearchDestination() {
        val context = capturePlaybackLaunchContext(
            mainDestination = MainDestination.SEARCH,
            selectedLibraryTab = LibraryTab.SONGS,
            selectedAlbumKey = null,
            selectedArtistName = null,
            selectedGenreKey = null,
            selectedPlaylistId = null,
            searchQuery = ""
        )

        assertEquals(PlaybackLaunchContext.Search(""), context)
    }

    @Test
    fun capturePreservesGenreDetail() {
        val context = capturePlaybackLaunchContext(
            mainDestination = MainDestination.LIBRARY,
            selectedLibraryTab = LibraryTab.GENRES,
            selectedAlbumKey = null,
            selectedArtistName = null,
            selectedGenreKey = "known:rock",
            selectedPlaylistId = null,
            searchQuery = ""
        )

        assertEquals(PlaybackLaunchContext.GenreDetail("known:rock"), context)
    }

    @Test
    fun existingGenreDetailRemainsValidForPlaybackReturn() {
        val genreContext = PlaybackLaunchContext.GenreDetail("known:rock")

        assertEquals(
            genreContext,
            genreContext.withValidDetails(
                albumKeys = emptySet(),
                artistNames = emptySet(),
                genreKeys = setOf("known:rock"),
                playlistIds = emptySet()
            )
        )
    }

    @Test
    fun missingDetailsFallBackToTheirParentSections() {
        val albumContext = PlaybackLaunchContext.AlbumDetail("missing")
            .withValidDetails(emptySet(), emptySet(), emptySet(), emptySet())
        val artistContext = PlaybackLaunchContext.ArtistDetail("missing")
            .withValidDetails(emptySet(), emptySet(), emptySet(), emptySet())
        val genreContext = PlaybackLaunchContext.GenreDetail("missing")
            .withValidDetails(emptySet(), emptySet(), emptySet(), emptySet())
        val playlistContext = PlaybackLaunchContext.PlaylistDetail(42L)
            .withValidDetails(emptySet(), emptySet(), emptySet(), emptySet())

        assertEquals(
            PlaybackLaunchContext.LibrarySection(LibraryTab.ALBUMS),
            albumContext
        )
        assertEquals(
            PlaybackLaunchContext.LibrarySection(LibraryTab.ARTISTS),
            artistContext
        )
        assertEquals(
            PlaybackLaunchContext.LibrarySection(LibraryTab.GENRES),
            genreContext
        )
        assertEquals(
            PlaybackLaunchContext.LibrarySection(LibraryTab.PLAYLISTS),
            playlistContext
        )
    }
}
