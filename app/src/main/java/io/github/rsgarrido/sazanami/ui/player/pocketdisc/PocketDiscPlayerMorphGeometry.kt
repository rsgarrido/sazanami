package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.modern.interpolateMorphRect
import io.github.rsgarrido.sazanami.ui.player.modern.morphProgressWindow
import kotlin.math.abs

internal object PocketDiscMorphSpec {
    const val headerRevealStart = 0.08f
    const val headerRevealEnd = 0.48f
    const val mediaRevealStart = 0.14f
    const val mediaRevealEnd = 0.64f
    const val panelRevealStart = 0.30f
    const val panelRevealEnd = 0.80f
    const val controlsRevealStart = 0.48f
    const val controlsRevealEnd = 0.94f
    const val expandedInputAt = 0.96f
    const val collapseGestureAt = 0.72f
    const val expensiveWorkAt = 0.38f
    const val minimumDragRangePx = 48f
    const val collapseDistanceThresholdFraction = 0.22f
    const val collapseVelocityThresholdPxPerSecond = 1_150f
}

internal data class PocketDiscMorphGeometry(val shell: Rect)

@Stable
class PocketDiscMorphBounds {
    var miniArtwork by mutableStateOf<Rect?>(null); private set
    var miniTitle by mutableStateOf<Rect?>(null); private set
    var miniArtist by mutableStateOf<Rect?>(null); private set
    var miniProgress by mutableStateOf<Rect?>(null); private set
    var miniPlay by mutableStateOf<Rect?>(null); private set

    var expandedArtwork by mutableStateOf<Rect?>(null); private set
    var expandedTitle by mutableStateOf<Rect?>(null); private set
    var expandedArtist by mutableStateOf<Rect?>(null); private set
    var expandedProgress by mutableStateOf<Rect?>(null); private set
    var expandedPlay by mutableStateOf<Rect?>(null); private set

    fun updateMiniArtwork(value: Rect) { miniArtwork = miniArtwork.keepValid(value) }
    fun updateMiniTitle(value: Rect) { miniTitle = miniTitle.keepValid(value) }
    fun updateMiniArtist(value: Rect) { miniArtist = miniArtist.keepValid(value) }
    fun updateMiniProgress(value: Rect) { miniProgress = miniProgress.keepValid(value) }
    fun updateMiniPlay(value: Rect) { miniPlay = miniPlay.keepValid(value) }

    fun updateExpandedArtwork(value: Rect) { expandedArtwork = expandedArtwork.keepValid(value) }
    fun updateExpandedTitle(value: Rect) { expandedTitle = expandedTitle.keepValid(value) }
    fun updateExpandedArtist(value: Rect) { expandedArtist = expandedArtist.keepValid(value) }
    fun updateExpandedProgress(value: Rect) { expandedProgress = expandedProgress.keepValid(value) }
    fun updateExpandedPlay(value: Rect) { expandedPlay = expandedPlay.keepValid(value) }
}

internal data class PocketDiscSharedGeometry(
    val artwork: Rect,
    val title: Rect,
    val artist: Rect,
    val progress: Rect,
    val play: Rect?
)

enum class PocketDiscSharedOwner { MINI, TRANSITION, EXPANDED }

internal fun resolvePocketDiscMorphGeometry(
    progress: Float,
    endpointBounds: PlayerEndpointBounds
): PocketDiscMorphGeometry? {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidPocketDiscRect() || !expanded.isValidPocketDiscRect()) return null
    return PocketDiscMorphGeometry(
        shell = interpolateMorphRect(mini!!, expanded!!, progress.coerceIn(0f, 1f))
    )
}

