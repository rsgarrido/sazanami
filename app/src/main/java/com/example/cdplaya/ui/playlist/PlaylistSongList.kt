package com.example.cdplaya.ui.playlist

import android.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.library.LibraryItemAction
import com.example.cdplaya.ui.library.LibraryItemActionSheet
import com.example.cdplaya.ui.library.LibraryItemActionSheetTarget
import com.example.cdplaya.ui.library.LibraryLayoutMotionDurationMillis
import com.example.cdplaya.ui.library.libraryItemActions
import com.example.cdplaya.R as AppR
import com.example.cdplaya.ui.ratings.LocalSongRatingUi
import com.example.cdplaya.ui.home.LocalHomePinUi

@Composable
fun PlaylistSongList(
    playlistSongs: List<Song>,
    playlistSongRows: List<PlaylistSong>,
    currentSongId: Long?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    listState: LazyListState? = null,
    headerContent: (@Composable () -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val ratingUi = LocalSongRatingUi.current
    val homePinUi = LocalHomePinUi.current
    val rateSongLabel = stringResource(AppR.string.rate_song)
    val rememberedListState = rememberLazyListState()

    LazyColumn(
        state = listState ?: rememberedListState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        headerContent?.let { content ->
            item(key = "playlist-detail-header") {
                content()
            }
        }

        if (playlistSongs.isEmpty()) {
            emptyContent?.let { content ->
                item(key = "playlist-detail-empty") {
                    content()
                }
            }
        }

        itemsIndexed(
            items = playlistSongs,
            key = { index, song ->
                playlistSongRows.getOrNull(index)?.playlistSongId ?: "${song.id}-$index"
            }
        ) { index, song ->
            val playlistSong = playlistSongRows.getOrNull(index)
            val isCurrentSong = song.id == currentSongId
            val wasRecentlyAdded = song.id in recentlyAddedSongIds
            val isFavorite = song.membershipKey() in favoriteMembershipKeys

            ListItem(
                leadingContent = {
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
                    Text(text = song.artist.ifBlank { "Unknown Artist" })
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (isCurrentSong) {
                        AppShellAccent.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier
                    .animateItem(
                        placementSpec = tween(
                            durationMillis = LibraryLayoutMotionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                    .libraryItemActions(
                    clickLabel = "Play ${song.title}",
                    onClick = {
                        onSongClick(song, playlistSongs)
                    },
                    onShowActions = {
                        val subtitleParts = buildList {
                            add(song.artist.ifBlank { "Unknown Artist" })
                            add(song.album.ifBlank { "Unknown Album" })
                            if (wasRecentlyAdded) add("Recently added")
                        }
                        val actions = buildList {
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
                                    label = if (isFavorite) {
                                        "Remove from favorites"
                                    } else {
                                        "Add to favorites"
                                    },
                                    icon = if (isFavorite) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Filled.FavoriteBorder
                                    },
                                    onClick = { onToggleFavoriteClick(song) }
                                )
                            )
                            add(homePinUi.actionForSong(song))
                            add(
                                LibraryItemAction(
                                    label = rateSongLabel,
                                    icon = Icons.Filled.Star,
                                    onClick = { ratingUi.onOpen(song) }
                                )
                            )
                            add(
                                LibraryItemAction(
                                    label = "Edit tags",
                                    icon = Icons.Filled.Edit,
                                    onClick = { onEditSongTagsClick(song) }
                                )
                            )
                            if (playlistSong != null) {
                                add(
                                    LibraryItemAction(
                                        label = "Remove from playlist",
                                        icon = Icons.Filled.Delete,
                                        isDestructive = true,
                                        onClick = { onRemovePlaylistSongClick(playlistSong) }
                                    )
                                )
                            }
                        }

                        actionSheetTarget = LibraryItemActionSheetTarget(
                            title = song.title.ifBlank { "Unknown Title" },
                            subtitle = subtitleParts.joinToString(" • "),
                            artworkUri = song.albumArtUri,
                            artworkDescription = "Album art for ${song.title}",
                            actions = actions
                        )
                    }
                )
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
