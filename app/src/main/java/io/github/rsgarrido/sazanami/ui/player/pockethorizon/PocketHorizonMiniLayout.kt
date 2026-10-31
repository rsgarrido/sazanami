package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Same 68dp surface and 12/6dp outer margins as the existing compact scaffold. */
internal object PocketHorizonMiniMetrics {
    val SurfaceHeight = 68.dp
    val HorizontalMargin = 12.dp
    val VerticalMargin = 6.dp
    val Padding = 8.dp
    val Artwork = 44.dp
    val Gap = 8.dp
    val TouchTarget = 48.dp
    // Artwork + one practical metadata/expand region + the mandatory playback action.
    val MinimumSurfaceWidth = Padding * 2 + Artwork + Gap + TouchTarget + Gap + TouchTarget
}

@Immutable
internal data class PocketHorizonMiniLayout(
    val showArtist: Boolean, val showTrackButtons: Boolean, val surfaceHeight: Dp
) {
    val contentHeight: Dp get() = surfaceHeight - PocketHorizonMiniMetrics.Padding * 2
}

/** Receives the surface width after outer margins. Text ellipsizes; touch regions never shrink. */
internal fun resolvePocketHorizonMiniLayout(
    width: Dp, fontScale: Float, availableHeight: Dp = Dp.Infinity
): PocketHorizonMiniLayout? {
    val m = PocketHorizonMiniMetrics
    if (!width.value.isFinite() || width < m.MinimumSurfaceWidth) return null
    val scale = PocketHorizonLayoutMetrics.textScale(fontScale)
    // Beyond ordinary large text, grow only enough to contain one real title line plus padding.
    val surfaceHeight = maxOf(m.SurfaceHeight, (20f * scale).dp + m.Padding * 2)
    if (availableHeight.value.isNaN() || availableHeight < surfaceHeight) return null
    val metadataBudget = maxOf(96.dp, 72.dp * scale)
    val withTrackButtons = m.Padding * 2 + m.Artwork + m.Gap + metadataBudget + m.Gap +
        m.TouchTarget * 3 + m.Gap * 2
    return PocketHorizonMiniLayout(
        showArtist = (36f * scale + 2f).dp <= surfaceHeight - m.Padding * 2,
        showTrackButtons = width >= withTrackButtons,
        surfaceHeight = surfaceHeight
    )
}

/** Keep MusicScreen's library clearance sufficient when a real title needs extra compact height. */
internal fun pocketHorizonMiniExtraHeight(fontScale: Float): Dp =
    (20f * PocketHorizonLayoutMetrics.textScale(fontScale) + PocketHorizonMiniMetrics.Padding.value * 2 -
        PocketHorizonMiniMetrics.SurfaceHeight.value).coerceAtLeast(0f).dp