internal fun resolvePocketDiscSharedGeometry(
    progress: Float,
    bounds: PocketDiscMorphBounds
): PocketDiscSharedGeometry? {
    // Artwork, title, artist, and progress are the essential shared anchors. A temporarily
    // missing play-button measurement should not downgrade those elements to a cross-fade.
    val coreBounds = listOf(
        bounds.miniArtwork,
        bounds.miniTitle,
        bounds.miniArtist,
        bounds.miniProgress,
        bounds.expandedArtwork,
        bounds.expandedTitle,
        bounds.expandedArtist,
        bounds.expandedProgress
    )
    if (coreBounds.any { !it.isValidPocketDiscRect() }) return null

    val p = progress.coerceIn(0f, 1f)
    val play = if (
        bounds.miniPlay.isValidPocketDiscRect() &&
        bounds.expandedPlay.isValidPocketDiscRect()
    ) {
        interpolateMorphRect(bounds.miniPlay!!, bounds.expandedPlay!!, p)
    } else {
        null
    }

    return PocketDiscSharedGeometry(
        artwork = interpolateMorphRect(bounds.miniArtwork!!, bounds.expandedArtwork!!, p),
        title = interpolateMorphRect(bounds.miniTitle!!, bounds.expandedTitle!!, p),
        artist = interpolateMorphRect(bounds.miniArtist!!, bounds.expandedArtist!!, p),
        progress = interpolateMorphRect(bounds.miniProgress!!, bounds.expandedProgress!!, p),
        play = play
    )
}

internal fun pocketDiscSharedOwner(progress: Float, geometryReady: Boolean): PocketDiscSharedOwner = when {
    progress <= 0f || !geometryReady -> PocketDiscSharedOwner.MINI
    progress >= 1f -> PocketDiscSharedOwner.EXPANDED
    else -> PocketDiscSharedOwner.TRANSITION
}

internal fun pocketDiscMorphTravelDistance(endpointBounds: PlayerEndpointBounds): Float {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidPocketDiscRect() || !expanded.isValidPocketDiscRect()) {
        return PocketDiscMorphSpec.minimumDragRangePx
    }
    return abs(mini!!.top - expanded!!.top).coerceAtLeast(PocketDiscMorphSpec.minimumDragRangePx)
}

internal fun pocketDiscDistanceThreshold(travelDistancePx: Float): Float =
    travelDistancePx.coerceAtLeast(PocketDiscMorphSpec.minimumDragRangePx) *
            PocketDiscMorphSpec.collapseDistanceThresholdFraction

internal fun pocketDiscHeaderReveal(progress: Float): Float = morphProgressWindow(
    progress, PocketDiscMorphSpec.headerRevealStart, PocketDiscMorphSpec.headerRevealEnd
)
internal fun pocketDiscMediaReveal(progress: Float): Float = morphProgressWindow(
    progress, PocketDiscMorphSpec.mediaRevealStart, PocketDiscMorphSpec.mediaRevealEnd
)
internal fun pocketDiscPanelReveal(progress: Float): Float = morphProgressWindow(
    progress, PocketDiscMorphSpec.panelRevealStart, PocketDiscMorphSpec.panelRevealEnd
)
internal fun pocketDiscControlsReveal(progress: Float): Float = morphProgressWindow(
    progress, PocketDiscMorphSpec.controlsRevealStart, PocketDiscMorphSpec.controlsRevealEnd
)
internal fun pocketDiscExpandedInputEnabled(progress: Float): Boolean =
    progress >= PocketDiscMorphSpec.expandedInputAt
internal fun shouldRunPocketDiscExpandedWork(progress: Float): Boolean =
    progress >= PocketDiscMorphSpec.expensiveWorkAt

private fun Rect?.isValidPocketDiscRect(): Boolean = this != null &&
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        width > 0f && height > 0f

private fun Rect?.keepValid(next: Rect): Rect? {
    if (!next.isValidPocketDiscRect()) return this
    val previous = this ?: return next
    return if (
        abs(previous.left - next.left) > 0.5f || abs(previous.top - next.top) > 0.5f ||
        abs(previous.right - next.right) > 0.5f || abs(previous.bottom - next.bottom) > 0.5f
    ) next else previous
}
