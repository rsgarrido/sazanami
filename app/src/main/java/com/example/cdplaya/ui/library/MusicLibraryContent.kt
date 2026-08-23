package com.example.cdplaya.ui.library

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
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.playlist.PlaylistsTabContent
import com.example.cdplaya.ui.queue.QueueScreen

@Composable
fun MusicLibraryContent(
    selectedLibraryTab: LibraryTab,
    songs: List<Song>,
    searchQuery: String,
    selectedSongSortOption: LibrarySortOption,
    selectedArtistSortOption: LibrarySortOption,
    selectedAlbumSortOption: LibrarySortOption,
    selectedFavoriteSortOption: LibrarySortOption,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    selectedArtistName: String?,
    selectedAlbumFolderPath: String?,
    selectedPlaylistId: Long?,
    playlists: List<Playlist>,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    isShuffleEnabled: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, Boolean) -> Unit,
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
    onBackFromQueue: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onBackFromPlaylist: () -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onMovePlaylistSongUpClick: (PlaylistSong) -> Unit,
    onMovePlaylistSongDownClick: (PlaylistSong) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
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
                sortOption = selectedSongSortOption,
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
                sortOption = selectedArtistSortOption,
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
                selectedAlbumFolderPath = selectedAlbumFolderPath,
                currentSong = currentSong,
                viewMode = viewMode,
                gridColumnCount = gridColumnCount,
                sortOption = selectedAlbumSortOption,
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
                sortOption = selectedFavoriteSortOption,
                currentSong = currentSong,
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
                selectedPlaylistId = selectedPlaylistId,
                selectedPlaylistName = selectedPlaylistName,
                selectedPlaylistSongs = selectedPlaylistSongs,
                currentSong = currentSong,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onRenamePlaylistClick = onRenamePlaylistClick,
                onPlaylistClick = onPlaylistClick,
                onDeletePlaylistClick = onDeletePlaylistClick,
                onExportPlaylistClick = onExportPlaylistClick,
                onImportPlaylistClick = onImportPlaylistClick,
                onBackFromPlaylist = onBackFromPlaylist,
                onPlaySongsClick = onPlaySongsClick,
                onMovePlaylistSongUpClick = onMovePlaylistSongUpClick,
                onMovePlaylistSongDownClick = onMovePlaylistSongDownClick,
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
            val displayedSongs = com.example.cdplaya.ui.filterSongsForSearch(
                recentlyAddedSongs,
                searchQuery
            )
            if (displayedSongs.isEmpty()) {
                EmptyHistoryMessage(
                    message = stringResource(R.string.recently_added_empty),
                    modifier = modifier
                )
            } else {
                SongList(
                    songs = displayedSongs,
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
