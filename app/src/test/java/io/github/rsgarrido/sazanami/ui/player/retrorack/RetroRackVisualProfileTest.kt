package io.github.rsgarrido.sazanami.ui.player.retrorack

import org.junit.Assert.assertEquals
import org.junit.Test

class RetroRackVisualProfileTest {
    @Test
    fun `same album identity produces same profile`() {
        val first = buildRetroRackVisualProfile(
            "Circuit Club",
            "After Hours"
        )
        val second = buildRetroRackVisualProfile(
            "Circuit Club",
            "After Hours"
        )

        assertEquals(first, second)
    }

    @Test
    fun `blank album falls back to artist deterministically`() {
        val nullAlbum = buildRetroRackVisualProfile(
            "Circuit Club",
            null
        )
        val blankAlbum = buildRetroRackVisualProfile(
            "Circuit Club",
            ""
        )

        assertEquals(nullAlbum, blankAlbum)
    }

    @Test
    fun `same album shares visualizer colors across artists`() {
        val first = buildRetroRackVisualProfile(
            "Circuit Club",
            "After Hours"
        )
        val second = buildRetroRackVisualProfile(
            "Guest Artist",
            "After Hours"
        )

        assertEquals(first.accent, second.accent)
        assertEquals(first.peak, second.peak)
    }
}
