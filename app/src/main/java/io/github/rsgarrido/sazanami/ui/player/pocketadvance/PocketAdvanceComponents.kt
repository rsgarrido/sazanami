package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.RetainedArtworkImage
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause

internal object PocketAdvanceColors {
    val Shell = Color(0xFF6653A5)
    val ShellHighlight = Color(0xFF9C87D2)
    val ShellShade = Color(0xFF392C65)
    val Bezel = Color(0xFF282639)
    val Screen = Color(0xFFDFE5CC)
    val ScreenShade = Color(0xFFC8D2B1)
    val Ink = Color(0xFF263C38)
    val MutedInk = Color(0xFF52655A)
    val ScreenAccent = Color(0xFF547B64)
    val Button = Color(0xFF383443)
    val ButtonHighlight = Color(0xFF686173)
    val ButtonInk = Color(0xFFF4EDF9)
    val Label = Color(0xFFE1D8F3)
    val Led = Color(0xFFA8E68D)
}

/** Same 16dp compact outline at zero; sculpted grips and bowed lower edge at one. */
internal fun PocketAdvanceShellShape(progress: Float = 1f): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = pocketAdvanceSafeProgress(progress)
        val r = with(density) { 16.dp.toPx() }.coerceAtMost(size.minDimension / 2f)
        val top = r + (size.height * .13f - r) * p
        val bottom = r + (size.height * .23f - r) * p
        val shoulder = size.height * .025f * p
        val belly = size.height * .045f * p
        val k = .5522848f
        return Outline.Generic(Path().apply {
            moveTo(top, shoulder)
            cubicTo(size.width * .32f, -shoulder / 3f, size.width * .68f, -shoulder / 3f, size.width - top, shoulder)
            cubicTo(size.width - top * (1f-k), shoulder, size.width, top * (1f-k), size.width, top)
            lineTo(size.width, size.height - bottom)
            cubicTo(size.width, size.height - bottom * (1f-k), size.width - bottom * (1f-k), size.height - belly, size.width - bottom, size.height - belly)
            cubicTo(size.width * .65f, size.height + belly / 3f, size.width * .35f, size.height + belly / 3f, bottom, size.height - belly)
            cubicTo(bottom * (1f-k), size.height - belly, 0f, size.height - bottom * (1f-k), 0f, size.height - bottom)
            lineTo(0f, top)
            cubicTo(0f, top * (1f-k), top * (1f-k), shoulder, top, shoulder)
            close()
        })
    }
}

