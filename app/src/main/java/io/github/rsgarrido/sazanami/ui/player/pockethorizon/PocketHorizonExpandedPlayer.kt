package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.state.LocalPlaybackStatus
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.resolve
import kotlinx.coroutines.flow.StateFlow

/**
 * Standalone presentation only. No resolver/host registration, playback owner, or collapse simulation.
 * A future host supplies its existing callbacks, seek environment and overlay/input capabilities.
 */
@Composable
internal fun PocketHorizonExpandedPlayer(
    playbackState: PlaybackUiState,
    progressState: StateFlow<PlaybackProgressUiState>,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
    seekEnabled: Boolean = false,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onOpenQueueHubClick: (() -> Unit)? = null,
    onOpenMoreClick: (() -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null,
    onViewArtwork: ((Song) -> Unit)? = null,
    style: PocketHorizonStyle = PocketHorizonStyle.Default
) {
    val enabled = inputEnabled && playbackState.isConnected && playbackState.currentSong != null
    PocketHorizonShell(modifier, style) { layout ->
        // Scroll within the clipped display, never by reducing type or target sizes. On fitting hosts
        // this plane exactly matches the viewport; below budget it becomes a readable stacked view.
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(layout.contentWidth).verticalScroll(rememberScrollState())
                .heightIn(min = layout.contentViewportSize.height)) {
                val actions: @Composable () -> Unit = {
                    PocketHorizonActions(enabled, onOpenQueueHubClick, onOpenMoreClick, onOpenLyrics, style)
                }
                if (layout.isWide) {
                    Row(Modifier.fillMaxWidth().heightIn(min = layout.headerHeight),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PocketHorizonStatus(playbackState, style, Modifier.weight(1f))
                        actions()
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PocketHorizonStatus(playbackState, style, Modifier.heightIn(min = layout.headerHeight))
                        actions()
                    }
                }
                // The status/action band and this gap reserve a real header budget for future categories.
                Spacer(Modifier.height(PocketHorizonLayoutMetrics.HeaderGap))
                val bodyHeight = maxOf(layout.bodyMinHeight,
                    layout.contentViewportSize.height - layout.headerHeight - PocketHorizonLayoutMetrics.HeaderGap)
                PocketHorizonNowPlaying(
                    playbackState = playbackState,
                    progressState = progressState,
                    layout = layout,
                    enabled = enabled,
                    seekEnabled = seekEnabled,
                    previousEnabled = previousEnabled,
                    nextEnabled = nextEnabled,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    onViewArtwork = onViewArtwork,
                    style = style,
                    modifier = Modifier.heightIn(min = bodyHeight)
                )
            }
        }
    }
}

@Composable
private fun PocketHorizonStatus(playbackState: PlaybackUiState, style: PocketHorizonStyle, modifier: Modifier) {
    val status = LocalPlaybackStatus.current ?: playbackState.playbackStatus?.resolve()
        ?: stringResource(when {
            !playbackState.isConnected -> R.string.pocket_horizon_disconnected
            playbackState.currentSong == null -> R.string.pocket_horizon_no_track
            playbackState.isPlaying -> R.string.pocket_horizon_playing
            else -> R.string.pocket_horizon_paused
        })
    val typography = TextStyle(fontFamily = FontFamily.SansSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    Column(modifier.testTag("pocketHorizonStatus").semantics(mergeDescendants = true) {}) {
        Text(stringResource(R.string.app_name) + " / " + stringResource(R.string.tablet_landscape_theme_pocket_horizon),
            color = style.secondaryText, style = typography, fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, color = style.text, style = typography, fontSize = 14.sp, lineHeight = 22.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
