package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Pure immutable-schema/report tests and unchanged production boundaries; no USB execution. */
class OutContinuityInstrumentationTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText().replace("\r\n","\n")
    private fun digest(text: String)=MessageDigest.getInstance("SHA-256").digest(pcm16WidthSourceEquivalent(text).toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    private fun packet(changes: Map<String,Long> = emptyMap()): UsbIsoNativeSnapshot {
        val values=mapOf("version" to UsbIsoNativeStats.VERSION.toLong(),"generation" to 1L,"streamGeneration" to 2L,
            "formatChannels" to 2L,"formatBitsPerSample" to 16L,"formatBytesPerSample" to 2L,"formatBytesPerFrame" to 4L,"formatSubslotBytes" to 2L,"formatValidBits" to 16L,"formatAlternate" to 9L,"schedulerNominalRateHz" to 44100L,"state" to 7L,"sourceMode" to 3L,"stopReason" to 14L) + changes
        return UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map { values[it] ?: 0L }.toLongArray(),longArrayOf(),longArrayOf())
    }
    @Test fun schemaAppendsContinuityWithoutMovingWorkingFeedbackFields() {
        assertEquals(13,UsbIsoNativeStats.VERSION);assertEquals(508,UsbIsoNativeStats.fields.size)
        assertEquals(111,UsbIsoNativeStats.fields.indexOf("configuredFeedbackUrbs"))
        assertEquals(131,UsbIsoNativeStats.fields.indexOf("feedbackFailureReapAttempted"))
        assertEquals(140,UsbIsoNativeStats.fields.indexOf("configuredOutUrbs"))
        assertEquals(173,UsbIsoNativeStats.fields.indexOf("firstOutRiskCompletedFrames"))
        assertEquals(182,UsbIsoNativeStats.fields.indexOf("playbackTransportState"))
        assertEquals(198,UsbIsoNativeStats.fields.indexOf("pauseDrainFailures"))
        assertEquals(199,UsbIsoNativeStats.fields.indexOf("sourceSeekCapable"))
        assertEquals(235,UsbIsoNativeStats.fields.indexOf("seekStartedPaused"))
        for (stage in 0L..2L) assertEquals(stage,UsbIsoNativeStats.decode(packet(mapOf("firstOutRiskStage" to stage)),1,2).count("firstOutRiskStage"))
        for (bad in listOf(packet(mapOf("version" to 11L)),packet(mapOf("version" to 7L)),
            packet().copy(numbers=packet().numbers.copyOf(199)),packet().copy(numbers=packet().numbers.copyOf(235)),
            packet(mapOf("firstOutRiskStage" to -1L)),packet(mapOf("firstOutRiskStage" to 3L)))) {
            try { UsbIsoNativeStats.decode(bad,1,2);fail("Old/malformed evidence was accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun seekSchemasAppendInNativeOrderAndProgressRemainsCoherentAndPassive() {
        fun enumFields(text: String,name: String)=text.substringAfter("enum $name : size_t {")
            .substringBefore("}").split(',').map { it.trim() }.dropLast(1)
        val native=source("cpp/usb_iso_transport.cpp")
        val nativeFields=enumFields(native,"Field")
        val appended=listOf(
            "SeekCapable" to "sourceSeekCapable", "SeekRequests" to "seekRequests", "SeekConfirmed" to "seekConfirmed",
            "SeekFailures" to "seekFailures", "SeekSuperseded" to "seekSuperseded", "SeekOutcomeField" to "seekOutcome",
            "FirstSeekTarget" to "firstSeekTargetFrame", "LastSeekTarget" to "lastSeekTargetFrame", "ConfirmedSeekTarget" to "confirmedSeekTargetFrame",
            "SeekEpoch" to "seekSourceEpoch", "SeekOldBoundary" to "oldSegmentSourceBoundary", "SeekDrainOutcome" to "seekDrainOutcome",
            "SeekPendingOut" to "seekOutstandingOut", "SeekInvalidatedFrames" to "seekInvalidatedFrames", "SeekInvalidatedBytes" to "seekInvalidatedBytes",
            "SeekSourceReady" to "seekSourceReady", "SeekFirstFrame" to "seekFirstSourceFrame", "SeekFirstProven" to "seekFirstFrameProven",
            "SeekSegmentCount" to "seekSegmentCount", "SeekSkipped" to "seekForwardDistanceFrames", "SeekRevisited" to "seekBackwardDistanceFrames",
            "SeekSourcePosition" to "sourcePositionFrame", "SeekSegmentCompleted" to "completedFramesInSeekSegment", "SeekConfirmedSerial" to "confirmedSeekSerial",
            "SeekSegmentIntegrity" to "seekSegmentIntegrity", "SeekChainHash" to "seekSegmentEvidenceHash", "SeekTailUrbs" to "seekTailOutUrbs",
            "SeekTailPackets" to "seekTailPackets", "SeekTailFrames" to "seekTailFrames", "SeekTailPacketCompletions" to "seekTailPacketCompletions",
            "SeekTailFrameCompletions" to "seekTailFrameCompletions", "SeekOriginalNext" to "originalDecoderNextFrame", "SeekAttempts" to "sourceSeekAttempts",
            "SeekSegmentFrames" to "seekSegmentFrames", "SeekSegmentBytes" to "seekSegmentBytes", "SeekWholeMd5AfterSeek" to "wholeMd5NotVerifiedAfterSeek",
            "SeekStartedPaused" to "seekStartedPaused")
        assertEquals(236,nativeFields.size)
        assertEquals(appended.map { it.first },nativeFields.drop(199))
        assertEquals(appended.map { it.second },UsbIsoNativeStats.seekFields)
        assertEquals("ConfiguredFeedbackUrbs",nativeFields[111]);assertEquals("FirstOutRiskCompletedFrames",nativeFields[173])
        assertTrue(native.contains("kSnapshotVersion = 13"))
        val prefix=source("cpp/flac/flac_prefix_queue.h")
        val sourceFields=enumFields(prefix,"PrefixField")
        assertEquals(53,sourceFields.size);assertEquals(53,UsbFlacSourceEvidence.fields.size)
        assertEquals(listOf("PSeekCapable","PSeekAttempts","PSeekFailures","PSourceEpoch","PSourceBase","POriginalNext",
            "PInvalidatedQueued","PInvalidatedCanonical","PInvalidatedRaw","PSeekFirstFrame","PSeekFirstProven"),sourceFields.drop(42))
        assertEquals(listOf("sourceSeekCapable","sourceSeekAttempts","sourceSeekFailures","sourceEpoch","sourceSegmentStartFrame",
            "originalDecoderNextFrame","intentionallyInvalidatedQueuedFrames","intentionallyInvalidatedCanonicalFrames",
            "intentionallyInvalidatedRawFrames","sourceFirstSeekFrame","sourceFirstSeekFrameProven"),UsbFlacSourceEvidence.fields.drop(42))
        assertEquals("PTotalMatch",sourceFields[41]);assertEquals("decodedTotalMatchesStreamInfo",UsbFlacSourceEvidence.fields[41])
        assertTrue(prefix.contains("facts[PApi]=4"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/player/usb/UsbFlacPrefixSource.kt").contains("value.numbers[0]==4L"))
        assertEquals(16,UsbFsEvidence.NATIVE_API_VERSION)
        val api=source("cpp/usb_fs_diagnostics.cpp")
        assertTrue(api.contains("kApiVersion = 16"));assertTrue(api.contains("return kApiVersion;"))
        assertEquals(58,4+UsbIsoNativeStats.controlFields.size+UsbIsoNativeStats.seekFields.size)
        val progress=native.substringAfter("UsbFsDiagnostics_nativeFullProgress(").substringBefore("extern \"C\"")
        assertTrue(progress.contains("std::array<jlong,4+kControlFieldCount+kSeekFieldCount> values{}"))
        assertTrue(progress.contains("std::lock_guard<std::mutex> lock(session->controlMutex)"))
        assertTrue(progress.contains("values.begin()+4+kControlFieldCount"))
        for(forbidden in listOf("submit(","reapOne(","ioctl(","queue.","prefix->")) assertFalse(forbidden,progress.contains(forbidden))
        val bridge=source("java/io/github/rsgarrido/sazanami/player/usb/UsbFsDiagnostics.kt")
        assertTrue(bridge.contains("nativeApiVersion() == UsbFsEvidence.NATIVE_API_VERSION"))
        assertTrue(bridge.contains("values.size==4+UsbIsoNativeStats.controlFields.size+UsbIsoNativeStats.seekFields.size"))
        assertTrue(bridge.contains("values.drop(4).take(17)"));assertTrue(bridge.contains("values.drop(21)"))
        assertTrue(bridge.contains("control.getValue(\"playbackTransportState\")==values[3]"))
    }
    @Test fun reportMakesRiskAndKernelUncertaintyExplicitWithCorrelationTimes() {
        val metrics=mapOf("configuredOutUrbs" to 8L,"outPacketsPerUrb" to 8L,"dataIntervalUs" to 125L,
            "outUrbDurationUs" to 1000L,"outNominalCoverageUs" to 8000L,
            "maxWorkerLoopGapUs" to 9001L,"maxGapObservedAtUs" to 1250000L,
            "outOutstandingAtMaxGap" to 8L,"feedbackOutstandingAtMaxGap" to 21L,
            "outRefillNeededAtMaxGap" to 1L,"outRefilledAfterMaxGap" to 1L,"workerGapsBeyondOutCoverage" to 1L,
            "outContinuityObservations" to 17L,"minOutOutstanding" to 0L,
            "outZeroOutstandingObservations" to 1L,"outOneOutstandingObservations" to 2L,"outTwoOutstandingObservations" to 3L,
            "minEstimatedOutCoverageUs" to 0L,"worstOutCoverageAtUs" to 1250000L,
            "maxOutSubmissionBatchGapUs" to 9200L,"maxOutSubmissionGapUs" to 9100L,
            "maxOutCompletionObservedGapUs" to 9005L,"maxOutRefillOpportunityGapUs" to 9105L,"maxOutRefillLatencyUs" to 105L,
            "outContinuityRiskEvents" to 1L,"firstOutRiskAtUs" to 1250000L,"firstOutRiskMask" to 7L,"firstOutRiskStage" to 1L,
            "firstOutRiskOutstanding" to 0L,"firstOutRiskFeedbackOutstanding" to 21L,
            "firstOutRiskWorkerGapUs" to 9001L,"firstOutRiskEstimatedCoverageUs" to 0L,"firstOutRiskCompletedFrames" to 44100L)
        val result=UsbIsoNativeStats.decode(packet(metrics),1,2)
        val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),transport=result)
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine))
        for (text in listOf("OUT continuity: configured URBs=8","packets/URB=8","packet interval us=125",
            "nominal queued coverage us=8000","maxWorkerLoopGapUs=9001","maxGapObservedAtUs=1250000",
            "successful OUT refill that iteration=1","min outstanding=0","zero=1; one=2; two=3",
            "max submission batch gap=9200","max successful submission gap=9100","max completion observed gap=9005",
            "max refill opportunity gap=9105","max refill latency=105","minimum us=0","ideal FIFO time model",
            "OUT_CONTINUITY_RISK: events=1","first elapsed us=1250000","first completed-frame position ms=1000",
            "stage=BEFORE_REFILL","NO_APPLICATION_OUT","first event frozen","wire underrun confirmed? UNKNOWN",
            "kernel completed-unreaped OUT count=UNKNOWN","do not prove uninterrupted wire service")) assertTrue(text,report.contains(text))
        assertFalse(report.contains("wire underrun confirmed? YES"));assertFalse(report.contains("wire underrun confirmed? NO"))
        assertTrue(report.lines().size<70)
        val missing=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine.copy(
            transport=UsbIsoTransportResult(1,2,mapOf("minOutOutstanding" to -1L,"minEstimatedOutCoverageUs" to -1L)))))
        assertTrue(missing.contains("min outstanding=unavailable"));assertTrue(missing.contains("minimum us=unavailable"))
        assertTrue(missing.contains("maxWorkerLoopGapUs=unavailable"))
    }
    @Test fun hooksPreserveSourceTransactionErrorAccountingEofAndCleanup() {
        val native=source("cpp/usb_iso_transport.cpp")
        val submit="    bool submit("+native.substringAfter("    bool submit(").substringBefore("    void packetExample(")
        val submissionHook="        if (!slot.feedback) outContinuity.submitted(monotonicUs(),slot.packets,slot.outReapedAt);\n"
        assertTrue(submit.contains(submissionHook))
        assertEquals("bf565b4d38f31e1bb7e8ba7b8dc36b82e6752cd51ed5989d1c2c82e73dd5d5b8",digest(submit.replace(submissionHook,"")))
        assertTrue(submit.indexOf("queue.reserve(")<submit.indexOf("queue.validate("))
        assertTrue(submit.indexOf("queue.validate(")<submit.indexOf("USBDEVFS_SUBMITURB"))
        assertTrue(submit.indexOf("if (returned != 0)")<submit.indexOf("outContinuity.submitted("))
        assertTrue(submit.indexOf("outContinuity.submitted(")<submit.indexOf("queue.commit()"))
        val shutdown="    void shutdown()"+native.substringAfter("    void shutdown()").substringBefore("    void run()")
        assertEquals("c9c5cb2e0ee36a66708c0ac4a17483a6a98764538e77a8962678551968242461",digest(shutdown.replace(
            "        captureOutContinuity(); // Freeze active telemetry before any stop/drain completions.\n","")))
        val natural="    bool naturalComplete()"+native.substringAfter("    bool naturalComplete()").substringBefore("    void beginStop()")
        // Preserve every EOF/transport condition while allowing the reviewed reference-policy change.
        val completionGate="!prefix->decoder.sequentialDecodeComplete()"
        assertEquals(1,Regex(Regex.escape(completionGate)).findAll(natural).count())
        assertEquals("a7f2ab508d5298558e106f1a0d25da534ea3c2ca163ecf8e2e66a93e16e72195",
            digest(natural.replace(completionGate,"prefix->decoder.facts[fv::Md5Verification]!=1")))
        val feedback="    void feedbackPacket("+native.substringAfter("    void feedbackPacket(").substringBefore("    bool reapOne()")
        assertEquals("78b404608e4266de52ade3fcb483e5b71ffaecd9fd51cb42ccd166859319af8a",digest(feedback))
        val worker=native.substringAfter("    void run() {").substringBefore("std::mutex registryMutex;")
        assertTrue(worker.indexOf("outContinuity.wake(")<worker.indexOf("reapBeforeFeedbackWatchdog("))
        assertTrue(worker.indexOf("reapBeforeFeedbackWatchdog(")<worker.indexOf("feedbackTimeout(feedbackReapPass.timeout,"))
        assertTrue(worker.contains("!fullTrack || !fullSubmitted()"))
        assertTrue(worker.contains("OutObservationStage::BeforeRefill"));assertTrue(worker.contains("OutObservationStage::AfterRefill"))
        val policy=source("cpp/usb_out_continuity.h")
        for (forbidden in listOf("ioctl(","fail(","scheduler.","queue.","hashBytes(","new ","malloc(","vector<"))
            assertFalse(forbidden,policy.contains(forbidden))
        assertTrue(native.contains("stats[OutstandingFinal] = static_cast<jlong>(outstanding(false) + outstanding(true))"))
        for (fatal in listOf("fail(UrbError, Stop::Worker)","fail(PacketError, Stop::Worker)",
            "saved == ENODEV ? DeviceDetached : SubmitError","fail(QueueUnderrun | FlacSourceFault, Stop::Underrun)"))
            assertTrue(fatal,native.contains(fatal))
    }
}
