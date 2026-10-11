package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.media3.common.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Fake engine only: no USB permission, source FD, renderer, AudioTrack or hardware playback. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DirectUsbMediaPlayerTest {
    private class Engine : FullUsbPlaybackEngineClient {
        var starts=0;var stops=0;var uri: Uri?=null
        var progress: ((UsbFullProgress)->Unit)?=null
        val terminal=CompletableDeferred<UsbSilentTransportExperimentResult>()
        override suspend fun runIntegratedFull(uri: Uri,progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult {
            starts++;this.uri=uri;this.progress=progress;return terminal.await()
        }
        override fun stopIntegratedFull() { stops++ }
    }
    private val song=Song(12,"Library title","Library artist","Library album",1,1000,
        Uri.parse("content://media/external/audio/media/12"),"private path","private folder",null)
    private fun stopped()=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=UsbSilentStatus.CANCELLED,
        configuration=UsbAudioConfigurationResult(1,UsbAudioConfigurationResolution(),targetStatus=UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
            acClaim=UsbConfigurationAndroidOwnership(true,true),asClaim=UsbConfigurationAndroidOwnership(true,true),
            restorationStatus=UsbConfigurationRestorationStatus.RESTORED,asRelease=UsbConfigurationAndroidOwnership(true,true),
            acRelease=UsbConfigurationAndroidOwnership(true,true),closeStatus=UsbAudioCleanupStatus.SUCCEEDED),
        transport=UsbIsoTransportResult(1,2,mapOf("workerStopped" to 1L,"outstandingUrbsFinal" to 0L,"framesCompleted" to 100L)),
        originalFlacFdClosed=true,flacSource=UsbFlacSourceResult(mapOf("totalSamples" to 44100L,"producerJoined" to 1L)))
    @Test fun libraryMetadataAndCompletedProgressAreObservedAndStopUsesSameEngine() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val engine=Engine();lateinit var player: DirectUsbMediaPlayer
        instrumentation.runOnMainSync {
            player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={ song })
            player.setMediaItem(song.toPlayableMediaItem(),500)
            assertEquals("Library title",player.currentMediaItem?.mediaMetadata?.title.toString())
            player.prepare();assertEquals(1,engine.starts);assertEquals(song.uri,engine.uri)
            assertEquals(IntegratedDirectPhase.PREPARING,player.evidence.phase);assertFalse(player.isPlaying)
            engine.progress!!.invoke(UsbFullProgress(100,44100))
            assertEquals(IntegratedDirectPhase.PLAYING,player.evidence.phase);assertEquals(2L,player.currentPosition)
            assertFalse(player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
            assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            player.pause();player.seekTo(700);assertTrue(player.isPlaying) // unavailable commands cannot fake a pause/seek
            player.stop();assertEquals(1,engine.stops);assertEquals(IntegratedDirectPhase.STOPPING,player.evidence.phase)
            engine.terminal.complete(stopped())
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals(IntegratedDirectPhase.STOPPED,player.evidence.phase);assertFalse(player.isPlaying)
            assertTrue(player.evidence.cleanupSafe);assertEquals(1,engine.starts)
            scope.cancel()
        }
    }
    @Test fun unknownLibraryIdentityNeverCallsEngineAndCannotFallBack() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val engine=Engine()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={ null })
            player.setMediaItem(song.toPlayableMediaItem());player.prepare()
            assertEquals(0,engine.starts);assertEquals(IntegratedDirectPhase.ERROR,player.evidence.phase)
            assertEquals("UNSUPPORTED_TRACK",player.evidence.error);scope.cancel()
        }
    }
    @Test fun activeReplacementIsRejectedWithoutChangingSourceOrGeneration() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val engine=Engine()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={ song })
            player.setMediaItem(song.toPlayableMediaItem());player.prepare()
            val generation=player.evidence.generation
            player.setMediaItem(song.copy(id=13,title="Other").toPlayableMediaItem())
            assertEquals(12L,player.evidence.libraryId);assertEquals(generation,player.evidence.generation)
            assertEquals(1,engine.starts)
            player.stop();engine.terminal.complete(stopped())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();scope.cancel()
    }
}
