package io.github.rsgarrido.sazanami.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandedPlayerClosePolicyTest {
    @Test
    fun explicitCloseOnlyResetsLyricsAndCollapsesPresentation() {
        listOf("HOME", "SEARCH", "SONGS", "ALBUM", "ARTIST", "PLAYLIST").forEach { route ->
            var currentRoute = route
            var lyricsResetCount = 0
            var collapseCount = 0
            var albumNavigationCount = 0
            val openPlayingAlbum = {
                albumNavigationCount++
                currentRoute = "PLAYING_ALBUM"
            }

            dismissExpandedPlayerPresentation(
                resetLyricsPresentation = { lyricsResetCount++ },
                collapsePlayer = { collapseCount++ }
            )

            assertEquals(route, currentRoute)
            assertEquals(1, lyricsResetCount)
            assertEquals(1, collapseCount)
            assertEquals(0, albumNavigationCount)

            // Album navigation remains a separate, intentional affordance.
            openPlayingAlbum()
            assertEquals("PLAYING_ALBUM", currentRoute)
            assertEquals(1, albumNavigationCount)
        }
    }
}
