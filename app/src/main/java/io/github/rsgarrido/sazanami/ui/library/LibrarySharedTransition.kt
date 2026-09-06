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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SharedArtworkDurationMillis = 280
private const val SharedSourceReplicaAlpha = 0.24f

internal enum class LibrarySharedArtworkSourceSlotTreatment {
    NEUTRAL_SURFACE,
    SUBDUED_ARTWORK
}

internal fun shouldDrawSharedArtworkSourceReplica(
    matchFound: Boolean,
    treatment: LibrarySharedArtworkSourceSlotTreatment
): Boolean = matchFound &&
    treatment == LibrarySharedArtworkSourceSlotTreatment.SUBDUED_ARTWORK

internal enum class LibrarySharedArtworkSourceScope {
    HOME_PINNED,
    LIBRARY_COLLECTION
}

internal sealed interface LibrarySharedArtworkKey {
    data class Album(
        val albumKey: String,
        val sourceScope: LibrarySharedArtworkSourceScope
    ) : LibrarySharedArtworkKey

    data class Artist(
        val artistKey: String,
        val sourceScope: LibrarySharedArtworkSourceScope
    ) : LibrarySharedArtworkKey

    data class Playlist(
        val playlistId: Long,
        val sourceScope: LibrarySharedArtworkSourceScope
    ) : LibrarySharedArtworkKey
}

internal data class LibrarySharedArtworkDetailSourceScopes(
    val album: LibrarySharedArtworkSourceScope =
        LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION,
    val artist: LibrarySharedArtworkSourceScope =
        LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION,
    val playlist: LibrarySharedArtworkSourceScope =
        LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
)

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalLibrarySharedTransitionScope =
    compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalLibraryAnimatedVisibilityScope =
    compositionLocalOf<AnimatedVisibilityScope?> { null }

private val LocalLibrarySharedArtworkDetailSourceScopes =
    compositionLocalOf { LibrarySharedArtworkDetailSourceScopes() }

/**
 * Provides one shared-element registry across Home and the normal library shell. The existing
 * destination transition remains the owner of Home/Library/Search motion.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <S> LibrarySharedTransitionHost(
    targetState: S,
    detailSourceScopes: (S) -> LibrarySharedArtworkDetailSourceScopes,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform,
    contentKey: (S) -> Any? = { it },
    modifier: Modifier = Modifier,
    label: String = "librarySharedTransitionHost",
    content: @Composable (S) -> Unit
) {
    SharedTransitionLayout(modifier = modifier) sharedTransition@{
        AnimatedContent(
            targetState = targetState,
            transitionSpec = transitionSpec,
            contentKey = contentKey,
            modifier = Modifier.fillMaxSize(),
            label = label
        ) animatedContent@{ visibleState ->
            CompositionLocalProvider(
                LocalLibrarySharedTransitionScope provides this@sharedTransition,
                LocalLibraryAnimatedVisibilityScope provides this@animatedContent,
                LocalLibrarySharedArtworkDetailSourceScopes provides
                    detailSourceScopes(visibleState)
            ) {
                content(visibleState)
            }
        }
    }
}

@Composable
internal fun albumDetailSharedArtworkKey(albumKey: String): LibrarySharedArtworkKey =
    LibrarySharedArtworkKey.Album(
        albumKey = albumKey,
        sourceScope = LocalLibrarySharedArtworkDetailSourceScopes.current.album
    )

@Composable
internal fun artistDetailSharedArtworkKey(artistKey: String): LibrarySharedArtworkKey =
    LibrarySharedArtworkKey.Artist(
        artistKey = artistKey,
        sourceScope = LocalLibrarySharedArtworkDetailSourceScopes.current.artist
    )

@Composable
internal fun playlistDetailSharedArtworkKey(playlistId: Long): LibrarySharedArtworkKey =
    LibrarySharedArtworkKey.Playlist(
        playlistId = playlistId,
        sourceScope = LocalLibrarySharedArtworkDetailSourceScopes.current.playlist
    )

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
    contentTopPadding: (S) -> Dp = { 0.dp },
    content: @Composable (S) -> Unit
) {
    val inheritedVisibilityScope = LocalLibraryAnimatedVisibilityScope.current

    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(durationMillis = 190, delayMillis = 40))
                .togetherWith(fadeOut(tween(durationMillis = 110)))
        },
        label = label
    ) animatedContent@{ visibleState ->
        val localTransitionIsRunning = transition.currentState != transition.targetState
        val inheritedTransitionIsRunning = inheritedVisibilityScope?.transition?.let {
            it.currentState != it.targetState
        } == true
        val visibilityScope = when {
            inheritedTransitionIsRunning -> inheritedVisibilityScope
            localTransitionIsRunning -> this@animatedContent
            else -> inheritedVisibilityScope ?: this@animatedContent
        }

        CompositionLocalProvider(
            LocalLibraryAnimatedVisibilityScope provides visibilityScope
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding(visibleState))
            ) {
                content(visibleState)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.librarySharedArtwork(
    key: LibrarySharedArtworkKey?
): Modifier {
    val state = rememberLibrarySharedArtworkState(key) ?: return this
    return librarySharedArtwork(state)
}

/**
 * Keeps a short-lived source-slot mask under the shared overlay. Compose intentionally lifts the
 * matched element out of its original draw layer; the treatment selects either a neutral surface
 * or the subdued artwork needed by Playlist while the collection fade completes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun LibrarySharedArtworkSource(
    key: LibrarySharedArtworkKey,
    shape: Shape,
    modifier: Modifier = Modifier,
    slotTreatment: LibrarySharedArtworkSourceSlotTreatment,
    content: @Composable (Modifier) -> Unit
) {
    val state = rememberLibrarySharedArtworkState(key)
    val matchFound = state?.sharedContentState?.isMatchFound == true

    Box(modifier = modifier.clip(shape)) {
        if (matchFound) {
            key("shared-source-replica") {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clearAndSetSemantics {}
                ) {
                    if (shouldDrawSharedArtworkSourceReplica(matchFound, slotTreatment)) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = SharedSourceReplicaAlpha }
                        ) {
                            content(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        key("shared-source-content") {
            content(
                Modifier
                    .fillMaxSize()
                    .then(if (state == null) Modifier else Modifier.librarySharedArtwork(state))
                    .clip(shape)
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberLibrarySharedArtworkState(
    key: LibrarySharedArtworkKey?
): LibrarySharedArtworkState? {
    if (key == null) return null
    val sharedTransitionScope = LocalLibrarySharedTransitionScope.current ?: return null
    val animatedVisibilityScope = LocalLibraryAnimatedVisibilityScope.current ?: return null
    val sharedContentState = with(sharedTransitionScope) {
        rememberSharedContentState(key = key)
    }

    return LibrarySharedArtworkState(
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedContentState = sharedContentState
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.librarySharedArtwork(
    state: LibrarySharedArtworkState
): Modifier = with(state.sharedTransitionScope) {
    sharedElement(
        sharedContentState = state.sharedContentState,
        animatedVisibilityScope = state.animatedVisibilityScope,
        boundsTransform = { _, _ ->
            tween(
                durationMillis = SharedArtworkDurationMillis,
                easing = FastOutSlowInEasing
            )
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
private data class LibrarySharedArtworkState(
    val sharedTransitionScope: SharedTransitionScope,
    val animatedVisibilityScope: AnimatedVisibilityScope,
    val sharedContentState: SharedTransitionScope.SharedContentState
)
