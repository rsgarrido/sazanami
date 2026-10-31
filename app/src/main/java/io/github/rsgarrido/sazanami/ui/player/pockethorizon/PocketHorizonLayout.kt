package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** Content measurements shared by the shell and its resolver budget. No device policy lives here. */
internal object PocketHorizonLayoutMetrics {
    val OuterInset = 20.dp
    val BezelInset = 8.dp
    val ControlWing = 148.dp
    val ControlCluster = 144.dp
    val ShoulderBand = 56.dp
    val HardwareStrip = 64.dp
    val DisplayPadding = 20.dp
    val ContentGap = 32.dp
    val HeaderGap = 16.dp
    val MetadataGap = 6.dp
    val DetailsGap = 12.dp
    val MinimumTouchTarget = 48.dp
    val ControlHeight = 64.dp
    val SeekTarget = 48.dp
    val MinimumArtwork = 144.dp
    // Fits beside metadata + transport even at the compact single-scale LCD height.
    val MaximumArtwork = 220.dp
    // Readable two-line metadata measure (21 artist-font ems), larger than the 192 dp transport row.
    val MinimumDetailsWidth = 336.dp
    val MinimumHeaderHeight = 48.dp
    val CategoryGap = 8.dp

    val TitleFontSize = 26.sp
    val TitleLineHeight = 32.sp
    val ArtistFontSize = 16.sp
    val ArtistLineHeight = 24.sp
    val AlbumFontSize = 14.sp
    val AlbumLineHeight = 20.sp
    val TimeLineHeight = 24.sp

    const val PreferredShellAspectRatio = 2f

    fun textScale(fontScale: Float): Float =
        fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f

    fun statusHeight(fontScale: Float): Dp =
        max(MinimumHeaderHeight.value, 24f * textScale(fontScale)).dp

    fun categoryHeight(fontScale: Float): Dp =
        max(MinimumTouchTarget.value, 22f * textScale(fontScale) + 16f).dp

    fun headerHeight(fontScale: Float): Dp =
        statusHeight(fontScale) + CategoryGap + categoryHeight(fontScale)

    /** A two-line Back label, two-line status banner, and one complete two-line song row. */
    fun browserMinimumHeight(fontScale: Float): Dp {
        val scale = textScale(fontScale)
        val back = (44f * scale + 16f).dp
        val status = (44f * scale + 16f).dp
        val row = max(80f, 68f * scale + 28f).dp
        return back + status + row
    }

    /** Two title/artist lines, one album line, seek/time, and fixed touch targets; text is never scaled down. */
    fun detailsMinimumHeight(fontScale: Float): Dp {
        val text = (TitleLineHeight.value * 2 + ArtistLineHeight.value * 2 +
            AlbumLineHeight.value + TimeLineHeight.value) * textScale(fontScale)
        return text.dp + MetadataGap * 2 + DetailsGap * 2 + SeekTarget + ControlHeight
    }
}

/**
 * Geometry for one already inset-adjusted host. Small isolated hosts retain a readable scroll plane
 * inside a bounded shell; [fitsMinimum] alone is a content-fit result, not tablet eligibility.
 */
@Immutable
internal data class PocketHorizonLayout(
    val shellSize: DpSize,
    val displaySize: DpSize,
    val contentViewportSize: DpSize,
    val contentWidth: Dp,
    val artworkSize: Dp,
    val detailsWidth: Dp,
    val outerInset: Dp,
    val bezelInset: Dp,
    val displayPadding: Dp,
    val controlWing: Dp,
    val shoulderBandHeight: Dp,
    val hardwareStripHeight: Dp,
    val isWide: Boolean,
    val fitsMinimum: Boolean,
    val headerHeight: Dp,
    val bodyMinHeight: Dp,
    val minimumSize: DpSize
)

/** The same typography, spacing, artwork, and touch budgets used by the real shell and nomination. */
internal fun minimumPocketHorizonSize(fontScale: Float): DpSize {
    val metrics = PocketHorizonLayoutMetrics
    val frame = (metrics.OuterInset + metrics.BezelInset + metrics.DisplayPadding) * 2
    return DpSize(
        frame + metrics.ControlWing * 2 + metrics.MinimumArtwork + metrics.ContentGap +
            metrics.MinimumDetailsWidth * metrics.textScale(fontScale),
        frame + metrics.HardwareStrip + metrics.headerHeight(fontScale) + metrics.HeaderGap +
            metrics.detailsMinimumHeight(fontScale)
    )
}

