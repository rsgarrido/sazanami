package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.UNKNOWN_ARTIST_IDENTITY
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PocketHorizonLibraryTest {
    @Test
    fun songsUseTheNormalLibrarySortAndKeepTheirOriginalObjects() {
        val beta = song(2, title = "Beta")
        val alpha = song(1, title = "Alpha")
        val supplied = listOf(beta, alpha)
        val state = ready(supplied)
        val ascending = buildPocketHorizonLibrary(state)
        assertEquals(listOf(1L, 2L), ascending.songs.map(Song::id))
        assertSame(alpha, ascending.songs.first())
        assertSame(beta, ascending.songByKey[beta.stableUiKey()])
        assertEquals(ascending.songs.map(Song::stableUiKey), ascending.songKeys)
        assertEquals(listOf(beta, alpha), supplied)
        val descending = buildPocketHorizonLibrary(state,
            songSortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.DESCENDING))
        assertEquals(listOf(2L, 1L), descending.songs.map(Song::id))
        // Rated and other collections' options must not silently alter the normal Songs context.
        val unsupported = buildPocketHorizonLibrary(state,
            songSortState = LibrarySortState(LibrarySortOption.RATING, LibrarySortDirection.ASCENDING))
        assertEquals(ascending.songs, unsupported.songs)
    }

    @Test
    fun albumsKeepFolderIdentityCompilationMetadataAndCanonicalTrackOrder() {
        val tracks = listOf(
            song(2, title = "Second", folder = "/music/one", album = "Same", track = 2),
            song(1, title = "First", folder = "/music/one", album = "Same", track = 1),
            song(3, folder = "/music/two", album = "Same", artist = "Other"),
            song(4, folder = "/music/compilation", album = "Collection", artist = "Guest A", albumArtist = "Ensemble"),
            song(5, folder = "/music/compilation", album = "Collection", artist = "Guest B", albumArtist = "Ensemble")
        )
        val library = buildPocketHorizonLibrary(ready(tracks))
        assertEquals(3, library.albums.size)
        assertNotEquals(library.albumByKey["/music/one"]?.key, library.albumByKey["/music/two"]?.key)
        assertEquals(listOf(1L, 2L), library.albumByKey.getValue("/music/one").songs.map(Song::id))
        assertEquals("Ensemble", library.albumByKey.getValue("/music/compilation").artistText)
        assertEquals(setOf("/music/one", "/music/two", "/music/compilation"), library.albumKeys.toSet())
        assertSame(tracks[1], library.albumByKey.getValue("/music/one").songs.first())
    }

    @Test
    fun albumsReuseCanonicalMultiDiscMergingAndItsCompletePlaybackContext() {
        val tracks = listOf(
            song(4, folder = "/music/voyage/CD2", album = "VOYAGE", track = 2, disc = 2),
            song(2, folder = "/music/voyage/CD1", album = "Voyage", track = 2, disc = 1),
            song(3, folder = "/music/voyage/CD2", album = "Voyage", track = 1, disc = 2),
            song(1, folder = "/music/voyage/CD1", album = "Voyage", track = 1, disc = 1)
        )
        val album = buildPocketHorizonLibrary(ready(tracks)).albums.single()
        assertTrue(album.key.startsWith("multi-disc:"))
        assertEquals(listOf(1L, 2L, 3L, 4L), album.songs.map(Song::id))
    }

    @Test
    fun missingMetadataKeepsDistinctAlbumFoldersAndTheCanonicalUnknownArtistIdentity() {
        val library = buildPocketHorizonLibrary(ready(listOf(
            song(1, folder = "/music/unknown/one", album = "", artist = "", albumArtist = ""),
            song(2, folder = "/music/unknown/two", album = " ", artist = " ", albumArtist = ""),
            song(3, folder = "/music/literal", album = "Other", artist = "Unknown Artist")
        )))
        assertEquals(3, library.albums.size)
        assertEquals(setOf("/music/unknown/one", "/music/unknown/two", "/music/literal"), library.albumKeys.toSet())
        assertEquals(listOf(1L, 2L), library.artistByKey.getValue(UNKNOWN_ARTIST_IDENTITY.key).songs.map(Song::id))
        assertEquals(listOf(3L), library.artistByKey.getValue(artistIdentity("Unknown Artist").key).songs.map(Song::id))
        assertNotEquals(UNKNOWN_ARTIST_IDENTITY.key, artistIdentity("Unknown Artist").key)
    }

    @Test
    fun artistsUseCanonicalMembershipNamesAndAlbumDiscTrackOrdering() {
        val tracks = listOf(
            song(4, artist = "  The   Artist ", album = "Zulu", track = 1),
            song(3, artist = "THE ARTIST", album = "Alpha", track = 1, disc = 2),
            song(2, artist = "The Artist", album = "Alpha", track = 2, disc = 1),
            song(1, artist = "The Artist", album = "Alpha", track = 1, disc = 1),
            song(5, artist = "The Artist & Guest", albumArtist = "Album Artist Only"),
            song(6, artist = " ", albumArtist = "Album Artist Only")
        )
        val library = buildPocketHorizonLibrary(ready(tracks))
        val artist = library.artistByKey.getValue(artistIdentity("the artist").key)
        assertEquals(listOf(1L, 2L, 3L, 4L), artist.songs.map(Song::id))
        assertEquals(listOf(5L), library.artistByKey.getValue(artistIdentity("The Artist & Guest").key).songs.map(Song::id))
        assertEquals(listOf(6L), library.artistByKey.getValue(UNKNOWN_ARTIST_IDENTITY.key).songs.map(Song::id))
        assertFalse(library.artistByKey.containsKey(artistIdentity("Album Artist Only").key))
        assertEquals(library.artists.map { it.key }, library.artistKeys)
    }

    @Test
    fun albumAndArtistListsReuseTheApplicationsSortChoices() {
        val tracks = listOf(song(1, folder = "/a", album = "Alpha", artist = "Alpha"),
            song(2, folder = "/b", album = "Beta", artist = "Beta"),
            song(3, folder = "/b", album = "Beta", artist = "Beta"))
        val library = buildPocketHorizonLibrary(ready(tracks),
            albumSortState = LibrarySortState(LibrarySortOption.SONG_COUNT, LibrarySortDirection.DESCENDING),
            artistSortState = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.DESCENDING))
        assertEquals(listOf("/b", "/a"), library.albumKeys)
        assertEquals(listOf("Beta", "Alpha"), library.artists.map { it.name })
        assertEquals(listOf(2L, 3L), library.albums.first().songs.map(Song::id))
    }

    @Test
    fun onlyAnInitiallyPublishedStableErrorFreeSnapshotCanConfirmRemoval() {
        val track = song(1)
        val stable = ready(listOf(track))
        assertTrue(buildPocketHorizonLibrary(stable).isAuthoritative)
        assertTrue(buildPocketHorizonLibrary(ready(emptyList())).isAuthoritative)
        listOf(
            LibraryUiState.Empty,
            stable.copy(hasPublishedInitialLibraryState = false),
            stable.copy(isLoading = true),
            stable.copy(isRefreshing = true),
            stable.copy(errorMessage = "Scan failed"),
            stable.copy(songs = emptyList(), isRefreshing = true),
            stable.copy(songs = emptyList(), errorMessage = "Scan failed")
        ).forEach { state ->
            val library = buildPocketHorizonLibrary(state)
            assertFalse(library.isAuthoritative)
            assertEquals(state.songs.size, library.songs.size)
            assertEquals(state.isLoading, library.isLoading)
            assertEquals(state.isRefreshing, library.isRefreshing)
            assertEquals(state.errorMessage, library.errorMessage)
            assertEquals(state.hasPublishedInitialLibraryState, library.hasPublishedInitialLibraryState)
        }
        val refreshing = buildPocketHorizonLibrary(stable.copy(isRefreshing = true))
        assertSame(track, refreshing.songs.single())
    }

    @Test
    fun readinessChangesCanReuseDerivedCollectionsAndIndexes() {
        val library = buildPocketHorizonLibrary(ready(listOf(song(1))))
        val refreshing = library.copy(isAuthoritative = false, isRefreshing = true)
        assertSame(library.songs, refreshing.songs)
        assertSame(library.albums, refreshing.albums)
        assertSame(library.artists, refreshing.artists)
        assertSame(library.songKeys, refreshing.songKeys)
        assertSame(library.albumByKey, refreshing.albumByKey)
        assertSame(library.artistByKey, refreshing.artistByKey)
    }

    private fun ready(songs: List<Song>) = LibraryUiState(songs = songs, hasPublishedInitialLibraryState = true)

    private fun song(
        id: Long,
        title: String = "Track $id",
        folder: String = "/music/default",
        album: String = "Album",
        artist: String = "Artist",
        albumArtist: String = "Artist",
        track: Int = 1,
        disc: Int? = null
    ) = Song(id = id, title = title, artist = artist, album = album, trackNumber = track,
        duration = 180_000L, uri = mock(Uri::class.java), filePath = "$folder/$id.flac",
        folderPath = folder, albumArtUri = null, albumArtist = albumArtist, discNumber = disc)
}
