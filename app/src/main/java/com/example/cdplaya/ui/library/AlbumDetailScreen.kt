package com.example.cdplaya.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cdplaya.R
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellIcons
import com.example.cdplaya.ui.formatDuration
import com.example.cdplaya.ui.getDisplayTrackNumber
import com.example.cdplaya.ui.home.LocalHomePinUi
import com.example.cdplaya.ui.ratings.CompactRatingIndicator
import com.example.cdplaya.ui.ratings.LocalSongRatingUi

@Composable
fun AlbumDetailScreen(
    album: LibraryAlbumGroup,
    currentSongId: Long?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddAllToPlaylistClick: () -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val songs = album.songs
    val context = LocalContext.current
    val metadataRepository = remember(context) {
        AlbumPresentationMetadataRepository(context)
    }
    val metadataKey = remember(album) {
        albumPresentationMetadataKey(album)
    }
    var presentationMetadata by remember(metadataKey) {
        mutableStateOf(AlbumPresentationMetadata())
    }

    // Load independent presentation pieces separately so a slow technical-quality scan
    // cannot hold back the release year or artwork-derived backdrop.
    LaunchedEffect(metadataKey) {
        val releaseYear = metadataRepository.getReleaseYear(album)
        presentationMetadata = presentationMetadata.copy(releaseYear = releaseYear)
    }
    LaunchedEffect(metadataKey) {
        val artworkAccentArgb = metadataRepository.getArtworkAccentArgb(
            album.songs.firstOrNull()?.albumArtUri
        )
        presentationMetadata = presentationMetadata.copy(
            artworkAccentArgb = artworkAccentArgb
        )
    }
    LaunchedEffect(metadataKey) {
        val audioQuality = metadataRepository.getAudioQualitySummary(album.songs)
        presentationMetadata = presentationMetadata.copy(audioQuality = audioQuality)
    }

    val listState = rememberLazyListState()
    val showCompactTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }
    val homePinUi = LocalHomePinUi.current
    val ratingUi = LocalSongRatingUi.current
    val ratingValues = ratingUi.state.ratingsByReferenceKey
    val rateSongLabel = stringResource(R.string.rate_song)
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }

    val songCountText = pluralStringResource(
        R.plurals.song_count,
        songs.size,
        songs.size
    )
    val durationText = formatCollectionDuration(
        songs.sumOf { song -> song.duration.coerceAtLeast(0L) }
    )
    val releaseContext = buildList {
        presentationMetadata.releaseYear?.let { releaseYear ->
            add(releaseYear.toString())
        }
        add(songCountText)
        durationText.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(separator = " • ")
    val actionSheetSubtitle = buildList {
        add(album.artistText)
        presentationMetadata.releaseYear?.let { releaseYear ->
            add(releaseYear.toString())
        }
        add(songCountText)
        durationText.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(separator = " • ")

    val baseBackground = MaterialTheme.colorScheme.background
    val artworkAccent = presentationMetadata.artworkAccentArgb?.let { argb -> Color(argb) }
    val backdropTopColor = artworkAccent?.let { accent ->
        lerp(baseBackground, accent, 0.34f)
    }
    val backdropMiddleColor = artworkAccent?.let { accent ->
        lerp(baseBackground, accent, 0.16f)
    }
    val backdropAlpha by animateFloatAsState(
        targetValue = if (showCompactTitle) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "albumArtworkBackdropAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackground)
    ) {
        if (backdropTopColor != null && backdropMiddleColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp)
                    .graphicsLayer {
                        alpha = backdropAlpha
                    }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to backdropTopColor,
                                0.56f to backdropMiddleColor,
                                1f to Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LibraryDetailTopBar(
                title = album.title,
                showTitle = showCompactTitle,
                containerColor = if (showCompactTitle) {
                    baseBackground.copy(alpha = 0.97f)
                } else {
                    Color.Transparent
                },
                onBackClick = onBackClick,
                onMoreClick = {
                    actionSheetTarget = albumActionSheetTarget(
                        albumTitle = album.title,
                        subtitle = actionSheetSubtitle,
                        artworkUri = songs.firstOrNull()?.albumArtUri,
                        albumSongs = songs,
                        onPlayClick = { _, _ -> onPlayAllClick() },
                        onShuffleClick = { _, _ -> onShuffleAllClick() },
                        onPlayNextClick = onPlayNextSongsClick,
                        onAddToQueueClick = onAddSongsToQueueClick,
                        onAddToPlaylistClick = { _, _ -> onAddAllToPlaylistClick() },
                        homePinAction = homePinUi.actionForAlbum(album)
                    )
                }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            ) {
                item(key = "album-detail-header") {
                    AlbumDetailHero(
                        album = album,
                        releaseContext = releaseContext,
                        audioQuality = presentationMetadata.audioQuality,
                        hasSongs = songs.isNotEmpty(),
                        onPlayClick = onPlayAllClick,
                        onShuffleClick = onShuffleAllClick,
                        onAddToPlaylistClick = onAddAllToPlaylistClick
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }

                items(
                    items = songs,
                    key = { song -> song.id }
                ) { song ->
                    val isCurrentSong = song.id == currentSongId
                    val isFavorite = song.membershipKey() in favoriteMembershipKeys
                    val wasRecentlyAdded = song.id in recentlyAddedSongIds
                    val rating = ratingValues[song.membershipKey()]

                    AlbumTrackRow(
                        song = song,
                        albumArtist = album.artistText,
                        isCurrentSong = isCurrentSong,
                        rating = rating,
                        onClick = {
                            onSongClick(song, songs)
                        },
                        onShowActions = {
                            actionSheetTarget = songActionSheetTarget(
                                song = song,
                                wasRecentlyAdded = wasRecentlyAdded,
                                isFavorite = isFavorite,
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onToggleFavoriteClick = onToggleFavoriteClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onEditSongTagsClick = onEditSongTagsClick,
                                rateSongLabel = rateSongLabel,
                                onRateSongClick = ratingUi.onOpen,
                                homePinAction = homePinUi.actionForSong(song)
                            )
                        }
                    )
                }
            }
        }
    }

    actionSheetTarget?.let { target ->
        LibraryItemActionSheet(
            target = target,
            onDismissRequest = {
                actionSheetTarget = null
            }
        )
    }
}

@Composable
private fun AlbumDetailHero(
    album: LibraryAlbumGroup,
    releaseContext: String,
    audioQuality: AlbumAudioQualitySummary?,
    hasSongs: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .widthIn(max = 360.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppShellIcons.AlbumStack,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            )
            AsyncImage(
                model = album.songs.firstOrNull()?.albumArtUri,
                contentDescription = "Album art for ${album.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artistText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = releaseContext,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Reserve the quality-pill row from the first frame so asynchronous metadata
            // does not push the action buttons and song list downward when it arrives.
            if (hasSongs) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 31.dp)
                        .padding(top = 7.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    audioQuality?.let { quality ->
                        AlbumAudioQualityBadges(audioQuality = quality)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Top
        ) {
            LibraryDetailAction(
                icon = Icons.Filled.PlayArrow,
                label = "Play",
                enabled = hasSongs,
                onClick = onPlayClick
            )
            LibraryDetailAction(
                icon = Icons.Filled.Shuffle,
                label = "Shuffle",
                enabled = hasSongs,
                onClick = onShuffleClick
            )
            LibraryDetailAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add",
                enabled = hasSongs,
                onClick = onAddToPlaylistClick
            )
        }
    }
}

