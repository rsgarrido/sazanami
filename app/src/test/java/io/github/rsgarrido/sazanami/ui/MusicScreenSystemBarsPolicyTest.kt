package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.data.PlayerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicScreenSystemBarsPolicyTest {
    @Test fun `retro rack uses immersive bars only while expanded player owns presentation`() {
        assertTrue(shouldUseImmersivePlayerSystemBars(PlayerTheme.RETRO_RACK, true, false))
        assertFalse(shouldUseImmersivePlayerSystemBars(PlayerTheme.RETRO_RACK, false, false))
        assertFalse(shouldUseImmersivePlayerSystemBars(PlayerTheme.RETRO_RACK, true, true))
    }

    @Test fun `default and classic wheel retain their existing system bar policies`() {
        assertFalse(shouldUseImmersivePlayerSystemBars(PlayerTheme.DEFAULT, true, false))
        assertTrue(shouldUseImmersivePlayerSystemBars(PlayerTheme.CLASSIC_WHEEL, true, false))
        assertFalse(shouldUseImmersivePlayerSystemBars(PlayerTheme.CLASSIC_WHEEL, false, false))
    }
}
