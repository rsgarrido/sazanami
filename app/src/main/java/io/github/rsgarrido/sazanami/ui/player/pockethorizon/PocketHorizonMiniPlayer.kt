package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerCallbacks
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerState
import io.github.rsgarrido.sazanami.ui.player.mini.normalizedMiniPlayerProgress
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause

/** Independent compact endpoint. Caller retains visibility, playback, expansion and gesture ownership.
 * Progress is read-only, matching MiniPlayerState's contract: no compact seek API is supplied.
 * The optional gesture modifier belongs only to the expand region, never transport buttons.
 */
@Composable
internal fun PocketHorizonMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
    playbackEnabled: Boolean = true,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    expandGestureModifier: Modifier = Modifier,
    measurements: PocketHorizonEndpointMeasurements? = null,
    style: PocketHorizonStyle = PocketHorizonStyle.Default
) {
    val contentKey = pocketHorizonMeasurementKey(state.currentSong, LocalLayoutDirection.current)
    val reports = measurements?.takeIf { it.contentKey == contentKey }
    DisposableEffect(measurements, contentKey) { onDispose { measurements?.clear() } }
    // A changed input lifetime removes in-flight clicks/host gestures before controls can fire.
    key(inputEnabled, playbackEnabled, reports) {
        BoxWithConstraints(modifier.fillMaxWidth()
            .padding(horizontal = PocketHorizonMiniMetrics.HorizontalMargin,
                vertical = PocketHorizonMiniMetrics.VerticalMargin)) {
            val layout = resolvePocketHorizonMiniLayout(maxWidth, LocalDensity.current.fontScale, maxHeight)
            if (layout == null) {
                SideEffect { reports?.clear() }
                return@BoxWithConstraints
            }
            val title = state.currentSong.title.takeIf(String::isNotBlank)
                ?: stringResource(R.string.player_unknown_title)
            val artist = state.currentSong.artist.takeIf(String::isNotBlank)
                ?: stringResource(R.string.player_unknown_artist)
            val shape = RoundedCornerShape(18.dp)
            Box(Modifier.fillMaxWidth().height(layout.surfaceHeight)
                .testTag("pocketHorizonMini")
                .horizonAnchor(reports, PocketHorizonAnchor.Surface)
                .clip(shape).background(style.shell).border(1.dp, style.shellHighlight, shape)) {
                Box(Modifier.fillMaxSize().testTag("pocketHorizonMiniDisplay")
                    .horizonAnchor(reports, PocketHorizonAnchor.Display)
                    .background(Brush.horizontalGradient(listOf(style.displayTop, style.displayBottom))))
                Row(Modifier.fillMaxSize().padding(PocketHorizonMiniMetrics.Padding),
                    horizontalArrangement = Arrangement.spacedBy(PocketHorizonMiniMetrics.Gap),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).height(layout.contentHeight)
                        .horizonAnchor(reports, PocketHorizonAnchor.GestureRegion)
                        .testTag("pocketHorizonMiniExpand")
                        .then(if (inputEnabled) expandGestureModifier else Modifier)
                        .clickable(enabled = inputEnabled, role = Role.Button,
                            onClickLabel = stringResource(R.string.player_open_for, title)) {
                            if (inputEnabled) callbacks.onExpandClick()
                        }, horizontalArrangement = Arrangement.spacedBy(PocketHorizonMiniMetrics.Gap),
                        verticalAlignment = Alignment.CenterVertically) {
                        PocketHorizonArtwork(state.currentSong, PocketHorizonMiniMetrics.Artwork, false, null, style,
                            modifier = Modifier.horizonAnchor(reports, PocketHorizonAnchor.Artwork),
                            requestSize = 128, tag = "pocketHorizonMiniArtwork")
                        val typography = TextStyle(fontFamily = FontFamily.SansSerif,
                            platformStyle = PlatformTextStyle(includeFontPadding = false))
                        Column(Modifier.weight(1f).testTag("pocketHorizonMiniMetadata")
                            .horizonAnchor(reports, PocketHorizonAnchor.Metadata),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, Modifier.testTag("pocketHorizonMiniTitle"), color = style.text,
                                style = typography, fontSize = 14.sp, lineHeight = 20.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (layout.showArtist) Text(artist, Modifier.testTag("pocketHorizonMiniArtist"),
                                color = style.secondaryText, style = typography, fontSize = 12.sp, lineHeight = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (layout.showTrackButtons) MiniAction(Icons.Filled.SkipPrevious,
                        stringResource(R.string.player_previous_song), inputEnabled && playbackEnabled && previousEnabled,
                        callbacks.onPreviousClick, "pocketHorizonMiniPrevious", style)
                    val showsPause = playbackPlayPauseShowsPause(state.isPlaying)
                    MiniAction(if (showsPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (showsPause) R.string.player_pause else R.string.player_play),
                        inputEnabled && playbackEnabled, callbacks.onPlayPauseClick, "pocketHorizonMiniPlayPause", style,
                        Modifier.horizonAnchor(reports, PocketHorizonAnchor.PlayPause), prominent = true)
                    if (layout.showTrackButtons) MiniAction(Icons.Filled.SkipNext,
                        stringResource(R.string.player_next_song), inputEnabled && playbackEnabled && nextEnabled,
                        callbacks.onNextClick, "pocketHorizonMiniNext", style)
                }
                val progress = normalizedMiniPlayerProgress(state.currentPosition, state.duration)
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val progressLabel = stringResource(R.string.player_track_position)
                Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                    .horizonAnchor(reports, PocketHorizonAnchor.ProgressRegion)
                    .testTag("pocketHorizonMiniProgress").semantics {
                        contentDescription = progressLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    }) {
                    drawRect(style.outline.copy(alpha = .35f))
                    drawRect(style.accent, topLeft = androidx.compose.ui.geometry.Offset(
                        if (rtl) size.width * (1f - progress) else 0f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width * progress, size.height))
                }
            }
        }
    }
}

@Composable
private fun MiniAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit,
    tag: String, style: PocketHorizonStyle, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Box(modifier.size(PocketHorizonMiniMetrics.TouchTarget).testTag(tag)
        .background(if (prominent && enabled) style.accent else style.surface.copy(alpha = .6f), CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { if (enabled) onClick() }
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (!enabled) style.disabled else if (prominent) style.onAccent else style.text,
            modifier = Modifier.size(24.dp))
    }
}
