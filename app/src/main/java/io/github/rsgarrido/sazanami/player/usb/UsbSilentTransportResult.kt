package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep

internal enum class UsbSilentPhase { CONFIGURING, STARTING_TRANSPORT, TRANSPORT_STOPPED, RESTORING_USB, FINISHED }
internal enum class UsbSilentStatus { NOT_ATTEMPTED, BLOCKED, FAILED, SILENT_TRANSPORT_VERIFIED, NON_AUTHORITATIVE }
internal data class UsbIsoFeedbackEvidence(val raw: Long, val length: Long, val packetStatus: Int, val elapsedUs: Long,
    val validity: UsbFeedbackValidity, val conservativeAgeUs: Long) {
    val bytes get() = (0 until minOf(length, 4L).toInt()).map { ((raw shr (it * 8)) and 0xff).toInt() }
    val rateHz get() = if (validity == UsbFeedbackValidity.VALID) raw * 8000.0 / 65536.0 else null
    fun framesPerServiceInterval(intervalUs: Long) = if (validity == UsbFeedbackValidity.VALID)
        raw * (intervalUs / 125.0) / 65536.0 else null
}
internal data class UsbIsoPacketEvidence(val endpoint: Int, val urbStatus: Int, val packetStatus: Int,
    val requestedLength: Long, val actualLength: Long, val streamGeneration: Long, val submission: Long)

@Keep
internal data class UsbIsoNativeSnapshot(val numbers: LongArray, val feedback: LongArray, val packets: LongArray)

internal data class UsbIsoTransportResult(val generation: Long, val streamGeneration: Long,
    val counters: Map<String, Long> = emptyMap(), val feedback: List<UsbIsoFeedbackEvidence> = emptyList(),
    val packets: List<UsbIsoPacketEvidence> = emptyList(), val exceptionType: String? = null,
    val authorityFailure: UsbClockQueryStatus? = null) {
    fun count(name: String) = counters[name] ?: 0L
    val integrityReasons get() = UsbIsoNativeStats.reasons.filterIndexed { index, _ -> (count("integrityMask") and (1L shl index)) != 0L }
    val valid get() = exceptionType == null && authorityFailure == null && counters.isNotEmpty() && count("integrityMask") == 0L
    val workerStopped get() = count("workerStopped") == 1L
    val shutdownAccounted get() = workerStopped && count("outstandingUrbsFinal") == 0L
    val state get() = counters["state"]?.let { UsbIsoNativeStats.states.getOrNull(it.toInt()) } ?: "UNAVAILABLE"
    val stopReason get() = counters["stopReason"]?.let { UsbIsoNativeStats.stops.getOrNull(it.toInt()) } ?: "UNAVAILABLE"
    val verified get() = valid && shutdownAccounted && count("started") == 1L && stopReason == "DURATION_COMPLETE" &&
        count("durationUs") >= UsbIsoTiming.DURATION_MS * 1000 && count("framesCompleted") > 0 &&
        count("outUrbsSubmitted") > 0 && count("feedbackPacketsValid") > 0 && count("feedbackScheduledPackets") > 0 &&
        count("sourceFramesGenerated") == count("framesScheduled") && count("framesScheduled") == count("framesSubmitted") &&
        count("framesSubmitted") == count("framesCompleted") && count("outUrbsSubmitted") == count("outUrbsCompleted") &&
        count("feedbackUrbsSubmitted") == count("feedbackUrbsCompleted") && count("outPacketsSubmitted") == count("outPacketsCompleted") &&
        count("outPacketErrors") == 0L && count("outUrbErrors") == 0L && count("queueUnderruns") == 0L &&
        count("pcmByteOr") == 0L && count("framesCancelled") == 0L && count("framesNotSubmitted") == 0L &&
        count("feedbackUrbErrors") == 0L && count("feedbackTimeouts") == 0L && count("shutdownIncomplete") == 0L &&
        count("unknownCompletions") == 0L && count("duplicateCompletions") == 0L && count("outBytesSubmitted") == count("kernelOutActualBytes") &&
        count("outBytesSubmitted") == count("framesSubmitted") * UsbIsoTiming.BYTES_PER_FRAME &&
        count("outPacketsSubmitted") == count("outUrbsSubmitted") * UsbIsoTiming.OUT_PACKETS
}

