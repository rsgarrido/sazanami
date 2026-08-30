package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import kotlin.math.abs

private const val BoundsEpsilonPx = 0.5f
internal const val DefaultMorphMinimumDragRangePx = 48f

internal object DefaultPlayerMorphSpec {
    const val BackgroundRevealStart = 0.08f
    const val BackgroundRevealEnd = 0.62f
    const val MetadataRevealStart = 0.30f
    const val MetadataRevealEnd = 0.75f
    const val ControlsRevealStart = 0.50f
    const val ControlsRevealEnd = 0.92f
    const val EndpointHandoffStart = 0.94f
    const val EndpointHandoffEnd = 1f
    const val ExpensiveContentThreshold = 0.30f
}

enum class DefaultMorphEndpointActivity {
    Mini,
    Transition,
    Expanded
}

enum class DefaultMorphMetadataOwner {
    Mini,
    Morph
}

@Stable
class DefaultPlayerMorphBounds {
    var miniSurface by mutableStateOf<Rect?>(null)
        private set
    var miniArtwork by mutableStateOf<Rect?>(null)
        private set
    var miniText by mutableStateOf<Rect?>(null)
        private set
    var miniPlayPause by mutableStateOf<Rect?>(null)
        private set
    var expandedArtwork by mutableStateOf<Rect?>(null)
        private set
    var expandedText by mutableStateOf<Rect?>(null)
        private set
    var expandedPlayPause by mutableStateOf<Rect?>(null)
        private set

    val hasRequiredElements: Boolean
        get() = miniSurface.isValidMorphRect() &&
                miniArtwork.isValidMorphRect() &&
                miniText.isValidMorphRect() &&
                miniPlayPause.isValidMorphRect() &&
                expandedArtwork.isValidMorphRect() &&
                expandedText.isValidMorphRect() &&
                expandedPlayPause.isValidMorphRect()

    fun updateMiniSurface(bounds: Rect) {
        miniSurface = replaceMeaningfully(miniSurface, bounds)
    }

    fun updateMiniArtwork(bounds: Rect) {
        miniArtwork = replaceMeaningfully(miniArtwork, bounds)
    }

    fun updateMiniText(bounds: Rect) {
        miniText = replaceMeaningfully(miniText, bounds)
    }

    fun updateMiniPlayPause(bounds: Rect) {
        miniPlayPause = replaceMeaningfully(miniPlayPause, bounds)
    }

    fun updateExpandedArtwork(bounds: Rect) {
        expandedArtwork = replaceMeaningfully(expandedArtwork, bounds)
    }

    fun updateExpandedText(bounds: Rect) {
        expandedText = replaceMeaningfully(expandedText, bounds)
    }

    fun updateExpandedPlayPause(bounds: Rect) {
        expandedPlayPause = replaceMeaningfully(expandedPlayPause, bounds)
    }

    fun clearExpanded() {
        expandedArtwork = null
        expandedText = null
        expandedPlayPause = null
    }
}

data class DefaultPlayerMorphGeometry(
    val surface: Rect,
    val artwork: Rect,
    val text: Rect,
    val playPause: Rect
)

fun interpolateMorphRect(
    start: Rect,
    end: Rect,
    progress: Float
): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerpMorphValue(start.left, end.left, fraction),
        top = lerpMorphValue(start.top, end.top, fraction),
        right = lerpMorphValue(start.right, end.right, fraction),
        bottom = lerpMorphValue(start.bottom, end.bottom, fraction)
    )
}

fun interpolateMorphCornerRadius(
    collapsedRadius: Float,
    expandedRadius: Float,
    progress: Float
): Float = lerpMorphValue(
    collapsedRadius.coerceAtLeast(0f),
    expandedRadius.coerceAtLeast(0f),
    progress.coerceIn(0f, 1f)
)

fun morphProgressWindow(
    progress: Float,
    start: Float,
    end: Float
): Float {
    val safeStart = start.coerceIn(0f, 1f)
    val safeEnd = end.coerceIn(safeStart, 1f)
    if (safeEnd <= safeStart) {
        return if (progress >= safeEnd) 1f else 0f
    }
    return ((progress.coerceIn(0f, 1f) - safeStart) / (safeEnd - safeStart))
        .coerceIn(0f, 1f)
}

