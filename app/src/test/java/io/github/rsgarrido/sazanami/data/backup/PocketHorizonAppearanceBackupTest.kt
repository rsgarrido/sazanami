package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PocketHorizonAppearanceBackupTest {
    private val custom = PocketHorizonAppearance(PocketHorizonConsoleFinish.MIDNIGHT_BLUE,
        PocketHorizonLcdPalette.WARM_AMBER, PocketHorizonWaveTreatment.SUBTLE)

    @Test
    fun schema17RoundTripPreservesAllThreeIdsWithoutEnablingTheTheme() {
        val source = BackupPreferences(selectedPlayerThemeId = PlayerTheme.CLASSIC_WHEEL.id,
            tabletLandscapeThemeId = TabletLandscapeTheme.NORMAL_PLAYER.id,
            pocketHorizonConsoleFinishId = custom.consoleFinish.id, pocketHorizonLcdPaletteId = custom.lcdPalette.id,
            pocketHorizonWaveTreatmentId = custom.waveTreatment.id,
            playerThemeTokenOverrides = mapOf("classic_wheel" to BackupPlayerThemeTokenOverrides(shellArgb = 0xFF123456)))
        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup(source)))
        assertEquals(17, decoded.schemaVersion)
        assertEquals(source.pocketHorizonConsoleFinishId, decoded.preferences.pocketHorizonConsoleFinishId)
        assertEquals(source.pocketHorizonLcdPaletteId, decoded.preferences.pocketHorizonLcdPaletteId)
        assertEquals(source.pocketHorizonWaveTreatmentId, decoded.preferences.pocketHorizonWaveTreatmentId)
        assertEquals(source.selectedPlayerThemeId, decoded.preferences.selectedPlayerThemeId)
        assertEquals(source.playerThemeTokenOverrides, decoded.preferences.playerThemeTokenOverrides)
        assertEquals(custom, decoded.preferences.toPocketHorizonAppearance())
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER.id, decoded.preferences.tabletLandscapeThemeId)
    }

    @Test
    fun backupsBeforeCustomizationKeepTheirLandscapeChoiceAndUseOriginalAppearance() {
        val source = BackupPreferences(selectedPlayerThemeId = PlayerTheme.POCKET_CASSETTE.id,
            tabletLandscapeThemeId = TabletLandscapeTheme.POCKET_ADVANCE.id)
        val root = Json.parseToJsonElement(AppBackupJson.encodeBackup(backup(source))).jsonObject
        val fields = root.getValue("preferences").jsonObject.filterKeys { it !in appearanceFields }
        val oldJson = JsonObject(root + ("preferences" to JsonObject(fields))).toString()
        appearanceFields.forEach { assertFalse(oldJson.contains(it)) }
        val restored = AppBackupJson.decodeBackup(oldJson)
        assertEquals(PocketHorizonAppearance.Default, restored.preferences.toPocketHorizonAppearance())
        assertEquals(TabletLandscapeTheme.POCKET_ADVANCE.id, restored.preferences.tabletLandscapeThemeId)
        assertEquals(PlayerTheme.POCKET_CASSETTE.id, restored.preferences.selectedPlayerThemeId)
        val withoutAdvance = JsonObject(root + ("preferences" to JsonObject(fields.filterKeys { it != "tabletLandscapeThemeId" }))).toString()
        val old = AppBackupJson.decodeBackup(withoutAdvance)
        assertEquals(PocketHorizonAppearance.Default, old.preferences.toPocketHorizonAppearance())
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER.id, old.preferences.tabletLandscapeThemeId)
    }

    @Test
    fun unknownIdsFallBackOneCategoryAtATime() {
        val valid = BackupPreferences(pocketHorizonConsoleFinishId = custom.consoleFinish.id,
            pocketHorizonLcdPaletteId = custom.lcdPalette.id, pocketHorizonWaveTreatmentId = custom.waveTreatment.id)
        assertEquals(custom.copy(consoleFinish = PocketHorizonConsoleFinish.GRAPHITE),
            valid.copy(pocketHorizonConsoleFinishId = "future_console").toPocketHorizonAppearance())
        assertEquals(custom.copy(lcdPalette = PocketHorizonLcdPalette.BLUE_VIOLET),
            valid.copy(pocketHorizonLcdPaletteId = "future_lcd").toPocketHorizonAppearance())
        assertEquals(custom.copy(waveTreatment = PocketHorizonWaveTreatment.STANDARD),
            valid.copy(pocketHorizonWaveTreatmentId = "future_controls").toPocketHorizonAppearance())
    }

    private val appearanceFields = setOf("pocketHorizonConsoleFinishId", "pocketHorizonLcdPaletteId", "pocketHorizonWaveTreatmentId")
    private fun backup(preferences: BackupPreferences) = AppBackup(createdAt = 123L,
        canonicalListeningHistory = BackupListeningHistoryV2(), preferences = preferences)
}
