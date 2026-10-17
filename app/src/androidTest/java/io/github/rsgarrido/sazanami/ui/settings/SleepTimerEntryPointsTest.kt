package io.github.rsgarrido.sazanami.ui.settings

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.SleepTimerController
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.MusicOverlayState
import io.github.rsgarrido.sazanami.ui.player.NowPlayingMoreDialog
import io.github.rsgarrido.sazanami.ui.player.NowPlayingMoreDialogTag
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.nowPlayingActions
import io.github.rsgarrido.sazanami.ui.player.performNowPlayingAction
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.state.SLEEP_TIMER_OPTIONS_MINUTES
import io.github.rsgarrido.sazanami.ui.state.displayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise the real Settings entry, More dispatch, dialog and controller with one timer owner. */
class SleepTimerEntryPointsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: SleepTimerController
    private lateinit var overlays: MusicOverlayState
    private val target = Song(
        1, "Song", "Artist", "Album", 1, 120_000L,
        Uri.parse("content://media/external/audio/media/1"), "/music/1.flac", "/music", null,
        volumeName = "external"
    )

    @Test
    fun settingsAndMoreStartAndCancelTheSameTimerThroughTheExistingDialog() {
        composeRule.setContent { SharedTimerFixture() }
        val resources = composeRule.activity.resources
        val timerLabel = resources.getString(R.string.sleep_timer_title)
        val cancelLabel = resources.getString(R.string.sleep_timer_cancel)
        val settingsEntry = resources.getString(R.string.settings_open_sleep_timer)

        composeRule.onNodeWithContentDescription(settingsEntry).performScrollTo().performClick()
        SLEEP_TIMER_OPTIONS_MINUTES.forEach { minutes ->
            composeRule.onNodeWithText(resources.getQuantityString(R.plurals.sleep_timer_minutes_option, minutes, minutes))
                .assertExists()
        }
        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.sleep_timer_minutes_option, 5, 5)).performClick()
        composeRule.runOnIdle {
            assertTrue(controller.uiState.value.isActive)
            assertTrue(controller.uiState.value.remainingSeconds in 1..300)
            assertFalse(overlays.isSleepTimerDialogVisible.value)
            overlays.isSettingsScreenVisible.value = false
            overlays.openNowPlayingMore(target)
        }
        composeRule.onNodeWithContentDescription(timerLabel).assertIsSelected().performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithText(cancelLabel).assertExists()
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertTrue(controller.uiState.value.isActive) // Closing leaves the existing timer running.
            assertFalse(overlays.isSleepTimerDialogVisible.value)
            assertEquals(PlayerPresentation.Expanded, overlays.playerMorphState.targetPresentation)
            overlays.openNowPlayingMore(target)
        }
        composeRule.onNodeWithContentDescription(timerLabel).performClick()
        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.runOnIdle {
            assertFalse(controller.uiState.value.isActive)
            assertEquals(0, controller.uiState.value.remainingSeconds)
            assertFalse(overlays.isSleepTimerDialogVisible.value)
            overlays.openNowPlayingMore(target)
        }
        composeRule.onNodeWithContentDescription(timerLabel).performClick()
        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.sleep_timer_minutes_option, 10, 10)).performClick()
        composeRule.runOnIdle {
            assertTrue(controller.uiState.value.isActive)
            assertTrue(controller.uiState.value.remainingSeconds in 1..600)
            overlays.isSettingsScreenVisible.value = true
        }
        composeRule.onNodeWithContentDescription(settingsEntry).performScrollTo().performClick()
        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.runOnIdle {
            assertFalse(controller.uiState.value.isActive)
            assertEquals(0, controller.uiState.value.remainingSeconds)
            assertFalse(overlays.isSleepTimerDialogVisible.value)
            assertTrue(overlays.isSettingsScreenVisible.value)
        }
    }

    @Composable
    private fun SharedTimerFixture() {
        val scope = rememberCoroutineScope()
        val player = rememberPlayerMorphState(PlayerPresentation.Expanded)
        overlays = remember(player) {
            MusicOverlayState(player, mutableStateOf(null), mutableStateOf(null)).apply {
                isSettingsScreenVisible.value = true
            }
        }
        controller = remember(scope) { SleepTimerController(scope) {} }
        DisposableEffect(controller) { onDispose { controller.release() } }
        val timer by controller.uiState.collectAsState()
        val displayText = timer.displayText(LocalContext.current.resources)
        MaterialTheme {
            if (overlays.isSettingsScreenVisible.value) {
                SettingsScreen(
                    totalSongCount = 1, availableFolderCount = 1,
                    folderSelectionMode = FolderSelectionMode.ALL, selectedFolderCount = 0,
                    excludedFolderCount = 0, isLibraryRefreshing = false,
                    lastLibraryRefreshSummary = null, libraryErrorMessage = null,
                    onBackClick = {}, onLibraryFoldersClick = {}, onScanLibraryClick = {},
                    onExportBackupClick = {}, onRestoreBackupClick = {}, onDiagnosticsClick = {},
                    equalizerSummary = "Off", onEqualizerClick = {},
                    isSleepTimerActive = timer.isActive, sleepTimerDisplayText = displayText,
                    onSleepTimerClick = { overlays.isSleepTimerDialogVisible.value = true },
                    selectedPlayerTheme = PlayerTheme.DEFAULT,
                    selectedPlayerThemeTokens = PlayerThemeTokens(Color.Black, Color.Blue, Color.Black, Color.White),
                    onPlayerThemeSelected = {}, onUpdatePlayerThemeTokenOverride = { _, _, _ -> },
                    onResetPlayerThemeTokenOverrides = {},
                    selectedModernArtworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
                    onModernArtworkTransitionStyleSelected = {},
                    selectedModernPlayerAppearance = ModernPlayerAppearance.Default,
                    activeModernAppearanceChoice = ModernAppearanceChoice.DEFAULT,
                    onModernAppearanceChoiceSelected = {}, onModernPlayerAppearanceEdited = {},
                    onResetModernPlayerAppearance = {}, previewSong = null,
                    selectedReplayGainMode = ReplayGainMode.OFF, onReplayGainModeSelected = {},
                    selectedAudioOffloadPreference = AudioOffloadPreference.DISABLED,
                    onAudioOffloadPreferenceSelected = {}
                )
            }
            if (overlays.isNowPlayingMoreVisible.value) {
                NowPlayingMoreDialog(
                    target, nowPlayingActions(target, emptySet(), listOf(target), isSleepTimerActive = timer.isActive),
                    onDismiss = overlays::dismissNowPlayingMore,
                    onAction = { action ->
                        performNowPlayingAction(action, target, target, listOf(target),
                            onDismiss = overlays::dismissNowPlayingMore,
                            onToggleFavorite = {}, onOpenAlbum = {}, onOpenLyrics = {},
                            onOpenSleepTimer = {
                                assertFalse(overlays.isNowPlayingMoreVisible.value)
                                assertFalse(overlays.isSettingsScreenVisible.value)
                                overlays.isSleepTimerDialogVisible.value = true
                            })
                    }
                )
            }
            if (overlays.isSleepTimerDialogVisible.value) {
                SleepTimerDialog(timer.isActive, displayText, controller::startTimer, controller::cancelTimer,
                    onDismiss = { overlays.isSleepTimerDialogVisible.value = false })
            }
        }
    }
}
