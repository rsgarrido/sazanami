package com.example.cdplaya.ui.player.modern

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.example.cdplaya.data.Song

internal enum class ModernArtworkRenderingPolicy {
    Slide,
    DepthScale,
    Parallax,
    CoverFlow,
    StackReveal
}

internal fun modernArtworkRenderingPolicy(
    style: ModernArtworkTransitionStyle
): ModernArtworkRenderingPolicy = when (style) {
    ModernArtworkTransitionStyle.SLIDE -> ModernArtworkRenderingPolicy.Slide
    ModernArtworkTransitionStyle.DEPTH_SCALE -> ModernArtworkRenderingPolicy.DepthScale
    ModernArtworkTransitionStyle.PARALLAX -> ModernArtworkRenderingPolicy.Parallax
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
                    translationX = transform.translationMultiplier *
                        carouselState.artworkWidthPx
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
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
                            .then(pageModifier)
                            .background(style.artworkContainerColor)
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
    val sourceMemoryCachePlaceholderKey: String?
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
            ?.takeIf { target != null && it.isNotBlank() }
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
    val innerRadius = (
            appearance.shape.cornerRadiusDp.toFloat() - inset.value
            ).coerceAtLeast(0f).dp
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
    retainPreviousPainter: Boolean = true,
    requestSizePx: Int? = null
) {
    val context = LocalContext.current
    val fallbackPainter = painterResource(R.drawable.ic_media_play)
    val painterRetentionKey = if (retainPreviousPainter) Unit else currentSong.id
    var retainedPainter by remember(painterRetentionKey) {
        mutableStateOf<Painter?>(null)
    }
    val requestPolicy = modernArtworkRequestPolicy(
        expandedTargetSizePx = requestSizePx,
        artworkIdentity = currentSong.albumArtUri?.toString()
    )
    val request = remember(currentSong.id, currentSong.albumArtUri, requestPolicy) {
        ImageRequest.Builder(context)
            .data(currentSong.albumArtUri)
            .crossfade(transitionDurationMillis)
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
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        transform = { state ->
            when (state) {
                is AsyncImagePainter.State.Loading -> state.copy(
                    painter = retainedPainter ?: fallbackPainter
                )
                is AsyncImagePainter.State.Error -> state.copy(
                    painter = fallbackPainter
                )
                else -> state
            }
        },
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> retainedPainter = state.painter
                is AsyncImagePainter.State.Error -> retainedPainter = fallbackPainter
                else -> Unit
            }
        },
        contentScale = contentScale
    )
}

private const val MAX_CONTAINED_ARTWORK_INSET_DP = 16f
