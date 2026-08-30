package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

internal const val LibraryLayoutMotionDurationMillis = 220

@Composable
fun LibraryLayoutTransition(
    viewMode: LibraryViewMode,
    modifier: Modifier = Modifier,
    listContent: @Composable () -> Unit,
    gridContent: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = viewMode,
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            (fadeIn(
                animationSpec = tween(
                    durationMillis = LibraryLayoutMotionDurationMillis,
                    easing = FastOutSlowInEasing
                )
            ) + scaleIn(
                animationSpec = tween(
                    durationMillis = LibraryLayoutMotionDurationMillis,
                    easing = FastOutSlowInEasing
                ),
                initialScale = 0.975f
            )).togetherWith(
                fadeOut(
                    animationSpec = tween(durationMillis = 150)
                ) + scaleOut(
                    animationSpec = tween(durationMillis = 180),
                    targetScale = 1.015f
                )
            )
        },
        label = "libraryLayoutMode"
    ) { mode ->
        when (mode) {
            LibraryViewMode.LIST -> listContent()
            LibraryViewMode.GRID -> gridContent()
        }
    }
}
