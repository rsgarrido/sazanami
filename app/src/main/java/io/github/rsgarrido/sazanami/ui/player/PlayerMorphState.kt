package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PlayerPresentation {
    Collapsed,
    Expanded
}

internal const val PlayerMorphDistanceThresholdFraction = 0.26f
internal const val PlayerMorphVelocityThresholdPxPerSecond = 1_400f
private const val PlayerMorphDragRangeFraction = 0.46f

sealed interface PlayerBoundsMeasurement {
    data object Missing : PlayerBoundsMeasurement

    data class Measured(
        val bounds: Rect,
        val generation: Long
    ) : PlayerBoundsMeasurement

    data class Stale(
        val previousBounds: Rect,
        val generation: Long
    ) : PlayerBoundsMeasurement
}

@Stable
class PlayerEndpointBounds internal constructor() {
    var mini: PlayerBoundsMeasurement by mutableStateOf(PlayerBoundsMeasurement.Missing)
        private set

    var expanded: PlayerBoundsMeasurement by mutableStateOf(PlayerBoundsMeasurement.Missing)
        private set

    private var miniGeneration = 0L
    private var expandedGeneration = 0L

    fun updateMini(bounds: Rect) {
        if (!bounds.isUsablePlayerBounds()) return
        val current = mini as? PlayerBoundsMeasurement.Measured
        if (current != null && current.bounds.nearlyEquals(bounds)) return
        miniGeneration += 1
        mini = PlayerBoundsMeasurement.Measured(bounds, miniGeneration)
    }

    fun markMiniStale() {
        val current = mini as? PlayerBoundsMeasurement.Measured ?: return
        mini = PlayerBoundsMeasurement.Stale(current.bounds, current.generation)
    }

    fun updateExpanded(bounds: Rect) {
        if (!bounds.isUsablePlayerBounds()) return
        val current = expanded as? PlayerBoundsMeasurement.Measured
        if (current != null && current.bounds.nearlyEquals(bounds)) return
        expandedGeneration += 1
        expanded = PlayerBoundsMeasurement.Measured(bounds, expandedGeneration)
    }
}

@Stable
class PlayerMorphState internal constructor(
    initialPresentation: PlayerPresentation,
    private val coroutineScope: CoroutineScope,
    private val onSettledPresentationChanged: (PlayerPresentation) -> Unit = {}
) {
    private val animatedProgress = Animatable(initialPresentation.progress)
    private var settleJob: Job? = null
    private var dragStartPresentation = initialPresentation
    private var dragDistancePx = 0f
    private var dragProgressRangePx = 1f
    private var dragDistanceThresholdPx = 1f

    var targetPresentation by mutableStateOf(initialPresentation)
        private set

    var settledPresentation by mutableStateOf(initialPresentation)
        private set

    var isDragging by mutableStateOf(false)
        private set

    val progress: Float
        get() = animatedProgress.value.coerceIn(0f, 1f)

    val isAnimating: Boolean
        get() = animatedProgress.isRunning

    val isExpandedOrTransitioning: Boolean
        get() = targetPresentation == PlayerPresentation.Expanded || progress > 0f

    val shouldComposeExpanded: Boolean
        get() = isExpandedOrTransitioning

    val isCollapsedAndIdle: Boolean
        get() = settledPresentation == PlayerPresentation.Collapsed &&
                targetPresentation == PlayerPresentation.Collapsed &&
                progress == 0f &&
                !isDragging &&
                !isAnimating

    val shouldConsumeBack: Boolean
        get() = isExpandedOrTransitioning

    fun expand() {
        animateTo(PlayerPresentation.Expanded)
    }

    fun collapse() {
        animateTo(PlayerPresentation.Collapsed)
    }

    fun snapTo(presentation: PlayerPresentation) {
        settleJob?.cancel()
        isDragging = false
        targetPresentation = presentation
        settledPresentation = presentation
        settleJob = coroutineScope.launch {
            animatedProgress.snapTo(presentation.progress)
            onSettledPresentationChanged(presentation)
        }
    }

    fun beginDrag(containerHeightPx: Float) {
        if (containerHeightPx <= 0f) return
        beginDragWithRange(
            progressRangePx = containerHeightPx * PlayerMorphDragRangeFraction,
            distanceThresholdPx = containerHeightPx *
                    PlayerMorphDistanceThresholdFraction
        )
    }

    fun beginDragWithRange(
        progressRangePx: Float,
        distanceThresholdPx: Float = progressRangePx *
                PlayerMorphDistanceThresholdFraction
    ) {
        if (progressRangePx <= 0f) return
        settleJob?.cancel()
        isDragging = true
        dragStartPresentation = if (progress <= 0f) {
            PlayerPresentation.Collapsed
        } else if (progress >= 1f) {
            PlayerPresentation.Expanded
        } else {
            targetPresentation
        }
        dragDistancePx = 0f
        dragProgressRangePx = progressRangePx.coerceAtLeast(1f)
        dragDistanceThresholdPx = distanceThresholdPx.coerceAtLeast(1f)
    }

    fun dragBy(deltaY: Float) {
        if (!isDragging) return
        dragDistancePx += deltaY
        val progressDelta = -deltaY / dragProgressRangePx
        coroutineScope.launch {
            animatedProgress.snapTo(
                (animatedProgress.value + progressDelta).coerceIn(0f, 1f)
            )
        }
    }

    fun updateProgressFromDrag(progress: Float) {
        settleJob?.cancel()
        isDragging = true
        coroutineScope.launch {
            animatedProgress.snapTo(progress.coerceIn(0f, 1f))
        }
    }

    fun endDrag(velocityY: Float) {
        endDragWithVelocityThreshold(velocityY, PlayerMorphVelocityThresholdPxPerSecond)
    }

    fun endDragWithVelocityThreshold(
        velocityY: Float,
        velocityThresholdPxPerSecond: Float
    ) {
        if (!isDragging) return
        isDragging = false
        animateTo(
            selectPlayerMorphTargetForThreshold(
                startPresentation = dragStartPresentation,
                dragDistancePx = dragDistancePx,
                distanceThresholdPx = dragDistanceThresholdPx,
                velocityY = velocityY,
                velocityThresholdPxPerSecond = velocityThresholdPxPerSecond
            )
        )
    }

    fun cancelDrag() {
        if (!isDragging) return
        isDragging = false
        animateTo(dragStartPresentation)
    }

    private fun animateTo(presentation: PlayerPresentation) {
        settleJob?.cancel()
        isDragging = false
        targetPresentation = presentation
        settleJob = coroutineScope.launch {
            animatedProgress.animateTo(
                targetValue = presentation.progress,
                animationSpec = tween(
                    durationMillis = if (presentation == PlayerPresentation.Collapsed) {
                        180
                    } else {
                        300
                    },
                    easing = FastOutSlowInEasing
                )
            )
            settledPresentation = presentation
            onSettledPresentationChanged(presentation)
        }
    }
}

