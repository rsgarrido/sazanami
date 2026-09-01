package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import kotlin.math.roundToInt

@Composable
fun ClassicWheelNowPlayingDisplay(
    currentSong: Song?,
    currentPosition: Int,
    duration: Int,
    isCurrentSongFavorite: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    musicVolume: Int,
    maxMusicVolume: Int,
    isVolumeIndicatorVisible: Boolean,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit
    ,morphBounds: ClassicWheelMorphBounds? = null
    ,sharedContentVisible: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = currentSong?.albumArtUri,
                contentDescription = currentSong?.let { song ->
                    "Album art for ${song.title}"
                },
                modifier = Modifier
                    .weight(0.95f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .onGloballyPositioned { morphBounds?.updateExpandedArtwork(it.boundsInRoot()) }
                    .sharedClassicContentVisibility(sharedContentVisible),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_media_play),
                placeholder = painterResource(android.R.drawable.ic_media_play)
            )

            Column(
                modifier = Modifier.weight(1.15f)
            ) {
                Text(
                    text = currentSong?.title?.ifBlank { "Unknown Title" }
                        ?: "No song selected",
                    color = ClassicWheelColors.screenText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { morphBounds?.updateExpandedTitle(it.boundsInRoot()) }
                        .sharedClassicContentVisibility(sharedContentVisible)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currentSong?.artist?.ifBlank { "Unknown Artist" }
                        ?: "Choose a song",
                    color = ClassicWheelColors.screenText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { morphBounds?.updateExpandedArtist(it.boundsInRoot()) }
                        .sharedClassicContentVisibility(sharedContentVisible)
                )

                Text(
                    text = currentSong?.album?.ifBlank { "Unknown Album" }
                        ?: "",
                    color = ClassicWheelColors.screenTextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShuffleClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) {
                                ClassicWheelColors.selectionAccent
                            } else {
                                ClassicWheelColors.screenText
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onRepeatClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode == RepeatMode.OFF) {
                                ClassicWheelColors.screenText
                            } else {
                                ClassicWheelColors.selectionAccent
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentSong?.let { song ->
                                onToggleFavoriteClick(song)
                            }
                        },
                        modifier = Modifier.size(38.dp),
                        enabled = currentSong != null
                    ) {
                        Icon(
                            imageVector = if (isCurrentSongFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = "Favorite",
                            tint = if (isCurrentSongFavorite) {
                                ClassicWheelColors.selectionAccent
                            } else {
                                ClassicWheelColors.screenText
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenUpNextClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "Open queues",
                            tint = ClassicWheelColors.screenText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = buildPlaybackModeText(
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode
                    ),
                    color = ClassicWheelColors.screenTextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isVolumeIndicatorVisible) {
            ClassicWheelVolumeProgress(
                volume = musicVolume,
                maxVolume = maxMusicVolume
            )
        } else {
            ClassicWheelProgress(
                currentPosition = currentPosition,
                duration = duration,
                onSeekChange = onSeekChange
            )
        }
    }
}

private fun Modifier.sharedClassicContentVisibility(visible: Boolean): Modifier = if (visible) this else
    graphicsLayer { alpha = 0f }.clearAndSetSemantics { }

@Composable
private fun ClassicWheelProgress(
    currentPosition: Int,
    duration: Int,
    onSeekChange: (Int) -> Unit
) {
    val safeDuration = duration.coerceAtLeast(1)
    val clampedPosition = currentPosition.coerceIn(0, safeDuration)
    val progress = clampedPosition.toFloat() / safeDuration.toFloat()

    var progressBarWidthPx by remember {
        mutableStateOf(1)
    }

    fun seekToPositionFromX(x: Float) {
        val seekRatio = (x / progressBarWidthPx.toFloat())
            .coerceIn(0f, 1f)

        onSeekChange(
            (seekRatio * safeDuration).roundToInt()
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(clampedPosition),
            color = ClassicWheelColors.screenText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .padding(horizontal = 10.dp)
                .onSizeChanged { size ->
                    progressBarWidthPx = size.width.coerceAtLeast(1)
                }
                .pointerInput(safeDuration, progressBarWidthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            seekToPositionFromX(offset.x)
                        },
                        onDrag = { change, _ ->
                            seekToPositionFromX(change.position.x)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            ClassicWheelBarCanvas(
                progress = progress
            )
        }

        Text(
            text = "-${formatTime((safeDuration - clampedPosition).coerceAtLeast(0))}",
            color = ClassicWheelColors.screenText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ClassicWheelVolumeProgress(
    volume: Int,
    maxVolume: Int
) {
    val safeMaxVolume = maxVolume.coerceAtLeast(1)
    val clampedVolume = volume.coerceIn(0, safeMaxVolume)
    val progress = clampedVolume.toFloat() / safeMaxVolume.toFloat()
    val volumePercent = (progress * 100).roundToInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Volume",
            color = ClassicWheelColors.screenText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            ClassicWheelBarCanvas(
                progress = progress
            )
        }

        Text(
            text = "$volumePercent%",
            color = ClassicWheelColors.screenText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ClassicWheelBarCanvas(
    progress: Float
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val trackHeight = 10.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val trackCorner = CornerRadius(
            x = trackHeight / 2f,
            y = trackHeight / 2f
        )

        val progressWidth = size.width * progress.coerceIn(0f, 1f)

        drawRoundRect(
            color = Color(0xFFD8D8D2),
            topLeft = Offset(
                x = 0f,
                y = trackTop
            ),
            size = Size(
                width = size.width,
                height = trackHeight
            ),
            cornerRadius = trackCorner
        )

        drawRoundRect(
            color = Color(0xFF67AEE7),
            topLeft = Offset(
                x = 0f,
                y = trackTop
            ),
            size = Size(
                width = progressWidth,
                height = trackHeight
            ),
            cornerRadius = trackCorner
        )

        val handleWidth = 5.dp.toPx()
        val handleHeight = 28.dp.toPx()
        val handleCorner = CornerRadius(
            x = 3.dp.toPx(),
            y = 3.dp.toPx()
        )

        val handleLeft = (progressWidth - handleWidth / 2f)
            .coerceIn(0f, size.width - handleWidth)

        drawRoundRect(
            color = Color(0xFF9DB2FF),
            topLeft = Offset(
                x = handleLeft,
                y = (size.height - handleHeight) / 2f
            ),
            size = Size(
                width = handleWidth,
                height = handleHeight
            ),
            cornerRadius = handleCorner
        )
    }
}

private fun formatTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun buildPlaybackModeText(
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode
): String {
    val shuffleText = if (isShuffleEnabled) {
        "Shuffle On"
    } else {
        "Shuffle Off"
    }

    val repeatText = when (repeatMode) {
        RepeatMode.OFF -> "Repeat Off"
        RepeatMode.ALL -> "Repeat All"
        RepeatMode.ONE -> "Repeat One"
    }

    return "$shuffleText • $repeatText"
}
