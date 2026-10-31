package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/** An environment for the existing host branches; owns no controller, queue, clock or saved state. */
internal class PocketHorizonRuntime(
    val owner: PlayerEndpointBounds,
    val mini: PocketHorizonEndpointMeasurements,
    val expanded: PocketHorizonEndpointMeasurements,
    val navigation: PocketHorizonNavigationState,
    val player: PlayerMorphState,
    val playback: PlaybackUiState,
    val library: LibraryUiState,
    val progress: StateFlow<PlaybackProgressUiState>,
    val songSort: LibrarySortState,
    val albumSort: LibrarySortState,
    val artistSort: LibrarySortState,
    private val hostInputAllowed: Boolean,
    private val lifetime: State<Boolean>
) {
    val inputAllowed: Boolean get() = hostInputAllowed && lifetime.value
    val pair: PocketHorizonEndpointPair? get() = resolvePocketHorizonEndpointPair(owner, mini, expanded)
    val compactInput: Boolean get() = inputAllowed && player.isCollapsedAndIdle
    val expandedInput: Boolean get() = inputAllowed && player.targetPresentation == PlayerPresentation.Expanded &&
        player.settledPresentation == PlayerPresentation.Expanded && player.progress == 1f &&
        !player.isAnimating && !player.isDragging

    fun expand(action: () -> Unit) { if (compactInput && pair != null) action() }
    fun beginDrag(): Boolean {
        if (!inputAllowed) return false
        val endpoints = pair ?: return false
        val distance = abs(endpoints.mini.surface.top - endpoints.expanded.surface.top)
            .coerceAtLeast(48f * endpoints.frame.density)
        player.beginDragWithRange(distance)
        return true
    }
}

internal val LocalPocketHorizonRuntime = compositionLocalOf<PocketHorizonRuntime?> { null }

/** Only the dedicated metadata/header region installs this. Key replacement cancels its pointer. */
@Composable
internal fun pocketHorizonMorphGesture(runtime: PocketHorizonRuntime): Modifier {
    val latest = rememberUpdatedState(runtime)
    return Modifier.pointerInput(runtime.owner, runtime.mini, runtime.expanded, runtime.inputAllowed) {
        var started = false
        val tracker = VelocityTracker()
        try {
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking(); started = latest.value.beginDrag() },
                onVerticalDrag = { change, delta ->
                    if (started) {
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        latest.value.player.dragBy(delta)
                    }
                },
                onDragEnd = {
                    if (started) latest.value.player.endDrag(tracker.calculateVelocity().y)
                    started = false
                },
                onDragCancel = {
                    if (started) latest.value.player.cancelDrag()
                    started = false
                }
            )
        } finally {
            if (started && latest.value.player.isDragging) latest.value.player.cancelDrag()
        }
    }
}

@Composable
internal fun PocketHorizonNavigationLifecycleEffect(
    navigation: PocketHorizonNavigationState, player: PlayerMorphState, active: Boolean
) {
    val collapsed = player.isCollapsedAndIdle
    LaunchedEffect(navigation, active, collapsed) { navigation.updatePlayerLifecycle(player, active) }
}
