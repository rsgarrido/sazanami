package io.github.rsgarrido.sazanami.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.home.HomePin

internal fun homeStatisticsMaximumWidth(fontScale: Float): Dp =
    560.dp * fontScale.coerceAtLeast(1f)

/** Width excludes the existing 16 dp margins on both sides. Normal phone sizing stays four-up. */
internal fun homeStatisticsColumns(availableWidth: Dp, fontScale: Float): Int {
    val width = minOf(availableWidth, homeStatisticsMaximumWidth(fontScale))
    val minimum = 72.dp * fontScale.coerceAtLeast(1f)
    return when {
        (width - 18.dp) / 4 >= minimum -> 4
        (width - 6.dp) / 2 >= minimum -> 2
        else -> 1
    }
}

internal fun homePinnedCardWidth(availableWidth: Dp): Dp {
    if (!availableWidth.value.isFinite()) return 0.dp
    // Preserve the four reorder slots and their spacing, while keeping artwork shelf-sized.
    return ((availableWidth - 8.dp * (HomePin.MAX_COUNT - 1)) / HomePin.MAX_COUNT)
        .coerceIn(0.dp, 152.dp)
}
