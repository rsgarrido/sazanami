package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.*
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PocketHorizonAppearanceRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var appearance by mutableStateOf(PocketHorizonAppearance.Default)
    private val song = Song(1L, "Track", "Artist", "Album", 1, 180_000,
        Uri.parse("content://media/1"), "", "", null)

    @Test fun allCombinationsKeepRealHardwareSeekAndGuideFunctionalAndOffStillHasASeekWave() {
        val commands = mutableListOf<String>()
        val seeks = mutableListOf<Int>()
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 30_000, duration = 180_000))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.5f, 1f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(1000.dp, 640.dp)) {
                        PocketHorizonExpandedPlayer(PlaybackUiState(isConnected = true, currentSong = song), progress,
                            { commands += "play" }, { commands += "previous" }, { commands += "next" }, { seeks += it },
                            style = remember(appearance) { PocketHorizonStyle.from(appearance) }, seekEnabled = true)
                    }
                }
            }
        }
        var graphite: List<androidx.compose.ui.graphics.Color>? = null
        for (finish in PocketHorizonConsoleFinish.entries) for (lcd in PocketHorizonLcdPalette.entries)
            for (wave in PocketHorizonWaveTreatment.entries) {
                compose.runOnIdle { appearance = PocketHorizonAppearance(finish, lcd, wave) }
                compose.onNodeWithTag("horizonWaveTrack", useUnmergedTree = true).assertExists()
                compose.onNodeWithTag("pocketHorizonSeek").assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(60_000f)) }
                val shell = compose.onNodeWithTag("pocketHorizonShell").captureToImage().toPixelMap()
                // The uninterrupted upper body below the cap is part of the real front housing.
                val samples = listOf(36, 44, 52).map { shell[it, 22] }
                if (lcd == PocketHorizonLcdPalette.BLUE_VIOLET && wave == PocketHorizonWaveTreatment.STANDARD) {
                    if (finish == PocketHorizonConsoleFinish.GRAPHITE) graphite = samples
                    else assertNotEquals(graphite, samples)
                }
            }
        assertEquals(36, seeks.size)
        compose.onNodeWithTag("horizonL").performClick()
        compose.onNodeWithTag("horizonR").performClick()
        compose.onNodeWithTag("horizonX").performClick()
        compose.onNodeWithTag("horizonSelect").performClick()
        compose.onNodeWithTag("horizonControlGuide").assertIsDisplayed()
        compose.onNodeWithTag("horizonB").performClick()
        compose.runOnIdle { assertEquals(listOf("previous", "next", "play"), commands) }
    }
}
