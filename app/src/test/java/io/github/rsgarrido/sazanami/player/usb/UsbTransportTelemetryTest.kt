package io.github.rsgarrido.sazanami.player.usb

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Numeric JNI fixtures and the production cache/formatter. No USB or native execution. */
class UsbTransportTelemetryTest {
    private val session = UsbTelemetrySession(1, 2)
    private fun packet(phase: UsbFullTransportPhase = UsbFullTransportPhase.PLAYING,
        epoch: Long = 0, terminal: Boolean = false, captured: Long = 1_000_000): LongArray {
        val paused = phase == UsbFullTransportPhase.PAUSED
        val frames = if(paused) 0L else 132L
        val values = mapOf("version" to 2L, "generation" to 1L, "stream" to 2L, "sourceEpoch" to epoch,
            "activeSeekSerial" to if(epoch == 0L) 0L else 9L, "capturedAtUs" to captured, "readAtUs" to captured + 250,
            "terminal" to if(terminal) 1L else 0L, "transportState" to if(terminal) 7L else 4L,
            "playbackPhase" to phase.ordinal.toLong(), "sampleRateHz" to 44100L, "channels" to 2L, "bits" to 16L,
            "bytesPerFrame" to 4L, "startedAtUs" to 1000L, "outSubmitted" to 10L, "outCompleted" to if(paused) 10L else 7L,
            "outOutstanding" to if(paused) 0L else 3L, "outPool" to 21L, "outFree" to if(paused) 21L else 18L, "pendingPayloadFrames" to frames,
            "pendingPayloadDurationUs" to frames * 1_000_000 / 44100, "estimatedCoverageUs" to if(paused) 0L else 2500L,
            "nominalCoverageUs" to 21000L, "lastSubmitAgeUs" to 1000L, "lastCompletionAgeUs" to 500L,
            "pcmDepth" to 8192L, "pcmCapacity" to 16384L, "pcmMinDepth" to 4096L,
            "feedbackValid" to 10L, "feedbackInvalid" to 1L, "acceptedRaw" to 379288L,
            "feedbackOutstanding" to if(paused) 0L else 21L, "feedbackHoldoverUs" to 20000L, "refillDemand" to if(paused || terminal) 0L else 1L,
            "pcmShortageDepth" to -1L, "pcmShortageRequested" to -1L, "pcmShortageAgeUs" to -1L,
            "collectionStartedUs" to captured - 100L, "collectionDurationUs" to 100L,
            "verifiedUsbSpeed" to 3L, "acInterface" to 10L, "asInterface" to 11L, "alternate" to 9L,
            "dataEndpoint" to 11L, "dataAttributes" to 5L, "dataMaxPacketRaw" to 776L, "dataBInterval" to 1L,
            "feedbackEndpoint" to 142L, "feedbackAttributes" to 17L, "feedbackMaxPacketRaw" to 4L, "feedbackBInterval" to 4L)
        val words = LongArray(UsbTransportTelemetrySchema.WORDS)
        UsbTransportTelemetrySchema.fields.forEachIndexed { i, key -> words[i] = values[key] ?: 0L }
        UsbTransportTelemetrySchema.reliabilityFields.forEachIndexed { i, key -> words[UsbTransportTelemetrySchema.RELIABILITY_OFFSET + i] = values[key] ?: 0L }
        return words
    }
    private fun LongArray.set(key: String, value: Long) {
        val index = UsbTransportTelemetrySchema.fields.indexOf(key)
        this[if(index >= 0) index else UsbTransportTelemetrySchema.RELIABILITY_OFFSET + UsbTransportTelemetrySchema.reliabilityFields.indexOf(key)] = value
    }
    private fun decode(w: LongArray = packet()) = UsbTransportTelemetrySchema.decode(w, session)
    private fun feedbackRow(seq: Long, raw: Long, rejected: Boolean = false) = listOf(seq, seq * 1000, raw,
        if(rejected) 4L else 0L, if(rejected) 4L else 0L, 4L, 4L, 0L, 0L, if(rejected) 0L else 1L, 1000L, 1L, 0L, 0L, 0L)
    private fun write(w: LongArray, offset: Int, row: List<Long>) { row.forEachIndexed { i, v -> w[offset + i] = v } }
    private fun rejects(w: LongArray) {
        try { decode(w); fail("Malformed snapshot accepted") } catch(_: IllegalArgumentException) { }
    }

