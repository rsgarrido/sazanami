package io.github.rsgarrido.sazanami.ui.player.pocketflip

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

internal object PocketFlipMorphSpec {
    const val displayRevealStart = 0.10f
    const val displayRevealEnd = 0.58f
    const val hingeRevealStart = 0.34f
    const val hingeRevealEnd = 0.68f
    const val controlsRevealStart = 0.48f
    const val controlsRevealEnd = 0.90f
    const val expandedInputAt = 0.96f
    const val collapseGestureAt = 0.72f
    const val expensiveWorkAt = 0.36f
    const val minimumDragRangePx = 48f
    const val collapseDistanceThresholdFraction = 0.22f
    const val collapseVelocityThresholdPxPerSecond = 1_150f
}

internal data class PocketFlipMorphGeometry(
    val shell: Rect
)

@Stable
class PocketFlipMorphBounds {
    var miniArtwork by mutableStateOf<Rect?>(null)
        private set
    var miniTitle by mutableStateOf<Rect?>(null)
        private set
    var miniArtist by mutableStateOf<Rect?>(null)
        private set
    var miniProgress by mutableStateOf<Rect?>(null)
        private set
    var miniPlay by mutableStateOf<Rect?>(null)
        private set

    var expandedArtwork by mutableStateOf<Rect?>(null)
        private set
    var expandedTitle by mutableStateOf<Rect?>(null)
        private set
    var expandedArtist by mutableStateOf<Rect?>(null)
        private set
    var expandedProgress by mutableStateOf<Rect?>(null)
        private set
    var expandedPlay by mutableStateOf<Rect?>(null)
        private set

    fun updateMiniArtwork(bounds: Rect) { miniArtwork = miniArtwork.keepValid(bounds) }
    fun updateMiniTitle(bounds: Rect) { miniTitle = miniTitle.keepValid(bounds) }
    fun updateMiniArtist(bounds: Rect) { miniArtist = miniArtist.keepValid(bounds) }
    fun updateMiniProgress(bounds: Rect) { miniProgress = miniProgress.keepValid(bounds) }
    fun updateMiniPlay(bounds: Rect) { miniPlay = miniPlay.keepValid(bounds) }

    fun updateExpandedArtwork(bounds: Rect) { expandedArtwork = expandedArtwork.keepValid(bounds) }
    fun updateExpandedTitle(bounds: Rect) { expandedTitle = expandedTitle.keepValid(bounds) }
    fun updateExpandedArtist(bounds: Rect) { expandedArtist = expandedArtist.keepValid(bounds) }
    fun updateExpandedProgress(bounds: Rect) { expandedProgress = expandedProgress.keepValid(bounds) }
    fun updateExpandedPlay(bounds: Rect) { expandedPlay = expandedPlay.keepValid(bounds) }
}

internal data class PocketFlipSharedGeometry(
    val artwork: Rect,
    val title: Rect,
    val artist: Rect,
    val progress: Rect,
    val play: Rect
)

enum class PocketFlipSharedOwner {
    MINI,
    TRANSITION,
    EXPANDED
}

internal enum class PocketFlipGestureRegion {
    DISPLAY_HEADER,
    DISPLAY_BODY,
    HINGE,
    DECK_DETAILS,
    SEEK,
    DIRECTION_PAD,
    ACTION_CLUSTER,
    UTILITY_BUTTON
}

internal fun pocketFlipCanStartCollapse(region: PocketFlipGestureRegion): Boolean = when (region) {
    PocketFlipGestureRegion.DISPLAY_HEADER,
    PocketFlipGestureRegion.DISPLAY_BODY,
    PocketFlipGestureRegion.HINGE,
    PocketFlipGestureRegion.DECK_DETAILS -> true

    PocketFlipGestureRegion.SEEK,
    PocketFlipGestureRegion.DIRECTION_PAD,
    PocketFlipGestureRegion.ACTION_CLUSTER,
    PocketFlipGestureRegion.UTILITY_BUTTON -> false
}

