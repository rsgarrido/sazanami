package io.github.rsgarrido.sazanami.ui.queue

import android.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueEntryUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueHubSheet(
    state: PlaybackQueueHubUiState,
    onDismiss: () -> Unit,
    onQueueSelected: (String) -> Unit,
    onSwitchSelected: () -> Unit,
    onCreateFromCurrent: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMessageDismissed: () -> Unit
) {
    var renameQueue by remember { mutableStateOf<PlaybackQueueCardUiState?>(null) }
    var deleteQueue by remember { mutableStateOf<PlaybackQueueCardUiState?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queues",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCreateFromCurrent,
                    enabled = !state.isCreating,
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = "New queue from current")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Queue Hub")
                }
            }

            state.message?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onMessageDismissed) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss queue message")
                        }
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.queues.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start playback to create your first queue.")
                }
                else -> {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.queues, key = PlaybackQueueCardUiState::queueId) { queue ->
                            QueueHubCard(
                                queue = queue,
                                canDelete = state.queues.size > 1 && !queue.isActive,
                                onSelect = { onQueueSelected(queue.queueId) },
                                onRename = { renameQueue = queue },
                                onDelete = { deleteQueue = queue }
                            )
                        }
                    }

                    val selected = state.selectedQueue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selected?.name ?: "Queue unavailable",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selected?.isActive == true) {
                                    "PLAYING QUEUE - Current / Up Next"
                                } else {
                                    "VIEWING SAVED QUEUE - Saved playback order"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected?.isActive == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (selected?.isActive == true) {
                            AssistChip(onClick = {}, enabled = false, label = { Text("Playing") })
                        } else if (selected != null) {
                            Button(
                                onClick = onSwitchSelected,
                                enabled = !state.isSwitching
                            ) {
                                if (state.isSwitching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Switch to this queue")
                                }
                            }
                        }
                    }

                    if (state.selectedEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.selectedQueueEntryCount == 0) {
                                    "This queue is empty."
                                } else {
                                    "No tracks in this queue are currently available."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        if (state.selectedEntries.none { entry -> entry.song != null }) {
                            Text(
                                text = "These tracks are not currently available in the local library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("queue-hub-entry-list"),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            items(
                                state.selectedEntries,
                                key = PlaybackQueueEntryUiState::entryId
                            ) { entry ->
                                QueueHubEntryRow(entry)
                            }
                        }
                    }
                }
            }
        }
    }

    renameQueue?.let { queue ->
        RenameQueueDialog(
            initialName = queue.name,
            onDismiss = { renameQueue = null },
            onConfirm = { name ->
                onRename(queue.queueId, name)
                renameQueue = null
            }
        )
    }

    deleteQueue?.let { queue ->
        AlertDialog(
            onDismissRequest = { deleteQueue = null },
            title = { Text("Delete ${queue.name}?") },
            text = { Text("This removes its saved queue and resume position.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(queue.queueId)
                    deleteQueue = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteQueue = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QueueHubCard(
    queue: PlaybackQueueCardUiState,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val borderColor = when {
        queue.isActive -> MaterialTheme.colorScheme.primary
        queue.isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (queue.isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        border = BorderStroke(if (queue.isActive || queue.isSelected) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(196.dp)
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = queue.representativeTrack?.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_media_play),
                    placeholder = painterResource(R.drawable.ic_media_play),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Queue actions for ${queue.name}")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = canDelete,
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = queue.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val progress = queue.currentPosition?.let { current ->
                "$current / ${queue.entryCount}"
            } ?: "${queue.entryCount} tracks"
            Text(
                text = progress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            queue.currentTrack?.let { song ->
                Text(
                    text = song.title.ifBlank { "Unknown title" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            queue.stateLabel?.let { stateLabel ->
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (queue.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QueueHubEntryRow(entry: PlaybackQueueEntryUiState) {
    val background = if (entry.isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.song?.albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_media_play),
            placeholder = painterResource(R.drawable.ic_media_play),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.song?.title?.ifBlank { "Unknown title" } ?: "Unavailable track",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.song?.artist?.ifBlank { "Unknown artist" } ?: "Not found locally",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.isCurrent) {
            Text(
                text = "CURRENT",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RenameQueueDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val isValid = name.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename queue") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Queue name") },
                singleLine = true,
                isError = !isValid
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = isValid) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
