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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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

class TabletLandscapeThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var repository: AppPreferencesRepository
    private lateinit var job: Job
    private lateinit var file: File
    private lateinit var filename: String
    private val landscapeRequests = mutableListOf<TabletLandscapeTheme>()
    private val primaryRequests = mutableListOf<PlayerTheme>()
    private val mountedSelections = mutableListOf<TabletLandscapeTheme>()
    private val colors = PlayerThemeTokenOverrides(shellColor = androidx.compose.ui.graphics.Color(0xFF123456))

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        filename = "tablet_landscape_settings_" + System.nanoTime() + ".preferences_pb"
        file = context.preferencesDataStoreFile(filename)
        job = SupervisorJob()
        repository = AppPreferencesRepository.create(context, CoroutineScope(job + Dispatchers.IO), filename, emptyList())
        withTimeout(5_000) { repository.awaitLoadedState() }
        repository.setSelectedPlayerTheme(PlayerTheme.CLASSIC_WHEEL)
        repository.setThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL, colors)
        withTimeout(5_000) { repository.state.first {
            it.selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL && it.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL] == colors
        } }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::job.isInitialized) job.cancelAndJoin()
        if (::file.isInitialized) file.delete()
    }

    @Test
    fun normalSettingsExposeAllThreeAccessibleChoicesInOrderAndPersistOnlyTheRequestedSelection() {
        compose.setContent { Content() }
        compose.onNodeWithText(text(R.string.settings_tablet_landscape_theme_summary)).performScrollTo().assertIsDisplayed()
        openChoices()
        compose.onNode(hasText(text(R.string.settings_tablet_landscape_theme_summary)) and hasAnyAncestor(isDialog()),
            useUnmergedTree = true).assertIsDisplayed()
        option(TabletLandscapeTheme.NORMAL_PLAYER).assertIsEnabled().assertIsSelected()
        option(TabletLandscapeTheme.POCKET_ADVANCE).assertIsEnabled().assertIsNotSelected()
        option(TabletLandscapeTheme.POCKET_HORIZON).assertIsEnabled().assertIsNotSelected()
        val ordered = listOf(TabletLandscapeTheme.NORMAL_PLAYER, TabletLandscapeTheme.POCKET_ADVANCE,
            TabletLandscapeTheme.POCKET_HORIZON)
        val tops = ordered.map { theme ->
            option(theme).assertTextContains(text(theme.labelRes))
            val node = option(theme).fetchSemanticsNode()
            assertEquals(Role.RadioButton, node.config[SemanticsProperties.Role])
            node.boundsInRoot.top
        }
        assertTrue(tops.zipWithNext().all { (before, after) -> before < after })
        option(TabletLandscapeTheme.POCKET_ADVANCE).performClick()
        awaitSelection(TabletLandscapeTheme.POCKET_ADVANCE)
        compose.onNodeWithTag("tabletLandscapeThemeSetting").assertTextContains(text(R.string.tablet_landscape_theme_pocket_advance))
        openChoices()
        option(TabletLandscapeTheme.POCKET_HORIZON).performClick()
        awaitSelection(TabletLandscapeTheme.POCKET_HORIZON)
        compose.onNodeWithTag("tabletLandscapeThemeSetting").assertTextContains(text(R.string.tablet_landscape_theme_pocket_horizon))
        compose.runOnIdle {
            assertEquals(PlayerTheme.CLASSIC_WHEEL, repository.state.value.selectedPlayerTheme)
            assertEquals(colors, repository.state.value.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL])
            assertTrue(primaryRequests.isEmpty())
        }
        compose.onNodeWithContentDescription(text(R.string.settings_choose_player_theme)).performScrollTo().performClick()
        compose.onNodeWithText(text(PlayerTheme.POCKET_CASSETTE.labelRes)).performClick()
        compose.waitUntil(5_000) { repository.state.value.selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE }
        openChoices()
        option(TabletLandscapeTheme.POCKET_HORIZON).assertIsSelected()
        option(TabletLandscapeTheme.NORMAL_PLAYER).performClick()
        awaitSelection(TabletLandscapeTheme.NORMAL_PLAYER)
        compose.runOnIdle {
            assertEquals(listOf(TabletLandscapeTheme.POCKET_ADVANCE, TabletLandscapeTheme.POCKET_HORIZON,
                TabletLandscapeTheme.NORMAL_PLAYER), landscapeRequests)
            assertEquals(listOf(PlayerTheme.POCKET_CASSETTE), primaryRequests)
            assertEquals(PlayerTheme.POCKET_CASSETTE, repository.state.value.selectedPlayerTheme)
            assertEquals(colors, repository.state.value.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL])
        }
    }

    @Test
    fun savedSelectionIsVisibleOnTheFirstLoadedCompositionAndSurvivesRecreationAndDismissal() {
        runBlocking {
            repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_ADVANCE)
            withTimeout(5_000) { repository.state.first { it.tabletLandscapeTheme == TabletLandscapeTheme.POCKET_ADVANCE } }
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        openChoices()
        option(TabletLandscapeTheme.POCKET_ADVANCE).assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        option(TabletLandscapeTheme.POCKET_ADVANCE).assertIsSelected()
        compose.onNodeWithText(text(R.string.settings_close)).performClick()
        openChoices()
        pressBack()
        compose.onNodeWithTag("tabletLandscapeThemeSetting").assertTextContains(text(R.string.tablet_landscape_theme_pocket_advance))
        compose.runOnIdle {
            assertTrue(mountedSelections.isNotEmpty())
            assertTrue(mountedSelections.all { it == TabletLandscapeTheme.POCKET_ADVANCE })
            assertTrue(landscapeRequests.isEmpty()); assertTrue(primaryRequests.isEmpty())
            assertEquals(PlayerTheme.CLASSIC_WHEEL, repository.state.value.selectedPlayerTheme)
        }
    }

    @Test
    fun restoredHorizonChoiceSurvivesRepositoryRestartAndRecreationWithoutChangingCustomization() {
        runBlocking {
            repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_HORIZON)
            withTimeout(5_000) { repository.state.first { it.tabletLandscapeTheme == TabletLandscapeTheme.POCKET_HORIZON } }
            job.cancelAndJoin()
            job = SupervisorJob()
            repository = AppPreferencesRepository.create(ApplicationProvider.getApplicationContext<Context>(),
                CoroutineScope(job + Dispatchers.IO), filename, emptyList())
            assertEquals(TabletLandscapeTheme.POCKET_HORIZON, withTimeout(5_000) {
                repository.awaitLoadedState()
            }.tabletLandscapeTheme)
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.onNodeWithTag("tabletLandscapeThemeSetting").performScrollTo()
            .assertTextContains(text(R.string.tablet_landscape_theme_pocket_horizon))
        openChoices()
        option(TabletLandscapeTheme.POCKET_HORIZON).assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        option(TabletLandscapeTheme.POCKET_HORIZON).assertIsSelected()
        option(TabletLandscapeTheme.NORMAL_PLAYER).assertIsNotSelected()
        option(TabletLandscapeTheme.POCKET_ADVANCE).assertIsNotSelected()
        compose.onNodeWithText(text(R.string.settings_close)).performClick()
        // Horizon has only the selection action; Advance's real customization stays available.
        compose.onAllNodes(hasText(text(R.string.tablet_landscape_theme_pocket_horizon), substring = true)
            and hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("pocketAdvanceAppearanceSetting").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("pocketAdvanceAppearanceOptions").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_done)).performClick()
        compose.runOnIdle {
            assertEquals(TabletLandscapeTheme.POCKET_HORIZON, repository.state.value.tabletLandscapeTheme)
            assertTrue(landscapeRequests.isEmpty())
            assertTrue(primaryRequests.isEmpty())
        }
        openChoices()
        option(TabletLandscapeTheme.POCKET_ADVANCE).performClick()
        awaitSelection(TabletLandscapeTheme.POCKET_ADVANCE)
        compose.runOnIdle {
            assertEquals(listOf(TabletLandscapeTheme.POCKET_ADVANCE), landscapeRequests)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, repository.state.value.selectedPlayerTheme)
            assertEquals(colors, repository.state.value.playerThemeTokenOverrides[PlayerTheme.CLASSIC_WHEEL])
        }
    }

    @Test
    fun productionLabelsAndCompatibilityExplanationHaveEnglishAndSpanishResources() {
        fun resources(language: String) = compose.activity.createConfigurationContext(
            Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        val english = resources("en")
        val spanish = resources("es")
        listOf(R.string.settings_tablet_landscape_theme, R.string.settings_choose_tablet_landscape_theme,
            R.string.settings_tablet_landscape_theme_summary, R.string.tablet_landscape_theme_normal_player).forEach { id ->
            assertTrue(english.getString(id).isNotBlank()); assertTrue(spanish.getString(id).isNotBlank())
            assertNotEquals(english.getString(id), spanish.getString(id))
        }
        assertEquals("Pocket Advance and Pocket Horizon work on supported tablets in landscape. Other layouts use your normal player theme.",
            english.getString(R.string.settings_tablet_landscape_theme_summary))
        assertEquals("Pocket Advance y Pocket Horizon funcionan en tabletas compatibles en horizontal. En los demás casos se usa el tema normal del reproductor.",
            spanish.getString(R.string.settings_tablet_landscape_theme_summary))
        assertEquals("Use normal player", english.getString(R.string.tablet_landscape_theme_normal_player))
        assertEquals("Usar reproductor normal", spanish.getString(R.string.tablet_landscape_theme_normal_player))
        assertEquals("Pocket Advance", spanish.getString(R.string.tablet_landscape_theme_pocket_advance))
        // Proper theme names are shared, non-translatable resources.
        assertEquals("Pocket Horizon", english.getString(R.string.tablet_landscape_theme_pocket_horizon))
        assertEquals("Pocket Horizon", spanish.getString(R.string.tablet_landscape_theme_pocket_horizon))
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun option(theme: TabletLandscapeTheme) = compose.onNodeWithTag("tabletLandscapeThemeOption" + theme.name)
    private fun openChoices() = compose.onNodeWithTag("tabletLandscapeThemeSetting").performScrollTo().performClick()
    private fun awaitSelection(theme: TabletLandscapeTheme) {
        compose.waitUntil(5_000) { repository.state.value.tabletLandscapeTheme == theme }
        compose.waitForIdle()
    }

    @Composable
    private fun Content() {
        val state by repository.state.collectAsState()
        if (!state.isLoaded) return
        val scope = rememberCoroutineScope()
        SideEffect { mountedSelections += state.tabletLandscapeTheme }
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
                onPlayerThemeSelected = { theme -> primaryRequests += theme; scope.launch { repository.setSelectedPlayerTheme(theme) } },
                selectedTabletLandscapeTheme = state.tabletLandscapeTheme,
                onTabletLandscapeThemeSelected = { theme -> landscapeRequests += theme; scope.launch { repository.setTabletLandscapeTheme(theme) } },
                onUpdatePlayerThemeTokenOverride = { _, _, _ -> }, onResetPlayerThemeTokenOverrides = {},
                selectedModernArtworkTransitionStyle = state.modernArtworkTransitionStyle, onModernArtworkTransitionStyleSelected = {},
                selectedModernPlayerAppearance = state.modernPlayerAppearance, activeModernAppearanceChoice = state.activeModernAppearanceChoice,
                onModernAppearanceChoiceSelected = {}, onModernPlayerAppearanceEdited = {}, onResetModernPlayerAppearance = {},
                previewSong = null, selectedReplayGainMode = state.replayGainMode, onReplayGainModeSelected = {},
                selectedAudioOffloadPreference = state.audioOffloadPreference, onAudioOffloadPreferenceSelected = {})
        }
    }
}
