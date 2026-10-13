package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.*
import io.github.rsgarrido.sazanami.player.bitperfect.flac.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic evidence only; requires separate real decoder and hardware validation. */
class UsbSeekEvidenceTest {
    private fun chain(rows: List<List<Long>>): Long {
        var hash=0xcbf29ce484222325UL.toLong()
        rows.forEach { row -> row.forEach { value -> repeat(8) { byte -> hash=(hash xor ((value ushr (byte*8)) and 255L))*0x100000001b3L } } }
        return hash
    }
    internal fun fixture(rate: Long,bits: Long,backward: Boolean=false): Pair<UsbIsoTransportResult,UsbFlacSourceResult> {
        val width=bits/8*2;val total=100000L;val old=if(backward) 70000L else 10000L;val target=if(backward) 5000L else 50000L
        val last=total-target;val frames=old+last
        val rows=listOf(listOf(0L,0L,old,old,old,old*width,old*width,111L,111L,0L),
            listOf(1L,target,total,last,last,last*width,last*width,222L,222L,1L))
        val native=UsbIsoNativeStats.fields.associateWith {0L}.toMutableMap().apply {putAll(mapOf(
            "version" to UsbIsoNativeStats.VERSION.toLong(),"generation" to 1L,"streamGeneration" to 2L,"started" to 1L,"state" to 7L,"stopReason" to 14L,
            "workerStopped" to 1L,"sourceMode" to 3L,"playbackTransportState" to 7L,"schedulerNominalRateHz" to rate,
            "formatChannels" to 2L,"formatBitsPerSample" to bits,"formatBytesPerSample" to bits/8,"formatBytesPerFrame" to width,
            "formatSubslotBytes" to bits/8,"formatValidBits" to bits,"formatAlternate" to 9L,
            "streamInfoTotalSamples" to total,"sourceFramesGenerated" to frames,"framesScheduled" to frames,"framesSubmitted" to frames,
            "framesCompleted" to frames,"prefixSourceFramesConsumed" to frames,"prefixInitialPrimedFrames" to 8192L,
            "outBytesSubmitted" to frames*width,"kernelOutActualBytes" to frames*width,"outUrbsSubmitted" to 180L,"outUrbsCompleted" to 180L,
            "outPacketsSubmitted" to 1435L,"outPacketsCompleted" to 1435L,"feedbackUrbsSubmitted" to 201L,"feedbackUrbsCompleted" to 201L,
            "feedbackPacketsValid" to 180L,"feedbackScheduledPackets" to 1300L,"trueEofReached" to 1L,"producerFinishedNaturally" to 1L,
            "prefixProducerStopped" to 1L,"prefixProducerJoined" to 1L,"sourcePcmHash" to 123L,"submittedPcmHash" to 123L,
            "wholeDecodedPcmHash" to 456L,"allEnqueuedPcmHash" to 789L,"sourceSeekCapable" to 1L,"seekRequests" to 1L,"seekConfirmed" to 1L,
            "seekOutcome" to 5L,"firstSeekTargetFrame" to target,"lastSeekTargetFrame" to target,"confirmedSeekTargetFrame" to target,
            "seekSourceEpoch" to 1L,"oldSegmentSourceBoundary" to old,"seekDrainOutcome" to 1L,"seekInvalidatedFrames" to 305L,
            "seekInvalidatedBytes" to 305*width,"seekSourceReady" to 1L,"seekFirstSourceFrame" to target,"seekFirstFrameProven" to 1L,
            "seekSegmentCount" to 2L,"sourcePositionFrame" to total,"completedFramesInSeekSegment" to last,"confirmedSeekSerial" to 1L,
            "seekSegmentIntegrity" to 1L,"seekSegmentEvidenceHash" to chain(rows),"seekTailOutUrbs" to 1L,"seekTailPackets" to 3L,
            "seekTailFrames" to 100L,"seekTailPacketCompletions" to 3L,"seekTailFrameCompletions" to 100L,"originalDecoderNextFrame" to total,
            "sourceSeekAttempts" to 1L,"seekSegmentFrames" to frames,"seekSegmentBytes" to frames*width,"wholeMd5NotVerifiedAfterSeek" to 1L,
            "resumeReadiness" to 2L,"pauseSourceContinuity" to 1L))}
        val decoded=frames+280
        val decoder=FlacVerificationResult(nativeStatus=FlacVerificationStatus.SEEKED_SOURCE_EOF_NOT_WHOLE_FILE_VERIFIED,
            decoderVersion="1.5.0",streamInfoMd5="1".repeat(32),counters=FlacNativeEvidence.fields.associateWith {0L}+mapOf(
                "version" to 2L,"result" to 15L,
                "sampleRate" to rate,"channels" to 2L,"bitsPerSample" to bits,"totalSamples" to total,"nativeFlacRecognized" to 1L,
                "decoderCreated" to 1L,"initStatus" to 0L,"metadataProcessed" to 1L,"streamInfoReceived" to 1L,"md5ReferenceAvailable" to 1L,
                "cleanEndOfStream" to 1L,"physicalInputEof" to 1L,"processSucceeded" to 1L,"finishCalled" to 1L,"finishReturned" to 1L,
                "decoderState" to 4L,"nativeDuplicateClosed" to 1L,"decoderDeleted" to 1L,"decodedStereoFrames" to decoded,"decoderCallbacks" to 20L,"decodedBlocks" to 20L,
                "decodedSamplesPerChannel" to decoded,"canonicalPcmBytesHashed" to decoded*width,"pcmFnv1a" to 456L))
        val source=UsbFlacSourceEvidence.fields.associateWith {0L}+mapOf("version" to 4L,"generation" to 1L,"streamGeneration" to 2L,
            "metadataAccepted" to 1L,"initialPrimedFrames" to 8192L,"sampleRate" to rate,"channels" to 2L,"bitsPerSample" to bits,"totalSamples" to total,
            "fullTrackMode" to 1L,"trueEofReached" to 1L,"producerStopped" to 1L,"producerJoined" to 1L,"decoderReleased" to 1L,
            "nativeFlacFdClosed" to 1L,"queueReleased" to 1L,"producerFinishedNaturally" to 1L,"framesDecoded" to decoded,
            "framesEnqueued" to frames+264,"framesConsumedFromQueue" to frames,"sourceSeekCapable" to 1L,"sourceSeekAttempts" to 1L,
            "sourceEpoch" to 1L,"sourceSegmentStartFrame" to target,"originalDecoderNextFrame" to total,"intentionallyInvalidatedQueuedFrames" to 264L,
            "intentionallyInvalidatedCanonicalFrames" to 16L,"intentionallyInvalidatedRawFrames" to 25L,"sourceFirstSeekFrame" to target,
            "sourceFirstSeekFrameProven" to 1L,"consumedSourcePcmHash" to 123L,"allEnqueuedPrefetchPcmHash" to 789L)
        return UsbIsoTransportResult(1,2,native,segments=rows) to UsbFlacSourceResult(source,decoder)
    }
    @Test fun fourFormatsAndBothDirectionsVerifyDeliveredSegmentsWithoutWholeFilePromotion() {
        for((rate,bits) in listOf(44100L to 16L,48000L to 16L,48000L to 24L,96000L to 24L)) for(backward in listOf(false,true)) {
            val (usb,source)=fixture(rate,bits,backward)
            assertTrue(usb.seekVerified);assertTrue(usb.verified);assertTrue(source.seekComplete);assertFalse(source.fullComplete)
            assertFalse(source.decoder!!.strongEvidence)
            val decoded=UsbIsoNativeStats.decode(UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map {usb.count(it)}.toLongArray(),
                longArrayOf(),longArrayOf(),segments=usb.segments.flatten().toLongArray()),1,2)
            assertTrue(decoded.seekVerified)
            val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=UsbSilentStatus.SEEKED_SEGMENTS_USB_TRANSPORT_VERIFIED,
                transport=usb,flacSource=source,originalFlacFdClosed=true,configuration=UsbAudioConfigurationResult(1,UsbAudioConfigurationResolution(),
                    acClaim=UsbConfigurationAndroidOwnership(true,true),asClaim=UsbConfigurationAndroidOwnership(true,true),
                    restorationStatus=UsbConfigurationRestorationStatus.RESTORED,acRelease=UsbConfigurationAndroidOwnership(true,true),
                    asRelease=UsbConfigurationAndroidOwnership(true,true),closeStatus=UsbAudioCleanupStatus.SUCCEEDED))
            val ended=integratedTerminalState(IntegratedDirectPlaybackState(enabled=true),engine)
            assertEquals(IntegratedDirectPhase.ENDED,ended.phase);assertEquals(usb.count("framesCompleted"),ended.completedFrames)
            assertEquals(ended.durationMs,ended.positionMs)
            val report=formatIntegratedDirectPlayback(ended)
            assertTrue(report.contains("WHOLE_FILE_MD5_NOT_VERIFIED_AFTER_SEEK"));assertTrue(report.contains("segment epoch=1"))
            assertTrue(report.contains("hash agreement=true"));assertFalse(report.contains("MD5=PASS"));assertFalse(report.contains("content://"))
            assertEquals(UsbSilentStatus.RESOURCE_CLEANUP_FAILED,engine.withFlacInputClosed(false,source).status)
            assertNotEquals(IntegratedDirectPhase.ENDED,integratedTerminalState(ended,engine.copy(status=UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED)).phase)
        }
    }
    @Test fun everyIntegrityFailureMissingProofOrStopPreventsSeekSuccess() {
        val (usb,source)=fixture(48000,24)
        for(change in listOf("seekFailures" to 1L,"seekRequests" to 2L,"seekFirstSourceFrame" to 49999L,"seekFirstFrameProven" to 0L,
            "seekDrainOutcome" to 2L,"seekOutstandingOut" to 1L,"seekSourceReady" to 0L,"seekSegmentIntegrity" to 0L,
            "seekSegmentFrames" to 59999L,"seekSegmentBytes" to 359999L,"kernelOutActualBytes" to 359999L,
            "wholeMd5NotVerifiedAfterSeek" to 0L,"md5VerificationPass" to 1L,"trueEofReached" to 0L,"originalDecoderNextFrame" to 99999L,
            "seekTailFrameCompletions" to 99L,"seekTailPacketCompletions" to 2L,"sourcePcmHash" to 124L,"framesCancelled" to 1L,
            "stopReason" to 1L,"playbackTransportState" to 6L,"outPacketErrors" to 1L,"feedbackTimeouts" to 1L,"resumeFailure" to 3L,
            "pauseDrainFailures" to 1L,"outstandingUrbsFinal" to 1L,"seekInvalidatedBytes" to 1L))
            assertFalse(change.first,usb.copy(counters=usb.counters+change).seekVerified)
        for(field in UsbIsoNativeStats.fields) assertFalse(field,usb.copy(counters=usb.counters-field).seekVerified)
        for(failure in listOf(UsbClockQueryStatus.DEVICE_DISCONNECTED,UsbClockQueryStatus.PERMISSION_LOST,UsbClockQueryStatus.STALE_GENERATION))
            assertFalse(usb.copy(authorityFailure=failure).seekVerified)
        for(column in 0..9) {
            val rows=usb.segments.map { it.toMutableList() };rows[1][column]=rows[1][column]+1
            assertFalse("corrupt segment column $column",usb.copy(segments=rows).seekVerified)
        }
        for(change in listOf("intentionallyInvalidatedQueuedFrames" to 265L,"sourceSeekFailures" to 1L,"sourceFirstSeekFrameProven" to 0L,
            "md5VerificationPass" to 1L,"queueFramesAtStop" to 1L,"sourceFrameGapCount" to 1L,"sourceFrameDuplicateCount" to 1L))
            assertFalse(change.first,source.copy(counters=source.counters+change).seekComplete)
        assertFalse(source.copy(decoder=source.decoder!!.copy(counters=source.decoder!!.counters+("md5Verification" to 1L))).seekComplete)
        assertFalse(source.copy(decoder=source.decoder!!.copy(errors=listOf(0))).seekComplete)
        assertEquals(UsbSilentStatus.FLAC_SOURCE_FAILED,UsbFlacFullStatus.sourceFailure(source.copy(counters=source.counters+("sourceIntegrityMask" to (2097152L or 2048L)))))
    }
    @Test fun diagnosticCapNeverSubstitutesForAllSegmentOnlineProof() {
        val (usb,_)=fixture(48000,16)
        val rows=(0L..7L).map { epoch -> listOf(epoch,0L,1L,1L,1L,4L,4L,123L,123L,0L) }
        val many=usb.copy(segments=rows,counters=usb.counters+mapOf("seekRequests" to 99L,"seekConfirmed" to 99L,"seekSegmentCount" to 100L,
            "seekSourceEpoch" to 99L,"sourceSeekAttempts" to 99L,"confirmedSeekSerial" to 99L))
        assertTrue(many.seekVerified)
        assertFalse(many.copy(counters=many.counters+("seekSegmentIntegrity" to 0L)).seekVerified)
        assertFalse(many.copy(segments=rows.dropLast(1)).seekVerified)
        assertFalse(many.copy(counters=many.counters+("seekSegmentFrames" to 59999L)).seekVerified)
    }
}
