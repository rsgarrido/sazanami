package com.example.cdplaya.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.cdplaya.R
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellIcons
import com.example.cdplaya.ui.home.LocalHomePinUi

@Composable
fun ArtistDetailScreen(
    artistName: String,
    artistSongs: List<Song>,
    librarySongs: List<Song>,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlayAllClick: () -> Unit,
    onPlayAlbumClick: (List<Song>) -> Unit,
    onShuffleAlbumClick: (List<Song>) -> Unit,
    onShuffleSongsClick: () -> Unit,
    onShuffleAlbumsClick: () -> Unit,
    onShuffleAlbumsAndSongsClick: () -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val libraryAlbums = remember(librarySongs) {
        buildLibraryAlbumGroups(librarySongs)
    }
    val rawAlbums = remember(artistSongs, libraryAlbums) {
        buildLibraryAlbumGroups(artistSongs).map { partialAlbum ->
            val fullAlbum = partialAlbum.songs.firstOrNull()?.let { song ->
                findLibraryAlbumGroupForSong(song, libraryAlbums)
            }
            if (fullAlbum == null || fullAlbum.key == partialAlbum.key) {
                partialAlbum
            } else {
                partialAlbum.copy(key = fullAlbum.key)
            }
        }
    }
    val metadataRepository = remember(context) {
        AlbumPresentationMetadataRepository(context)
    }
    val releaseYearMetadataKey = remember(rawAlbums) {
        rawAlbums.joinToString(separator = "||") { album ->
            albumPresentationMetadataKey(album)
        }
    }
    var releaseYears by remember(releaseYearMetadataKey) {
        mutableStateOf<Map<String, Int?>>(emptyMap())
    }

    LaunchedEffect(releaseYearMetadataKey) {
        releaseYears = metadataRepository.getReleaseYears(rawAlbums)
    }

    val albums = remember(rawAlbums, releaseYears) {
        rawAlbums.sortedWith(
            compareByDescending<LibraryAlbumGroup> { album ->
                releaseYears[album.key] ?: Int.MIN_VALUE
            }.thenBy { album ->
                album.title.lowercase()
            }
        )
    }
    val artistGroup = remember(artistName, artistSongs) {
        LibraryArtistGroup(
            name = artistName,
            songs = artistSongs
        )
    }
    val homePinUi = LocalHomePinUi.current
    val gridState = rememberLazyGridState()
    val showCompactTitle by remember {
        derivedStateOf {
            shouldShowCompactLibraryDetailTitle(gridState.firstVisibleItemIndex)
        }
    }
    var shuffleMenuExpanded by remember { mutableStateOf(false) }
    var actionSheetTarget by remember {
        mutableStateOf<LibraryItemActionSheetTarget?>(null)
    }

    val albumCountText = if (albums.size == 1) {
        "1 album"
    } else {
        "${albums.size} albums"
    }
    val songCountText = pluralStringResource(
        R.plurals.song_count,
        artistSongs.size,
        artistSongs.size
    )
    val subtitle = "$albumCountText • $songCountText"

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        LibraryDetailTopBar(
            title = artistName,
            showTitle = showCompactTitle,
            onBackClick = onBackClick,
            onMoreClick = {
                actionSheetTarget = artistActionSheetTarget(
                    artistName = artistName,
                    subtitle = subtitle,
                    artworkUri = artistSongs.firstOrNull()?.albumArtUri,
                    artistSongs = artistSongs,
                    onPlayClick = { _, _ -> onPlayAllClick() },
                    onShuffleClick = { _, _ -> onShuffleSongsClick() },
                    onPlayNextClick = onPlayNextSongsClick,
                    onAddToQueueClick = onAddSongsToQueueClick,
                    onAddToPlaylistClick = { _, songs ->
                        onAddSongsToPlaylistClick(songs)
                    },
                    homePinAction = homePinUi.actionForArtist(artistGroup)
                )
            }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = bottomContentPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(
                key = "artist-detail-header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        LibraryDetailAction(
                            icon = Icons.Filled.PlayArrow,
                            label = "Play",
                            enabled = artistSongs.isNotEmpty(),
                            onClick = onPlayAllClick
                        )

                        Box {
                            LibraryDetailAction(
                                icon = Icons.Filled.Shuffle,
                                label = "Shuffle",
                                enabled = artistSongs.isNotEmpty(),
                                trailingIcon = Icons.Filled.KeyboardArrowDown,
                                onClick = {
                                    shuffleMenuExpanded = true
                                }
                            )
                            DropdownMenu(
                                expanded = shuffleMenuExpanded,
                                onDismissRequest = {
                                    shuffleMenuExpanded = false
                                },
                                modifier = Modifier.widthIn(min = 300.dp),
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = true
                                )
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        ShuffleModeMenuText(
                                            title = "Shuffle songs",
                                            subtitle = "Play all tracks in random order"
                                        )
                                    },
                                    onClick = {
                                        shuffleMenuExpanded = false
                                        onShuffleSongsClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        ShuffleModeMenuText(
                                            title = "Shuffle albums",
                                            subtitle = "Play full albums in a random order"
                                        )
                                    },
                                    onClick = {
                                        shuffleMenuExpanded = false
                                        onShuffleAlbumsClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        ShuffleModeMenuText(
                                            title = "Shuffle albums + songs",
                                            subtitle = "Randomize albums and tracks within them"
                                        )
                                    },
                                    onClick = {
                                        shuffleMenuExpanded = false
                                        onShuffleAlbumsAndSongsClick()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "ALBUMS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppShellAccent
                    )
                }
            }

            items(
                items = albums,
                key = { album -> album.key }
            ) { album ->
                val firstSong = album.songs.firstOrNull()
                val albumSongCount = pluralStringResource(
                    R.plurals.song_count,
                    album.songs.size,
                    album.songs.size
                )
                val releaseYear = releaseYears[album.key]
                val albumSubtitle = buildList {
                    releaseYear?.let { year ->
                        add(year.toString())
                    }
                    add(albumSongCount)
                }.joinToString(separator = " • ")
                val actionSheetSubtitle = buildList {
                    add(album.artistText)
                    releaseYear?.let { year ->
                        add(year.toString())
                    }
                    add(albumSongCount)
                }.joinToString(separator = " • ")

                ArtistAlbumCard(
                    album = album,
                    subtitle = albumSubtitle,
                    onClick = {
                        onAlbumClick(album.key)
                    },
                    onShowActions = {
                        actionSheetTarget = albumActionSheetTarget(
                            albumTitle = album.title,
                            subtitle = actionSheetSubtitle,
                            artworkUri = firstSong?.albumArtUri,
                            albumSongs = album.songs,
                            onPlayClick = { _, songs ->
                                onPlayAlbumClick(songs)
                            },
                            onShuffleClick = { _, songs ->
                                onShuffleAlbumClick(songs)
                            },
                            onPlayNextClick = onPlayNextSongsClick,
                            onAddToQueueClick = onAddSongsToQueueClick,
                            onAddToPlaylistClick = { _, songs ->
                                onAddSongsToPlaylistClick(songs)
                            },
                            homePinAction = homePinUi.actionForAlbum(album)
                        )
                    }
                )
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
private fun ShuffleModeMenuText(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistAlbumCard(
    album: LibraryAlbumGroup,
    subtitle: String,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .libraryItemActions(
                clickLabel = "Open ${album.title}",
                onClick = onClick,
                onShowActions = onShowActions
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = AppShellIcons.AlbumStack,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            )
            AsyncImage(
                model = album.songs.firstOrNull()?.albumArtUri,
                contentDescription = "Album art for ${album.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun shouldShowCompactLibraryDetailTitle(firstVisibleItemIndex: Int): Boolean =
    firstVisibleItemIndex > 0

@Composable
internal fun LibraryDetailTopBar(
    title: String,
    showTitle: Boolean,
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        if (showTitle) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options"
                )
            }
        }
    }
}

@Composable
internal fun LibraryDetailAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    contentDescription: String = label
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            shape = CircleShape,
            color = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(23.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                trailingIcon?.let { trailing ->
                    Icon(
                        imageVector = trailing,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(13.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}
