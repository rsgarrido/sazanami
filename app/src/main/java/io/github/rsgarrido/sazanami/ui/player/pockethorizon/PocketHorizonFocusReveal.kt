package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/** Minimal correction for an actually measured row, never an estimate from its neighbour. */
internal fun pocketHorizonFocusScrollDelta(offset: Int, size: Int, start: Int, end: Int): Int = when {
    size > end - start -> offset - start // An oversized row can only expose its leading edge.
    offset < start -> offset - start
    offset + size > end -> offset + size - end
    else -> 0
}

private data class HorizonFocusPlacement(
    val valid: Boolean,
    val item: LazyListItemInfo?,
    val start: Int,
    val end: Int
)

/**
 * Materialize an off-screen stable key near the relevant edge, then use its real measurement to
 * reveal the whole row. The caller commits focus only after this completes. Its one local job is
 * cancelled when input, data, or detail ownership changes; it stores no navigation/library state.
 */
internal suspend fun LazyListState.revealPocketHorizonItem(
    key: String,
    currentKeys: () -> List<String>,
    isCurrentContext: () -> Boolean
): Boolean {
    if (!isCurrentContext()) return false
    val target = currentKeys().indexOf(key)
    if (target < 0) return false
    val before = layoutInfo
    if (before.visibleItemsInfo.none { it.key == key }) {
        val viewportHeight = (before.viewportEndOffset - before.viewportStartOffset).coerceAtLeast(0)
        val estimate = before.visibleItemsInfo.lastOrNull()?.size ?: viewportHeight
        val offset = if (target < firstVisibleItemIndex) 0 else -(viewportHeight - estimate).coerceAtLeast(0)
        // This estimate chooses a starting edge only. It is never accepted as a completed reveal.
        scrollToItem(target, offset)
    }
    val placement = snapshotFlow {
        val info = layoutInfo
        HorizonFocusPlacement(isCurrentContext() && key in currentKeys(),
            info.visibleItemsInfo.firstOrNull { it.key == key && it.size > 0 },
            info.viewportStartOffset, info.viewportEndOffset)
    }.first { !it.valid || it.item != null }
    if (!placement.valid) return false
    val item = requireNotNull(placement.item)
    val correction = pocketHorizonFocusScrollDelta(item.offset, item.size, placement.start, placement.end)
    if (correction != 0) scroll { scrollBy(correction.toFloat()) }

    // scrollToItem/scrollBy remeasure the lazy layout. Recheck the actual stable key and bounds;
    // never publish a new focus based on a consumed distance or a mismatched refreshed index.
    val final = layoutInfo
    val measured = final.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
    return isCurrentContext() && key in currentKeys() &&
        pocketHorizonFocusScrollDelta(measured.offset, measured.size,
            final.viewportStartOffset, final.viewportEndOffset) == 0
}
