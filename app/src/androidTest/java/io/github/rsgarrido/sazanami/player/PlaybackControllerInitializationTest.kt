package io.github.rsgarrido.sazanami.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.player.audio.AdvancedAudioRuntimeBridge
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.viewmodel.MusicViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackControllerInitializationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun resetRuntimeBridge() {
        onMain {
            AdvancedAudioRuntimeBridge.disconnect()
            EqualizerRuntimeBridge.release()
        }
    }

    @Test
    fun playbackControllerConstructsWithImmediateDispatcher() {
        withImmediateController { controller ->
            assertNotNull(controller.audioOutputState.value)
        }
    }

    @Test
    fun initialOffloadPreferenceEmissionDoesNotAccessUninitializedState() {
        onMain {
            AdvancedAudioRuntimeBridge.onPlayerConnected(AudioOffloadPreference.AUTOMATIC)
        }

        withImmediateController { controller ->
            assertEquals(
                AudioOffloadPreference.AUTOMATIC,
                controller.audioOutputState.value.offloadState.requestedPreference
            )
        }
    }

    @Test
    fun initialAudioOutputStateIsAvailableImmediately() {
        onMain {
            AdvancedAudioRuntimeBridge.disconnect()
        }

        withImmediateController { controller ->
            val state = controller.audioOutputState.value
            assertNotNull(state)
            assertFalse(state.isPlayerConnected)
            assertEquals(
                AudioOffloadPreference.DISABLED,
                state.offloadState.requestedPreference
            )
        }
    }

    @Test
    fun preferenceCollectorStartsExactlyOnce() {
        withImmediateController { controller ->
            assertEquals(
                1,
                controller.privateIntField("advancedAudioRuntimeCollectionStartCount")
            )
            assertTrue(
                controller.privateJobField("advancedAudioRuntimeCollectionJob").isActive
            )
        }
    }

    @Test
    fun equalizerRuntimeUpdatesPreserveReplayGainState() {
        withImmediateController { controller ->
            controller.setReplayGainMode(ReplayGainMode.TRACK)
            AdvancedAudioRuntimeBridge.updateEqualizerRuntimeState(
                EqualizerRuntimeState(
                    processorConfigured = true,
                    requestedEnabled = true,
                    effectivelyActive = true,
                    bypassed = false,
                    configurationVersion = 4L,
                    appliedPlanVersion = 4L,
                    sampleRateHz = 48_000,
                    channelCount = 2,
                    requiresDecodedPcm = true
                )
            )

            val state = controller.audioOutputState.value
            assertEquals(
                ReplayGainMode.TRACK,
                state.replayGainMode
            )
            assertEquals(
                4L,
                state.equalizerRuntimeState.appliedPlanVersion
            )
        }
    }

    @Test
    fun releaseCancelsPreferenceCollector() {
        val scope = immediateScope()
        var capturedJob: Job? = null
        onMain {
            val controller = PlaybackController(context, scope)
            capturedJob = controller.privateJobField("advancedAudioRuntimeCollectionJob")
            assertTrue(requireNotNull(capturedJob).isActive)
            controller.release()
        }

        assertFalse(requireNotNull(capturedJob).isActive)
        scope.cancel()
    }

    @Test
    fun constructingMusicViewModelDoesNotThrow() {
        lateinit var store: ViewModelStore
        lateinit var viewModel: MusicViewModel
        onMain {
            val application = ApplicationProvider.getApplicationContext<Application>()
            store = ViewModelStore()
            val provider = ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MusicViewModel(application) as T
                    }
                }
            )

            viewModel = provider[MusicViewModel::class.java]
            assertNotNull(viewModel.audioOutputUiState.value)
        }

        waitUntil(timeoutMillis = 5_000) {
            viewModel.playbackUiState.value.isConnected
        }

        onMain {
            store.clear()
        }
    }

    private fun withImmediateController(assertions: (PlaybackController) -> Unit) {
        val scope = immediateScope()
        onMain {
            val controller = PlaybackController(context, scope)
            try {
                assertions(controller)
            } finally {
                controller.release()
            }
        }
        scope.cancel()
    }

    private fun immediateScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun PlaybackController.privateIntField(name: String): Int {
        return PlaybackController::class.java.getDeclaredField(name).run {
            isAccessible = true
            getInt(this@privateIntField)
        }
    }

    private fun PlaybackController.privateJobField(name: String): Job {
        return PlaybackController::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateJobField) as Job
        }
    }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        requireNotNull(result).getOrThrow()
    }

    private fun waitUntil(
        timeoutMillis: Long,
        condition: () -> Boolean
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(25)
        }
        assertTrue("Condition was not met within $timeoutMillis ms", condition())
    }
}
