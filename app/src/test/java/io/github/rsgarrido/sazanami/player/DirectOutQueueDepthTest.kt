package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Policy mirror, geometry report and unchanged production boundaries. Authored, not run by Codex. */
class DirectOutQueueDepthTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText().replace("\r\n","\n")
    private fun digest(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    @Test fun measuredBudgetDerivesBoundedKa15DepthWithoutChangingPacketShape() {
        val ka15=UsbIsoTiming(125,1000,28)
        assertEquals(20000L,UsbIsoTiming.REQUIRED_DIRECT_OUT_COVERAGE_US)
        assertEquals(1,UsbIsoTiming.OUT_PHASE_MARGIN_URBS);assertEquals(8,UsbIsoTiming.OUT_PACKETS)
        assertEquals(1000L,ka15.outUrbDurationUs)
        assertEquals(21,ka15.outUrbCount(UsbPcmDiagnosticMode.FLAC_FULL))
        assertEquals(21,ka15.outUrbCount(UsbPcmDiagnosticMode.FLAC_PREFIX))
        assertEquals(21000L,ka15.outCoverageUs(UsbPcmDiagnosticMode.FLAC_FULL))
        assertEquals(8,ka15.outUrbCount(UsbPcmDiagnosticMode.ZERO));assertEquals(8,ka15.outUrbCount(UsbPcmDiagnosticMode.REFERENCE))
        assertEquals(21,ka15.feedbackUrbCount);assertEquals(20000L,ka15.feedbackHoldoverUs)
        assertEquals(42,ka15.directOutUrbCount+ka15.feedbackUrbCount)
        assertEquals(21L,UsbIsoTiming.MAX_DIRECT_OUT_POOL);assertEquals(182L,UsbIsoTiming.MAX_READY_REAPS)
        for ((interval,count) in listOf(125L to 21,250L to 11,500L to 6)) {
            val timing=UsbIsoTiming(interval,1000,128)
            assertEquals(count,timing.directOutUrbCount)
            assertEquals((20000L+timing.outUrbDurationUs-1)/timing.outUrbDurationUs+1,count.toLong())
            assertTrue(count<=UsbIsoTiming.MAX_DIRECT_OUT_POOL)
            assertTrue(timing.outCoverageUs(UsbPcmDiagnosticMode.FLAC_FULL) in 21000L..24000L)
        }
        for (invalid in listOf(0L,124L,126L,1000L,Long.MAX_VALUE)) assertEquals(0,UsbIsoTiming(invalid,1000,28).directOutUrbCount)
        val nominal=UsbIsoFrameAccumulator();repeat(ka15.directOutUrbCount*8) { nominal.next(1) }
        assertEquals(926L,nominal.frames);assertTrue(nominal.frames<8192L)
        val before=nominal.remainder;assertTrue(before>0) // Fraction survives priming.
        nominal.next(1,361267L);assertTrue(nominal.frames>926L)
    }
    @Test fun allSlotBoundariesUseRuntimeDepthAndAlgorithmsRemainIdentical() {
        val native=source("cpp/usb_iso_transport.cpp")
        assertTrue(native.contains("outUrbs(outUrbCount(intervalUs(dataInterval),config[16]))"))
        assertTrue(native.contains("slots(outUrbs+feedbackUrbs)"))
        assertTrue(native.contains("outContinuity(outUrbs,kOutPackets,intervalUs(dataInterval))"))
        assertTrue(native.contains("i >= outUrbs ? 1 : static_cast<unsigned>(kOutPackets)"))
        assertTrue(native.contains("i = outUrbs; i < slots.size()"))
        assertTrue(native.contains("i = 0; i < outUrbs"))
        assertFalse(native.contains("kOutPool"));assertFalse(native.contains("slots.resize("))
        val functions=listOf(
            Triple("    bool submit(","    void packetExample(","d254752bdd32166f1328eda1d9669d5522837b5989708bbf88eb66ece3536f94"),
            Triple("    bool reapOne()","    void cancel(","fae72993e6a809b69269158d310e19cb7e39e7a7331e9a9c54f695e991a6fd6a"),
            Triple("    void shutdown()","    void run()","25c04ab055fddbb36f65869a63246732a4f54b662dc8b7f7b481f242cf85cb0b"),
            Triple("    bool naturalComplete()","    void beginStop()","a7f2ab508d5298558e106f1a0d25da534ea3c2ca163ecf8e2e66a93e16e72195"),
            Triple("    void beginStop()","    bool submit(","468ea597d112d02b59346dd0a8749065ed66f6ea91f35619bfbd8dafb9ddc7ce"))
        for ((start,end,hash) in functions) assertEquals(start,hash,digest(start+native.substringAfter(start).substringBefore(end)))
        val submit=native.substringAfter("    bool submit(").substringBefore("    void packetExample(")
        assertEquals(2,Regex("durationReached\\(").findAll(submit).count())
        assertTrue(submit.indexOf("durationReached(")<submit.indexOf("queue.reserve("))
        assertTrue(submit.indexOf("queue.reserve(")<submit.indexOf("USBDEVFS_SUBMITURB"))
        assertTrue(submit.indexOf("if (returned != 0)")<submit.indexOf("queue.commit()"))
        assertTrue(submit.contains("fullTrack && !slot.feedback && fullSubmitted()"))
        assertTrue(native.contains("for (auto& slot : slots) if (slot.feedback || !integrity.valid() || requestedStop.load()) cancel(slot)"))
        assertTrue(native.contains("monotonicUs() - drainStarted < kDrainUs + kCancelUs"))
        assertTrue(native.contains("stats[OutstandingFinal] = static_cast<jlong>(outstanding(false) + outstanding(true))"))
        val policy=source("cpp/usb_out_queue_policy.h")
        assertTrue(policy.contains("(kRequiredDirectOutCoverageUs+duration-1)/duration+kOutPhaseMarginUrbs"))
        assertTrue(policy.contains("interval!=125 && interval!=250 && interval!=500"))
        val feedback=source("cpp/usb_feedback_policy.h")
        assertTrue(feedback.contains("std::max(kOutPool,kMaximumDirectOutUrbs) + kMaximumFeedbackUrbs"))
        assertTrue(feedback.contains("std::min(slotCount,kReadyReapLimit)"))
        val queue=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(queue.contains("kPrefixCapacity = 16384"));assertTrue(queue.contains("kPrefixPrebuffer = 8192"))
    }
    @Test fun reportShowsActualDepthAndKeepsWireStatusUnknown() {
        val counters=mapOf("configuredOutUrbs" to 21L,"outPacketsPerUrb" to 8L,"dataIntervalUs" to 125L,
            "outUrbDurationUs" to 1000L,"outNominalCoverageUs" to 21000L,
            "configuredFeedbackUrbs" to 21L,"feedbackPacketsPerUrb" to 1L,"feedbackCoverageUs" to 21000L)
        val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),transport=UsbIsoTransportResult(1,2,counters))
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine))
        for (text in listOf("OUT geometry=21x8","OUT continuity: configured URBs=21; packets/URB=8",
            "nominal URB duration us=1000","nominal queued coverage us=21000","maximum 182","wire underrun confirmed? UNKNOWN"))
            assertTrue(text,report.contains(text))
        assertTrue(report.lines().size<70)
    }
}
