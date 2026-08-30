package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedPlayerWaveformStateTest {
    @Test
    fun retroWaveformThemes_requestWaveformLoading() {
        assertTrue(
            shouldLoadExpandedPlayerWaveform(
                PlayerTheme.POCKET_FLIP,
                ModernSeekbarStyle.CLASSIC_BAR
            )
        )
        assertTrue(
            shouldLoadExpandedPlayerWaveform(
                PlayerTheme.RETRO_RACK,
                ModernSeekbarStyle.CLASSIC_BAR
            )
        )
    }

    @Test
    fun unrelatedRetroThemes_doNotRequestWaveformLoading() {
        assertFalse(
            shouldLoadExpandedPlayerWaveform(
                PlayerTheme.CLASSIC_WHEEL,
                ModernSeekbarStyle.WAVEFORM_PREVIEW
            )
        )
        assertFalse(
            shouldLoadExpandedPlayerWaveform(
                PlayerTheme.POCKET_CASSETTE,
                ModernSeekbarStyle.WAVEFORM_PREVIEW
            )
        )
    }

    @Test
    fun modernTheme_onlyRequestsWaveformForWaveformSeekbars() {
        assertTrue(
            shouldLoadExpandedPlayerWaveform(
                PlayerTheme.DEFAULT,
                ModernSeekbarStyle.WAVEFORM_GLOW
            )
        )
        assertFalse(
            shouldLoadExpandedPlayerWaveform(
                PlayerTheme.DEFAULT,
                ModernSeekbarStyle.CLASSIC_BAR
            )
        )
    }
}
