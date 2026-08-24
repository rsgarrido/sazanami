package com.example.cdplaya.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistArtworkMode
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellTypography

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allPlaylists: List<Playlist>,
    playlistSongs: List<Song>,
    playlistSongRows: List<PlaylistSong>,
    currentSongId: Long?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onChangeArtworkClick: (Playlist) -> Unit,
    onResetArtworkClick: (Playlist) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onMovePlaylistSongUpClick: (PlaylistSong) -> Unit,
    onMovePlaylistSongDownClick: (PlaylistSong) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = playlist.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options for ${playlist.name}"
                    )
                }
                DropdownMenu(overflowExpanded, { overflowExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            overflowExpanded = false
                            renameDialogVisible = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Change artwork") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        onClick = {
                            overflowExpanded = false
                            onChangeArtworkClick(playlist)
                        }
                    )
                    if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
                        DropdownMenuItem(
                            text = { Text("Reset to automatic artwork") },
                            leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onResetArtworkClick(playlist)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Export as M3U8") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            overflowExpanded = false
                            onExportPlaylistClick(playlist)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            overflowExpanded = false
                            deleteDialogVisible = true
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlaylistArtwork(
                playlist = playlist,
                contentDescription = "Artwork for ${playlist.name}",
                modifier = Modifier.size(132.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlistMetadataText(playlist),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Manual playlist",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppShellAccent
                )
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(
                onClick = onPlayAllClick,
                enabled = playlistSongs.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = "Play")
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = onShuffleAllClick,
                enabled = playlistSongs.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Text(text = "Shuffle")
            }
        }

        when {
            playlist.songCount == 0 -> Text(
                text = "This playlist is empty.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            playlistSongs.isEmpty() -> Text(
                text = "The songs in this playlist are not currently available on this device.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> PlaylistSongList(
                playlistSongs = playlistSongs,
                playlistSongRows = playlistSongRows,
                currentSongId = currentSongId,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onRemovePlaylistSongClick = onRemovePlaylistSongClick,
                onMovePlaylistSongUpClick = onMovePlaylistSongUpClick,
                onMovePlaylistSongDownClick = onMovePlaylistSongDownClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (renameDialogVisible) {
        PlaylistNameDialog(
            title = "Rename Playlist",
            confirmButtonText = "Rename",
            initialName = playlist.name,
            originalName = playlist.name,
            existingPlaylistNames = allPlaylists.map(Playlist::name),
            onDismiss = { renameDialogVisible = false },
            onConfirmClick = { name ->
                onRenamePlaylistClick(playlist, name)
                renameDialogVisible = false
            }
        )
    }

    if (deleteDialogVisible) {
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { deleteDialogVisible = false },
            onConfirmDeleteClick = {
                onDeletePlaylistClick(it)
                deleteDialogVisible = false
                onBackClick()
            }
        )
    }
}
