package io.github.rsgarrido.sazanami.ui.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistExportActionsTest {
    @Test
    fun sanitizedM3uFilename_replacesInvalidCharactersAndAddsExtension() {
        assertEquals(
            "Road_Trip_2026.m3u8",
            sanitizedM3uFilename(" Road/Trip:2026 ")
        )
    }

    @Test
    fun sanitizedM3uFilename_doesNotDuplicateExtension() {
        assertEquals(
            "Favorites.m3u8",
            sanitizedM3uFilename("Favorites.M3U8")
        )
    }

    @Test
    fun exportSuccessMessage_includesSkippedSongCountWhenNeeded() {
        assertEquals(
            "Exported 12 songs. 2 unavailable songs were skipped.",
            exportSuccessMessage(
                exportedSongCount = 12,
                unavailableSongCount = 2
            )
        )
    }

    @Test
    fun exportSuccessMessage_usesSingularSongLabels() {
        assertEquals(
            "Exported 1 song. 1 unavailable song was skipped.",
            exportSuccessMessage(
                exportedSongCount = 1,
                unavailableSongCount = 1
            )
        )
    }
}
