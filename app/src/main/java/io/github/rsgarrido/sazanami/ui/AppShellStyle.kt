package io.github.rsgarrido.sazanami.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import kotlin.math.max

@Composable
fun Modifier.appShellBackground(): Modifier {
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val accent = AppShellAccent

    return drawWithCache {
        val baseGradient = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to background,
                0.48f to surface,
                1f to background
            )
        )
        val accentGlow = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.075f),
                accent.copy(alpha = 0.02f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.16f, size.height * 0.08f),
            radius = max(size.width, size.height) * 0.64f
        )
        val vignette = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to background.copy(alpha = 0.72f)
            ),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = max(size.width, size.height) * 0.72f
        )

        onDrawBehind {
            drawRect(baseGradient)
            drawRect(accentGlow)
            drawRect(vignette)
        }
    }
}
