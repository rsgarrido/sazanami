package io.github.rsgarrido.sazanami.ui.lyrics

internal data class LyricsVisibleRow(val key: Any, val offset: Int, val size: Int)

/** Called only on selection/identity changes, never as a response to manual scrolling. */
internal fun selectedLyricsScrollTarget(index: Int, key: String, visible: List<LyricsVisibleRow>, start: Int, end: Int): Int? {
    val row = visible.firstOrNull { it.key == key }
    val fullyVisible = row != null && row.offset >= start && row.offset + row.size <= end
    // An oversized selected line cannot fit in the viewport; its beginning is useful context.
    val readableBeginning = row != null && row.size > end - start && row.offset in start until end
    return if (fullyVisible || readableBeginning || end <= start) null else index.coerceAtLeast(0)
}

internal fun signedLyricsOffset(offset: Long): String = if (offset > 0) "+$offset" else offset.toString()
