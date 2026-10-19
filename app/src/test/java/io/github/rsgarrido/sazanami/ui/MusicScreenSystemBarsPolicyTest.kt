package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.data.PlayerTheme
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.rsgarrido.sazanami.ui.player.SystemBarPresentation
import io.github.rsgarrido.sazanami.ui.player.resolvePlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.player.applySystemBarPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class MusicScreenSystemBarsPolicyTest {
    @Test fun `resolved existing players retain system bar policy at every presentation boundary`() {
        PlayerTheme.entries.forEach { theme ->
            val identity = resolvePlayerPresentation(theme, theme.defaultTokens()).identity
            listOf(false, true).forEach { expanded ->
                listOf(false, true).forEach { lyrics ->
                    assertEquals(shouldUseImmersivePlayerSystemBars(theme, expanded, lyrics),
                        shouldUseImmersivePlayerSystemBars(identity, expanded, lyrics))
                    listOf(false, true).forEach { hasSong ->
                        assertEquals(playerSystemBarSurface(theme, expanded, hasSong, lyrics),
                            playerSystemBarSurface(identity, expanded, hasSong, lyrics))
                    }
                }
            }
        }
    }

    @Test fun `dialog preserves hidden bars and transient swipe behavior`() {
        val controller = mock(WindowInsetsControllerCompat::class.java)
        val presentation = SystemBarPresentation(false, false,
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, false, false)
        applySystemBarPresentation(controller, presentation)
        verify(controller).hide(WindowInsetsCompat.Type.statusBars())
        verify(controller).hide(WindowInsetsCompat.Type.navigationBars())
        verify(controller, never()).show(WindowInsetsCompat.Type.statusBars())
        verify(controller, never()).show(WindowInsetsCompat.Type.navigationBars())
        verify(controller).systemBarsBehavior = presentation.behavior
    }

    @Test fun `dialog preserves visible and independently hidden bars`() {
        val visible = mock(WindowInsetsControllerCompat::class.java)
        applySystemBarPresentation(visible, SystemBarPresentation(true, true, 1, true, true))
        verify(visible).show(WindowInsetsCompat.Type.statusBars())
        verify(visible).show(WindowInsetsCompat.Type.navigationBars())
        verify(visible, never()).hide(WindowInsetsCompat.Type.statusBars())
        verify(visible, never()).hide(WindowInsetsCompat.Type.navigationBars())
        val mixed = mock(WindowInsetsControllerCompat::class.java)
        applySystemBarPresentation(mixed, SystemBarPresentation(true, false, 1, false, false))
        verify(mixed).show(WindowInsetsCompat.Type.statusBars())
        verify(mixed).hide(WindowInsetsCompat.Type.navigationBars())
    }

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
