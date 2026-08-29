package com.example.cdplaya.ui.settings

import com.example.cdplaya.data.backup.BackupRestoreResult
import com.example.cdplaya.data.backup.BackupRestoreSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreActionsTest {
    @Test
    fun backupRestoreConfirmationText_listsReplacedDataAndProtectsMusicFiles() {
        val text = backupRestoreConfirmationText()

        listOf(
            "Favorites",
            "Playlists",
            "Artist pictures and custom playlist artwork",
            "Listening history, imported-track links, and ratings",
            "Library folder selection",
            "Player theme and ReplayGain setting",
            "Your music files will not be changed."
        ).forEach { expectedText ->
            assertTrue(text.contains(expectedText))
        }
    }

    @Test
    fun backupRestoreSummaryText_formatsAllCounts() {
        assertEquals(
            """
            - 24 favorites
            - 3 playlists
            - 120 playlist songs
            - 86 history entries
            - 2 selected folders
            - 4 pictures
            """.trimIndent(),
            backupRestoreSummaryText(
                BackupRestoreSummary(
                    favoriteCount = 24,
                    playlistCount = 3,
                    playlistSongCount = 120,
                    listeningHistoryCount = 86,
                    selectedFolderCount = 2,
                    visualAssetCount = 4
                )
            )
        )
    }

    @Test
    fun backupRestoreSuccessMessage_formatsResultCounts() {
        assertEquals(
            "Backup restored. 3 playlists, 24 favorites, 86 history entries, 4 pictures.",
            backupRestoreSuccessMessage(
                BackupRestoreResult(
                    favoriteCount = 24,
                    playlistCount = 3,
                    playlistSongCount = 120,
                    listeningHistoryCount = 86,
                    selectedFolderCount = 2,
                    visualAssetCount = 4
                )
            )
        )
    }

    @Test
    fun invalidBackupErrorMessage_isFriendlyAndActionable() {
        assertEquals(
            "Couldn't open backup. The file is invalid or uses an unsupported version.",
            invalidBackupErrorMessage()
        )
    }
}
