package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Consumes an already inset-adjusted, bounded host; it neither probes devices nor changes system bars. */
@Composable
internal fun PocketHorizonShell(
    modifier: Modifier = Modifier,
    style: PocketHorizonStyle = PocketHorizonStyle.Default,
    content: @Composable (PocketHorizonLayout) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val layout = resolvePocketHorizonLayout(DpSize(maxWidth, maxHeight), fontScale)
            ?: return@BoxWithConstraints
        val shellShape = RoundedCornerShape(32.dp)
        Box(
            Modifier.size(layout.shellSize.width, layout.shellSize.height)
                .testTag("pocketHorizonShell")
                .shadow(8.dp, shellShape)
                .clip(shellShape)
                .background(Brush.verticalGradient(listOf(style.shellHighlight, style.shell, style.bezel)))
                .border(1.dp, style.shellHighlight, shellShape)
                .padding(layout.bezelInset)
        ) {
            PocketHorizonDisplay(layout, style) { content(layout) }
        }
    }
}

@Composable
private fun PocketHorizonDisplay(
    layout: PocketHorizonLayout,
    style: PocketHorizonStyle,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxSize().testTag("pocketHorizonDisplay").clip(shape)
            .background(style.bezel).border(1.dp, style.outline.copy(alpha = 0.45f), shape)
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
