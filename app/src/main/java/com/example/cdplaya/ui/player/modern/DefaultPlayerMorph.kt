package com.example.cdplaya.ui.player.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class DefaultPlayerMorphVisualState(
    val isReady: Boolean,
    val backgroundAlpha: Float,
    val metadataAlpha: Float,
    val controlsAlpha: Float,
    val expensiveContentActive: Boolean
)

@Composable
internal fun DefaultPlayerMorph(
    progress: Float,
    geometry: DefaultPlayerMorphGeometry?,
    carouselPresentation: ModernArtworkCarouselPresentation,
    artworkTransitionStyle: ModernArtworkTransitionStyle,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    style: ModernPlayerStyle,
    appearance: ModernPlayerAppearance,
    artworkPalette: ModernArtworkPalette,
    modifier: Modifier = Modifier,
    expandedContent: @Composable (DefaultPlayerMorphVisualState) -> Unit
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val ready = geometry != null
    val visualState = DefaultPlayerMorphVisualState(
        isReady = ready,
        backgroundAlpha = if (ready) {
            morphProgressWindow(
                safeProgress,
                DefaultPlayerMorphSpec.BackgroundRevealStart,
                DefaultPlayerMorphSpec.BackgroundRevealEnd
            )
        } else {
            0f
        },
        metadataAlpha = if (ready) {
            morphProgressWindow(
                safeProgress,
                DefaultPlayerMorphSpec.MetadataRevealStart,
                DefaultPlayerMorphSpec.MetadataRevealEnd
            )
        } else {
            0f
        },
        controlsAlpha = if (ready) {
            morphProgressWindow(
                safeProgress,
                DefaultPlayerMorphSpec.ControlsRevealStart,
                DefaultPlayerMorphSpec.ControlsRevealEnd
            )
        } else {
            0f
        },
        expensiveContentActive = ready && shouldRunDefaultExpandedWork(safeProgress)
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (ready) 1f else 0f }
                .drawWithContent {
                    val surface = geometry?.surface ?: return@drawWithContent
                    val radius = interpolateMorphCornerRadius(
                        collapsedRadius = 18.dp.toPx(),
                        expandedRadius = 0f,
                        progress = safeProgress
                    )
                    val clipPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = surface,
                                cornerRadius = CornerRadius(radius, radius)
                            )
                        )
                    }
                    clipPath(clipPath) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        lerpColor(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            style.backgroundColor,
                            safeProgress
                        )
                    )
            )
            expandedContent(visualState)
        }

        if (geometry != null) {
            DefaultMorphArtwork(
                carouselPresentation = carouselPresentation,
                transitionStyle = artworkTransitionStyle,
                bounds = geometry.artwork,
                progress = safeProgress,
                style = style,
                appearance = appearance.artwork
            )
            DefaultMorphTitleArtist(
                carouselPresentation = carouselPresentation,
                transitionStyle = artworkTransitionStyle,
                bounds = geometry.text,
                progress = safeProgress,
                style = style,
                layoutAppearance = appearance.layout
            )
            DefaultMorphPlayPause(
                isPlaying = isPlaying,
                bounds = geometry.playPause,
                progress = safeProgress,
                onClick = onPlayPauseClick,
                style = style,
                appearance = appearance.controls,
                artworkPalette = artworkPalette
            )
        }
    }
}

@Composable
private fun DefaultMorphArtwork(
    carouselPresentation: ModernArtworkCarouselPresentation,
    transitionStyle: ModernArtworkTransitionStyle,
    bounds: androidx.compose.ui.geometry.Rect,
    progress: Float,
    style: ModernPlayerStyle,
    appearance: ModernArtworkAppearance
) {
    val density = LocalDensity.current
    val radius = interpolateMorphCornerRadius(
        collapsedRadius = 10f,
        expandedRadius = appearance.shape.cornerRadiusDp.toFloat(),
        progress = progress
    )
    Box(
        modifier = Modifier
            .placeInRootBounds(bounds)
            .shadow(
                elevation = (appearance.shadow.elevationDp * progress).dp,
                shape = RoundedCornerShape(radius.dp)
            )
            .background(
                color = style.artworkContainerColor,
                shape = RoundedCornerShape(radius.dp)
            )
            .graphicsLayer {
                shape = RoundedCornerShape(radius.dp)
                clip = true
            }
    ) {
        ModernPlayerArtworkPages(
            carouselItems = carouselPresentation.songs.items(),
            carouselState = carouselPresentation.state,
            transitionStyle = transitionStyle,
            style = style,
            artworkSize = with(density) {
                bounds.width.coerceAtLeast(1f).toDp()
            },
            decoratePages = false,
            appearance = appearance
        )
    }
}

