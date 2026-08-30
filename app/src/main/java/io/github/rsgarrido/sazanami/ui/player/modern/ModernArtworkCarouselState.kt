package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.rsgarrido.sazanami.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val MODERN_ARTWORK_SWIPE_DISTANCE_THRESHOLD_FRACTION = 0.25f
internal const val MODERN_ARTWORK_SWIPE_VELOCITY_THRESHOLD_PX_PER_SECOND = 900f
internal const val MODERN_ARTWORK_BUTTON_TRANSITION_DURATION_MILLIS = 400
internal const val MODERN_ARTWORK_ACCEPTED_DRAG_DURATION_MILLIS = 280
internal const val MODERN_ARTWORK_CANCELLED_DRAG_DURATION_MILLIS = 180

internal enum class ModernCarouselDirection {
    PREVIOUS,
    NEXT,
    NONE
}

internal data class ModernCarouselSongs(
    val current: Song,
    val previous: Song?,
    val next: Song?
) {
    fun previewFor(direction: ModernCarouselDirection): Song? {
        return when (direction) {
            ModernCarouselDirection.PREVIOUS -> previous
            ModernCarouselDirection.NEXT -> next
            ModernCarouselDirection.NONE -> null
        }
    }

    fun items(): List<ModernCarouselItem> {
        return buildList {
            previous
                ?.takeIf { song -> song.id != current.id }
                ?.let { song -> add(ModernCarouselItem(song, -1f)) }

            next
                ?.takeIf { song -> song.id != current.id && song.id != previous?.id }
                ?.let { song -> add(ModernCarouselItem(song, 1f)) }

            add(ModernCarouselItem(current, 0f, isCurrent = true))
        }
    }
}

internal data class ModernCarouselItem(
    val song: Song,
    val restingOffsetMultiplier: Float,
    val isCurrent: Boolean = false
)

internal data class ModernPendingSongTransition(
    val direction: ModernCarouselDirection,
    val sourceSongId: Long,
    val startedFromDrag: Boolean
)

@Stable
internal data class ModernArtworkCarouselPresentation(
    val songs: ModernCarouselSongs,
    val state: ModernArtworkCarouselState,
    val onPreviousButtonClick: () -> Unit,
    val onNextButtonClick: () -> Unit
)

@Stable
internal class ModernArtworkCarouselState(
    private val coroutineScope: CoroutineScope,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit
) {
    var offsetX by mutableFloatStateOf(0f)
        private set

    var artworkWidthPx by mutableFloatStateOf(1f)
        private set

    private var settleJob: Job? = null
    private var pendingExpiryJob: Job? = null
    private var pendingTransition: ModernPendingSongTransition? = null

    val dragProgress: Float
        get() = (abs(offsetX) / artworkWidthPx).coerceIn(0f, 1f)

    fun updateArtworkWidth(widthPx: Int) {
        if (widthPx > 0) {
            artworkWidthPx = widthPx.toFloat()
        }
    }

    fun startDrag() {
        settleJob?.cancel()
        clearPendingTransition()
    }

    fun dragBy(deltaX: Float) {
        offsetX = (offsetX + deltaX).coerceIn(-artworkWidthPx, artworkWidthPx)
    }

    fun settle(velocityX: Float, sourceSongId: Long) {
        settleJob?.cancel()

        val direction = resolveModernArtworkSwipe(
            offsetX = offsetX,
            artworkWidthPx = artworkWidthPx,
            velocityX = velocityX
        )
        if (direction == ModernCarouselDirection.NONE) {
            settleJob = coroutineScope.launch {
                animateOffsetTo(
                    targetOffset = 0f,
                    durationMillis = MODERN_ARTWORK_CANCELLED_DRAG_DURATION_MILLIS
                )
            }
            return
        }

        recordPendingTransition(direction, sourceSongId, startedFromDrag = true)
        when (direction) {
            ModernCarouselDirection.PREVIOUS -> onPrevious()
            ModernCarouselDirection.NEXT -> onNext()
            ModernCarouselDirection.NONE -> Unit
        }

        settleJob = coroutineScope.launch {
            delay(DRAG_NAVIGATION_CONFIRMATION_MILLIS)
            val pending = pendingTransition
            if (pending?.sourceSongId == sourceSongId && pending.startedFromDrag) {
                clearPendingTransition()
                animateOffsetTo(
                    targetOffset = 0f,
                    durationMillis = MODERN_ARTWORK_CANCELLED_DRAG_DURATION_MILLIS
                )
            }
        }
    }

    fun recordButtonNavigation(
        direction: ModernCarouselDirection,
        sourceSongId: Long
    ) {
        require(direction != ModernCarouselDirection.NONE)
        recordPendingTransition(direction, sourceSongId, startedFromDrag = false)
    }

    fun consumeTransitionForSongChange(newSongId: Long): ModernPendingSongTransition? {
        val transition = pendingTransition ?: return null
        clearPendingTransition()

        return transition.takeIf { pending -> pending.sourceSongId != newSongId }
    }

    suspend fun animateSongChange(
        direction: ModernCarouselDirection,
        durationMillis: Int
    ) {
        settleJob?.cancel()
        val targetOffset = when (direction) {
            ModernCarouselDirection.PREVIOUS -> artworkWidthPx
            ModernCarouselDirection.NEXT -> -artworkWidthPx
            ModernCarouselDirection.NONE -> return
        }
        animateOffsetTo(
            targetOffset = targetOffset,
            durationMillis = durationMillis
        )
    }

    fun resetForSongChange() {
        settleJob?.cancel()
        offsetX = 0f
    }

    private fun recordPendingTransition(
        direction: ModernCarouselDirection,
        sourceSongId: Long,
        startedFromDrag: Boolean
    ) {
        pendingExpiryJob?.cancel()
        pendingTransition = ModernPendingSongTransition(
            direction = direction,
            sourceSongId = sourceSongId,
            startedFromDrag = startedFromDrag
        )
        pendingExpiryJob = coroutineScope.launch {
            delay(PENDING_NAVIGATION_TIMEOUT_MILLIS)
            pendingTransition = null
        }
    }

    private fun clearPendingTransition() {
        pendingExpiryJob?.cancel()
        pendingExpiryJob = null
        pendingTransition = null
    }

    private suspend fun animateOffsetTo(targetOffset: Float, durationMillis: Int) {
        Animatable(offsetX).animateTo(
            targetValue = targetOffset,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        ) {
            offsetX = value
        }
    }
}

