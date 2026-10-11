package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Pure timing/schema/report tests plus source boundaries. Authored, not executed by Codex. */
class FeedbackQueueCorrectionTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText()

    @Test fun coverageIsDerivedFromIntervalAndUnchangedHoldover() {
        for(interval in 1..7) {
            val tick=UsbIsoTiming.serviceIntervalUs(UsbFsSpeed.HIGH,interval)!!
            val timing=UsbIsoTiming(125,tick,28)
            assertEquals(maxOf(20000L,tick*4),timing.feedbackHoldoverUs)
            assertEquals((timing.feedbackHoldoverUs+tick-1)/tick+1,timing.feedbackUrbCount.toLong())
            assertTrue(timing.feedbackCoverageUs>timing.feedbackHoldoverUs)
            assertTrue(timing.feedbackCoverageUs<=timing.feedbackHoldoverUs+tick)
            assertTrue(timing.feedbackUrbCount<=UsbIsoTiming.MAX_FEEDBACK_POOL)
        }
        val ka15=UsbIsoTiming(125,1000,28)
        assertEquals(21,ka15.feedbackUrbCount);assertEquals(21000L,ka15.feedbackCoverageUs)
        assertEquals(1,UsbIsoTiming.FEEDBACK_PACKETS)
        assertEquals(8000L,ka15.outCoverageUs(UsbPcmDiagnosticMode.ZERO))
        assertEquals(0,UsbIsoTiming(125,0,28).feedbackUrbCount)
    }

    private fun packet(overrides: Map<String,Long> = emptyMap()): UsbIsoNativeSnapshot {
        val values=mapOf("version" to UsbIsoNativeStats.VERSION.toLong(),"generation" to 1L,"streamGeneration" to 2L,
            "state" to 7L,"sourceMode" to 3L,"stopReason" to 6L) + overrides
        return UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map { values[it] ?: 0L }.toLongArray(),longArrayOf(),longArrayOf())
    }
    @Test fun schemaHasThreeDistinctTimeoutOriginsAndRejectsOldOrInvalidData() {
        assertEquals(8,UsbIsoNativeStats.VERSION);assertEquals(174,UsbIsoNativeStats.fields.size)
        assertEquals(111,UsbIsoNativeStats.fields.indexOf("configuredFeedbackUrbs"))
        for(kind in 0L..3L) assertEquals(kind,UsbIsoNativeStats.decode(packet(mapOf("feedbackTimeoutSubtype" to kind)),1,2).count("feedbackTimeoutSubtype"))
        assertEquals(listOf("NONE","BOOTSTRAP_TIMEOUT","LAST_VALID_WATCHDOG_TIMEOUT","VALID_OBSERVATION_GAP_AT_REAP"),UsbIsoNativeStats.feedbackTimeoutSubtypes)
        for(bad in listOf(packet(mapOf("version" to 7L)),packet(mapOf("version" to 6L)),packet(mapOf("version" to 5L)),packet(mapOf("feedbackTimeoutSubtype" to 4L)))) {
            try { UsbIsoNativeStats.decode(bad,1,2);fail("Invalid/old feedback evidence was accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun firstFaultReportDistinguishesObservedRequestAndShutdownClocks() {
        val counters=mapOf("integrityMask" to (1L shl 7),"feedbackTimeoutSubtype" to 2L,
            "configuredFeedbackUrbs" to 21L,"feedbackPacketsPerUrb" to 1L,"feedbackCoverageUs" to 21000L,
            "feedbackLastValidWatchdogTimeouts" to 1L,"feedbackFailureObservedUs" to 1020001L,
            "feedbackFailureLastValidObservedUs" to 1000000L,"feedbackFailureLastValidSubmittedUs" to 979000L,
            "feedbackFailureOffendingSubmittedUs" to 0L,"feedbackFailureAgeUs" to 20001L,
            "feedbackFailureOutstandingUrbs" to 21L,"feedbackFailureReapedNotRearmedUrbs" to 0L,
            "feedbackFailureAcceptedRaw" to 361264L,"lastValidFeedbackAgeUs" to 95000L)
        val native=UsbIsoNativeStats.decode(packet(counters),1,2)
        val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=UsbSilentStatus.USB_TRANSPORT_FAILED,transport=native)
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.ERROR,
            generation=1,normalReleased=true,error="DIRECT_TRANSPORT_FAILED",engine=engine))
        for(text in listOf("configured URBs=21","packets/URB=1","nominal coverage us=21000",
            "LAST_VALID_WATCHDOG_TIMEOUT","last-valid watchdog=1","observedUs=1020001","lastValidObservedUs=1000000",
            "lastValidSubmittedUs=979000","offendingSubmittedUs=0","observationAgeUs=20001","frozen before drain",
            "application outstanding=21","kernel completed-unreaped count=unknown","accepted Hz=44099.609375",
            "lastValidFeedbackAgeUs=95000","post-shutdown, not failure-time","request age is not DAC sample age")) assertTrue(text,report.contains(text))
        assertFalse(report.contains("feedback timeout path=unresolved"));assertTrue(report.lines().size<70)
        assertEquals(1020001L,native.count("feedbackFailureObservedUs"))
    }

    @Test fun productionUsesBoundedSinglePacketQueueAndFrozenFailureEvidence() {
        val native=source("cpp/usb_iso_transport.cpp")
        assertTrue(native.contains("feedbackUrbs(feedbackUrbCount(intervalUs(feedbackInterval)))"))
        assertTrue(native.contains("slots(outUrbs+feedbackUrbs)"))
        assertTrue(native.contains("i >= outUrbs ? 1 : static_cast<unsigned>(kOutPackets)"))
        assertTrue(native.contains("slot.packets=slot.feedback ? 1 : static_cast<unsigned>(kOutPackets)"))
        assertTrue(native.contains("urb->number_of_packets = static_cast<int>(slot.packets)"))
        val fault=native.substringAfter("void feedbackTimeout(").substringBefore("void feedbackPacket(")
        assertTrue(fault.contains("if (!stats[TimeoutSubtype])"))
        assertTrue(fault.contains("if (stopping || kind==FeedbackTimeoutKind::None) return"))
        assertTrue(fault.contains("TimeoutReapedNotRearmed"));assertTrue(fault.contains("TimeoutAcceptedRaw"))
        val parser=native.substringAfter("void feedbackPacket(").substringBefore("bool reapOne()")
        assertTrue(parser.contains("decodeFeedback(slot.buffer.data(), packet.actual_length, static_cast<int>(packet.status))"))
        assertTrue(parser.contains("++stats[RequestsOverHoldover]"));assertFalse(parser.contains("if (stale)"))
        assertTrue(parser.contains("feedbackClock.accept(slot.submittedAt,observed)"))
        assertTrue(parser.contains("if (stopping || stopCause.chosen) return"))
        assertTrue(parser.contains("fail(MalformedFeedback, Stop::Feedback)"))
        assertTrue(parser.contains("if (invalidStreak >= kInvalidFeedbackLimit) fail(InvalidFeedback, Stop::Feedback)"))
        assertTrue(native.contains("if (!slot.identity.outstanding && integrity.valid() && !stopCause.chosen) submit(slot)"))
        assertTrue(native.contains("for (auto& slot : slots) if (slot.feedback || !integrity.valid() || requestedStop.load()) cancel(slot)"))
        assertTrue(native.contains("stats[OutstandingFinal] = static_cast<jlong>(outstanding(false) + outstanding(true))"))
        assertTrue(native.contains("saved == ENODEV ? DeviceDetached : SubmitError"))
        assertTrue(native.contains("fail(UrbError, Stop::Worker)"))
    }
}
