package io.github.rsgarrido.sazanami.player.equalizer.parametric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricFilterValidationTest {
    @Test
    fun defaultFilterIsUnityPeakingWithStableIdentity() {
        val filter = ParametricFilterFactory.default(
            id = "stable-id"
        )

        assertTrue(filter is ParametricFilter.Peaking)
        filter as ParametricFilter.Peaking
        assertEquals("stable-id", filter.id)
        assertTrue(filter.enabled)
        assertEquals(1_000.0, filter.frequencyHz, 0.0)
        assertEquals(0.0, filter.gainDb, 0.0)
        assertEquals(1.0, filter.q, 0.0)
        assertFalse(filter.hasAudibleEffect)
    }

    @Test
    fun allTypesAcceptSupportedEndpoints() {
        listOf(
            ParametricFilter.Peaking(
                "peak", true, 20.0, -15.0, 0.10
            ),
            ParametricFilter.LowShelf(
                "low-shelf", true, 20_000.0, 15.0, 0.10
            ),
            ParametricFilter.HighShelf(
                "high-shelf", false, 20.0, -15.0, 1.0
            ),
            ParametricFilter.LowPass(
                "low-pass", true, 20_000.0, 20.0
            ),
            ParametricFilter.HighPass(
                "high-pass", true, 20.0, 0.10
            ),
            ParametricFilter.Notch(
                "notch", true, 20_000.0, 20.0
            ),
            ParametricFilter.BandPass(
                "band-pass", true, 20.0, 0.10
            )
        ).forEach { filter ->
            assertTrue(filter.id.isNotBlank())
        }
    }

    @Test
    fun invalidIdentityAndParametersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ParametricFilter.Peaking(
                "", true, 1_000.0, 0.0, 1.0
            )
        }
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            19.9,
            20_000.1
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                ParametricFilter.Peaking(
                    "id", true, invalid, 0.0, 1.0
                )
            }
        }
        listOf(Double.NaN, -15.1, 15.1).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                ParametricFilter.Peaking(
                    "id", true, 1_000.0, invalid, 1.0
                )
            }
        }
        listOf(Double.NaN, 0.09, 20.01).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                ParametricFilter.Notch(
                    "id", true, 1_000.0, invalid
                )
            }
        }
        listOf(Double.NaN, 0.09, 1.01).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                ParametricFilter.LowShelf(
                    "id", true, 100.0, 0.0, invalid
                )
            }
        }
    }

    @Test
    fun normalizationUsesDocumentedPrecision() {
        val state = ParametricEqualizerState(
            filters = listOf(
                ParametricFilter.Peaking(
                    "peak",
                    true,
                    1_234.56,
                    3.26,
                    1.236
                ),
                ParametricFilter.LowShelf(
                    "shelf",
                    true,
                    123.44,
                    -2.24,
                    0.876
                )
            )
        )

        val peak = state.filters[0] as ParametricFilter.Peaking
        assertEquals(1_234.6, peak.frequencyHz, 0.0)
        assertEquals(3.3, peak.gainDb, 0.0)
        assertEquals(1.24, peak.q, 0.0)
        val shelf = state.filters[1] as ParametricFilter.LowShelf
        assertEquals(123.4, shelf.frequencyHz, 0.0)
        assertEquals(-2.2, shelf.gainDb, 0.0)
        assertEquals(0.88, shelf.slope, 0.0)
    }

    @Test
    fun typeChangePreservesIdentityStateAndCompatibleValues() {
        val peak = ParametricFilter.Peaking(
            id = "stable",
            enabled = false,
            frequencyHz = 2_345.6,
            gainDb = -4.5,
            q = 2.25
        )

        val shelf = peak.changeType(
            ParametricFilterType.HIGH_SHELF
        ) as ParametricFilter.HighShelf
        assertEquals("stable", shelf.id)
        assertFalse(shelf.enabled)
        assertEquals(2_345.6, shelf.frequencyHz, 0.0)
        assertEquals(-4.5, shelf.gainDb, 0.0)
        assertEquals(1.0, shelf.slope, 0.0)

        val pass = peak.changeType(
            ParametricFilterType.LOW_PASS
        ) as ParametricFilter.LowPass
        assertEquals(2.25, pass.q, 0.0)
        assertNull(pass.gainDbOrNull)
    }

    @Test
    fun nonGainFiltersAreAlwaysAudibleWhenEnabled() {
        val filters = listOf(
            ParametricFilterFactory.default(
                ParametricFilterType.LOW_PASS,
                "lp"
            ),
            ParametricFilterFactory.default(
                ParametricFilterType.HIGH_PASS,
                "hp"
            ),
            ParametricFilterFactory.default(
                ParametricFilterType.NOTCH,
                "notch"
            ),
            ParametricFilterFactory.default(
                ParametricFilterType.BAND_PASS,
                "bp"
            )
        )

        assertTrue(filters.all { filter -> filter.hasAudibleEffect })
        assertTrue(
            filters.map { filter -> filter.withEnabled(false) }
                .none { filter -> filter.hasAudibleEffect }
        )
    }
}
