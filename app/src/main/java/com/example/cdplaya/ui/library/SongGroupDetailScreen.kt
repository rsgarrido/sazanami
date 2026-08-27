package com.example.cdplaya.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.cdplaya.data.Song

@Composable
fun SongGroupDetailScreen(
    title: String,
    subtitle: String,
    songs: List<Song>,
    currentSongId: Long?,
    recentlyAddedSongIds: Set<Long>,
    showAlbumName: Boolean,
    showTrackNumbers: Boolean,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    favoriteMembershipKeys: Set<String>,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddAllToPlaylistClick: () -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        LibraryDetailTopBar(
            title = title,
            showTitle = false,
            onBackClick = onBackClick,
            onMoreClick = {},
            trailingContent = {}
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                LibraryDetailAction(
                    icon = Icons.Filled.PlayArrow,
                    label = "Play",
                    enabled = songs.isNotEmpty(),
                    onClick = onPlayAllClick
                )
                LibraryDetailAction(
                    icon = Icons.Filled.Shuffle,
                    label = "Shuffle",
                    enabled = songs.isNotEmpty(),
                    onClick = onShuffleAllClick
                )
                LibraryDetailAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "Add",
                    enabled = songs.isNotEmpty(),
                    onClick = onAddAllToPlaylistClick,
                    contentDescription = "Add to playlist"
                )
            }
        }

        if (songs.isEmpty()) {
            Text(
                text = "No songs match your search.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            SongList(
                songs = songs,
                currentSongId = currentSongId,
                recentlyAddedSongIds = recentlyAddedSongIds,
                showAlbumName = showAlbumName,
                showTrackNumbers = showTrackNumbers,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
