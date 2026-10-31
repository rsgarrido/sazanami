package io.github.rsgarrido.sazanami.data.preferences

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
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
class PocketHorizonAppearancePreferencesTest {
    private lateinit var context: Context
    private lateinit var repository: AppPreferencesRepository
    private lateinit var job: Job
    private lateinit var filename: String
    private val custom = PocketHorizonAppearance(PocketHorizonConsoleFinish.DEEP_BURGUNDY,
        PocketHorizonLcdPalette.WARM_AMBER, PocketHorizonWaveTreatment.SUBTLE)

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        filename = "pocket_horizon_appearance_" + System.nanoTime() + ".preferences_pb"
        openRepository()
        assertEquals(PocketHorizonAppearance.Default, withTimeout(5_000) { repository.awaitLoadedState() }.pocketHorizonAppearance)
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::job.isInitialized) job.cancelAndJoin()
        if (::filename.isInitialized) context.preferencesDataStoreFile(filename).delete()
    }

    @Test
    fun individualAndConcurrentUpdatesPreserveAllOtherCategoriesWhileDisabled() = runBlocking {
        repository.setPocketHorizonConsoleFinish(custom.consoleFinish)
        awaitAppearance(PocketHorizonAppearance(consoleFinish = custom.consoleFinish))
        repository.setPocketHorizonLcdPalette(custom.lcdPalette)
        awaitAppearance(PocketHorizonAppearance(consoleFinish = custom.consoleFinish, lcdPalette = custom.lcdPalette))
        repository.setPocketHorizonWaveTreatment(custom.waveTreatment)
        awaitAppearance(custom)
        val latest = PocketHorizonAppearance(PocketHorizonConsoleFinish.MIDNIGHT_BLUE,
            PocketHorizonLcdPalette.EMERALD_TEAL, PocketHorizonWaveTreatment.OFF)
        listOf(async { repository.setPocketHorizonConsoleFinish(latest.consoleFinish) },
            async { repository.setPocketHorizonLcdPalette(latest.lcdPalette) },
            async { repository.setPocketHorizonWaveTreatment(latest.waveTreatment) }).awaitAll()
        val state = awaitAppearance(latest)
        assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, state.tabletLandscapeTheme)
        assertEquals(PlayerTheme.DEFAULT, state.selectedPlayerTheme)
    }

    @Test
    fun disablingReenablingAndResetNeverChangeOtherThemePreferences() = runBlocking {
        repository.setPocketAdvanceConsoleFinish(io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish.BERRY)
        val colors = PlayerThemeTokenOverrides(shellColor = Color(0xFF123456))
        repository.setSelectedPlayerTheme(PlayerTheme.CLASSIC_WHEEL)
        repository.setThemeTokenOverrides(PlayerTheme.CLASSIC_WHEEL, colors)
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_HORIZON)
        setCustomAppearance()
        awaitAppearance(custom)
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.NORMAL_PLAYER)
        val disabled = withTimeout(5_000) { repository.state.first { it.tabletLandscapeTheme == TabletLandscapeTheme.NORMAL_PLAYER } }
        assertEquals(custom, disabled.pocketHorizonAppearance)
        repository.setSelectedPlayerTheme(PlayerTheme.POCKET_CASSETTE)
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_HORIZON)
        val enabled = withTimeout(5_000) { repository.state.first {
            it.tabletLandscapeTheme == TabletLandscapeTheme.POCKET_HORIZON && it.selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE
        } }
        assertEquals(custom, enabled.pocketHorizonAppearance)
        repository.resetPocketHorizonAppearance()
        val reset = awaitAppearance(PocketHorizonAppearance.Default)
        assertEquals(io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish.BERRY, reset.pocketAdvanceAppearance.consoleFinish)
        assertEquals(TabletLandscapeTheme.POCKET_HORIZON, reset.tabletLandscapeTheme)
        assertEquals(PlayerTheme.POCKET_CASSETTE, reset.selectedPlayerTheme)
        assertEquals(mapOf(PlayerTheme.CLASSIC_WHEEL to colors), reset.playerThemeTokenOverrides)
    }

    @Test
    fun reopeningStartsWithTheStoredAppearanceAndFullReplacementIncludesAllThreeIds() = runBlocking {
        repository.setTabletLandscapeTheme(TabletLandscapeTheme.POCKET_HORIZON)
        setCustomAppearance()
        awaitAppearance(custom)
        job.cancelAndJoin()
        openRepository()
        val reopened = withTimeout(5_000) { repository.awaitLoadedState() }
        assertEquals(custom, reopened.pocketHorizonAppearance)
        assertEquals(TabletLandscapeTheme.POCKET_HORIZON, reopened.tabletLandscapeTheme)
        val replacement = reopened.copy(selectedPlayerTheme = PlayerTheme.POCKET_FLIP,
            pocketHorizonAppearance = custom.copy(consoleFinish = PocketHorizonConsoleFinish.GRAPHITE))
        repository.replaceAll(replacement)
        val restored = awaitAppearance(replacement.pocketHorizonAppearance)
        assertEquals(PlayerTheme.POCKET_FLIP, restored.selectedPlayerTheme)
        assertEquals(replacement.tabletLandscapeTheme, restored.tabletLandscapeTheme)
        // A pre-customization full replacement has original defaults, without changing its other selections.
        repository.replaceAll(replacement.copy(pocketHorizonAppearance = PocketHorizonAppearance.Default))
        val older = awaitAppearance(PocketHorizonAppearance.Default)
        assertEquals(replacement.selectedPlayerTheme, older.selectedPlayerTheme)
        assertEquals(replacement.tabletLandscapeTheme, older.tabletLandscapeTheme)
    }

    private fun openRepository() {
        job = SupervisorJob()
        repository = AppPreferencesRepository.create(context, CoroutineScope(job + Dispatchers.IO), filename, emptyList())
    }
    private suspend fun setCustomAppearance() {
        repository.setPocketHorizonConsoleFinish(custom.consoleFinish)
        repository.setPocketHorizonLcdPalette(custom.lcdPalette)
        repository.setPocketHorizonWaveTreatment(custom.waveTreatment)
    }
    private suspend fun awaitAppearance(appearance: PocketHorizonAppearance): AppPreferencesState =
        withTimeout(5_000) { repository.state.first { it.isLoaded && it.pocketHorizonAppearance == appearance } }
}
