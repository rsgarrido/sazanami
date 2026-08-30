package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rsgarrido.sazanami.performance.VisualizerPerformanceCounters
import io.github.rsgarrido.sazanami.performance.tracePerformance
import kotlinx.coroutines.isActive

internal class VisualizerCadenceLimiter(targetCadenceHz: Int) {
    val targetCadenceHz: Int = targetCadenceHz.coerceIn(1, 60)
    private val updatePeriodNanos = 1_000_000_000L / this.targetCadenceHz
    private var lastUpdateNanos: Long? = null

    fun shouldUpdate(frameTimeNanos: Long): Boolean {
        val previous = lastUpdateNanos
        if (previous == null || frameTimeNanos - previous >= updatePeriodNanos) {
            lastUpdateNanos = frameTimeNanos
            return true
        }
        return false
    }
}

@Composable
internal fun rememberBoundedVisualizerPhase(
    animationEnabled: Boolean,
    targetCadenceHz: Int,
    cycleDurationMillis: Int,
    updateTraceName: String
): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleStarted = remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted.value = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val phase = remember { mutableFloatStateOf(0f) }
    val shouldRun = animationEnabled && lifecycleStarted.value
    LaunchedEffect(shouldRun, targetCadenceHz, cycleDurationMillis, updateTraceName) {
        if (!shouldRun) {
            VisualizerPerformanceCounters.onSkippedUpdate()
            return@LaunchedEffect
        }

        val limiter = VisualizerCadenceLimiter(targetCadenceHz)
        val cycleNanos = cycleDurationMillis.coerceAtLeast(1) * 1_000_000L
        var startFrameNanos: Long? = null
        VisualizerPerformanceCounters.onLoopStarted(limiter.targetCadenceHz)
        try {
            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    if (startFrameNanos == null) {
                        startFrameNanos = frameTimeNanos -
                            (phase.floatValue * cycleNanos).toLong()
                    }
                    if (limiter.shouldUpdate(frameTimeNanos)) {
                        tracePerformance(updateTraceName) {
                            val elapsed = frameTimeNanos - requireNotNull(startFrameNanos)
                            phase.floatValue = (elapsed % cycleNanos).toFloat() / cycleNanos
                            VisualizerPerformanceCounters.onUpdate()
                        }
                    } else {
                        VisualizerPerformanceCounters.onSkippedUpdate()
                    }
                }
            }
        } finally {
            VisualizerPerformanceCounters.onLoopStopped()
        }
    }
    return phase
}

internal const val RETRO_VISUALIZER_CADENCE_HZ = 30
