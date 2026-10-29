package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.data.PlayerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPresentationBoundaryTest {
    private val advance = PlayerPresentationIdentity.PocketAdvance

    @Test
    fun existingThemeChangesAndResizesRetainTheirOriginalTransitionPolicy() {
        PlayerTheme.entries.forEach { previous ->
            PlayerTheme.entries.forEach { next ->
                listOf(false, true).forEach { resized ->
                    assertFalse(requiresPlayerPresentationBoundary(
                        PlayerPresentationIdentity.Existing(previous),
                        PlayerPresentationIdentity.Existing(next),
                        resized
                    ))
                }
            }
        }
    }

    @Test
    fun enteringOrLeavingAdvanceRequiresABoundaryEvenWithoutAResize() {
        PlayerTheme.entries.forEach { theme ->
            val existing = PlayerPresentationIdentity.Existing(theme)
            listOf(false, true).forEach { resized ->
                assertTrue(requiresPlayerPresentationBoundary(existing, advance, resized))
                assertTrue(requiresPlayerPresentationBoundary(advance, existing, resized))
            }
        }
    }

    @Test
    fun advanceViewportChangeRequiresFreshPointerAndMeasurementLifetimes() {
        assertTrue(requiresPlayerPresentationBoundary(advance, advance, viewportChanged = true))
        assertFalse(requiresPlayerPresentationBoundary(advance, advance, viewportChanged = false))
    }
}
