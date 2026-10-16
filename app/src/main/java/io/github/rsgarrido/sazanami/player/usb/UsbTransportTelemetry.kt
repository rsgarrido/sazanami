package io.github.rsgarrido.sazanami.player.usb

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class UsbTelemetrySession(val generation: Long, val stream: Long)
internal data class UsbTransportTelemetry(
    val values: Map<String, Long>, val feedback: UsbFeedbackDiagnosticEvidence,
    val rejected: List<List<Long>>, val gaps: List<List<Long>>, val startups: List<List<Long>>
) {
    val session get() = UsbTelemetrySession(n("generation"), n("stream"))
    val terminal get() = n("terminal") == 1L
    fun n(name: String) = values.getValue(name)
}

/** Independent schema; the existing terminal/proof schema and its acceptance guards are unchanged. */
internal object UsbTransportTelemetrySchema {
    val fields = listOf("version", "generation", "stream", "sourceEpoch", "activeSeekSerial", "capturedAtUs", "readAtUs", "terminal",
        "transportState", "playbackPhase", "sampleRateHz", "channels", "bits", "bytesPerFrame", "startedAtUs",
        "outSubmitted", "outCompleted", "outOutstanding", "outPool", "outFree", "pendingPayloadFrames",
        "pendingPayloadDurationUs", "estimatedCoverageUs", "nominalCoverageUs", "lastSubmitAgeUs", "lastCompletionAgeUs",
        "maxSubmitGapUs", "maxBatchGapUs", "maxCompletionGapUs", "maxWorkerGapUs", "maxRefillGapUs", "maxRefillLatencyUs",
        "poolStalls", "emptyAfterReap", "outUrbErrors", "outPacketErrors", "outCancelled", "submitErrno", "reapErrno",
        "pcmDepth", "pcmCapacity", "pcmMinDepth", "pcmEmpty", "pcmUnderruns", "producerFullWaits", "pcmPrepared",
        "sourceFaults", "waitingForEof", "feedbackValid", "feedbackInvalid", "invalidStreak", "acceptedRaw",
        "feedbackOutstanding", "feedbackLastAcceptedAgeUs", "feedbackHoldoverUs", "feedbackTimeouts", "feedbackErrors",
        "feedbackLastObservationAgeUs", "refillDemand", "integrityMask", "stopReason", "gapCount", "startupCount", "rejectedCount", "feedbackRecoveredGaps",
        "pcmShortageDepth", "pcmShortageRequested", "pcmShortageAgeUs",
        "feedbackAcceptedDecisions", "feedbackRejectedDecisions", "feedbackIgnoredDecisions")
    const val FIELD_COUNT = 71
    const val FEEDBACK_OFFSET = FIELD_COUNT
    const val REJECTED_OFFSET = FEEDBACK_OFFSET + UsbFeedbackDiagnostics.WORDS
    const val GAP_OFFSET = REJECTED_OFFSET + 8 * UsbFeedbackDiagnostics.ROW_WORDS
    const val STARTUP_OFFSET = GAP_OFFSET + 16 * 8
    const val WORDS = STARTUP_OFFSET + 8 * 10

