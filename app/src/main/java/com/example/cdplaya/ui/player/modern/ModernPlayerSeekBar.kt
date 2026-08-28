package com.example.cdplaya.ui.player.modern

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.player.waveform.WaveformData
import com.example.cdplaya.player.waveform.mapWaveformAmplitudes
import com.example.cdplaya.ui.formatDuration
import kotlin.math.max

@Composable
internal fun ModernPlayerSeekBar(
    currentPosition: Int,
    duration: Int,
    onSeekChange: (Int) -> Unit,
    appearance: ModernSeekbarAppearance,
    waveformSeed: String,
    modifier: Modifier = Modifier,
    waveformData: WaveformData? = null,
    style: ModernPlayerStyle
) {
    val values = resolveModernSeekbarValues(
        currentPosition = currentPosition,
        duration = duration
    )

    Column(modifier = modifier.fillMaxWidth()) {
    val seekbarStyle = appearance.style
    val activeColor = when (appearance.colorMode) {
        ModernSeekbarColorMode.WHITE -> style.contentColor
        ModernSeekbarColorMode.APP_ACCENT -> style.accentColor
    }
    when (seekbarStyle) {
        ModernSeekbarStyle.CLASSIC_BAR -> ClassicSeekbar(
            safePosition = values.sliderPosition,
            safeDuration = values.sliderDuration,
            onSeekChange = onSeekChange,
            activeColor = activeColor,
            style = style
        )

        ModernSeekbarStyle.SLIM_LINE -> VisualSeekbar(
            safePosition = values.sliderPosition,
            safeDuration = values.sliderDuration,
            onSeekChange = onSeekChange,
            thumbSize = 8.dp,
            thumbColor = activeColor
        ) { sliderProgress ->
            RoundedTrack(
                progress = sliderProgress,
                height = 2.dp,
                activeColor = activeColor,
                inactiveColor = style.inactiveTrackColor
            )
        }

        ModernSeekbarStyle.THICK_CAPSULE -> VisualSeekbar(
            safePosition = values.sliderPosition,
            safeDuration = values.sliderDuration,
            onSeekChange = onSeekChange,
            thumbSize = 20.dp,
            thumbColor = activeColor
        ) { sliderProgress ->
            RoundedTrack(
                progress = sliderProgress,
                height = 12.dp,
                activeColor = activeColor,
                inactiveColor = style.inactiveTrackColor
            )
        }

        ModernSeekbarStyle.SEGMENTED -> VisualSeekbar(
            safePosition = values.sliderPosition,
            safeDuration = values.sliderDuration,
            onSeekChange = onSeekChange,
            thumbSize = 1.dp,
            thumbColor = Color.Transparent
        ) { sliderProgress ->
            SegmentedTrack(
                progress = sliderProgress,
                activeColor = activeColor,
                inactiveColor = style.inactiveTrackColor
            )
        }

        ModernSeekbarStyle.WAVEFORM_PREVIEW -> {
            val fallbackBars = remember(waveformSeed, appearance.waveformDensity) {
                generateWaveformPreviewBars(
                    waveformSeed,
                    appearance.waveformDensity.barCount
                )
            }
            val bars = rememberAnimatedWaveformBars(
                fallbackBars = fallbackBars,
                waveformData = waveformData
            )
            VisualSeekbar(
                safePosition = values.sliderPosition,
                safeDuration = values.sliderDuration,
                onSeekChange = onSeekChange,
                thumbSize = 1.dp,
                thumbColor = Color.Transparent
            ) { sliderProgress ->
                WaveformPreviewTrack(
                    progress = sliderProgress,
                    bars = bars,
                    activeColor = activeColor,
                    inactiveColor = style.inactiveTrackColor,
                    waveformSize = appearance.waveformSize,
                    density = appearance.waveformDensity
                )
            }
        }

        ModernSeekbarStyle.WAVEFORM_PEAKS -> {
            val fallbackBars = remember(waveformSeed, appearance.waveformDensity) {
                generateWaveformPeaksBars(
                    waveformSeed,
                    appearance.waveformDensity.barCount
                )
            }
            val bars = rememberAnimatedWaveformBars(
                fallbackBars = fallbackBars,
                waveformData = waveformData
            )
            VisualSeekbar(
                safePosition = values.sliderPosition,
                safeDuration = values.sliderDuration,
                onSeekChange = onSeekChange,
                thumbSize = 1.dp,
                thumbColor = Color.Transparent
            ) { sliderProgress ->
                WaveformPeaksTrack(
                    progress = sliderProgress,
                    bars = bars,
                    activeColor = activeColor,
                    inactiveColor = style.inactiveTrackColor,
                    waveformSize = appearance.waveformSize,
                    density = appearance.waveformDensity
                )
            }
        }

        ModernSeekbarStyle.WAVEFORM_GLOW -> {
            val fallbackBars = remember(waveformSeed, appearance.waveformDensity) {
                generateWaveformGlowBars(
                    waveformSeed,
                    appearance.waveformDensity.barCount
                )
            }
            val bars = rememberAnimatedWaveformBars(
                fallbackBars = fallbackBars,
                waveformData = waveformData
            )
            VisualSeekbar(
                safePosition = values.sliderPosition,
                safeDuration = values.sliderDuration,
                onSeekChange = onSeekChange,
                thumbSize = 1.dp,
                thumbColor = Color.Transparent
            ) { sliderProgress ->
                WaveformGlowTrack(
                    progress = sliderProgress,
                    bars = bars,
                    activeColor = activeColor,
                    inactiveColor = style.inactiveTrackColor,
                    waveformSize = appearance.waveformSize,
                    density = appearance.waveformDensity
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatDuration(values.displayPosition),
            style = MaterialTheme.typography.bodySmall,
            color = style.timeColor
        )

        Text(
            text = formatDuration(values.displayDuration),
            style = MaterialTheme.typography.bodySmall,
            color = style.timeColor
        )
    }
}
}

internal data class ModernSeekbarValues(
    val sliderPosition: Int,
    val sliderDuration: Int,
    val displayPosition: Int,
    val displayDuration: Int
)

internal fun resolveModernSeekbarValues(
    currentPosition: Int,
    duration: Int
): ModernSeekbarValues {
    val displayDuration = duration.coerceAtLeast(0)
    val displayPosition = currentPosition.coerceIn(0, displayDuration)
    return ModernSeekbarValues(
        sliderPosition = displayPosition,
        sliderDuration = displayDuration.coerceAtLeast(1),
        displayPosition = displayPosition,
        displayDuration = displayDuration
    )
}

internal data class ModernSeekbarVerticalLayout(
    val trackTop: Float,
    val trackBottom: Float,
    val timingTop: Float,
    val timingBottom: Float
)

internal fun resolveModernSeekbarVerticalLayout(
    trackHeightPx: Float,
    timingHeightPx: Float,
    spacingPx: Float = 0f
): ModernSeekbarVerticalLayout {
    val safeTrackHeight = trackHeightPx.coerceAtLeast(0f)
    val safeTimingHeight = timingHeightPx.coerceAtLeast(0f)
    val safeSpacing = spacingPx.coerceAtLeast(0f)
    return ModernSeekbarVerticalLayout(
        trackTop = 0f,
        trackBottom = safeTrackHeight,
        timingTop = safeTrackHeight + safeSpacing,
        timingBottom = safeTrackHeight + safeSpacing + safeTimingHeight
    )
}

@Composable
private fun rememberAnimatedWaveformBars(
    fallbackBars: List<Float>,
    waveformData: WaveformData?
): List<Float> {
    val realBars = remember(
        waveformData?.sourceKey,
        waveformData?.amplitudes,
        fallbackBars.size
    ) {
        waveformData?.amplitudes
            ?.let { amplitudes -> mapWaveformAmplitudes(amplitudes, fallbackBars.size) }
            ?.takeIf(List<Float>::isNotEmpty)
    }
    val realWaveformBlend by animateFloatAsState(
        targetValue = if (realBars == null) 0f else 1f,
        animationSpec = tween(durationMillis = REAL_WAVEFORM_TRANSITION_MILLIS),
        label = "realWaveformBlend"
    )

    return remember(fallbackBars, realBars, realWaveformBlend) {
        blendWaveformBars(
            fallbackBars = fallbackBars,
            realBars = realBars,
            blend = realWaveformBlend
        )
    }
}

@Composable
private fun ClassicSeekbar(
    safePosition: Int,
    safeDuration: Int,
    onSeekChange: (Int) -> Unit,
    activeColor: Color,
    style: ModernPlayerStyle
) {
    Slider(
        value = safePosition.toFloat(),
        onValueChange = { newPosition ->
            onSeekChange(newPosition.toInt())
        },
        valueRange = 0f..safeDuration.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = style.inactiveTrackColor
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VisualSeekbar(
    safePosition: Int,
    safeDuration: Int,
    onSeekChange: (Int) -> Unit,
    thumbSize: Dp,
    thumbColor: Color,
    track: @Composable (Float) -> Unit
) {
    Slider(
        value = safePosition.toFloat(),
        onValueChange = { newPosition ->
            onSeekChange(newPosition.toInt())
        },
        valueRange = 0f..safeDuration.toFloat(),
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .background(thumbColor, CircleShape)
            )
        },
        track = { sliderState ->
            track((sliderState.value / safeDuration).coerceIn(0f, 1f))
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RoundedTrack(
    progress: Float,
    height: Dp,
    activeColor: Color,
    inactiveColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = inactiveColor,
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = activeColor,
            size = Size(size.width * progress, size.height),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}

@Composable
private fun SegmentedTrack(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    segmentCount: Int = 24
) {
    val fills = segmentedFillFractions(progress, segmentCount)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val gap = 3.dp.toPx()
        val segmentWidth = max(1f, (size.width - gap * (segmentCount - 1)) / segmentCount)
        val radius = size.height / 2f

        fills.forEachIndexed { index, fill ->
            val left = index * (segmentWidth + gap)
            drawRoundRect(
                color = inactiveColor,
                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(radius, radius)
            )
            if (fill > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = Size(segmentWidth * fill, size.height),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }
    }
}

@Composable
private fun WaveformPreviewTrack(
    progress: Float,
    bars: List<Float>,
    activeColor: Color,
    inactiveColor: Color,
    waveformSize: ModernWaveformSize,
    density: ModernWaveformDensity
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(waveformSize.trackHeightDp.dp)
    ) {
        if (bars.isEmpty()) return@Canvas

        val gap = density.gapDp.dp.toPx()
        drawCenteredWaveformBars(bars, gap, inactiveColor)
        clipRect(right = size.width * progress) {
            drawCenteredWaveformBars(bars, gap, activeColor)
        }
    }
}

@Composable
private fun WaveformPeaksTrack(
    progress: Float,
    bars: List<Float>,
    activeColor: Color,
    inactiveColor: Color,
    waveformSize: ModernWaveformSize,
    density: ModernWaveformDensity
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(waveformSize.trackHeightDp.dp)
    ) {
        if (bars.isEmpty()) return@Canvas

        val gap = density.gapDp.dp.toPx()
        val centerGap = 1.dp.toPx()
        drawRoundRect(
            color = inactiveColor.copy(alpha = inactiveColor.alpha * 0.55f),
            topLeft = Offset(0f, size.height / 2f - 0.5.dp.toPx()),
            size = Size(size.width, 1.dp.toPx()),
            cornerRadius = CornerRadius(0.5.dp.toPx())
        )
        drawMirroredWaveformBars(
            bars = bars,
            gap = gap,
            centerGap = centerGap,
            color = inactiveColor.copy(alpha = inactiveColor.alpha * 0.8f)
        )
        clipRect(right = size.width * progress) {
            drawMirroredWaveformBars(
                bars = bars,
                gap = gap,
                centerGap = centerGap,
                color = activeColor
            )
        }
    }
}

@Composable
private fun WaveformGlowTrack(
    progress: Float,
    bars: List<Float>,
    activeColor: Color,
    inactiveColor: Color,
    waveformSize: ModernWaveformSize,
    density: ModernWaveformDensity
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(waveformSize.trackHeightDp.dp)
    ) {
        if (bars.isEmpty()) return@Canvas

        val gap = density.gapDp.dp.toPx()
        drawCenteredWaveformBars(
            bars = bars,
            gap = gap,
            color = inactiveColor.copy(alpha = inactiveColor.alpha * 0.55f)
        )
        clipRect(right = size.width * progress) {
            drawCenteredWaveformBars(
                bars = bars,
                gap = gap,
                color = activeColor.copy(alpha = 0.16f),
                widthExpansion = 1.5.dp.toPx(),
                heightExpansion = 2.dp.toPx()
            )
            drawCenteredWaveformBars(
                bars = bars,
                gap = gap,
                color = activeColor.copy(alpha = 0.88f)
            )
        }
    }
}

private fun DrawScope.drawCenteredWaveformBars(
    bars: List<Float>,
    gap: Float,
    color: Color,
    widthExpansion: Float = 0f,
    heightExpansion: Float = 0f
) {
    val barWidth = max(1f, (size.width - gap * (bars.size - 1)) / bars.size)
    bars.forEachIndexed { index, amplitude ->
        val baseHeight = size.height * amplitude
        val barHeight = (baseHeight + heightExpansion * 2f).coerceAtMost(size.height)
        val left = index * (barWidth + gap) - widthExpansion
        val top = (size.height - barHeight) / 2f
        val drawnWidth = barWidth + widthExpansion * 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(drawnWidth, barHeight),
            cornerRadius = CornerRadius(drawnWidth / 2f)
        )
    }
}

private fun DrawScope.drawMirroredWaveformBars(
    bars: List<Float>,
    gap: Float,
    centerGap: Float,
    color: Color
) {
    val barWidth = max(1f, (size.width - gap * (bars.size - 1)) / bars.size)
    val centerY = size.height / 2f
    val maximumHalfHeight = centerY - centerGap
    bars.forEachIndexed { index, amplitude ->
        val halfHeight = max(barWidth, maximumHalfHeight * amplitude)
        val left = index * (barWidth + gap)
        val radius = barWidth / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, centerY - centerGap - halfHeight),
            size = Size(barWidth, halfHeight),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(left, centerY + centerGap),
            size = Size(barWidth, halfHeight),
            cornerRadius = CornerRadius(radius)
        )
    }
}

