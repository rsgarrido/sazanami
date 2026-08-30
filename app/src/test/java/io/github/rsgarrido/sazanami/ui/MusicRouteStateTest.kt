package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.mutableStateOf
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRouteStateTest {
    @Test
    fun primaryDestinationsAreMutuallyExclusive() {
        val state = MusicOverlayState(
            playerMorphState = playerMorphState(PlayerPresentation.Collapsed),
            primaryDestination = mutableStateOf(null),
            transientDestination = mutableStateOf(null)
        )

        state.isSettingsScreenVisible.value = true
        state.isDiagnosticsScreenVisible.value = true
        state.isEqualizerScreenVisible.value = true
        state.isListeningHistoryReconciliationVisible.value = true
        state.isStatisticsScreenVisible.value = true

        assertFalse(state.isSettingsScreenVisible.value)
        assertFalse(state.isDiagnosticsScreenVisible.value)
        assertFalse(state.isEqualizerScreenVisible.value)
        assertFalse(state.isListeningHistoryReconciliationVisible.value)
        assertTrue(state.isStatisticsScreenVisible.value)
        assertFalse(state.isFolderScreenVisible.value)
    }

    @Test
    fun transientOverlaysAreMutuallyExclusiveButDoNotCollapsePlayer() {
        val state = MusicOverlayState(
            playerMorphState = playerMorphState(PlayerPresentation.Expanded),
            primaryDestination = mutableStateOf(null),
            transientDestination = mutableStateOf(null)
        )

        state.isExpandedUpNextSheetVisible.value = true
        state.isSleepTimerDialogVisible.value = true

        assertFalse(state.isExpandedUpNextSheetVisible.value)
        assertTrue(state.isSleepTimerDialogVisible.value)
        assertTrue(state.playerMorphState.isExpandedOrTransitioning)
    }

    private fun playerMorphState(presentation: PlayerPresentation) =
        PlayerMorphState(
            initialPresentation = presentation,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
}
