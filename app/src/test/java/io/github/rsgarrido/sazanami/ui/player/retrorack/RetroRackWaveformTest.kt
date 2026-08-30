package io.github.rsgarrido.sazanami.ui.player.retrorack

import io.github.rsgarrido.sazanami.ui.player.buildRetroMeterLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroRackWaveformTest {
    @Test
    fun unavailableWaveform_keepsDecorativeFallback() {
        assertNull(buildLevels(null))
        assertNull(buildLevels(emptyList()))
    }

    @Test
    fun waveformEnergy_drivesClampedSpectrumBands() {
        val levels = buildLevels(listOf(Float.POSITIVE_INFINITY, -0.5f, 0.5f, 1.5f))

        requireNotNull(levels)
        assertEquals(RETRO_RACK_VISUALIZER_COLUMN_COUNT, levels.size)
        assertTrue(levels.all { level -> level.isFinite() && level in 0f..1f })
    }

    @Test
    fun silentWaveform_createsLowSpectrumColumns() {
        val levels = buildLevels(List(64) { 0f })

        requireNotNull(levels)
        assertTrue(levels.all { level -> level <= 0.01f })
    }

    private fun buildLevels(amplitudes: List<Float>?) =
        buildRetroMeterLevels(
            amplitudes = amplitudes,
            currentPositionMs = 45_000L,
            durationMs = 180_000L,
            columnCount = RETRO_RACK_VISUALIZER_COLUMN_COUNT,
            animationPhase = 0.25f,
            isPlaying = true,
            songSeed = 42L
        )
}
