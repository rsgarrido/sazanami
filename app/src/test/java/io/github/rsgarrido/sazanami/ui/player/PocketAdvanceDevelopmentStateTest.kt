package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketAdvanceDevelopmentStateTest {
    @Test
    fun unavailableSelectorCannotNominateEvenWithRestoredTrueState() {
        val restored = mutableStateOf(true)
        val state = PocketAdvanceDevelopmentState(isAvailable = false, requested = restored)
        assertFalse(state.enabled)
        state.setEnabled(true)
        assertFalse(state.enabled)
    }

    @Test
    fun availableSelectorTogglesOnlyItsActivitySavedNomination() {
        val saved = mutableStateOf(false)
        val state = PocketAdvanceDevelopmentState(isAvailable = true, requested = saved)
        assertFalse(state.enabled)
        state.setEnabled(true)
        assertTrue(saved.value)
        assertTrue(state.enabled)
        val restoredState = PocketAdvanceDevelopmentState(isAvailable = true, requested = saved)
        assertTrue(restoredState.enabled)
        restoredState.setEnabled(false)
        assertFalse(saved.value)
        assertFalse(state.enabled)
    }
}
