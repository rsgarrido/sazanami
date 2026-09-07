package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

internal enum class LibraryContentMotionLevel {
    PRIMARY,
    SECONDARY
}

internal data class LibraryContentMotion(
    val direction: Int,
    val level: LibraryContentMotionLevel
)

internal sealed interface LibraryContentPresentation {
    data object List : LibraryContentPresentation
    data class Grid(val columns: Int) : LibraryContentPresentation
    data object AdaptiveGrid : LibraryContentPresentation
}

internal enum class LibraryContentTransitionStyle {
    DIRECTIONAL,
    FADE_THROUGH
}

internal fun libraryContentPresentation(
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    adaptiveGrid: Boolean = false
): LibraryContentPresentation = when (viewMode) {
    LibraryViewMode.LIST -> LibraryContentPresentation.List
    LibraryViewMode.GRID -> if (adaptiveGrid) {
        LibraryContentPresentation.AdaptiveGrid
    } else {
        LibraryContentPresentation.Grid(gridColumnCount)
    }
}

internal fun libraryContentTransitionStyle(
    initial: LibraryContentPresentation,
    target: LibraryContentPresentation
): LibraryContentTransitionStyle = if (initial == target) {
    LibraryContentTransitionStyle.DIRECTIONAL
} else {
    LibraryContentTransitionStyle.FADE_THROUGH
}

internal fun libraryContentMotion(
    initialTab: LibraryTab,
    targetTab: LibraryTab
): LibraryContentMotion? {
    val initialPrimary = initialTab.primaryBrowseTab()
    val targetPrimary = targetTab.primaryBrowseTab()

    if (initialPrimary != null && targetPrimary != null && initialPrimary != targetPrimary) {
        return LibraryContentMotion(
            direction = orderedDirection(
                initial = initialPrimary,
                target = targetPrimary,
                order = primaryLibraryTabs
            ),
            level = LibraryContentMotionLevel.PRIMARY
        )
    }

    if (initialPrimary == LibraryTab.SONGS &&
        targetPrimary == LibraryTab.SONGS &&
        initialTab != targetTab
    ) {
        return LibraryContentMotion(
            direction = orderedDirection(
                initial = initialTab,
                target = targetTab,
                order = songCollectionTabs
            ),
            level = LibraryContentMotionLevel.SECONDARY
        )
    }

    return null
}

internal fun libraryContentTransitionSpec(
    initialTab: LibraryTab,
    targetTab: LibraryTab,
    initialPresentation: LibraryContentPresentation,
    targetPresentation: LibraryContentPresentation
): ContentTransform {
    val motion = libraryContentMotion(initialTab, targetTab)
        ?: return EnterTransition.None togetherWith ExitTransition.None

    return when (motion.level) {
        LibraryContentMotionLevel.PRIMARY -> {
            when (libraryContentTransitionStyle(initialPresentation, targetPresentation)) {
                LibraryContentTransitionStyle.DIRECTIONAL -> {
                    (fadeIn(animationSpec = tween(190)) +
                            slideInHorizontally(
                                animationSpec = tween(210, easing = FastOutSlowInEasing)
                            ) { width -> motion.direction * width / 28 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(145)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            175,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> -motion.direction * width / 36 }
                        )
                }

                LibraryContentTransitionStyle.FADE_THROUGH -> {
                    fadeIn(animationSpec = tween(durationMillis = 160, delayMillis = 70))
                        .togetherWith(fadeOut(animationSpec = tween(durationMillis = 90)))
                }
            }
        }

        LibraryContentMotionLevel.SECONDARY -> {
            (fadeIn(animationSpec = tween(170)) +
                    slideInHorizontally(
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) { width -> motion.direction * width / 64 })
                .togetherWith(
                    fadeOut(animationSpec = tween(125)) +
                            slideOutHorizontally(
                                animationSpec = tween(145, easing = FastOutSlowInEasing)
                            ) { width -> -motion.direction * width / 80 }
                )
        }
    }
}

private fun orderedDirection(
    initial: LibraryTab,
    target: LibraryTab,
    order: List<LibraryTab>
): Int {
    val initialIndex = order.indexOf(initial)
    val targetIndex = order.indexOf(target)
    return if (targetIndex > initialIndex) 1 else -1
}
