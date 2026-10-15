package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.player.usb.*
import io.github.rsgarrido.sazanami.ui.state.*
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Authored JVM policy/position/command tests; no native test or hardware execution. */
class UsbNativeSeekTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path)).first { Files.isRegularFile(it) }.toFile().readText()
    @Test fun floorTargetsAndDurationClampUseAcceptedRateWithoutOverflow() {
        for(rate in listOf(44100L,48000L,96000L)) {
            assertEquals(rate*90,directSeekTargetFrame(90000,rate,rate*180))
            assertEquals(0L,directSeekTargetFrame(0,rate,rate*180))
            assertEquals(rate/1000,directSeekTargetFrame(1,rate,rate*180))
            assertEquals(rate*180-1,directSeekTargetFrame(180000,rate,rate*180))
            assertEquals(rate*180-1,directSeekTargetFrame(Long.MAX_VALUE,rate,rate*180))
            assertTrue(directSeekTargetFrame(Long.MAX_VALUE,rate,Long.MAX_VALUE)>0)
        }
        for(arguments in listOf(Triple(-1L,48000L,100L),Triple(1L,0L,100L),Triple(1L,192000L,100L),Triple(1L,48000L,0L))) {
            try {directSeekTargetFrame(arguments.first,arguments.second,arguments.third);fail("Invalid target accepted")} catch(_: IllegalArgumentException) {}
        }
    }
    @Test fun boundedMailboxKeepsFirstTargetLastSerialAndPlayIntentAcrossBind() {
        val control=UsbFullPlaybackControl()
        assertTrue(control.seek(4320000));assertTrue(control.seek(0));assertTrue(control.request(false))
        var delivered: UsbFullControlIntent?=null;var stops=0
        control.bind({delivered=it;true},{++stops})
        assertEquals(3L,delivered!!.sequence);assertEquals(2L,delivered!!.seekSerial)
        assertEquals(4320000L,delivered!!.firstTargetFrame);assertEquals(0L,delivered!!.targetFrame);assertFalse(delivered!!.play)
        repeat(10000) { assertTrue(control.seek(it.toLong())) }
        assertEquals(10002L,delivered!!.seeks);assertEquals(9999L,delivered!!.targetFrame)
        assertTrue(control.request(true));assertTrue(delivered!!.play)
        control.stop();assertEquals(1,stops);assertFalse(control.seek(0));assertFalse(control.request(true))
        val stopped=control.intent();assertEquals(stopped,control.intent())
        val rejected=UsbFullPlaybackControl();rejected.bind({false},{++stops});assertFalse(rejected.seek(1))
        try {UsbFullPlaybackControl().seek(-1);fail("negative")} catch(_: IllegalArgumentException) {}
    }
    private fun progress(completed: Long,position: Long,epoch: Long,serial: Long,phase: UsbFullTransportPhase)=UsbFullProgress(
        completed,480000,48000,phase,mapOf("sourcePositionFrame" to position,"seekSourceEpoch" to epoch,
            "confirmedSeekSerial" to serial,"seekConfirmed" to epoch,"sourceSeekCapable" to 1L))
    @Test fun confirmationCanJumpBackwardWhileCumulativeTransportAndMetadataStayMonotonic() {
        var state=IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.PLAYING,generation=8,libraryId=42,
            completedFrames=96000,totalFrames=480000,sampleRateHz=48000,sourcePositionFrame=96000,seekCapable=true)
        state=state.copy(phase=IntegratedDirectPhase.SEEKING,pendingSeekSerial=3)
        state=integratedProgressState(state,progress(96100,96100,0,0,UsbFullTransportPhase.SEEK_DRAINING),true)
        assertEquals(2002L,state.positionMs);assertEquals(96100L,state.completedFrames)
        // An old target confirmation must not overwrite the newer app serial.
        state=integratedProgressState(state,progress(96100,300000,1,2,UsbFullTransportPhase.RESUMING),true)
        assertEquals(96100L,state.sourcePositionFrame);assertEquals(IntegratedDirectPhase.SEEKING,state.phase)
        state=integratedProgressState(state,progress(96100,48000,2,3,UsbFullTransportPhase.PAUSED),false)
        assertEquals(1000L,state.positionMs);assertFalse(state.playWhenReady);assertEquals(96100L,state.completedFrames)
        repeat(3) {state=integratedProgressState(state,progress(96100,48000,2,3,UsbFullTransportPhase.PAUSED),false)}
        assertEquals(42L,state.libraryId);assertEquals(8L,state.generation);assertEquals(10000L,state.durationMs)
        state=integratedProgressState(state,progress(96200,48100,2,3,UsbFullTransportPhase.PLAYING),true)
        assertEquals(48100L,state.sourcePositionFrame)
        for(bad in listOf(progress(96200,48099,2,3,UsbFullTransportPhase.PLAYING),progress(96199,48200,2,3,UsbFullTransportPhase.PLAYING),
            progress(96200,48000,1,3,UsbFullTransportPhase.PAUSED))) {
            try {integratedProgressState(state,bad,true);fail("stale/regressing progress accepted")} catch(_: IllegalArgumentException) {}
        }
        val terminal=state.copy(phase=IntegratedDirectPhase.STOPPING)
        assertSame(terminal,integratedProgressState(terminal,progress(96200,0,3,4,UsbFullTransportPhase.PLAYING),true))
        val repeated=progress(600000,24000,3,4,UsbFullTransportPhase.PAUSED)
        assertEquals(600000L,repeated.completedFrames);assertEquals(24000L,repeated.sourcePositionFrame)
    }
    @Test fun oneScrubCommitAndExternalSeekOrModeChangeCancelItWithoutMutatingProgress() {
        val authority=DirectScrubAuthority(true,5,0,true);val scrub=DirectSeekScrub()
        val actual=PlaybackProgressUiState(currentPosition=20000,duration=200000)
        scrub.begin(authority)
        repeat(500) { assertTrue(scrub.change(90000+it,authority)) }
        assertEquals(90499,scrub.preview());assertEquals(20000,actual.currentPosition)
        assertEquals(90499,scrub.end(authority));assertNull(scrub.end(authority))
        for(changed in listOf(authority.copy(enabled=false),authority.copy(generation=6),authority.copy(seekSerial=1),authority.copy(allowed=false))) {
            scrub.begin(authority);scrub.change(10000,authority);scrub.observe(changed)
            assertNull(scrub.preview());assertTrue(scrub.change(12000,changed));assertNull(scrub.end(changed))
        }
        scrub.begin(authority);scrub.change(10000,authority);scrub.cancel();assertNull(scrub.end(authority))
        assertTrue(scrub.change(20000,authority)) // A completed direct touch remains a tombstone; semantics have their own immediate path.
        scrub.begin(authority.copy(enabled=false));assertFalse(scrub.change(20000,authority.copy(enabled=false)))
        assertNull(scrub.end(authority.copy(enabled=false))) // Normal seeking remains per-movement.
        scrub.begin(authority.copy(enabled=false));assertTrue(scrub.change(20000,authority));assertNull(scrub.end(authority))
    }
    @Test fun oneMediaSessionAndAppPathSourceOwnershipAndTransportGuardsRemainExplicit() {
        assertTrue(allowsDirectCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,false))
        assertTrue(allowsDirectCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM,true))
        assertFalse(allowsDirectCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM,false))
        for(command in listOf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_CHANGE_MEDIA_ITEMS))
            assertFalse(allowsDirectCommand(command,true))
        val native=source("cpp/usb_iso_transport.cpp")
        val seek=native.substringAfter("bool seekStep(").substringBefore("bool fullSubmitted()")
        assertTrue(seek.indexOf("sealSegment(false)")<seek.indexOf("q.resetEpoch("))
        assertTrue(seek.contains("outstanding(false) || outstanding(true)"));assertTrue(seek.contains("seekPhase.load(std::memory_order_acquire)==2"))
        assertTrue(seek.contains("!seekSealed || outstanding(false) || outstanding(true) || q.prepared"))
        assertTrue(seek.contains("prefix->authorizeSeek(activeSeekSerial,activeSeekTarget)"));assertTrue(seek.contains("prefix->seekFirst.load()!=activeSeekTarget"))
        assertTrue(seek.contains("confirmation(controlMutex)") && seek.contains("command.seekSerial!=activeSeekSerial"))
        assertFalse(seek.contains("submit("));assertFalse(seek.contains("DISCARDURB"));assertFalse(seek.contains("beginStop("))
        for(guard in listOf("!fv::canAdd(stats[ScheduledFrames],frames)","!fv::canAdd(stats[CompletedFrames],slot.frames[i])",
            "slot.sourceEpoch!=segments.epoch","!fv::canAdd(stats[KernelOutActualBytes],packet.actual_length)")) assertTrue(guard,native.contains(guard))
        val actor=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(actor.contains("processInteractive(native)"));assertTrue(actor.contains("FLAC__stream_decoder_seek_absolute(native,seekTarget)"))
        assertEquals(1,Regex("producer=std::thread").findAll(actor).count())
        assertTrue(actor.contains("if (joined.load())")) // EOF checkpoint can still seek; observers require join for decoder evidence.
        val core=source("cpp/flac/flac_decode_core.h")
        assertTrue(core.contains("v.targetBlock.resize(static_cast<size_t>(h.blocksize)*v.pcm.frameBytes())"))
        assertTrue(core.contains("h.blocksize>static_cast<uint32_t>(v.facts[MaxBlock])"))
        assertTrue(core.contains("h.number.sample_number!=v.seekTarget"))
        assertTrue(core.contains("S_ISREG(info.st_mode)"))
        val ui=source("java/io/github/rsgarrido/sazanami/ui/MusicRoute.kt")
        assertFalse(ui.contains("scrub.end(scrubAuthority())"));assertTrue(ui.contains("LocalDirectSeekPreview provides seekPreview"))
        assertTrue(ui.contains("LocalDirectSeekGestureEnvironment provides seekGestures"))
        val slider=source("java/io/github/rsgarrido/sazanami/ui/player/modern/ModernPlayerSeekBar.kt")
        assertEquals(2,Regex("onValueChangeFinished = gesture::finish").findAll(slider).count())
        assertTrue(slider.contains("sliderState.onValueChange={gesture.change(it.toInt())}"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/player/PlaybackController.kt").contains("IntegratedDirectPlaybackBridge.seek(position.toLong())"))
        assertEquals(508,UsbIsoNativeStats.fields.size);assertEquals(17,UsbIsoNativeStats.controlFields.size);assertEquals(37,UsbIsoNativeStats.seekFields.size)
        assertEquals(53,UsbFlacSourceEvidence.fields.size)
    }
}
