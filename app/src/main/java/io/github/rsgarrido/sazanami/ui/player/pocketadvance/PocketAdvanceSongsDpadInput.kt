package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal const val PocketAdvanceSongsRepeatDelayMillis = 380L
internal const val PocketAdvanceSongsRepeatIntervalMillis = 110L

/** One local owner for both directions. A second finger cancels the first hold and cannot take over. */
internal class PocketAdvanceSongsDpadPressGate {
    private var activeOwner: Any? = null
    private var cancelOwner: (() -> Unit)? = null

    fun claim(owner: Any, onCancel: () -> Unit): Boolean {
        if (activeOwner != null) {
            cancelOwner?.invoke()
            return false
        }
        activeOwner = owner
        cancelOwner = onCancel
        return true
    }

    fun release(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            cancelOwner = null
        }
    }
}

/** Songs Up/Down alone consume the down before clickable; its accessibility/key click remains intact. */
internal fun Modifier.pocketAdvanceSongsDpadInput(
    interaction: MutableInteractionSource,
    pressGate: PocketAdvanceSongsDpadPressGate,
    onInitialPress: () -> Unit,
    onRepeat: () -> Boolean
): Modifier = pointerInput(interaction, pressGate) {
    coroutineScope {
        val repeatScope = this
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            down.consume()
            val owner = Any()
            val press = PressInteraction.Press(down.position)
            var released = false
            var cancelled = false
            var repeatJob: Job? = null
            fun cancelHold() {
                if (!cancelled) {
                    cancelled = true
                    repeatJob?.cancel()
                    interaction.tryEmit(PressInteraction.Cancel(press))
                }
            }
            if (!pressGate.claim(owner, ::cancelHold)) return@awaitEachGesture
            try {
                interaction.tryEmit(press)
                onInitialPress()
                repeatJob = repeatScope.launch {
                    var next = withFrameNanos { it } + PocketAdvanceSongsRepeatDelayMillis * 1_000_000L
                    while (true) {
                        val now = withFrameNanos { it }
                        if (now >= next) {
                            if (!onRepeat()) break
                            // No catch-up burst after a slow frame.
                            next = now + PocketAdvanceSongsRepeatIntervalMillis * 1_000_000L
                        }
                    }
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) break
                    // Retain ownership until up after cancellation; a second direction cannot take over.
                    if (change.isConsumed || event.changes.count { it.pressed } > 1 ||
                        change.position.x < 0f || change.position.x >= size.width ||
                        change.position.y < 0f || change.position.y >= size.height) cancelHold()
                    if (!change.pressed) {
                        released = !cancelled
                        change.consume()
                        break
                    }
                    if (!cancelled) change.consume()
                }
            } finally {
                repeatJob?.cancel()
                if (!cancelled) {
                    interaction.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                }
                pressGate.release(owner)
            }
        }
    }
}
