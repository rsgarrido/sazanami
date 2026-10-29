package io.github.rsgarrido.sazanami.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.applyOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PocketAdvanceAppearanceSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var repository: AppPreferencesRepository
    private lateinit var job: Job
    private lateinit var file: File
    private val primaryColors = PlayerThemeTokenOverrides(shellColor = Color(0xFF123456))
    private val consoleRequests = mutableListOf<PocketAdvanceConsoleFinish>()
    private val lcdRequests = mutableListOf<PocketAdvanceLcdPalette>()
    private val controlsRequests = mutableListOf<PocketAdvanceControlsFinish>()
    private val otherRequests = mutableListOf<String>()
    private val mountedAppearances = mutableListOf<PocketAdvanceAppearance>()
    private var resets = 0

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "advance_appearance_settings_" + System.nanoTime() + ".preferences_pb"
        file = context.preferencesDataStoreFile(filename)
        job = SupervisorJob()
        repository = AppPreferencesRepository.create(context, CoroutineScope(job + Dispatchers.IO), filename, emptyList())
        withTimeout(5_000) { repository.awaitLoadedState() }
        repository.setSelectedPlayerTheme(PlayerTheme.CLASSIC_WHEEL)
        repository.setThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL, primaryColors)
        withTimeout(5_000) { repository.state.first {
            it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL && it.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL] == primaryColors
        } }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::job.isInitialized) job.cancelAndJoin()
        if (::file.isInitialized) file.delete()
    }

    @Test
    fun disabledPocketAdvanceStillExposesAllCategoriesAndAccessibleOriginalSwatches() {
        compose.setContent { Content() }
        val row = compose.onNodeWithTag("pocketAdvanceAppearanceSetting").performScrollTo().assertIsDisplayed()
        row.assertTextContains(text(R.string.settings_customize_pocket_advance))
            .assertTextContains(text(R.string.settings_customize_pocket_advance_summary))
        compose.onNodeWithContentDescription(text(R.string.settings_customize_pocket_advance), useUnmergedTree = true)
            .assertIsDisplayed()
        row.performClick()
        compose.onNodeWithText(text(R.string.pocket_advance_appearance_title)).assertIsDisplayed()
        val originals = listOf(
            swatch(R.string.pocket_advance_console_finish, PocketAdvanceConsoleFinish.ORIGINAL_PURPLE.labelRes),
            swatch(R.string.pocket_advance_lcd_palette, PocketAdvanceLcdPalette.ORIGINAL.labelRes),
            swatch(R.string.pocket_advance_controls_finish, PocketAdvanceControlsFinish.ORIGINAL_DARK.labelRes)
        )
        val density = compose.activity.resources.displayMetrics.density
        originals.forEach { node ->
            node.performScrollTo().assertIsDisplayed().assertIsSelected()
            val semantics = node.fetchSemanticsNode()
            assertEquals(Role.RadioButton, semantics.config[SemanticsProperties.Role])
            assertTrue(semantics.size.width >= 48f * density - 1f)
            assertTrue(semantics.size.height >= 48f * density - 1f)
        }
        compose.onNodeWithTag("pocketAdvanceConsoleCurrent", useUnmergedTree = true)
            .assertTextEquals(text(PocketAdvanceConsoleFinish.ORIGINAL_PURPLE.labelRes))
        compose.onNodeWithTag("pocketAdvanceLcdCurrent", useUnmergedTree = true)
            .assertTextEquals(text(PocketAdvanceLcdPalette.ORIGINAL.labelRes))
        compose.onNodeWithTag("pocketAdvanceControlsCurrent", useUnmergedTree = true)
            .assertTextEquals(text(PocketAdvanceControlsFinish.ORIGINAL_DARK.labelRes))
        compose.runOnIdle {
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, repository.state.value.tabletLandscapeTheme)
            assertEquals(PocketAdvanceAppearance.Default, repository.state.value.pocketAdvanceAppearance)
            assertTrue(otherRequests.isEmpty())
        }
    }

    @Test
    fun presetsUpdateIndependentlyAndResetLeavesActivationAndOtherThemeColorsUntouched() {
        compose.setContent { Content() }
        openChoices()
        swatch(R.string.pocket_advance_console_finish, PocketAdvanceConsoleFinish.OCEAN_BLUE.labelRes).performScrollTo().performClick()
        awaitAppearance(PocketAdvanceAppearance(consoleFinish = PocketAdvanceConsoleFinish.OCEAN_BLUE))
        swatch(R.string.pocket_advance_lcd_palette, PocketAdvanceLcdPalette.WARM_AMBER.labelRes).performScrollTo().performClick()
        awaitAppearance(PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE, PocketAdvanceLcdPalette.WARM_AMBER))
        swatch(R.string.pocket_advance_controls_finish, PocketAdvanceControlsFinish.LIGHT_GRAY.labelRes).performScrollTo().performClick()
        val selected = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
            PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY)
        awaitAppearance(selected)
        swatch(R.string.pocket_advance_console_finish, PocketAdvanceConsoleFinish.GRAPHITE.labelRes).performScrollTo().performClick()
        swatch(R.string.pocket_advance_console_finish, PocketAdvanceConsoleFinish.FOREST_GREEN.labelRes).performScrollTo().performClick()
        awaitAppearance(selected.copy(consoleFinish = PocketAdvanceConsoleFinish.FOREST_GREEN))
        compose.onNodeWithText(text(R.string.theme_color_reset)).performClick()
        awaitAppearance(PocketAdvanceAppearance.Default)
        originalsAreSelected()
        compose.onNodeWithText(text(R.string.settings_done)).performClick()
        compose.onNodeWithTag("pocketAdvanceAppearanceSetting").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(PocketAdvanceConsoleFinish.OCEAN_BLUE, PocketAdvanceConsoleFinish.GRAPHITE,
                PocketAdvanceConsoleFinish.FOREST_GREEN), consoleRequests)
            assertEquals(listOf(PocketAdvanceLcdPalette.WARM_AMBER), lcdRequests)
            assertEquals(listOf(PocketAdvanceControlsFinish.LIGHT_GRAY), controlsRequests)
            assertEquals(1, resets)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, repository.state.value.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to primaryColors), repository.state.value.playerThemeTokenOverrides)
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, repository.state.value.tabletLandscapeTheme)
            assertTrue(otherRequests.isEmpty())
        }
        compose.onNodeWithContentDescription(text(R.string.settings_customize_theme_colors))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.theme_color_customize, text(PlayerTheme.CLASSIC_WHEEL.labelRes)))
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pocket_advance_appearance_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_done)).performClick()
        openChoices()
        compose.onNodeWithText(text(R.string.pocket_advance_appearance_title)).assertIsDisplayed()
        originalsAreSelected()
        compose.onNodeWithText(text(R.string.settings_done)).performClick()
        compose.runOnIdle {
            assertEquals(PlayerTheme.CLASSIC_WHEEL, repository.state.value.selectedPlayerTheme)
            assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to primaryColors), repository.state.value.playerThemeTokenOverrides)
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, repository.state.value.tabletLandscapeTheme)
            assertEquals(PocketAdvanceAppearance.Default, repository.state.value.pocketAdvanceAppearance)
            assertTrue(otherRequests.isEmpty())
        }
    }

    @Test
    fun savedAppearanceSurvivesDialogRecreationAndDismissalWithoutEnablingPocketAdvance() {
        val saved = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.BERRY,
            PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.DEEP_NAVY)
        runBlocking {
            repository.setPocketAdvanceConsoleFinish(saved.consoleFinish)
            repository.setPocketAdvanceLcdPalette(saved.lcdPalette)
            repository.setPocketAdvanceControlsFinish(saved.controlsFinish)
            withTimeout(5_000) { repository.state.first { it.pocketAdvanceAppearance == saved } }
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        openChoices()
        restoration.emulateSavedInstanceStateRestore()
        swatch(R.string.pocket_advance_console_finish, saved.consoleFinish.labelRes).performScrollTo().assertIsSelected()
        swatch(R.string.pocket_advance_lcd_palette, saved.lcdPalette.labelRes).performScrollTo().assertIsSelected()
        swatch(R.string.pocket_advance_controls_finish, saved.controlsFinish.labelRes).performScrollTo().assertIsSelected()
        pressBack()
        openChoices()
        compose.onNodeWithText(text(R.string.settings_done)).performClick()
        compose.runOnIdle {
            assertTrue(mountedAppearances.isNotEmpty())
            assertTrue(mountedAppearances.all { it == saved })
            assertEquals(saved, repository.state.value.pocketAdvanceAppearance)
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, repository.state.value.tabletLandscapeTheme)
            assertTrue(consoleRequests.isEmpty()); assertTrue(lcdRequests.isEmpty()); assertTrue(controlsRequests.isEmpty())
            assertTrue(otherRequests.isEmpty()); assertEquals(0, resets)
        }
    }

    @Test
    fun appearanceEntryCategoriesAndPresetNamesHaveEnglishAndSpanishResources() {
        fun resources(language: String) = compose.activity.createConfigurationContext(
            Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        val english = resources("en")
        val spanish = resources("es")
        val ids = listOf(R.string.settings_customize_pocket_advance, R.string.settings_customize_pocket_advance_summary,
            R.string.pocket_advance_appearance_title,
            R.string.pocket_advance_console_finish, R.string.pocket_advance_console_finish_description,
            R.string.pocket_advance_lcd_palette, R.string.pocket_advance_lcd_palette_description,
            R.string.pocket_advance_controls_finish, R.string.pocket_advance_controls_finish_description) +
            PocketAdvanceConsoleFinish.entries.map { it.labelRes } + PocketAdvanceLcdPalette.entries.map { it.labelRes } +
            PocketAdvanceControlsFinish.entries.map { it.labelRes }
        ids.forEach { id ->
            assertTrue(english.getString(id).isNotBlank()); assertTrue(spanish.getString(id).isNotBlank())
            assertNotEquals(english.getString(id), spanish.getString(id))
        }
        assertEquals("Customize landscape theme", english.getString(R.string.settings_customize_pocket_advance))
        assertEquals("Personalizar tema horizontal", spanish.getString(R.string.settings_customize_pocket_advance))
        assertEquals("Pocket Advance · Console, LCD & controls", english.getString(R.string.settings_customize_pocket_advance_summary))
        assertEquals("Pocket Advance · Consola, LCD y controles", spanish.getString(R.string.settings_customize_pocket_advance_summary))
        assertEquals("Pocket Advance appearance", english.getString(R.string.pocket_advance_appearance_title))
        assertEquals("Apariencia de Pocket Advance", spanish.getString(R.string.pocket_advance_appearance_title))
        assertEquals("Original Purple", english.getString(PocketAdvanceConsoleFinish.ORIGINAL_PURPLE.labelRes))
        assertEquals("Morado original", spanish.getString(PocketAdvanceConsoleFinish.ORIGINAL_PURPLE.labelRes))
    }

    private fun originalsAreSelected() {
        swatch(R.string.pocket_advance_console_finish, PocketAdvanceConsoleFinish.ORIGINAL_PURPLE.labelRes).assertIsSelected()
        swatch(R.string.pocket_advance_lcd_palette, PocketAdvanceLcdPalette.ORIGINAL.labelRes).assertIsSelected()
        swatch(R.string.pocket_advance_controls_finish, PocketAdvanceControlsFinish.ORIGINAL_DARK.labelRes).assertIsSelected()
    }
    private fun text(id: Int) = compose.activity.getString(id)
    private fun swatch(category: Int, preset: Int) = compose.onNodeWithContentDescription(
        compose.activity.getString(R.string.theme_color_swatch, text(category), text(preset)))
    private fun openChoices() = compose.onNodeWithTag("pocketAdvanceAppearanceSetting").performScrollTo().performClick()
    private fun awaitAppearance(appearance: PocketAdvanceAppearance) {
        compose.waitUntil(5_000) { repository.state.value.pocketAdvanceAppearance == appearance }
        compose.waitForIdle()
    }

    @Composable
    private fun Content() {
        val state by repository.state.collectAsState()
        if (!state.isLoaded) return
        val scope = rememberCoroutineScope()
        SideEffect { mountedAppearances += state.pocketAdvanceAppearance }
        MaterialTheme {
            SettingsScreen(totalSongCount = 0, availableFolderCount = 0,
                folderSelectionMode = state.folderSelectionMode, selectedFolderCount = 0, excludedFolderCount = 0,
                isLibraryRefreshing = false, lastLibraryRefreshSummary = null, libraryErrorMessage = null,
                onBackClick = {}, onLibraryFoldersClick = {}, onScanLibraryClick = {}, onExportBackupClick = {},
                onRestoreBackupClick = {}, onDiagnosticsClick = {}, equalizerSummary = "", onEqualizerClick = {},
                isSleepTimerActive = false, sleepTimerDisplayText = "", onSleepTimerClick = {},
                selectedPlayerTheme = state.selectedPlayerTheme,
                selectedPlayerThemeTokens = state.selectedPlayerTheme.defaultTokens().applyOverrides(
                    state.playerThemeTokenOverrides[state.selectedPlayerTheme] ?: PlayerThemeTokenOverrides()),
                onPlayerThemeSelected = { otherRequests += "primary" },
                selectedTabletLandscapeTheme = state.tabletLandscapeTheme,
                onTabletLandscapeThemeSelected = { otherRequests += "landscape" },
                pocketAdvanceAppearance = state.pocketAdvanceAppearance,
                onPocketAdvanceConsoleFinishSelected = { finish -> consoleRequests += finish; scope.launch { repository.setPocketAdvanceConsoleFinish(finish) } },
                onPocketAdvanceLcdPaletteSelected = { palette -> lcdRequests += palette; scope.launch { repository.setPocketAdvanceLcdPalette(palette) } },
                onPocketAdvanceControlsFinishSelected = { finish -> controlsRequests += finish; scope.launch { repository.setPocketAdvanceControlsFinish(finish) } },
                onResetPocketAdvanceAppearance = { resets++; scope.launch { repository.resetPocketAdvanceAppearance() } },
                onUpdatePlayerThemeTokenOverride = { _, _, _ -> otherRequests += "normal-colors" },
                onResetPlayerThemeTokenOverrides = { otherRequests += "normal-reset" },
                selectedModernArtworkTransitionStyle = state.modernArtworkTransitionStyle, onModernArtworkTransitionStyleSelected = {},
                selectedModernPlayerAppearance = state.modernPlayerAppearance, activeModernAppearanceChoice = state.activeModernAppearanceChoice,
                onModernAppearanceChoiceSelected = {}, onModernPlayerAppearanceEdited = {}, onResetModernPlayerAppearance = {},
                previewSong = null, selectedReplayGainMode = state.replayGainMode, onReplayGainModeSelected = {},
                selectedAudioOffloadPreference = state.audioOffloadPreference, onAudioOffloadPreferenceSelected = {})
        }
    }
}
