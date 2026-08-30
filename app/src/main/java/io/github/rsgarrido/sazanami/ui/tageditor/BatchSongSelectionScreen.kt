package io.github.rsgarrido.sazanami.ui.tageditor

import android.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.filterSongsForSearch
import io.github.rsgarrido.sazanami.ui.library.LibrarySearchBar

@Composable
fun BatchSongSelectionScreen(
    songs: List<Song>,
    isPreparing: Boolean,
    onDismiss: () -> Unit,
    onContinue: (List<Song>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val uniqueSongs = remember(songs) { songs.distinctBy(Song::membershipKey) }
    val filteredSongs = remember(uniqueSongs, searchQuery) {
        filterSongsForSearch(uniqueSongs, searchQuery)
    }
    val selectedSongs = remember(uniqueSongs, selectedKeys) {
        uniqueSongs.filter { song -> song.membershipKey() in selectedKeys }
    }

    Dialog(
        onDismissRequest = { if (!isPreparing) onDismiss() },
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
                    IconButton(onClick = onDismiss, enabled = !isPreparing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Select tracks", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${selectedSongs.size} selected for metadata planning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LibrarySearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            selectedKeys = selectedKeys + filteredSongs.map(Song::membershipKey)
                        },
                        enabled = filteredSongs.isNotEmpty() && !isPreparing
                    ) {
                        Text(if (searchQuery.isBlank()) "Select all" else "Select results")
                    }
                    TextButton(
                        onClick = { selectedKeys = emptySet() },
                        enabled = selectedKeys.isNotEmpty() && !isPreparing
                    ) {
                        Text("Clear")
                    }
                }

                if (filteredSongs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No matching tracks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredSongs, key = Song::membershipKey) { song ->
                            val key = song.membershipKey()
                            val isSelected = key in selectedKeys
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
                                        song.title.ifBlank { "Unknown Title" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        listOf(
                                            song.artist.ifBlank { "Unknown Artist" },
                                            song.album.ifBlank { "Unknown Album" }
                                        ).joinToString(" • "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                },
                                modifier = Modifier.clickable(enabled = !isPreparing) {
                                    selectedKeys = if (isSelected) {
                                        selectedKeys - key
                                    } else {
                                        selectedKeys + key
                                    }
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = { onContinue(selectedSongs) },
                    enabled = selectedSongs.size >= 2 && !isPreparing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Continue (${selectedSongs.size})")
                    }
                }
            }
        }
    }
}
