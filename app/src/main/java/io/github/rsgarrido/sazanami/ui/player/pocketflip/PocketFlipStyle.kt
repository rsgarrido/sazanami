package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

internal val PocketFlipDefaultTokens = PlayerThemeTokens(
    shellColor = Color(0xFF982E3B),
    accentColor = Color(0xFFA5C980),
    displayBackgroundColor = Color(0xFF263029),
    displayTextColor = Color(0xFFE0E7D8),
    secondaryAccentColor = Color(0xFF6D203C)
)

internal class PocketFlipPalette private constructor(tokens: PlayerThemeTokens) {
    private val shellBase = tokens.shellColor
    private val accent = tokens.accentColor
    private val displayBackground = tokens.displayBackgroundColor
    private val displayText = tokens.displayTextColor
    private val secondaryAccent = tokens.secondaryAccentColor ?: shellBase.darken(0.28f)
    private val controlSurface = PocketFlipDecorativeColors.controlSurface

    val shellTop = PocketFlipDecorativeColors.shellTop
    val shell = shellBase
    val shellBottom = shellBase.darken(0.23f)
    val shellHighlight = PocketFlipDecorativeColors.shellHighlight
    val shellShadow = shellBase.darken(0.90f).withAlpha(0.40f)
    val shellText = PocketFlipDecorativeColors.shellText
    val bezel = controlSurface.darken(0.455f)
    val bezelText = displayText.darken(0.112f)
    val bezelTextMuted = displayText.darken(0.465f)
    val display = displayBackground
    val lcdBand = displayBackground.darken(0.367f)
    val lcdTint = accent.darken(0.04f).withAlpha(0.047f)
    val lcdGlow = accent.lighten(0.09f).withAlpha(0.059f)
    val lcdGrid = displayBackground.darken(0.66f).withAlpha(0.078f)
    val lcdScanline = displayBackground.darken(0.84f).withAlpha(0.133f)
    val artworkWell = displayBackground.darken(0.534f)
    val artworkLcdTint = displayBackground.darken(0.50f).withAlpha(0.125f)
    val screenText = displayText
    val screenTextMuted = displayText.darken(0.286f)
    val screenAccent = accent
    val seekInactive = displayBackground.lighten(0.166f)
    val seekHousing = displayBackground.darken(0.788f)
    val seekThumb = displayText.darken(0.112f)
    val seekThumbHighlight = displayText.lighten(0.496f)
    val statusOn = PocketFlipDecorativeColors.statusOn
    val statusIdle = PocketFlipDecorativeColors.statusIdle
    val hinge = shellBase.darken(0.301f)
    val hingeCap = shellBase.darken(0.655f)
    val button = controlSurface
    val buttonPressed = controlSurface.darken(0.417f)
    val buttonShadow = shellBase.darken(0.443f)
    val buttonCenter = controlSurface.darken(0.231f)
    val buttonHighlight = controlSurface.lighten(0.163f)
    val buttonIcon = controlSurface.lighten(0.832f)
    val buttonActiveIcon = accent.lighten(0.046f)
    val modeGlow = PocketFlipDecorativeColors.modeGlow
    val modeLamp = PocketFlipDecorativeColors.modeLamp
    val action = secondaryAccent.darken(0.287f)
    val actionActive = secondaryAccent
    val actionPressed = secondaryAccent.darken(0.506f)
    val actionIcon = PocketFlipDecorativeColors.actionIcon
    val utility = secondaryAccent.darken(0.546f)
    val utilityPressed = secondaryAccent.darken(0.732f)
    val utilityLabel = PocketFlipDecorativeColors.utilityLabel
    val utilityEdge = PocketFlipDecorativeColors.utilityEdge
    val utilityIcon = PocketFlipDecorativeColors.utilityIcon
    val speaker = shellBase.darken(0.525f)
    val speakerHighlight = PocketFlipDecorativeColors.speakerHighlight
    val controlWell = shellBase.darken(0.248f)
    val controlGroove = shellBase.darken(0.410f)
    val controlMark = shellBase.darken(0.323f)
    val engravedText = PocketFlipDecorativeColors.engravedText
    val screw = shellBase.darken(0.173f)
    val screwSlot = shellBase.darken(0.533f)

    companion object {
        fun from(tokens: PlayerThemeTokens): PocketFlipPalette = PocketFlipPalette(tokens)
    }
}

internal val PocketFlipDefaultPalette = PocketFlipPalette.from(PocketFlipDefaultTokens)

// Kept as the call-site name for this staged migration; its value is now a derived palette.
internal val LocalPocketFlipPalette = staticCompositionLocalOf {
    PocketFlipDefaultPalette
}

internal val PocketFlipColors: PocketFlipPalette
    @Composable get() = LocalPocketFlipPalette.current