    @Test fun validPlayingPausedPreparingTerminalAndUnavailablePcmHaveExplicitMeaning() {
        assertEquals(1094, UsbTransportTelemetrySchema.WORDS)
        for(phase in listOf(UsbFullTransportPhase.STARTING, UsbFullTransportPhase.PLAYING, UsbFullTransportPhase.PAUSED,
            UsbFullTransportPhase.SEEK_PRIMING, UsbFullTransportPhase.FAILED)) {
            val w = packet(phase, terminal = phase == UsbFullTransportPhase.FAILED)
            val snapshot = decode(w)
            val report = formatUsbTransportTelemetry(UsbTransportTelemetryView(session,
                if(snapshot.terminal) "TERMINAL" else "LIVE", snapshot))
            assertTrue(report.contains("playback=${phase.name}")); assertTrue(report.contains("snapshot age at retrieval=250 us"))
            assertTrue(report.contains("Actual kernel-armed queued audio duration: unavailable"))
            assertTrue(report.contains("payload duration is not remaining wire audio"))
            assertEquals(if(phase == UsbFullTransportPhase.PAUSED) 0L else 3L, snapshot.n("outOutstanding"))
            assertEquals(21L - snapshot.n("outOutstanding"), snapshot.n("outFree"))
            w.fill(0); assertEquals(10L, snapshot.n("outSubmitted")) // Decoding owns every word.
        }
        val unavailable = packet().also { w -> UsbTransportTelemetrySchema.fields.subList(39, 47).forEach { w.set(it, -1) } }
        assertTrue(formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "LIVE", decode(unavailable))).contains("PCM: available=unavailable"))
        assertTrue(formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "PREPARING")).contains("Snapshot unavailable"))
        assertTrue(formatUsbTransportTelemetry(UsbTransportTelemetryView()).contains("IDLE"))
        val shortage = packet().also { it.set("pcmShortageDepth", 0); it.set("pcmShortageRequested", 44); it.set("pcmShortageAgeUs", 1000) }
        val shortageReport = formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "LIVE", decode(shortage)))
        assertTrue(shortageReport.contains("PCM: available=8192 frames"))
        assertTrue(shortageReport.contains("observed available=0 frames; requested=44 frames"))
    }

    @Test fun strictWireBoundsPoolUnitsAndSourceOwnershipRejectMalformedData() {
        for((key, value) in listOf("version" to 1L, "generation" to 2L, "stream" to 3L,
            "terminal" to 2L, "playbackPhase" to 12L, "readAtUs" to 1L, "capturedAtUs" to 0L,
            "outOutstanding" to 22L, "outFree" to 17L, "outCompleted" to 11L,
            "pendingPayloadDurationUs" to 2994L, "pcmDepth" to 16385L, "gapCount" to 17L,
            "startupCount" to 9L, "rejectedCount" to 9L, "estimatedCoverageUs" to 21001L)) rejects(packet().also { it.set(key, value) })
        rejects(packet().copyOf(UsbTransportTelemetrySchema.WORDS - 1)); rejects(packet().copyOf(UsbTransportTelemetrySchema.WORDS + 1))
        rejects(packet().also { it[UsbTransportTelemetrySchema.GAP_OFFSET] = 1 }) // Unused row must be zero.
        val failed = packet(UsbFullTransportPhase.FAILED, terminal = true).also {
            it.set("integrityMask", 1L shl 12); it.set("outCompleted", 6)
        }
        assertFalse(formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "TERMINAL", decode(failed)))
            .contains("OUT counter accounting agrees=true")) // Failed accounting must remain observable.
        for((rate, bits) in listOf(44100L to 16L, 48000L to 16L, 48000L to 24L, 96000L to 24L)) {
            val w = packet().also { it.set("sampleRateHz", rate); it.set("bits", bits); it.set("bytesPerFrame", 2 * bits / 8)
                it.set("pendingPayloadDurationUs", 132 * 1_000_000L / rate) }
            assertEquals(132 * 1_000_000L / rate, decode(w).n("pendingPayloadDurationUs"))
        }
    }

    @Test fun rawAcceptedRejectedHistoriesAndSourceStartupDurationsSurviveCapture() {
        val w = packet(epoch = 2)
        val rows = listOf(feedbackRow(10, 379288), feedbackRow(11, 379616, true))
        w.set("feedbackAcceptedDecisions", 1);w.set("feedbackRejectedDecisions", 1)
        w[UsbTransportTelemetrySchema.FEEDBACK_OFFSET + 28] = 2
        rows.forEachIndexed { i, r -> write(w, UsbTransportTelemetrySchema.FEEDBACK_OFFSET + 32 + i * 15, r) }
        w.set("rejectedCount", 1); write(w, UsbTransportTelemetrySchema.REJECTED_OFFSET, rows.last())
        w.set("gapCount", 2)
        write(w, UsbTransportTelemetrySchema.GAP_OFFSET, listOf(25, 30000, 1, 22000, 21, 8192, 0, 0).map(Int::toLong))
        write(w, UsbTransportTelemetrySchema.GAP_OFFSET + 8, listOf(26, 31000, 2, 23000, 0, 0, 2, 9).map(Int::toLong))
        w.set("startupCount", 2)
        write(w, UsbTransportTelemetrySchema.STARTUP_OFFSET, listOf(0, 0, 500, 0, 100, 200, 300, 600, 1700, 601).map(Int::toLong))
        write(w, UsbTransportTelemetrySchema.STARTUP_OFFSET + 10, listOf(2, 9, 40000, 0, -1, 20, 100, 110, 1110, 111).map(Int::toLong))
        val snapshot = decode(w)
        val report = formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "LIVE", snapshot))
        assertTrue(report.contains("379288 | 46299.80469 | VALID/NONE | 1"))
        assertTrue(report.contains("379616 | 46339.84375 | IMPLAUSIBLE/IMPLAUSIBLE_RATE | 0"))
        assertTrue(report.contains("Recorded feedback decisions: accepted=1; rejected=1; ignored quiet/terminal=0"))
        assertEquals(listOf(8192L, 0L), snapshot.gaps.map { it[5] })
        assertEquals(0L, snapshot.startups.last()[3]); assertEquals(-1L, snapshot.startups.last()[4])
        assertTrue(report.contains("not confirmed audible dropouts")); assertTrue(report.contains("do not prove audible DAC output"))
        assertFalse(report.contains("content://")); assertFalse(report.contains("/storage/"))
        rejects(w.copyOf().also { it[UsbTransportTelemetrySchema.STARTUP_OFFSET + 10] = 3 })
        rejects(w.copyOf().also { it[UsbTransportTelemetrySchema.GAP_OFFSET + 8] = 24 })
        w.fill(0); assertEquals(379616L, snapshot.rejected.single()[2])
    }

    @Test fun newSessionTerminalRetentionAndRepeatedDetachNeverUseAnOldProvider() {
        val store = UsbTransportTelemetryStore()
        repeat(5) { cycle ->
            val key = UsbTelemetrySession(10L + cycle, 20L + cycle)
            var reads = 0
            store.begin(key); assertNull(store.state.value.snapshot)
            val live = decode().let { it.copy(values = it.values + mapOf("generation" to key.generation, "stream" to key.stream)) }
            store.bind(key) { reads++; live }
            assertEquals(live, store.capture().snapshot); assertEquals(1, reads)
            val terminal = live.copy(values = live.values + mapOf("terminal" to 1L, "transportState" to 7L,
                "playbackPhase" to UsbFullTransportPhase.STOPPED.ordinal.toLong()))
            store.finish(key, terminal)
            assertEquals(terminal, store.capture().snapshot); assertEquals(1, reads)
            store.finish(session); assertEquals(key, store.state.value.session)
        }
        assertEquals("TERMINAL", store.state.value.status)
        assertTrue(formatUsbTransportTelemetry(store.state.value).contains("TERMINAL HISTORICAL"))
    }

    @Test fun readRacingTerminalOrReplacementCannotRepublishOldLiveData() {
        for(replace in listOf(false, true)) {
            val store = UsbTransportTelemetryStore(); store.begin(session)
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            try {
                store.bind(session) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); decode() }
                val pending = executor.submit<UsbTransportTelemetryView> { store.capture() }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                if(replace) store.begin(UsbTelemetrySession(3, 4))
                else store.finish(session, decode(packet(UsbFullTransportPhase.FAILED, terminal = true)))
                release.countDown(); pending.get(5, TimeUnit.SECONDS)
                if(replace) { assertEquals(UsbTelemetrySession(3, 4), store.state.value.session); assertNull(store.state.value.snapshot) }
                else { assertEquals("TERMINAL", store.state.value.status); assertTrue(store.state.value.snapshot!!.terminal) }
            } finally { release.countDown(); executor.shutdownNow() }
        }
    }

    @Test fun failingCaptureIsIsolatedAndAnOlderConcurrentCaptureCannotRegressTime() {
        val store = UsbTransportTelemetryStore(); store.begin(session)
        store.bind(session) { decode(packet(captured = 2_000_000)) }; val latest = store.capture().snapshot
        store.bind(session) { decode(packet(captured = 1_000_000)) }; assertSame(latest, store.capture().snapshot)
        store.bind(session) { decode(packet(captured = 2_000_000).also { it.set("readAtUs", 2_000_100) }) }
        assertSame(latest, store.capture().snapshot) // Same paused publication, older retrieval must also lose.
        store.bind(session) { error("private details") }; assertSame(latest, store.capture().snapshot)
        assertFalse(formatUsbTransportTelemetry(store.state.value).contains("private details"))
        store.finish(session); assertFalse(store.state.value.snapshot!!.terminal)
        assertTrue(formatUsbTransportTelemetry(store.state.value).contains("retained last live observation is historical"))
    }

    @Test fun captureAcknowledgmentDistinguishesReadSuccessFailureAndRetainedEvidence() {
        val store = UsbTransportTelemetryStore()
        assertEquals(UsbTelemetryCaptureOutcome.UNAVAILABLE, store.captureResult().outcome)
        store.begin(session)
        assertEquals(UsbTelemetryCaptureOutcome.UNAVAILABLE, store.captureResult().outcome)
        var reads = 0
        val live = decode(packet(captured = 2_000_000))
        store.bind(session) { reads++; live }
        assertEquals(UsbTelemetryCaptureOutcome.CAPTURED, store.captureResult().outcome)
        // A coherent paused publication can retain its timestamp and still be read successfully.
        assertEquals(UsbTelemetryCaptureOutcome.CAPTURED, store.captureResult().outcome)
        store.bind(session) { reads++; null }
        val failed = store.captureResult()
        assertEquals(UsbTelemetryCaptureOutcome.FAILED, failed.outcome)
        assertSame(live, failed.view.snapshot)
        store.bind(session) { error("/private/music/song.flac") }
        assertEquals(UsbTelemetryCaptureOutcome.FAILED, store.captureResult().outcome)
        assertFalse(formatUsbTransportTelemetry(store.state.value).contains("/private/music/song.flac"))
        store.bind(session) { decode(packet(captured = 1_000_000)) }
        assertEquals(UsbTelemetryCaptureOutcome.FAILED, store.captureResult().outcome)
        store.finish(session)
        val historical = store.captureResult()
        assertEquals(UsbTelemetryCaptureOutcome.HISTORICAL, historical.outcome)
        assertSame(live, historical.view.snapshot)
        assertEquals(3, reads) // No provider is called after terminal cleanup.
        assertTrue(formatUsbTransportTelemetry(historical.view).contains("TERMINAL HISTORICAL"))
        val emptyTerminal = UsbTransportTelemetryStore()
        emptyTerminal.begin(session); emptyTerminal.finish(session)
        assertEquals(UsbTelemetryCaptureOutcome.UNAVAILABLE, emptyTerminal.captureResult().outcome)
    }

    @Test fun jniCaptureReadsOnlyPinnedPublicationsAndCopyActionsCaptureBeforeFormatting() {
        fun source(path: String) = listOf(Paths.get("src/main", path), Paths.get("app/src/main", path))
            .first { Files.isRegularFile(it) }.toFile().readText()
        val native = source("cpp/usb_iso_transport.cpp")
        val jni = native.substringAfter("UsbFsDiagnostics_nativeLiveDiagnostics(").substringBefore("UsbFsDiagnostics_nativeStopSilentTransport(")
        assertTrue(jni.contains("lookup(handle,attachment,stream)")); assertTrue(jni.contains("publishedDiagnostics.read()"))
        for(forbidden in listOf("->prefix", "->slots", "->stats", "::ioctl", "controlMutex")) assertFalse(forbidden, jni.contains(forbidden))
        val worker = native.substringAfter("void publishDiagnostics(").substringBefore("void move(")
        assertTrue(worker.contains("250000")); assertTrue(worker.contains("tryPublish(value)"))
        val backend = source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt")
        assertTrue(backend.indexOf("store.finish(telemetrySession, terminalTelemetry)") < backend.indexOf("destroySilentTransport(handle"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/ui/settings/UsbAudioDiagnosticsSection.kt").contains("enabled = state.serviceAvailable && !capturing"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/player/IntegratedDirectPlaybackReport.kt").contains("formatUsbTransportTelemetry()"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioDiagnosticReport.kt").contains("formatUsbTransportTelemetry()"))
    }

    @Test fun delayedCollectionAndRefreshThenTimeoutPreserveExactTimingAndRequestCorrelation() {
        val w = packet().also { it.set("collectionStartedUs", 970000); it.set("collectionDurationUs", 30000)
            it.set("iteration", 4); it.set("timingEventCount", 1); it.set("correlationCount", 1)
            it.set("integrityMask", 1L shl 7); it.set("stopReason", 6)
            it.set("watchdogPresent", 1); it.set("fatalWatchdogPresent", 1) }
        val event = listOf<Long>(1, 4, 4, 101040, 124300, -11, 0, 0, 0)
        write(w, UsbTransportTelemetrySchema.TIMING_OFFSET, event)
        write(w, UsbTransportTelemetrySchema.CORRELATION_OFFSET, listOf(52703L, 9, 90000, 101000, 101030, 361264, 0, 0, 0))
        val watchdog = listOf<Long>(4, 0, 0, 100799, 100799, 101000, 101030, 9, 101040, 124300, -1, 11, 124350, 799, 23350, 1, 1, 1, 2)
        write(w, UsbTransportTelemetrySchema.WATCHDOG_OFFSET, watchdog)
        write(w, UsbTransportTelemetrySchema.FATAL_WATCHDOG_OFFSET, watchdog)
        val snapshot = decode(w)
        val report = formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "LIVE", snapshot))
        assertTrue(report.contains("970000/1000000/30000 us"))
        assertTrue(report.contains("loop-top/evaluation ages=799/23350"))
        assertTrue(report.contains("valid observation to acceptance=30"))
        assertTrue(report.contains("final probe to decision=50"))
        assertTrue(report.contains("valid observation to decision=23350"))
        assertTrue(report.contains("REAP_SYSCALL")); assertEquals(9L, snapshot.correlations.single()[1])
        assertTrue(report.contains("not proven contention")); assertTrue(report.contains("no scheduler/CPU-time attribution"))
        assertTrue(report.contains("Last successful OUT submit / last OUT reap observation ages"))
        assertFalse(report.contains("Last successful OUT submit/reap"))
        assertTrue(report.contains("Verified configured clock: unavailable"))
        w.fill(0); assertEquals(watchdog, snapshot.fatalWatchdog) // Immutable native-copy lifetime.
    }

    @Test fun extendedSchemaRejectsInventedTimingsRatesAndCrossGenerationEvidence() {
        for((key, value) in listOf("collectionStartedUs" to 1000001L, "collectionDurationUs" to 99L,
            "verifiedUsbSpeed" to 2L, "dataAttributes" to 1L, "feedbackMaxPacketRaw" to 3L,
            "dataBInterval" to 4L, "timingEventCount" to 17L, "correlationCount" to 17L,
            "watchdogPresent" to 2L, "fatalWatchdogPresent" to 2L)) rejects(packet().also { it.set(key, value) })
        rejects(packet().also { it[UsbTransportTelemetrySchema.TIMING_OFFSET] = 1 })
        rejects(packet().also { it[UsbTransportTelemetrySchema.WATCHDOG_OFFSET] = 1 })
        val acceptedInvalid = packet().also { it.set("correlationCount", 1) }
        write(acceptedInvalid, UsbTransportTelemetrySchema.CORRELATION_OFFSET, listOf(1L, 9, 100, 1000, 1010, 379616, 0, 0, 0))
        rejects(acceptedInvalid)
        val futureEpoch = packet().also { it.set("correlationCount", 1) }
        write(futureEpoch, UsbTransportTelemetrySchema.CORRELATION_OFFSET, listOf(1L, 9, 100, 1000, -1, 379616, 4, 1, 0))
        rejects(futureEpoch)
        assertNull(decode().fatalWatchdog)
        assertTrue(formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "LIVE", decode()))
            .contains("First timeout associated reap evaluation: unavailable"))
        val store = UsbTransportTelemetryStore(); store.begin(session)
        assertNull(store.state.value.clockFacts)
        store.bind(UsbTelemetrySession(999, 2)) { error("private path must never be read") }
        assertNull(store.capture().snapshot)
    }

    @Test fun laterExplicitRemovalKeepsItsRawStatusWithoutReplacingAnEarlierFeedbackFailure() {
        val w = packet(UsbFullTransportPhase.FAILED, terminal = true).also {
            it.set("stopReason", 6); it.set("integrityMask", (1L shl 6) or (1L shl 13))
            it.set("detachPacketStatus", -19); it.set("detachRequestSerial", 52728)
            it.set("detachObservedUs", 999000); it.set("detachFeedback", 1)
        }
        val report = formatUsbTransportTelemetry(UsbTransportTelemetryView(session, "TERMINAL", decode(w)))
        assertTrue(report.contains("Terminal stop cause=FEEDBACK_FAILURE"))
        assertTrue(report.contains("URB/packet status=0/-19; request=52728"))
        assertTrue(report.contains("does not replace an earlier stop cause"))
        rejects(w.copyOf().also { it.set("detachPacketStatus", -5) })
        rejects(w.copyOf().also { it.set("detachObservedUs", 1000001) })
        for((urb, packetErrors, cancelled) in listOf(Triple(0L,0L,0L),Triple(0L,0L,1L),Triple(1L,0L,0L),Triple(0L,1L,0L))) {
            val terminal = packet(UsbFullTransportPhase.FAILED, terminal = true).also {
                it.set("outUrbErrors", urb);it.set("outPacketErrors", packetErrors);it.set("outCancelled", cancelled)
            }
            val text = formatUsbTransportTelemetry(UsbTransportTelemetryView(session,"TERMINAL",decode(terminal)))
            assertTrue(text.contains("last OUT reap observation ages"));assertFalse(text.contains("successful OUT submit/reap"))
        }
    }
}
