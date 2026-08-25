package com.example.cdplaya.data.backup

import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistRule
import com.example.cdplaya.data.SmartPlaylistRuleField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartPlaylistBackupJsonTest {
    @Test
    fun smartDefinitionAndGeneratedSnapshotRoundTrip() {
        val playlist = BackupPlaylist(
            playlistId = 9L,
            name = "Heavy Rotation",
            createdAt = 10L,
            updatedAt = 20L,
            type = "SMART",
            folderId = 3L,
            smartDefinition = BackupSmartPlaylistDefinition(
                matchMode = "ANY",
                rules = listOf(SmartPlaylistRule(
                    SmartPlaylistRuleField.RATING,
                    SmartPlaylistOperator.AT_LEAST,
                    listOf("4")
                )),
                sortField = "play_count",
                sortDirection = "DESC",
                resultLimit = 25,
                definitionVersion = 1,
                updatedAt = 20L
            ),
            generatedState = BackupGeneratedPlaylistState(
                templateKey = "heavy_rotation",
                refreshPolicy = "periodic",
                refreshIntervalMillis = 86_400_000L,
                lastRefreshedAt = 30L,
                songs = listOf(BackupGeneratedPlaylistSong(
                    position = 0,
                    reference = BackupSongReference(
                        relativePath = "Music/",
                        displayName = "alpha.mp3",
                        duration = 100_000L,
                        title = "Alpha",
                        artist = "Artist",
                        album = "Album",
                        portableKey = "portable-alpha"
                    )
                ))
            )
        )
        val backup = AppBackup(
            createdAt = 40L,
            playlistFolders = listOf(BackupPlaylistFolder(3L, "Smart", 1L, 2L)),
            playlists = listOf(playlist),
            canonicalListeningHistory = BackupListeningHistoryV2()
        )

        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup))

        assertEquals(playlist, decoded.playlists.single())
    }

    @Test
    fun schemaThirteenSmartPlaylistMigratesWithoutInventingLiveMembership() {
        val backup = AppBackup(
            schemaVersion = 13,
            createdAt = 40L,
            playlists = listOf(BackupPlaylist(
                playlistId = 5L,
                name = "Legacy Smart",
                createdAt = 1L,
                updatedAt = 2L,
                type = "SMART",
                songs = emptyList()
            )),
            canonicalListeningHistory = BackupListeningHistoryV2()
        )

        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup))

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertNull(decoded.playlists.single().smartDefinition)
        assertEquals(emptyList<BackupPlaylistSong>(), decoded.playlists.single().songs)
    }
}

