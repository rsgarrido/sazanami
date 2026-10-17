package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep

internal enum class UsbSilentPhase { CONFIGURING, STARTING_TRANSPORT, TRANSPORT_STOPPED, RESTORING_USB, FINISHED, PRIMING_SOURCE }
internal enum class UsbSilentStatus { NOT_ATTEMPTED, BLOCKED, FAILED, SILENT_TRANSPORT_VERIFIED, NON_AUTHORITATIVE, DETERMINISTIC_PCM_TRANSPORT_VERIFIED,
    BOUNDED_FLAC_USB_TRANSPORT_VERIFIED, FLAC_SOURCE_FAILED, FLAC_INPUT_OPEN_FAILED, QUEUE_PRIME_FAILED,
    SOURCE_EOF_BEFORE_DURATION, QUEUE_UNDERRUN, USB_CONFIGURATION_FAILED, USB_TRANSPORT_FAILED, RESOURCE_CLEANUP_FAILED, CANCELLED, FULL_FLAC_USB_TRANSPORT_VERIFIED, FLAC_MD5_UNAVAILABLE, FLAC_MD5_MISMATCH,
    STREAMINFO_TOTAL_MISMATCH, TERMINAL_TAIL_ACCOUNTING_FAILED, OUT_POOL_STALL, SOURCE_VERIFICATION_STALLED, SEEKED_SEGMENTS_USB_TRANSPORT_VERIFIED,
    FULL_FLAC_USB_TRANSPORT_COMPLETE_MD5_UNAVAILABLE }
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
internal data class UsbIsoNativeSnapshot(val numbers: LongArray, val feedback: LongArray, val packets: LongArray,
    val pcm: LongArray = longArrayOf(),val segments: LongArray=longArrayOf())
internal data class UsbPcmFrameEvidence(val index: Long, val left: Int, val right: Int, val bytes: List<Int>)

