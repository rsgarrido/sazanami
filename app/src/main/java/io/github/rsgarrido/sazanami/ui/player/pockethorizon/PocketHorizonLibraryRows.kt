package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.ui.library.ArtistPicture
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup
import io.github.rsgarrido.sazanami.ui.library.LibraryArtworkImage
import io.github.rsgarrido.sazanami.ui.library.LibraryArtworkOwnerType
import io.github.rsgarrido.sazanami.ui.library.hasUnresolvedLibraryArtwork
import io.github.rsgarrido.sazanami.ui.library.rememberLibraryArtworkRequest

internal val PocketHorizonLibraryTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Composable
internal fun PocketHorizonSongRow(
    song: Song,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    style: PocketHorizonStyle
) {
    val title = song.title.takeIf(String::isNotBlank) ?: stringResource(R.string.library_song_unknown_title)
    val artist = song.artist.takeIf(String::isNotBlank) ?: stringResource(R.string.library_song_unknown_artist)
    val album = song.album.takeIf(String::isNotBlank) ?: stringResource(R.string.library_song_unknown_album)
    PocketHorizonLibraryRow(title, "$artist · $album", selected, enabled,
        stringResource(R.string.pocket_horizon_play_song, title), onClick,
        "pocketHorizonSong:" + song.stableUiKey(), style) {
        PocketHorizonLibraryArtwork(song, song.stableUiKey(), 56.dp, style)
    }
}

@Composable
internal fun PocketHorizonAlbumRow(album: LibraryAlbumGroup, enabled: Boolean, onClick: () -> Unit,
    style: PocketHorizonStyle) {
    val title = pocketHorizonAlbumTitle(album)
    PocketHorizonLibraryRow(title, pocketHorizonAlbumArtist(album), false, enabled,
        stringResource(R.string.pocket_horizon_open_album, title), onClick,
        "pocketHorizonAlbum:" + album.key, style) {
        PocketHorizonLibraryArtwork(album.songs.firstOrNull { it.albumArtUri != null }
            ?: album.songs.firstOrNull(), album.key, 56.dp, style)
    }
}

@Composable
internal fun PocketHorizonArtistRow(artist: LibraryArtistGroup, enabled: Boolean, onClick: () -> Unit,
    style: PocketHorizonStyle) {
    val name = pocketHorizonArtistName(artist)
    PocketHorizonLibraryRow(name, pluralStringResource(R.plurals.song_count,
        artist.songs.size, artist.songs.size), false, enabled,
        stringResource(R.string.pocket_horizon_open_artist, name), onClick,
        "pocketHorizonArtist:" + artist.key, style) {
        PocketHorizonArtistArtwork(artist, 56.dp)
    }
}

@Composable
private fun PocketHorizonLibraryRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    tag: String,
    style: PocketHorizonStyle,
    artwork: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag(tag).clip(shape)
        .background(if (selected) style.surface else style.surface.copy(alpha = .45f))
        .border(1.dp, if (selected) style.accent else style.outline.copy(alpha = .25f), shape)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = actionLabel) { if (enabled) onClick() }
        .semantics { this.selected = selected }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        artwork()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = style.text, style = PocketHorizonLibraryTextStyle, fontSize = 16.sp,
                lineHeight = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = style.secondaryText, style = PocketHorizonLibraryTextStyle, fontSize = 14.sp,
                lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun PocketHorizonLibraryBack(album: Boolean, enabled: Boolean,
    onClick: () -> Unit, style: PocketHorizonStyle) {
    val label = stringResource(if (album) R.string.pocket_horizon_back_albums else R.string.pocket_horizon_back_artists)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pocketHorizonLibraryBack")
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { if (enabled) onClick() }
        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = style.accent, modifier = Modifier.size(24.dp))
        Text(label, color = style.accent, style = PocketHorizonLibraryTextStyle, fontSize = 14.sp,
            lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PocketHorizonAlbumSummary(album: LibraryAlbumGroup, style: PocketHorizonStyle) {
    PocketHorizonDetailSummary(pocketHorizonAlbumTitle(album), pocketHorizonAlbumArtist(album), album.songs.size, style) {
        PocketHorizonLibraryArtwork(album.songs.firstOrNull { it.albumArtUri != null }
            ?: album.songs.firstOrNull(), album.key, 96.dp, style, VisualAssetVariant.DISPLAY)
    }
}

@Composable
internal fun PocketHorizonArtistSummary(artist: LibraryArtistGroup, style: PocketHorizonStyle) {
    PocketHorizonDetailSummary(pocketHorizonArtistName(artist), null, artist.songs.size, style) {
        PocketHorizonArtistArtwork(artist, 96.dp, VisualAssetVariant.DISPLAY)
    }
}

@Composable
private fun pocketHorizonAlbumTitle(album: LibraryAlbumGroup): String =
    if (album.songs.all { it.album.isBlank() } || album.title.isBlank()) stringResource(R.string.library_song_unknown_album)
    else album.title

@Composable
private fun pocketHorizonAlbumArtist(album: LibraryAlbumGroup): String =
    if (album.songs.all { it.artist.isBlank() && it.albumArtist.isBlank() }) stringResource(R.string.library_song_unknown_artist)
    else album.artistText

@Composable
private fun pocketHorizonArtistName(artist: LibraryArtistGroup): String =
    if (artist.identity.isUnknown) stringResource(R.string.library_song_unknown_artist) else artist.name

@Composable
private fun PocketHorizonDetailSummary(title: String, subtitle: String?, count: Int,
    style: PocketHorizonStyle, artwork: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        artwork()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.testTag("pocketHorizonDetailTitle"), color = style.text,
                style = PocketHorizonLibraryTextStyle, fontSize = 20.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = style.secondaryText, style = PocketHorizonLibraryTextStyle,
                    fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(pluralStringResource(R.plurals.song_count, count, count), color = style.secondaryText,
                style = PocketHorizonLibraryTextStyle, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun PocketHorizonLibraryArtwork(song: Song?, ownerKey: String, size: Dp, style: PocketHorizonStyle,
    variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL) {
    val request = rememberLibraryArtworkRequest(LibraryArtworkOwnerType.ALBUM, ownerKey, song?.albumArtUri, variant)
    LibraryArtworkImage(request, song.hasUnresolvedLibraryArtwork(), contentDescription = null,
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).clearAndSetSemantics {}) {
        Box(Modifier.fillMaxSize().background(style.surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Album, null, tint = style.secondaryText, modifier = Modifier.size(size * .42f))
        }
    }
}

@Composable
private fun PocketHorizonArtistArtwork(artist: LibraryArtistGroup, size: Dp,
    variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL) {
    val fallback = artist.songs.firstOrNull { it.albumArtUri != null } ?: artist.songs.firstOrNull()
    ArtistPicture(identity = artist.identity, fallbackModel = fallback?.albumArtUri,
        contentDescription = "", variant = variant, unresolvedFallbackArtwork = fallback.hasUnresolvedLibraryArtwork(),
        neutralWhileLoading = true,
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).clearAndSetSemantics {})
}
