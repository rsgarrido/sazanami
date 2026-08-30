package io.github.rsgarrido.sazanami.ui.player.modern

import android.R
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import io.github.rsgarrido.sazanami.data.Song

internal enum class ModernArtworkRenderingPolicy {
    Slide,
    DepthScale,
    CoverFlow,
    StackReveal
}

internal fun modernArtworkRenderingPolicy(
    style: ModernArtworkTransitionStyle
): ModernArtworkRenderingPolicy = when (style) {
    ModernArtworkTransitionStyle.SLIDE -> ModernArtworkRenderingPolicy.Slide
    ModernArtworkTransitionStyle.DEPTH_SCALE -> ModernArtworkRenderingPolicy.DepthScale
    ModernArtworkTransitionStyle.COVER_FLOW -> ModernArtworkRenderingPolicy.CoverFlow
    ModernArtworkTransitionStyle.STACK_REVEAL -> ModernArtworkRenderingPolicy.StackReveal
}

@Composable
internal fun ModernPlayerArtwork(
    carouselSongs: ModernCarouselSongs,
    carouselState: ModernArtworkCarouselState,
    artworkSize: Dp,
    transitionStyle: ModernArtworkTransitionStyle,
    style: ModernPlayerStyle,
    appearance: ModernArtworkAppearance = ModernArtworkAppearance(),
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    renderArtwork: Boolean = true
) {
    val horizontalDragState = rememberDraggableState { deltaX ->
        carouselState.dragBy(deltaX)
    }
    val carouselItems = carouselSongs.items()

    Box(
        modifier = modifier
            .size(artworkSize)
            .onSizeChanged { size ->
                carouselState.updateArtworkWidth(size.width)
            }
            .draggable(
                state = horizontalDragState,
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                onDragStarted = { carouselState.startDrag() },
                onDragStopped = { velocityX ->
                    carouselState.settle(
                        velocityX = velocityX,
                        sourceSongId = carouselSongs.current.id
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (renderArtwork) {
            ModernPlayerArtworkPages(
                carouselItems = carouselItems,
                carouselState = carouselState,
                transitionStyle = transitionStyle,
                style = style,
                appearance = appearance,
                artworkSize = artworkSize,
                decoratePages = true
            )
        }
    }
}

@Composable
internal fun ModernPlayerArtworkPages(
    carouselItems: List<ModernCarouselItem>,
    carouselState: ModernArtworkCarouselState,
    transitionStyle: ModernArtworkTransitionStyle,
    style: ModernPlayerStyle,
    artworkSize: Dp,
    decoratePages: Boolean,
    appearance: ModernArtworkAppearance = ModernArtworkAppearance(),
    fitFrameProgress: Float = 1f,
    artworkRequestSizePx: Int? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        carouselItems.forEach { item ->
            key(item.song.id) {
                val pageModifier = Modifier.graphicsLayer {
                    val gestureOffset = normalizedModernCarouselOffset(
                        offsetX = carouselState.offsetX,
                        artworkWidthPx = carouselState.artworkWidthPx
                    )
                    val transform = modernArtworkPageTransform(
                        style = transitionStyle,
                        gestureOffset = gestureOffset,
                        restingOffset = item.restingOffsetMultiplier,
                        isCurrent = item.isCurrent
                    )
                    translationX = modernArtworkTranslationPx(
                        translationMultiplier = transform.translationMultiplier,
                        artworkWidthPx = carouselState.artworkWidthPx
                    )
                    scaleX = transform.scale
                    scaleY = transform.scale
                    alpha = transform.alpha
                    rotationY = transform.rotationY
                    if (transform.rotationY != 0f) {
                        cameraDistance = COVER_FLOW_CAMERA_DISTANCE_MULTIPLIER * density
                    }
                }
                val contentDescription = if (item.isCurrent) {
                    "Album art for ${item.song.title}"
                } else {
                    null
                }
                if (decoratePages) {
                    ModernPlayerArtworkCard(
                        song = item.song,
                        artworkSize = artworkSize,
                        style = style,
                        appearance = appearance,
                        fitFrameProgress = fitFrameProgress,
                        artworkRequestSizePx = artworkRequestSizePx,
                        contentDescription = contentDescription,
                        elevation = if (item.isCurrent) {
                            appearance.shadow.elevationDp.dp
                        } else {
                            (appearance.shadow.elevationDp * 0.55f).dp
                        },
                        modifier = pageModifier
                    )
                } else {
                    val clippingPolicy = modernArtworkTransitionClippingPolicy()
                    val pageShape = RoundedCornerShape(
                        modernArtworkMorphCornerRadiusDp(
                            appearance = appearance,
                            transitionProgress = fitFrameProgress
                        ).dp
                    )
                    val pageElevation = appearance.shadow.elevationDp *
                        fitFrameProgress.coerceIn(0f, 1f) *
                        (if (item.isCurrent) 1f else 0.55f)
                    Box(
                        modifier = pageModifier
                            .size(artworkSize)
                            .shadow(pageElevation.dp, pageShape, clip = false)
                            .background(style.artworkContainerColor, pageShape)
                            .graphicsLayer {
                                shape = pageShape
                                clip = clippingPolicy.clipArtworkCardToShape
                            }
                    ) {
                        ModernPlayerFramedAlbumImage(
                            currentSong = item.song,
                            contentDescription = contentDescription,
                            artworkSize = artworkSize,
                            appearance = appearance,
                            fitFrameProgress = fitFrameProgress,
                            requestSizePx = artworkRequestSizePx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernPlayerArtworkCard(
    song: Song,
    artworkSize: Dp,
    style: ModernPlayerStyle,
    appearance: ModernArtworkAppearance,
    fitFrameProgress: Float,
    artworkRequestSizePx: Int?,
    contentDescription: String?,
    elevation: Dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = Modifier
            .size(artworkSize)
            .then(modifier),
        shape = RoundedCornerShape(appearance.shape.cornerRadiusDp.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = style.artworkContainerColor
        )
    ) {
        ModernPlayerFramedAlbumImage(
            currentSong = song,
            contentDescription = contentDescription,
            artworkSize = artworkSize,
            appearance = appearance,
            fitFrameProgress = fitFrameProgress,
            requestSizePx = artworkRequestSizePx,
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal data class ModernArtworkFitLayout(
    val contentScale: ContentScale,
    val frameInsetFraction: Float
)

internal data class ModernArtworkTransitionClippingPolicy(
    val clipTransitionViewportToRestingBounds: Boolean,
    val clipArtworkCardToShape: Boolean
)

internal fun modernArtworkTransitionClippingPolicy() =
    MODERN_ARTWORK_TRANSITION_CLIPPING_POLICY

internal fun modernArtworkTranslationPx(
    translationMultiplier: Float,
    artworkWidthPx: Float
): Float = translationMultiplier * artworkWidthPx.coerceAtLeast(0f)

internal fun modernArtworkMorphCornerRadiusDp(
    appearance: ModernArtworkAppearance,
    transitionProgress: Float
): Float = interpolateMorphCornerRadius(
    collapsedRadius = 10f,
    expandedRadius = appearance.shape.cornerRadiusDp.toFloat(),
    progress = transitionProgress
)

internal fun modernArtworkFitLayout(fit: ModernArtworkFit): ModernArtworkFitLayout =
    when (fit) {
        ModernArtworkFit.CROP -> ModernArtworkFitLayout(
            contentScale = ContentScale.Crop,
            frameInsetFraction = 0f
        )
        ModernArtworkFit.SHOW_FULL -> ModernArtworkFitLayout(
            contentScale = ContentScale.Fit,
            frameInsetFraction = 0.055f
        )
    }

internal fun modernArtworkFrameInsetDp(
    fit: ModernArtworkFit,
    artworkSizeDp: Float,
    transitionProgress: Float = 1f
): Float = (
        artworkSizeDp.coerceAtLeast(0f) *
                modernArtworkFitLayout(fit).frameInsetFraction *
                transitionProgress.coerceIn(0f, 1f)
        ).coerceIn(0f, MAX_CONTAINED_ARTWORK_INSET_DP)

internal data class ModernArtworkRequestPolicy(
    val targetSizePx: Int?,
    val exactSize: Boolean,
    val sourceMemoryCachePlaceholderKey: String?,
    val expandedMemoryCacheKey: String?
)

internal fun modernArtworkRequestPolicy(
    expandedTargetSizePx: Int?,
    artworkIdentity: String? = null
): ModernArtworkRequestPolicy {
    val target = expandedTargetSizePx?.takeIf { it > 0 }
    return ModernArtworkRequestPolicy(
        targetSizePx = target,
        exactSize = target != null,
        sourceMemoryCachePlaceholderKey = artworkIdentity
            ?.takeIf { target != null && it.isNotBlank() },
        expandedMemoryCacheKey = artworkIdentity
            ?.takeIf { target != null && it.isNotBlank() }
            ?.let { identity ->
                modernExpandedArtworkMemoryCacheKey(
                    artworkIdentity = identity,
                    targetSizePx = requireNotNull(target)
                )
            }
    )
}

@Composable
internal fun ModernPlayerFramedAlbumImage(
    currentSong: Song,
    contentDescription: String?,
    artworkSize: Dp,
    appearance: ModernArtworkAppearance,
    modifier: Modifier = Modifier,
    fitFrameProgress: Float = 1f,
    requestSizePx: Int? = null
) {
    val fitLayout = modernArtworkFitLayout(appearance.fit)
    val inset = modernArtworkFrameInsetDp(
        fit = appearance.fit,
        artworkSizeDp = artworkSize.value,
        transitionProgress = fitFrameProgress
    ).dp
    val outerRadius = modernArtworkMorphCornerRadiusDp(
        appearance = appearance,
        transitionProgress = fitFrameProgress
    )
    val innerRadius = (outerRadius - inset.value).coerceAtLeast(0f).dp
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ModernPlayerAlbumImage(
            currentSong = currentSong,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(innerRadius)),
            contentScale = fitLayout.contentScale,
            requestSizePx = requestSizePx
        )
    }
}

@Composable
internal fun ModernPlayerAlbumImage(
    currentSong: Song,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    transitionDurationMillis: Int = ModernPlayerDefaults.SongTransitionDurationMillis,
    retainCurrentPainterDuringRefinement: Boolean = true,
    requestSizePx: Int? = null
) {
    val context = LocalContext.current
    val artworkIdentity = currentSong.albumArtUri?.toString()?.takeIf(String::isNotBlank)
    val requestPolicy = modernArtworkRequestPolicy(
        expandedTargetSizePx = requestSizePx,
        artworkIdentity = artworkIdentity
    )
    var readinessState by remember(artworkIdentity) {
        mutableStateOf(
            ModernArtworkReadinessState<Painter>(
                currentArtworkIdentity = artworkIdentity
            )
        )
    }
    val requestQuality = if (requestPolicy.exactSize) {
        ModernArtworkQuality.Expanded
    } else {
        ModernArtworkQuality.Temporary
    }
    val request = remember(currentSong.id, currentSong.albumArtUri, requestPolicy) {
        ImageRequest.Builder(context)
            .data(currentSong.albumArtUri)
            .crossfade(transitionDurationMillis)
            .placeholder(R.drawable.ic_media_play)
            .error(R.drawable.ic_media_play)
            .apply {
                requestPolicy.targetSizePx?.let { targetSize ->
                    size(targetSize)
                    if (requestPolicy.exactSize) {
                        precision(Precision.EXACT)
                    }
                }
                requestPolicy.sourceMemoryCachePlaceholderKey?.let { key ->
                    placeholderMemoryCacheKey(key)
                }
                requestPolicy.expandedMemoryCacheKey?.let { key ->
                    memoryCacheKey(key)
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        transform = { state ->
            val readyPainter = if (retainCurrentPainterDuringRefinement) {
                preferredModernArtworkReadyLayer(readinessState)?.value
            } else {
                null
            }
            when (state) {
                is AsyncImagePainter.State.Loading -> readyPainter?.let { painter ->
                    state.copy(painter = painter)
                } ?: state
                is AsyncImagePainter.State.Error -> readyPainter?.let { painter ->
                    state.copy(painter = painter)
                } ?: state
                else -> state
            }
        },
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> {
                    val identity = artworkIdentity
                    if (identity != null) {
                        readinessState = acceptModernArtworkReadyLayer(
                            state = readinessState,
                            layer = ModernArtworkReadyLayer(
                                artworkIdentity = identity,
                                quality = requestQuality,
                                value = state.painter
                            )
                        )
                    }
                }
                else -> Unit
            }
        },
        contentScale = contentScale
    )
}

private const val MAX_CONTAINED_ARTWORK_INSET_DP = 16f

private val MODERN_ARTWORK_TRANSITION_CLIPPING_POLICY =
    ModernArtworkTransitionClippingPolicy(
        clipTransitionViewportToRestingBounds = false,
        clipArtworkCardToShape = true
    )
