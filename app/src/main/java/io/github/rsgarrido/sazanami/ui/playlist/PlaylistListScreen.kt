package io.github.rsgarrido.sazanami.ui.playlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.ui.library.playlistQueueActions
import io.github.rsgarrido.sazanami.data.PlaylistArtworkMode
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistMembershipBehavior
import io.github.rsgarrido.sazanami.data.PlaylistType
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.home.LocalHomePinUi
import io.github.rsgarrido.sazanami.ui.library.LibraryItemAction
import io.github.rsgarrido.sazanami.ui.library.LibraryItemActionSheet
import io.github.rsgarrido.sazanami.ui.library.LibraryItemActionSheetTarget
import io.github.rsgarrido.sazanami.ui.library.LocalLibraryQueueUi
import io.github.rsgarrido.sazanami.ui.library.libraryItemActions
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.ResetLazyGridOnSortChange
import io.github.rsgarrido.sazanami.ui.library.ResetLazyListOnSortChange
import io.github.rsgarrido.sazanami.ui.library.compareKnownPositiveLong
import io.github.rsgarrido.sazanami.ui.library.compareLibraryText

internal object PlaylistGridLayout {
    val minimumTileWidth = 148.dp
    val metadataHeight = 72.dp
    const val titleMaxLines = 2
    const val secondaryMaxLines = 1
}

