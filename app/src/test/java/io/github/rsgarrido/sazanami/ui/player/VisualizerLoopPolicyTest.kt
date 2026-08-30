package io.github.rsgarrido.sazanami.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerLoopPolicyTest {
    private val active = VisualizerLoopConditions(
        themeSelected = true,
        expandedPlayerVisible = true,
        lifecycleStarted = true,
        coveredByOverlay = false,
        playbackActive = true,
        effectivelySilent = false
    )

    @Test
    fun onlyFullyActiveVisualizerRuns() {
        assertTrue(active.shouldRun())
    }

    @Test
    fun themeSwitchDisposesOldLoop() {
        assertFalse(active.copy(themeSelected = false).shouldRun())
    }

    @Test
    fun collapseStopsExpandedLoop() {
        assertFalse(active.copy(expandedPlayerVisible = false).shouldRun())
    }

    @Test
    fun activityStopSuspendsLoop() {
        assertFalse(active.copy(lifecycleStarted = false).shouldRun())
    }

    @Test
    fun overlayPauseAndSilenceGateWork() {
        assertFalse(active.copy(coveredByOverlay = true).shouldRun())
        assertFalse(active.copy(playbackActive = false).shouldRun())
        assertFalse(active.copy(effectivelySilent = true).shouldRun())
    }
}
