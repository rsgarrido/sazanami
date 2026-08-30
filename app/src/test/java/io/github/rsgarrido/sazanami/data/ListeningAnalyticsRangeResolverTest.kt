package io.github.rsgarrido.sazanami.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningAnalyticsRangeResolverTest {
    private val instant = Instant.parse("2026-03-18T19:15:00Z")
    private var zone = ZoneId.of("America/Los_Angeles")
    private val resolver = ListeningAnalyticsRangeResolver(
        Clock.fixed(instant, ZoneId.of("UTC")),
        AnalyticsZoneIdProvider { zone }
    )

    @Test
    fun presetsResolveToExactLocalCalendarIntervals() {
        assertDates(AnalyticsRangePreset.TODAY, "2026-03-18", "2026-03-19")
        assertDates(AnalyticsRangePreset.LAST_7_DAYS, "2026-03-12", "2026-03-19")
        assertDates(AnalyticsRangePreset.LAST_30_DAYS, "2026-02-17", "2026-03-19")
        assertDates(AnalyticsRangePreset.THIS_MONTH, "2026-03-01", "2026-03-19")
        assertDates(AnalyticsRangePreset.THIS_YEAR, "2026-01-01", "2026-03-19")

        val allTime = resolver.resolve(AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME))
        assertNull(allTime.eventRange)
        assertTrue(allTime.isAllTime)
        assertEquals(instant, allTime.resolvedAt)
    }

    @Test
    fun customEndIsInclusiveAndReversedDatesAreRejected() {
        val oneDay = resolver.resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2024-02-29"), LocalDate.parse("2024-02-29"))
        )
        assertResolvedDates(oneDay, "2024-02-29", "2024-03-01")

        val multiDay = resolver.resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2025-12-30"), LocalDate.parse("2026-01-02"))
        )
        assertResolvedDates(multiDay, "2025-12-30", "2026-01-03")

        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsRangeSelection.Custom(LocalDate.parse("2026-04-02"), LocalDate.parse("2026-04-01"))
        }
    }

    @Test
    fun everyResolutionUsesTheCurrentTimezoneWithoutChangingCustomDates() {
        val selection = AnalyticsRangeSelection.Custom(
            LocalDate.parse("2026-03-08"),
            LocalDate.parse("2026-03-09")
        )
        val losAngeles = resolver.resolve(selection)
        zone = ZoneId.of("Asia/Kolkata")
        val kolkata = resolver.resolve(selection)

        assertEquals(selection, losAngeles.selection)
        assertEquals(selection, kolkata.selection)
        assertEquals(ZoneId.of("America/Los_Angeles"), losAngeles.zoneId)
        assertEquals(ZoneId.of("Asia/Kolkata"), kolkata.zoneId)
        assertResolvedDates(losAngeles, "2026-03-08", "2026-03-10")
        assertResolvedDates(kolkata, "2026-03-08", "2026-03-10")
        assertTrue(requireNotNull(losAngeles.eventRange).startInclusive != requireNotNull(kolkata.eventRange).startInclusive)
    }

    @Test
    fun localDaysUseActualDstElapsedDurations() {
        val spring = resolver.resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08"))
        )
        val fall = resolver.resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"))
        )
        assertEquals(Duration.ofHours(23).toMillis(), duration(spring))
        assertEquals(Duration.ofHours(25).toMillis(), duration(fall))

        zone = ZoneId.of("Asia/Kolkata")
        val noDst = resolver.resolve(
            AnalyticsRangeSelection.Custom(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08"))
        )
        assertEquals(Duration.ofHours(24).toMillis(), duration(noDst))
    }

    private fun assertDates(preset: AnalyticsRangePreset, start: String, end: String) {
        assertResolvedDates(resolver.resolve(AnalyticsRangeSelection.Preset(preset)), start, end)
    }

    private fun assertResolvedDates(range: ResolvedAnalyticsRange, start: String, end: String) {
        val eventRange = requireNotNull(range.eventRange)
        assertEquals(LocalDate.parse(start), Instant.ofEpochMilli(eventRange.startInclusive).atZone(range.zoneId).toLocalDate())
        assertEquals(LocalDate.parse(end), Instant.ofEpochMilli(eventRange.endExclusive).atZone(range.zoneId).toLocalDate())
    }

    private fun duration(range: ResolvedAnalyticsRange): Long = requireNotNull(range.eventRange).let {
        it.endExclusive - it.startInclusive
    }
}
