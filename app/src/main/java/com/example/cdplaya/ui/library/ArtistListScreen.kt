package com.example.cdplaya.ui.library

import android.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
fun ArtistListScreen(
    songs: List<Song>,
    onArtistClick: (String) -> Unit,
    onArtistPlayClick: (String, List<Song>) -> Unit,
    onArtistShuffleClick: (String, List<Song>) -> Unit,
    onArtistPlayNextClick: (String, List<Song>) -> Unit,
    onArtistAddToQueueClick: (String, List<Song>) -> Unit,
    onArtistAddToPlaylistClick: (String, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    sortState: LibrarySortState = LibrarySortState(
        LibrarySortOption.NAME,
        LibrarySortDirection.ASCENDING
    ),
    listState: LazyListState? = null,
    bottomContentPadding: Dp = 0.dp
) {
    val artists = sortedLibraryArtistGroups(songs, sortState)
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val homePinUi = LocalHomePinUi.current
    val rememberedListState = rememberLazyListState()

    LazyColumn(
        state = listState ?: rememberedListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        items(
            items = artists,
            key = { artist -> artist.name }
        ) { artist ->
            val firstSong = artist.songs.firstOrNull()
            val songCountText = pluralStringResource(
                AppR.plurals.song_count,
                artist.songs.size,
                artist.songs.size
            )

            ListItem(
                leadingContent = {
                    AsyncImage(
                        model = firstSong?.albumArtUri,
                        contentDescription = "Artwork for ${artist.name}",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.ic_media_play),
                        placeholder = painterResource(R.drawable.ic_media_play)
                    )
                },
                headlineContent = {
                    Text(text = artist.name)
                },
                supportingContent = {
                    Text(text = songCountText)
                },
                modifier = Modifier
                    .animateItem(
                        placementSpec = tween(
                            durationMillis = LibraryLayoutMotionDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    )
                    .libraryItemActions(
                        clickLabel = "Open ${artist.name}",
                        onClick = {
                            onArtistClick(artist.name)
                        },
                        onShowActions = {
                            actionSheetTarget = artistActionSheetTarget(
                                artistName = artist.name,
                                subtitle = songCountText,
                                artworkUri = firstSong?.albumArtUri,
                                artistSongs = artist.songs,
                                onPlayClick = onArtistPlayClick,
                                onShuffleClick = onArtistShuffleClick,
                                onPlayNextClick = onArtistPlayNextClick,
                                onAddToQueueClick = onArtistAddToQueueClick,
                                onAddToPlaylistClick = onArtistAddToPlaylistClick,
                                homePinAction = homePinUi.actionForArtist(artist)
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

internal fun sortedLibraryArtistGroups(
    songs: List<Song>,
    sortState: LibrarySortState
): List<LibraryArtistGroup> {
    val artistGroups = buildLibraryArtistGroups(songs)

    return when (sortState.option) {
        LibrarySortOption.SONG_COUNT -> {
            artistGroups.sortedWith { left, right ->
                sortState.direction.applyTo(left.songs.size.compareTo(right.songs.size))
                    .takeUnless { it == 0 }
                    ?: compareLibraryText(
                        left.name,
                        right.name,
                        LibrarySortDirection.ASCENDING
                    )
            }
        }

        else -> {
            artistGroups.sortedWith { left, right ->
                compareLibraryText(
                    left.name.takeIf {
                        left.songs.any { song -> song.artist.isNotBlank() }
                    }.orEmpty(),
                    right.name.takeIf {
                        right.songs.any { song -> song.artist.isNotBlank() }
                    }.orEmpty(),
                    sortState.direction
                )
            }
        }
    }
}

internal fun artistActionSheetTarget(
    artistName: String,
    subtitle: String,
    artworkUri: Any?,
    artistSongs: List<Song>,
    onPlayClick: (String, List<Song>) -> Unit,
    onShuffleClick: (String, List<Song>) -> Unit,
    onPlayNextClick: (String, List<Song>) -> Unit,
    onAddToQueueClick: (String, List<Song>) -> Unit,
    onAddToPlaylistClick: (String, List<Song>) -> Unit,
    homePinAction: LibraryItemAction? = null
): LibraryItemActionSheetTarget {
    return LibraryItemActionSheetTarget(
        title = artistName,
        subtitle = subtitle,
        artworkUri = artworkUri,
        artworkDescription = "Artwork for $artistName",
        actions = buildList {
            add(LibraryItemAction(
                label = "Play",
                icon = Icons.Filled.PlayArrow,
                onClick = { onPlayClick(artistName, artistSongs) }
            ))
            add(LibraryItemAction(
                label = "Shuffle",
                icon = Icons.Filled.Shuffle,
                onClick = { onShuffleClick(artistName, artistSongs) }
            ))
            add(LibraryItemAction(
                label = "Play next",
                icon = Icons.Filled.SkipNext,
                onClick = { onPlayNextClick(artistName, artistSongs) }
            ))
            add(LibraryItemAction(
                label = "Add to queue",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                onClick = { onAddToQueueClick(artistName, artistSongs) }
            ))
            add(LibraryItemAction(
                label = "Add to playlist",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                onClick = { onAddToPlaylistClick(artistName, artistSongs) }
            ))
            homePinAction?.let(::add)
        }
    )
}