@Composable
fun PlaylistListScreen(
    playlists: List<Playlist>,
    folders: List<PlaylistFolder>,
    selectedFolderId: Long?,
    onFolderSelected: (Long?) -> Unit,
    onCreatePlaylistClick: (Long?) -> Unit,
    onCreateFolderClick: (String) -> Unit,
    onRenameFolderClick: (PlaylistFolder, String) -> Unit,
    onDeleteFolderClick: (PlaylistFolder) -> Unit,
    onMovePlaylistClick: (Playlist, Long?) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onAddPlaylistToQueueClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangeArtworkClick: (Playlist) -> Unit,
    onResetArtworkClick: (Playlist) -> Unit,
    viewMode: LibraryViewMode,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val homePinUi = LocalHomePinUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    var playlistPendingRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingMove by remember { mutableStateOf<Playlist?>(null) }
    var folderPendingRename by remember { mutableStateOf<PlaylistFolder?>(null) }
    var folderPendingDelete by remember { mutableStateOf<PlaylistFolder?>(null) }
    var createFolderDialogVisible by remember { mutableStateOf(false) }
    var actionSheetTarget by remember { mutableStateOf<LibraryItemActionSheetTarget?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var sortFieldName by rememberSaveable {
        mutableStateOf(PlaylistSortField.MODIFIED.name)
    }
    var sortDirectionName by rememberSaveable {
        mutableStateOf(LibrarySortDirection.DESCENDING.name)
    }
    val sortField = PlaylistSortField.valueOf(sortFieldName)
    val sortDirection = LibrarySortDirection.valueOf(sortDirectionName)
    val sortKey = sortField to sortDirection
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    ResetLazyListOnSortChange(sortKey, listState)
    ResetLazyGridOnSortChange(sortKey, gridState)
    val currentFolder = folders.firstOrNull { it.folderId == selectedFolderId }
    val visiblePlaylists = remember(playlists, selectedFolderId, sortField, sortDirection) {
        sortField.sort(
            playlists.filter { it.folderId == selectedFolderId },
            sortDirection
        )
    }

    LaunchedEffect(selectedFolderId, currentFolder) {
        if (selectedFolderId != null && currentFolder == null) onFolderSelected(null)
    }

    fun showPlaylistActions(playlist: Playlist) {
        actionSheetTarget = LibraryItemActionSheetTarget(
            title = playlist.name,
            subtitle = playlistMetadataText(playlist),
            artworkUri = null,
            artworkDescription = "Artwork for ${playlist.name}",
            actions = buildList {
                add(homePinUi.actionForPlaylist(playlist))
                addAll(playlistQueueActions(playlist, libraryQueueUi, onAddPlaylistToQueueClick))
                add(LibraryItemAction("Change artwork", Icons.Filled.Image) {
                    onChangeArtworkClick(playlist)
                })
                if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
                    add(LibraryItemAction("Reset to automatic artwork", Icons.Filled.Restore) {
                        onResetArtworkClick(playlist)
                    })
                }
                add(LibraryItemAction("Move to folder", Icons.AutoMirrored.Filled.DriveFileMove) {
                    playlistPendingMove = playlist
                })
                add(LibraryItemAction("Rename", Icons.Filled.Edit) {
                    playlistPendingRename = playlist
                })
                add(LibraryItemAction("Export as M3U8", Icons.Filled.Share) {
                    onExportPlaylistClick(playlist)
                })
                add(LibraryItemAction("Delete", Icons.Filled.Delete, isDestructive = true) {
                    playlistPendingDelete = playlist
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

    fun showFolderActions(folder: PlaylistFolder) {
        actionSheetTarget = LibraryItemActionSheetTarget(
            title = folder.name,
            subtitle = folderPlaylistCountText(folder.playlistCount),
            artworkUri = null,
            artworkDescription = "Folder ${folder.name}",
            actions = listOf(
                LibraryItemAction("Rename folder", Icons.Filled.Edit) {
                    folderPendingRename = folder
                },
                LibraryItemAction("Delete folder", Icons.Filled.Delete, isDestructive = true) {
                    folderPendingDelete = folder
                }
            ),
            artworkContent = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = AppShellAccent
                    )
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (currentFolder == null) 16.dp else 4.dp,
                    top = 12.dp,
                    end = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (currentFolder != null) {
                IconButton(onClick = { onFolderSelected(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentFolder?.name ?: "Your playlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentFolder?.let { folderPlaylistCountText(it.playlistCount) }
                        ?: rootCollectionCountText(folders.size, visiblePlaylists.size),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort playlists by ${sortField.label}",
                        tint = AppShellAccent
                    )
                }
                DropdownMenu(sortMenuExpanded, { sortMenuExpanded = false }) {
                    PlaylistSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field.label) },
                            leadingIcon = {
                                if (field == sortField) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                }
                            },
                            onClick = {
                                sortFieldName = field.name
                                sortMenuExpanded = false
                            }
                        )
                    }

                    HorizontalDivider()

                    val directionTitle = if (
                        sortDirection == LibrarySortDirection.ASCENDING
                    ) {
                        "Ascending"
                    } else {
                        "Descending"
                    }
                    DropdownMenuItem(
                        text = { Text(directionTitle) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (
                                    sortDirection == LibrarySortDirection.ASCENDING
                                ) {
                                    Icons.Filled.ArrowUpward
                                } else {
                                    Icons.Filled.ArrowDownward
                                },
                                contentDescription = "$directionTitle playlist sort direction",
                                tint = AppShellAccent
                            )
                        },
                        onClick = {
                            sortDirectionName = sortDirection.toggled().name
                            sortMenuExpanded = false
                        }
                    )
                }
            }

            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Playlist actions")
                }
                DropdownMenu(overflowExpanded, { overflowExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Create folder") },
                        leadingIcon = {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        },
                        onClick = {
                            overflowExpanded = false
                            createFolderDialogVisible = true
                        }
                    )
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
                onClick = { onCreatePlaylistClick(selectedFolderId) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "New", modifier = Modifier.padding(start = 6.dp))
            }
        }

        val collectionIsEmpty = visiblePlaylists.isEmpty() &&
            (currentFolder != null || folders.isEmpty())
        if (collectionIsEmpty) {
            PlaylistCollectionEmptyState(inFolder = currentFolder != null)
        } else if (viewMode == LibraryViewMode.LIST) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = bottomContentPadding + 8.dp)
            ) {
                if (currentFolder == null) {
                    items(
                        items = folders,
                        key = { folder -> playlistFolderLazyListKey(folder.folderId) }
                    ) { folder ->
                        PlaylistFolderRow(
                            folder = folder,
                            onClick = { onFolderSelected(folder.folderId) },
                            onMoreClick = { showFolderActions(folder) }
                        )
                    }
                }
                items(
                    items = visiblePlaylists,
                    key = { playlist -> playlistLazyListKey(playlist.playlistId) }
                ) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onMoreClick = { showPlaylistActions(playlist) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(PlaylistGridLayout.minimumTileWidth),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = bottomContentPadding + 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentFolder == null) {
                    gridItems(
                        items = folders,
                        key = { folder -> playlistFolderLazyListKey(folder.folderId) }
                    ) { folder ->
                        PlaylistFolderGridTile(
                            folder = folder,
                            onClick = { onFolderSelected(folder.folderId) },
                            onMoreClick = { showFolderActions(folder) }
                        )
                    }
                }
                gridItems(
                    items = visiblePlaylists,
                    key = { playlist -> playlistLazyListKey(playlist.playlistId) }
                ) { playlist ->
                    PlaylistGridTile(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onMoreClick = { showPlaylistActions(playlist) }
                    )
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

    playlistPendingMove?.let { playlist ->
        MovePlaylistToFolderDialog(
            playlist = playlist,
            folders = folders,
            onDismiss = { playlistPendingMove = null },
            onFolderSelected = { folderId ->
                onMovePlaylistClick(playlist, folderId)
                playlistPendingMove = null
            }
        )
    }

    if (createFolderDialogVisible) {
        PlaylistFolderNameDialog(
            title = "Create Folder",
            confirmButtonText = "Create",
            existingNames = folders.map(PlaylistFolder::name),
            onDismiss = { createFolderDialogVisible = false },
            onConfirmClick = { name ->
                onCreateFolderClick(name)
                createFolderDialogVisible = false
            }
        )
    }

    folderPendingRename?.let { folder ->
        PlaylistFolderNameDialog(
            title = "Rename Folder",
            confirmButtonText = "Rename",
            existingNames = folders.map(PlaylistFolder::name),
            initialName = folder.name,
            originalName = folder.name,
            onDismiss = { folderPendingRename = null },
            onConfirmClick = { name ->
                onRenameFolderClick(folder, name)
                folderPendingRename = null
            }
        )
    }

    folderPendingDelete?.let { folder ->
        DeletePlaylistFolderDialog(
            folder = folder,
            onDismiss = { folderPendingDelete = null },
            onConfirm = {
                if (selectedFolderId == folder.folderId) onFolderSelected(null)
                onDeleteFolderClick(folder)
                folderPendingDelete = null
            }
        )
    }
}

@Composable
private fun PlaylistFolderRow(
    folder: PlaylistFolder,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .libraryItemActions(
                clickLabel = "Open ${folder.name}",
                onClick = onClick,
                onShowActions = onMoreClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = AppShellAccent
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = AppShellTypography.FeaturedSongTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = folderPlaylistCountText(folder.playlistCount),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${folder.name}")
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .libraryItemActions(
                clickLabel = "Open ${playlist.name}",
                onClick = onClick,
                onShowActions = onMoreClick
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
                if (playlist.type == PlaylistType.SMART) {
                    Text(
                        text = playlistCollectionKindText(playlist),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppShellAccent
                    )
                }
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options for ${playlist.name}"
                )
            }
        }
    }
}

