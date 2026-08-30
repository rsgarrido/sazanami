package io.github.rsgarrido.sazanami.ui.statistics

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsFormattingTest {
    @Test
    fun durationPartsCoverZeroMinutesHoursAndDays() {
        assertEquals(ListeningDurationParts(minutes = 0), listeningDurationParts(0L))
        assertEquals(ListeningDurationParts(minutes = 0), listeningDurationParts(59_999L))
        assertEquals(ListeningDurationParts(minutes = 42), listeningDurationParts(42L * 60_000L))
        assertEquals(ListeningDurationParts(hours = 1), listeningDurationParts(60L * 60_000L))
        assertEquals(
            ListeningDurationParts(hours = 3, minutes = 18),
            listeningDurationParts((3L * 60L + 18L) * 60_000L)
        )
        assertEquals(ListeningDurationParts(days = 1), listeningDurationParts(24L * 60L * 60_000L))
        assertEquals(
            ListeningDurationParts(days = 2, hours = 4),
            listeningDurationParts((52L * 60L) * 60_000L)
        )
        assertEquals(ListeningDurationParts(minutes = 0), listeningDurationParts(-1L))
    }

    @Test
    fun countsAndCustomDatesUseTheRequestedLocale() {
        assertEquals("1,234,567", formatAnalyticsCount(1_234_567L, Locale.US))
        assertEquals("1.234.567", formatAnalyticsCount(1_234_567L, Locale.GERMANY))
        assertEquals("0", formatAnalyticsCount(-2L, Locale.US))
        assertEquals(
            "Jul 1, 2026 – Jul 31, 2026",
            formatCustomDateRange(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                Locale.US
            )
        )
    }
}
