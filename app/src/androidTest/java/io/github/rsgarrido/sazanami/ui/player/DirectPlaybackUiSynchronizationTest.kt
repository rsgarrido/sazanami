package io.github.rsgarrido.sazanami.ui.player

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.*
import io.github.rsgarrido.sazanami.player.usb.*
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerPlayPauseButton
import io.github.rsgarrido.sazanami.ui.state.LocalPlaybackPlayPauseIntent
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Compose control and Media3 adapter with a fake engine. No JNI, USB or audio output. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DirectPlaybackUiSynchronizationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = Engine()
    private lateinit var player: DirectUsbMediaPlayer
    private var ui by mutableStateOf(PlaybackUiState())
    private val song = Song(12, "Track", "Artist", "Album", 1, 10000,
        Uri.parse("content://media/external/audio/media/12"), "private", "private", null)

    private fun content(direct: Boolean = true) {
        compose.runOnIdle {
            player = DirectUsbMediaPlayer(scope, engine, changed = { state ->
                ui = directPlaybackUiState(ui, state, listOf(song))
            }, librarySong = { song })
            player.setMediaItem(song.toPlayableMediaItem())
            player.prepare()
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlaybackPlayPauseIntent provides if (direct) ui.playPauseIntent else null) {
                    MiniPlayerPlayPauseButton(isPlaying = ui.isPlaying,
                        onClick = { player.playWhenReady = !player.playWhenReady })
                }
            }
        }
    }

    private fun pauseAction() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_pause))
    private fun playAction() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_play))
    private fun progress(phase: UsbFullTransportPhase, position: Long = 96000) = compose.runOnIdle {
        val intent = engine.control.intent()
        engine.progress(UsbFullProgress(96000, 480000, 48000, phase, mapOf(
            "sourceSeekCapable" to 1L, "sourcePositionFrame" to position,
            "confirmedSeekSerial" to intent.seekSerial, "seekSourceEpoch" to if (intent.seeks > 0) 1L else 0L,
            "seekConfirmed" to if (intent.seeks > 0) 1L else 0L)))
    }

    @Test fun preparationAndRapidPauseResumeActionsMatchRequestedIntentBeforeConfirmation() {
        content()
        pauseAction().assertExists() // Preparing with play intent has a Pause action, not a false Play action.
        compose.runOnIdle { assertFalse(ui.isPlaying) }
        pauseAction().performClick()
        compose.runOnIdle { assertFalse(engine.control.playWhenReady); assertFalse(ui.isPlaying) }
        playAction().performClick()
        compose.runOnIdle { assertTrue(engine.control.playWhenReady); assertFalse(ui.isPlaying) }
        progress(UsbFullTransportPhase.PLAYING)
        pauseAction().performClick()
        compose.runOnIdle { assertEquals(IntegratedDirectPhase.PAUSING, player.evidence.phase); assertFalse(ui.isPlaying) }
        playAction().performClick()
        compose.runOnIdle { assertEquals(IntegratedDirectPhase.RESUMING, player.evidence.phase); assertFalse(ui.isPlaying) }
        pauseAction().performClick() // A tap during RESUMING still requests Pause.
        progress(UsbFullTransportPhase.PAUSED)
        playAction().assertExists()
        compose.runOnIdle { assertFalse(player.isPlaying); assertFalse(ui.isPlaying) }
        playAction().performClick()
        progress(UsbFullTransportPhase.PLAYING)
        compose.runOnIdle { assertTrue(ui.isPlaying); assertEquals(1, engine.starts) }
    }

    @Test fun seekingAndRejectedCommandsNeverDisplayFalseConfirmedPlayingState() {
        content()
        progress(UsbFullTransportPhase.PLAYING)
        compose.runOnIdle { player.seekTo(5000); assertEquals(IntegratedDirectPhase.SEEKING, player.evidence.phase) }
        pauseAction().assertExists()
        pauseAction().performClick()
        compose.runOnIdle { assertFalse(engine.control.playWhenReady); assertFalse(ui.isPlaying) }
        progress(UsbFullTransportPhase.PAUSED, position = 240000)
        playAction().assertExists()
        compose.runOnIdle { engine.acceptCommands = false }
        playAction().performClick()
        compose.runOnIdle {
            assertEquals(IntegratedDirectPhase.STOPPING, player.evidence.phase)
            assertFalse(ui.isPlaying)
            assertEquals(false, ui.playPauseIntent)
            assertEquals(1, engine.starts)
        }
    }

    @Test fun androidPresentationUsesExistingConfirmedPlayingInput() {
        content(direct = false)
        playAction().assertExists()
        progress(UsbFullTransportPhase.PLAYING)
        pauseAction().assertExists()
        compose.runOnIdle { player.pause() }
        playAction().assertExists()
    }

    @After fun close() {
        compose.runOnIdle {
            if (::player.isInitialized) player.requestStop()
            engine.terminal.complete(UsbSilentTransportExperimentResult(1, 2, UsbSilentTransportResolution(),
                status = UsbSilentStatus.CANCELLED))
            scope.cancel()
        }
    }

    private class Engine : FullUsbPlaybackEngineClient {
        var starts = 0
        var acceptCommands = true
        lateinit var control: UsbFullPlaybackControl
        lateinit var progress: (UsbFullProgress) -> Unit
        val terminal = CompletableDeferred<UsbSilentTransportExperimentResult>()
        override suspend fun runIntegratedFull(uri: Uri, control: UsbFullPlaybackControl,
            progress: (UsbFullProgress) -> Unit): UsbSilentTransportExperimentResult {
            starts++; this.control = control; this.progress = progress
            control.bind({ acceptCommands }, {})
            return terminal.await()
        }
        override fun stopIntegratedFull() {}
    }
}
