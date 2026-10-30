package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause

@Composable
internal fun PocketHorizonTransport(
    isPlaying: Boolean,
    playPauseIntent: Boolean?,
    enabled: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier,
    measurements: PocketHorizonEndpointMeasurements? = null
) {
    // The route's shared command-intent local has priority; isolated callers can supply its state value.
    val showsPause = playbackPlayPauseShowsPause(playPauseIntent ?: isPlaying)
    Row(modifier.height(PocketHorizonLayoutMetrics.ControlHeight),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PocketHorizonButton(Icons.Filled.SkipPrevious, stringResource(R.string.player_previous_song),
            enabled && previousEnabled, onPreviousClick, "pocketHorizonPrevious", style)
        PocketHorizonButton(if (showsPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (showsPause) R.string.player_pause else R.string.player_play),
            enabled, onPlayPauseClick, "pocketHorizonPlayPause", style, prominent = true,
            modifier = Modifier.horizonAnchor(measurements, PocketHorizonAnchor.PlayPause))
        PocketHorizonButton(Icons.Filled.SkipNext, stringResource(R.string.player_next_song),
            enabled && nextEnabled, onNextClick, "pocketHorizonNext", style)
    }
}

/** Missing callbacks omit actions entirely. Overlay guards remain with the caller at future integration. */
@Composable
internal fun PocketHorizonActions(
    enabled: Boolean,
    onOpenQueueHubClick: (() -> Unit)?,
    onOpenMoreClick: (() -> Unit)?,
    onOpenLyrics: (() -> Unit)?,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier
) {
    Row(modifier.testTag("pocketHorizonActions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onOpenQueueHubClick?.let {
            PocketHorizonButton(Icons.Filled.QueueMusic, stringResource(R.string.player_open_queues),
                enabled, it, "pocketHorizonQueue", style)
        }
        onOpenLyrics?.let {
            PocketHorizonButton(Icons.AutoMirrored.Filled.Notes, stringResource(R.string.player_open_lyrics),
                enabled, it, "pocketHorizonLyrics", style)
        }
        onOpenMoreClick?.let {
            PocketHorizonButton(Icons.Filled.MoreHoriz, stringResource(R.string.player_more_actions),
                enabled, it, "pocketHorizonMore", style)
        }
    }
}

@Composable
private fun PocketHorizonButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    style: PocketHorizonStyle,
    prominent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = if (prominent) CircleShape else RoundedCornerShape(16.dp)
    Box(
        modifier.size(if (prominent) PocketHorizonLayoutMetrics.ControlHeight else PocketHorizonLayoutMetrics.MinimumTouchTarget)
            .testTag(tag).background(if (prominent && enabled) style.accent else style.surface.copy(alpha = 0.65f), shape)
            .border(1.dp, style.outline.copy(alpha = 0.40f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { if (enabled) onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null,
            tint = when { !enabled -> style.disabled; prominent -> style.onAccent; else -> style.text },
            modifier = Modifier.size(if (prominent) 32.dp else 24.dp))
    }
}
