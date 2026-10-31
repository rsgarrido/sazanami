package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.librarySortOptionsFor
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryArtistGroups
import io.github.rsgarrido.sazanami.ui.sortSongsForLibrary
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState

/** A presentation of the supplied library, never an independently owned library or playback queue. */
internal data class PocketHorizonLibrary(
    val songs: List<Song>,
    val albums: List<LibraryAlbumGroup>,
    val artists: List<LibraryArtistGroup>,
    val isAuthoritative: Boolean,
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val errorMessage: String?,
    val hasPublishedInitialLibraryState: Boolean,
    val songKeys: List<String> = songs.map(Song::stableUiKey),
    val albumKeys: List<String> = albums.map(LibraryAlbumGroup::key),
    val artistKeys: List<String> = artists.map(LibraryArtistGroup::key),
    val songByKey: Map<String, Song> = songs.associateBy(Song::stableUiKey),
    val albumByKey: Map<String, LibraryAlbumGroup> = albums.associateBy(LibraryAlbumGroup::key),
    val artistByKey: Map<String, LibraryArtistGroup> = artists.associateBy(LibraryArtistGroup::key)
)

/** Mirrors the normal Songs screen's allowed sort choices before using the existing sorter. */
internal fun buildPocketHorizonLibrary(
    state: LibraryUiState,
    songSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING),
    albumSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING),
    artistSortState: LibrarySortState = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING)
): PocketHorizonLibrary {
    return PocketHorizonLibrary(
        songs = sortedPocketHorizonSongs(state.songs, songSortState),
        albums = sortedLibraryAlbumGroups(state.songs, albumSortState),
        artists = sortedLibraryArtistGroups(state.songs, artistSortState),
        isAuthoritative = state.isAuthoritativeForHorizon(),
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        errorMessage = state.errorMessage,
        hasPublishedInitialLibraryState = state.hasPublishedInitialLibraryState
    )
}

/** Grouping, ordering and key indexes depend on library inputs, never position ticks or scroll state. */
@Composable
internal fun rememberPocketHorizonLibrary(
    state: LibraryUiState,
    songSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING),
    albumSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING),
    artistSortState: LibrarySortState = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING)
): PocketHorizonLibrary {
    val songs = remember(state.songs, songSortState) { sortedPocketHorizonSongs(state.songs, songSortState) }
    val albums = remember(state.songs, albumSortState) { sortedLibraryAlbumGroups(state.songs, albumSortState) }
    val artists = remember(state.songs, artistSortState) { sortedLibraryArtistGroups(state.songs, artistSortState) }
    val content = remember(songs, albums, artists) {
        PocketHorizonLibrary(songs, albums, artists, state.isAuthoritativeForHorizon(), state.isLoading,
            state.isRefreshing, state.errorMessage, state.hasPublishedInitialLibraryState)
    }
    return remember(content, state.isLoading, state.isRefreshing, state.errorMessage,
        state.hasPublishedInitialLibraryState) {
        // Data-class copy retains the already derived lists/maps when only readiness changes.
        content.copy(
            isAuthoritative = state.isAuthoritativeForHorizon(),
            isLoading = state.isLoading,
            isRefreshing = state.isRefreshing,
            errorMessage = state.errorMessage,
            hasPublishedInitialLibraryState = state.hasPublishedInitialLibraryState
        )
    }
}

private fun LibraryUiState.isAuthoritativeForHorizon(): Boolean =
    hasPublishedInitialLibraryState && !isLoading && !isRefreshing && errorMessage == null

private fun sortedPocketHorizonSongs(songs: List<Song>, sortState: LibrarySortState): List<Song> {
    val option = sortState.option.takeIf { it in librarySortOptionsFor(LibraryTab.SONGS) }
        ?: LibrarySortOption.TITLE
    return sortSongsForLibrary(songs, option, sortState.direction)
}
