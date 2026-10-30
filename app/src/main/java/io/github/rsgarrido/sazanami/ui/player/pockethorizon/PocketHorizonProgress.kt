package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.formatDuration
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgress
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.rememberDirectSeekGestureControl
import kotlinx.coroutines.flow.StateFlow

/** Only this leaf collects position ticks and Direct preview; the frame and metadata stay structural. */
@Composable
internal fun PocketHorizonProgress(
    progressState: StateFlow<PlaybackProgressUiState>,
    trackKey: String?,
    enabled: Boolean,
    onSeekChange: (Int) -> Unit,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier
) {
    PlaybackProgress(progressState) { progress ->
        val duration = if (trackKey == null) 0 else progress.duration.coerceAtLeast(0)
        val position = progress.currentPosition.coerceIn(0, duration)
        val canSeek = enabled && duration > 0
        // Disposing the shared adapter retires delayed callbacks and any unfinished Direct gesture.
        key(trackKey, duration, canSeek) {
            Column(modifier.fillMaxWidth()) {
                if (duration > 0) {
                    PocketHorizonSeek(position, duration, canSeek, onSeekChange, style)
                } else {
                    val label = stringResource(R.string.player_track_position)
                    val unavailable = stringResource(R.string.pocket_horizon_seek_unavailable)
                    Box(Modifier.fillMaxWidth().height(PocketHorizonLayoutMetrics.SeekTarget)
                        .testTag("pocketHorizonSeek").semantics {
                            contentDescription = label
                            stateDescription = unavailable
                            disabled()
                        }, contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(3.dp)
                            .background(style.outline.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
                    }
                }
                val unknown = stringResource(R.string.pocket_horizon_time_unavailable)
                val timeStyle = TextStyle(fontFamily = FontFamily.SansSerif,
                    platformStyle = PlatformTextStyle(includeFontPadding = false))
                Row(Modifier.fillMaxWidth().testTag("pocketHorizonTimes").semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (duration > 0) formatDuration(position) else unknown,
                        color = style.secondaryText, style = timeStyle, fontSize = 12.sp,
                        lineHeight = PocketHorizonLayoutMetrics.TimeLineHeight, maxLines = 1)
                    Text(if (duration > 0) formatDuration(duration) else unknown,
                        color = style.secondaryText, style = timeStyle, fontSize = 12.sp,
                        lineHeight = PocketHorizonLayoutMetrics.TimeLineHeight, maxLines = 1)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PocketHorizonSeek(
    position: Int,
    duration: Int,
    enabled: Boolean,
    onSeekChange: (Int) -> Unit,
    style: PocketHorizonStyle
) {
    // The existing adapter owns Direct preview/final commit and normal playback's immediate routing.
    val gesture = rememberDirectSeekGestureControl { if (enabled) onSeekChange(it.coerceIn(0, duration)) }
    gesture.sliderDuration = duration
    var thumbWidth by remember { mutableIntStateOf(0) }
    val reverse = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slider = remember(duration) { SliderState(position.toFloat(), valueRange = 0f..duration.toFloat()) }
    slider.value = position.toFloat()
    slider.onValueChange = { if (enabled) gesture.change(it.toInt()) }
    slider.onValueChangeFinished = { if (enabled) gesture.finish() }
    val interactions = remember(gesture, slider) {
        gesture.interactions(MutableInteractionSource()) { slider.isDragging }
    }
    val label = stringResource(R.string.player_track_position)
    val input = if (enabled) Modifier
        .then(gesture.sliderPointerModifier({ thumbWidth }, reverse))
        .then(gesture.keyboardModifier())
        .semantics {
            setProgress { target ->
                if (!enabled || !target.isFinite()) false else {
                    gesture.immediate(target.coerceIn(0f, duration.toFloat()).toInt())
                    true
                }
            }
        } else Modifier
    val colors = SliderDefaults.colors(
        thumbColor = style.accent, activeTrackColor = style.accent,
        inactiveTrackColor = style.outline.copy(alpha = 0.45f),
        disabledThumbColor = style.disabled, disabledActiveTrackColor = style.disabled,
        disabledInactiveTrackColor = style.outline.copy(alpha = 0.25f)
    )
    Slider(
        state = slider,
        enabled = enabled,
        interactionSource = interactions,
        colors = colors,
        thumb = {
            SliderDefaults.Thumb(interactionSource = interactions, colors = colors, enabled = enabled,
                modifier = Modifier.onSizeChanged { thumbWidth = it.width })
        },
        modifier = Modifier.fillMaxWidth().height(PocketHorizonLayoutMetrics.SeekTarget)
            .testTag("pocketHorizonSeek").then(input).semantics { contentDescription = label }
    )
}
