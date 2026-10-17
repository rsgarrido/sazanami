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
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints

/**
 * Logical viewport for player presentation. Default morph geometry is expressed in this host's
 * pixels and must be rendered aligned with its origin. Player/lyrics graphics transforms remain
 * downstream presentation effects; they do not redefine the measurement frame. [layoutBoundary]
 * identifies arrangements that can change endpoints without changing the viewport. It must not
 * include transient chrome visibility or animation progress.
 */
@Composable
fun PlayerMorphHost(
    morphState: PlayerMorphState,
    modifier: Modifier = Modifier,
    layoutBoundary: Any? = null,
    content: @Composable BoxScope.(PlayerEndpointBounds) -> Unit
) {
    val endpointBounds = remember(morphState) { PlayerEndpointBounds() }
    val density = LocalDensity.current
    // A changed arrangement must dispatch positioning through the whole host, including expanded
    // placeholders whose rectangles did not move. A new modifier instance requests that pass
    // without keying/recreating any content or state owners. Unchanged boundaries reuse it.
    val measurementBoundary = remember(layoutBoundary) {
        object : LayoutModifier {
            override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
                val placeable = measurable.measure(constraints)
                return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        }
    }

    DisposableEffect(endpointBounds) {
        onDispose {
            endpointBounds.markMiniStale()
        }
    }

    Box(
        modifier = modifier
            .then(measurementBoundary)
            // Publish the frame before descendant global-position callbacks stamp their bounds.
            .onPlaced { coordinates ->
                endpointBounds.updateLayoutFrame(
                    coordinates.boundsInRoot(), coordinates.positionInRoot(),
                    coordinates.findRootCoordinates().size, density.density, density.fontScale,
                    layoutBoundary
                )
            }
            .onGloballyPositioned { coordinates ->
                endpointBounds.updateLayoutFrame(
                    coordinates.boundsInRoot(), coordinates.positionInRoot(),
                    coordinates.findRootCoordinates().size, density.density, density.fontScale,
                    layoutBoundary
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
