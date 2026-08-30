package com.example.cdplaya.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.theme.defaultTokens
import com.example.cdplaya.ui.theme.SazanamiAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellThemeAccentTest {
    @Test
    fun everyPlayerThemeResolvesAnOpaqueShellAccent() {
        PlayerTheme.entries.forEach { playerTheme ->
            val accent = resolveAppShellAccent(
                playerTheme = playerTheme,
                tokens = playerTheme.defaultTokens()
            )

            assertNotEquals(Color.Unspecified, accent)
            assertEquals(1f, accent.alpha, 0f)
        }
    }

    @Test
    fun defaultThemeKeepsCurrentSazanamiAccent() {
        val unrelatedTokens = tokens(accent = Color.Cyan)

        assertEquals(
            SazanamiAccent,
            resolveAppShellAccent(PlayerTheme.DEFAULT, unrelatedTokens)
        )
    }

    @Test
    fun tokenOverrideChangesRetroShellAccent() {
        val customAccent = Color(0xFF62E6FF)

        assertEquals(
            customAccent,
            resolveAppShellAccent(
                playerTheme = PlayerTheme.POCKET_FLIP,
                tokens = tokens(accent = customAccent)
            )
        )
    }

    @Test
    fun cassetteUsesItsActiveControlAccent() {
        val panelAccent = Color(0xFF5B7EA0)
        val activeAccent = Color(0xFFFF9D63)

        assertEquals(
            activeAccent,
            resolveAppShellAccent(
                playerTheme = PlayerTheme.POCKET_CASSETTE,
                tokens = tokens(
                    accent = panelAccent,
                    secondaryAccent = activeAccent
                )
            )
        )
    }

    @Test
    fun missingTokensFallBackToCurrentAccent() {
        PlayerTheme.entries.forEach { playerTheme ->
            assertEquals(
                SazanamiAccent,
                resolveAppShellAccent(playerTheme, tokens = null)
            )
        }
    }

    @Test
    fun darkCustomAccentIsLiftedForShellReadability() {
        val darkAccent = Color(0xFF101820)
        val resolved = resolveAppShellAccent(
            playerTheme = PlayerTheme.RETRO_RACK,
            tokens = tokens(accent = darkAccent)
        )

        assertTrue(resolved.luminance() > darkAccent.luminance())
    }

    private fun tokens(
        accent: Color,
        secondaryAccent: Color? = null
    ) = PlayerThemeTokens(
        shellColor = Color.DarkGray,
        accentColor = accent,
        displayBackgroundColor = Color.Black,
        displayTextColor = Color.White,
        secondaryAccentColor = secondaryAccent
    )
}
