package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real Room/queue/routing and, in the readiness test, real Android Media3 output. USB is simulated. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DirectDetachRecoveryIntegrationTest : DirectQueueTestFixture() {
    private fun detached(f: Fixture,run: Run): UsbSilentTransportExperimentResult {
        val base=terminal(run,false,UsbSilentStatus.NON_AUTHORITATIVE)
        val result=base.copy(generation=f.token.generation,nativeExecutionJoined=true,connectionCloseRequired=true,
            outstandingAfterConnectionClose=0,
            configuration=base.configuration!!.copy(generation=f.token.generation,
                accessFailure=UsbClockQueryStatus.DEVICE_DISCONNECTED,restorationStatus=UsbConfigurationRestorationStatus.INDETERMINATE),
            transport=base.transport!!.copy(generation=f.token.generation),
            flacSource=base.flacSource!!.copy(counters=base.flacSource.counters+mapOf(
                "generation" to f.token.generation,"streamGeneration" to base.streamGeneration,
                "producerStopped" to 1L,"decoderReleased" to 1L,"nativeFlacFdClosed" to 1L,"queueReleased" to 1L)))
        val proof=checkNotNull(UsbDetachedResourceRelease.verified(checkNotNull(f.reservation.owner),f.token,result,true,
            sourceAbsent=false,inputAbsent=false,connectionAbsent=false,nativeRegistriesIdle=true))
        return result.copy(detachedResourceRelease=proof)
    }
    private fun detach(f: Fixture) {
        f.gate.select(null);f.attachmentFailure="DIRECT_USB_DEVICE_UNAVAILABLE"
        repeat(2) { f.route.usbSelectionChanged() }
    }
    @Test fun detachWhileReplacementPendingCancelsTargetAndRestoresFrozenQueueWithoutChangingSavedDirectQueue()=check { f ->
        f.withController { controller ->
            val first=f.startAt()
            controller.playSelectedSong(b,listOf(b,c))
            first.stopped.await();first.terminal.complete(terminal(first))
            val direct=f.engine.next();f.confirm(direct)
            assertTrue(withTimeout(5000) { f.contextResults.receive() })
            val directId=checkNotNull(f.coordinator.getActiveQueueId())
            val savedBefore=checkNotNull(f.repository.loadQueue(directId))
            val proof=detached(f,direct)
            val pending=f.route.replaceDirectSource(c.toPlayableMediaItem("pending"),true)
            direct.stopped.await();detach(f)
            assertEquals(DirectSourceTransitionStatus.CANCELLED,pending.await().status)
            assertEquals(0,f.recreations);assertNotNull(f.reservation.owner)
            direct.terminal.complete(proof);f.settled();f.assertRestoredAndroid()
            assertEquals(0,f.engine.active);assertEquals(1,f.engine.maximumActive);assertEquals(2,f.engine.runs.size)
            assertEquals(1,f.recreations);assertNull(f.reservation.owner)
            assertFalse(f.route.shuffleModeEnabled);assertEquals(Player.REPEAT_MODE_OFF,f.route.repeatMode)
            assertEquals(savedBefore.entries,f.repository.loadQueue(directId)?.entries)
            assertEquals(savedBefore.queue.currentEntryId,f.repository.loadQueue(directId)?.queue?.currentEntryId)
            assertEquals(DirectListeningEvent.ERROR,f.events.last().second)
            assertFalse(f.events.any { it.second==DirectListeningEvent.NATURAL_END })
        }
    }
    @Test fun detachDuringAndroidSwitchWaitsForLateDisposalThenRestoresOnlyOnce()=check { f ->
        val run=f.startAt();val proof=detached(f,run)
        f.route.selectDirect(false);run.stopped.await();detach(f)
        assertEquals(0,f.recreations);assertNotNull(f.reservation.owner)
        assertEquals(1,f.engine.active)
        run.terminal.complete(proof);f.settled();f.assertRestoredAndroid()
        assertEquals(0,f.engine.active);assertNull(f.reservation.owner)
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackBridge.reportState)
        assertTrue(report.contains("authoritative backend=NORMAL"))
        assertTrue(report.contains("underlying Session 11 engine result=NON_AUTHORITATIVE"))
        assertTrue(report.contains("restoration=INDETERMINATE"))
        repeat(3) { f.route.selectDirect(false);f.route.usbSelectionChanged() }
        assertEquals(1,f.recreations)
    }
    @Test fun shutdownDuringSuspendedQueueCheckpointKeepsReleasedOwnerAndSkipsAndroidRecreation()=check { f ->
        val checkpoint=CompletableDeferred<Unit>();val proceed=CompletableDeferred<Unit>()
        f.route.attachQueueOutputObserver(object : PlaybackQueueOutputObserver by f.coordinator {
            override suspend fun beforeAndroidRestore() {
                checkpoint.complete(Unit);proceed.await();f.coordinator.beforeAndroidRestore()
            }
        })
        try {
            val run=f.startAt();val proof=detached(f,run)
            detach(f);run.stopped.await();run.terminal.complete(proof)
            withTimeout(5000) { checkpoint.await() }
            assertNull(f.reservation.owner);assertEquals(0,f.engine.active);assertEquals(0,f.recreations)
            val shutdown=f.scope.async(start=CoroutineStart.UNDISPATCHED) { f.route.shutdown() }
            assertFalse(shutdown.isCompleted)
            proceed.complete(Unit)
            assertTrue(withTimeout(5000) { shutdown.await() });assertEquals(0,f.recreations)
        } finally { proceed.complete(Unit) }
    }
    @Test fun recoveredAndroidBackendActuallyPreparesReportsDurationRendersAndAdvances() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val wave=File.createTempFile("detach-recovery-",".wav",context.cacheDir)
        // Four seconds of low-level PCM, generated only by the test. This adds no Direct format support.
        val rate=44100;val samples=rate*4;val bytes=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate*2).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(samples*2)
        repeat(samples) { bytes.putShort(if(it%100<50) 512.toShort() else (-512).toShort()) }
        wave.writeBytes(bytes.array())
        lateinit var android: ExoPlayer
        val rendered=CompletableDeferred<Unit>()
        try {
            check(androidPlayerFactory={
                ExoPlayer.Builder(context).setLooper(Looper.getMainLooper()).build().also { player ->
                    android=player
                    player.addAnalyticsListener(object : AnalyticsListener {
                        override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime,playoutStartSystemTimeMs: Long) {
                            rendered.complete(Unit)
                        }
                    })
                }
            }) { f ->
                val run=f.startAt();val proof=detached(f,run)
                detach(f);run.stopped.await()
                assertEquals(0,f.recreations);assertEquals(1,f.engine.active)
                run.terminal.complete(proof);f.settled();f.assertRestoredAndroid()
                f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM) && f.route.isCommandAvailable(Player.COMMAND_PREPARE) }
                assertFalse(f.route.playWhenReady);assertEquals(1,f.recreations)
                f.route.setSmoothPlaybackEnabled(false)
                val local=a.copy(id=100,duration=4000,uri=Uri.fromFile(wave))
                PlaybackLibraryBridge.updateSongs(listOf(a,b,c,local))
                f.route.setMediaItems(listOf(local.toPlayableMediaItem("wave-first"),local.toPlayableMediaItem("wave-second")))
                f.route.prepare()
                f.forwarding { f.route.playbackState==Player.STATE_READY || f.route.playerError!=null }
                assertNull(f.route.playerError);assertEquals(4000L,f.route.duration)
                val advanced=CompletableDeferred<Unit>()
                android.createMessage { _, _ -> advanced.complete(Unit) }.setLooper(Looper.getMainLooper()).setPosition(500).send()
                f.route.play()
                withTimeout(5000) { rendered.await();advanced.await() }
                assertTrue(f.route.currentPosition>=500);assertEquals(0,f.engine.active)
                f.route.pause();f.forwarding { !f.route.playWhenReady }
                f.route.seekTo(1000);f.forwarding { f.route.currentPosition==1000L }
                f.route.seekToNextMediaItem()
                f.forwarding { f.route.currentMediaItemIndex==1 && f.route.playbackState==Player.STATE_READY && f.route.duration==4000L }
                assertEquals(4000L,f.route.duration);assertEquals(1,f.recreations)
            }
        } finally { wave.delete() }
    }
}
