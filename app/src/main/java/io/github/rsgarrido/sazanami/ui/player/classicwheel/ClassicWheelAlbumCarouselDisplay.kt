package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlin.math.abs

data class ClassicWheelAlbumCarouselItem(
    val title: String,
    val artist: String,
    val albumArtUri: Any?
)

@Composable
fun ClassicWheelAlbumCarouselDisplay(
    items: List<ClassicWheelAlbumCarouselItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ClassicWheelColors.screenBackground)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No albums found",
                color = ClassicWheelColors.screenText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Check your library",
                color = ClassicWheelColors.screenTextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        return
    }

    val safeSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val selectedItem = items[safeSelectedIndex]
    val carouselPositions = buildClassicWheelCarouselPositions(
        itemCount = items.size,
        selectedIndex = safeSelectedIndex
    )

    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClassicWheelColors.screenBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            carouselPositions.forEach { position ->
                key(position.itemIndex) {
                    val distanceFromCenter = abs(position.relativeOffset)
                    val item = items[position.itemIndex]

                    val targetCoverSize = when (distanceFromCenter) {
                        0 -> 166.dp
                        1 -> 136.dp
                        else -> 112.dp
                    }

                    val targetOffsetX = when (position.relativeOffset) {
                        -2 -> (-170).dp
                        -1 -> (-92).dp
                        0 -> 0.dp
                        1 -> 92.dp
                        2 -> 170.dp
                        else -> (position.relativeOffset * 92).dp
                    }

                    val targetScale = when (distanceFromCenter) {
                        0 -> 1f
                        1 -> 0.88f
                        else -> 0.76f
                    }

                    val targetAlpha = when (distanceFromCenter) {
                        0 -> 1f
                        1 -> 0.9f
                        else -> 0.62f
                    }

                    val targetRotationY = when {
                        position.relativeOffset < 0 -> 50f
                        position.relativeOffset > 0 -> -50f
                        else -> 0f
                    }

                    val targetTranslationY = when (distanceFromCenter) {
                        0 -> 0f
                        1 -> 8f
                        else -> 18f
                    }

                    val animatedCoverSize by animateDpAsState(
                        targetValue = targetCoverSize,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselCoverSize"
                    )

                    val animatedOffsetX by animateDpAsState(
                        targetValue = targetOffsetX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselOffsetX"
                    )

                    val animatedScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselScale"
                    )

                    val animatedAlpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselAlpha"
                    )

                    val animatedRotationY by animateFloatAsState(
                        targetValue = targetRotationY,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselRotationY"
                    )

                    val animatedTranslationY by animateFloatAsState(
                        targetValue = targetTranslationY,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselTranslationY"
                    )

                    Surface(
                        modifier = Modifier
                            .offset(x = animatedOffsetX)
                            .size(animatedCoverSize)
                            .zIndex(10f - distanceFromCenter)
                            .graphicsLayer {
                                rotationY = animatedRotationY
                                scaleX = animatedScale
                                scaleY = animatedScale
                                alpha = animatedAlpha
                                translationY = animatedTranslationY
                                cameraDistance = with(density) {
                                    18.dp.toPx()
                                }
                            },
                        shape = RoundedCornerShape(5.dp),
                        color = Color.Transparent,
                        shadowElevation = if (distanceFromCenter == 0) {
                            12.dp
                        } else {
                            4.dp
                        }
                    ) {
                        AsyncImage(
                            model = item.albumArtUri,
                            contentDescription = "Album art for ${item.title}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(5.dp))
                                .aspectRatio(1f),
                            contentScale = ContentScale.Crop,
                            error = painterResource(android.R.drawable.ic_media_play),
                            placeholder = painterResource(android.R.drawable.ic_media_play)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = selectedItem.title,
            color = ClassicWheelColors.screenText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = selectedItem.artist,
            color = ClassicWheelColors.screenTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private data class ClassicWheelCarouselPosition(
    val itemIndex: Int,
    val relativeOffset: Int
)

private fun buildClassicWheelCarouselPositions(
    itemCount: Int,
    selectedIndex: Int
): List<ClassicWheelCarouselPosition> {
    if (itemCount <= 0) {
        return emptyList()
    }

    val relativeOffsets = listOf(0, -1, 1, -2, 2)

    return relativeOffsets
        .map { relativeOffset ->
            val rawIndex = selectedIndex + relativeOffset
            val wrappedIndex = ((rawIndex % itemCount) + itemCount) % itemCount

            ClassicWheelCarouselPosition(
                itemIndex = wrappedIndex,
                relativeOffset = relativeOffset
            )
        }
        .distinctBy { position ->
            position.itemIndex
        }
}
