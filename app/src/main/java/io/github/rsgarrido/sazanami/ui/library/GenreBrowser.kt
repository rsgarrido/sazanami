package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.GenreCollection
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.buildGenreCollections
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import io.github.rsgarrido.sazanami.ui.AppShellIcons

@Composable
fun GenresTabContent(
    songs: List<Song>,
    searchQuery: String,
    selectedGenreKey: String?,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onGenreSelected: (String) -> Unit,
    onBackFromGenre: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val genreGroups = remember(songs) { buildGenreCollections(songs) }
    val selectedGenre = remember(genreGroups, selectedGenreKey) {
        genreGroups.firstOrNull { genre -> genre.key == selectedGenreKey }
    }

    if (selectedGenreKey != null && selectedGenre == null) {
        LaunchedEffect(selectedGenreKey, genreGroups) {
            onBackFromGenre()
        }
    }

    if (selectedGenreKey == null || selectedGenre == null) {
        val visibleGenres = remember(genreGroups, searchQuery) {
            val query = searchQuery.trim()
            if (query.isEmpty()) genreGroups else genreGroups.filter { genre ->
                genre.name.contains(query, ignoreCase = true)
            }
        }

        GenreListScreen(
            genres = visibleGenres,
            onGenreClick = onGenreSelected,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    } else {
        val songCountText = pluralStringResource(
            R.plurals.song_count,
            selectedGenre.songs.size,
            selectedGenre.songs.size
        )
        SongGroupDetailScreen(
            title = selectedGenre.name,
            subtitle = songCountText,
            songs = selectedGenre.songs,
            currentSongId = currentSong?.id,
            recentlyAddedSongIds = recentlyAddedSongIds,
            showAlbumName = true,
            showTrackNumbers = false,
            onBackClick = onBackFromGenre,
            onPlayAllClick = {
                onPlaySongsClick(selectedGenre.songs, PlaybackShuffleMode.OFF)
            },
            onShuffleAllClick = {
                onPlaySongsClick(selectedGenre.songs, PlaybackShuffleMode.SONGS)
            },
            onSongClick = onSongClick,
            onPlayNextClick = onPlayNextClick,
            onAddToQueueClick = onAddToQueueClick,
            favoriteMembershipKeys = favoriteMembershipKeys,
            onToggleFavoriteClick = onToggleFavoriteClick,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onAddAllToPlaylistClick = {
                onAddSongsToPlaylistClick(selectedGenre.songs)
            },
            onEditSongTagsClick = onEditSongTagsClick,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    }
}

@Composable
private fun GenreListScreen(
    genres: List<GenreCollection>,
    onGenreClick: (String) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No genres match your search.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        items(
            items = genres,
            key = GenreCollection::key
        ) { genre ->
            val songCountText = pluralStringResource(
                R.plurals.song_count,
                genre.songs.size,
                genre.songs.size
            )
            ListItem(
                leadingContent = {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppShellIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                },
                headlineContent = { Text(genre.name) },
                supportingContent = { Text(songCountText) },
                modifier = Modifier
                    .semantics { contentDescription = "Open ${genre.name}" }
                    .clickable { onGenreClick(genre.key) }
            )
        }
    }
}
