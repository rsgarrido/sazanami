package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import io.github.rsgarrido.sazanami.player.spectrum.Pcm16Observer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EqualizerPcmObservationTest {
    @Before
    fun resetBridgeBeforeTest() {
        EqualizerRuntimeBridge.release()
    }

    @After
    fun resetBridgeAfterTest() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun `accepted PCM is observed before byte exact bypass without changing output`() {
        val observer = RecordingObserver()
        val processor = EqualizerAudioProcessor(pcmObserver = observer)
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush(StreamMetadata.DEFAULT)
        val inputBytes = ByteArray(4 * 1_024) { index -> (index * 31).toByte() }
        val input = ByteBuffer.allocateDirect(inputBytes.size)
            .order(ByteOrder.nativeOrder())
        input.put(inputBytes)
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        val outputBytes = ByteArray(output.remaining())
        output.get(outputBytes)

        assertEquals(1, observer.callCount)
        assertEquals(0, observer.positionAtEntry)
        assertEquals(0, observer.positionAtExit)
        assertEquals(inputBytes.size, observer.byteCount)
        assertEquals(48_000, observer.sampleRateHz)
        assertEquals(2, observer.channelCount)
        assertArrayEquals(inputBytes, outputBytes)
        processor.reset()
    }

    private class RecordingObserver : Pcm16Observer {
        var callCount = 0
        var positionAtEntry = -1
        var positionAtExit = -1
        var byteCount = 0
        var sampleRateHz = 0
        var channelCount = 0

        override fun onPcm16(
            source: ByteBuffer,
            offsetBytes: Int,
            byteCount: Int,
            sampleRateHz: Int,
            channelCount: Int
        ) {
            callCount++
            positionAtEntry = source.position()
            if (byteCount > 0) source.get(offsetBytes)
            positionAtExit = source.position()
            this.byteCount = byteCount
            this.sampleRateHz = sampleRateHz
            this.channelCount = channelCount
        }

        override fun onDiscontinuity() = Unit
        override fun setSourceActive(active: Boolean) = Unit
        override fun setPlaybackActive(active: Boolean) = Unit
        override fun setPcmAvailable(available: Boolean) = Unit
        override fun close() = Unit
    }
}
