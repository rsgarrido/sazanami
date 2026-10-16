package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Native helper scenarios are in usb_feedback_policy_test.cpp; these pin production wiring/evidence. */
class FeedbackWatchdogOrderingTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText()

    @Test fun readyWorkUsesNormalAccountingBeforeWatchdogAndRefill() {
        val native=source("cpp/usb_iso_transport.cpp")
        val worker=native.substringAfter("    void run() {").substringBefore("std::mutex registryMutex;")
        val pass=worker.indexOf("feedbackReapPass=reapBeforeFeedbackWatchdog")
        val timeout=worker.indexOf("feedbackTimeout(feedbackReapPass.timeout,")
        val outWatchdog=worker.indexOf("monotonicUs() - lastOutCompletion > kQueueWatchdogUs")
        val refill=worker.indexOf("for (auto& slot : slots) if (!slot.identity.outstanding")
        val poll=worker.indexOf("pollCompletion();")
        assertTrue(pass>=0 && pass<timeout && timeout<outWatchdog && outWatchdog<refill && refill<poll)
        assertTrue(worker.contains("now,slots.size(),"))
        assertTrue(worker.contains("[this] { return reapOne(); }"))
        val gate=worker.substringAfter("[this] { return integrity.valid()").substringBefore("});")
        for (condition in listOf("!stopCause.chosen","!requestedStop.load()","prefix->queue.faults.load()"))
            assertTrue(condition,gate.contains(condition))
        val policy=source("cpp/usb_feedback_policy.h")
        assertTrue(policy.contains("kReadyReapLimit = std::max(kOutPool,kMaximumDirectOutUrbs) + kMaximumFeedbackUrbs"))
        assertTrue(policy.contains("std::min(slotCount,kReadyReapLimit)"))
        assertTrue(policy.contains("while (pass.reaped<limit && active() && reap())"))
        assertTrue(policy.indexOf("active() && reap()")<policy.indexOf("clock.expired(pass.evaluatedAt,holdover)"))
        assertFalse(policy.contains("submit("));assertFalse(policy.contains("poll("))
        val reap=native.substringAfter("bool reapOne()").substringBefore("void cancel(")
        assertTrue(reap.contains("stats[CompletedFrames] += slot.frames[i]"))
        assertTrue(reap.contains("stats[KernelOutActualBytes] += packet.actual_length"))
        assertTrue(reap.contains("feedbackPacket(slot, status)"))
    }

    @Test fun validationPrecedesRefreshAndRealErrorsRemainFatal() {
        val native=source("cpp/usb_iso_transport.cpp")
        val parser=native.substringAfter("void feedbackPacket(").substringBefore("bool reapOne()")
        assertFalse(parser.contains(".expired("))
        assertTrue(parser.indexOf("fail(MalformedFeedback, Stop::Feedback)")<parser.indexOf("feedbackClock.accept("))
        assertTrue(parser.indexOf("if (decoded.validity != FeedbackValidity::Valid)")<parser.indexOf("feedbackClock.accept("))
        assertTrue(parser.contains("packet.status != 0"))
        assertTrue(parser.contains("if (invalidStreak >= kInvalidFeedbackLimit) fail(InvalidFeedback, Stop::Feedback)"))
        assertTrue(parser.contains("if (stopping || stopCause.chosen) return"))
        assertTrue(parser.contains("feedbackClock.accept(slot.submittedAt,observed)"))
        for (fatal in listOf("fail(UrbError, Stop::Worker)","saved == ENODEV ? DeviceDetached : SubmitError",
            "saved == ENODEV ? DeviceDetached : ReapError","fail(DeviceDetached, Stop::Detached)",
            "fail(AuthorityChanged, requestedStop.load() == 2 ? Stop::AuthorityLost : Stop::Requested)"))
            assertTrue(fatal,native.contains(fatal))
        val core=source("cpp/usb_iso_core.h")
        assertTrue(core.contains("kBootstrapUs = 100000"))
        val policy=source("cpp/usb_feedback_policy.h")
        assertTrue(policy.contains("std::max<uint64_t>(20000, interval * 4)"))
        assertTrue(policy.contains("now-started>=kBootstrapUs"))
        assertTrue(policy.contains("now-lastObserved<=holdover"))
        val ka15=UsbIsoTiming(125,1000,28)
        assertEquals(21,ka15.feedbackUrbCount);assertEquals(1,UsbIsoTiming.FEEDBACK_PACKETS)
        assertEquals(20000L,ka15.feedbackHoldoverUs)
        assertEquals(8,UsbIsoTiming.OUT_POOL);assertEquals(8,UsbIsoTiming.OUT_PACKETS)
        val queue=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(queue.contains("kPrefixCapacity = 16384"));assertTrue(queue.contains("kPrefixPrebuffer = 8192"))
        for (forbidden in listOf("USBDEVFS_RESET","USBDEVFS_CONNECT","USBDEVFS_DISCONNECT","AudioTrack","ExoPlayer"))
            assertFalse(forbidden,native.contains(forbidden))
    }

    @Test fun firstTimeoutCopiesPassEvidenceAndReportKeepsUnknownSeparateFromZero() {
        val native=source("cpp/usb_iso_transport.cpp")
        val fault=native.substringAfter("if (!stats[TimeoutSubtype]) {").substringBefore("fail(FeedbackTimeout,Stop::Feedback)")
        for (field in listOf("TimeoutReapAttempted","TimeoutReapCount","TimeoutReapFeedbackCount","TimeoutReapRefreshed",
            "TimeoutAgeBeforeReap","TimeoutAgeAfterReap","TimeoutWatchdogEvaluated","TimeoutWatchdogFired"))
            assertTrue(field,fault.contains("stats[$field]="))
        val numbers=mapOf("version" to UsbIsoNativeStats.VERSION.toLong(),"generation" to 1L,"streamGeneration" to 2L,"state" to 7L,
            "stopReason" to 6L,"sourceMode" to 3L,"integrityMask" to (1L shl 7),"feedbackTimeoutSubtype" to 2L,
            "feedbackFailureReapAttempted" to 1L,"feedbackFailureReapCount" to 8L,"feedbackFailureReapFeedbackCount" to 0L,
            "feedbackFailureReapRefreshed" to 0L,"feedbackFailureAgeBeforeReapUs" to 20455L,
            "feedbackFailureAgeAfterReapUs" to 20475L,"feedbackFailureWatchdogEvaluated" to 1L,
            "feedbackFailureWatchdogFired" to 1L,"feedbackRecoveredObservationGaps" to 2L)
        val packet=UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map { numbers[it] ?: 0L }.toLongArray(),longArrayOf(),longArrayOf())
        val result=UsbIsoNativeStats.decode(packet,1,2)
        assertEquals(131,UsbIsoNativeStats.fields.indexOf("feedbackFailureReapAttempted"))
        assertEquals(20455L,result.count("feedbackFailureAgeBeforeReapUs"))
        val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),transport=result)
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine))
        for (text in listOf("attempted=1","URBs reaped=8","feedback completions=0","valid refresh=0",
            "age before us=20455","age after us=20475","watchdog evaluated=1","fired=1",
            "observation gaps recovered before watchdog=2","kernel completed-unreaped count=unknown","frozen before drain"))
            assertTrue(text,report.contains(text))
        assertTrue(report.lines().size<70)
        val missing=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine.copy(
            transport=UsbIsoTransportResult(1,2,mapOf("integrityMask" to (1L shl 7))))))
        assertTrue(missing.contains("pre-watchdog reap at fault: attempted=unavailable"))
        val absent = numbers + UsbIsoNativeStats.historicalFields.subList(131,139).associateWith { -1L }
        val noPass = UsbIsoNativeStats.decode(UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map { absent[it] ?: 0L }.toLongArray(),
            longArrayOf(),longArrayOf()),1,2)
        val directReport = formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine.copy(transport=noPass)))
        assertTrue(directReport.contains("valid refresh=unavailable")); assertTrue(directReport.contains("fired=unavailable"))
        assertFalse(directReport.contains("attempted=-1"))
    }
}
