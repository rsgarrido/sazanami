package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonEndpointMeasurementsTest {
    private val key = PocketHorizonMeasurementKey(listOf("song", "title", "artist"))

    private fun newOwner(density: Float = 1f, fontScale: Float = 1f) = PlayerEndpointBounds().apply {
        updateLayoutFrame(Rect(50f, 80f, 1250f, 980f), Offset(50f, 80f), IntSize(1300, 1000), density, fontScale)
        updateMini(Rect(100f, 850f, 1200f, 950f))
    }

    private fun endpoint(owner: PlayerEndpointBounds, expanded: Boolean = false,
        contentKey: PocketHorizonMeasurementKey = key, offsetY: Float = 0f): PocketHorizonEndpointMeasurements {
        val surface = (if (expanded) Rect(70f, 100f, 1230f, 960f) else Rect(112f, 856f, 1188f, 944f))
            .translate(0f, offsetY)
        val display = Rect(surface.left + 4f, surface.top + 4f, surface.right - 4f, surface.bottom - 4f)
        return PocketHorizonEndpointMeasurements(owner, owner.layoutFrame, contentKey).apply {
            update(PocketHorizonAnchor.Surface, surface)
            update(PocketHorizonAnchor.Display, display)
            update(PocketHorizonAnchor.Artwork, Rect(display.left + 4f, display.top + 4f, display.left + 48f, display.top + 48f))
            update(PocketHorizonAnchor.Metadata, Rect(display.left + 60f, display.top + 4f, display.right - 60f, display.top + 40f))
            update(PocketHorizonAnchor.PlayPause, Rect(display.right - 52f, display.top + 4f, display.right - 4f, display.top + 52f))
            update(PocketHorizonAnchor.ProgressRegion, Rect(display.left + 4f, display.bottom - 4f, display.right - 4f, display.bottom - 1f))
            update(PocketHorizonAnchor.GestureRegion, Rect(display.left + 4f, display.top + 4f, display.right - 60f, display.top + 52f))
        }
    }

    @Test fun completeContainedPairConvertsRootPixelsToLogicalHostPixelsExactlyOnce() {
        val owner = newOwner()
        val mini = endpoint(owner)
        val expanded = endpoint(owner, true)
        val pair = requireNotNull(resolvePocketHorizonEndpointPair(owner, mini, expanded))
        assertEquals(requireNotNull(mini.geometry()).artwork.translate(-50f, -80f), pair.mini.artwork)
        assertEquals(requireNotNull(expanded.geometry()).surface.translate(-50f, -80f), pair.expanded.surface)
    }

    @Test fun partialInvalidAndOutOfSurfaceAnchorsCannotProduceAPair() {
        val owner = newOwner()
        val mini = endpoint(owner)
        val expanded = endpoint(owner, true)
        mini.update(PocketHorizonAnchor.Artwork, Rect.Zero)
        assertNull(resolvePocketHorizonEndpointPair(owner, mini, expanded))
        mini.update(PocketHorizonAnchor.Artwork, Rect(-10f, -10f, 20f, 20f))
        assertNull(mini.geometry())
        expanded.clear()
        assertNull(expanded.geometry())
    }

    @Test fun viewportDensityFontAndArrangementChangesInvalidateEveryOldScope() {
        val owner = newOwner()
        val mini = endpoint(owner)
        val expanded = endpoint(owner, true)
        assertNotNull(resolvePocketHorizonEndpointPair(owner, mini, expanded))
        val frame = requireNotNull(owner.layoutFrame)
        owner.updateLayoutFrame(frame.boundsInRoot, frame.originInRoot, frame.rootSize, 2f, 2f, "changed-insets")
        assertNull(mini.geometry())
        assertNull(expanded.geometry())
        assertNull(resolvePocketHorizonEndpointPair(owner, mini, expanded))
        // Even geometrically unchanged reports from the old callback lifetime remain invalid.
        mini.update(PocketHorizonAnchor.Surface, Rect(112f, 856f, 1188f, 944f))
        assertNull(mini.geometry())
        val freshMini = endpoint(owner)
        val freshExpanded = endpoint(owner, true)
        assertNull(resolvePocketHorizonEndpointPair(owner, freshMini, freshExpanded)) // Mini host is stale.
        owner.updateMini(Rect(100f, 850f, 1200f, 950f))
        assertNotNull(resolvePocketHorizonEndpointPair(owner, freshMini, freshExpanded))
    }

    @Test fun replacedPresentationAndChangedMetadataNeverBorrowPreviousGeometry() {
        val owner = newOwner()
        val mini = endpoint(owner)
        val other = newOwner()
        assertNull(resolvePocketHorizonEndpointPair(owner, mini, endpoint(other, true)))
        assertNull(resolvePocketHorizonEndpointPair(owner, mini, endpoint(owner, true,
            key.copy(values = listOf("song", "new title", "artist")))))
        owner.markMiniStale()
        assertNull(resolvePocketHorizonEndpointPair(owner, mini, endpoint(owner, true)))
    }

    @Test fun disabledMeasurementScopesNeverPublishAnchors() {
        val owner = newOwner()
        val scope = PocketHorizonEndpointMeasurements(owner, owner.layoutFrame, key, enabled = false)
        scope.update(PocketHorizonAnchor.Surface, Rect(1f, 1f, 50f, 50f))
        assertNull(scope.geometry())
    }

    @Test fun missingFramesAndInvalidDensityCannotYieldTransitionGeometry() {
        val missing = PlayerEndpointBounds()
        assertNull(endpoint(missing).geometry())
        for ((density, scale) in listOf(0f to 1f, Float.NaN to 1f, 1f to 0f, 1f to Float.POSITIVE_INFINITY)) {
            val owner = newOwner(density, scale)
            assertNull(resolvePocketHorizonEndpointPair(owner, endpoint(owner), endpoint(owner, true)))
        }
        val owner = newOwner()
        val mini = endpoint(owner, offsetY = 1000f)
        val expanded = endpoint(owner, true)
        // Individually valid endpoints are insufficient if the app's source lies outside its host.
        owner.updateMini(Rect(100f, 1850f, 1200f, 1950f))
        assertNotNull(mini.geometry())
        assertNotNull(expanded.geometry())
        assertNull(resolvePocketHorizonEndpointPair(owner, mini, expanded))
    }
}
