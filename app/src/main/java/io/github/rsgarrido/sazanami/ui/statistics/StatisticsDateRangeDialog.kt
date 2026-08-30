package io.github.rsgarrido.sazanami.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rsgarrido.sazanami.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun pickerUtcMillisToLocalDate(value: Long): LocalDate =
    Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()

internal fun localDateToPickerUtcMillis(value: LocalDate): Long =
    value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsDateRangeDialog(
    initialStartDate: LocalDate?,
    initialEndDateInclusive: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate?.let(::localDateToPickerUtcMillis),
        initialSelectedEndDateMillis = initialEndDateInclusive?.let(::localDateToPickerUtcMillis)
    )
    val startDate = pickerState.selectedStartDateMillis?.let(::pickerUtcMillisToLocalDate)
    val endDate = pickerState.selectedEndDateMillis?.let(::pickerUtcMillisToLocalDate)
    val isValid = startDate != null && endDate != null && !endDate.isBefore(startDate)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                StatisticsDateRangeHeader(
                    isValid = isValid,
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (startDate != null && endDate != null && !endDate.isBefore(startDate)) {
                            onConfirm(startDate, endDate)
                        }
                    }
                )
                DateRangePicker(
                    state = pickerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    title = null,
                    headline = null,
                    showModeToggle = true
                )
            }
        }
    }
}

@Composable
private fun StatisticsDateRangeHeader(
    isValid: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val compact = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
        if (compact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.statistics_date_picker_title),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateRangeCancelButton(onDismiss)
                    DateRangeConfirmButton(isValid, onConfirm)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateRangeCancelButton(onDismiss)
                Text(
                    text = stringResource(R.string.statistics_date_picker_title),
                    style = MaterialTheme.typography.titleMedium
                )
                DateRangeConfirmButton(isValid, onConfirm)
            }
        }
    }
}

@Composable
private fun DateRangeCancelButton(onDismiss: () -> Unit) {
    TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(stringResource(R.string.statistics_cancel))
    }
}

@Composable
private fun DateRangeConfirmButton(isValid: Boolean, onConfirm: () -> Unit) {
    TextButton(
        enabled = isValid,
        onClick = onConfirm,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(stringResource(R.string.statistics_confirm))
    }
}
