package com.example.cdplaya.ui.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.cdplaya.R
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistFolder
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.PlaybackShuffleMode
import com.example.cdplaya.ui.playlist.PlaylistsTabContent
import com.example.cdplaya.ui.queue.QueueScreen

@Composable
fun MusicLibraryContent(
    selectedLibraryTab: LibraryTab,
    songs: List<Song>,
    searchQuery: String,
    selectedSongFilterState: LibrarySongFilterState,
    selectedSongSortState: LibrarySortState,
    selectedRatedFilter: RatedSongFilter,
    selectedArtistSortState: LibrarySortState,
    selectedAlbumSortState: LibrarySortState,
    selectedFavoriteSortState: LibrarySortState,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    selectedArtistName: String?,
    selectedAlbumKey: String?,
    selectedGenreKey: String?,
    selectedPlaylistId: Long?,
    playlists: List<Playlist>,
    playlistFolders: List<PlaylistFolder>,
    selectedPlaylistStateId: Long?,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    isSelectedPlaylistLoading: Boolean,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    isShuffleEnabled: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onArtistSelected: (String) -> Unit,
    onBackFromArtist: () -> Unit,
    onAlbumSelected: (String) -> Unit,
    onBackFromAlbum: () -> Unit,
    onGenreSelected: (String) -> Unit,
    onBackFromGenre: () -> Unit,
    onBackFromQueue: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    onCreatePlaylistClick: (Long?) -> Unit,
    onCreatePlaylistFolderClick: (String) -> Unit,
    onRenamePlaylistFolderClick: (PlaylistFolder, String) -> Unit,
    onDeletePlaylistFolderClick: (PlaylistFolder) -> Unit,
    onMovePlaylistToFolderClick: (Playlist, Long?) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onAddPlaylistToQueueClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangePlaylistArtwork: (Playlist, Uri) -> Unit,
    onResetPlaylistArtwork: (Playlist) -> Unit,
    onBackFromPlaylist: () -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onReorderPlaylistSongs: (Long, List<Long>) -> Unit,
    onAddSongsToCurrentPlaylistClick: (Playlist, List<Song>) -> Unit,
    onEditAlbumMetadataClick: (LibraryAlbumGroup) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    onClearSongFilters: () -> Unit,
    recentlyPlayedSongs: List<Song>,
    recentlyAddedSongs: List<Song>,
    mostPlayedSongs: List<Song>,
    ratingFeaturesEnabled: Boolean = true,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    when (selectedLibraryTab) {
        LibraryTab.SONGS -> {
            SongsTabContent(
                songs = songs,
                searchQuery = searchQuery,
                filterState = selectedSongFilterState,
                sortState = selectedSongSortState,
                currentSong = currentSong,
                viewMode = viewMode,
                gridColumnCount = gridColumnCount,
                recentlyAddedSongIds = recentlyAddedSongIds,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onEditSongTagsClick = onEditSongTagsClick,
                onClearFilters = onClearSongFilters,
                ratingFeaturesEnabled = ratingFeaturesEnabled,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.RATED -> {
            RatedSongsTabContent(
                songs = songs,
                searchQuery = searchQuery,
                sortState = selectedSongSortState,
                selectedFilter = selectedRatedFilter,
                currentSong = currentSong,
                viewMode = viewMode,
                gridColumnCount = gridColumnCount,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onEditSongTagsClick = onEditSongTagsClick,
                ratingFeaturesEnabled = ratingFeaturesEnabled,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.ARTISTS -> {
            ArtistsTabContent(
                songs = songs,
                searchQuery = searchQuery,
                selectedArtistName = selectedArtistName,
                sortState = selectedArtistSortState,
                currentSong = currentSong,
                viewMode = viewMode,
                gridColumnCount = gridColumnCount,
                recentlyAddedSongIds = recentlyAddedSongIds,
                onArtistSelected = onArtistSelected,
                onAlbumSelected = onAlbumSelected,
                onBackFromArtist = onBackFromArtist,
                onPlaySongsClick = onPlaySongsClick,
                onPlayNextClick = onPlayNextClick,
                onSongClick = onSongClick,
                onAddToQueueClick = onAddToQueueClick,
                onPlayNextSongsClick = onPlayNextSongsClick,
                onAddSongsToQueueClick = onAddSongsToQueueClick,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.ALBUMS -> {
            AlbumsTabContent(
                songs = songs,
                searchQuery = searchQuery,
                selectedAlbumKey = selectedAlbumKey,
                currentSong = currentSong,
                viewMode = viewMode,
                gridColumnCount = gridColumnCount,
                sortState = selectedAlbumSortState,
                recentlyAddedSongIds = recentlyAddedSongIds,
                onAlbumSelected = onAlbumSelected,
                onBackFromAlbum = onBackFromAlbum,
                onPlaySongsClick = onPlaySongsClick,
                onPlayNextClick = onPlayNextClick,
                onSongClick = onSongClick,
                onAddToQueueClick = onAddToQueueClick,
                onPlayNextSongsClick = onPlayNextSongsClick,
                onAddSongsToQueueClick = onAddSongsToQueueClick,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                onEditAlbumMetadataClick = onEditAlbumMetadataClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.GENRES -> {
            GenresTabContent(
                songs = songs,
                searchQuery = searchQuery,
                selectedGenreKey = selectedGenreKey,
                currentSong = currentSong,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onGenreSelected = onGenreSelected,
                onBackFromGenre = onBackFromGenre,
                onSongClick = onSongClick,
                onPlaySongsClick = onPlaySongsClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.FAVORITES -> {
            FavoritesTabContent(
                songs = songs,
                favoriteMembershipKeys = favoriteMembershipKeys,
                searchQuery = searchQuery,
                sortState = selectedFavoriteSortState,
                currentSong = currentSong,
                viewMode = viewMode,
                gridColumnCount = gridColumnCount,
                recentlyAddedSongIds = recentlyAddedSongIds,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.QUEUE -> {
            QueueScreen(
                queuedSongs = queuedSongs,
                upcomingSongs = upcomingSongs,
                isShuffleEnabled = isShuffleEnabled,
                onBackClick = onBackFromQueue,
                onRemoveFromQueueClick = onRemoveFromQueueClick,
                onMoveQueueItemUpClick = onMoveQueueItemUpClick,
                onMoveQueueItemDownClick = onMoveQueueItemDownClick,
                onClearQueueClick = onClearQueueClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.PLAYLISTS -> {
            PlaylistsTabContent(
                songs = songs,
                playlists = playlists,
                playlistFolders = playlistFolders,
                selectedPlaylistId = selectedPlaylistId,
                selectedPlaylistStateId = selectedPlaylistStateId,
                selectedPlaylistName = selectedPlaylistName,
                selectedPlaylistSongs = selectedPlaylistSongs,
                isSelectedPlaylistLoading = isSelectedPlaylistLoading,
                currentSong = currentSong,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                viewMode = viewMode,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onCreateFolderClick = onCreatePlaylistFolderClick,
                onRenameFolderClick = onRenamePlaylistFolderClick,
                onDeleteFolderClick = onDeletePlaylistFolderClick,
                onMovePlaylistClick = onMovePlaylistToFolderClick,
                onRenamePlaylistClick = onRenamePlaylistClick,
                onPlaylistClick = onPlaylistClick,
                onDeletePlaylistClick = onDeletePlaylistClick,
                onExportPlaylistClick = onExportPlaylistClick,
                onAddPlaylistToQueueClick = onAddPlaylistToQueueClick,
                onImportPlaylistClick = onImportPlaylistClick,
                onChangePlaylistArtwork = onChangePlaylistArtwork,
                onResetPlaylistArtwork = onResetPlaylistArtwork,
                onBackFromPlaylist = onBackFromPlaylist,
                onPlaySongsClick = onPlaySongsClick,
                onReorderPlaylistSongs = onReorderPlaylistSongs,
                onAddSongsToCurrentPlaylistClick = onAddSongsToCurrentPlaylistClick,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onRemovePlaylistSongClick = onRemovePlaylistSongClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }

        LibraryTab.RECENTLY_PLAYED -> {
            if (recentlyPlayedSongs.isEmpty()) {
                EmptyHistoryMessage(
                    message = "No recently played songs yet.",
                    modifier = modifier
                )
            } else {
                SongList(
                    songs = recentlyPlayedSongs,
                    currentSongId = currentSong?.id,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onSongClick = onSongClick,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onEditSongTagsClick = onEditSongTagsClick,
                    bottomContentPadding = bottomContentPadding,
                    modifier = modifier
                )
            }
        }

        LibraryTab.RECENTLY_ADDED -> {
            val searchedSongs = com.example.cdplaya.ui.filterSongsForSearch(
                recentlyAddedSongs,
                searchQuery
            )
            val displayedSongs = com.example.cdplaya.ui.sortSongsForLibrary(
                searchedSongs,
                selectedSongSortState.option,
                selectedSongSortState.direction
            )
            val scrollStates = rememberLibrarySortScrollStates(selectedSongSortState)
            if (displayedSongs.isEmpty()) {
                EmptyHistoryMessage(
                    message = stringResource(R.string.recently_added_empty),
                    modifier = modifier
                )
            } else {
                LibraryLayoutTransition(
                    viewMode = viewMode,
                    modifier = modifier,
                    listContent = {
                        SongList(
                            songs = displayedSongs,
                            listState = scrollStates.list,
                            currentSongId = currentSong?.id,
                            recentlyAddedSongIds = recentlyAddedSongIds,
                            favoriteMembershipKeys = favoriteMembershipKeys,
                            onSongClick = onSongClick,
                            onPlayNextClick = onPlayNextClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onToggleFavoriteClick = onToggleFavoriteClick,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onEditSongTagsClick = onEditSongTagsClick,
                            bottomContentPadding = bottomContentPadding,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    gridContent = {
                        SongGrid(
                            songs = displayedSongs,
                            gridState = scrollStates.grid,
                            currentSongId = currentSong?.id,
                            gridColumnCount = gridColumnCount,
                            recentlyAddedSongIds = recentlyAddedSongIds,
                            favoriteMembershipKeys = favoriteMembershipKeys,
                            onSongClick = onSongClick,
                            onPlayNextClick = onPlayNextClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onToggleFavoriteClick = onToggleFavoriteClick,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onEditSongTagsClick = onEditSongTagsClick,
                            bottomContentPadding = bottomContentPadding,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }

        LibraryTab.MOST_PLAYED -> {
            if (mostPlayedSongs.isEmpty()) {
                EmptyHistoryMessage(
                    message = "No most played songs yet.",
                    modifier = modifier
                )
            } else {
                SongList(
                    songs = mostPlayedSongs,
                    currentSongId = currentSong?.id,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onSongClick = onSongClick,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onEditSongTagsClick = onEditSongTagsClick,
                    bottomContentPadding = bottomContentPadding,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
