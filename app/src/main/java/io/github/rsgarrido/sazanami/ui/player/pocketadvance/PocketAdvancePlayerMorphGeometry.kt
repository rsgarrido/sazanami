package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphLayoutFrame
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import kotlin.math.abs

/**
 * Pocket Advance records visible rectangles, never the larger button hit regions. All reports are
 * root-relative; only a complete, current pair is converted to PlayerMorphHost coordinates.
 */
@Stable
class PocketAdvancePlayerMorphBounds internal constructor(
    private val currentLayoutFrame: () -> PlayerMorphLayoutFrame? = { null },
    private val currentIdentity: () -> PlayerPresentationIdentity = {
        PlayerPresentationIdentity.PocketAdvance
    },
    // Lyrics applies a presentation transform above the endpoint. Its transient coordinates must
    // never replace logical host-local anchors. Resolving the retained pair remains allowed.
    private val canMeasure: () -> Boolean = { true }
) {
    private enum class Element {
        MiniSurface, MiniDisplay, MiniArtwork, MiniTitle, MiniArtist, MiniPlayPause, MiniProgress,
        ExpandedSurface, ExpandedDisplay, ExpandedArtwork, ExpandedTitle, ExpandedArtist, ExpandedPlayPause, ExpandedProgress
    }

    private data class Measurement(
        val bounds: Rect,
        val frame: PlayerMorphLayoutFrame?,
        val identityEpoch: Long
    )

    private data class Endpoints(
        val mini: PocketAdvanceMorphGeometry,
        val expanded: PocketAdvanceMorphGeometry,
        val frame: PlayerMorphLayoutFrame?,
        val identityEpoch: Long,
        val measurements: Map<Element, Measurement>
    ) {
        fun interpolate(progress: Float) = PocketAdvanceMorphGeometry(
            shell = interpolatePocketAdvanceRect(mini.shell, expanded.shell, progress),
            artwork = interpolatePocketAdvanceRect(mini.artwork, expanded.artwork, progress),
            title = interpolatePocketAdvanceRect(mini.title, expanded.title, progress),
            artist = interpolatePocketAdvanceRect(mini.artist, expanded.artist, progress),
            playPause = interpolatePocketAdvanceRect(mini.playPause, expanded.playPause, progress),
            display = interpolatePocketAdvanceRect(mini.display, expanded.display, progress),
            progressTrack = interpolatePocketAdvanceRect(mini.progressTrack, expanded.progressTrack, progress)
        )
    }

    private var measurements by mutableStateOf<Map<Element, Measurement>>(emptyMap())
    private var identity = currentIdentity()
    private var identityEpoch = 0L
    // This cache has no progress or animation controller. A reversal always interpolates the same
    // complete endpoints at the new shared progress, including while a replacement layout measures.
    private var lastCompleteEndpoints: Endpoints? = null

    val miniSurface: Rect? get() = measurements[Element.MiniSurface]?.bounds
    val expandedSurface: Rect? get() = measurements[Element.ExpandedSurface]?.bounds

    fun updateMiniSurface(bounds: Rect) = update(Element.MiniSurface, bounds)
    fun updateMiniDisplay(bounds: Rect) = update(Element.MiniDisplay, bounds)
    fun updateMiniArtwork(bounds: Rect) = update(Element.MiniArtwork, bounds)
    fun updateMiniTitle(bounds: Rect) = update(Element.MiniTitle, bounds)
    fun updateMiniArtist(bounds: Rect) = update(Element.MiniArtist, bounds)
    fun updateMiniPlayPause(bounds: Rect) = update(Element.MiniPlayPause, bounds)
    fun updateMiniProgress(bounds: Rect) = update(Element.MiniProgress, bounds)
    fun updateExpandedProgress(bounds: Rect) = update(Element.ExpandedProgress, bounds)
    fun updateExpandedSurface(bounds: Rect) = update(Element.ExpandedSurface, bounds)
    fun updateExpandedDisplay(bounds: Rect) = update(Element.ExpandedDisplay, bounds)
    fun updateExpandedArtwork(bounds: Rect) = update(Element.ExpandedArtwork, bounds)
    fun updateExpandedTitle(bounds: Rect) = update(Element.ExpandedTitle, bounds)
    fun updateExpandedArtist(bounds: Rect) = update(Element.ExpandedArtist, bounds)
    fun updateExpandedPlayPause(bounds: Rect) = update(Element.ExpandedPlayPause, bounds)

    /** A presentation lifetime must never inherit a previous lifetime's measured endpoints. */
    fun clear() {
        identityEpoch += 1L
        measurements = emptyMap()
        lastCompleteEndpoints = null
    }

    private fun synchronizeIdentity() {
        val nextIdentity = currentIdentity()
        if (identity != nextIdentity) {
            identity = nextIdentity
            identityEpoch += 1L
            // Old reports remain unusable even if the identity subsequently switches back.
            lastCompleteEndpoints = null
        }
    }

    private fun update(element: Element, bounds: Rect) {
        synchronizeIdentity()
        if (identity != PlayerPresentationIdentity.PocketAdvance || !canMeasure()) return
        val next = Measurement(bounds, currentLayoutFrame(), identityEpoch)
        if (measurements[element] != next) measurements = measurements + (element to next)
    }

    internal fun resolveGeometry(
        progress: Float,
        endpointBounds: PlayerEndpointBounds
    ): PocketAdvanceMorphGeometry? {
        synchronizeIdentity()
        completeEndpoints(endpointBounds)?.let { lastCompleteEndpoints = it }
        return lastCompleteEndpoints?.takeIf { it.identityEpoch == identityEpoch }
            ?.interpolate(progress)
    }

    internal fun travelDistance(endpointBounds: PlayerEndpointBounds): Float {
        synchronizeIdentity()
        completeEndpoints(endpointBounds)?.let { lastCompleteEndpoints = it }
        lastCompleteEndpoints?.takeIf { it.identityEpoch == identityEpoch }?.let {
            return abs(it.mini.shell.top - it.expanded.shell.top)
                .coerceAtLeast(PocketAdvanceMinimumMorphTravelPx)
        }
        // A first upward gesture can start before the destination has been composed. Use the
        // measured source and current host rather than borrowing a previous presentation's bounds.
        val source = measurements[Element.MiniSurface]
        val frame = currentLayoutFrame()
        if (endpointBounds.mini is PlayerBoundsMeasurement.Measured && source != null &&
            source.frame == frame && source.identityEpoch == identityEpoch &&
            source.bounds.isUsablePocketAdvanceRect()
        ) {
            val destinationTop = frame?.boundsInRoot?.top
                ?: (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds?.top
            if (destinationTop != null && destinationTop.isFinite()) {
                return abs(source.bounds.top - destinationTop)
                    .coerceAtLeast(PocketAdvanceMinimumMorphTravelPx)
            }
        }
        return PocketAdvanceMinimumMorphTravelPx
    }

    private fun completeEndpoints(endpointBounds: PlayerEndpointBounds): Endpoints? {
        if (identity != PlayerPresentationIdentity.PocketAdvance) return null
        val frame = currentLayoutFrame()
        if (endpointBounds.mini !is PlayerBoundsMeasurement.Measured) return null
        val host = (endpointBounds.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
            ?: return null
        if (!host.isUsablePocketAdvanceRect() || (frame != null && host != frame.boundsInRoot)) {
            return null
        }
        if (Element.entries.any { element ->
                val measurement = measurements[element]
                measurement == null || measurement.frame != frame ||
                    measurement.identityEpoch != identityEpoch ||
                    !measurement.bounds.isUsablePocketAdvanceRect()
            }
        ) return null

        lastCompleteEndpoints?.let { previous ->
            if (previous.frame == frame && previous.identityEpoch == identityEpoch &&
                previous.measurements === measurements
            ) return previous
        }
        val origin = frame?.originInRoot ?: Offset.Zero
        fun rect(element: Element) = measurements.getValue(element).bounds
            .translate(-origin.x, -origin.y)
        return Endpoints(
            mini = PocketAdvanceMorphGeometry(
                rect(Element.MiniSurface), rect(Element.MiniArtwork), rect(Element.MiniTitle),
                rect(Element.MiniArtist), rect(Element.MiniPlayPause), rect(Element.MiniDisplay),
                rect(Element.MiniProgress)
            ),
            expanded = PocketAdvanceMorphGeometry(
                rect(Element.ExpandedSurface), rect(Element.ExpandedArtwork),
                rect(Element.ExpandedTitle), rect(Element.ExpandedArtist),
                rect(Element.ExpandedPlayPause), rect(Element.ExpandedDisplay), rect(Element.ExpandedProgress)
            ),
            frame = frame,
            identityEpoch = identityEpoch,
            measurements = measurements
        )
    }
}

/** All rectangles are host-local pixels, including the actual device footprint. */
data class PocketAdvanceMorphGeometry(
    val shell: Rect,
    val artwork: Rect,
    val title: Rect,
    val artist: Rect,
    val playPause: Rect,
    val display: Rect,
    val progressTrack: Rect
)

internal const val PocketAdvanceMinimumMorphTravelPx = 48f

internal data class PocketAdvanceMiniChromeGeometry(
    val cross: Rect,
    val alpha: Float
)

/**
 * The non-interactive 24dp cross precedes the measured display by its 10dp gap.
 * Using the display anchor also follows the incoming mini song's AnimatedContent placement.
 * Carry them with those moving anchors and let them yield before device controls enter.
 */
internal fun pocketAdvanceMiniChromeGeometry(
    geometry: PocketAdvanceMorphGeometry?,
    progress: Float,
    density: Float
): PocketAdvanceMiniChromeGeometry? {
    if (geometry == null || !density.isFinite() || density <= 0f) return null
    val crossSize = 24f * density
    return PocketAdvanceMiniChromeGeometry(
        cross = Rect(
            geometry.display.left - 10f * density - crossSize,
            geometry.display.center.y - crossSize / 2f,
            geometry.display.left - 10f * density,
            geometry.display.center.y + crossSize / 2f
        ),
        alpha = 1f - (pocketAdvanceSafeProgress(progress) / 0.18f).coerceIn(0f, 1f)
    )
}

internal fun interpolatePocketAdvanceRect(start: Rect, end: Rect, progress: Float): Rect {
    val p = pocketAdvanceSafeProgress(progress)
    fun lerp(a: Float, b: Float) = a + (b - a) * p
    return Rect(lerp(start.left, end.left), lerp(start.top, end.top),
        lerp(start.right, end.right), lerp(start.bottom, end.bottom))
}

private fun Rect.isUsablePocketAdvanceRect(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        width > 0f && height > 0f

internal fun pocketAdvanceSafeProgress(progress: Float): Float =
    if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

private fun pocketAdvanceReveal(progress: Float, start: Float, end: Float): Float =
    ((pocketAdvanceSafeProgress(progress) - start) / (end - start)).coerceIn(0f, 1f)

data class PocketAdvanceMorphVisualState(
    val isReady: Boolean,
    val morphOwnsSharedVisuals: Boolean,
    val endpointOwnsVisuals: Boolean,
    val hideMiniVisuals: Boolean,
    val shellAlpha: Float,
    val bezelAlpha: Float,
    val hardwareAlpha: Float,
    val displayDetailsAlpha: Float,
    val inputEnabled: Boolean,
    val collapseGestureEnabled: Boolean
)

internal fun pocketAdvanceMorphVisualState(
    progress: Float,
    hasGeometry: Boolean
): PocketAdvanceMorphVisualState {
    val p = pocketAdvanceSafeProgress(progress)
    // A recreated/resolved stable expanded presentation has no outgoing geometry to morph from.
    // Show its actual endpoint immediately, then transfer to the warmed shared owner at the same
    // destination rectangles once a complete pair exists. Partial transitions still wait for it.
    val endpointOwnsVisuals = !hasGeometry && p >= 0.999f
    return PocketAdvanceMorphVisualState(
        isReady = hasGeometry,
        morphOwnsSharedVisuals = hasGeometry && p > 0f,
        endpointOwnsVisuals = endpointOwnsVisuals,
        hideMiniVisuals = (hasGeometry && p > 0f) || endpointOwnsVisuals,
        shellAlpha = if ((hasGeometry && p > 0f) || endpointOwnsVisuals) 1f else 0f,
        bezelAlpha = if (endpointOwnsVisuals) 1f else if (hasGeometry) pocketAdvanceReveal(p, 0.12f, 0.62f) else 0f,
        hardwareAlpha = if (endpointOwnsVisuals) 1f else if (hasGeometry) pocketAdvanceReveal(p, 0.42f, 0.90f) else 0f,
        displayDetailsAlpha = if (endpointOwnsVisuals) 1f else if (hasGeometry) pocketAdvanceReveal(p, 0.38f, 0.88f) else 0f,
        inputEnabled = (hasGeometry || endpointOwnsVisuals) && p >= 0.999f,
        collapseGestureEnabled = (hasGeometry || endpointOwnsVisuals) && p >= 0.68f
    )
}
