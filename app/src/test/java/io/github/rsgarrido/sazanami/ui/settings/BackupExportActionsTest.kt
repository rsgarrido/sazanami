package io.github.rsgarrido.sazanami.ui.settings

import io.github.rsgarrido.sazanami.data.backup.BackupExportResult
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportActionsTest {
    @Test
    fun backupFilename_usesProvidedLocalDateAndPackageExtension() {
        assertEquals(
            "sazanami-backup-2026-07-15.sazanami",
            backupFilename(LocalDate.of(2026, 7, 15))
        )
    }

    @Test
    fun backupExportSuccessMessage_includesExportCounts() {
        assertEquals(
            "Backup exported. 3 playlists, 24 favorites, 86 history entries, 4 pictures.",
            backupExportSuccessMessage(
                BackupExportResult(
                    favoriteCount = 24,
                    playlistCount = 3,
                    playlistSongCount = 120,
                    listeningHistoryCount = 86,
                    visualAssetCount = 4
                )
            )
        )
    }

    @Test
    fun backupExportSuccessMessage_usesSingularLabels() {
        assertEquals(
            "Backup exported. 1 playlist, 1 favorite, 1 history entry, 1 picture.",
            backupExportSuccessMessage(
                BackupExportResult(
                    favoriteCount = 1,
                    playlistCount = 1,
                    playlistSongCount = 1,
                    listeningHistoryCount = 1,
                    visualAssetCount = 1
                )
            )
        )
    }
}
