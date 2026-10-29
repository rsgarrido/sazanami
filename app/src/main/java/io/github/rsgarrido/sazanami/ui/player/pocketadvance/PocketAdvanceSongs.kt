package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.resolveAlbumDiscNumbers
import io.github.rsgarrido.sazanami.data.trackNumberWithinDisc
import io.github.rsgarrido.sazanami.ui.getDisplayTrackNumber
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumDiscSections

@Composable
internal fun pocketAdvanceSongTitle(song: Song): String =
    song.title.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_title)

/** Shared song rows preserve the current library, artist, or album playback order. */
@Composable
internal fun PocketAdvanceSongs(
    navigation: PocketAdvanceNavigationState,
    songs: List<Song>,
    songKeys: List<String>,
    isLibraryReady: Boolean,
    onActivateSong: (String) -> Unit,
    modifier: Modifier = Modifier,
    artistName: String? = null,
    albumTitle: String? = null
) {
    val isArtistSongs = navigation.browserPage is PocketAdvanceBrowserPage.ArtistSongs
    val isAlbumRecord = navigation.browserPage is PocketAdvanceBrowserPage.AlbumRecord
    val listTag = when {
        isAlbumRecord -> "pocketAdvanceAlbumRecord"
        isArtistSongs -> "pocketAdvanceArtistSongs"
        else -> "pocketAdvanceSongs"
    }
    if (songs.isEmpty()) {
        // Do not attach an empty lazy list: a temporary empty publication must retain its viewport.
        PocketAdvanceBrowserEmpty(
            stringResource(if (isLibraryReady) R.string.player_wheel_no_songs else R.string.pocket_advance_library_pending),
            modifier.testTag(listTag + "Empty")
        )
        return
    }
    val cursor = when {
        isAlbumRecord -> navigation.albumTracksCursor
        isArtistSongs -> navigation.artistSongsCursor
        else -> null
    }
    val listState = cursor?.listState ?: navigation.songsListState
    val selectedIndex = when {
        isAlbumRecord -> navigation.selectedAlbumTrackIndex(songKeys)
        isArtistSongs -> navigation.selectedArtistSongIndex(songKeys)
        else -> navigation.selectedSongIndex(songKeys)
    }
    val scrollRequest = cursor?.scrollRequest ?: navigation.songsScrollRequest
    LaunchedEffect(scrollRequest, navigation.browserPage) {
        if (cursor?.takeScrollRequest() ?: navigation.takeSongsScrollRequest()) {
            selectedIndex?.let { listState.revealPocketAdvanceBrowseItem(it) }
        }
    }
    val description = when {
        isAlbumRecord -> stringResource(R.string.pocket_advance_album_tracks, albumTitle.orEmpty())
        isArtistSongs -> stringResource(R.string.pocket_advance_artist_songs, artistName.orEmpty())
        else -> null
    }
    val rowTagPrefix = when {
        isAlbumRecord -> "pocketAdvanceAlbumTrack:"
        isArtistSongs -> "pocketAdvanceArtistSong:"
        else -> "pocketAdvanceSong:"
    }
    val discNumbers = remember(songs, isAlbumRecord) { if (isAlbumRecord) resolveAlbumDiscNumbers(songs) else emptyMap() }
    val showDiscs = remember(songs, isAlbumRecord) { isAlbumRecord && buildLibraryAlbumDiscSections(songs).size > 1 }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(listTag)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(songs, key = { index, _ -> songKeys[index] }) { index, song ->
            val key = songKeys[index]
            val songTitle = pocketAdvanceSongTitle(song)
            val title = if (isAlbumRecord && song.trackNumberWithinDisc() != null)
                stringResource(R.string.pocket_advance_numbered_track, getDisplayTrackNumber(song.trackNumber), songTitle) else songTitle
            val artist = song.artist.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_artist)
            val discLabel = if (showDiscs) discNumbers[key]?.let { stringResource(R.string.library_album_disc, it) }
                ?: stringResource(R.string.library_album_other_tracks) else null
            val subtitle = when {
                isArtistSongs -> song.album.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_album)
                discLabel != null -> discLabel + " · " + artist
                else -> artist
            }
            PocketAdvanceBrowserRow(
                title, subtitle, index == selectedIndex, stringResource(R.string.pocket_advance_play_song, songTitle),
                onClick = { onActivateSong(key) },
                modifier = Modifier.testTag(rowTagPrefix + key)
            )
        }
    }
}
