package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphLayoutFrame

internal enum class PocketHorizonAnchor { Surface, Display, Artwork, Metadata, PlayPause, ProgressRegion, GestureRegion }

/** Structural identity only: position ticks and play/pause intent never invalidate placement. */
internal data class PocketHorizonMeasurementKey(val values: List<Any?>)

internal fun pocketHorizonMeasurementKey(song: Song?, direction: LayoutDirection) =
    PocketHorizonMeasurementKey(listOf(song?.stableUiKey(), song?.title, song?.artist, song?.album,
        song?.albumArtUri?.toString(), song?.dateModifiedEpochSeconds, song?.fileSizeBytes,
        song?.artworkEnrichmentVersion, direction))

/** Root-relative visible layout boxes. ProgressRegion is a layout/input region, not a painted track. */
internal data class PocketHorizonEndpointGeometry(
    val surface: Rect, val display: Rect, val artwork: Rect, val metadata: Rect,
    val playPause: Rect, val progressRegion: Rect, val gestureRegion: Rect
) {
    fun translated(x: Float, y: Float) = PocketHorizonEndpointGeometry(
        surface.translate(x, y), display.translate(x, y), artwork.translate(x, y),
        metadata.translate(x, y), playPause.translate(x, y), progressRegion.translate(x, y), gestureRegion.translate(x, y))

    internal fun isValid(): Boolean = listOf(surface, display, artwork, metadata, playPause, progressRegion, gestureRegion)
        .all { it.isUsableHorizonRect() } && surface.containsHorizonRect(display) &&
        listOf(artwork, metadata, playPause, progressRegion, gestureRegion).all { display.containsHorizonRect(it) }
}

/** One layout/content lifetime. Never retains a previous complete pair across a changed frame. */
@Stable
internal class PocketHorizonEndpointMeasurements(
    val owner: PlayerEndpointBounds,
    val frame: PlayerMorphLayoutFrame?,
    val contentKey: PocketHorizonMeasurementKey,
    private val enabled: Boolean = true
) {
    private val anchors = mutableStateMapOf<PocketHorizonAnchor, Rect>()
    // Like PlayerMorphHost's arrangement boundary, a new scope must position even unchanged boxes
    // again. Metadata/frame replacement must not leave a fresh holder waiting on a moved rectangle.
    internal val measurementBoundary: Modifier = object : LayoutModifier {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placed = measurable.measure(constraints)
            return layout(placed.width, placed.height) { placed.place(0, 0) }
        }
    }

    fun update(anchor: PocketHorizonAnchor, rect: Rect) {
        if (!enabled || frame == null || owner.layoutFrame != frame) return
        // Invalid replacement measurements retire an earlier anchor instead of keeping stale data.
        if (rect.isUsableHorizonRect()) anchors[anchor] = rect else anchors.remove(anchor)
    }

    fun clear() = anchors.clear()

    fun geometry(): PocketHorizonEndpointGeometry? {
        if (!enabled || frame == null || owner.layoutFrame != frame ||
            !frame.density.isFinite() || frame.density <= 0f || !frame.fontScale.isFinite() || frame.fontScale <= 0f ||
            PocketHorizonAnchor.entries.any { it !in anchors }) return null
        return PocketHorizonEndpointGeometry(
            anchors.getValue(PocketHorizonAnchor.Surface), anchors.getValue(PocketHorizonAnchor.Display),
            anchors.getValue(PocketHorizonAnchor.Artwork), anchors.getValue(PocketHorizonAnchor.Metadata),
            anchors.getValue(PocketHorizonAnchor.PlayPause), anchors.getValue(PocketHorizonAnchor.ProgressRegion),
            anchors.getValue(PocketHorizonAnchor.GestureRegion)
        ).takeIf { it.isValid() }
    }
}

/** Session 4B must remember the owner per presentation epoch and disable reports under transforms. */
@Composable
internal fun rememberPocketHorizonEndpointMeasurements(
    owner: PlayerEndpointBounds,
    song: Song?,
    enabled: Boolean = true
): PocketHorizonEndpointMeasurements {
    val frame = owner.layoutFrame
    val contentKey = pocketHorizonMeasurementKey(song, LocalLayoutDirection.current)
    return remember(owner, frame, contentKey, enabled) {
        PocketHorizonEndpointMeasurements(owner, frame, contentKey, enabled)
    }
}

internal fun Modifier.horizonAnchor(
    measurements: PocketHorizonEndpointMeasurements?, anchor: PocketHorizonAnchor
): Modifier = if (measurements == null) this else then(measurements.measurementBoundary).onGloballyPositioned {
    measurements.update(anchor, it.boundsInRoot())
}

/** Complete, current endpoints converted once to the existing PlayerMorphHost's logical pixels. */
internal data class PocketHorizonEndpointPair(
    val mini: PocketHorizonEndpointGeometry,
    val expanded: PocketHorizonEndpointGeometry,
    val frame: PlayerMorphLayoutFrame
)

internal fun resolvePocketHorizonEndpointPair(
    owner: PlayerEndpointBounds,
    mini: PocketHorizonEndpointMeasurements,
    expanded: PocketHorizonEndpointMeasurements
): PocketHorizonEndpointPair? {
    val frame = owner.layoutFrame ?: return null
    if (mini === expanded || mini.owner !== owner || expanded.owner !== owner || mini.frame != frame || expanded.frame != frame ||
        mini.contentKey != expanded.contentKey) return null
    val miniHost = (owner.mini as? PlayerBoundsMeasurement.Measured)?.bounds ?: return null
    val expandedHost = (owner.expanded as? PlayerBoundsMeasurement.Measured)?.bounds ?: return null
    if (expandedHost != frame.boundsInRoot) return null
    val source = mini.geometry() ?: return null
    val destination = expanded.geometry() ?: return null
    if (!miniHost.containsHorizonRect(source.surface) || !expandedHost.containsHorizonRect(source.surface) ||
        !expandedHost.containsHorizonRect(destination.surface)) return null
    val origin = frame.originInRoot
    return PocketHorizonEndpointPair(source.translated(-origin.x, -origin.y),
        destination.translated(-origin.x, -origin.y), frame)
}

private fun Rect.isUsableHorizonRect() = left.isFinite() && top.isFinite() && right.isFinite() &&
    bottom.isFinite() && width > 0f && height > 0f

private fun Rect.containsHorizonRect(other: Rect): Boolean {
    // Allow a physical pixel for independent Compose rounding, never an off-screen endpoint.
    val tolerance = 1f
    return other.left >= left - tolerance && other.top >= top - tolerance &&
        other.right <= right + tolerance && other.bottom <= bottom + tolerance
}
