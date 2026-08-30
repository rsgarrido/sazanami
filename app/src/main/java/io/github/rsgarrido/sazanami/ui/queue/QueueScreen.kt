package io.github.rsgarrido.sazanami.ui.queue

import android.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song

@Composable
fun QueueScreen(
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    isShuffleEnabled: Boolean,
    onBackClick: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomContentPadding)
        ) {
            item {
                QueueSectionHeader(
                    title = "Queue",
                    subtitle = if (queuedSongs.isEmpty()) {
                        "No manually queued songs."
                    } else {
                        "${queuedSongs.size} queued song(s)"
                    },
                    actionContent = {
                        if (queuedSongs.isNotEmpty()) {
                            Button(
                                onClick = onClearQueueClick
                            ) {
                                Text(text = "Clear")
                            }
                        }
                    }
                )
            }

            if (queuedSongs.isEmpty()) {
                item {
                    Text(
                        text = "Songs you add to queue will appear here.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                itemsIndexed(
                    items = queuedSongs,
                    key = { index, song -> "${song.id}-$index" }
                ) { index, song ->
                    QueuedSongRow(
                        song = song,
                        index = index,
                        canMoveUp = index > 0,
                        canMoveDown = index < queuedSongs.lastIndex,
                        onMoveUpClick = {
                            onMoveQueueItemUpClick(index)
                        },
                        onMoveDownClick = {
                            onMoveQueueItemDownClick(index)
                        },
                        onRemoveClick = {
                            onRemoveFromQueueClick(index)
                        }
                    )
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                QueueSectionHeader(
                    title = "Coming Up",
                    subtitle = if (isShuffleEnabled) {
                        "Exact shuffled order"
                    } else {
                        "Based on current playback context"
                    }
                )
            }

            if (upcomingSongs.isEmpty()) {
                item {
                    Text(
                        text = "Nothing else is coming up.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                itemsIndexed(
                    items = upcomingSongs,
                    key = { index, song -> "upcoming-${song.id}-$index" }
                ) { index, song ->
                    UpcomingSongRow(
                        song = song,
                        number = index + 1
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
    subtitle: String,
    actionContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        }

        actionContent?.invoke()
    }
}

@Composable
private fun QueuedSongRow(
    song: Song,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
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
            Text(text = song.title)
        },
        supportingContent = {
            Text(text = song.artist)
        },
        trailingContent = {
            Row {
                IconButton(
                    onClick = onMoveUpClick,
                    enabled = canMoveUp
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move ${song.title} up"
                    )
                }

                IconButton(
                    onClick = onMoveDownClick,
                    enabled = canMoveDown
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move ${song.title} down"
                    )
                }

                IconButton(
                    onClick = onRemoveClick
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove ${song.title} from queue"
                    )
                }
            }
        }
    )
}

@Composable
private fun UpcomingSongRow(
    song: Song,
    number: Int
) {
    ListItem(
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(28.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album art for ${song.title}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_media_play),
                    placeholder = painterResource(R.drawable.ic_media_play)
                )
            }
        },
        headlineContent = {
            Text(text = song.title)
        },
        supportingContent = {
            Text(text = "${song.artist} • ${song.album}")
        }
    )
}
