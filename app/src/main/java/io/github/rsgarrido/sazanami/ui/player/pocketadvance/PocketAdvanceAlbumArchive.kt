package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.LibraryArtworkOwnerType
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumDiscSections
import io.github.rsgarrido.sazanami.ui.library.hasRepresentativeArtist
import io.github.rsgarrido.sazanami.ui.library.rememberLibraryArtworkRequest

@Composable
internal fun pocketAdvanceAlbumTitle(album: LibraryAlbumGroup): String =
    if (album.songs.firstOrNull()?.album.isNullOrBlank()) stringResource(R.string.player_unknown_album) else album.title

@Composable
private fun pocketAdvanceAlbumArtist(album: LibraryAlbumGroup): String =
    if (album.hasRepresentativeArtist()) album.artistText else stringResource(R.string.auto_various_artists)

/** One persistent artwork owner moves inside the LCD while the appropriate list owns input. */
@Composable
internal fun PocketAdvanceAlbumArchive(
    navigation: PocketAdvanceNavigationState,
    albums: List<LibraryAlbumGroup>,
    albumKeys: List<String>,
    isLibraryReady: Boolean,
    onActivateAlbum: (String) -> Unit,
    onActivateSong: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPocketAdvancePalette.current
    val recordPage = navigation.browserPage as? PocketAdvanceBrowserPage.AlbumRecord
    val isRecord = recordPage != null
    val selectedIndex = navigation.selectedAlbumIndex(albumKeys)
    val album = if (recordPage != null) albums.firstOrNull { it.key == recordPage.albumKey }
        else selectedIndex?.let(albums::get)
    // Compose's duration scale applies automatically. Restored/cancelled-collapse records start settled.
    val opening by animateFloatAsState(if (isRecord) 1f else 0f,
        animationSpec = tween(PocketAdvanceAlbumOpenDurationMillis, easing = FastOutSlowInEasing), label = "advanceAlbumOpening")
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().testTag("pocketAdvanceAlbumArchive")) {
        val lcdHeight = maxHeight
        val scale = LocalDensity.current.fontScale
        val geometry = remember(maxWidth, maxHeight, scale) { pocketAdvanceAlbumArchiveLayout(maxWidth.value, maxHeight.value, scale) }
        val panelWidth = geometry.cataloguePanelWidth + (geometry.recordPanelWidth - geometry.cataloguePanelWidth) * opening
        val artwork = lerp(geometry.catalogueArtwork, geometry.recordArtwork, opening)
        val title = album?.let { pocketAdvanceAlbumTitle(it) } ?: stringResource(R.string.pocket_advance_album_unavailable)
        val index = if (isRecord) albums.indexOfFirst { it.key == recordPage?.albumKey } else selectedIndex ?: -1
        val position = stringResource(R.string.pocket_advance_archive_position, (index + 1).coerceAtLeast(0), albums.size)
        val metadataScroll = key(album?.key, isRecord) { rememberScrollState() }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(panelWidth.dp).fillMaxHeight()) {
                PocketAdvanceArchiveArtwork(album, title, position, !isRecord, onActivateAlbum,
                    Modifier.offset(artwork.left.dp, artwork.top.dp).size(artwork.width.dp))
                val metadataTop = artwork.bottom + 8f
                Column(Modifier.offset(8.dp, metadataTop.dp).width((panelWidth - 16f).coerceAtLeast(1f).dp)
                    .height((lcdHeight.value - metadataTop).coerceAtLeast(1f).dp).verticalScroll(metadataScroll),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isRecord) Text(title, color = colors.Ink, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("pocketAdvanceAlbumRecordTitle"))
                    else Text(position, color = colors.Ink, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
                        modifier = Modifier.testTag("pocketAdvanceAlbumIndex"))
                    album?.let { current ->
                        Text(pocketAdvanceAlbumArtist(current), color = colors.MutedInk,
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp,
                            maxLines = if (isRecord) 1 else 2, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.player_wheel_song_count, current.songs.size, current.songs.size),
                            color = colors.Ink, fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val discs = remember(current.songs) { buildLibraryAlbumDiscSections(current.songs).mapNotNull { it.discNumber }.distinct().size }
                        if (discs > 1) Text(pluralStringResource(R.plurals.pocket_advance_disc_count, discs, discs),
                            color = colors.MutedInk, fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(geometry.gap.dp))
            Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = if (isRecord) .9f + .1f * opening else 1f }) {
                if (isRecord) {
                    val tracks = album?.songs.orEmpty()
                    val keys = remember(tracks) { tracks.map { it.stableUiKey() } }
                    PocketAdvanceSongs(navigation, tracks, keys, isLibraryReady, onActivateSong, albumTitle = title)
                } else {
                    PocketAdvanceAlbumCatalogue(navigation, albums, albumKeys, isLibraryReady, onActivateAlbum)
                }
            }
        }
    }
}

