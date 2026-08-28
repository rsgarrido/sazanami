package com.example.cdplaya.ui.player.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.cdplaya.player.RepeatMode

@Composable
internal fun ModernPlayerControls(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    style: ModernPlayerStyle,
    appearance: ModernControlAppearance = ModernControlAppearance(),
    artworkPalette: ModernArtworkPalette? = null,
    modifier: Modifier = Modifier,
    primaryControlModifier: Modifier = Modifier,
    expandedControlsAlpha: Float = 1f,
    controlsEnabled: Boolean = true,
    controlScale: Float = 1f
) {
    val safeControlScale = controlScale.coerceIn(0.01f, 1f)
    val accentColor = resolveModernControlAccentColor(
        appearance = appearance,
        artworkPalette = artworkPalette,
        style = style
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        ModernPlayerModeIconButton(
            onClick = { if (controlsEnabled) onShuffleClick() },
            modifier = Modifier.graphicsLayer { alpha = expandedControlsAlpha },
            style = style,
            appearance = appearance,
            accentColor = accentColor,
            controlScale = safeControlScale
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = if (isShuffleEnabled) "Shuffle on" else "Shuffle off",
                tint = if (isShuffleEnabled) {
                    accentColor
                } else {
                    style.inactiveControlColor
                }
            )
        }

        ModernPlayerNavigationIconButton(
            onClick = { if (controlsEnabled) onPreviousClick() },
            modifier = Modifier.graphicsLayer { alpha = expandedControlsAlpha },
            appearance = appearance,
            accentColor = accentColor,
            style = style,
            controlScale = safeControlScale
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous song",
                tint = style.contentColor,
                modifier = Modifier.size(
                    (appearance.size.navigationIconSizeDp * safeControlScale).dp
                )
            )
        }

        ModernPlayerPlayPauseButton(
            isPlaying = isPlaying,
            onClick = { if (controlsEnabled) onPlayPauseClick() },
            style = style,
            appearance = appearance,
            accentColor = accentColor,
            controlScale = safeControlScale,
            modifier = primaryControlModifier
        )

        ModernPlayerNavigationIconButton(
            onClick = { if (controlsEnabled) onNextClick() },
            modifier = Modifier.graphicsLayer { alpha = expandedControlsAlpha },
            appearance = appearance,
            accentColor = accentColor,
            style = style,
            controlScale = safeControlScale
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next song",
                tint = style.contentColor,
                modifier = Modifier.size(
                    (appearance.size.navigationIconSizeDp * safeControlScale).dp
                )
            )
        }

        ModernPlayerModeIconButton(
            onClick = { if (controlsEnabled) onRepeatClick() },
            modifier = Modifier.graphicsLayer { alpha = expandedControlsAlpha },
            style = style,
            appearance = appearance,
            accentColor = accentColor,
            controlScale = safeControlScale
        ) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                contentDescription = when (repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                },
                tint = if (repeatMode == RepeatMode.OFF) {
                    style.inactiveControlColor
                } else {
                    accentColor
                }
            )
        }
    }
}

