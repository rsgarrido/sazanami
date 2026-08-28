package com.example.cdplaya.ui.player.modern

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernPlayerRefinementPolicyTest {
    @Test
    fun artworkFitKeepsStoredValuesButMapsToDistinctVisualPolicies() {
        val fill = modernArtworkFitLayout(ModernArtworkFit.CROP)
        val contained = modernArtworkFitLayout(ModernArtworkFit.SHOW_FULL)

        assertEquals("crop", ModernArtworkFit.CROP.storageValue)
        assertEquals("show_full", ModernArtworkFit.SHOW_FULL.storageValue)
        assertEquals(ContentScale.Crop, fill.contentScale)
        assertEquals(0f, fill.frameInsetFraction)
        assertEquals(ContentScale.Fit, contained.contentScale)
        assertTrue(contained.frameInsetFraction > 0f)
    }

    @Test
    fun containedArtworkFrameIsVisibleResponsiveAndBounded() {
        assertEquals(0f, modernArtworkFrameInsetDp(ModernArtworkFit.CROP, 300f))
        assertEquals(0f, modernArtworkFrameInsetDp(ModernArtworkFit.SHOW_FULL, 300f, 0f))

        val compact = modernArtworkFrameInsetDp(ModernArtworkFit.SHOW_FULL, 100f)
        val large = modernArtworkFrameInsetDp(ModernArtworkFit.SHOW_FULL, 400f)
        assertTrue(compact > 0f)
        assertTrue(large > compact)
        assertTrue(large <= 16f)
    }

    @Test
    fun previewControlLayoutFitsEverySizeAtNormalWidth() {
        ModernControlSize.entries.forEach { size ->
            val layout = resolveModernControlRowLayout(size, availableWidthDp = 360f)

            assertEquals(1f, layout.scale)
            assertTrue(layout.requiredWidthDp <= 360f)
            assertTrue(layout.primarySizeDp > layout.secondarySizeDp)
        }
    }

    @Test
    fun largePreviewControlsScaleProportionallyForNarrowWidth() {
        val layout = resolveModernControlRowLayout(
            size = ModernControlSize.LARGE,
            availableWidthDp = 276f
        )

        assertTrue(layout.scale in 0f..1f)
        assertTrue(layout.requiredWidthDp <= 276.001f)
        assertTrue(layout.primarySizeDp > layout.secondarySizeDp)
        assertTrue(layout.primarySizeDp >= 0f)
        assertTrue(layout.secondarySizeDp >= 0f)
    }

    @Test
    fun constraintSizedArtworkDoesNotFreezeARequestAtMiniResolution() {
        val automatic = modernArtworkRequestPolicy(expandedTargetSizePx = null)
        val expanded = modernArtworkRequestPolicy(
            expandedTargetSizePx = 1080,
            artworkIdentity = "content://album-art/42"
        )

        assertNull(automatic.targetSizePx)
        assertFalse(automatic.exactSize)
        assertNull(automatic.sourceMemoryCachePlaceholderKey)
        assertNull(automatic.expandedMemoryCacheKey)
        assertEquals(1080, expanded.targetSizePx)
        assertTrue(expanded.targetSizePx != 52)
        assertTrue(expanded.exactSize)
        assertEquals(
            "content://album-art/42",
            expanded.sourceMemoryCachePlaceholderKey
        )
        assertEquals(
            modernExpandedArtworkMemoryCacheKey("content://album-art/42", 1080),
            expanded.expandedMemoryCacheKey
        )
    }

    @Test
    fun invalidExpandedArtworkTargetFallsBackToConstraintSizing() {
        listOf(0, -1).forEach { target ->
            val policy = modernArtworkRequestPolicy(target)
            assertNull(policy.targetSizePx)
            assertFalse(policy.exactSize)
            assertNull(policy.sourceMemoryCachePlaceholderKey)
            assertNull(policy.expandedMemoryCacheKey)
        }
    }

    @Test
    fun expandedArtworkPlaceholderIdentityIsStableAndOptional() {
        val first = modernArtworkRequestPolicy(1080, "content://album-art/42")
        val second = modernArtworkRequestPolicy(1080, "content://album-art/42")
        val missing = modernArtworkRequestPolicy(1080, null)

        assertEquals(first, second)
        assertNull(missing.sourceMemoryCachePlaceholderKey)
        assertNull(missing.expandedMemoryCacheKey)
        assertTrue(missing.exactSize)
    }

    @Test
    fun paletteAnimationFramesCannotChangeArtworkRequestIdentity() {
        val paletteFrames = listOf(Color.Red, Color.Magenta, Color.Blue)
        val requestPolicies = paletteFrames.map {
            modernArtworkRequestPolicy(1080, "content://album-art/42")
        }

        assertEquals(1, requestPolicies.distinct().size)
        assertEquals(
            requestPolicies.first().expandedMemoryCacheKey,
            requestPolicies.last().expandedMemoryCacheKey
        )
    }

    @Test
    fun artworkTransitionViewportAllowsOverflowWhileCardsKeepShapeClipping() {
        val policy = modernArtworkTransitionClippingPolicy()

        assertFalse(policy.clipTransitionViewportToRestingBounds)
        assertTrue(policy.clipArtworkCardToShape)
    }

    @Test
    fun artworkTranslationCanLeaveRestingBoundsSymmetricallyAndReturnToCenter() {
        val left = modernArtworkTranslationPx(-0.6f, 300f)
        val right = modernArtworkTranslationPx(0.6f, 300f)

        assertEquals(-180f, left)
        assertEquals(180f, right)
        assertEquals(-left, right)
        assertEquals(0f, modernArtworkTranslationPx(0f, 300f))
    }

    @Test
    fun morphCardShapeMovesFromMiniRadiusToConfiguredArtworkRadius() {
        val appearance = ModernArtworkAppearance(shape = ModernArtworkShape.EXTRA_ROUNDED)

        assertEquals(10f, modernArtworkMorphCornerRadiusDp(appearance, 0f))
        assertEquals(
            appearance.shape.cornerRadiusDp.toFloat(),
            modernArtworkMorphCornerRadiusDp(appearance, 1f)
        )
    }
}
