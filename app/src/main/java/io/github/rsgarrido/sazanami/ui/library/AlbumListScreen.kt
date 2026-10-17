package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.ui.readableContentWidth

import android.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.ui.home.LocalHomePinUi
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import io.github.rsgarrido.sazanami.R as AppR

@Composable
fun AlbumListScreen(
    songs: List<Song>,
    onAlbumClick: (String) -> Unit,
    onAlbumPlayClick: (String, List<Song>) -> Unit,
    onAlbumShuffleClick: (String, List<Song>) -> Unit,
    onAlbumPlayNextClick: (String, List<Song>) -> Unit,
    onAlbumAddToQueueClick: (String, List<Song>) -> Unit,
    onAlbumAddToPlaylistClick: (String, List<Song>) -> Unit,
    selectionEnabled: Boolean = false,
    searchActive: Boolean = false,
    modifier: Modifier = Modifier,
    sortState: LibrarySortState = LibrarySortState(
        LibrarySortOption.TITLE,
        LibrarySortDirection.ASCENDING
    ),
    listState: LazyListState? = null,
    bottomContentPadding: Dp = 0.dp,
    fastScrollSessionKey: Any? = null
) {
    val resources = LocalResources.current
    val albums = remember(songs, sortState) {
        sortedLibraryAlbumGroups(songs, sortState)
    }
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val homePinUi = LocalHomePinUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    val rememberedListState = rememberLazyListState()
    val activeListState = listState ?: rememberedListState
    val selectionUi = LocalLibrarySelectionUi.current
    val selectionActive = selectionEnabled &&
        selectionUi.state.entity == LibrarySelectionEntity.ALBUM && selectionUi.state.isActive
    val displayedKeys = remember(albums) { albums.map(LibraryAlbumGroup::key) }
    val fallbackAlbums = remember(selectionUi.allSongs, sortState) {
        sortedLibraryAlbumGroups(selectionUi.allSongs, sortState)
    }
    val resolvedSelectedAlbums = {
        resolveSelectedAlbums(selectionUi.state.selectedKeys, albums, fallbackAlbums)
    }
    val resolvedSelectedSongs = { resolvedSelectedAlbums().flatMap(LibraryAlbumGroup::songs) }

    Column(modifier = modifier.fillMaxSize().readableContentWidth()) {
        if (selectionEnabled) {
            LibrarySelectionHeader(
                entity = LibrarySelectionEntity.ALBUM,
                displayedKeys = displayedKeys,
                searchActive = searchActive,
                selectionActionTarget = {
                    val selectedAlbums = resolvedSelectedAlbums()
                    val singleAlbumTarget = selectedAlbums.singleOrNull()?.let { album ->
                        albumActionSheetTarget(
                            albumTitle = album.title,
                            subtitle = album.artistText,
                            artworkUri = album.songs.firstOrNull()?.albumArtUri,
                            albumSongs = album.songs,
                            onPlayClick = onAlbumPlayClick,
                            onShuffleClick = onAlbumShuffleClick,
                            onPlayNextClick = onAlbumPlayNextClick,
                            onAddToQueueClick = onAlbumAddToQueueClick,
                            onAddToPlaylistClick = onAlbumAddToPlaylistClick,
                            homePinAction = homePinUi.actionForAlbum(album),
                            resolveString = resources::getString,
                            resolveArtworkDescription = { resources.getString(AppR.string.library_album_art_for, it) }
                        )
                    }
                    albumSelectionActionSheetTarget(
                        selectedAlbums = selectedAlbums,
                        singleAlbumTarget = singleAlbumTarget,
                        onAddToAnotherQueue = selectionUi.onAddToAnotherQueue,
                        onPlayInNewQueue = selectionUi.onPlayInNewQueue,
                        onClearSelection = selectionUi.onClear,
                        resolveString = resources::getString,
                        resolvePlural = { id, count -> resources.getQuantityString(id, count, count) }
                    )
                }
            )
        }

    LibraryFastScrollViewport(
        state = activeListState,
        enabled = true,
        sessionKey = Triple(sortState, selectionActive, fastScrollSessionKey),
        modifier = Modifier.weight(1f).fillMaxWidth()
    ) {
    LazyColumn(
        state = activeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (selectionActive) 0.dp else bottomContentPadding)
    ) {
        items(
            items = albums,
            key = { album -> album.key }
        ) { album ->
            val firstSong = album.songs.firstOrNull()
            val artworkRequest = rememberLibraryArtworkRequest(
                ownerType = LibraryArtworkOwnerType.ALBUM,
                ownerKey = album.key,
                model = firstSong?.albumArtUri,
                variant = VisualAssetVariant.THUMBNAIL
            )
            val albumSubtitle = pluralStringResource(
                AppR.plurals.library_album_artist_song_count,
                album.songs.size,
                album.artistText,
                album.songs.size
            )
            val isSelectionSelected = selectionActive &&
                album.key in selectionUi.state.selectedKeys

            ListItem(
                leadingContent = {
                    LibrarySharedArtworkSource(
                        key = LibrarySharedArtworkKey.Album(
                            albumKey = album.key,
                            sourceScope =
                                LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(56.dp),
                        slotTreatment =
                            LibrarySharedArtworkSourceSlotTreatment.NEUTRAL_SURFACE,
                        hasResolvedArtwork = artworkRequest != null
                    ) { artworkModifier ->
                        LibraryArtworkImage(
                            model = artworkRequest,
                            unresolvedNull = firstSong.hasUnresolvedLibraryArtwork(),
                            contentDescription = stringResource(AppR.string.library_album_art_for, album.title),
                            modifier = artworkModifier
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_media_play),
                                contentDescription = stringResource(AppR.string.library_album_art_for, album.title),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                },
                headlineContent = {
                    Text(text = album.title)
                },
                supportingContent = {
                    Text(text = albumSubtitle)
                },
                trailingContent = if (isSelectionSelected) ({
                    LibrarySelectionCheckBadge()
                }) else null,
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelectionSelected) {
                        AppShellAccent.copy(alpha = 0.28f)
                    } else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .animateItem(
                        placementSpec = tween(
                            durationMillis = LibraryLayoutMotionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                    .then(
                        if (selectionEnabled) Modifier.librarySelectableItem(
                            clickLabel = stringResource(AppR.string.library_album_open, album.title),
                            selectionActive = selectionActive,
                            selected = isSelectionSelected,
                            onClick = { onAlbumClick(album.key) },
                            onToggleSelection = {
                                selectionUi.onToggle(LibrarySelectionEntity.ALBUM, album.key)
                            },
                            onEnterSelection = {
                                if (selectionActive) {
                                    selectionUi.onToggle(LibrarySelectionEntity.ALBUM, album.key)
                                } else {
                                    selectionUi.onEnter(LibrarySelectionEntity.ALBUM, album.key)
                                }
                            }
                        ) else Modifier.libraryItemActions(
                            clickLabel = stringResource(AppR.string.library_album_open, album.title),
                            onClick = { onAlbumClick(album.key) },
                            onShowActions = {
                            actionSheetTarget = albumActionSheetTarget(
                                albumTitle = album.title,
                                subtitle = albumSubtitle,
                                artworkUri = firstSong?.albumArtUri,
                                albumSongs = album.songs,
                                onPlayClick = onAlbumPlayClick,
                                onShuffleClick = onAlbumShuffleClick,
                                onPlayNextClick = onAlbumPlayNextClick,
                                onAddToQueueClick = onAlbumAddToQueueClick,
                                onAddToAnotherQueueClick = libraryQueueUi.onAddToAnotherQueue,
                                onPlayInNewQueueClick = { name, selectedSongs ->
                                    libraryQueueUi.onPlayInNewQueue(name, selectedSongs)
                                },
                                onAddToPlaylistClick = onAlbumAddToPlaylistClick,
                                homePinAction = homePinUi.actionForAlbum(album),
                                resolveString = resources::getString,
                                resolveArtworkDescription = { resources.getString(AppR.string.library_album_art_for, it) }
                            )
                            }
                        )
                    )
            )
        }
    }
    }

        if (selectionActive) {
            LibrarySelectionActionBar(
                selectedSongs = resolvedSelectedSongs,
                onAddToPlaylist = { selectedSongs ->
                    onAlbumAddToPlaylistClick(resources.getString(AppR.string.library_selected_albums), selectedSongs)
                },
                modifier = Modifier.padding(bottom = bottomContentPadding)
            )
        }
    }

    actionSheetTarget?.let { target ->
        LibraryItemActionSheet(
            target = target,
            onDismissRequest = {
                actionSheetTarget = null
            }
        )
    }
}

internal fun sortedLibraryAlbumGroups(
    songs: List<Song>,
    sortState: LibrarySortState
): List<LibraryAlbumGroup> {
    val albumGroups = buildLibraryAlbumGroups(songs)

    return when (sortState.option) {
        LibrarySortOption.ARTIST -> {
            albumGroups.sortedWith { left, right ->
                compareLibraryText(
                    left.artistText.takeIf { left.songs.any { song -> song.artist.isNotBlank() } }
                        .orEmpty(),
                    right.artistText.takeIf { right.songs.any { song -> song.artist.isNotBlank() } }
                        .orEmpty(),
                    sortState.direction
                ).takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.sortableTitle(),
                        right.sortableTitle(),
                        LibrarySortDirection.ASCENDING
                    )
            }
        }

        LibrarySortOption.SONG_COUNT -> {
            albumGroups.sortedWith { left, right ->
                sortState.direction.applyTo(left.songs.size.compareTo(right.songs.size))
                    .takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.sortableTitle(),
                        right.sortableTitle(),
                        LibrarySortDirection.ASCENDING
                    )
            }
        }

        else -> {
            albumGroups.sortedWith { left, right ->
                compareLibraryText(
                    left.sortableTitle(),
                    right.sortableTitle(),
                    sortState.direction
                )
            }
        }
    }
}