    fun decode(words: LongArray, session: UsbTelemetrySession): UsbTransportTelemetry {
        require(fields.size == FIELD_COUNT && words.size == WORDS)
        val values = fields.zip(words.take(FIELD_COUNT)).toMap()
        fun n(key: String) = values.getValue(key)
        require(n("version") == 1L && session.generation > 0 && session.stream > 0 &&
            n("generation") == session.generation && n("stream") == session.stream)
        require(n("capturedAtUs") > 0 && n("readAtUs") >= n("capturedAtUs"))
        require(n("terminal") in 0L..1L && n("playbackPhase") in -1L..11L && n("transportState") in 0L..7L &&
            n("stopReason") in UsbIsoNativeStats.stops.indices.map(Int::toLong))
        if(n("terminal") == 1L) require(n("transportState") == 7L && n("playbackPhase") in listOf(-1L, 6L, 7L, 8L))
        require(n("sourceEpoch") >= 0 && n("activeSeekSerial") >= 0)
        require(UsbDirectPcmFormat.resolve(n("sampleRateHz"), n("channels"), n("bits"))?.bytesPerFrame?.toLong() == n("bytesPerFrame"))
        require(n("outPool") in 1L..161L && n("outOutstanding") in 0L..n("outPool") &&
            n("outFree") == n("outPool") - n("outOutstanding"))
        require(n("outCompleted") >= 0 && n("outSubmitted") >= 0)
        if(n("integrityMask") == 0L) require(n("outCompleted") <= n("outSubmitted") && n("outSubmitted") - n("outCompleted") == n("outOutstanding"))
        require(n("pendingPayloadFrames") in 0L..Long.MAX_VALUE / 1_000_000L &&
            n("pendingPayloadDurationUs") == n("pendingPayloadFrames") * 1_000_000L / n("sampleRateHz"))
        require(n("nominalCoverageUs") > 0 && n("estimatedCoverageUs") in 0L..n("nominalCoverageUs"))
        val optionalTimes = listOf("lastSubmitAgeUs", "lastCompletionAgeUs", "feedbackLastAcceptedAgeUs", "feedbackLastObservationAgeUs")
        require(optionalTimes.all { n(it) >= -1L })
        require(n("startedAtUs") == -1L || n("startedAtUs") in 1L..n("capturedAtUs"))
        val counters = fields.subList(26, 39) + fields.subList(48, 53) + listOf("feedbackHoldoverUs", "feedbackTimeouts", "feedbackErrors", "integrityMask")
        require(counters.all { n(it) >= 0L } && n("feedbackRecoveredGaps") >= 0 && n("waitingForEof") in 0L..1L && n("refillDemand") in 0L..1L)
        require(listOf("feedbackAcceptedDecisions", "feedbackRejectedDecisions", "feedbackIgnoredDecisions").all { n(it) >= 0 })
        if(n("pcmDepth") == -1L) require(fields.subList(39, 47).all { n(it) == -1L })
        else require(n("pcmCapacity") > 0 && n("pcmDepth") in 0L..n("pcmCapacity") &&
            n("pcmMinDepth") in -1L..n("pcmCapacity") && n("pcmPrepared") in 0L..n("pcmCapacity") &&
            fields.subList(42, 47).all { n(it) >= 0 })
        if(n("pcmShortageAgeUs") == -1L) require(n("pcmShortageDepth") == -1L && n("pcmShortageRequested") == -1L)
        else require(n("pcmShortageAgeUs") >= 0 && n("pcmShortageRequested") in 1L..n("pcmCapacity") &&
            n("pcmShortageDepth") in 0L until n("pcmShortageRequested"))
        val feedback = UsbFeedbackDiagnostics.decode(words.copyOfRange(FEEDBACK_OFFSET, REJECTED_OFFSET), n("sampleRateHz"))
        fun rows(offset: Int, count: Long, capacity: Int, width: Int): List<List<Long>> {
            require(count in 0L..capacity.toLong())
            require(words.slice(offset + count.toInt() * width until offset + capacity * width).all { it == 0L })
            return (0 until count.toInt()).map { words.slice(offset + it * width until offset + (it + 1) * width) }
        }
        val rejected = rows(REJECTED_OFFSET, n("rejectedCount"), 8, 15)
        rejected.forEachIndexed { index, row ->
            // Reuse the established feedback validator for each sparse row; sequence gaps are intentional.
            val single = LongArray(UsbFeedbackDiagnostics.WORDS); single[28] = 1
            row.forEachIndexed { i, value -> single[32 + i] = value }
            UsbFeedbackDiagnostics.decode(single, n("sampleRateHz"))
            require(row[4] in 1L..6L && (index == 0 || row[0] > rejected[index - 1][0]))
        }
        val gaps = rows(GAP_OFFSET, n("gapCount"), 16, 8)
        gaps.forEachIndexed { index, row ->
            require(row[0] > 0 && row[1] >= 0 && row[2] in 1L..2L && row[3] > n("nominalCoverageUs") &&
                row[4] in 0L..n("outPool") && row[5] >= -1 && row[6] in 0L..n("sourceEpoch") && row[7] >= 0)
            if(index > 0) require(row[0] == gaps[index - 1][0] + 1 && row[1] >= gaps[index - 1][1])
        }
        val startups = rows(STARTUP_OFFSET, n("startupCount"), 8, 10)
        startups.forEachIndexed { index, row ->
            require(row[0] in 0L..n("sourceEpoch") && row[1] >= 0 && row[2] in 1L..n("capturedAtUs") && row[3] >= 0)
            require(row.drop(4).all { it >= -1 && (it == -1L || it <= n("capturedAtUs") - row[2]) })
            if(index > 0) require(row[0] > startups[index - 1][0])
        }
        return UsbTransportTelemetry(values, feedback, rejected, gaps, startups)
    }
}

