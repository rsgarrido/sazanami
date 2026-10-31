package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.ArtistPictureUiEnvironment
import io.github.rsgarrido.sazanami.ui.library.LocalArtistPictureUi
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState

/** A finite-height lazy body. Category navigation and Now Playing remain owned by the parent. */
@Composable
internal fun PocketHorizonLibraryBrowser(
    library: PocketHorizonLibrary,
    libraryState: LibraryUiState,
    navigationState: PocketHorizonNavigationState,
    inputEnabled: Boolean,
    onSongClick: ((Song, List<Song>) -> Unit)?,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier
) {
    val category = navigationState.category
    if (category == PocketHorizonCategory.NowPlaying) return
    val albumKey = navigationState.albumKey.takeIf { category == PocketHorizonCategory.Albums }
    val artistKey = navigationState.artistKey.takeIf { category == PocketHorizonCategory.Artists }
    val album = albumKey?.let(library.albumByKey::get)
    val artist = artistKey?.let(library.artistByKey::get)
    val isDetail = albumKey != null || artistKey != null
    val missingDetail = isDetail && album == null && artist == null
    val context = when (category) {
        PocketHorizonCategory.Songs -> library.songs
        PocketHorizonCategory.Albums -> album?.songs.orEmpty()
        PocketHorizonCategory.Artists -> artist?.songs.orEmpty()
        PocketHorizonCategory.NowPlaying -> emptyList()
    }
    val list = when (category) {
        PocketHorizonCategory.Songs -> PocketHorizonList.Songs
        PocketHorizonCategory.Albums -> if (isDetail) PocketHorizonList.AlbumTracks else PocketHorizonList.Albums
        PocketHorizonCategory.Artists -> if (isDetail) PocketHorizonList.ArtistTracks else PocketHorizonList.Artists
        PocketHorizonCategory.NowPlaying -> PocketHorizonList.Songs
    }
    val summaryKey = when {
        album != null -> "horizon:album-summary:" + album.key
        artist != null -> "horizon:artist-summary:" + artist.key
        else -> null
    }
    val trackKeys = remember(context) { context.map(Song::stableUiKey) }
    val rowKeys = when (list) {
        PocketHorizonList.Songs -> library.songKeys
        PocketHorizonList.Albums -> library.albumKeys
        PocketHorizonList.Artists -> library.artistKeys
        PocketHorizonList.AlbumTracks, PocketHorizonList.ArtistTracks -> trackKeys
    }
    val keys = remember(rowKeys, summaryKey) {
        if (summaryKey == null) rowKeys else listOf(summaryKey) + rowKeys
    }
    val hasRows = rowKeys.isNotEmpty() && !missingDetail
    val currentLibrary by rememberUpdatedState(library)
    val currentInputEnabled by rememberUpdatedState(inputEnabled)
    val currentSongClick by rememberUpdatedState(onSongClick)
    val currentKeys by rememberUpdatedState(keys)
    val consoleInput = LocalHorizonConsoleInput.current
    val focusKey = navigationState.focusedKey(list)?.takeIf { it in rowKeys } ?: rowKeys.firstOrNull()
    val activateRow: (String) -> Unit = { key ->
        if (currentInputEnabled && navigationState.category == category &&
            navigationState.albumKey == albumKey && category == PocketHorizonCategory.Albums) {
            if (key in currentLibrary.albumByKey) navigationState.openAlbum(key)
        } else if (currentInputEnabled && navigationState.category == category &&
            navigationState.artistKey == artistKey && category == PocketHorizonCategory.Artists && !isDetail) {
            if (key in currentLibrary.artistByKey) navigationState.openArtist(key)
        }
    }
    val activateSong: (String) -> Unit = activate@ { songKey ->
        if (!currentInputEnabled || navigationState.category != category) return@activate
        val callback = currentSongClick ?: return@activate
        val freshContext = when (list) {
            PocketHorizonList.Songs -> currentLibrary.songs
            PocketHorizonList.AlbumTracks -> {
                if (navigationState.albumKey != albumKey) return@activate
                albumKey?.let { currentLibrary.albumByKey[it]?.songs }.orEmpty()
            }
            PocketHorizonList.ArtistTracks -> {
                if (navigationState.artistKey != artistKey) return@activate
                artistKey?.let { currentLibrary.artistByKey[it]?.songs }.orEmpty()
            }
            else -> return@activate
        }
        val selected = freshContext.firstOrNull { it.stableUiKey() == songKey } ?: return@activate
        // A browsing selection is not a claim that playback started. The application owns the request.
        navigationState.selectSong(songKey)
        callback(selected, freshContext)
    }
    val artistEnvironment = remember(libraryState.artistPictureAssignments, libraryState.localArtistArtwork) {
        ArtistPictureUiEnvironment(assignments = libraryState.artistPictureAssignments,
            localArtwork = libraryState.localArtistArtwork)
    }
    val inheritedColors = MaterialTheme.colorScheme
    val colors = remember(inheritedColors, style.surface, style.secondaryText) {
        inheritedColors.copy(surfaceContainerHighest = style.surface, onSurfaceVariant = style.secondaryText)
    }
    CompositionLocalProvider(LocalArtistPictureUi provides artistEnvironment) {
        MaterialTheme(colorScheme = colors) {
            Column(modifier.fillMaxSize()) {
                if (isDetail) {
                    PocketHorizonLibraryBack(albumKey != null, inputEnabled, { navigationState.back() }, style)
                }
                val status = when {
                    library.errorMessage != null -> R.string.pocket_horizon_library_error
                    library.isRefreshing -> R.string.pocket_horizon_library_refreshing
                    library.isLoading || !library.hasPublishedInitialLibraryState -> R.string.pocket_horizon_library_loading
                    else -> null
                }
                if (hasRows && status != null) {
                    PocketHorizonLibraryMessage(stringResource(status), style,
                        Modifier.testTag("pocketHorizonLibraryStatus"))
                }
                if (!hasRows) {
                    val message = status ?: when {
                        missingDetail -> R.string.pocket_horizon_library_detail_unavailable
                        isDetail -> R.string.pocket_horizon_library_no_tracks
                        category == PocketHorizonCategory.Songs -> R.string.pocket_horizon_library_no_songs
                        category == PocketHorizonCategory.Albums -> R.string.pocket_horizon_library_no_albums
                        else -> R.string.pocket_horizon_library_no_artists
                    }
                    PocketHorizonLibraryMessage(stringResource(message), style,
                        Modifier.testTag("pocketHorizonLibraryMessage"))
                } else {
                    // A summary is a lazy item so long metadata and large fonts can scroll too.
                    key(list, albumKey, artistKey) {
                        val listState = rememberPocketHorizonListState(navigationState, list, keys,
                            library.isAuthoritative, albumKey ?: artistKey)
                        val scope = rememberCoroutineScope()
                        val movement = remember(listState) { mutableStateOf<Job?>(null) }
                        fun currentContext(): Boolean = currentInputEnabled && navigationState.category == category && when (category) {
                            PocketHorizonCategory.Albums -> navigationState.albumKey == albumKey
                            PocketHorizonCategory.Artists -> navigationState.artistKey == artistKey
                            PocketHorizonCategory.Songs -> true
                            PocketHorizonCategory.NowPlaying -> false
                        }
                        val activate by rememberUpdatedState<(String) -> Unit> { key ->
                            if (currentContext() && key in currentKeys) {
                                movement.value?.cancel()
                                navigationState.focus(list, key)
                                if (list == PocketHorizonList.Albums || list == PocketHorizonList.Artists) activateRow(key)
                                else activateSong(key)
                            }
                        }
                        DisposableEffect(consoleInput, listState, rowKeys, inputEnabled, category, albumKey, artistKey, onSongClick != null) {
                            val binding = if (!inputEnabled) null else PocketHorizonConsoleInput.Binding(
                                move = { delta ->
                                    // A held stick must not move the selection faster than placement
                                    // can reveal it. Drop repeats while this one local move completes.
                                    if (currentContext() && movement.value?.isActive != true) {
                                        val index = rowKeys.indexOf(navigationState.focusedKey(list)).let { if (it < 0) 0 else it }
                                        val target = rowKeys[(index + delta).coerceIn(0, rowKeys.lastIndex)]
                                        movement.value = scope.launch {
                                            if (listState.revealPocketHorizonItem(target, { currentKeys }, ::currentContext)) {
                                                navigationState.focus(list, target)
                                            }
                                        }
                                    }
                                }, confirm = {
                                    if (currentContext() && movement.value?.isActive != true) rowKeys.firstOrNull { it == navigationState.focusedKey(list) }
                                        .let { it ?: rowKeys.firstOrNull() }?.let(activate)
                                }, canConfirm = list == PocketHorizonList.Albums || list == PocketHorizonList.Artists || onSongClick != null)
                            consoleInput?.binding = binding
                            onDispose {
                                movement.value?.cancel()
                                if (consoleInput?.binding === binding) consoleInput?.binding = null
                            }
                        }
                        LazyColumn(state = listState, userScrollEnabled = inputEnabled,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                            .testTag("pocketHorizonLibraryList"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            summaryKey?.let { summary ->
                                item(key = summary, contentType = "summary") {
                                    if (album != null) PocketHorizonAlbumSummary(album, style)
                                    else if (artist != null) PocketHorizonArtistSummary(artist, style)
                                }
                            }
                            when (list) {
                                PocketHorizonList.Songs, PocketHorizonList.AlbumTracks, PocketHorizonList.ArtistTracks -> {
                                    items(context, key = Song::stableUiKey, contentType = { "song" }) { song ->
                                        val songKey = song.stableUiKey()
                                        PocketHorizonSongRow(song, focusKey == songKey,
                                            inputEnabled && onSongClick != null, { activate(songKey) }, style)
                                    }
                                }
                                PocketHorizonList.Albums -> {
                                    items(library.albums, key = { it.key }, contentType = { "album" }) { entry ->
                                        PocketHorizonAlbumRow(entry, inputEnabled, {
                                            if (currentInputEnabled && navigationState.category == PocketHorizonCategory.Albums &&
                                                entry.key in currentLibrary.albumByKey) activate(entry.key)
                                        }, style, selected = focusKey == entry.key)
                                    }
                                }
                                PocketHorizonList.Artists -> {
                                    items(library.artists, key = { it.key }, contentType = { "artist" }) { entry ->
                                        PocketHorizonArtistRow(entry, inputEnabled, {
                                            if (currentInputEnabled && navigationState.category == PocketHorizonCategory.Artists &&
                                                entry.key in currentLibrary.artistByKey) activate(entry.key)
                                        }, style, selected = focusKey == entry.key)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketHorizonLibraryMessage(message: String, style: PocketHorizonStyle, modifier: Modifier) {
    Text(message, modifier.fillMaxWidth().padding(vertical = 8.dp), color = style.secondaryText,
        style = PocketHorizonLibraryTextStyle, fontSize = 14.sp, lineHeight = 22.sp,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

private data class PocketHorizonViewportSample(val key: String?, val index: Int, val offset: Int,
    val isScrolling: Boolean)

/** Save only stable anchors. Transient absent data must not replace a pending restore with index zero. */
@Composable
private fun rememberPocketHorizonListState(
    navigation: PocketHorizonNavigationState,
    list: PocketHorizonList,
    keys: List<String>,
    authoritative: Boolean,
    detailKey: String?
): LazyListState {
    val initial = remember(navigation, list) { navigation.anchor(list) }
    val state = remember(navigation, list) { LazyListState(initial.index, initial.offset) }
    val keySet = remember(keys) { keys.toHashSet() }
    val currentKeys by rememberUpdatedState(keys)
    val currentKeySet by rememberUpdatedState(keySet)
    val currentAuthoritative by rememberUpdatedState(authoritative)
    var restoring by remember(state) { mutableStateOf(true) }
    var appliedKeys by remember(state) { mutableStateOf<List<String>?>(null) }

    fun capture(sample: PocketHorizonViewportSample) {
        // A dataset change can remeasure the old viewport before its restore effect runs. Do not
        // let that intermediate row overwrite the stable target, especially after partial refreshes.
        if (restoring || currentKeys.isEmpty() || appliedKeys != currentKeys) return
        if (list == PocketHorizonList.AlbumTracks && navigation.albumKey != detailKey) return
        if (list == PocketHorizonList.ArtistTracks && navigation.artistKey != detailKey) return
        val savedKey = navigation.anchor(list).key
        val waitingForMissingKey = savedKey != null && savedKey !in currentKeySet
        if (waitingForMissingKey && !currentAuthoritative && !sample.isScrolling) return
        val visibleKey = sample.key?.takeIf { it in currentKeySet }
            ?: currentKeys.getOrNull(sample.index) ?: return
        navigation.updateAnchor(list, visibleKey, sample.index, sample.offset)
    }
    fun sample(): PocketHorizonViewportSample = PocketHorizonViewportSample(
        key = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.firstVisibleItemIndex }?.key as? String,
        index = state.firstVisibleItemIndex,
        offset = state.firstVisibleItemScrollOffset,
        isScrolling = state.isScrollInProgress
    )
    LaunchedEffect(state, keys, authoritative) {
        val saved = navigation.anchor(list)
        val requiresConfirmedRecovery = authoritative &&
            ((saved.key != null && saved.key !in keySet) || saved.index !in keys.indices)
        // A refresh flag alone must not cancel a user's fling or reset an unchanged viewport.
        if (appliedKeys == keys && !requiresConfirmedRecovery) return@LaunchedEffect
        restoring = true
        try {
            val anchor = navigation.resolveAnchor(list, keys, authoritative)
            if (keys.isNotEmpty()) state.scrollToItem(anchor.index, anchor.offset)
            appliedKeys = keys
        } finally {
            restoring = false
        }
    }
    LaunchedEffect(state, navigation, list) {
        snapshotFlow { sample() }.collect { capture(it) }
    }
    DisposableEffect(state, navigation, list) {
        onDispose { capture(sample()) }
    }
    return state
}
