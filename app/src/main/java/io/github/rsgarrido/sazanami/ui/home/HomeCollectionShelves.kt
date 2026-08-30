package io.github.rsgarrido.sazanami.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.data.home.HomePin
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.library.ArtistPicture
import io.github.rsgarrido.sazanami.ui.library.LocalArtistPictureUi
import io.github.rsgarrido.sazanami.ui.playlist.PlaylistArtwork
import kotlin.math.roundToInt

@Composable
fun HomePinnedShelf(
    pins: List<ResolvedHomePin>,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pins.isEmpty()) return

    val homePinUi = LocalHomePinUi.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    val persistedPins = pins.take(HomePin.MAX_COUNT)
    val persistedPinIds = persistedPins.map { pin -> pin.pin.id }
    val persistedOrderKey = persistedPinIds.joinToString(separator = "|")

    var isEditing by remember { mutableStateOf(false) }

    /*
     * visualPinIds is the optimistic UI order used while dragging. Resolved pin
     * objects remain authoritative inputs so playlist artwork metadata and
     * automatic-collage songs can update without changing the persisted order.
     *
     * Keying this state to the persisted order is intentional:
     *
     * - A drag can update visualPinIds immediately without waiting for DataStore.
     * - Pressing DONE does not reset the optimistic order.
     * - Once the persisted pin order actually changes, Compose recreates this
     *   state from the authoritative persisted list.
     *
     * This avoids a race between edit mode and an asynchronous LaunchedEffect.
     */
    var visualPinIds by remember(persistedOrderKey) {
        mutableStateOf(persistedPinIds)
    }
    val visualPins = homePinsInVisualOrder(
        authoritativePins = persistedPins,
        visualPinIds = visualPinIds
    )

    var draggedPinId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    fun finishEditing() {
        isEditing = false
        draggedPinId = null
        dragStartIndex = -1
        dragOffsetX = 0f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HomeSectionHeader(text = "Pinned")

            TextButton(
                onClick = {
                    if (isEditing) {
                        finishEditing()
                    } else {
                        isEditing = true
                        visualPinIds = persistedPinIds
                    }
                }
            ) {
                Text(
                    text = if (isEditing) "DONE" else "MANAGE",
                    style = AppShellTypography.CompactAction,
                    color = AppShellAccent
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val gap = 8.dp
            val cardWidth =
                (maxWidth - gap * (HomePin.MAX_COUNT - 1).toFloat()) /
                        HomePin.MAX_COUNT.toFloat()

            val stepPx = with(density) {
                (cardWidth + gap).toPx()
            }

            val maxSlotIndex = (visualPins.size - 1).coerceAtLeast(0)

            Box(modifier = Modifier.fillMaxWidth()) {
                visualPins.forEach { pin ->
                    key(pin.pin.id) {
                        val currentIndex = visualPinIds
                            .indexOf(pin.pin.id)
                            .coerceAtLeast(0)

                        val isDragged = draggedPinId == pin.pin.id

                        val slotOffset by animateIntOffsetAsState(
                            targetValue = IntOffset(
                                x = (currentIndex * stepPx).roundToInt(),
                                y = 0
                            ),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "homePinSlot-${pin.pin.id}"
                        )

                        val dragScale by animateFloatAsState(
                            targetValue = if (isDragged) 1.045f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "homePinDragScale-${pin.pin.id}"
                        )

                        val dragModifier = if (isEditing) {
                            Modifier.pointerInput(
                                pin.pin.id,
                                stepPx,
                                maxSlotIndex
                            ) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val startIndex =
                                            visualPinIds.indexOf(pin.pin.id)

                                        if (startIndex >= 0) {
                                            draggedPinId = pin.pin.id
                                            dragStartIndex = startIndex
                                            dragOffsetX = 0f

                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        }
                                    },

                                    onDrag = { change, dragAmount ->
                                        if (
                                            draggedPinId != pin.pin.id ||
                                            dragStartIndex < 0
                                        ) {
                                            return@detectDragGesturesAfterLongPress
                                        }

                                        change.consume()

                                        val minimumOffset =
                                            -dragStartIndex * stepPx

                                        val maximumOffset =
                                            (maxSlotIndex - dragStartIndex) * stepPx

                                        dragOffsetX =
                                            (dragOffsetX + dragAmount.x)
                                                .coerceIn(
                                                    minimumOffset,
                                                    maximumOffset
                                                )

                                        val desiredIndex =
                                            (
                                                    (
                                                            dragStartIndex * stepPx +
                                                                    dragOffsetX
                                                            ) / stepPx
                                                    )
                                                .roundToInt()
                                                .coerceIn(
                                                    0,
                                                    maxSlotIndex
                                                )

                                        val fromIndex =
                                            visualPinIds.indexOf(pin.pin.id)

                                        if (
                                            fromIndex >= 0 &&
                                            fromIndex != desiredIndex
                                        ) {
                                            visualPinIds =
                                                visualPinIds
                                                    .toMutableList()
                                                    .apply {
                                                        val movingPin =
                                                            removeAt(fromIndex)

                                                        add(
                                                            desiredIndex,
                                                            movingPin
                                                        )
                                                    }
                                        }
                                    },

                                    onDragEnd = {
                                        val finalIndex =
                                            visualPinIds.indexOf(pin.pin.id)

                                        if (
                                            dragStartIndex >= 0 &&
                                            finalIndex >= 0
                                        ) {
                                            val offset =
                                                finalIndex - dragStartIndex

                                            if (offset != 0) {
                                                homePinUi.onMovePinRequested(
                                                    pin.pin.id,
                                                    offset
                                                )
                                            }
                                        }

                                        draggedPinId = null
                                        dragStartIndex = -1
                                        dragOffsetX = 0f
                                    },

                                    onDragCancel = {
                                        draggedPinId = null
                                        dragStartIndex = -1
                                        dragOffsetX = 0f

                                        visualPinIds = persistedPinIds
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }

                        val displayedOffset =
                            if (isDragged && dragStartIndex >= 0) {
                                IntOffset(
                                    x = (
                                            dragStartIndex * stepPx +
                                                    dragOffsetX
                                            ).roundToInt(),
                                    y = 0
                                )
                            } else {
                                slotOffset
                            }

                        HomePinnedCard(
                            pin = pin,
                            isEditing = isEditing,
                            onClick = {
                                if (!isEditing) {
                                    when (val target = pin.target) {
                                        is HomePinTarget.SongTarget ->
                                            onSongClick(target.song)

                                        is HomePinTarget.AlbumTarget ->
                                            onAlbumClick(target.album.key)

                                        is HomePinTarget.ArtistTarget ->
                                            onArtistClick(target.artist.name)

                                        is HomePinTarget.PlaylistTarget ->
                                            onPlaylistClick(target.playlist)

                                        null -> Unit
                                    }
                                }
                            },
                            onUnpin = {
                                if (draggedPinId == pin.pin.id) {
                                    draggedPinId = null
                                    dragStartIndex = -1
                                    dragOffsetX = 0f
                                }

                                visualPinIds = visualPinIds.filterNot { candidateId ->
                                    candidateId == pin.pin.id
                                }

                                homePinUi.onUnpinRequested(pin.pin.id)
                            },
                            modifier = Modifier
                                .width(cardWidth)
                                .offset { displayedOffset }
                                .zIndex(
                                    if (isDragged) 1f else 0f
                                )
                                .then(
                                    if (isDragged) {
                                        Modifier.shadow(
                                            elevation = 10.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            clip = false
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .graphicsLayer {
                                    scaleX = dragScale
                                    scaleY = dragScale
                                }
                                .then(dragModifier)
                        )
                    }
                }
            }
        }
    }
}

internal fun homePinsInVisualOrder(
    authoritativePins: List<ResolvedHomePin>,
    visualPinIds: List<String>
): List<ResolvedHomePin> {
    val authoritativePinsById = authoritativePins.associateBy { pin -> pin.pin.id }
    return visualPinIds.mapNotNull(authoritativePinsById::get)
}

@Composable
private fun HomePinnedCard(
    pin: ResolvedHomePin,
    isEditing: Boolean,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistPictureAssignments = LocalArtistPictureUi.current.assignments
    PressableHomeCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val playlistTarget = pin.target as? HomePinTarget.PlaylistTarget
                val artistPictureIdentity = pin.artistPictureIdentityOrNull()
                val artistPictureAssignment = pin.artistPictureAssignmentOrNull(
                    artistPictureAssignments
                )
                if (playlistTarget != null) {
                    PlaylistArtwork(
                        playlist = playlistTarget.playlist,
                        contentDescription = "Artwork for ${pin.title}",
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (artistPictureIdentity != null && artistPictureAssignment != null) {
                    ArtistPicture(
                        identity = artistPictureIdentity,
                        fallbackModel = pin.artworkUri,
                        contentDescription = "Artwork for ${pin.title}",
                        modifier = Modifier.fillMaxSize(),
                        variant = VisualAssetVariant.THUMBNAIL
                    )
                } else {
                    ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                    AsyncImage(
                        model = pin.artworkUri,
                        contentDescription = "Artwork for ${pin.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(40.dp)
                            .clickable(onClick = onUnpin),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Unpin ${pin.title}",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = pin.title,
                style = AppShellTypography.SongTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pin.typeLabel,
                style = AppShellTypography.Eyebrow,
                color = if (pin.target == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
fun HomeRecentlyPlayedShelf(
    songs: List<Song>,
    onSeeAllClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeCollectionSectionHeader(
            title = "Recently Played",
            onSeeAllClick = onSeeAllClick,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "recent-featured-${songs.first().stableUiKey()}") {
                HomeFeaturedSongCard(
                    song = songs.first(),
                    onClick = { onSongClick(songs.first()) }
                )
            }

            items(
                items = songs.drop(1),
                key = { song -> song.stableUiKey() }
            ) { song ->
                HomeCompactArtworkCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
fun HomeFavoritesShelf(
    songs: List<Song>,
    onSeeAllClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeCollectionSectionHeader(
            title = "Favorites",
            onSeeAllClick = onSeeAllClick,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = songs,
                key = { song -> song.stableUiKey() }
            ) { song ->
                HomeFavoriteRowCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
fun HomeRecentlyAddedShelf(
    songs: List<Song>,
    onSeeAllClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeCollectionSectionHeader(
            title = stringResource(R.string.recently_added),
            onSeeAllClick = onSeeAllClick,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = songs, key = { it.stableUiKey() }) { song ->
                HomeCompactArtworkCard(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun HomeCollectionSectionHeader(
    title: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HomeSectionHeader(text = title)

        TextButton(onClick = onSeeAllClick) {
            Text(
                text = "SEE ALL  ›",
                style = AppShellTypography.CompactAction,
                color = AppShellAccent
            )
        }
    }
}

@Composable
private fun HomeFeaturedSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)

    PressableHomeCard(
        onClick = onClick,
        modifier = modifier.width(228.dp),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        pressedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(208.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            ArtworkPlaceholder(modifier = Modifier.fillMaxSize())

            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "Artwork for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "LAST PLAYED",
                    style = AppShellTypography.Eyebrow,
                    color = AppShellAccent
                )
                Text(
                    text = song.title,
                    style = AppShellTypography.FeaturedSongTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist.ifBlank { "Unknown artist" },
                    style = AppShellTypography.SongSubtitle,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeCompactArtworkCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableHomeCard(
        onClick = onClick,
        modifier = modifier.width(132.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Artwork for ${song.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            SongShelfMetadata(song = song)
        }
    }
}

@Composable
private fun HomeFavoriteRowCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableHomeCard(
        onClick = onClick,
        modifier = modifier.width(214.dp),
        shape = RoundedCornerShape(19.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                ArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Artwork for ${song.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            SongShelfMetadata(
                song = song,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SongShelfMetadata(
    song: Song,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = song.title,
            style = AppShellTypography.SongTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist.ifBlank { "Unknown artist" },
            style = AppShellTypography.SongSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtworkPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppShellIcons.AlbumStack,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}
