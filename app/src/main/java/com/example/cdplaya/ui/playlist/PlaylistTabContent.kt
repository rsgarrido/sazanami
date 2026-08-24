package com.example.cdplaya.ui.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.PlaybackShuffleMode

@Composable
fun PlaylistsTabContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    selectedPlaylistId: Long?,
    selectedPlaylistStateId: Long?,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    isSelectedPlaylistLoading: Boolean,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangePlaylistArtwork: (Playlist, Uri) -> Unit,
    onResetPlaylistArtwork: (Playlist) -> Unit,
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
    var playlistPendingArtworkId by remember { mutableStateOf<Long?>(null) }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val playlist = playlistPendingArtworkId?.let { playlistId ->
            playlists.firstOrNull { it.playlistId == playlistId }
        }
        if (uri != null && playlist != null) {
            onChangePlaylistArtwork(playlist, uri)
        }
        playlistPendingArtworkId = null
    }
    val chooseArtwork: (Playlist) -> Unit = { playlist ->
        playlistPendingArtworkId = playlist.playlistId
        artworkPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    if (selectedPlaylistId == null) {
        PlaylistListScreen(
            playlists = playlists,
            onCreatePlaylistClick = onCreatePlaylistClick,
            onPlaylistClick = onPlaylistClick,
            onDeletePlaylistClick = onDeletePlaylistClick,
            onExportPlaylistClick = onExportPlaylistClick,
            onImportPlaylistClick = onImportPlaylistClick,
            onChangeArtworkClick = chooseArtwork,
            onResetArtworkClick = onResetPlaylistArtwork,
            onRenamePlaylistClick = onRenamePlaylistClick,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    } else {
        val stateMatchesSelection = selectedPlaylistStateId == selectedPlaylistId
        val scopedPlaylistSongRows = if (stateMatchesSelection) {
            selectedPlaylistSongs
        } else {
            emptyList()
        }
        val availablePlaylistSongRows = scopedPlaylistSongRows.filter { it.resolvedSong != null }
        val availablePlaylistSongs = availablePlaylistSongRows.mapNotNull(PlaylistSong::resolvedSong)
        val selectedPlaylist = playlists.firstOrNull { playlist ->
            playlist.playlistId == selectedPlaylistId
        } ?: Playlist(
            playlistId = selectedPlaylistId,
            name = if (stateMatchesSelection) selectedPlaylistName else "Playlist",
            songCount = scopedPlaylistSongRows.size,
            totalDuration = scopedPlaylistSongRows.sumOf { it.duration.coerceAtLeast(0L) },
            automaticArtworkSongs = availablePlaylistSongs.distinctBy { song ->
                Triple(
                    song.albumArtist.ifBlank { song.artist }.lowercase(),
                    song.album.lowercase(),
                    song.folderPath.lowercase()
                )
            }.take(4)
        )

        key(selectedPlaylistId) {
            PlaylistDetailScreen(
                playlist = selectedPlaylist,
                allPlaylists = playlists,
                playlistSongs = availablePlaylistSongs,
                playlistSongRows = availablePlaylistSongRows,
                isLoading = !stateMatchesSelection || isSelectedPlaylistLoading,
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
                onRenamePlaylistClick = onRenamePlaylistClick,
                onDeletePlaylistClick = onDeletePlaylistClick,
                onExportPlaylistClick = onExportPlaylistClick,
                onChangeArtworkClick = chooseArtwork,
                onResetArtworkClick = onResetPlaylistArtwork,
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
}
