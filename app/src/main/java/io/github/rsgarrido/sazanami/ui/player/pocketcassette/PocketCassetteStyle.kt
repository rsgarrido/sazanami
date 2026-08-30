package io.github.rsgarrido.sazanami.ui.player.pocketcassette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.darken
import io.github.rsgarrido.sazanami.ui.player.theme.lighten
import io.github.rsgarrido.sazanami.ui.player.theme.withAlpha

internal val PocketCassetteDefaultTokens = PlayerThemeTokens(
    shellColor = Color(0xFFB9BEC0),
    accentColor = Color(0xFF456D8E),
    displayBackgroundColor = Color(0xFF080B0D),
    displayTextColor = Color(0xFFE1E6E4),
    secondaryAccentColor = Color(0xFFE56C36)
)

internal class PocketCassettePalette private constructor(tokens: PlayerThemeTokens) {
    private val shell = tokens.shellColor
    private val accent = tokens.accentColor
    private val displayBackground = tokens.displayBackgroundColor
    private val displayText = tokens.displayTextColor
    private val warmAccent = tokens.secondaryAccentColor ?: accent

    val silverLight = shell.lighten(0.606f)
    val silver = shell
    val silverMid = shell.darken(0.173f)
    val silverDark = shell.darken(0.458f)
    val shellInk = shell.darken(0.756f)
    val blueLight = accent.lighten(0.225f)
    val blue = accent
    val blueDark = accent.darken(0.304f)
    val window = displayBackground
    val windowEdge = displayBackground.lighten(0.086f)
    val windowText = displayText
    val windowTextMuted = displayText.darken(0.243f)
    val tape = PocketCassetteDecorativeColors.tape
    val reel = PocketCassetteDecorativeColors.reel
    val reelHub = PocketCassetteDecorativeColors.reelHub
    val button = displayBackground.lighten(0.128f)
    val buttonTop = displayBackground.lighten(0.286f)
    val buttonPressed = displayBackground.lighten(0.056f)
    val buttonEdge = displayBackground.lighten(0.020f)
    val buttonIcon = displayText.lighten(0.488f)
    val buttonActive = warmAccent.lighten(0.026f)
    val orange = warmAccent
    val statusGreen = PocketCassetteDecorativeColors.statusGreen
    val seam = shell.darken(0.411f)
    val highlight = shell.lighten(1f).withAlpha(0.75f)

    companion object {
        fun from(tokens: PlayerThemeTokens): PocketCassettePalette =
            PocketCassettePalette(tokens)
    }
}

internal val PocketCassetteDefaultPalette =
    PocketCassettePalette.from(PocketCassetteDefaultTokens)

// Kept as the call-site name for this staged migration; its value is now a derived palette.
internal val LocalPocketCassettePalette = staticCompositionLocalOf {
    PocketCassetteDefaultPalette
}

internal val PocketCassetteColors: PocketCassettePalette
    @Composable get() = LocalPocketCassettePalette.current

private object PocketCassetteDecorativeColors {
    val tape = Color(0xFF4B2F24)
    val reel = Color(0xFFD2D6D4)
    val reelHub = Color(0xFF6D7678)
    val statusGreen = Color(0xFF8EBA72)
}

@Composable
internal fun Modifier.pocketCassetteShellFinish(): Modifier {
    val colors = PocketCassetteColors
    return background(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to colors.silverDark,
                0.025f to colors.silverLight,
                0.22f to colors.silver,
                0.52f to colors.silverLight,
                0.78f to colors.silverMid,
                0.975f to colors.silverLight,
                1f to colors.silverDark
            )
        )
    ).drawWithContent {
        drawContent()
        val hairline = 1.dp.toPx()
        var y = 2.dp.toPx()
        while (y < size.height) {
            drawLine(
                color = if ((y / hairline).toInt() % 4 == 0) {
                    Color.White.copy(alpha = 0.035f)
                } else {
                    Color.Black.copy(alpha = 0.025f)
                },
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = hairline
            )
            y += 3.dp.toPx()
        }
        drawLine(
            color = colors.highlight,
            start = Offset(hairline, 0f),
            end = Offset(hairline, size.height),
            strokeWidth = hairline
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.35f),
            start = Offset(size.width - hairline, 0f),
            end = Offset(size.width - hairline, size.height),
            strokeWidth = hairline
        )
    }
}

@Composable
internal fun Modifier.pocketCassetteBluePanelFinish(radius: Dp): Modifier {
    val colors = PocketCassetteColors
    return background(
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.blueLight,
                colors.blue,
                colors.blueDark
            )
        ),
        shape = RoundedCornerShape(radius)
    ).drawWithContent {
        drawContent()
        val inset = 1.dp.toPx()
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - inset * 2,
                height = size.height - inset * 2
            ),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawLine(
            color = Color.White.copy(alpha = 0.24f),
            start = Offset(radius.toPx(), 2.dp.toPx()),
            end = Offset(size.width - radius.toPx(), 2.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
internal fun Modifier.pocketCassetteBevel(
    radius: Dp,
    pressed: Boolean = false
): Modifier {
    val colors = PocketCassetteColors
    return drawWithContent {
    drawContent()
    val inset = 1.dp.toPx()
    drawRoundRect(
        color = if (pressed) colors.buttonEdge else Color.White.copy(alpha = 0.25f),
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            width = size.width - inset * 2,
            height = size.height - inset * 2
        ),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )
    drawLine(
        color = Color.Black.copy(alpha = if (pressed) 0.18f else 0.48f),
        start = Offset(radius.toPx(), size.height - inset),
        end = Offset(size.width - radius.toPx(), size.height - inset),
        strokeWidth = 1.5.dp.toPx()
    )
    }
}

@Composable
internal fun PocketCassetteScrew(
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val colors = PocketCassetteColors
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.silverLight,
                        colors.silverDark
                    )
                )
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawLine(
                color = colors.shellInk.copy(alpha = 0.75f),
                start = Offset(this.size.width * 0.25f, this.size.height * 0.56f),
                end = Offset(this.size.width * 0.75f, this.size.height * 0.44f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
