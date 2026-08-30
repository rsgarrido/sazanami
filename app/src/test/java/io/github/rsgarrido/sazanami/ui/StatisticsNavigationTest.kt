package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.mutableStateOf
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsNavigationTest {
    @Test
    fun statisticsIsPrimaryAndDoesNotChangeUnderlyingShellDestination() {
        val shellDestination = mutableStateOf(MainDestination.LIBRARY)
        val overlayState = MusicOverlayState(
            playerMorphState = PlayerMorphState(
                initialPresentation = PlayerPresentation.Collapsed,
                coroutineScope = CoroutineScope(Dispatchers.Unconfined)
            ),
            primaryDestination = mutableStateOf(null),
            transientDestination = mutableStateOf(null)
        )

        overlayState.isStatisticsScreenVisible.value = true
        assertTrue(overlayState.isStatisticsScreenVisible.value)
        assertFalse(overlayState.isSettingsScreenVisible.value)
        assertEquals(MainDestination.LIBRARY, shellDestination.value)

        overlayState.isStatisticsScreenVisible.value = false
        assertFalse(overlayState.isStatisticsScreenVisible.value)
        assertEquals(MainDestination.LIBRARY, shellDestination.value)
    }
}
