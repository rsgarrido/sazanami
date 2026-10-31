package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.player.*
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.shouldUseImmersivePlayerSystemBars
import io.github.rsgarrido.sazanami.ui.playerSystemBarSurface
import io.github.rsgarrido.sazanami.ui.SystemBarSurface
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonRuntimePolicyTest {
    @Test fun ordinarySavedSelectionNominatesTheCompleteRuntimeWithExistingOverlayCapabilities() {
        val tokens = PlayerTheme.DEFAULT.defaultTokens()
        val context = TabletLandscapePlayerContext(PlayerDeviceKind.TABLET, 800, DpSize(1200.dp, 900.dp))
        val candidate = requireNotNull(tabletLandscapePlayerCandidate(
            TabletLandscapeTheme.fromId("pocket_horizon"), tokens, 1f))
        assertTrue(TabletLandscapeTheme.selectableThemes.contains(TabletLandscapeTheme.POCKET_HORIZON))
        assertEquals(minimumPocketHorizonSize(1f), candidate.minimumSize)
        assertEquals(PlayerPresentationIdentity.PocketHorizon,
            resolvePlayerPresentation(PlayerTheme.DEFAULT, tokens, context, candidate).identity)
        for (size in listOf(DpSize(600.dp, 500.dp), DpSize(900.dp, 1200.dp))) {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.DEFAULT),
                resolvePlayerPresentation(PlayerTheme.DEFAULT, tokens, context.copy(availableSize = size), candidate).identity)
        }
        assertTrue(supportsNowPlayingMore(PlayerPresentationIdentity.PocketHorizon))
        assertTrue(supportsNowPlayingArtworkViewer(PlayerPresentationIdentity.PocketHorizon))
    }

    @Test fun interpolationUsesTheSameEndpointsInBothDirectionsAndClampsUnsafeProgress() {
        val source = Rect(10f, 400f, 600f, 468f)
        val destination = Rect(20f, 20f, 1000f, 700f)
        assertEquals(source, interpolateHorizonRect(source, destination, 0f))
        assertEquals(destination, interpolateHorizonRect(source, destination, 1f))
        assertEquals(interpolateHorizonRect(source, destination, .25f),
            interpolateHorizonRect(destination, source, .75f))
        assertEquals(source, interpolateHorizonRect(source, destination, Float.NaN))
        assertEquals(destination, interpolateHorizonRect(source, destination, 2f))
    }

    @Test fun extremeFontClearanceMatchesTheRealCompactHeightWithoutChangingTheNormalReservation() {
        assertEquals(0.dp, pocketHorizonMiniExtraHeight(1f))
        assertEquals(0.dp, pocketHorizonMiniExtraHeight(2f))
        val large = requireNotNull(resolvePocketHorizonMiniLayout(600.dp, 3.5f))
        assertEquals(large.surfaceHeight - PocketHorizonMiniMetrics.SurfaceHeight, pocketHorizonMiniExtraHeight(3.5f))
    }

    @Test fun horizonUsesDarkExpandedSurfacesAndReturnsBarsToLyricsAndShellOwnership() {
        val identity = PlayerPresentationIdentity.PocketHorizon
        assertFalse(shouldUseImmersivePlayerSystemBars(identity, false, false))
        assertTrue(shouldUseImmersivePlayerSystemBars(identity, true, false))
        assertFalse(shouldUseImmersivePlayerSystemBars(identity, true, true))
        assertEquals(SystemBarSurface.SHELL, playerSystemBarSurface(identity, false, true, false))
        assertEquals(SystemBarSurface.MODERN_PLAYER, playerSystemBarSurface(identity, true, true, false))
        assertEquals(SystemBarSurface.LYRICS, playerSystemBarSurface(identity, true, true, true))
    }
}
