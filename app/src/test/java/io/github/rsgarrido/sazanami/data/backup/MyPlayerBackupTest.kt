package io.github.rsgarrido.sazanami.data.backup

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesState
import io.github.rsgarrido.sazanami.data.preferences.decodeAppPreferences
import io.github.rsgarrido.sazanami.data.preferences.selectModernAppearanceChoice
import io.github.rsgarrido.sazanami.data.preferences.writeModernPlayerAppearance
import io.github.rsgarrido.sazanami.data.preferences.writeRestoredModernAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearancePreset
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkFit
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShadow
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBlurStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernDimmingStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernMetadataAlignment
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarColorMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MyPlayerBackupTest {
    @Test
    fun independentLandscapeChoiceRoundTripsAlongsideBuiltInAndSavedAppearance() {
        for (layout in ModernLandscapeLayout.entries) {
            val saved = customizedAppearance()
            val source = AppPreferencesState(myPlayerAppearance = saved,
                activeModernAppearanceChoice = ModernAppearanceChoice.MINIMAL, modernLandscapeLayout = layout)
            val restored = roundTrip(BackupPreferences().withMyPlayerAppearance(source))
                .preferences.toModernAppearanceState()
            assertEquals(layout, restored.modernLandscapeLayout)
            assertEquals(ModernAppearanceChoice.MINIMAL, restored.activeModernAppearanceChoice)
            assertEquals(saved, restored.myPlayerAppearance)
            assertEquals(ModernAppearancePreset.MINIMAL.appearance(), restored.modernPlayerAppearance)
        }
    }

    @Test
    fun olderBackupMissingLandscapeChoiceAndUnknownValuesUseBalanced() {
        val saved = customizedAppearance()
        val backup = AppBackup(createdAt = 123L, canonicalListeningHistory = BackupListeningHistoryV2(),
            preferences = BackupPreferences().withMyPlayerAppearance(AppPreferencesState(myPlayerAppearance = saved)))
        val oldJson = AppBackupJson.encodeBackup(backup)
            .replace("\"modernLandscapeLayout\":\"balanced\",", "")
        assertFalse(oldJson.contains("modernLandscapeLayout"))
        val old = AppBackupJson.decodeBackup(oldJson).preferences.toModernAppearanceState()
        assertEquals(ModernLandscapeLayout.BALANCED, old.modernLandscapeLayout)
        assertEquals(saved, old.myPlayerAppearance)
        assertEquals(ModernLandscapeLayout.BALANCED,
            roundTrip(BackupPreferences(modernLandscapeLayout = "future_layout"))
                .preferences.toModernAppearanceState().modernLandscapeLayout)
    }

    @Test
    fun currentSchemaRoundTripPreservesAllMyPlayerFieldsWhenMyPlayerIsActive() {
        val saved = customizedAppearance()
        val source = AppPreferencesState(
            modernPlayerAppearance = saved,
            myPlayerAppearance = saved,
            activeModernAppearanceChoice = ModernAppearanceChoice.MY_PLAYER
        )

        val decoded = roundTrip(BackupPreferences().withMyPlayerAppearance(source))
        val restored = restoreModernAppearance(decoded.preferences)

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("MY_PLAYER", decoded.preferences.modernActiveAppearanceChoice)
        assertEquals(saved, restored.myPlayerAppearance)
        assertEquals(saved, restored.modernPlayerAppearance)
        assertEquals(ModernAppearanceChoice.MY_PLAYER, restored.activeModernAppearanceChoice)
    }

    @Test
    fun currentSchemaBuiltInActiveKeepsSavedMyPlayerThroughRestoreAndSwitchBack() {
        val saved = customizedAppearance()
        val source = AppPreferencesState(
            modernPlayerAppearance = ModernAppearancePreset.MINIMAL.appearance(),
            myPlayerAppearance = saved,
            activeModernAppearanceChoice = ModernAppearanceChoice.MINIMAL
        )

        val decoded = roundTrip(BackupPreferences().withMyPlayerAppearance(source))
        assertEquals("MINIMAL", decoded.preferences.modernActiveAppearanceChoice)
        assertEquals(saved, decoded.preferences.toMyPlayerAppearance())
        assertFalse(decoded.preferences.toMyPlayerAppearance() == ModernAppearancePreset.MINIMAL.appearance())

        val restored = restoreModernAppearance(decoded.preferences)
        assertEquals(saved, restored.myPlayerAppearance)
        assertEquals(ModernAppearanceChoice.MINIMAL, restored.activeModernAppearanceChoice)
        assertEquals(ModernAppearancePreset.MINIMAL.appearance(), restored.modernPlayerAppearance)

        val preferences = mutablePreferencesOf()
        preferences.writeRestoredModernAppearance(decoded.preferences.toModernAppearanceState())
        preferences.selectModernAppearanceChoice(ModernAppearanceChoice.MY_PLAYER)
        assertEquals(saved, decodeAppPreferences(preferences).modernPlayerAppearance)
    }

    @Test
    fun schema15RestoreUsesItsAppearanceAsMyPlayerAndReplacesOldActiveChoice() {
        val saved = customizedAppearance()
        val legacyPreferences = BackupPreferences().withMyPlayerAppearance(
            AppPreferencesState(myPlayerAppearance = saved)
        )
        val encodedV15 = AppBackupJson.encodeBackup(
            AppBackup(
                schemaVersion = 15,
                createdAt = 123L,
                canonicalListeningHistory = BackupListeningHistoryV2(),
                preferences = legacyPreferences
            )
        ).replace(
            "\"modernActiveAppearanceChoice\":\"MY_PLAYER\",",
            ""
        )
        assertFalse(encodedV15.contains("modernActiveAppearanceChoice"))

        val migrated = AppBackupJson.decodeBackup(encodedV15)
        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals("MY_PLAYER", migrated.preferences.modernActiveAppearanceChoice)

        val existing = mutablePreferencesOf()
        existing.writeModernPlayerAppearance(ModernAppearancePreset.DEFAULT.appearance())
        existing.selectModernAppearanceChoice(ModernAppearanceChoice.COLORFUL)
        existing.clear()
        existing.writeRestoredModernAppearance(migrated.preferences.toModernAppearanceState())
        val restored = decodeAppPreferences(existing)
        assertEquals(ModernAppearanceChoice.MY_PLAYER, restored.activeModernAppearanceChoice)
        assertEquals(saved, restored.myPlayerAppearance)
        assertEquals(saved, restored.modernPlayerAppearance)
    }

    @Test
    fun unknownSchema16ChoiceUsesExistingSafeMyPlayerFallback() {
        val restored = restoreModernAppearance(
            BackupPreferences(modernActiveAppearanceChoice = "FUTURE_PRESET")
        )
        assertEquals(ModernAppearanceChoice.MY_PLAYER, restored.activeModernAppearanceChoice)
        assertEquals(restored.myPlayerAppearance, restored.modernPlayerAppearance)
    }

    private fun roundTrip(preferences: BackupPreferences): AppBackup = AppBackupJson.decodeBackup(
        AppBackupJson.encodeBackup(
            AppBackup(
                createdAt = 123L,
                canonicalListeningHistory = BackupListeningHistoryV2(),
                preferences = preferences
            )
        )
    )

    private fun restoreModernAppearance(preferences: BackupPreferences): AppPreferencesState {
        val existing = mutablePreferencesOf()
        existing.writeModernPlayerAppearance(ModernAppearancePreset.COLORFUL.appearance())
        existing.selectModernAppearanceChoice(ModernAppearanceChoice.COLORFUL)
        existing.clear()
        existing.writeRestoredModernAppearance(preferences.toModernAppearanceState())
        return decodeAppPreferences(existing)
    }

    private fun customizedAppearance() = ModernPlayerAppearance(
        seekbar = ModernSeekbarAppearance(
            style = ModernSeekbarStyle.SEGMENTED,
            waveformSize = ModernWaveformSize.TALL,
            waveformDensity = ModernWaveformDensity.SPARSE,
            colorMode = ModernSeekbarColorMode.APP_ACCENT
        ),
        background = ModernBackgroundAppearance(
            style = ModernBackgroundStyle.SOLID_COLOR,
            blurStrength = ModernBlurStrength.HIGH,
            dimmingStrength = ModernDimmingStrength.LOW,
            solidColorArgb = 0xFF123456L
        ),
        artwork = ModernArtworkAppearance(
            shape = ModernArtworkShape.EXTRA_ROUNDED,
            size = ModernArtworkSize.LARGE,
            fit = ModernArtworkFit.SHOW_FULL,
            shadow = ModernArtworkShadow.STRONG
        ),
        controls = ModernControlAppearance(
            style = ModernControlStyle.TONAL,
            size = ModernControlSize.LARGE,
            accent = ModernControlAccent.ALBUM_DERIVED
        ),
        layout = ModernLayoutAppearance(
            density = ModernLayoutDensity.RELAXED,
            metadataAlignment = ModernMetadataAlignment.CENTER,
            showAudioQualityBadge = false
        )
    )
}
