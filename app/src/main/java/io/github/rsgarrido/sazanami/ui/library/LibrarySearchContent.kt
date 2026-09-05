package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.playlist.PlaylistArtwork
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SEARCH_PREVIEW_LIMIT = 3

@Composable
fun LibrarySearchContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    query: String,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    onAlbumSelected: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    val selection = LocalLibrarySelectionUi.current
    var category by rememberSaveable { mutableStateOf(SearchCategory.ALL) }
    // A snapshot is independent of the query: grouping and normalization never run per row.
    val index by produceState<LibrarySearchIndex?>(null, songs, playlists) {
        value = null
        value = withContext(Dispatchers.Default) { LibrarySearchIndex(songs, playlists) }
    }
    val searchIndex = index
    val response by produceState<Triple<LibrarySearchIndex?, String, LibrarySearchResults>?>(null, searchIndex, query) {
        value = withContext(Dispatchers.Default) {
            Triple(searchIndex, query, searchIndex?.search(query) ?: LibrarySearchResults())
        }
    }
    val searching = index == null || response?.first !== index || response?.second != query
    val results = if (searching) LibrarySearchResults() else response?.third ?: LibrarySearchResults()
    val listState = rememberLazyListState()
    LaunchedEffect(query, category) {
        selection.onClear()
        selection.headerState.resetActions()
        listState.scrollToItem(0)
    }
    val matchingSongs = remember(results) {
        results.inCategory(SearchCategory.SONGS).map { (it as LibrarySearchResult.Track).song }
    }
    LaunchedEffect(matchingSongs) {
        if (selection.state.selectedKeys.any { key -> matchingSongs.none { it.membershipKey() == key } }) {
            selection.onClear()
        }
    }
    val visibleSongs = when (category) {
        SearchCategory.ALL -> matchingSongs.take(SEARCH_PREVIEW_LIMIT)
        SearchCategory.SONGS -> matchingSongs
        else -> emptyList()
    }
    val idle = LibrarySearchRanking.normalize(query).isEmpty()
    fun selectCategory(next: SearchCategory) {
        selection.onClear()
        selection.headerState.resetActions()
        category = next
    }

    val sections = if (category == SearchCategory.ALL) results.sectionOrder else listOf(category)
    val beforeSongSections = sections.takeWhile { it != SearchCategory.SONGS }
    val afterSongSections = sections.dropWhile { it != SearchCategory.SONGS }.drop(1)
    fun androidx.compose.foundation.lazy.LazyListScope.entitySections(sections: List<SearchCategory>) {
        sections
            .forEach { section ->
                val matches = results.inCategory(section)
                if (matches.isNotEmpty()) {
                    item(key = "search-heading-$section") { SearchSectionTitle(section) }
                    items(if (category == SearchCategory.ALL) matches.take(SEARCH_PREVIEW_LIMIT) else matches,
                        key = { "search-${it.category}-${it.key}" }) { result ->
                        SearchEntityRow(result, enabled = !selection.state.isActive,
                            onAlbumSelected, onArtistSelected, onPlaylistSelected,
                            onPlaySongsClick, onPlayNextSongsClick, onAddSongsToQueueClick,
                            onAddSongsToPlaylistClick)
                    }
                    if (category == SearchCategory.ALL && matches.size > SEARCH_PREVIEW_LIMIT) {
                        item(key = "search-more-$section") {
                            TextButton(onClick = { selectCategory(section) }) {
                                Text("See all ${matches.size} ${section.label.lowercase()}")
                            }
                        }
                    }
                }
            }
    }

    Column(modifier) {
        if (!idle) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchCategory.entries.forEach { option ->
                    FilterChip(selected = category == option, onClick = { selectCategory(option) },
                        label = { Text(option.label) })
                }
            }
        }
        SongList(
            songs = if (idle) emptyList() else visibleSongs,
            currentSongId = currentSong?.id,
            recentlyAddedSongIds = recentlyAddedSongIds,
            favoriteMembershipKeys = favoriteMembershipKeys,
            onSongClick = { song, _ -> onSongClick(song, matchingSongs) },
            onPlayNextClick = onPlayNextClick,
            onAddToQueueClick = onAddToQueueClick,
            onToggleFavoriteClick = onToggleFavoriteClick,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
            onEditSongTagsClick = onEditSongTagsClick,
            selectionEnabled = true,
            searchActive = true,
            showOverflowActions = true,
            additionalSongActions = { song -> buildList {
                findLibraryAlbumGroupForSong(song, index?.albums.orEmpty())?.let { album ->
                    add(LibraryItemAction("Go to album", Icons.Filled.Album, onClick = { onAlbumSelected(album.key) }))
                }
                add(LibraryItemAction("Go to artist", Icons.Filled.Person, onClick = { onArtistSelected(song.artist) }))
            } },
            listState = listState,
            bottomContentPadding = bottomContentPadding,
            modifier = Modifier.weight(1f),
            headerContent = {
                when {
                    idle -> Text("Search your library", Modifier.padding(24.dp))
                    searching -> Text("Searching…", Modifier.padding(24.dp))
                    results.inCategory(category).isEmpty() -> Text(
                        if (category == SearchCategory.ALL) "No results for \"$query\""
                        else "No ${category.label.lowercase()} for \"$query\"",
                        Modifier.padding(24.dp))
                    visibleSongs.isNotEmpty() -> SearchSectionTitle(SearchCategory.SONGS)
                }
            },
            beforeSongsContent = { if (!idle) entitySections(beforeSongSections) },
            afterSongsContent = {
                if (!idle) {
                    if (category == SearchCategory.ALL && matchingSongs.size > SEARCH_PREVIEW_LIMIT) {
                        item(key = "search-more-songs") {
                            TextButton(onClick = { selectCategory(SearchCategory.SONGS) }) {
                                Text("See all ${matchingSongs.size} songs")
                            }
                        }
                    }
                    entitySections(afterSongSections)
                }
            }
        )
    }
}

