package io.github.rsgarrido.sazanami.ui.player.pocketcassette

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

internal object PocketCassetteMorphSpec {
    const val headerRevealStart = 0.10f
    const val headerRevealEnd = 0.56f
    const val windowRevealStart = 0.08f
    const val windowRevealEnd = 0.58f
    const val mechanismRevealStart = 0.24f
    const val mechanismRevealEnd = 0.76f
    const val controlsRevealStart = 0.46f
    const val controlsRevealEnd = 0.92f
    const val expandedInputAt = 0.96f
    const val collapseGestureAt = 0.72f
    const val expensiveWorkAt = 0.34f
    const val minimumDragRangePx = 48f
    const val collapseDistanceThresholdFraction = 0.22f
    const val collapseVelocityThresholdPxPerSecond = 1_150f
}

internal data class PocketCassetteMorphGeometry(
    val shell: Rect
)

@Stable
class PocketCassetteMorphBounds {
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

internal data class PocketCassetteSharedGeometry(
    val artwork: Rect,
    val title: Rect,
    val artist: Rect,
    val progress: Rect,
    val play: Rect
)

enum class PocketCassetteSharedOwner {
    MINI,
    TRANSITION,
    EXPANDED
}

internal enum class PocketCassetteGestureRegion {
    HEADER,
    WINDOW,
    TRACK_LABEL,
    LOWER_SEAM,
    SEEK,
    TRANSPORT_CONTROLS,
    UTILITY_CONTROLS,
    CLOSE_BUTTON
}

internal fun pocketCassetteCanStartCollapse(region: PocketCassetteGestureRegion): Boolean =
    when (region) {
        PocketCassetteGestureRegion.HEADER,
        PocketCassetteGestureRegion.WINDOW,
        PocketCassetteGestureRegion.TRACK_LABEL,
        PocketCassetteGestureRegion.LOWER_SEAM -> true

        PocketCassetteGestureRegion.SEEK,
        PocketCassetteGestureRegion.TRANSPORT_CONTROLS,
        PocketCassetteGestureRegion.UTILITY_CONTROLS,
        PocketCassetteGestureRegion.CLOSE_BUTTON -> false
    }

internal fun resolvePocketCassetteMorphGeometry(
    progress: Float,
    endpointBounds: PlayerEndpointBounds
): PocketCassetteMorphGeometry? {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidPocketCassetteRect() || !expanded.isValidPocketCassetteRect()) return null

    return PocketCassetteMorphGeometry(
        shell = interpolateMorphRect(
            start = mini!!,
            end = expanded!!,
            progress = progress.coerceIn(0f, 1f)
        )
    )
}

internal fun resolvePocketCassetteSharedGeometry(
    progress: Float,
    bounds: PocketCassetteMorphBounds
): PocketCassetteSharedGeometry? {
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
    if (allBounds.any { !it.isValidPocketCassetteRect() }) return null

    val p = progress.coerceIn(0f, 1f)
    return PocketCassetteSharedGeometry(
        artwork = interpolateMorphRect(bounds.miniArtwork!!, bounds.expandedArtwork!!, p),
        title = interpolateMorphRect(bounds.miniTitle!!, bounds.expandedTitle!!, p),
        artist = interpolateMorphRect(bounds.miniArtist!!, bounds.expandedArtist!!, p),
        progress = interpolateMorphRect(bounds.miniProgress!!, bounds.expandedProgress!!, p),
        play = interpolateMorphRect(bounds.miniPlay!!, bounds.expandedPlay!!, p)
    )
}

internal fun pocketCassetteSharedOwner(
    progress: Float,
    geometryReady: Boolean
): PocketCassetteSharedOwner = when {
    progress <= 0f || !geometryReady -> PocketCassetteSharedOwner.MINI
    progress >= 1f -> PocketCassetteSharedOwner.EXPANDED
    else -> PocketCassetteSharedOwner.TRANSITION
}

internal fun pocketCassetteMorphTravelDistance(endpointBounds: PlayerEndpointBounds): Float {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidPocketCassetteRect() || !expanded.isValidPocketCassetteRect()) {
        return PocketCassetteMorphSpec.minimumDragRangePx
    }

    return abs(mini!!.top - expanded!!.top)
        .coerceAtLeast(PocketCassetteMorphSpec.minimumDragRangePx)
}

internal fun pocketCassetteDistanceThreshold(travelDistancePx: Float): Float =
    travelDistancePx.coerceAtLeast(PocketCassetteMorphSpec.minimumDragRangePx) *
            PocketCassetteMorphSpec.collapseDistanceThresholdFraction

internal fun pocketCassetteHeaderReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketCassetteMorphSpec.headerRevealStart,
    PocketCassetteMorphSpec.headerRevealEnd
)

internal fun pocketCassetteWindowReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketCassetteMorphSpec.windowRevealStart,
    PocketCassetteMorphSpec.windowRevealEnd
)

internal fun pocketCassetteMechanismReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketCassetteMorphSpec.mechanismRevealStart,
    PocketCassetteMorphSpec.mechanismRevealEnd
)

internal fun pocketCassetteControlsReveal(progress: Float): Float = morphProgressWindow(
    progress,
    PocketCassetteMorphSpec.controlsRevealStart,
    PocketCassetteMorphSpec.controlsRevealEnd
)

internal fun pocketCassetteExpandedInputEnabled(progress: Float): Boolean =
    progress >= PocketCassetteMorphSpec.expandedInputAt

internal fun shouldRunPocketCassetteExpandedWork(progress: Float): Boolean =
    progress >= PocketCassetteMorphSpec.expensiveWorkAt

private fun Rect?.isValidPocketCassetteRect(): Boolean =
    this != null &&
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            width > 0f && height > 0f

private fun Rect?.keepValid(candidate: Rect): Rect? {
    if (!candidate.isValidPocketCassetteRect()) return this
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
