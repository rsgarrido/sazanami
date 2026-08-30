package io.github.rsgarrido.sazanami.ui.queue

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.rsgarrido.sazanami.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class QueueSnackbarActions(
    val recentlyAddedSongIds: Set<Long>,
    val addToQueue: (Song) -> Unit,
    val playNext: (Song) -> Unit,
    val playNextSongs: (String, List<Song>) -> Unit,
    val addSongsToQueue: (String, List<Song>) -> Unit
)

@Composable
fun rememberQueueSnackbarActions(
    snackbarHostState: SnackbarHostState,
    onAddToQueueClick: (Song) -> Unit,
    onUndoAddToQueueClick: (Song) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onUndoPlayNextClick: (Song) -> Unit,
    onPlayNextSongsClick: (List<Song>) -> Unit,
    onUndoPlayNextSongsClick: (List<Song>) -> Unit,
    onAddSongsToQueueClick: (List<Song>) -> Unit,
    onUndoAddSongsToQueueClick: (List<Song>) -> Unit
): QueueSnackbarActions {
    val coroutineScope = rememberCoroutineScope()
    var recentlyAddedSongIds by remember { mutableStateOf(setOf<Long>()) }

    fun markSongsAsRecentlyAdded(songs: List<Song>) {
        recentlyAddedSongIds = recentlyAddedSongIds + songs.map { song ->
            song.id
        }.toSet()
    }

    fun clearRecentlyAddedSongs(songs: List<Song>) {
        recentlyAddedSongIds = recentlyAddedSongIds - songs.map { song ->
            song.id
        }.toSet()
    }

    return QueueSnackbarActions(
        recentlyAddedSongIds = recentlyAddedSongIds,
        addToQueue = { song ->
            onAddToQueueClick(song)
            markSongsAsRecentlyAdded(listOf(song))

            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "\"${song.title}\" added to queue",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )

                if (result == SnackbarResult.ActionPerformed) {
                    onUndoAddToQueueClick(song)
                }

                delay(300)
                clearRecentlyAddedSongs(listOf(song))
            }
        },
        playNext = { song ->
            onPlayNextClick(song)
            markSongsAsRecentlyAdded(listOf(song))

            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "\"${song.title}\" will play next",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )

                if (result == SnackbarResult.ActionPerformed) {
                    onUndoPlayNextClick(song)
                }

                delay(300)
                clearRecentlyAddedSongs(listOf(song))
            }
        },
        playNextSongs = { label, songsToAdd ->
            if (songsToAdd.isNotEmpty()) {
                onPlayNextSongsClick(songsToAdd)
                markSongsAsRecentlyAdded(songsToAdd)

                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "\"$label\" will play next",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        onUndoPlayNextSongsClick(songsToAdd)
                    }

                    delay(300)
                    clearRecentlyAddedSongs(songsToAdd)
                }
            }
        },
        addSongsToQueue = { label, songsToAdd ->
            if (songsToAdd.isEmpty()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "No playable songs in \"$label\"",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
            } else {
                onAddSongsToQueueClick(songsToAdd)
                markSongsAsRecentlyAdded(songsToAdd)

                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "\"$label\" added to queue",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        onUndoAddSongsToQueueClick(songsToAdd)
                    }

                    delay(300)
                    clearRecentlyAddedSongs(songsToAdd)
                }
            }
        }
    )
}
