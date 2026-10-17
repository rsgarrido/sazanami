package io.github.rsgarrido.sazanami.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal enum class MusicNavigationMode { Bottom, Rail }

internal val AppNavigationRailWidth = 66.dp
internal val AppNavigationRailMargin = 16.dp
internal val AppNavigationRailGutter = AppNavigationRailWidth + AppNavigationRailMargin * 2

/** Insets are physical edges of the full presentation host, in dp, excluding the IME. */
internal data class MusicShellInsets(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp
)

/**
 * Presentation policy only: never owns navigation, playback, or expansion state. Reservations
 * depend on the arrangement, not chrome visibility, so expansion cannot move its mini source.
 * This value also identifies a same-viewport morph measurement boundary.
 */
internal data class MusicShellLayout(
    val navigationMode: MusicNavigationMode = MusicNavigationMode.Bottom,
    val insets: MusicShellInsets = MusicShellInsets()
) {
    val usesRail: Boolean get() = navigationMode == MusicNavigationMode.Rail
    val contentLeft: Dp get() = if (usesRail) insets.left + AppNavigationRailGutter else 0.dp
    val contentTop: Dp get() = if (usesRail) insets.top else 0.dp
    val contentRight: Dp get() = if (usesRail) insets.right else 0.dp
    val miniPlayerBottomGap: Dp get() = if (usesRail) 8.dp else AppBottomNavigationHeight
    val miniPlayerMaxWidth: Dp get() = if (usesRail) 840.dp else Dp.Infinity

    fun bottomContentPadding(
        compactNavigationBarInset: Dp,
        navigationVisible: Boolean,
        miniPlayerVisible: Boolean,
        selectionActive: Boolean,
        sleepTimerActive: Boolean
    ): Dp = (if (usesRail) insets.bottom else compactNavigationBarInset) +
        (if (!usesRail && navigationVisible) AppBottomNavigationHeight else 0.dp) +
        when {
            selectionActive -> 8.dp
            !miniPlayerVisible -> 24.dp
            sleepTimerActive -> 176.dp + (if (usesRail) miniPlayerBottomGap else 0.dp)
            else -> 96.dp + (if (usesRail) miniPlayerBottomGap else 0.dp)
        }
}

internal fun resolveMusicShellLayout(
    width: Dp,
    height: Dp,
    insets: MusicShellInsets = MusicShellInsets(),
    fontScale: Float = 1f
): MusicShellLayout {
    val usableWidth = (width - insets.left - insets.right).coerceAtLeast(0.dp)
    val usableHeight = (height - insets.top - insets.bottom).coerceAtLeast(0.dp)
    // Keep at least 600 dp of content room; large text can require a wider window. Icon-only
    // rail targets remain 50 dp. The height guard also excludes shallow landscape phone windows.
    val minimumWidth = maxOf(840.dp, AppNavigationRailGutter + 600.dp * fontScale.coerceAtLeast(1f))
    return if (usableWidth >= minimumWidth && usableHeight >= 400.dp) {
        MusicShellLayout(MusicNavigationMode.Rail, insets)
    } else {
        MusicShellLayout() // Compact geometry and inset handling stay exactly as before.
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun musicShellLayout(
    width: Dp,
    height: Dp,
    topInsetAlreadyReserved: Boolean = false
): MusicShellLayout {
    val density = LocalDensity.current
    // Ignoring visibility prevents immersive player chrome from moving the mini endpoint or
    // changing the breakpoint. Taskbars/caption bars/cutouts count; keyboard visibility does not.
    val safeInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val insets = with(density) {
        MusicShellInsets(
            safeInsets.getLeft(this, LayoutDirection.Ltr).toDp(),
            if (topInsetAlreadyReserved) 0.dp else safeInsets.getTop(this).toDp(),
            safeInsets.getRight(this, LayoutDirection.Ltr).toDp(),
            safeInsets.getBottom(this).toDp()
        )
    }
    return remember(width, height, insets, density.fontScale) {
        resolveMusicShellLayout(width, height, insets, density.fontScale)
    }
}

internal fun Modifier.musicContentPane(layout: MusicShellLayout): Modifier =
    absolutePadding(left = layout.contentLeft, top = layout.contentTop, right = layout.contentRight)
        .consumeWindowInsets(
            PaddingValues.Absolute(left = layout.contentLeft, top = layout.contentTop, right = layout.contentRight)
        )
