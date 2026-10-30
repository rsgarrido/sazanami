package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation

/** A completed collapse resets the visible category, retaining compact browsing keys and anchors.
 * An interrupted/reversed transition never resets navigation. Leaving the presentation returns its
 * retained owner to Now Playing too. An expanded recreation retains the Session 3 saver state.
 * Session 4B must observe this outside conditional expanded composition, after boundary settlement.
 */
internal fun PocketHorizonNavigationState.updatePlayerLifecycle(
    isPresentationActive: Boolean,
    settled: PlayerPresentation,
    target: PlayerPresentation,
    progress: Float,
    isDragging: Boolean,
    isAnimating: Boolean
) {
    if (!isPresentationActive || (settled == PlayerPresentation.Collapsed &&
            target == PlayerPresentation.Collapsed && progress == 0f && !isDragging && !isAnimating)) {
        showNowPlaying()
    }
}

internal fun PocketHorizonNavigationState.updatePlayerLifecycle(player: PlayerMorphState, isPresentationActive: Boolean) =
    updatePlayerLifecycle(isPresentationActive, player.settledPresentation, player.targetPresentation,
        player.progress, player.isDragging, player.isAnimating)
