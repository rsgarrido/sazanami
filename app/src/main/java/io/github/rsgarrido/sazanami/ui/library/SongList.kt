package io.github.rsgarrido.sazanami.ui.library

import android.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.getDisplayTrackNumber
import io.github.rsgarrido.sazanami.R as AppR
import io.github.rsgarrido.sazanami.ui.ratings.CompactRatingIndicator
import io.github.rsgarrido.sazanami.ui.ratings.QuickRatingControl
import io.github.rsgarrido.sazanami.ui.home.LocalHomePinUi
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity

@Composable
fun SongList(
    songs: List<Song>,
    currentSongId: Long?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit = {},
    selectionEnabled: Boolean = false,
    searchActive: Boolean = false,
    modifier: Modifier = Modifier,
    showAlbumName: Boolean = false,
    showTrackNumbers: Boolean = false,
    bottomContentPadding: Dp = 0.dp,
    ratingValuesByReferenceKey: Map<String, Int> = emptyMap(),
    quickRatingMode: Boolean = false,
    listState: LazyListState? = null,
    headerContent: (@Composable () -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    beforeSongsContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    afterSongsContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    showOverflowActions: Boolean = false,
    additionalSongActions: (Song) -> List<LibraryItemAction> = { emptyList() }
) {
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val ratingUi = LocalSongRatingUi.current
    val homePinUi = LocalHomePinUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    val rateSongLabel = stringResource(AppR.string.rate_song)
    val rememberedListState = rememberLazyListState()
    val selectionUi = LocalLibrarySelectionUi.current
    val selectionActive = selectionEnabled &&
        selectionUi.state.entity == LibrarySelectionEntity.SONG && selectionUi.state.isActive
    val displayedKeys = remember(songs) { songs.map(Song::membershipKey) }
    val resolvedSelectedSongs = {
        resolveSelectedSongs(selectionUi.state.selectedKeys, songs, selectionUi.allSongs)
    }

    Column(modifier = modifier) {
        if (selectionEnabled) {
            LibrarySelectionHeader(
                entity = LibrarySelectionEntity.SONG,
                displayedKeys = displayedKeys,
                searchActive = searchActive,
                selectionActionTarget = {
                    val selectedSongs = resolvedSelectedSongs()
                    val singleSongTarget = selectedSongs.singleOrNull()?.let { selectedSong ->
                        songActionSheetTarget(
                            song = selectedSong,
                            wasRecentlyAdded = selectedSong.id in recentlyAddedSongIds,
                            isFavorite = selectedSong.membershipKey() in favoriteMembershipKeys,
                            onPlayNextClick = onPlayNextClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onToggleFavoriteClick = onToggleFavoriteClick,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onEditSongTagsClick = onEditSongTagsClick,
                            rateSongLabel = rateSongLabel,
                            onRateSongClick = ratingUi.onOpen,
                            homePinAction = homePinUi.actionForSong(selectedSong)
                        )
                    }
                    songSelectionActionSheetTarget(
                        selectedSongs = selectedSongs,
                        singleSongTarget = singleSongTarget,
                        favoriteMembershipKeys = selectionUi.favoriteMembershipKeys,
                        rateSongLabel = rateSongLabel,
                        onAddToAnotherQueue = selectionUi.onAddToAnotherQueue,
                        onPlayInNewQueue = selectionUi.onPlayInNewQueue,
                        onApplyFavoriteBatch = selectionUi.onApplyFavoriteBatch,
                        onClearSelection = selectionUi.onClear
                    )
                }
            )
        }

    LazyColumn(
        state = listState ?: rememberedListState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = if (selectionActive) 0.dp else bottomContentPadding)
    ) {
        beforeSongsContent?.invoke(this)
        headerContent?.let { content ->
            item(key = "library-song-list-header") {
                content()
            }
        }

        if (songs.isEmpty()) {
            emptyContent?.let { content ->
                item(key = "library-song-list-empty") {
                    content()
                }
            }
        }

        items(
            items = songs,
            key = { song -> song.stableUiKey() }
        ) { song ->
            val isCurrentSong = song.id == currentSongId
            val wasRecentlyAdded = song.id in recentlyAddedSongIds
            val isFavorite = song.membershipKey() in favoriteMembershipKeys
            val rating = ratingValuesByReferenceKey[song.membershipKey()]
            val isSelectionSelected = selectionActive &&
                song.membershipKey() in selectionUi.state.selectedKeys

            ListItem(
                leadingContent = {
                    if (showTrackNumbers) {
                        Text(
                            text = getDisplayTrackNumber(song.trackNumber),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(56.dp)
                        )
                    } else {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = "Album art for ${song.title}",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(R.drawable.ic_media_play),
                            placeholder = painterResource(R.drawable.ic_media_play)
                        )
                    }
                },
                headlineContent = {
                    Text(
                        text = song.title.ifBlank { "Unknown Title" },
                        fontWeight = if (isCurrentSong) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                },
                supportingContent = {
                    Column {
                        Text(
                            text = if (showAlbumName) {
                                song.album.ifBlank { "Unknown Album" }
                            } else {
                                song.artist.ifBlank { "Unknown Artist" }
                            }
                        )
                        if (quickRatingMode) {
                            QuickRatingControl(
                                rating = rating,
                                onRatingSelected = { value ->
                                    ratingUi.onSetDirectRating(song, value)
                                }
                            )
                        }
                    }
                },
                trailingContent = when {
                    isSelectionSelected -> ({
                        LibrarySelectionCheckBadge()
                    })
                    showOverflowActions && !selectionActive -> ({
                        androidx.compose.material3.IconButton(onClick = {
                            val target = songActionSheetTarget(
                                song = song,
                                wasRecentlyAdded = wasRecentlyAdded,
                                isFavorite = isFavorite,
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToAnotherQueueClick = libraryQueueUi.onAddToAnotherQueue,
                                onPlayInNewQueueClick = { libraryQueueUi.onPlayInNewQueue("", listOf(it)) },
                                onToggleFavoriteClick = onToggleFavoriteClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onEditSongTagsClick = onEditSongTagsClick,
                                rateSongLabel = rateSongLabel,
                                onRateSongClick = ratingUi.onOpen,
                                homePinAction = homePinUi.actionForSong(song)
                            )
                            actionSheetTarget = target.copy(actions = target.actions + additionalSongActions(song))
                        }) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.MoreVert,
                                contentDescription = "Actions for ${song.title}"
                            )
                        }
                    })
                    quickRatingMode -> null
                    rating != null -> ({
                        CompactRatingIndicator(
                            rating = rating,
                            iconFirst = true,
                            modifier = Modifier.clickable { ratingUi.onOpen(song) }
                        )
                    })
                    else -> null
                },
                colors = ListItemDefaults.colors(
                    containerColor = when {
                        isSelectionSelected -> AppShellAccent.copy(alpha = 0.28f)
                        isCurrentSong -> AppShellAccent.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier
                    .animateItem(
                        placementSpec = tween(
                            durationMillis = LibraryLayoutMotionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                    .then(
                        if (selectionEnabled) {
                            Modifier.librarySelectableItem(
                                clickLabel = "Play ${song.title}",
                                selectionActive = selectionActive,
                                selected = isSelectionSelected,
                                onClick = { onSongClick(song, songs) },
                                onToggleSelection = {
                                    selectionUi.onToggle(
                                        LibrarySelectionEntity.SONG,
                                        song.membershipKey()
                                    )
                                },
                                onEnterSelection = {
                                    if (selectionActive) {
                                        selectionUi.onToggle(
                                            LibrarySelectionEntity.SONG,
                                            song.membershipKey()
                                        )
                                    } else {
                                        selectionUi.onEnter(
                                            LibrarySelectionEntity.SONG,
                                            song.membershipKey()
                                        )
                                    }
                                }
                            )
                        } else Modifier.libraryItemActions(
                            clickLabel = "Play ${song.title}",
                            onClick = { onSongClick(song, songs) },
                            onShowActions = {
                            actionSheetTarget = songActionSheetTarget(
                                song = song,
                                wasRecentlyAdded = wasRecentlyAdded,
                                isFavorite = isFavorite,
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToAnotherQueueClick = libraryQueueUi.onAddToAnotherQueue,
                                onPlayInNewQueueClick = { selectedSong ->
                                    libraryQueueUi.onPlayInNewQueue("", listOf(selectedSong))
                                },
                                onToggleFavoriteClick = onToggleFavoriteClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onEditSongTagsClick = onEditSongTagsClick,
                                rateSongLabel = rateSongLabel,
                                onRateSongClick = ratingUi.onOpen,
                                homePinAction = homePinUi.actionForSong(song)
                            )
                            }
                        )
                    )
            )
        }
        afterSongsContent?.invoke(this)
    }

        if (selectionActive) {
            LibrarySelectionActionBar(
                selectedSongs = resolvedSelectedSongs,
                onAddToPlaylist = onAddSongsToPlaylistClick,
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

internal fun songActionSheetTarget(
    song: Song,
    wasRecentlyAdded: Boolean,
    isFavorite: Boolean,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onAddToAnotherQueueClick: (List<Song>) -> Unit = {},
    onPlayInNewQueueClick: (Song) -> Unit = {},
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    rateSongLabel: String,
    onRateSongClick: (Song) -> Unit,
    homePinAction: LibraryItemAction? = null
): LibraryItemActionSheetTarget {
    val artist = song.artist.ifBlank { "Unknown Artist" }
    val album = song.album.ifBlank { "Unknown Album" }
    val subtitle = if (wasRecentlyAdded) {
        "$artist • $album • Recently added"
    } else {
        "$artist • $album"
    }

    return LibraryItemActionSheetTarget(
        title = song.title.ifBlank { "Unknown Title" },
        subtitle = subtitle,
        artworkUri = song.albumArtUri,
        artworkDescription = "Album art for ${song.title}",
        actions = buildList {
            add(
                LibraryItemAction(
                    label = "Play next",
                    icon = Icons.Filled.SkipNext,
                    onClick = { onPlayNextClick(song) }
                )
            )
            add(
                LibraryItemAction(
                    label = "Add to queue",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = { onAddToQueueClick(song) }
                )
            )
            add(
                LibraryItemAction(
                    label = "Add to another queue...",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = { onAddToAnotherQueueClick(listOf(song)) }
                )
            )
            add(
                LibraryItemAction(
                    label = "Play in new queue",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlayInNewQueueClick(song) }
                )
            )
            add(
                LibraryItemAction(
                    label = if (isFavorite) {
                        "Remove from favorites"
                    } else {
                        "Add to favorites"
                    },
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onClick = { onToggleFavoriteClick(song) }
                )
            )
            homePinAction?.let(::add)
            add(
                LibraryItemAction(
                    label = rateSongLabel,
                    icon = Icons.Filled.Star,
                    onClick = { onRateSongClick(song) }
                )
            )
            add(
                LibraryItemAction(
                    label = "Add to playlist",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    onClick = { onAddToPlaylistClick(song) }
                )
            )
            add(
                LibraryItemAction(
                    label = "Edit tags",
                    icon = Icons.Filled.Edit,
                    onClick = { onEditSongTagsClick(song) }
                )
            )
        }
    )
}
