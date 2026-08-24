package com.example.cdplaya.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistArtworkMode
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.library.LibraryDetailAction
import com.example.cdplaya.ui.library.LibraryDetailTopBar
import com.example.cdplaya.ui.library.LibraryItemAction
import com.example.cdplaya.ui.library.LibraryItemActionSheet
import com.example.cdplaya.ui.library.LibraryItemActionSheetTarget

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allPlaylists: List<Playlist>,
    playlistSongs: List<Song>,
    playlistSongRows: List<PlaylistSong>,
    isLoading: Boolean,
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
    var actionSheetTarget by remember { mutableStateOf<LibraryItemActionSheetTarget?>(null) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showCompactTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    fun showPlaylistActions() {
        actionSheetTarget = LibraryItemActionSheetTarget(
            title = playlist.name,
            subtitle = playlistMetadataText(playlist),
            artworkUri = null,
            artworkDescription = "Artwork for ${playlist.name}",
            actions = buildList {
                add(LibraryItemAction("Change artwork", Icons.Filled.Image) {
                    onChangeArtworkClick(playlist)
                })
                if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
                    add(LibraryItemAction("Reset to automatic artwork", Icons.Filled.Restore) {
                        onResetArtworkClick(playlist)
                    })
                }
                add(LibraryItemAction("Rename", Icons.Filled.Edit) {
                    renameDialogVisible = true
                })
                add(LibraryItemAction("Export as M3U8", Icons.Filled.Share) {
                    onExportPlaylistClick(playlist)
                })
                add(LibraryItemAction("Delete", Icons.Filled.Delete, isDestructive = true) {
                    deleteDialogVisible = true
                })
            },
            artworkContent = {
                PlaylistArtwork(
                    playlist = playlist,
                    contentDescription = "Artwork for ${playlist.name}",
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        LibraryDetailTopBar(
            title = playlist.name,
            showTitle = showCompactTitle,
            containerColor = if (showCompactTitle) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            } else {
                Color.Transparent
            },
            onBackClick = onBackClick,
            onMoreClick = ::showPlaylistActions
        )

        PlaylistSongList(
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
            listState = listState,
            headerContent = {
                PlaylistDetailHero(
                    playlist = playlist,
                    hasSongs = playlistSongs.isNotEmpty(),
                    onPlayClick = onPlayAllClick,
                    onShuffleClick = onShuffleAllClick
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            },
            emptyContent = {
                PlaylistDetailEmptyState(
                    playlist = playlist,
                    isLoading = isLoading
                )
            },
            bottomContentPadding = bottomContentPadding,
            modifier = Modifier.fillMaxSize()
        )
    }

    actionSheetTarget?.let { target ->
        LibraryItemActionSheet(
            target = target,
            onDismissRequest = { actionSheetTarget = null }
        )
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

@Composable
private fun PlaylistDetailHero(
    playlist: Playlist,
    hasSongs: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        PlaylistArtwork(
            playlist = playlist,
            contentDescription = "Artwork for ${playlist.name}",
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .widthIn(max = 320.dp)
                .aspectRatio(1f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
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

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Top
        ) {
            LibraryDetailAction(
                icon = Icons.Filled.PlayArrow,
                label = "Play",
                enabled = hasSongs,
                onClick = onPlayClick
            )
            LibraryDetailAction(
                icon = Icons.Filled.Shuffle,
                label = "Shuffle",
                enabled = hasSongs,
                onClick = onShuffleClick
            )
        }
    }
}

@Composable
private fun PlaylistDetailEmptyState(
    playlist: Playlist,
    isLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                Text(
                    text = "Loading playlist\u2026",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = if (playlist.songCount == 0) {
                    "This playlist is empty."
                } else {
                    "The songs in this playlist are not currently available on this device."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
