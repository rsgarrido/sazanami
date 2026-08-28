package com.example.cdplaya.ui.player.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernColorPickerTest {
    @Test
    fun hsvRoundTripPreservesOpaqueRgbColor() {
        val original = 0xFF2E7D9AL
        val roundTrip = modernHsvToArgb(modernArgbToHsv(original))

        assertChannelNear(original, roundTrip, shift = 16)
        assertChannelNear(original, roundTrip, shift = 8)
        assertChannelNear(original, roundTrip, shift = 0)
        assertEquals(0xFFL, roundTrip ushr 24)
    }

    @Test
    fun invalidStoredColorsFallBackAndValidColorsBecomeOpaque() {
        assertEquals(DEFAULT_MODERN_SOLID_COLOR_ARGB, sanitizeModernSolidColorArgb(-1L))
        assertEquals(0xFF123456L, sanitizeModernSolidColorArgb(0x00123456L))
        assertTrue(ModernSolidColorSwatches.all { color -> color ushr 24 == 0xFFL })
    }

    private fun assertChannelNear(expected: Long, actual: Long, shift: Int) {
        val expectedChannel = (expected ushr shift and 0xFF).toInt()
        val actualChannel = (actual ushr shift and 0xFF).toInt()
        assertTrue(kotlin.math.abs(expectedChannel - actualChannel) <= 1)
    }
}
