package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow

/** Android Studio inspection without an activity, live theme registration, or fabricated track. */
@Preview(name = "Pocket Horizon — empty", widthDp = 1024, heightDp = 640, showBackground = true)
@Preview(name = "Pocket Horizon — large text", widthDp = 1000, heightDp = 720, fontScale = 2f)
@Composable
private fun PocketHorizonEmptyPreview() {
    val progress = remember { MutableStateFlow(PlaybackProgressUiState.Empty) }
    MaterialTheme {
        PocketHorizonExpandedPlayer(PlaybackUiState.Disconnected, progress,
            onPlayPauseClick = {}, onPreviousClick = {}, onNextClick = {}, onSeekChange = {})
    }
}