private fun LibraryAlbumGroup.sortableTitle(): String = title.takeIf {
    songs.any { song -> song.album.isNotBlank() }
}.orEmpty()

internal fun albumActionSheetTarget(
    albumTitle: String,
    subtitle: String,
    artworkUri: Any?,
    albumSongs: List<Song>,
    onPlayClick: (String, List<Song>) -> Unit,
    onShuffleClick: (String, List<Song>) -> Unit,
    onPlayNextClick: (String, List<Song>) -> Unit,
    onAddToQueueClick: (String, List<Song>) -> Unit,
    onAddToAnotherQueueClick: (List<Song>) -> Unit = {},
    onPlayInNewQueueClick: (String, List<Song>) -> Unit = { _, _ -> },
    onAddToPlaylistClick: (String, List<Song>) -> Unit,
    onEditMetadataClick: (() -> Unit)? = null,
    homePinAction: LibraryItemAction? = null,
    resolveString: (Int) -> String,
    resolveArtworkDescription: (String) -> String
): LibraryItemActionSheetTarget {
    return LibraryItemActionSheetTarget(
        title = albumTitle,
        subtitle = subtitle,
        artworkUri = artworkUri,
        artworkDescription = resolveArtworkDescription(albumTitle),
        actions = buildList {
            add(LibraryItemAction(
                label = resolveString(AppR.string.playlist_play),
                icon = Icons.Filled.PlayArrow,
                id = LibraryActionId.PLAY,
                onClick = { onPlayClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = resolveString(AppR.string.playlist_shuffle),
                icon = Icons.Filled.Shuffle,
                id = LibraryActionId.SHUFFLE,
                onClick = { onShuffleClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = resolveString(AppR.string.playlist_play_next),
                icon = Icons.Filled.SkipNext,
                onClick = { onPlayNextClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = resolveString(AppR.string.playlist_add_to_queue),
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                onClick = { onAddToQueueClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = resolveString(AppR.string.playlist_add_to_another_queue),
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                onClick = { onAddToAnotherQueueClick(albumSongs) }
            ))
            add(LibraryItemAction(
                label = resolveString(AppR.string.playlist_play_in_new_queue),
                icon = Icons.Filled.PlayArrow,
                onClick = { onPlayInNewQueueClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = resolveString(AppR.string.library_song_add_to_playlist),
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                onClick = { onAddToPlaylistClick(albumTitle, albumSongs) }
            ))
            onEditMetadataClick?.let { onClick ->
                add(LibraryItemAction(
                    label = resolveString(AppR.string.library_album_edit_metadata),
                    icon = Icons.Filled.EditNote,
                    id = LibraryActionId.EDIT_ALBUM_METADATA,
                    onClick = onClick
                ))
            }
            homePinAction?.let(::add)
        }
    )
}