internal data class UsbTransportTelemetryView(val session: UsbTelemetrySession? = null,
    val status: String = "IDLE", val snapshot: UsbTransportTelemetry? = null, val captureFailure: String? = null)

/** Only immutable copies survive a run. Never holds its monitor while invoking a native provider. */
internal class UsbTransportTelemetryStore {
    private val mutableState = MutableStateFlow(UsbTransportTelemetryView())
    val state = mutableState.asStateFlow()
    private var provider: (() -> UsbTransportTelemetry?)? = null
    @Synchronized fun begin(session: UsbTelemetrySession) {
        provider = null; mutableState.value = UsbTransportTelemetryView(session, "PREPARING")
    }
    @Synchronized fun bind(session: UsbTelemetrySession, read: () -> UsbTransportTelemetry?) {
        val current = mutableState.value
        if(current.session == session && current.status != "TERMINAL") {
            provider = read
            if(current.status == "PREPARING") mutableState.value = current.copy(status = "AVAILABLE")
        }
    }
    fun capture(): UsbTransportTelemetryView {
        val request = synchronized(this) {
            mutableState.value.takeUnless { it.status == "TERMINAL" }?.session?.let { it to provider }
        }
        val result = request?.second?.let { runCatching(it) }
        synchronized(this) {
            val current = mutableState.value
            if(request != null && current.session == request.first && current.status != "TERMINAL" && result != null) {
                val value = result.getOrNull()?.takeIf { it.session == request.first }
                val prior = current.snapshot
                val ordered = value != null && (prior == null || (value.n("capturedAtUs") >= prior.n("capturedAtUs") && value.n("readAtUs") >= prior.n("readAtUs")))
                mutableState.value = if(value != null && ordered) current.copy(snapshot = value,
                    status = if(value.terminal) "TERMINAL" else "LIVE", captureFailure = null)
                else if(value == null) current.copy(captureFailure = "Native snapshot unavailable; any prior copy is retained with its original capture time.")
                else current
                if(value != null && ordered && value.terminal) provider = null
            }
            return mutableState.value
        }
    }
    @Synchronized fun finish(session: UsbTelemetrySession, terminal: UsbTransportTelemetry? = null) {
        val current = mutableState.value
        if(current.session != session) return
        provider = null
        val terminalCopy = terminal?.takeIf { it.session == session && it.terminal }
        mutableState.value = current.copy(status = "TERMINAL", snapshot = terminalCopy ?: current.snapshot,
            captureFailure = if(terminalCopy != null) null else current.captureFailure)
    }
}

internal object UsbTransportTelemetryBridge {
    val store = UsbTransportTelemetryStore()
    val state get() = store.state
    suspend fun capture() = withContext(Dispatchers.IO) { store.capture() }
}

