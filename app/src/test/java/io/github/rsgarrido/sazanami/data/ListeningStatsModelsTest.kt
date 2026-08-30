package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.ListeningSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsModelsTest {
    @Test
    fun dateRangeIsHalfOpenAndRejectsEmptyOrReversedBounds() {
        val range = ListeningDateRange(100L, 200L)
        assertTrue(100L >= range.startInclusive && 100L < range.endExclusive)
        assertTrue(199L >= range.startInclusive && 199L < range.endExclusive)
        assertFalse(200L >= range.startInclusive && 200L < range.endExclusive)
        assertThrows(IllegalArgumentException::class.java) { ListeningDateRange(100L, 100L) }
        assertThrows(IllegalArgumentException::class.java) { ListeningDateRange(101L, 100L) }
    }

    @Test
    fun legacyCanOnlyBeEffectiveForUnfilteredAllTimeQueries() {
        assertTrue(ListeningStatsFilter().effectiveIncludeLegacy)
        assertFalse(
            ListeningStatsFilter(
                sources = setOf(ListeningSource.NATIVE),
                includeLegacyBaseline = true
            ).effectiveIncludeLegacy
        )
        assertThrows(IllegalArgumentException::class.java) {
            ListeningStatsFilter(
                dateRange = ListeningDateRange(0L, 1L),
                includeLegacyBaseline = true
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ListeningStatsFilter(sources = emptySet())
        }
    }
}