internal data class UsbIsoTransportResult(val generation: Long, val streamGeneration: Long,
    val counters: Map<String, Long> = emptyMap(), val feedback: List<UsbIsoFeedbackEvidence> = emptyList(),
    val packets: List<UsbIsoPacketEvidence> = emptyList(), val exceptionType: String? = null,
    val authorityFailure: UsbClockQueryStatus? = null, val pcm: List<UsbPcmFrameEvidence> = emptyList(),val segments: List<List<Long>> = emptyList(),
    val feedbackDiagnostics: UsbFeedbackDiagnosticEvidence? = null) {
    fun count(name: String) = counters[name] ?: 0L
    val pcmFormat get()=UsbDirectPcmFormat.resolve(count("schedulerNominalRateHz"),count("formatChannels"),count("formatBitsPerSample"))
    val bytesPerFrame get()=pcmFormat?.bytesPerFrame?.toLong() ?: 0L
    val formatEvidenceValid get()=pcmFormat?.let { count("formatBytesPerFrame")==it.bytesPerFrame.toLong() &&
        count("formatBytesPerSample")==it.bytesPerSample.toLong() && count("formatSubslotBytes")==it.subslotBytes.toLong() &&
        count("formatValidBits")==it.validBits.toLong() && count("formatAlternate") in 1L..255L }==true
    val integrityReasons get() = UsbIsoNativeStats.reasons.filterIndexed { index, _ -> (count("integrityMask") and (1L shl index)) != 0L }
    val valid get() = exceptionType == null && authorityFailure == null && counters.isNotEmpty() && count("integrityMask") == 0L
    val workerStopped get() = count("workerStopped") == 1L
    val shutdownAccounted get() = workerStopped && count("outstandingUrbsFinal") == 0L
    val state get() = counters["state"]?.let { UsbIsoNativeStats.states.getOrNull(it.toInt()) } ?: "UNAVAILABLE"
    val stopReason get() = counters["stopReason"]?.let { UsbIsoNativeStats.stops.getOrNull(it.toInt()) } ?: "UNAVAILABLE"
    val verified get() = if (count("sourceMode")==3L) {if(count("seekConfirmed")>0L) seekVerified else fullVerified} else valid && formatEvidenceValid && shutdownAccounted && count("started") == 1L && stopReason == "DURATION_COMPLETE" &&
        count("durationUs") >= (if (count("sourceMode")==2L) 5000000L else UsbIsoTiming.DURATION_MS * 1000) && count("framesCompleted") > 0 &&
        count("outUrbsSubmitted") > 0 && count("feedbackPacketsValid") > 0 && count("feedbackScheduledPackets") > 0 &&
        count("sourceFramesGenerated") == count("framesScheduled") && count("framesScheduled") == count("framesSubmitted") &&
        count("framesSubmitted") == count("framesCompleted") && count("outUrbsSubmitted") == count("outUrbsCompleted") &&
        count("feedbackUrbsSubmitted") == count("feedbackUrbsCompleted") && count("outPacketsSubmitted") == count("outPacketsCompleted") &&
        count("outPacketErrors") == 0L && count("outUrbErrors") == 0L && count("queueUnderruns") == 0L &&
        bytesPerFrame>0 && count("framesSubmitted")<=Long.MAX_VALUE/bytesPerFrame &&
        (count("sourceMode")>=2 || (pcmFormat?.bitsPerSample==16 && pcmFormat?.sampleRateHz==44100L)) &&
        sourceVerified && count("framesCancelled") == 0L && count("framesNotSubmitted") == 0L &&
        count("feedbackUrbErrors") == 0L && count("feedbackTimeouts") == 0L && count("shutdownIncomplete") == 0L &&
        count("unknownCompletions") == 0L && count("duplicateCompletions") == 0L && count("outBytesSubmitted") == count("kernelOutActualBytes") &&
        count("outBytesSubmitted") == count("framesSubmitted") * bytesPerFrame &&
        count("outPacketsSubmitted") == count("outUrbsSubmitted") * UsbIsoTiming.OUT_PACKETS
    val sourceVerified get() = when (count("sourceMode")) {
        0L -> count("pcmByteOr") == 0L
        1L -> count("referencePreflightPass") == 1L && count("referenceTableHash") == UsbReferencePreflight.EXPECTED_HASH &&
            counters.containsKey("sourcePcmHash") && counters.containsKey("submittedPcmHash") &&
            count("sourcePcmHash") == count("submittedPcmHash") &&
            count("leftToneFramesGenerated") == 8800L && count("rightToneFramesGenerated") == 8800L &&
            count("referenceActivePerCycle") in 1L..100L &&
            count("leftToneActiveSamples") == 88L * count("referenceActivePerCycle") &&
            count("rightToneActiveSamples") == 88L * count("referenceActivePerCycle") &&
            count("silenceFramesGenerated") + count("leftToneFramesGenerated") + count("rightToneFramesGenerated") == count("sourceFramesGenerated") &&
            count("unexpectedNonZeroSamples") == 0L && count("inactiveChannelNonZeroSamples") == 0L &&
            count("nonZeroSamplesOutsideToneWindows") == 0L && count("maxPositiveSample") == 1024L &&
            count("minNegativeSample") == -1024L && count("peakAbsoluteSample") == 1024L
        2L -> count("transportDurationTargetUs")==5000000L && count("activeTransportUsAtStop")>=5000000L &&
            count("outPoolStallEvents")==0L && count("prefixSourceFramesConsumed")==count("framesSubmitted") && count("prefixInitialPrimedFrames")>=8192L &&
            count("prefixSourceIntegrityMask")==0L && count("prefixProducerStopped")==1L && count("prefixProducerJoined")==1L &&
            counters.containsKey("sourcePcmHash") && counters.containsKey("submittedPcmHash") && count("sourcePcmHash")==count("submittedPcmHash")
        3L -> if(count("seekConfirmed")>0L) seekVerified else fullVerified
        else -> false
    }
    val pauseEvidenceValid get() = counters.keys.containsAll(UsbIsoNativeStats.controlFields) &&
        UsbIsoNativeStats.controlFields.all { count(it)>=0L } && count("playbackTransportState")==UsbFullTransportPhase.ENDED.ordinal.toLong() &&
        count("pauseRequests")>=count("pauseConfirmed") && count("resumeRequests")>=count("resumeConfirmed") &&
        count("pauseConfirmed")>=count("resumeConfirmed") && count("pauseDrainFailures")==0L && count("resumeFailure")==0L &&
        (count("pauseConfirmed")==0L || (count("pauseSourceContinuity")==1L && count("pauseOutstandingOut")==0L && count("pauseDrainOutcome")==1L))
    val seekVerified get()=valid && formatEvidenceValid && shutdownAccounted && counters.keys.containsAll(UsbIsoNativeStats.fields) &&
        count("version")==UsbIsoNativeStats.VERSION.toLong() && count("generation")==generation && count("streamGeneration")==streamGeneration &&
        count("sourceMode")==3L && count("sourceSeekCapable")==1L && count("pauseDrainFailures")==0L && count("resumeFailure")==0L &&
        count("transportDurationTargetUs")==0L && count("sourceSeekAttempts")>=count("seekConfirmed") && count("seekSourceEpoch")>=count("seekConfirmed") &&
        count("confirmedSeekSerial")>0L && count("firstSeekTargetFrame") in 0L until count("streamInfoTotalSamples") &&
        bytesPerFrame>0L && count("seekInvalidatedFrames") in 0L..(Long.MAX_VALUE/bytesPerFrame) && count("seekInvalidatedBytes")==count("seekInvalidatedFrames")*bytesPerFrame &&
        count("started")==1L && stopReason=="NATURAL_EOF_COMPLETE" && count("playbackTransportState")==7L && count("seekConfirmed")>0L &&
        count("seekRequests")==count("seekConfirmed")+count("seekSuperseded") && count("seekFailures")==0L && count("seekOutcome")==5L &&
        count("lastSeekTargetFrame")==count("confirmedSeekTargetFrame") && count("confirmedSeekTargetFrame") in 0L until count("streamInfoTotalSamples") &&
        count("seekDrainOutcome")==1L && count("seekOutstandingOut")==0L && count("seekSourceReady")==1L &&
        count("seekFirstFrameProven")==1L && count("seekFirstSourceFrame")==count("confirmedSeekTargetFrame") &&
        count("seekSegmentIntegrity")==1L && count("seekSegmentCount")==count("seekConfirmed")+1 &&
        count("seekSegmentFrames")==count("framesSubmitted") && count("seekSegmentBytes")==count("outBytesSubmitted") &&
        count("sourcePositionFrame")==count("streamInfoTotalSamples") && count("originalDecoderNextFrame")==count("streamInfoTotalSamples") &&
        count("sourceFramesGenerated")==count("framesSubmitted") && count("prefixSourceFramesConsumed")==count("framesSubmitted") &&
        count("framesScheduled")==count("framesSubmitted") && count("framesSubmitted")==count("framesCompleted") &&
        bytesPerFrame>0 && count("framesSubmitted") in 1L..(Long.MAX_VALUE/bytesPerFrame) && count("outBytesSubmitted")==count("framesSubmitted")*bytesPerFrame &&
        count("outBytesSubmitted")==count("kernelOutActualBytes") && counters["sourcePcmHash"]==counters["submittedPcmHash"] &&
        count("wholeMd5NotVerifiedAfterSeek")==1L && count("md5VerificationPass")==0L && count("resumeReadiness") in 1L..2L &&
        listOf("trueEofReached","producerFinishedNaturally","prefixProducerStopped","prefixProducerJoined").all {count(it)==1L} &&
        listOf("outPacketErrors","outUrbErrors","queueUnderruns","framesCancelled","framesNotSubmitted","feedbackUrbErrors","feedbackTimeouts",
            "shutdownIncomplete","unknownCompletions","duplicateCompletions","outCancelledUrbs","outPoolStallEvents","sourceVerificationStalls",
            "eofPaddingFrames","queueFramesAtNaturalCompletion","prefetchedFramesDiscardedAtNaturalCompletion","prefixSourceIntegrityMask").all {count(it)==0L} &&
        count("seekTailOutUrbs")==1L && count("seekTailPackets") in 1L..8L && count("seekTailFrames")>0L &&
        count("seekTailFrames")==count("seekTailFrameCompletions") && count("seekTailPackets")==count("seekTailPacketCompletions") &&
        count("outUrbsSubmitted")==count("outUrbsCompleted") && count("outPacketsSubmitted")==count("outPacketsCompleted") &&
        count("feedbackUrbsSubmitted")==count("feedbackUrbsCompleted") && seekExamplesValid()
    private fun seekExamplesValid(): Boolean {
        val segmentCount=count("seekSegmentCount")
        if(bytesPerFrame<=0L || segmentCount<=0L || segments.size!=minOf(segmentCount,8L).toInt()) return false
        var frames=0L;var bytes=0L;var previousEpoch=-1L
        var chain=0xcbf29ce484222325UL.toLong()
        for((index,row) in segments.withIndex()) {
            if(row.size!=10 || row[0]<=previousEpoch || (index==0 && (row[0]!=0L || row[1]!=0L)) || row[1]<0L || row[2]<row[1] ||
                row[2]>count("streamInfoTotalSamples") || row[2]-row[1]!=row[3] || row[3]!=row[4] || row[3]>Long.MAX_VALUE/bytesPerFrame ||
                row[5]!=row[3]*bytesPerFrame || row[5]!=row[6] || row[7]!=row[8] || row[9]!=(if(index.toLong()==segmentCount-1) 1L else 0L) ||
                frames>Long.MAX_VALUE-row[3] || bytes>Long.MAX_VALUE-row[5]) return false
            previousEpoch=row[0];frames+=row[3];bytes+=row[5]
            row.forEach { value -> repeat(8) { b -> chain=(chain xor ((value ushr (b*8)) and 255L))*0x100000001b3L } }
        }
        if(frames>count("seekSegmentFrames") || bytes>count("seekSegmentBytes")) return false
        if(segmentCount<=8L) {
            val last=segments.last()
            return frames==count("seekSegmentFrames") && bytes==count("seekSegmentBytes") && chain==count("seekSegmentEvidenceHash") &&
                last[0]==count("seekSourceEpoch") && last[1]==count("confirmedSeekTargetFrame") && last[2]==count("streamInfoTotalSamples") &&
                last[3]==count("completedFramesInSeekSegment")
        }
        return true // All remaining segments are checked/sealed online; the diagnostic cap is not the proof cap.
    }
    private val fullVerified get() = fullCompletionAccounting && count("md5VerificationPass")==1L
    /** Exact host completion; the source snapshot must independently establish reference availability. */
    val fullPlaybackComplete get() = counters.keys.containsAll(UsbIsoNativeStats.fields) &&
        count("version")==UsbIsoNativeStats.VERSION.toLong() && count("generation")==generation && count("streamGeneration")==streamGeneration &&
        count("sourceMode")==3L && count("seekConfirmed")==0L && count("sourceSeekAttempts")==0L &&
        count("md5VerificationPass") in 0L..1L && fullCompletionAccounting
    private val fullCompletionAccounting get() = valid && formatEvidenceValid && pauseEvidenceValid && shutdownAccounted && count("started")==1L && stopReason=="NATURAL_EOF_COMPLETE" &&
        count("transportDurationTargetUs")==0L && bytesPerFrame>0 && count("streamInfoTotalSamples") in 1L..(Long.MAX_VALUE/bytesPerFrame) &&
        count("feedbackPacketsValid")>0L && count("feedbackScheduledPackets")>0L &&
        listOf("sourceFramesGenerated","framesScheduled","framesSubmitted","framesCompleted","prefixSourceFramesConsumed")
            .all { counters[it]==count("streamInfoTotalSamples") } &&
        listOf("outPacketErrors","outUrbErrors","queueUnderruns","framesCancelled","framesNotSubmitted","feedbackUrbErrors",
            "feedbackTimeouts","shutdownIncomplete","unknownCompletions","duplicateCompletions","outCancelledUrbs",
            "outPoolStallEvents","sourceVerificationStalls","eofPaddingFrames","queueFramesAtNaturalCompletion",
            "prefetchedFramesDiscardedAtNaturalCompletion").all { counters[it]==0L } &&
        listOf("trueEofReached","producerFinishedNaturally","prefixProducerStopped","prefixProducerJoined")
            .all { counters[it]==1L } && count("prefixSourceIntegrityMask")==0L &&
        count("finalSubmittedSourceFrame")==count("streamInfoTotalSamples")-1 &&
        count("finalCompletedSourceFrame")==count("streamInfoTotalSamples")-1 &&
        count("outUrbsSubmitted")>0L && count("outUrbsSubmitted")==count("outUrbsCompleted") &&
        count("outPacketsSubmitted")==count("outPacketsCompleted") && count("feedbackUrbsSubmitted")==count("feedbackUrbsCompleted") &&
        count("outBytesSubmitted")==count("streamInfoTotalSamples")*bytesPerFrame && count("kernelOutActualBytes")==count("outBytesSubmitted") &&
        count("terminalTailOutUrbs")==1L && count("terminalTailPackets") in 1L..8L &&
        count("prefixInitialPrimedFrames") in minOf(8192L,count("streamInfoTotalSamples"))..16384L &&
        count("terminalTailFrames") in 1L..count("streamInfoTotalSamples") &&
        count("remainingFramesAtTerminalTransition")==count("terminalTailFrames") &&
        count("terminalTailPacketCompletions")==count("terminalTailPackets") && count("terminalTailFrameCompletions")==count("terminalTailFrames") &&
        count("outPacketsSubmitted")== (count("outUrbsSubmitted")-1)*8+count("terminalTailPackets") &&
        listOf("wholeDecodedPcmHash","allEnqueuedPcmHash","sourcePcmHash","submittedPcmHash")
            .all { counters.containsKey(it) && counters[it]==counters["submittedPcmHash"] }
}

