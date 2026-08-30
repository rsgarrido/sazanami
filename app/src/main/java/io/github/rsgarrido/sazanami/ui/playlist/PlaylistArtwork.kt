package io.github.rsgarrido.sazanami.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.Coil
import coil.request.ImageRequest
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistArtworkMode
import io.github.rsgarrido.sazanami.data.PlaylistArtworkStore
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.visual.VisualAssetIdentity
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.data.visual.PlaylistCollageAsset
import io.github.rsgarrido.sazanami.data.visual.PlaylistCollageStore
import io.github.rsgarrido.sazanami.data.visual.playlistCollageSignature
import io.github.rsgarrido.sazanami.data.visual.requestPolicy
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import kotlinx.coroutines.CancellationException

@Composable
fun PlaylistArtwork(
    playlist: Playlist,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL
) {
    val context = LocalContext.current
    val customArtwork = remember(playlist.playlistId, playlist.artworkMode, playlist.artworkReference, variant) {
        if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
            PlaylistArtworkStore.fileFor(context, playlist.playlistId, playlist.artworkReference, variant)
        } else {
            null
        }
    }
    val customIdentity = remember(playlist.playlistId, playlist.artworkReference) {
        PlaylistArtworkStore.identity(playlist.playlistId, playlist.artworkReference)
    }
    val customDisplayArtwork = remember(playlist.playlistId, playlist.artworkMode, playlist.artworkReference) {
        if (playlist.artworkMode == PlaylistArtworkMode.CUSTOM) {
            PlaylistArtworkStore.fileFor(
                context,
                playlist.playlistId,
                playlist.artworkReference,
                VisualAssetVariant.DISPLAY
            )
        } else {
            null
        }
    }
    VisualAssetDisplayPrefetchEffect(
        model = customDisplayArtwork,
        identity = customIdentity,
        enabled = variant == VisualAssetVariant.THUMBNAIL
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (customArtwork != null) {
            ArtworkTile(
                model = customArtwork,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                identity = customIdentity,
                variant = variant
            )
        } else {
            AutomaticPlaylistArtwork(
                playlistId = playlist.playlistId,
                songs = playlist.automaticArtworkSongs,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                variant = variant
            )
        }
    }
}

@Composable
private fun AutomaticPlaylistArtwork(
    playlistId: Long,
    songs: List<Song>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: VisualAssetVariant
) {
    val artwork = songs.take(4)
    val signature = remember(playlistId, artwork) {
        playlistCollageSignature(
            playlistId,
            artwork.map { song ->
                "${song.albumArtUri}:${song.artworkEnrichmentVersion}:${song.dateModifiedEpochSeconds}"
            }
        )
    }
    if (artwork.isEmpty()) {
        ArtworkTile(null, contentDescription, modifier)
        return
    }
    val context = LocalContext.current
    val store = remember(context) { PlaylistCollageStore(context) }
    val expected = remember(playlistId, signature) { store.expected(playlistId, signature) }
    var displayed by remember(playlistId) { mutableStateOf<PlaylistCollageAsset?>(expected) }
    var requestEpoch by remember(playlistId) { mutableIntStateOf(0) }
    LaunchedEffect(playlistId, signature) {
        val ready = try {
            store.ensure(
                playlistId = playlistId,
                signature = signature,
                orderedArtworkUris = artwork.mapNotNull(Song::albumArtUri)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (ready != null && ready.identity.revision == signature) {
            displayed = ready
            requestEpoch += 1
        }
    }
    val asset = displayed
    VisualAssetDisplayPrefetchEffect(
        model = asset?.displayFile,
        identity = asset?.identity,
        enabled = variant == VisualAssetVariant.THUMBNAIL && requestEpoch > 0
    )
    ArtworkTile(
        model = asset?.file(variant),
        contentDescription = contentDescription,
        modifier = modifier,
        identity = asset?.identity,
        variant = variant,
        reloadKey = requestEpoch
    )
}

@Composable
private fun ArtworkTile(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    identity: VisualAssetIdentity? = null,
    variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL,
    reloadKey: Any? = null
) {
    val context = LocalContext.current
    val request = remember(model, identity, variant, reloadKey) {
        if (model == null) null else ImageRequest.Builder(context)
            .data(model)
            .apply {
                identity?.requestPolicy(variant)?.let { policy ->
                    memoryCacheKey(policy.cacheKey)
                    diskCacheKey(policy.cacheKey)
                    policy.placeholderMemoryCacheKey?.let(::placeholderMemoryCacheKey)
                }
            }
            .crossfade(false)
            .build()
    }
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
            model = request,
            contentDescription = if (model != null) contentDescription else null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun VisualAssetDisplayPrefetchEffect(
    model: Any?,
    identity: VisualAssetIdentity?,
    enabled: Boolean
) {
    val context = LocalContext.current
    DisposableEffect(model, identity, enabled) {
        val disposable = if (enabled && model != null && identity != null) {
            val policy = identity.requestPolicy(VisualAssetVariant.DISPLAY)
            Coil.imageLoader(context).enqueue(
                ImageRequest.Builder(context)
                    .data(model)
                    .size(1024)
                    .memoryCacheKey(policy.cacheKey)
                    .diskCacheKey(policy.cacheKey)
                    .crossfade(false)
                    .build()
            )
        } else {
            null
        }
        onDispose { disposable?.dispose() }
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
