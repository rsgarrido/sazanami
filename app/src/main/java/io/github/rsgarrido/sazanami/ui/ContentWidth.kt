package io.github.rsgarrido.sazanami.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val ReadableContentMaximumWidth = 840.dp

/** Constrain the child, then center it in its existing pane. Does not reserve shell insets. */
internal fun Modifier.centeredContentWidth(maximum: Dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maximum)

@Composable
internal fun Modifier.readableContentWidth(): Modifier =
    centeredContentWidth(ReadableContentMaximumWidth * LocalDensity.current.fontScale.coerceAtLeast(1f))

internal fun boundedArtworkSize(availableWidth: Dp, fraction: Float, maximum: Dp): Dp {
    if (!availableWidth.value.isFinite() || availableWidth <= 0.dp) return 0.dp
    return minOf(availableWidth * fraction.coerceIn(0f, 1f), maximum, availableWidth)
}

/** The shared-element modifier stays on the actual artwork supplied by the caller. */
@Composable
internal fun BoundedArtworkFrame(
    fraction: Float,
    maximum: Dp,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        content(Modifier.size(boundedArtworkSize(maxWidth, fraction, maximum)))
    }
}
