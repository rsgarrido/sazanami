package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private data class FastScrollMetrics(
    val totalItems: Int,
    val totalUnits: Int,
    val visibleUnits: Int,
    val firstUnit: Int,
    val fractionalOffset: Float,
    val atEnd: Boolean,
    val canScroll: Boolean
) {
    val progress: Float
        get() = when {
            !canScroll -> 0f
            atEnd -> 1f
            else -> ((firstUnit + fractionalOffset) /
                (totalUnits - visibleUnits).coerceAtLeast(1)).coerceIn(0f, 1f)
        }

    val visibleFraction: Float
        get() = if (totalUnits == 0) 1f else
            (visibleUnits.toFloat() / totalUnits).coerceIn(0f, 1f)

    fun targetIndex(position: Float, columns: Int): Int {
        if (totalItems == 0) return 0
        if (position >= 1f) return totalItems - 1
        val lastTopUnit = (totalUnits - visibleUnits).coerceAtLeast(0)
        val unit = (position.coerceIn(0f, 1f) * lastTopUnit).roundToInt()
        return (unit * columns).coerceIn(0, totalItems - 1)
    }
}

private interface FastScrollAdapter {
    val columns: Int
    val isScrollInProgress: Boolean
    fun metrics(): FastScrollMetrics
    suspend fun scrollToItem(index: Int)
}

private class ListFastScrollAdapter(private val state: LazyListState) : FastScrollAdapter {
    override val columns = 1
    override val isScrollInProgress get() = state.isScrollInProgress

    override fun metrics(): FastScrollMetrics {
        val layout = state.layoutInfo
        val count = layout.totalItemsCount
        val visible = layout.visibleItemsInfo
        val visibleCount = visible.size.coerceAtMost(count)
        val firstHeight = visible.firstOrNull { it.index == state.firstVisibleItemIndex }
            ?.size?.coerceAtLeast(1) ?: 1
        return FastScrollMetrics(
            totalItems = count,
            totalUnits = count,
            visibleUnits = visibleCount,
            firstUnit = state.firstVisibleItemIndex,
            fractionalOffset = state.firstVisibleItemScrollOffset.toFloat() / firstHeight,
            atEnd = !state.canScrollForward,
            canScroll = count > 0 && (state.canScrollBackward || state.canScrollForward)
        )
    }

    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
}

private class GridFastScrollAdapter(
    private val state: LazyGridState,
    override val columns: Int
) : FastScrollAdapter {
    override val isScrollInProgress get() = state.isScrollInProgress

    override fun metrics(): FastScrollMetrics {
        val layout = state.layoutInfo
        val count = layout.totalItemsCount
        val rows = (count + columns - 1) / columns
        val visible = layout.visibleItemsInfo
        val firstRow = state.firstVisibleItemIndex / columns
        val lastRow = visible.lastOrNull()?.index?.div(columns) ?: firstRow
        val visibleRows = if (visible.isEmpty()) 0 else
            (lastRow - firstRow + 1).coerceIn(1, rows.coerceAtLeast(1))
        val firstHeight = visible.firstOrNull { it.index / columns == firstRow }
            ?.size?.height?.coerceAtLeast(1) ?: 1
        return FastScrollMetrics(
            totalItems = count,
            totalUnits = rows,
            visibleUnits = visibleRows,
            firstUnit = firstRow,
            fractionalOffset = state.firstVisibleItemScrollOffset.toFloat() / firstHeight,
            atEnd = !state.canScrollForward,
            canScroll = count > 0 && (state.canScrollBackward || state.canScrollForward)
        )
    }

    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
}

@Composable
internal fun LibraryFastScrollViewport(
    state: LazyListState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sessionKey: Any? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val adapter = remember(state) { ListFastScrollAdapter(state) }
    Box(modifier = modifier) {
        content()
        if (enabled) LibraryFastScrollbar(adapter, sessionKey, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun LibraryFastScrollViewport(
    state: LazyGridState,
    columns: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sessionKey: Any? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val normalizedColumns = LibraryGridColumns.normalize(columns)
    val adapter = remember(state, normalizedColumns) {
        GridFastScrollAdapter(state, normalizedColumns)
    }
    Box(modifier = modifier) {
        content()
        if (enabled) LibraryFastScrollbar(adapter, sessionKey, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun LibraryFastScrollbar(
    adapter: FastScrollAdapter,
    sessionKey: Any?,
    modifier: Modifier = Modifier
) {
    val metrics by remember(adapter) { derivedStateOf { adapter.metrics() } }
    val scrolling = adapter.isScrollInProgress
    var dragging by remember(adapter, sessionKey) { mutableStateOf(false) }
    var dragProgress by remember(adapter, sessionKey) { mutableStateOf<Float?>(null) }
    var shown by remember(adapter, sessionKey) { mutableStateOf(false) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val minimumThumbPx = with(density) { 30.dp.toPx() }
    val thumbHeightPx = (trackHeightPx * metrics.visibleFraction)
        .coerceAtLeast(minimumThumbPx).coerceAtMost(trackHeightPx.toFloat())
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val latestMetrics by rememberUpdatedState(metrics)
    val latestTravel by rememberUpdatedState(travelPx)
    val requests = remember(adapter, sessionKey, metrics.totalItems) { MutableStateFlow<Int?>(null) }

    LaunchedEffect(requests) {
        requests.collectLatest { target ->
            if (target != null) adapter.scrollToItem(target)
        }
    }
    LaunchedEffect(adapter, sessionKey, metrics.totalItems, metrics.canScroll) {
        dragging = false
        dragProgress = null
        if (!metrics.canScroll) requests.value = null
    }
    LaunchedEffect(scrolling, dragging, metrics.canScroll) {
        if (metrics.canScroll && (scrolling || dragging)) {
            shown = true
        } else {
            delay(800)
            shown = false
        }
    }
    val opacity by animateFloatAsState(
        targetValue = if (shown && metrics.canScroll) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "libraryFastScrollOpacity"
    )
    if (!metrics.canScroll || trackHeightPx <= 0) {
        // Keep the measured rail in composition even before the lazy viewport is laid out.
        Box(modifier.fillMaxHeight().width(28.dp).onSizeChanged { trackHeightPx = it.height })
        return
    }

    val position = (dragProgress ?: metrics.progress).coerceIn(0f, 1f)
    val thumbOffset = (position * travelPx).roundToInt()
    Box(modifier.fillMaxHeight().width(28.dp).onSizeChanged { trackHeightPx = it.height }) {
        Box(
            Modifier
                .offset { IntOffset(0, thumbOffset) }
                .width(28.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .then(if (shown) Modifier.pointerInput(adapter, sessionKey, metrics.totalItems) {
                    var startProgress = 0f
                    var distance = 0f
                    detectDragGestures(
                        onDragStart = { _: Offset ->
                            startProgress = latestMetrics.progress
                            distance = 0f
                            requests.value = null
                            dragProgress = startProgress
                            dragging = true
                        },
                        onDragEnd = {
                            dragging = false
                            dragProgress = null
                        },
                        onDragCancel = {
                            dragging = false
                            dragProgress = null
                            requests.value = null
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            distance += amount.y
                            val next = (startProgress + distance / latestTravel.coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            dragProgress = next
                            val current = latestMetrics
                            val target = current.targetIndex(next, adapter.columns)
                            if (target != requests.value) requests.value = target
                        }
                    )
                } else Modifier),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .width(if (dragging) 6.dp else 4.dp)
                    .fillMaxHeight()
                    .alpha(if (dragging) 1f else opacity)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (dragging) 0.9f else 0.7f
                        ),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}
