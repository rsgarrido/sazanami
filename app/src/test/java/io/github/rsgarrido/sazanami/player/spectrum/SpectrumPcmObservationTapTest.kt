package io.github.rsgarrido.sazanami.player.spectrum

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumPcmObservationTapTest {
    @Test
    fun `observer uses absolute reads and does not mutate source buffer`() {
        val runtime = SpectrumAnalyzerRuntime(scheduleWorker = false)
        val observer = runtime.createPcmObserver()
        val sourceSamples = ShortArray(1_024 * 2) { index -> (index - 1_024).toShort() }
        val source = directBuffer(sourceSamples)
        val originalBytes = source.snapshot()
        val originalPosition = source.position()
        val originalLimit = source.limit()
        runtime.setConsumerCount(1)
        observer.setSourceActive(true)
        observer.setPlaybackActive(true)

        observer.onPcm16(source, source.position(), source.remaining(), 48_000, 2)
        val frame = runtime.processLatestForTest(33_333_333L)

        assertNotNull(frame)
        assertEquals(originalPosition, source.position())
        assertEquals(originalLimit, source.limit())
        assertArrayEquals(originalBytes, source.snapshot())
        observer.close()
        runtime.close()
    }

    @Test
    fun `disabled and standby observers do no handoff work`() {
        val runtime = SpectrumAnalyzerRuntime(scheduleWorker = false)
        val observer = runtime.createPcmObserver()
        val source = directBuffer(ShortArray(2_048) { 2_000 })
        observer.setSourceActive(true)
        observer.setPlaybackActive(true)

        observer.onPcm16(source, 0, source.remaining(), 48_000, 2)
        assertNull(runtime.processLatestForTest())

        runtime.setConsumerCount(1)
        observer.setSourceActive(false)
        observer.onPcm16(source, 0, source.remaining(), 48_000, 2)
        assertNull(runtime.processLatestForTest())
        assertEquals(0L, runtime.droppedWindowCountForTest())
        observer.close()
        runtime.close()
    }

    @Test
    fun `format changes and explicit discontinuity discard stale windows`() {
        val published = mutableListOf<SpectrumFrame>()
        val runtime = SpectrumAnalyzerRuntime(scheduleWorker = false)
        val observer = runtime.createPcmObserver()
        runtime.setPublisher(published::add)
        runtime.setConsumerCount(1)
        observer.setSourceActive(true)
        observer.setPlaybackActive(true)
        val source = directBuffer(ShortArray(2_048) { 4_000 })
        observer.onPcm16(source, 0, source.remaining(), 48_000, 2)
        observer.onDiscontinuity()

        assertNull(runtime.processLatestForTest(33_333_333L))
        assertTrue(published.last().availability == SpectrumAvailability.UNAVAILABLE)
        observer.close()
        runtime.close()
    }

    @Test
    fun `source handoff drops old source data and accepts only new authority`() {
        val runtime = SpectrumAnalyzerRuntime(scheduleWorker = false)
        val outgoing = runtime.createPcmObserver()
        val incoming = runtime.createPcmObserver()
        runtime.setConsumerCount(1)
        outgoing.setSourceActive(true)
        outgoing.setPlaybackActive(true)
        incoming.setPlaybackActive(true)
        val source = directBuffer(ShortArray(2_048) { 6_000 })
        outgoing.onPcm16(source, 0, source.remaining(), 48_000, 2)

        outgoing.setSourceActive(false)
        incoming.setSourceActive(true)
        outgoing.onPcm16(source, 0, source.remaining(), 48_000, 2)
        incoming.onPcm16(source, 0, source.remaining(), 48_000, 2)

        val frame = runtime.processLatestForTest(33_333_333L)
        assertNotNull(frame)
        assertEquals(SpectrumAvailability.AVAILABLE, frame?.availability)
        outgoing.close()
        incoming.close()
        runtime.close()
    }

    @Test
    fun `missing PCM settles available state to unavailable without synthetic data`() {
        val published = mutableListOf<SpectrumFrame>()
        val runtime = SpectrumAnalyzerRuntime(scheduleWorker = false)
        val observer = runtime.createPcmObserver()
        runtime.setPublisher(published::add)
        runtime.setConsumerCount(1)
        observer.setSourceActive(true)
        observer.setPlaybackActive(true)
        val source = directBuffer(ShortArray(2_048) { 8_000 })
        observer.onPcm16(source, 0, source.remaining(), 48_000, 2)

        assertEquals(
            SpectrumAvailability.AVAILABLE,
            runtime.processLatestForTest(33_333_333L)?.availability
        )
        val timedOut = runtime.processLatestForTest(300_000_000L)

        assertEquals(SpectrumAvailability.UNAVAILABLE, timedOut?.availability)
        assertEquals(SpectrumAvailability.UNAVAILABLE, published.last().availability)
        observer.close()
        runtime.close()
    }

    @Test
    fun `bounded handoff replaces stale ready windows instead of blocking`() {
        val handoff = LatestPcmWindowHandoff(
            fftSize = 8,
            maximumChannelCount = 2,
            slotCount = 2
        )
        val samples = ShortArray(16)

        repeat(5) {
            assertTrue(handoff.offer(7L, 0L, samples, 8, 48_000, 2))
        }

        assertTrue(handoff.droppedWindowCount() >= 3L)
        val latest = handoff.takeLatest(7L, 0L)
        assertNotNull(latest)
        handoff.release(checkNotNull(latest))
    }

    private fun directBuffer(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .also { buffer ->
                samples.forEach(buffer::putShort)
                buffer.flip()
            }

    private fun ByteBuffer.snapshot(): ByteArray = ByteArray(limit()).also { output ->
        for (index in output.indices) output[index] = get(index)
    }
}
