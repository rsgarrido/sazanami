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
        val values = mapOf("version" to 1L, "generation" to 1L, "stream" to 2L, "sourceEpoch" to epoch,
            "activeSeekSerial" to if(epoch == 0L) 0L else 9L, "capturedAtUs" to captured, "readAtUs" to captured + 250,
            "terminal" to if(terminal) 1L else 0L, "transportState" to if(terminal) 7L else 4L,
            "playbackPhase" to phase.ordinal.toLong(), "sampleRateHz" to 44100L, "channels" to 2L, "bits" to 16L,
            "bytesPerFrame" to 4L, "startedAtUs" to 1000L, "outSubmitted" to 10L, "outCompleted" to if(paused) 10L else 7L,
            "outOutstanding" to if(paused) 0L else 3L, "outPool" to 21L, "outFree" to if(paused) 21L else 18L, "pendingPayloadFrames" to frames,
            "pendingPayloadDurationUs" to frames * 1_000_000 / 44100, "estimatedCoverageUs" to if(paused) 0L else 2500L,
            "nominalCoverageUs" to 21000L, "lastSubmitAgeUs" to 1000L, "lastCompletionAgeUs" to 500L,
            "pcmDepth" to 8192L, "pcmCapacity" to 16384L, "pcmMinDepth" to 4096L,
            "feedbackValid" to 10L, "feedbackInvalid" to 1L, "acceptedRaw" to 379288L,
            "feedbackOutstanding" to if(paused) 0L else 21L, "feedbackHoldoverUs" to 100000L, "refillDemand" to if(paused || terminal) 0L else 1L,
            "pcmShortageDepth" to -1L, "pcmShortageRequested" to -1L, "pcmShortageAgeUs" to -1L)
        val words = LongArray(UsbTransportTelemetrySchema.WORDS)
        UsbTransportTelemetrySchema.fields.forEachIndexed { i, key -> words[i] = values[key] ?: 0L }
        return words
    }
    private fun LongArray.set(key: String, value: Long) { this[UsbTransportTelemetrySchema.fields.indexOf(key)] = value }
    private fun decode(w: LongArray = packet()) = UsbTransportTelemetrySchema.decode(w, session)
    private fun feedbackRow(seq: Long, raw: Long, rejected: Boolean = false) = listOf(seq, seq * 1000, raw,
        if(rejected) 4L else 0L, if(rejected) 4L else 0L, 4L, 4L, 0L, 0L, if(rejected) 0L else 1L, 1000L, 1L, 0L, 0L, 0L)
    private fun write(w: LongArray, offset: Int, row: List<Long>) { row.forEachIndexed { i, v -> w[offset + i] = v } }
    private fun rejects(w: LongArray) {
        try { decode(w); fail("Malformed snapshot accepted") } catch(_: IllegalArgumentException) { }
    }

    @Test fun validPlayingPausedPreparingTerminalAndUnavailablePcmHaveExplicitMeaning() {
        assertEquals(671, UsbTransportTelemetrySchema.WORDS)
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
        for((key, value) in listOf("version" to 2L, "generation" to 2L, "stream" to 3L,
            "terminal" to 2L, "playbackPhase" to 12L, "readAtUs" to 1L, "capturedAtUs" to 0L,
            "outOutstanding" to 22L, "outFree" to 17L, "outCompleted" to 11L,
            "pendingPayloadDurationUs" to 2994L, "pcmDepth" to 16385L, "gapCount" to 17L,
            "startupCount" to 9L, "rejectedCount" to 9L, "estimatedCoverageUs" to 21001L)) rejects(packet().also { it.set(key, value) })
        rejects(packet().copyOf(670)); rejects(packet().copyOf(672))
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
}
