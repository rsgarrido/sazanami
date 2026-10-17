package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Parser vectors and exact native ownership/accounting boundaries; standalone models exercise native policies separately. */
class UsbSeekFailureAuditTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first {Files.isRegularFile(it)}.toFile().readText()
    private fun bytes(raw: Long)=(0..3).map {((raw ushr (it*8)) and 255).toInt()}
    @Test fun nootsLastFeedbackIsImplausibleAndFatalThresholdRemainsThree() {
        assertEquals(UsbFeedbackValidity.VALID,UsbExplicitFeedback.decode(bytes(361267),sampleRateHz=44100).validity)
        assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(379616),sampleRateHz=44100).validity)
        assertTrue(source("cpp/usb_iso_core.h").contains("kInvalidFeedbackLimit = 3, kPlausibilityPercent = 5"))
        val packet=source("cpp/usb_iso_transport.cpp").substringAfter("void feedbackPacket(").substringBefore("bool reapOne()")
        val invalid=packet.substringAfter("if (decoded.validity != FeedbackValidity::Valid) {").substringBefore("++stats[FeedbackValid]")
        assertTrue(invalid.contains("stats[InvalidStreak] = ++invalidStreak"))
        assertTrue(invalid.contains("if (invalidStreak >= kInvalidFeedbackLimit) fail(InvalidFeedback, Stop::Feedback)"))
        assertTrue(invalid.contains("return;"));assertFalse(invalid.contains("feedbackClock.accept"))
        // Additional rejected shutdown observations may increase final diagnostics; they never become accepted feedback.
        val stopped=packet.indexOf("if (stopping || stopCause.chosen) return")
        assertTrue(stopped>=0 && stopped<packet.indexOf("invalidStreak = 0"))
    }
    @Test fun quietPoolsReconcileBeforeFeedbackResetAndLatestSerialConfirmation() {
        val native=source("cpp/usb_iso_transport.cpp")
        val dispatch=native.substringAfter("bool playbackStep(").substringBefore("bool sealSegment(")
        assertTrue(dispatch.contains("const auto wanted=intent()"))
        assertTrue(dispatch.contains("if(wanted.seekSerial>activeSeekSerial || playback.seeking()) return seekStep(now,wanted)"))
        assertFalse(dispatch.contains("hasPendingSeek()"))
        val resume=native.substringAfter("if(playback.phaseIs(PlaybackPhase::Resuming) && !resumePrepared)").substringBefore("bool sealSegment(")
        for(reset in listOf("acceptedFeedback=0","invalidStreak=0","feedbackClock={}","outContinuity.resumeSegment()"))
            assertTrue(reset,resume.indexOf(reset)>=0 && resume.indexOf(reset)<resume.indexOf("submit(slots[i])"))
        val seek=native.substringAfter("bool seekStep(").substringBefore("bool fullSubmitted()")
        assertTrue(seek.indexOf("outstanding(false) || outstanding(true)")<seek.indexOf("prefix->authorizeSeek("))
        assertTrue(seek.contains("seekPhase.load(std::memory_order_acquire)==2"))
        assertTrue(seek.contains("!seekSealed || outstanding(false) || outstanding(true) || q.prepared"))
        assertTrue(seek.contains("command.seekSerial!=activeSeekSerial || requestedStop.load() || commandsClosed"))
        assertTrue(seek.indexOf("command.seekSerial!=activeSeekSerial")<seek.indexOf("++stats[SeekConfirmed]"))
        assertTrue(seek.contains("playback.countResume=playback.resumeOutcome==ControlOutcome::Requested")) // Only a pending user Resume is counted.
        val reap=native.substringAfter("bool reapOne()").substringBefore("void cancel(")
        assertTrue(reap.contains("slot.identity.complete(attachment, stream)"))
        assertTrue(reap.contains("slot.sourceEpoch!=segments.epoch"))
        val quiet=native.substringAfter("if(fullTrack && playback.quiet()) {").substringBefore("const auto& packet = slot.urb->iso_frame_desc[0]")
        assertTrue(quiet.contains("return;"));assertFalse(quiet.contains("feedbackClock.accept"))
        val continuity=source("cpp/usb_out_continuity.h")
        assertTrue(continuity.contains("if(!suspended && haveLoop && at>lastLoop) wake("))
        assertTrue(continuity.contains("suspended=true"));assertTrue(continuity.contains("suspended=false"))
    }
    @Test fun requestAfterCapturedIntentWaitsForTheNextSnapshotAndPreservesNewestPlayIntent() {
        val mailbox=UsbFullPlaybackControl()
        val captured=mailbox.intent() // Native worker has not observed a seek yet.
        assertTrue(mailbox.seek(10000));assertTrue(mailbox.request(false))
        val newest=mailbox.intent()
        assertEquals(0L,captured.seekSerial);assertTrue(newest.seekSerial>captured.seekSerial);assertFalse(newest.play)
        // Dispatch must use captured.seekSerial, not a predicate from newest paired with the older captured command.
        assertEquals(0L,captured.seeks);assertEquals(1L,newest.seeks)
        repeat(10000) {mailbox.seek(it.toLong())}
        val final=mailbox.intent();assertEquals(10001L,final.seeks);assertEquals(9999L,final.targetFrame);assertFalse(final.play)
        mailbox.request(true);assertTrue(mailbox.intent().play);assertEquals(10001L,mailbox.intent().seeks)
        mailbox.stop();assertFalse(mailbox.seek(20000));assertFalse(mailbox.request(false))
    }
    @Test fun rawActualBytesAndCancelledFramesCannotBeRelabeledSuccessfulCompletions() {
        val native=source("cpp/usb_iso_transport.cpp")
        val reap=native.substringAfter("bool reapOne()").substringBefore("void cancel(")
        val raw=reap.indexOf("stats[KernelOutActualBytes] += packet.actual_length")
        val cancelled=reap.indexOf("if (cancelled) continue")
        val verified=reap.indexOf("stats[CompletedFrames] += slot.frames[i]")
        assertTrue(raw>=0 && cancelled>raw && verified>cancelled)
        assertTrue(reap.contains("packet.status != 0"));assertTrue(reap.contains("packet.actual_length != packet.length"))
        assertTrue(reap.contains("else if (status == 0)"))
        val shutdown=native.substringAfter("void shutdown()").substringBefore("void run()")
        assertTrue(shutdown.contains("stats[SubmittedFrames] != stats[CompletedFrames]"))
        assertTrue(shutdown.contains("stats[KernelOutActualBytes] != stats[OutBytesSubmitted]"))
        assertTrue(shutdown.contains("integrity.compromise(FrameMismatch)"))
        assertTrue(shutdown.contains("stats[OutstandingFinal]"));assertTrue(shutdown.contains("ShutdownUnaccounted"))
        for(width in listOf(4L,6L)) {
            val sent=940158L*width;val actual=(939279L+59)*width;val successful=939279L*width
            assertEquals(820L*width,sent-actual);assertEquals(59L*width,actual-successful)
        }
    }
}
