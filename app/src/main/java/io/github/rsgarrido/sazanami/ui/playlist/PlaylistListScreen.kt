package io.github.rsgarrido.sazanami.ui.playlist

import io.github.rsgarrido.sazanami.ui.readableContentWidth
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import io.github.rsgarrido.sazanami.ui.library.collectionGridMinimumWidth
import androidx.compose.ui.res.pluralStringResource
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
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkKey
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkSource
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkSourceScope
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkSourceSlotTreatment
import io.github.rsgarrido.sazanami.ui.library.LocalLibraryQueueUi
import io.github.rsgarrido.sazanami.ui.library.libraryItemActions
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.library.LibraryLayoutMotionDurationMillis
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
    val resources = LocalResources.current
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
            subtitle = playlistMetadataText(resources, playlist),
            artworkUri = null,
            artworkDescription = resources.getString(R.string.playlist_artwork_for, playlist.name),
            actions = buildList {
                add(homePinUi.actionForPlaylist(playlist))
                addAll(playlistQueueActions(playlist, libraryQueueUi, onAddPlaylistToQueueClick, resources::getString))
                add(LibraryItemAction(resources.getString(R.string.playlist_change_artwork), Icons.Filled.Image) {
                    onChangeArtworkClick(playlist)
                })
                if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
                    add(LibraryItemAction(resources.getString(R.string.playlist_reset_artwork), Icons.Filled.Restore) {
                        onResetArtworkClick(playlist)
                    })
                }
                add(LibraryItemAction(resources.getString(R.string.playlist_move_to_folder), Icons.AutoMirrored.Filled.DriveFileMove) {
                    playlistPendingMove = playlist
                })
                add(LibraryItemAction(resources.getString(R.string.playlist_rename_action), Icons.Filled.Edit) {
                    playlistPendingRename = playlist
                })
                add(LibraryItemAction(resources.getString(R.string.playlist_export_m3u8), Icons.Filled.Share) {
                    onExportPlaylistClick(playlist)
                })
                add(LibraryItemAction(resources.getString(R.string.playlist_delete_action), Icons.Filled.Delete, isDestructive = true) {
                    playlistPendingDelete = playlist
                })
            },
            artworkContent = {
                PlaylistArtwork(
                    playlist = playlist,
                    contentDescription = stringResource(R.string.playlist_artwork_for, playlist.name),
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }

    fun showFolderActions(folder: PlaylistFolder) {
        actionSheetTarget = LibraryItemActionSheetTarget(
            title = folder.name,
            subtitle = folderPlaylistCountText(resources, folder.playlistCount),
            artworkUri = null,
            artworkDescription = resources.getString(R.string.playlist_folder_artwork_for, folder.name),
            actions = listOf(
                LibraryItemAction(resources.getString(R.string.playlist_folder_rename_action), Icons.Filled.Edit) {
                    folderPendingRename = folder
                },
                LibraryItemAction(resources.getString(R.string.playlist_folder_delete_action), Icons.Filled.Delete, isDestructive = true) {
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.playlist_folder_back))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentFolder?.name ?: stringResource(R.string.playlist_your_playlists),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentFolder?.let {
                        pluralStringResource(R.plurals.playlist_folder_count, it.playlistCount, it.playlistCount)
                    } ?: rootCollectionCountText(folders.size, visiblePlaylists.size),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.playlist_sort_by, stringResource(sortField.labelRes)),
                        tint = AppShellAccent
                    )
                }
                DropdownMenu(sortMenuExpanded, { sortMenuExpanded = false }) {
                    PlaylistSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(stringResource(field.labelRes)) },
                            leadingIcon = {
                                if (field == sortField) {
                                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.playlist_selected))
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
                        stringResource(R.string.playlist_sort_ascending)
                    } else {
                        stringResource(R.string.playlist_sort_descending)
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
                                contentDescription = stringResource(R.string.playlist_sort_direction_description, directionTitle),
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
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.playlist_actions))
                }
                DropdownMenu(overflowExpanded, { overflowExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_create_folder)) },
                        leadingIcon = {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        },
                        onClick = {
                            overflowExpanded = false
                            createFolderDialogVisible = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_import_m3u)) },
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
                Text(text = stringResource(R.string.playlist_new), modifier = Modifier.padding(start = 6.dp))
            }
        }

        val collectionIsEmpty = visiblePlaylists.isEmpty() &&
            (currentFolder != null || folders.isEmpty())
        if (collectionIsEmpty) {
            PlaylistCollectionEmptyState(inFolder = currentFolder != null)
        } else if (viewMode == LibraryViewMode.LIST) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).readableContentWidth(),
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
                            onMoreClick = { showFolderActions(folder) },
                            modifier = Modifier.animateItem(
                                placementSpec = tween(
                                    durationMillis = LibraryLayoutMotionDurationMillis,
                                    easing = FastOutSlowInEasing
                                )
                            )
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
                        onMoreClick = { showPlaylistActions(playlist) },
                        modifier = Modifier.animateItem(
                            placementSpec = tween(
                                durationMillis = LibraryLayoutMotionDurationMillis,
                                easing = FastOutSlowInEasing
                            )
                        )
                    )
                }
            }
        } else {
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(collectionGridMinimumWidth(
                        maxWidth, PlaylistGridLayout.minimumTileWidth, fontScale
                    )),
                    modifier = Modifier.fillMaxSize(),
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
                                onMoreClick = { showFolderActions(folder) },
                                modifier = Modifier.animateItem(
                                    placementSpec = tween(
                                        durationMillis = LibraryLayoutMotionDurationMillis,
                                        easing = FastOutSlowInEasing
                                    )
                                )
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
                            onMoreClick = { showPlaylistActions(playlist) },
                            modifier = Modifier.animateItem(
                                placementSpec = tween(
                                    durationMillis = LibraryLayoutMotionDurationMillis,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        )
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
            title = stringResource(R.string.playlist_rename_title),
            confirmButtonText = stringResource(R.string.playlist_rename_action),
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
            title = stringResource(R.string.playlist_folder_create_title),
            confirmButtonText = stringResource(R.string.playlist_create_action),
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
            title = stringResource(R.string.playlist_folder_rename_title),
            confirmButtonText = stringResource(R.string.playlist_rename_action),
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
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .libraryItemActions(
                clickLabel = stringResource(R.string.playlist_open_item, folder.name),
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
                    text = pluralStringResource(R.plurals.playlist_folder_count, folder.playlistCount, folder.playlistCount),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.playlist_more_options_for, folder.name))
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .libraryItemActions(
                clickLabel = stringResource(R.string.playlist_open_item, playlist.name),
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
            LibrarySharedArtworkSource(
                key = LibrarySharedArtworkKey.Playlist(
                    playlistId = playlist.playlistId,
                    sourceScope = LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(72.dp),
                slotTreatment = LibrarySharedArtworkSourceSlotTreatment.SUBDUED_ARTWORK
            ) { artworkModifier ->
                PlaylistArtwork(
                    playlist = playlist,
                    contentDescription = stringResource(R.string.playlist_artwork_for, playlist.name),
                    modifier = artworkModifier
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = AppShellTypography.FeaturedSongTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlistMetadataText(LocalResources.current, playlist),
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
                    contentDescription = stringResource(R.string.playlist_more_options_for, playlist.name)
                )
            }
        }
    }
}

