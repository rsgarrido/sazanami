package io.github.rsgarrido.sazanami.ui.lyrics

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Optional presentation colors; null at the screen preserves its original artwork/white-text style. */
@Immutable
data class LyricsColors(
    val background: Color,
    val foreground: Color,
    val returnContainer: Color,
    val returnContent: Color,
    val backdropTopAlpha: Float = 0.78f,
    val backdropMiddleAlpha: Float = 0.64f,
    val backdropBottomAlpha: Float = 0.88f,
    val headerScrimAlpha: Float = 0.58f,
    val pastLineAlpha: Float = 0.58f,
    val upcomingLineAlpha: Float = 0.38f,
    val secondaryTextAlpha: Float? = null
)
