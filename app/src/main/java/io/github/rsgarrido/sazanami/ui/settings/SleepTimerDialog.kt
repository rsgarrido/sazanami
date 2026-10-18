package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.state.SLEEP_TIMER_OPTIONS_MINUTES

@Composable
fun SleepTimerDialog(
    isTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onStartTimerClick: (Int) -> Unit,
    onCancelTimerClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.sleep_timer_title))
        },
        text = {
            // AlertDialog constrains this slot; keep the choices reachable in short windows while
            // its existing dismiss button stays outside the scrolling content.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text = sleepTimerDisplayText)

                SLEEP_TIMER_OPTIONS_MINUTES.forEach { minutes ->
                    Button(
                        onClick = {
                            onStartTimerClick(minutes)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(text = pluralStringResource(R.plurals.sleep_timer_minutes_option, minutes, minutes))
                    }
                }

                if (isTimerActive) {
                    OutlinedButton(
                        onClick = {
                            onCancelTimerClick()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(text = stringResource(R.string.sleep_timer_cancel))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    )
}
