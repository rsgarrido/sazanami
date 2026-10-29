package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup

@Composable
internal fun pocketAdvanceArtistName(artist: LibraryArtistGroup): String =
    if (artist.identity.isUnknown) stringResource(R.string.player_unknown_artist) else artist.name

@Composable
internal fun PocketAdvanceArtists(
    navigation: PocketAdvanceNavigationState,
    artists: List<LibraryArtistGroup>,
    artistKeys: List<String>,
    isLibraryReady: Boolean,
    onActivateArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        PocketAdvanceBrowserEmpty(
            stringResource(if (isLibraryReady) R.string.player_wheel_no_artists else R.string.pocket_advance_library_pending),
            modifier.testTag("pocketAdvanceArtistsEmpty")
        )
        return
    }
    val cursor = navigation.artistsCursor
    val selectedIndex = navigation.selectedArtistIndex(artistKeys)
    LaunchedEffect(cursor.scrollRequest) {
        if (cursor.takeScrollRequest()) selectedIndex?.let { cursor.listState.revealPocketAdvanceBrowseItem(it) }
    }
    val description = stringResource(R.string.player_wheel_artists)
    LazyColumn(
        modifier.fillMaxSize().testTag("pocketAdvanceArtists").semantics { contentDescription = description },
        state = cursor.listState, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(artists, key = { _, artist -> artist.key }) { index, artist ->
            val title = pocketAdvanceArtistName(artist)
            PocketAdvanceBrowserRow(
                title, pluralStringResource(R.plurals.player_wheel_song_count, artist.songs.size, artist.songs.size),
                index == selectedIndex, stringResource(R.string.pocket_advance_open_entry, title),
                onClick = { onActivateArtist(artist.key) }, modifier = Modifier.testTag("pocketAdvanceArtistRow:" + artist.key)
            )
        }
    }
}
