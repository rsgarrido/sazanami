package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryArtistGroups
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsGestureRegion
import io.github.rsgarrido.sazanami.ui.player.artworkViewerClick
import io.github.rsgarrido.sazanami.ui.state.DirectSeekGestureControl
import io.github.rsgarrido.sazanami.ui.state.directSeekPositionInput
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause
import io.github.rsgarrido.sazanami.ui.state.rememberDirectSeekGestureControl
import kotlinx.coroutines.CancellationException

/** The browser changes LCD ownership only; Now Playing remains laid out for the existing morph. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PocketAdvanceExpandedPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    renderShell: Boolean = true,
    shellAlpha: Float = 1f,
    bezelAlpha: Float = 1f,
    hardwareAlpha: Float = 1f,
    displayDetailsAlpha: Float = 1f,
    inputEnabled: Boolean = true,
    collapseGestureEnabled: Boolean = true,
    morphBounds: PocketAdvancePlayerMorphBounds? = null,
    morphOwnsSharedVisuals: Boolean = false,
    onMorphDragStart: () -> Unit = {},
    onMorphDragBy: (Float) -> Unit = {},
    onMorphDragEnd: (Float) -> Unit = {},
    onMorphDragCancel: () -> Unit = {},
    lyricsGestureModifier: Modifier = Modifier,
    lyricsGestureRegion: PlayerLyricsGestureRegion? = null,
    onViewArtwork: ((Song) -> Unit)? = null,
    onOpenArtist: ((Song) -> Unit)? = null,
    onOpenAlbum: ((Song) -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null,
    navigationState: PocketAdvanceNavigationState = rememberPocketAdvanceNavigationState(),
    browserVisible: Boolean = inputEnabled && navigationState.lcdMode == PocketAdvanceLcdMode.MusicBrowser,
    songs: List<Song> = emptyList(),
    isLibraryReady: Boolean = true,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    songsRepeatAllowed: Boolean = true,
    artistSortState: LibrarySortState = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING),
    albumSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING)
) {
    val songKeys = remember(songs) { songs.map(Song::stableUiKey) }
    val artists = remember(songs, artistSortState) { sortedLibraryArtistGroups(songs, artistSortState) }
    val artistKeys = remember(artists) { artists.map { it.key } }
    val albums = remember(songs, albumSortState) { sortedLibraryAlbumGroups(songs, albumSortState) }
    val albumKeys = remember(albums) { albums.map { it.key } }
    val page = navigationState.browserPage
    val activeArtist = (page as? PocketAdvanceBrowserPage.ArtistSongs)?.let { artistPage ->
        artists.firstOrNull { it.key == artistPage.artistKey }
    }
    val activeAlbum = (page as? PocketAdvanceBrowserPage.AlbumRecord)?.let { record -> albums.firstOrNull { it.key == record.albumKey } }
    val contextSongs = when (page) {
        is PocketAdvanceBrowserPage.ArtistSongs -> activeArtist?.songs.orEmpty()
        is PocketAdvanceBrowserPage.AlbumRecord -> activeAlbum?.songs.orEmpty()
        else -> songs
    }
    val contextKeys = remember(contextSongs) { contextSongs.map(Song::stableUiKey) }
    val browseKeys = when (page) {
        PocketAdvanceBrowserPage.Artists -> artistKeys
        PocketAdvanceBrowserPage.AlbumCatalogue -> albumKeys
        else -> contextKeys
    }
    val latestContext by rememberUpdatedState(contextSongs)
    val latestContextKeys by rememberUpdatedState(contextKeys)
    val latestArtists by rememberUpdatedState(artists)
    val latestArtistKeys by rememberUpdatedState(artistKeys)
    val latestAlbums by rememberUpdatedState(albums)
    val latestAlbumKeys by rememberUpdatedState(albumKeys)
    val latestSongClick by rememberUpdatedState(onSongClick)
    val browserInputEnabled by rememberUpdatedState(inputEnabled && browserVisible)
    LaunchedEffect(navigationState, songKeys, artists, albums, isLibraryReady, page) {
        navigationState.reconcileSongs(songKeys, isLibraryReady)
        navigationState.reconcileArtists(artists, isLibraryReady)
        navigationState.reconcileAlbums(albums, isLibraryReady)
    }
    val selectedArtist = navigationState.selectedArtistIndex(artistKeys)?.let(artists::get)
    val selectedAlbum = navigationState.selectedAlbumIndex(albumKeys)?.let(albums::get)
    val selectedSong = when (page) {
        PocketAdvanceBrowserPage.Songs, is PocketAdvanceBrowserPage.ArtistSongs, is PocketAdvanceBrowserPage.AlbumRecord ->
            navigationState.selectedBrowseIndex(contextKeys)?.let(contextSongs::get)
        else -> null
    }
    val activeArtistName = activeArtist?.let { pocketAdvanceArtistName(it) }
    val activeAlbumTitle = activeAlbum?.let { pocketAdvanceAlbumTitle(it) }
    val browserConfirmLabel = pocketAdvanceConfirmLabel(navigationState, selectedSong,
        selectedArtist?.let { pocketAdvanceArtistName(it) }, selectedAlbum?.let { pocketAdvanceAlbumTitle(it) })
    val browserBackLabel = stringResource(when (page) {
        is PocketAdvanceBrowserPage.ArtistSongs -> R.string.pocket_advance_back_artists
        is PocketAdvanceBrowserPage.AlbumRecord -> R.string.pocket_advance_back_archive
        PocketAdvanceBrowserPage.AlbumCatalogue -> R.string.pocket_advance_back_music
        PocketAdvanceBrowserPage.Songs, PocketAdvanceBrowserPage.Artists -> R.string.pocket_advance_back_music
        PocketAdvanceBrowserPage.Root -> R.string.pocket_advance_return_now_playing
    })
    val browserCanConfirm = navigationState.canConfirm(browseKeys)
    val activateArtist: (String) -> Unit = { key ->
        if (browserInputEnabled && navigationState.lcdMode == PocketAdvanceLcdMode.MusicBrowser &&
            navigationState.browserPage == PocketAdvanceBrowserPage.Artists) {
            val index = latestArtistKeys.indexOf(key)
            if (index >= 0) navigationState.openArtist(latestArtists[index].key, index)
        }
    }
    val activateAlbum: (String) -> Unit = { key ->
        if (browserInputEnabled && navigationState.lcdMode == PocketAdvanceLcdMode.MusicBrowser &&
            navigationState.browserPage == PocketAdvanceBrowserPage.AlbumCatalogue) {
            val index = latestAlbumKeys.indexOf(key)
            if (index >= 0) navigationState.openAlbum(latestAlbums[index].key, index)
        }
    }
    val activateSong: (String) -> Unit = { key ->
        if (browserInputEnabled && navigationState.lcdMode == PocketAdvanceLcdMode.MusicBrowser &&
            navigationState.browserPage == page &&
            (page == PocketAdvanceBrowserPage.Songs || page is PocketAdvanceBrowserPage.ArtistSongs || page is PocketAdvanceBrowserPage.AlbumRecord)) {
            val context = latestContext
            val index = latestContextKeys.indexOf(key)
            if (index >= 0) {
                when (page) {
                    is PocketAdvanceBrowserPage.ArtistSongs -> navigationState.selectArtistSong(key, index)
                    is PocketAdvanceBrowserPage.AlbumRecord -> navigationState.selectAlbumTrack(key, index)
                    else -> navigationState.selectSong(key, index)
                }
                latestSongClick(context[index], context)
                navigationState.showNowPlaying()
            }
        }
    }
    val confirmBrowser: () -> Unit = {
        when (navigationState.browserPage) {
            PocketAdvanceBrowserPage.Root -> navigationState.confirmSelection()
            PocketAdvanceBrowserPage.Artists -> navigationState.selectedArtistIndex(latestArtistKeys)?.let { index ->
                activateArtist(latestArtistKeys[index])
            }
            PocketAdvanceBrowserPage.AlbumCatalogue -> navigationState.selectedAlbumIndex(latestAlbumKeys)?.let { index ->
                activateAlbum(latestAlbumKeys[index])
            }
            PocketAdvanceBrowserPage.Songs, is PocketAdvanceBrowserPage.ArtistSongs, is PocketAdvanceBrowserPage.AlbumRecord ->
                navigationState.selectedBrowseIndex(latestContextKeys)?.let { index -> activateSong(latestContextKeys[index]) }
        }
    }
    val fontScale = LocalDensity.current.fontScale
    val collapseLabel = stringResource(R.string.player_collapse)
    val displayLyricsModifier = if (browserVisible) Modifier else lyricsGestureModifier
    val lyricsEnabled by rememberUpdatedState(inputEnabled && !browserVisible && onOpenLyrics != null)
    val openLyrics by rememberUpdatedState(onOpenLyrics)
    val collapseDrag = Modifier.pocketAdvanceCollapseGesture(
        collapseGestureEnabled, morphBounds, onMorphDragStart, onMorphDragBy, onMorphDragEnd, onMorphDragCancel,
        lyricsEnabled = { lyricsEnabled }, onOpenLyrics = { openLyrics?.invoke() }
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
            .padding(PocketAdvanceLayout.OuterInset),
        contentAlignment = Alignment.Center
    ) {
        // Use the stable safe viewport, not visibility-dependent insets: hiding bars cannot
        // move these destinations halfway through a morph. The finish extends behind the bars.
        val shellWidth = maxWidth
        val shellHeight = PocketAdvanceLayout.shellHeight(maxHeight, shellWidth, fontScale)
        Box(
            modifier = Modifier
                .width(shellWidth)
                .height(shellHeight)
                .onGloballyPositioned { morphBounds?.updateExpandedSurface(it.boundsInRoot()) }
                .testTag("pocketAdvanceExpanded")
        ) {
            if (renderShell) {
                PocketAdvanceShell(Modifier.matchParentSize().graphicsLayer { alpha = shellAlpha })
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(PocketAdvanceLayout.ShellInset),
                verticalArrangement = Arrangement.spacedBy(PocketAdvanceLayout.BrandGap)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PocketAdvanceLayout.BrandHeight * fontScale.coerceAtLeast(1f))
                        .graphicsLayer { alpha = hardwareAlpha }
                        .then(collapseDrag)
                        .then(displayLyricsModifier)
                        .testTag("pocketAdvanceHeader")
                        .then(if (inputEnabled) Modifier.semantics {
                            customActions = listOf(CustomAccessibilityAction(collapseLabel) { onCollapseClick(); true })
                        } else Modifier.clearAndSetSemantics { })
                        .padding(start = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(if (isPlaying) PocketAdvanceColors.Led else PocketAdvanceColors.ShellShade, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pocket_advance_power), color = PocketAdvanceColors.Label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(PocketAdvanceLayout.ScreenGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketAdvanceDirectionWing(
                        isShuffleEnabled, repeatMode, inputEnabled, onPreviousClick, onNextClick,
                        onShuffleClick, onRepeatClick, navigationState, browserVisible,
                        browseKeys, songsRepeatAllowed, confirmBrowser, browserCanConfirm, browserConfirmLabel, browserBackLabel,
                        Modifier.width(PocketAdvanceLayout.ControlWing).fillMaxHeight()
                            .graphicsLayer { alpha = hardwareAlpha }
                    )
                    PocketAdvanceDisplay(
                        currentSong, isPlaying, currentPosition, duration, onSeekChange, inputEnabled,
                        morphOwnsSharedVisuals, morphBounds, bezelAlpha, displayDetailsAlpha,
                        Modifier.weight(1f).fillMaxHeight(), collapseDrag, displayLyricsModifier,
                        lyricsGestureRegion, onViewArtwork, onOpenArtist, onOpenAlbum, navigationState, browserVisible,
                        contextSongs, contextKeys, artists, artistKeys, activeArtistName, albums, albumKeys, activeAlbumTitle,
                        isLibraryReady, activateSong, activateArtist, activateAlbum
                    )
                    PocketAdvanceActionWing(
                        isPlaying, inputEnabled, onPlayPauseClick,
                        onOpenUpNextClick, onMoreClick, morphOwnsSharedVisuals, morphBounds, navigationState, browserVisible,
                        confirmBrowser, browserCanConfirm, browserConfirmLabel, browserBackLabel,
                        Modifier.width(PocketAdvanceLayout.ControlWing).fillMaxHeight()
                            .graphicsLayer { alpha = hardwareAlpha }
                    )
                }
            }
        }
    }
}

@Composable
private fun PocketAdvanceDisplay(
    currentSong: Song?, isPlaying: Boolean, currentPosition: Int, duration: Int,
    onSeekChange: (Int) -> Unit, inputEnabled: Boolean,
    morphOwnsSharedVisuals: Boolean, morphBounds: PocketAdvancePlayerMorphBounds?,
    bezelAlpha: Float, detailsAlpha: Float, modifier: Modifier,
    collapseDrag: Modifier, lyricsGestureModifier: Modifier, lyricsGestureRegion: PlayerLyricsGestureRegion?,
    onViewArtwork: ((Song) -> Unit)?, onOpenArtist: ((Song) -> Unit)?, onOpenAlbum: ((Song) -> Unit)?,
    navigation: PocketAdvanceNavigationState, browserVisible: Boolean,
    songs: List<Song>, songKeys: List<String>, artists: List<LibraryArtistGroup>, artistKeys: List<String>,
    artistName: String?, albums: List<LibraryAlbumGroup>, albumKeys: List<String>, albumTitle: String?,
    isLibraryReady: Boolean, onActivateSong: (String) -> Unit, onActivateArtist: (String) -> Unit, onActivateAlbum: (String) -> Unit
) {
    val nowPlayingInputEnabled = inputEnabled && !browserVisible
    val sharedModifier = Modifier.graphicsLayer { alpha = if (morphOwnsSharedVisuals) 0f else 1f }
        .then(if (morphOwnsSharedVisuals) Modifier.clearAndSetSemantics { } else Modifier)
    val backgroundAlpha = if (morphOwnsSharedVisuals) 0f else bezelAlpha
    Column(
        modifier = modifier
            .onGloballyPositioned { morphBounds?.updateExpandedDisplay(it.boundsInRoot()) }
            .testTag("pocketAdvanceDisplay")
            .background(PocketAdvanceColors.Bezel.copy(alpha = backgroundAlpha), RoundedCornerShape(20.dp))
            .border(1.dp, PocketAdvanceColors.ShellShade.copy(alpha = backgroundAlpha), RoundedCornerShape(20.dp))
            .padding(8.dp)
            .background(PocketAdvanceColors.Screen.copy(alpha = backgroundAlpha), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(PocketAdvanceLayout.StatusHeight * LocalDensity.current.fontScale.coerceAtLeast(1f))
                .graphicsLayer { alpha = detailsAlpha }
                .testTag("pocketAdvanceLyricsGesture")
                .onGloballyPositioned { lyricsGestureRegion?.updateTop(it.boundsInRoot()) }
                .then(collapseDrag).then(lyricsGestureModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(PocketAdvanceColors.ScreenAccent, CircleShape))
            Spacer(Modifier.width(7.dp))
            val header = if (browserVisible) when (navigation.browserPage) {
                PocketAdvanceBrowserPage.Root -> stringResource(R.string.player_wheel_music)
                PocketAdvanceBrowserPage.Songs -> stringResource(R.string.player_wheel_songs)
                PocketAdvanceBrowserPage.Artists -> stringResource(R.string.player_wheel_artists)
                is PocketAdvanceBrowserPage.ArtistSongs -> artistName ?: stringResource(R.string.pocket_advance_artist_unavailable)
                PocketAdvanceBrowserPage.AlbumCatalogue -> stringResource(R.string.pocket_advance_album_archive)
                is PocketAdvanceBrowserPage.AlbumRecord -> albumTitle ?: stringResource(R.string.pocket_advance_album_unavailable)
            } else stringResource(if (playbackPlayPauseShowsPause(isPlaying)) R.string.pocket_advance_now_playing else R.string.pocket_advance_paused)
            Text(header, color = PocketAdvanceColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(PocketAdvanceLayout.StatusGap))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Same constraints and anchors in both modes. Only pixels/input/semantics yield.
            Column(Modifier.fillMaxSize()
                .graphicsLayer { alpha = if (browserVisible) 0f else 1f }
                .then(if (browserVisible) Modifier.clearAndSetSemantics { } else Modifier)) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f).testTag("pocketAdvanceDisplayDrag")
                        .then(if (browserVisible) Modifier else collapseDrag.then(lyricsGestureModifier))
                ) {
                    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val artSize = minOf(maxHeight, (maxWidth - PocketAdvanceLayout.MetadataWidth * fontScale - PocketAdvanceLayout.ScreenGap)
                        .coerceAtLeast(PocketAdvanceLayout.Artwork), 288.dp)
                    val metadataHeight = minOf(maxHeight, 192.dp * fontScale)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PocketAdvanceLayout.ScreenGap),
                        verticalAlignment = Alignment.CenterVertically) {
                        // Interactive endpoint wrappers remain in place underneath the passive shared
                        // artwork/text owner. Their hit path includes the screen's directional gesture.
                        Box(Modifier.size(artSize)
                            .then(if (nowPlayingInputEnabled) Modifier.artworkViewerClick(currentSong, onViewArtwork) else Modifier)) {
                            PocketAdvanceArtwork(currentSong, Modifier.fillMaxSize()
                                .onGloballyPositioned { morphBounds?.updateExpandedArtwork(it.boundsInRoot()) }
                                .then(sharedModifier))
                        }
                        Column(Modifier.weight(1f).height(metadataHeight), verticalArrangement = Arrangement.Center) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                                PocketAdvanceTitle(currentSong, 1f, Modifier.fillMaxWidth().clipToBounds()
                                    .onGloballyPositioned { morphBounds?.updateExpandedTitle(it.boundsInRoot()) }.then(sharedModifier))
                            }
                            Box(Modifier.fillMaxWidth().height(maxOf(48.dp, 24.dp * fontScale)).testTag("pocketAdvanceArtist")
                                .then(if (browserVisible) Modifier else Modifier.clickable(enabled = nowPlayingInputEnabled && currentSong != null && onOpenArtist != null,
                                    role = Role.Button, onClickLabel = stringResource(R.string.library_search_go_artist),
                                    onClick = { currentSong?.let { onOpenArtist?.invoke(it) } }))
                                .semantics { contentDescription = currentSong?.artist.orEmpty() }, contentAlignment = Alignment.CenterStart) {
                                PocketAdvanceArtist(currentSong, 1f, Modifier.fillMaxWidth()
                                    .onGloballyPositioned { morphBounds?.updateExpandedArtist(it.boundsInRoot()) }.then(sharedModifier))
                            }
                            Box(Modifier.fillMaxWidth().height(maxOf(48.dp, 32.dp * fontScale)).testTag("pocketAdvanceAlbum")
                                .then(if (browserVisible) Modifier else Modifier.clickable(enabled = nowPlayingInputEnabled && currentSong != null && onOpenAlbum != null,
                                    role = Role.Button, onClickLabel = stringResource(R.string.player_go_to_album),
                                    onClick = { currentSong?.let { onOpenAlbum?.invoke(it) } }))
                                .graphicsLayer { alpha = detailsAlpha }, contentAlignment = Alignment.TopStart) {
                                Text(currentSong?.album.orEmpty(), color = PocketAdvanceColors.MutedInk,
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(PocketAdvanceLayout.SeekGap))
                PocketAdvanceSeekBar(currentPosition, duration, onSeekChange, nowPlayingInputEnabled, morphOwnsSharedVisuals, morphBounds,
                    Modifier.fillMaxWidth().height(PocketAdvanceLayout.SeekHeight * LocalDensity.current.fontScale.coerceAtLeast(1f))
                        .graphicsLayer { alpha = detailsAlpha })
            }
            if (browserVisible) {
                when (navigation.browserPage) {
                    PocketAdvanceBrowserPage.Root -> PocketAdvanceBrowserRoot(navigation, Modifier.fillMaxSize())
                    PocketAdvanceBrowserPage.Songs -> PocketAdvanceSongs(
                        navigation, songs, songKeys, isLibraryReady, onActivateSong, Modifier.fillMaxSize()
                    )
                    PocketAdvanceBrowserPage.Artists -> PocketAdvanceArtists(
                        navigation, artists, artistKeys, isLibraryReady, onActivateArtist, Modifier.fillMaxSize()
                    )
                    is PocketAdvanceBrowserPage.ArtistSongs -> PocketAdvanceSongs(
                        navigation, songs, songKeys, isLibraryReady, onActivateSong, Modifier.fillMaxSize(), artistName
                    )
                    PocketAdvanceBrowserPage.AlbumCatalogue, is PocketAdvanceBrowserPage.AlbumRecord -> PocketAdvanceAlbumArchive(
                        navigation, albums, albumKeys, isLibraryReady, onActivateAlbum, onActivateSong, Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private val PocketAdvanceTrackModeBottom = HorizontalAlignmentLine { old, new -> maxOf(old, new) }

@Composable
private fun PocketAdvanceDirectionWing(
    isShuffleEnabled: Boolean, repeatMode: RepeatMode, inputEnabled: Boolean,
    onPreviousClick: () -> Unit, onNextClick: () -> Unit, onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit, navigation: PocketAdvanceNavigationState, browserVisible: Boolean,
    songKeys: List<String>, songsRepeatAllowed: Boolean, onConfirm: () -> Unit, canConfirm: Boolean, confirmLabel: String, backLabel: String,
    modifier: Modifier
) {
    val repeatLabel = stringResource(when (repeatMode) {
        RepeatMode.OFF -> R.string.player_repeat_off
        RepeatMode.ALL -> R.string.player_repeat_all
        RepeatMode.ONE -> R.string.player_repeat_one
    })
    val repeatPage = navigation.browserPage
    val songsHoldEnabled = inputEnabled && browserVisible && songsRepeatAllowed && songKeys.isNotEmpty() &&
        navigation.lcdMode == PocketAdvanceLcdMode.MusicBrowser && repeatPage != PocketAdvanceBrowserPage.Root
    val latestSongKeys by rememberUpdatedState(songKeys)
    val repeatPressGate = remember { PocketAdvanceSongsDpadPressGate() }
    Layout(modifier = modifier, content = {
        // Keep the original full-size centered column and its measurement constraints unchanged.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(144.dp).testTag("pocketAdvanceDpad")) {
                Box(Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(PocketAdvanceColors.ShellShade, PocketAdvanceColors.ShellHighlight)), CircleShape)
                    .padding(3.dp).background(PocketAdvanceColors.Shell.copy(alpha = .7f), CircleShape))
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val unit = size.width / 3f
                    val path = Path().apply {
                        moveTo(unit, 0f); lineTo(unit * 2, 0f); lineTo(unit * 2, unit)
                        lineTo(size.width, unit); lineTo(size.width, unit * 2)
                        lineTo(unit * 2, unit * 2); lineTo(unit * 2, size.height)
                        lineTo(unit, size.height); lineTo(unit, unit * 2)
                        lineTo(0f, unit * 2); lineTo(0f, unit); lineTo(unit, unit); close()
                    }
                    val shadow = Path().apply { addPath(path); translate(Offset(0f, 4.dp.toPx())) }
                    drawPath(shadow, PocketAdvanceColors.ShellShade)
                    drawPath(path, Brush.verticalGradient(listOf(PocketAdvanceColors.ButtonHighlight, PocketAdvanceColors.Button)))
                    drawPath(path, PocketAdvanceColors.ButtonInk.copy(alpha = .22f), style = Stroke(1.dp.toPx()))
                    drawCircle(PocketAdvanceColors.Button, radius = unit * .30f)
                    drawCircle(PocketAdvanceColors.ButtonHighlight, radius = unit * .30f, style = Stroke(1.dp.toPx()))
                }
                PocketAdvanceDirectionButton(if (browserVisible) Icons.Filled.KeyboardArrowUp else Icons.Filled.Shuffle,
                    if (browserVisible) stringResource(R.string.pocket_advance_previous_selection)
                    else stringResource(if (isShuffleEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off),
                    if (browserVisible) { { navigation.moveSelectionUp(songKeys) } } else onShuffleClick,
                    inputEnabled, !browserVisible && isShuffleEnabled,
                    Modifier.align(Alignment.TopCenter).testTag("pocketAdvanceUp"),
                    repeatPressGate, if (songsHoldEnabled) ({ navigation.repeatBrowseSelection(-1, latestSongKeys, repeatPage) }) else null, repeatPage)
                PocketAdvanceDirectionButton(if (browserVisible) Icons.Filled.KeyboardArrowLeft else Icons.Filled.SkipPrevious,
                    if (browserVisible) backLabel else stringResource(R.string.player_previous_song),
                    if (browserVisible) navigation::back else onPreviousClick, inputEnabled, false,
                    Modifier.align(Alignment.CenterStart).testTag("pocketAdvancePrevious"))
                PocketAdvanceDirectionButton(if (browserVisible) Icons.Filled.KeyboardArrowRight else Icons.Filled.SkipNext,
                    if (browserVisible) confirmLabel else stringResource(R.string.player_next_song),
                    if (browserVisible) onConfirm else onNextClick,
                    inputEnabled && (!browserVisible || canConfirm), false,
                    Modifier.align(Alignment.CenterEnd).testTag("pocketAdvanceNext"))
                PocketAdvanceDirectionButton(if (browserVisible) Icons.Filled.KeyboardArrowDown
                    else if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    if (browserVisible) stringResource(R.string.pocket_advance_next_selection) else repeatLabel,
                    if (browserVisible) { { navigation.moveSelectionDown(songKeys) } } else onRepeatClick,
                    inputEnabled, !browserVisible && repeatMode != RepeatMode.OFF,
                    Modifier.align(Alignment.BottomCenter).testTag("pocketAdvanceDown"),
                    repeatPressGate, if (songsHoldEnabled) ({ navigation.repeatBrowseSelection(1, latestSongKeys, repeatPage) }) else null, repeatPage)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.pocket_advance_track_mode), color = PocketAdvanceColors.Label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.testTag("pocketAdvanceTrackMode").layout { measurable, constraints ->
                    val label = measurable.measure(constraints)
                    layout(label.width, label.height, mapOf(PocketAdvanceTrackModeBottom to label.height)) {
                        label.placeRelative(0, 0)
                    }
                })
        }
        PocketAdvanceStartButton(
            label = stringResource(if (browserVisible) R.string.pocket_advance_return_now_playing else R.string.pocket_advance_open_browser),
            enabled = inputEnabled, onClick = navigation::toggleMode, modifier = Modifier
        )
    }) { measurables, constraints ->
        // Box used these same loose child constraints. The column still fills the whole wing.
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val controls = measurables[0].measure(childConstraints)
        val start = measurables[1].measure(childConstraints)
        // The face sits about 12dp below the actual label: 1dp target gap + 11dp inset.
        // Clamp only START at the minimum budget; neither the column nor the shell can move.
        val startTop = minOf(controls[PocketAdvanceTrackModeBottom] + 1.dp.roundToPx(),
            controls.height - start.height)
        layout(controls.width, controls.height) {
            controls.placeRelative(0, 0)
            start.placeRelative((controls.width - start.width) / 2, startTop)
        }
    }
}

/** A small physical face inside a separate 48dp target; it never participates in morph geometry. */
@Composable
private fun PocketAdvanceStartButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier.size(width = 64.dp, height = 48.dp).testTag("pocketAdvanceStart")
        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 52.dp, height = 26.dp).testTag("pocketAdvanceStartFace")
            .graphicsLayer { translationY = if (pressed) 1.dp.toPx() else 0f }
            .shadow(if (pressed) 1.dp else 3.dp, RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(if (pressed)
                listOf(PocketAdvanceColors.Button, PocketAdvanceColors.ButtonHighlight)
                else listOf(PocketAdvanceColors.ButtonHighlight, PocketAdvanceColors.Button)), RoundedCornerShape(13.dp))
            .border(1.dp, PocketAdvanceColors.ShellHighlight, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.pocket_advance_start), color = PocketAdvanceColors.ButtonInk,
                // Physical engraving keeps its size and legibility when surrounding UI text scales.
                fontSize = with(LocalDensity.current) { 9.dp.toSp() },
                lineHeight = with(LocalDensity.current) { 11.dp.toSp() }, maxLines = 1,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("pocketAdvanceStartLabel").clearAndSetSemantics { })
        }
    }
}

