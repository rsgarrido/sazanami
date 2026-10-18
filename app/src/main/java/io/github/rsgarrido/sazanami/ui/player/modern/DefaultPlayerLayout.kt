package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class DefaultPlayerArrangement { Vertical, Balanced }

/** Resolved full-player geometry, also used as the host's measurement boundary. No UI state. */
@Immutable
data class DefaultPlayerLayout(
    val arrangement: DefaultPlayerArrangement,
    val artworkSize: Dp,
    val detailsWidth: Dp,
    val regionGap: Dp,
    val safePadding: PaddingValues,
    val minimumDetailsHeight: Dp,
    val density: ModernLayoutDensity,
    val scrollVertical: Boolean
) {
    internal val foregroundPadding: PaddingValues get() = PaddingValues.Absolute(
        safePadding.calculateLeftPadding(LayoutDirection.Ltr) + ModernPlayerDefaults.ContentHorizontalPadding,
        safePadding.calculateTopPadding() + ModernPlayerDefaults.ContentVerticalPadding,
        safePadding.calculateRightPadding(LayoutDirection.Ltr) + ModernPlayerDefaults.ContentHorizontalPadding,
        safePadding.calculateBottomPadding() + ModernPlayerDefaults.ContentVerticalPadding
    )
}

internal data class DefaultPlayerInsets(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp
)

/** Preserve the established portrait budget, including its pre-inset height calculation. */
internal fun verticalDefaultArtworkSize(
    width: Dp,
    height: Dp,
    appearance: ModernPlayerAppearance,
    albumArtSize: Dp = ModernPlayerDefaults.MaximumArtworkSize
): Dp = minOf(
    albumArtSize * appearance.artwork.size.maximumScale,
    (width - 32.dp).coerceAtLeast(1.dp),
    height * appearance.artwork.size.maximumHeightFraction,
    (height - verticalDefaultReservedHeight(appearance)).coerceAtLeast(112.dp)
)

private fun verticalDefaultReservedHeight(appearance: ModernPlayerAppearance): Dp =
    210.dp + appearance.controls.size.primarySizeDp.dp +
        (if (appearance.seekbar.style.usesWaveformData) {
            (appearance.seekbar.waveformSize.trackHeightDp + 36).dp
        } else 64.dp) + appearance.layout.density.minimumFlexibleGapDp.dp +
        (if (appearance.layout.showAudioQualityBadge) 36.dp else 0.dp)

internal fun resolveDefaultPlayerLayout(
    width: Dp,
    height: Dp,
    insets: DefaultPlayerInsets = DefaultPlayerInsets(),
    fontScale: Float = 1f,
    appearance: ModernPlayerAppearance = ModernPlayerAppearance.Default,
    albumArtSize: Dp = ModernPlayerDefaults.MaximumArtworkSize
): DefaultPlayerLayout {
    val textScale = fontScale.coerceAtLeast(1f)
    val usableWidth = (width - insets.left - insets.right - 32.dp).coerceAtLeast(0.dp)
    val usableHeight = (height - insets.top - insets.bottom - 24.dp).coerceAtLeast(0.dp)
    val gap = when (appearance.layout.density) {
        ModernLayoutDensity.COMPACT -> 24.dp
        ModernLayoutDensity.BALANCED -> 32.dp
        ModernLayoutDensity.RELAXED -> 40.dp
    }
    // Four secondary targets plus the primary, with at least 12 dp between each target.
    val minimumControlsWidth = (appearance.controls.size.primarySizeDp +
        4 * appearance.controls.size.modeContainerSizeDp + 48).dp
    val minimumDetailsWidth = maxOf(360.dp * textScale, minimumControlsWidth)
    // Two title lines, artist, album, optional quality badge, actions, seek/times and transport.
    // Budget worst-case metadata, not the current song: changing tracks cannot select a layout.
    val metadataHeight = 108.dp * textScale + 9.dp +
        (if (appearance.layout.showAudioQualityBadge) 22.dp + 16.dp * textScale else 0.dp)
    val seekHeight = maxOf(48.dp,
        if (appearance.seekbar.style.usesWaveformData) appearance.seekbar.waveformSize.trackHeightDp.dp
        else 48.dp) + 20.dp * textScale
    val minimumDetailsHeight = metadataHeight + 48.dp +
        appearance.layout.density.minimumFlexibleGapDp.dp + seekHeight + 18.dp +
        appearance.controls.size.primarySizeDp.dp + 20.dp
    val balanced = albumArtSize * appearance.artwork.size.maximumScale >= 240.dp &&
        usableWidth >= maxOf(760.dp, 240.dp + gap + minimumDetailsWidth) &&
        usableWidth >= usableHeight * 1.15f &&
        usableHeight >= maxOf(320.dp, minimumDetailsHeight)
    val artwork = if (balanced) {
        minOf(albumArtSize * appearance.artwork.size.maximumScale, usableHeight,
            usableWidth - gap - minimumDetailsWidth)
    } else verticalDefaultArtworkSize(width, height, appearance, albumArtSize)
    return DefaultPlayerLayout(
        arrangement = if (balanced) DefaultPlayerArrangement.Balanced else DefaultPlayerArrangement.Vertical,
        artworkSize = artwork,
        detailsWidth = if (balanced) minOf(520.dp * textScale, usableWidth - artwork - gap) else 0.dp,
        regionGap = gap,
        safePadding = PaddingValues.Absolute(insets.left, insets.top, insets.right, insets.bottom),
        minimumDetailsHeight = minimumDetailsHeight,
        density = appearance.layout.density,
        scrollVertical = !balanced && usableHeight <
            maxOf(verticalDefaultReservedHeight(appearance) + 112.dp, minimumDetailsHeight + 136.dp)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun defaultPlayerLayout(
    width: Dp,
    height: Dp,
    appearance: ModernPlayerAppearance,
    topInsetAlreadyReserved: Boolean = false
): DefaultPlayerLayout {
    val density = LocalDensity.current
    val safeInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val insets = with(density) {
        DefaultPlayerInsets(
            safeInsets.getLeft(this, LayoutDirection.Ltr).toDp(),
            if (topInsetAlreadyReserved) 0.dp else safeInsets.getTop(this).toDp(),
            safeInsets.getRight(this, LayoutDirection.Ltr).toDp(),
            safeInsets.getBottom(this).toDp()
        )
    }
    return remember(width, height, insets, density.fontScale, appearance) {
        resolveDefaultPlayerLayout(width, height, insets, density.fontScale, appearance)
    }
}