internal fun formatUsbTransportTelemetry(view: UsbTransportTelemetryView = UsbTransportTelemetryBridge.state.value): String = buildString {
    appendLine("Live native USB transport diagnostics — Session 25")
    appendLine("Capture status: ${view.status}; attachment generation=${view.session?.generation ?: "unavailable"}; native stream/session=${view.session?.stream ?: "unavailable"}")
    view.captureFailure?.let { appendLine(it) }
    val s = view.snapshot
    if(s == null) { appendLine("Snapshot unavailable: no successful capture for this run yet, native transport is not available, or no run exists."); return@buildString }
    fun n(key: String) = s.n(key)
    fun value(key: String) = n(key).let { if(it < 0) "unavailable" else it.toString() }
    val phase = if(n("playbackPhase") < 0) "unavailable" else UsbFullTransportPhase.entries[n("playbackPhase").toInt()].name
    appendLine("${if(view.status == "TERMINAL") "TERMINAL HISTORICAL" else "LIVE observation"}; native terminal=${s.terminal}; source epoch=${n("sourceEpoch")}; active seek serial=${n("activeSeekSerial")}")
    appendLine("Transport=${UsbIsoNativeStats.states[n("transportState").toInt()]}; playback=$phase; nominal=${n("sampleRateHz")} Hz / ${n("channels")} channels / ${n("bits")} bits / ${n("bytesPerFrame")} bytes per frame")
    appendLine("Terminal stop cause=${if(s.terminal) UsbIsoNativeStats.stops[n("stopReason").toInt()] else "unavailable (snapshot is not terminal)"}")
    appendLine("Host monotonic capture/read=${n("capturedAtUs")}/${n("readAtUs")} us; snapshot age at retrieval=${n("readAtUs") - n("capturedAtUs")} us; transport started=${value("startedAtUs")} us")
    if(view.status == "TERMINAL" && !s.terminal) appendLine("Final native snapshot unavailable; retained last live observation is historical.")
    appendLine("USB OUT: submitted=${n("outSubmitted")}; reaped=${n("outCompleted")}; application outstanding=${n("outOutstanding")}; pool slots=${n("outPool")}; free=${n("outFree")}")
    appendLine("Pending payload=${n("pendingPayloadFrames")} frames / ${value("pendingPayloadDurationUs")} us at nominal rate; host FIFO timing estimate=${n("estimatedCoverageUs")} us; configured nominal coverage=${n("nominalCoverageUs")} us")
    appendLine("Actual kernel-armed queued audio duration: unavailable. Application outstanding includes completed but unreaped URBs; payload duration is not remaining wire audio.")
    appendLine("Last successful OUT submit/reap ages at snapshot=${value("lastSubmitAgeUs")}/${value("lastCompletionAgeUs")} us; worker-phase refill demand=${n("refillDemand")} (pending control may still block admission)")
    appendLine("Maximum gaps us: submit=${n("maxSubmitGapUs")}; submission batch=${n("maxBatchGapUs")}; completion observation=${n("maxCompletionGapUs")}; worker wake=${n("maxWorkerGapUs")}; refill opportunity=${n("maxRefillGapUs")}; reap-to-refill latency=${n("maxRefillLatencyUs")}")
    appendLine("OUT pool stalls=${n("poolStalls")}; empty pool after reap=${n("emptyAfterReap")}; URB/packet errors=${n("outUrbErrors")}/${n("outPacketErrors")}; cancelled=${n("outCancelled")}; submit/reap errno=${n("submitErrno")}/${n("reapErrno")}; integrity mask=${n("integrityMask")}")
    appendLine("PCM: available=${value("pcmDepth")} frames; capacity=${value("pcmCapacity")} frames; streaming minimum=${value("pcmMinDepth")} frames; reserved=${value("pcmPrepared")} frames")
    appendLine("PCM insufficient reservation events=${value("pcmEmpty")}; underruns=${value("pcmUnderruns")}; producer full-buffer waits=${value("producerFullWaits")}; source faults=${value("sourceFaults")}; awaiting EOF verification=${n("waitingForEof")}")
    appendLine("Last insufficient PCM decision: observed available=${value("pcmShortageDepth")} frames; requested=${value("pcmShortageRequested")} frames; age=${value("pcmShortageAgeUs")} us. This is the occupancy used by that decision, not a later buffer sample.")
    appendLine("PCM wait duration: unavailable (active reserve fails on insufficient PCM). Producer full waits indicate backpressure. No new waiting or buffering policy is applied.")
    appendLine("Recent long gaps (maximum 16; exceeding configured coverage; host scheduling observations, not confirmed audible dropouts):")
    appendLine("seq | sessionElapsedUs | kind | gapUs | appOut | pcmFrames | epoch | seekSerial")
    if(s.gaps.isEmpty()) appendLine("None recorded.")
    s.gaps.forEach { row -> appendLine(row.joinToString(" | ") { if(it < 0) "unavailable" else it.toString() }) }
    appendLine("Gap kind: 1=worker wake, 2=successful OUT submission; PCM depth is sampled near that observation. Submission observations include the just-reserved candidate until commit. Intentional quiet intervals are excluded.")
    appendLine("Feedback: nominal=${n("sampleRateHz")} Hz; valid/invalid processed=${n("feedbackValid")}/${n("feedbackInvalid")}; consecutive invalid=${n("invalidStreak")}; held accepted raw=${if(n("acceptedRaw") > 0) value("acceptedRaw") else "unavailable (nominal bootstrap/quiet reset)"}")
    appendLine("Feedback outstanding=${n("feedbackOutstanding")}; last accepted/last retained observation ages at snapshot=${value("feedbackLastAcceptedAgeUs")}/${value("feedbackLastObservationAgeUs")} us; holdover=${n("feedbackHoldoverUs")} us; timeouts/errors=${n("feedbackTimeouts")}/${n("feedbackErrors")}")
    appendLine("Feedback observation gaps recovered before watchdog=${n("feedbackRecoveredGaps")}; OUT counter accounting agrees=${n("outSubmitted") - n("outCompleted") == n("outOutstanding")}")
    appendLine("Recorded feedback decisions: accepted=${n("feedbackAcceptedDecisions")}; rejected=${n("feedbackRejectedDecisions")}; ignored quiet/terminal=${n("feedbackIgnoredDecisions")} (stop counting when the existing recorder freezes at first fatal feedback decision)")
    appendLine("Feedback policy unchanged: +/-5%, three consecutive invalid; no accepted-rate override. Missing/stale evidence is age/timeout evidence; quiet playback intentionally stops feedback admission. Valid processed counts can include terminal drain observations that do not select a rate.")
    fun feedbackRows(title: String, rows: List<List<Long>>) {
        appendLine(title)
        appendLine("seq | sessionElapsedUs | raw | decodedHz (interpretation) | validity/reason | accepted | requestAgeUs | phase/epoch/confirmedSeek | requested/actualBytes | urb/packet/errors")
        if(rows.isEmpty()) appendLine("No recorded observations.")
        rows.forEach { r ->
            fun number(index: Int) = if(r[index] < 0) "unavailable" else r[index].toString()
            val hz = if(r[2] >= 0 && r[3] in listOf(0L, 2L, 4L)) String.format(Locale.ROOT, "%.5f", r[2] * 8000.0 / 65536.0) else "unavailable"
            appendLine("${r[0]} | ${number(1)} | ${number(2)} | $hz | ${UsbFeedbackValidity.entries[r[3].toInt()]}/${UsbFeedbackDiagnostics.rejections[r[4].toInt()]} | ${r[9]} | ${number(10)} | ${number(11)}/${number(12)}/${number(13)} | ${r[5]}/${r[6]} | ${r[7]}/${r[8]}/${r[14]}")
        }
    }
    feedbackRows("Recent actual feedback decisions (maximum 16; frozen at first fatal feedback decision):", s.feedback.history)
    feedbackRows("Recent rejected feedback retained across valid-sample churn (maximum 8):", s.rejected)
    appendLine("Source startup (maximum 8 epochs; host observations; durations from each source/seek origin, in us):")
    appendLine("epoch | seekSerial | monotonicOriginUs | targetFrame | metadataUs | firstPcmUs | prebufferUs | firstOutSubmitUs | firstSuccessfulOutReapUs | nativePlayingUs")
    if(s.startups.isEmpty()) appendLine("Unavailable: no native FLAC source milestones published.")
    s.startups.forEach { row -> appendLine(row.joinToString(" | ") { if(it < 0) "unavailable" else it.toString() }) }
    appendLine("Source admission begins when native PrefixSource is constructed; seek origin is worker command admission. Metadata readiness is only measured for initial preparation. USB submit/reap and native Playing do not prove audible DAC output.")
    appendLine("Snapshot publication is coherent; producer PCM atomics are sampled over a short interval. Quiet/blocked worker snapshots retain their capture time; capture age must be considered. Diagnostic capture performs no USB work and changes no player controls.")
}
