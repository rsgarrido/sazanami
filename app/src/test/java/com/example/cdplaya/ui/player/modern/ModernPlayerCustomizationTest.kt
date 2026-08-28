package com.example.cdplaya.ui.player.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernPlayerCustomizationTest {
    @Test
    fun artworkTransitionStyles_exposeStableValuesAndFriendlyLabels() {
        val expected = listOf(
            StyleExpectation(
                style = ModernArtworkTransitionStyle.SLIDE,
                storageValue = "slide",
                displayName = "Slide",
                description = "Current artwork slides naturally with the next cover."
            ),
            StyleExpectation(
                style = ModernArtworkTransitionStyle.DEPTH_SCALE,
                storageValue = "depth_scale",
                displayName = "Depth & Scale",
                description = "Covers shrink and fade slightly as they move, creating depth."
            ),
            StyleExpectation(
                style = ModernArtworkTransitionStyle.PARALLAX,
                storageValue = "parallax",
                displayName = "Parallax",
                description = "Artwork and text move at different speeds."
            ),
            StyleExpectation(
                style = ModernArtworkTransitionStyle.COVER_FLOW,
                storageValue = "cover_flow",
                displayName = "Cover Flow",
                description = "Covers tilt slightly as they move off-center."
            ),
            StyleExpectation(
                style = ModernArtworkTransitionStyle.STACK_REVEAL,
                storageValue = "stack_reveal",
                displayName = "Stack Reveal",
                description = "Current cover peels away to reveal the next cover underneath."
            )
        )

        assertEquals(expected.map { item -> item.style }, ModernArtworkTransitionStyle.values().toList())
        expected.forEach { item ->
            assertEquals(item.storageValue, item.style.storageValue)
            assertEquals(item.displayName, item.style.displayName)
            assertEquals(item.description, item.style.description)
        }
        assertTrue(ModernArtworkTransitionStyle.values().all { style ->
            style.displayName.isNotBlank() && style.description.isNotBlank()
        })
    }

    @Test
    fun seekbarStyles_exposeStableValuesAndFriendlyLabels() {
        val expected = listOf(
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.CLASSIC_BAR,
                storageValue = "classic_bar",
                displayName = "Classic Bar",
                description = "The current simple progress bar."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.SLIM_LINE,
                storageValue = "slim_line",
                displayName = "Slim Line",
                description = "A minimal thin progress line."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.THICK_CAPSULE,
                storageValue = "thick_capsule",
                displayName = "Thick Capsule",
                description = "A larger rounded progress control."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.SEGMENTED,
                storageValue = "segmented",
                displayName = "Segmented",
                description = "Small separated blocks that fill with progress."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.WAVEFORM_PREVIEW,
                storageValue = "waveform_preview",
                displayName = "Waveform Preview",
                description = "Uses analyzed track shape when available, with a generated preview while loading or unsupported."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.WAVEFORM_PEAKS,
                storageValue = "waveform_peaks",
                displayName = "Waveform Peaks",
                description = "Mirrors analyzed track peaks when available, with a generated preview as fallback."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.WAVEFORM_GLOW,
                storageValue = "waveform_glow",
                displayName = "Waveform Glow",
                description = "Adds a soft glow to analyzed track shape, with a generated preview as fallback."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.CONTINUOUS_WAVEFORM,
                storageValue = "continuous_waveform",
                displayName = "Continuous Waveform",
                description = "A connected, filled waveform silhouette."
            ),
            SeekbarStyleExpectation(
                style = ModernSeekbarStyle.WAVE_LINE,
                storageValue = "wave_line",
                displayName = "Wave Line",
                description = "A smooth line that follows the track's waveform."
            )
        )

        assertEquals(expected.map { item -> item.style }, ModernSeekbarStyle.values().toList())
        expected.forEach { item ->
            assertEquals(item.storageValue, item.style.storageValue)
            assertEquals(item.displayName, item.style.displayName)
            assertEquals(item.description, item.style.description)
        }
        assertTrue(
            ModernSeekbarStyle.values().count { style -> style.usesWaveformData } == 5
        )
        assertTrue(
            listOf(
                ModernSeekbarStyle.CLASSIC_BAR,
                ModernSeekbarStyle.SLIM_LINE,
                ModernSeekbarStyle.THICK_CAPSULE,
                ModernSeekbarStyle.SEGMENTED
            ).none { style -> style.usesWaveformData }
        )
        assertEquals(
            setOf(
                ModernSeekbarStyle.CONTINUOUS_WAVEFORM,
                ModernSeekbarStyle.WAVE_LINE
            ),
            ModernSeekbarStyle.entries.filter { style -> style.usesContinuousPath }.toSet()
        )
    }

    @Test
    fun modernAppearanceDefaultsMatchTheIntendedCdPlayaBaseline() {
        val defaults = ModernPlayerAppearance.Default

        assertEquals(ModernSeekbarStyle.WAVEFORM_PREVIEW, defaults.seekbar.style)
        assertEquals(ModernWaveformSize.STANDARD, defaults.seekbar.waveformSize)
        assertEquals(ModernWaveformDensity.BALANCED, defaults.seekbar.waveformDensity)
        assertEquals(ModernSeekbarColorMode.WHITE, defaults.seekbar.colorMode)
        assertEquals(ModernBackgroundStyle.BLURRED_ARTWORK, defaults.background.style)
        assertEquals(ModernBlurStrength.MEDIUM, defaults.background.blurStrength)
        assertEquals(ModernDimmingStrength.MEDIUM, defaults.background.dimmingStrength)
        assertEquals(DEFAULT_MODERN_SOLID_COLOR_ARGB, defaults.background.solidColorArgb)
        assertEquals(ModernArtworkShape.ROUNDED, defaults.artwork.shape)
        assertEquals(ModernArtworkSize.STANDARD, defaults.artwork.size)
        assertEquals(ModernArtworkFit.CROP, defaults.artwork.fit)
        assertEquals(ModernArtworkShadow.SOFT, defaults.artwork.shadow)
        assertEquals(ModernControlStyle.GLASS, defaults.controls.style)
        assertEquals(ModernControlSize.STANDARD, defaults.controls.size)
        assertEquals(ModernControlAccent.WHITE, defaults.controls.accent)
        assertEquals(ModernLayoutDensity.BALANCED, defaults.layout.density)
        assertEquals(ModernMetadataAlignment.LEFT, defaults.layout.metadataAlignment)
        assertTrue(defaults.layout.showAudioQualityBadge)
    }

    @Test
    fun newAppearanceEnumsUseStableStorageValuesAndSafeFallbacks() {
        ModernWaveformSize.entries.forEach { value ->
            assertEquals(value, ModernWaveformSize.fromStorageValue(value.storageValue))
        }
        ModernWaveformDensity.entries.forEach { value ->
            assertEquals(value, ModernWaveformDensity.fromStorageValue(value.storageValue))
        }
        ModernSeekbarColorMode.entries.forEach { value ->
            assertEquals(value, ModernSeekbarColorMode.fromStorageValue(value.storageValue))
        }
        ModernBackgroundStyle.entries.forEach { value ->
            assertEquals(value, ModernBackgroundStyle.fromStorageValue(value.storageValue))
        }
        ModernBlurStrength.entries.forEach { value ->
            assertEquals(value, ModernBlurStrength.fromStorageValue(value.storageValue))
        }
        ModernDimmingStrength.entries.forEach { value ->
            assertEquals(value, ModernDimmingStrength.fromStorageValue(value.storageValue))
        }
        ModernArtworkShape.entries.forEach { value ->
            assertEquals(value, ModernArtworkShape.fromStorageValue(value.storageValue))
        }
        ModernArtworkSize.entries.forEach { value ->
            assertEquals(value, ModernArtworkSize.fromStorageValue(value.storageValue))
        }
        ModernArtworkFit.entries.forEach { value ->
            assertEquals(value, ModernArtworkFit.fromStorageValue(value.storageValue))
        }
        ModernArtworkShadow.entries.forEach { value ->
            assertEquals(value, ModernArtworkShadow.fromStorageValue(value.storageValue))
        }
        ModernControlStyle.entries.forEach { value ->
            assertEquals(value, ModernControlStyle.fromStorageValue(value.storageValue))
        }
        ModernControlSize.entries.forEach { value ->
            assertEquals(value, ModernControlSize.fromStorageValue(value.storageValue))
        }
        ModernControlAccent.entries.forEach { value ->
            assertEquals(value, ModernControlAccent.fromStorageValue(value.storageValue))
        }
        ModernLayoutDensity.entries.forEach { value ->
            assertEquals(value, ModernLayoutDensity.fromStorageValue(value.storageValue))
        }
        ModernMetadataAlignment.entries.forEach { value ->
            assertEquals(value, ModernMetadataAlignment.fromStorageValue(value.storageValue))
        }

        assertEquals(ModernWaveformSize.STANDARD, ModernWaveformSize.fromStorageValue("future"))
        assertEquals(
            ModernWaveformDensity.BALANCED,
            ModernWaveformDensity.fromStorageValue(null)
        )
        assertEquals(ModernSeekbarColorMode.WHITE, ModernSeekbarColorMode.fromStorageValue(""))
        assertEquals(
            ModernBackgroundStyle.BLURRED_ARTWORK,
            ModernBackgroundStyle.fromStorageValue("future")
        )
        assertEquals(ModernBlurStrength.MEDIUM, ModernBlurStrength.fromStorageValue(null))
        assertEquals(ModernDimmingStrength.MEDIUM, ModernDimmingStrength.fromStorageValue(""))
    }

    @Test
    fun presetsAreDistinctAndRecognizeExactMatches() {
        val appearances = ModernAppearancePreset.entries.map { preset -> preset.appearance() }

        assertEquals(appearances.size, appearances.distinct().size)
        ModernAppearancePreset.entries.forEach { preset ->
            assertEquals(preset, ModernAppearancePreset.matching(preset.appearance()))
        }
        assertEquals(
            null,
            ModernAppearancePreset.matching(
                ModernPlayerAppearance.Default.copy(
                    background = ModernPlayerAppearance.Default.background.copy(
                        solidColorArgb = 0xFF123456L
                    )
                )
            )
        )
        assertEquals(
            ModernPlayerAppearance.Default,
            ModernAppearancePreset.CDPLAYA.appearance()
        )
        assertEquals(
            ModernArtworkSize.LARGE,
            ModernAppearancePreset.ARTWORK_FOCUS.appearance().artwork.size
        )
        assertEquals(
            ModernControlStyle.MINIMAL,
            ModernAppearancePreset.MINIMAL.appearance().controls.style
        )
        assertEquals(
            ModernSeekbarColorMode.ALBUM_DERIVED,
            ModernAppearancePreset.COLORFUL.appearance().seekbar.colorMode
        )
        assertEquals(
            ModernControlAccent.ALBUM_DERIVED,
            ModernAppearancePreset.COLORFUL.appearance().controls.accent
        )
    }

    @Test
    fun backgroundCapabilitiesHideControlsThatWouldHaveNoEffect() {
        assertTrue(ModernBackgroundStyle.BLURRED_ARTWORK.supportsBlur)
        assertTrue(ModernBackgroundStyle.DETAILED_ARTWORK.supportsBlur)
        assertTrue(ModernBackgroundStyle.ALBUM_GRADIENT.supportsDimming)
        assertFalse(ModernBackgroundStyle.PURE_BLACK.supportsBlur)
        assertFalse(ModernBackgroundStyle.PURE_BLACK.supportsDimming)
        assertFalse(ModernBackgroundStyle.SOLID_COLOR.supportsBlur)
        assertFalse(ModernBackgroundStyle.SOLID_COLOR.supportsDimming)
    }

    @Test
    fun artworkControlAndLayoutMappingsAreOrderedAndAccessible() {
        assertEquals(listOf(0, 10, 30, 48), ModernArtworkShape.entries.map { it.cornerRadiusDp })
        assertTrue(ModernArtworkSize.COMPACT.maximumScale < ModernArtworkSize.STANDARD.maximumScale)
        assertTrue(ModernArtworkSize.STANDARD.maximumScale < ModernArtworkSize.LARGE.maximumScale)
        assertEquals(listOf(0, 10, 22), ModernArtworkShadow.entries.map { it.elevationDp })
        assertTrue(ModernControlSize.entries.all { it.modeContainerSizeDp >= 48 })
        assertTrue(
            ModernLayoutDensity.COMPACT.minimumFlexibleGapDp <
                    ModernLayoutDensity.BALANCED.minimumFlexibleGapDp
        )
        assertTrue(
            ModernLayoutDensity.BALANCED.minimumFlexibleGapDp <
                    ModernLayoutDensity.RELAXED.minimumFlexibleGapDp
        )
    }

    private data class StyleExpectation(
        val style: ModernArtworkTransitionStyle,
        val storageValue: String,
        val displayName: String,
        val description: String
    )

    private data class SeekbarStyleExpectation(
        val style: ModernSeekbarStyle,
        val storageValue: String,
        val displayName: String,
        val description: String
    )
}