/** The same native finish is used by the endpoint and the moving morph shell. */
@Composable
internal fun PocketAdvanceShell(modifier: Modifier = Modifier, progress: Float = 1f) {
    val shape = PocketAdvanceShellShape(progress)
    val amount = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .shadow((10f + 12f * amount).dp, shape, clip = false)
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(PocketAdvanceColors.Shell, PocketAdvanceColors.ShellHighlight, amount * .48f),
                        PocketAdvanceColors.Shell,
                        androidx.compose.ui.graphics.lerp(PocketAdvanceColors.Shell, PocketAdvanceColors.ShellShade, amount * .42f)
                    )
                ),
                shape
            )
            .border(1.dp, PocketAdvanceColors.ShellHighlight.copy(alpha = 1f - .28f * amount), shape)
    ) {
        Canvas(Modifier.fillMaxSize().clip(shape)) {
            val lip = Path().apply {
                moveTo(size.width * .08f, size.height * .055f)
                cubicTo(size.width * .32f, size.height * .015f, size.width * .68f, size.height * .015f,
                    size.width * .92f, size.height * .055f)
            }
            drawPath(lip, PocketAdvanceColors.Label.copy(alpha = .25f * amount), style = Stroke(2.dp.toPx()))
            val seam = Path().apply {
                val y = size.height - 10.dp.toPx()
                moveTo(size.width * .36f, y)
                cubicTo(size.width * .44f, size.height - 5.dp.toPx(), size.width * .56f, size.height - 5.dp.toPx(),
                    size.width * .64f, y)
            }
            drawPath(seam, PocketAdvanceColors.ShellShade.copy(alpha = .18f * amount), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Fixed decode identity/crop across mini, morph and expanded owners avoids a resize request handoff. */
@Composable
internal fun PocketAdvanceArtwork(
    currentSong: Song?,
    modifier: Modifier = Modifier,
    progress: Float = 1f
) {
    val context = LocalContext.current
    val uri = currentSong?.albumArtUri
    val model = remember(context, uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(512)
            .crossfade(false)
            .memoryCacheKey("pocket-advance:$uri")
            .build()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape((3f + progress.coerceIn(0f, 1f) * 3f).dp))
            .background(PocketAdvanceColors.ScreenShade),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Album, null, tint = PocketAdvanceColors.MutedInk, modifier = Modifier.size(24.dp))
        if (uri != null) {
            RetainedArtworkImage(
                model = model,
                artworkIdentity = uri.toString(),
                requestKey = "pocket-advance:$uri:512",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
internal fun PocketAdvanceTitle(currentSong: Song?, progress: Float, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    Text(
        text = currentSong?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_title),
        color = PocketAdvanceColors.Ink,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = (13f + 9f * fraction).sp,
        lineHeight = (17f + 11f * fraction).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun PocketAdvanceArtist(currentSong: Song?, progress: Float, modifier: Modifier = Modifier) {
    Text(
        text = currentSong?.artist?.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_artist),
        color = PocketAdvanceColors.MutedInk,
        fontFamily = FontFamily.Monospace,
        fontSize = (10f + 5f * progress.coerceIn(0f, 1f)).sp,
        lineHeight = (14f + 6f * progress.coerceIn(0f, 1f)).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Pure visible face: callers provide a separate >=48dp interactive target. */
@Composable
internal fun PocketAdvancePlayVisual(isPlaying: Boolean, modifier: Modifier = Modifier, progress: Float = 1f, pressed: Boolean = false) {
    Box(
        modifier = modifier
            .shadow(if (pressed) 1.dp else 3.dp, CircleShape)
            .background(
                Brush.verticalGradient(if (pressed) listOf(PocketAdvanceColors.Button, PocketAdvanceColors.ButtonHighlight)
                    else listOf(PocketAdvanceColors.ButtonHighlight, PocketAdvanceColors.Button)),
                CircleShape
            )
            .border(1.dp, PocketAdvanceColors.ButtonHighlight, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (playbackPlayPauseShowsPause(isPlaying)) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = PocketAdvanceColors.ButtonInk,
            modifier = Modifier.size((22f + 2f * progress.coerceIn(0f, 1f)).dp)
        )
    }
}

@Composable
internal fun PocketAdvanceSpeaker(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        repeat(5) { index ->
            val x = size.width * (index + 1) / 6f
            val inset = kotlin.math.abs(index - 2) * size.height * .1f
            drawLine(
                PocketAdvanceColors.ShellShade.copy(alpha = .7f),
                Offset(x, inset), Offset(x, size.height - inset),
                strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                PocketAdvanceColors.ShellHighlight.copy(alpha = .45f),
                Offset(x + 2.dp.toPx(), inset), Offset(x + 2.dp.toPx(), size.height - inset),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

internal fun formatPocketAdvanceTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Passive track shared by the native endpoint and morph owner; never receives pointer input. */
internal fun DrawScope.drawPocketAdvanceProgress(fraction: Float, progress: Float) {
    val p = pocketAdvanceSafeProgress(progress)
    val value = pocketAdvanceSafeProgress(fraction)
    val radius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx() * p)
    drawRoundRect(PocketAdvanceColors.MutedInk.copy(alpha = .22f), cornerRadius = radius)
    drawRoundRect(PocketAdvanceColors.ScreenAccent, size = Size(size.width * value, size.height), cornerRadius = radius)
    if (p > 0f) {
        val width = 10.dp.toPx() * p
        val x = (size.width * value - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
        drawRoundRect(PocketAdvanceColors.Ink.copy(alpha = p), Offset(x, -4.dp.toPx() * p),
            Size(width, size.height + 8.dp.toPx() * p), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx() * p))
    }
}
