package com.example.cdplaya.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.example.cdplaya.R
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistType
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    songsToAdd: List<Song>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist, List<Song>) -> Unit,
    onCreatePlaylist: (String, List<Song>) -> Unit
) {
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    val eligiblePlaylists = playlists.filter { it.type == PlaylistType.MANUAL }
    val distinctSongs = songsToAdd.distinctBy(Song::membershipKey)

    if (isCreatingPlaylist) {
        PlaylistNameDialog(
            title = "Create Playlist",
            existingPlaylistNames = playlists.map(Playlist::name),
            onDismiss = { isCreatingPlaylist = false },
            onConfirmClick = { name ->
                onCreatePlaylist(name, distinctSongs)
                onDismiss()
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add to Playlist")
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "create-playlist") {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = { Text("Create new playlist") },
                        modifier = Modifier.clickable { isCreatingPlaylist = true }
                    )
                }
                if (eligiblePlaylists.isEmpty()) {
                    item(key = "no-playlists") {
                        Text(
                            text = "No manual playlists yet.",
                            modifier = Modifier,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(
                        items = eligiblePlaylists,
                        key = { playlist -> playlist.playlistId }
                    ) { playlist ->
                        val songsMissing = distinctSongs.filter { song ->
                            song.membershipKey() !in playlist.songMembershipKeys
                        }
                        val presentCount = distinctSongs.size - songsMissing.size
                        val alreadyContainsAll = distinctSongs.isNotEmpty() &&
                            presentCount == distinctSongs.size
                        val songCountText = pluralStringResource(
                            R.plurals.song_count,
                            playlist.songCount,
                            playlist.songCount
                        )
                        ListItem(
                            leadingContent = {
                                PlaylistArtwork(
                                    playlist = playlist,
                                    contentDescription = "Artwork for ${playlist.name}",
                                    modifier = Modifier.size(48.dp)
                                )
                            },
                            headlineContent = {
                                Text(text = playlist.name)
                            },
                            supportingContent = {
                                Text(
                                    text = buildString {
                                        append(songCountText)
                                        when {
                                            alreadyContainsAll -> append(" • Already added")
                                            presentCount > 0 -> append(" • $presentCount selected already added")
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.clickable(enabled = !alreadyContainsAll) {
                                onPlaylistSelected(playlist, songsMissing)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
