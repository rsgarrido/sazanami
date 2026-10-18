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

    @Test
    fun balancedDefaultAndPortraitGeometryAreIndependentOfTheSavedWideChoice() {
        assertEquals(resolveDefaultPlayerLayout(1280.dp, 800.dp), resolveDefaultPlayerLayout(
            1280.dp, 800.dp, preferredLandscapeLayout = ModernLandscapeLayout.BALANCED))
        assertEquals(resolveDefaultPlayerLayout(412.dp, 850.dp), wide(412.dp, 850.dp))
        assertEquals(DefaultPlayerArrangement.Vertical, wide(900.dp, 1400.dp).arrangement)
    }

    @Test
    fun wideReservesProgressBeforeSizingTheUpperRegions() {
        val layout = wide(1280.dp, 720.dp)
        assertEquals(DefaultPlayerArrangement.WideWaveform, layout.arrangement)
        val upperHeight = 696.dp - layout.progressRegionHeight - layout.regionGap
        assertTrue(layout.artworkSize <= upperHeight)
        assertTrue(layout.minimumDetailsHeight <= upperHeight)
        assertTrue(layout.artworkSize + layout.regionGap + layout.detailsWidth <= 1248.dp)
        assertTrue(layout.artworkSize >= 240.dp)
    }

    @Test
    fun wideHeightBoundaryFallsBackToBalancedThenVerticalAndRestores() {
        val roomy = wide(1280.dp, 720.dp)
        val requiredHostHeight = maxOf(240.dp, roomy.minimumDetailsHeight) +
            roomy.progressRegionHeight + roomy.regionGap + 24.dp
        assertEquals(DefaultPlayerArrangement.WideWaveform, wide(1280.dp, requiredHostHeight).arrangement)
        assertEquals(DefaultPlayerArrangement.Balanced, wide(1280.dp, requiredHostHeight - 1.dp).arrangement)
        assertEquals(DefaultPlayerArrangement.Vertical, wide(1280.dp, 380.dp).arrangement)
        assertEquals(roomy, wide(1280.dp, 720.dp))
        repeat(4) {
            assertEquals(DefaultPlayerArrangement.Vertical, wide(740.dp, 600.dp).arrangement)
            assertEquals(DefaultPlayerArrangement.WideWaveform, wide(800.dp, 600.dp).arrangement)
        }
    }

    @Test
    fun wideAppliesRemainingInsetsOnceAndRejectsInsufficientUpperHeight() {
        val insets = DefaultPlayerInsets(20.dp, 24.dp, 16.dp, 48.dp)
        val inset = wide(1300.dp, 820.dp, insets = insets)
        val usable = wide(1264.dp, 748.dp)
        assertEquals(DefaultPlayerArrangement.WideWaveform, inset.arrangement)
        assertDp(usable.artworkSize, inset.artworkSize)
        assertDp(usable.detailsWidth, inset.detailsWidth)
        assertEquals(usable.progressRegionHeight, inset.progressRegionHeight)
        assertEquals(DefaultPlayerArrangement.Vertical,
            wide(1280.dp, 480.dp, insets = DefaultPlayerInsets(top = 32.dp, bottom = 64.dp)).arrangement)
    }

    @Test
    fun wideBudgetsEveryControlWaveformAndDensityWithoutShrinkingTargets() {
        for (controls in ModernControlSize.entries) for (waveform in ModernWaveformSize.entries)
            for (density in ModernLayoutDensity.entries) {
                val appearance = appearance(controlSize = controls, waveformSize = waveform,
                    artworkSize = ModernArtworkSize.LARGE, density = density)
                val layout = wide(1600.dp, 1000.dp, appearance = appearance, fontScale = 1.8f)
                assertEquals(DefaultPlayerArrangement.WideWaveform, layout.arrangement)
                assertTrue(layout.detailsWidth >= (controls.primarySizeDp + 4 * controls.modeContainerSizeDp + 48).dp)
                assertTrue(layout.progressRegionHeight >= waveform.trackHeightDp.dp + 20.dp * 1.8f)
                assertTrue(layout.artworkSize <= 472.dp)
                assertTrue(layout.artworkSize + layout.regionGap + layout.detailsWidth <= 1568.dp)
                val requiredHeight = maxOf(240.dp, layout.minimumDetailsHeight) +
                    layout.progressRegionHeight + layout.regionGap + 24.dp
                assertEquals(DefaultPlayerArrangement.WideWaveform,
                    wide(1600.dp, requiredHeight, appearance = appearance, fontScale = 1.8f).arrangement)
                assertTrue(wide(1600.dp, requiredHeight - 1.dp, appearance = appearance,
                    fontScale = 1.8f).arrangement != DefaultPlayerArrangement.WideWaveform)
            }
    }

    @Test
    fun wideSupportsEverySeekbarWithoutChangingItsAppearanceOrSampling() {
        for (style in ModernSeekbarStyle.entries) {
            val appearance = ModernPlayerAppearance.Default.copy(
                seekbar = ModernSeekbarAppearance(style = style, waveformSize = ModernWaveformSize.TALL))
            val layout = wide(1280.dp, 720.dp, appearance = appearance)
            assertEquals(DefaultPlayerArrangement.WideWaveform, layout.arrangement)
            assertDp((if (style.usesWaveformData) 64.dp else 48.dp) + 20.dp, layout.progressRegionHeight)
        }
    }

    private fun wide(width: Dp, height: Dp, insets: DefaultPlayerInsets = DefaultPlayerInsets(),
        fontScale: Float = 1f, appearance: ModernPlayerAppearance = ModernPlayerAppearance.Default) =
        resolveDefaultPlayerLayout(width, height, insets, fontScale, appearance,
            preferredLandscapeLayout = ModernLandscapeLayout.WIDE_WAVEFORM)

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
