package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.IntegratedDirectPhase
import io.github.rsgarrido.sazanami.player.completedFramePositionMs
import io.github.rsgarrido.sazanami.player.integratedProgressState
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class UsbDirectSampleRateTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    private fun precedes(text: String,before: String,after: String) {
        val first=text.indexOf(before);val second=text.indexOf(after)
        assertTrue("Missing boundary: $before",first>=0);assertTrue("Missing boundary: $after",second>=0)
        assertTrue("$before must precede $after",first<second)
    }
    @Test fun authoritativeMetadataAndReadbacksPrecedePcmAndNativeGateDefendsRate() {
        val controller=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt")
            .substringAfter("private fun runConfigurationDiagnostic")
        precedes(controller,"source.prepare","val sourceRateHz = source.result.count(\"sampleRate\")")
        precedes(controller,"val sourceRateHz = source.result.count(\"sampleRate\")","usb.openDevice(live)")
        assertTrue(controller.contains("UsbSilentTransportPlan.resolve(model, selected.interfaces, sourceRateHz, source.result.count(\"bitsPerSample\"), source.result.count(\"channels\"))"))
        val endpoint=source("java/io/github/rsgarrido/sazanami/player/usb/UsbSilentTransportExperiment.kt")
        precedes(endpoint,"require(configuration.targetStatus == UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED)","backend = factory")
        precedes(endpoint,"require(configuration.selectedFormatMatches(sourceRate,sourceChannels,sourceBits))","backend = factory")
        precedes(endpoint,"backend = factory","native = requireNotNull(backend).run()")
        val configuration=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioConfigurationExperiment.kt")
        val agreement=configuration.substringAfter("internal fun UsbAudioConfigurationResult.selectedRateMatches(")
            .substringBefore("internal fun UsbAudioConfigurationResult.selectedFormatMatches(")
        for(required in listOf("targetStatus == UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED",
            "resolution.plan?.sampleRateHz == sourceRateHz","targetClockReadback?.status == UsbClockQueryStatus.SUCCESS",
            "targetClockReadback.currentFrequencyHz == sourceRateHz","configuredClockReadback?.status == UsbClockQueryStatus.SUCCESS",
            "configuredClockReadback.currentFrequencyHz == sourceRateHz")) assertTrue(required,agreement.contains(required))
        assertTrue(configuration.contains("selectedRateMatches(rateHz) && resolution.plan?.pcmFormat == UsbDirectPcmFormat.resolve(rateHz,channels,bits)"))
        // Query/SET/readback/alternate/configured readback are all forward gates before endpoint work.
        precedes(configuration,"UsbConfigurationMutationPermit.fromPreflight(","targetClockSet = control(")
        precedes(configuration,"targetClockSet = control(","targetClockReadback = clock(")
        precedes(configuration,"requireStage(result.targetClockReadback?.status == UsbClockQueryStatus.SUCCESS", "targetAlternateSet = alternate(")
        precedes(configuration,"targetAlternateSet = alternate(","configuredClockReadback = clock(")
        precedes(configuration,"requireStage(result.configuredClockReadback?.status == UsbClockQueryStatus.SUCCESS","endpointWork.runConfigured(result)")
        precedes(configuration,"requireStage(result.configuredClockValidity?.status == UsbClockQueryStatus.SUCCESS","endpointWork.runConfigured(result)")
        val native=source("cpp/usb_iso_transport.cpp")
        for (field in listOf("Rate", "Channels", "Bits")) assertTrue(native.contains("metadataFacts[fv::$field] != config["))
        assertTrue(native.contains("scheduler(sampleRateHz)"))
        assertTrue(native.contains("scheduler.nominalRate"))
        assertTrue(native.contains("config[16] < 2 && (config[12] != static_cast<int>(kRate) || config[15] != 16)"))
        val creation=native.substringAfter("UsbFsDiagnostics_nativeCreateSilentTransport(").substringBefore("UsbFsDiagnostics_nativeRunSilentTransport(")
        for(gate in listOf("!sazanami::directpcm::supports(config[12],config[13],config[15])",
            "config[14] != config[15]/8","config[2] < 1 || config[2] > 255","config[2] == config[3]",
            "!found->second->metadataReady.load(std::memory_order_acquire)","metadataFacts[fv::Rate] != config[12]",
            "metadataFacts[fv::Channels] != config[13]","metadataFacts[fv::Bits] != config[15]",
            "prefixSource->queue.format->subslotBytes!=static_cast<uint32_t>(config[14])"))
            precedes(creation,gate,"std::make_shared<Session>")
        val wrapper=source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt")
        precedes(wrapper,"UsbFsDiagnostics.createSilentTransport(","playbackControl?.bind(")
        precedes(wrapper,"playbackControl?.bind(","async(Dispatchers.IO) { UsbFsDiagnostics.runSilentTransport")
        val queue=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(queue.contains("kPrefixCapacity = 16384"));assertTrue(queue.contains("kPrefixPrebuffer = 8192"))
        assertTrue(queue.contains("kPrefixDurationUs = 5000000"))
        assertFalse(native.contains("066098d12bfd5e3c")) // Known 88 hash belongs to that exact manual fixture only.
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        // Initial seeks preserve the accepted rate and validate progress before confirmation/publication.
        assertTrue(adapter.contains("val before=if(start.positionPending) evidence.copy(pendingSeekSerial=commands.intent().seekSerial) else evidence"))
        precedes(adapter,"val before=if(start.positionPending)","val accepted=integratedProgressState(before,progress,start.playWhenReady)")
        precedes(adapter,"val accepted=integratedProgressState(before,progress,start.playWhenReady)","start.confirmPosition(progress)")
        precedes(adapter,"start.confirmPosition(progress)","update(accepted)")
        assertTrue(adapter.contains("PositionSupplier"));assertFalse(adapter.contains("ExoPlayer"))
        // Exercise the actual delegated projection; its source-selected rate must survive Pause/Resume.
        for((rate,bits) in listOf(44100L to 16L,48000L to 16L,48000L to 24L,96000L to 24L)) {
            assertTrue(UsbDirectPcmFormat.supports(rate,2,bits))
            val initial=IntegratedDirectPlaybackState(phase=IntegratedDirectPhase.PREPARING,metadataDurationMs=99)
            val prepared=integratedProgressState(initial,UsbFullProgress(0,rate*2,rate,UsbFullTransportPhase.STARTING),false)
            assertEquals(rate,prepared.sampleRateHz);assertEquals(2000L,prepared.durationMs)
            val paused=integratedProgressState(prepared,UsbFullProgress(rate,rate*2,rate,UsbFullTransportPhase.PAUSED),false)
            assertEquals(1000L,paused.positionMs);assertEquals(rate,paused.completedFrames)
            val resumed=integratedProgressState(paused,UsbFullProgress(rate,rate*2,rate,UsbFullTransportPhase.PLAYING),true)
            assertEquals(rate,resumed.sampleRateHz);assertEquals(paused.positionMs,resumed.positionMs)
            val wrongRate=if(rate==48000L) 44100L else 48000L
            try {
                integratedProgressState(paused,UsbFullProgress(rate,rate*2,wrongRate,UsbFullTransportPhase.RESUMING),true)
                fail("Resume must not change an accepted source rate")
            } catch(_: IllegalArgumentException) {}
        }
        assertFalse("A supported clock rate is not permission for PCM16/96",UsbDirectPcmFormat.supports(96000,2,16))
    }
    private fun bytes(raw: Long) = (0..3).map { ((raw shr (8*it)) and 255).toInt() }
    @Test fun boundaryPermitsExactlyTwoStereo16Formats() {
        for (rate in listOf(44100L,48000L)) {
            assertTrue(UsbDirectPcmFormat.supports(rate,2,16))
            for (channels in listOf(0L,1L,3L,8L)) assertFalse(UsbDirectPcmFormat.supports(rate,channels,16))
            for (bits in listOf(8L,32L)) assertFalse(UsbDirectPcmFormat.supports(rate,2,bits))
        }
        for (rate in listOf(0L,88200L,96000L,176400L,192000L)) assertFalse(UsbDirectPcmFormat.supports(rate,2,16))
        assertTrue(UsbDirectPcmFormat.supports(48000,2,24));assertFalse(UsbDirectPcmFormat.supports(44100,2,24))
    }
    @Test fun feedbackEnvelopeMovesWithSelectedRateWithoutChangingEncodingOrTolerance() {
        for (rate in listOf(44100L,48000L)) {
            val nominal=rate*65536/8000
            for (percent in listOf(96L,100L,104L))
                assertEquals(UsbFeedbackValidity.VALID,UsbExplicitFeedback.decode(bytes(nominal*percent/100),sampleRateHz=rate).validity)
            for (percent in listOf(94L,106L))
                assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(nominal*percent/100),sampleRateHz=rate).validity)
        }
        val sample=UsbExplicitFeedback.decode(bytes(393216),sampleRateHz=48000)
        assertEquals(48000.0,sample.rateHz!!,0.0)
        assertEquals(6.0,sample.framesPerServiceInterval(125)!!,0.0)
        assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(393216),sampleRateHz=44100).validity)
        assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(361267),sampleRateHz=48000).validity)
    }
    @Test fun nominalAndDrifting48000UseSameRationalAccumulator() {
        val nominal=UsbIsoFrameAccumulator(48000)
        repeat(8000) { assertEquals(6,nominal.next(1)) }
        assertEquals(48000L,nominal.frames);assertEquals(0L,nominal.remainder)
        val drift=UsbIsoFrameAccumulator(48000)
        repeat(17) { drift.next(1,393216L*104/100) }
        val phase=drift.remainder
        assertEquals(((phase+393216L*96/100*8000)/UsbIsoFrameAccumulator.DENOMINATOR).toInt(),drift.next(1,393216L*96/100))
        val faster=UsbIsoFrameAccumulator(48000)
        val slower=UsbIsoFrameAccumulator(48000)
        repeat(8000) { faster.next(1,393216L*104/100);slower.next(1,393216L*96/100) }
        assertTrue(faster.frames>48000);assertTrue(slower.frames<48000)
    }
    @Test fun geometryIsTimeBasedAndCompletedFrameTimeUsesSourceRate() {
        val timing=UsbIsoTiming(125,1000,28)
        assertEquals(21,timing.outUrbCount(UsbPcmDiagnosticMode.FLAC_FULL))
        assertEquals(21,timing.outUrbCount(UsbPcmDiagnosticMode.FLAC_PREFIX))
        assertEquals(8,UsbIsoTiming.OUT_PACKETS);assertEquals(21000L,timing.outCoverageUs(UsbPcmDiagnosticMode.FLAC_FULL))
        assertEquals(21,timing.feedbackUrbCount);assertEquals(1,UsbIsoTiming.FEEDBACK_PACKETS)
        assertEquals(21000L,timing.feedbackCoverageUs);assertEquals(20000L,timing.feedbackHoldoverUs)
        assertEquals(100L,UsbIsoTiming.BOOTSTRAP_MS);assertEquals(3,UsbIsoTiming.INVALID_FEEDBACK_LIMIT)
        for (rate in listOf(44100L,48000L)) {
            assertEquals(1000L,completedFramePositionMs(rate,rate))
            val state=IntegratedDirectPlaybackState(sampleRateHz=rate,completedFrames=rate,totalFrames=rate*2,metadataDurationMs=99)
            assertEquals(1000L,state.positionMs);assertEquals(2000L,state.durationMs)
            assertTrue(completedFramePositionMs(Long.MAX_VALUE,rate)>0)
        }
        assertEquals(0L,IntegratedDirectPlaybackState(completedFrames=48000,totalFrames=96000).positionMs)
        assertEquals(48000L,UsbFullProgress(48000,96000,48000).sampleRateHz)
    }
}
