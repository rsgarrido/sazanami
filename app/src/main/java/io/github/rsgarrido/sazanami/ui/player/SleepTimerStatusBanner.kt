package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SleepTimerStatusBanner(
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isSleepTimerActive) {
        return
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null
            )
        },
        headlineContent = {
            Text(text = "Sleep timer active")
        },
        supportingContent = {
            Text(text = sleepTimerDisplayText)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable {
                onSleepTimerClick()
            },
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    )
}