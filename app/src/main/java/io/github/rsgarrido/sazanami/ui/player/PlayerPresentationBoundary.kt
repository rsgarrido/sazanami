package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/** Geometry-affecting inputs only; playback progress and disappearing app chrome are excluded. */
internal data class PlayerPresentationViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float,
    val insetLeftPx: Int,
    val insetTopPx: Int,
    val insetRightPx: Int,
    val insetBottomPx: Int
)

internal fun requiresPlayerPresentationBoundary(
    previousIdentity: PlayerPresentationIdentity,
    nextIdentity: PlayerPresentationIdentity,
    viewportChanged: Boolean
): Boolean = (previousIdentity == PlayerPresentationIdentity.PocketAdvance ||
    nextIdentity == PlayerPresentationIdentity.PocketAdvance) &&
    (previousIdentity != nextIdentity || viewportChanged)

internal data class PlayerPresentationBoundary(
    val presentation: ResolvedPlayerPresentation,
    /** Replaces measurement/pointer lifetimes only at an Advance boundary. */
    val epoch: Long,
    val inputEnabled: Boolean
)

/**
 * Keep one outgoing pair until its controller is settled, then publish one incoming pair. This is
 * deliberately limited to the new presentation: existing-to-existing switching retains its policy.
 * Activity recreation restores the shared controller's saved endpoint and starts fresh geometry.
 */
@Composable
internal fun rememberPlayerPresentationBoundary(
    requested: ResolvedPlayerPresentation,
    viewport: PlayerPresentationViewport,
    morphState: PlayerMorphState,
    resetPresentationOverlays: () -> Unit
): PlayerPresentationBoundary {
    var committed by remember { mutableStateOf(requested) }
    var committedViewport by remember { mutableStateOf(viewport) }
    var epoch by remember { mutableStateOf(0L) }
    val resetOverlays by rememberUpdatedState(resetPresentationOverlays)
    val switching = requiresPlayerPresentationBoundary(
        committed.identity, requested.identity, committedViewport != viewport
    )
    LaunchedEffect(requested, viewport) {
        if (switching) {
            resetOverlays()
            morphState.settleForPresentationChange()
            epoch += 1
        }
        committed = requested
        committedViewport = viewport
    }
    return PlayerPresentationBoundary(
        presentation = if (switching) committed else requested,
        epoch = epoch,
        inputEnabled = !switching
    )
}
