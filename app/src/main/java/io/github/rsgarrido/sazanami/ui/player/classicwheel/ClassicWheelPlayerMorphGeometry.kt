package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.modern.interpolateMorphRect
import io.github.rsgarrido.sazanami.ui.player.modern.morphProgressWindow
import kotlin.math.abs

/** Pure geometry and ownership policy for the Classic Wheel transition. */
internal object ClassicWheelMorphSpec {
    const val ScreenRevealStart = 0.18f
    const val ScreenRevealEnd = 0.62f
    const val WheelRevealStart = 0.48f
    const val WheelRevealEnd = 0.90f
    const val MiniChromeHideStart = 0.08f
    const val MiniChromeHideEnd = 0.38f
    const val PlayPauseHandoffStart = 0.86f
    const val PlayPauseHandoffEnd = 1f
    const val ExpandedControlsActiveAt = 0.88f
    const val MinimumDragRangePx = 48f
}

internal data class ClassicWheelPlayPauseVisualOwnership(
    val sharedAlpha: Float,
    val expandedAlpha: Float
)

internal enum class PlayerMorphRenderer { DEFAULT, CLASSIC_WHEEL, RETRO_RACK, ENDPOINT }

internal fun playerMorphRendererFor(theme: PlayerTheme): PlayerMorphRenderer = when (theme) {
    PlayerTheme.DEFAULT -> PlayerMorphRenderer.DEFAULT
    PlayerTheme.CLASSIC_WHEEL -> PlayerMorphRenderer.CLASSIC_WHEEL
    PlayerTheme.RETRO_RACK -> PlayerMorphRenderer.RETRO_RACK
    else -> PlayerMorphRenderer.ENDPOINT
}

internal data class ClassicWheelMorphGeometry(val shell: Rect)

@Stable
class ClassicWheelMorphBounds {
    var miniShell by mutableStateOf<Rect?>(null); private set
    var miniArtwork by mutableStateOf<Rect?>(null); private set
    var miniTitle by mutableStateOf<Rect?>(null); private set
    var miniArtist by mutableStateOf<Rect?>(null); private set
    var miniQueue by mutableStateOf<Rect?>(null); private set
    var miniPlayPause by mutableStateOf<Rect?>(null); private set
    var expandedArtwork by mutableStateOf<Rect?>(null); private set
    var expandedTitle by mutableStateOf<Rect?>(null); private set
    var expandedArtist by mutableStateOf<Rect?>(null); private set
    var expandedPlayPause by mutableStateOf<Rect?>(null); private set
    fun updateMiniShell(value: Rect) { miniShell = miniShell.keepValid(value) }
    fun updateMiniArtwork(value: Rect) { miniArtwork = miniArtwork.keepValid(value) }
    fun updateMiniTitle(value: Rect) { miniTitle = miniTitle.keepValid(value) }
    fun updateMiniArtist(value: Rect) { miniArtist = miniArtist.keepValid(value) }
    fun updateMiniQueue(value: Rect) { miniQueue = miniQueue.keepValid(value) }
    fun updateMiniPlayPause(value: Rect) { miniPlayPause = miniPlayPause.keepValid(value) }
    fun updateExpandedArtwork(value: Rect) { expandedArtwork = expandedArtwork.keepValid(value) }
    fun updateExpandedTitle(value: Rect) { expandedTitle = expandedTitle.keepValid(value) }
    fun updateExpandedArtist(value: Rect) { expandedArtist = expandedArtist.keepValid(value) }
    fun updateExpandedPlayPause(value: Rect) { expandedPlayPause = expandedPlayPause.keepValid(value) }
}

internal data class ClassicWheelSharedGeometry(
    val artwork: Rect, val title: Rect, val artist: Rect, val playPause: Rect
)

internal data class ClassicWheelMiniChromeGeometry(val queue: Rect)

internal fun resolveClassicWheelMorphGeometry(
    progress: Float,
    endpointBounds: PlayerEndpointBounds,
    elementBounds: ClassicWheelMorphBounds
): ClassicWheelMorphGeometry? {
    // The generic mini measurement tracks readiness/staleness for the outer slot.
    // Classic Wheel interpolates from its separately measured visible Surface.
    if (endpointBounds.mini !is PlayerBoundsMeasurement.Measured) return null
    val mini = elementBounds.miniShell
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidClassicWheelRect() || !expanded.isValidClassicWheelRect()) return null
    return ClassicWheelMorphGeometry(interpolateMorphRect(mini!!, expanded!!, progress))
}

internal fun resolveClassicWheelSharedGeometry(
    progress: Float, bounds: ClassicWheelMorphBounds
): ClassicWheelSharedGeometry? {
    if (!bounds.miniArtwork.isValidClassicWheelRect() || !bounds.expandedArtwork.isValidClassicWheelRect() ||
        !bounds.miniTitle.isValidClassicWheelRect() || !bounds.expandedTitle.isValidClassicWheelRect() ||
        !bounds.miniArtist.isValidClassicWheelRect() || !bounds.expandedArtist.isValidClassicWheelRect() ||
        !bounds.miniPlayPause.isValidClassicWheelRect() || !bounds.expandedPlayPause.isValidClassicWheelRect()) return null
    return ClassicWheelSharedGeometry(
        interpolateMorphRect(bounds.miniArtwork!!, bounds.expandedArtwork!!, progress),
        interpolateMorphRect(bounds.miniTitle!!, bounds.expandedTitle!!, progress),
        interpolateMorphRect(bounds.miniArtist!!, bounds.expandedArtist!!, progress),
        interpolateMorphRect(bounds.miniPlayPause!!, bounds.expandedPlayPause!!, progress)
    )
}