fun morphProgressFromDrag(
    startProgress: Float,
    deltaY: Float,
    dragRangePx: Float
): Float {
    val safeRange = dragRangePx.coerceAtLeast(DefaultMorphMinimumDragRangePx)
    return (startProgress - deltaY / safeRange).coerceIn(0f, 1f)
}

fun defaultMorphEndpointActivity(progress: Float): DefaultMorphEndpointActivity =
    when (progress.coerceIn(0f, 1f)) {
        0f -> DefaultMorphEndpointActivity.Mini
        1f -> DefaultMorphEndpointActivity.Expanded
        else -> DefaultMorphEndpointActivity.Transition
    }

fun defaultMorphMetadataOwner(
    isMorphActive: Boolean,
    geometryReady: Boolean
): DefaultMorphMetadataOwner =
    if (isMorphActive && geometryReady) {
        DefaultMorphMetadataOwner.Morph
    } else {
        DefaultMorphMetadataOwner.Mini
    }

fun shouldUseDefaultMorph(playerTheme: PlayerTheme): Boolean =
    playerTheme == PlayerTheme.DEFAULT

fun shouldRunDefaultExpandedWork(progress: Float): Boolean =
    progress.coerceIn(0f, 1f) >= DefaultPlayerMorphSpec.ExpensiveContentThreshold

fun defaultMorphTravelDistance(
    endpointBounds: PlayerEndpointBounds,
    elementBounds: DefaultPlayerMorphBounds
): Float {
    val expandedSurface = (
            endpointBounds.expanded as? PlayerBoundsMeasurement.Measured
            )?.bounds
    val miniSurface = elementBounds.miniSurface
    if (!miniSurface.isValidMorphRect() || !expandedSurface.isValidMorphRect()) {
        return DefaultMorphMinimumDragRangePx
    }
    return abs(miniSurface!!.top - expandedSurface!!.top)
        .coerceAtLeast(DefaultMorphMinimumDragRangePx)
}

fun resolveDefaultPlayerMorphGeometry(
    progress: Float,
    endpointBounds: PlayerEndpointBounds,
    elementBounds: DefaultPlayerMorphBounds
): DefaultPlayerMorphGeometry? {
    val miniSurface = elementBounds.miniSurface ?: (
            endpointBounds.mini as? PlayerBoundsMeasurement.Measured
            )?.bounds
    val expandedSurface = (
            endpointBounds.expanded as? PlayerBoundsMeasurement.Measured
            )?.bounds
    val miniArtwork = elementBounds.miniArtwork
    val expandedArtwork = elementBounds.expandedArtwork
    val miniText = elementBounds.miniText
    val expandedText = elementBounds.expandedText
    val miniPlayPause = elementBounds.miniPlayPause
    val expandedPlayPause = elementBounds.expandedPlayPause

    if (!miniSurface.isValidMorphRect() ||
        !expandedSurface.isValidMorphRect() ||
        !miniArtwork.isValidMorphRect() ||
        !expandedArtwork.isValidMorphRect() ||
        !miniText.isValidMorphRect() ||
        !expandedText.isValidMorphRect() ||
        !miniPlayPause.isValidMorphRect() ||
        !expandedPlayPause.isValidMorphRect()
    ) {
        return null
    }

    return DefaultPlayerMorphGeometry(
        surface = interpolateMorphRect(miniSurface!!, expandedSurface!!, progress),
        artwork = interpolateMorphRect(miniArtwork!!, expandedArtwork!!, progress),
        text = interpolateMorphRect(miniText!!, expandedText!!, progress),
        playPause = interpolateMorphRect(
            miniPlayPause!!,
            expandedPlayPause!!,
            progress
        )
    )
}

internal fun Rect?.isValidMorphRect(): Boolean =
    this != null &&
            left.isFinite() &&
            top.isFinite() &&
            right.isFinite() &&
            bottom.isFinite() &&
            width > 0f &&
            height > 0f

private fun replaceMeaningfully(previous: Rect?, next: Rect): Rect? {
    if (!next.isValidMorphRect()) return previous
    if (previous == null ||
        abs(previous.left - next.left) >= BoundsEpsilonPx ||
        abs(previous.top - next.top) >= BoundsEpsilonPx ||
        abs(previous.right - next.right) >= BoundsEpsilonPx ||
        abs(previous.bottom - next.bottom) >= BoundsEpsilonPx
    ) {
        return next
    }
    return previous
}

private fun lerpMorphValue(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
