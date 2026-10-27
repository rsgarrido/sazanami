package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song

@Composable
internal fun pocketAdvanceSongTitle(song: Song): String =
    song.title.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_title)

@Composable
private fun pocketAdvanceSongArtist(song: Song): String =
    song.artist.takeIf(String::isNotBlank) ?: stringResource(R.string.player_unknown_artist)

/** Supplied library order is authoritative. Only D-pad requests move the viewport automatically. */
@Composable
internal fun PocketAdvanceSongs(
    navigation: PocketAdvanceNavigationState,
    songs: List<Song>,
    songKeys: List<String>,
    isLibraryReady: Boolean,
    onActivateSong: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) {
        // Do not attach an empty lazy list: a temporary empty publication must retain its viewport.
        Box(modifier.fillMaxSize().testTag("pocketAdvanceSongsEmpty"), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (isLibraryReady) R.string.player_wheel_no_songs
                    else R.string.pocket_advance_library_pending),
                    color = PocketAdvanceColors.Ink, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                Text(stringResource(R.string.player_wheel_check_library), color = PocketAdvanceColors.MutedInk,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
        return
    }
    val listState = navigation.songsListState
    val selectedIndex = navigation.selectedSongIndex(songKeys)
    LaunchedEffect(navigation.songsScrollRequest) {
        if (navigation.takeSongsScrollRequest()) {
            navigation.selectedSongIndex(songKeys)?.let { index ->
                listState.revealPocketAdvanceSong(index)
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("pocketAdvanceSongs"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(songs, key = { index, _ -> songKeys[index] }) { index, song ->
            val key = songKeys[index]
            val isSelected = index == selectedIndex
            val title = pocketAdvanceSongTitle(song)
            val artist = pocketAdvanceSongArtist(song)
            val playLabel = stringResource(R.string.pocket_advance_play_song, title)
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("pocketAdvanceSong:$key")
                    .background(if (isSelected) PocketAdvanceColors.Ink else PocketAdvanceColors.Screen,
                        RoundedCornerShape(4.dp))
                    .border(1.dp, if (isSelected) PocketAdvanceColors.ScreenAccent else PocketAdvanceColors.ScreenShade,
                        RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClickLabel = playLabel, onClick = { onActivateSong(key) })
                    .semantics { selected = isSelected; contentDescription = "$title, $artist" }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val ink = if (isSelected) PocketAdvanceColors.Screen else PocketAdvanceColors.Ink
                Text(title, color = ink, fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = if (isSelected) PocketAdvanceColors.Screen else PocketAdvanceColors.MutedInk,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** All song rows use the same two single-line text slots, padding and minimum height. */
private suspend fun LazyListState.revealPocketAdvanceSong(index: Int) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val measured = visible.firstOrNull { it.index == index }
    val anchor = measured ?: (if (index < firstVisibleItemIndex) visible.firstOrNull() else visible.lastOrNull())
    if (anchor == null) {
        scrollToItem(index)
        return
    }
    if (index < visible.first().index - 1 || index > visible.last().index + 1) {
        // A wrap/reorder can cross the library. Reveal its nearest edge without animating every row.
        val bottomOffset = -(info.viewportEndOffset - info.viewportStartOffset - anchor.size).coerceAtLeast(0)
        scrollToItem(index, if (index > anchor.index) bottomOffset else 0)
        return
    }
    val start = anchor.offset + (index - anchor.index) * (anchor.size + info.mainAxisItemSpacing)
    val end = start + anchor.size
    val distance = when {
        start < info.viewportStartOffset -> start - info.viewportStartOffset
        end > info.viewportEndOffset -> end - info.viewportEndOffset
        else -> 0
    }
    // Complete within one repeat interval. A newer request cancels this effect instead of queuing work.
    if (distance != 0) animateScrollBy(distance.toFloat(), tween(durationMillis = 90))
}
