package com.example.cdplaya.ui.player.modern

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernArtworkPaletteTransitionTest {
    @Test
    fun uncachedRequestRetainsExistingPaletteWhileLoading() {
        val paletteA = palette(Color.Red)
        val requestB = ModernArtworkPaletteRequest(2L, "artwork-b")
        val loading = beginModernArtworkPaletteRequest(
            previousState = resolvedState(paletteA),
            request = requestB,
            immediatePalette = null,
            fallbackPalette = palette(Color.Magenta, isFallback = true)
        )

        assertTrue(loading.isLoading)
        assertEquals(paletteA, loading.displayedPalette)
        assertFalse(loading.displayedPalette.isFallback)
    }

    @Test
    fun resolvedPaletteReplacesPreviousPaletteForCurrentRequest() {
        val paletteA = palette(Color.Red)
        val paletteB = palette(Color.Blue)
        val requestB = ModernArtworkPaletteRequest(2L, "artwork-b")
        val loading = beginModernArtworkPaletteRequest(
            previousState = resolvedState(paletteA),
            request = requestB,
            immediatePalette = null,
            fallbackPalette = palette(Color.Magenta, isFallback = true)
        )

        val resolved = completeModernArtworkPaletteRequest(loading, requestB, paletteB)

        assertFalse(resolved.isLoading)
        assertEquals(paletteB, resolved.displayedPalette)
    }

    @Test
    fun failureAndMissingArtworkResolveToFallbackInsteadOfStayingStale() {
        val paletteA = palette(Color.Red)
        val fallback = palette(Color.Magenta, isFallback = true)
        val failedRequest = ModernArtworkPaletteRequest(2L, "artwork-b")
        val loading = beginModernArtworkPaletteRequest(
            previousState = resolvedState(paletteA),
            request = failedRequest,
            immediatePalette = null,
            fallbackPalette = fallback
        )
        val failed = completeModernArtworkPaletteRequest(loading, failedRequest, fallback)
        val missing = beginModernArtworkPaletteRequest(
            previousState = failed,
            request = ModernArtworkPaletteRequest(3L, null),
            immediatePalette = null,
            fallbackPalette = fallback
        )

        assertEquals(fallback, failed.displayedPalette)
        assertFalse(failed.isLoading)
        assertEquals(fallback, missing.displayedPalette)
        assertFalse(missing.isLoading)
    }

    @Test
    fun staleResultsCannotOverwriteNewestRapidRequest() {
        val paletteA = palette(Color.Red)
        val paletteB = palette(Color.Blue)
        val paletteC = palette(Color.Green)
        val fallback = palette(Color.Magenta, isFallback = true)
        val requestB = ModernArtworkPaletteRequest(2L, "artwork-b")
        val requestC = ModernArtworkPaletteRequest(3L, "artwork-c")
        val loadingB = beginModernArtworkPaletteRequest(
            resolvedState(paletteA), requestB, null, fallback
        )
        val loadingC = beginModernArtworkPaletteRequest(
            loadingB, requestC, null, fallback
        )

        val staleB = completeModernArtworkPaletteRequest(loadingC, requestB, paletteB)
        val resolvedC = completeModernArtworkPaletteRequest(staleB, requestC, paletteC)

        assertEquals(loadingC, staleB)
        assertEquals(paletteA, staleB.displayedPalette)
        assertEquals(paletteC, resolvedC.displayedPalette)
        assertFalse(resolvedC.isLoading)
    }

    @Test
    fun cachedPaletteCanResolveImmediatelyWithoutLoadingFallback() {
        val paletteA = palette(Color.Red)
        val paletteB = palette(Color.Blue)
        val resolved = beginModernArtworkPaletteRequest(
            previousState = resolvedState(paletteA),
            request = ModernArtworkPaletteRequest(2L, "artwork-b"),
            immediatePalette = paletteB,
            fallbackPalette = palette(Color.Magenta, isFallback = true)
        )

        assertEquals(paletteB, resolved.displayedPalette)
        assertFalse(resolved.isLoading)
    }

    private fun resolvedState(palette: ModernArtworkPalette) =
        ModernArtworkPaletteLoadState(
            request = ModernArtworkPaletteRequest(1L, "artwork-a"),
            displayedPalette = palette,
            isLoading = false
        )

    private fun palette(
        color: Color,
        isFallback: Boolean = false
    ) = ModernArtworkPalette(
        dominant = color,
        primary = color,
        secondary = color,
        accent = color,
        readableForeground = Color.White,
        isFallback = isFallback
    )
}
