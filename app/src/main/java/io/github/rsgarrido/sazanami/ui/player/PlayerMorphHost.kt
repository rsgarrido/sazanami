package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics

@Composable
fun PlayerMorphHost(
    morphState: PlayerMorphState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PlayerEndpointBounds) -> Unit
) {
    val endpointBounds = remember(morphState) { PlayerEndpointBounds() }

    DisposableEffect(endpointBounds) {
        onDispose {
            endpointBounds.markMiniStale()
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            endpointBounds.updateExpanded(coordinates.boundsInRoot())
        }
    ) {
        content(endpointBounds)
    }
}

fun Modifier.playerEndpointInput(enabled: Boolean): Modifier =
    if (enabled) {
        this
    } else {
        clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                            .changes
                            .forEach { it.consume() }
                    }
                }
            }
    }
