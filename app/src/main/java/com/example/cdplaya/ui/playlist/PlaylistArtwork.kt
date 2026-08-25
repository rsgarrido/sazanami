package com.example.cdplaya.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistArtworkMode
import com.example.cdplaya.data.PlaylistArtworkStore
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.AppShellIcons

@Composable
fun PlaylistArtwork(
    playlist: Playlist,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customArtwork = remember(playlist.artworkMode, playlist.artworkReference) {
        if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
            PlaylistArtworkStore.fileFor(context, playlist.artworkReference)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (customArtwork != null) {
            ArtworkTile(
                model = customArtwork,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AutomaticPlaylistArtwork(
                songs = playlist.automaticArtworkSongs,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AutomaticPlaylistArtwork(
    songs: List<Song>,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val artwork = songs.take(4)
    when (artwork.size) {
        0 -> ArtworkTile(null, contentDescription, modifier)
        1 -> ArtworkTile(artwork[0].albumArtUri, contentDescription, modifier)
        2 -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            artwork.forEachIndexed { index, song ->
                ArtworkTile(
                    model = song.albumArtUri,
                    contentDescription = if (index == 0) contentDescription else null,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
        3 -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            ArtworkTile(
                model = artwork[0].albumArtUri,
                contentDescription = contentDescription,
                modifier = Modifier.weight(1f).fillMaxSize()
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                artwork.drop(1).forEach { song ->
                    ArtworkTile(
                        model = song.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
        }
        else -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            artwork.chunked(2).forEachIndexed { rowIndex, rowArtwork ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    rowArtwork.forEachIndexed { columnIndex, song ->
                        ArtworkTile(
                            model = song.albumArtUri,
                            contentDescription = if (rowIndex == 0 && columnIndex == 0) {
                                contentDescription
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppShellIcons.AlbumStack,
            contentDescription = if (model == null) contentDescription else null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.42f)
        )
        AsyncImage(
            model = model,
            contentDescription = if (model != null) contentDescription else null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

internal fun playlistMetadataText(playlist: Playlist): String = buildList {
    add(if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs")
    formatPlaylistDuration(playlist.totalDuration).takeIf(String::isNotBlank)?.let(::add)
}.joinToString(separator = " • ")

internal fun formatPlaylistDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours == 0L -> "$totalMinutes min"
        minutes == 0L -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}
