package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.audioquality.AudioQualityInfo
import io.github.rsgarrido.sazanami.player.audioquality.AudioQualityRepository

@Composable
internal fun ModernPlayerMetadataCarousel(
    carouselSongs: ModernCarouselSongs,
    carouselState: ModernArtworkCarouselState,
    audioQualityRepository: AudioQualityRepository,
    transitionStyle: ModernArtworkTransitionStyle,
    style: ModernPlayerStyle,
    layoutAppearance: ModernLayoutAppearance = ModernLayoutAppearance(),
    modifier: Modifier = Modifier,
    onPersistentContentBoundsChanged: (Rect) -> Unit = {},
    hidePersistentContent: Boolean = false,
    expandedContentAlpha: Float = 1f,
    loadExpandedMetadata: Boolean = true
) {
    var pageWidthPx by remember { mutableFloatStateOf(1f) }
    var stableAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var persistentContentSize by remember { mutableStateOf(IntSize.Zero) }

    fun publishStableDestination() {
        resolveStableMetadataDestination(
            anchorBounds = stableAnchorBounds,
            persistentContentSize = persistentContentSize
        )?.let(onPersistentContentBoundsChanged)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.width > 0) {
                    pageWidthPx = size.width.toFloat()
                }
            }
            .onGloballyPositioned { coordinates ->
                stableAnchorBounds = coordinates.boundsInRoot()
                publishStableDestination()
            },
        contentAlignment = if (layoutAppearance.metadataAlignment == ModernMetadataAlignment.CENTER) {
            Alignment.TopCenter
        } else {
            Alignment.TopStart
        }
    ) {
        carouselSongs.items().forEach { item ->
            key(item.song.id) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val gestureOffset = normalizedModernCarouselOffset(
                                offsetX = carouselState.offsetX,
                                artworkWidthPx = carouselState.artworkWidthPx
                            )
                            val transform = modernMetadataPageTransform(
                                style = transitionStyle,
                                gestureOffset = gestureOffset,
                                restingOffset = item.restingOffsetMultiplier,
                                isCurrent = item.isCurrent
                            )
                            translationX = transform.translationMultiplier * pageWidthPx
                            scaleX = transform.scale
                            scaleY = transform.scale
                            alpha = transform.alpha
                            rotationY = transform.rotationY
                            transformOrigin = TransformOrigin.Center
                            if (transform.rotationY != 0f) {
                                cameraDistance =
                                    COVER_FLOW_METADATA_CAMERA_DISTANCE_MULTIPLIER * density
                            }
                        },
                    contentAlignment = if (
                        layoutAppearance.metadataAlignment == ModernMetadataAlignment.CENTER
                    ) Alignment.TopCenter else Alignment.TopStart
                ) {
                    ModernPlayerMetadataPage(
                        song = item.song,
                        audioQualityRepository = audioQualityRepository,
                        style = style,
                        layoutAppearance = layoutAppearance,
                        onPersistentContentSizeChanged = if (item.isCurrent) {
                            { size ->
                                persistentContentSize = size
                                publishStableDestination()
                            }
                        } else {
                            {}
                        },
                        hidePersistentContent = hidePersistentContent,
                        expandedContentAlpha = expandedContentAlpha,
                        loadExpandedMetadata = loadExpandedMetadata
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernPlayerMetadataPage(
    song: Song,
    audioQualityRepository: AudioQualityRepository,
    style: ModernPlayerStyle,
    layoutAppearance: ModernLayoutAppearance,
    onPersistentContentSizeChanged: (IntSize) -> Unit,
    hidePersistentContent: Boolean,
    expandedContentAlpha: Float,
    loadExpandedMetadata: Boolean
) {
    var audioQualityInfo by remember(song.id, song.filePath) {
        mutableStateOf<AudioQualityInfo?>(null)
    }

    LaunchedEffect(
        song.id,
        song.filePath,
        loadExpandedMetadata,
        layoutAppearance.showAudioQualityBadge
    ) {
        audioQualityInfo = if (
            loadExpandedMetadata && layoutAppearance.showAudioQualityBadge
        ) {
            audioQualityRepository.getAudioQualityInfo(song)
        } else {
            null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ModernPlayerMetadata(
            currentSong = song,
            style = style,
            alignment = layoutAppearance.metadataAlignment,
            onPersistentContentSizeChanged = onPersistentContentSizeChanged,
            hidePersistentContent = hidePersistentContent,
            expandedContentAlpha = expandedContentAlpha
        )

        if (layoutAppearance.showAudioQualityBadge) {
            ModernPlayerAudioQualityBadge(
                audioQualityInfo = audioQualityInfo,
                style = style,
                modifier = Modifier
                    .align(
                        if (layoutAppearance.metadataAlignment == ModernMetadataAlignment.CENTER) {
                            Alignment.CenterHorizontally
                        } else {
                            Alignment.Start
                        }
                    )
                    .padding(top = 12.dp)
                    .graphicsLayer { alpha = expandedContentAlpha }
            )
        }
    }
}

@Composable
internal fun ModernPlayerMetadata(
    currentSong: Song,
    style: ModernPlayerStyle,
    alignment: ModernMetadataAlignment = ModernMetadataAlignment.LEFT,
    onPersistentContentSizeChanged: (IntSize) -> Unit = {},
    hidePersistentContent: Boolean = false,
    expandedContentAlpha: Float = 1f
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignment == ModernMetadataAlignment.CENTER) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged(onPersistentContentSizeChanged)
                .hiddenFromDefaultMorph(hidePersistentContent)
        ) {
            Text(
                text = currentSong.title.ifBlank { "Unknown Title" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = style.contentColor,
                maxLines = 2,
                textAlign = alignment.textAlign(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = currentSong.artist.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.titleMedium,
                color = style.secondaryContentColor,
                maxLines = 1,
                textAlign = alignment.textAlign(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = currentSong.album.ifBlank { "Unknown Album" },
            style = MaterialTheme.typography.bodyMedium,
            color = style.tertiaryContentColor,
            maxLines = 1,
            textAlign = alignment.textAlign(),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = expandedContentAlpha }
        )
    }
}

private fun ModernMetadataAlignment.textAlign(): TextAlign = when (this) {
    ModernMetadataAlignment.LEFT -> TextAlign.Start
    ModernMetadataAlignment.CENTER -> TextAlign.Center
}

internal fun resolveStableMetadataDestination(
    anchorBounds: Rect?,
    persistentContentSize: IntSize
): Rect? {
    if (!anchorBounds.isValidMorphRect() ||
        persistentContentSize.width <= 0 ||
        persistentContentSize.height <= 0
    ) {
        return null
    }

    return Rect(
        left = anchorBounds!!.left,
        top = anchorBounds.top,
        right = anchorBounds.left + persistentContentSize.width,
        bottom = anchorBounds.top + persistentContentSize.height
    )
}