@Composable
private fun ModernPlayerPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    style: ModernPlayerStyle,
    appearance: ModernControlAppearance,
    accentColor: Color,
    controlScale: Float,
    modifier: Modifier = Modifier,
) {
    val containerModifier = when (appearance.style) {
        ModernControlStyle.MINIMAL -> Modifier.clip(style.primaryControlShape)
        ModernControlStyle.GLASS -> Modifier
            .shadow(
                elevation = 14.dp,
                shape = style.primaryControlShape,
                ambientColor = Color.Black.copy(alpha = 0.24f),
                spotColor = Color.Black.copy(alpha = 0.32f)
            )
            .clip(style.primaryControlShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        style.primaryControlSurfaceTopColor,
                        style.primaryControlSurfaceBottomColor
                    )
                )
            )
            .border(
                width = 1.dp,
                color = style.primaryControlSurfaceBorderColor,
                shape = style.primaryControlShape
            )
        ModernControlStyle.TONAL -> Modifier
            .shadow(8.dp, style.primaryControlShape)
            .clip(style.primaryControlShape)
            .background(accentColor.copy(alpha = 0.88f))
            .border(1.dp, accentColor, style.primaryControlShape)
        ModernControlStyle.OUTLINE -> Modifier
            .clip(style.primaryControlShape)
            .border(1.5.dp, accentColor.copy(alpha = 0.9f), style.primaryControlShape)
    }
    val iconColor = when (appearance.style) {
        ModernControlStyle.TONAL -> modernContrastingForeground(accentColor)
        ModernControlStyle.MINIMAL,
        ModernControlStyle.GLASS,
        ModernControlStyle.OUTLINE -> accentColor
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size((appearance.size.primarySizeDp * controlScale).dp)
            .then(containerModifier)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isPlaying) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = iconColor,
                modifier = Modifier.size(
                    (appearance.size.primarySizeDp * 0.61f * controlScale).dp
                )
            )
        }
    }
}

@Composable
private fun ModernPlayerModeIconButton(
    onClick: () -> Unit,
    style: ModernPlayerStyle,
    appearance: ModernControlAppearance,
    accentColor: Color,
    controlScale: Float,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size((appearance.size.modeContainerSizeDp * controlScale).dp)
            .then(modernSecondaryControlContainer(appearance, accentColor, style)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            icon()
        }
    }
}

@Composable
private fun ModernPlayerNavigationIconButton(
    onClick: () -> Unit,
    appearance: ModernControlAppearance,
    accentColor: Color,
    style: ModernPlayerStyle,
    controlScale: Float,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size((appearance.size.modeContainerSizeDp * controlScale).dp)
            .then(modernSecondaryControlContainer(appearance, accentColor, style)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) { icon() }
    }
}

private fun modernSecondaryControlContainer(
    appearance: ModernControlAppearance,
    accentColor: Color,
    style: ModernPlayerStyle
): Modifier = when (appearance.style) {
    ModernControlStyle.MINIMAL -> Modifier.clip(style.modeControlShape)
    ModernControlStyle.GLASS -> Modifier
        .clip(style.modeControlShape)
        .background(style.primaryControlSurfaceBottomColor.copy(alpha = 0.34f))
    ModernControlStyle.TONAL -> Modifier
        .clip(style.modeControlShape)
        .background(accentColor.copy(alpha = 0.18f))
    ModernControlStyle.OUTLINE -> Modifier
        .clip(style.modeControlShape)
        .border(1.dp, accentColor.copy(alpha = 0.58f), style.modeControlShape)
}

internal fun resolveModernControlAccentColor(
    appearance: ModernControlAppearance,
    artworkPalette: ModernArtworkPalette?,
    style: ModernPlayerStyle
): Color = when (appearance.accent) {
    ModernControlAccent.WHITE -> style.contentColor
    ModernControlAccent.APP_ACCENT -> style.accentColor
    ModernControlAccent.ALBUM_DERIVED -> resolveModernAlbumAccent(
        palette = artworkPalette,
        fallbackAccent = style.accentColor
    )
}

internal data class ModernControlRowLayout(
    val scale: Float,
    val primarySizeDp: Float,
    val secondarySizeDp: Float,
    val requiredWidthDp: Float
)

internal fun resolveModernControlRowLayout(
    size: ModernControlSize,
    availableWidthDp: Float
): ModernControlRowLayout {
    val baseWidth = size.primarySizeDp + size.modeContainerSizeDp * 4f
    val safeWidth = availableWidthDp.coerceAtLeast(0f)
    val scale = if (baseWidth <= 0f) 1f else (safeWidth / baseWidth).coerceIn(0f, 1f)
    val primary = size.primarySizeDp * scale
    val secondary = size.modeContainerSizeDp * scale
    return ModernControlRowLayout(
        scale = scale,
        primarySizeDp = primary,
        secondarySizeDp = secondary,
        requiredWidthDp = primary + secondary * 4f
    )
}
