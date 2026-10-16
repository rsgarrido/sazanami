package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.PlaybackQueueWorkingOutput
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import io.github.rsgarrido.sazanami.data.workingOutput
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
    private suspend fun reconnect(f: Fixture) {
        assertEquals(0,f.engine.active);assertNull(f.reservation.owner)
        f.token=f.gate.select(f.identity)!!;f.attachmentFailure=null
        f.route.selectDirect(true);f.settled()
        assertTrue("Reactivation failed: ${f.route.directState.activation?.failure}",f.route.directState.confirmedDirect)
        assertNull(f.route.directState.activation?.failure)
        assertEquals(UsbActivationReservation.PROTECTED,f.route.directState.activation?.reservation)
        assertNotNull(f.reservation.owner)
    }
    private suspend fun playRecoveredAndroid(f: Fixture) {
        assertFalse(f.route.isDirectSelected);assertEquals(0,f.engine.active)
        f.route.setSmoothPlaybackEnabled(false);f.route.prepare();f.route.play()
        f.forwarding { f.route.playbackState==Player.STATE_READY && f.route.isPlaying }
        assertEquals(0,f.engine.active)
        f.route.pause();f.forwarding { !f.route.playWhenReady }
    }
    private suspend fun startAdoptedTimeline(f: Fixture,playing: Boolean): Run {
        val request=f.route.replaceDirectTimeline(f.items,1,playing,0,false,Player.REPEAT_MODE_OFF)
        val run=f.engine.next()
        assertFalse(request.isCompleted);assertFalse(f.route.directQueueCheckpointAllowed)
        f.confirm(run)
        assertEquals(DirectSourceTransitionStatus.READY,request.await().status)
        assertEquals(if(playing) IntegratedDirectPhase.PLAYING else IntegratedDirectPhase.PAUSED,f.route.directState.phase)
        return run
    }

    @Test fun sharedOwnerReorderRestoresUnassignedAndroidAndAllowsThreeDetachReactivations() {
        for(playing in listOf(true,false)) check(playing) { f ->
            val saved=f.saved()
            var run=f.startAt(index=1)
            // Direct initially adopts Android's occurrence IDs. Moving the non-first current
            // occurrence to the front naturally edits their shared persisted owner.
            f.coordinator.onDirectShuffle(true)
            val reordered=checkNotNull(f.repository.loadQueue(f.originalId))
            assertEquals("b",reordered.entries.first().entryId)
            assertNotEquals(f.items.map { it.listeningEvidence()!!.itemInstanceId },reordered.entries.map { it.entryId })
            var protectedOwner: PlaybackQueueWithEntries?=null
            repeat(3) { cycle ->
                val proof=detached(f,run)
                detach(f);run.stopped.await()
                assertEquals(cycle,f.recreations);assertNotNull(f.reservation.owner)
                run.terminal.complete(proof);f.settled()
                f.forwarding { f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId=="a-first" }
                assertFalse(f.route.isDirectSelected);assertEquals(2000L,f.route.currentPosition)
                assertNull(f.coordinator.getActiveQueueId());assertNull(f.repository.getActiveQueueId())
                assertNull(f.coordinator.captureActiveQueueSnapshot())
                val restored=checkNotNull(f.runtime.captureSnapshot())
                assertEquals(listOf("a-first","b","a-second"),restored.entries.map { it.entryId })
                assertEquals("a-first",restored.currentEntryId);assertEquals(2000L,restored.currentPositionMs)
                assertNull(f.coordinator.persistActiveQueueSnapshot())
                assertEquals(reordered.entries,f.repository.loadQueue(f.originalId)?.entries)
                if(protectedOwner==null) protectedOwner=f.repository.loadQueue(f.originalId)
                else assertEquals(protectedOwner,f.repository.loadQueue(f.originalId))
                assertEquals(saved,f.repository.loadQueue(saved.queue.queueId))
                assertEquals(2,f.repository.listQueues().size)
                assertEquals(1,f.repository.listQueues().count { it.workingOutput==null })
                assertEquals(cycle+1,f.recreations);assertNull(f.reservation.owner)
                playRecoveredAndroid(f)
                assertNull(f.coordinator.persistActiveQueueSnapshot())
                reconnect(f)
                assertNull(f.coordinator.getActiveQueueId())
                assertNull(f.coordinator.persistActiveQueueSnapshot())
                assertEquals(protectedOwner,f.repository.loadQueue(f.originalId))
                run=startAdoptedTimeline(f,playing)
                // A native-acknowledged run of the old unassigned IDs still cannot claim them.
                assertNull(f.coordinator.persistActiveQueueSnapshot())
                assertEquals(protectedOwner,f.repository.loadQueue(f.originalId))
            }
            assertEquals(1,f.engine.maximumActive)
        }
    }

    @Test fun repeatedDetachReactivationReusesWorkingOwnerAndProtectsSavedAndAndroidQueues() {
        for(playing in listOf(true,false)) check(playing) { f -> f.withController { controller ->
            val saved=f.saved()
            var previous: Run?=f.startAt()
            var workingId: String?=null
            repeat(4) { cycle ->
                controller.playSelectedSong(if(cycle%2==0) b else c,listOf(a,b,c,a))
                previous?.let { old -> old.stopped.await();old.terminal.complete(terminal(old)) }
                val run=f.engine.next();f.confirm(run)
                assertTrue(withTimeout(5000) { f.contextResults.receive() })
                val acceptedId=checkNotNull(f.coordinator.getActiveQueueId())
                if(workingId==null) workingId=acceptedId else assertEquals(workingId,acceptedId)
                assertEquals(PlaybackQueueWorkingOutput.DIRECT,f.repository.loadQueue(acceptedId)?.queue?.workingOutput)
                if(!playing) {
                    f.route.pauseDirectPlayback();run.emit(UsbFullTransportPhase.PAUSED)
                    f.forwarding { !f.route.playWhenReady && f.route.playbackState==Player.STATE_READY }
                    assertEquals(IntegratedDirectPhase.PAUSED,f.route.directState.phase)
                } else assertEquals(IntegratedDirectPhase.PLAYING,f.route.directState.phase)
                f.coordinator.persistActiveQueueSnapshot()
                val directCheckpoint=checkNotNull(f.repository.loadQueue(acceptedId))
                assertEquals(saved,f.repository.loadQueue(saved.queue.queueId))
                assertEquals(3,f.repository.listQueues().size)
                assertEquals(1,f.repository.listQueues().count { it.workingOutput==null })
                if(cycle<3) {
                    val proof=detached(f,run)
                    detach(f);run.stopped.await();run.terminal.complete(proof)
                    f.settled();f.assertRestoredAndroid()
                    assertEquals(directCheckpoint.entries,f.repository.loadQueue(acceptedId)?.entries)
                    assertEquals(directCheckpoint.queue.currentEntryId,f.repository.loadQueue(acceptedId)?.queue?.currentEntryId)
                    assertEquals(directCheckpoint.queue.currentPositionMs,f.repository.loadQueue(acceptedId)?.queue?.currentPositionMs)
                    playRecoveredAndroid(f);reconnect(f)
                    assertEquals(cycle+1,f.recreations)
                    previous=null
                }
            }
            assertEquals(1,f.engine.maximumActive)
        } }
    }

    @Test fun checkpointOverlappingDetachDrainsBeforeRestorationAndDoesNotBlockReactivation()=check { f ->
        val run=f.startAt()
        val entered=CompletableDeferred<Unit>();val proceed=CompletableDeferred<Unit>()
        f.beforeCheckpoint={ entered.complete(Unit);proceed.await() }
        try {
            val checkpoint=f.scope.async { f.coordinator.persistActiveQueueSnapshot() }
            withTimeout(5000) { entered.await() }
            val proof=detached(f,run)
            detach(f);run.stopped.await();run.terminal.complete(proof)
            // The checkpoint owns the coordinator mutex; verified native join must not allow
            // Android recreation to skip that persistence/restoration boundary.
            withTimeout(5000) { while(f.engine.active!=0 || f.reservation.owner!=null) yield() }
            assertEquals(0,f.recreations)
            f.beforeCheckpoint=null;proceed.complete(Unit);checkpoint.await()
            f.settled();f.assertRestoredAndroid()
            playRecoveredAndroid(f);reconnect(f)
            startAdoptedTimeline(f,true)
            assertEquals(1,f.engine.maximumActive)
        } finally { f.beforeCheckpoint=null;proceed.complete(Unit) }
    }

    @Test fun genuinelyPendingAndroidQueueActivationRejectsRepeatedDirectAttemptsUntilItCommits()=check { f ->
        val saved=f.saved();val run=f.startAt();val proof=detached(f,run)
        detach(f);run.stopped.await();run.terminal.complete(proof)
        f.settled();f.assertRestoredAndroid()
        f.token=f.gate.select(f.identity)!!;f.attachmentFailure=null
        val entered=CompletableDeferred<Unit>();val proceed=CompletableDeferred<Unit>()
        f.beforeCheckpoint={ queueId -> if(queueId==saved.queue.queueId) { entered.complete(Unit);proceed.await() } }
        try {
            val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
            withTimeout(5000) { entered.await() }
            repeat(2) {
                f.route.selectDirect(true)
                assertFalse(f.route.isDirectSelected)
                assertEquals("DIRECT_QUEUE_TRANSACTION_PENDING",f.route.directState.activation?.failure)
                assertEquals(UsbActivationStage.SNAPSHOT,f.route.directState.activation?.failureStage)
                assertEquals(UsbActivationReservation.RELEASED,f.route.directState.activation?.reservation)
                assertNull(f.reservation.owner);assertEquals(0,f.engine.active)
                assertFalse(activation.isCompleted)
            }
            f.beforeCheckpoint=null;proceed.complete(Unit)
            assertTrue(activation.await())
            f.forwarding { f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId==saved.queue.currentEntryId }
            assertEquals(saved.queue.queueId,f.coordinator.getActiveQueueId())
            reconnect(f)
            assertEquals(saved.queue.queueId,f.coordinator.getActiveQueueId())
            assertEquals(2,f.repository.listQueues().size);assertEquals(1,f.recreations)
        } finally { f.beforeCheckpoint=null;proceed.complete(Unit) }
    }

    @Test fun supersededLibraryPreparationDetachesThenNewPendingSelectionKeepsItsOwnership()=check { f -> f.withController { controller ->
        val first=f.startAt()
        controller.playSelectedSong(b,listOf(b,a))
        first.stopped.await();first.terminal.complete(terminal(first))
        val superseded=f.engine.next()
        assertFalse(f.coordinator.onDirectRequested())
        controller.playSelectedSong(c,listOf(c,a))
        superseded.stopped.await();superseded.terminal.complete(terminal(superseded))
        val preparing=f.engine.next()
        assertFalse(f.coordinator.onDirectRequested())
        val proof=detached(f,preparing)
        detach(f);preparing.stopped.await();preparing.terminal.complete(proof)
        assertFalse(withTimeout(5000) { f.contextResults.receive() })
        assertFalse(withTimeout(5000) { f.contextResults.receive() })
        f.settled();f.assertRestoredAndroid()
        assertEquals(1,f.repository.listQueues().size)
        playRecoveredAndroid(f);reconnect(f)
        controller.playSelectedSong(b,listOf(b,c))
        val latest=f.engine.next()
        superseded.emit(UsbFullTransportPhase.PLAYING);preparing.emit(UsbFullTransportPhase.PLAYING)
        assertFalse(f.route.directQueueCheckpointAllowed)
        assertFalse(f.coordinator.onDirectRequested())
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertTrue(f.contextResults.tryReceive().isFailure)
        f.confirm(latest)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        assertEquals(PlaybackQueueWorkingOutput.DIRECT,
            f.repository.loadQueue(checkNotNull(f.coordinator.getActiveQueueId()))?.queue?.workingOutput)
        assertEquals(2,f.repository.listQueues().size);assertEquals(1,f.engine.maximumActive)
    } }
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
