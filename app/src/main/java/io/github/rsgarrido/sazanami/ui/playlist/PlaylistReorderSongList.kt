package io.github.rsgarrido.sazanami.ui.playlist

import android.R
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import kotlin.math.abs

@Composable
internal fun PlaylistReorderSongList(
    playlistSongRows: List<PlaylistSong>,
    onOrderCommitted: (List<Long>) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    val persistedRows = playlistSongRows.sortedBy(PlaylistSong::position)
    val persistedOrderKey = persistedRows.joinToString("|") { it.playlistSongId.toString() }
    var visualRows by remember(persistedOrderKey) { mutableStateOf(persistedRows) }
    var draggedRowId by remember { mutableStateOf<Long?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var autoScrollPerFrame by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 72.dp.toPx() }
    val maximumAutoScrollPerFramePx = with(density) { 12.dp.toPx() }
    val crossingHysteresisPx = with(density) { 6.dp.toPx() }
    val latestOnOrderCommitted by rememberUpdatedState(onOrderCommitted)
    val containerCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val viewportCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val dragHandleCoordinates = remember { mutableMapOf<Long, LayoutCoordinates>() }

    fun moveDraggedRowAcrossNeighbor(direction: Int) {
        if (direction == 0) return
        val draggedId = draggedRowId ?: return
        val visibleItemsByKey = listState.layoutInfo.visibleItemsInfo.associateBy { item ->
            item.key
        }
        val updatedRows = visualRows.toMutableList()
        var didMove = false
        var attempts = 0

        while (attempts < updatedRows.size) {
            val fromIndex = updatedRows.indexOfFirst { it.playlistSongId == draggedId }
            val neighborIndex = fromIndex + direction
            if (fromIndex < 0 || neighborIndex !in updatedRows.indices) break

            val neighborId = updatedRows[neighborIndex].playlistSongId
            val neighborInfo = visibleItemsByKey[neighborId] ?: break
            val neighborCenter = neighborInfo.offset + neighborInfo.size / 2f
            val crossedNeighbor = if (direction > 0) {
                dragPointerY >= neighborCenter + crossingHysteresisPx
            } else {
                dragPointerY <= neighborCenter - crossingHysteresisPx
            }
            if (!crossedNeighbor) break

            updatedRows.add(neighborIndex, updatedRows.removeAt(fromIndex))
            didMove = true
            attempts += 1
        }

        if (didMove) {
            visualRows = updatedRows
        }
    }

    fun updateAutoScroll() {
        val layoutInfo = listState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        autoScrollPerFrame = when {
            dragPointerY < viewportStart + edgeThresholdPx -> {
                val intensity = ((viewportStart + edgeThresholdPx - dragPointerY) /
                    edgeThresholdPx).coerceIn(0f, 1f)
                -maximumAutoScrollPerFramePx * intensity
            }
            dragPointerY > viewportEnd - edgeThresholdPx -> {
                val intensity = ((dragPointerY - (viewportEnd - edgeThresholdPx)) /
                    edgeThresholdPx).coerceIn(0f, 1f)
                maximumAutoScrollPerFramePx * intensity
            }
            else -> 0f
        }
    }

    fun finishDrag(commit: Boolean) {
        if (commit) {
            val updatedOrder = visualRows.map(PlaylistSong::playlistSongId)
            val persistedOrder = persistedRows.map(PlaylistSong::playlistSongId)
            if (updatedOrder != persistedOrder) {
                latestOnOrderCommitted(updatedOrder)
            }
        } else {
            visualRows = persistedRows
        }
        draggedRowId = null
        dragPointerY = 0f
        autoScrollPerFrame = 0f
    }

    LaunchedEffect(draggedRowId, autoScrollPerFrame) {
        while (draggedRowId != null && autoScrollPerFrame != 0f) {
            val consumedScroll = listState.scrollBy(autoScrollPerFrame)
            if (consumedScroll == 0f) {
                autoScrollPerFrame = 0f
                break
            }
            moveDraggedRowAcrossNeighbor(
                direction = when {
                    consumedScroll > 0f -> 1
                    consumedScroll < 0f -> -1
                    else -> 0
                }
            )
            withFrameNanos { }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerCoordinates[0] = coordinates
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val container = containerCoordinates[0]
                        ?.takeIf { it.isAttached }
                        ?: return@awaitEachGesture
                    val viewport = viewportCoordinates[0]
                        ?.takeIf { it.isAttached }
                        ?: return@awaitEachGesture
                    val candidateRowId = dragHandleCoordinates.entries
                        .firstOrNull { (_, handle) ->
                            handle.isAttached && container.localBoundingBoxOf(
                                sourceCoordinates = handle,
                                clipBounds = true
                            ).contains(down.position)
                        }
                        ?.key
                        ?: return@awaitEachGesture
                    val candidateIsVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
                        item.key == candidateRowId
                    }
                    if (!candidateIsVisible) return@awaitEachGesture

                    val viewportBounds = container.localBoundingBoxOf(
                        sourceCoordinates = viewport,
                        clipBounds = false
                    )
                    down.consume()

                    var accumulatedDragY = 0f
                    var started = false
                    var completed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) {
                                completed = true
                                break
                            }

                            val dragAmountY = change.positionChange().y
                            change.consume()
                            if (!started) {
                                accumulatedDragY += dragAmountY
                                if (abs(accumulatedDragY) >= viewConfiguration.touchSlop) {
                                    started = true
                                    draggedRowId = candidateRowId
                                    dragPointerY = change.position.y - viewportBounds.top
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                    moveDraggedRowAcrossNeighbor(
                                        direction = when {
                                            accumulatedDragY > 0f -> 1
                                            accumulatedDragY < 0f -> -1
                                            else -> 0
                                        }
                                    )
                                    updateAutoScroll()
                                }
                            } else if (dragAmountY != 0f) {
                                dragPointerY += dragAmountY
                                moveDraggedRowAcrossNeighbor(
                                    direction = when {
                                        dragAmountY > 0.5f -> 1
                                        dragAmountY < -0.5f -> -1
                                        else -> 0
                                    }
                                )
                                updateAutoScroll()
                            }
                        }
                    } finally {
                        if (started && draggedRowId == candidateRowId) {
                            finishDrag(commit = completed)
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Custom order \u2022 Drag songs using the handles",
                style = AppShellTypography.SongSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportCoordinates[0] = coordinates
                },
            contentPadding = PaddingValues(bottom = bottomContentPadding)
        ) {
            items(
                items = visualRows,
                key = PlaylistSong::playlistSongId
            ) { row ->
                DisposableEffect(row.playlistSongId) {
                    onDispose {
                        dragHandleCoordinates.remove(row.playlistSongId)
                    }
                }
                val isDragged = draggedRowId == row.playlistSongId
                val visibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                    item.key == row.playlistSongId
                }
                val translationY = if (isDragged && visibleInfo != null) {
                    dragPointerY - (visibleInfo.offset + visibleInfo.size / 2f)
                } else {
                    0f
                }
                val dragScale by animateFloatAsState(
                    targetValue = if (isDragged) 1.015f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "playlistDragScale-${row.playlistSongId}"
                )
                val song = row.resolvedSong
                val placementModifier = if (isDragged) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }

                ListItem(
                    leadingContent = {
                        AsyncImage(
                            model = song?.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(R.drawable.ic_media_play),
                            placeholder = painterResource(R.drawable.ic_media_play)
                        )
                    },
                    headlineContent = {
                        Text(
                            text = song?.title?.ifBlank { "Unknown Title" }
                                ?: row.title.ifBlank { "Unknown Title" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = song?.artist?.ifBlank { "Unknown Artist" }
                                ?: row.artist.ifBlank { "Unavailable song" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .onGloballyPositioned { coordinates ->
                                    dragHandleCoordinates[row.playlistSongId] = coordinates
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (isDragged) AppShellAccent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    modifier = placementModifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            this.translationY = translationY
                            shadowElevation = if (isDragged) 12.dp.toPx() else 0f
                            scaleX = dragScale
                            scaleY = dragScale
                        }
                )
            }
        }
    }
}
