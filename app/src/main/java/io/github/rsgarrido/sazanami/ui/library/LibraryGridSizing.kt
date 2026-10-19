package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

private val AutomaticCollectionWideWidth = 600.dp

internal fun collectionGridMinimumWidth(availableWidth: Dp, minimum: Dp, fontScale: Float): Dp =
    minimum * (if (availableWidth >= AutomaticCollectionWideWidth) fontScale.coerceAtLeast(1f) else 1f)

/**
 * These preferences explicitly promise an exact column count in the view menu. Bound the grid
 * footprint rather than silently changing that contract or persisting a calculated count.
 */
internal fun exactLibraryGridMaximumWidth(preference: Int, spacing: Dp, padding: Dp): Dp {
    val columns = LibraryGridColumns.normalize(preference)
    return 256.dp * columns + spacing * (columns - 1) + padding * 2
}

/** Artist detail's album grid has no user-selected count. Compact remains its existing two-up. */
internal fun artistAlbumGridColumns(availableWidth: Dp, fontScale: Float = 1f): Int {
    if (!availableWidth.value.isFinite() || availableWidth < AutomaticCollectionWideWidth) return 2
    val minimumArtwork = collectionGridMinimumWidth(availableWidth, 160.dp, fontScale)
    val available = (availableWidth - 32.dp).coerceAtLeast(0.dp)
    return floor(((available + 12.dp) / (minimumArtwork + 12.dp)).toDouble()).toInt()
        .coerceAtLeast(2)
}
