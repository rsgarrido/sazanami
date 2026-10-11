package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.completedFramePositionMs
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class UsbDirectSampleRateTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path))
        .first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    @Test fun authoritativeMetadataAndReadbacksPrecedePcmAndNativeGateDefendsRate() {
        val controller=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt")
            .substringAfter("private fun runConfigurationDiagnostic")
        val selected=controller.indexOf("val sourceRateHz = source.result.count(\"sampleRate\")")
        assertTrue(selected>controller.indexOf("source.prepare"))
        assertTrue(selected<controller.indexOf("usb.openDevice(live)"))
        assertTrue(controller.contains("UsbSilentTransportPlan.resolve(model, selected.interfaces, sourceRateHz)"))
        val endpoint=source("java/io/github/rsgarrido/sazanami/player/usb/UsbSilentTransportExperiment.kt")
        assertTrue(endpoint.indexOf("require(configuration.selectedRateMatches(sourceRate))")<endpoint.indexOf("backend = factory"))
        val native=source("cpp/usb_iso_transport.cpp")
        for (field in listOf("Rate", "Channels", "Bits")) assertTrue(native.contains("metadataFacts[fv::$field] != config["))
        assertTrue(native.contains("scheduler(sampleRateHz)"))
        assertTrue(native.contains("scheduler.nominalRate"))
        assertTrue(native.contains("config[16] < 2 && config[12] != static_cast<int>(kRate)"))
        val queue=source("cpp/flac/flac_prefix_queue.h")
        assertTrue(queue.contains("kPrefixCapacity = 16384"));assertTrue(queue.contains("kPrefixPrebuffer = 8192"))
        assertTrue(queue.contains("kPrefixDurationUs = 5000000"))
        assertFalse(native.contains("066098d12bfd5e3c")) // Known 88 hash belongs to that exact manual fixture only.
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        assertTrue(adapter.contains("sampleRateHz=progress.sampleRateHz"))
        assertTrue(adapter.contains("PositionSupplier"));assertFalse(adapter.contains("ExoPlayer"))
    }
    private fun bytes(raw: Long) = (0..3).map { ((raw shr (8*it)) and 255).toInt() }
    @Test fun boundaryPermitsExactlyTwoStereo16Formats() {
        for (rate in listOf(44100L,48000L)) {
            assertTrue(UsbDirectPcmFormat.supports(rate,2,16))
            for (channels in listOf(0L,1L,3L,8L)) assertFalse(UsbDirectPcmFormat.supports(rate,channels,16))
            for (bits in listOf(8L,24L,32L)) assertFalse(UsbDirectPcmFormat.supports(rate,2,bits))
        }
        for (rate in listOf(0L,88200L,96000L,176400L,192000L)) assertFalse(UsbDirectPcmFormat.supports(rate,2,16))
        assertFalse(UsbDirectPcmFormat.supports(48000,2,24))
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
