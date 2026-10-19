package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticState
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsAdaptiveLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsCentersOnlyWideContentAndRetainsScrollValuesAndActions() {
        val width = mutableStateOf(412.dp)
        val fontScale = mutableStateOf(1f)
        val selectedFont = mutableStateOf(AppFont.SAZANAMI)
        val actions = mutableListOf<String>()
        lateinit var scroll: ScrollState
        compose.setContent {
            // Fit logical tablet constraints onto a phone test device without clipping semantics.
            CompositionLocalProvider(LocalDensity provides Density(0.25f, fontScale.value)) {
                MaterialTheme {
                    scroll = rememberScrollState()
                    Box(Modifier.requiredSize(width.value, 600.dp).testTag("settings-window")) {
                        SettingsFixture(scroll, selectedFont.value, actions) {
                            actions += "font"
                            selectedFont.value = it
                        }
                    }
                }
            }
        }
        val originalScroll = scroll
        for (available in listOf(412.dp, 1200.dp, 600.dp, 1200.dp)) {
            compose.runOnIdle { width.value = available }
            val viewport = compose.onNodeWithTag("settings-window").fetchSemanticsNode()
            val content = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            val expectedWidth = minOf(available.value, 840f) * 0.25f
            assertEquals(expectedWidth, content.size.width.toFloat(), 1f)
            assertEquals((viewport.size.width - expectedWidth) / 2f,
                content.positionInRoot.x - viewport.positionInRoot.x, 1f)
            compose.runOnIdle {
                assertSame(originalScroll, scroll)
                assertTrue(actions.isEmpty())
            }
        }

        compose.runOnIdle { scroll.dispatchRawDelta(70f) }
        var previousOffset = 0
        compose.runOnIdle { previousOffset = scroll.value; width.value = 600.dp }
        compose.runOnIdle {
            assertEquals(previousOffset, scroll.value)
            assertTrue(scroll.value > 0)
            assertTrue(actions.isEmpty())
        }
        compose.onNodeWithContentDescription(text(R.string.settings_open_equalizer))
            .performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.settings_choose_font))
            .performScrollTo().performClick()
        compose.runOnIdle { width.value = 1200.dp; fontScale.value = 1.5f }
        compose.onNodeWithText(text(R.string.settings_device_font)).assertIsDisplayed().performClick()
        assertEquals(1200f * 0.25f,
            compose.onNode(hasScrollAction()).fetchSemanticsNode().size.width.toFloat(), 1f)
        compose.runOnIdle {
            assertEquals(AppFont.DEVICE, selectedFont.value)
            assertEquals(listOf("equalizer", "font"), actions)
            assertSame(originalScroll, scroll)
        }
    }

    @Test
    fun usbConfigurationKeepsItsDisclosureAndRoutesNoOutputCommandsOnResize() {
        val width = mutableStateOf(1400.dp)
        val actions = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.25f, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width.value, 320.dp).testTag("usb-window")) {
                        UsbAudioSettingsContent(
                            usb = UsbAudioDiagnosticState(serviceAvailable = true),
                            output = IntegratedDirectPlaybackState(serviceAvailable = true),
                            onBack = { actions += "back" },
                            onDiagnostics = { actions += "diagnostics" },
                            onSelectDevice = { actions += "select" },
                            onPermission = { actions += "permission" },
                            onRefresh = { actions += "refresh" },
                            onOutput = { _, _, _ -> actions += "output" },
                            onAcknowledgeExperimental = { actions += "acknowledge" }
                        )
                    }
                }
            }
        }
        assertEquals(1260f * 0.25f,
            compose.onNode(hasScrollAction()).fetchSemanticsNode().size.width.toFloat(), 1f)
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental))
            .performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.usb_settings_experimental_warning)).assertIsDisplayed()
        compose.runOnIdle { width.value = 412.dp }
        compose.onNodeWithText(text(R.string.usb_settings_experimental_warning)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_settings_cancel)).performClick()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).assertIsOff()
        compose.onNodeWithText(text(R.string.usb_settings_troubleshooting))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("diagnostics"), actions) }
    }

    @Composable
    private fun SettingsFixture(
        scroll: ScrollState,
        font: AppFont,
        actions: MutableList<String>,
        onFontSelected: (AppFont) -> Unit
    ) {
        SettingsScreen(
            totalSongCount = 1,
            availableFolderCount = 1,
            folderSelectionMode = FolderSelectionMode.ALL,
            selectedFolderCount = 0,
            excludedFolderCount = 0,
            isLibraryRefreshing = false,
            lastLibraryRefreshSummary = null,
            libraryErrorMessage = null,
            onBackClick = { actions += "back" },
            onLibraryFoldersClick = { actions += "folders" },
            onScanLibraryClick = { actions += "scan" },
            onExportBackupClick = { actions += "export" },
            onRestoreBackupClick = { actions += "restore" },
            onDiagnosticsClick = { actions += "diagnostics" },
            equalizerSummary = "Existing equalizer configuration",
            onEqualizerClick = { actions += "equalizer" },
            isSleepTimerActive = false,
            sleepTimerDisplayText = "",
            onSleepTimerClick = { actions += "timer" },
            selectedPlayerTheme = PlayerTheme.DEFAULT,
            selectedAppFont = font,
            onAppFontSelected = onFontSelected,
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
            onAudioOffloadPreferenceSelected = {},
            scrollState = scroll
        )
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
