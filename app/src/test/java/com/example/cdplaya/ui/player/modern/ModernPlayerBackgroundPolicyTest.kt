package com.example.cdplaya.ui.player.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernPlayerBackgroundPolicyTest {
    @Test
    fun android10UsesScrimWithoutPlatformBlur() {
        val policy = modernBackgroundPolicy(sdkInt = 29)

        assertFalse(policy.usePlatformBlur)
        assertTrue(policy.legacyScrimAlpha > 0f)
    }

    @Test
    fun android12AndNewerKeepPlatformBlurWithoutExtraScrim() {
        listOf(31, 36).forEach { sdk ->
            val policy = modernBackgroundPolicy(sdk)

            assertTrue(policy.usePlatformBlur)
            assertEquals(0f, policy.legacyScrimAlpha)
        }
    }

    @Test
    fun detailedArtworkAlwaysUsesLessBlurThanBlurredArtwork() {
        ModernBlurStrength.entries.forEach { strength ->
            val blurred = modernBackgroundPolicy(
                sdkInt = 36,
                backgroundStyle = ModernBackgroundStyle.BLURRED_ARTWORK,
                blurStrength = strength
            )
            val detailed = modernBackgroundPolicy(
                sdkInt = 36,
                backgroundStyle = ModernBackgroundStyle.DETAILED_ARTWORK,
                blurStrength = strength
            )

            assertTrue(detailed.blurRadiusDp < blurred.blurRadiusDp)
        }
    }

    @Test
    fun nonArtworkBackgroundsNeverRequestBlurOrLegacyFallbackScrim() {
        listOf(
            ModernBackgroundStyle.ALBUM_GRADIENT,
            ModernBackgroundStyle.SOLID_COLOR,
            ModernBackgroundStyle.PURE_BLACK
        ).forEach { style ->
            val policy = modernBackgroundPolicy(29, style, ModernBlurStrength.HIGH)

            assertFalse(policy.usePlatformBlur)
            assertEquals(0, policy.blurRadiusDp)
            assertEquals(0f, policy.legacyScrimAlpha)
        }
    }
}
