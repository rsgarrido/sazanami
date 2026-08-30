package io.github.rsgarrido.sazanami.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.player.equalizer.MAX_EQUALIZER_BAND_DB
import io.github.rsgarrido.sazanami.player.equalizer.MIN_EQUALIZER_BAND_DB
import io.github.rsgarrido.sazanami.player.equalizer.normalizeEqualizerDb
import kotlin.math.round

@Composable
internal fun EqualizerBandSlider(
    frequencyHz: Double,
    gainDb: Double,
    unavailable: Boolean,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: (Double) -> Unit,
    onFineEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragStartValue by remember {
        mutableDoubleStateOf(gainDb)
    }
    var accumulatedDragPixels by remember {
        mutableDoubleStateOf(0.0)
    }
    var pendingDragValue by remember {
        mutableDoubleStateOf(gainDb)
    }
    val currentGainDb by rememberUpdatedState(gainDb)
    val activeColor = if (unavailable) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val inactiveColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val zeroColor = thumbColor.copy(alpha = 0.55f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(76.dp)
            .semantics {
                contentDescription =
                    equalizerBandAccessibilityText(
                        frequencyHz,
                        gainDb,
                        unavailable
                    )
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = gainDb.toFloat(),
                    range = MIN_EQUALIZER_BAND_DB.toFloat()..
                        MAX_EQUALIZER_BAND_DB.toFloat(),
                    steps = 47
                )
                setProgress { requested ->
                    if (unavailable) return@setProgress false
                    val normalized = snapBandGain(
                        requested.toDouble()
                    )
                    onValueChange(normalized)
                    onValueChangeFinished(normalized)
                    true
                }
            }
    ) {
        Text(
            text = formatEqualizerFrequency(frequencyHz),
            style = MaterialTheme.typography.labelMedium
        )
        Canvas(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(64.dp)
                .height(210.dp)
                .testTag(
                    equalizerBandDragTargetTag(frequencyHz)
                )
                .pointerInput(unavailable) {
                    if (unavailable) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false
                        )
                        val captured =
                            awaitVerticalTouchSlopOrCancellation(
                                down.id
                            ) { change, overSlop ->
                                change.consume()
                                dragStartValue = currentGainDb
                                pendingDragValue = currentGainDb
                                accumulatedDragPixels =
                                    overSlop.toDouble()
                                val range =
                                    MAX_EQUALIZER_BAND_DB -
                                        MIN_EQUALIZER_BAND_DB
                                val requested = dragStartValue -
                                    accumulatedDragPixels /
                                    size.height * range
                                pendingDragValue =
                                    snapBandGain(requested)
                                onValueChange(pendingDragValue)
                            }
                        if (captured == null) {
                            return@awaitEachGesture
                        }
                        drag(captured.id) { change ->
                            val amount = change.positionChange()
                            if (amount != Offset.Zero) {
                                change.consume()
                                accumulatedDragPixels +=
                                    amount.y.toDouble()
                                val range =
                                    MAX_EQUALIZER_BAND_DB -
                                        MIN_EQUALIZER_BAND_DB
                                val requested = dragStartValue -
                                    accumulatedDragPixels /
                                    size.height * range
                                pendingDragValue =
                                    snapBandGain(requested)
                                onValueChange(pendingDragValue)
                            }
                        }
                        onValueChangeFinished(
                            pendingDragValue
                        )
                    }
                }
        ) {
            val centerX = size.width / 2f
            fun yFor(value: Double): Float {
                val fraction =
                    (MAX_EQUALIZER_BAND_DB - value) /
                        (
                            MAX_EQUALIZER_BAND_DB -
                                MIN_EQUALIZER_BAND_DB
                            )
                return (fraction * size.height).toFloat()
            }
            drawLine(
                color = inactiveColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = activeColor,
                start = Offset(
                    centerX,
                    yFor(gainDb.coerceAtLeast(0.0))
                ),
                end = Offset(
                    centerX,
                    yFor(gainDb.coerceAtMost(0.0))
                ),
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = thumbColor,
                start = Offset(
                    centerX - 16f,
                    yFor(gainDb)
                ),
                end = Offset(
                    centerX + 16f,
                    yFor(gainDb)
                ),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = zeroColor,
                start = Offset(centerX - 12f, yFor(0.0)),
                end = Offset(centerX + 12f, yFor(0.0)),
                strokeWidth = 2f
            )
        }
        TextButton(
            onClick = onFineEditClick,
            enabled = !unavailable
        ) {
            Text(
                text = formatEqualizerDb(gainDb),
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (unavailable) {
            Text(
                text = "Unavailable",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

internal fun snapBandGain(value: Double): Double {
    val clamped = value.coerceIn(
        MIN_EQUALIZER_BAND_DB,
        MAX_EQUALIZER_BAND_DB
    )
    return normalizeEqualizerDb(
        round(clamped * 2.0) / 2.0
    )
}

internal fun equalizerBandDragTargetTag(
    frequencyHz: Double
): String = "graphic-band-drag-${frequencyHz.toInt()}"