/**
 * Uses actual finite constraints. Preferred proportions yield to the content budget, never to uniform
 * scaling. An undersized host stacks the content and requires scrolling instead of shrinking controls.
 */
internal fun resolvePocketHorizonLayout(
    availableSize: DpSize,
    fontScale: Float
): PocketHorizonLayout? {
    if (!availableSize.isFiniteAndPositive()) return null
    val metrics = PocketHorizonLayoutMetrics
    val minimum = minimumPocketHorizonSize(fontScale)
    if (!minimum.isFiniteAndPositive()) return null
    val fits = availableSize.width >= minimum.width && availableSize.height >= minimum.height
    val outer = min(metrics.OuterInset.value,
        min(availableSize.width.value, availableSize.height.value) / 20f).dp
    val availableShellWidth = availableSize.width - outer * 2
    val availableShellHeight = availableSize.height - outer * 2
    val shellWidth = if (fits) {
        min(availableShellWidth.value, max(
            minimum.width.value - metrics.OuterInset.value * 2,
            availableShellHeight.value * metrics.PreferredShellAspectRatio
        )).dp
    } else availableShellWidth
    val shellHeight = min(availableShellHeight.value, max(
        minimum.height.value - metrics.OuterInset.value * 2,
        shellWidth.value / metrics.PreferredShellAspectRatio
    )).dp

    // Insets become shallower only for insufficient isolated hosts, so even tiny bounds stay valid.
    val bezel = min(metrics.BezelInset.value, min(shellWidth.value, shellHeight.value) / 8f).dp
    val wing = min(metrics.ControlWing.value, (shellWidth.value - bezel.value * 2) / 3f).dp
    val innerHeight = shellHeight - bezel * 2
    val strip = min(metrics.HardwareStrip.value, innerHeight.value / 4f).dp
    val shoulderBand = min(metrics.ShoulderBand.value, innerHeight.value / 4f).dp
    val display = DpSize(shellWidth - bezel * 2 - wing * 2, innerHeight - strip)
    val padding = min(metrics.DisplayPadding.value,
        min(display.width.value, display.height.value) / 8f).dp
    val viewport = DpSize(display.width - padding * 2, display.height - padding * 2)
    val detailsMinimumWidth = metrics.MinimumDetailsWidth * metrics.textScale(fontScale)
    val contentWidth = max(viewport.width.value, detailsMinimumWidth.value).dp
    val artworkWidthLimit = if (fits) {
        contentWidth - metrics.ContentGap - detailsMinimumWidth
    } else contentWidth
    val artwork = min(artworkWidthLimit.value,
        (contentWidth.value * .30f).coerceIn(
            metrics.MinimumArtwork.value, metrics.MaximumArtwork.value
        )).dp
    val detailsWidth = if (fits) contentWidth - metrics.ContentGap - artwork else contentWidth
    val detailsHeight = metrics.detailsMinimumHeight(fontScale)
    val bodyHeight = if (fits) max(detailsHeight.value, artwork.value).dp
        else artwork + metrics.ContentGap + detailsHeight
    return PocketHorizonLayout(
        shellSize = DpSize(shellWidth, shellHeight),
        displaySize = display,
        contentViewportSize = viewport,
        contentWidth = contentWidth,
        artworkSize = artwork,
        detailsWidth = detailsWidth,
        outerInset = outer,
        bezelInset = bezel,
        displayPadding = padding,
        controlWing = wing,
        shoulderBandHeight = shoulderBand,
        hardwareStripHeight = strip,
        isWide = fits,
        fitsMinimum = fits,
        headerHeight = metrics.headerHeight(fontScale),
        bodyMinHeight = bodyHeight,
        minimumSize = minimum
    )
}

private fun DpSize.isFiniteAndPositive(): Boolean =
    width.value.isFinite() && height.value.isFinite() && width.value > 0f && height.value > 0f