internal fun segmentedFillFractions(
    progress: Float,
    segmentCount: Int
): List<Float> {
    if (segmentCount <= 0) return emptyList()

    val filledAmount = progress.coerceIn(0f, 1f) * segmentCount
    return List(segmentCount) { index ->
        (filledAmount - index).coerceIn(0f, 1f)
    }
}

internal fun blendWaveformBars(
    fallbackBars: List<Float>,
    realBars: List<Float>?,
    blend: Float
): List<Float> {
    if (realBars == null || realBars.size != fallbackBars.size) return fallbackBars

    val fraction = blend.coerceIn(0f, 1f)
    return fallbackBars.indices.map { index ->
        fallbackBars[index] + (realBars[index] - fallbackBars[index]) * fraction
    }
}

internal fun generateWaveformPreviewBars(
    seed: String,
    barCount: Int = 48
): List<Float> {
    return generateDeterministicWaveformBars(
        seed = seed,
        barCount = barCount,
        minimumAmplitude = 0.24f,
        maximumAmplitude = 0.96f,
        contourDepth = 0.18f
    )
}

internal fun generateWaveformPeaksBars(seed: String, barCount: Int = 42): List<Float> {
    return generateDeterministicWaveformBars(
        seed = "$seed|peaks",
        barCount = barCount,
        minimumAmplitude = 0.12f,
        maximumAmplitude = 1f,
        contourDepth = 0.12f
    )
}