@Composable
private fun PocketAdvanceArchiveArtwork(
    album: LibraryAlbumGroup?, title: String, position: String, canOpen: Boolean,
    onActivateAlbum: (String) -> Unit, modifier: Modifier
) {
    val colors = LocalPocketAdvancePalette.current
    val uri = album?.songs?.firstOrNull()?.albumArtUri?.takeIf { isPocketAdvanceArchiveArtworkSchemeLocal(it.scheme) }
    val sharedRequest = rememberLibraryArtworkRequest(LibraryArtworkOwnerType.ALBUM, album?.key.orEmpty(), uri, VisualAssetVariant.DISPLAY)
    // A fixed decode size avoids resizing/redecoding the cover on every opening-animation frame.
    val request = remember(sharedRequest) { sharedRequest?.newBuilder()?.size(512)?.crossfade(false)?.build() }
    val description = stringResource(R.string.library_album_art_for, title)
    val openLabel = stringResource(R.string.pocket_advance_open_entry, title)
    Box(modifier.testTag("pocketAdvanceAlbumArtwork:" + (album?.key ?: "unavailable"))
        .clip(RoundedCornerShape(4.dp)).background(colors.ScreenShade)
        .border(1.dp, colors.Ink.copy(alpha = .65f), RoundedCornerShape(4.dp))
        .then(if (canOpen && album != null) Modifier.clickable(role = Role.Button, onClickLabel = openLabel,
            onClick = { onActivateAlbum(album.key) }) else Modifier)
        .semantics { contentDescription = description; stateDescription = position; if (!canOpen) role = Role.Image }
        .padding(3.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Album, null, tint = colors.MutedInk, modifier = Modifier.size(28.dp))
        // A new identity disposes the old painter immediately: rapid selection never retains another cover.
        key(album?.key, uri) {
            if (request != null) AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun PocketAdvanceAlbumCatalogue(
    navigation: PocketAdvanceNavigationState, albums: List<LibraryAlbumGroup>, keys: List<String>,
    isLibraryReady: Boolean, onActivateAlbum: (String) -> Unit
) {
    if (albums.isEmpty()) {
        PocketAdvanceBrowserEmpty(stringResource(if (isLibraryReady) R.string.player_wheel_no_albums
            else R.string.pocket_advance_library_pending), Modifier.testTag("pocketAdvanceAlbumCatalogueEmpty"))
        return
    }
    val cursor = navigation.albumsCursor
    val selectedIndex = navigation.selectedAlbumIndex(keys)
    LaunchedEffect(cursor.scrollRequest) {
        if (cursor.takeScrollRequest()) selectedIndex?.let { cursor.listState.revealPocketAdvanceBrowseItem(it) }
    }
    val description = stringResource(R.string.pocket_advance_album_archive)
    LazyColumn(Modifier.fillMaxSize().testTag("pocketAdvanceAlbumCatalogue").semantics { contentDescription = description },
        state = cursor.listState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(albums, key = { _, album -> album.key }) { index, album ->
            val title = pocketAdvanceAlbumTitle(album)
            PocketAdvanceBrowserRow(title, pocketAdvanceAlbumArtist(album), index == selectedIndex,
                stringResource(R.string.pocket_advance_open_entry, title), onClick = { onActivateAlbum(album.key) },
                modifier = Modifier.testTag("pocketAdvanceAlbumRow:" + album.key))
        }
    }
}
