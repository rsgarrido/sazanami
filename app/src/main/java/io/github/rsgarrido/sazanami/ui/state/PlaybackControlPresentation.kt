package io.github.rsgarrido.sazanami.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** Only the Play/Pause action reads intent. Animations and lyrics still use confirmed isPlaying. */
internal val LocalPlaybackPlayPauseIntent = compositionLocalOf<Boolean?> { null }
internal val LocalPlaybackStatus = compositionLocalOf<String?> { null }

@Composable
internal fun playbackPlayPauseShowsPause(confirmedIsPlaying: Boolean): Boolean =
    LocalPlaybackPlayPauseIntent.current ?: confirmedIsPlaying
