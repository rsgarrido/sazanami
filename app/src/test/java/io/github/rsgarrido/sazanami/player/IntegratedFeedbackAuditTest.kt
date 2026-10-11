package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Source synchronization boundaries and synthetic retained reports; no native or Android execution. */
class IntegratedFeedbackAuditTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText()

    @Test fun modePreferencesAreNotResentByCompletedFrameUpdates() {
        val service=source("java/io/github/rsgarrido/sazanami/player/PlaybackService.kt")
        val collector=service.substringAfter("IntegratedDirectPlaybackBridge.state.collect { direct ->")
            .substringBefore("wasDirect=direct.enabled")
        assertTrue("Both preference paths must be inside the mode-change block",Regex(
            "if\\(direct.enabled!=wasDirect\\)\\s*\\{[\\s\\S]*setMediaButtonPreferences\\(emptyList\\(\\)\\)[\\s\\S]*updateAndroidAutoMediaButtonPreferences[\\s\\S]*}\\s*$"
        ).containsMatchIn(collector))
        assertEquals(1,Regex("setMediaButtonPreferences\\(").findAll(collector).count())
        assertTrue(collector.contains("updateDirectControllerCommands(direct.enabled)"))
    }

    @Test fun bothFullClientsUseTheSameEngineAndProgressCannotScheduleOrConsumeFeedback() {
        val controller=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt").replace(Regex("\\s+"),"")
        assertTrue(controller.contains("runConfigurationDiagnostic(identity,true,UsbPcmDiagnosticMode.FLAC_FULL,uri)"))
        assertTrue(controller.contains("runConfigurationDiagnostic(selected.identity,true,UsbPcmDiagnosticMode.FLAC_FULL,uri,client)"))
        assertTrue(controller.contains("transport.silentBackend(target,token.generation,stream,mode,prefixSource)"))
        val native=source("cpp/usb_iso_transport.cpp")
        val progress=native.substringAfter("UsbFsDiagnostics_nativeFullProgress(").substringBefore("extern \"C\" JNIEXPORT void")
        assertTrue(progress.contains("lookup(handle,attachment,stream)"))
        assertTrue(progress.contains("completedProgress.load(std::memory_order_acquire)"))
        assertTrue(progress.contains("session->totalProgress"))
        for (forbidden in listOf("ioctl(","reapOne(","submit(","scheduler.","requestedStop.store","queue.","CallVoidMethod","lock_guard"))
            assertFalse(forbidden,progress.contains(forbidden))
        val lookup=native.substringAfter("std::shared_ptr<Session> lookup(").substringBefore("jobject snapshot(")
        assertTrue(lookup.contains("lock(registryMutex)"));assertTrue(lookup.contains("return found->second;"))
        assertFalse(lookup.contains("session->run()"));assertFalse(lookup.contains("ioctl("))
        val worker=native.substringAfter("    void run() {").substringBefore("std::mutex registryMutex;")
        assertFalse(worker.contains("registryMutex"));assertFalse(worker.contains("CallVoidMethod"))
        val wrapper=source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt")
        assertTrue(wrapper.contains("async(Dispatchers.IO) { UsbFsDiagnostics.runSilentTransport"))
        assertTrue(wrapper.contains("withContext(Dispatchers.Main.immediate) { listener(progress) }"))
        assertTrue(wrapper.contains("nextProgressAt=now+250L"));assertTrue(wrapper.contains("delay(5)"))
    }

    @Test fun unchangedFeedbackFailuresAndSourceFailuresRemainDistinctAndFatal() {
        val native=source("cpp/usb_iso_transport.cpp")
        val core=source("cpp/usb_iso_core.h")
        val policy=source("cpp/usb_feedback_policy.h")
        assertTrue(policy.contains("std::max<uint64_t>(20000, interval * 4)"))
        assertTrue(core.contains("now - lastValid > holdover"));assertTrue(core.contains("now - started >= kBootstrapUs"))
        assertTrue(native.contains("feedbackClock.accept(slot.submittedAt,observed)"))
        assertTrue(native.contains("feedbackTimeout(feedbackReapPass.timeout,feedbackReapPass.evaluatedAt)"))
        assertFalse(native.contains("if (stale)"))
        assertTrue(native.contains("if (invalidStreak >= kInvalidFeedbackLimit) fail(InvalidFeedback, Stop::Feedback)"))
        assertTrue(native.contains("fail(MalformedFeedback, Stop::Feedback)"))
        assertTrue(native.contains("saved == ENODEV ? DeviceDetached : SubmitError"))
        assertTrue(native.contains("saved == ENODEV ? DeviceDetached : ReapError"))
        assertTrue(native.contains("fail(QueueUnderrun | FlacSourceFault, Stop::Underrun)"))
        assertTrue(native.contains("fail(OutPoolStall, Stop::OutPoolStall)"))
        val prefix=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(prefix.contains("kPrefixCapacity = 16384"));assertTrue(prefix.contains("kPrefixPrebuffer = 8192"))
        val result=source("java/io/github/rsgarrido/sazanami/player/usb/UsbSilentTransportResult.kt")
        assertTrue(result.contains("stopReason==\"NATURAL_EOF_COMPLETE\""))
        assertTrue(result.contains("transportDurationTargetUs\")==5000000L"))
        assertTrue(result.contains("\"wholeDecodedPcmHash\",\"allEnqueuedPcmHash\",\"sourcePcmHash\",\"submittedPcmHash\""))
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        assertTrue(adapter.contains("usb.runIntegratedFull(song.uri)"));assertFalse(adapter.contains("ExoPlayer"));assertFalse(adapter.contains("AudioTrack"))
    }

    @Test fun retainedFailureReportAddsBoundedExistingTimingWithoutPretendingItIsFailureTime() {
        val counters=mapOf("integrityMask" to (1L shl 7),"stopReason" to 6L,"feedbackIntervalUs" to 1000L,
            "feedbackHoldoverUs" to 20000L,"lastValidFeedbackAgeUs" to 50000L,"feedbackUrbsSubmitted" to 110910L,
            "feedbackUrbsCompleted" to 110910L,"feedbackOutstandingAtStop" to 2L,"outOutstandingAtStop" to 8L,
            "feedbackPacketsInvalid" to 1L,"invalidStreak" to 0L,"feedbackSamples" to 110908L,
            "sumFeedbackRaw" to 110908L*361267L,"minFeedbackRaw" to 361267L,"maxFeedbackRaw" to 361267L)
        val sample=UsbIsoFeedbackEvidence(361267,4,0,111900000,UsbFeedbackValidity.STALE,21000)
        val result=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=UsbSilentStatus.USB_TRANSPORT_FAILED,
            transport=UsbIsoTransportResult(1,2,counters,feedback=listOf(sample)))
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.ERROR,
            generation=1,normalReleased=true,error="DIRECT_TRANSPORT_FAILED",engine=result))
        for (text in listOf("FEEDBACK_FAILURE","FEEDBACK_TIMEOUT","DIRECT_TRANSPORT_FAILED","feedbackHoldoverUs=20000",
            "lastValidFeedbackAgeUs=50000","post-shutdown, not failure-time","outstandingAtStop=2","invalid=1",
            "invalidStreak=0","submissionAgeUs=21000","validity=STALE","Hz=unavailable","may be drain-time",
            "valid feedback mean Hz=44099.975586","feedback timeout subtype=unavailable","frozen before drain",
            "kernel completed-unreaped count=unknown")) assertTrue(text,report.contains(text))
        assertTrue(report.lines().size<70)
        assertTrue(report.contains("submitErrno=unavailable")) // absent evidence is not a zero-error claim
        assertEquals(counters,result.transport?.counters)
    }
}
