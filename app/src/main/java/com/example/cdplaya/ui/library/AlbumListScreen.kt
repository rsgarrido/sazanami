package com.example.cdplaya.ui.library

import android.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.home.LocalHomePinUi
import com.example.cdplaya.R as AppR

@Composable
fun AlbumListScreen(
    songs: List<Song>,
    onAlbumClick: (String) -> Unit,
    onAlbumPlayClick: (String, List<Song>) -> Unit,
    onAlbumShuffleClick: (String, List<Song>) -> Unit,
    onAlbumPlayNextClick: (String, List<Song>) -> Unit,
    onAlbumAddToQueueClick: (String, List<Song>) -> Unit,
    onAlbumAddToPlaylistClick: (String, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    sortOption: LibrarySortOption = LibrarySortOption.TITLE,
    bottomContentPadding: Dp = 0.dp
) {
    val albums = sortedLibraryAlbumGroups(songs, sortOption)
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val homePinUi = LocalHomePinUi.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        items(
            items = albums,
            key = { album -> album.key }
        ) { album ->
            val firstSong = album.songs.firstOrNull()
            val songCountText = pluralStringResource(
                AppR.plurals.song_count,
                album.songs.size,
                album.songs.size
            )

            ListItem(
                leadingContent = {
                    AsyncImage(
                        model = firstSong?.albumArtUri,
                        contentDescription = "Album art for ${album.title}",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.ic_media_play),
                        placeholder = painterResource(R.drawable.ic_media_play)
                    )
                },
                headlineContent = {
                    Text(text = album.title)
                },
                supportingContent = {
                    Text(text = "${album.artistText} • $songCountText")
                },
                modifier = Modifier
                    .animateItem(
                        placementSpec = tween(
                            durationMillis = LibraryLayoutMotionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                    .libraryItemActions(
                        clickLabel = "Open ${album.title}",
                        onClick = {
                            onAlbumClick(album.key)
                        },
                        onShowActions = {
                            actionSheetTarget = albumActionSheetTarget(
                                albumTitle = album.title,
                                subtitle = "${album.artistText} • $songCountText",
                                artworkUri = firstSong?.albumArtUri,
                                albumSongs = album.songs,
                                onPlayClick = onAlbumPlayClick,
                                onShuffleClick = onAlbumShuffleClick,
                                onPlayNextClick = onAlbumPlayNextClick,
                                onAddToQueueClick = onAlbumAddToQueueClick,
                                onAddToPlaylistClick = onAlbumAddToPlaylistClick,
                                homePinAction = homePinUi.actionForAlbum(album)
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

internal fun sortedLibraryAlbumGroups(
    songs: List<Song>,
    sortOption: LibrarySortOption
): List<LibraryAlbumGroup> {
    val albumGroups = buildLibraryAlbumGroups(songs)

    return when (sortOption) {
        LibrarySortOption.ARTIST -> {
            albumGroups.sortedWith(
                compareBy<LibraryAlbumGroup> { album ->
                    album.artistText.lowercase()
                }.thenBy { album ->
                    album.title.lowercase()
                }
            )
        }

        LibrarySortOption.SONG_COUNT -> {
            albumGroups.sortedWith(
                compareBy<LibraryAlbumGroup> { album ->
                    album.songs.size
                }.thenBy { album ->
                    album.title.lowercase()
                }
            )
        }

        else -> {
            albumGroups.sortedBy { album ->
                album.title.lowercase()
            }
        }
    }
}

internal fun albumActionSheetTarget(
    albumTitle: String,
    subtitle: String,
    artworkUri: Any?,
    albumSongs: List<Song>,
    onPlayClick: (String, List<Song>) -> Unit,
    onShuffleClick: (String, List<Song>) -> Unit,
    onPlayNextClick: (String, List<Song>) -> Unit,
    onAddToQueueClick: (String, List<Song>) -> Unit,
    onAddToPlaylistClick: (String, List<Song>) -> Unit,
    homePinAction: LibraryItemAction? = null
): LibraryItemActionSheetTarget {
    return LibraryItemActionSheetTarget(
        title = albumTitle,
        subtitle = subtitle,
        artworkUri = artworkUri,
        artworkDescription = "Album art for $albumTitle",
        actions = buildList {
            add(LibraryItemAction(
                label = "Play",
                icon = Icons.Filled.PlayArrow,
                onClick = { onPlayClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = "Shuffle",
                icon = Icons.Filled.Shuffle,
                onClick = { onShuffleClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = "Play next",
                icon = Icons.Filled.SkipNext,
                onClick = { onPlayNextClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = "Add to queue",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                onClick = { onAddToQueueClick(albumTitle, albumSongs) }
            ))
            add(LibraryItemAction(
                label = "Add to playlist",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                onClick = { onAddToPlaylistClick(albumTitle, albumSongs) }
            ))
            homePinAction?.let(::add)
        }
    )
}
