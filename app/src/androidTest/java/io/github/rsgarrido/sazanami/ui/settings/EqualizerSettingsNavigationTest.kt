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
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EqualizerSettingsNavigationTest {
    @get:Rule
    val composeRule =
        createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playbackSettingsShowsSummaryAndOpensEqualizer() {
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
                    selectedModernSeekbarStyle =
                        ModernSeekbarStyle.CLASSIC_BAR,
                    onModernSeekbarStyleSelected = {},
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
                    selectedModernSeekbarStyle = ModernSeekbarStyle.CLASSIC_BAR,
                    onModernSeekbarStyleSelected = {},
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