@Composable
private fun SearchSectionTitle(category: SearchCategory) {
    Text(category.label, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun SearchEntityRow(
    result: LibrarySearchResult,
    enabled: Boolean,
    onAlbumSelected: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayNextSongsClick: (String, List<Song>) -> Unit,
    onAddSongsToQueueClick: (String, List<Song>) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit
) {
    var actionTarget by remember(result) { mutableStateOf<LibraryItemActionSheetTarget?>(null) }
    val queueUi = LocalLibraryQueueUi.current
    val pictureUi = LocalArtistPictureUi.current
    val artworkModifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
    ListItem(
        headlineContent = { Text(result.title) },
        supportingContent = { Text(when (result) {
            is LibrarySearchResult.Album -> "Album · ${result.album.artistText}"
            is LibrarySearchResult.Artist -> "Artist · ${result.artist.songs.size} songs"
            is LibrarySearchResult.PlaylistItem -> "Playlist · ${result.playlist.songCount} songs"
            is LibrarySearchResult.Track -> "Song"
        }) },
        leadingContent = {
            when (result) {
                is LibrarySearchResult.Album -> AsyncImage(result.album.songs.firstOrNull()?.albumArtUri,
                    "Artwork for ${result.title}", modifier = artworkModifier, contentScale = ContentScale.Crop,
                    error = painterResource(android.R.drawable.ic_media_play),
                    placeholder = painterResource(android.R.drawable.ic_media_play))
                is LibrarySearchResult.Artist -> ArtistPicture(result.artist.identity,
                    result.artist.songs.firstOrNull()?.albumArtUri, "Artwork for ${result.title}", artworkModifier)
                is LibrarySearchResult.PlaylistItem -> PlaylistArtwork(result.playlist, "Artwork for ${result.title}", artworkModifier)
                is LibrarySearchResult.Track -> Unit
            }
        },
        trailingContent = {
            if (enabled && (result is LibrarySearchResult.Album || result is LibrarySearchResult.Artist)) {
                IconButton(onClick = {
                    actionTarget = when (result) {
                        is LibrarySearchResult.Album -> albumActionSheetTarget(
                            albumTitle = result.title,
                            subtitle = result.album.artistText,
                            artworkUri = result.album.songs.firstOrNull()?.albumArtUri,
                            albumSongs = result.album.songs,
                            onPlayClick = { _, tracks -> onPlaySongsClick(tracks, PlaybackShuffleMode.OFF) },
                            onShuffleClick = { _, tracks -> onPlaySongsClick(tracks, PlaybackShuffleMode.SONGS) },
                            onPlayNextClick = onPlayNextSongsClick,
                            onAddToQueueClick = onAddSongsToQueueClick,
                            onAddToAnotherQueueClick = queueUi.onAddToAnotherQueue,
                            onPlayInNewQueueClick = queueUi.onPlayInNewQueue,
                            onAddToPlaylistClick = { _, tracks -> onAddSongsToPlaylistClick(tracks) }
                        )
                        is LibrarySearchResult.Artist -> artistActionSheetTarget(
                            artistName = result.title,
                            subtitle = "${result.artist.songs.size} songs",
                            artworkUri = result.artist.songs.firstOrNull()?.albumArtUri,
                            artistIdentity = result.artist.identity,
                            hasCustomPicture = result.artist.key in pictureUi.assignments,
                            onChoosePicture = pictureUi.onChoosePicture,
                            onRemovePicture = pictureUi.onRemovePicture,
                            artistSongs = result.artist.songs,
                            onPlayClick = { _, tracks -> onPlaySongsClick(tracks, PlaybackShuffleMode.OFF) },
                            onShuffleClick = { _, tracks -> onPlaySongsClick(tracks, PlaybackShuffleMode.SONGS) },
                            onPlayNextClick = onPlayNextSongsClick,
                            onAddToQueueClick = onAddSongsToQueueClick,
                            onAddToAnotherQueueClick = queueUi.onAddToAnotherQueue,
                            onPlayInNewQueueClick = queueUi.onPlayInNewQueue,
                            onAddToPlaylistClick = { _, tracks -> onAddSongsToPlaylistClick(tracks) }
                        )
                        else -> null
                    }
                }) { Icon(Icons.Filled.MoreVert, "Actions for ${result.title}") }
            }
        },
        modifier = Modifier.clickable(enabled = enabled) {
            when (result) {
                is LibrarySearchResult.Album -> onAlbumSelected(result.album.key)
                is LibrarySearchResult.Artist -> onArtistSelected(result.artist.name)
                is LibrarySearchResult.PlaylistItem -> onPlaylistSelected(result.playlist)
                is LibrarySearchResult.Track -> Unit
            }
        }
    )
    actionTarget?.let { LibraryItemActionSheet(it, onDismissRequest = { actionTarget = null }) }
}