internal object UsbIsoNativeStats {
    const val VERSION = 13
    val historicalFields = listOf("version", "generation", "streamGeneration", "started", "state", "integrityMask", "stopReason", "durationUs",
        "sourceFramesGenerated", "framesScheduled", "framesSubmitted", "framesCompleted", "outUrbsSubmitted", "outUrbsCompleted",
        "outPacketsSubmitted", "outPacketsCompleted", "outBytesSubmitted", "kernelOutActualBytes", "outPacketErrors", "outUrbErrors",
        "feedbackUrbsSubmitted", "feedbackUrbsCompleted", "feedbackPacketsCompleted", "feedbackPacketsValid", "feedbackPacketsInvalid", "feedbackTimeouts",
        "discardedUrbs", "cancellationCompletions", "completionsAfterStop", "unknownCompletions", "duplicateCompletions",
        "outOutstandingAtStop", "feedbackOutstandingAtStop", "outstandingUrbsFinal", "workerStopped", "bootstrapPackets", "feedbackScheduledPackets",
        "lastValidFeedbackAgeUs", "minFeedbackRaw", "maxFeedbackRaw", "sumFeedbackRaw", "feedbackSamples", "invalidStreak", "schedulerRemainder",
        "queueUnderruns", "framesCancelled", "framesNotSubmitted", "pcmByteOr", "submitErrno", "reapErrno", "discardErrno",
        "firstUrbStatus", "firstPacketStatus", "firstPacketLength", "firstPacketActualLength", "outCancelledUrbs", "feedbackCancelledUrbs",
        "feedbackIntervalUs", "dataIntervalUs", "endpointCapacity", "feedbackHoldoverUs", "shutdownIncomplete", "firstUrbErrorCount",
        "feedbackUrbErrors", "lifecycleMask", "sourceMode", "referencePreflightPass", "referenceTableHash", "sourcePcmHash", "submittedPcmHash",
        "silenceFramesGenerated", "leftToneFramesGenerated", "rightToneFramesGenerated", "leftToneActiveSamples", "rightToneActiveSamples",
        "unexpectedNonZeroSamples", "inactiveChannelNonZeroSamples", "nonZeroSamplesOutsideToneWindows",
        "maxPositiveSample", "minNegativeSample", "peakAbsoluteSample", "referenceActivePerCycle",
        "prefixSourceFramesConsumed", "prefixInitialPrimedFrames", "prefixSourceIntegrityMask", "prefixProducerStopped", "prefixProducerJoined",
        "outPoolEmptyAfterReapEvents", "outPoolStallEvents", "outPoolOutstandingAtStall", "outCompletionAgeUsAtStall",
        "transportDurationTargetUs", "activeTransportUsAtStop", "streamInfoTotalSamples", "trueEofReached", "producerFinishedNaturally",
        "remainingFramesAtTerminalTransition", "terminalTailFrames", "terminalTailPackets", "terminalTailOutUrbs", "terminalTailPacketCompletions",
        "terminalTailFrameCompletions", "eofPaddingFrames", "finalSubmittedSourceFrame", "finalCompletedSourceFrame",
        "queueFramesAtNaturalCompletion", "prefetchedFramesDiscardedAtNaturalCompletion", "md5VerificationPass", "wholeDecodedPcmHash",
        "allEnqueuedPcmHash", "sourceVerificationStalls",
        "configuredFeedbackUrbs", "feedbackPacketsPerUrb", "feedbackCoverageUs",
        "feedbackBootstrapTimeouts", "feedbackLastValidWatchdogTimeouts", "feedbackStaleReapedTimeouts",
        "feedbackTimeoutSubtype", "feedbackFailureObservedUs", "feedbackFailureLastValidObservedUs", "feedbackFailureLastValidSubmittedUs",
        "feedbackFailureOffendingSubmittedUs", "feedbackFailureAgeUs", "feedbackFailureOutstandingUrbs", "feedbackFailureReapedNotRearmedUrbs", "feedbackFailureAcceptedRaw",
        "feedbackRequestsOverHoldover", "feedbackMaxRequestAgeUs", "lastAcceptedFeedbackSubmittedUs", "lastAcceptedFeedbackObservedUs", "lastAcceptedFeedbackRequestAgeUs",
        "feedbackFailureReapAttempted", "feedbackFailureReapCount", "feedbackFailureReapFeedbackCount", "feedbackFailureReapRefreshed",
        "feedbackFailureAgeBeforeReapUs", "feedbackFailureAgeAfterReapUs", "feedbackFailureWatchdogEvaluated", "feedbackFailureWatchdogFired",
        "feedbackRecoveredObservationGaps",
        "configuredOutUrbs", "outPacketsPerUrb", "outUrbDurationUs", "outNominalCoverageUs",
        "maxWorkerLoopGapUs", "maxGapObservedAtUs", "outOutstandingAtMaxGap", "feedbackOutstandingAtMaxGap",
        "outRefilledAfterMaxGap", "outRefillNeededAtMaxGap", "workerGapsBeyondOutCoverage",
        "outContinuityObservations", "minOutOutstanding", "outZeroOutstandingObservations", "outOneOutstandingObservations", "outTwoOutstandingObservations",
        "minEstimatedOutCoverageUs", "worstOutCoverageAtUs", "outCoverageBelowOneUrbObservations", "outCoverageBelowTwoUrbsObservations",
        "maxOutSubmissionBatchGapUs", "maxOutSubmissionGapUs", "maxOutCompletionObservedGapUs", "maxOutRefillOpportunityGapUs", "maxOutRefillLatencyUs",
        "outContinuityRiskEvents", "firstOutRiskAtUs", "firstOutRiskMask", "firstOutRiskStage", "firstOutRiskOutstanding", "firstOutRiskFeedbackOutstanding",
        "firstOutRiskWorkerGapUs", "firstOutRiskEstimatedCoverageUs", "firstOutRiskCompletedFrames", "schedulerNominalRateHz", "formatChannels", "formatBitsPerSample", "formatBytesPerSample", "formatBytesPerFrame", "formatSubslotBytes", "formatValidBits", "formatAlternate",
        "playbackTransportState", "pauseRequests", "pauseConfirmed", "resumeRequests", "resumeConfirmed", "lastPauseOutcome", "lastResumeOutcome",
        "firstPauseSourceFrame", "lastPauseSourceFrame", "confirmedPausedFrame", "intentionalPausedUs", "pauseOutstandingOut",
        "pauseDrainOutcome", "resumeReadiness", "resumeFailure", "pauseSourceContinuity", "pauseDrainFailures",
        "sourceSeekCapable","seekRequests","seekConfirmed","seekFailures","seekSuperseded","seekOutcome","firstSeekTargetFrame","lastSeekTargetFrame",
        "confirmedSeekTargetFrame","seekSourceEpoch","oldSegmentSourceBoundary","seekDrainOutcome","seekOutstandingOut","seekInvalidatedFrames","seekInvalidatedBytes",
        "seekSourceReady","seekFirstSourceFrame","seekFirstFrameProven","seekSegmentCount","seekForwardDistanceFrames","seekBackwardDistanceFrames","sourcePositionFrame",
        "completedFramesInSeekSegment","confirmedSeekSerial","seekSegmentIntegrity","seekSegmentEvidenceHash","seekTailOutUrbs","seekTailPackets","seekTailFrames",
        "seekTailPacketCompletions","seekTailFrameCompletions","originalDecoderNextFrame","sourceSeekAttempts","seekSegmentFrames","seekSegmentBytes",
        "wholeMd5NotVerifiedAfterSeek","seekStartedPaused")
    val fields=historicalFields+UsbFeedbackDiagnostics.fields
    val outRiskStages=listOf("WORKER_WAKE","BEFORE_REFILL","AFTER_REFILL")
    val outRiskReasons=listOf("WORKER_GAP_BEYOND_NOMINAL_COVERAGE","NO_APPLICATION_OUT","ESTIMATED_COVERAGE_BELOW_ONE_URB","REFILL_LATENCY_BEYOND_NOMINAL_COVERAGE")
    val feedbackTimeoutSubtypes=listOf("NONE","BOOTSTRAP_TIMEOUT","LAST_VALID_WATCHDOG_TIMEOUT","VALID_OBSERVATION_GAP_AT_REAP")
    val controlFields=fields.subList(182,199)
    val seekFields=historicalFields.drop(199)
    val seekOutcomes=listOf("NONE","REQUESTED","DRAINING","REPOSITIONING","PRIMING","CONFIRMED","SUPERSEDED","FAILED","STOPPED")
    val controlOutcomes=listOf("NONE","REQUESTED","ALREADY","CONFIRMED","SUPERSEDED","TERMINAL","FAILED")
    val states = listOf("IDLE", "CONFIGURED", "STARTING_TRANSPORT", "BOOTSTRAP", "FEEDBACK_LOCKED", "STOP_REQUESTED", "DRAINING_OR_CANCELLING", "TRANSPORT_STOPPED")
    val stops = listOf("DURATION_COMPLETE", "STOP_REQUESTED", "AUTHORITY_LOST", "DEVICE_DETACHED", "SUBMIT_FAILURE", "REAP_FAILURE",
        "FEEDBACK_FAILURE", "QUEUE_UNDERRUN", "CAPACITY_EXCEEDED", "WORKER_FAILURE", "ACCOUNTING_FAILURE", "SOURCE_FAILURE", "SOURCE_EOF_BEFORE_DURATION", "OUT_POOL_STALL", "NATURAL_EOF_COMPLETE", "SOURCE_VERIFICATION_STALLED")
    val reasons = listOf("SUBMIT_ERROR", "REAP_ERROR", "URB_ERROR", "PACKET_ERROR", "SHORT_PACKET", "MALFORMED_FEEDBACK",
        "PERSISTENT_INVALID_FEEDBACK", "FEEDBACK_TIMEOUT", "QUEUE_UNDERRUN", "CAPACITY_EXCEEDED", "UNKNOWN_COMPLETION", "DUPLICATE_COMPLETION",
        "GENERATION_MISMATCH", "DEVICE_DETACHED", "AUTHORITY_CHANGED", "WORKER_FAILURE", "SHUTDOWN_UNACCOUNTED", "FRAME_ACCOUNTING_MISMATCH",
        "NONZERO_PCM", "SCHEDULER_INVALID", "CANCEL_ERROR", "REFERENCE_PREFLIGHT_FAILED", "REFERENCE_TABLE_HASH_MISMATCH",
        "PCM_PACKING_MISMATCH", "SOURCE_HASH_MISMATCH", "INACTIVE_CHANNEL_NONZERO", "NONZERO_OUTSIDE_SIGNAL_WINDOW", "REFERENCE_PEAK_VIOLATION", "FLAC_SOURCE_QUEUE_FAULT", "OUT_POOL_STALL", "TERMINAL_TAIL_ACCOUNTING_FAILED")
    fun validateControl(counters: Map<String,Long>) {
        require(controlFields.all { counters.getValue(it)>=0L } && counters.getValue("playbackTransportState") in 0L..11L &&
            counters.getValue("lastPauseOutcome") in 0L..6L && counters.getValue("lastResumeOutcome") in 0L..6L &&
            counters.getValue("pauseDrainOutcome") in 0L..2L && counters.getValue("resumeReadiness") in 0L..2L &&
            counters.getValue("resumeFailure") in 0L..3L && counters.getValue("pauseSourceContinuity") in 0L..1L)
    }
    fun validateSeek(counters: Map<String,Long>) {
        require(seekFields.all {it=="seekSegmentEvidenceHash" || counters.getValue(it)>=0L} && counters.getValue("seekOutcome") in 0L..8L &&
            listOf("sourceSeekCapable","seekSourceReady","seekFirstFrameProven","seekSegmentIntegrity","wholeMd5NotVerifiedAfterSeek","seekStartedPaused").all {counters.getValue(it) in 0L..1L})
    }
    fun decode(packet: UsbIsoNativeSnapshot, generation: Long, stream: Long): UsbIsoTransportResult {
        require(packet.numbers.size == fields.size && packet.numbers[0] == VERSION.toLong() && packet.numbers[1] == generation &&
            packet.numbers[2] == stream && packet.feedback.size % 6 == 0 && packet.feedback.size <= 12 * 6 &&
            packet.packets.size % 7 == 0 && packet.packets.size <= 24 * 7)
        val counters = fields.zip(packet.numbers.toList()).toMap()
        validateControl(counters)
        validateSeek(counters)
        require(counters.getValue("feedbackTimeoutSubtype") in feedbackTimeoutSubtypes.indices.map { it.toLong() })
        val reapEvidence = historicalFields.subList(131,139).map(counters::getValue)
        require(reapEvidence.all { it == -1L } || (reapEvidence.all { it >= 0L } &&
            listOf(0,3,6,7).all { reapEvidence[it] in 0L..1L } && reapEvidence[1] <= 182L && reapEvidence[2] <= reapEvidence[1]))
        require(counters.getValue("firstOutRiskStage") in outRiskStages.indices.map { it.toLong() })
        require(counters.getValue("state") in states.indices.map { it.toLong() } && counters.getValue("stopReason") in stops.indices.map { it.toLong() })
        require(counters.getValue("sourceMode") in 0L..3L)
        val feedback = packet.feedback.toList().chunked(6).map {
            require(it[0] in 0..0xffffffffL && it[1] in 0..0xffffffffL && it[3] >= 0 && it[4] in UsbFeedbackValidity.entries.indices.map { ordinal -> ordinal.toLong() })
            require(it[5] >= 0)
            UsbIsoFeedbackEvidence(it[0], it[1], it[2].toInt(), it[3], UsbFeedbackValidity.entries[it[4].toInt()], it[5])
        }
        val packets = packet.packets.toList().chunked(7).map {
            require(it[5] == stream && it[6] > 0 && it[0] in 1..255 && it[3] in 0..0xffffffffL && it[4] in 0..0xffffffffL)
            UsbIsoPacketEvidence(it[0].toInt(), it[1].toInt(), it[2].toInt(), it[3], it[4], it[5], it[6])
        }
        require(packet.pcm.size % 9 == 0 && packet.pcm.size <= 13 * 9)
        val format=UsbDirectPcmFormat.resolve(counters.getValue("schedulerNominalRateHz"),counters.getValue("formatChannels"),counters.getValue("formatBitsPerSample"))
        val pcm=packet.pcm.toList().chunked(9).map { decodeDirectPcmExample(it,requireNotNull(format)) }
        require(packet.segments.size%10==0 && packet.segments.size<=80)
        val segments=packet.segments.toList().chunked(10)
        require(segments.all {row -> row.indices.all {it in 7..8 || row[it]>=0L} && row[9] in 0L..1L})
        val diagnostics=UsbFeedbackDiagnostics.decode(packet.numbers.copyOfRange(UsbFeedbackDiagnostics.HISTORICAL_FIELDS,packet.numbers.size),
            counters.getValue("schedulerNominalRateHz"))
        if(diagnostics.exists) {
            val reason=diagnostics.fatal[1]
            val mask=when(reason) {1L->1L shl 6;2L->1L shl 5;3L,7L->1L shl 2;else->1L shl 7}
            require((counters.getValue("integrityMask") and mask)!=0L && counters.getValue("stopReason")== (if(reason==7L) 9L else 6L))
        }
        return UsbIsoTransportResult(generation, stream, counters, feedback, packets, pcm = pcm,segments=segments,feedbackDiagnostics=diagnostics)
    }
}

