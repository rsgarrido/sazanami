package io.github.rsgarrido.sazanami.ui.player.retrorack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroRackVisualProfileTest {
    @Test
    fun `same song identity produces same profile`() {
        val first = buildRetroRackVisualProfile(
            42L,
            "Night Drive",
            "Circuit Club",
            "After Hours"
        )
        val second = buildRetroRackVisualProfile(
            42L,
            "Night Drive",
            "Circuit Club",
            "After Hours"
        )

        assertEquals(first, second)
    }

    @Test
    fun `different song identity changes visual pattern`() {
        val first = buildRetroRackVisualProfile(
            42L,
            "Night Drive",
            "Circuit Club",
            "After Hours"
        )
        val second = buildRetroRackVisualProfile(
            43L,
            "Morning Train",
            "Signal House",
            "Early Shift"
        )

        assertNotEquals(first.levels, second.levels)
        assertNotEquals(first.accent, second.accent)
        assertTrue(first.levels.all { level -> level in 0.24f..0.94f })
    }

    @Test
    fun `songs from same album share visualizer colors but keep unique patterns`() {
        val first = buildRetroRackVisualProfile(
            42L,
            "Opening Track",
            "Circuit Club",
            "After Hours"
        )
        val second = buildRetroRackVisualProfile(
            43L,
            "Closing Track",
            "Circuit Club",
            "After Hours"
        )

        assertEquals(first.accent, second.accent)
        assertEquals(first.peak, second.peak)
        assertNotEquals(first.levels, second.levels)
    }
}