internal object UsbIsoNativeStats {
    const val VERSION = 1
    val fields = listOf("version", "generation", "streamGeneration", "started", "state", "integrityMask", "stopReason", "durationUs",
        "sourceFramesGenerated", "framesScheduled", "framesSubmitted", "framesCompleted", "outUrbsSubmitted", "outUrbsCompleted",
        "outPacketsSubmitted", "outPacketsCompleted", "outBytesSubmitted", "kernelOutActualBytes", "outPacketErrors", "outUrbErrors",
        "feedbackUrbsSubmitted", "feedbackUrbsCompleted", "feedbackPacketsCompleted", "feedbackPacketsValid", "feedbackPacketsInvalid", "feedbackTimeouts",
        "discardedUrbs", "cancellationCompletions", "completionsAfterStop", "unknownCompletions", "duplicateCompletions",
        "outOutstandingAtStop", "feedbackOutstandingAtStop", "outstandingUrbsFinal", "workerStopped", "bootstrapPackets", "feedbackScheduledPackets",
        "lastValidFeedbackAgeUs", "minFeedbackRaw", "maxFeedbackRaw", "sumFeedbackRaw", "feedbackSamples", "invalidStreak", "schedulerRemainder",
        "queueUnderruns", "framesCancelled", "framesNotSubmitted", "pcmByteOr", "submitErrno", "reapErrno", "discardErrno",
        "firstUrbStatus", "firstPacketStatus", "firstPacketLength", "firstPacketActualLength", "outCancelledUrbs", "feedbackCancelledUrbs",
        "feedbackIntervalUs", "dataIntervalUs", "endpointCapacity", "feedbackHoldoverUs", "shutdownIncomplete", "firstUrbErrorCount",
        "feedbackUrbErrors", "lifecycleMask")
    val states = listOf("IDLE", "CONFIGURED", "STARTING_TRANSPORT", "BOOTSTRAP", "FEEDBACK_LOCKED", "STOP_REQUESTED", "DRAINING_OR_CANCELLING", "TRANSPORT_STOPPED")
    val stops = listOf("DURATION_COMPLETE", "STOP_REQUESTED", "AUTHORITY_LOST", "DEVICE_DETACHED", "SUBMIT_FAILURE", "REAP_FAILURE",
        "FEEDBACK_FAILURE", "QUEUE_UNDERRUN", "CAPACITY_EXCEEDED", "WORKER_FAILURE", "ACCOUNTING_FAILURE")
    val reasons = listOf("SUBMIT_ERROR", "REAP_ERROR", "URB_ERROR", "PACKET_ERROR", "SHORT_PACKET", "MALFORMED_FEEDBACK",
        "PERSISTENT_INVALID_FEEDBACK", "FEEDBACK_TIMEOUT", "QUEUE_UNDERRUN", "CAPACITY_EXCEEDED", "UNKNOWN_COMPLETION", "DUPLICATE_COMPLETION",
        "GENERATION_MISMATCH", "DEVICE_DETACHED", "AUTHORITY_CHANGED", "WORKER_FAILURE", "SHUTDOWN_UNACCOUNTED", "FRAME_ACCOUNTING_MISMATCH",
        "NONZERO_PCM", "SCHEDULER_INVALID", "CANCEL_ERROR")
    fun decode(packet: UsbIsoNativeSnapshot, generation: Long, stream: Long): UsbIsoTransportResult {
        require(packet.numbers.size == fields.size && packet.numbers[0] == VERSION.toLong() && packet.numbers[1] == generation &&
            packet.numbers[2] == stream && packet.feedback.size % 6 == 0 && packet.feedback.size <= 12 * 6 &&
            packet.packets.size % 7 == 0 && packet.packets.size <= 24 * 7)
        val counters = fields.zip(packet.numbers.toList()).toMap()
        require(counters.getValue("state") in states.indices.map { it.toLong() } && counters.getValue("stopReason") in stops.indices.map { it.toLong() })
        val feedback = packet.feedback.toList().chunked(6).map {
            require(it[0] in 0..0xffffffffL && it[1] in 0..0xffffffffL && it[3] >= 0 && it[4] in UsbFeedbackValidity.entries.indices.map { ordinal -> ordinal.toLong() })
            require(it[5] >= 0)
            UsbIsoFeedbackEvidence(it[0], it[1], it[2].toInt(), it[3], UsbFeedbackValidity.entries[it[4].toInt()], it[5])
        }
        val packets = packet.packets.toList().chunked(7).map {
            require(it[5] == stream && it[6] > 0 && it[0] in 1..255 && it[3] in 0..0xffffffffL && it[4] in 0..0xffffffffL)
            UsbIsoPacketEvidence(it[0].toInt(), it[1].toInt(), it[2].toInt(), it[3], it[4], it[5], it[6])
        }
        return UsbIsoTransportResult(generation, stream, counters, feedback, packets)
    }
}

internal data class UsbSilentTransportExperimentResult(val generation: Long, val streamGeneration: Long,
    val resolution: UsbSilentTransportResolution, val phase: UsbSilentPhase = UsbSilentPhase.FINISHED,
    val status: UsbSilentStatus = UsbSilentStatus.NOT_ATTEMPTED, val configuration: UsbAudioConfigurationResult? = null,
    val timing: UsbIsoTiming? = null, val transport: UsbIsoTransportResult? = null, val detail: String? = null,
    val nativeMemoryReleased: Boolean = true, val connectionCloseRequired: Boolean = false,
    val connectionCloseDisposedUrbs: Long? = null, val outstandingAfterConnectionClose: Long? = null) {
    val kernelRecovery get() = configuration?.kernelRecovery ?: UsbKernelBindingRecovery.INDETERMINATE
    val frameworkRecovery get() = configuration?.frameworkRecovery ?: UsbFrameworkEnumerationRecovery.INDETERMINATE
    val functionalRecovery get() = UsbFunctionalAudioRecovery.NOT_VERIFIED
    fun invalidate(failure: UsbClockQueryStatus) = copy(status = UsbSilentStatus.NON_AUTHORITATIVE,
        detail = "Attachment/permission authority changed; transaction evidence is historical only.",
        configuration = configuration?.invalidate(failure, "Session 7 authority changed."), transport = transport?.copy(authorityFailure = failure))
}
