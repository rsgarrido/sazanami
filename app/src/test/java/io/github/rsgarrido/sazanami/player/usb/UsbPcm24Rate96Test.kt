package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.completedFramePositionMs
import org.junit.Assert.*
import org.junit.Test

/** Pure JVM arithmetic/policy tests; no JNI, source files, USB or hardware claims. */
class UsbPcm24Rate96Test {
    @Test fun completeTuplesAdmitExactlyFourFormats() {
        // The standalone native matrix uses these same independent expected tuples/domains.
        val accepted=setOf(Triple(44100L,2L,16L),Triple(48000L,2L,16L),
            Triple(48000L,2L,24L),Triple(96000L,2L,24L))
        for (rate in listOf(0L,44100L,48000L,88200L,96000L,176400L,192000L,384000L))
            for (channels in listOf(0L,1L,2L,6L,8L)) for (bits in listOf(0L,8L,16L,24L,32L)) {
                val tuple=Triple(rate,channels,bits)
                assertEquals(tuple.toString(),tuple in accepted,UsbDirectPcmFormat.supports(rate,channels,bits))
                assertEquals(tuple.toString(),tuple in accepted,UsbDirectPcmFormat.resolve(rate,channels,bits)!=null)
            }
    }
    private fun bytes(raw: Long)=(0..3).map { ((raw ushr (8*it)) and 255L).toInt() }
    @Test fun selected96FeedbackRetainsEncodingToleranceAndFatalErrors() {
        val raw=786432L
        val nominal=UsbExplicitFeedback.decode(bytes(raw),sampleRateHz=96000)
        assertEquals(UsbFeedbackValidity.VALID,nominal.validity)
        assertEquals(96000.0,nominal.rateHz!!,0.0)
        assertEquals(12.0,nominal.framesPerServiceInterval(125)!!,0.0)
        for (percent in listOf(96L,100L,104L))
            assertEquals(UsbFeedbackValidity.VALID,UsbExplicitFeedback.decode(bytes(raw*percent/100),sampleRateHz=96000).validity)
        for (percent in listOf(94L,106L))
            assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(raw*percent/100),sampleRateHz=96000).validity)
        assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(393216),sampleRateHz=96000).validity)
        assertEquals(UsbFeedbackValidity.IMPLAUSIBLE,UsbExplicitFeedback.decode(bytes(raw),sampleRateHz=48000).validity)
        assertEquals(UsbFeedbackValidity.ZERO,UsbExplicitFeedback.decode(bytes(0),sampleRateHz=96000).validity)
        assertEquals(UsbFeedbackValidity.WRONG_LENGTH,UsbExplicitFeedback.decode(listOf(0,0,12),sampleRateHz=96000).validity)
        assertEquals(UsbFeedbackValidity.RESERVED_BITS,UsbExplicitFeedback.decode(bytes(0x10000000),sampleRateHz=96000).validity)
        assertEquals(UsbFeedbackValidity.COMPLETION_ERROR,UsbExplicitFeedback.decode(bytes(raw),-18,96000).validity)
    }
    @Test fun nominalAndDrifting96UseExistingRemainderAccumulator() {
        for (feedback in listOf<Long?>(null,786432L)) {
            val scheduler=UsbIsoFrameAccumulator(96000)
            repeat(8000) { assertEquals(12,scheduler.next(1,feedback)) }
            assertEquals(96000L,scheduler.frames);assertEquals(0L,scheduler.remainder)
        }
        val fast=UsbIsoFrameAccumulator(96000);val slow=UsbIsoFrameAccumulator(96000)
        var thirteen=false
        repeat(8000) {
            if (fast.next(1,786432L*104/100)==13) thirteen=true
            slow.next(1,786432L*96/100)
        }
        assertTrue(thirteen);assertTrue(fast.frames>96000);assertTrue(slow.frames<96000)
        val phase=fast.remainder
        val increment=786432L*96/100*8000
        assertEquals(((phase+increment)/UsbIsoFrameAccumulator.DENOMINATOR).toInt(),fast.next(1,786432L*96/100))
        assertEquals((phase+increment)%UsbIsoFrameAccumulator.DENOMINATOR,fast.remainder)
    }
    @Test fun selectedRateAndWidthTogetherDetermineCapacityAtEverySupportedInterval() {
        val data=UsbEndpointDescriptor(7,5,78,1)
        val feedback=UsbEndpointDescriptor(0x83,0x11,4,4)
        for ((rate,width,minimum) in listOf(Triple(44100L,4,24),Triple(48000L,4,28),
            Triple(48000L,6,42),Triple(96000L,6,78))) {
            assertNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data.copy(maxPacketSizeRaw=minimum-1),feedback,rate,width))
            assertNotNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data.copy(maxPacketSizeRaw=minimum),feedback,rate,width))
        }
        assertEquals(72,12*6);assertEquals(78,13*6)
        assertNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data.copy(maxPacketSizeRaw=72),feedback,96000,6))
        for ((interval,minimum) in listOf(1 to 78,2 to 156,3 to 306)) {
            assertNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data.copy(interval=interval,maxPacketSizeRaw=minimum-1),feedback,96000,6))
            assertNotNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data.copy(interval=interval,maxPacketSizeRaw=minimum),feedback,96000,6))
        }
        assertNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data,feedback,96000,4)) // No PCM16/96 leak.
        assertNull(UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data,feedback,96000,8)) // No padded PCM24.
        assertNull(UsbIsoTiming.resolve(UsbFsSpeed.FULL,data,feedback,96000,6))
        val timing=UsbIsoTiming.resolve(UsbFsSpeed.HIGH,data,feedback,96000,6)!!
        assertEquals(21,timing.directOutUrbCount);assertEquals(8,UsbIsoTiming.OUT_PACKETS)
        assertEquals(21000L,timing.outCoverageUs(UsbPcmDiagnosticMode.FLAC_FULL))
        assertEquals(21,timing.feedbackUrbCount);assertEquals(1,UsbIsoTiming.FEEDBACK_PACKETS)
        assertEquals(21000L,timing.feedbackCoverageUs);assertEquals(20000L,timing.feedbackHoldoverUs)
        assertEquals(100L,UsbIsoTiming.BOOTSTRAP_MS);assertEquals(3,UsbIsoTiming.INVALID_FEEDBACK_LIMIT)
    }
    @Test fun packedSamplesAndCompletedFrameTimeRetainTheirSeparateUnits() {
        val format=UsbDirectPcmFormat.resolve(96000,2,24)!!
        assertEquals(3,format.bytesPerSample);assertEquals(6,format.bytesPerFrame)
        assertEquals(3,format.subslotBytes);assertEquals(24,format.validBits)
        val row=listOf(0L,-8388608L,8388607L,0L,0L,128L,255L,255L,127L)
        assertEquals(listOf(0,0,128,255,255,127),decodeDirectPcmExample(row,format).bytes)
        assertEquals(decodeDirectPcmExample(row,UsbDirectPcmFormat.resolve(48000,2,24)!!),decodeDirectPcmExample(row,format))
        assertEquals(1000L,completedFramePositionMs(96000,96000))
        assertEquals(999L,completedFramePositionMs(95999,96000))
        val state=IntegratedDirectPlaybackState(sampleRateHz=96000,completedFrames=96000,totalFrames=192000)
        assertEquals(1000L,state.positionMs);assertEquals(2000L,state.durationMs)
        assertEquals(1000L,state.positionMs) // No wall-clock interpolation or decoded-ahead position.
        val progress=UsbFullProgress(96000,192000,96000)
        assertEquals(96000L,progress.sampleRateHz)
        assertEquals(96000L,progress.completedFrames)
        assertEquals(Long.MAX_VALUE/96000*1000+(Long.MAX_VALUE%96000)*1000/96000,
            completedFramePositionMs(Long.MAX_VALUE,96000))
    }
}
