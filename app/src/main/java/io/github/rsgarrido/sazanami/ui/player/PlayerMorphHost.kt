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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Logical viewport for player presentation. Default morph geometry is expressed in this host's
 * pixels and must be rendered aligned with its origin. Player/lyrics graphics transforms remain
 * downstream presentation effects; they do not redefine the measurement frame.
 */
@Composable
fun PlayerMorphHost(
    morphState: PlayerMorphState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PlayerEndpointBounds) -> Unit
) {
    val endpointBounds = remember(morphState) { PlayerEndpointBounds() }
    val density = LocalDensity.current

    DisposableEffect(endpointBounds) {
        onDispose {
            endpointBounds.markMiniStale()
        }
    }

    Box(
        modifier = modifier
            // Publish the frame before descendant global-position callbacks stamp their bounds.
            .onPlaced { coordinates ->
                endpointBounds.updateLayoutFrame(
                    coordinates.boundsInRoot(), coordinates.positionInRoot(),
                    coordinates.findRootCoordinates().size, density.density, density.fontScale
                )
            }
            .onGloballyPositioned { coordinates ->
                endpointBounds.updateLayoutFrame(
                    coordinates.boundsInRoot(), coordinates.positionInRoot(),
                    coordinates.findRootCoordinates().size, density.density, density.fontScale
                )
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
