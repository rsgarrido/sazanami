package io.github.rsgarrido.sazanami.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroMeterLevelsTest {
    @Test
    fun levelsUseOnlyEnergyNearCurrentPlaybackPosition() {
        val first = MutableList(100) { 0f }
        val second = MutableList(100) { 1f }
        for (index in 47..52) {
            first[index] = 0.6f
            second[index] = 0.6f
        }

        assertEquals(buildLevels(first), buildLevels(second))
    }

    @Test
    fun levelsDoNotTraceWaveformLeftToRight() {
        val amplitudes = List(18) { index -> index / 17f }
        val levels = buildLevels(amplitudes, columnCount = 18)

        requireNotNull(levels)
        assertNotEquals(amplitudes, levels)
        assertTrue(levels.zipWithNext().any { (first, second) -> first > second })
    }

    @Test
    fun outputChangesWithPlaybackPosition() {
        val amplitudes = List(64) { index -> index / 63f }

        val early = buildLevels(amplitudes, positionMs = 10_000L, phase = 0.3f)
        val late = buildLevels(amplitudes, positionMs = 90_000L, phase = 0.3f)

        assertNotEquals(early, late)
    }

    @Test
    fun playingOutputChangesWithAnimationPhase() {
        val amplitudes = List(64) { 0.65f }

        val first = buildLevels(amplitudes, phase = 0.1f, isPlaying = true)
        val second = buildLevels(amplitudes, phase = 0.6f, isPlaying = true)

        assertNotEquals(first, second)
    }

    @Test
    fun nearZeroEnergyIsSilencedByNoiseGate() {
        val levels = buildLevels(List(64) { RETRO_METER_SILENCE_GATE * 0.75f })

        requireNotNull(levels)
        assertTrue(levels.all { level -> level <= 0.01f })
    }

    @Test
    fun animationPhaseHasNoEffectDuringSilence() {
        val amplitudes = List(64) { 0f }

        val first = buildLevels(amplitudes, phase = 0.1f, isPlaying = true)
        val second = buildLevels(amplitudes, phase = 0.8f, isPlaying = true)

        assertEquals(first, second)
    }

    @Test
    fun localSilenceCanGateVisualizerUpdates() {
        val amplitudes = listOf(0.8f, 0.8f, 0f, 0f, 0f, 0.7f)

        assertTrue(isRetroMeterEffectivelySilent(amplitudes, 50_000L, 100_000L))
        assertTrue(!isRetroMeterEffectivelySilent(amplitudes, 0L, 100_000L))
    }

    @Test
    fun meterReleasesImmediatelyWhenCurrentEnergyIsSilent() {
        val amplitudes = listOf(1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f)
        val levels = buildLevels(
            amplitudes = amplitudes,
            positionMs = 50_000L,
            phase = 0.25f,
            isPlaying = true
        )

        requireNotNull(levels)
        assertTrue(levels.all { level -> level <= 0.01f })
    }

    @Test
    fun higherEnergyProducesStrongerMeterLevels() {
        val quiet = buildLevels(List(64) { 0.12f }, phase = 0.25f)
        val loud = buildLevels(List(64) { 0.8f }, phase = 0.25f)

        requireNotNull(quiet)
        requireNotNull(loud)
        assertTrue(loud.average() > quiet.average())
    }

    @Test
    fun pausedOutputIsStableAcrossAnimationPhases() {
        val amplitudes = List(64) { 0.65f }

        val first = buildLevels(amplitudes, phase = 0.1f, isPlaying = false)
        val second = buildLevels(amplitudes, phase = 0.8f, isPlaying = false)

        assertEquals(first, second)
    }

    @Test
    fun invalidInputsReturnFallbackSignal() {
        assertNull(buildLevels(emptyList()))
        assertNull(buildLevels(listOf(0.5f), durationMs = 0L))
        assertNull(buildLevels(listOf(0.5f), columnCount = 0))
    }

    @Test
    fun outputIsSanitizedAndClamped() {
        val levels = buildLevels(listOf(Float.NaN, -1f, 0.5f, Float.POSITIVE_INFINITY, 2f))

        requireNotNull(levels)
        assertTrue(levels.all { level -> level.isFinite() && level in 0f..1f })
    }

    @Test
    fun callerOwnedMeterBufferIsReusedAcrossUpdates() {
        val output = FloatArray(2)
        val firstUpdated = fillRetroMeterLevels(
            output = output,
            amplitudes = List(32) { 0.5f },
            currentPositionMs = 10_000L,
            durationMs = 100_000L,
            animationPhase = 0.1f,
            isPlaying = true,
            songSeed = 7L
        )
        val firstValues = output.copyOf()
        val secondUpdated = fillRetroMeterLevels(
            output = output,
            amplitudes = List(32) { 0.8f },
            currentPositionMs = 10_000L,
            durationMs = 100_000L,
            animationPhase = 0.2f,
            isPlaying = true,
            songSeed = 7L
        )

        assertTrue(firstUpdated && secondUpdated)
        assertNotEquals(firstValues.toList(), output.toList())
    }

    private fun buildLevels(
        amplitudes: List<Float>,
        positionMs: Long = 50_000L,
        durationMs: Long = 100_000L,
        columnCount: Int = 18,
        phase: Float = 0f,
        isPlaying: Boolean = true,
        songSeed: Long = 42L
    ) = buildRetroMeterLevels(
        amplitudes = amplitudes,
        currentPositionMs = positionMs,
        durationMs = durationMs,
        columnCount = columnCount,
        animationPhase = phase,
        isPlaying = isPlaying,
        songSeed = songSeed
    )
}