private object PocketFlipDecorativeColors {
    val shellTop = Color(0xFFB3414C)
    val shellHighlight = Color(0x66FFD9DC)
    val shellText = Color(0xFFF5C7CB)
    val controlSurface = Color(0xFF2A2B31)
    val statusOn = Color(0xFF8DD663)
    val statusIdle = Color(0xFF715257)
    val modeGlow = Color(0x293E7C52)
    val modeLamp = Color(0xFF82C66B)
    val actionIcon = Color(0xFFF7DFE8)
    val utilityLabel = Color(0xFFF0B1B8)
    val utilityEdge = Color(0xFF9E4B56)
    val utilityIcon = Color(0xFFF7D8DB)
    val speakerHighlight = Color(0xFFBD5260)
    val engravedText = Color(0xFFC66C76)
}

internal fun Modifier.pocketFlipRoundButtonFinish(isPressed: Boolean): Modifier =
    drawWithContent {
        drawContent()
        val inset = 1.dp.toPx()
        drawCircle(
            color = if (isPressed) Color(0xFF210B14) else Color(0xFF7D3651),
            radius = size.minDimension / 2f - inset,
            center = center,
            style = Stroke(width = if (isPressed) 1.dp.toPx() else 2.dp.toPx())
        )
    }

@Composable
internal fun Modifier.pocketFlipActionPlateFinish(): Modifier {
    val colors = PocketFlipColors
    return drawWithContent {
        drawContent()
        val inset = 1.dp.toPx()
        drawRoundRect(
            color = colors.controlGroove,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - inset * 2f,
                height = size.height - inset * 2f
            ),
            cornerRadius = CornerRadius(size.height / 2f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
internal fun Modifier.pocketFlipUtilitySwitchFinish(isPressed: Boolean): Modifier {
    val colors = PocketFlipColors
    return drawWithContent {
        drawContent()
        val inset = 1.dp.toPx()
        drawRoundRect(
            color = if (isPressed) colors.utilityPressed else colors.utilityEdge,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - inset * 2f,
                height = size.height - inset * 2f
            ),
            cornerRadius = CornerRadius(size.height / 2f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
internal fun Modifier.pocketFlipShellFinish(): Modifier {
    val colors = PocketFlipColors
    return background(
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.shellTop,
                colors.shell,
                colors.shellBottom
            )
        )
    ).drawWithContent {
        drawContent()
        val edge = 1.dp.toPx()
        drawLine(
            color = colors.shellHighlight,
            start = Offset(0f, edge),
            end = Offset(size.width, edge),
            strokeWidth = edge
        )
        drawLine(
            color = colors.shellShadow,
            start = Offset(0f, size.height - edge),
            end = Offset(size.width, size.height - edge),
            strokeWidth = edge
        )
    }
}

@Composable
internal fun Modifier.pocketFlipBezelFinish(cornerRadius: Dp): Modifier {
    val colors = PocketFlipColors
    return background(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF24242A), colors.bezel)
        )
    ).drawWithContent {
        drawContent()
        drawRoundRect(
            color = Color(0xFF08080A),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = Color(0xFF46464D),
            start = Offset(cornerRadius.toPx(), 2.dp.toPx()),
            end = Offset(size.width - cornerRadius.toPx(), 2.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

internal fun Modifier.pocketFlipLcdFrameFinish(cornerRadius: Dp): Modifier =
    drawWithContent {
        drawContent()
        val outerInset = 1.dp.toPx()
        drawRoundRect(
            color = Color(0xFF050706),
            topLeft = Offset(outerInset, outerInset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - outerInset * 2f,
                height = size.height - outerInset * 2f
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = Color(0xFF566159),
            start = Offset(cornerRadius.toPx(), 3.dp.toPx()),
            end = Offset(size.width - cornerRadius.toPx(), 3.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }

internal fun Modifier.pocketFlipArtworkFrameFinish(): Modifier =
    drawWithContent {
        drawContent()
        val inset = 1.dp.toPx()
        drawRoundRect(
            color = Color(0xFF667065),
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - inset * 2f,
                height = size.height - inset * 2f
            ),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }

@Composable
internal fun PocketFlipHinge(compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 20.dp else 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketFlipHingeSegment(
            modifier = Modifier.weight(0.22f),
            compact = compact
        )
        PocketFlipHingeSegment(
            modifier = Modifier.weight(0.56f),
            compact = compact,
            centerGroove = true
        )
        PocketFlipHingeSegment(
            modifier = Modifier.weight(0.22f),
            compact = compact
        )
    }
}

@Composable
private fun PocketFlipHingeSegment(
    compact: Boolean,
    modifier: Modifier = Modifier,
    centerGroove: Boolean = false
) {
    Box(
        modifier = modifier
            .height(if (compact) 16.dp else 20.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFB13B47),
                        PocketFlipColors.hinge,
                        PocketFlipColors.hingeCap
                    )
                ),
                RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (centerGroove) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (compact) 11.dp else 14.dp)
                    .background(PocketFlipColors.hingeCap, CircleShape)
            )
        }
    }
}
