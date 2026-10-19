package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EqualizerSettingsNavigationTest {
    @get:Rule
    val composeRule =
        createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playbackSettingsShowsSummaryAndOpensEqualizer() {
        // The real USB destination observes this singleton, so restore only the flag we change.
        val preferences = AppPreferencesRepository.getInstance(composeRule.activity.applicationContext)
        val experimentalEnabledBeforeTest = runBlocking {
            withTimeout(5_000) { preferences.awaitLoadedState().experimentalUsbDirectEnabled }
        }
        try {
            runBlocking {
                preferences.acknowledgeExperimentalUsbDirect()
                withTimeout(5_000) {
                    preferences.state.first { it.isLoaded && it.experimentalUsbDirectEnabled }
                    preferences.experimentalUsbDirectAccess.first { it }
                }
            }

            var opened = false
            composeRule.setContent {
                MaterialTheme {
                    SettingsScreen(
                        totalSongCount = 1,
                        availableFolderCount = 1,
                        folderSelectionMode = FolderSelectionMode.ALL,
                        selectedFolderCount = 0,
                        excludedFolderCount = 0,
                        isLibraryRefreshing = false,
                        lastLibraryRefreshSummary = null,
                        libraryErrorMessage = null,
                        onBackClick = {},
                        onLibraryFoldersClick = {},
                        onScanLibraryClick = {},
                        onExportBackupClick = {},
                        onRestoreBackupClick = {},
                        onDiagnosticsClick = {},
                        equalizerSummary = "Bass Lift",
                        onEqualizerClick = { opened = true },
                        isSleepTimerActive = false,
                        sleepTimerDisplayText = "",
                        onSleepTimerClick = {},
                        selectedPlayerTheme = PlayerTheme.DEFAULT,
                        selectedPlayerThemeTokens =
                            PlayerThemeTokens(
                                shellColor = Color.Black,
                                accentColor = Color.Blue,
                                displayBackgroundColor =
                                    Color.Black,
                                displayTextColor = Color.White
                            ),
                        onPlayerThemeSelected = {},
                        onUpdatePlayerThemeTokenOverride =
                            { _, _, _ -> },
                        onResetPlayerThemeTokenOverrides = {},
                        selectedModernArtworkTransitionStyle =
                            ModernArtworkTransitionStyle.SLIDE,
                        onModernArtworkTransitionStyleSelected = {},
                        selectedModernPlayerAppearance = ModernPlayerAppearance.Default,
                        activeModernAppearanceChoice = ModernAppearanceChoice.DEFAULT,
                        onModernAppearanceChoiceSelected = {},
                        onModernPlayerAppearanceEdited = {},
                        onResetModernPlayerAppearance = {},
                        previewSong = null,
                        selectedReplayGainMode =
                            ReplayGainMode.OFF,
                        onReplayGainModeSelected = {},
                        selectedAudioOffloadPreference =
                            AudioOffloadPreference.DISABLED,
                        onAudioOffloadPreferenceSelected = {}
                    )
                }
            }

            composeRule.onNodeWithText("Bass Lift")
                .assertExists()
            composeRule.onNodeWithContentDescription("Open equalizer settings")
                .performScrollTo()
                .performClick()
            composeRule.runOnIdle {
                assertTrue(opened)
            }
            composeRule.onNodeWithContentDescription("Open USB Audio settings")
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText("Audio output").assertExists()
            composeRule.onNodeWithText("USB Direct (Experimental)").assertExists()
        } finally {
            runBlocking {
                if (experimentalEnabledBeforeTest) preferences.acknowledgeExperimentalUsbDirect()
                else preferences.disableExperimentalUsbDirect()
                withTimeout(5_000) {
                    preferences.state.first {
                        it.isLoaded && it.experimentalUsbDirectEnabled == experimentalEnabledBeforeTest
                    }
                    preferences.experimentalUsbDirectAccess.first { it == experimentalEnabledBeforeTest }
                }
            }
        }
    }

    @Test
    fun listeningHistorySectionOpensFunctionalImportDestination() {
        var opened = false
        var matchingOpened = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    totalSongCount = 1,
                    availableFolderCount = 1,
                    folderSelectionMode = FolderSelectionMode.ALL,
                    selectedFolderCount = 0,
                    excludedFolderCount = 0,
                    isLibraryRefreshing = false,
                    lastLibraryRefreshSummary = null,
                    libraryErrorMessage = null,
                    onBackClick = {},
                    onLibraryFoldersClick = {},
                    onScanLibraryClick = {},
                    onExportBackupClick = {},
                    onRestoreBackupClick = {},
                    onListeningHistoryImportClick = { opened = true },
                    onListeningHistoryReconciliationClick = { matchingOpened = true },
                    onDiagnosticsClick = {},
                    equalizerSummary = "Off",
                    onEqualizerClick = {},
                    isSleepTimerActive = false,
                    sleepTimerDisplayText = "",
                    onSleepTimerClick = {},
                    selectedPlayerTheme = PlayerTheme.DEFAULT,
                    selectedPlayerThemeTokens = PlayerThemeTokens(
                        shellColor = Color.Black,
                        accentColor = Color.Blue,
                        displayBackgroundColor = Color.Black,
                        displayTextColor = Color.White
                    ),
                    onPlayerThemeSelected = {},
                    onUpdatePlayerThemeTokenOverride = { _, _, _ -> },
                    onResetPlayerThemeTokenOverrides = {},
                    selectedModernArtworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
                    onModernArtworkTransitionStyleSelected = {},
                    selectedModernPlayerAppearance = ModernPlayerAppearance.Default,
                    activeModernAppearanceChoice = ModernAppearanceChoice.DEFAULT,
                    onModernAppearanceChoiceSelected = {},
                    onModernPlayerAppearanceEdited = {},
                    onResetModernPlayerAppearance = {},
                    previewSong = null,
                    selectedReplayGainMode = ReplayGainMode.OFF,
                    onReplayGainModeSelected = {},
                    selectedAudioOffloadPreference = AudioOffloadPreference.DISABLED,
                    onAudioOffloadPreferenceSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Import listening history")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertTrue(opened) }
        composeRule.onNodeWithText("Match imported tracks")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertTrue(matchingOpened) }
    }
}
