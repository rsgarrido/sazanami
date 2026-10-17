package io.github.rsgarrido.sazanami.ui.playlist

import io.github.rsgarrido.sazanami.ui.BoundedArtworkFrame

import io.github.rsgarrido.sazanami.ui.readableContentWidth
import android.util.Log
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalResources
import io.github.rsgarrido.sazanami.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.ui.library.playlistQueueActions
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistArtworkMode
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.PlaylistType
import io.github.rsgarrido.sazanami.data.PlaylistMembershipBehavior
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.home.LocalHomePinUi
import io.github.rsgarrido.sazanami.ui.library.LibraryDetailAction
import io.github.rsgarrido.sazanami.ui.library.LibraryDetailTopBar
import io.github.rsgarrido.sazanami.ui.library.LibraryItemAction
import io.github.rsgarrido.sazanami.ui.library.LibraryItemActionSheet
import io.github.rsgarrido.sazanami.ui.library.LibraryItemActionSheetTarget
import io.github.rsgarrido.sazanami.ui.library.playlistDetailSharedArtworkKey
import io.github.rsgarrido.sazanami.ui.library.LocalLibraryQueueUi
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.ResetLazyListOnSortChange
import io.github.rsgarrido.sazanami.ui.library.librarySharedArtwork

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
    val resources = LocalResources.current
    val homePinUi = LocalHomePinUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    var actionSheetTarget by remember { mutableStateOf<LibraryItemActionSheetTarget?>(null) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var addSongsVisible by remember { mutableStateOf(false) }
    var movePlaylistVisible by remember { mutableStateOf(false) }
    var isEditingOrder by remember { mutableStateOf(false) }
    var smartEditorData by remember(playlist.playlistId) { mutableStateOf<io.github.rsgarrido.sazanami.controller.SmartPlaylistUiData?>(null) }
    var smartActionError by remember(playlist.playlistId) { mutableStateOf<Int?>(null) }
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
            subtitle = playlistMetadataText(resources, playlist),
            artworkUri = null,
            artworkDescription = resources.getString(R.string.playlist_artwork_for, playlist.name),
            actions = buildList {
                add(homePinUi.actionForPlaylist(playlist))
                addAll(playlistQueueActions(playlist, libraryQueueUi, onAddPlaylistToQueueClick, resources::getString))
                if (!isLoading && allowsManualPlaylistActions(playlist)) {
                    add(LibraryItemAction(resources.getString(R.string.playlist_add_songs_title), Icons.AutoMirrored.Filled.PlaylistAdd) {
                        addSongsVisible = true
                    })
                    add(LibraryItemAction(resources.getString(R.string.playlist_edit_order), Icons.Filled.DragHandle) {
                        sortFieldName = PlaylistSongSortField.CUSTOM.name
                        isEditingOrder = true
                    })
                }
                if (playlist.membershipBehavior == PlaylistMembershipBehavior.USER_SMART_LIVE) {
                    add(LibraryItemAction(resources.getString(R.string.smart_playlist_edit_action), Icons.Filled.AutoAwesome) {
                        smartActionError = null
                        smartUi.onLoad(playlist.playlistId) { result ->
                            result.onSuccess { smartEditorData = it }
                                .onFailure { failure ->
                                    Log.w("SmartPlaylist", "Unable to load rules", failure)
                                    smartActionError = R.string.smart_playlist_load_rules_failed
                                }
                        }
                    })
                }
                if (playlist.membershipBehavior == PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT) {
                    add(LibraryItemAction(resources.getString(R.string.smart_playlist_refresh_action), Icons.Filled.Refresh) {
                        isRefreshingSnapshot = true
                        smartActionError = null
                        smartUi.onRefresh(playlist.playlistId) { result ->
                            isRefreshingSnapshot = false
                            result.onFailure { failure ->
                                Log.w("SmartPlaylist", "Unable to refresh playlist", failure)
                                smartActionError = R.string.smart_playlist_refresh_failed
                            }
                        }
                    })
                }
                add(LibraryItemAction(resources.getString(R.string.playlist_rename_action), Icons.Filled.Edit) {
                    renameDialogVisible = true
                })
                add(LibraryItemAction(resources.getString(R.string.playlist_move_to_folder), Icons.AutoMirrored.Filled.DriveFileMove) {
                    movePlaylistVisible = true
                })
                add(LibraryItemAction(resources.getString(R.string.playlist_change_artwork), Icons.Filled.Image) {
                    onChangeArtworkClick(playlist)
                })
                if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
                    add(LibraryItemAction(resources.getString(R.string.playlist_reset_artwork), Icons.Filled.Restore) {
                        onResetArtworkClick(playlist)
                    })
                }
                add(LibraryItemAction(resources.getString(R.string.playlist_export_m3u8), Icons.Filled.Share) {
                    onExportPlaylistClick(playlist)
                })
                add(LibraryItemAction(resources.getString(R.string.playlist_delete_action), Icons.Filled.Delete, isDestructive = true) {
                    deleteDialogVisible = true
                })
            },
            artworkContent = {
                PlaylistArtwork(
                    playlist = playlist,
                    contentDescription = stringResource(R.string.playlist_artwork_for, playlist.name),
                    modifier = Modifier.fillMaxSize(),
                    variant = VisualAssetVariant.DISPLAY
                )
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().readableContentWidth()) {
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
                            text = stringResource(R.string.playlist_done_uppercase),
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

            else -> PlaylistSongList(
                playlistSongs = if (isLoading) emptyList() else displayedSongs,
                playlistSongRows = if (isLoading) emptyList() else displayedRows,
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
                        hasSongs = !isLoading && displayedSongs.isNotEmpty(),
                        sortField = sortField,
                        sortDirection = sortDirection,
                        onSortFieldSelected = { field -> sortFieldName = field.name },
                        onSortDirectionToggle = {
                            sortDirectionName = sortDirection.toggled().name
                        },
                        onPlayClick = {
                            if (!isLoading) onPlayAllClick(displayedSongs)
                        },
                        onShuffleClick = {
                            if (!isLoading) onShuffleAllClick(displayedSongs)
                        },
                        isRefreshingSnapshot = !isLoading && isRefreshingSnapshot,
                        onRefreshClick = if (!isLoading &&
                            playlist.membershipBehavior == PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT
                        ) {
                            {
                                isRefreshingSnapshot = true
                                smartActionError = null
                                smartUi.onRefresh(playlist.playlistId) { result ->
                                    isRefreshingSnapshot = false
                                    result.onFailure { failure ->
                                        Log.w("SmartPlaylist", "Unable to refresh playlist", failure)
                                        smartActionError = R.string.smart_playlist_refresh_failed
                                    }
                                }
                            }
                        } else null
                    )
                    playlist.smartResolutionError?.let {
                        Text(
                            text = stringResource(R.string.smart_playlist_unsupported_rules),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    smartActionError?.let { errorRes ->
                        Text(
                            text = stringResource(errorRes),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                },
                emptyContent = {
                    if (isLoading) {
                        PlaylistLoadingState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp)
                        )
                    } else {
                        PlaylistDetailEmptyState(playlist = playlist)
                    }
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
            title = stringResource(R.string.playlist_rename_title),
            confirmButtonText = stringResource(R.string.playlist_rename_action),
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
        BoundedArtworkFrame(fraction = 0.72f, maximum = 320.dp) { artworkModifier ->
            PlaylistArtwork(
                playlist = playlist,
                contentDescription = stringResource(R.string.playlist_artwork_for, playlist.name),
                modifier = artworkModifier
                    .librarySharedArtwork(
                        playlistDetailSharedArtworkKey(playlist.playlistId)
                    ),
                variant = VisualAssetVariant.DISPLAY
            )
        }

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
                text = playlistMetadataText(LocalResources.current, playlist),
                style = AppShellTypography.SongSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (playlist.membershipBehavior == PlaylistMembershipBehavior.MANUAL)
                    stringResource(R.string.playlist_kind_manual) else playlistKindText(playlist),
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
                label = stringResource(R.string.playlist_play),
                enabled = hasSongs,
                onClick = onPlayClick
            )
            LibraryDetailAction(
                icon = Icons.Filled.Shuffle,
                label = stringResource(R.string.playlist_shuffle),
                enabled = hasSongs,
                onClick = onShuffleClick
            )
            onRefreshClick?.let { refresh ->
                LibraryDetailAction(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(if (isRefreshingSnapshot) R.string.smart_playlist_refreshing else R.string.smart_playlist_refresh_action),
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
                text = stringResource(R.string.playlist_songs_heading),
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
                    Text(stringResource(sortField.labelRes))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    PlaylistSongSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(stringResource(field.labelRes)) },
                            leadingIcon = {
                                if (field == sortField) {
                                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.playlist_selected))
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
                                    contentDescription =
                                        stringResource(R.string.playlist_song_sort_direction_description, directionTitle),
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
            text = if (playlist.smartResolutionError != null) {
                stringResource(R.string.smart_playlist_unsupported_rules)
            } else if (playlist.songCount == 0) {
                if (playlist.type == PlaylistType.SMART) {
                    stringResource(R.string.smart_playlist_no_matching_songs)
                } else {
                    stringResource(R.string.playlist_empty_detail)
                }
            } else {
                stringResource(R.string.playlist_songs_unavailable)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun playlistKindText(playlist: Playlist): String = when (playlist.membershipBehavior) {
    PlaylistMembershipBehavior.MANUAL -> stringResource(playlistKindBaseRes(playlist.membershipBehavior))
    PlaylistMembershipBehavior.USER_SMART_LIVE,
    PlaylistMembershipBehavior.GENERATED_SMART_LIVE -> stringResource(playlistKindBaseRes(playlist.membershipBehavior))
    PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT -> playlist.generatedLastRefreshedAt?.let {
        val age = relativePlaylistAge(it)
        when (age.unit) {
            PlaylistAgeUnit.NOW -> stringResource(R.string.smart_playlist_updated_just_now)
            PlaylistAgeUnit.MINUTE -> pluralStringResource(R.plurals.smart_playlist_updated_minutes, age.count, age.count)
            PlaylistAgeUnit.HOUR -> pluralStringResource(R.plurals.smart_playlist_updated_hours, age.count, age.count)
            PlaylistAgeUnit.DAY -> pluralStringResource(R.plurals.smart_playlist_updated_days, age.count, age.count)
        }
    } ?: stringResource(R.string.smart_playlist_kind)
}

internal fun playlistKindBaseRes(behavior: PlaylistMembershipBehavior): Int = when (behavior) {
    PlaylistMembershipBehavior.MANUAL -> R.string.playlist_kind_manual
    PlaylistMembershipBehavior.USER_SMART_LIVE,
    PlaylistMembershipBehavior.GENERATED_SMART_LIVE -> R.string.smart_playlist_updates_automatically
    PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT -> R.string.smart_playlist_kind
}

internal fun allowsManualPlaylistActions(playlist: Playlist): Boolean =
    playlist.type == PlaylistType.MANUAL &&
        playlist.membershipBehavior == PlaylistMembershipBehavior.MANUAL

internal enum class PlaylistAgeUnit { NOW, MINUTE, HOUR, DAY }

internal data class RelativePlaylistAge(val unit: PlaylistAgeUnit, val count: Int = 0)

internal fun relativePlaylistAge(timestamp: Long, now: Long = System.currentTimeMillis()): RelativePlaylistAge {
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> RelativePlaylistAge(PlaylistAgeUnit.NOW)
        minutes < 60L -> RelativePlaylistAge(PlaylistAgeUnit.MINUTE, minutes.toInt())
        hours < 24L -> RelativePlaylistAge(PlaylistAgeUnit.HOUR, hours.toInt())
        else -> RelativePlaylistAge(PlaylistAgeUnit.DAY, days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
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
                text = stringResource(R.string.playlist_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
