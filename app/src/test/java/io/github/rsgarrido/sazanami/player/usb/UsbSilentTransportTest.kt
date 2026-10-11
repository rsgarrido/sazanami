package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.pcm16WidthSourceEquivalent
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic evidence and source-boundary tests, never claims about real kernel/DAC completions. */
class UsbSilentTransportTest {
    @Test fun stopCannotMaskDrainResumeOrTransportFailureAsCancellation() {
        val cancelledSource=fullSource("sourceIntegrityMask" to 2048L)
        val configuration=fullExperiment(ConfigurationFake()).configuration!!
        assertEquals(UsbSilentStatus.CANCELLED,UsbFlacFullStatus.classify(configuration,fullNative("stopReason" to 1L,"playbackTransportState" to 6L),cancelledSource,true))
        for(change in listOf("pauseDrainFailures" to 1L,"resumeFailure" to 3L,"playbackTransportState" to 8L)) {
            val fault=fullNative("stopReason" to 1L,change)
            assertEquals(UsbSilentStatus.USB_TRANSPORT_FAILED,UsbFlacFullStatus.classify(configuration,fault,cancelledSource,true))
        }
    }
    @Test fun pauseEvidenceCannotWeakenFullTrackSuccessAndSchemaRejectsInvalidStates() {
        val evidence=fullNative("pauseRequests" to 2,"pauseConfirmed" to 2,"resumeRequests" to 2,"resumeConfirmed" to 2,
            "pauseSourceContinuity" to 1,"pauseDrainOutcome" to 1,"resumeReadiness" to 2,"intentionalPausedUs" to 20000000)
        assertTrue(evidence.verified)
        for (change in listOf("playbackTransportState" to 3L,"pauseRequests" to 1L,"resumeRequests" to 1L,
            "resumeConfirmed" to 3L,"pauseSourceContinuity" to 0L,"pauseOutstandingOut" to 1L,"pauseDrainOutcome" to 2L,
            "pauseDrainFailures" to 1L,"resumeFailure" to 1L,"md5VerificationPass" to 0L,"wholeDecodedPcmHash" to 999L))
            assertFalse(change.first,evidence.copy(counters=evidence.counters+change).verified)
        assertFalse(evidence.copy(counters=evidence.counters-"pauseRequests").verified)
        for (change in listOf("playbackTransportState" to 9L,"lastPauseOutcome" to 7L,"resumeFailure" to 4L,"intentionalPausedUs" to -1L))
            rejects(snapshot(change))
    }
    // Synthetic 10001-frame track: 104 full 96-frame URBs and a 12+5-frame terminal tail.
    private fun native96(vararg changes: Pair<String,Long>)=native24(*(mapOf("schedulerNominalRateHz" to 96000L,
        "outUrbsSubmitted" to 105L,"outUrbsCompleted" to 105L,"outPacketsSubmitted" to 834L,"outPacketsCompleted" to 834L,
        "terminalTailFrames" to 17L,"terminalTailPackets" to 2L,"terminalTailPacketCompletions" to 2L,
        "terminalTailFrameCompletions" to 17L,"remainingFramesAtTerminalTransition" to 17L)+changes.toMap()).toList().toTypedArray())
    @Test fun sharedFull96RequiresExactClockFormatIntegrityAndRestorationAndReportsSelectedRate() {
        val source=fullSource("sampleRate" to 96000,"bitsPerSample" to 24)
        val fake=ConfigurationFake().apply { rate=192000;ranges=ranges+192000L }
        val result=fullExperiment(fake,source,native96())
        assertEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,result.status)
        val configuration=result.configuration!!
        assertTrue(configuration.selectedFormatMatches(96000,2,24))
        assertEquals(96000L,configuration.targetClockSet!!.request.rateHz)
        assertEquals(96000L,configuration.targetClockReadback!!.currentFrequencyHz)
        assertEquals(96000L,configuration.configuredClockReadback!!.currentFrequencyHz)
        assertEquals(192000L,configuration.originalClockReadback!!.currentFrequencyHz)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,configuration.restorationStatus)
        assertEquals(UsbSilentTransportPlan.resolve(model,android(model),48000,24).plan!!.configurationPlan.streaming,
            result.resolution.plan!!.configurationPlan.streaming)
        assertTrue(fake.events.indexOf("nativeStopped")<fake.events.indexOf("sourceJoined"))
        assertTrue(fake.events.indexOf("sourceJoined")<fake.events.lastIndexOf("setAlt:0"))
        assertTrue(fake.events.indexOf("restoreClock")<fake.events.indexOf("releaseAS"))
        assertTrue(fake.events.indexOf("releaseAS")<fake.events.indexOf("releaseAC"))
        assertTrue(fake.events.indexOf("releaseAC")<fake.events.indexOf("close"))
        val end=io.github.rsgarrido.sazanami.player.integratedTerminalState(
            io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState(enabled=true,normalReleased=true),result.copy(originalFlacFdClosed=true))
        assertEquals(io.github.rsgarrido.sazanami.player.IntegratedDirectPhase.ENDED,end.phase)
        assertEquals(10001L*1000/96000,end.durationMs);assertEquals(end.durationMs,end.positionMs)
        val report=io.github.rsgarrido.sazanami.player.formatIntegratedDirectPlayback(end)
        for (text in listOf("Session 16","format=96000/2/24","PCM24LE packed","subslot bytes=3","valid bits=24",
            "bytes per frame=6","alternate=10","endpoint capacity bytes=776","requested USB clock Hz=96000",
            "verified USB clock Hz=96000","scheduler nominal/source rate Hz=96000","source/configuration agreement=true",
            "MD5=PASS","source PCM bytes=60006","outBytesSubmitted=60006","kernelOutActualBytes=60006",
            "hash agreement=true","eofPaddingFrames=0","restoration=RESTORED","production Bit-perfect: NOT YET COMPLETE"))
            assertTrue(text,report.contains(text))
        assertFalse(report.contains("content://"))
        for (change in listOf("schedulerNominalRateHz" to 48000L,"formatBitsPerSample" to 16L,
            "formatSubslotBytes" to 4L,"formatValidBits" to 16L,"formatAlternate" to 9L,
            "framesCompleted" to 10000L,"kernelOutActualBytes" to 60000L,"outBytesSubmitted" to 40004L,
            "trueEofReached" to 0L,"md5VerificationPass" to 0L,"eofPaddingFrames" to 1L,
            "wholeDecodedPcmHash" to 999L,"allEnqueuedPcmHash" to 999L,"sourcePcmHash" to 999L,"submittedPcmHash" to 999L))
            assertNotEquals(change.first,UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
                UsbFlacFullStatus.classify(configuration,native96(change),source,true))
        val noMd5=source.copy(decoder=source.decoder!!.copy(counters=source.decoder!!.counters+("md5Verification" to 0L)))
        assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,UsbFlacFullStatus.classify(configuration,native96(),noMd5,true))
        assertFalse(native96("streamInfoTotalSamples" to Long.MAX_VALUE,"framesSubmitted" to Long.MAX_VALUE).verified)
        assertFalse(source.copy(counters=source.counters+("totalSamples" to Long.MAX_VALUE)).fullComplete)
        assertEquals(UsbSilentStatus.CANCELLED,fullExperiment(ConfigurationFake(),source,native96("stopReason" to 1L)).status)
        assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
            UsbFlacFullStatus.classify(configuration.copy(restorationStatus=UsbConfigurationRestorationStatus.FAILED),native96(),source,true))
    }
    @Test fun ignored96ClockAndConfiguredClockMismatchPreventNativeFactory() {
        val source=fullSource("sampleRate" to 96000,"bitsPerSample" to 24)
        for (fake in listOf(ConfigurationFake().apply { rate=44100;ignoreTargetRate=true },
            ConfigurationFake().apply { rateAfterTargetAlternate=48000 })) {
            val result=fullExperiment(fake,source,native96())
            assertEquals(UsbSilentStatus.USB_CONFIGURATION_FAILED,result.status)
            assertFalse(fake.events.contains("native"))
            assertEquals(UsbConfigurationRestorationStatus.RESTORED,result.configuration!!.restorationStatus)
        }
        val missing=ConfigurationFake().apply { ranges=ranges-96000L }
        val blocked=fullExperiment(missing,source,native96())
        assertFalse(missing.events.contains("claimAS"));assertFalse(missing.events.contains("native"))
        assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,blocked.status)
    }
    @Test fun shared96PrefixRetainsFiveSecondWallClockGateAndNoWholeMd5Claim() {
        val source=prefixEvidence("sampleRate" to 96000,"bitsPerSample" to 24)
        val native=prefixNative("schedulerNominalRateHz" to 96000,"formatBitsPerSample" to 24,"formatBytesPerSample" to 3,
            "formatBytesPerFrame" to 6,"formatSubslotBytes" to 3,"formatValidBits" to 24,"formatAlternate" to 10,
            "outBytesSubmitted" to 6000,"kernelOutActualBytes" to 6000)
        val result=prefixExperiment(ConfigurationFake(),source,native)
        assertEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,result.status)
        assertEquals(5000000L,result.transport!!.count("transportDurationTargetUs"))
        assertFalse(native.copy(counters=native.counters+("activeTransportUsAtStop" to 4999999L)).verified)
        assertEquals(io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationStatus.CANCELLED,source.decoder!!.nativeStatus)
        val report=UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation=17,flacUsbExperiment=result))
        assertTrue(report.contains("24/96000"));assertTrue(report.contains("whole-file FLAC MD5 verification: NOT APPLICABLE"))
    }
    @Test fun excluded96Pcm16AndOtherUnsupportedSourcesBlockOwnershipWithoutFallback() {
        for ((rate,bits) in listOf(96000L to 16L,44100L to 24L,88200L to 24L,176400L to 24L,192000L to 24L,96000L to 32L)) {
            val fake=ConfigurationFake()
            val source=fullSource("sampleRate" to rate,"bitsPerSample" to bits,"sourceIntegrityMask" to 4L)
            val result=fullExperiment(fake,source,native96())
            assertFalse(fake.events.contains("claimAC"));assertFalse(fake.events.contains("native"))
            val end=io.github.rsgarrido.sazanami.player.integratedTerminalState(
                io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState(enabled=true,normalReleased=true),result.copy(originalFlacFdClosed=true))
            assertTrue(end.enabled);assertTrue(end.normalReleased)
            assertEquals("UNSUPPORTED_TRACK",end.error)
            assertEquals(io.github.rsgarrido.sazanami.player.IntegratedDirectPhase.ERROR,end.phase)
        }
    }
    private fun native24(vararg changes: Pair<String,Long>)=fullNative(*(mapOf("schedulerNominalRateHz" to 48000L,
        "formatBitsPerSample" to 24L,"formatBytesPerSample" to 3L,"formatBytesPerFrame" to 6L,
        "formatSubslotBytes" to 3L,"formatValidBits" to 24L,"formatAlternate" to 10L,
        "outBytesSubmitted" to 60006L,"kernelOutActualBytes" to 60006L)+changes.toMap()).toList().toTypedArray())
    @Test fun sharedFullPcm24RequiresWidthClockAlternateMd5HashesAndSixByteAccounting() {
        val source=fullSource("sampleRate" to 48000,"bitsPerSample" to 24)
        assertTrue(source.fullComplete);assertEquals(6L,source.bytesPerFrame)
        val run=fullExperiment(ConfigurationFake().apply { rate=96000 },source,native24())
        assertEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,run.status)
        assertEquals(10,run.resolution.plan!!.configurationPlan.streaming.alternate)
        assertEquals(3,run.resolution.plan!!.configurationPlan.subslotBytes)
        assertTrue(run.configuration!!.selectedFormatMatches(48000,2,24))
        assertEquals(48000L,run.configuration!!.configuredClockReadback!!.currentFrequencyHz)
        assertEquals(96000L,run.configuration!!.originalClockReadback!!.currentFrequencyHz)
        for(change in listOf("formatBitsPerSample" to 16L,"formatSubslotBytes" to 4L,"formatAlternate" to 9L,
            "outBytesSubmitted" to 40004L,"kernelOutActualBytes" to 40004L,"framesCompleted" to 10000L,
            "submittedPcmHash" to 999L,"md5VerificationPass" to 0L,"eofPaddingFrames" to 1L))
            assertNotEquals(change.first,UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
                UsbFlacFullStatus.classify(run.configuration!!,native24(change),source,true))
        val end=io.github.rsgarrido.sazanami.player.integratedTerminalState(
            io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState(enabled=true),run.copy(originalFlacFdClosed=true))
        assertEquals(io.github.rsgarrido.sazanami.player.IntegratedDirectPhase.ENDED,end.phase)
        val report=io.github.rsgarrido.sazanami.player.formatIntegratedDirectPlayback(end)
        for(text in listOf("Session 16","format=48000/2/24","PCM24LE packed","subslot bytes=3","valid bits=24",
            "bytes per frame=6","alternate=10","source/configuration agreement=true","source PCM bytes=60006"))
            assertTrue(text,report.contains(text))
    }
    @Test fun packed24CapacityGateRejectsThirtySixAndAcceptsFortyTwoBytes() {
        fun pcm24(capacity: Int): UsbSilentTransportPlan {
            val value=model.copy(interfaces=model.interfaces.map { iface ->
                if(iface.audioFormat?.validBits==24) iface.copy(endpoints=iface.endpoints.map { if(!it.isIn) it.copy(maxPacketSizeRaw=capacity) else it }) else iface })
            return UsbSilentTransportPlan.resolve(value,android(value),48000,24).plan!!
        }
        assertNull(pcm24(36).timing(UsbFsSpeed.HIGH))
        assertEquals(42,pcm24(42).timing(UsbFsSpeed.HIGH)!!.capacity)
        assertEquals(21,pcm24(42).timing(UsbFsSpeed.HIGH)!!.directOutUrbCount)
        assertNotNull(plan.timing(UsbFsSpeed.HIGH))
    }
    @Test fun sharedPcm24PrefixKeepsFiveSecondDeadlineAndExactCommittedByteHashEvidence() {
        val source=prefixEvidence("sampleRate" to 48000,"bitsPerSample" to 24)
        val native=prefixNative("schedulerNominalRateHz" to 48000,"formatBitsPerSample" to 24,"formatBytesPerSample" to 3,
            "formatBytesPerFrame" to 6,"formatSubslotBytes" to 3,"formatValidBits" to 24,"formatAlternate" to 10,
            "outBytesSubmitted" to 6000,"kernelOutActualBytes" to 6000)
        val result=prefixExperiment(ConfigurationFake().apply { rate=96000 },source,native)
        assertEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,result.status)
        assertEquals(5000000L,result.transport!!.count("transportDurationTargetUs"))
        assertEquals(6L,result.transport!!.bytesPerFrame)
        assertFalse(prefixNative("schedulerNominalRateHz" to 48000,"formatBitsPerSample" to 24,"formatBytesPerSample" to 3,
            "formatBytesPerFrame" to 6,"formatSubslotBytes" to 3,"formatValidBits" to 24,"activeTransportUsAtStop" to 4999999).verified)
    }
    @Test fun pcm24WidthMismatchBlocksFactoryAndRequestedStopCannotBecomeNaturalEof()=runBlocking {
        var started=false;val fake=ConfigurationFake()
        val source=fullSource("sampleRate" to 48000,"bitsPerSample" to 24)
        val selected=UsbSilentTransportPlan.resolve(model,android(model),48000,16)
        fake.selectedPlan=selected.plan!!.configurationPlan
        val mismatch=UsbSilentTransportExperiment.run(selected,17,23,fake,{ _,_ -> started=true;BackendFake(fake.events) },
            mode=UsbPcmDiagnosticMode.FLAC_FULL,prefixSource=PrefixFake(source,fake.events))
        assertFalse(started);assertNull(mismatch.transport)
        val cancelled=fullExperiment(ConfigurationFake(),source,native24("stopReason" to 1L,"outCancelledUrbs" to 1))
        assertEquals(UsbSilentStatus.CANCELLED,cancelled.status)
        assertFalse(cancelled.transport!!.verified)
    }
    @Test fun sourceSelected48000UsesSharedPrefixAndFullEngineAndVerifiedRestoration() {
        val prefix=prefixExperiment(ConfigurationFake().apply { rate=44100 },prefixEvidence("sampleRate" to 48000),
            prefixNative("schedulerNominalRateHz" to 48000))
        assertEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,prefix.status)
        val fake=ConfigurationFake().apply { rate=96000 }
        val full=fullExperiment(fake,fullSource("sampleRate" to 48000),fullNative("schedulerNominalRateHz" to 48000))
        assertEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,full.status)
        assertEquals(48000L,full.resolution.plan!!.configurationPlan.sampleRateHz)
        assertEquals(48000L,full.configuration!!.targetClockReadback!!.currentFrequencyHz)
        assertEquals(48000L,full.configuration!!.configuredClockReadback!!.currentFrequencyHz)
        assertEquals(96000L,full.configuration!!.originalClockReadback!!.currentFrequencyHz)
        assertEquals(96000L,fake.rate)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,full.configuration!!.restorationStatus)
        assertEquals(configurationPlan.streaming,full.resolution.plan!!.configurationPlan.streaming)
        assertTrue(fake.events.indexOf("restoreClock")<fake.events.indexOf("releaseAS"))
        val end=io.github.rsgarrido.sazanami.player.integratedTerminalState(
            io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState(enabled=true),full.copy(originalFlacFdClosed=true))
        assertEquals(io.github.rsgarrido.sazanami.player.IntegratedDirectPhase.ENDED,end.phase)
        val report=io.github.rsgarrido.sazanami.player.formatIntegratedDirectPlayback(end)
        for (text in listOf("Session 16","format=48000/2/16","requested USB clock Hz=48000",
            "verified USB clock Hz=48000","scheduler nominal/source rate Hz=48000",
            "source/request/both readbacks/scheduler agree=true","production Bit-perfect: NOT YET COMPLETE"))
            assertTrue(text,report.contains(text))
    }
    @Test fun clockReadbackMismatchAndSourcePlanMismatchNeverStartNativeStreaming()=runBlocking {
        val ignored=ConfigurationFake().apply { rate=44100;ignoreTargetRate=true }
        val badRead=fullExperiment(ignored,fullSource("sampleRate" to 48000),fullNative("schedulerNominalRateHz" to 48000))
        assertEquals(UsbSilentStatus.USB_CONFIGURATION_FAILED,badRead.status)
        assertFalse(ignored.events.contains("native"))
        var streamed=false
        val fake=ConfigurationFake()
        val mismatch=UsbSilentTransportExperiment.run(resolution,17,23,fake,{ _,_ -> streamed=true;BackendFake(fake.events) },
            mode=UsbPcmDiagnosticMode.FLAC_FULL,prefixSource=PrefixFake(fullSource("sampleRate" to 48000),fake.events))
        assertFalse(streamed);assertNull(mismatch.transport)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,mismatch.configuration!!.restorationStatus)
    }
    @Test fun rateConsistencyAndStrongFullPredicatesApplyEquallyAt48000() {
        val good=fullExperiment(ConfigurationFake(),fullSource("sampleRate" to 48000),fullNative("schedulerNominalRateHz" to 48000))
        val configuration=good.configuration!!
        assertTrue(configuration.selectedRateMatches(48000))
        assertFalse(configuration.copy(configuredClockReadback=configuration.configuredClockReadback!!.copy(currentFrequencyHz=44100)).selectedRateMatches(48000))
        assertFalse(configuration.copy(targetClockReadback=null).selectedRateMatches(48000))
        assertEquals(UsbSilentStatus.USB_TRANSPORT_FAILED,UsbFlacFullStatus.classify(configuration,fullNative(),fullSource("sampleRate" to 48000),true))
        for (name in listOf("framesCompleted","kernelOutActualBytes","terminalTailFrameCompletions","wholeDecodedPcmHash",
            "allEnqueuedPcmHash","submittedPcmHash","trueEofReached","md5VerificationPass")) {
            val damaged=fullNative("schedulerNominalRateHz" to 48000,name to 0)
            assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,UsbFlacFullStatus.classify(configuration,damaged,fullSource("sampleRate" to 48000),true))
        }
        assertFalse(fullSource("sampleRate" to 44100,"bitsPerSample" to 24).primed)
        val unsupported=ConfigurationFake()
        assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
            fullExperiment(unsupported,fullSource("sampleRate" to 44100,"bitsPerSample" to 24)).status)
        assertFalse(unsupported.events.contains("claimAC"));assertFalse(unsupported.events.contains("native"))
    }
    @Test fun ka15CapacityGateAllowsDrifting48000WithoutEnlargingOwnedBuffers() {
        val ka15=changeData { it.copy(maxPacketSizeRaw=28) }
        val selected=UsbSilentTransportPlan.resolve(ka15,android(ka15),48000).plan!!
        assertEquals(28,selected.timing(UsbFsSpeed.HIGH)!!.capacity)
        val tooSmall=changeData { it.copy(maxPacketSizeRaw=24) }
        assertNull(UsbSilentTransportPlan.resolve(tooSmall,android(tooSmall),48000).plan!!.timing(UsbFsSpeed.HIGH))
        assertEquals(21,selected.timing(UsbFsSpeed.HIGH)!!.directOutUrbCount)
    }
    private val model = descriptors()
    private val resolution = resolve(model)
    private val plan = resolution.plan!!
    private val configurationPlan = plan.configurationPlan
    private fun resolve(value: UsbAudioDescriptorModel) = UsbSilentTransportPlan.resolve(value, android(value))
    private fun android(value: UsbAudioDescriptorModel) = value.interfaces.map { iface -> UsbAndroidInterfaceSnapshot(
        iface.key.number, iface.key.alternate, iface.interfaceClass, iface.subclass, iface.protocol,
        iface.endpoints.map { UsbAndroidEndpointSnapshot(it.address, it.attributes, it.maxPacketSizeRaw, it.interval) }) }
    private fun changeData(transform: (UsbEndpointDescriptor) -> UsbEndpointDescriptor) = model.copy(interfaces = model.interfaces.map {
        if (it.key == configurationPlan.streaming) it.copy(endpoints = it.endpoints.map { ep -> if (ep.address == 11) transform(ep) else ep }) else it })
    private fun changeFeedback(transform: (UsbEndpointDescriptor) -> UsbEndpointDescriptor) = model.copy(interfaces = model.interfaces.map {
        if (it.key == configurationPlan.streaming) it.copy(endpoints = it.endpoints.map { ep -> if (ep.address == 0x8e) transform(ep) else ep }) else it })
    private fun withoutFeedback() = model.copy(interfaces = model.interfaces.map { if (it.key == configurationPlan.streaming)
        it.copy(endpointCount = 1, endpoints = it.endpoints.filterNot { ep -> ep.isIn }) else it })
    private fun bytes(raw: Long) = (0..3).map { ((raw shr (it * 8)) and 0xff).toInt() }
    private fun decode(raw: Long) = UsbExplicitFeedback.decode(bytes(raw))
    private fun source(relative: String) = listOf(Paths.get("src/main", relative), Paths.get("app/src/main", relative))
        .first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    /** Bounds a C++ definition by balanced braces, excluding comments/literals from source inspection. */
    private fun cppBody(text: String, declaration: Regex): String {
        val nonCode = Regex("""(?s)//[^\r\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")
        val code = nonCode.replace(text) { " ".repeat(it.value.length) }
        val match = declaration.find(code) ?: throw AssertionError("C++ definition missing: $declaration")
        val opening = match.range.last
        require(code[opening] == '{')
        var depth = 0
        for (position in opening until code.length) {
            when (code[position]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(opening + 1, position)
                }
            }
        }
        throw AssertionError("Unterminated C++ definition: $declaration")
    }
    private val nominalFeedback = 361267L

    @Test fun acceptsOnlyStereoPcm16And44100() {
        assertEquals(44100L, configurationPlan.sampleRateHz); assertEquals(2, configurationPlan.channels)
        assertEquals(2, configurationPlan.subslotBytes); assertEquals(16, configurationPlan.validBits)
        listOf(UsbAudioFormatDescriptor(1, 3, 24), UsbAudioFormatDescriptor(1, 4, 32)).forEach { format ->
            assertNull(resolve(model.copy(interfaces = model.interfaces.map { if (it.key == configurationPlan.streaming) it.copy(audioFormat = format) else it })).plan)
        }
    }
    @Test fun resolvesSyntheticEndpointsRatherThanKa15Numbers() { assertEquals(11, plan.data.address); assertEquals(0x8e, plan.feedback.address); assertEquals(9, configurationPlan.streaming.alternate) }
    @Test fun rejectsNonIsoData() { assertNull(resolve(changeData { it.copy(attributes = 6) }).plan) }
    @Test fun rejectsDataIn() { assertNull(resolve(changeData { it.copy(address = 0x8b) }).plan) }
    @Test fun rejectsNonDataUsage() { assertNull(resolve(changeData { it.copy(attributes = 0x25) }).plan) }
    @Test fun rejectsAdaptiveOrSynchronousData() { listOf(9, 13).forEach { attr -> assertNull(resolve(changeData { it.copy(attributes = attr) }).plan) } }
    @Test fun rejectsNonIsoFeedback() { assertNull(resolve(changeFeedback { it.copy(attributes = 0x12) }).plan) }
    @Test fun rejectsFeedbackOut() { assertNull(resolve(changeFeedback { it.copy(address = 14) }).plan) }
    @Test fun rejectsFeedbackWithoutFeedbackUsage() { assertNull(resolve(changeFeedback { it.copy(attributes = 1) }).plan) }
    @Test fun missingFeedbackBlocks() { assertNull(resolve(withoutFeedback()).plan) }
    @Test fun ambiguousFeedbackBlocks() {
        val value = model.copy(interfaces = model.interfaces.map { if (it.key == configurationPlan.streaming)
            it.copy(endpointCount = 3, endpoints = it.endpoints + plan.feedback.copy(address = 0x8f)) else it })
        assertNull(resolve(value).plan)
    }
    @Test fun synchronizationAddressMustAgree() { assertNull(resolve(changeData { it.copy(synchronizationAddress = 0x8f) }).plan) }
    @Test fun maxPacketsOnlyCannotSilentlyPadPcm() { assertNull(resolve(changeData { it.copy(audio = UsbAudioEndpointDescriptor(0x80, 0, 0, 0)) }).plan) }
    @Test fun missingClassSpecificDataDescriptorBlocks() { assertNull(resolve(changeData { it.copy(audio = null) }).plan) }
    @Test fun androidEndpointMismatchBlocks() { assertNull(UsbSilentTransportPlan.resolve(model, android(model).map { it.copy(endpoints = emptyList()) }).plan) }
    @Test fun actualHighSpeedIsRequired() { listOf(null, UsbFsSpeed.FULL, UsbFsSpeed.SUPER, UsbFsSpeed.UNKNOWN).forEach { assertNull(plan.timing(it)) } }
    @Test fun highSpeedDataIntervalIs125Microseconds() { assertEquals(125L, plan.timing(UsbFsSpeed.HIGH)!!.dataIntervalUs) }
    @Test fun feedbackIntervalUsesExponentAndNotFrameRateScaling() { assertEquals(1000L, plan.timing(UsbFsSpeed.HIGH)!!.feedbackIntervalUs); assertEquals(500L, UsbIsoTiming.serviceIntervalUs(UsbFsSpeed.HIGH, 3)) }
    @Test fun invalidIntervalBoundsAreRejected() { listOf(0, 17).forEach { assertNull(UsbIsoTiming.serviceIntervalUs(UsbFsSpeed.HIGH, it)) } }
    @Test fun boundedIntervalsRejectLargeQueueSpans() { assertNull(resolve(changeData { it.copy(interval = 4) }).plan); assertNull(resolve(changeFeedback { it.copy(interval = 8) }).plan) }
    @Test fun highSpeedCapacityIncludesEncodedTransactions() { assertEquals(776, UsbIsoTiming.highSpeedCapacity(776)); assertEquals(2048, UsbIsoTiming.highSpeedCapacity(1024 or (1 shl 11))); assertEquals(3072, UsbIsoTiming.highSpeedCapacity(1024 or (2 shl 11))) }
    @Test fun reservedCapacityAndOversizedTransactionAreRejected() { listOf(0, 1025, 4 or (3 shl 11), 4 or 0x8000).forEach { assertNull(UsbIsoTiming.highSpeedCapacity(it)) } }
    @Test fun highBandwidthTransactionsRequireIntervalOne() { assertNull(resolve(changeData { it.copy(maxPacketSizeRaw = 1024 or 0x800, interval = 2) }).plan) }
    @Test fun endpointTooSmallForPlausibleRateBlocksAtBaseline() { val selected = resolve(changeData { it.copy(maxPacketSizeRaw = 20) }); assertNotNull(selected.plan); assertNull(selected.plan!!.timing(UsbFsSpeed.HIGH)) }
    @Test fun feedbackRepresentationRequiresFourByteEndpoint() { listOf(3, 5, 4 or 0x800).forEach { raw -> assertNull(resolve(changeFeedback { it.copy(maxPacketSizeRaw = raw) }).plan) } }
    @Test fun knownFeedbackDecodesLeFixedPointPerMicroframe() { val sample = decode(nominalFeedback); assertEquals(UsbFeedbackValidity.VALID, sample.validity); assertEquals(44100.0, sample.rateHz!!, 0.2); assertEquals(5.5125, sample.framesPerServiceInterval(125)!!, 0.0001) }
    @Test fun feedbackPollingIntervalDoesNotMultiplySampleRate() { assertEquals(44100.0, decode(nominalFeedback).rateHz!!, 0.2); assertEquals(44.1, decode(nominalFeedback).framesPerServiceInterval(1000)!!, 0.001) }
    @Test fun wrongFeedbackLengthIsRejected() { listOf(emptyList(), listOf(1, 2, 3), listOf(1, 2, 3, 4, 5)).forEach { assertEquals(UsbFeedbackValidity.WRONG_LENGTH, UsbExplicitFeedback.decode(it).validity) } }
    @Test fun zeroFeedbackIsRejected() { assertEquals(UsbFeedbackValidity.ZERO, decode(0).validity) }
    @Test fun reservedMostSignificantBitsAreRejected() { assertEquals(UsbFeedbackValidity.RESERVED_BITS, decode(0xf0000001L).validity) }
    @Test fun implausibleFeedbackIsNotClamped() { listOf(1L, 48000L * 65536 / 8000, 0x0fffffffL).forEach { assertEquals(UsbFeedbackValidity.IMPLAUSIBLE, decode(it).validity) } }
    @Test fun plausibilityWindowAcceptsInnerAndRejectsOuterValues() { assertEquals(5L, UsbExplicitFeedback.PLAUSIBILITY_PERCENT); assertEquals(UsbFeedbackValidity.VALID, decode(nominalFeedback * 96 / 100).validity); assertEquals(UsbFeedbackValidity.IMPLAUSIBLE, decode(nominalFeedback * 94 / 100).validity) }
    @Test fun negativeFeedbackPacketStatusIsNotData() { assertEquals(UsbFeedbackValidity.COMPLETION_ERROR, UsbExplicitFeedback.decode(bytes(nominalFeedback), -18).validity) }
    @Test fun unsignedFeedbackArithmeticAvoidsOverflow() { assertEquals(0xffffffffL, decode(0xffffffffL).raw); assertEquals(UsbFeedbackValidity.RESERVED_BITS, decode(0xffffffffL).validity) }
    @Test fun exactNominalAccumulatorProduces44100FramesIn8000Microframes() { val scheduler = UsbIsoFrameAccumulator(); repeat(8000) { scheduler.next(1) }; assertEquals(44100L, scheduler.frames); assertEquals(0L, scheduler.remainder) }
    @Test fun schedulingLargerIntervalsPreservesExactRate() { val scheduler = UsbIsoFrameAccumulator(); repeat(2000) { scheduler.next(4) }; assertEquals(44100L, scheduler.frames) }
    @Test fun nominalToFeedbackSwitchRetainsFractionalRemainder() {
        val scheduler = UsbIsoFrameAccumulator(); repeat(7) { scheduler.next(1) }; val remainder = scheduler.remainder
        val expected = (remainder + nominalFeedback * 8000) / UsbIsoFrameAccumulator.DENOMINATOR
        assertEquals(expected.toInt(), scheduler.next(1, nominalFeedback))
    }
    @Test fun latestFeedbackGovernsPacketizationWithoutSourceChanges() { val slow = UsbIsoFrameAccumulator(); val fast = UsbIsoFrameAccumulator(); repeat(8000) { slow.next(1, nominalFeedback * 96 / 100); fast.next(1, nominalFeedback * 104 / 100) }; assertTrue(slow.frames < fast.frames) }
    @Test fun frameCountersAreMonotonic() { val scheduler = UsbIsoFrameAccumulator(); repeat(1000) { val before = scheduler.frames; val count = scheduler.next(1); assertEquals(before + count, scheduler.frames); assertTrue(count in 5..6) } }
    @Test fun bytesPerFrameIsExactlyFour() { assertEquals(4, UsbIsoTiming.BYTES_PER_FRAME) }
    @Test fun poolDepthIsModestAndBounded() {
        val timing = plan.timing(UsbFsSpeed.HIGH)!!
        assertTrue(UsbIsoTiming.OUT_POOL * UsbIsoTiming.OUT_PACKETS * timing.dataIntervalUs <= 32000)
        assertEquals(21,timing.feedbackUrbCount);assertEquals(1,UsbIsoTiming.FEEDBACK_PACKETS)
        assertEquals(21000L,timing.feedbackCoverageUs)
        assertTrue(timing.feedbackUrbCount<=UsbIsoTiming.MAX_FEEDBACK_POOL)
        assertTrue(timing.feedbackCoverageUs>timing.feedbackHoldoverMs*1000)
    }
    @Test fun bootstrapAndHoldoverAreFinite() { assertEquals(100L, UsbIsoTiming.BOOTSTRAP_MS); assertEquals(20L, plan.timing(UsbFsSpeed.HIGH)!!.feedbackHoldoverMs); assertTrue(UsbIsoTiming.DURATION_MS > UsbIsoTiming.BOOTSTRAP_MS) }
    @Test fun unsupportedAccumulatorIntervalIsRejected() { try { UsbIsoFrameAccumulator().next(32768); fail() } catch (_: IllegalArgumentException) {} }
    @Test fun verifiedResultRequiresCleanAccounting() { assertTrue(native().verified); assertFalse(native("framesCompleted" to 999).verified); assertFalse(native("framesSubmitted" to 999).verified) }
    @Test fun sourceScheduledMismatchPreventsSuccess() { assertFalse(native("sourceFramesGenerated" to 999).verified) }
    @Test fun byteFrameAndPacketUrbConsistencyAreRequired() { assertFalse(native("outBytesSubmitted" to 3999, "kernelOutActualBytes" to 3999).verified); assertFalse(native("outUrbsSubmitted" to 24, "outUrbsCompleted" to 24).verified) }
    @Test fun missingNativeSnapshotDoesNotPretendNormalDurationStop() { val result = UsbIsoTransportResult(17, 23, exceptionType = "Unavailable"); assertEquals("UNAVAILABLE", result.stopReason); assertFalse(result.verified) }
    @Test fun packetAndUrbMismatchPreventsSuccess() { assertFalse(native("outPacketsCompleted" to 199).verified); assertFalse(native("outUrbsCompleted" to 24).verified) }
    @Test fun validFeedbackAndLockedSchedulingAreRequired() { assertFalse(native("feedbackPacketsValid" to 0).verified); assertFalse(native("feedbackScheduledPackets" to 0).verified) }
    @Test fun compromisedIntegrityNeverReportsVerified() { UsbIsoNativeStats.reasons.indices.forEach { assertFalse(native("integrityMask" to (1L shl it)).verified) } }
    @Test fun workerMustStopAndOutstandingMustReachZero() { assertFalse(native("workerStopped" to 0).shutdownAccounted); assertFalse(native("outstandingUrbsFinal" to 1).verified) }
    @Test fun stopMustBeNormalDurationCompletion() { (1 until UsbIsoNativeStats.stops.size).forEach { assertFalse(native("stopReason" to it.toLong()).verified) } }
    @Test fun normalStopCannotVerifyAnEarlyAbortedDuration() { assertFalse(native("durationUs" to 900000).verified) }
    @Test fun nonzeroPcmPreventsVerification() { assertFalse(native("pcmByteOr" to 1).verified) }
    @Test fun cancellationAndUnsubmittedFramesRemainExplicitFailures() { assertFalse(native("framesCancelled" to 1).verified); assertFalse(native("framesNotSubmitted" to 1).verified) }
    @Test fun detachAndPermissionFailureInvalidateNativeAuthority() { assertFalse(native().copy(authorityFailure = UsbClockQueryStatus.DEVICE_DISCONNECTED).verified); assertFalse(native().copy(authorityFailure = UsbClockQueryStatus.PERMISSION_LOST).valid) }
    @Test fun snapshotRejectsOldAttachmentAndStreamGeneration() { listOf(snapshot().copy(numbers = snapshot().numbers.copyOf().also { it[1] = 16 }), snapshot().copy(numbers = snapshot().numbers.copyOf().also { it[2] = 22 })).forEach { rejects(it) } }
    @Test fun snapshotRejectsWrongVersionAndSchemaSize() { rejects(snapshot().copy(numbers = snapshot().numbers.copyOf().also { it[0] = 0 })); rejects(snapshot().copy(numbers = longArrayOf(1, 17, 23))) }
    @Test fun unknownStateAndStopCodesAreRejected() { rejects(snapshot("state" to 99)); rejects(snapshot("stopReason" to 99)) }
    @Test fun feedbackEvidencePreservesRawBytesAndCompletionStatus() {
        val result = UsbIsoNativeStats.decode(snapshot().copy(feedback = longArrayOf(nominalFeedback, 4, 0, 1000, 0, 1000)), 17, 23)
        assertEquals(bytes(nominalFeedback), result.feedback.single().bytes); assertEquals(1000L, result.feedback.single().conservativeAgeUs)
    }
    @Test fun packetEvidencePreservesNegativeStatusAndRequestedActualLengths() {
        val result = UsbIsoNativeStats.decode(snapshot().copy(packets = longArrayOf(11, -18, -18, 24, 0, 23, 1)), 17, 23)
        assertEquals(-18, result.packets.single().packetStatus); assertEquals(24L, result.packets.single().requestedLength); assertEquals(0L, result.packets.single().actualLength)
    }
    @Test fun latePacketFromOldStreamIsRejected() { rejects(snapshot().copy(packets = longArrayOf(11, 0, 0, 24, 24, 22, 1))) }
    @Test fun malformedFeedbackEvidenceAndExcessiveExamplesAreRejected() { rejects(snapshot().copy(feedback = longArrayOf(1))); rejects(snapshot().copy(feedback = LongArray(13 * 6))) }
    @Test fun malformedUnsignedFeedbackLengthIsPreservedWithoutClamping() {
        val result = UsbIsoNativeStats.decode(snapshot("integrityMask" to 32).copy(feedback = longArrayOf(1, 0xffffffffL, 0, 1000, 1, 1000)), 17, 23)
        assertEquals(0xffffffffL, result.feedback.single().length); assertNull(result.feedback.single().rateHz)
    }
    @Test fun busyGateIncludesTheWholeSilentTransaction() { assertTrue(UsbAudioDiagnosticState(silentExperiment = UsbSilentTransportExperimentResult(17, 23, resolution, UsbSilentPhase.CONFIGURING)).diagnosticOperationRunning) }
    @Test fun silentVerificationDoesNotAssertFunctionalRecovery() { assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, UsbSilentTransportExperimentResult(17, 23, resolution).functionalRecovery) }

    @Test fun nativeSourceGeneratesAndChecksZeroOnly() {
        val core = source("cpp/usb_iso_core.h"); assertTrue(core.contains("bytes[i] = 0")); assertTrue(core.contains("byteOr |= bytes[i]"))
        val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("urb->buffer_length = static_cast<int>(length)")); assertTrue(native.contains("source.inspect("))
    }
    @Test fun nativeCapacityOverflowFailsWithoutTruncating() {
        val native = source("cpp/usb_iso_transport.cpp")
        val submit = cppBody(cppBody(native,Regex("""\bstruct\s+Session\s*\{""")),Regex("""\bbool\s+submit\s*\(Slot& slot\)\s*\{"""))
        val guard = "if (!packetFits(frames, payload, frameBytes()) || length + bytes > slot.buffer.size()) { fail(CapacityExceeded, Stop::Capacity); return false; }"
        assertTrue(submit.contains("const uint64_t bytes = static_cast<uint64_t>(frames) * frameBytes()"))
        assertTrue(submit.contains(guard))
        assertTrue(submit.contains("urb->iso_frame_desc[i].length = static_cast<unsigned>(bytes)"))
        assertTrue(submit.indexOf(guard)<submit.indexOf("urb->iso_frame_desc[i].length = static_cast<unsigned>(bytes)"))
        assertTrue(submit.indexOf(guard)<submit.indexOf("USBDEVFS_SUBMITURB"))
        val core = source("cpp/usb_iso_core.h")
        assertTrue(core.contains("(bytesPerFrame==4 || bytesPerFrame==6) && static_cast<uint64_t>(frames) * bytesPerFrame <= payload"))
        assertTrue(native.contains("capacity(config[6]) < worstFrames * selectedFrameBytes"))
        assertTrue(native.contains("count > kOutPackets || capacityBytes < 1 || capacityBytes > 3072"))
        assertTrue(native.contains("buffer.resize(static_cast<size_t>(count) * capacityBytes, 0)"))
        val queue = source("cpp/flac/flac_prefix_queue.h")
        assertTrue(queue.contains("kPrefixCapacity>SIZE_MAX/selected->bytesPerFrame"))
        assertTrue(queue.contains("bytes.resize(kPrefixCapacity*selected->bytesPerFrame)"))
        assertTrue(queue.contains("static_cast<uint64_t>(v.facts[Total])>kCounterMax/queue.frameBytes()"))
        val pcm = source("cpp/flac/flac_pcm_core.h")
        assertTrue(pcm.contains("current <= kCounterMax && amount <= kCounterMax - current"))
        assertTrue(pcm.contains("!canAdd(bytes, frameBytes())"))
        assertTrue(pcm.contains("pcm.frames > kCounterMax / pcm.frameBytes() || pcm.bytes != pcm.frames * pcm.frameBytes()"))
    }
    @Test fun nativeHasNoPerPacketAllocations() { val submit = source("cpp/usb_iso_transport.cpp").substringAfter("bool submit(Slot& slot)").substringBefore("void packetExample"); listOf("resize(", "malloc(", "calloc(", "new ").forEach { assertFalse(submit.contains(it)) } }
    @Test fun nativeQueueUnderrunIsFailureNotDroppedWork() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("outstanding(false) == 0")); assertTrue(native.contains("fail(QueueUnderrun, Stop::Underrun)")) }
    @Test fun nativePreservesFractionAndNeverHardcodesPacketPattern() { val core = source("cpp/usb_iso_core.h"); assertTrue(core.contains("remainder %= denominator")); assertTrue(core.contains("static_cast<uint64_t>(feedback) * 8000")); assertFalse(core.contains("resample")) }
    @Test fun feedbackRequestResidenceIsSeparateFromTheBoundedObservationDeadline() {
        val native = source("cpp/usb_iso_transport.cpp")
        assertTrue(native.contains("FeedbackObservationClock::requestAge(slot.submittedAt,observed)"))
        assertTrue(native.contains("feedbackClock.accept(slot.submittedAt,observed)"))
        assertTrue(native.contains("feedbackReapPass=reapBeforeFeedbackWatchdog(feedbackClock,"))
        assertTrue(source("cpp/usb_feedback_policy.h").contains("clock.expired(pass.evaluatedAt,holdover)"))
        assertFalse(native.substringAfter("void feedbackPacket(").substringBefore("bool reapOne()").contains(".expired("))
        assertTrue(native.contains("++stats[RequestsOverHoldover]"))
        assertFalse(native.contains("if (stale)"))
    }
    @Test fun knownIdentityIsRequiredForDiscardAndCompletion() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("!slot.identity.outstanding || slot.identity.discardRequested")); assertTrue(native.contains("slot.urb.get() == completed")); assertTrue(native.contains("fail(UnknownCompletion")); assertTrue(native.contains("fail(DuplicateCompletion")) }
    @Test fun nativeInspectsEveryPacketAndShortActualLength() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("for (unsigned i = 0; i < slot.packets; ++i)")); assertTrue(native.contains("packet.actual_length != packet.length")); assertTrue(native.contains("packet.status != 0")); assertTrue(native.contains("slot.urb->error_count != 0")) }
    @Test fun nativeUsesRealUapiVariableSizedUrbs() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("allocationBytes(sizeof(usbdevfs_urb), sizeof(usbdevfs_iso_packet_desc), count)")); assertTrue(source("cpp/usb_iso_core.h").contains("extra <= SIZE_MAX - base")); assertTrue(native.contains("USBDEVFS_SUBMITURB")); assertTrue(native.contains("USBDEVFS_REAPURBNDELAY")); assertTrue(native.contains("USBDEVFS_DISCARDURB")) }
    @Test fun nativeRetainsUnreapedPoolsUntilJavaConnectionClose() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("if (!connectionClosed && session->outstanding(false) + session->outstanding(true) != 0) return JNI_FALSE")); assertFalse(native.contains("::close(")); assertFalse(native.contains(".detach(")) }
    @Test fun kotlinOnlyMonitorsAndAlwaysAwaitsNativeWorker() { val kotlin = source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt"); assertTrue(kotlin.contains("withContext(NonCancellable)")); assertTrue(kotlin.contains("worker.await()")); assertFalse(kotlin.contains("framesPerInterval")); assertFalse(kotlin.contains("bulkTransfer")) }
    @Test fun forbiddenAudioAndResetPathsAreAbsentFromNewTransport() {
        val native = source("cpp/usb_iso_transport.cpp") + source("cpp/usb_iso_core.h")
        listOf("USBDEVFS_RESET", "USBDEVFS_SETCONFIGURATION", "USBDEVFS_CONNECT", "USBDEVFS_DISCONNECT", "sin(", "libusb", "AudioTrack", "AudioSink").forEach { assertFalse(native.contains(it)) }
    }
    @Test fun actionIsDebugOnlyAndNeverAutomaticPlayback() { val controller = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt"); assertTrue(controller.contains("if (BuildConfig.DEBUG) runConfigurationDiagnostic(identity, silentRun = true)")); assertFalse(controller.contains("setPreferredMixerAttributes")); assertFalse(controller.contains("AudioTrack(")) }

    @Test fun configurationIsReusedBeforeEndpointWorkAndIdleAfterward() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events)
        val result = experiment(fake, backend)
        assertEquals(UsbSilentStatus.SILENT_TRANSPORT_VERIFIED, result.status)
        assertTrue(fake.events.indexOf("native") > fake.events.indexOf("setAlt:9"))
        assertTrue(fake.events.lastIndexOf("setAlt:0") > fake.events.indexOf("nativeStopped"))
        assertTrue(fake.events.indexOf("restoreClock") > fake.events.indexOf("nativeStopped"))
        assertTrue(fake.events.indexOf("releaseAS") < fake.events.indexOf("releaseAC")); assertTrue(fake.events.indexOf("close") > fake.events.indexOf("nativeStopped"))
    }
    @Test fun endpointLifecyclePhasesPrecedeSharedRestoration() {
        val fake = ConfigurationFake(); val phases = mutableListOf<UsbSilentPhase>()
        runBlocking { UsbSilentTransportExperiment.run(resolution, 17, 23, fake, { _, _ -> BackendFake(fake.events) },
            onPhase = { phases += it; if (it == UsbSilentPhase.RESTORING_USB) assertFalse(fake.events.contains("restoreClock")) }) }
        assertEquals(listOf(UsbSilentPhase.STARTING_TRANSPORT, UsbSilentPhase.TRANSPORT_STOPPED, UsbSilentPhase.RESTORING_USB), phases)
    }
    @Test fun undeclaredGetInterfaceDoesNotBlockSilentTransport() { val fake = ConfigurationFake().apply { reportedAlternate = 6 }; assertEquals(UsbSilentStatus.SILENT_TRANSPORT_VERIFIED, experiment(fake, BackendFake(fake.events)).status) }
    @Test fun declaredContradictionPreventsAnyEndpointWork() { val fake = ConfigurationFake().apply { reportedAlternate = 9 }; val result = experiment(fake, BackendFake(fake.events)); assertFalse(fake.events.contains("native")); assertFalse(result.transport?.verified == true) }
    @Test fun failedConfigurationPreventsAnyEndpointWork() { val fake = ConfigurationFake().apply { claimAs = false }; experiment(fake, BackendFake(fake.events)); assertFalse(fake.events.contains("native")) }
    @Test fun nonHighSpeedBlocksBeforeClaims() { val fake = ConfigurationFake().apply { speed = UsbFsSpeed.FULL }; val result = experiment(fake, BackendFake(fake.events)); assertEquals(UsbSilentStatus.BLOCKED, result.status); assertFalse(fake.events.contains("claimAC")) }
    @Test fun unresolvedFeedbackBlocksBeforeClaims() { val fake = ConfigurationFake(); experiment(fake, BackendFake(fake.events), resolve(withoutFeedback())); assertFalse(fake.events.contains("claimAC")); assertFalse(fake.events.contains("native")) }
    @Test fun unsafeEndpointShutdownSkipsAltClockAndExplicitReleasesUntilClose() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { safe = false; result = native("outstandingUrbsFinal" to 1, "integrityMask" to (1L shl 16)) }
        val result = experiment(fake, backend)
        assertEquals(UsbSilentStatus.FAILED, result.status); assertEquals(1, fake.events.count { it == "setAlt:0" })
        assertFalse(fake.events.contains("restoreClock")); assertFalse(fake.events.contains("releaseAS")); assertFalse(fake.events.contains("releaseAC"))
        assertTrue(fake.events.indexOf("close") < fake.events.indexOf("poolReleasedAfterClose")); assertTrue(result.connectionCloseRequired)
        assertEquals(1L, result.connectionCloseDisposedUrbs); assertEquals(0L, result.outstandingAfterConnectionClose)
    }
    @Test fun nativeDetachInvalidatesConfigurationAndSkipsWrites() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native("integrityMask" to (1L shl 13)).copy(authorityFailure = UsbClockQueryStatus.DEVICE_DISCONNECTED) }
        val result = experiment(fake, backend); assertEquals(UsbSilentStatus.NON_AUTHORITATIVE, result.status); assertFalse(fake.events.contains("restoreClock"))
    }
    @Test fun nativeBadFdAuthorityPreventsAnyRestorationWrites() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native("integrityMask" to 2).copy(authorityFailure = UsbClockQueryStatus.TRANSPORT_ERROR) }
        assertEquals(UsbSilentStatus.NON_AUTHORITATIVE, experiment(fake, backend).status); assertFalse(fake.events.contains("restoreClock")); assertEquals(1, fake.events.count { it == "setAlt:0" })
    }
    @Test fun oldStreamResultCannotPublishCountersIntoCurrentStream() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native().copy(streamGeneration = 22) }
        val result = experiment(fake, backend); assertEquals(UsbSilentStatus.NON_AUTHORITATIVE, result.status); assertTrue(result.transport!!.counters.isEmpty()); assertFalse(fake.events.contains("restoreClock"))
    }
    @Test fun transportFailurePreservesPartialCountersAndRestoresConfiguration() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native("integrityMask" to 8, "framesCompleted" to 900) }
        val result = experiment(fake, backend); assertEquals(UsbSilentStatus.FAILED, result.status); assertEquals(900L, result.transport!!.count("framesCompleted")); assertTrue(fake.events.contains("restoreClock"))
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.configuration!!.targetStatus)
    }
    @Test fun restorationFailureDoesNotInventTransportFailureOrAndroidRecovery() {
        val fake = ConfigurationFake().apply { restoreFails = true }; val result = experiment(fake, BackendFake(fake.events))
        assertEquals(UsbSilentStatus.SILENT_TRANSPORT_VERIFIED, result.status); assertEquals(UsbConfigurationRestorationStatus.PARTIAL, result.configuration!!.restorationStatus); assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalRecovery)
    }
    @Test fun originalClockIsRestoredDynamically() { val fake = ConfigurationFake().apply { rate = 96000 }; val result = experiment(fake, BackendFake(fake.events)); assertEquals(96000L, result.configuration!!.originalClockReadback!!.currentFrequencyHz) }
    @Test fun reportIncludesPartialFailureShutdownAndSeparateRecovery() {
        val fake = ConfigurationFake(); val result = experiment(fake, BackendFake(fake.events).apply { this.result = native("integrityMask" to 8, "framesCompleted" to 900) })
        val text = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 17, silentExperiment = result))
        listOf("Silent Direct USB Transport — Session 7", "DIGITAL ZERO ONLY", "framesCompleted: 900", "COMPROMISED", "worker stopped=true", "Functional normal USB playback: NOT_VERIFIED", "NOT IMPLEMENTED / NOT ASSERTED").forEach { assertTrue(text.contains(it)) }
    }
    @Test fun staleAttachmentReportSuppressesTransportEvidence() { val fake = ConfigurationFake(); val result = experiment(fake, BackendFake(fake.events)); val text = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 18, silentExperiment = result)); assertTrue(text.contains("previous Session 7 evidence omitted")); assertFalse(text.contains("framesCompleted: 1000")) }

    private fun experiment(connection: ConfigurationFake, backend: BackendFake, selected: UsbSilentTransportResolution = resolution) = runBlocking {
        UsbSilentTransportExperiment.run(selected, 17, 23, connection, { _, _ -> backend })
    }
    private fun snapshot(vararg changed: Pair<String, Long>): UsbIsoNativeSnapshot {
        val values = mutableMapOf("version" to UsbIsoNativeStats.VERSION.toLong(), "generation" to 17L, "streamGeneration" to 23L, "started" to 1L, "state" to 7L,
            "formatChannels" to 2L,"formatBitsPerSample" to 16L,"formatBytesPerSample" to 2L,"formatBytesPerFrame" to 4L,"formatSubslotBytes" to 2L,"formatValidBits" to 16L,"formatAlternate" to 9L,"schedulerNominalRateHz" to 44100L, "workerStopped" to 1L, "durationUs" to 1008000L, "sourceFramesGenerated" to 1000L, "framesScheduled" to 1000L, "framesSubmitted" to 1000L, "framesCompleted" to 1000L,
            "outUrbsSubmitted" to 25L, "outUrbsCompleted" to 25L, "outPacketsSubmitted" to 200L, "outPacketsCompleted" to 200L,
            "feedbackUrbsSubmitted" to 100L, "feedbackUrbsCompleted" to 100L, "feedbackPacketsValid" to 98L, "feedbackScheduledPackets" to 190L, "outBytesSubmitted" to 4000L, "kernelOutActualBytes" to 4000L)
        values.putAll(changed)
        return UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map { values[it] ?: 0L }.toLongArray(), longArrayOf(), longArrayOf())
    }
    private fun native(vararg changed: Pair<String, Long>) = UsbIsoNativeStats.decode(snapshot(*changed), 17, 23)
    private fun referencePreflight() = UsbReferencePreflight(listOf(1,0,100,UsbReferencePreflight.EXPECTED_HASH,
        UsbReferencePreflight.EXPECTED_HASH,1024,-1024,1024,1,1,1,98))
    private fun referenceNative(vararg changed: Pair<String, Long>): UsbIsoTransportResult {
        val values = mapOf("sourceMode" to 1L, "referencePreflightPass" to 1L, "referenceTableHash" to UsbReferencePreflight.EXPECTED_HASH,
            "sourcePcmHash" to 0xe397ae906a35c025uL.toLong(), "submittedPcmHash" to 0xe397ae906a35c025uL.toLong(),
            "sourceFramesGenerated" to 44100L, "framesScheduled" to 44100L, "framesSubmitted" to 44100L, "framesCompleted" to 44100L,
            "outUrbsSubmitted" to 1000L, "outUrbsCompleted" to 1000L, "outPacketsSubmitted" to 8000L, "outPacketsCompleted" to 8000L,
            "outBytesSubmitted" to 176400L, "kernelOutActualBytes" to 176400L, "silenceFramesGenerated" to 26500L,
            "leftToneFramesGenerated" to 8800L, "rightToneFramesGenerated" to 8800L, "referenceActivePerCycle" to 98L,
            "leftToneActiveSamples" to 8624L, "rightToneActiveSamples" to 8624L, "maxPositiveSample" to 1024L,
            "minNegativeSample" to -1024L, "peakAbsoluteSample" to 1024L)
        return native(*(values + changed.toMap()).toList().toTypedArray())
    }
    private fun referenceExperiment(fake: ConfigurationFake, backend: BackendFake,
        preflight: UsbReferencePreflight = referencePreflight()) = runBlocking {
        UsbSilentTransportExperiment.run(resolution, 17, 23, fake, { _, _ -> backend },
            mode = UsbPcmDiagnosticMode.REFERENCE, preflight = { preflight })
    }
    @Test fun referencePreflightFailurePreventsBothClaimsAndEndpointWork() {
        val fake = ConfigurationFake()
        val result = referenceExperiment(fake, BackendFake(fake.events), referencePreflight().copy(values = listOf(0)))
        assertEquals(UsbSilentStatus.BLOCKED, result.status)
        assertFalse(fake.events.contains("claimAC")); assertFalse(fake.events.contains("claimAS")); assertFalse(fake.events.contains("native"))
        assertTrue(fake.events.contains("close")); assertFalse(result.preflight!!.passed)
        val report = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 17, referenceExperiment = result))
        assertTrue(report.contains("Reference preflight prevented AC/AS ownership"))
    }
    @Test fun referencePreflightValidatesExpectedHashPackingChannelsAndPeaks() {
        assertTrue(referencePreflight().passed)
        for (index in listOf(0,2,3,4,5,6,7,8,9,10,11)) {
            val changed = referencePreflight().values.toMutableList(); changed[index] = 0
            assertFalse(UsbReferencePreflight(changed).passed)
        }
        assertFalse(referencePreflight().copy(exceptionType = "LinkageError").passed)
        assertEquals("bbd1384f8c503ee1", pcmHashHex(UsbReferencePreflight.EXPECTED_HASH))
    }
    @Test fun deterministicSuccessUsesSharedLifecycleWithoutListeningEvidence() {
        val fake = ConfigurationFake(); val result = referenceExperiment(fake, BackendFake(fake.events).apply { this.result = referenceNative() })
        assertEquals(UsbSilentStatus.DETERMINISTIC_PCM_TRANSPORT_VERIFIED, result.status)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalRecovery)
        assertTrue(fake.events.lastIndexOf("setAlt:0") > fake.events.indexOf("nativeStopped"))
        assertTrue(fake.events.indexOf("restoreClock") > fake.events.indexOf("nativeStopped"))
        assertTrue(fake.events.indexOf("releaseAS") < fake.events.indexOf("releaseAC"))
    }
    @Test fun referenceRequiresCompleteContentAndAllOriginalTransportAccounting() {
        assertTrue(referenceNative().verified)
        for (name in listOf("referencePreflightPass", "leftToneFramesGenerated", "rightToneFramesGenerated",
            "leftToneActiveSamples", "rightToneActiveSamples", "peakAbsoluteSample", "maxPositiveSample",
            "minNegativeSample", "framesCompleted", "feedbackPacketsValid", "feedbackScheduledPackets", "workerStopped"))
            assertFalse(referenceNative(name to 0).verified)
        for (name in listOf("outstandingUrbsFinal", "queueUnderruns", "outPacketErrors", "outUrbErrors",
            "framesCancelled", "framesNotSubmitted", "unexpectedNonZeroSamples", "inactiveChannelNonZeroSamples",
            "nonZeroSamplesOutsideToneWindows", "shutdownIncomplete", "duplicateCompletions"))
            assertFalse(referenceNative(name to 1).verified)
        assertFalse(referenceNative("submittedPcmHash" to 1).verified)
        assertFalse(referenceNative("integrityMask" to (1L shl 25)).verified)
    }
    @Test fun wrongSourceModeCannotBecomeReferenceSuccess() {
        val fake = ConfigurationFake(); val result = referenceExperiment(fake, BackendFake(fake.events))
        assertEquals(UsbSilentStatus.FAILED, result.status)
        assertEquals("SourceModeMismatch", result.transport!!.exceptionType)
    }
    @Test fun failedReferenceKeepsPartialHashesCountersAndRestorationEvidence() {
        val fake = ConfigurationFake()
        val partial = referenceNative("submittedPcmHash" to 1, "integrityMask" to (1L shl 24), "framesCompleted" to 20000)
        val result = referenceExperiment(fake, BackendFake(fake.events).apply { this.result = partial })
        assertEquals(UsbSilentStatus.FAILED, result.status)
        assertEquals(partial.count("sourcePcmHash"), result.transport!!.count("sourcePcmHash"))
        assertEquals(20000L, result.transport!!.count("framesCompleted"))
        assertTrue(fake.events.contains("restoreClock"))
    }
    @Test fun referenceReportSeparatesZeroAndReferenceAndNeverHashesDacConsumption() {
        val zeroFake = ConfigurationFake()
        val zero = experiment(zeroFake, BackendFake(zeroFake.events))
        val referenceFake = ConfigurationFake()
        val reference = referenceExperiment(referenceFake, BackendFake(referenceFake.events).apply { result = referenceNative() })
        val report = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 17,
            silentExperiment = zero, referenceExperiment = reference))
        val zeroTitle = "Silent Direct USB Transport — Session 7"
        val referenceTitle = "Deterministic Direct USB PCM — Session 8"
        assertTrue("Missing ZERO section", report.contains(zeroTitle))
        assertTrue("Missing REFERENCE section", report.contains(referenceTitle))
        val zeroSection = report.substringAfter(zeroTitle).substringBefore(referenceTitle)
        val referenceSection = report.substringAfter(referenceTitle).substringBefore("Raw descriptors")
        fun contains(section: String, expected: String) =
            assertTrue("Missing report evidence: $expected", section.contains(expected))
        fun field(section: String, label: String): String {
            val lines = section.lineSequence().map { it.trim() }.filter { it.startsWith("$label:") }.toList()
            assertEquals("Expected exactly one $label field", 1, lines.size)
            return lines.single().substringAfter(':').trim()
        }

        contains(zeroSection, "Source: DIGITAL ZERO ONLY")
        contains(zeroSection, "PCM all-zero buffer validation: PASS")
        contains(zeroSection, "result=SILENT_TRANSPORT_VERIFIED")
        assertFalse("ZERO must not describe reference-tone content", zeroSection.contains("Reference: 441 Hz"))
        assertFalse("ZERO must not label a generated PCM hash", zeroSection.contains("Source PCM hash:"))
        assertFalse("ZERO must not label a submitted PCM hash", zeroSection.contains("Submitted PCM hash:"))
        assertFalse("REFERENCE must not claim all-zero content", referenceSection.contains("DIGITAL ZERO ONLY"))
        assertFalse("REFERENCE must not show all-zero validation", referenceSection.contains("PCM all-zero buffer validation:"))
        listOf("Reference: 441 Hz", "4410..13209 LEFT", "17620..26419 RIGHT", "bbd1384f8c503ee1",
            "NOT AUTOMATICALLY VERIFIED", "leftToneFramesGenerated: 8800",
            "result=DETERMINISTIC_PCM_TRANSPORT_VERIFIED").forEach { contains(referenceSection, it) }
        assertEquals("e397ae906a35c025", field(referenceSection, "Source PCM hash"))
        assertEquals("e397ae906a35c025", field(referenceSection, "Submitted PCM hash"))
        assertEquals("true", field(referenceSection, "Source/submitted hash match").substringBefore(';'))
        contains(referenceSection, "Payload hash advances only after successful SUBMITURB")
        // A denial is required; mere absence of a hash label does not prove the evidence boundary.
        contains(referenceSection, "No completion hash")
        contains(referenceSection, "or DAC-internal consumed-data hash")
        contains(referenceSection, "DAC internal reconstruction is not measured")
        val inventedPcmHash = Regex("""(?i)\b(?:completion|DAC)\b[^:\r\n]*hash:\s*(?:0x)?[0-9a-f]{16}\b""")
        assertFalse("Must not report a completion or DAC-consumed PCM hash value", inventedPcmHash.containsMatchIn(report))
        for (section in listOf(zeroSection, referenceSection)) {
            contains(section, "Kernel OUT actual bytes are raw ISO completion evidence")
            contains(section, "zero URB/packet status and exact packet length")
            contains(section, "no DAC-consumption inference")
            assertFalse("Must not assert verified DAC consumption", section.contains("PCM consumed internally by DAC"))
            assertFalse("Must not assert verified DAC consumption", section.contains("PCM consumed by DAC verified"))
        }
    }
    @Test fun referenceSnapshotRejectsOversizedOrMalformedPcmExamples() {
        rejects(snapshot().copy(pcm = LongArray(14 * 9)))
        rejects(snapshot().copy(pcm = longArrayOf(4411,64,0,256,0,0,0,0,0)))
        rejects(snapshot().copy(pcm = longArrayOf(4411,32768,0,0,0,0,0,0,0)))
    }
    @Test fun separateReferenceStateParticipatesInBusyGateAndRetainsSilenceEvidence() {
        val resolution = this.resolution
        val silence = UsbSilentTransportExperimentResult(17, 22, resolution)
        val reference = UsbSilentTransportExperimentResult(17, 23, resolution, phase = UsbSilentPhase.CONFIGURING, mode = UsbPcmDiagnosticMode.REFERENCE)
        val state = UsbAudioDiagnosticState(silentExperiment = silence).withEndpointExperiment(UsbPcmDiagnosticMode.REFERENCE, reference)
        assertTrue(state.diagnosticOperationRunning); assertEquals(silence, state.silentExperiment)
        assertEquals(reference, state.endpointExperiment(UsbPcmDiagnosticMode.REFERENCE))
    }
    @Test fun referenceRuntimeIsNativeClosedIntegerVectorWithoutAudioOrFileInputs() {
        val reference = source("cpp/usb_pcm_reference.h").lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val runtime = reference + source("cpp/usb_iso_transport.cpp")
        listOf("sin(", "cos(", "float ", "double ", "AudioTrack", "AudioSink", "ReplayGain", "libusb", "InputStream",
            "ByteBuffer", "ifstream", "fopen(", "USBDEVFS_RESET", "setPreferredMixerAttributes").forEach { assertFalse(runtime.contains(it)) }
        assertTrue(reference.contains("inline constexpr std::array<int16_t, 100>"))
        assertTrue(reference.contains("index - kLeftStart")); assertTrue(reference.contains("index - kRightStart"))
        val jni = source("java/io/github/rsgarrido/sazanami/player/usb/UsbFsDiagnostics.kt")
        assertFalse(jni.contains("pcm: ByteArray")); assertTrue(jni.contains("mode.ordinal"))
        val controller = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt")
        assertTrue(controller.contains("if (BuildConfig.DEBUG) runConfigurationDiagnostic(identity, silentRun = true, mode = UsbPcmDiagnosticMode.REFERENCE)"))
    }
    private fun prefixEvidence(vararg changes: Pair<String,Long>): UsbFlacSourceResult {
        val values = mutableMapOf("version" to 3L,"generation" to 17L,"streamGeneration" to 23L,
            "initialPrimedFrames" to 8192L,"metadataAccepted" to 1L,"sampleRate" to 44100L,"channels" to 2L,"bitsPerSample" to 16L,
            "framesDecoded" to 17385L,"framesEnqueued" to 17384L,"framesConsumedFromQueue" to 1000L,
            "queueFramesAtStop" to 16384L,"prefetchedFramesDiscardedAtStop" to 16384L,
            "minimumQueueDepthDuringStreaming" to 7800L,"maximumQueueDepthDuringStreaming" to 16384L,
            "producerFullWaitCount" to 20L,"lastSourceFrameEnqueued" to 17383L,"lastSourceFrameConsumed" to 999L,
            "expectedNextEnqueueFrame" to 17384L,"expectedNextConsumeFrame" to 1000L,
            "consumedSourcePcmHash" to 123L,"allEnqueuedPrefetchPcmHash" to 456L,
            "producerStopped" to 1L,"producerJoined" to 1L,"decoderReleased" to 1L,"nativeFlacFdClosed" to 1L,"queueReleased" to 1L)
        values.putAll(changes)
        val decoder = io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationResult(
            nativeStatus = io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationStatus.CANCELLED,
            counters = mapOf("nativeFlacRecognized" to 1L,"initStatus" to 0L,"metadataProcessed" to 1L,"streamInfoReceived" to 1L,
                "sampleRate" to values.getValue("sampleRate"),"channels" to values.getValue("channels"),"bitsPerSample" to values.getValue("bitsPerSample"),
                "canonicalPcmBytesHashed" to (values.getValue("framesDecoded")*values.getValue("bitsPerSample")/8*2),"nativeDuplicateClosed" to 1L,"decoderDeleted" to 1L,"finishCalled" to 1L), decoderVersion = "1.5.0")
        return UsbFlacSourceResult(UsbFlacSourceEvidence.fields.associateWith { values[it] ?: 0L },decoder)
    }
    private fun prefixNative(vararg changes: Pair<String,Long>) = native(*(mapOf("sourceMode" to 2L,
        "durationUs" to 5008000L,"transportDurationTargetUs" to 5000000L,"activeTransportUsAtStop" to 5000000L,"prefixSourceFramesConsumed" to 1000L,"prefixInitialPrimedFrames" to 8192L,
        "prefixProducerStopped" to 1L,"prefixProducerJoined" to 1L,"sourcePcmHash" to 123L,"submittedPcmHash" to 123L) +
        changes.toMap()).toList().toTypedArray())
    private class PrefixFake(override var result: UsbFlacSourceResult, private val events: MutableList<String>) : UsbFlacSourceLifecycle {
        override val handle = 1L
        override fun requestStop() { events += "sourceStop" }
        override fun healthy() = result.primed
        override suspend fun prepare(access: () -> UsbClockQueryStatus?) = result.primed
        override suspend fun finish() { events += "sourceJoined" }
    }
    private fun prefixExperiment(fake: ConfigurationFake, source: UsbFlacSourceResult=prefixEvidence(), usb: UsbIsoTransportResult=prefixNative()) = runBlocking {
        val selected=if (source.count("sampleRate")==44100L && source.count("bitsPerSample")==16L) resolution else UsbSilentTransportPlan.resolve(model,android(model),source.count("sampleRate"),source.count("bitsPerSample"),source.count("channels"))
        selected.plan?.let { fake.selectedPlan=it.configurationPlan }
        UsbSilentTransportExperiment.run(selected,17,23,fake,{ _,_ -> BackendFake(fake.events).apply { result=usb } },
            mode=UsbPcmDiagnosticMode.FLAC_PREFIX,prefixSource=PrefixFake(source,fake.events))
    }
    @Test fun flacPrimeFailuresPreventAcAsOwnershipAndUsbWorker() {
        for (fault in listOf(1L,2L,4L,16L,32L,8192L,16384L,32768L)) {
            val fake=ConfigurationFake(); val result=prefixExperiment(fake,prefixEvidence("sourceIntegrityMask" to fault,"initialPrimedFrames" to 0))
            assertFalse(fake.events.contains("claimAC")); assertFalse(fake.events.contains("claimAS")); assertFalse(fake.events.contains("native"))
            assertTrue(fake.events.contains("sourceJoined")); assertTrue(fake.events.contains("close"))
            assertNotEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,result.status)
        }
        assertFalse(prefixEvidence("sampleRate" to 88200).primed)
        assertFalse(prefixEvidence("channels" to 1).primed)
        assertFalse(prefixEvidence("bitsPerSample" to 24).primed)
        assertFalse(prefixEvidence("metadataAccepted" to 0).primed)
        assertFalse(prefixEvidence("initialPrimedFrames" to 8191).primed)
    }
    @Test fun flacUsesFiveSecondDurationAndSameOrderedRestorationTransaction() {
        val fake=ConfigurationFake(); val result=prefixExperiment(fake)
        assertEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,result.status)
        assertTrue(fake.events.indexOf("sourceJoined")<fake.events.lastIndexOf("setAlt:0"))
        assertTrue(fake.events.lastIndexOf("setAlt:0")<fake.events.indexOf("restoreClock"))
        assertTrue(fake.events.indexOf("restoreClock")<fake.events.indexOf("releaseAS"))
        assertTrue(fake.events.indexOf("releaseAS")<fake.events.indexOf("releaseAC"))
        assertTrue(fake.events.indexOf("releaseAC")<fake.events.indexOf("close"))
        assertFalse(prefixNative("durationUs" to 1008000).verified)
        assertTrue(native().verified); assertTrue(referenceNative().verified)
    }
    @Test fun flacSuccessRequiresCommittedCountsBytesHashesAndCleanTransport() {
        for ((name,value) in listOf("framesCompleted" to 999L,"framesSubmitted" to 999L,"kernelOutActualBytes" to 3999L,
            "outBytesSubmitted" to 3999L,"outPacketErrors" to 1L,"outUrbErrors" to 1L,"outstandingUrbsFinal" to 1L,
            "workerStopped" to 0L,"prefixProducerJoined" to 0L,"queueUnderruns" to 1L,"prefixSourceIntegrityMask" to 1L,
            "sourcePcmHash" to 124L,"submittedPcmHash" to 124L,"framesNotSubmitted" to 1L,"stopReason" to 12L)) {
            assertFalse("Accepted $name",prefixNative(name to value).verified)
            assertNotEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,prefixExperiment(ConfigurationFake(),usb=prefixNative(name to value)).status)
        }
    }
    @Test fun flacCleanupAndOrderingEvidenceCannotBeMissing() {
        for (name in listOf("producerStopped","producerJoined","decoderReleased","nativeFlacFdClosed","queueReleased"))
            assertFalse(prefixEvidence(name to 0).clean)
        for (name in listOf("preparedButUnsubmittedFrames","sourceFrameGapCount","sourceFrameDuplicateCount","sourceFrameOrderErrors","queueUnderruns"))
            assertFalse(prefixEvidence(name to 1).clean)
        assertFalse(prefixEvidence("framesDecoded" to 999).clean)
        assertFalse(prefixEvidence("prefetchedFramesDiscardedAtStop" to 0).clean)
        assertFalse(prefixEvidence("expectedNextConsumeFrame" to 999).clean)
        assertTrue(prefixEvidence().clean) // prefetched frames > consumed and full waits are expected
        val fake=ConfigurationFake().apply { restoreFails=true }
        assertEquals(UsbSilentStatus.USB_CONFIGURATION_FAILED,prefixExperiment(fake).status)
    }
    @Test fun flacSourceFailuresHaveDistinctTaxonomyAndStickyReasons() {
        for ((fault,status) in listOf(16L to UsbSilentStatus.SOURCE_EOF_BEFORE_DURATION,64L to UsbSilentStatus.QUEUE_UNDERRUN,
            2048L to UsbSilentStatus.CANCELLED,128L to UsbSilentStatus.FLAC_SOURCE_FAILED,256L to UsbSilentStatus.FLAC_SOURCE_FAILED,
            1024L to UsbSilentStatus.FLAC_SOURCE_FAILED)) {
            val evidence=prefixEvidence("sourceIntegrityMask" to fault)
            assertFalse(evidence.clean); assertTrue(evidence.reasons.isNotEmpty())
            assertEquals(status,prefixExperiment(ConfigurationFake(),evidence).status)
        }
        assertFalse(prefixNative().copy(authorityFailure=UsbClockQueryStatus.STALE_GENERATION).verified)
    }
    @Test fun threeUsbDiagnosticsKeepIndependentResultsAndBusyStates() {
        val silence=UsbSilentTransportExperimentResult(17,21,resolution)
        val reference=UsbSilentTransportExperimentResult(17,22,resolution,mode=UsbPcmDiagnosticMode.REFERENCE)
        val flac=UsbSilentTransportExperimentResult(17,23,resolution,phase=UsbSilentPhase.PRIMING_SOURCE,mode=UsbPcmDiagnosticMode.FLAC_PREFIX)
        val state=UsbAudioDiagnosticState(silentExperiment=silence,referenceExperiment=reference).withEndpointExperiment(UsbPcmDiagnosticMode.FLAC_PREFIX,flac)
        assertEquals(silence,state.endpointExperiment(UsbPcmDiagnosticMode.ZERO))
        assertEquals(reference,state.endpointExperiment(UsbPcmDiagnosticMode.REFERENCE))
        assertEquals(flac,state.endpointExperiment(UsbPcmDiagnosticMode.FLAC_PREFIX)); assertTrue(state.diagnosticOperationRunning)
    }
    @Test fun flacReportSeparatesPrefixHashesMd5AndManualEvidence() {
        val result=prefixExperiment(ConfigurationFake()).copy(originalFlacFdClosed=true)
        val report=UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation=17,flacUsbExperiment=result))
        listOf("Bounded FLAC Direct USB Transport — Session 10","NOT APPLICABLE — bounded prefix","target=5000 ms",
            "source start frame=0","capacity frames=16384","prebuffer target frames=8192","producerFullWaitCount: 20",
            "prefetchedFramesDiscardedAtStop: 16384","Bridge hash match=true","NOT AUTOMATICALLY VERIFIED",
            "nativeFlacFdClosed: 1","original amplitude","full-track playback: NOT IMPLEMENTED").forEach { assertTrue("Missing $it",report.contains(it)) }
        assertFalse(report.contains("Bit-perfect ACTIVE")); assertFalse(report.contains("content://"))
        assertTrue(report.contains("allEnqueuedPrefetchPcmHash covers prefetched frames too; it is NOT compared"))
        val stale=UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation=18,flacUsbExperiment=result))
        assertTrue(stale.contains("previous Session 10 evidence omitted")); assertFalse(stale.contains("prefetchedFramesDiscardedAtStop: 16384"))
    }
    @Test fun flacNativeEvidenceRejectsStaleIdentityAndUnboundedExamples() {
        val snapshot=UsbFlacSourceSnapshot(UsbFlacSourceEvidence.fields.map { prefixEvidence().count(it) }.toLongArray(),longArrayOf(),null)
        assertTrue(UsbFlacSourceEvidence.decode(snapshot,17,23).primed)
        for (bad in listOf(snapshot.copy(numbers=longArrayOf(1)),snapshot.copy(examples=LongArray(6*9)),
            snapshot.copy(numbers=snapshot.numbers.copyOf().also { it[2]=22 }))) {
            try { UsbFlacSourceEvidence.decode(bad,17,23); fail("Invalid source evidence accepted") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun flacDebugPickerAndPrimingPrecedeUsbOpenAndNoApplicationPcmApiExists() {
        val ui=source("java/io/github/rsgarrido/sazanami/ui/settings/UsbFlacPlaybackDiagnosticsSection.kt")
        val controller=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt").substringAfter("private fun runConfigurationDiagnostic")
        assertTrue(ui.contains("if (!BuildConfig.DEBUG) return")); assertTrue(ui.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(ui.contains("token.generation")); assertTrue(ui.contains("uri != null && token != null"))
        assertTrue(controller.indexOf("source.prepare")<controller.indexOf("usb.openDevice(live)"))
        assertTrue(controller.contains("openFileDescriptor(requireNotNull(flacUri), \"r\")")); assertTrue(controller.contains("input.close()"))
        assertTrue(controller.contains("gate.accepts(token)")); assertTrue(controller.contains("originalFlacFdClosed"))
        val core=source("cpp/flac/flac_decode_core.h")+source("cpp/flac/flac_prefix_queue.h")+ui+
            source("java/io/github/rsgarrido/sazanami/player/usb/UsbFlacPrefixSource.kt")
        listOf("AudioTrack(","ExoPlayer","MediaCodec","AudioSink","ReplayGain","resample(","sin(","float ","double ",
            ".detach(","playPcmOverUsb","sendPcm(","nativeInitOgg","takePersistableUriPermission").forEach { assertFalse("Unexpected $it",core.contains(it)) }
        val native=source("cpp/usb_iso_transport.cpp")
        val submit=native.substringAfter("bool submit(Slot& slot)").substringBefore("void packetExample")
        assertTrue(submit.indexOf("queue.reserve")<submit.indexOf("USBDEVFS_SUBMITURB"))
        assertTrue(submit.indexOf("USBDEVFS_SUBMITURB")<submit.indexOf("submittedPrefixHash ="))
        assertTrue(submit.indexOf("submittedPrefixHash =")<submit.indexOf("queue.commit"))
        assertFalse(core.contains("acceptedFeedback")); assertFalse(core.contains("Scheduler"))
    }
    @Test fun flacOriginalSafDescriptorMustCloseBeforeVerifiedPublication() {
        val candidate=prefixExperiment(ConfigurationFake())
        assertEquals(UsbSilentStatus.RESOURCE_CLEANUP_FAILED,candidate.withFlacInputClosed(false,prefixEvidence()).status)
        assertEquals(UsbSilentStatus.RESOURCE_CLEANUP_FAILED,candidate.withFlacInputClosed(true,prefixEvidence("queueReleased" to 0)).status)
        assertEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,candidate.withFlacInputClosed(true,prefixEvidence()).status)
    }
    @Test fun flacSourceHealthIsRecheckedImmediatelyBeforeAcClaim() = runBlocking {
        val fake=ConfigurationFake(); var checks=0
        val source=object : UsbFlacSourceLifecycle {
            override val handle=1L
            override val result=prefixEvidence()
            override fun requestStop() {}
            override fun healthy() = ++checks<4
            override suspend fun prepare(access: () -> UsbClockQueryStatus?) = true
            override suspend fun finish() { fake.events += "sourceJoined" }
        }
        val result=UsbSilentTransportExperiment.run(resolution,17,23,fake,{ _,_ -> error("Worker must not start") },
            mode=UsbPcmDiagnosticMode.FLAC_PREFIX,prefixSource=source)
        assertFalse(fake.events.contains("claimAC")); assertFalse(fake.events.contains("claimAS"))
        assertTrue(fake.events.contains("sourceJoined")); assertNotEquals(UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED,result.status)
    }
    @Test fun sessionEightReferenceTimingAndSessionNineClassificationRemainPinned() {
        fun digest(text: String) = java.security.MessageDigest.getInstance("SHA-256").digest(pcm16WidthSourceEquivalent(text).toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
        assertEquals("e5dfbace7f00de7d4c963a4c53766023a2a4d53cd7761f33dd3496bc98242d71",digest(source("cpp/usb_pcm_reference.h")))
        val core=source("cpp/usb_iso_core.h")
        val algorithms="struct Integrity {"+core.substringAfter("struct Integrity {").substringBefore("} // namespace")
        assertEquals("6351c171e559fcc717b850076b0f6155bc4884dc26c117d1bbe34ba02fbcc606",digest(algorithms))
        val native=source("cpp/usb_iso_transport.cpp")
        // Session 11 adds Slot metadata and completion accounting. Pin the unchanged allocation,
        // cancellation and polling implementations independently to baseline HEAD. Session 12
        // intentionally changes feedback freshness; pin that audited function to its new version,
        // and exercise its policy separately in usb_feedback_policy_test.cpp.
        val allocation="    void allocate("+native.substringAfter("    void allocate(").substringBefore("struct Session {")
        assertEquals("6d1fc92fd7ab7430fedb5840f3550d965cfed3bdcf1b45c909be58ff33808513",digest(allocation))
        val feedback="    void feedbackPacket("+native.substringAfter("    void feedbackPacket(").substringBefore("    bool reapOne()")
        assertEquals("78b404608e4266de52ade3fcb483e5b71ffaecd9fd51cb42ccd166859319af8a",digest(feedback))
        val normalReap=("    bool reapOne()"+native.substringAfter("    bool reapOne()").substringBefore("    void cancel("))
            .replace("\r\n","\n")
            // Remove only the new diagnostic hook; all identity/error/PCM accounting remains pinned.
            .replace("        if (!slot.feedback && !stopping && !stopCause.chosen && !requestedStop.load()) {\n" +
                "            slot.outReapedAt=monotonicUs();\n" +
                "            outContinuity.completed(slot.outReapedAt,slot.packets);\n" +
                "        }\n", "")
            .replace("        uint64_t sourceOffset=0;\n","")
            .replace("            sourceOffset+=slot.frames[i];\n","")
            .replace("""                if (fullTrack) {
                    stats[FullLastCompleted]=std::max(stats[FullLastCompleted],static_cast<jlong>(slot.firstSourceFrame+sourceOffset+slot.frames[i]-1));
                    if (slot.terminal) { ++stats[TailPacketCompletions]; stats[TailFrameCompletions]+=slot.frames[i]; }
                }
""", "")
            // Full-mode counters required braces around this unchanged ordinary completion update.
            // Normalize only that exact wrapper; changes to its guard or frame accounting still fail the pin.
            .replace("            } else if (status == 0) {\n" +
                "                stats[CompletedFrames] += slot.frames[i];\n" +
                "            }\n", "            } else if (status == 0) stats[CompletedFrames] += slot.frames[i];\n")
        assertEquals("b06af3644ac591207308751667b053befa96651974e16a747e9bbb23ccdab879",digest(normalReap))
        val reconciliation="    void cancel("+native.substringAfter("    void cancel(").substringBefore("    void captureOutContinuity()")
        assertEquals("965936723c3ba623c4dae5fcb73c743b67009a80c2789d91a4406656c5c5bc00",digest(reconciliation))
        val pcm=source("cpp/flac/flac_pcm_core.h")
        val classifier="inline Outcome classify("+pcm.substringAfter("inline Outcome classify(").substringBefore("} // namespace")
        assertEquals("500287e078b63ba6fa562cc9842058f1fd0e2eb1d586e8d99187669a8beb2ceb",digest(classifier))
        assertTrue(core.contains("kOutPool = 8, kOutPackets = 8, kFeedbackPool = 2"))
        assertTrue(core.contains("kDurationUs = 1000000, kBootstrapUs = 100000"))
    }
    @Test fun flacProducerCleanupIsStructuredAndPrecedesUsbRestoration() {
        fun precedes(text: String, first: String, second: String) {
            val before=text.indexOf(first); val after=text.indexOf(second)
            assertTrue("$first must exist before $second", before>=0 && after>before)
        }
        val prefix=source("cpp/flac/flac_prefix_queue.h")
        val native=source("cpp/usb_iso_transport.cpp")
        val owner=source("java/io/github/rsgarrido/sazanami/player/usb/UsbFlacPrefixSource.kt")
        val configuration=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioConfigurationExperiment.kt")
        assertTrue(prefix.contains("producer=std::thread(")); assertFalse(prefix.contains(".detach("))
        assertTrue(prefix.contains("changed.notify_all()")); assertTrue(prefix.contains("while (!stop.load() && depth() == kPrefixCapacity)"))
        val finish=prefix.substringAfter("    void finish() {").substringBefore("    std::array<int64_t,PrefixFieldCount>")
        precedes(finish,"queue.requestStop()","producer.join()")
        precedes(finish,"producer.join()","atStop=queue.depth()")
        assertTrue(native.contains("prefix->finish()")); assertTrue(native.contains("::dup(borrowedFd)"))
        assertTrue(prefix.contains("decoder.finalize()")); assertTrue(prefix.contains("facts[FdClosed]"))
        assertTrue(owner.contains("withContext(NonCancellable")); assertTrue(owner.contains("worker.await()"))
        val cleanup=configuration.substringAfter("withContext(NonCancellable)")
        precedes(cleanup,"beforeRestoration()","UsbStreamingAlternateRequest.idle(plan)")
        precedes(cleanup,"releaseStreaming(plan.asClaim)","transport.release(plan.acClaim)")
        precedes(cleanup,"transport.release(plan.acClaim)","transport.close()")
        val core=source("cpp/flac/flac_decode_core.h")
        assertTrue(prefix.contains("queue(full,nullptr),decoder(std::move(duplicate)")); assertTrue(prefix.contains("bool full=false") && prefix.contains("},full)"))
        val metadata=prefix.substringAfter("decoder.metadataAccepted=").substringBefore("decoder.consumeCanonical=")
        assertTrue(metadata.contains("queue.configure(v.pcm.format)"))
        precedes(metadata,"queue.configure(v.pcm.format)","metadataReady.store(true,std::memory_order_release)")
        val session=cppBody(native,Regex("""\bstruct\s+Session\s*\{"""))
        val beginStop=cppBody(session,Regex("""\bvoid\s+beginStop\s*\(\s*\)\s*\{"""))
        assertTrue(beginStop.contains("stopping = true")); assertTrue(beginStop.contains("prefix->finish()"))
        precedes(beginStop,"stopping = true","prefix->finish()")
        val shutdown=cppBody(session,Regex("""\bvoid\s+shutdown\s*\(\s*\)\s*\{"""))
        assertTrue(shutdown.contains("beginStop()")); assertTrue(shutdown.contains("cancel(slot)"))
        precedes(shutdown,"beginStop()","cancel(slot)")
        assertTrue(shutdown.contains("static_cast<jlong>(prefix->queue.prepared)"))
        // JNI serialization follows run/producer join and must precede releasing its source reference.
        val run=cppBody(session,Regex("""\bvoid\s+run\s*\(\s*\)\s*\{"""))
        assertTrue(run.contains("shutdown()"))
        assertFalse(run.contains("prefix.reset()"))
        val jniRun=native.substringAfter("UsbFsDiagnostics_nativeRunSilentTransport(").substringBefore("// Snapshot only atomic progress")
        listOf("session->run()","snapshot(env, *session)","session->prefix.reset()","return result").forEach { assertTrue(it,jniRun.contains(it)) }
        precedes(jniRun,"session->run()","snapshot(env, *session)")
        precedes(jniRun,"snapshot(env, *session)","session->prefix.reset()")
        precedes(jniRun,"session->prefix.reset()","return result")
        val snapshot=native.substringAfter("jobject snapshot(JNIEnv* env, const Session& session)").substringBefore("// Pure CPU preflight")
        assertTrue(snapshot.contains("session.prefix->queue.examples[row][column]"))
        assertTrue(snapshot.contains("session.source.exampleCount * 9"))
        precedes(core,"set_md5_checking(decoder.get(), wholeFile)","init_stream(decoder.get()")
        assertTrue(core.contains("failure == Outcome::Pending && wholeFile"))
        assertTrue(source("cpp/flac/flac_pcm_core.h").contains("if (!process || !eos"))
    }
    @Test fun flacRefillsReapedSlotsBeforeItsDistinctOutPoolWatchdog() {
        val native=source("cpp/usb_iso_transport.cpp")
        val session=cppBody(native,Regex("""\bstruct\s+Session\s*\{"""))
        val run=cppBody(session,Regex("""\bvoid\s+run\s*\(\s*\)\s*\{"""))
        val loop=cppBody(run,Regex("""\bwhile\s*\(\s*integrity\.valid\(\)\s*&&\s*!stopCause\.chosen\s*\)\s*\{"""))
        assertTrue(loop.indexOf("reapOne()")<loop.indexOf("for (auto& slot : slots)"))
        assertTrue(loop.indexOf("for (auto& slot : slots)")<loop.indexOf("outPoolStalled("))
        assertTrue(loop.contains("if (!prefix && (outstanding(false) == 0")) // legacy 7/8 unchanged
        assertTrue(native.contains("stats[Underruns] = static_cast<jlong>(prefix->queue.underruns.load())"))
        assertTrue(native.contains("fail(OutPoolStall, Stop::OutPoolStall)"))
        assertTrue(native.contains("case fv::ReserveResult::InsufficientFrames: fail(QueueUnderrun"))
        assertTrue(native.contains("case fv::ReserveResult::StopRequested: fail(AuthorityChanged, Stop::Requested)"))
        val submit=native.substringAfter("bool submit(Slot& slot)").substringBefore("void packetExample")
        assertTrue(submit.indexOf("scheduler = schedulerBefore")<submit.indexOf("queue.reserve("))
        assertTrue(submit.contains("stats[ScheduledFrames] = scheduledBefore"))
        assertTrue(run.indexOf("started = monotonicUs()")<run.indexOf("submit(slots[i])"))
        assertFalse(run.contains("prime()")); assertFalse(run.contains("openFileDescriptor")); assertFalse(run.contains("claimInterface"))
    }
    @Test fun cppRunInspectionExcludesSeparatePrimingAndHandlesNestedScopes() {
        val text="""
            struct Session {
                void run ( ) {
                    // prime() and an unmatched } in a comment are not executable code.
                    const char* label = "prime() }";
                    if (ready) { submit(slot); }
                }
                void prepare() { prime(); }
            };
            void nativePrime() { source->prime(); }
        """.trimIndent()
        val session=cppBody(text,Regex("""\bstruct\s+Session\s*\{"""))
        val signature=Regex("""\bvoid\s+run\s*\(\s*\)\s*\{""")
        val run=cppBody(session,signature)
        assertTrue(run.contains("submit(slot)")); assertFalse(run.contains("prime()"))
        val withActualPrime=text.replace("submit(slot);","prime(); submit(slot);")
        assertTrue(cppBody(cppBody(withActualPrime,Regex("""\bstruct\s+Session\s*\{""")),signature).contains("prime()"))
    }
    @Test fun flacActiveTimeAndTargetCannotBeInferredFromCleanupDuration() {
        assertTrue(prefixNative().verified)
        assertFalse(prefixNative("activeTransportUsAtStop" to 3483383,"durationUs" to 5100000).verified)
        assertFalse(prefixNative("transportDurationTargetUs" to 1000000).verified)
        val stalled=prefixNative("stopReason" to 13,"outPoolStallEvents" to 1,"integrityMask" to (1L shl 29))
        assertFalse(stalled.verified); assertEquals("OUT_POOL_STALL",stalled.stopReason)
        assertEquals(0L,stalled.count("queueUnderruns")); assertTrue(stalled.integrityReasons.contains("OUT_POOL_STALL"))
        assertTrue(native().verified); assertTrue(referenceNative().verified)
    }
    private fun fullSource(vararg changes: Pair<String,Long>): UsbFlacSourceResult {
        val values=prefixEvidence().counters.toMutableMap().apply {
            putAll(mapOf("fullTrackMode" to 1L,"totalSamples" to 10001L,"framesDecoded" to 10001L,"framesEnqueued" to 10001L,
                "framesConsumedFromQueue" to 10001L,"queueFramesAtStop" to 0L,"prefetchedFramesDiscardedAtStop" to 0L,
                "lastSourceFrameEnqueued" to 10000L,"lastSourceFrameConsumed" to 10000L,"expectedNextEnqueueFrame" to 10001L,
                "expectedNextConsumeFrame" to 10001L,"allEnqueuedPrefetchPcmHash" to 123L,"trueEofReached" to 1L,
                "producerFinishedNaturally" to 1L,"md5VerificationPass" to 1L,"decodedTotalMatchesStreamInfo" to 1L))
            putAll(changes)
        }
        val counters=mapOf("nativeFlacRecognized" to 1L,"decoderCreated" to 1L,"initStatus" to 0L,"streamInfoReceived" to 1L,
            "sampleRate" to values.getValue("sampleRate"),"channels" to values.getValue("channels"),"bitsPerSample" to values.getValue("bitsPerSample"),"metadataProcessed" to 1L,"md5CheckingEnabled" to 1L,
            "md5ReferenceAvailable" to 1L,"md5Verification" to 1L,"processSucceeded" to 1L,"cleanEndOfStream" to 1L,
            "physicalInputEof" to 1L,"decoderState" to 4L,"lifecycleMask" to 511L,"finishCalled" to 1L,"finishReturned" to 1L,
            "totalSamples" to 10001L,"decodedStereoFrames" to 10001L,"decodedSamplesPerChannel" to 10001L,
            "canonicalPcmBytesHashed" to (10001L*(values.getValue("bitsPerSample")/8)*2),"pcmFnv1a" to 123L,"decoderCallbacks" to 3L,"decodedBlocks" to 3L,
            "nativeDuplicateClosed" to 1L,"decoderDeleted" to 1L)
        val decoder=io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationResult(
            nativeStatus=io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationStatus.EXACT_FLAC_DECODE_VERIFIED,
            counters=counters,streamInfoMd5="1".repeat(32),decoderVersion="1.5.0")
        return UsbFlacSourceResult(values,decoder)
    }
    private fun fullNative(vararg changes: Pair<String,Long>) = native(*(mapOf("playbackTransportState" to 7L,"sourceMode" to 3L,"stopReason" to 14L,
        "transportDurationTargetUs" to 0L,"durationUs" to 240000000L,"activeTransportUsAtStop" to 239999000L,
        "streamInfoTotalSamples" to 10001L,"sourceFramesGenerated" to 10001L,"framesScheduled" to 10001L,
        "framesSubmitted" to 10001L,"framesCompleted" to 10001L,"prefixSourceFramesConsumed" to 10001L,
        "prefixInitialPrimedFrames" to 8192L,"prefixProducerStopped" to 1L,"prefixProducerJoined" to 1L,
        "outUrbsSubmitted" to 30L,"outUrbsCompleted" to 30L,"outPacketsSubmitted" to 235L,"outPacketsCompleted" to 235L,
        "outBytesSubmitted" to 40004L,"kernelOutActualBytes" to 40004L,"trueEofReached" to 1L,"producerFinishedNaturally" to 1L,
        "md5VerificationPass" to 1L,"terminalTailOutUrbs" to 1L,"terminalTailFrames" to 101L,"terminalTailPackets" to 3L,
        "terminalTailPacketCompletions" to 3L,"terminalTailFrameCompletions" to 101L,"remainingFramesAtTerminalTransition" to 101L,
        "finalSubmittedSourceFrame" to 10000L,"finalCompletedSourceFrame" to 10000L,
        "wholeDecodedPcmHash" to 123L,"allEnqueuedPcmHash" to 123L,"sourcePcmHash" to 123L,"submittedPcmHash" to 123L)+
        changes.toMap()).toList().toTypedArray())
    private fun fullExperiment(fake: ConfigurationFake,source: UsbFlacSourceResult=fullSource(),usb: UsbIsoTransportResult=fullNative(),safe: Boolean=true) = runBlocking {
        val selected=if (source.count("sampleRate")==44100L && source.count("bitsPerSample")==16L) resolution else UsbSilentTransportPlan.resolve(model,android(model),source.count("sampleRate"),source.count("bitsPerSample"),source.count("channels"))
        selected.plan?.let { fake.selectedPlan=it.configurationPlan }
        UsbSilentTransportExperiment.run(selected,17,23,fake,{ _,_ -> BackendFake(fake.events).apply { result=usb;this.safe=safe } },
            mode=UsbPcmDiagnosticMode.FLAC_FULL,prefixSource=PrefixFake(source,fake.events))
    }
    @Test fun fullAccountingSuccessDoesNotCertifyWireContinuity() {
        val result=fullNative("outContinuityRiskEvents" to 1L,"maxWorkerLoopGapUs" to 9001L,
            "outZeroOutstandingObservations" to 1L,"minEstimatedOutCoverageUs" to 0L)
        assertTrue(result.verified) // Diagnostic risk cannot change the proven Session 11 classifier.
        assertEquals(0L,result.count("outPoolStallEvents"))
        assertEquals(1L,result.count("outContinuityRiskEvents"))
        assertTrue(result.integrityReasons.isEmpty())
        assertEquals(result.count("framesSubmitted"),result.count("framesCompleted"))
        assertEquals(result.count("sourcePcmHash"),result.count("submittedPcmHash"))
    }
    @Test fun wholeTrackNaturalSuccessHasNoDurationCeilingAndClosesOriginalFd() {
        assertTrue(fullSource().fullComplete); assertTrue(fullNative().verified)
        assertTrue(fullNative("durationUs" to 1L,"activeTransportUsAtStop" to 1L).verified) // no minimum timer
        assertTrue(fullNative("durationUs" to 9000000000L).verified) // no maximum song duration
        val candidate=fullExperiment(ConfigurationFake())
        assertEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,candidate.withFlacInputClosed(true,fullSource()).status)
        assertEquals(UsbSilentStatus.RESOURCE_CLEANUP_FAILED,candidate.withFlacInputClosed(false,fullSource()).status)
        assertEquals(UsbSilentStatus.RESOURCE_CLEANUP_FAILED,candidate.withFlacInputClosed(true,fullSource("queueReleased" to 0L)).status)
        assertFalse(fullNative("stopReason" to 0L).verified); assertFalse(fullNative("transportDurationTargetUs" to 5000000L).verified)
    }
    @Test fun wholeTrackRequiresAllFramesBytesFinalIndicesAndTerminalCompletions() {
        for (name in listOf("sourceFramesGenerated","framesScheduled","framesSubmitted","framesCompleted","prefixSourceFramesConsumed","streamInfoTotalSamples"))
            for (value in listOf(10000L,10002L,0L)) assertFalse("Accepted $name=$value",fullNative(name to value).verified)
        for ((name,value) in listOf("outBytesSubmitted" to 40003L,"kernelOutActualBytes" to 40003L,
            "finalSubmittedSourceFrame" to 9999L,"finalCompletedSourceFrame" to 9999L,"terminalTailOutUrbs" to 0L,
            "terminalTailPackets" to 0L,"terminalTailPackets" to 9L,"terminalTailFrameCompletions" to 100L,
            "terminalTailPacketCompletions" to 2L,"remainingFramesAtTerminalTransition" to 100L,
            "prefixInitialPrimedFrames" to 8191L,"outPacketsCompleted" to 234L,"outUrbsCompleted" to 29L))
            assertFalse("Accepted $name=$value",fullNative(name to value).verified)
        for (name in listOf("queueFramesAtNaturalCompletion","prefetchedFramesDiscardedAtNaturalCompletion","eofPaddingFrames",
            "framesNotSubmitted","framesCancelled","outCancelledUrbs","outstandingUrbsFinal","outPacketErrors","outUrbErrors",
            "outPoolStallEvents","sourceVerificationStalls","queueUnderruns","unknownCompletions","duplicateCompletions"))
            assertFalse("Accepted $name",fullNative(name to 1L).verified)
        // Feedback cancellation is separate; completed/accounted feedback does not cancel any PCM OUT.
        assertTrue(fullNative("feedbackCancelledUrbs" to 2L,"cancellationCompletions" to 2L).verified)
    }
    @Test fun wholeTrackHashesCannotOmitMutateDuplicateOrReorderTerminalPcm() {
        for (name in listOf("wholeDecodedPcmHash","allEnqueuedPcmHash","sourcePcmHash","submittedPcmHash")) {
            assertFalse(fullNative(name to 124L).verified)
            assertFalse(fullNative().copy(counters=fullNative().counters-name).verified)
        }
        for (name in listOf("framesDecoded","framesEnqueued","framesConsumedFromQueue","expectedNextEnqueueFrame","expectedNextConsumeFrame"))
            for (n in listOf(10000L,10002L)) assertFalse("Accepted $name=$n",fullSource(name to n).fullComplete)
        for (name in listOf("preparedButUnsubmittedFrames","queueFramesAtStop","prefetchedFramesDiscardedAtStop",
            "sourceFrameGapCount","sourceFrameDuplicateCount","sourceFrameOrderErrors","consumerEmptyEvents","queueUnderruns"))
            assertFalse(fullSource(name to 1L).fullComplete)
        assertFalse(fullSource("lastSourceFrameConsumed" to 9999L).fullComplete)
        assertFalse(fullSource("firstSourceFrameConsumed" to 1L).fullComplete)
        assertFalse(fullSource("allEnqueuedPrefetchPcmHash" to 124L).fullComplete)
    }
    @Test fun fullSourceAndUsbMustDescribeTheSameWholeTrack() {
        val source=fullSource(); val decoder=source.decoder!!
        val differentHash=source.copy(counters=source.counters+("allEnqueuedPrefetchPcmHash" to 124L)+("consumedSourcePcmHash" to 124L),
            decoder=decoder.copy(counters=decoder.counters+("pcmFnv1a" to 124L)))
        assertTrue(differentHash.fullComplete); assertTrue(fullNative().verified)
        assertEquals(UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED,fullExperiment(ConfigurationFake(),differentHash).status)
        // Even internally consistent transport counters cannot verify a different complete source length.
        val other=fullNative("streamInfoTotalSamples" to 10002L,"sourceFramesGenerated" to 10002L,"framesScheduled" to 10002L,
            "framesSubmitted" to 10002L,"framesCompleted" to 10002L,"prefixSourceFramesConsumed" to 10002L,
            "outBytesSubmitted" to 40008L,"kernelOutActualBytes" to 40008L,"finalSubmittedSourceFrame" to 10001L,
            "finalCompletedSourceFrame" to 10001L)
        assertTrue(other.verified)
        assertEquals(UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED,fullExperiment(ConfigurationFake(),usb=other).status)
    }
    @Test fun wholeTrackMd5EosDecoderAndReleaseEvidenceAreRequired() {
        val source=fullSource(); val decoder=source.decoder!!
        for (name in listOf("md5CheckingEnabled","md5ReferenceAvailable","md5Verification","cleanEndOfStream","physicalInputEof",
            "processSucceeded","finishReturned","nativeDuplicateClosed","decoderDeleted"))
            assertFalse(name,source.copy(decoder=decoder.copy(counters=decoder.counters+(name to 0L))).fullComplete)
        for (name in listOf("decoderErrors","formatContradictions","frameOrderErrors","outOfRangeSamples","resourceErrors"))
            assertFalse(name,source.copy(decoder=decoder.copy(counters=decoder.counters+(name to 1L))).fullComplete)
        assertFalse(source.copy(decoder=decoder.copy(streamInfoMd5="0".repeat(32))).fullComplete)
        assertFalse(source.copy(decoder=decoder.copy(counters=decoder.counters+("md5Verification" to 2L))).fullComplete)
        for (n in listOf(10000L,10002L)) assertFalse(source.copy(decoder=decoder.copy(counters=decoder.counters+("decodedStereoFrames" to n))).fullComplete)
        for (name in listOf("trueEofReached","producerFinishedNaturally","producerStopped","producerJoined","decoderReleased",
            "nativeFlacFdClosed","queueReleased","md5VerificationPass","decodedTotalMatchesStreamInfo"))
            assertFalse(fullSource(name to 0L).fullComplete)
        assertFalse(fullSource("totalSamples" to 0L).fullComplete)
        assertFalse(source.copy(decoder=decoder.copy(nativeStatus=io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationStatus.CANCELLED)).fullComplete)
    }
    @Test fun fullSourceRejectsUnsupportedAndMissingWholeFileProofBeforeOwnership() {
        for ((fault,status) in listOf(4L to UsbSilentStatus.FLAC_SOURCE_FAILED,131072L to UsbSilentStatus.FLAC_MD5_UNAVAILABLE,
            262144L to UsbSilentStatus.FLAC_MD5_MISMATCH,524288L to UsbSilentStatus.STREAMINFO_TOTAL_MISMATCH,
            64L to UsbSilentStatus.QUEUE_UNDERRUN,2048L to UsbSilentStatus.CANCELLED)) {
            val fake=ConfigurationFake(); val result=fullExperiment(fake,fullSource("sourceIntegrityMask" to fault))
            assertEquals(status,result.status); assertFalse(fake.events.contains("claimAC")); assertFalse(fake.events.contains("native"))
        }
        for ((name,value) in listOf("sampleRate" to 88200L,"channels" to 1L,"bitsPerSample" to 24L,"metadataAccepted" to 0L)) {
            val fake=ConfigurationFake(); assertFalse(fullSource(name to value).primed)
            assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,fullExperiment(fake,fullSource(name to value)).status)
            assertFalse(fake.events.contains("claimAC"))
        }
    }
    @Test fun fullNaturalStopPreservesRestorationAndRejectsUnsafePendingOut() {
        val fake=ConfigurationFake(); fullExperiment(fake)
        assertTrue(fake.events.indexOf("nativeStopped")<fake.events.indexOf("sourceJoined"))
        assertTrue(fake.events.indexOf("sourceJoined")<fake.events.lastIndexOf("setAlt:0"))
        assertTrue(fake.events.lastIndexOf("setAlt:0")<fake.events.indexOf("restoreClock"))
        assertTrue(fake.events.indexOf("restoreClock")<fake.events.indexOf("releaseAS"))
        assertTrue(fake.events.indexOf("releaseAS")<fake.events.indexOf("releaseAC"))
        assertTrue(fake.events.indexOf("releaseAC")<fake.events.indexOf("close"))
        val unsafe=ConfigurationFake()
        assertNotEquals(UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
            fullExperiment(unsafe,usb=fullNative("outstandingUrbsFinal" to 1L),safe=false).status)
        assertFalse(unsafe.events.contains("restoreClock")); assertFalse(unsafe.events.contains("releaseAS")); assertFalse(unsafe.events.contains("releaseAC"))
        assertEquals(UsbSilentStatus.USB_CONFIGURATION_FAILED,fullExperiment(ConfigurationFake().apply { restoreFails=true }).status)
    }
    @Test fun fullUserStopAndEarlierFailuresNeverBecomeNaturalSuccess() {
        assertEquals(UsbSilentStatus.CANCELLED,fullExperiment(ConfigurationFake(),usb=fullNative("stopReason" to 1L)).status)
        assertEquals(UsbSilentStatus.OUT_POOL_STALL,fullExperiment(ConfigurationFake(),usb=fullNative("stopReason" to 13L)).status)
        assertEquals(UsbSilentStatus.SOURCE_VERIFICATION_STALLED,fullExperiment(ConfigurationFake(),usb=fullNative("stopReason" to 15L)).status)
        assertEquals(UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED,fullExperiment(ConfigurationFake(),usb=fullNative("integrityMask" to (1L shl 30))).status)
        for (stop in UsbIsoNativeStats.stops.indices.filter { it!=14 }) assertFalse(fullNative("stopReason" to stop.toLong()).verified)
        for (failure in listOf(UsbClockQueryStatus.DEVICE_DISCONNECTED,UsbClockQueryStatus.PERMISSION_LOST,UsbClockQueryStatus.STALE_GENERATION))
            assertFalse(fullNative().copy(authorityFailure=failure).verified)
    }
    @Test fun fourEndpointModesAndWholeFileVerifierStayIndependent() {
        val zero=UsbSilentTransportExperimentResult(17,20,resolution)
        val reference=zero.copy(streamGeneration=21,mode=UsbPcmDiagnosticMode.REFERENCE)
        val prefix=zero.copy(streamGeneration=22,mode=UsbPcmDiagnosticMode.FLAC_PREFIX)
        val full=zero.copy(streamGeneration=23,mode=UsbPcmDiagnosticMode.FLAC_FULL,phase=UsbSilentPhase.PRIMING_SOURCE)
        val state=UsbAudioDiagnosticState(silentExperiment=zero,referenceExperiment=reference,flacUsbExperiment=prefix)
            .withEndpointExperiment(UsbPcmDiagnosticMode.FLAC_FULL,full)
        assertTrue(state.diagnosticOperationRunning); assertEquals(zero,state.silentExperiment); assertEquals(reference,state.referenceExperiment)
        assertEquals(prefix,state.flacUsbExperiment); assertEquals(full,state.fullFlacUsbExperiment)
        assertTrue(native().verified); assertTrue(referenceNative().verified); assertTrue(prefixNative().verified)
        assertFalse(prefixNative("activeTransportUsAtStop" to 4999999L).verified)
    }
    @Test fun fullReportHasWholeTrackEvidenceWithExplicitBoundedClaims() {
        val result=fullExperiment(ConfigurationFake()).withFlacInputClosed(true,fullSource())
        val report=UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation=17,fullFlacUsbExperiment=result))
        listOf("Full FLAC Direct USB Transport — Session 11","FULL_FLAC_USB_TRANSPORT_VERIFIED","NATURAL_EOF_COMPLETE",
            "Duration target=NONE","verification=PASS","wholeDecodedPcmFNV","All whole-track FNVs equal=true",
            "eofPaddingFrames=0","finalSubmittedSourceFrame: 10000","Terminal OUT: 1..8","Production Bit-perfect playback: NOT IMPLEMENTED",
            "DAC-internal reconstructed samples are not measured","Original Kotlin FLAC fd closed=true").forEach { assertTrue(it,report.contains(it)) }
        assertFalse(report.contains("content://")); assertFalse(report.contains("066098d12bfd5e3c"))
        val stale=UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation=18,fullFlacUsbExperiment=result))
        assertTrue(stale.contains("previous Session 11 evidence omitted")); assertFalse(stale.contains("finalSubmittedSourceFrame: 10000"))
    }
    @Test fun fullSourceSchemaRejectsOldVersionStaleIdentityAndInvalidFlags() {
        val source=fullSource()
        val snapshot=UsbFlacSourceSnapshot(UsbFlacSourceEvidence.fields.map { source.count(it) }.toLongArray(),longArrayOf(),null)
        assertTrue(UsbFlacSourceEvidence.decode(snapshot,17,23).primed)
        val flags=listOf("fullTrackMode","trueEofReached","producerFinishedNaturally","md5VerificationPass","decodedTotalMatchesStreamInfo")
        for (name in flags) {
            val bad=snapshot.copy(numbers=snapshot.numbers.copyOf().also { it[UsbFlacSourceEvidence.fields.indexOf(name)]=2L })
            try { UsbFlacSourceEvidence.decode(bad,17,23); fail(name) } catch (_: IllegalArgumentException) {}
        }
        for (index in listOf(0,1,2)) {
            val bad=snapshot.copy(numbers=snapshot.numbers.copyOf().also { it[index]-=1L })
            try { UsbFlacSourceEvidence.decode(bad,17,23); fail("Accepted identity/version $index") } catch (_: IllegalArgumentException) {}
        }
        val short=fullSource("totalSamples" to 1L,"initialPrimedFrames" to 1L)
        assertTrue(short.primed); assertFalse(short.copy(counters=short.counters+("trueEofReached" to 0L)).primed)
        assertFalse(fullSource("initialPrimedFrames" to 8191L).primed)
        assertFalse(fullSource().copy(counters=fullSource().counters-"queueReleased").fullComplete)
    }
    @Test fun fullDebugPickerStopAndWholeFileNativeBoundariesAreExplicit() {
        val ui=source("java/io/github/rsgarrido/sazanami/ui/settings/UsbFullFlacDiagnosticsSection.kt")
        val controller=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt")
        val stop=controller.substringAfter("fun stopFullFlac(").substringBefore("private fun runConfigurationDiagnostic")
        assertTrue(ui.contains("if (!BuildConfig.DEBUG) return")); assertTrue(ui.contains("OpenDocument()"))
        assertTrue(ui.contains("token.generation")); assertTrue(ui.contains("result.streamGeneration")); assertTrue(ui.contains("stopFullFlac"))
        assertTrue(stop.contains("running.streamGeneration==stream")); assertFalse(stop.contains("readJob?.cancel()"))
        assertTrue(controller.contains("transport.stopSilentTransport(authority=false)"))
        val prefix=source("cpp/flac/flac_prefix_queue.h"); val decoder=source("cpp/flac/flac_decode_core.h")
        assertTrue(prefix.contains("fullTrack(full),queue(full,nullptr),decoder(")); assertTrue(prefix.contains("},full)"))
        assertTrue(prefix.contains("decoder.enforceExpectedTotal=fullTrack"))
        assertTrue(prefix.contains("if(!queue.configure(v.pcm.format)) { queue.fail(PrefixFormat);return; }"))
        assertTrue(decoder.indexOf("set_md5_checking")<decoder.indexOf("init_stream"))
        assertTrue(decoder.indexOf("v.enforceExpectedTotal &&")<decoder.indexOf("!v.pcm.append"))
        assertTrue(prefix.contains("queue.eofReached.store(true,std::memory_order_release)")); assertTrue(prefix.contains("decoder.failure==Outcome::Exact"))
        val native=source("cpp/usb_iso_transport.cpp"); val submit=native.substringAfter("bool submit(Slot& slot)").substringBefore("void packetExample")
        assertTrue(submit.contains("urb->number_of_packets=static_cast<int>(slot.packets)"))
        assertTrue(submit.contains("length=frameCount*frameBytes()"))
        assertTrue(submit.contains("urb->iso_frame_desc[i].length=slot.frames[i]*frameBytes()"))
        assertFalse(submit.contains("slot.frames[i]*kBytesPerFrame"))
        assertTrue(native.contains("packet.actual_length != packet.length"))
        val run=cppBody(cppBody(native,Regex("""\bstruct\s+Session\s*\{""")),Regex("""\bvoid\s+run\s*\(\s*\)\s*\{"""))
        assertTrue(run.indexOf("submit(slot)")<run.indexOf("outPoolStalled("))
        listOf("AudioTrack(","AudioSink","ExoPlayer","ReplayGain","resample(","seekTo(","pausePlayback(","PlaybackService","playNext(")
            .forEach { assertFalse(it,(ui+prefix+decoder).contains(it)) }
    }
    private fun rejects(packet: UsbIsoNativeSnapshot) { try { UsbIsoNativeStats.decode(packet, 17, 23); fail("Malformed evidence accepted") } catch (_: IllegalArgumentException) {} }
    private inner class BackendFake(private val events: MutableList<String>) : UsbSilentTransportBackend {
        var result = native(); var safe = true
        override val usbCleanupSafe get() = safe
        override var memoryReleased = true
        override suspend fun run(): UsbIsoTransportResult { events += "native"; events += "nativeStopped"; memoryReleased = safe; return result }
        override fun connectionClosed(succeeded: Boolean) { if (succeeded && !safe) { events += "poolReleasedAfterClose"; memoryReleased = true } }
    }
    private inner class ConfigurationFake : UsbAudioConfigurationTransport {
        var selectedPlan=configurationPlan
        var ignoreTargetRate=false
        var ranges=listOf(44100L,48000L,96000L)
        var rateAfterTargetAlternate: Long?=null
        val events = mutableListOf<String>(); var claimAs = true; var reportedAlternate: Int? = null; var rate = 48000L; var alt = 0
        var restoreFails = false; var speed = UsbFsSpeed.HIGH; var ac = false; var streaming = false; var closed = false; var now = 0L
        override fun interfaces() = android(model).mapIndexed { index, it -> UsbAudioClaimInterface(index, it.number, it.alternate, it.interfaceClass, it.subclass, it.protocol) }
        override fun connectionValid() = !closed
        override fun snapshot() = UsbFsOwnershipDiagnostics(17, selectedPlan.probe, UsbFsProbeStatus.RECORDED,
            selectedPlan.probe.interfaces.map { UsbFsDriverBinding(it, UsbFsIoctlResult(UsbFsIoctlOperation.GETDRIVER, 0, 0, null), if (ac || streaming) "usbfs" else "synthetic") },
            UsbFsSpeedEvidence(UsbFsIoctlResult(UsbFsIoctlOperation.GET_SPEED, 3, 0, null), speed), androidAudio = frameworkSnapshot())
        override fun frameworkSnapshot() = UsbAndroidAudioDiagnostics()
        override fun lastControlEvidence(): UsbFsControlEvidence? = null
        override fun claim(request: UsbAudioForcedClaimRequest): Boolean { events += "claimAC"; ac = true; return true }
        override fun claimStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean { check(ac); events += "claimAS"; streaming = claimAs; return claimAs }
        override fun releaseStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean { events += "releaseAS"; streaming = false; return true }
        override fun release(request: UsbAudioForcedClaimRequest): Boolean { events += "releaseAC"; ac = false; return true }
        private fun u32(value: Long) = ByteArray(4) { (value shr (it * 8)).toByte() }
        override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
            check(ac)
            val data = when { request.rangeHeaderOnly -> byteArrayOf(ranges.size.toByte(), 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> byteArrayOf(ranges.size.toByte(), 0) + ranges.flatMap { (u32(it) + u32(it) + u32(0)).toList() }.toByteArray()
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(rate)
                else -> byteArrayOf(1) }
            data.copyInto(buffer); return data.size
        }
        override fun control(request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence {
            check(ac && streaming)
            val evidence = if (request.operation == UsbConfigurationControlOperation.GET_INTERFACE) UsbFsControlEvidence(1, 1, 0, null, listOf(reportedAlternate ?: alt))
                else { val restore = request.operation == UsbConfigurationControlOperation.ORIGINAL_CLOCK_RESTORE
                    events += if (restore) "restoreClock" else "targetClock"
                    if ((restore && !restoreFails) || (!restore && !ignoreTargetRate)) rate = request.rateHz!!
                    UsbFsControlEvidence(4, 4, 0, null, request.payload) }
            return UsbConfigurationControlEvidence.decode(request, evidence)
        }
        override fun setAlternate(request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence { check(ac && streaming); events += "setAlt:${request.alternate}"; alt = request.alternate
            if(request.alternate==selectedPlan.streaming.alternate) rateAfterTargetAlternate?.let { rate=it }
            return UsbStreamingAlternateEvidence(request, UsbFsIoctlResult(UsbFsIoctlOperation.SETINTERFACE, 0, 0, null), UsbClockQueryStatus.SUCCESS) }
        override fun close() { events += "close"; closed = true }
        override fun monotonicMs() = now
        override suspend fun pause(milliseconds: Long) { now += milliseconds }
    }
    private fun descriptors(): UsbAudioDescriptorModel {
        val ac = UsbInterfaceDescriptor(UsbInterfaceKey(18, 10, 0), 0, 1, 1, 0x20, audioControl = UsbAudioControlDescriptor(0x0200,
            entities = listOf(UsbAudioEntity(43, UsbAudioEntityKind.CLOCK_SOURCE, controls = 7),
                UsbAudioEntity(44, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(43), terminalType = 0x0101),
                UsbAudioEntity(45, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(44), clockSources = listOf(43), terminalType = 0x0301))))
        val idle = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 0), 0, 1, 2, 0x20)
        val target = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 9), 2, 1, 2, 0x20,
            endpoints = listOf(UsbEndpointDescriptor(11, 5, 776, 1, synchronizationAddress = 0x8e, audio = UsbAudioEndpointDescriptor(0, 0, 0, 0)),
                UsbEndpointDescriptor(0x8e, 0x11, 4, 4)), audioStreaming = UsbAudioStreamingDescriptor(44, 0, 1, 1, 2, 3), audioFormat = UsbAudioFormatDescriptor(1, 2, 16))
        return UsbAudioDescriptorModel(200, null, listOf(UsbConfigurationDescriptor(18, 1, 182, 3, 0x80, 50)), listOf(UsbInterfaceAssociation(18, 10, 2, 1, 0, 0x20)),
            listOf(UsbInterfaceDescriptor(UsbInterfaceKey(18, 3, 0), 0, 3, 0, 0), ac, idle, target, target.copy(key=target.key.copy(alternate=10),audioFormat=UsbAudioFormatDescriptor(1,3,24))), emptyList(), emptyList())
    }
}
