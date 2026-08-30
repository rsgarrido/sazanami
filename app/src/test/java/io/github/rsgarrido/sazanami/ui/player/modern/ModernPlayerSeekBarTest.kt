package io.github.rsgarrido.sazanami.ui.player.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernPlayerSeekBarTest {
    @Test
    fun waveformPreviewBars_sameSeedProducesStableBars() {
        val first = generateWaveformPreviewBars("42|music/track.flac|Example")
        val second = generateWaveformPreviewBars("42|music/track.flac|Example")

        assertEquals(first, second)
        assertEquals(48, first.size)
        assertTrue(first.all { amplitude -> amplitude in 0.24f..0.96f })
    }

    @Test
    fun waveformPreviewBars_differentSeedsProduceDifferentBars() {
        val first = generateWaveformPreviewBars("song-one")
        val second = generateWaveformPreviewBars("song-two")

        assertNotEquals(first, second)
    }

    @Test
    fun waveformPeaksBars_areStableWithExpectedCountAndRange() {
        val first = generateWaveformPeaksBars("42|music/track.flac|Example")
        val second = generateWaveformPeaksBars("42|music/track.flac|Example")

        assertEquals(first, second)
        assertEquals(42, first.size)
        assertTrue(first.all { amplitude -> amplitude in 0.12f..1f })
    }

    @Test
    fun waveformGlowBars_areStableWithExpectedCountAndRange() {
        val first = generateWaveformGlowBars("42|music/track.flac|Example")
        val second = generateWaveformGlowBars("42|music/track.flac|Example")

        assertEquals(first, second)
        assertEquals(72, first.size)
        assertTrue(first.all { amplitude -> amplitude in 0.2f..0.78f })
    }

    @Test
    fun waveformDensityControlsRendererBarCount() {
        ModernWaveformDensity.entries.forEach { density ->
            assertEquals(
                density.barCount,
                generateWaveformPreviewBars("density", density.barCount).size
            )
            assertEquals(
                density.barCount,
                generateWaveformPeaksBars("density", density.barCount).size
            )
            assertEquals(
                density.barCount,
                generateWaveformGlowBars("density", density.barCount).size
            )
            assertEquals(
                density.barCount,
                generateContinuousWaveformSamples("density", density.barCount).size
            )
            assertEquals(
                density.barCount,
                generateWaveLineSamples("density", density.barCount).size
            )
        }
    }

    @Test
    fun waveformSizeUsesOrderedMeaningfulTrackHeights() {
        assertTrue(
            ModernWaveformSize.COMPACT.trackHeightDp <
                    ModernWaveformSize.STANDARD.trackHeightDp
        )
        assertTrue(
            ModernWaveformSize.STANDARD.trackHeightDp <
                    ModernWaveformSize.TALL.trackHeightDp
        )
        assertTrue(
            ModernWaveformSize.TALL.trackHeightDp >=
                    ModernWaveformSize.STANDARD.trackHeightDp * 1.5f
        )
    }

    @Test
    fun segmentedFillFractions_fillsWholeAndPartialSegments() {
        assertEquals(
            listOf(1f, 1f, 0.5f, 0f),
            segmentedFillFractions(progress = 0.625f, segmentCount = 4)
        )
    }

    @Test
    fun segmentedFillFractions_clampsProgressAndHandlesEmptyCount() {
        assertEquals(listOf(0f, 0f), segmentedFillFractions(-1f, 2))
        assertEquals(listOf(1f, 1f), segmentedFillFractions(2f, 2))
        assertTrue(segmentedFillFractions(0.5f, 0).isEmpty())
    }

    @Test
    fun blendWaveformBars_keepsFallbackUntilRealDataIsUsable() {
        val fallback = listOf(0.2f, 0.4f)

        assertEquals(fallback, blendWaveformBars(fallback, null, 1f))
        assertEquals(fallback, blendWaveformBars(fallback, listOf(1f), 1f))
        val blended = blendWaveformBars(fallback, listOf(1f, 1f), 0.5f)
        assertEquals(0.6f, blended[0], 0.0001f)
        assertEquals(0.7f, blended[1], 0.0001f)
    }

    @Test
    fun timingLayoutReservesASeparateRowBelowSeekbar() {
        val layout = resolveModernSeekbarVerticalLayout(
            trackHeightPx = 48f,
            timingHeightPx = 18f
        )

        assertEquals(0f, layout.trackTop, 0f)
        assertEquals(48f, layout.trackBottom, 0f)
        assertEquals(48f, layout.timingTop, 0f)
        assertEquals(66f, layout.timingBottom, 0f)
        assertTrue(layout.timingTop >= layout.trackBottom)
    }

    @Test
    fun timingLabelsNeverOccupyWaveformVerticalBounds() {
        ModernSeekbarStyle.values().forEach { style ->
            val trackHeight = when (style) {
                ModernSeekbarStyle.WAVEFORM_PEAKS -> 36f
                ModernSeekbarStyle.WAVEFORM_PREVIEW,
                ModernSeekbarStyle.WAVEFORM_GLOW,
                ModernSeekbarStyle.CONTINUOUS_WAVEFORM,
                ModernSeekbarStyle.WAVE_LINE -> 32f
                else -> 48f
            }
            val layout = resolveModernSeekbarVerticalLayout(
                trackHeightPx = trackHeight,
                timingHeightPx = 18f
            )

            assertTrue(
                "${style.displayName} timing row overlaps its track",
                layout.timingTop >= layout.trackBottom
            )
        }
    }

    @Test
    fun unknownOrZeroDurationUsesSafeSliderAndDisplayValues() {
        assertEquals(
            ModernSeekbarValues(
                sliderPosition = 0,
                sliderDuration = 1,
                displayPosition = 0,
                displayDuration = 0
            ),
            resolveModernSeekbarValues(
                currentPosition = 12_000,
                duration = 0
            )
        )
        assertEquals(
            0,
            resolveModernSeekbarValues(
                currentPosition = -1,
                duration = -1
            ).displayDuration
        )
    }
}
