package com.example.cdplaya.ui.player.modern

import android.R
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
                        ModernPlayerAlbumImage(
                            currentSong = item.song,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = appearance.fit.contentScale()
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
        ModernPlayerAlbumImage(
            currentSong = song,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = appearance.fit.contentScale()
        )
    }
}

private fun ModernArtworkFit.contentScale(): ContentScale = when (this) {
    ModernArtworkFit.CROP -> ContentScale.Crop
    ModernArtworkFit.SHOW_FULL -> ContentScale.Fit
}

@Composable
internal fun ModernPlayerAlbumImage(
    currentSong: Song,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    transitionDurationMillis: Int = ModernPlayerDefaults.SongTransitionDurationMillis,
    retainPreviousPainter: Boolean = true
) {
    val context = LocalContext.current
    val fallbackPainter = painterResource(R.drawable.ic_media_play)
    val painterRetentionKey = if (retainPreviousPainter) Unit else currentSong.id
    var retainedPainter by remember(painterRetentionKey) {
        mutableStateOf<Painter?>(null)
    }
    val request = remember(currentSong.id, currentSong.albumArtUri) {
        ImageRequest.Builder(context)
            .data(currentSong.albumArtUri)
            .crossfade(transitionDurationMillis)
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
