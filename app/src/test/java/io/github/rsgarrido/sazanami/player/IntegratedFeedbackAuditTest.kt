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
    private fun section(text: String,start: String,end: String): String {
        val from=text.indexOf(start);assertTrue("Missing boundary: $start",from>=0)
        val to=text.indexOf(end,from+start.length);assertTrue("Missing following boundary: $end",to>from)
        return text.substring(from,to)
    }

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
        assertEquals("Both clients must share one backend factory",1,Regex("transport\\.silentBackend\\(").findAll(controller).count())
        val native=source("cpp/usb_iso_transport.cpp")
        val progress=section(native,"UsbFsDiagnostics_nativeFullProgress(","UsbFsDiagnostics_nativeStopSilentTransport(")
        assertTrue(progress.contains("lookup(handle,attachment,stream)"))
        // Session 16 publishes completed frames and phase together; synchronization is required, not forbidden.
        val lockedCopy=Regex("""\{\s*std::lock_guard<std::mutex>\s+lock\(session->controlMutex\);([\s\S]*?)\}""").find(progress)
        assertNotNull("Progress values must be copied under the publication mutex",lockedCopy)
        val copyMatch=requireNotNull(lockedCopy)
        val copy=copyMatch.groupValues[1]
        val outsideCopy=progress.removeRange(copyMatch.range)
        for(read in listOf("session->livePlayback","session->liveControl","session->livePausedStarted"))
            assertFalse("Published progress cannot be read outside its mutex: $read",outsideCopy.contains(read))
        for (read in listOf("session->livePlayback[0]","session->totalProgress","session->sampleRateHz",
            "session->livePlayback[1]","session->liveControl.begin()","session->liveControl.end()","session->livePausedStarted"))
            assertTrue("Missing synchronized progress read: $read",copy.contains(read))
        assertTrue(progress.contains("std::array<jlong,4+kControlFieldCount> values"))
        assertTrue(native.contains("kControlFieldCount=FieldCount-PlaybackPhaseField"))
        assertTrue(native.contains("static_assert(kControlFieldCount==17)"))
        for (forbidden in listOf("ioctl(","reapOne(","submit(","scheduler.","requestedStop.store","queue.","CallVoidMethod",
            "session->stats[","session->playback.","session->command","requestPlayback(","publishPlayback(","notify(","consume("))
            assertFalse(forbidden,progress.contains(forbidden))
        val publication=section(native,"void publishPlayback()","bool requestPlayback(")
        assertTrue(publication.contains("lock(controlMutex)"))
        assertTrue(publication.contains("livePlayback={static_cast<uint64_t>(stats[CompletedFrames]),static_cast<uint64_t>(playback.phase)}"))
        val completion=section(native,"bool reapOne()","void cancel(")
        assertTrue(completion.contains("if (cancelled) continue"))
        assertTrue(Regex("""else if \(status == 0\) \{\s*stats\[CompletedFrames\] \+= slot.frames\[i\];""").containsMatchIn(completion))
        assertTrue(completion.indexOf("packet.actual_length != packet.length")<completion.indexOf("stats[CompletedFrames] +="))
        // Control delivery is a separate bounded mailbox/wake path, never a second scheduling worker.
        val command=section(native,"bool requestPlayback(","Session(int borrowedFd")
        assertTrue(command.contains("lock(controlMutex)"));assertTrue(command.contains("commandsClosed || requestedStop.load()"))
        assertTrue(command.contains("sequence!=pauses+resumes"));assertTrue(command.contains("controlWake->notify()"))
        for (forbidden in listOf("ioctl(","reapOne(","submit(","scheduler.","queue.","stats[","playback."))
            assertFalse(forbidden,command.contains(forbidden))
        val controlEntry=section(native,"UsbFsDiagnostics_nativeSetFullPlayWhenReady(","UsbFsDiagnostics_nativeSilentTransportIdle(")
        assertTrue(controlEntry.contains("lookup(handle,attachment,stream)"))
        val lookup=native.substringAfter("std::shared_ptr<Session> lookup(").substringBefore("jobject snapshot(")
        assertTrue(lookup.contains("lock(registryMutex)"));assertTrue(lookup.contains("return found->second;"))
        assertTrue(lookup.contains("found->second->attachment != static_cast<uint64_t>(attachment)"))
        assertTrue(lookup.contains("found->second->stream != static_cast<uint64_t>(stream)"))
        assertFalse(lookup.contains("session->run()"));assertFalse(lookup.contains("ioctl("))
        val worker=native.substringAfter("    void run() {").substringBefore("std::mutex registryMutex;")
        assertFalse(worker.contains("registryMutex"));assertFalse(worker.contains("CallVoidMethod"))
        assertTrue(worker.contains("executing.exchange(true)"));assertTrue(worker.contains("if (ran) throw"))
        val wrapper=source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt")
        assertTrue(wrapper.contains("async(Dispatchers.IO) { UsbFsDiagnostics.runSilentTransport"))
        assertEquals(1,Regex("UsbFsDiagnostics\\.runSilentTransport\\(").findAll(wrapper).count())
        assertTrue(wrapper.contains("withContext(Dispatchers.Main.immediate) { listener(progress) }"))
        assertTrue(wrapper.contains("nextProgressAt=now+250L"));assertTrue(wrapper.contains("delay(5)"))
        val observer=section(wrapper,"val progress=UsbFsDiagnostics.fullProgress(","delay(5)")
        for(forbidden in listOf("runSilentTransport(","setFullPlayWhenReady(","stopSilentTransport(","submit(","reapOne(","scheduler.","queue."))
            assertFalse(forbidden,observer.contains(forbidden))
        val jni=source("java/io/github/rsgarrido/sazanami/player/usb/UsbFsDiagnostics.kt")
        val decode=section(jni,"fun fullProgress(","private external fun nativeFullProgress(")
        assertEquals(17,UsbIsoNativeStats.controlFields.size)
        assertTrue(decode.contains("values.size==4+UsbIsoNativeStats.controlFields.size"))
        assertTrue(decode.contains("UsbIsoNativeStats.validateControl(control)"))
        assertTrue(decode.contains("control.getValue(\"playbackTransportState\")==values[3]"))
        assertTrue(decode.contains("UsbFullProgress(values[0],values[1],values[2],UsbFullTransportPhase.entries[values[3].toInt()],control)"))
        assertTrue(jni.contains("nativeApiVersion() == UsbFsEvidence.NATIVE_API_VERSION"))
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
        assertTrue(adapter.contains("usb.runIntegratedFull(song.uri,commands)"));assertFalse(adapter.contains("ExoPlayer"));assertFalse(adapter.contains("AudioTrack"))
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
