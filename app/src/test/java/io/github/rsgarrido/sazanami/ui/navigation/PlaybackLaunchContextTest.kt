package io.github.rsgarrido.sazanami.ui.navigation

import android.net.Uri
import io.github.rsgarrido.sazanami.data.FolderId
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.buildFolderBrowseIndex
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackLaunchContextTest {
    @Test
    fun searchOwnedDetailsKeepTheirEntityPlaybackContext() {
        assertEquals(PlaybackLaunchContext.AlbumDetail("album"), capturePlaybackLaunchContext(
            MainDestination.SEARCH, LibraryTab.ALBUMS, "album", null, null, null, "the"
        ))
        assertEquals(PlaybackLaunchContext.ArtistDetail("artist"), capturePlaybackLaunchContext(
            MainDestination.SEARCH, LibraryTab.ARTISTS, null, "artist", null, null, "the"
        ))
        assertEquals(PlaybackLaunchContext.PlaylistDetail(42), capturePlaybackLaunchContext(
            MainDestination.SEARCH, LibraryTab.PLAYLISTS, null, null, null, 42, "the"
        ))
    }

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
    fun captureAndSerializationPreserveExactNestedFolder() {
        val folderId = FolderId("1234-5678", "music/artist/album")
        val context = capturePlaybackLaunchContext(
            mainDestination = MainDestination.LIBRARY,
            selectedLibraryTab = LibraryTab.FOLDERS,
            selectedAlbumKey = null,
            selectedArtistName = null,
            selectedGenreKey = null,
            selectedPlaylistId = null,
            searchQuery = "",
            selectedFolderId = folderId
        )

        assertEquals(PlaybackLaunchContext.FolderDetail(folderId), context)
        assertEquals(
            context,
            playbackLaunchContextFromSavedValues(context.toSavedValues())
        )
    }

    @Test
    fun removedFolderPlaybackContextResolvesToNearestAncestorOrRoot() {
        val index = buildFolderBrowseIndex(
            listOf(song(relativePath = "Music/Artist/Retained/"))
        )
        val removedAlbum = PlaybackLaunchContext.FolderDetail(
            FolderId("external_primary", "music/artist/removed/album")
        )
        val unrelated = PlaybackLaunchContext.FolderDetail(
            FolderId("external_primary", "audiobooks/missing")
        )

        assertEquals(
            PlaybackLaunchContext.FolderDetail(
                FolderId("external_primary", "music/artist")
            ),
            removedAlbum.withValidDetails(
                emptySet(), emptySet(), emptySet(), emptySet(), index
            )
        )
        assertEquals(
            PlaybackLaunchContext.LibrarySection(LibraryTab.FOLDERS),
            unrelated.withValidDetails(
                emptySet(), emptySet(), emptySet(), emptySet(), index
            )
        )
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

    private fun song(relativePath: String): Song = Song(
        id = 1,
        title = "Song",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 1_000,
        uri = mock(Uri::class.java),
        filePath = "/storage/emulated/0/$relativePath/song.flac",
        folderPath = "/storage/emulated/0/$relativePath",
        albumArtUri = null,
        volumeName = "external_primary",
        displayName = "song.flac",
        relativePath = relativePath
    )
}
