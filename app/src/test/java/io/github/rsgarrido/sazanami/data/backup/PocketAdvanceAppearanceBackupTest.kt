package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PocketAdvanceAppearanceBackupTest {
    private val custom = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
        PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY)

    @Test
    fun schema17RoundTripPreservesAllThreeIdsWithoutEnablingTheTheme() {
        val source = BackupPreferences(selectedPlayerThemeId = PlayerTheme.CLASSIC_WHEEL.id,
            tabletLandscapeThemeId = TabletLandscapeTheme.NORMAL_PLAYER.id,
            pocketAdvanceConsoleFinishId = custom.consoleFinish.id, pocketAdvanceLcdPaletteId = custom.lcdPalette.id,
            pocketAdvanceControlsFinishId = custom.controlsFinish.id,
            playerThemeTokenOverrides = mapOf("classic_wheel" to BackupPlayerThemeTokenOverrides(shellArgb = 0xFF123456)))
        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup(source)))
        assertEquals(17, decoded.schemaVersion)
        assertEquals(source.pocketAdvanceConsoleFinishId, decoded.preferences.pocketAdvanceConsoleFinishId)
        assertEquals(source.pocketAdvanceLcdPaletteId, decoded.preferences.pocketAdvanceLcdPaletteId)
        assertEquals(source.pocketAdvanceControlsFinishId, decoded.preferences.pocketAdvanceControlsFinishId)
        assertEquals(source.selectedPlayerThemeId, decoded.preferences.selectedPlayerThemeId)
        assertEquals(source.playerThemeTokenOverrides, decoded.preferences.playerThemeTokenOverrides)
        assertEquals(custom, decoded.preferences.toPocketAdvanceAppearance())
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
        assertEquals(PocketAdvanceAppearance.Default, restored.preferences.toPocketAdvanceAppearance())
        assertEquals(TabletLandscapeTheme.POCKET_ADVANCE.id, restored.preferences.tabletLandscapeThemeId)
        assertEquals(PlayerTheme.POCKET_CASSETTE.id, restored.preferences.selectedPlayerThemeId)
        val withoutAdvance = JsonObject(root + ("preferences" to JsonObject(fields.filterKeys { it != "tabletLandscapeThemeId" }))).toString()
        val old = AppBackupJson.decodeBackup(withoutAdvance)
        assertEquals(PocketAdvanceAppearance.Default, old.preferences.toPocketAdvanceAppearance())
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER.id, old.preferences.tabletLandscapeThemeId)
    }

    @Test
    fun unknownIdsFallBackOneCategoryAtATime() {
        val valid = BackupPreferences(pocketAdvanceConsoleFinishId = custom.consoleFinish.id,
            pocketAdvanceLcdPaletteId = custom.lcdPalette.id, pocketAdvanceControlsFinishId = custom.controlsFinish.id)
        assertEquals(custom.copy(consoleFinish = PocketAdvanceConsoleFinish.ORIGINAL_PURPLE),
            valid.copy(pocketAdvanceConsoleFinishId = "future_console").toPocketAdvanceAppearance())
        assertEquals(custom.copy(lcdPalette = PocketAdvanceLcdPalette.ORIGINAL),
            valid.copy(pocketAdvanceLcdPaletteId = "future_lcd").toPocketAdvanceAppearance())
        assertEquals(custom.copy(controlsFinish = PocketAdvanceControlsFinish.ORIGINAL_DARK),
            valid.copy(pocketAdvanceControlsFinishId = "future_controls").toPocketAdvanceAppearance())
    }

    private val appearanceFields = setOf("pocketAdvanceConsoleFinishId", "pocketAdvanceLcdPaletteId", "pocketAdvanceControlsFinishId")
    private fun backup(preferences: BackupPreferences) = AppBackup(createdAt = 123L,
        canonicalListeningHistory = BackupListeningHistoryV2(), preferences = preferences)
}
