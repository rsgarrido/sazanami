package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionUiState

@Immutable
data class LibrarySelectionUiEnvironment(
    val state: LibrarySelectionUiState = LibrarySelectionUiState(),
    val allSongs: List<Song> = emptyList(),
    val favoriteMembershipKeys: Set<String> = emptySet(),
    val onEnter: (LibrarySelectionEntity, String) -> Unit = { _, _ -> },
    val onToggle: (LibrarySelectionEntity, String) -> Unit = { _, _ -> },
    val onSelectDisplayed: (LibrarySelectionEntity, Collection<String>) -> Unit = { _, _ -> },
    val onClear: () -> Unit = {},
    val onPlayNext: (List<Song>) -> Unit = {},
    val onAddToQueue: (List<Song>) -> Unit = {},
    val onAddToAnotherQueue: (List<Song>) -> Unit = {},
    val onPlayInNewQueue: (String, List<Song>) -> Unit = { _, _ -> },
    val onApplyFavoriteBatch: (List<Song>) -> Unit = {}
)

val LocalLibrarySelectionUi = staticCompositionLocalOf { LibrarySelectionUiEnvironment() }

internal fun resolveSelectedSongs(
    selectedKeys: Set<String>,
    displayedSongs: List<Song>,
    fallbackSongs: List<Song>
): List<Song> = (displayedSongs + fallbackSongs)
    .distinctBy(Song::membershipKey)
    .filter { it.membershipKey() in selectedKeys }

internal fun resolveSelectedAlbums(
    selectedKeys: Set<String>,
    displayedAlbums: List<LibraryAlbumGroup>,
    fallbackAlbums: List<LibraryAlbumGroup>
): List<LibraryAlbumGroup> = (displayedAlbums + fallbackAlbums)
    .distinctBy(LibraryAlbumGroup::key)
    .filter { it.key in selectedKeys }

internal fun isSongSelectionMoreAction(label: String, rateLabel: String): Boolean =
    label == rateLabel || label == "Edit tags" || "Home" in label

internal fun isAlbumSelectionMoreAction(label: String): Boolean =
    label == "Play" || label == "Shuffle" || label == "Edit album metadata" ||
        "Home" in label

@Composable
internal fun LibrarySelectionHeader(
    entity: LibrarySelectionEntity,
    displayedKeys: List<String>,
    searchActive: Boolean,
    onMoreClick: (() -> Unit)?
) {
    val selection = LocalLibrarySelectionUi.current
    if (selection.state.entity != entity || !selection.state.isActive) return

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = selection.onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
            Text(
                text = "${selection.state.selectedCount} selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { selection.onSelectDisplayed(entity, displayedKeys) },
                enabled = displayedKeys.isNotEmpty()
            ) {
                Text(if (searchActive) "Select results" else "Select all")
            }
            if (selection.state.selectedCount == 1 && onMoreClick != null) {
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                }
            }
        }
    }
}

@Composable
internal fun LibrarySelectionActionBar(
    selectedSongs: () -> List<Song>,
    onAddToPlaylist: (List<Song>) -> Unit,
    favoritesEnabled: Boolean,
    newQueueName: () -> String = { "" },
    modifier: Modifier = Modifier
) {
    val selection = LocalLibrarySelectionUi.current
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(modifier = modifier, tonalElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                onClick = {
                val songs = selectedSongs()
                if (songs.isNotEmpty()) {
                    selection.onPlayNext(songs)
                    selection.onClear()
                }
            }) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Text("Play next")
            }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                onClick = {
                val songs = selectedSongs()
                if (songs.isNotEmpty()) {
                    selection.onAddToQueue(songs)
                    selection.onClear()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                Text("Queue")
            }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                onClick = {
                val songs = selectedSongs()
                if (songs.isNotEmpty()) onAddToPlaylist(songs)
            }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                Text("Playlist")
            }
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More batch actions")
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add to another queue...") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    },
                    onClick = {
                        overflowExpanded = false
                        val songs = selectedSongs()
                        if (songs.isNotEmpty()) selection.onAddToAnotherQueue(songs)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play in new queue") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = {
                        overflowExpanded = false
                        val songs = selectedSongs()
                        if (songs.isNotEmpty()) {
                            selection.onPlayInNewQueue(newQueueName(), songs)
                            selection.onClear()
                        }
                    }
                )
                if (favoritesEnabled) {
                    val allFavorite = selection.state.selectedKeys.isNotEmpty() &&
                        selection.state.selectedKeys.all {
                            it in selection.favoriteMembershipKeys
                        }
                    DropdownMenuItem(
                        text = {
                            Text(if (allFavorite) "Remove from favorites" else "Add to favorites")
                        },
                        leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                        onClick = {
                            overflowExpanded = false
                            val songs = selectedSongs()
                            if (songs.isNotEmpty()) {
                                selection.onApplyFavoriteBatch(songs)
                                selection.onClear()
                            }
                        }
                    )
                }
            }
        }
    }
}
