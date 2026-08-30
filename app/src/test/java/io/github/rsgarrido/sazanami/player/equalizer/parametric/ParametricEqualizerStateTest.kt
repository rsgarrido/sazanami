package io.github.rsgarrido.sazanami.player.equalizer.parametric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricEqualizerStateTest {
    @Test
    fun defaultStateIsFlat() {
        val state = ParametricEqualizerState()

        assertEquals(0.0, state.preampDb, 0.0)
        assertTrue(state.automaticHeadroomEnabled)
        assertTrue(state.filters.isEmpty())
        assertTrue(state.userPresets.isEmpty())
        assertTrue(state.isEffectivelyFlat)
    }

    @Test
    fun addRemoveReorderAndEnablePreserveStableIds() {
        var state = ParametricEqualizerState()
            .addFilter(
                ParametricFilterFactory.default(id = "first")
            )
            .addFilter(
                ParametricFilterFactory.default(id = "second")
            )

        state = state.moveFilter("second", 0)
        assertEquals(
            listOf("second", "first"),
            state.filters.map(ParametricFilter::id)
        )

        state = state.withFilter(
            state.filters[0].withEnabled(false)
        )
        assertFalse(state.filters[0].enabled)
        assertEquals("second", state.filters[0].id)

        state = state.removeFilter("first")
        assertEquals(listOf("second"), state.filters.map { it.id })
    }

    @Test
    fun tenFiltersAllowedAndEleventhRejected() {
        var state = ParametricEqualizerState()
        repeat(MAX_PARAMETRIC_FILTER_COUNT) { index ->
            state = state.addFilter(
                ParametricFilterFactory.default(id = "filter-$index")
            )
        }
        assertEquals(MAX_PARAMETRIC_FILTER_COUNT, state.filters.size)

        assertThrows(IllegalArgumentException::class.java) {
            state.addFilter(
                ParametricFilterFactory.default(id = "eleventh")
            )
        }
    }

    @Test
    fun duplicateIdsAndCallerMutationAreRejectedOrIsolated() {
        val first = ParametricFilterFactory.default(id = "same")
        assertThrows(IllegalArgumentException::class.java) {
            ParametricEqualizerState(filters = listOf(first, first))
        }

        val caller = mutableListOf<ParametricFilter>(
            ParametricFilterFactory.default(id = "one")
        )
        val state = ParametricEqualizerState(filters = caller)
        caller.clear()

        assertEquals(1, state.filters.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (state.filters as MutableList<ParametricFilter>).clear()
        }
    }

    @Test
    fun effectiveFlatRulesDistinguishUnityAndShapeFilters() {
        assertTrue(
            ParametricEqualizerState(
                filters = listOf(
                    ParametricFilterFactory.default(id = "unity")
                )
            ).isEffectivelyFlat
        )
        assertTrue(
            ParametricEqualizerState(
                filters = listOf(
                    ParametricFilterFactory.default(
                        ParametricFilterType.NOTCH,
                        "disabled"
                    ).withEnabled(false)
                )
            ).isEffectivelyFlat
        )
        assertFalse(
            ParametricEqualizerState(
                filters = listOf(
                    ParametricFilterFactory.default(
                        ParametricFilterType.NOTCH,
                        "notch"
                    )
                )
            ).isEffectivelyFlat
        )
        assertFalse(
            ParametricEqualizerState(preampDb = 0.1)
                .isEffectivelyFlat
        )
    }

    @Test
    fun applyingPresetCreatesIndependentActiveList() {
        val preset = ParametricEqualizerPreset(
            id = "preset",
            name = "Reference",
            preampDb = -1.0,
            automaticHeadroomEnabled = false,
            filters = listOf(
                ParametricFilterFactory.default(id = "filter")
            )
        )
        val state = ParametricEqualizerState(
            userPresets = listOf(preset)
        ).applyPreset(preset)

        assertEquals(preset.filters, state.filters)
        assertNotSame(preset.filters, state.filters)
        assertEquals("filter", state.filters.single().id)
        assertEquals(preset, state.userPresets.single())
    }
}
