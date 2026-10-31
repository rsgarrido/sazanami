package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.player.RetainedArtworkImage
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun PocketHorizonNowPlaying(
    playbackState: PlaybackUiState,
    progressState: StateFlow<PlaybackProgressUiState>,
    layout: PocketHorizonLayout,
    enabled: Boolean,
    seekEnabled: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onViewArtwork: ((Song) -> Unit)?,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier,
    measurements: PocketHorizonEndpointMeasurements? = null
) {
    val artwork: @Composable () -> Unit = {
        PocketHorizonArtwork(playbackState.currentSong, layout.artworkSize, enabled, onViewArtwork, style,
            Modifier.horizonAnchor(measurements, PocketHorizonAnchor.Artwork))
    }
    val details: @Composable () -> Unit = {
        Column(Modifier.width(layout.detailsWidth)) {
            PocketHorizonMetadata(playbackState.currentSong, style, measurements)
            Spacer(Modifier.height(PocketHorizonLayoutMetrics.DetailsGap))
            PocketHorizonTransport(playbackState.isPlaying, playbackState.playPauseIntent,
                enabled, previousEnabled, nextEnabled,
                onPlayPauseClick, onPreviousClick, onNextClick, style, measurements = measurements)
        }
    }
    if (layout.isWide) {
        Column(modifier.fillMaxWidth().testTag("pocketHorizonNowPlaying"),
            verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PocketHorizonLayoutMetrics.ContentGap),
            verticalAlignment = Alignment.CenterVertically) { artwork(); details() }
        Spacer(Modifier.height(PocketHorizonLayoutMetrics.DetailsGap))
        PocketHorizonProgress(progressState, playbackState.currentSong?.stableUiKey(), enabled && seekEnabled,
            onSeekChange, style, Modifier.horizonAnchor(measurements, PocketHorizonAnchor.ProgressRegion))
        }
    } else {
        Column(modifier.fillMaxWidth().testTag("pocketHorizonNowPlaying"),
            verticalArrangement = Arrangement.spacedBy(PocketHorizonLayoutMetrics.ContentGap),
            horizontalAlignment = Alignment.CenterHorizontally) {
            artwork(); details()
            PocketHorizonProgress(progressState, playbackState.currentSong?.stableUiKey(), enabled && seekEnabled,
                onSeekChange, style, Modifier.horizonAnchor(measurements, PocketHorizonAnchor.ProgressRegion))
        }
    }
}

@Composable
internal fun PocketHorizonMetadata(song: Song?, style: PocketHorizonStyle, measurements: PocketHorizonEndpointMeasurements?,
    modifier: Modifier = Modifier) {
    val metrics = PocketHorizonLayoutMetrics
    val typography = TextStyle(fontFamily = FontFamily.SansSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    Column(modifier.fillMaxWidth().horizonAnchor(measurements, PocketHorizonAnchor.Metadata),
        verticalArrangement = Arrangement.spacedBy(metrics.MetadataGap)) {
        Text(song?.title?.takeIf { it.isNotBlank() }
            ?: stringResource(if (song == null) R.string.pocket_horizon_no_track else R.string.library_song_unknown_title),
            modifier = Modifier.fillMaxWidth().testTag("pocketHorizonTitle"),
            color = style.text, style = typography, fontSize = metrics.TitleFontSize,
            lineHeight = metrics.TitleLineHeight, fontWeight = FontWeight.Medium,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (song == null) stringResource(R.string.pocket_horizon_no_track_hint)
            else song.artist.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_song_unknown_artist),
            modifier = Modifier.fillMaxWidth().testTag("pocketHorizonArtist"),
            color = style.secondaryText, style = typography, fontSize = metrics.ArtistFontSize,
            lineHeight = metrics.ArtistLineHeight, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (song != null) {
            Text(song.album.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_song_unknown_album),
                modifier = Modifier.fillMaxWidth().testTag("pocketHorizonAlbum"),
                color = style.secondaryText, style = typography, fontSize = metrics.AlbumFontSize,
                lineHeight = metrics.AlbumLineHeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            // Retain the geometry without inventing album metadata for the empty state.
            Spacer(Modifier.heightIn(min = with(LocalDensity.current) {
                metrics.AlbumLineHeight.toDp()
            }))
        }
    }
}

@Composable
internal fun PocketHorizonArtwork(
    song: Song?,
    size: Dp,
    enabled: Boolean,
    onViewArtwork: ((Song) -> Unit)?,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier,
    requestSize: Int = 1024,
    tag: String = "pocketHorizonArtwork"
) {
    val context = LocalContext.current
    val uri = song?.albumArtUri
    val requestKey = listOf(uri?.toString(), song?.dateModifiedEpochSeconds, song?.fileSizeBytes,
        song?.artworkEnrichmentVersion, requestSize)
    val model = remember(context, requestKey, requestSize) {
        uri?.let {
            ImageRequest.Builder(context).data(it).size(requestSize).crossfade(false)
                .memoryCacheKey("horizon:$requestKey").build()
        }
    }
    val action = if (song != null && uri != null && onViewArtwork != null) {
        val label = stringResource(R.string.player_view_artwork)
        Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = label) {
            if (enabled) onViewArtwork(song)
        }.semantics { contentDescription = label }
    } else Modifier
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.size(size).testTag(tag).clip(shape)
        .background(style.surface).border(1.dp, style.outline.copy(alpha = 0.65f), shape).then(action),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Album, contentDescription = null, tint = style.secondaryText,
            modifier = Modifier.size(minOf(48.dp, size * .5f)))
        // Always retain the same loader lifetime, including null requests; the shared loader retires
        // obsolete completions and retains the preceding image only during a pending replacement.
        RetainedArtworkImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize(),
            artworkIdentity = uri?.toString(), requestKey = requestKey, contentScale = ContentScale.Fit)
    }
}
