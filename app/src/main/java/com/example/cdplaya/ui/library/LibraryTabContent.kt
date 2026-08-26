package com.example.cdplaya.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.R
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.player.PlaybackShuffleMode
import com.example.cdplaya.ui.filterSongsByAlbumSearch
import com.example.cdplaya.ui.filterSongsByArtistSearch
import com.example.cdplaya.ui.filterSongsForSearch
import com.example.cdplaya.ui.sortSongsByAlbumOrder
import com.example.cdplaya.ui.sortSongsForArtistDetail
import com.example.cdplaya.ui.sortSongsForLibrary
import com.example.cdplaya.ui.ratings.LocalSongRatingUi
import androidx.compose.ui.res.stringResource

@Composable
fun SongsTabContent(
    songs: List<Song>,
    searchQuery: String,
    sortOption: LibrarySortOption,
    currentSong: Song?,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    recentlyAddedSongIds: Set<Long>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    favoriteMembershipKeys: Set<String>,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    ratingFeaturesEnabled: Boolean = true,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val filteredSongs = remember(songs, searchQuery) {
        filterSongsForSearch(
            songs = songs,
            searchQuery = searchQuery
        )
    }

    val effectiveSortOption = sortOption.takeIf {
        it in librarySortOptionsFor(LibraryTab.SONGS, ratingFeaturesEnabled)
    } ?: LibrarySortOption.TITLE
    // The normal Songs collection stays visually and semantically uncluttered. Rating management
    // and rating-specific sorting belong to the Rated collection.
    val displayedSongs = remember(filteredSongs, effectiveSortOption) {
        sortSongsForLibrary(
            songs = filteredSongs,
            sortOption = effectiveSortOption
        )
    }

    if (songs.isEmpty()) {
        Text(
            text = "No songs found.",
            modifier = Modifier.padding(16.dp)
        )
    } else if (filteredSongs.isEmpty()) {
        Text(
            text = "No songs match your search.",
            modifier = Modifier.padding(16.dp)
        )
    } else {
        LibraryLayoutTransition(
            viewMode = viewMode,
            modifier = modifier,
            listContent = {
                SongList(
                    songs = displayedSongs,
                    currentSongId = currentSong?.id,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    onSongClick = onSongClick,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onEditSongTagsClick = onEditSongTagsClick,
                    bottomContentPadding = bottomContentPadding,
                    quickRatingMode = false,
                    modifier = Modifier.fillMaxSize()
                )
            },
            gridContent = {
                SongGrid(
                    songs = displayedSongs,
                    currentSongId = currentSong?.id,
                    gridColumnCount = gridColumnCount,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onSongClick = onSongClick,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onEditSongTagsClick = onEditSongTagsClick,
                    bottomContentPadding = bottomContentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }
}

@Composable
fun RatedSongsTabContent(
    songs: List<Song>,
    searchQuery: String,
    sortOption: LibrarySortOption,
    selectedFilter: RatedSongFilter = RatedSongFilter.ALL,
    currentSong: Song?,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    ratingFeaturesEnabled: Boolean = true,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val ratingUi = LocalSongRatingUi.current
    val quickRateActive = ratingFeaturesEnabled && ratingUi.quickRateMode
    val ratings = ratingUi.state.ratingsByReferenceKey
    val projectedSongs = remember(songs, selectedFilter, ratings, quickRateActive) {
        projectSongsForRatedCollection(
            songs = songs,
            filter = selectedFilter,
            ratingsByReferenceKey = ratings,
            quickRateActive = quickRateActive
        )
    }
    val searchedSongs = remember(projectedSongs, searchQuery) {
        filterSongsForSearch(projectedSongs, searchQuery)
    }
    val displayedSongs = remember(searchedSongs, sortOption, ratings) {
        sortSongsForLibrary(searchedSongs, sortOption, ratings)
    }

    if (quickRateActive && displayedSongs.isNotEmpty()) {
        SongList(
            songs = displayedSongs,
            currentSongId = currentSong?.id,
            recentlyAddedSongIds = recentlyAddedSongIds,
            onSongClick = onSongClick,
            onPlayNextClick = onPlayNextClick,
            onAddToQueueClick = onAddToQueueClick,
            onToggleFavoriteClick = onToggleFavoriteClick,
            favoriteMembershipKeys = favoriteMembershipKeys,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onEditSongTagsClick = onEditSongTagsClick,
            bottomContentPadding = bottomContentPadding,
            ratingValuesByReferenceKey = ratings,
            quickRatingMode = true,
            modifier = modifier.fillMaxSize()
        )
    } else if (quickRateActive || projectedSongs.isEmpty() || searchedSongs.isEmpty()) {
        Text(
            ratedCollectionEmptyMessage(
                filter = selectedFilter,
                searchQuery = searchQuery,
                quickRateActive = quickRateActive
            ),
            modifier = Modifier.padding(16.dp)
        )
    } else {
        LibraryLayoutTransition(
            viewMode = viewMode,
            modifier = modifier,
            listContent = {
                SongList(
                    songs = displayedSongs,
                    currentSongId = currentSong?.id,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    onSongClick = onSongClick,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onEditSongTagsClick = onEditSongTagsClick,
                    bottomContentPadding = bottomContentPadding,
                    ratingValuesByReferenceKey = ratings,
                    modifier = Modifier.fillMaxSize()
                )
            },
            gridContent = {
                SongGrid(
                    songs = displayedSongs,
                    currentSongId = currentSong?.id,
                    gridColumnCount = gridColumnCount,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onSongClick = onSongClick,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onEditSongTagsClick = onEditSongTagsClick,
                    bottomContentPadding = bottomContentPadding,
                    ratingValuesByReferenceKey = ratings,
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }
}

@Composable
fun FavoritesTabContent(
    songs: List<Song>,
    favoriteMembershipKeys: Set<String>,
    searchQuery: String,
    sortOption: LibrarySortOption,
    currentSong: Song?,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    recentlyAddedSongIds: Set<Long>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val favoriteSongs = songs.filter { song ->
        song.membershipKey() in favoriteMembershipKeys
    }

    val filteredSongs = filterSongsForSearch(
        songs = favoriteSongs,
        searchQuery = searchQuery
    )

    val displayedSongs = sortSongsForLibrary(
        songs = filteredSongs,
        sortOption = sortOption
    )

    if (favoriteSongs.isEmpty()) {
        Text(
            text = "No favorite songs yet.",
            modifier = Modifier.padding(16.dp)
        )
    } else if (filteredSongs.isEmpty()) {
        Text(
            text = "No favorite songs match your search.",
            modifier = Modifier.padding(16.dp)
        )
    } else {
        Column(
            modifier = modifier
        ) {
            Button(
                onClick = {
                    onAddSongsToPlaylistClick(displayedSongs)
                },
                enabled = displayedSongs.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = "Add all to playlist")
            }

            LibraryLayoutTransition(
                viewMode = viewMode,
                modifier = Modifier.weight(1f),
                listContent = {
                    SongList(
                        songs = displayedSongs,
                        currentSongId = currentSong?.id,
                        recentlyAddedSongIds = recentlyAddedSongIds,
                        favoriteMembershipKeys = favoriteMembershipKeys,
                        onSongClick = onSongClick,
                        onPlayNextClick = onPlayNextClick,
                        onAddToQueueClick = onAddToQueueClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        onAddToPlaylistClick = onAddToPlaylistClick,
                        onEditSongTagsClick = onEditSongTagsClick,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                gridContent = {
                    SongGrid(
                        songs = displayedSongs,
                        currentSongId = currentSong?.id,
                        gridColumnCount = gridColumnCount,
                        recentlyAddedSongIds = recentlyAddedSongIds,
                        favoriteMembershipKeys = favoriteMembershipKeys,
                        onSongClick = onSongClick,
                        onPlayNextClick = onPlayNextClick,
                        onAddToQueueClick = onAddToQueueClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        onAddToPlaylistClick = onAddToPlaylistClick,
                        onEditSongTagsClick = onEditSongTagsClick,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        }
    }
}

@Composable
fun ArtistsTabContent(
    songs: List<Song>,
    searchQuery: String,
    selectedArtistName: String?,
    currentSong: Song?,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    sortOption: LibrarySortOption,
    recentlyAddedSongIds: Set<Long>,
    onArtistSelected: (String) -> Unit,
    onAlbumSelected: (String) -> Unit,
    onBackFromArtist: () -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    favoriteMembershipKeys: Set<String>,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val artistSearchSongs = filterSongsByArtistSearch(
        songs = songs,
        searchQuery = searchQuery
    )

    if (songs.isEmpty()) {
        Text(
            text = "No artists found.",
            modifier = Modifier.padding(16.dp)
        )
    } else if (selectedArtistName == null) {
        if (artistSearchSongs.isEmpty()) {
            Text(
                text = "No artists match your search.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val onArtistPlay: (String, List<Song>) -> Unit = { _, artistSongs ->
                onPlaySongsClick(artistSongs, PlaybackShuffleMode.OFF)
            }
            val onArtistShuffle: (String, List<Song>) -> Unit = { _, artistSongs ->
                onPlaySongsClick(artistSongs, PlaybackShuffleMode.SONGS)
            }
            val onArtistPlayNext: (String, List<Song>) -> Unit =
                { artistName, artistSongs ->
                    onPlayNextSongsClick(artistName, artistSongs)
                }
            val onArtistAddToQueue: (String, List<Song>) -> Unit =
                { artistName, artistSongs ->
                    onAddSongsToQueueClick(artistName, artistSongs)
                }
            val onArtistAddToPlaylist: (String, List<Song>) -> Unit =
                { _, artistSongs ->
                    onAddSongsToPlaylistClick(artistSongs)
                }

            LibraryLayoutTransition(
                viewMode = viewMode,
                modifier = modifier,
                listContent = {
                    ArtistListScreen(
                        songs = artistSearchSongs,
                        onArtistClick = onArtistSelected,
                        sortOption = sortOption,
                        onArtistPlayClick = onArtistPlay,
                        onArtistShuffleClick = onArtistShuffle,
                        onArtistPlayNextClick = onArtistPlayNext,
                        onArtistAddToQueueClick = onArtistAddToQueue,
                        onArtistAddToPlaylistClick = onArtistAddToPlaylist,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                gridContent = {
                    ArtistGridScreen(
                        songs = artistSearchSongs,
                        onArtistClick = onArtistSelected,
                        sortOption = sortOption,
                        gridColumnCount = gridColumnCount,
                        onArtistPlayClick = onArtistPlay,
                        onArtistShuffleClick = onArtistShuffle,
                        onArtistPlayNextClick = onArtistPlayNext,
                        onArtistAddToQueueClick = onArtistAddToQueue,
                        onArtistAddToPlaylistClick = onArtistAddToPlaylist,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        }
    } else {
        val artistSongs = sortSongsForArtistDetail(
            songs.filter { song ->
                song.artist.ifBlank { "Unknown Artist" } == selectedArtistName
            }
        )

        ArtistDetailScreen(
            artistName = selectedArtistName,
            artistSongs = artistSongs,
            onBackClick = onBackFromArtist,
            onAlbumClick = onAlbumSelected,
            onPlayAllClick = {
                onPlaySongsClick(artistSongs, PlaybackShuffleMode.OFF)
            },
            onPlayAlbumClick = { albumSongs ->
                onPlaySongsClick(albumSongs, PlaybackShuffleMode.OFF)
            },
            onShuffleAlbumClick = { albumSongs ->
                onPlaySongsClick(albumSongs, PlaybackShuffleMode.SONGS)
            },
            onShuffleSongsClick = {
                onPlaySongsClick(artistSongs, PlaybackShuffleMode.SONGS)
            },
            onShuffleAlbumsClick = {
                onPlaySongsClick(
                    buildArtistAlbumShuffleQueue(
                        artistSongs = artistSongs,
                        shuffleSongsWithinAlbums = false
                    ),
                    PlaybackShuffleMode.ALBUMS
                )
            },
            onShuffleAlbumsAndSongsClick = {
                onPlaySongsClick(
                    buildArtistAlbumShuffleQueue(
                        artistSongs = artistSongs,
                        shuffleSongsWithinAlbums = true
                    ),
                    PlaybackShuffleMode.ALBUMS_AND_SONGS
                )
            },
            onPlayNextSongsClick = onPlayNextSongsClick,
            onAddSongsToQueueClick = onAddSongsToQueueClick,
            onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    }
}

@Composable
fun AlbumsTabContent(
    songs: List<Song>,
    searchQuery: String,
    selectedAlbumFolderPath: String?,
    currentSong: Song?,
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    sortOption: LibrarySortOption,
    recentlyAddedSongIds: Set<Long>,
    onAlbumSelected: (String) -> Unit,
    onBackFromAlbum: () -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    favoriteMembershipKeys: Set<String>,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val albumSearchSongs = filterSongsByAlbumSearch(
        songs = songs,
        searchQuery = searchQuery
    )

    if (songs.isEmpty()) {
        Text(
            text = "No albums found.",
            modifier = Modifier.padding(16.dp)
        )
    } else if (selectedAlbumFolderPath == null) {
        if (albumSearchSongs.isEmpty()) {
            Text(
                text = "No albums match your search.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val onAlbumPlay: (String, List<Song>) -> Unit = { _, albumSongs ->
                onPlaySongsClick(albumSongs, PlaybackShuffleMode.OFF)
            }
            val onAlbumShuffle: (String, List<Song>) -> Unit = { _, albumSongs ->
                onPlaySongsClick(albumSongs, PlaybackShuffleMode.SONGS)
            }
            val onAlbumPlayNext: (String, List<Song>) -> Unit =
                { albumTitle, albumSongs ->
                    onPlayNextSongsClick(albumTitle, albumSongs)
                }
            val onAlbumAddToQueue: (String, List<Song>) -> Unit =
                { albumTitle, albumSongs ->
                    onAddSongsToQueueClick(albumTitle, albumSongs)
                }
            val onAlbumAddToPlaylist: (String, List<Song>) -> Unit =
                { _, albumSongs ->
                    onAddSongsToPlaylistClick(albumSongs)
                }

            LibraryLayoutTransition(
                viewMode = viewMode,
                modifier = modifier,
                listContent = {
                    AlbumListScreen(
                        songs = albumSearchSongs,
                        onAlbumClick = onAlbumSelected,
                        sortOption = sortOption,
                        onAlbumPlayClick = onAlbumPlay,
                        onAlbumShuffleClick = onAlbumShuffle,
                        onAlbumPlayNextClick = onAlbumPlayNext,
                        onAlbumAddToQueueClick = onAlbumAddToQueue,
                        onAlbumAddToPlaylistClick = onAlbumAddToPlaylist,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                gridContent = {
                    AlbumGridScreen(
                        songs = albumSearchSongs,
                        onAlbumClick = onAlbumSelected,
                        sortOption = sortOption,
                        gridColumnCount = gridColumnCount,
                        onAlbumPlayClick = onAlbumPlay,
                        onAlbumShuffleClick = onAlbumShuffle,
                        onAlbumPlayNextClick = onAlbumPlayNext,
                        onAlbumAddToQueueClick = onAlbumAddToQueue,
                        onAlbumAddToPlaylistClick = onAlbumAddToPlaylist,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        }
    } else {
        val albumSongs = sortSongsByAlbumOrder(
            songs.filter { song ->
                song.folderPath == selectedAlbumFolderPath
            }
        )
        val firstSong = albumSongs.firstOrNull()
        val album = firstSong?.let { song ->
            LibraryAlbumGroup(
                key = selectedAlbumFolderPath,
                title = song.album.ifBlank { "Unknown Album" },
                artistText = buildLibraryAlbumArtistText(albumSongs),
                songs = albumSongs
            )
        }

        if (album == null) {
            Text(
                text = "Album is no longer available.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            AlbumDetailScreen(
                album = album,
                currentSongId = currentSong?.id,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onBackClick = onBackFromAlbum,
                onPlayAllClick = {
                    onPlaySongsClick(albumSongs, PlaybackShuffleMode.OFF)
                },
                onShuffleAllClick = {
                    onPlaySongsClick(albumSongs, PlaybackShuffleMode.SONGS)
                },
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onPlayNextSongsClick = onPlayNextSongsClick,
                onAddSongsToQueueClick = onAddSongsToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddAllToPlaylistClick = {
                    onAddSongsToPlaylistClick(albumSongs)
                },
                onEditSongTagsClick = onEditSongTagsClick,
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }
    }
}

internal fun buildArtistAlbumShuffleQueue(
    artistSongs: List<Song>,
    shuffleSongsWithinAlbums: Boolean
): List<Song> {
    return buildLibraryAlbumGroups(artistSongs)
        .shuffled()
        .flatMap { album ->
            if (shuffleSongsWithinAlbums) {
                album.songs.shuffled()
            } else {
                album.songs
            }
        }
}
