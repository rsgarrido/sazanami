package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** These are content budgets used by both eligibility and the actual handheld layout. */
internal object PocketAdvanceLayout {
    val OuterInset = 24.dp
    val ShellInset = 24.dp
    val ControlWing = 144.dp
    val ScreenGap = 16.dp
    val ScreenInset = 16.dp
    val Artwork = 144.dp
    val MetadataWidth = 176.dp
    val BrandHeight = 24.dp
    val BrandGap = 12.dp
    val StatusHeight = 20.dp
    val StatusGap = 12.dp
    val SeekGap = 8.dp
    val SeekHeight = 64.dp

    fun minimumSize(fontScale: Float): DpSize {
        val textScale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
        val screenWidth = Artwork + ScreenGap + MetadataWidth * textScale + ScreenInset * 2
        val screenHeight = ScreenInset * 2 + StatusHeight * textScale + StatusGap +
            max(Artwork.value, 92f * textScale).dp + SeekGap + SeekHeight * textScale
        return DpSize(
            OuterInset * 2 + ShellInset * 2 + ControlWing * 2 + ScreenGap * 2 + screenWidth,
            OuterInset * 2 + ShellInset * 2 + BrandHeight * textScale + BrandGap + screenHeight
        )
    }

    // A deeper handheld dominates typical landscape tablets without stretching vertically in
    // near-square windows. Minimum eligibility budgets above remain unchanged.
    fun shellHeight(availableHeight: Dp, shellWidth: Dp, fontScale: Float): Dp =
        max(minimumSize(fontScale).height.value - OuterInset.value * 2f, shellWidth.value / 1.65f)
            .coerceAtMost(availableHeight.value).dp
}

internal fun minimumPocketAdvanceSize(fontScale: Float): DpSize =
    PocketAdvanceLayout.minimumSize(fontScale)