@Composable
private fun PocketAdvanceDirectionButton(
    icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean,
    active: Boolean, modifier: Modifier, repeatPressGate: PocketAdvanceSongsDpadPressGate? = null,
    onHoldRepeat: (() -> Boolean)? = null, repeatPage: PocketAdvanceBrowserPage? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val latestClick by rememberUpdatedState(onClick)
    val latestRepeat by rememberUpdatedState(onHoldRepeat)
    val holdInput = if (enabled && onHoldRepeat != null && repeatPressGate != null && repeatPage != null) {
        Modifier.pocketAdvanceSongsDpadInput(interaction, repeatPressGate, repeatPage,
            onInitialPress = { latestClick() }, onRepeat = { latestRepeat?.invoke() == true })
    } else Modifier
    Box(modifier.size(48.dp).then(holdInput).clickable(interactionSource = interaction, indication = null,
        enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (pressed) Box(Modifier.size(32.dp).background(PocketAdvanceColors.Button, RoundedCornerShape(4.dp))
            .border(1.dp, PocketAdvanceColors.ShellShade, RoundedCornerShape(4.dp)))
        Icon(icon, null, tint = if (active) PocketAdvanceColors.Led else PocketAdvanceColors.ButtonInk,
            modifier = Modifier.size(21.dp).graphicsLayer { translationY = if (pressed) 2.dp.toPx() else 0f })
    }
}

@Composable
private fun PocketAdvanceActionWing(
    isPlaying: Boolean, inputEnabled: Boolean, onPlayPauseClick: () -> Unit,
    onQueueClick: () -> Unit, onMoreClick: () -> Unit,
    morphOwnsSharedVisuals: Boolean, bounds: PocketAdvancePlayerMorphBounds?,
    navigation: PocketAdvanceNavigationState, browserVisible: Boolean,
    onConfirm: () -> Unit, canConfirm: Boolean, confirmLabel: String, browserBackLabel: String, modifier: Modifier
) {
    val playLabel = stringResource(if (playbackPlayPauseShowsPause(isPlaying)) R.string.player_pause else R.string.player_play)
    val primaryLabel = if (browserVisible) confirmLabel else playLabel
    val primaryClick = if (browserVisible) onConfirm else onPlayPauseClick
    val morphOwnsActionVisual = morphOwnsSharedVisuals && !browserVisible
    val primaryEnabled = inputEnabled && !morphOwnsActionVisual &&
        (!browserVisible || canConfirm)
    val backLabel = if (browserVisible) browserBackLabel else stringResource(R.string.pocket_advance_open_browser)
    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val backInteraction = remember { MutableInteractionSource() }
    val backPressed by backInteraction.collectIsPressedAsState()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().height(144.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                // The capsule ends share the centers of the two 72dp touch regions below.
                val diameter = 72.dp.toPx()
                val housingWidth = (size.width - diameter) * kotlin.math.sqrt(2f) + diameter
                val topLeft = Offset(center.x - housingWidth / 2f, center.y - diameter / 2f)
                val rim = 3.dp.toPx()
                rotate(-45f) {
                    drawRoundRect(Brush.verticalGradient(listOf(PocketAdvanceColors.ShellShade, PocketAdvanceColors.ShellHighlight),
                        startY = topLeft.y, endY = topLeft.y + diameter),
                        topLeft, Size(housingWidth, diameter), CornerRadius(diameter / 2f))
                    drawRoundRect(PocketAdvanceColors.Shell, topLeft + Offset(rim, rim),
                        Size(housingWidth - rim * 2f, diameter - rim * 2f), CornerRadius(diameter / 2f - rim))
                }
            }
            Box(Modifier.align(Alignment.TopEnd).size(72.dp)) {
                Box(
                    Modifier.fillMaxSize().testTag("pocketAdvancePlay")
                        .clickable(interactionSource = playInteraction, indication = null,
                            enabled = primaryEnabled, role = Role.Button,
                            onClickLabel = primaryLabel, onClick = primaryClick)
                        .then(if (morphOwnsActionVisual) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = primaryLabel }),
                    contentAlignment = Alignment.Center
                ) {
                    PocketAdvancePlayVisual(isPlaying, Modifier.size(56.dp)
                        .onGloballyPositioned { bounds?.updateExpandedPlayPause(it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (morphOwnsActionVisual) 0f else 1f },
                        pressed = playPressed, confirm = browserVisible)
                }
            }
            Box(Modifier.align(Alignment.BottomStart).size(72.dp)) {
                Box(Modifier.fillMaxSize().testTag("pocketAdvanceBack")
                    .clickable(interactionSource = backInteraction, indication = null,
                        enabled = inputEnabled, role = Role.Button, onClickLabel = backLabel, onClick = navigation::back)
                    .semantics { contentDescription = backLabel }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(56.dp).shadow(if (backPressed) 1.dp else 3.dp, CircleShape)
                        .background(Brush.verticalGradient(if (backPressed) listOf(PocketAdvanceColors.Button, PocketAdvanceColors.ButtonHighlight)
                            else listOf(PocketAdvanceColors.ButtonHighlight, PocketAdvanceColors.Button)), CircleShape)
                        .border(1.dp, PocketAdvanceColors.ButtonHighlight, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (browserVisible) Icons.Filled.ArrowBack else Icons.Filled.Menu, null, tint = PocketAdvanceColors.ButtonInk)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketAdvanceUtilityButton(Icons.Filled.QueueMusic, stringResource(R.string.player_open_queues), onQueueClick, inputEnabled)
            PocketAdvanceUtilityButton(Icons.Filled.MoreHoriz, stringResource(R.string.player_more_actions), onMoreClick, inputEnabled)
        }
        Spacer(Modifier.height(12.dp))
        PocketAdvanceSpeaker(Modifier.size(width = 58.dp, height = 28.dp).graphicsLayer { rotationZ = -18f })
    }
}

@Composable
private fun PocketAdvanceUtilityButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(Modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 24.dp).background(PocketAdvanceColors.ShellShade, RoundedCornerShape(12.dp))
            .border(1.dp, PocketAdvanceColors.ShellHighlight.copy(alpha = .5f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PocketAdvanceColors.Label, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PocketAdvanceSeekBar(
    currentPosition: Int, duration: Int, onSeekChange: (Int) -> Unit,
    inputEnabled: Boolean, morphOwnsSharedVisuals: Boolean, bounds: PocketAdvancePlayerMorphBounds?, modifier: Modifier
) {
    val seek = rememberDirectSeekGestureControl(onSeekChange)
    val safeDuration = duration.coerceAtLeast(1)
    val safePosition = currentPosition.coerceIn(0, safeDuration)
    val seekLabel = stringResource(R.string.player_track_position)
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(48.dp).testTag("pocketAdvanceSeek")
                .then(if (inputEnabled) Modifier.semantics {
                    contentDescription = seekLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(safePosition.toFloat(), 0f..safeDuration.toFloat())
                    setProgress { seek.immediate(it.coerceIn(0f, safeDuration.toFloat()).toInt()); true }
                }.directSeekPositionInput(seek, safeDuration)
                    .forwardPocketAdvanceSeekCancellation(seek) else Modifier.clearAndSetSemantics { }),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)
                .onGloballyPositioned { bounds?.updateExpandedProgress(it.boundsInRoot()) }
                .graphicsLayer { alpha = if (morphOwnsSharedVisuals) 0f else 1f }) {
                drawPocketAdvanceProgress(safePosition.toFloat() / safeDuration, 1f)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatPocketAdvanceTime(safePosition), color = PocketAdvanceColors.MutedInk, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(formatPocketAdvanceTime(duration), color = PocketAdvanceColors.MutedInk, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Compose sends ACTION_CANCEL as a consumed synthetic release, not a coroutine exception.
 * Keep this observer inside the existing position-input modifiers: cancellation reaches child
 * nodes first, so the shared transaction is cancelled before its outer Final-pass completion.
 * This only forwards cancellation; all sampling, authority and commit rules stay in the adapter.
 */
private fun Modifier.forwardPocketAdvanceSeekCancellation(control: DirectSeekGestureControl): Modifier =
    pointerInput(control) {
        awaitEachGesture {
            val pointerId = awaitFirstDown(requireUnconsumed = false).id
            do {
                val pointer = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointerId }
                if (pointer == null || (!pointer.pressed && pointer.isConsumed)) control.cancel()
            } while (pointer?.pressed == true)
        }
    }

/** Safe screen/header regions arbitrate vertical intent; seeking and device buttons are excluded.
 * Lyrics commits on release into the shared overlay, so its input fence cannot cancel this gesture. */
private fun Modifier.pocketAdvanceCollapseGesture(
    enabled: Boolean, measurementIdentity: Any?, onStart: () -> Unit, onDelta: (Float) -> Unit,
    onEnd: (Float) -> Unit, onCancel: () -> Unit,
    lyricsEnabled: () -> Boolean, onOpenLyrics: () -> Unit
): Modifier = if (!enabled) this else pointerInput(enabled, measurementIdentity) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var started = false
        var openingLyrics = false
        var lyricsTravel = 0f
        var horizontalIntent = false
        try {
            val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                val travel = change.position - down.position
                horizontalIntent = horizontalIntent || kotlin.math.abs(travel.x) >= kotlin.math.abs(travel.y)
                if (!horizontalIntent && overSlop > 0f) {
                    started = true; change.consume(); onStart(); onDelta(overSlop)
                    tracker.addPosition(change.uptimeMillis, change.position)
                } else if (!horizontalIntent && overSlop < 0f && lyricsEnabled()) {
                    started = true; openingLyrics = true; lyricsTravel = overSlop
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                }
            }
            if (started && slop != null) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
                        if (!openingLyrics) onCancel()
                        break
                    }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        val velocity = tracker.calculateVelocity().y
                        if (openingLyrics) {
                            if (shouldOpenPocketAdvanceLyrics(lyricsTravel, velocity, density)) onOpenLyrics()
                        } else onEnd(velocity)
                        break
                    }
                    if (openingLyrics) lyricsTravel += change.positionChange().y
                    else onDelta(change.positionChange().y)
                    change.consume()
                }
            } else if (started && !openingLyrics) onCancel()
        } catch (cancelled: CancellationException) {
            if (started && !openingLyrics) onCancel()
            throw cancelled
        }
    }
}

internal fun shouldOpenPocketAdvanceLyrics(travelY: Float, velocityY: Float, density: Float): Boolean =
    density.isFinite() && density > 0f && travelY.isFinite() && velocityY.isFinite() &&
        (travelY <= -64f * density || (travelY <= -24f * density &&
            velocityY <= io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState.OPEN_VELOCITY_PX_PER_SECOND))
