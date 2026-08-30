package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.modern.interpolateMorphRect
import io.github.rsgarrido.sazanami.ui.player.modern.morphProgressWindow
import kotlin.math.abs

/** Theme-local timing and geometry policy.  Keeping this separate prevents the rack from
 * accidentally inheriting the screen-shaped policies of the other retro devices. */
internal object RetroRackMorphSpec {
    const val deckRevealStart = .16f
    const val deckRevealEnd = .62f
    const val spectrumRevealStart = .48f
    const val spectrumRevealEnd = .78f
    const val queueRevealStart = .62f
    const val queueRevealEnd = .94f
    const val controlsRevealStart = .54f
    const val controlsRevealEnd = .88f
    const val expandedInputAt = .96f
    const val expensiveWorkAt = .52f
    const val minimumDragRangePx = 48f
    const val collapseDistanceThresholdFraction = .22f
    const val collapseVelocityThresholdPxPerSecond = 1_150f
}

internal data class RetroRackMorphGeometry(val shell: Rect)

@Stable class RetroRackMorphBounds {
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
    fun updateMiniArtwork(v: Rect) { miniArtwork = miniArtwork.keep(v) }
    fun updateMiniTitle(v: Rect) { miniTitle = miniTitle.keep(v) }
    fun updateMiniArtist(v: Rect) { miniArtist = miniArtist.keep(v) }
    fun updateMiniProgress(v: Rect) { miniProgress = miniProgress.keep(v) }
    fun updateMiniPlay(v: Rect) { miniPlay = miniPlay.keep(v) }
    fun updateExpandedArtwork(v: Rect) { expandedArtwork = expandedArtwork.keep(v) }
    fun updateExpandedTitle(v: Rect) { expandedTitle = expandedTitle.keep(v) }
    fun updateExpandedArtist(v: Rect) { expandedArtist = expandedArtist.keep(v) }
    fun updateExpandedProgress(v: Rect) { expandedProgress = expandedProgress.keep(v) }
    fun updateExpandedPlay(v: Rect) { expandedPlay = expandedPlay.keep(v) }
}
internal data class RetroRackSharedGeometry(
    val artwork: Rect, val title: Rect, val artist: Rect, val progress: Rect, val play: Rect
)
internal fun resolveRetroRackSharedGeometry(p: Float, b: RetroRackMorphBounds): RetroRackSharedGeometry? {
    val all = listOf(b.miniArtwork, b.miniTitle, b.miniArtist, b.miniProgress, b.miniPlay,
        b.expandedArtwork, b.expandedTitle, b.expandedArtist, b.expandedProgress, b.expandedPlay)
    if (all.any { !it.isValidRackRect() }) return null
    return RetroRackSharedGeometry(
        interpolateMorphRect(b.miniArtwork!!, b.expandedArtwork!!, p),
        interpolateMorphRect(b.miniTitle!!, b.expandedTitle!!, p),
        interpolateMorphRect(b.miniArtist!!, b.expandedArtist!!, p),
        interpolateMorphRect(b.miniProgress!!, b.expandedProgress!!, p),
        interpolateMorphRect(b.miniPlay!!, b.expandedPlay!!, p)
    )
}

enum class RetroRackSharedOwner { MINI, TRANSITION, EXPANDED }
internal fun retroRackSharedOwner(progress: Float, geometryReady: Boolean) = when {
    progress <= 0f || !geometryReady -> RetroRackSharedOwner.MINI
    progress >= 1f -> RetroRackSharedOwner.EXPANDED
    else -> RetroRackSharedOwner.TRANSITION
}

internal enum class RetroRackGestureRegion {
    SAFE_HEADER, ARTWORK, METADATA, SPECTRUM_BODY,
    BUTTON, SEEK, QUEUE, SPECTRUM_CONTROL
}
internal fun retroRackCanStartCollapse(region: RetroRackGestureRegion): Boolean = when (region) {
    RetroRackGestureRegion.SAFE_HEADER,
    RetroRackGestureRegion.ARTWORK,
    RetroRackGestureRegion.METADATA,
    RetroRackGestureRegion.SPECTRUM_BODY -> true
    RetroRackGestureRegion.BUTTON,
    RetroRackGestureRegion.SEEK,
    RetroRackGestureRegion.QUEUE,
    RetroRackGestureRegion.SPECTRUM_CONTROL -> false
}

internal fun retroRackDistanceThreshold(travelDistancePx: Float): Float =
    travelDistancePx.coerceAtLeast(RetroRackMorphSpec.minimumDragRangePx) *
            RetroRackMorphSpec.collapseDistanceThresholdFraction

internal fun resolveRetroRackMorphGeometry(
    progress: Float, endpointBounds: PlayerEndpointBounds
): RetroRackMorphGeometry? {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidRackRect() || !expanded.isValidRackRect()) return null
    return RetroRackMorphGeometry(interpolateMorphRect(mini!!, expanded!!, progress))
}

internal fun retroRackMorphTravelDistance(endpointBounds: PlayerEndpointBounds): Float {
    val mini = (endpointBounds.mini as? PlayerBoundsMeasurement.Measured)?.bounds
    val expanded = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
    if (!mini.isValidRackRect() || !expanded.isValidRackRect()) return RetroRackMorphSpec.minimumDragRangePx
    return abs(mini!!.top - expanded!!.top).coerceAtLeast(RetroRackMorphSpec.minimumDragRangePx)
}

internal fun retroRackDeckReveal(progress: Float) = morphProgressWindow(progress, RetroRackMorphSpec.deckRevealStart, RetroRackMorphSpec.deckRevealEnd)
internal fun retroRackSpectrumReveal(progress: Float) = morphProgressWindow(progress, RetroRackMorphSpec.spectrumRevealStart, RetroRackMorphSpec.spectrumRevealEnd)
internal fun retroRackQueueReveal(progress: Float) = morphProgressWindow(progress, RetroRackMorphSpec.queueRevealStart, RetroRackMorphSpec.queueRevealEnd)
internal fun retroRackControlsReveal(progress: Float) = morphProgressWindow(progress, RetroRackMorphSpec.controlsRevealStart, RetroRackMorphSpec.controlsRevealEnd)
internal fun retroRackExpandedInputEnabled(progress: Float) = progress >= RetroRackMorphSpec.expandedInputAt
internal fun shouldRunRetroRackExpandedWork(progress: Float) = progress >= RetroRackMorphSpec.expensiveWorkAt

private fun Rect?.isValidRackRect() = this != null && left.isFinite() && top.isFinite() &&
    right.isFinite() && bottom.isFinite() && width > 0f && height > 0f
private fun Rect?.keep(v: Rect): Rect? = if (!v.isValidRackRect()) this else if (this == null || abs(left-v.left)>.5f || abs(top-v.top)>.5f || abs(right-v.right)>.5f || abs(bottom-v.bottom)>.5f) v else this
