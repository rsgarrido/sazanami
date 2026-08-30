package io.github.rsgarrido.sazanami.ui.playlist

import io.github.rsgarrido.sazanami.data.playlistfile.PlaylistImportResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistImportActionsTest {
    @Test
    fun importResultMessage_reportsImportedAndUnmatchedCounts() {
        assertEquals(
            "Imported 12 songs into Road Trip. 3 entries could not be matched.",
            importResultMessage(
                PlaylistImportResult(
                    playlistName = "Road Trip",
                    importedSongCount = 12,
                    unmatchedEntryCount = 3
                )
            )
        )
    }

    @Test
    fun importResultMessage_reportsNoMatches() {
        assertEquals(
            "No matching songs found in your library.",
            importResultMessage(
                PlaylistImportResult(
                    playlistName = null,
                    importedSongCount = 0,
                    unmatchedEntryCount = 4
                )
            )
        )
    }
}
