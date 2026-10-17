package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Media3 source owner, controlled engine completions; no JNI or physical USB access. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DirectSourceTransitionTest {
    private val a=song(1)
    private val b=song(2)
    private val c=song(3)
    private fun song(id: Long)=Song(id,"Track $id","Artist","Album",1,10000,
        Uri.parse("content://media/external/audio/media/$id"),"private","private",null)

    private class Run(val uri: Uri,val control: UsbFullPlaybackControl,val progress: (UsbFullProgress)->Unit) {
        val terminal=CompletableDeferred<UsbSilentTransportExperimentResult>()
        val stopped=CompletableDeferred<Unit>()
        lateinit var initialIntent: UsbFullControlIntent
        var submittedFrames=0L
        var confirmedSerial=0L
        fun emit(phase: UsbFullTransportPhase,position: Long=0,epoch: Long=0,serial: Long=0) {
            confirmedSerial=serial
            progress(UsbFullProgress(submittedFrames,480000,48000,phase,mapOf(
                "sourcePositionFrame" to position,"seekSourceEpoch" to epoch,
                "confirmedSeekSerial" to serial,"seekConfirmed" to epoch,"sourceSeekCapable" to 1L)))
        }
        fun submit(frames: Long) {
            // Mirrors native admission: stopped, paused and pending-seek mailboxes cannot submit.
            kotlin.check(!control.stopped && control.playWhenReady && control.intent().seekSerial<=confirmedSerial)
            submittedFrames+=frames
        }
    }

    private class Engine : FullUsbPlaybackEngineClient {
        val runs=mutableListOf<Run>()
        val started=Channel<Run>(Channel.UNLIMITED)
        var active=0
        var maximumActive=0
        var seekable=true
        var failureBeforeOwnership: String?=null
        override suspend fun runIntegratedFull(uri: Uri,control: UsbFullPlaybackControl,progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult {
            failureBeforeOwnership?.let { throw UsbIntegratedStartException(it) }
            val run=Run(uri,control,progress)
            runs+=run;active++;maximumActive=maxOf(active,maximumActive)
            try {
                // Production ordering: accepted source metadata, mailbox bind, then native run.
                progress(UsbFullProgress(0,480000,48000,UsbFullTransportPhase.STARTING,
                    mapOf("sourceSeekCapable" to if(seekable) 1L else 0L,"sourcePositionFrame" to 0L)))
                control.bind({true},{ run.stopped.complete(Unit) })
                run.initialIntent=control.intent()
                started.send(run)
                return withContext(NonCancellable) { run.terminal.await() }
            } finally { control.finish();active-- }
        }
        override fun stopIntegratedFull() { runs.lastOrNull()?.stopped?.complete(Unit) }
        suspend fun next(): Run=withTimeout(5000) { started.receive() }
    }

    private inner class Fixture {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val engine=Engine()
        var admissionFailure: String?=null
        var observer: ((IntegratedDirectPlaybackState)->Unit)?=null
        val events=mutableListOf<Pair<String?,DirectListeningEvent>>()
        val translator=DirectListeningEventTranslator().apply { activate() }
        val player=DirectUsbMediaPlayer(scope,engine,changed={ observer?.invoke(it) },librarySong={ id ->
            listOf(a,b,c).firstOrNull { it.id.toString()==id }
        },listeningChanged={ item,state,terminal ->
            val id=item?.listeningEvidence()?.itemInstanceId
            translator.observe(id,state,terminal).forEach { events+=id to it }
        },sourceAdmissionFailure={ admissionFailure })
        suspend fun start(song: Song=a,playing: Boolean=true): Run {
            val result=player.replaceSource(song.toPlayableMediaItem("original-${song.id}"),playing)
            val run=engine.next()
            run.emit(if(playing) UsbFullTransportPhase.PLAYING else UsbFullTransportPhase.PAUSED)
            assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
            return run
        }
        suspend fun close() {
            player.requestStop()
            engine.runs.forEach { it.terminal.complete(terminal()) }
            player.stopAndJoin();scope.cancel()
        }
    }

    private fun check(block: suspend (Fixture)->Unit)=runBlocking {
        withContext(Dispatchers.Main) {
            val f=Fixture()
            try { withTimeout(5000) { block(f) } } finally { f.close() }
        }
    }
    private fun terminal(safe: Boolean=true,status: UsbSilentStatus=UsbSilentStatus.CANCELLED)=
        UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=status,
            configuration=UsbAudioConfigurationResult(1,UsbAudioConfigurationResolution(),
                targetStatus=UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
                acClaim=UsbConfigurationAndroidOwnership(true,true),asClaim=UsbConfigurationAndroidOwnership(true,true),
                restorationStatus=if(safe) UsbConfigurationRestorationStatus.RESTORED else UsbConfigurationRestorationStatus.FAILED,
                asRelease=UsbConfigurationAndroidOwnership(true,true),acRelease=UsbConfigurationAndroidOwnership(true,true),
                closeStatus=UsbAudioCleanupStatus.SUCCEEDED),
            transport=UsbIsoTransportResult(1,2,mapOf("workerStopped" to 1L,"outstandingUrbsFinal" to 0L,"framesCompleted" to 0L)),
            originalFlacFdClosed=true,flacSource=UsbFlacSourceResult(mapOf("totalSamples" to 480000L,"sampleRate" to 48000L,"producerJoined" to 1L)))

    @Test fun sameFormatReplacementWaitsForCleanupAndUsesFreshRunAndOriginalEvidence()=check { f ->
        val old=f.start()
        val generation=f.player.evidence.generation
        val item=b.toPlayableMediaItem("selected-b")
        val replacement=f.player.replaceSource(item,true)
        old.stopped.await()
        assertFalse(replacement.isCompleted)
        assertEquals(a.id,f.player.evidence.libraryId)
        assertEquals("original-1",f.player.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertEquals(item.mediaId,f.player.evidence.sourceTransition?.mediaId)
        assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal())
        val next=f.engine.next()
        assertEquals(b.uri,next.uri);assertEquals(1,f.engine.maximumActive)
        assertTrue(f.player.evidence.enabled);assertTrue(f.player.evidence.generation>generation)
        assertEquals(item,f.player.currentMediaItem)
        assertFalse(f.player.isPlaying);assertFalse(replacement.isCompleted)
        next.emit(UsbFullTransportPhase.PLAYING)
        val ready=replacement.await()
        assertEquals(DirectSourceTransitionStatus.READY,ready.status)
        assertEquals(f.player.evidence.generation,ready.nativeGeneration)
        assertEquals(listOf(DirectListeningEvent.PLAYING,DirectListeningEvent.SUSPENDED,DirectListeningEvent.TRANSITION),
            f.events.filter { it.first=="original-1" }.map { it.second })
        assertEquals(listOf(DirectListeningEvent.PLAYING),f.events.filter { it.first=="selected-b" }.map { it.second })
        val accepted=f.player.evidence
        old.emit(UsbFullTransportPhase.PLAYING,100)
        assertEquals(accepted,f.player.evidence)
        assertFalse(old.control.request(true))
        assertFalse(f.player.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(f.player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }

    @Test fun playingAndPausedReplacementMatrixHonorsNativeIntentAndLaterPlay() {
        for(oldPlaying in listOf(false,true)) for(newPlaying in listOf(false,true)) check { f ->
            val old=f.start(playing=oldPlaying)
            val result=f.player.replaceSource(b.toPlayableMediaItem("matrix-b"),newPlaying)
            old.terminal.complete(terminal())
            val next=f.engine.next()
            assertEquals(newPlaying,next.initialIntent.play)
            assertEquals(0L,next.submittedFrames)
            if(!newPlaying) {
                try { next.submit(1);fail("Paused start admitted PCM") } catch(_: IllegalStateException) { }
                next.emit(UsbFullTransportPhase.PAUSED)
                assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
                assertFalse(f.player.isPlaying)
                assertFalse(f.events.any { it.first=="matrix-b" })
                f.player.play()
                assertTrue(next.control.playWhenReady)
            }
            next.submit(1);next.emit(UsbFullTransportPhase.PLAYING,1)
            assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
            assertTrue(f.player.isPlaying);assertEquals(b.id,f.player.evidence.libraryId)
            assertEquals(2,f.engine.runs.size);assertEquals(1,f.engine.maximumActive)
        }
    }

    @Test fun nonzeroStartWithBothIntentsKeepsPositionUnconfirmedAndAudioHeld() {
        for(playing in listOf(false,true)) check { f ->
            val old=f.start()
            val result=f.player.replaceSource(b.toPlayableMediaItem("position-b"),playing,3000)
            old.terminal.complete(terminal())
            val next=f.engine.next()
            val serial=next.initialIntent.seekSerial
            assertFalse(next.initialIntent.play);assertEquals(144000L,next.initialIntent.targetFrame)
            assertEquals(0L,f.player.currentPosition);assertFalse(result.isCompleted)
            try { next.submit(1);fail("Initial seek admitted PCM") } catch(_: IllegalStateException) { }
            next.emit(UsbFullTransportPhase.SEEK_PRIMING)
            assertEquals(0L,f.player.currentPosition)
            assertFalse(f.events.any { it.first=="position-b" })
            next.emit(UsbFullTransportPhase.PAUSED,144000,1,serial)
            assertEquals(3000L,f.player.currentPosition)
            assertEquals(playing,next.control.playWhenReady)
            if(playing) {
                assertFalse(result.isCompleted);assertFalse(f.player.isPlaying)
                next.submit(1);next.emit(UsbFullTransportPhase.PLAYING,144001,1,serial)
            }
            assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
            assertEquals(playing,f.player.isPlaying)
        }
    }

    @Test fun publicSingleItemStartingPositionUsesTheSameConfirmedSeekContract()=check { f ->
        f.player.setMediaItem(a.toPlayableMediaItem("public-a"),3000)
        f.player.prepare()
        val run=f.engine.next()
        assertFalse(run.initialIntent.play);assertEquals(144000L,run.initialIntent.targetFrame)
        assertEquals(0L,f.player.currentPosition)
        run.emit(UsbFullTransportPhase.PAUSED,144000,1,run.initialIntent.seekSerial)
        assertEquals(3000L,f.player.currentPosition);assertTrue(run.control.playWhenReady)
    }

    @Test fun latestAcceptedTargetWinsDuringSlowCleanupWithoutPreparingObsoleteTargets()=check { f ->
        val old=f.start()
        val first=f.player.replaceSource(b.toPlayableMediaItem(),true,2000)
        val last=f.player.replaceSource(c.toPlayableMediaItem("latest-c"),false,3000)
        assertEquals(DirectSourceTransitionStatus.SUPERSEDED,first.await().status)
        assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal())
        val next=f.engine.next()
        assertEquals(c.uri,next.uri);assertEquals(2,f.engine.runs.size)
        assertFalse(next.initialIntent.play);assertEquals(144000L,next.initialIntent.targetFrame)
        next.emit(UsbFullTransportPhase.PAUSED,144000,1,next.initialIntent.seekSerial)
        assertEquals(DirectSourceTransitionStatus.READY,last.await().status)
    }

    @Test fun supersededInitialSeekCannotPublishIntoTheSuccessorOrSkipItsCleanup()=check { f ->
        val old=f.start()
        val first=f.player.replaceSource(b.toPlayableMediaItem("pending-b"),true,3000)
        old.terminal.complete(terminal())
        val preparing=f.engine.next()
        val last=f.player.replaceSource(c.toPlayableMediaItem("successor-c"),false)
        assertEquals(DirectSourceTransitionStatus.SUPERSEDED,first.await().status)
        val stopping=f.player.evidence
        preparing.emit(UsbFullTransportPhase.PAUSED,144000,1,preparing.initialIntent.seekSerial)
        assertEquals(stopping,f.player.evidence);assertEquals(2,f.engine.runs.size)
        preparing.terminal.complete(terminal())
        val next=f.engine.next()
        next.emit(UsbFullTransportPhase.PAUSED)
        assertEquals(DirectSourceTransitionStatus.READY,last.await().status)
        val accepted=f.player.evidence
        preparing.emit(UsbFullTransportPhase.PAUSED,144000,1,preparing.initialIntent.seekSerial)
        assertEquals(accepted,f.player.evidence);assertEquals(0L,f.player.currentPosition)
        assertEquals(1,f.engine.maximumActive)
    }

    @Test fun repeatedRequestsDuringOneCleanupRetainOnlyTheFinalIntentAndPosition()=check { f ->
        val old=f.start()
        val requests=(0 until 20).map { index ->
            f.player.replaceSource((if(index%2==0) b else c).toPlayableMediaItem("request-$index"),
                playing=index%2==0,startPositionMs=3000)
        }
        requests.dropLast(1).forEach { assertEquals(DirectSourceTransitionStatus.SUPERSEDED,it.await().status) }
        assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal())
        val next=f.engine.next();assertEquals(c.uri,next.uri)
        assertFalse(next.initialIntent.play);assertEquals(144000L,next.initialIntent.targetFrame)
        next.emit(UsbFullTransportPhase.PAUSED,144000,1,next.initialIntent.seekSerial)
        assertEquals(DirectSourceTransitionStatus.READY,requests.last().await().status)
        assertEquals("request-19",f.player.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertEquals(2,f.engine.runs.size);assertEquals(1,f.engine.maximumActive)
    }

    @Test fun unsafeCleanupCannotBeErasedByInternalOrPublicSelection()=check { f ->
        val old=f.start()
        val result=f.player.replaceSource(b.toPlayableMediaItem(),true)
        old.terminal.complete(terminal(safe=false))
        assertEquals(DirectSourceTransitionStatus.FAILED,result.await().status)
        assertEquals(1,f.engine.runs.size);assertFalse(f.player.evidence.cleanupSafe)
        val failed=f.player.evidence
        f.player.setMediaItem(c.toPlayableMediaItem());f.player.prepare()
        assertEquals(failed.generation,f.player.evidence.generation)
        assertEquals(a.id,f.player.evidence.libraryId);assertFalse(f.player.evidence.cleanupSafe)
        assertEquals(failed.engine,f.player.evidence.engine)
        assertEquals(DirectSourceTransitionStatus.REJECTED,f.player.replaceSource(c.toPlayableMediaItem(),true).await().status)
        old.emit(UsbFullTransportPhase.PLAYING)
        assertFalse(f.player.evidence.cleanupSafe);assertEquals(1,f.engine.runs.size)
    }

    @Test fun nonseekableReplacementReportsFailedTargetAndAllowsLaterSafeOperation()=check { f ->
        val old=f.start()
        f.engine.seekable=false
        val result=f.player.replaceSource(b.toPlayableMediaItem("failed-b"),true,3000)
        old.terminal.complete(terminal())
        val failed=f.engine.next()
        assertTrue(failed.control.stopped);assertEquals(0L,failed.submittedFrames)
        failed.terminal.complete(terminal(status=UsbSilentStatus.FAILED))
        assertEquals(DirectSourceTransitionStatus.FAILED,result.await().status)
        assertEquals("DIRECT_INITIAL_POSITION_UNSUPPORTED",f.player.evidence.error)
        assertEquals(b.id,f.player.evidence.libraryId);assertFalse(f.player.isPlaying)
        assertTrue(f.player.evidence.enabled);assertTrue(f.player.evidence.cleanupSafe)
        assertNotNull(f.player.evidence.engine)
        f.engine.seekable=true
        val nextResult=f.player.replaceSource(c.toPlayableMediaItem(),false)
        val next=f.engine.next();next.emit(UsbFullTransportPhase.PAUSED)
        assertEquals(DirectSourceTransitionStatus.READY,nextResult.await().status)
    }

    @Test fun initialSeekTransportFailureNeverReportsReadinessOrNaturalCompletion()=check { f ->
        val result=f.player.replaceSource(b.toPlayableMediaItem("seek-failure"),true,3000)
        val run=f.engine.next()
        run.emit(UsbFullTransportPhase.FAILED)
        assertFalse(result.isCompleted);assertEquals(0L,f.player.currentPosition)
        run.terminal.complete(terminal(status=UsbSilentStatus.USB_TRANSPORT_FAILED))
        assertEquals(DirectSourceTransitionStatus.FAILED,result.await().status)
        assertEquals(IntegratedDirectPhase.ERROR,f.player.evidence.phase)
        assertEquals("DIRECT_TRANSPORT_FAILED",f.player.evidence.error)
        assertFalse(f.events.any { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(1,f.engine.runs.size)
    }

    @Test fun stopDuringPreparationCancelsReplacementAndStillWaitsForJoinedResult()=check { f ->
        val result=f.player.replaceSource(b.toPlayableMediaItem(),false,3000)
        val preparing=f.engine.next()
        f.player.stop()
        assertEquals(DirectSourceTransitionStatus.CANCELLED,result.await().status)
        val joined=f.scope.async { f.player.stopAndJoin() }
        yield();assertFalse(joined.isCompleted)
        preparing.terminal.complete(terminal())
        assertTrue(joined.await());assertEquals(1,f.engine.runs.size)
        assertFalse(f.events.any { it.second==DirectListeningEvent.PLAYING })
    }

    @Test fun authorityLossDuringCleanupPreventsReplacementAdmission()=check { f ->
        val old=f.start()
        val result=f.player.replaceSource(b.toPlayableMediaItem(),true)
        f.admissionFailure="DIRECT_USB_SELECTION_CHANGED"
        old.terminal.complete(terminal())
        assertEquals("DIRECT_USB_SELECTION_CHANGED",result.await().error)
        assertEquals(a.id,f.player.evidence.libraryId);assertEquals(1,f.engine.runs.size)
    }

    @Test fun cancellingPendingCallerCannotAbandonCleanupOrStartItsTarget()=check { f ->
        val old=f.start()
        val result=f.player.replaceSource(b.toPlayableMediaItem(),true)
        result.cancel()
        old.terminal.complete(terminal())
        assertTrue(f.player.stopAndJoin())
        assertEquals(1,f.engine.runs.size)
        assertEquals(DirectListeningEvent.STOPPED,f.events.last().second)
    }

    @Test fun unknownSourceIsRejectedBeforeDisturbingCurrentPlayback()=check { f ->
        val old=f.start()
        val before=f.player.evidence
        val rejected=f.player.replaceSource(song(99).toPlayableMediaItem(),true)
        assertEquals(DirectSourceTransitionStatus.REJECTED,rejected.await().status)
        assertEquals("UNSUPPORTED_TRACK",rejected.await().error)
        assertEquals(before,f.player.evidence);assertFalse(old.stopped.isCompleted)
        val missingIdentity=MediaItem.Builder().setMediaId(b.id.toString()).build()
        assertEquals("DIRECT_SOURCE_IDENTITY_MISSING",f.player.replaceSource(missingIdentity,true).await().error)
        assertEquals(before,f.player.evidence);assertFalse(old.stopped.isCompleted)
    }

    @Test fun failedStartupAfterCleanupRetainsFailedTargetWithoutRestartOrFallback()=check { f ->
        val old=f.start()
        f.engine.failureBeforeOwnership="DIRECT_USB_CONFIGURATION_FAILED"
        val result=f.player.replaceSource(b.toPlayableMediaItem("startup-failure"),true)
        old.terminal.complete(terminal())
        assertEquals(DirectSourceTransitionStatus.FAILED,result.await().status)
        assertEquals(b.id,f.player.evidence.libraryId)
        assertEquals("DIRECT_USB_CONFIGURATION_FAILED",f.player.evidence.error)
        assertTrue(f.player.evidence.cleanupSafe);assertTrue(f.player.evidence.enabled)
        assertFalse(f.player.isPlaying);assertEquals(1,f.engine.runs.size)
        f.engine.failureBeforeOwnership=null
        val recovery=f.player.replaceSource(c.toPlayableMediaItem(),false)
        val next=f.engine.next();assertEquals(c.uri,next.uri)
        next.emit(UsbFullTransportPhase.PAUSED)
        assertEquals(DirectSourceTransitionStatus.READY,recovery.await().status)
    }

    @Test fun rejectedLaterRequestDoesNotSupersedeAnAlreadyAcceptedPendingTarget()=check { f ->
        val old=f.start()
        val pending=f.player.replaceSource(b.toPlayableMediaItem(),false)
        val rejected=f.player.replaceSource(song(99).toPlayableMediaItem(),true)
        assertEquals(DirectSourceTransitionStatus.REJECTED,rejected.await().status)
        assertFalse(pending.isCompleted)
        old.terminal.complete(terminal())
        val next=f.engine.next();assertEquals(b.uri,next.uri)
        next.emit(UsbFullTransportPhase.PAUSED)
        assertEquals(DirectSourceTransitionStatus.READY,pending.await().status)
    }

    @Test fun reentrantSupersessionBeforeEngineStartupNeverPreparesTheObsoleteSource()=check { f ->
        val old=f.start()
        var last: Deferred<DirectSourceTransitionResult>?=null
        var superseded=false
        f.observer={ state ->
            if(state.libraryId==b.id && state.phase==IntegratedDirectPhase.PREPARING && !superseded) {
                superseded=true
                last=f.player.replaceSource(c.toPlayableMediaItem(),false)
            }
        }
        val first=f.player.replaceSource(b.toPlayableMediaItem(),true)
        old.terminal.complete(terminal())
        val next=f.engine.next();assertEquals(c.uri,next.uri)
        assertEquals(DirectSourceTransitionStatus.SUPERSEDED,first.await().status)
        assertEquals(listOf(a.uri,c.uri),f.engine.runs.map { it.uri })
        next.emit(UsbFullTransportPhase.PAUSED)
        assertEquals(DirectSourceTransitionStatus.READY,requireNotNull(last).await().status)
    }

    @Test fun nativeFailureWhileReplacementWaitsRetainsErrorHistoryAndDoesNotAutoAdvance()=check { f ->
        val old=f.start()
        val result=f.player.replaceSource(b.toPlayableMediaItem("after-error"),false)
        old.terminal.complete(terminal(status=UsbSilentStatus.USB_TRANSPORT_FAILED))
        val next=f.engine.next();next.emit(UsbFullTransportPhase.PAUSED)
        assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
        assertEquals(DirectListeningEvent.ERROR,f.events.last().second)
        assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION || it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(2,f.engine.runs.size)
    }

    @Test fun sameItemReplayHasFreshGenerationAndOneFinalizationBeforeNextAttempt()=check { f ->
        val old=f.start()
        val generation=f.player.evidence.generation
        val result=f.player.replaceSource(a.toPlayableMediaItem("original-1"),true)
        old.terminal.complete(terminal())
        val next=f.engine.next();next.emit(UsbFullTransportPhase.PLAYING)
        assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
        assertTrue(f.player.evidence.generation>generation)
        assertEquals(listOf(DirectListeningEvent.PLAYING,DirectListeningEvent.SUSPENDED,
            DirectListeningEvent.TRANSITION,DirectListeningEvent.PLAYING),f.events.map { it.second })
        assertTrue(f.events.all { it.first=="original-1" })
    }
}
