package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

internal data class RetainedArtworkRequest(
    val generation: Long,
    val artworkIdentity: String?,
    val requestKey: Any?
)

internal data class ResolvedArtwork<T>(
    val artworkIdentity: String,
    val value: T
)

internal data class RetainedArtworkLoadState<T>(
    val currentRequest: RetainedArtworkRequest? = null,
    val displayedArtwork: ResolvedArtwork<T>? = null,
    val isLoading: Boolean = false,
    val isFallbackResolved: Boolean = false
)

internal fun <T> beginRetainedArtworkRequest(
    previousState: RetainedArtworkLoadState<T>,
    request: RetainedArtworkRequest
): RetainedArtworkLoadState<T> {
    val previousRequest = previousState.currentRequest
    return when {
        previousRequest?.let { previous ->
            previous.requestKey == request.requestKey &&
                previous.artworkIdentity == request.artworkIdentity
        } == true -> previousState

        request.artworkIdentity == null -> RetainedArtworkLoadState(
            currentRequest = request,
            displayedArtwork = null,
            isLoading = false,
            isFallbackResolved = true
        )

        else -> RetainedArtworkLoadState(
            currentRequest = request,
            displayedArtwork = previousState.displayedArtwork,
            isLoading = true,
            isFallbackResolved = false
        )
    }
}

internal fun <T> completeRetainedArtworkRequest(
    currentState: RetainedArtworkLoadState<T>,
    request: RetainedArtworkRequest,
    value: T
): RetainedArtworkLoadState<T> {
    if (currentState.currentRequest != request) return currentState
    val artworkIdentity = request.artworkIdentity ?: return currentState
    return RetainedArtworkLoadState(
        currentRequest = request,
        displayedArtwork = ResolvedArtwork(artworkIdentity, value),
        isLoading = false,
        isFallbackResolved = false
    )
}

internal fun <T> failRetainedArtworkRequest(
    currentState: RetainedArtworkLoadState<T>,
    request: RetainedArtworkRequest
): RetainedArtworkLoadState<T> {
    if (currentState.currentRequest != request) return currentState
    val resolvedForSameArtwork = currentState.displayedArtwork
        ?.takeIf { it.artworkIdentity == request.artworkIdentity }
    return RetainedArtworkLoadState(
        currentRequest = request,
        displayedArtwork = resolvedForSameArtwork,
        isLoading = false,
        isFallbackResolved = resolvedForSameArtwork == null
    )
}

private class RetainedArtworkController<T> {
    private var nextGeneration = 0L

    var state = RetainedArtworkLoadState<T>()
        private set

    fun begin(artworkIdentity: String?, requestKey: Any?): RetainedArtworkRequest {
        state.currentRequest?.let { current ->
            if (current.artworkIdentity == artworkIdentity && current.requestKey == requestKey) {
                return current
            }
        }
        val request = RetainedArtworkRequest(
            generation = ++nextGeneration,
            artworkIdentity = artworkIdentity,
            requestKey = requestKey
        )
        state = beginRetainedArtworkRequest(state, request)
        return request
    }

    fun complete(request: RetainedArtworkRequest, value: T) {
        state = completeRetainedArtworkRequest(state, request, value)
    }

    fun fail(request: RetainedArtworkRequest) {
        state = failRetainedArtworkRequest(state, request)
    }
}

@Composable
internal fun RetainedArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    artworkIdentity: String? = model?.toString()?.takeIf(String::isNotBlank),
    requestKey: Any? = artworkIdentity,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error
) {
    val controller = remember { RetainedArtworkController<Painter>() }
    val request = controller.begin(artworkIdentity, requestKey)

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        transform = { state ->
            val retained = controller.state.displayedArtwork
            when (state) {
                is AsyncImagePainter.State.Loading -> retained?.let { artwork ->
                    state.copy(painter = artwork.value)
                } ?: placeholder?.let { painter ->
                    state.copy(painter = painter)
                } ?: state

                is AsyncImagePainter.State.Error -> retained
                    ?.takeIf { artwork ->
                        artwork.artworkIdentity == request.artworkIdentity
                    }
                    ?.let { artwork -> state.copy(painter = artwork.value) }
                    ?: (if (request.artworkIdentity == null) fallback else error)
                        ?.let { painter -> state.copy(painter = painter) }
                    ?: state

                else -> state
            }
        },
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> controller.complete(request, state.painter)
                is AsyncImagePainter.State.Error -> controller.fail(request)
                else -> Unit
            }
        }
    )
}
