package io.github.rsgarrido.sazanami.ui.library

import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song

@Immutable
data class LibraryQueueUiEnvironment(
    val onPlayInNewQueue: (String, List<Song>) -> Unit = { _, _ -> },
    val onAddToAnotherQueue: (List<Song>) -> Unit = {},
    val onPlayPlaylistNext: (Playlist) -> Unit = {},
    val onPlayPlaylistInNewQueue: (Playlist) -> Unit = {},
    val onAddPlaylistToAnotherQueue: (Playlist) -> Unit = {}
)

val LocalLibraryQueueUi = staticCompositionLocalOf { LibraryQueueUiEnvironment() }

internal fun playlistQueueActions(
    playlist: Playlist,
    queueUi: LibraryQueueUiEnvironment,
    onAddToQueue: (Playlist) -> Unit
): List<LibraryItemAction> = listOf(
    LibraryItemAction("Play next", Icons.Filled.SkipNext) {
        queueUi.onPlayPlaylistNext(playlist)
    },
    LibraryItemAction("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) {
        onAddToQueue(playlist)
    },
    LibraryItemAction("Add to another queue...", Icons.AutoMirrored.Filled.QueueMusic) {
        queueUi.onAddPlaylistToAnotherQueue(playlist)
    },
    LibraryItemAction("Play in new queue", Icons.Filled.PlayArrow) {
        queueUi.onPlayPlaylistInNewQueue(playlist)
    }
)

@androidx.compose.runtime.Composable
fun AddToAnotherQueueDialog(
    queues: List<PlaybackQueueCardUiState>,
    activeQueueId: String?,
    onQueueSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val availableQueues = queues.filterNot { queue -> queue.queueId == activeQueueId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to another queue") },
        text = {
            if (availableQueues.isEmpty()) {
                Text("There are no inactive queues. The only available queue is already playing.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableQueues, key = PlaybackQueueCardUiState::queueId) { queue ->
                        ListItem(
                            leadingContent = {
                                AsyncImage(
                                    model = queue.representativeTrack?.albumArtUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(R.drawable.ic_media_play),
                                    placeholder = painterResource(R.drawable.ic_media_play),
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            },
                            headlineContent = { Text(queue.name) },
                            supportingContent = {
                                Text(
                                    queue.currentTrack?.title?.let { title ->
                                        "${queue.entryCount} tracks • $title"
                                    } ?: "${queue.entryCount} tracks"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = {
                                TextButton(onClick = { onQueueSelected(queue.queueId) }) {
                                    Text("Add")
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(end = 4.dp)
            ) { Text("Cancel") }
        }
    )
}
