package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R

/** Decorative only: shares the real palette, cap/housing paths and static backdrop. */
@Composable
internal fun PocketHorizonAppearancePreview(style: PocketHorizonStyle, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.horizon_appearance_preview)
    Box(modifier.fillMaxWidth().aspectRatio(2.3f).testTag("pocketHorizonAppearancePreview")
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.width / 1160.dp.toPx()
            val wing = size.width * .15f
            val capSize = Size(wing * .75f, 32.dp.toPx() * scale)
            for (left in listOf(true, false)) {
                val cap = pocketHorizonBumperCapPath(capSize, left)
                cap.translate(Offset(if (left) wing * .15f else size.width - wing * .9f, 2.dp.toPx() * scale))
                drawPath(cap, style.hardware.outline)
            }
            val housing = Path().apply { addOutline(PocketHorizonUpperHousingShape(wing.toDp(), scale, 64.dp * scale)
                .createOutline(size, LayoutDirection.Ltr, this@Canvas)) }
            drawPath(housing, Brush.verticalGradient(listOf(style.hardware.highlight, style.hardware.shell, style.hardware.shade)))
            for (left in listOf(true, false)) {
                val center = Offset(if (left) wing * .5f else size.width - wing * .5f, size.height * .4f)
                drawCircle(style.hardware.shade, wing * .3f, center)
                if (left) {
                    drawLine(style.hardware.ink, center - Offset(wing * .2f, 0f), center + Offset(wing * .2f, 0f), wing * .1f)
                    drawLine(style.hardware.ink, center - Offset(0f, wing * .2f), center + Offset(0f, wing * .2f), wing * .1f)
                    drawCircle(style.hardware.outline, wing * .2f, Offset(center.x, size.height * .76f))
                } else repeat(4) { index ->
                    val dx = listOf(0f, .18f, 0f, -.18f)[index] * wing
                    val dy = listOf(-.18f, 0f, .18f, 0f)[index] * wing
                    drawCircle(style.hardware.controlHighlight, wing * .1f, center + Offset(dx, dy))
                }
            }
            repeat(2) { index ->
                drawRoundRect(style.hardware.shade, Offset(size.width * (.65f + .08f * index), size.height * .88f),
                    Size(size.width * .06f, size.height * .04f), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
        }
        Box(Modifier.fillMaxWidth(.67f).fillMaxHeight(.76f).clip(RoundedCornerShape(8.dp))) {
            PocketHorizonBackdrop(style, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                drawRoundRect(style.surface, Offset(0f, size.height * .56f), Size(size.width, size.height * .3f),
                    androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                drawRoundRect(style.accent, Offset(0f, size.height * .56f), Size(size.width, size.height * .3f),
                    androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawLine(style.text, Offset.Zero, Offset(size.width * .6f, 0f), 2.dp.toPx())
                drawLine(style.secondaryText, Offset(0f, size.height * .2f), Offset(size.width * .4f, size.height * .2f), 2.dp.toPx())
            }
        }
    }
}
