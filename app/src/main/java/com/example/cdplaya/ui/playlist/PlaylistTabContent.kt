package com.example.cdplaya.ui.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.stableKey
import com.example.cdplaya.player.PlaybackShuffleMode

@Composable
fun PlaylistsTabContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    selectedPlaylistId: Long?,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onBackFromPlaylist: () -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onMovePlaylistSongUpClick: (PlaylistSong) -> Unit,
    onMovePlaylistSongDownClick: (PlaylistSong) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    if (selectedPlaylistId == null) {
        PlaylistListScreen(
            playlists = playlists,
            onCreatePlaylistClick = onCreatePlaylistClick,
            onPlaylistClick = onPlaylistClick,
            onDeletePlaylistClick = onDeletePlaylistClick,
            onExportPlaylistClick = onExportPlaylistClick,
            onImportPlaylistClick = onImportPlaylistClick,
            onRenamePlaylistClick = onRenamePlaylistClick,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    } else {
        val availablePlaylistSongs = selectedPlaylistSongs.mapNotNull(PlaylistSong::resolvedSong)

        PlaylistDetailScreen(
            playlistName = selectedPlaylistName,
            playlistSongs = availablePlaylistSongs,
            playlistSongRows = selectedPlaylistSongs,
            currentSongId = currentSong?.id,
            recentlyAddedSongIds = recentlyAddedSongIds,
            favoriteMembershipKeys = favoriteMembershipKeys,
            onBackClick = onBackFromPlaylist,
            onPlayAllClick = {
                onPlaySongsClick(availablePlaylistSongs, PlaybackShuffleMode.OFF)
            },
            onShuffleAllClick = {
                onPlaySongsClick(availablePlaylistSongs, PlaybackShuffleMode.SONGS)
            },
            onSongClick = onSongClick,
            onPlayNextClick = onPlayNextClick,
            onAddToQueueClick = onAddToQueueClick,
            onToggleFavoriteClick = onToggleFavoriteClick,
            onRemovePlaylistSongClick = onRemovePlaylistSongClick,
            onEditSongTagsClick = onEditSongTagsClick,
            onMovePlaylistSongUpClick = onMovePlaylistSongUpClick,
            onMovePlaylistSongDownClick = onMovePlaylistSongDownClick,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    }
}
