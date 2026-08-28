package com.example.cdplaya.ui.player.modern

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song

@Composable
internal fun BoxScope.ModernPlayerBackground(
    currentSong: Song,
    style: ModernPlayerStyle,
    appearance: ModernBackgroundAppearance
) {
    val policy = modernBackgroundPolicy(
        sdkInt = Build.VERSION.SDK_INT,
        backgroundStyle = appearance.style,
        blurStrength = appearance.blurStrength
    )

    when (appearance.style) {
        ModernBackgroundStyle.BLURRED_ARTWORK,
        ModernBackgroundStyle.DETAILED_ARTWORK -> {
            ModernPlayerAlbumImage(
                currentSong = currentSong,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (policy.usePlatformBlur && policy.blurRadiusDp > 0) {
                            Modifier.blur(policy.blurRadiusDp.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop,
                transitionDurationMillis = ModernPlayerDefaults.BackgroundTransitionDurationMillis
            )
        }

        ModernBackgroundStyle.ALBUM_GRADIENT -> Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            style.accentColor.copy(alpha = 0.78f),
                            style.accentColor.copy(alpha = 0.28f),
                            style.backgroundColor
                        )
                    )
                )
        )

        ModernBackgroundStyle.SOLID_COLOR -> Box(
            modifier = Modifier
                .matchParentSize()
                .background(style.solidBackgroundColor)
        )

        ModernBackgroundStyle.PURE_BLACK -> Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black)
        )
    }

    if (appearance.style.supportsDimming) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = appearance.dimmingStrength.overlayAlpha))
        )
    }

    if (policy.legacyScrimAlpha > 0f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = policy.legacyScrimAlpha))
        )
    }

    if (appearance.style == ModernBackgroundStyle.BLURRED_ARTWORK ||
        appearance.style == ModernBackgroundStyle.DETAILED_ARTWORK
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            style.gradientTopColor,
                            Color.Transparent,
                            style.gradientBottomColor
                        )
                    )
                )
        )
    }
}

internal data class ModernBackgroundPolicy(
    val usePlatformBlur: Boolean,
    val legacyScrimAlpha: Float,
    val blurRadiusDp: Int
)

internal fun modernBackgroundPolicy(
    sdkInt: Int,
    backgroundStyle: ModernBackgroundStyle = ModernBackgroundStyle.BLURRED_ARTWORK,
    blurStrength: ModernBlurStrength = ModernBlurStrength.MEDIUM
): ModernBackgroundPolicy {
    val requestsBlur = backgroundStyle.supportsBlur
    val blurRadius = when (backgroundStyle) {
        ModernBackgroundStyle.BLURRED_ARTWORK -> blurStrength.blurredArtworkRadiusDp
        ModernBackgroundStyle.DETAILED_ARTWORK -> blurStrength.detailedArtworkRadiusDp
        else -> 0
    }
    return ModernBackgroundPolicy(
        usePlatformBlur = requestsBlur && sdkInt >= Build.VERSION_CODES.S,
        legacyScrimAlpha = if (requestsBlur && sdkInt < Build.VERSION_CODES.S) 0.30f else 0f,
        blurRadiusDp = blurRadius
    )
}
