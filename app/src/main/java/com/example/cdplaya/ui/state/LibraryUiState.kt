package com.example.cdplaya.ui.state

import com.example.cdplaya.data.LibraryFolder
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.LibraryRefreshResult
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistFolder
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.library.SongRatingFilter

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val folderSelectionMode: FolderSelectionMode = FolderSelectionMode.ALL,
    val selectedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),
    val favoriteMembershipKeys: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val playlistFolders: List<PlaylistFolder> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val selectedPlaylistName: String = DEFAULT_PLAYLIST_NAME,
    val selectedPlaylistSongs: List<PlaylistSong> = emptyList(),
    val isSelectedPlaylistLoading: Boolean = false,
    val recentlyPlayedSongs: List<Song> = emptyList(),
    val mostPlayedSongs: List<Song> = emptyList(),
    val recentlyAddedSongs: List<Song> = emptyList(),
    val songRatingFilter: SongRatingFilter = SongRatingFilter.ALL,
    val unresolvedFavoriteCount: Int = 0,
    val unresolvedPlaylistRowCount: Int = 0,
    val unresolvedListeningHistoryCount: Int = 0,
    val lastRefreshSummary: LibraryRefreshSummary? = null,
    val hasPublishedInitialLibraryState: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        const val DEFAULT_PLAYLIST_NAME = "Playlist"
        val Empty = LibraryUiState()
    }
}

data class LibraryRefreshSummary(
    val addedCount: Int,
    val updatedCount: Int,
    val removedCount: Int,
    val movedCount: Int,
    val reusedCount: Int,
    val artworkRepairCount: Int,
    val successfulCompleteScan: Boolean
)

fun LibraryRefreshResult.toUiSummary(): LibraryRefreshSummary = LibraryRefreshSummary(
    addedCount = addedCount,
    updatedCount = updatedCount,
    removedCount = removedCount,
    movedCount = movedCount,
    reusedCount = reusedCount,
    artworkRepairCount = artworkRepairCount,
    successfulCompleteScan = successfulCompleteScan
)

fun libraryUiState(
    songs: Collection<Song> = emptyList(),
    folders: Collection<LibraryFolder> = emptyList(),
    folderSelectionMode: FolderSelectionMode = FolderSelectionMode.ALL,
    selectedFolders: Collection<String> = emptySet(),
    excludedFolders: Collection<String> = emptySet(),
    favoriteMembershipKeys: Collection<String> = emptySet(),
    playlists: Collection<Playlist> = emptyList(),
    playlistFolders: Collection<PlaylistFolder> = emptyList(),
    selectedPlaylistId: Long? = null,
    selectedPlaylistName: String = LibraryUiState.DEFAULT_PLAYLIST_NAME,
    selectedPlaylistSongs: Collection<PlaylistSong> = emptyList(),
    isSelectedPlaylistLoading: Boolean = false,
    recentlyPlayedSongs: Collection<Song> = emptyList(),
    mostPlayedSongs: Collection<Song> = emptyList(),
    recentlyAddedSongs: Collection<Song> = emptyList(),
    songRatingFilter: SongRatingFilter = SongRatingFilter.ALL,
    unresolvedFavoriteCount: Int = 0,
    unresolvedPlaylistRowCount: Int = 0,
    unresolvedListeningHistoryCount: Int = 0,
    lastRefreshResult: LibraryRefreshResult? = null,
    hasPublishedInitialLibraryState: Boolean = true,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    errorMessage: String? = null
): LibraryUiState = LibraryUiState(
    songs = songs.toList(),
    folders = folders.toList(),
    folderSelectionMode = folderSelectionMode,
    selectedFolders = selectedFolders.toSet(),
    excludedFolders = excludedFolders.toSet(),
    favoriteMembershipKeys = favoriteMembershipKeys.toSet(),
    playlists = playlists.toList(),
    playlistFolders = playlistFolders.toList(),
    selectedPlaylistId = selectedPlaylistId,
    selectedPlaylistName = selectedPlaylistName,
    selectedPlaylistSongs = selectedPlaylistSongs.toList(),
    isSelectedPlaylistLoading = isSelectedPlaylistLoading,
    recentlyPlayedSongs = recentlyPlayedSongs.toList(),
    mostPlayedSongs = mostPlayedSongs.toList(),
    recentlyAddedSongs = recentlyAddedSongs.toList(),
    songRatingFilter = songRatingFilter,
    unresolvedFavoriteCount = unresolvedFavoriteCount,
    unresolvedPlaylistRowCount = unresolvedPlaylistRowCount,
    unresolvedListeningHistoryCount = unresolvedListeningHistoryCount,
    lastRefreshSummary = lastRefreshResult?.toUiSummary(),
    hasPublishedInitialLibraryState = hasPublishedInitialLibraryState,
    isLoading = isLoading,
    isRefreshing = isRefreshing,
    errorMessage = errorMessage
)
