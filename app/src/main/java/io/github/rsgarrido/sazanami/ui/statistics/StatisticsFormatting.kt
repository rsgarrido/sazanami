package io.github.rsgarrido.sazanami.ui.statistics

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class ListeningDurationParts(
    val days: Long = 0L,
    val hours: Long = 0L,
    val minutes: Long = 0L
)

internal fun listeningDurationParts(milliseconds: Long): ListeningDurationParts {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    return when {
        totalMinutes < 60L -> ListeningDurationParts(minutes = totalMinutes)
        totalMinutes < 1_440L -> ListeningDurationParts(
            hours = totalMinutes / 60L,
            minutes = totalMinutes % 60L
        )
        else -> ListeningDurationParts(
            days = totalMinutes / 1_440L,
            hours = totalMinutes % 1_440L / 60L
        )
    }
}

internal fun formatAnalyticsCount(value: Long, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(value.coerceAtLeast(0L))

internal fun formatCustomDateRange(
    startDate: LocalDate,
    endDateInclusive: LocalDate,
    locale: Locale = Locale.getDefault()
): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return "${formatter.format(startDate)} – ${formatter.format(endDateInclusive)}"
}