@Composable
private fun PlaylistFolderGridTile(
    folder: PlaylistFolder,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .libraryItemActions(
                clickLabel = stringResource(R.string.playlist_open_item, folder.name),
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
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.playlist_more_options_for, folder.name))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PlaylistGridLayout.metadataHeight * LocalDensity.current.fontScale.coerceAtLeast(1f))
                    .padding(top = 8.dp)
            ) {
                Text(
                    folder.name,
                    style = AppShellTypography.FeaturedSongTitle,
                    maxLines = PlaylistGridLayout.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.playlist_folder_count, folder.playlistCount, folder.playlistCount),
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
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .libraryItemActions(
                clickLabel = stringResource(R.string.playlist_open_item, playlist.name),
                onClick = onClick,
                onShowActions = onMoreClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                LibrarySharedArtworkSource(
                    key = LibrarySharedArtworkKey.Playlist(
                        playlistId = playlist.playlistId,
                        sourceScope = LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    slotTreatment = LibrarySharedArtworkSourceSlotTreatment.SUBDUED_ARTWORK
                ) { artworkModifier ->
                    PlaylistArtwork(
                        playlist = playlist,
                        contentDescription = stringResource(R.string.playlist_artwork_for, playlist.name),
                        modifier = artworkModifier
                    )
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.playlist_more_options_for, playlist.name))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PlaylistGridLayout.metadataHeight * LocalDensity.current.fontScale.coerceAtLeast(1f))
                    .padding(top = 8.dp)
            ) {
                Text(
                    playlist.name,
                    style = AppShellTypography.FeaturedSongTitle,
                    maxLines = PlaylistGridLayout.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playlistGridMetadataText(LocalResources.current, playlist),
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
                if (inFolder) stringResource(R.string.playlist_empty_folder) else stringResource(R.string.playlist_empty_root),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (inFolder) stringResource(R.string.playlist_empty_folder_guidance)
                else stringResource(R.string.playlist_empty_root_guidance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun folderPlaylistCountText(resources: Resources, count: Int): String =
    resources.getQuantityString(R.plurals.playlist_folder_count, count, count)

@Composable
private fun rootCollectionCountText(folderCount: Int, playlistCount: Int): String {
    if (folderCount == 0) {
        return pluralStringResource(R.plurals.playlist_folder_count, playlistCount, playlistCount)
    }
    val pluralRes = if (playlistCount == 1) R.plurals.playlist_root_counts_one_playlist
    else R.plurals.playlist_root_counts_many_playlists
    return pluralStringResource(pluralRes, folderCount, playlistCount, folderCount)
}

@Composable
internal fun playlistCollectionKindText(playlist: Playlist): String =
    when {
        playlist.type != PlaylistType.SMART -> stringResource(playlistCollectionKindRes(playlist))
        playlist.membershipBehavior ==
            io.github.rsgarrido.sazanami.data.PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT &&
            playlist.generatedLastRefreshedAt != null -> {
            val age = relativePlaylistAge(playlist.generatedLastRefreshedAt)
            when (age.unit) {
                PlaylistAgeUnit.NOW -> stringResource(R.string.smart_playlist_collection_updated_now)
                PlaylistAgeUnit.MINUTE -> pluralStringResource(R.plurals.smart_playlist_collection_updated_minutes, age.count, age.count)
                PlaylistAgeUnit.HOUR -> pluralStringResource(R.plurals.smart_playlist_collection_updated_hours, age.count, age.count)
                PlaylistAgeUnit.DAY -> pluralStringResource(R.plurals.smart_playlist_collection_updated_days, age.count, age.count)
            }
        }
        else -> stringResource(playlistCollectionKindRes(playlist))
    }

internal fun playlistGridMetadataText(
    resources: Resources,
    playlist: Playlist,
    now: Long = System.currentTimeMillis()
): String = when {
    playlist.type != PlaylistType.SMART -> playlistMetadataText(resources, playlist)
    playlist.membershipBehavior == PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT &&
        playlist.generatedLastRefreshedAt != null -> {
        val age = relativePlaylistAge(playlist.generatedLastRefreshedAt, now)
        when (age.unit) {
            PlaylistAgeUnit.NOW -> resources.getString(R.string.smart_playlist_grid_updated_now)
            PlaylistAgeUnit.MINUTE -> resources.getQuantityString(R.plurals.smart_playlist_grid_updated_minutes, age.count, age.count)
            PlaylistAgeUnit.HOUR -> resources.getQuantityString(R.plurals.smart_playlist_grid_updated_hours, age.count, age.count)
            PlaylistAgeUnit.DAY -> resources.getQuantityString(R.plurals.smart_playlist_grid_updated_days, age.count, age.count)
        }
    }
    else -> resources.getString(R.string.smart_playlist_collection_smart)
}

internal fun playlistCollectionKindRes(playlist: Playlist): Int =
    if (playlist.type == PlaylistType.SMART) R.string.smart_playlist_collection_smart
    else R.string.smart_playlist_collection_manual

internal enum class PlaylistSortField(
    @StringRes val labelRes: Int
) {
    NAME(R.string.playlist_sort_name),
    CREATED(R.string.playlist_sort_created),
    MODIFIED(R.string.playlist_sort_modified);

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
