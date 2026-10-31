package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppAppearanceBackupTest {
    @Test
    fun schema17RoundTripPreservesSavedAppearanceValues() {
        AppAppearance.entries.forEach { appearance ->
            val decoded = roundTrip(
                BackupPreferences(appAppearance = appearance.storageValue)
            )

            assertEquals(18, decoded.schemaVersion)
            assertEquals(appearance.storageValue, decoded.preferences.appAppearance)
            assertEquals(appearance, decoded.preferences.toAppAppearance())
        }
    }

    @Test
    fun schema16WithoutAppearanceMigratesToDark() {
        val encodedV16 = AppBackupJson.encodeBackup(
            backup(
                BackupPreferences(appAppearance = AppAppearance.DARK.storageValue)
            ).copy(schemaVersion = 16)
        ).replace("\"appAppearance\":\"DARK\",", "")
        assertFalse(encodedV16.contains("appAppearance"))

        val decoded = AppBackupJson.decodeBackup(encodedV16)

        assertEquals(18, decoded.schemaVersion)
        assertEquals("DARK", decoded.preferences.appAppearance)
        assertEquals(AppAppearance.DARK, decoded.preferences.toAppAppearance())
    }

    @Test
    fun unknownSchema17AppearanceFallsBackToDark() {
        val decoded = roundTrip(
            BackupPreferences(appAppearance = "FUTURE_APPEARANCE")
        )

        assertEquals(AppAppearance.DARK, decoded.preferences.toAppAppearance())
    }

    @Test
    fun appearanceDoesNotChangePlayerThemeOrModernChoice() {
        val decoded = roundTrip(
            BackupPreferences(
                appAppearance = AppAppearance.LIGHT.storageValue,
                selectedPlayerThemeId = PlayerTheme.POCKET_DISC.id,
                modernActiveAppearanceChoice = ModernAppearanceChoice.MINIMAL.name
            )
        )

        assertEquals(AppAppearance.LIGHT, decoded.preferences.toAppAppearance())
        assertEquals(PlayerTheme.POCKET_DISC.id, decoded.preferences.selectedPlayerThemeId)
        assertEquals(
            ModernAppearanceChoice.MINIMAL,
            decoded.preferences.toModernAppearanceState().activeModernAppearanceChoice
        )
    }

    private fun roundTrip(preferences: BackupPreferences): AppBackup =
        AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup(preferences)))

    private fun backup(preferences: BackupPreferences): AppBackup = AppBackup(
        createdAt = 123L,
        canonicalListeningHistory = BackupListeningHistoryV2(),
        preferences = preferences
    )
}
