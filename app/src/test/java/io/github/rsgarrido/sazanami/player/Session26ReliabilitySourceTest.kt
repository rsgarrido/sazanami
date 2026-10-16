package io.github.rsgarrido.sazanami.player

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Production wiring guards complement deterministic native policy tests. No native execution. */
class Session26ReliabilitySourceTest {
    private fun source(path: String) = listOf(Paths.get("src/main", path), Paths.get("app/src/main", path))
        .first { Files.isRegularFile(it) }.toFile().readText().replace("\r\n", "\n")

    @Test fun explicitDetachPrecedesGenericErrorsWithoutSkippingCompletionAccounting() {
        val native = source("cpp/usb_iso_transport.cpp")
        val reap = native.substringAfter("bool reapOne()").substringBefore("void cancel(")
        val classification = reap.indexOf("completionIndicatesDetach(status,static_cast<int>(slot.urb->iso_frame_desc[i].status),cancelled)")
        assertTrue(classification > reap.indexOf("slot.identity.complete(attachment, stream)"))
        assertTrue(classification < reap.indexOf("if (cancelled) {"))
        assertTrue(reap.contains("for(unsigned i=0;i<slot.packets;++i) if(completionIndicatesDetach("))
        assertTrue(reap.contains("fail(DeviceDetached,Stop::Detached);break;"))
        assertTrue(classification < reap.indexOf("fail(UrbError, Stop::Worker)"))
        assertTrue(classification < reap.indexOf("feedbackPacket(slot, status)"))
        for(accounting in listOf("++stats[CancellationCompletions]", "stats[CancelledFrames] += slot.frames[i]",
            "stats[KernelOutActualBytes] += packet.actual_length", "stats[CompletedFrames] += slot.frames[i]"))
            assertTrue(accounting, reap.contains(accounting))
        val stop = source("cpp/usb_iso_stop.h")
        assertTrue(stop.contains("!cancelled && (urbStatus==-ENODEV || packetStatus==-ENODEV)"))
        assertTrue(stop.contains("if (!chosen) { value = cause; chosen = true; }"))
    }

    @Test fun timeoutEvidenceIsExplicitAndDoesNotIntroduceAnotherReapOrDeadline() {
        val native = source("cpp/usb_iso_transport.cpp")
        val timeout = native.substringAfter("void feedbackTimeout(").substringBefore("void feedbackPacket(")
        assertTrue(timeout.contains("const FeedbackReapPass* pass=nullptr"))
        assertTrue(timeout.contains("timeoutReapEvidence(kind,observed,pass)"))
        assertFalse(timeout.contains("feedbackReapPass.")); assertFalse(timeout.contains("reapOne("))
        assertTrue(native.contains("feedbackTimeout(feedbackReapPass.timeout,feedbackReapPass.evaluatedAt,0,&feedbackReapPass)"))
        val direct = native.substringAfter("bool seekNaturalComplete()").substringBefore("bool naturalComplete()")
        assertTrue(direct.contains("feedbackTimeout(FeedbackTimeoutKind::Bootstrap,monotonicUs())"))
        assertFalse(direct.contains("&feedbackReapPass"))
        val policy = source("cpp/usb_feedback_policy.h")
        assertTrue(policy.contains("pass->timeout!=kind || pass->evaluatedAt!=observed"))
        assertTrue(policy.contains("result.fill(-1)"))
    }

    @Test fun diagnosticPublicationNeverAcquiresABlockingWorkerLockAndHooksCannotHideMutations() {
        val header = source("cpp/usb_live_diagnostics.h")
        val terminal = header.substringAfter("void publishTerminal(").substringBefore("LiveWords read()")
        assertFalse(terminal.contains("lock(")); assertTrue(terminal.contains("std::memory_order_release"))
        val active = header.substringAfter("bool tryPublish(").substringBefore("void publishTerminal(")
        assertTrue(active.contains("std::try_to_lock"))
        val native = source("cpp/usb_iso_transport.cpp")
        val reap = "    bool reapOne()" + native.substringAfter("    bool reapOne()").substringBefore("    void cancel(")
        for((before, after) in listOf(
            "status),cancelled)" to "status),false)",
            "fail(DeviceDetached,Stop::Detached);break;" to "fail(UrbError,Stop::Worker);break;",
            "timings.probe(reapEntered,monotonicUs(),returned,saved" to "timings.probe(reapEntered,monotonicUs(),returned,0")) {
            assertEquals(1, Regex(Regex.escape(before)).findAll(reap).count())
            assertNotEquals(session26ReviewedSourceEquivalent(reap), session26ReviewedSourceEquivalent(reap.replace(before, after)))
        }
        val feedback = "    void feedbackPacket(" + native.substringAfter("    void feedbackPacket(").substringBefore("    bool reapOne()")
        assertNotEquals(session26ReviewedSourceEquivalent(feedback), session26ReviewedSourceEquivalent(feedback.replace(
            "liveDiagnostics.accepted(monotonicUs(),slot.identity.submission)", "liveDiagnostics.accepted(monotonicUs(),0)")))
    }
}
