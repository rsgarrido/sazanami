package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlayerLayoutTest {
    @Test
    fun phonePortraitKeepsTheEstablishedVerticalArtworkBudgets() {
        val ordinary = resolveDefaultPlayerLayout(412.dp, 850.dp)
        assertEquals(DefaultPlayerArrangement.Vertical, ordinary.arrangement)
        assertDp(357.dp, ordinary.artworkSize)
        assertFalse(ordinary.scrollVertical)

        val taller = resolveDefaultPlayerLayout(412.dp, 915.dp)
        assertEquals(DefaultPlayerArrangement.Vertical, taller.arrangement)
        assertDp(380.dp, taller.artworkSize)

        val compactArtwork = resolveDefaultPlayerLayout(
            412.dp, 850.dp,
            appearance = appearance(artworkSize = ModernArtworkSize.COMPACT)
        )
        assertDp(289.dp, compactArtwork.artworkSize)
    }

    @Test
    fun balancedRequiresBothWidthAndHeightRatherThanTheNavigationBreakpoint() {
        // This window is narrower than the shell's rail threshold but can fit both player regions.
        assertEquals(DefaultPlayerArrangement.Balanced,
            resolveDefaultPlayerLayout(800.dp, 600.dp).arrangement)
        assertEquals(DefaultPlayerArrangement.Vertical,
            resolveDefaultPlayerLayout(740.dp, 600.dp).arrangement)
        // A wide portrait window can use a rail without needing side-by-side player content.
        assertEquals(DefaultPlayerArrangement.Vertical,
            resolveDefaultPlayerLayout(900.dp, 1400.dp).arrangement)
        val short = resolveDefaultPlayerLayout(1280.dp, 380.dp)
        assertEquals(DefaultPlayerArrangement.Vertical, short.arrangement)
        assertTrue(short.scrollVertical)
    }

    @Test
    fun insetsAreAppliedOnceToBalancedGeometry() {
        val insets = DefaultPlayerInsets(20.dp, 24.dp, 16.dp, 48.dp)
        val inset = resolveDefaultPlayerLayout(1300.dp, 820.dp, insets)
        val alreadyUsable = resolveDefaultPlayerLayout(1264.dp, 748.dp)

        assertEquals(DefaultPlayerArrangement.Balanced, inset.arrangement)
        assertEquals(alreadyUsable.arrangement, inset.arrangement)
        assertDp(alreadyUsable.artworkSize, inset.artworkSize)
        assertDp(alreadyUsable.detailsWidth, inset.detailsWidth)
        assertEquals(alreadyUsable.minimumDetailsHeight, inset.minimumDetailsHeight)
        assertDp(24.dp, inset.safePadding.calculateTopPadding())
        assertDp(48.dp, inset.safePadding.calculateBottomPadding())
    }

    @Test
    fun taskbarAndCaptionInsetsCanMakeAnOtherwiseWideWindowTooShort() {
        assertEquals(DefaultPlayerArrangement.Balanced,
            resolveDefaultPlayerLayout(1280.dp, 480.dp).arrangement)
        val inset = resolveDefaultPlayerLayout(
            1280.dp, 480.dp, DefaultPlayerInsets(top = 32.dp, bottom = 64.dp)
        )
        assertEquals(DefaultPlayerArrangement.Vertical, inset.arrangement)
        assertTrue(inset.scrollVertical)
    }

    @Test
    fun largerTextRequiresEnoughMetadataWidthAndHeight() {
        assertEquals(DefaultPlayerArrangement.Balanced,
            resolveDefaultPlayerLayout(900.dp, 600.dp).arrangement)
        val largeText = resolveDefaultPlayerLayout(900.dp, 600.dp, fontScale = 1.8f)
        assertEquals(DefaultPlayerArrangement.Vertical, largeText.arrangement)

        val roomy = resolveDefaultPlayerLayout(1600.dp, 1000.dp, fontScale = 1.8f)
        assertEquals(DefaultPlayerArrangement.Balanced, roomy.arrangement)
        assertTrue(roomy.detailsWidth >= 360.dp * 1.8f)
        assertTrue(roomy.minimumDetailsHeight >
            resolveDefaultPlayerLayout(1600.dp, 1000.dp).minimumDetailsHeight)
    }

    @Test
    fun tallWaveformLargeControlsAndRelaxedSpacingRequireMoreHeight() {
        val standard = resolveDefaultPlayerLayout(1280.dp, 440.dp)
        assertEquals(DefaultPlayerArrangement.Balanced, standard.arrangement)
        val demanding = resolveDefaultPlayerLayout(
            1280.dp, 440.dp,
            appearance = appearance(
                controlSize = ModernControlSize.LARGE,
                waveformSize = ModernWaveformSize.TALL,
                density = ModernLayoutDensity.RELAXED
            )
        )
        assertEquals(DefaultPlayerArrangement.Vertical, demanding.arrangement)
        assertTrue(demanding.minimumDetailsHeight > standard.minimumDetailsHeight)

        val withoutBadge = resolveDefaultPlayerLayout(
            1280.dp, 440.dp,
            appearance = appearance(
                controlSize = ModernControlSize.LARGE,
                waveformSize = ModernWaveformSize.TALL,
                density = ModernLayoutDensity.RELAXED,
                showQuality = false
            )
        )
        assertEquals(DefaultPlayerArrangement.Balanced, withoutBadge.arrangement)
    }

    @Test
    fun artworkPreferenceChangesSizeWithoutExceedingTheAllocatedRegions() {
        val sizes = ModernArtworkSize.entries.map { size ->
            val layout = resolveDefaultPlayerLayout(
                1280.dp, 800.dp,
                appearance = appearance(artworkSize = size)
            )
            assertEquals(DefaultPlayerArrangement.Balanced, layout.arrangement)
            assertTrue(layout.artworkSize <= 776.dp)
            assertTrue(layout.artworkSize + layout.regionGap + layout.detailsWidth <= 1248.dp)
            layout.artworkSize
        }
        assertTrue(sizes[0] < sizes[1])
        assertTrue(sizes[1] < sizes[2])

        val heightLimited = resolveDefaultPlayerLayout(
            1280.dp, 460.dp,
            appearance = appearance(artworkSize = ModernArtworkSize.LARGE)
        )
        assertEquals(DefaultPlayerArrangement.Balanced, heightLimited.arrangement)
        assertTrue(heightLimited.artworkSize <= 436.dp)
    }

    @Test
    fun controlTargetsAndGapsFitForEveryExistingControlSize() {
        ModernControlSize.entries.forEach { size ->
            val layout = resolveDefaultPlayerLayout(
                820.dp, 650.dp, appearance = appearance(controlSize = size)
            )
            assertEquals(DefaultPlayerArrangement.Balanced, layout.arrangement)
            val targetsAndGaps = (size.primarySizeDp + 4 * size.modeContainerSizeDp + 48).dp
            assertTrue(layout.detailsWidth >= targetsAndGaps)
            assertTrue(layout.artworkSize >= 240.dp)
            assertTrue(layout.artworkSize + layout.regionGap + layout.detailsWidth <= 788.dp)
        }
    }

    @Test
    fun densityChangesTheRegionGapWithoutDiscardingArtworkPreferences() {
        val layouts = ModernLayoutDensity.entries.map { density ->
            resolveDefaultPlayerLayout(
                1400.dp, 900.dp,
                appearance = appearance(artworkSize = ModernArtworkSize.LARGE, density = density)
            )
        }
        assertTrue(layouts.all { it.arrangement == DefaultPlayerArrangement.Balanced })
        assertTrue(layouts[0].regionGap < layouts[1].regionGap)
        assertTrue(layouts[1].regionGap < layouts[2].regionGap)
        assertDp(layouts[0].artworkSize, layouts[1].artworkSize)
        assertDp(layouts[1].artworkSize, layouts[2].artworkSize)
    }

    @Test
    fun nonGeometricAppearanceOptionsDoNotChangeLayoutSelectionOrBounds() {
        val baseline = resolveDefaultPlayerLayout(1280.dp, 800.dp)
        val visualVariant = ModernPlayerAppearance.Default.copy(
            background = ModernBackgroundAppearance(
                style = ModernBackgroundStyle.PURE_BLACK,
                blurStrength = ModernBlurStrength.HIGH,
                dimmingStrength = ModernDimmingStrength.HIGH
            ),
            artwork = ModernArtworkAppearance(
                shape = ModernArtworkShape.SQUARE,
                fit = ModernArtworkFit.SHOW_FULL,
                shadow = ModernArtworkShadow.STRONG
            ),
            controls = ModernControlAppearance(
                style = ModernControlStyle.OUTLINE,
                accent = ModernControlAccent.ALBUM_DERIVED
            ),
            seekbar = ModernSeekbarAppearance(
                waveformDensity = ModernWaveformDensity.DETAILED,
                colorMode = ModernSeekbarColorMode.APP_ACCENT
            ),
            layout = ModernLayoutAppearance(metadataAlignment = ModernMetadataAlignment.CENTER)
        )
        assertEquals(baseline, resolveDefaultPlayerLayout(1280.dp, 800.dp, appearance = visualVariant))
    }

    private fun appearance(
        artworkSize: ModernArtworkSize = ModernArtworkSize.STANDARD,
        controlSize: ModernControlSize = ModernControlSize.STANDARD,
        waveformSize: ModernWaveformSize = ModernWaveformSize.STANDARD,
        density: ModernLayoutDensity = ModernLayoutDensity.BALANCED,
        showQuality: Boolean = true
    ) = ModernPlayerAppearance.Default.copy(
        artwork = ModernArtworkAppearance(size = artworkSize),
        controls = ModernControlAppearance(size = controlSize),
        seekbar = ModernSeekbarAppearance(waveformSize = waveformSize),
        layout = ModernLayoutAppearance(density = density, showAudioQualityBadge = showQuality)
    )

    private fun assertDp(expected: Dp, actual: Dp) =
        assertEquals(expected.value, actual.value, 0.01f)
}
