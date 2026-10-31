package io.github.rsgarrido.sazanami.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.*
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale

class PocketHorizonAppearanceSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var repository: AppPreferencesRepository
    private lateinit var job: Job
    private lateinit var file: File
    private var largeFonts = false

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "horizon_settings_${System.nanoTime()}.preferences_pb"
        file = context.preferencesDataStoreFile(name)
        job = SupervisorJob()
        repository = AppPreferencesRepository.create(context, CoroutineScope(job + Dispatchers.IO), name, emptyList())
        withTimeout(5_000) { repository.awaitLoadedState() }
    }
    @After fun close() = runBlocking<Unit> { job.cancelAndJoin(); file.delete() }

    @Composable private fun Content() {
        val state by repository.state.collectAsState()
        val scope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if (largeFonts) 2f else density.fontScale)) {
        MaterialTheme {
            PocketHorizonAppearanceSettings(state.pocketHorizonAppearance,
                { scope.launch { repository.setPocketHorizonConsoleFinish(it) } },
                { scope.launch { repository.setPocketHorizonLcdPalette(it) } },
                { scope.launch { repository.setPocketHorizonWaveTreatment(it) } },
                { scope.launch { repository.resetPocketHorizonAppearance() } })
        }
    }
    }
    private fun choose(section: Int, name: Int) = compose.onNodeWithContentDescription(
        compose.activity.getString(R.string.theme_color_swatch, compose.activity.getString(section), compose.activity.getString(name)))
        .performScrollTo().assertIsEnabled().performClick()
    private fun await(expected: PocketHorizonAppearance) = compose.waitUntil(5_000) {
        repository.state.value.pocketHorizonAppearance == expected
    }
    private fun pixels() = compose.onNodeWithTag("pocketHorizonAppearancePreview").performScrollTo()
        .captureToImage().toPixelMap().let { p -> List(p.height) { y -> List(p.width) { x -> p[x,y] } } }

    @Test fun liveSelectionsPreviewResetAndRecreationWorkWithoutActivatingTheTheme() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Content() }
        compose.onNodeWithTag("pocketAdvanceAppearanceSetting").assertIsEnabled().performClick()
        val original = pixels()
        choose(R.string.pocket_horizon_console_finish, R.string.horizon_finish_silver)
        val shell = PocketHorizonAppearance(consoleFinish = PocketHorizonConsoleFinish.SILVER)
        await(shell)
        assertNotEquals(original, pixels())
        choose(R.string.pocket_horizon_lcd_palette, R.string.horizon_lcd_warm_amber)
        await(shell.copy(lcdPalette = PocketHorizonLcdPalette.WARM_AMBER))
        val standardWaves = pixels()
        choose(R.string.pocket_horizon_wave_treatment, R.string.horizon_wave_off)
        val selected = shell.copy(lcdPalette = PocketHorizonLcdPalette.WARM_AMBER, waveTreatment = PocketHorizonWaveTreatment.OFF)
        await(selected)
        assertNotEquals(standardWaves, pixels())
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("pocketHorizonAppearanceOptions").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_done)).performClick()
        await(selected)
        compose.onNodeWithTag("pocketHorizonAppearanceOptions").assertDoesNotExist()
        compose.onNodeWithTag("pocketAdvanceAppearanceSetting").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.theme_color_reset)).performClick()
        await(PocketHorizonAppearance.Default)
        assertEquals(original, pixels())
        compose.runOnIdle {
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, repository.state.value.tabletLandscapeTheme)
            assertEquals(PocketAdvanceAppearance.Default, repository.state.value.pocketAdvanceAppearance)
        }
    }

    @Test fun largeFontDialogKeepsEverySectionAndBottomChoiceReachable() {
        largeFonts = true
        compose.setContent { Content() }
        compose.onNodeWithTag("pocketAdvanceAppearanceSetting").performClick()
        choose(R.string.pocket_horizon_console_finish, R.string.horizon_finish_deep_burgundy)
        choose(R.string.pocket_horizon_lcd_palette, R.string.horizon_lcd_emerald_teal)
        choose(R.string.pocket_horizon_wave_treatment, R.string.horizon_wave_off)
        await(PocketHorizonAppearance(PocketHorizonConsoleFinish.DEEP_BURGUNDY,
            PocketHorizonLcdPalette.EMERALD_TEAL, PocketHorizonWaveTreatment.OFF))
        compose.onNodeWithText(compose.activity.getString(R.string.settings_done)).assertIsDisplayed().performClick()
    }

    @Test fun allOptionsHaveEnglishAndSpanishLabels() {
        val ids = listOf(R.string.settings_customize_pocket_horizon, R.string.settings_customize_pocket_horizon_summary,
            R.string.pocket_horizon_appearance_title, R.string.pocket_horizon_console_finish,
            R.string.pocket_horizon_lcd_palette, R.string.pocket_horizon_wave_treatment, R.string.horizon_appearance_preview) +
            PocketHorizonConsoleFinish.entries.map { it.labelRes } + PocketHorizonLcdPalette.entries.map { it.labelRes } +
            PocketHorizonWaveTreatment.entries.map { it.labelRes }
        for (language in listOf("en", "es")) {
            val resources = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration)
                .apply { setLocale(Locale.forLanguageTag(language)) }).resources
            ids.forEach { assertTrue(resources.getString(it).isNotBlank()) }
        }
    }
}
