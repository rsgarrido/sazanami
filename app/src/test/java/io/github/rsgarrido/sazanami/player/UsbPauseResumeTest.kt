package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.player.usb.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/** Policy/projection/mailbox tests only. No JNI, USB, audio, service or hardware execution. */
class UsbPauseResumeTest {
    @Test fun livePausedReportUsesCoherentNativeEvidenceBeforeTheFinalResultExists() {
        val counters=UsbIsoNativeStats.controlFields.associateWith { 0L }+mapOf(
            "playbackTransportState" to 3L,"pauseRequests" to 1L,"pauseConfirmed" to 1L,"lastPauseOutcome" to 3L,
            "firstPauseSourceFrame" to 48000L,"lastPauseSourceFrame" to 48000L,"confirmedPausedFrame" to 48100L,
            "intentionalPausedUs" to 12345000L,"pauseDrainOutcome" to 1L,"pauseSourceContinuity" to 1L)
        UsbIsoNativeStats.validateControl(counters)
        val state=integratedProgressState(IntegratedDirectPlaybackState(phase=IntegratedDirectPhase.PAUSING),
            UsbFullProgress(48100,240000,48000,UsbFullTransportPhase.PAUSED,counters),false)
        val report=formatIntegratedDirectPlayback(state)
        for(expected in listOf("native transport state=PAUSED","Pause requests=1; confirmed=1",
            "last Pause outcome=CONFIRMED","confirmed paused frame=48100","intentional paused duration us=12345000",
            "pending source-bearing OUT at confirmed pause=0","drain outcome=1","production Bit-perfect: NOT YET COMPLETE"))
            assertTrue(expected,report.contains(expected))
        assertNull(state.engine);assertEquals(counters,state.nativePauseCounters)
        assertFalse(report.contains("FULL_FLAC_USB_TRANSPORT_VERIFIED"))
    }
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path)).first { Files.isRegularFile(it) }.toFile().readText()
    @Test fun workerDrainAndResumeGuardsProtectTheActualUsbAndSourceBoundaries() {
        // Source boundary checks supplement synthetic semantics; they do NOT execute USBfs or a decoder.
        val native=source("cpp/usb_iso_transport.cpp")
        val step=native.substringAfter("bool playbackStep(").substringBefore("bool fullSubmitted(")
        val drain=step.substringAfter("if(playback.phaseIs(PlaybackPhase::Pausing))").substringBefore("if(playback.phaseIs(PlaybackPhase::Paused))")
        assertTrue(drain.indexOf("reapOne()")<drain.indexOf("confirmPause("))
        assertTrue(drain.indexOf("!outstanding(false) && !outstanding(true)")<drain.indexOf("confirmPause("))
        assertTrue(drain.contains("q.head.load(),q.prepared"));assertTrue(drain.contains("q.consumedHash,submittedPrefixHash"))
        assertFalse(drain.contains("submit("))
        for(forbidden in listOf("cancel(","prefix->finish(","prefix.reset(","queue.requestStop(")) assertFalse(forbidden,step.contains(forbidden))
        val paused=step.substringAfter("if(playback.phaseIs(PlaybackPhase::Paused))").substringBefore("if(playback.phaseIs(PlaybackPhase::Resuming)")
        assertTrue(paused.contains("waitPlayback(-1)"));assertFalse(paused.contains("submit("))
        val resume=step.substringAfter("if(playback.phaseIs(PlaybackPhase::Resuming)")
        assertTrue(resume.indexOf("acceptedFeedback=0")<resume.indexOf("submit(slots[i])"))
        assertTrue(resume.indexOf("feedbackClock={}")<resume.indexOf("submit(slots[i])"))
        assertFalse(resume.contains("scheduler="));assertFalse(resume.contains("queue.head.store"))
        assertTrue(resume.contains("std::min<uint64_t>(fv::kPrefixPrebuffer,remaining)"))
        assertTrue(resume.contains("prefix->queue.eofReached.load(std::memory_order_acquire)"))
        val submit=native.substringAfter("bool submit(").substringBefore("void packetExample(")
        assertTrue(submit.indexOf("if(pauseAdmission()) return false")<submit.indexOf("queue.reserve("))
        assertTrue(submit.indexOf("queue.abandonReservation()")<submit.indexOf("USBDEVFS_SUBMITURB"))
        assertTrue(submit.indexOf("USBDEVFS_SUBMITURB")<submit.indexOf("queue.commit()"))
    }
    @Test fun wakeLifetimeStopAndGenerationGuardsRemainBoundToOneHandle() {
        val native=source("cpp/usb_iso_transport.cpp")
        val wait=native.substringAfter("void waitPlayback(").substringBefore("bool playbackStep(")
        assertTrue(wait.contains("controlWake->fd,POLLIN"));assertTrue(wait.contains("POLLERR|POLLHUP|POLLNVAL"))
        val stop=native.substringAfter("UsbFsDiagnostics_nativeStopSilentTransport(").substringBefore("UsbFsDiagnostics_nativeSetFullPlayWhenReady(")
        assertTrue(stop.indexOf("requestedStop.store")<stop.indexOf("controlWake->notify()"))
        val commands=native.substringAfter("bool requestPlayback(").substringBefore("Session(int borrowedFd")
        assertTrue(commands.contains("commandsClosed || requestedStop.load()"));assertTrue(commands.contains("lock(controlMutex)"))
        val monitor=source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt")
        assertTrue(monitor.indexOf("playbackControl?.bind")<monitor.indexOf("val worker = async"))
        assertTrue(monitor.indexOf("worker.await()")<monitor.indexOf("destroySilentTransport"))
        assertTrue(monitor.indexOf("playbackControl?.finish()")<monitor.indexOf("destroySilentTransport"))
        val queue=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(queue.contains("std::atomic_load(&workerWake)"));assertTrue(queue.contains("changed.notify_all(); wakeWorker()"))
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        assertTrue(adapter.contains("evidence.generation==generation && !stopRequested"))
        assertTrue(adapter.contains("if(evidence.generation!=generation) return@launch"))
    }
    @Test fun primingCommandsAreStagedAndBoundExactlyOnceToTheSameTrack() {
        val control=UsbFullPlaybackControl();val delivered=mutableListOf<UsbFullControlIntent>()
        assertTrue(control.request(false));assertTrue(control.request(false));assertTrue(control.request(true));assertTrue(control.request(false))
        assertFalse(control.playWhenReady)
        control.bind({ delivered+=it;true },{ fail("Pause must not become Stop") })
        assertEquals(listOf(UsbFullControlIntent(4,3,1,false)),delivered)
        assertTrue(control.request(true));assertEquals(UsbFullControlIntent(5,3,2,true),delivered.last())
    }
    @Test fun duplicatePauseAndResumeRemainCommandsWithoutReplacingSession() {
        val control=UsbFullPlaybackControl();val delivered=mutableListOf<UsbFullControlIntent>();var stops=0
        control.bind({ delivered+=it;true },{ ++stops })
        repeat(3) { control.request(false);control.request(true) }
        assertEquals(UsbFullControlIntent(6,3,3,true),delivered.last())
        assertEquals(0,stops);assertEquals(7,delivered.size)
        control.request(false);control.request(false)
        assertEquals(5L,control.intent().pauses)
    }
    @Test fun stopAndTerminalDeliveryPreventEveryStaleResume() {
        for(beforeBind in listOf(false,true)) {
            val control=UsbFullPlaybackControl();var delivered=0;var stops=0
            if(beforeBind) control.stop()
            control.bind({ ++delivered;true },{ ++stops })
            control.stop();assertFalse(control.request(true));assertTrue(control.stopped)
            assertFalse(control.playWhenReady);assertEquals(if(beforeBind) 0 else 1,delivered)
            assertTrue(stops>=1)
        }
        val old=UsbFullPlaybackControl();old.finish()
        val replacement=UsbFullPlaybackControl();var current=0
        replacement.bind({ ++current;true },{})
        assertFalse(old.request(true));assertEquals(1,current)
        val ended=UsbFullPlaybackControl();ended.bind({ false },{})
        assertFalse(ended.request(true));assertFalse(ended.playWhenReady)
    }
    @Test fun nativeControlErrorsAreRecordedAndStopRatherThanIgnoredOrRetried() {
        val control=UsbFullPlaybackControl();var stops=0;var calls=0
        control.bind({ ++calls;if(it.sequence>0) throw IllegalStateException("private native details");true },{ ++stops })
        try { control.request(false);fail("Failed native command accepted") } catch(_: IllegalStateException) {}
        assertEquals("IllegalStateException",control.failure);assertEquals(1,stops);assertEquals(2,calls)
        assertFalse(control.request(true));assertEquals(2,calls)
    }
    @Test fun monitoringFailureClosesIntentWithoutRetryingTheNativeCommand() {
        val control=UsbFullPlaybackControl();var calls=0
        control.bind({++calls;true},{})
        control.recordFailure(IllegalArgumentException("invalid progress snapshot"))
        assertEquals("IllegalArgumentException",control.failure);assertFalse(control.request(true))
        assertFalse(control.playWhenReady);assertEquals(1,calls)
    }
    @Test fun confirmedPauseFreezesCompletedFramesAndResumeIsMonotonicForAllFourFormats() {
        for((rate,bits) in listOf(44100L to 16L,48000L to 16L,48000L to 24L,96000L to 24L)) {
            assertNotNull(UsbDirectPcmFormat.resolve(rate,2,bits))
            var state=IntegratedDirectPlaybackState(enabled=true,normalReleased=true,phase=IntegratedDirectPhase.PREPARING,
                generation=9,libraryId=42)
            fun progress(frame: Long,phase: UsbFullTransportPhase,play: Boolean) {
                state=integratedProgressState(state,UsbFullProgress(frame,rate*5,rate,phase),play)
            }
            progress(rate,UsbFullTransportPhase.PLAYING,true)
            assertEquals(1000L,state.positionMs);assertEquals(5000L,state.durationMs)
            progress(rate+20,UsbFullTransportPhase.PAUSING,false)
            assertEquals(IntegratedDirectPhase.PAUSING,state.phase);assertFalse(state.playWhenReady)
            progress(rate+100,UsbFullTransportPhase.PAUSED,false)
            val paused=state;assertEquals(IntegratedDirectPhase.PAUSED,state.phase);assertTrue(state.busy)
            repeat(3) { progress(rate+100,UsbFullTransportPhase.PAUSED,false);assertEquals(paused.positionMs,state.positionMs) }
            progress(rate+100,UsbFullTransportPhase.PAUSED,true) // Resume requested during the previous drain/snapshot.
            assertEquals(IntegratedDirectPhase.RESUMING,state.phase)
            progress(rate+100,UsbFullTransportPhase.RESUMING,true);assertEquals(paused.positionMs,state.positionMs)
            progress(rate+100,UsbFullTransportPhase.PLAYING,true)
            progress(rate+200,UsbFullTransportPhase.PLAYING,true);assertTrue(state.positionMs>=paused.positionMs)
            assertEquals(42L,state.libraryId);assertEquals(9L,state.generation);assertEquals(5000L,state.durationMs)
            progress(rate+200,UsbFullTransportPhase.RESUMING,false) // New Pause supersedes a pending Resume.
            assertEquals(IntegratedDirectPhase.PAUSING,state.phase)
        }
    }
    @Test fun initialPauseAndEofProjectionWaitForNativeConfirmationAndFinalCleanup() {
        val priming=IntegratedDirectPlaybackState(phase=IntegratedDirectPhase.PREPARING)
        val requested=integratedProgressState(priming,UsbFullProgress(0,48000,48000,UsbFullTransportPhase.STARTING),false)
        assertEquals(IntegratedDirectPhase.PREPARING,requested.phase)
        val paused=integratedProgressState(requested,UsbFullProgress(0,48000,48000,UsbFullTransportPhase.PAUSED),false)
        assertEquals(IntegratedDirectPhase.PAUSED,paused.phase)
        val end=integratedProgressState(paused,UsbFullProgress(48000,48000,48000,UsbFullTransportPhase.ENDED),false)
        assertEquals(IntegratedDirectPhase.STOPPING,end.phase) // Full result/restoration, not a progress tick, authorizes ENDED.
    }
    @Test fun terminalAndStoppedGenerationsIgnoreLatePauseAndResumeCallbacks() {
        for(phase in listOf(IntegratedDirectPhase.STOPPING,IntegratedDirectPhase.STOPPED,IntegratedDirectPhase.ENDED,IntegratedDirectPhase.ERROR)) {
            val previous=IntegratedDirectPlaybackState(phase=phase,generation=20,completedFrames=48000,totalFrames=96000,sampleRateHz=48000)
            for(native in UsbFullTransportPhase.entries)
                assertSame(previous,integratedProgressState(previous,UsbFullProgress(0,48000,48000,native),true))
        }
    }
    @Test fun backwardOrDifferentSourceProgressIsRejectedInsteadOfRepositioning() {
        val current=IntegratedDirectPlaybackState(phase=IntegratedDirectPhase.PAUSED,completedFrames=48000,totalFrames=96000,sampleRateHz=48000)
        for(bad in listOf(UsbFullProgress(47999,96000,48000),UsbFullProgress(48000,192000,48000),UsbFullProgress(48000,96000,96000))) {
            try { integratedProgressState(current,bad,true);fail("Source progress identity changed") } catch(_: IllegalArgumentException) {}
        }
        val paused=current.copy(nativeTransportPhase=UsbFullTransportPhase.PAUSED)
        try { integratedProgressState(paused,UsbFullProgress(48001,96000,48000,UsbFullTransportPhase.PAUSED),false)
            fail("Confirmed PAUSED position advanced") } catch(_: IllegalArgumentException) {}
    }
    @Test fun appAndExternalSessionSharePlayPauseWhileOtherDirectCommandsStayExcluded() {
        for(internal in listOf(false,true)) {
            assertTrue(allowsDirectCommand(Player.COMMAND_PLAY_PAUSE,internal))
            assertTrue(allowsDirectCommand(Player.COMMAND_STOP,internal))
            for(command in listOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SET_SHUFFLE_MODE,Player.COMMAND_SET_REPEAT_MODE,Player.COMMAND_SET_VOLUME,Player.COMMAND_SET_SPEED_AND_PITCH))
                assertFalse(allowsDirectCommand(command,internal))
        }
        assertFalse(allowsDirectCommand(Player.COMMAND_SET_MEDIA_ITEM,false))
        assertTrue(allowsDirectCommand(Player.COMMAND_SET_MEDIA_ITEM,true)) // Explicit initial library selection, never Resume/replay.
    }
}