internal data class UsbSilentTransportExperimentResult(val generation: Long, val streamGeneration: Long,
    val resolution: UsbSilentTransportResolution, val phase: UsbSilentPhase = UsbSilentPhase.FINISHED,
    val status: UsbSilentStatus = UsbSilentStatus.NOT_ATTEMPTED, val configuration: UsbAudioConfigurationResult? = null,
    val timing: UsbIsoTiming? = null, val transport: UsbIsoTransportResult? = null, val detail: String? = null,
    val nativeMemoryReleased: Boolean = true, val connectionCloseRequired: Boolean = false,
    val connectionCloseDisposedUrbs: Long? = null, val outstandingAfterConnectionClose: Long? = null,
    val mode: UsbPcmDiagnosticMode = UsbPcmDiagnosticMode.ZERO, val preflight: UsbReferencePreflight? = null,
    val flacSource: UsbFlacSourceResult? = null, val originalFlacFdClosed: Boolean? = null,
    val nativeExecutionJoined: Boolean = false, val detachedResourceRelease: UsbDetachedResourceRelease? = null) {
    val kernelRecovery get() = configuration?.kernelRecovery ?: UsbKernelBindingRecovery.INDETERMINATE
    val frameworkRecovery get() = configuration?.frameworkRecovery ?: UsbFrameworkEnumerationRecovery.INDETERMINATE
    val functionalRecovery get() = UsbFunctionalAudioRecovery.NOT_VERIFIED
    /** Includes cross-snapshot PCM agreement and cleanup, without changing strict verification. */
    val flacPlaybackComplete get() = originalFlacFdClosed==true && integratedCleanupSafe() && configuration?.let {
        UsbFlacFullStatus.classify(it,transport,flacSource,nativeMemoryReleased) in setOf(
            UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_COMPLETE_MD5_UNAVAILABLE,
            UsbSilentStatus.SEEKED_SEGMENTS_USB_TRANSPORT_VERIFIED)
    }==true
    /** The controller publishes only after closing its original SAF descriptor. */
    fun withFlacInputClosed(closed: Boolean, source: UsbFlacSourceResult?) = copy(
        originalFlacFdClosed = closed, flacSource = source ?: flacSource,
        status = if (status == UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED &&
            (!closed || (source ?: flacSource)?.clean != true)) UsbSilentStatus.RESOURCE_CLEANUP_FAILED
        else if (status==UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED && (!closed || (source ?: flacSource)?.fullComplete!=true))
            UsbSilentStatus.RESOURCE_CLEANUP_FAILED
        else if (status==UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_COMPLETE_MD5_UNAVAILABLE &&
            (!closed || (source ?: flacSource)?.referenceUnavailableComplete!=true)) UsbSilentStatus.RESOURCE_CLEANUP_FAILED
        else if(status==UsbSilentStatus.SEEKED_SEGMENTS_USB_TRANSPORT_VERIFIED && (!closed || (source ?: flacSource)?.seekComplete!=true))
            UsbSilentStatus.RESOURCE_CLEANUP_FAILED else status)
    fun invalidate(failure: UsbClockQueryStatus) = copy(status = UsbSilentStatus.NON_AUTHORITATIVE,
        detail = "Attachment/permission authority changed; transaction evidence is historical only.",
        configuration = configuration?.invalidate(failure, "Session 7 authority changed."), transport = transport?.copy(authorityFailure = failure))
}
