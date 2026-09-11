package io.github.rsgarrido.sazanami.player.spectrum

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumAnalyzerTest {
    private val frameNanos = 33_333_333L

    @Test
    fun `silence produces zero available bands`() {
        val frame = SpectrumAnalyzer().analyzeMono(FloatArray(1_024), 48_000, frameNanos)

        assertEquals(SpectrumAvailability.AVAILABLE, frame.availability)
        assertEquals(28, frame.bandCount)
        assertTrue(frame.values().all { it == 0f })
    }

    @Test
    fun `low mid and high tones energize distinct canonical regions`() {
        val low = analyzeTone(120f)
        val mid = analyzeTone(1_000f)
        val high = analyzeTone(8_000f)

        assertTrue(low.peakIndex() in 0..7)
        assertTrue(mid.peakIndex() in 10..19)
        assertTrue(high.peakIndex() in 21..27)
        assertTrue(low.peakIndex() < mid.peakIndex())
        assertTrue(mid.peakIndex() < high.peakIndex())
    }

    @Test
    fun `multiple tones energize separated regions simultaneously`() {
        val analyzer = SpectrumAnalyzer()
        val samples = FloatArray(1_024) { index ->
            val time = index / 48_000.0
            (0.42 * sin(2.0 * PI * 140.0 * time) +
                0.42 * sin(2.0 * PI * 6_000.0 * time)).toFloat()
        }

        val frame = analyzer.analyzeMono(samples, 48_000, frameNanos)

        assertTrue(frame.values().slice(0..7).max() > 0.35f)
        assertTrue(frame.values().slice(20..27).max() > 0.35f)
    }

    @Test
    fun `attack rises quickly and release decays without sticking`() {
        val analyzer = SpectrumAnalyzer()
        val tone = sine(1_000f, 48_000)
        val attack = analyzer.analyzeMono(tone, 48_000, frameNanos)
        val firstRelease = analyzer.analyzeMono(FloatArray(1_024), 48_000, frameNanos * 2)
        var released = firstRelease
        for (frame in 3L..80L) {
            released = analyzer.analyzeMono(FloatArray(1_024), 48_000, frameNanos * frame)
        }

        assertTrue(attack.peakValue() > 0.5f)
        assertTrue(firstRelease.peakValue() in 0f..attack.peakValue())
        assertTrue(firstRelease.peakValue() > 0.1f)
        assertTrue(released.peakValue() < 0.001f)
    }

    @Test
    fun `moderate tones retain headroom below stronger tones`() {
        val moderate = analyzeSteadyTone(amplitude = 0.35f)
        val strong = analyzeSteadyTone(amplitude = 0.80f)

        assertTrue(moderate in 0.2f..0.96f)
        assertTrue(strong > moderate + 0.03f)
        assertTrue(strong <= 1f)
    }

    @Test
    fun `equivalent tone maps sensibly at common sample rates`() {
        val at44100 = analyzeTone(1_000f, 44_100)
        val at48000 = analyzeTone(1_000f, 48_000)

        assertTrue(kotlin.math.abs(at44100.peakIndex() - at48000.peakIndex()) <= 1)
    }

    @Test
    fun `normalization is finite and bounded for hostile float input`() {
        val samples = FloatArray(1_024) { index ->
            when (index % 4) {
                0 -> Float.NaN
                1 -> Float.POSITIVE_INFINITY
                2 -> Float.NEGATIVE_INFINITY
                else -> 100f
            }
        }
        val frame = SpectrumAnalyzer().analyzeMono(samples, 48_000, frameNanos)

        assertTrue(frame.values().all { it.isFinite() && it in 0f..1f })
    }

    @Test
    fun `interleaved channels are averaged to canonical mono`() {
        val analyzer = SpectrumAnalyzer()
        val samples = ShortArray(1_024 * 2)
        for (frame in 0 until 1_024) {
            val sample = (sin(2.0 * PI * 1_000.0 * frame / 48_000.0) * 20_000).toInt().toShort()
            samples[frame * 2] = sample
            samples[frame * 2 + 1] = (-sample).toShort()
        }

        val result = analyzer.analyzeInterleavedPcm16(
            samples = samples,
            frameCount = 1_024,
            sampleRateHz = 48_000,
            channelCount = 2,
            timestampNanos = frameNanos
        )

        assertTrue(result.peakValue() < 0.001f)
        assertFalse(result.hasStereoBands)
    }

    @Test
    fun `reset clears stale energy and marks output unavailable`() {
        val analyzer = SpectrumAnalyzer()
        analyzer.analyzeMono(sine(400f, 48_000), 48_000, frameNanos)

        val reset = analyzer.reset(frameNanos * 2)

        assertEquals(SpectrumAvailability.UNAVAILABLE, reset.availability)
        assertEquals(0, reset.sampleRateHz)
        assertTrue(reset.values().all { it == 0f })
    }

    @Test
    fun `published primitive storage cannot be mutated through a consumer copy`() {
        val frame = analyzeTone(1_000f)
        val copy = frame.values()
        val originalPeak = frame.peakValue()

        copy.fill(0f)

        assertEquals(originalPeak, frame.peakValue(), 0f)
    }

    private fun analyzeTone(frequencyHz: Float, sampleRateHz: Int = 48_000): SpectrumFrame =
        SpectrumAnalyzer().analyzeMono(
            samples = sine(frequencyHz, sampleRateHz),
            sampleRateHz = sampleRateHz,
            timestampNanos = frameNanos
        )

    private fun analyzeSteadyTone(amplitude: Float): Float {
        val analyzer = SpectrumAnalyzer()
        val samples = sine(1_000f, 48_000, amplitude)
        var frame = analyzer.analyzeMono(samples, 48_000, frameNanos)
        repeat(29) { index ->
            frame = analyzer.analyzeMono(
                samples,
                48_000,
                frameNanos * (index + 2L)
            )
        }
        return frame.peakValue()
    }

    private fun sine(
        frequencyHz: Float,
        sampleRateHz: Int,
        amplitude: Float = 0.8f
    ): FloatArray =
        FloatArray(1_024) { index ->
            (amplitude * sin(2.0 * PI * frequencyHz * index / sampleRateHz)).toFloat()
        }

    private fun SpectrumFrame.values() = FloatArray(bandCount).also(::copyMonoBandsInto)

    private fun SpectrumFrame.peakIndex(): Int {
        val values = values()
        return values.indices.maxBy { values[it] }
    }

    private fun SpectrumFrame.peakValue(): Float = values().max()
}
