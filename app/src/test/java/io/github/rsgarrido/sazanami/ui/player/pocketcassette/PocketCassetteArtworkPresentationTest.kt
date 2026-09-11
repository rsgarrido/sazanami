package io.github.rsgarrido.sazanami.ui.player.pocketcassette

import org.junit.Assert.assertEquals
import org.junit.Test

class PocketCassetteArtworkPresentationTest {
    @Test
    fun `artwork overlay reaches the same dimming at the expanded morph endpoint`() {
        assertEquals(0f, pocketCassetteArtworkOverlayAlpha(0f))
        assertEquals(
            PocketCassetteArtworkDimmingAlpha / 2f,
            pocketCassetteArtworkOverlayAlpha(.5f)
        )
        assertEquals(PocketCassetteArtworkDimmingAlpha, pocketCassetteArtworkOverlayAlpha(1f))
    }

    @Test
    fun `artwork overlay progress remains bounded`() {
        assertEquals(0f, pocketCassetteArtworkOverlayAlpha(-1f))
        assertEquals(PocketCassetteArtworkDimmingAlpha, pocketCassetteArtworkOverlayAlpha(2f))
    }
}