internal fun resolvePocketFlipMorphGeometry(
    progress: Float,
    endpointBounds: PlayerEndpointBounds
): PocketFlipMorphGeometry? {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidPocketFlipRect() || !expanded.isValidPocketFlipRect()) return null

    return PocketFlipMorphGeometry(
        shell = interpolateMorphRect(
            start = mini!!,
            end = expanded!!,
            progress = progress.coerceIn(0f, 1f)
        )
    )
}

internal fun resolvePocketFlipSharedGeometry(
    progress: Float,
    bounds: PocketFlipMorphBounds
): PocketFlipSharedGeometry? {
    val allBounds = listOf(
        bounds.miniArtwork,
        bounds.miniTitle,
        bounds.miniArtist,
        bounds.miniProgress,
        bounds.miniPlay,
        bounds.expandedArtwork,
        bounds.expandedTitle,
        bounds.expandedArtist,
        bounds.expandedProgress,
        bounds.expandedPlay
    )
    if (allBounds.any { !it.isValidPocketFlipRect() }) return null

    val p = progress.coerceIn(0f, 1f)
    return PocketFlipSharedGeometry(
        artwork = interpolateMorphRect(bounds.miniArtwork!!, bounds.expandedArtwork!!, p),
        title = interpolateMorphRect(bounds.miniTitle!!, bounds.expandedTitle!!, p),
        artist = interpolateMorphRect(bounds.miniArtist!!, bounds.expandedArtist!!, p),
        progress = interpolateMorphRect(bounds.miniProgress!!, bounds.expandedProgress!!, p),
        play = interpolateMorphRect(bounds.miniPlay!!, bounds.expandedPlay!!, p)
    )
}

internal fun pocketFlipSharedOwner(
    progress: Float,
    geometryReady: Boolean
): PocketFlipSharedOwner = when {
    progress <= 0f || !geometryReady -> PocketFlipSharedOwner.MINI
    progress >= 1f -> PocketFlipSharedOwner.EXPANDED
    else -> PocketFlipSharedOwner.TRANSITION
}

internal fun pocketFlipMorphTravelDistance(endpointBounds: PlayerEndpointBounds): Float {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidPocketFlipRect() || !expanded.isValidPocketFlipRect()) {
        return PocketFlipMorphSpec.minimumDragRangePx
    }

    return abs(mini!!.top - expanded!!.top)
        .coerceAtLeast(PocketFlipMorphSpec.minimumDragRangePx)
}

internal fun pocketFlipDistanceThreshold(travelDistancePx: Float): Float =
    travelDistancePx.coerceAtLeast(PocketFlipMorphSpec.minimumDragRangePx) *
            PocketFlipMorphSpec.collapseDistanceThresholdFraction

internal fun pocketFlipDisplayReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketFlipMorphSpec.displayRevealStart,
    PocketFlipMorphSpec.displayRevealEnd
)

internal fun pocketFlipHingeReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketFlipMorphSpec.hingeRevealStart,
    PocketFlipMorphSpec.hingeRevealEnd
)

internal fun pocketFlipControlsReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketFlipMorphSpec.controlsRevealStart,
    PocketFlipMorphSpec.controlsRevealEnd
)

internal fun pocketFlipExpandedInputEnabled(progress: Float): Boolean =
    progress >= PocketFlipMorphSpec.expandedInputAt

internal fun shouldRunPocketFlipExpandedWork(progress: Float): Boolean =
    progress >= PocketFlipMorphSpec.expensiveWorkAt

private fun Rect?.isValidPocketFlipRect(): Boolean =
    this != null &&
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            width > 0f && height > 0f

private fun Rect?.keepValid(candidate: Rect): Rect? {
    if (!candidate.isValidPocketFlipRect()) return this
    if (this == null) return candidate

    return if (
        abs(left - candidate.left) > 0.5f ||
        abs(top - candidate.top) > 0.5f ||
        abs(right - candidate.right) > 0.5f ||
        abs(bottom - candidate.bottom) > 0.5f
    ) {
        candidate
    } else {
        this
    }
}
