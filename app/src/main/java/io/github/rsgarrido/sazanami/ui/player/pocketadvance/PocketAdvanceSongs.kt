package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song

@Composable
internal fun pocketAdvanceSongTitle(song: Song): String =
    song.title.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_title)

/** One song-row renderer for the supplied library order and the canonical artist-detail order. */
@Composable
internal fun PocketAdvanceSongs(
    navigation: PocketAdvanceNavigationState,
    songs: List<Song>,
    songKeys: List<String>,
    isLibraryReady: Boolean,
    onActivateSong: (String) -> Unit,
    modifier: Modifier = Modifier,
    artistName: String? = null
) {
    val isArtistSongs = navigation.browserPage is PocketAdvanceBrowserPage.ArtistSongs
    val listTag = if (isArtistSongs) "pocketAdvanceArtistSongs" else "pocketAdvanceSongs"
    if (songs.isEmpty()) {
        // Do not attach an empty lazy list: a temporary empty publication must retain its viewport.
        PocketAdvanceBrowserEmpty(
            stringResource(if (isLibraryReady) R.string.player_wheel_no_songs else R.string.pocket_advance_library_pending),
            modifier.testTag(listTag + "Empty")
        )
        return
    }
    val cursor = if (isArtistSongs) navigation.artistSongsCursor else null
    val listState = cursor?.listState ?: navigation.songsListState
    val selectedIndex = if (isArtistSongs) navigation.selectedArtistSongIndex(songKeys) else navigation.selectedSongIndex(songKeys)
    val scrollRequest = cursor?.scrollRequest ?: navigation.songsScrollRequest
    LaunchedEffect(scrollRequest, navigation.browserPage) {
        if (cursor?.takeScrollRequest() ?: navigation.takeSongsScrollRequest()) {
            selectedIndex?.let { listState.revealPocketAdvanceBrowseItem(it) }
        }
    }
    val description = if (isArtistSongs) stringResource(R.string.pocket_advance_artist_songs, artistName.orEmpty()) else null
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(listTag)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(songs, key = { index, _ -> songKeys[index] }) { index, song ->
            val key = songKeys[index]
            val title = pocketAdvanceSongTitle(song)
            val subtitle = if (isArtistSongs) song.album.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_album)
                else song.artist.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_artist)
            PocketAdvanceBrowserRow(
                title, subtitle, index == selectedIndex, stringResource(R.string.pocket_advance_play_song, title),
                onClick = { onActivateSong(key) },
                modifier = Modifier.testTag((if (isArtistSongs) "pocketAdvanceArtistSong:" else "pocketAdvanceSong:") + key)
            )
        }
    }
}
