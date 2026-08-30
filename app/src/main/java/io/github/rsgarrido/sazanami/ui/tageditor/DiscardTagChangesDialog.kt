package io.github.rsgarrido.sazanami.ui.tageditor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun DiscardTagChangesDialog(
    onDismiss: () -> Unit,
    onConfirmDiscardClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Discard changes?")
        },
        text = {
            Text(
                text = "You have unsaved tag or artwork changes. If you leave now, those edits will be lost."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDiscardClick
            ) {
                Text(text = "Discard")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(text = "Keep Editing")
            }
        }
    )
}