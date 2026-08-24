package com.example.cdplaya.ui.playlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistArtworkMode
import com.example.cdplaya.data.PlaylistArtworkStore
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellIcons
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.library.LibraryItemAction
import com.example.cdplaya.ui.library.LibraryItemActionSheet
import com.example.cdplaya.ui.library.LibraryItemActionSheetTarget
import com.example.cdplaya.ui.library.libraryItemActions

@Composable
fun PlaylistListScreen(
    playlists: List<Playlist>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangeArtworkClick: (Playlist) -> Unit,
    onResetArtworkClick: (Playlist) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var playlistPendingRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var actionSheetTarget by remember { mutableStateOf<LibraryItemActionSheetTarget?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var sortOptionName by rememberSaveable {
        mutableStateOf(PlaylistSortOption.RECENTLY_MODIFIED.name)
    }
    val sortOption = PlaylistSortOption.valueOf(sortOptionName)
    val sortedPlaylists = remember(playlists, sortOption) {
        playlists.sortedWith(sortOption.comparator)
    }
    val context = LocalContext.current

    fun showActions(playlist: Playlist) {
        val sheetArtwork = PlaylistArtworkStore.fileFor(context, playlist.artworkReference)
            ?: playlist.automaticArtworkSongs.firstOrNull()?.albumArtUri
        actionSheetTarget = LibraryItemActionSheetTarget(
            title = playlist.name,
            subtitle = playlistMetadataText(playlist),
            artworkUri = sheetArtwork,
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
                    playlistPendingRename = playlist
                })
                add(LibraryItemAction("Export as M3U8", Icons.Filled.Share) {
                    onExportPlaylistClick(playlist)
                })
                add(LibraryItemAction("Delete", Icons.Filled.Delete, isDestructive = true) {
                    playlistPendingDelete = playlist
                })
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your playlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (playlists.size == 1) "1 playlist" else "${playlists.size} playlists",
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort playlists: ${sortOption.label}",
                        tint = AppShellAccent
                    )
                }
                DropdownMenu(sortMenuExpanded, { sortMenuExpanded = false }) {
                    PlaylistSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == sortOption) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                }
                            },
                            onClick = {
                                sortOptionName = option.name
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Playlist actions")
                }
                DropdownMenu(overflowExpanded, { overflowExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Import M3U") },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                        onClick = {
                            overflowExpanded = false
                            onImportPlaylistClick()
                        }
                    )
                }
            }

            FilledTonalButton(
                onClick = onCreatePlaylistClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "New", modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (sortedPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppShellIcons.AlbumStack,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create one to start building your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = bottomContentPadding + 8.dp)
            ) {
                items(sortedPlaylists, key = Playlist::playlistId) { playlist ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .libraryItemActions(
                                clickLabel = "Open ${playlist.name}",
                                onClick = { onPlaylistClick(playlist) },
                                onShowActions = { showActions(playlist) }
                            ),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PlaylistArtwork(
                                playlist = playlist,
                                contentDescription = "Artwork for ${playlist.name}",
                                modifier = Modifier.size(72.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = AppShellTypography.FeaturedSongTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playlistMetadataText(playlist),
                                    style = AppShellTypography.SongSubtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showActions(playlist) }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options for ${playlist.name}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    actionSheetTarget?.let { target ->
        LibraryItemActionSheet(target = target, onDismissRequest = { actionSheetTarget = null })
    }

    playlistPendingRename?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename Playlist",
            confirmButtonText = "Rename",
            initialName = playlist.name,
            originalName = playlist.name,
            existingPlaylistNames = playlists.map(Playlist::name),
            onDismiss = { playlistPendingRename = null },
            onConfirmClick = { newName ->
                onRenamePlaylistClick(playlist, newName)
                playlistPendingRename = null
            }
        )
    }

    playlistPendingDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { playlistPendingDelete = null },
            onConfirmDeleteClick = {
                onDeletePlaylistClick(it)
                playlistPendingDelete = null
            }
        )
    }
}

private enum class PlaylistSortOption(
    val label: String,
    val comparator: Comparator<Playlist>
) {
    NAME_ASCENDING(
        "Name (A–Z)",
        compareBy<Playlist> { it.name.lowercase() }.thenBy(Playlist::playlistId)
    ),
    NAME_DESCENDING(
        "Name (Z–A)",
        compareByDescending<Playlist> { it.name.lowercase() }.thenBy(Playlist::playlistId)
    ),
    RECENTLY_CREATED(
        "Recently created",
        compareByDescending<Playlist> { it.createdAt }.thenByDescending(Playlist::playlistId)
    ),
    RECENTLY_MODIFIED(
        "Recently modified",
        compareByDescending<Playlist> { it.modifiedAt }.thenByDescending(Playlist::playlistId)
    )
}
