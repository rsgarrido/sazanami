package io.github.rsgarrido.sazanami.lyrics

import kotlin.math.abs

internal data class LyricAnchorGeometry(
    val viewportStartPx: Int,
    val viewportEndPx: Int,
    val itemOffsetPx: Int,
    val itemSizePx: Int,
    val anchorFraction: Float = 0.42f
)

/**
 * Returns the delta expected by LazyListState.scrollBy.
 *
 * A positive result scrolls forward so an item below the anchor moves upward.
 * A negative result scrolls backward so an item above the anchor moves downward.
 */
internal fun calculateLyricAnchorScrollDelta(
    geometry: LyricAnchorGeometry,
    tolerancePx: Float = 0f
): Float {
    val viewportSize = geometry.viewportEndPx - geometry.viewportStartPx
    if (viewportSize <= 0 || geometry.itemSizePx <= 0) return 0f
    if (!geometry.anchorFraction.isFinite()) return 0f

    val anchorFraction = geometry.anchorFraction.coerceIn(0f, 1f)
    val desiredCenter =
        geometry.viewportStartPx + viewportSize * anchorFraction
    val actualCenter = geometry.itemOffsetPx + geometry.itemSizePx / 2f
    val delta = actualCenter - desiredCenter
    return if (abs(delta) <= tolerancePx.coerceAtLeast(0f)) 0f else delta
}