@Composable
private fun PlaylistFolderGridTile(
    folder: PlaylistFolder,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .libraryItemActions(
                clickLabel = "Open ${folder.name}",
                onClick = onClick,
                onShowActions = onMoreClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = AppShellAccent
                )
                IconButton(onClick = onMoreClick, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${folder.name}")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlaylistGridLayout.metadataHeight)
                    .padding(top = 8.dp)
            ) {
                Text(
                    folder.name,
                    style = AppShellTypography.FeaturedSongTitle,
                    maxLines = PlaylistGridLayout.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    folderPlaylistCountText(folder.playlistCount),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = PlaylistGridLayout.secondaryMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistGridTile(
    playlist: Playlist,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .libraryItemActions(
                clickLabel = "Open ${playlist.name}",
                onClick = onClick,
                onShowActions = onMoreClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                PlaylistArtwork(
                    playlist = playlist,
                    contentDescription = "Artwork for ${playlist.name}",
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(onClick = onMoreClick, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${playlist.name}")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlaylistGridLayout.metadataHeight)
                    .padding(top = 8.dp)
            ) {
                Text(
                    playlist.name,
                    style = AppShellTypography.FeaturedSongTitle,
                    maxLines = PlaylistGridLayout.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playlistGridMetadataText(playlist),
                    style = AppShellTypography.SongSubtitle,
                    color = if (playlist.type == PlaylistType.SMART) {
                        AppShellAccent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = PlaylistGridLayout.secondaryMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistCollectionEmptyState(inFolder: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (inFolder) Icons.Filled.Folder else AppShellIcons.AlbumStack,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (inFolder) "No playlists in this folder" else "No playlists yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (inFolder) "Move a playlist here from its action menu."
                else "Create one to start building your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun folderPlaylistCountText(count: Int): String =
    if (count == 1) "1 playlist" else "$count playlists"

private fun rootCollectionCountText(folderCount: Int, playlistCount: Int): String = buildString {
    append(folderPlaylistCountText(playlistCount))
    if (folderCount > 0) append(" • $folderCount folder${if (folderCount == 1) "" else "s"}")
}

internal fun playlistCollectionKindText(playlist: Playlist): String =
    when {
        playlist.type != PlaylistType.SMART -> "Manual"
        playlist.membershipBehavior ==
            io.github.rsgarrido.sazanami.data.PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT &&
            playlist.generatedLastRefreshedAt != null ->
            "Smart • Updated ${relativeUpdatedText(playlist.generatedLastRefreshedAt)}"
        else -> "Smart"
    }

internal fun playlistGridMetadataText(
    playlist: Playlist,
    now: Long = System.currentTimeMillis()
): String = when {
    playlist.type != PlaylistType.SMART -> playlistMetadataText(playlist)
    playlist.membershipBehavior == PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT &&
        playlist.generatedLastRefreshedAt != null ->
        "Smart \u2022 Updated ${compactRelativeUpdatedText(playlist.generatedLastRefreshedAt, now)}"
    else -> "Smart"
}

internal fun compactRelativeUpdatedText(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        else -> "${days}d"
    }
}

internal enum class PlaylistSortField(
    val label: String
) {
    NAME("Name"),
    CREATED("Created"),
    MODIFIED("Modified");

    fun sort(
        playlists: List<Playlist>,
        direction: LibrarySortDirection
    ): List<Playlist> = playlists.sortedWith { left, right ->
        val fieldComparison = when (this) {
            NAME -> compareLibraryText(left.name, right.name, direction)
            CREATED -> compareKnownPositiveLong(left.createdAt, right.createdAt, direction)
            MODIFIED -> compareKnownPositiveLong(left.modifiedAt, right.modifiedAt, direction)
        }
        fieldComparison.takeUnless { it == 0 }
            ?: left.playlistId.compareTo(right.playlistId)
    }
}