internal fun generateWaveformGlowBars(seed: String, barCount: Int = 72): List<Float> {
    return generateDeterministicWaveformBars(
        seed = "$seed|glow",
        barCount = barCount,
        minimumAmplitude = 0.2f,
        maximumAmplitude = 0.78f,
        contourDepth = 0.22f
    )
}

private fun generateDeterministicWaveformBars(
    seed: String,
    barCount: Int,
    minimumAmplitude: Float,
    maximumAmplitude: Float,
    contourDepth: Float
): List<Float> {
    if (barCount <= 0) return emptyList()

    var state = seed.fold(0x811C9DC5u) { hash, character ->
        (hash xor character.code.toUInt()) * 0x01000193u
    }
    if (state == 0u) state = 0x6D2B79F5u

    return List(barCount) { index ->
        state = state xor (state shl 13)
        state = state xor (state shr 17)
        state = state xor (state shl 5)
        val randomUnit = (state and 0xFFFFu).toFloat() / 0xFFFFu.toFloat()
        val contour = 1f - contourDepth + ((index % 7) / 6f) * contourDepth
        (minimumAmplitude + randomUnit * (maximumAmplitude - minimumAmplitude) * contour)
            .coerceIn(minimumAmplitude, maximumAmplitude)
    }
}

private const val REAL_WAVEFORM_TRANSITION_MILLIS = 320
