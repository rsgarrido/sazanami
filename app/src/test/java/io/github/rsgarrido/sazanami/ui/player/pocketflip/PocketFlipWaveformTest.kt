package io.github.rsgarrido.sazanami.ui.player.pocketflip

import io.github.rsgarrido.sazanami.ui.player.buildRetroMeterLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketFlipWaveformTest {
    @Test
    fun unavailableWaveform_keepsDecorativeFallback() {
        assertNull(buildLevels(null))
        assertNull(buildLevels(emptyList()))
    }

    @Test
    fun waveformEnergy_drivesTwoClampedMeterLines() {
        val levels = buildLevels(listOf(Float.NaN, -1f, 0.25f, 2f))

        requireNotNull(levels)
        assertEquals(POCKET_FLIP_METER_LINE_COUNT, levels.size)
        assertTrue(levels.all { level -> level.isFinite() && level in 0f..1f })
    }

    @Test
    fun silentWaveform_createsEmptyMeterLines() {
        val levels = buildLevels(List(64) { 0f })

        requireNotNull(levels)
        assertTrue(levels.all { level -> level <= 0.01f })
    }

    private fun buildLevels(amplitudes: List<Float>?) =
        buildRetroMeterLevels(
            amplitudes = amplitudes,
            currentPositionMs = 45_000L,
            durationMs = 180_000L,
            columnCount = POCKET_FLIP_METER_LINE_COUNT,
            animationPhase = 0.25f,
            isPlaying = true,
            songSeed = 42L
        )
}
