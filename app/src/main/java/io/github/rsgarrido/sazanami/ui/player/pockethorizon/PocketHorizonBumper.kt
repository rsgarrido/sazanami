package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mechanical dimensions only. Hit targets and playback ownership remain with ConsoleButton. */
internal object PocketHorizonBumperMetrics {
    val CapHeight = 32.dp
    val UpperFaceHeight = 20.dp
    val RestTop = 2.dp
    val Travel = 4.dp
    val FrontEdgeDrop = 28.dp
    val UpperCornerRadius = 40.dp
}

/** Broad upper face, swept outer end, and a tapered front edge; physical side ignores RTL. */
internal fun pocketHorizonBumperCapPath(size: Size, left: Boolean): Path {
    val w = size.width
    val h = size.height
    fun x(fraction: Float) = w * if (left) fraction else 1f - fraction
    return Path().apply {
        moveTo(x(.02f), h * .65f)
        cubicTo(x(.04f), h * .30f, x(.12f), 0f, x(.24f), 0f)
        lineTo(x(.91f), 0f)
        quadraticTo(x(.99f), 0f, x(1f), h * .22f)
        lineTo(x(.97f), h * .80f)
        quadraticTo(x(.94f), h, x(.85f), h)
        lineTo(x(.15f), h * .92f)
        quadraticTo(x(0f), h * .85f, x(.02f), h * .65f)
        close()
    }
}

private class HorizonBumperCapShape(private val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(pocketHorizonBumperCapPath(size, left))
}

/**
 * Only the upper wing edge is cut back. The central display and every measured body bound retain
 * their existing positions. This continuous front contour, painted after the caps, is their housing.
 * The shallow curved wing edge replaces independent rectangular platforms beneath the bumpers.
 */
internal class PocketHorizonUpperHousingShape(
    private val wingEdge: Dp,
    private val shoulderScale: Float = 1f,
    private val bottomCorner: Dp = 64.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val scale = shoulderScale.coerceIn(0f, 1f)
        val drop = with(density) { PocketHorizonBumperMetrics.FrontEdgeDrop.toPx() * scale }.coerceAtMost(size.height / 4f)
        val radius = with(density) { bottomCorner.toPx() }.coerceAtMost(minOf(size.width, size.height) / 4f)
        val wing = with(density) { wingEdge.toPx() }.coerceIn(0f, size.width / 2f)
        val upper = with(density) { PocketHorizonBumperMetrics.UpperCornerRadius.toPx() * scale }
            .coerceAtMost(minOf(wing * .35f, size.height / 4f))
        val arc = radius * .5522848f
        return Outline.Generic(Path().apply {
            // A smooth shoulder saddle joins the outer corner to the central bezel. There is no
            // horizontal ledge or square-ended lip at either end of the cap.
            moveTo(0f, drop + upper)
            cubicTo(0f, drop + upper * .4f, upper * .4f, drop * 1.14f, upper, drop * 1.14f)
            cubicTo(upper + (wing - upper) * .25f, drop * 1.14f, wing * .65f, drop * .93f, wing * .8f, drop)
            cubicTo(wing * .9f, drop * (1f + .07f * 2f / 3f), wing * .92f, drop, wing * .94f, drop * .7f)
            cubicTo(wing * .96f, drop * .4f, wing * .96f, 0f, wing, 0f)
            lineTo(size.width - wing, 0f)
            cubicTo(size.width - wing * .96f, 0f, size.width - wing * .96f, drop * .4f, size.width - wing * .94f, drop * .7f)
            cubicTo(size.width - wing * .92f, drop, size.width - wing * .9f, drop * (1f + .07f * 2f / 3f), size.width - wing * .8f, drop)
            cubicTo(size.width - wing * .65f, drop * .93f, size.width - upper - (wing - upper) * .25f, drop * 1.14f, size.width - upper, drop * 1.14f)
            cubicTo(size.width - upper * .4f, drop * 1.14f, size.width, drop + upper * .4f, size.width, drop + upper)
            lineTo(size.width, size.height - radius)
            cubicTo(size.width, size.height - radius + arc, size.width - radius + arc, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            cubicTo(radius - arc, size.height, 0f, size.height - radius + arc, 0f, size.height - radius)
            close()
        })
    }
}

/** No input, capability state, or callback lives in this drawing; unavailable hardware looks intact. */
@Composable
internal fun PocketHorizonBumperVisual(left: Boolean, pressed: Boolean, style: PocketHorizonStyle,
    modifier: Modifier = Modifier) {
    val tag = if (left) "horizonL" else "horizonR"
    val capShape = HorizonBumperCapShape(left)
    val travel = if (pressed) PocketHorizonBumperMetrics.Travel else 0.dp
    Box(modifier.testTag(tag + "Mount").drawWithCache {
        // A cap-shaped socket follows the mechanical contour. The main shell overlaps it, rather
        // than a second, locally shaded housing slab with its own exposed bottom/sides.
        val socket = pocketHorizonBumperCapPath(Size((size.width - 8.dp.toPx()).coerceAtLeast(0f),
            36.dp.toPx().coerceAtMost(size.height)), left).apply { translate(Offset(4.dp.toPx(), 0f)) }
        onDrawBehind {
            drawPath(socket, style.bezel)
        }
    }) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(PocketHorizonBumperMetrics.CapHeight)
            .graphicsLayer {
                translationY = (PocketHorizonBumperMetrics.RestTop + travel).toPx()
                shadowElevation = if (pressed) 1.dp.toPx() else 4.dp.toPx()
                shape = capShape
                clip = true
            }
            .testTag(tag + "Plane")
            .drawWithCache {
                val face = PocketHorizonBumperMetrics.UpperFaceHeight.toPx()
                val fill = Brush.verticalGradient(listOf(style.outline.copy(alpha = if (pressed) .72f else .9f),
                    style.shellHighlight, style.bezel))
                val contour = pocketHorizonBumperCapPath(size, left)
                onDrawBehind {
                    drawRect(fill)
                    drawRect(style.bezel.copy(alpha = .65f), Offset(0f, face),
                        Size(size.width, (size.height - face).coerceAtLeast(0f)))
                    drawPath(contour, style.secondaryText.copy(alpha = if (pressed) .18f else .38f), style = Stroke(1.dp.toPx()))
                    drawLine(style.shellHighlight, Offset(0f, face), Offset(size.width, face), 1.dp.toPx())
                }
            }) {
            Box(Modifier.fillMaxWidth().height(PocketHorizonBumperMetrics.UpperFaceHeight), contentAlignment = Alignment.Center) {
                Text(if (left) "L" else "R", Modifier.clearAndSetSemantics {},
                    color = style.text.copy(alpha = .85f), fontSize = 10.sp, lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), maxLines = 1)
            }
        }
        // The cap's dark front facet is the mechanical seam. Its exposed portion contracts as it
        // travels behind the stationary curved front shell; no rectangular seam/lip is overpainted.
    }
}