internal fun resolveClassicWheelMiniChromeGeometry(
    bounds: ClassicWheelMorphBounds
): ClassicWheelMiniChromeGeometry? = bounds.miniQueue
    .takeIf { it.isValidClassicWheelRect() }
    ?.let(::ClassicWheelMiniChromeGeometry)

/**
 * Locates the expanded wheel's visible play/pause icon inside its larger touch target.
 * The touch target affects placement only; its dimensions never become morph dimensions.
 */
internal fun classicWheelExpandedPlayPauseVisualBounds(
    wheelBounds: Rect,
    visibleIconSizePx: Float,
    touchTargetSizePx: Float,
    bottomPaddingPx: Float
): Rect? {
    if (!wheelBounds.isValidClassicWheelRect() ||
        !visibleIconSizePx.isFinite() || visibleIconSizePx <= 0f ||
        !touchTargetSizePx.isFinite() || touchTargetSizePx <= 0f ||
        !bottomPaddingPx.isFinite() || bottomPaddingPx < 0f
    ) {
        return null
    }

    val halfIcon = visibleIconSizePx / 2f
    val centerX = wheelBounds.center.x
    val centerY = wheelBounds.bottom - bottomPaddingPx - touchTargetSizePx / 2f
    val visualBounds = Rect(
        left = centerX - halfIcon,
        top = centerY - halfIcon,
        right = centerX + halfIcon,
        bottom = centerY + halfIcon
    )
    return visualBounds.takeIf { it.isValidClassicWheelRect() }
}

internal fun classicWheelMorphTravelDistance(
    endpointBounds: PlayerEndpointBounds,
    elementBounds: ClassicWheelMorphBounds
): Float {
    // Keep stale-slot fallback behavior while deriving distance from the visible card.
    if (endpointBounds.mini !is PlayerBoundsMeasurement.Measured) {
        return ClassicWheelMorphSpec.MinimumDragRangePx
    }
    val mini = elementBounds.miniShell
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidClassicWheelRect() || !expanded.isValidClassicWheelRect()) {
        return ClassicWheelMorphSpec.MinimumDragRangePx
    }
    return abs(mini!!.top - expanded!!.top).coerceAtLeast(ClassicWheelMorphSpec.MinimumDragRangePx)
}

internal fun classicWheelWheelReveal(progress: Float): Float = morphProgressWindow(
    progress, ClassicWheelMorphSpec.WheelRevealStart, ClassicWheelMorphSpec.WheelRevealEnd
)

internal fun classicWheelScreenReveal(progress: Float): Float = morphProgressWindow(
    progress, ClassicWheelMorphSpec.ScreenRevealStart, ClassicWheelMorphSpec.ScreenRevealEnd
)

internal fun classicWheelMiniChromeAlpha(progress: Float): Float = 1f - morphProgressWindow(
    progress, ClassicWheelMorphSpec.MiniChromeHideStart, ClassicWheelMorphSpec.MiniChromeHideEnd
)

internal enum class ClassicWheelMiniVisualOwner { MINI, TRANSITION }

internal fun classicWheelMiniVisualOwner(
    progress: Float,
    shellGeometryReady: Boolean,
    sharedGeometryReady: Boolean,
    miniChromeGeometryReady: Boolean,
    ownsNowPlayingContent: Boolean
): ClassicWheelMiniVisualOwner = if (
    progress > 0f &&
    shellGeometryReady &&
    sharedGeometryReady &&
    (miniChromeGeometryReady || classicWheelMiniChromeAlpha(progress) <= 0f) &&
    ownsNowPlayingContent
) {
    ClassicWheelMiniVisualOwner.TRANSITION
} else {
    ClassicWheelMiniVisualOwner.MINI
}

internal fun classicWheelPlayPauseVisualOwnership(
    progress: Float
): ClassicWheelPlayPauseVisualOwnership {
    val expandedAlpha = morphProgressWindow(
        progress,
        ClassicWheelMorphSpec.PlayPauseHandoffStart,
        ClassicWheelMorphSpec.PlayPauseHandoffEnd
    )
    return ClassicWheelPlayPauseVisualOwnership(
        sharedAlpha = 1f - expandedAlpha,
        expandedAlpha = expandedAlpha
    )
}

internal fun classicWheelExpandedControlsActive(progress: Float): Boolean =
    progress >= ClassicWheelMorphSpec.ExpandedControlsActiveAt

private fun Rect?.isValidClassicWheelRect(): Boolean = this != null &&
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
    width > 0f && height > 0f

private fun Rect?.keepValid(next: Rect): Rect? = if (!next.isValidClassicWheelRect()) this else {
    val previous = this
    if (previous == null || abs(previous.left-next.left) > .5f || abs(previous.top-next.top) > .5f ||
        abs(previous.right-next.right) > .5f || abs(previous.bottom-next.bottom) > .5f) next else previous
}
