package com.example.cdplaya.ui.player.modern

import kotlin.math.abs

internal data class ModernArtworkPageTransform(
    val translationMultiplier: Float,
    val scale: Float,
    val alpha: Float,
    val rotationY: Float = 0f
)

internal data class ModernMetadataPageTransform(
    val translationMultiplier: Float,
    val scale: Float = 1f,
    val alpha: Float,
    val rotationY: Float = 0f
)

internal fun modernArtworkPageTransform(
    style: ModernArtworkTransitionStyle,
    gestureOffset: Float,
    restingOffset: Float,
    isCurrent: Boolean
): ModernArtworkPageTransform {
    val progress = abs(gestureOffset).coerceIn(0f, 1f)
    val pageOffset = gestureOffset + restingOffset
    val distanceFromCenter = abs(pageOffset).coerceIn(0f, 1f)
    val effectDistance = emphasizedModernArtworkDistance(distanceFromCenter)
    val translationCompression = modernArtworkTranslationCompression(distanceFromCenter)

    return when (modernArtworkRenderingPolicy(style)) {
        ModernArtworkRenderingPolicy.Slide -> slideArtworkTransform(
            pageOffset = pageOffset,
            progress = progress,
            isCurrent = isCurrent
        )

        ModernArtworkRenderingPolicy.DepthScale -> {
            val pageAlpha = 1f - DEPTH_ALPHA_REDUCTION * effectDistance
            ModernArtworkPageTransform(
                translationMultiplier = pageOffset *
                    (1f - DEPTH_TRANSLATION_REDUCTION * translationCompression),
                scale = 1f - DEPTH_SCALE_REDUCTION * effectDistance,
                alpha = visiblePageAlpha(
                    pageAlpha = pageAlpha,
                    progress = progress,
                    isCurrent = isCurrent
                )
            )
        }

        ModernArtworkRenderingPolicy.CoverFlow -> {
            val pageAlpha = 1f - COVER_FLOW_ALPHA_REDUCTION * effectDistance
            ModernArtworkPageTransform(
                translationMultiplier = pageOffset *
                    (1f - COVER_FLOW_TRANSLATION_REDUCTION * translationCompression),
                scale = 1f - COVER_FLOW_SCALE_REDUCTION * effectDistance,
                alpha = visiblePageAlpha(
                    pageAlpha = pageAlpha,
                    progress = progress,
                    isCurrent = isCurrent
                ),
                rotationY = -pageOffset.signOrZero() * effectDistance *
                    COVER_FLOW_MAX_ROTATION_DEGREES
            )
        }

        ModernArtworkRenderingPolicy.StackReveal -> stackRevealArtworkTransform(
            gestureOffset = gestureOffset,
            restingOffset = restingOffset,
            pageOffset = pageOffset,
            progress = progress,
            isCurrent = isCurrent
        )
    }
}

internal fun modernMetadataPageTransform(
    style: ModernArtworkTransitionStyle,
    gestureOffset: Float,
    restingOffset: Float,
    isCurrent: Boolean
): ModernMetadataPageTransform {
    val progress = abs(gestureOffset).coerceIn(0f, 1f)
    val pageOffset = gestureOffset + restingOffset
    val distanceFromCenter = abs(pageOffset).coerceIn(0f, 1f)
    val effectDistance = emphasizedModernArtworkDistance(distanceFromCenter)

    return when (style) {
        ModernArtworkTransitionStyle.SLIDE -> ModernMetadataPageTransform(
            translationMultiplier = pageOffset,
            alpha = slidePageAlpha(progress, isCurrent)
        )

        ModernArtworkTransitionStyle.DEPTH_SCALE -> {
            val pageAlpha = 1f - DEPTH_METADATA_ALPHA_REDUCTION * effectDistance
            ModernMetadataPageTransform(
                translationMultiplier = pageOffset,
                scale = 1f - DEPTH_METADATA_SCALE_REDUCTION * effectDistance,
                alpha = visiblePageAlpha(
                    pageAlpha = pageAlpha,
                    progress = progress,
                    isCurrent = isCurrent
                )
            )
        }

        ModernArtworkTransitionStyle.COVER_FLOW -> {
            val pageAlpha = 1f -
                COVER_FLOW_METADATA_ALPHA_REDUCTION * effectDistance
            ModernMetadataPageTransform(
                translationMultiplier = pageOffset,
                scale = 1f -
                    COVER_FLOW_METADATA_SCALE_REDUCTION * effectDistance,
                alpha = visiblePageAlpha(
                    pageAlpha = pageAlpha,
                    progress = progress,
                    isCurrent = isCurrent
                ),
                rotationY = -pageOffset.signOrZero() * effectDistance *
                    COVER_FLOW_MAX_ROTATION_DEGREES *
                    COVER_FLOW_METADATA_ROTATION_MULTIPLIER
            )
        }

        ModernArtworkTransitionStyle.STACK_REVEAL -> {
            val isActiveNeighbor = isActiveStackNeighbor(
                gestureOffset = gestureOffset,
                restingOffset = restingOffset
            )
            when {
                isCurrent -> ModernMetadataPageTransform(
                    translationMultiplier = gestureOffset,
                    scale = 1f - STACK_CURRENT_METADATA_SCALE_REDUCTION * progress,
                    alpha = 1f - progress
                )

                isActiveNeighbor -> ModernMetadataPageTransform(
                    translationMultiplier = restingOffset *
                        STACK_UNDERLYING_TRANSLATION_MULTIPLIER * (1f - progress),
                    scale = STACK_UNDERLYING_METADATA_START_SCALE +
                        (1f - STACK_UNDERLYING_METADATA_START_SCALE) * progress,
                    alpha = progress
                )

                else -> ModernMetadataPageTransform(
                    translationMultiplier = restingOffset,
                    alpha = 0f
                )
            }
        }
    }
}

