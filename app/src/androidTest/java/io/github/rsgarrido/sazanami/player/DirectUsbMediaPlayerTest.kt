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
    @Test fun media3Uses96000CompletedFramesWithoutWallClockInterpolation() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val engine=Engine()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        lateinit var player: DirectUsbMediaPlayer
        instrumentation.runOnMainSync {
            player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={ song })
            player.setMediaItem(song.toPlayableMediaItem());player.prepare()
            engine.progress!!.invoke(UsbFullProgress(96000,192000,96000))
            assertEquals(96000L,player.evidence.sampleRateHz)
            assertEquals(1000L,player.currentPosition);assertEquals(2000L,player.duration)
            assertEquals(IntegratedDirectPhase.PLAYING,player.evidence.phase)
            assertEquals(1,engine.starts);assertEquals(song.uri,engine.uri)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals(1000L,player.currentPosition)
            player.stop();assertEquals(1,engine.stops);engine.terminal.complete(stopped())
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { scope.cancel() }
    }
    @Test fun media3Uses48000CompletedFramesForPositionAndDuration() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val engine=Engine()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        lateinit var player: DirectUsbMediaPlayer
        instrumentation.runOnMainSync {
            player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={ song })
            player.setMediaItem(song.toPlayableMediaItem());player.prepare()
            engine.progress!!.invoke(UsbFullProgress(48000,96000,48000))
            assertEquals(48000L,player.evidence.sampleRateHz)
            assertEquals(1000L,player.currentPosition);assertEquals(2000L,player.duration)
            assertEquals(IntegratedDirectPhase.PLAYING,player.evidence.phase)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals(1000L,player.currentPosition) // No extrapolation while completed frames remain fixed.
            player.stop();engine.terminal.complete(stopped())
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { scope.cancel() }
    }
    private class Engine : FullUsbPlaybackEngineClient {
        var starts=0;var stops=0;var uri: Uri?=null
        var progress: ((UsbFullProgress)->Unit)?=null
        var control: UsbFullPlaybackControl?=null
        var terminal=CompletableDeferred<UsbSilentTransportExperimentResult>()
        override suspend fun runIntegratedFull(uri: Uri,control: UsbFullPlaybackControl,progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult {
            starts++;this.uri=uri;this.progress=progress;this.control=control;control.bind({true},{})
            return terminal.await()
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
            engine.progress!!.invoke(UsbFullProgress(100,44100,44100))
            assertEquals(IntegratedDirectPhase.PLAYING,player.evidence.phase);assertEquals(2L,player.currentPosition)
            assertTrue(player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
            assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            player.seekTo(700);assertTrue(player.isPlaying) // Seeking remains excluded.
            player.pause();assertFalse(engine.control!!.playWhenReady);assertFalse(player.isPlaying)
            engine.progress!!.invoke(UsbFullProgress(100,44100,44100,UsbFullTransportPhase.PAUSED))
            assertEquals(IntegratedDirectPhase.PAUSED,player.evidence.phase)
            player.play();assertTrue(engine.control!!.playWhenReady);assertFalse(player.isPlaying)
            engine.progress!!.invoke(UsbFullProgress(100,44100,44100,UsbFullTransportPhase.PLAYING))
            assertTrue(player.isPlaying);assertEquals(1,engine.starts)
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
    @Test fun pausedPositionMetadataAndRepeatedControlsUseOneWorkerAndIgnoreReplacedGeneration() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val engine=Engine()
        lateinit var player: DirectUsbMediaPlayer;lateinit var oldProgress: (UsbFullProgress)->Unit
        lateinit var oldControl: UsbFullPlaybackControl
        instrumentation.runOnMainSync {
            player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={song})
            player.setMediaItem(song.toPlayableMediaItem());player.prepare()
            oldProgress=engine.progress!!;oldControl=engine.control!!
            engine.progress!!.invoke(UsbFullProgress(48000,240000,48000))
            repeat(2) {
                player.playWhenReady=false
                engine.progress!!.invoke(UsbFullProgress(48100,240000,48000,UsbFullTransportPhase.PAUSED))
                assertFalse(player.isPlaying);assertEquals(1002L,player.currentPosition)
                assertEquals("Library title",player.currentMediaItem?.mediaMetadata?.title.toString())
                assertEquals(5000L,player.duration)
                player.pause();assertEquals(IntegratedDirectPhase.PAUSED,player.evidence.phase)
                player.playWhenReady=true;assertFalse(player.isPlaying)
                engine.progress!!.invoke(UsbFullProgress(48100,240000,48000,UsbFullTransportPhase.RESUMING))
                assertFalse(player.isPlaying)
                engine.progress!!.invoke(UsbFullProgress(48100,240000,48000,UsbFullTransportPhase.PLAYING))
                assertTrue(player.isPlaying);assertEquals(1,engine.starts)
            }
            player.pause();engine.progress!!.invoke(UsbFullProgress(48100,240000,48000,UsbFullTransportPhase.PAUSED))
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals(1002L,player.currentPosition);assertFalse(player.isPlaying)
            player.stop();assertFalse(oldControl.request(true))
            oldProgress(UsbFullProgress(96000,240000,48000,UsbFullTransportPhase.PLAYING))
            assertEquals(IntegratedDirectPhase.STOPPING,player.evidence.phase)
            engine.terminal.complete(stopped())
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertFalse(player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
            engine.terminal=CompletableDeferred()
            player.setMediaItem(song.copy(id=13,title="Replacement").toPlayableMediaItem());player.prepare()
            val current=player.evidence
            oldProgress(UsbFullProgress(48100,240000,48000,UsbFullTransportPhase.PAUSED))
            assertEquals(current,player.evidence);assertEquals(2,engine.starts)
            assertFalse(oldControl.request(true));assertNotSame(oldControl,engine.control)
            player.stop();engine.terminal.complete(stopped())
        }
        instrumentation.waitForIdleSync();scope.cancel()
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
    @Test fun stopStillTerminatesWorkerAfterAnInvalidProgressProjection() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val engine=Engine()
        lateinit var player: DirectUsbMediaPlayer
        instrumentation.runOnMainSync {
            player=DirectUsbMediaPlayer(scope,engine,changed={},librarySong={song})
            player.setMediaItem(song.toPlayableMediaItem());player.prepare()
            engine.progress!!.invoke(UsbFullProgress(48000,96000,48000))
            engine.progress!!.invoke(UsbFullProgress(47999,96000,48000))
            assertEquals(IntegratedDirectPhase.ERROR,player.evidence.phase)
            assertNotNull(engine.control!!.failure);assertFalse(engine.control!!.request(true))
            player.stop();assertEquals(1,engine.stops)
            engine.terminal.complete(stopped().copy(status=UsbSilentStatus.FAILED))
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertEquals(IntegratedDirectPhase.ERROR,player.evidence.phase);scope.cancel() }
    }
}
