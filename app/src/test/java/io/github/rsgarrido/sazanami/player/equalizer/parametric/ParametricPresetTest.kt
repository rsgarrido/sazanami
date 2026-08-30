package io.github.rsgarrido.sazanami.player.equalizer.parametric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ParametricPresetTest {
    @Test
    fun flatMatchesOnlyDocumentedFlatState() {
        assertEquals(
            "Flat",
            ParametricEqualizerPresetMatcher.match(
                ParametricEqualizerState()
            )?.name
        )
        assertNull(
            ParametricEqualizerPresetMatcher.match(
                ParametricEqualizerState(
                    automaticHeadroomEnabled = false
                )
            )
        )
    }

    @Test
    fun matchingIncludesOrderTypeStateAndApplicableParametersButNotIds() {
        val preset = ParametricEqualizerPreset(
            id = "preset",
            name = "Headphones",
            preampDb = -2.0,
            automaticHeadroomEnabled = false,
            filters = listOf(
                ParametricFilter.Peaking(
                    "preset-a", true, 1_000.0, 4.0, 1.2
                ),
                ParametricFilter.Notch(
                    "preset-b", false, 5_000.0, 4.0
                )
            )
        )
        val active = ParametricEqualizerState(
            preampDb = -2.0,
            automaticHeadroomEnabled = false,
            filters = listOf(
                ParametricFilter.Peaking(
                    "active-a", true, 1_000.0, 4.0, 1.2
                ),
                ParametricFilter.Notch(
                    "active-b", false, 5_000.0, 4.0
                )
            ),
            userPresets = listOf(preset)
        )

        val match = ParametricEqualizerPresetMatcher.match(active)
        assertEquals("Headphones", match?.name)
        assertEquals("preset", match?.userPresetId)

        assertNull(
            ParametricEqualizerPresetMatcher.match(
                active.copy(filters = active.filters.reversed())
            )
        )
        assertNull(
            ParametricEqualizerPresetMatcher.match(
                active.withFilter(
                    active.filters[1].withEnabled(true)
                )
            )
        )
    }

    @Test
    fun saveRenameAndNameValidationPreservePresetIdentity() {
        val state = ParametricEqualizerState(
            filters = listOf(
                ParametricFilterFactory.default(id = "filter")
            )
        )
        val preset = ParametricEqualizerPresets.createUserPreset(
            name = "  Reference  ",
            state = state,
            id = "stable"
        )
        assertEquals("Reference", preset.name)

        val renamed = ParametricEqualizerPresets.renameUserPreset(
            presetId = "stable",
            newName = "Revised",
            userPresets = listOf(preset)
        ).single()
        assertEquals("stable", renamed.id)
        assertEquals("Revised", renamed.name)
        assertEquals(preset.filters, renamed.filters)

        assertThrows(IllegalArgumentException::class.java) {
            ParametricEqualizerPresets.createUserPreset(
                name = "Flat",
                state = state
            )
        }
    }
}
