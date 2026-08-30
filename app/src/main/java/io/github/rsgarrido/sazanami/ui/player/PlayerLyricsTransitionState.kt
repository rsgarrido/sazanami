package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class PlayerSurfaceState {
    EXPANDED,
    LYRICS
}

@Stable
class PlayerLyricsTransitionState internal constructor(
    initiallyLyricsVisible: Boolean,
    private val scope: CoroutineScope,
    private val onCompositionVisibilityChanged: (Boolean) -> Unit
) {
    var progress by mutableFloatStateOf(if (initiallyLyricsVisible) 1f else 0f)
        private set
    var settledSurface by mutableStateOf(
        if (initiallyLyricsVisible) PlayerSurfaceState.LYRICS else PlayerSurfaceState.EXPANDED
    )
        private set
    var isDragging by mutableStateOf(false)
        private set

    private var animationJob: Job? = null

    val lyricsComposed: Boolean
        get() = progress > 0f || settledSurface == PlayerSurfaceState.LYRICS

    val lyricsInteractive: Boolean
        get() = progress >= 0.92f

    val lyricsOwnsInput: Boolean
        get() = progress > 0f || settledSurface == PlayerSurfaceState.LYRICS

    fun beginOpeningDrag() {
        animationJob?.cancel()
        isDragging = true
        onCompositionVisibilityChanged(true)
    }

    fun dragOpeningBy(deltaY: Float, heightPx: Float) {
        if (heightPx <= 0f) return
        progress = (progress - deltaY / heightPx).coerceIn(0f, 1f)
    }

    fun settleOpening(velocityY: Float) {
        isDragging = false
        animateTo(openingDestination(progress, velocityY))
    }

    fun beginClosingDrag() {
        animationJob?.cancel()
        isDragging = true
    }

    fun dragClosingBy(deltaY: Float, heightPx: Float) {
        if (heightPx <= 0f) return
        progress = (progress - deltaY / heightPx).coerceIn(0f, 1f)
    }

    fun settleClosing(velocityY: Float) {
        isDragging = false
        animateTo(closingDestination(progress, velocityY))
    }

    fun returnToExpanded() {
        isDragging = false
        animateTo(PlayerSurfaceState.EXPANDED)
    }

    fun openLyrics() {
        onCompositionVisibilityChanged(true)
        isDragging = false
        animateTo(PlayerSurfaceState.LYRICS)
    }

    fun snapToExpanded() {
        animationJob?.cancel()
        progress = 0f
        settledSurface = PlayerSurfaceState.EXPANDED
        isDragging = false
        onCompositionVisibilityChanged(false)
    }

    private fun animateTo(destination: PlayerSurfaceState) {
        animationJob?.cancel()
        animationJob = scope.launch {
            val target = if (destination == PlayerSurfaceState.LYRICS) 1f else 0f
            Animatable(progress).animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = transitionDurationMillis(progress, target),
                    easing = FastOutSlowInEasing
                )
            ) {
                progress = value
            }
            progress = target
            settledSurface = destination
            onCompositionVisibilityChanged(destination == PlayerSurfaceState.LYRICS)
        }
    }

    companion object {
        const val OPEN_THRESHOLD = 0.18f
        const val CLOSE_THRESHOLD = 0.82f
        const val OPEN_VELOCITY_PX_PER_SECOND = -1_400f
        const val CLOSE_VELOCITY_PX_PER_SECOND = 1_200f
    }
}

internal fun openingDestination(progress: Float, velocityY: Float): PlayerSurfaceState =
    if (progress >= PlayerLyricsTransitionState.OPEN_THRESHOLD ||
        velocityY <= PlayerLyricsTransitionState.OPEN_VELOCITY_PX_PER_SECOND
    ) {
        PlayerSurfaceState.LYRICS
    } else {
        PlayerSurfaceState.EXPANDED
    }

internal fun closingDestination(progress: Float, velocityY: Float): PlayerSurfaceState =
    if (progress <= PlayerLyricsTransitionState.CLOSE_THRESHOLD ||
        velocityY >= PlayerLyricsTransitionState.CLOSE_VELOCITY_PX_PER_SECOND
    ) {
        PlayerSurfaceState.EXPANDED
    } else {
        PlayerSurfaceState.LYRICS
    }

internal fun lyricsVisualAlpha(progress: Float): Float = progress.coerceIn(0f, 1f)

internal fun playerVisualAlpha(progress: Float): Float =
    1f - 0.72f * progress.coerceIn(0f, 1f)

internal fun transitionDurationMillis(current: Float, target: Float): Int =
    (220f * kotlin.math.abs(target - current))
        .toInt()
        .coerceIn(90, 220)

@Composable
fun rememberPlayerLyricsTransitionState(
    initiallyLyricsVisible: Boolean,
    onCompositionVisibilityChanged: (Boolean) -> Unit
): PlayerLyricsTransitionState {
    val scope = rememberCoroutineScope()
    val currentCallback by rememberUpdatedState(onCompositionVisibilityChanged)
    return remember(scope) {
        PlayerLyricsTransitionState(
            initiallyLyricsVisible = initiallyLyricsVisible,
            scope = scope,
            onCompositionVisibilityChanged = { currentCallback(it) }
        )
    }
}