@Composable
private fun DefaultMorphTitleArtist(
    carouselPresentation: ModernArtworkCarouselPresentation,
    transitionStyle: ModernArtworkTransitionStyle,
    bounds: androidx.compose.ui.geometry.Rect,
    progress: Float,
    style: ModernPlayerStyle,
    layoutAppearance: ModernLayoutAppearance
) {
    val useCenteredAlignment =
        layoutAppearance.metadataAlignment == ModernMetadataAlignment.CENTER && progress >= 0.72f
    val titleStyle = lerp(
        MaterialTheme.typography.titleMedium,
        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        progress
    )
    val artistStyle = lerp(
        MaterialTheme.typography.bodySmall,
        MaterialTheme.typography.titleMedium,
        progress
    )
    Box(
        modifier = Modifier.placeInRootBounds(bounds),
        contentAlignment = if (useCenteredAlignment) Alignment.TopCenter else Alignment.TopStart
    ) {
        carouselPresentation.songs.items().forEach { item ->
            key(item.song.id) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val gestureOffset = normalizedModernCarouselOffset(
                                offsetX = carouselPresentation.state.offsetX,
                                artworkWidthPx =
                                    carouselPresentation.state.artworkWidthPx
                            )
                            val transform = modernMetadataPageTransform(
                                style = transitionStyle,
                                gestureOffset = gestureOffset,
                                restingOffset = item.restingOffsetMultiplier,
                                isCurrent = item.isCurrent
                            )
                            translationX =
                                transform.translationMultiplier * bounds.width
                            scaleX = transform.scale
                            scaleY = transform.scale
                            alpha = transform.alpha
                            rotationY = transform.rotationY
                            transformOrigin =
                                androidx.compose.ui.graphics.TransformOrigin.Center
                            if (transform.rotationY != 0f) {
                                cameraDistance =
                                    COVER_FLOW_METADATA_CAMERA_DISTANCE_MULTIPLIER *
                                        density
                            }
                        }
                        .suppressDefaultMorphSemantics(!item.isCurrent),
                    horizontalAlignment = if (useCenteredAlignment) {
                        Alignment.CenterHorizontally
                    } else {
                        Alignment.Start
                    }
                ) {
                    Text(
                        text = item.song.title.ifBlank { "Unknown Title" },
                        style = titleStyle,
                        color = lerpColor(
                            MaterialTheme.colorScheme.onSurface,
                            style.contentColor,
                            progress
                        ),
                        maxLines = if (progress < 0.55f) 1 else 2,
                        textAlign = if (useCenteredAlignment) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height((6f * progress).dp))
                    Text(
                        text = item.song.artist.ifBlank { "Unknown Artist" },
                        style = artistStyle,
                        color = lerpColor(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            style.secondaryContentColor,
                            progress
                        ),
                        maxLines = 1,
                        textAlign = if (useCenteredAlignment) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultMorphPlayPause(
    isPlaying: Boolean,
    bounds: androidx.compose.ui.geometry.Rect,
    progress: Float,
    onClick: () -> Unit,
    style: ModernPlayerStyle,
    appearance: ModernControlAppearance,
    artworkPalette: ModernArtworkPalette
) {
    val expandedSurfaceAlpha = morphProgressWindow(progress, 0.20f, 0.72f)
    val shape = RoundedCornerShape(percent = 50)
    val accentColor = resolveModernControlAccentColor(appearance, artworkPalette, style)
    val decoratedModifier = when (appearance.style) {
        ModernControlStyle.MINIMAL -> Modifier
        ModernControlStyle.GLASS -> Modifier
            .shadow(
                elevation = (14f * expandedSurfaceAlpha).dp,
                shape = shape
            )
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        style.primaryControlSurfaceTopColor.copy(
                            alpha = style.primaryControlSurfaceTopColor.alpha *
                                    expandedSurfaceAlpha
                        ),
                        style.primaryControlSurfaceBottomColor.copy(
                            alpha = style.primaryControlSurfaceBottomColor.alpha *
                                    expandedSurfaceAlpha
                        )
                    )
                ),
                shape = shape
            )
            .border(
                width = expandedSurfaceAlpha.dp,
                color = style.primaryControlSurfaceBorderColor.copy(
                    alpha = style.primaryControlSurfaceBorderColor.alpha *
                            expandedSurfaceAlpha
                ),
                shape = shape
            )
        ModernControlStyle.TONAL -> Modifier
            .shadow((8f * expandedSurfaceAlpha).dp, shape)
            .background(
                accentColor.copy(alpha = 0.88f * expandedSurfaceAlpha),
                shape
            )
        ModernControlStyle.OUTLINE -> Modifier.border(
            width = (1.5f * expandedSurfaceAlpha).dp,
            color = accentColor.copy(alpha = 0.9f * expandedSurfaceAlpha),
            shape = shape
        )
    }
    Box(
        modifier = Modifier
            .placeInRootBounds(bounds)
            .then(decoratedModifier),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = lerpColor(
                    MaterialTheme.colorScheme.onSurface,
                    if (appearance.style == ModernControlStyle.TONAL) {
                        modernContrastingForeground(accentColor)
                    } else {
                        accentColor
                    },
                    progress
                ),
                modifier = Modifier.size((24f + 26f * progress).dp)
            )
        }
    }
}

internal fun Modifier.hiddenFromDefaultMorph(hidden: Boolean): Modifier =
    if (!hidden) {
        this
    } else {
        graphicsLayer { alpha = 0f }
            .clearAndSetSemantics { }
    }

internal fun Modifier.suppressDefaultMorphSemantics(suppress: Boolean): Modifier =
    if (suppress) clearAndSetSemantics { } else this

@Composable
private fun Modifier.placeInRootBounds(
    bounds: androidx.compose.ui.geometry.Rect
): Modifier {
    val density = LocalDensity.current
    return with(density) {
        this@placeInRootBounds
            .offset {
                IntOffset(
                    x = bounds.left.roundToInt(),
                    y = bounds.top.roundToInt()
                )
            }
            .size(
                width = bounds.width.coerceAtLeast(1f).toDp(),
                height = bounds.height.coerceAtLeast(1f).toDp()
            )
    }
}

private fun lerpColor(start: Color, end: Color, progress: Float): Color =
    Color(
        red = start.red + (end.red - start.red) * progress,
        green = start.green + (end.green - start.green) * progress,
        blue = start.blue + (end.blue - start.blue) * progress,
        alpha = start.alpha + (end.alpha - start.alpha) * progress
    )
