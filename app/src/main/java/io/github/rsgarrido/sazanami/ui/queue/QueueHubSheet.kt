package io.github.rsgarrido.sazanami.ui.queue

import android.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueEntryUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import kotlin.math.abs

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
    onRemoveEntry: (String, String) -> Unit = { _, _ -> },
    onPlayEntry: (String, String) -> Unit = { _, _ -> },
    onUndoRemove: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
    onReorderEntry: (String, String, Int) -> Unit = { _, _, _ -> },
    onMessageDismissed: () -> Unit
) {
    var renameQueue by remember { mutableStateOf<PlaybackQueueCardUiState?>(null) }
    var deleteQueue by remember { mutableStateOf<PlaybackQueueCardUiState?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val latestOnUndoDismissed by rememberUpdatedState<() -> Unit>(onUndoDismissed)

    DisposableEffect(Unit) {
        onDispose { latestOnUndoDismissed() }
    }

    LaunchedEffect(state.removalUndoEventId) {
        if (state.removalUndoEventId == null) return@LaunchedEffect
        when (snackbarHostState.showSnackbar(
            message = "Queue entry removed",
            actionLabel = "Undo",
            duration = queueRemovalSnackbarDuration
        )) {
            SnackbarResult.ActionPerformed -> onUndoRemove()
            SnackbarResult.Dismissed -> onUndoDismissed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        val selectedQueueId = selected?.queueId
                        val reorderEnabled = selected != null && !selected.shuffleEnabled
                        var displayedEntries by remember(
                            selectedQueueId,
                            state.selectedEntries
                        ) { mutableStateOf(state.selectedEntries) }
                        var draggedEntryId by remember(selectedQueueId) {
                            mutableStateOf<String?>(null)
                        }
                        var dragStartIndex by remember(selectedQueueId) {
                            mutableStateOf<Int?>(null)
                        }
                        if (state.selectedEntries.none { entry -> entry.song != null }) {
                            Text(
                                text = "These tracks are not currently available in the local library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                        if (selected?.shuffleEnabled == true) {
                            Text(
                                text = "Turn off shuffle to reorder this queue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                        val listState = rememberLazyListState()
                        val density = LocalDensity.current
                        var draggedOffsetY by remember(selectedQueueId) { mutableStateOf(0f) }
                        var dragPointerY by remember(selectedQueueId) { mutableStateOf(0f) }
                        var dragAutoScrollDelta by remember(selectedQueueId) { mutableStateOf(0f) }
                        val currentBoundaryIndex = displayedEntries.indexOfFirst { it.isCurrent }

                        fun moveDraggedEntryIfNeeded() {
                            var moved: Boolean
                            do {
                                moved = false
                                val draggedId = draggedEntryId ?: break
                                val from = displayedEntries.indexOfFirst { it.entryId == draggedId }
                                if (from < 0) break

                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                val draggedInfo = visibleItems.firstOrNull { it.key == draggedId }
                                val rowHeight = draggedInfo?.size?.toFloat() ?: with(density) { 64.dp.toPx() }

                                val requested = when {
                                    draggedOffsetY > rowHeight / 2f -> from + 1
                                    draggedOffsetY < -rowHeight / 2f -> from - 1
                                    else -> break
                                }

                                val absoluteTarget = clampQueueReorderTarget(
                                    requestedIndex = requested,
                                    lastIndex = displayedEntries.lastIndex,
                                    activeCurrentIndex = currentBoundaryIndex.takeIf { selected?.isActive == true }
                                )

                                // CRITICAL FIX: Ensure the swap target is strictly within fully visible items.
                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                val viewportEnd = listState.layoutInfo.viewportEndOffset

                                val fullyVisibleItems = visibleItems.filter {
                                    it.offset >= viewportStart && (it.offset + it.size) <= viewportEnd
                                }

                                val firstSafeIndex = fullyVisibleItems.firstOrNull()?.index
                                    ?: visibleItems.firstOrNull()?.index ?: 0
                                val lastSafeIndex = fullyVisibleItems.lastOrNull()?.index
                                    ?: visibleItems.lastOrNull()?.index ?: displayedEntries.lastIndex

                                val target = absoluteTarget.coerceIn(firstSafeIndex, lastSafeIndex)

                                if (target == from) {
                                    // Only snap the visual offset to 0 if we hit the absolute boundaries of the queue.
                                    if (absoluteTarget == from) {
                                        draggedOffsetY = when {
                                            requested < from -> draggedOffsetY.coerceAtLeast(0f)
                                            requested > from -> draggedOffsetY.coerceAtMost(0f)
                                            else -> 0f
                                        }
                                    }
                                    break
                                }

                                displayedEntries = displayedEntries.toMutableList().apply {
                                    add(target, removeAt(from))
                                }
                                draggedOffsetY += if (target > from) -rowHeight else rowHeight
                                moved = true
                            } while (moved)
                        }

                        fun updateAutoScroll() {
                            val draggedId = draggedEntryId
                            if (draggedId == null) {
                                dragAutoScrollDelta = 0f
                                return
                            }
                            dragAutoScrollDelta = queueDragAutoScrollDelta(
                                pointerY = dragPointerY,
                                viewportStart = listState.layoutInfo.viewportStartOffset.toFloat(),
                                viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat(),
                                edgeZonePx = with(density) { 72.dp.toPx() },
                                maxScrollPerFramePx = with(density) { 18.dp.toPx() },
                                canScrollBackward = listState.canScrollBackward,
                                canScrollForward = listState.canScrollForward
                            )
                        }

                        LaunchedEffect(draggedEntryId) {
                            if (draggedEntryId == null) return@LaunchedEffect
                            while (draggedEntryId != null) {
                                withFrameNanos { }

                                if (dragAutoScrollDelta != 0f) {
                                    val consumed = listState.scrollBy(dragAutoScrollDelta)

                                    // CRITICAL FIX: Decouple visual offset from layout lag.
                                    // Directly compensate for the scroll movement instead of reading stale layout info.
                                    draggedOffsetY += consumed

                                    if (!queueDragCanContinueAutoScrolling(
                                            requestedDelta = dragAutoScrollDelta,
                                            consumedDelta = consumed,
                                            canScrollBackward = listState.canScrollBackward,
                                            canScrollForward = listState.canScrollForward
                                        )
                                    ) {
                                        dragAutoScrollDelta = 0f
                                    }
                                }

                                moveDraggedEntryIfNeeded()
                                updateAutoScroll()
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("queue-hub-entry-list"),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            items(
                                displayedEntries,
                                key = PlaybackQueueEntryUiState::entryId
                            ) { entry ->
                                val index = displayedEntries.indexOfFirst {
                                    candidate -> candidate.entryId == entry.entryId
                                }
                                val swipeResetVersion =
                                    state.swipeResetVersions[entry.entryId] ?: 0L
                                key(entry.entryId, swipeResetVersion) {
                                QueueHubEntryRow(
                                    entry = entry,
                                    canPlay = selected?.isActive == true && !entry.isCurrent,
                                    canSwipeRemove = !(selected?.isActive == true && entry.isCurrent),
                                    reorderEnabled = reorderEnabled &&
                                        !(selected?.isActive == true && entry.isCurrent),
                                    isDragging = draggedEntryId == entry.entryId,
                                    swipeResetVersion = swipeResetVersion,
                                    modifier = (if (draggedEntryId == entry.entryId) {
                                        Modifier
                                    } else {
                                        Modifier.animateItem()
                                    })
                                        .zIndex(if (draggedEntryId == entry.entryId) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (draggedEntryId == entry.entryId) {
                                                draggedOffsetY
                                            } else {
                                                0f
                                            }
                                        },
                                    onRemove = {
                                        selectedQueueId?.let { queueId ->
                                            onRemoveEntry(queueId, entry.entryId)
                                        }
                                    },
                                    onPlay = {
                                        selectedQueueId?.let { queueId ->
                                            onPlayEntry(queueId, entry.entryId)
                                        }
                                    },
                                    onDragStarted = {
                                        draggedEntryId = entry.entryId
                                        dragStartIndex = index
                                        draggedOffsetY = 0f
                                        dragPointerY = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == entry.entryId }
                                            ?.let { info -> info.offset + info.size / 2f }
                                            ?: 0f
                                        dragAutoScrollDelta = 0f
                                        updateAutoScroll()
                                    },
                                    onDrag = { deltaY ->
                                        dragPointerY += deltaY
                                        draggedOffsetY += deltaY
                                        moveDraggedEntryIfNeeded()
                                        updateAutoScroll()
                                    },
                                    onDragFinished = {
                                        val from = dragStartIndex
                                        val to = displayedEntries.indexOfFirst { candidate ->
                                            candidate.entryId == entry.entryId
                                        }
                                        draggedEntryId = null
                                        dragStartIndex = null
                                        draggedOffsetY = 0f
                                        dragPointerY = 0f
                                        dragAutoScrollDelta = 0f
                                        if (from != null && to >= 0 && from != to) {
                                            selectedQueueId?.let { queueId ->
                                                onReorderEntry(queueId, entry.entryId, to)
                                            }
                                        }
                                    }
                                )
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("queue-hub-snackbar-overlay")
        )
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
private fun QueueHubEntryRow(
    entry: PlaybackQueueEntryUiState,
    canPlay: Boolean,
    canSwipeRemove: Boolean,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    swipeResetVersion: Long,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
    onDragStarted: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit
) {
    if (canSwipeRemove) {
        var removalCommitted by remember(entry.entryId, swipeResetVersion) {
            mutableStateOf(false)
        }
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { target ->
                when {
                    target == SwipeToDismissBoxValue.EndToStart && !removalCommitted -> {
                        removalCommitted = true
                        onRemove()
                        true
                    }
                    target == SwipeToDismissBoxValue.EndToStart -> true
                    else -> !removalCommitted
                }
            }
        )
        val swipeActive = abs(
            runCatching { dismissState.requireOffset() }.getOrDefault(0f)
        ) > 0.5f || dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier.height(64.dp),
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (swipeActive) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .then(
                            if (swipeActive) {
                                Modifier.testTag("queue-swipe-background-${entry.entryId}")
                            } else {
                                Modifier
                            }
                        )
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                        dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove queue entry",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        ) {
            QueueHubEntryRowContent(
                entry = entry,
                canPlay = canPlay,
                reorderEnabled = reorderEnabled,
                isDragging = isDragging,
                onRemove = onRemove,
                onPlay = onPlay,
                onDragStarted = onDragStarted,
                onDrag = onDrag,
                onDragFinished = onDragFinished
            )
        }
    } else {
        QueueHubEntryRowContent(
            entry = entry,
            canPlay = canPlay,
            reorderEnabled = reorderEnabled,
            isDragging = isDragging,
            onRemove = onRemove,
            onPlay = onPlay,
            onDragStarted = onDragStarted,
            onDrag = onDrag,
            onDragFinished = onDragFinished,
            modifier = modifier
        )
    }
}

@Composable
private fun QueueHubEntryRowContent(
    entry: PlaybackQueueEntryUiState,
    canPlay: Boolean,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
    onDragStarted: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val latestOnDragStarted by rememberUpdatedState<() -> Unit>(onDragStarted)
    val latestOnDrag by rememberUpdatedState<(Float) -> Unit>(onDrag)
    val latestOnDragFinished by rememberUpdatedState<() -> Unit>(onDragFinished)
    val background = if (entry.isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else if (isDragging) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("queue-entry-${entry.entryId}")
            .background(background)
            .clickable(enabled = canPlay, onClick = onPlay)
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
        if (reorderEnabled) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Reorder ${entry.song?.title ?: "queue entry"}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
                    .pointerInput(entry.entryId, reorderEnabled) {
                        detectDragGestures(
                            onDragStart = {
                                latestOnDragStarted()
                            },
                            onDragCancel = {
                                latestOnDragFinished()
                            },
                            onDragEnd = {
                                latestOnDragFinished()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                latestOnDrag(dragAmount.y)
                            }
                        )
                    }
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions for queue entry")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}

internal fun clampQueueReorderTarget(
    requestedIndex: Int,
    lastIndex: Int,
    activeCurrentIndex: Int?
): Int {
    if (lastIndex < 0) return 0
    val minimum = activeCurrentIndex?.plus(1)?.coerceAtMost(lastIndex) ?: 0
    return requestedIndex.coerceIn(minimum, lastIndex)
}

internal fun queueDragAutoScrollDelta(
    pointerY: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgeZonePx: Float,
    maxScrollPerFramePx: Float,
    canScrollBackward: Boolean = true,
    canScrollForward: Boolean = true
): Float {
    if (viewportEnd <= viewportStart || edgeZonePx <= 0f) return 0f
    return when {
        pointerY <= viewportStart + edgeZonePx && canScrollBackward -> {
            val proximity = ((viewportStart + edgeZonePx - pointerY) / edgeZonePx)
                .coerceIn(MIN_QUEUE_DRAG_AUTO_SCROLL_PROXIMITY, 1f)
            -maxScrollPerFramePx * proximity
        }
        pointerY >= viewportEnd - edgeZonePx && canScrollForward -> {
            val proximity = ((pointerY - (viewportEnd - edgeZonePx)) / edgeZonePx)
                .coerceIn(MIN_QUEUE_DRAG_AUTO_SCROLL_PROXIMITY, 1f)
            maxScrollPerFramePx * proximity
        }
        else -> 0f
    }
}

internal fun queueDragCanContinueAutoScrolling(
    requestedDelta: Float,
    consumedDelta: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): Boolean = when {
    requestedDelta < 0f -> consumedDelta != 0f || canScrollBackward
    requestedDelta > 0f -> consumedDelta != 0f || canScrollForward
    else -> false
}

internal val queueRemovalSnackbarDuration: SnackbarDuration = SnackbarDuration.Short

private const val MIN_QUEUE_DRAG_AUTO_SCROLL_PROXIMITY = 0.05f

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
