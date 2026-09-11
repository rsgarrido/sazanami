package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput

internal const val ExpandedPlayerCollapseThresholdFraction = 0.26f
internal const val ExpandedPlayerCollapseVelocityPxPerSecond = 1_400f
internal const val ExpandedPlayerLyricsThresholdFraction = 0.18f
internal const val ExpandedPlayerLyricsVelocityPxPerSecond = -1_400f
private const val HorizontalSwipeThresholdPx = 120f

/** Layout-reported vertical region in root coordinates for retro lyrics gestures. */
@Stable
class PlayerLyricsGestureRegion {
    var topPx by mutableFloatStateOf(Float.NaN)
        private set
    var bottomPx by mutableFloatStateOf(Float.NaN)
        private set

    val isReady: Boolean
        get() = topPx.isFinite() && bottomPx.isFinite() && bottomPx > topPx

    fun updateTop(bounds: Rect) {
        if (bounds.top.isFinite()) topPx = bounds.top
    }

    fun updateBottom(bounds: Rect) {
        if (bounds.bottom.isFinite()) bottomPx = bounds.bottom
    }

    fun contains(rootY: Float): Boolean = isReady && rootY in topPx..bottomPx
}

internal fun shouldOpenLyrics(
    offsetY: Float,
    containerHeightPx: Float,
    velocityY: Float
): Boolean {
    val distanceThreshold = containerHeightPx * ExpandedPlayerLyricsThresholdFraction
    return offsetY <= -distanceThreshold ||
            velocityY <= ExpandedPlayerLyricsVelocityPxPerSecond
}

internal fun shouldCollapseExpandedPlayer(
    offsetY: Float,
    containerHeightPx: Float,
    velocityY: Float
): Boolean {
    val distanceThreshold = containerHeightPx * ExpandedPlayerCollapseThresholdFraction
    return offsetY >= distanceThreshold ||
            velocityY >= ExpandedPlayerCollapseVelocityPxPerSecond
}

fun Modifier.expandedPlayerHorizontalSwipeGestures(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    return pointerInput(onSwipeLeft, onSwipeRight) {
        var totalDragX = 0f

        detectHorizontalDragGestures(
            onDragStart = {
                totalDragX = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                change.consume()
            },
            onDragEnd = {
                when {
                    totalDragX <= -HorizontalSwipeThresholdPx -> onSwipeLeft()
                    totalDragX >= HorizontalSwipeThresholdPx -> onSwipeRight()
                }
                totalDragX = 0f
            },
            onDragCancel = {
                totalDragX = 0f
            }
        )
    }
}