internal fun emphasizedModernArtworkDistance(distanceFromCenter: Float): Float {
    val distance = distanceFromCenter.coerceIn(0f, 1f)
    return 1f - (1f - distance) * (1f - distance)
}

internal fun modernArtworkTranslationCompression(distanceFromCenter: Float): Float {
    val distance = distanceFromCenter.coerceIn(0f, 1f)
    return 4f * distance * (1f - distance)
}

private fun Float.signOrZero(): Float = when {
    this < 0f -> -1f
    this > 0f -> 1f
    else -> 0f
}

private fun slideArtworkTransform(
    pageOffset: Float,
    progress: Float,
    isCurrent: Boolean
): ModernArtworkPageTransform {
    return ModernArtworkPageTransform(
        translationMultiplier = pageOffset,
        scale = if (isCurrent) {
            1f - progress * SLIDE_CURRENT_SCALE_REDUCTION
        } else {
            SLIDE_NEIGHBOR_START_SCALE + progress * SLIDE_NEIGHBOR_SCALE_INCREASE
        },
        alpha = slidePageAlpha(progress, isCurrent)
    )
}

private fun stackRevealArtworkTransform(
    gestureOffset: Float,
    restingOffset: Float,
    pageOffset: Float,
    progress: Float,
    isCurrent: Boolean
): ModernArtworkPageTransform {
    if (isCurrent) {
        return ModernArtworkPageTransform(
            translationMultiplier = pageOffset,
            scale = 1f - STACK_CURRENT_SCALE_REDUCTION * progress,
            alpha = 1f - STACK_CURRENT_ALPHA_REDUCTION * progress
        )
    }

    if (!isActiveStackNeighbor(gestureOffset, restingOffset)) {
        return ModernArtworkPageTransform(
            translationMultiplier = restingOffset,
            scale = STACK_UNDERLYING_START_SCALE,
            alpha = 0f
        )
    }

    return ModernArtworkPageTransform(
        translationMultiplier = restingOffset *
            STACK_UNDERLYING_TRANSLATION_MULTIPLIER * (1f - progress),
        scale = STACK_UNDERLYING_START_SCALE +
            (1f - STACK_UNDERLYING_START_SCALE) * progress,
        alpha = 1f
    )
}

private fun isActiveStackNeighbor(
    gestureOffset: Float,
    restingOffset: Float
): Boolean {
    return when {
        gestureOffset < 0f -> restingOffset > 0f
        gestureOffset > 0f -> restingOffset < 0f
        else -> false
    }
}

private fun slidePageAlpha(progress: Float, isCurrent: Boolean): Float {
    return if (isCurrent) {
        1f - progress * SLIDE_CURRENT_ALPHA_REDUCTION
    } else {
        (progress * SLIDE_NEIGHBOR_ALPHA_MULTIPLIER).coerceAtMost(
            SLIDE_NEIGHBOR_MAX_ALPHA
        )
    }
}

private fun visiblePageAlpha(
    pageAlpha: Float,
    progress: Float,
    isCurrent: Boolean
): Float {
    if (isCurrent) {
        return pageAlpha
    }

    val neighborReveal = (progress * NEIGHBOR_REVEAL_MULTIPLIER).coerceIn(0f, 1f)
    return pageAlpha * neighborReveal
}

internal const val COVER_FLOW_MAX_ROTATION_DEGREES = 32f
internal const val COVER_FLOW_METADATA_ROTATION_MULTIPLIER = 0.70f
internal const val COVER_FLOW_CAMERA_DISTANCE_MULTIPLIER = 10f
internal const val COVER_FLOW_METADATA_CAMERA_DISTANCE_MULTIPLIER = 13f

private const val SLIDE_CURRENT_SCALE_REDUCTION = 0.035f
private const val SLIDE_NEIGHBOR_START_SCALE = 0.94f
private const val SLIDE_NEIGHBOR_SCALE_INCREASE = 0.04f
private const val SLIDE_CURRENT_ALPHA_REDUCTION = 0.08f
private const val SLIDE_NEIGHBOR_ALPHA_MULTIPLIER = 1.8f
private const val SLIDE_NEIGHBOR_MAX_ALPHA = 0.9f

internal const val DEPTH_SCALE_REDUCTION = 0.24f
internal const val DEPTH_ALPHA_REDUCTION = 0.34f
internal const val DEPTH_TRANSLATION_REDUCTION = 0.18f
private const val DEPTH_METADATA_SCALE_REDUCTION = 0.16f
private const val DEPTH_METADATA_ALPHA_REDUCTION = 0.28f

internal const val COVER_FLOW_SCALE_REDUCTION = 0.10f
internal const val COVER_FLOW_ALPHA_REDUCTION = 0.22f
internal const val COVER_FLOW_TRANSLATION_REDUCTION = 0.06f
private const val COVER_FLOW_METADATA_SCALE_REDUCTION = 0.07f
private const val COVER_FLOW_METADATA_ALPHA_REDUCTION = 0.22f

private const val NEIGHBOR_REVEAL_MULTIPLIER = 3f

private const val STACK_CURRENT_SCALE_REDUCTION = 0.08f
private const val STACK_CURRENT_ALPHA_REDUCTION = 0.18f
private const val STACK_CURRENT_METADATA_SCALE_REDUCTION = 0.03f
private const val STACK_UNDERLYING_TRANSLATION_MULTIPLIER = 0.06f
private const val STACK_UNDERLYING_START_SCALE = 0.94f
private const val STACK_UNDERLYING_METADATA_START_SCALE = 0.98f
