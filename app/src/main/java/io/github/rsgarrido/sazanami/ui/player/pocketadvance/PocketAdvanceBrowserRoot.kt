package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song

@Composable
internal fun pocketAdvanceRootEntryTitle(entry: PocketAdvanceRootEntry): String = stringResource(
    when (entry) {
        PocketAdvanceRootEntry.NowPlaying -> R.string.player_wheel_now_playing
        PocketAdvanceRootEntry.Songs -> R.string.player_wheel_songs
        PocketAdvanceRootEntry.Artists -> R.string.player_wheel_artists
        PocketAdvanceRootEntry.Albums -> R.string.player_wheel_albums
    }
)

@Composable
internal fun pocketAdvanceConfirmLabel(
    navigation: PocketAdvanceNavigationState, selectedSong: Song? = null, selectedArtistName: String? = null
): String {
    if (navigation.browserPage == PocketAdvanceBrowserPage.Artists) {
        return if (selectedArtistName == null) stringResource(R.string.pocket_advance_no_artist)
        else stringResource(R.string.pocket_advance_open_entry, selectedArtistName)
    }
    if (navigation.browserPage == PocketAdvanceBrowserPage.Songs || navigation.browserPage is PocketAdvanceBrowserPage.ArtistSongs) {
        return if (selectedSong == null) stringResource(R.string.pocket_advance_no_song)
        else stringResource(R.string.pocket_advance_play_song, pocketAdvanceSongTitle(selectedSong))
    }
    val title = pocketAdvanceRootEntryTitle(navigation.selectedRootEntry)
    return stringResource(
        if (navigation.selectedRootEntry.isAvailable) R.string.pocket_advance_open_entry
        else R.string.pocket_advance_unavailable_entry,
        title
    )
}

/** Root only: unavailable entries advertise the intended structure without opening placeholders. */
@Composable
internal fun PocketAdvanceBrowserRoot(
    navigation: PocketAdvanceNavigationState,
    modifier: Modifier = Modifier
) {
    val listState = navigation.rootListState
    LaunchedEffect(navigation.rootScrollRequest) {
        if (navigation.takeRootScrollRequest()) {
            listState.animateScrollToItem(navigation.selectedRootEntry.ordinal)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("pocketAdvanceBrowser"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(PocketAdvanceRootEntry.entries, key = { it.name }) { entry ->
            val selected = navigation.selectedRootEntry == entry
            val unavailable = stringResource(R.string.pocket_advance_unavailable)
            val title = pocketAdvanceRootEntryTitle(entry)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("pocketAdvanceRoot${entry.name}")
                    .background(
                        if (selected) PocketAdvanceColors.Ink else PocketAdvanceColors.Screen,
                        RoundedCornerShape(4.dp)
                    )
                    .border(1.dp, if (selected) PocketAdvanceColors.ScreenAccent else PocketAdvanceColors.ScreenShade,
                        RoundedCornerShape(4.dp))
                    .selectable(
                        selected = selected, enabled = entry.isAvailable, role = Role.Button,
                        onClick = { navigation.activateRootEntry(entry) }
                    )
                    .semantics { if (!entry.isAvailable) stateDescription = unavailable }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (selected) PocketAdvanceColors.Screen else PocketAdvanceColors.Ink,
                        fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!entry.isAvailable) {
                        Text(unavailable, color = if (selected) PocketAdvanceColors.Screen else PocketAdvanceColors.MutedInk,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
