package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphLayoutFrame
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
class DefaultPlayerMorphBounds internal constructor(
    private val currentLayoutFrame: () -> PlayerMorphLayoutFrame? = { null }
) {
    // Measurements stay in root coordinates. Only complete endpoint snapshots are converted to
    // PlayerMorphHost coordinates, once, before interpolation. A null frame supports standalone
    // root-aligned callers that do not use PlayerMorphHost.
    private enum class Element {
        MiniSurface, MiniArtwork, MiniText, MiniPlayPause,
        ExpandedArtwork, ExpandedText, ExpandedPlayPause
    }

    private data class Measurement(
        val bounds: Rect?,
        val frame: PlayerMorphLayoutFrame?,
        val isValidUpdate: Boolean
    )

    private data class Endpoints(
        val mini: DefaultPlayerMorphGeometry,
        val expanded: DefaultPlayerMorphGeometry,
        val measurements: Map<Element, Measurement>,
        val frame: PlayerMorphLayoutFrame?,
        val surfaceInRoot: Rect
    ) {
        fun interpolate(progress: Float) = DefaultPlayerMorphGeometry(
            surface = interpolateMorphRect(mini.surface, expanded.surface, progress),
            artwork = interpolateMorphRect(mini.artwork, expanded.artwork, progress),
            text = interpolateMorphRect(mini.text, expanded.text, progress),
            playPause = interpolateMorphRect(mini.playPause, expanded.playPause, progress)
        )
    }

    private var measurements by mutableStateOf<Map<Element, Measurement>>(emptyMap())
    // This is a cache of complete measurements, not animation state. Holding the previous local
    // endpoints avoids flashes while a new layout is being measured, even if progress reverses.
    private var lastCompleteEndpoints: Endpoints? = null

    val miniSurface: Rect? get() = measurements[Element.MiniSurface]?.bounds
    val miniArtwork: Rect? get() = measurements[Element.MiniArtwork]?.bounds
    val miniText: Rect? get() = measurements[Element.MiniText]?.bounds
    val miniPlayPause: Rect? get() = measurements[Element.MiniPlayPause]?.bounds
    val expandedArtwork: Rect? get() = measurements[Element.ExpandedArtwork]?.bounds
    val expandedText: Rect? get() = measurements[Element.ExpandedText]?.bounds
    val expandedPlayPause: Rect? get() = measurements[Element.ExpandedPlayPause]?.bounds

    val hasRequiredElements: Boolean
        get() = miniSurface.isValidMorphRect() &&
                miniArtwork.isValidMorphRect() &&
                miniText.isValidMorphRect() &&
                miniPlayPause.isValidMorphRect() &&
                expandedArtwork.isValidMorphRect() &&
                expandedText.isValidMorphRect() &&
                expandedPlayPause.isValidMorphRect()

    fun updateMiniSurface(bounds: Rect) {
        update(Element.MiniSurface, bounds)
    }

    fun updateMiniArtwork(bounds: Rect) {
        update(Element.MiniArtwork, bounds)
    }

    fun updateMiniText(bounds: Rect) {
        update(Element.MiniText, bounds)
    }

    fun updateMiniPlayPause(bounds: Rect) {
        update(Element.MiniPlayPause, bounds)
    }

    fun updateExpandedArtwork(bounds: Rect) {
        update(Element.ExpandedArtwork, bounds)
    }

    fun updateExpandedText(bounds: Rect) {
        update(Element.ExpandedText, bounds)
    }

    fun updateExpandedPlayPause(bounds: Rect) {
        update(Element.ExpandedPlayPause, bounds)
    }

    fun clearExpanded() {
        measurements = measurements - setOf(
            Element.ExpandedArtwork, Element.ExpandedText, Element.ExpandedPlayPause
        )
        lastCompleteEndpoints = null
    }

    private fun update(element: Element, bounds: Rect) {
        val previous = measurements[element]
        val next = Measurement(
            replaceMeaningfully(previous?.bounds, bounds), currentLayoutFrame(),
            isValidUpdate = bounds.isValidMorphRect()
        )
        if (next != previous) measurements = measurements + (element to next)
    }

    internal fun resolveGeometry(
        progress: Float,
        endpointBounds: PlayerEndpointBounds
    ): DefaultPlayerMorphGeometry? {
        completeEndpoints(endpointBounds)?.let { lastCompleteEndpoints = it }
        return lastCompleteEndpoints?.interpolate(progress)
    }

    internal fun travelDistance(endpointBounds: PlayerEndpointBounds): Float {
        val frame = currentLayoutFrame()
        val expandedSurface = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
        val mini = measurements[Element.MiniSurface]
        // Source surfaces are available before the expanded placeholders are first composed.
        if (mini?.frame == frame && mini?.isValidUpdate == true && mini.bounds.isValidMorphRect() &&
            expandedSurface.isValidMorphRect() &&
            (frame == null || expandedSurface == frame.boundsInRoot) &&
            endpointBounds.mini is PlayerBoundsMeasurement.Measured
        ) {
            return abs(mini!!.bounds!!.top - expandedSurface!!.top)
                .coerceAtLeast(DefaultMorphMinimumDragRangePx)
        }
        return lastCompleteEndpoints?.let {
            abs(it.mini.surface.top - it.expanded.surface.top)
                .coerceAtLeast(DefaultMorphMinimumDragRangePx)
        } ?: DefaultMorphMinimumDragRangePx
    }

    private fun completeEndpoints(endpointBounds: PlayerEndpointBounds): Endpoints? {
        val frame = currentLayoutFrame()
        val expandedSurface = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
        lastCompleteEndpoints?.let { cached ->
            if (cached.measurements === measurements && cached.frame == frame &&
                cached.surfaceInRoot == expandedSurface
            ) return cached
        }
        if (endpointBounds.mini !is PlayerBoundsMeasurement.Measured ||
            !expandedSurface.isValidMorphRect() ||
            (frame != null && expandedSurface != frame.boundsInRoot) ||
            Element.entries.any { element ->
                val measurement = measurements[element]
                measurement == null || !measurement.isValidUpdate || measurement.frame != frame ||
                    !measurement.bounds.isValidMorphRect()
            }
        ) return null

        val origin = frame?.originInRoot ?: Offset.Zero
        fun Rect.inHost(): Rect = translate(-origin.x, -origin.y)
        return Endpoints(
            mini = DefaultPlayerMorphGeometry(
                miniSurface!!.inHost(), miniArtwork!!.inHost(), miniText!!.inHost(),
                miniPlayPause!!.inHost()
            ),
            expanded = DefaultPlayerMorphGeometry(
                expandedSurface!!.inHost(), expandedArtwork!!.inHost(), expandedText!!.inHost(),
                expandedPlayPause!!.inHost()
            ),
            measurements = measurements,
            frame = frame,
            surfaceInRoot = expandedSurface
        )
    }
}

/** Rectangles in logical PlayerMorphHost pixels; render without another root-origin offset. */
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
): Float = elementBounds.travelDistance(endpointBounds)

fun resolveDefaultPlayerMorphGeometry(
    progress: Float,
    endpointBounds: PlayerEndpointBounds,
    elementBounds: DefaultPlayerMorphBounds
): DefaultPlayerMorphGeometry? = elementBounds.resolveGeometry(progress, endpointBounds)

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