@Composable
private fun AlbumAudioQualityBadges(
    audioQuality: AlbumAudioQualitySummary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        audioQuality.formatLabel?.let { label ->
            AudioQualityBadge(text = label)
        }
        audioQuality.qualityLabel?.let { label ->
            AudioQualityBadge(text = label)
        }
    }
}

@Composable
private fun AudioQualityBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = AppShellAccent.copy(alpha = 0.11f),
        border = BorderStroke(
            width = 1.dp,
            color = AppShellAccent.copy(alpha = 0.24f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            maxLines = 1
        )
    }
}

@Composable
private fun AlbumTrackRow(
    song: Song,
    albumArtist: String,
    isCurrentSong: Boolean,
    rating: Int?,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayArtist = song.artist
        .trim()
        .takeIf { artist ->
            artist.isNotBlank() && !artist.equals(albumArtist, ignoreCase = true)
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isCurrentSong) {
                    AppShellAccent.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
            )
            .libraryItemActions(
                clickLabel = "Play ${song.title}",
                onClick = onClick,
                onShowActions = onShowActions
            )
            .heightIn(min = 62.dp)
            .padding(start = 16.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = getDisplayTrackNumber(song.trackNumber),
            modifier = Modifier.width(34.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrentSong) {
                AppShellAccent
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = song.title.ifBlank { "Unknown Title" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrentSong) AppShellAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            displayArtist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        rating?.let { value ->
            CompactRatingIndicator(
                rating = value,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        if (song.duration > 0L) {
            Text(
                text = formatDuration(
                    song.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                ),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onShowActions,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More options for ${song.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatCollectionDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""

    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    return when {
        hours <= 0L -> "$totalMinutes min"
        minutes == 0L -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}
