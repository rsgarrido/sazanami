package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

/** Consumes an already inset-adjusted, bounded host; it neither probes devices nor changes system bars. */
@Composable
internal fun PocketHorizonShell(
    modifier: Modifier = Modifier,
    style: PocketHorizonStyle = PocketHorizonStyle.Default,
    measurements: PocketHorizonEndpointMeasurements? = null,
    leftControls: @Composable () -> Unit = {},
    rightControls: @Composable () -> Unit = {},
    leftShoulder: @Composable () -> Unit = {},
    rightShoulder: @Composable () -> Unit = {},
    systemControls: @Composable () -> Unit = {},
    content: @Composable (PocketHorizonLayout) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val layout = resolvePocketHorizonLayout(DpSize(maxWidth, maxHeight), fontScale)
            ?: return@BoxWithConstraints
        val shellShape = PocketHorizonUpperHousingShape(layout.bezelInset + layout.controlWing)
        Box(
            Modifier.size(layout.shellSize.width, layout.shellSize.height)
                .horizonAnchor(measurements, PocketHorizonAnchor.Surface)
                .testTag("pocketHorizonShell")
        ) {
            // Rear caps and their unchanged touch targets sit behind the real front housing.
            // The housing has no pointer input, so its visual overlap cannot swallow bumper taps.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxSize().padding(horizontal = layout.bezelInset)) {
                    Box(Modifier.width(layout.controlWing).height(layout.shoulderBandHeight + layout.bezelInset)
                        .padding(start = 16.dp, end = 8.dp)) { leftShoulder() }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(layout.controlWing).height(layout.shoulderBandHeight + layout.bezelInset)
                        .padding(start = 8.dp, end = 16.dp)) { rightShoulder() }
                }
            }
            Box(Modifier.matchParentSize().testTag("horizonFrontHousing").shadow(12.dp, shellShape)
                .clip(shellShape)
                .background(Brush.verticalGradient(listOf(style.shellHighlight, style.shell, style.bezel)))
                .border(1.dp, style.shellHighlight, shellShape))
            Box(Modifier.fillMaxSize().padding(start = layout.bezelInset, end = layout.bezelInset, bottom = layout.bezelInset)) {
                val displayDirection = LocalLayoutDirection.current
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(layout.controlWing).fillMaxHeight().testTag("horizonLeftWing")) {
                            Spacer(Modifier.height(layout.shoulderBandHeight + layout.bezelInset))
                            Box(Modifier.weight(1f)) { leftControls() }
                        }
                        // Restore the existing LCD/strip origin; only the caps use the top-edge space.
                        Column(Modifier.weight(1f).fillMaxHeight().padding(top = layout.bezelInset)) {
                            Box(Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides displayDirection) {
                                    PocketHorizonDisplay(layout, style, measurements) { content(layout) }
                                }
                            }
                            Box(Modifier.height(layout.hardwareStripHeight).testTag("horizonHardwareStrip")) { systemControls() }
                        }
                        Column(Modifier.width(layout.controlWing).fillMaxHeight().testTag("horizonRightWing")) {
                            Spacer(Modifier.height(layout.shoulderBandHeight + layout.bezelInset))
                            Box(Modifier.weight(1f)) { rightControls() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketHorizonDisplay(
    layout: PocketHorizonLayout,
    style: PocketHorizonStyle,
    measurements: PocketHorizonEndpointMeasurements?,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxSize().horizonAnchor(measurements, PocketHorizonAnchor.Display)
            .testTag("pocketHorizonDisplay").clip(shape)
            .shadow(4.dp, shape, clip = false).background(style.bezel).border(2.dp, style.outline.copy(alpha = 0.45f), shape)
    ) {
        PocketHorizonBackdrop(style, Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().padding(layout.displayPadding)) { content() }
    }
}

/** Original, static light fields and a low horizon. Drawing has no semantics or pointer handlers. */
@Composable
internal fun PocketHorizonBackdrop(style: PocketHorizonStyle, modifier: Modifier = Modifier) {
    Box(modifier.drawWithCache {
        val base = Brush.verticalGradient(listOf(style.displayTop, style.displayBottom))
        val glow = Brush.radialGradient(
            listOf(style.horizon.copy(alpha = 0.50f), Color.Transparent),
            center = Offset(size.width * 0.18f, size.height * 1.04f),
            radius = size.width.coerceAtLeast(1f) * 0.9f
        )
        val horizon = Path().apply {
            moveTo(0f, size.height * 0.78f)
            cubicTo(size.width * 0.34f, size.height * 0.50f,
                size.width * 0.62f, size.height * 0.98f, size.width, size.height * 0.63f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        onDrawBehind {
            drawRect(base)
            drawRect(glow)
            drawPath(horizon, style.horizon.copy(alpha = 0.12f))
        }
    })
}
