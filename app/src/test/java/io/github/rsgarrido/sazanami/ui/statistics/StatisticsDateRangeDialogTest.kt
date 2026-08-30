package io.github.rsgarrido.sazanami.ui.statistics

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsDateRangeDialogTest {
    @Test
    fun pickerMillisecondsAreUtcCalendarDatesInBothDirections() {
        listOf(
            LocalDate.of(2026, 3, 8),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(1970, 1, 1),
            LocalDate.of(2035, 12, 31)
        ).forEach { date ->
            val millis = localDateToPickerUtcMillis(date)
            assertEquals(date, pickerUtcMillisToLocalDate(millis))
            assertEquals(
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                millis
            )
        }
    }
}
