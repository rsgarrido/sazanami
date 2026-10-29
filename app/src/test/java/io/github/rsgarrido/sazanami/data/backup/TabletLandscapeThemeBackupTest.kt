package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletLandscapeThemeBackupTest {
    @Test
    fun schema17AddsTheIndependentSelectionWithoutChangingNormalThemeOrColors() {
        TabletLandscapeTheme.entries.forEach { selection ->
            val preferences = BackupPreferences(selectedPlayerThemeId = PlayerTheme.CLASSIC_WHEEL.id,
                tabletLandscapeThemeId = selection.id,
                playerThemeTokenOverrides = mapOf("classic_wheel" to BackupPlayerThemeTokenOverrides(shellArgb = 0xFF123456)))
            val encoded = AppBackupJson.encodeBackup(backup(preferences))
            assertTrue(encoded.contains("\"tabletLandscapeThemeId\":\"" + selection.id + "\""))
            val decoded = AppBackupJson.decodeBackup(encoded)
            assertEquals(17, decoded.schemaVersion)
            assertEquals(selection.id, decoded.preferences.tabletLandscapeThemeId)
            assertEquals(preferences.selectedPlayerThemeId, decoded.preferences.selectedPlayerThemeId)
            assertEquals(preferences.playerThemeTokenOverrides, decoded.preferences.playerThemeTokenOverrides)
        }
    }

    @Test
    fun olderBackupsWithoutTheFieldDefaultToTheNormalPlayer() {
        for (version in listOf(16, 17)) {
            val encoded = AppBackupJson.encodeBackup(backup(BackupPreferences(
                selectedPlayerThemeId = PlayerTheme.POCKET_CASSETTE.id)).copy(schemaVersion = version))
                .replace("\"tabletLandscapeThemeId\":\"normal_player\",", "")
            assertFalse(encoded.contains("tabletLandscapeThemeId"))
            val decoded = AppBackupJson.decodeBackup(encoded)
            assertEquals(17, decoded.schemaVersion)
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER.id, decoded.preferences.tabletLandscapeThemeId)
            assertEquals(PlayerTheme.POCKET_CASSETTE.id, decoded.preferences.selectedPlayerThemeId)
        }
    }

    @Test
    fun unknownBackupSelectionHasTheSameSafeFallbackAsPreferences() {
        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup(BackupPreferences(
            selectedPlayerThemeId = PlayerTheme.POCKET_FLIP.id, tabletLandscapeThemeId = "future_theme"))))
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, TabletLandscapeTheme.fromId(decoded.preferences.tabletLandscapeThemeId))
        assertEquals(PlayerTheme.POCKET_FLIP.id, decoded.preferences.selectedPlayerThemeId)
    }

    private fun backup(preferences: BackupPreferences) = AppBackup(createdAt = 123L,
        canonicalListeningHistory = BackupListeningHistoryV2(), preferences = preferences)
}
