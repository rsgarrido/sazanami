package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.completedFramePositionMs
import org.junit.Assert.*
import org.junit.Test

class UsbPcm24FormatTest {
    @Test fun policyAcceptsExactlyFourNativeFlacPcmFormats() {
        for(rate in listOf(44100L,48000L)) assertTrue(UsbDirectPcmFormat.supports(rate,2,16))
        assertTrue(UsbDirectPcmFormat.supports(48000,2,24))
        assertTrue(UsbDirectPcmFormat.supports(96000,2,24))
        for(rate in listOf(44100L,88200L,176400L,192000L)) assertFalse(UsbDirectPcmFormat.supports(rate,2,24))
        for(rate in listOf(88200L,96000L,176400L,192000L)) assertFalse(UsbDirectPcmFormat.supports(rate,2,16))
        for(bits in listOf(16L,24L,32L)) {
            assertFalse(UsbDirectPcmFormat.supports(48000,1,bits));assertFalse(UsbDirectPcmFormat.supports(48000,8,bits))
        }
        assertFalse(UsbDirectPcmFormat.supports(48000,2,32))
    }
    @Test fun immutableFormatRepresentsPackedWidthAndDescriptorResolvedAlternate() {
        val source=UsbDirectPcmFormat.resolve(48000,2,24)!!
        val selected=UsbSelectedPcmFormat(source,UsbInterfaceKey(18,11,10))
        assertEquals(3,source.bytesPerSample);assertEquals(6,source.bytesPerFrame)
        assertEquals(3,source.subslotBytes);assertEquals(24,source.validBits)
        assertEquals(10,selected.streaming.alternate);assertSame(source,selected.source)
        assertEquals(4,UsbDirectPcmFormat.resolve(48000,2,16)!!.bytesPerFrame)
    }
    @Test fun evidenceChecksAllSixPackedBytesAndRejectsSignPaddingAndTruncation() {
        val format=UsbDirectPcmFormat.resolve(48000,2,24)!!
        val samples=listOf(0L,1L,-1L,8388607L,-8388608L,0x123456L,-0x123456L,65537L,-65537L)
        for(left in samples) for(right in samples) {
            val bytes=listOf(left,right).flatMap { sample -> (0..2).map { (sample ushr (it*8)) and 255L } }
            val row=listOf(0L,left,right)+bytes
            val decoded=decodeDirectPcmExample(row,format)
            assertEquals(left.toInt(),decoded.left);assertEquals(right.toInt(),decoded.right)
            assertEquals(bytes.map(Long::toInt),decoded.bytes);assertEquals(6,decoded.bytes.size)
            val corrupted=row.toMutableList().also { it[5]=it[5] xor 1L }
            try { decodeDirectPcmExample(corrupted,format);fail("Corrupted PCM24 byte accepted") } catch (_: IllegalArgumentException) { }
        }
        val legacy=UsbDirectPcmFormat.resolve(44100,2,16)!!
        assertEquals(listOf(0,128,255,127),decodeDirectPcmExample(listOf(0,-32768,32767,0,128,255,127,0,0),legacy).bytes)
        try { decodeDirectPcmExample(listOf(0,-32768,32767,0,128,255,127,1,0),legacy);fail("Unused padding accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun frameSchedulingAndTimeGeometryIgnoreWidthButPayloadCapacityDoesNot() {
        val pcm16=UsbIsoFrameAccumulator(48000);val pcm24=UsbIsoFrameAccumulator(48000)
        repeat(8000) { i ->
            val raw=if(i<4000) 393216L else 393216L*104/100
            assertEquals(pcm16.next(1,raw),pcm24.next(1,raw))
            assertEquals(pcm16.remainder,pcm24.remainder)
        }
        assertTrue(pcm24.frames>48000)
        val timing=UsbIsoTiming(125,1000,776)
        assertEquals(21,timing.directOutUrbCount);assertEquals(21000L,timing.outCoverageUs(UsbPcmDiagnosticMode.FLAC_FULL))
        assertEquals(21,timing.feedbackUrbCount);assertEquals(1,UsbIsoTiming.FEEDBACK_PACKETS)
        assertEquals(20000L,timing.feedbackHoldoverUs);assertEquals(100L,UsbIsoTiming.BOOTSTRAP_MS)
        assertEquals(1000L,completedFramePositionMs(48000,48000))
        assertEquals(1000L,IntegratedDirectPlaybackState(sampleRateHz=48000,completedFrames=48000,totalFrames=96000).positionMs)
    }
}
