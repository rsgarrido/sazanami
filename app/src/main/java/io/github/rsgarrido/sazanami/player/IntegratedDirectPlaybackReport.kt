package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.pcmHashHex
import io.github.rsgarrido.sazanami.player.usb.selectedRateMatches
import io.github.rsgarrido.sazanami.player.usb.selectedFormatMatches
import io.github.rsgarrido.sazanami.player.usb.UsbDirectPcmFormat
import io.github.rsgarrido.sazanami.player.usb.UsbIsoNativeStats
import io.github.rsgarrido.sazanami.player.usb.UsbIsoTiming
import io.github.rsgarrido.sazanami.player.usb.UsbFullTransportPhase
import io.github.rsgarrido.sazanami.player.usb.formatFeedbackDiagnostics
import java.util.Locale

internal fun formatIntegratedDirectPlayback(state: IntegratedDirectPlaybackState,locale: Locale=Locale.getDefault()): String=buildString {
    append(io.github.rsgarrido.sazanami.player.usb.formatUsbTransportTelemetry())
    appendLine("Integrated Direct USB Playback")
    appendLine("experimental direct USB enabled=${state.enabled}; requested backend=${if(state.enabled) "DIRECT_USB" else "NORMAL"}; authoritative backend=${if(state.enabled && !state.normalReleased) "NONE_BLOCKED" else if(state.enabled) "DIRECT_USB" else "NORMAL"}")
    appendLine("safe library ID=${state.libraryId ?: "none"}; request generation=${state.generation}; current track only; no raw URI/path")
    appendLine("normal pipelines synchronously released=${state.normalReleased}; standby/preload inactive=${state.normalReleased}; crossfade inactive=${state.normalReleased}")
    appendLine("integrated state=${state.phase}; completed frames=${state.completedFrames} (cumulative delivered); original source frame=${state.sourcePositionFrame ?: state.completedFrames}; source epoch=${state.sourceEpoch}; position ms=${state.positionMs}; duration ms=${state.durationMs}; STREAMINFO total=${state.totalFrames}")
    appendLine("requested playWhenReady=${state.playWhenReady}; latest native lifecycle=${state.nativeTransportPhase}; app Pause/Resume requests=${state.pauseRequests}/${state.resumeRequests} (native confirmations below require snapshot evidence)")
    appendLine("error=${state.error ?: "none"}; command notice=${state.notice ?: "none"}")
    appendLine("direct resources/restoration safe=${state.cleanupSafe}; detached native resources independently released=${state.detachedResourcesReleased}; DAC-internal bit-perfect output: NOT INDEPENDENTLY VERIFIED")
    appendLine("Integrated Direct Queue playback; native Pause/Resume and absolute seek on capable sources; no DSP/gain, Android audio sink or mixer; physical DAC volume controls output")
    val controlCounters=state.engine?.transport?.counters ?: state.nativePauseCounters
    fun control(name: String)=controlCounters[name]?.toString() ?: "unavailable"
    val phase=controlCounters["playbackTransportState"]?.let { UsbFullTransportPhase.entries.getOrNull(it.toInt()) }
    fun outcome(name: String)=controlCounters[name]?.let { UsbIsoNativeStats.controlOutcomes.getOrNull(it.toInt()) } ?: "unavailable"
    appendLine("native transport state=${phase ?: "unavailable"}; Pause requests=${control("pauseRequests")}; confirmed=${control("pauseConfirmed")}; Resume requests=${control("resumeRequests")}; confirmed=${control("resumeConfirmed")}")
    appendLine("last Pause outcome=${outcome("lastPauseOutcome")}; last Resume outcome=${outcome("lastResumeOutcome")}; first/last Pause source frames=${control("firstPauseSourceFrame")}/${control("lastPauseSourceFrame")}; confirmed paused frame=${control("confirmedPausedFrame")}")
    appendLine("intentional paused duration us=${control("intentionalPausedUs")}; pending source-bearing OUT at confirmed pause=${control("pauseOutstandingOut")}; drain outcome=${control("pauseDrainOutcome")} (0=none,1=PASS,2=FAIL); drain failures=${control("pauseDrainFailures")}")
    appendLine("Resume readiness=${control("resumeReadiness")} (0=none,1=nominal bootstrap,2=fresh valid feedback); failure=${control("resumeFailure")} (0=none,1=prebuffer deadline,2=format/accounting,3=transport); continuity exact=${control("pauseSourceContinuity")}; control exception=${state.engine?.transport?.exceptionType ?: "none"}")
    appendLine("PAUSED preserves decoder/queue/clock/alternate; OUT and feedback pools drain without cancellation; resume uses a new feedback epoch and preserved rational/source phase; intentional gaps excluded from active continuity timing")
    if((controlCounters["seekRequests"] ?: 0L)>0L) {
        fun seekOutcome()=controlCounters["seekOutcome"]?.let { UsbIsoNativeStats.seekOutcomes.getOrNull(it.toInt()) } ?: "unavailable"
        appendLine("seek capability=${control("sourceSeekCapable")}; requests=${control("seekRequests")}; confirmed=${control("seekConfirmed")}; failures=${control("seekFailures")}; superseded=${control("seekSuperseded")}; outcome=${seekOutcome()}; pending app serial=${state.pendingSeekSerial}; confirmed serial=${control("confirmedSeekSerial")}")
        appendLine("seek targets original frames: first=${control("firstSeekTargetFrame")}; last=${control("lastSeekTargetFrame")}; confirmed=${control("confirmedSeekTargetFrame")}; epoch=${control("seekSourceEpoch")}; started paused=${control("seekStartedPaused")}")
        appendLine("old segment boundary=${control("oldSegmentSourceBoundary")}; OUT drain=${control("seekDrainOutcome")} (1=PASS,2=FAIL); pending source OUT=${control("seekOutstandingOut")}; intentionally invalidated unsubmitted frames/bytes=${control("seekInvalidatedFrames")}/${control("seekInvalidatedBytes")}")
        state.engine?.flacSource?.let { source -> appendLine("invalidation breakdown: queued canonical=${source.count("intentionallyInvalidatedQueuedFrames")}; other canonical=${source.count("intentionallyInvalidatedCanonicalFrames")}; acknowledged raw un-serialized=${source.count("intentionallyInvalidatedRawFrames")}; invalidated byte total is canonical-equivalent width, not delivered bytes") }
        appendLine("target source readiness=${control("seekSourceReady")}; first original frame=${control("seekFirstSourceFrame")}; exact target proven=${control("seekFirstFrameProven")}; fresh feedback readiness=${control("resumeReadiness")}; original source position=${control("sourcePositionFrame")}; completed in segment=${control("completedFramesInSeekSegment")}")
        appendLine("seek distances: forward=${control("seekForwardDistanceFrames")}; backward=${control("seekBackwardDistanceFrames")} (sum of confirmed target minus previous delivered boundary by direction; intervals may overlap)")
        appendLine("segments sealed=${control("seekSegmentCount")}; online integrity=${control("seekSegmentIntegrity")}; reconciled frames/bytes=${control("seekSegmentFrames")}/${control("seekSegmentBytes")}; all-segment evidence FNV=${controlCounters["seekSegmentEvidenceHash"]?.let(::pcmHashHex) ?: "unavailable"}; first 8 examples only, every segment verified online")
        appendLine("whole-file MD5=${if((controlCounters["sourceSeekAttempts"] ?: 0L)>0L) "WHOLE_FILE_MD5_NOT_VERIFIED_AFTER_SEEK" else "no native seek attempted"}; source seek attempts=${control("sourceSeekAttempts")}; original decoder next=${control("originalDecoderNextFrame")}")
        appendLine("final seek-segment tail: URBs=${control("seekTailOutUrbs")}; frames=${control("seekTailFrames")}; packets=${control("seekTailPackets")}; successful frames/packets=${control("seekTailFrameCompletions")}/${control("seekTailPacketCompletions")}")
        state.engine?.transport?.segments?.forEach { row ->
            appendLine("segment epoch=${row[0]}; original [${row[1]},${row[2]}); submitted/completed frames=${row[3]}/${row[4]}; submitted/actual bytes=${row[5]}/${row[6]}; consumed/submitted FNV=${pcmHashHex(row[7])}/${pcmHashHex(row[8])}; terminal natural EOF=${row[9]}")
        }
        appendLine("Pause frame telemetry remains cumulative delivered frames after seeking. Confirmed source base + current-epoch successful completions drives position; queued PCM and scrub previews are not completions.")
    }
    state.engine?.let { result ->
        appendLine("native engine result=${result.status}; original descriptor closed=${result.originalFlacFdClosed}; native memory released=${result.nativeMemoryReleased}; native execution joined=${result.nativeExecutionJoined}")
        result.flacSource?.let { source ->
            appendLine("format=${source.count("sampleRate")}/${source.count("channels")}/${source.count("bitsPerSample")}; accepted=${source.count("metadataAccepted")}; MD5=${source.md5Report}; true EOF=${source.count("trueEofReached")}")
            appendLine("STREAMINFO MD5 reference available=${source.decoder?.counters?.get("md5ReferenceAvailable") ?: "unavailable"}; whole-file verification scope=${if(source.count("sourceSeekAttempts")>0L) "INVALIDATED_BY_SEEK" else "SEQUENTIAL"}")
            appendLine("PCM consistency=${if(result.flacPlaybackComplete) "PASS" else "NOT_VERIFIED"}; USB transport completion=${if(result.transport?.let { it.fullPlaybackComplete || it.seekVerified }==true) "PASS" else "NOT_VERIFIED"}")
            appendLine("decoded=${source.count("framesDecoded")}; enqueued=${source.count("framesEnqueued")}; consumed=${source.count("framesConsumedFromQueue")}; prepared=${source.count("preparedButUnsubmittedFrames")}; discarded=${source.count("prefetchedFramesDiscardedAtStop")}")
            appendLine("source faults=${source.reasons.joinToString()}; source released=${source.count("queueReleased")}; producer joined=${source.count("producerJoined")}")
        }
        val plan=result.resolution.plan?.configurationPlan
        val selectedRate=plan?.sampleRateHz
        val format=plan?.pcmFormat
        appendLine("USB target=${format?.label}; subslot bytes=${format?.subslotBytes}; valid bits=${format?.validBits}; bytes per frame=${format?.bytesPerFrame}; descriptor-resolved AS=${plan?.streaming?.number} alternate=${plan?.streaming?.alternate}; endpoint capacity bytes=${result.timing?.capacity}")
        appendLine("source/configuration agreement=${result.configuration?.selectedFormatMatches(result.flacSource?.count("sampleRate") ?: 0L,result.flacSource?.count("channels") ?: 0L,result.flacSource?.count("bitsPerSample") ?: 0L)==true && result.transport?.pcmFormat==result.flacSource?.pcmFormat && result.transport?.count("formatAlternate")==plan?.streaming?.alternate?.toLong()}; source PCM bytes=${result.flacSource?.decoder?.count("canonicalPcmBytesHashed")}")
        appendLine("requested USB clock Hz=$selectedRate; verified USB clock Hz=${result.configuration?.configuredClockReadback?.currentFrequencyHz}; scheduler nominal/source rate Hz=${result.transport?.counters?.get("schedulerNominalRateHz")}; source/request/both readbacks/scheduler agree=${result.configuration?.selectedRateMatches(result.flacSource?.count("sampleRate") ?: 0L)==true && result.transport?.counters?.get("schedulerNominalRateHz")==result.flacSource?.count("sampleRate")}")
        result.transport?.let { native ->
            appendLine("stop cause=${native.stopReason}; integrity=${native.valid}; transport reasons=${native.integrityReasons.joinToString()}; worker stopped=${native.workerStopped}")
            listOf("framesSubmitted","framesCompleted","outBytesSubmitted","kernelOutActualBytes","terminalTailFrames","terminalTailPackets",
                "terminalTailPacketCompletions","terminalTailFrameCompletions","eofPaddingFrames","outCancelledUrbs","outstandingUrbsFinal",
                "feedbackPacketsValid","feedbackTimeouts","queueUnderruns","outPoolStallEvents").forEach { appendLine("$it=${native.count(it)}") }
            // Existing immutable snapshot only. Do not query/format diagnostics on the USB worker.
            fun field(name: String)=native.counters[name]?.toString() ?: "unavailable"
            fun reapField(name: String)=native.counters[name]?.takeIf { it>=0 }?.toString() ?: "unavailable"
            appendLine("feedback queue: configured URBs=${field("configuredFeedbackUrbs")}; packets/URB=${field("feedbackPacketsPerUrb")}; nominal coverage us=${field("feedbackCoverageUs")}; OUT geometry=${field("configuredOutUrbs")}x${field("outPacketsPerUrb")}; dataIntervalUs=${field("dataIntervalUs")}")
            appendLine("feedback timing: feedbackIntervalUs=${field("feedbackIntervalUs")}; feedbackHoldoverUs=${field("feedbackHoldoverUs")}; lastValidFeedbackAgeUs=${field("lastValidFeedbackAgeUs")} (post-shutdown, not failure-time)")
            appendLine("feedback URBs: submitted=${field("feedbackUrbsSubmitted")}; completed=${field("feedbackUrbsCompleted")}; outstandingAtStop=${field("feedbackOutstandingAtStop")}; cancelled=${field("feedbackCancelledUrbs")}; errors=${field("feedbackUrbErrors")}")
            appendLine("feedback samples: completed=${field("feedbackPacketsCompleted")}; invalid=${field("feedbackPacketsInvalid")}; invalidStreak=${field("invalidStreak")}; minRaw=${field("minFeedbackRaw")}; maxRaw=${field("maxFeedbackRaw")}")
            append(formatFeedbackDiagnostics(native.feedbackDiagnostics,locale))
            val samples=native.counters["feedbackSamples"]
            val sum=native.counters["sumFeedbackRaw"]
            val nominalRate=native.counters["schedulerNominalRateHz"]?.takeIf { UsbDirectPcmFormat.supportsRate(it) }
            val meanHz=if(samples!=null && samples>0 && sum!=null) sum.toDouble()/samples*8000.0/65536.0 else null
            appendLine("valid feedback mean Hz=${meanHz?.let { String.format(Locale.ROOT,"%.6f",it) } ?: "unavailable"}; mean drift ppm=${meanHz?.takeIf { nominalRate!=null }?.let { String.format(Locale.ROOT,"%.3f",(it/requireNotNull(nominalRate).toDouble()-1.0)*1000000.0) } ?: "unavailable"} (whole-run mean, not last sample)")
            native.feedback.lastOrNull()?.let { sample ->
                appendLine("last recorded feedback: raw=${sample.raw}; length=${sample.length}; status=${sample.packetStatus}; validity=${sample.validity}; elapsedUs=${sample.elapsedUs}; submissionAgeUs=${sample.conservativeAgeUs}; Hz=${sample.rateHz?.let { String.format(Locale.ROOT,"%.6f",it) } ?: "unavailable"} (may be drain-time)")
            }
            appendLine("OUT at stop=${field("outOutstandingAtStop")}; OUT completion age at pool stall=${field("outCompletionAgeUsAtStall")} (only recorded for OUT_POOL_STALL); submitErrno=${field("submitErrno")}; reapErrno=${field("reapErrno")}; firstUrbStatus=${field("firstUrbStatus")}; firstPacketStatus=${field("firstPacketStatus")}")
            val timeoutSubtype=native.counters["feedbackTimeoutSubtype"]?.let { UsbIsoNativeStats.feedbackTimeoutSubtypes.getOrNull(it.toInt()) } ?: "unavailable"
            appendLine("feedback timeout subtype=$timeoutSubtype; bootstrap=${field("feedbackBootstrapTimeouts")}; last-valid watchdog=${field("feedbackLastValidWatchdogTimeouts")}; at-reap observation gap=${field("feedbackStaleReapedTimeouts")}")
            if("FEEDBACK_TIMEOUT" in native.integrityReasons) {
                appendLine("first feedback timeout: observedUs=${field("feedbackFailureObservedUs")}; lastValidObservedUs=${field("feedbackFailureLastValidObservedUs")}; lastValidSubmittedUs=${field("feedbackFailureLastValidSubmittedUs")}; offendingSubmittedUs=${field("feedbackFailureOffendingSubmittedUs")}; observationAgeUs=${field("feedbackFailureAgeUs")} (frozen before drain; 0=no accepted/offending request)")
                val acceptedRaw=native.counters["feedbackFailureAcceptedRaw"]
                appendLine("feedback at fault: application outstanding=${field("feedbackFailureOutstandingUrbs")}; reaped-not-rearmed=${field("feedbackFailureReapedNotRearmedUrbs")}; kernel completed-unreaped count=unknown; accepted raw=${field("feedbackFailureAcceptedRaw")}; accepted Hz=${acceptedRaw?.takeIf { it>0 }?.let { String.format(Locale.ROOT,"%.6f",it*8000.0/65536.0) } ?: "unavailable"}")
                appendLine("pre-watchdog reap at fault: attempted=${reapField("feedbackFailureReapAttempted")}; URBs reaped=${reapField("feedbackFailureReapCount")}; feedback completions=${reapField("feedbackFailureReapFeedbackCount")}; valid refresh=${reapField("feedbackFailureReapRefreshed")}; age before us=${reapField("feedbackFailureAgeBeforeReapUs")}; age after us=${reapField("feedbackFailureAgeAfterReapUs")}; watchdog evaluated=${reapField("feedbackFailureWatchdogEvaluated")}; fired=${reapField("feedbackFailureWatchdogFired")} (frozen before drain)")
            }
            appendLine("feedback observation gaps recovered before watchdog=${field("feedbackRecoveredObservationGaps")}; ready reap bound=configured OUT + feedback URBs, maximum ${UsbIsoTiming.MAX_READY_REAPS}; no rearm within pass")
            fun measured(name: String)=native.counters[name]?.takeIf { it>=0 }?.toString() ?: "unavailable"
            appendLine("OUT continuity: configured URBs=${field("configuredOutUrbs")}; packets/URB=${field("outPacketsPerUrb")}; packet interval us=${field("dataIntervalUs")}; nominal URB duration us=${field("outUrbDurationUs")}; nominal queued coverage us=${field("outNominalCoverageUs")}")
            appendLine("OUT worker timing: maxWorkerLoopGapUs=${field("maxWorkerLoopGapUs")}; maxGapObservedAtUs=${field("maxGapObservedAtUs")} (elapsed steady-clock); application OUT outstanding=${field("outOutstandingAtMaxGap")}; feedback outstanding=${field("feedbackOutstandingAtMaxGap")}; further OUT needed=${field("outRefillNeededAtMaxGap")}; successful OUT refill that iteration=${field("outRefilledAfterMaxGap")}; gaps beyond nominal coverage=${field("workerGapsBeyondOutCoverage")}")
            appendLine("OUT refill-demand observations: samples=${field("outContinuityObservations")}; min outstanding=${measured("minOutOutstanding")}; zero=${field("outZeroOutstandingObservations")}; one=${field("outOneOutstandingObservations")}; two=${field("outTwoOutstandingObservations")} (wake, before/after refill; excludes normal EOF depletion and shutdown)")
            appendLine("OUT estimated nominal coverage: minimum us=${measured("minEstimatedOutCoverageUs")}; worst elapsed us=${field("worstOutCoverageAtUs")}; observations below one URB=${field("outCoverageBelowOneUrbObservations")}; below two URBs=${field("outCoverageBelowTwoUrbsObservations")} (ideal FIFO time model; thresholds are one/two nominal URB durations)")
            appendLine("OUT intervals us: max submission batch gap=${field("maxOutSubmissionBatchGapUs")}; max successful submission gap=${field("maxOutSubmissionGapUs")}; max completion observed gap=${field("maxOutCompletionObservedGapUs")}; max refill opportunity gap=${field("maxOutRefillOpportunityGapUs")}; max refill latency=${field("maxOutRefillLatencyUs")} (reap observation to successful resubmit)")
            val riskMask=native.counters["firstOutRiskMask"]
            val riskReasons=riskMask?.let { mask -> UsbIsoNativeStats.outRiskReasons.filterIndexed { i, _ -> (mask and (1L shl i))!=0L }.joinToString().ifEmpty { "NONE" } } ?: "unavailable"
            val riskStage=native.counters["firstOutRiskStage"]?.let { UsbIsoNativeStats.outRiskStages.getOrNull(it.toInt()) } ?: "unavailable"
            val riskPosition=native.counters["firstOutRiskCompletedFrames"]?.takeIf { it>=0 }?.takeIf { nominalRate!=null }?.let { completedFramePositionMs(it,requireNotNull(nominalRate)) }?.toString() ?: "unavailable"
            appendLine("OUT_CONTINUITY_RISK: events=${field("outContinuityRiskEvents")}; first elapsed us=${field("firstOutRiskAtUs")}; first completed-frame position ms=$riskPosition; reasons=$riskReasons; stage=$riskStage; application OUT=${field("firstOutRiskOutstanding")}; feedback=${field("firstOutRiskFeedbackOutstanding")}; worker gap us=${field("firstOutRiskWorkerGapUs")}; estimated coverage us=${field("firstOutRiskEstimatedCoverageUs")} (first event frozen; 0 events=no first event; diagnostic only, at most one event per iteration)")
            appendLine("OUT wire underrun confirmed? UNKNOWN; kernel completed-unreaped OUT count=UNKNOWN; application outstanding includes completed-unreaped URBs; timing estimates and final frame/byte/hash agreement do not prove uninterrupted wire service")
            appendLine("feedback observations: submittedUs=${field("lastAcceptedFeedbackSubmittedUs")}; acceptedUs=${field("lastAcceptedFeedbackObservedUs")}; requestAgeUs=${field("lastAcceptedFeedbackRequestAgeUs")}; requests over holdover=${field("feedbackRequestsOverHoldover")}; max request age us=${field("feedbackMaxRequestAgeUs")} (request age is not DAC sample age)")
            val hashes=if(native.count("sourceSeekAttempts")>0L) listOf("sourcePcmHash","submittedPcmHash") else listOf("wholeDecodedPcmHash","allEnqueuedPcmHash","sourcePcmHash","submittedPcmHash")
            hashes.forEach { appendLine("$it=${native.counters[it]?.let(::pcmHashHex) ?: "unavailable"}") }
            appendLine("hash agreement=${hashes.all { native.counters.containsKey(it) && native.counters[it]==native.counters["submittedPcmHash"] }}")
            if(native.count("sourceSeekAttempts")>0L) appendLine("Seek-aware delivered-byte hashes above reconcile consumption with successful submission. Decoder-ahead/enqueued hashes include intentionally invalidated PCM and are not whole-track output evidence.")
        }
        result.configuration?.let {
            appendLine("USB configuration=${it.targetStatus}; restoration=${it.restorationStatus}; AS release=${it.asRelease.returned}; AC release=${it.acRelease.returned}; connection close=${it.closeStatus}")
            append(io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticReport.formatIntegratedConfiguration(it))
        }
    }
    appendLine("Completed frames are host USB evidence; DAC-internal reconstruction and listening are not measured.")
}
