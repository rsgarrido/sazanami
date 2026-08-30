package io.github.rsgarrido.sazanami.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Restarts only this narrow subtree for high-frequency playback position updates. */
@Composable
fun PlaybackProgress(
    progressState: StateFlow<PlaybackProgressUiState>,
    content: @Composable (PlaybackProgressUiState) -> Unit
) {
    val progress by progressState.collectAsStateWithLifecycle()
    content(progress)
}
