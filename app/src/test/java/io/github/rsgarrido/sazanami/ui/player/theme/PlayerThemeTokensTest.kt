package io.github.rsgarrido.sazanami.ui.player.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerThemeTokensTest {
    @Test
    fun lighten_blendsTowardWhiteAndPreservesAlpha() {
        val result = Color(0.2f, 0.4f, 0.6f, 0.8f).lighten(0.25f)

        assertColorComponents(
            expected = Color(0.4f, 0.55f, 0.7f, 0.8f),
            actual = result
        )
    }

    @Test
    fun darken_blendsTowardBlackAndPreservesAlpha() {
        val result = Color(0.2f, 0.4f, 0.6f, 0.8f).darken(0.25f)

        assertColorComponents(
            expected = Color(0.15f, 0.3f, 0.45f, 0.8f),
            actual = result
        )
    }

    @Test
    fun derivationAmounts_areClamped() {
        assertColorComponents(Color(0.2f, 0.4f, 0.6f), Color(0.2f, 0.4f, 0.6f).lighten(-1f))
        assertColorComponents(Color.White, Color(0.2f, 0.4f, 0.6f).lighten(Float.POSITIVE_INFINITY))
        assertColorComponents(Color.Black, Color(0.2f, 0.4f, 0.6f).darken(2f))
        assertColorComponents(Color(0.2f, 0.4f, 0.6f), Color(0.2f, 0.4f, 0.6f).darken(Float.NaN))
    }

    @Test
    fun withAlpha_clampsAlphaAndHandlesNaN() {
        val color = Color(0.2f, 0.4f, 0.6f, 0.8f)

        assertEquals(0f, color.withAlpha(-0.1f).alpha, COMPONENT_TOLERANCE)
        assertEquals(1f, color.withAlpha(Float.POSITIVE_INFINITY).alpha, COMPONENT_TOLERANCE)
        assertEquals(0f, color.withAlpha(Float.NaN).alpha, COMPONENT_TOLERANCE)
    }

    private fun assertColorComponents(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, COMPONENT_TOLERANCE)
        assertEquals(expected.green, actual.green, COMPONENT_TOLERANCE)
        assertEquals(expected.blue, actual.blue, COMPONENT_TOLERANCE)
        assertEquals(expected.alpha, actual.alpha, COMPONENT_TOLERANCE)
    }

    private companion object {
        const val COMPONENT_TOLERANCE = 0.0001f
    }
}
