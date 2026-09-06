package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

internal sealed interface LibrarySharedArtworkKey {
    data class Album(val albumKey: String) : LibrarySharedArtworkKey
    data class Artist(val artistKey: String) : LibrarySharedArtworkKey
    data class Playlist(val playlistId: Long) : LibrarySharedArtworkKey
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalLibrarySharedTransitionScope =
    compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalLibraryAnimatedVisibilityScope =
    compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Provides one shared-element registry across Home and the normal library shell. The existing
 * destination transition remains the owner of Home/Library/Search motion.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <S> LibrarySharedTransitionHost(
    targetState: S,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    label: String = "librarySharedTransitionHost",
    content: @Composable (S) -> Unit
) {
    SharedTransitionLayout(modifier = modifier) sharedTransition@{
        AnimatedContent(
            targetState = targetState,
            transitionSpec = transitionSpec,
            modifier = Modifier.fillMaxSize(),
            label = label
        ) animatedContent@{ visibleState ->
            CompositionLocalProvider(
                LocalLibrarySharedTransitionScope provides this@sharedTransition,
                LocalLibraryAnimatedVisibilityScope provides this@animatedContent
            ) {
                content(visibleState)
            }
        }
    }
}

/**
 * Keeps collection and detail content alive together during a local transition. When a detail is
 * first opened from Home, the inner transition starts idle and deliberately inherits the outer
 * shell visibility scope so the Home artwork can match the destination artwork.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <S> LibraryDetailAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable (S) -> Unit
) {
    val inheritedVisibilityScope = LocalLibraryAnimatedVisibilityScope.current

    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(durationMillis = 180))
                .togetherWith(fadeOut(tween(durationMillis = 130)))
        },
        label = label
    ) animatedContent@{ visibleState ->
        val localTransitionIsRunning = transition.currentState != transition.targetState
        val visibilityScope = if (localTransitionIsRunning) {
            this@animatedContent
        } else {
            inheritedVisibilityScope ?: this@animatedContent
        }

        CompositionLocalProvider(
            LocalLibraryAnimatedVisibilityScope provides visibilityScope
        ) {
            content(visibleState)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.librarySharedArtwork(
    key: LibrarySharedArtworkKey?
): Modifier {
    if (key == null) return this
    val sharedTransitionScope = LocalLibrarySharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalLibraryAnimatedVisibilityScope.current ?: return this

    return with(sharedTransitionScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ ->
                tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                )
            }
        )
    }
}
