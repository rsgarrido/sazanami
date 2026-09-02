package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.ratings.CompactRatingIndicator
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.home.LocalHomePinUi
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity

@Composable
fun SongGrid(
    songs: List<Song>,
    currentSongId: Long?,
    gridColumnCount: Int,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit = {},
    selectionEnabled: Boolean = false,
    searchActive: Boolean = false,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
    ratingValuesByReferenceKey: Map<String, Int> = emptyMap(),
    gridState: LazyGridState? = null
) {
    val gridMetrics = libraryGridMetrics(gridColumnCount)
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val ratingUi = LocalSongRatingUi.current
    val homePinUi = LocalHomePinUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    val rateSongLabel = stringResource(R.string.rate_song)
    val rememberedGridState = rememberLazyGridState()
    val selectionUi = LocalLibrarySelectionUi.current
    val selectionActive = selectionEnabled &&
        selectionUi.state.entity == LibrarySelectionEntity.SONG && selectionUi.state.isActive
    val displayedKeys = remember(songs) { songs.map(Song::membershipKey) }
    val resolvedSelectedSongs = {
        resolveSelectedSongs(selectionUi.state.selectedKeys, songs, selectionUi.allSongs)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionActive) {
            LibrarySelectionHeader(
                entity = LibrarySelectionEntity.SONG,
                displayedKeys = displayedKeys,
                searchActive = searchActive,
                onMoreClick = {
                    resolvedSelectedSongs().singleOrNull()?.let { selectedSong ->
                        val base = songActionSheetTarget(
                            song = selectedSong,
                            wasRecentlyAdded = selectedSong.id in recentlyAddedSongIds,
                            isFavorite = selectedSong.membershipKey() in favoriteMembershipKeys,
                            onPlayNextClick = onPlayNextClick,
                            onAddToQueueClick = onAddToQueueClick,
                            onToggleFavoriteClick = onToggleFavoriteClick,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onEditSongTagsClick = onEditSongTagsClick,
                            rateSongLabel = rateSongLabel,
                            onRateSongClick = ratingUi.onOpen,
                            homePinAction = homePinUi.actionForSong(selectedSong)
                        )
                        actionSheetTarget = base.copy(
                            actions = base.actions.filter { action ->
                                isSongSelectionMoreAction(action.label, rateSongLabel)
                            }.map { action ->
                                action.copy(onClick = {
                                    selectionUi.onClear()
                                    action.onClick()
                                })
                            }
                        )
                    }
                }
            )
        }

    LazyVerticalGrid(
        state = gridState ?: rememberedGridState,
        columns = GridCells.Fixed(gridMetrics.columnCount),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = libraryGridPadding(
            if (selectionActive) 0.dp else bottomContentPadding,
            gridMetrics
        ),
        horizontalArrangement = Arrangement.spacedBy(gridMetrics.horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(gridMetrics.verticalSpacing)
    ) {
        items(
            items = songs,
            key = { song -> song.stableUiKey() }
        ) { song ->
            val isCurrentSong = song.id == currentSongId
            val isSelectionSelected = selectionActive &&
                song.membershipKey() in selectionUi.state.selectedKeys
            LibraryGridCard(
                artworkUri = song.albumArtUri,
                artworkDescription = "Album art for ${song.title}",
                title = song.title.ifBlank { "Unknown Title" },
                subtitle = song.artist.ifBlank { "Unknown Artist" },
                clickLabel = "Play ${song.title}",
                gridMetrics = gridMetrics,
                selected = isCurrentSong,
                selectionEnabled = selectionEnabled,
                selectionActive = selectionActive,
                selectionSelected = isSelectionSelected,
                onToggleSelection = {
                    selectionUi.onToggle(LibrarySelectionEntity.SONG, song.membershipKey())
                },
                onEnterSelection = {
                    if (selectionActive) {
                        selectionUi.onToggle(LibrarySelectionEntity.SONG, song.membershipKey())
                    } else {
                        selectionUi.onEnter(LibrarySelectionEntity.SONG, song.membershipKey())
                    }
                },
                rating = ratingValuesByReferenceKey[song.membershipKey()],
                onClick = { onSongClick(song, songs) },
                onShowActions = {
                    actionSheetTarget = songActionSheetTarget(
                        song = song,
                        wasRecentlyAdded = song.id in recentlyAddedSongIds,
                        isFavorite = song.membershipKey() in favoriteMembershipKeys,
                        onPlayNextClick = onPlayNextClick,
                        onAddToQueueClick = onAddToQueueClick,
                        onAddToAnotherQueueClick = libraryQueueUi.onAddToAnotherQueue,
                        onPlayInNewQueueClick = { selectedSong ->
                            libraryQueueUi.onPlayInNewQueue("", listOf(selectedSong))
                        },
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        onAddToPlaylistClick = onAddToPlaylistClick,
                        onEditSongTagsClick = onEditSongTagsClick,
                        rateSongLabel = rateSongLabel,
                        onRateSongClick = ratingUi.onOpen,
                        homePinAction = homePinUi.actionForSong(song)
                    )
                },
                modifier = Modifier.animateItem(
                    placementSpec = tween(
                        durationMillis = LibraryLayoutMotionDurationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            )
        }
    }

        if (selectionActive) {
            LibrarySelectionActionBar(
                selectedSongs = resolvedSelectedSongs,
                onAddToPlaylist = onAddSongsToPlaylistClick,
                favoritesEnabled = true,
                modifier = Modifier.padding(bottom = bottomContentPadding)
            )
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
fun AlbumGridScreen(
    songs: List<Song>,
    sortState: LibrarySortState,
    gridColumnCount: Int,
    onAlbumClick: (String) -> Unit,
    onAlbumPlayClick: (String, List<Song>) -> Unit,
    onAlbumShuffleClick: (String, List<Song>) -> Unit,
    onAlbumPlayNextClick: (String, List<Song>) -> Unit,
    onAlbumAddToQueueClick: (String, List<Song>) -> Unit,
    onAlbumAddToPlaylistClick: (String, List<Song>) -> Unit,
    selectionEnabled: Boolean = false,
    searchActive: Boolean = false,
    bottomContentPadding: Dp,
    gridState: LazyGridState? = null,
    modifier: Modifier = Modifier
) {
    val albums = sortedLibraryAlbumGroups(songs, sortState)
    val gridMetrics = libraryGridMetrics(gridColumnCount)
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val homePinUi = LocalHomePinUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    val rememberedGridState = rememberLazyGridState()
    val selectionUi = LocalLibrarySelectionUi.current
    val selectionActive = selectionEnabled &&
        selectionUi.state.entity == LibrarySelectionEntity.ALBUM && selectionUi.state.isActive
    val displayedKeys = remember(albums) { albums.map(LibraryAlbumGroup::key) }
    val fallbackAlbums = remember(selectionUi.allSongs, sortState) {
        sortedLibraryAlbumGroups(selectionUi.allSongs, sortState)
    }
    val resolvedSelectedAlbums = {
        resolveSelectedAlbums(selectionUi.state.selectedKeys, albums, fallbackAlbums)
    }
    val resolvedSelectedSongs = { resolvedSelectedAlbums().flatMap(LibraryAlbumGroup::songs) }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionActive) {
            LibrarySelectionHeader(
                entity = LibrarySelectionEntity.ALBUM,
                displayedKeys = displayedKeys,
                searchActive = searchActive,
                onMoreClick = {
                    resolvedSelectedAlbums().singleOrNull()?.let { album ->
                        val base = albumActionSheetTarget(
                            albumTitle = album.title,
                            subtitle = album.artistText,
                            artworkUri = album.songs.firstOrNull()?.albumArtUri,
                            albumSongs = album.songs,
                            onPlayClick = onAlbumPlayClick,
                            onShuffleClick = onAlbumShuffleClick,
                            onPlayNextClick = onAlbumPlayNextClick,
                            onAddToQueueClick = onAlbumAddToQueueClick,
                            onAddToPlaylistClick = onAlbumAddToPlaylistClick,
                            homePinAction = homePinUi.actionForAlbum(album)
                        )
                        actionSheetTarget = base.copy(
                            actions = base.actions.filter { action ->
                                isAlbumSelectionMoreAction(action.label)
                            }.map { action ->
                                action.copy(onClick = {
                                    selectionUi.onClear()
                                    action.onClick()
                                })
                            }
                        )
                    }
                }
            )
        }

    LazyVerticalGrid(
        state = gridState ?: rememberedGridState,
        columns = GridCells.Fixed(gridMetrics.columnCount),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = libraryGridPadding(
            if (selectionActive) 0.dp else bottomContentPadding,
            gridMetrics
        ),
        horizontalArrangement = Arrangement.spacedBy(gridMetrics.horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(gridMetrics.verticalSpacing)
    ) {
        items(
            items = albums,
            key = { album -> album.key }
        ) { album ->
            val songCountText = pluralStringResource(
                R.plurals.song_count,
                album.songs.size,
                album.songs.size
            )
            LibraryGridCard(
                artworkUri = album.songs.firstOrNull()?.albumArtUri,
                artworkDescription = "Album art for ${album.title}",
                title = album.title,
                subtitle = "${album.artistText} • $songCountText",
                clickLabel = "Open ${album.title}",
                gridMetrics = gridMetrics,
                selectionEnabled = selectionEnabled,
                selectionActive = selectionActive,
                selectionSelected = selectionActive && album.key in selectionUi.state.selectedKeys,
                onToggleSelection = {
                    selectionUi.onToggle(LibrarySelectionEntity.ALBUM, album.key)
                },
                onEnterSelection = {
                    if (selectionActive) {
                        selectionUi.onToggle(LibrarySelectionEntity.ALBUM, album.key)
                    } else {
                        selectionUi.onEnter(LibrarySelectionEntity.ALBUM, album.key)
                    }
                },
                onClick = { onAlbumClick(album.key) },
                onShowActions = {
                    actionSheetTarget = albumActionSheetTarget(
                        albumTitle = album.title,
                        subtitle = "${album.artistText} • $songCountText",
                        artworkUri = album.songs.firstOrNull()?.albumArtUri,
                        albumSongs = album.songs,
                        onPlayClick = onAlbumPlayClick,
                        onShuffleClick = onAlbumShuffleClick,
                        onPlayNextClick = onAlbumPlayNextClick,
                        onAddToQueueClick = onAlbumAddToQueueClick,
                        onAddToAnotherQueueClick = libraryQueueUi.onAddToAnotherQueue,
                        onPlayInNewQueueClick = libraryQueueUi.onPlayInNewQueue,
                        onAddToPlaylistClick = onAlbumAddToPlaylistClick,
                        homePinAction = homePinUi.actionForAlbum(album)
                    )
                },
                modifier = Modifier.animateItem(
                    placementSpec = tween(
                        durationMillis = LibraryLayoutMotionDurationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            )
        }
    }

        if (selectionActive) {
            LibrarySelectionActionBar(
                selectedSongs = resolvedSelectedSongs,
                onAddToPlaylist = { selectedSongs ->
                    onAlbumAddToPlaylistClick("Selected albums", selectedSongs)
                },
                favoritesEnabled = false,
                newQueueName = { resolvedSelectedAlbums().singleOrNull()?.title.orEmpty() },
                modifier = Modifier.padding(bottom = bottomContentPadding)
            )
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
fun ArtistGridScreen(
    songs: List<Song>,
    sortState: LibrarySortState,
    gridColumnCount: Int,
    onArtistClick: (String) -> Unit,
    onArtistPlayClick: (String, List<Song>) -> Unit,
    onArtistShuffleClick: (String, List<Song>) -> Unit,
    onArtistPlayNextClick: (String, List<Song>) -> Unit,
    onArtistAddToQueueClick: (String, List<Song>) -> Unit,
    onArtistAddToPlaylistClick: (String, List<Song>) -> Unit,
    bottomContentPadding: Dp,
    gridState: LazyGridState? = null,
    modifier: Modifier = Modifier
) {
    val artists = sortedLibraryArtistGroups(songs, sortState)
    val gridMetrics = libraryGridMetrics(gridColumnCount)
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }
    val homePinUi = LocalHomePinUi.current
    val artistPictureUi = LocalArtistPictureUi.current
    val libraryQueueUi = LocalLibraryQueueUi.current
    val rememberedGridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState ?: rememberedGridState,
        columns = GridCells.Fixed(gridMetrics.columnCount),
        modifier = modifier.fillMaxSize(),
        contentPadding = libraryGridPadding(bottomContentPadding, gridMetrics),
        horizontalArrangement = Arrangement.spacedBy(gridMetrics.horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(gridMetrics.verticalSpacing)
    ) {
        items(
            items = artists,
            key = { artist -> artist.key }
        ) { artist ->
            val songCountText = pluralStringResource(
                R.plurals.song_count,
                artist.songs.size,
                artist.songs.size
            )
            LibraryGridCard(
                artworkUri = artist.songs.firstOrNull()?.albumArtUri,
                artworkDescription = "Artwork for ${artist.name}",
                artworkContent = {
                    ArtistPicture(
                        identity = artist.identity,
                        fallbackModel = artist.songs.firstOrNull()?.albumArtUri,
                        contentDescription = "Artwork for ${artist.name}",
                        modifier = Modifier.fillMaxSize(),
                        variant = VisualAssetVariant.THUMBNAIL
                    )
                },
                title = artist.name,
                subtitle = songCountText,
                clickLabel = "Open ${artist.name}",
                gridMetrics = gridMetrics,
                onClick = { onArtistClick(artist.name) },
                onShowActions = {
                    actionSheetTarget = artistActionSheetTarget(
                        artistName = artist.name,
                        subtitle = songCountText,
                        artworkUri = artist.songs.firstOrNull()?.albumArtUri,
                        artistIdentity = artist.identity,
                        hasCustomPicture = artist.key in artistPictureUi.assignments,
                        onChoosePicture = artistPictureUi.onChoosePicture,
                        onRemovePicture = artistPictureUi.onRemovePicture,
                        artistSongs = artist.songs,
                        onPlayClick = onArtistPlayClick,
                        onShuffleClick = onArtistShuffleClick,
                        onPlayNextClick = onArtistPlayNextClick,
                        onAddToQueueClick = onArtistAddToQueueClick,
                        onAddToAnotherQueueClick = libraryQueueUi.onAddToAnotherQueue,
                        onPlayInNewQueueClick = libraryQueueUi.onPlayInNewQueue,
                        onAddToPlaylistClick = onArtistAddToPlaylistClick,
                        homePinAction = homePinUi.actionForArtist(artist)
                    )
                },
                modifier = Modifier.animateItem(
                    placementSpec = tween(
                        durationMillis = LibraryLayoutMotionDurationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            )
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
private fun LibraryGridCard(
    artworkUri: Any?,
    artworkDescription: String,
    artworkContent: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String?,
    clickLabel: String,
    gridMetrics: LibraryGridMetrics,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionEnabled: Boolean = false,
    selectionActive: Boolean = false,
    selectionSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    rating: Int? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selectionEnabled) Modifier.librarySelectableItem(
                    clickLabel = clickLabel,
                    selectionActive = selectionActive,
                    selected = selectionSelected,
                    onClick = onClick,
                    onToggleSelection = onToggleSelection,
                    onEnterSelection = onEnterSelection
                ) else Modifier.libraryItemActions(
                    clickLabel = clickLabel,
                    onClick = onClick,
                    onShowActions = onShowActions
                )
            ),
        verticalArrangement = Arrangement.spacedBy(gridMetrics.metadataSpacing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(gridMetrics.artworkCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = AppShellIcons.AlbumStack,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(gridMetrics.placeholderIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            )
            if (artworkContent != null) {
                artworkContent()
            } else {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = artworkDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 7.dp)
                        .size(width = gridMetrics.selectedAccentWidth, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppShellAccent)
                )
            }
            if (selectionSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppShellAccent.copy(alpha = 0.24f))
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    tint = AppShellAccent
                )
            }
        }

        Column(
            modifier = Modifier.padding(
                start = gridMetrics.metadataHorizontalPadding,
                end = gridMetrics.metadataHorizontalPadding,
                bottom = gridMetrics.metadataBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = gridMetrics.titleStyle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) {
                    AppShellAccent
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (gridMetrics.showSubtitle && !subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = gridMetrics.subtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            rating?.let { value ->
                CompactRatingIndicator(rating = value)
            }
        }
    }
}

private data class LibraryGridMetrics(
    val columnCount: Int,
    val horizontalSpacing: Dp,
    val verticalSpacing: Dp,
    val contentHorizontalPadding: Dp,
    val artworkCornerRadius: Dp,
    val metadataSpacing: Dp,
    val metadataHorizontalPadding: Dp,
    val metadataBottomPadding: Dp,
    val placeholderIconSize: Dp,
    val selectedAccentWidth: Dp,
    val titleMinimumHeight: Dp,
    val titleStyle: TextStyle,
    val subtitleStyle: TextStyle,
    val showSubtitle: Boolean
)

private fun libraryGridMetrics(columnCount: Int): LibraryGridMetrics {
    return when (LibraryGridColumns.normalize(columnCount)) {
        2 -> LibraryGridMetrics(
            columnCount = 2,
            horizontalSpacing = 12.dp,
            verticalSpacing = 20.dp,
            contentHorizontalPadding = 12.dp,
            artworkCornerRadius = 18.dp,
            metadataSpacing = 8.dp,
            metadataHorizontalPadding = 2.dp,
            metadataBottomPadding = 4.dp,
            placeholderIconSize = 36.dp,
            selectedAccentWidth = 30.dp,
            titleMinimumHeight = 36.dp,
            titleStyle = AppShellTypography.SongTitle,
            subtitleStyle = AppShellTypography.SongSubtitle,
            showSubtitle = true
        )

        3 -> LibraryGridMetrics(
            columnCount = 3,
            horizontalSpacing = 10.dp,
            verticalSpacing = 17.dp,
            contentHorizontalPadding = 10.dp,
            artworkCornerRadius = 15.dp,
            metadataSpacing = 6.dp,
            metadataHorizontalPadding = 1.dp,
            metadataBottomPadding = 4.dp,
            placeholderIconSize = 30.dp,
            selectedAccentWidth = 26.dp,
            titleMinimumHeight = 34.dp,
            titleStyle = AppShellTypography.SongTitle.copy(
                fontSize = 13.sp,
                lineHeight = 17.sp
            ),
            subtitleStyle = AppShellTypography.SongSubtitle.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            showSubtitle = true
        )

        else -> LibraryGridMetrics(
            columnCount = 4,
            horizontalSpacing = 8.dp,
            verticalSpacing = 14.dp,
            contentHorizontalPadding = 8.dp,
            artworkCornerRadius = 12.dp,
            metadataSpacing = 5.dp,
            metadataHorizontalPadding = 0.dp,
            metadataBottomPadding = 3.dp,
            placeholderIconSize = 24.dp,
            selectedAccentWidth = 22.dp,
            titleMinimumHeight = 28.dp,
            titleStyle = AppShellTypography.SongTitle.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.sp
            ),
            subtitleStyle = AppShellTypography.SongSubtitle.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp
            ),
            showSubtitle = false
        )
    }
}

private fun libraryGridPadding(
    bottomContentPadding: Dp,
    gridMetrics: LibraryGridMetrics
): PaddingValues {
    return PaddingValues(
        start = gridMetrics.contentHorizontalPadding,
        top = 8.dp,
        end = gridMetrics.contentHorizontalPadding,
        bottom = bottomContentPadding
    )
}
