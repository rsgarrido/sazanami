package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAnalyzerRuntimeBridge
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame

private val InactiveSpectrumFrame = SpectrumFrame.unavailable()
private val InactiveSpectrumState = object : State<SpectrumFrame> {
    override val value: SpectrumFrame = InactiveSpectrumFrame
}

/**
 * Owns analyzer demand and exposes state for direct reads from the visualizer's draw scope.
 * Reading the state in Canvas invalidates only that draw node when a new frame arrives.
 */
@Composable
internal fun rememberSpectrumVisualizerState(enabled: Boolean): State<SpectrumFrame> {
    if (!enabled) return InactiveSpectrumState

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var registration: AutoCloseable? = null

        fun reconcileRegistration() {
            val shouldAcquire = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (shouldAcquire && registration == null) {
                registration = SpectrumAnalyzerRuntimeBridge.acquireConsumer()
            } else if (!shouldAcquire) {
                registration?.close()
                registration = null
            }
        }

        val observer = LifecycleEventObserver { _, _ -> reconcileRegistration() }
        lifecycle.addObserver(observer)
        reconcileRegistration()

        onDispose {
            lifecycle.removeObserver(observer)
            registration?.close()
            registration = null
        }
    }

    return SpectrumAnalyzerRuntimeBridge.state.collectAsStateWithLifecycle()
}
