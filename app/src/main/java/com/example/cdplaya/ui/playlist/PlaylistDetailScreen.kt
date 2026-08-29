package com.example.cdplaya.ui.playlist

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.cdplaya.data.PlaylistFolder
import com.example.cdplaya.data.PlaylistArtworkMode
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.PlaylistType
import com.example.cdplaya.data.PlaylistMembershipBehavior
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.visual.VisualAssetVariant
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.home.LocalHomePinUi
import com.example.cdplaya.ui.library.LibraryDetailAction
import com.example.cdplaya.ui.library.LibraryDetailTopBar
import com.example.cdplaya.ui.library.LibraryItemAction
import com.example.cdplaya.ui.library.LibraryItemActionSheet
import com.example.cdplaya.ui.library.LibraryItemActionSheetTarget
import com.example.cdplaya.ui.library.LibrarySortDirection
import com.example.cdplaya.ui.library.ResetLazyListOnSortChange

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allPlaylists: List<Playlist>,
    playlistFolders: List<PlaylistFolder>,
    allSongs: List<Song>,
    playlistSongRows: List<PlaylistSong>,
    isLoading: Boolean,
    currentSongId: Long?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onBackClick: () -> Unit,
    onPlayAllClick: (List<Song>) -> Unit,
    onShuffleAllClick: (List<Song>) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onAddPlaylistToQueueClick: (Playlist) -> Unit,
    onChangeArtworkClick: (Playlist) -> Unit,
    onResetArtworkClick: (Playlist) -> Unit,
    onMovePlaylistClick: (Playlist, Long?) -> Unit,
    onAddSongsClick: (List<Song>) -> Unit,
    onReorderPlaylistSongs: (Long, List<Long>) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val homePinUi = LocalHomePinUi.current
    var actionSheetTarget by remember { mutableStateOf<LibraryItemActionSheetTarget?>(null) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var addSongsVisible by remember { mutableStateOf(false) }
    var movePlaylistVisible by remember { mutableStateOf(false) }
    var isEditingOrder by remember { mutableStateOf(false) }
    var smartEditorData by remember(playlist.playlistId) { mutableStateOf<com.example.cdplaya.controller.SmartPlaylistUiData?>(null) }
    var smartActionError by remember(playlist.playlistId) { mutableStateOf<String?>(null) }
    var isRefreshingSnapshot by remember(playlist.playlistId) { mutableStateOf(false) }
    val smartUi = LocalSmartPlaylistUi.current
    var sortFieldName by rememberSaveable(playlist.playlistId) {
        mutableStateOf(PlaylistSongSortField.CUSTOM.name)
    }
    var sortDirectionName by rememberSaveable(playlist.playlistId) {
        mutableStateOf(LibrarySortDirection.ASCENDING.name)
    }
    val sortField = PlaylistSongSortField.valueOf(sortFieldName)
    val sortDirection = LibrarySortDirection.valueOf(sortDirectionName)
    val listState = rememberLazyListState()
    ResetLazyListOnSortChange(sortField to sortDirection, listState)
    val showCompactTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val displayedRows = remember(playlistSongRows, sortField, sortDirection) {
        sortField.sort(playlistSongRows, sortDirection).filter { it.resolvedSong != null }
    }
    val displayedSongs = remember(displayedRows) {
        displayedRows.mapNotNull(PlaylistSong::resolvedSong)
    }

    BackHandler(enabled = isEditingOrder) {
        isEditingOrder = false
    }

    fun showPlaylistActions() {
        actionSheetTarget = LibraryItemActionSheetTarget(
            title = playlist.name,
            subtitle = playlistMetadataText(playlist),
            artworkUri = null,
            artworkDescription = "Artwork for ${playlist.name}",
            actions = buildList {
                add(homePinUi.actionForPlaylist(playlist))
                add(LibraryItemAction("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) {
                    onAddPlaylistToQueueClick(playlist)
                })
                if (!isLoading && allowsManualPlaylistActions(playlist)) {
                    add(LibraryItemAction("Add songs", Icons.AutoMirrored.Filled.PlaylistAdd) {
                        addSongsVisible = true
                    })
                    add(LibraryItemAction("Edit order", Icons.Filled.DragHandle) {
                        sortFieldName = PlaylistSongSortField.CUSTOM.name
                        isEditingOrder = true
                    })
                }
                if (playlist.membershipBehavior == PlaylistMembershipBehavior.USER_SMART_LIVE) {
                    add(LibraryItemAction("Edit Smart Playlist", Icons.Filled.AutoAwesome) {
                        smartActionError = null
                        smartUi.onLoad(playlist.playlistId) { result ->
                            result.onSuccess { smartEditorData = it }
                                .onFailure { smartActionError = it.message ?: "Unable to load Smart Playlist rules." }
                        }
                    })
                }
                if (playlist.membershipBehavior == PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT) {
                    add(LibraryItemAction("Refresh", Icons.Filled.Refresh) {
                        isRefreshingSnapshot = true
                        smartActionError = null
                        smartUi.onRefresh(playlist.playlistId) { result ->
                            isRefreshingSnapshot = false
                            result.onFailure { smartActionError = it.message ?: "Unable to refresh this playlist." }
                        }
                    })
                }
                add(LibraryItemAction("Rename", Icons.Filled.Edit) {
                    renameDialogVisible = true
                })
                add(LibraryItemAction("Move to folder", Icons.AutoMirrored.Filled.DriveFileMove) {
                    movePlaylistVisible = true
                })
                add(LibraryItemAction("Change artwork", Icons.Filled.Image) {
                    onChangeArtworkClick(playlist)
                })
                if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
                    add(LibraryItemAction("Reset to automatic artwork", Icons.Filled.Restore) {
                        onResetArtworkClick(playlist)
                    })
                }
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
                    modifier = Modifier.fillMaxSize(),
                    variant = VisualAssetVariant.DISPLAY
                )
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        LibraryDetailTopBar(
            title = playlist.name,
            showTitle = isLoading || isEditingOrder || showCompactTitle,
            containerColor = if (isLoading || isEditingOrder || showCompactTitle) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            } else {
                Color.Transparent
            },
            onBackClick = {
                if (isEditingOrder) isEditingOrder = false else onBackClick()
            },
            onMoreClick = ::showPlaylistActions,
            trailingContent = if (isEditingOrder) {
                {
                    TextButton(onClick = { isEditingOrder = false }) {
                        Text(
                            text = "DONE",
                            style = AppShellTypography.CompactAction,
                            color = AppShellAccent
                        )
                    }
                }
            } else {
                null
            }
        )

        when {
            isEditingOrder -> PlaylistReorderSongList(
                playlistSongRows = playlistSongRows,
                onOrderCommitted = { orderedIds ->
                    onReorderPlaylistSongs(playlist.playlistId, orderedIds)
                },
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier.fillMaxSize()
            )

            isLoading -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "playlist-loading-header") {
                    PlaylistDetailHero(
                        playlist = playlist,
                        hasSongs = false,
                        sortField = sortField,
                        sortDirection = sortDirection,
                        onSortFieldSelected = { field -> sortFieldName = field.name },
                        onSortDirectionToggle = {
                            sortDirectionName = sortDirection.toggled().name
                        },
                        onPlayClick = {},
                        onShuffleClick = {},
                        isRefreshingSnapshot = false,
                        onRefreshClick = null
                    )
                }
                item(key = "playlist-loading-progress") {
                    PlaylistLoadingState(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                    )
                }
            }

            else -> PlaylistSongList(
                playlistSongs = displayedSongs,
                playlistSongRows = displayedRows,
                currentSongId = currentSongId,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onRemovePlaylistSongClick = onRemovePlaylistSongClick,
                allowManualRemoval = allowsManualPlaylistActions(playlist),
                onEditSongTagsClick = onEditSongTagsClick,
                listState = listState,
                headerContent = {
                    PlaylistDetailHero(
                        playlist = playlist,
                        hasSongs = displayedSongs.isNotEmpty(),
                        sortField = sortField,
                        sortDirection = sortDirection,
                        onSortFieldSelected = { field -> sortFieldName = field.name },
                        onSortDirectionToggle = {
                            sortDirectionName = sortDirection.toggled().name
                        },
                        onPlayClick = { onPlayAllClick(displayedSongs) },
                        onShuffleClick = { onShuffleAllClick(displayedSongs) },
                        isRefreshingSnapshot = isRefreshingSnapshot,
                        onRefreshClick = if (
                            playlist.membershipBehavior == PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT
                        ) {
                            {
                                isRefreshingSnapshot = true
                                smartActionError = null
                                smartUi.onRefresh(playlist.playlistId) { result ->
                                    isRefreshingSnapshot = false
                                    result.onFailure {
                                        smartActionError = it.message ?: "Unable to refresh this playlist."
                                    }
                                }
                            }
                        } else null
                    )
                    playlist.smartResolutionError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    smartActionError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                },
                emptyContent = {
                    PlaylistDetailEmptyState(
                        playlist = playlist
                    )
                },
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    actionSheetTarget?.let { target ->
        LibraryItemActionSheet(
            target = target,
            onDismissRequest = { actionSheetTarget = null }
        )
    }

    if (addSongsVisible) {
        PlaylistAddSongsScreen(
            playlistName = playlist.name,
            allSongs = allSongs,
            playlistSongRows = playlistSongRows,
            onDismiss = { addSongsVisible = false },
            onAddSongs = onAddSongsClick
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

    if (movePlaylistVisible) {
        MovePlaylistToFolderDialog(
            playlist = playlist,
            folders = playlistFolders,
            onDismiss = { movePlaylistVisible = false },
            onFolderSelected = { folderId ->
                onMovePlaylistClick(playlist, folderId)
                movePlaylistVisible = false
            }
        )
    }

    smartEditorData?.let { data ->
        SmartPlaylistEditor(
            request = SmartPlaylistEditorRequest(
                folderId = playlist.folderId,
                playlistId = playlist.playlistId,
                originalName = playlist.name,
                model = SmartPlaylistEditorModel.fromDraft(playlist.name, data.definition.draft)
            ),
            existingNames = allPlaylists.map(Playlist::name),
            onDismiss = { smartEditorData = null },
            onSaved = { savedName ->
                if (savedName != playlist.name) onRenamePlaylistClick(playlist, savedName)
                smartEditorData = null
            }
        )
    }
}

@Composable
private fun PlaylistDetailHero(
    playlist: Playlist,
    hasSongs: Boolean,
    sortField: PlaylistSongSortField,
    sortDirection: LibrarySortDirection,
    onSortFieldSelected: (PlaylistSongSortField) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    isRefreshingSnapshot: Boolean,
    onRefreshClick: (() -> Unit)?
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        PlaylistArtwork(
            playlist = playlist,
            contentDescription = "Artwork for ${playlist.name}",
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .widthIn(max = 320.dp)
                .aspectRatio(1f),
            variant = VisualAssetVariant.DISPLAY
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
                text = playlistKindText(playlist),
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
            onRefreshClick?.let { refresh ->
                LibraryDetailAction(
                    icon = Icons.Filled.Refresh,
                    label = if (isRefreshingSnapshot) "Refreshing" else "Refresh",
                    enabled = !isRefreshingSnapshot,
                    onClick = refresh
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Songs",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box {
                TextButton(
                    onClick = { sortMenuExpanded = true }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(sortField.label)
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    PlaylistSongSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field.label) },
                            leadingIcon = {
                                if (field == sortField) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                }
                            },
                            onClick = {
                                onSortFieldSelected(field)
                                sortMenuExpanded = false
                            }
                        )
                    }
                    if (sortField != PlaylistSongSortField.CUSTOM) {
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
                                    contentDescription =
                                        "$directionTitle playlist song sort direction",
                                    tint = AppShellAccent
                                )
                            },
                            onClick = {
                                onSortDirectionToggle()
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailEmptyState(
    playlist: Playlist
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = playlist.smartResolutionError ?: if (playlist.songCount == 0) {
                if (playlist.type == PlaylistType.SMART) {
                    "No songs currently match this Smart Playlist."
                } else {
                    "This playlist is empty."
                }
            } else {
                "The songs in this playlist are not currently available on this device."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

internal fun playlistKindText(playlist: Playlist): String = when (playlist.membershipBehavior) {
    PlaylistMembershipBehavior.MANUAL -> "Manual playlist"
    PlaylistMembershipBehavior.USER_SMART_LIVE -> "Smart Playlist • Updates automatically"
    PlaylistMembershipBehavior.GENERATED_SMART_LIVE -> "Smart Playlist • Updates automatically"
    PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT -> buildString {
        append("Smart Playlist")
        playlist.generatedLastRefreshedAt?.let { refreshedAt ->
            append(" • Updated ")
            append(relativeUpdatedText(refreshedAt))
        }
    }
}

internal fun allowsManualPlaylistActions(playlist: Playlist): Boolean =
    playlist.type == PlaylistType.MANUAL &&
        playlist.membershipBehavior == PlaylistMembershipBehavior.MANUAL

internal fun relativeUpdatedText(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        hours < 24L -> "$hours hr ago"
        else -> "$days day${if (days == 1L) "" else "s"} ago"
    }
}

@Composable
private fun PlaylistLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
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
    }
}
