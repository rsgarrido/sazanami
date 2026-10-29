package io.github.rsgarrido.sazanami.data.preferences

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketAdvanceAppearancePreferencesTest {
    private lateinit var context: Context
    private lateinit var repository: AppPreferencesRepository
    private lateinit var job: Job
    private lateinit var filename: String
    private val custom = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.BERRY,
        PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY)

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        filename = "pocket_advance_appearance_" + System.nanoTime() + ".preferences_pb"
        openRepository()
        assertEquals(PocketAdvanceAppearance.Default, withTimeout(5_000) { repository.awaitLoadedState() }.pocketAdvanceAppearance)
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::job.isInitialized) job.cancelAndJoin()
        if (::filename.isInitialized) context.preferencesDataStoreFile(filename).delete()
    }

    @Test
    fun individualAndConcurrentUpdatesPreserveAllOtherCategoriesWhileDisabled() = runBlocking {
        repository.setPocketAdvanceConsoleFinish(custom.consoleFinish)
        awaitAppearance(PocketAdvanceAppearance(consoleFinish = custom.consoleFinish))
        repository.setPocketAdvanceLcdPalette(custom.lcdPalette)
        awaitAppearance(PocketAdvanceAppearance(consoleFinish = custom.consoleFinish, lcdPalette = custom.lcdPalette))
        repository.setPocketAdvanceControlsFinish(custom.controlsFinish)
        awaitAppearance(custom)
        val latest = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
            PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.DEEP_NAVY)
        listOf(async { repository.setPocketAdvanceConsoleFinish(latest.consoleFinish) },
            async { repository.setPocketAdvanceLcdPalette(latest.lcdPalette) },
            async { repository.setPocketAdvanceControlsFinish(latest.controlsFinish) }).awaitAll()
        val state = awaitAppearance(latest)
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, state.tabletLandscapeTheme)
        assertEquals(PlayerTheme.DEFAULT, state.selectedPlayerTheme)
    }

    @Test
    fun disablingReenablingAndResetNeverChangeOtherThemePreferences() = runBlocking {
        val colors = PlayerThemeTokenOverrides(shellColor = Color(0xFF123456))
        repository.setSelectedPlayerTheme(PlayerTheme.CLASSIC_WHEEL)
        repository.setThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL, colors)
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_ADVANCE)
        setCustomAppearance()
        awaitAppearance(custom)
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.NORMAL_PLAYER)
        val disabled = withTimeout(5_000) { repository.state.first { it.tabletLandscapeTheme == TabletLandscapeTheme.NORMAL_PLAYER } }
        assertEquals(custom, disabled.pocketAdvanceAppearance)
        repository.setSelectedPlayerTheme(PlayerTheme.POCKET_CASSETTE)
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_ADVANCE)
        val enabled = withTimeout(5_000) { repository.state.first {
            it.tabletLandscapeTheme == TabletLandscapeTheme.POCKET_ADVANCE && it.selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE
        } }
        assertEquals(custom, enabled.pocketAdvanceAppearance)
        repository.resetPocketAdvanceAppearance()
        val reset = awaitAppearance(PocketAdvanceAppearance.Default)
        assertEquals(TabletLandscapeTheme.POCKET_ADVANCE, reset.tabletLandscapeTheme)
        assertEquals(PlayerTheme.POCKET_CASSETTE, reset.selectedPlayerTheme)
        assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), reset.playerThemeTokenOverrides)
    }

    @Test
    fun reopeningStartsWithTheStoredAppearanceAndFullReplacementIncludesAllThreeIds() = runBlocking {
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_ADVANCE)
        setCustomAppearance()
        awaitAppearance(custom)
        job.cancelAndJoin()
        openRepository()
        val reopened = withTimeout(5_000) { repository.awaitLoadedState() }
        assertEquals(custom, reopened.pocketAdvanceAppearance)
        assertEquals(TabletLandscapeTheme.POCKET_ADVANCE, reopened.tabletLandscapeTheme)
        val replacement = reopened.copy(selectedPlayerTheme = PlayerTheme.POCKET_FLIP,
            pocketAdvanceAppearance = custom.copy(consoleFinish = PocketAdvanceConsoleFinish.GRAPHITE))
        repository.replaceAll(replacement)
        val restored = awaitAppearance(replacement.pocketAdvanceAppearance)
        assertEquals(PlayerTheme.POCKET_FLIP, restored.selectedPlayerTheme)
        assertEquals(replacement.tabletLandscapeTheme, restored.tabletLandscapeTheme)
        // A pre-customization full replacement has original defaults, without changing its other selections.
        repository.replaceAll(replacement.copy(pocketAdvanceAppearance = PocketAdvanceAppearance.Default))
        val older = awaitAppearance(PocketAdvanceAppearance.Default)
        assertEquals(replacement.selectedPlayerTheme, older.selectedPlayerTheme)
        assertEquals(replacement.tabletLandscapeTheme, older.tabletLandscapeTheme)
    }

    private fun openRepository() {
        job = SupervisorJob()
        repository = AppPreferencesRepository.create(context, CoroutineScope(job + Dispatchers.IO), filename, emptyList())
    }
    private suspend fun setCustomAppearance() {
        repository.setPocketAdvanceConsoleFinish(custom.consoleFinish)
        repository.setPocketAdvanceLcdPalette(custom.lcdPalette)
        repository.setPocketAdvanceControlsFinish(custom.controlsFinish)
    }
    private suspend fun awaitAppearance(appearance: PocketAdvanceAppearance): AppPreferencesState =
        withTimeout(5_000) { repository.state.first { it.isLoaded && it.pocketAdvanceAppearance == appearance } }
}