@Composable
fun rememberPlayerMorphState(
    initialPresentation: PlayerPresentation = PlayerPresentation.Collapsed
): PlayerMorphState {
    var savedSettledPresentation by rememberSaveable { mutableStateOf(initialPresentation) }
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) {
        PlayerMorphState(
            initialPresentation = savedSettledPresentation,
            coroutineScope = coroutineScope,
            onSettledPresentationChanged = { savedSettledPresentation = it }
        )
    }
}

internal fun selectPlayerMorphTarget(
    startPresentation: PlayerPresentation,
    dragDistancePx: Float,
    containerHeightPx: Float,
    velocityY: Float
): PlayerPresentation {
    return selectPlayerMorphTargetForThreshold(
        startPresentation = startPresentation,
        dragDistancePx = dragDistancePx,
        distanceThresholdPx = containerHeightPx *
                PlayerMorphDistanceThresholdFraction,
        velocityY = velocityY
    )
}

internal fun selectPlayerMorphTargetForThreshold(
    startPresentation: PlayerPresentation,
    dragDistancePx: Float,
    distanceThresholdPx: Float,
    velocityY: Float,
    velocityThresholdPxPerSecond: Float = PlayerMorphVelocityThresholdPxPerSecond
): PlayerPresentation {
    if (velocityY >= velocityThresholdPxPerSecond) {
        return PlayerPresentation.Collapsed
    }
    if (velocityY <= -velocityThresholdPxPerSecond) {
        return PlayerPresentation.Expanded
    }

    return when {
        dragDistancePx >= distanceThresholdPx -> PlayerPresentation.Collapsed
        dragDistancePx <= -distanceThresholdPx -> PlayerPresentation.Expanded
        else -> startPresentation
    }
}

private val PlayerPresentation.progress: Float
    get() = if (this == PlayerPresentation.Collapsed) 0f else 1f

private fun Rect.isUsablePlayerBounds(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            width > 0f && height > 0f

private fun Rect.nearlyEquals(other: Rect): Boolean =
    abs(left - other.left) < 0.5f &&
            abs(top - other.top) < 0.5f &&
            abs(right - other.right) < 0.5f &&
            abs(bottom - other.bottom) < 0.5f