@Composable
internal fun rememberModernArtworkCarouselState(
    onPrevious: () -> Unit,
    onNext: () -> Unit
): ModernArtworkCarouselState {
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)
    val coroutineScope = rememberCoroutineScope()

    return remember(coroutineScope) {
        ModernArtworkCarouselState(
            coroutineScope = coroutineScope,
            onPrevious = { currentOnPrevious() },
            onNext = { currentOnNext() }
        )
    }
}

@Composable
internal fun rememberModernArtworkCarouselPresentation(
    currentSong: Song,
    previousPreviewSong: Song?,
    nextPreviewSong: Song?,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit
): ModernArtworkCarouselPresentation {
    val carouselState = rememberModernArtworkCarouselState(
        onPrevious = onPreviousClick,
        onNext = onNextClick
    )
    val actualCarouselSongs = ModernCarouselSongs(
        current = currentSong,
        previous = previousPreviewSong,
        next = nextPreviewSong
    )
    var displayedCarouselSongs by remember {
        mutableStateOf(actualCarouselSongs)
    }
    val latestActualCarouselSongs by rememberUpdatedState(actualCarouselSongs)

    LaunchedEffect(currentSong.id) {
        if (displayedCarouselSongs.current.id != currentSong.id) {
            val transition = carouselState.consumeTransitionForSongChange(
                newSongId = currentSong.id
            )
            val hasMatchingPreview = transition?.let { pending ->
                displayedCarouselSongs.previewFor(pending.direction)?.id ==
                    currentSong.id
            } ?: false

            if (transition != null && hasMatchingPreview) {
                carouselState.animateSongChange(
                    direction = transition.direction,
                    durationMillis = if (transition.startedFromDrag) {
                        MODERN_ARTWORK_ACCEPTED_DRAG_DURATION_MILLIS
                    } else {
                        MODERN_ARTWORK_BUTTON_TRANSITION_DURATION_MILLIS
                    }
                )
            }

            displayedCarouselSongs = latestActualCarouselSongs
            carouselState.resetForSongChange()
        }
    }

    LaunchedEffect(actualCarouselSongs) {
        if (displayedCarouselSongs.current.id == actualCarouselSongs.current.id) {
            displayedCarouselSongs = actualCarouselSongs
        }
    }

    return ModernArtworkCarouselPresentation(
        songs = displayedCarouselSongs,
        state = carouselState,
        onPreviousButtonClick = {
            carouselState.recordButtonNavigation(
                direction = ModernCarouselDirection.PREVIOUS,
                sourceSongId = currentSong.id
            )
            onPreviousClick()
        },
        onNextButtonClick = {
            carouselState.recordButtonNavigation(
                direction = ModernCarouselDirection.NEXT,
                sourceSongId = currentSong.id
            )
            onNextClick()
        }
    )
}

internal fun normalizedModernCarouselOffset(
    offsetX: Float,
    artworkWidthPx: Float
): Float = if (artworkWidthPx > 0f) {
    (offsetX / artworkWidthPx).coerceIn(-1f, 1f)
} else {
    0f
}

internal fun resolveModernArtworkSwipe(
    offsetX: Float,
    artworkWidthPx: Float,
    velocityX: Float
): ModernCarouselDirection {
    if (artworkWidthPx <= 0f) {
        return ModernCarouselDirection.NONE
    }

    if (abs(velocityX) >= MODERN_ARTWORK_SWIPE_VELOCITY_THRESHOLD_PX_PER_SECOND) {
        return if (velocityX > 0f) {
            ModernCarouselDirection.PREVIOUS
        } else {
            ModernCarouselDirection.NEXT
        }
    }

    val distanceThreshold =
        artworkWidthPx * MODERN_ARTWORK_SWIPE_DISTANCE_THRESHOLD_FRACTION
    return when {
        offsetX >= distanceThreshold -> ModernCarouselDirection.PREVIOUS
        offsetX <= -distanceThreshold -> ModernCarouselDirection.NEXT
        else -> ModernCarouselDirection.NONE
    }
}

private const val PENDING_NAVIGATION_TIMEOUT_MILLIS = 700L
private const val DRAG_NAVIGATION_CONFIRMATION_MILLIS = 120L
