package com.example.cdplaya.ui.playlist

import android.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.data.stableKey
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.filterSongsForSearch
import com.example.cdplaya.ui.library.LibrarySearchBar

@Composable
internal fun PlaylistAddSongsScreen(
    playlistName: String,
    allSongs: List<Song>,
    playlistSongRows: List<PlaylistSong>,
    onDismiss: () -> Unit,
    onAddSongs: (List<Song>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val existingMembershipKeys = remember(playlistSongRows) {
        playlistSongRows.mapNotNullTo(mutableSetOf()) { row ->
            row.resolvedSong?.membershipKey()
        }
    }
    val existingLegacyKeys = remember(playlistSongRows) {
        playlistSongRows.mapTo(mutableSetOf(), PlaylistSong::songKey)
    }
    val uniqueSongs = remember(allSongs) {
        allSongs.distinctBy { song -> song.membershipKey() }
    }
    val filteredSongs = remember(uniqueSongs, searchQuery) {
        filterSongsForSearch(uniqueSongs, searchQuery)
    }
    val selectedSongs = remember(
        uniqueSongs,
        selectedKeys,
        existingMembershipKeys,
        existingLegacyKeys
    ) {
        uniqueSongs.filter { song ->
            song.membershipKey() in selectedKeys &&
                song.membershipKey() !in existingMembershipKeys &&
                song.stableKey() !in existingLegacyKeys
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add songs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = playlistName,
                            style = AppShellTypography.SongSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LibrarySearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it }
                )

                if (filteredSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching songs.",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(
                            items = filteredSongs,
                            key = { song -> song.membershipKey() }
                        ) { song ->
                            val membershipKey = song.membershipKey()
                            val alreadyInPlaylist = membershipKey in existingMembershipKeys ||
                                song.stableKey() in existingLegacyKeys
                            val isSelected = membershipKey in selectedKeys
                            ListItem(
                                leadingContent = {
                                    AsyncImage(
                                        model = song.albumArtUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(R.drawable.ic_media_play),
                                        placeholder = painterResource(R.drawable.ic_media_play)
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = song.title.ifBlank { "Unknown Title" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = if (alreadyInPlaylist) {
                                            "Already in playlist"
                                        } else {
                                            listOf(
                                                song.artist.ifBlank { "Unknown Artist" },
                                                song.album.ifBlank { "Unknown Album" }
                                            ).joinToString(" \u2022 ")
                                        },
                                        color = if (alreadyInPlaylist) {
                                            AppShellAccent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    if (alreadyInPlaylist) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Already in playlist",
                                            tint = AppShellAccent
                                        )
                                    } else {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null
                                        )
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !alreadyInPlaylist) {
                                    selectedKeys = if (isSelected) {
                                        selectedKeys - membershipKey
                                    } else {
                                        selectedKeys + membershipKey
                                    }
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onAddSongs(selectedSongs)
                        onDismiss()
                    },
                    enabled = selectedSongs.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Add (${selectedSongs.size})")
                }
            }
        }
    }
}
