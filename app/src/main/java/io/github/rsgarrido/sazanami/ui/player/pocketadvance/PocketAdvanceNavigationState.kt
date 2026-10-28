package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity

enum class PocketAdvanceLcdMode { NowPlaying, MusicBrowser }

sealed interface PocketAdvanceBrowserPage {
    data object Root : PocketAdvanceBrowserPage
    data object Songs : PocketAdvanceBrowserPage
    data object Artists : PocketAdvanceBrowserPage
    data class ArtistSongs(val artistKey: String) : PocketAdvanceBrowserPage
    data object AlbumCatalogue : PocketAdvanceBrowserPage
    data class AlbumRecord(val albumKey: String) : PocketAdvanceBrowserPage
}

/** The four established Music Browser destinations, all available. */
enum class PocketAdvanceRootEntry {
    NowPlaying, Songs, Artists, Albums;

    val isAvailable: Boolean get() = true
}

/** Browser location outlives LCD toggles and expansion. It never owns playback or morph geometry. */
@Stable
class PocketAdvanceNavigationState(
    initialRootEntry: PocketAdvanceRootEntry = PocketAdvanceRootEntry.NowPlaying
) {
    var lcdMode by mutableStateOf(PocketAdvanceLcdMode.NowPlaying)
        private set

    var browserPage by mutableStateOf<PocketAdvanceBrowserPage>(PocketAdvanceBrowserPage.Root)
        private set

    var selectedSongKey by mutableStateOf<String?>(null)
        private set
    private var selectedSongIndexHint by mutableIntStateOf(0)

    var rootListState = LazyListState()
        private set
    var songsListState = LazyListState()
        private set

    internal var rootScrollRequest by mutableLongStateOf(0L)
        private set
    internal var songsScrollRequest by mutableLongStateOf(0L)
        private set
    private var handledRootScrollRequest = 0L
    private var handledSongsScrollRequest = 0L
    private var restoredSongsViewportKey: String? = null

    internal val artistsCursor = PocketAdvanceBrowseCursor()
    internal val artistSongsCursor = PocketAdvanceBrowseCursor()
    internal var retainedArtistKey by mutableStateOf<String?>(null)
        private set
    val selectedArtistKey: String? get() = artistsCursor.selectedKey
    val selectedArtistSongKey: String? get() = artistSongsCursor.selectedKey
    val artistsListState: LazyListState get() = artistsCursor.listState
    val artistSongsListState: LazyListState get() = artistSongsCursor.listState

    internal val albumsCursor = PocketAdvanceBrowseCursor(preserveEmptyKeys = true)
    internal val albumTracksCursor = PocketAdvanceBrowseCursor()
    internal var retainedAlbumKey by mutableStateOf<String?>(null)
        private set
    val selectedAlbumKey: String? get() = albumsCursor.selectedKey
    val selectedAlbumTrackKey: String? get() = albumTracksCursor.selectedKey
    val albumsListState: LazyListState get() = albumsCursor.listState
    val albumTracksListState: LazyListState get() = albumTracksCursor.listState

    var selectedRootEntry by mutableStateOf(initialRootEntry)
        private set

    fun openBrowser() {
        lcdMode = PocketAdvanceLcdMode.MusicBrowser
    }

    fun showNowPlaying() {
        lcdMode = PocketAdvanceLcdMode.NowPlaying
    }

    fun toggleMode() {
        if (lcdMode == PocketAdvanceLcdMode.NowPlaying) openBrowser() else showNowPlaying()
    }

    fun back() {
        when {
            lcdMode == PocketAdvanceLcdMode.NowPlaying -> openBrowser()
            browserPage is PocketAdvanceBrowserPage.ArtistSongs -> browserPage = PocketAdvanceBrowserPage.Artists
            browserPage is PocketAdvanceBrowserPage.AlbumRecord -> browserPage = PocketAdvanceBrowserPage.AlbumCatalogue
            browserPage == PocketAdvanceBrowserPage.AlbumCatalogue -> browserPage = PocketAdvanceBrowserPage.Root
            browserPage == PocketAdvanceBrowserPage.Songs || browserPage == PocketAdvanceBrowserPage.Artists ->
                browserPage = PocketAdvanceBrowserPage.Root
            else -> showNowPlaying()
        }
    }

    fun moveSelectionUp(songKeys: List<String> = emptyList()) = moveSelection(-1, songKeys)

    fun moveSelectionDown(songKeys: List<String> = emptyList()) = moveSelection(1, songKeys)

    private fun moveSelection(delta: Int, songKeys: List<String>) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser) return
        when (browserPage) {
            PocketAdvanceBrowserPage.Root -> {
                val entries = PocketAdvanceRootEntry.entries
                selectedRootEntry = entries[(selectedRootEntry.ordinal + delta + entries.size) % entries.size]
                rootScrollRequest += 1
            }
            PocketAdvanceBrowserPage.Songs -> {
                val current = selectedSongIndex(songKeys) ?: return
                val index = (current + delta + songKeys.size) % songKeys.size
                selectSong(songKeys[index], index)
                songsScrollRequest += 1
            }
            PocketAdvanceBrowserPage.Artists -> artistsCursor.move(delta, songKeys, wrap = true)
            is PocketAdvanceBrowserPage.ArtistSongs -> artistSongsCursor.move(delta, songKeys, wrap = true)
            PocketAdvanceBrowserPage.AlbumCatalogue -> albumsCursor.move(delta, songKeys, wrap = true)
            is PocketAdvanceBrowserPage.AlbumRecord -> albumTracksCursor.move(delta, songKeys, wrap = true)
        }
    }

    /** Hold ticks are Songs-only and stop at an edge; ordinary presses keep their wraparound. */
    internal fun repeatSongSelection(delta: Int, songKeys: List<String>): Boolean {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Songs ||
            (delta != -1 && delta != 1)) return false
        val current = selectedSongIndex(songKeys) ?: return false
        val next = current + delta
        if (next !in songKeys.indices) return false
        selectSong(songKeys[next], next)
        songsScrollRequest += 1
        return true
    }

    /** Only the current browse page can accept a hold tick captured by its input handler. */
    internal fun repeatBrowseSelection(delta: Int, keys: List<String>, page: PocketAdvanceBrowserPage): Boolean {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != page || (delta != -1 && delta != 1)) return false
        return when (page) {
            PocketAdvanceBrowserPage.Root -> false
            PocketAdvanceBrowserPage.Songs -> repeatSongSelection(delta, keys)
            PocketAdvanceBrowserPage.Artists -> artistsCursor.move(delta, keys, wrap = false)
            is PocketAdvanceBrowserPage.ArtistSongs -> artistSongsCursor.move(delta, keys, wrap = false)
            PocketAdvanceBrowserPage.AlbumCatalogue -> albumsCursor.move(delta, keys, wrap = false)
            is PocketAdvanceBrowserPage.AlbumRecord -> albumTracksCursor.move(delta, keys, wrap = false)
        }
    }

    fun confirmSelection() {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Root) return
        when (selectedRootEntry) {
            PocketAdvanceRootEntry.NowPlaying -> showNowPlaying()
            PocketAdvanceRootEntry.Songs -> browserPage = PocketAdvanceBrowserPage.Songs
            PocketAdvanceRootEntry.Artists -> browserPage = PocketAdvanceBrowserPage.Artists
            PocketAdvanceRootEntry.Albums -> browserPage = PocketAdvanceBrowserPage.AlbumCatalogue
        }
    }

    fun activateRootEntry(entry: PocketAdvanceRootEntry) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Root || !entry.isAvailable) return
        selectedRootEntry = entry
        confirmSelection()
    }

    fun selectedArtistIndex(keys: List<String>): Int? = artistsCursor.selectedIndex(keys)
    fun selectedArtistSongIndex(keys: List<String>): Int? = artistSongsCursor.selectedIndex(keys)

    fun selectedBrowseIndex(keys: List<String>): Int? = when (browserPage) {
        PocketAdvanceBrowserPage.Root -> null
        PocketAdvanceBrowserPage.Songs -> selectedSongIndex(keys)
        PocketAdvanceBrowserPage.Artists -> selectedArtistIndex(keys)
        is PocketAdvanceBrowserPage.ArtistSongs -> selectedArtistSongIndex(keys)
        PocketAdvanceBrowserPage.AlbumCatalogue -> selectedAlbumIndex(keys)
        is PocketAdvanceBrowserPage.AlbumRecord -> selectedAlbumTrackIndex(keys)
    }

    fun openArtist(key: String, index: Int) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Artists) return
        artistsCursor.select(key, index)
        if (retainedArtistKey != key) artistSongsCursor.clear()
        retainedArtistKey = key
        browserPage = PocketAdvanceBrowserPage.ArtistSongs(key)
    }

    fun selectArtistSong(key: String, index: Int) {
        if (lcdMode == PocketAdvanceLcdMode.MusicBrowser && browserPage is PocketAdvanceBrowserPage.ArtistSongs) {
            artistSongsCursor.select(key, index)
        }
    }

    /** Reuse canonical groups; an authoritative removed artist returns to the safe parent list. */
    fun reconcileArtists(artists: List<LibraryArtistGroup>, isLibraryReady: Boolean) {
        artistsCursor.reconcile(artists.map { it.key }, isLibraryReady)
        val retained = retainedArtistKey ?: return
        val artist = artists.firstOrNull { it.key == retained }
        if (artist == null && isLibraryReady) {
            if (browserPage is PocketAdvanceBrowserPage.ArtistSongs) browserPage = PocketAdvanceBrowserPage.Artists
            retainedArtistKey = null
            artistSongsCursor.clear()
        } else {
            artistSongsCursor.reconcile(artist?.songs?.map { it.stableUiKey() }.orEmpty(), isLibraryReady)
        }
    }

    fun selectedAlbumIndex(keys: List<String>): Int? = albumsCursor.selectedIndex(keys)
    fun selectedAlbumTrackIndex(keys: List<String>): Int? = albumTracksCursor.selectedIndex(keys)

    fun openAlbum(key: String, index: Int) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.AlbumCatalogue) return
        albumsCursor.select(key, index)
        if (retainedAlbumKey != key) albumTracksCursor.clear()
        retainedAlbumKey = key
        browserPage = PocketAdvanceBrowserPage.AlbumRecord(key)
    }

    fun selectAlbumTrack(key: String, index: Int) {
        if (lcdMode == PocketAdvanceLcdMode.MusicBrowser && browserPage is PocketAdvanceBrowserPage.AlbumRecord) {
            albumTracksCursor.select(key, index)
        }
    }

    fun reconcileAlbums(albums: List<LibraryAlbumGroup>, isLibraryReady: Boolean) {
        albumsCursor.reconcile(albums.map { it.key }, isLibraryReady)
        val retained = retainedAlbumKey ?: return
        val album = albums.firstOrNull { it.key == retained }
        if (album == null && isLibraryReady) {
            if (browserPage is PocketAdvanceBrowserPage.AlbumRecord) browserPage = PocketAdvanceBrowserPage.AlbumCatalogue
            retainedAlbumKey = null
            albumTracksCursor.clear()
        } else {
            albumTracksCursor.reconcile(album?.songs?.map { it.stableUiKey() }.orEmpty(), isLibraryReady)
        }
    }

    /** The visible highlight and confirmation always resolve this same current-library index. */
    fun selectedSongIndex(songKeys: List<String>): Int? {
        if (songKeys.isEmpty()) return null
        val key = selectedSongKey
        if (key != null) {
            if (songKeys.getOrNull(selectedSongIndexHint) == key) return selectedSongIndexHint
            val relocated = songKeys.indexOf(key)
            if (relocated >= 0) return relocated
        }
        return selectedSongIndexHint.coerceIn(0, songKeys.lastIndex)
    }

    fun selectSong(key: String, index: Int) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Songs) return
        selectedSongKey = key
        selectedSongIndexHint = index.coerceAtLeast(0)
    }

    fun canConfirm(songKeys: List<String>): Boolean = when (browserPage) {
        PocketAdvanceBrowserPage.Root -> selectedRootEntry.isAvailable
        PocketAdvanceBrowserPage.Songs -> selectedSongIndex(songKeys) != null
        PocketAdvanceBrowserPage.Artists -> selectedArtistIndex(songKeys) != null
        is PocketAdvanceBrowserPage.ArtistSongs -> selectedArtistSongIndex(songKeys) != null
        PocketAdvanceBrowserPage.AlbumCatalogue -> selectedAlbumIndex(songKeys) != null
        is PocketAdvanceBrowserPage.AlbumRecord -> selectedAlbumTrackIndex(songKeys) != null
    }

    /** Busy/error snapshots must not erase a location that a rescan can still restore. */
    fun reconcileSongs(songKeys: List<String>, isLibraryReady: Boolean) {
        if (browserPage != PocketAdvanceBrowserPage.Songs && selectedSongKey == null) return
        val index = selectedSongIndex(songKeys)
        if (index == null) {
            if (isLibraryReady) {
                selectedSongKey = null
                selectedSongIndexHint = 0
                restoredSongsViewportKey = null
                songsListState.requestScrollToItem(0)
            }
            return
        }
        if (isLibraryReady || selectedSongKey == null || songKeys[index] == selectedSongKey) {
            selectedSongKey = songKeys[index]
            selectedSongIndexHint = index
        }
        restoredSongsViewportKey?.let { key ->
            val restoredIndex = songKeys.indexOf(key)
            if (restoredIndex >= 0 || isLibraryReady) {
                songsListState.requestScrollToItem(
                    if (restoredIndex >= 0) restoredIndex else songsListState.firstVisibleItemIndex.coerceIn(0, songKeys.lastIndex),
                    songsListState.firstVisibleItemScrollOffset
                )
                restoredSongsViewportKey = null
            }
        }
    }

    // Claim before suspending, so touch interruption or LCD toggling cannot replay an old D-pad scroll.
    internal fun takeRootScrollRequest(): Boolean {
        if (handledRootScrollRequest == rootScrollRequest) return false
        handledRootScrollRequest = rootScrollRequest
        return true
    }

    internal fun takeSongsScrollRequest(): Boolean {
        if (handledSongsScrollRequest == songsScrollRequest) return false
        handledSongsScrollRequest = songsScrollRequest
        return true
    }

    companion object {
        // Save compact page/cursor/viewport values only. Recreation always starts in Now Playing.
        val Saver: Saver<PocketAdvanceNavigationState, Any> = Saver(
            save = { state ->
                listOf(
                    state.selectedRootEntry.name,
                    when (state.browserPage) {
                        PocketAdvanceBrowserPage.Root -> "Root"
                        PocketAdvanceBrowserPage.Songs -> "Songs"
                        PocketAdvanceBrowserPage.Artists -> "Artists"
                        is PocketAdvanceBrowserPage.ArtistSongs -> "ArtistSongs"
                        PocketAdvanceBrowserPage.AlbumCatalogue -> "AlbumCatalogue"
                        is PocketAdvanceBrowserPage.AlbumRecord -> "AlbumRecord"
                    },
                    state.selectedSongKey.orEmpty(), state.selectedSongIndexHint,
                    state.rootListState.firstVisibleItemIndex, state.rootListState.firstVisibleItemScrollOffset,
                    state.songsListState.firstVisibleItemIndex, state.songsListState.firstVisibleItemScrollOffset,
                    if (state.selectedSongKey == null) "" else {
                        val visible = state.songsListState.layoutInfo.visibleItemsInfo.firstOrNull()
                        ((visible?.takeIf { it.index == state.songsListState.firstVisibleItemIndex }?.key as? String)
                            ?: state.restoredSongsViewportKey).orEmpty()
                    },
                    state.artistsCursor.save(), state.retainedArtistKey.orEmpty(), state.artistSongsCursor.save(),
                    state.albumsCursor.save(), state.retainedAlbumKey, state.albumTracksCursor.save()
                )
            },
            restore = { saved ->
                val values = saved as? List<*>
                // Accept Session 3B's single root-entry string as well as the extended compact state.
                val rootName = (values?.getOrNull(0) ?: saved) as? String
                PocketAdvanceNavigationState(
                    PocketAdvanceRootEntry.entries.firstOrNull { it.name == rootName }
                        ?: PocketAdvanceRootEntry.NowPlaying
                ).apply {
                    artistsCursor.restore(values?.getOrNull(9))
                    retainedArtistKey = (values?.getOrNull(10) as? String)?.takeIf(String::isNotEmpty)
                    artistSongsCursor.restore(values?.getOrNull(11))
                    albumsCursor.restore(values?.getOrNull(12))
                    retainedAlbumKey = values?.getOrNull(13) as? String
                    albumTracksCursor.restore(values?.getOrNull(14))
                    browserPage = when (values?.getOrNull(1)) {
                        "Songs" -> PocketAdvanceBrowserPage.Songs
                        "Artists" -> PocketAdvanceBrowserPage.Artists
                        "ArtistSongs" -> retainedArtistKey?.let { PocketAdvanceBrowserPage.ArtistSongs(it) }
                            ?: PocketAdvanceBrowserPage.Artists
                        "AlbumCatalogue" -> PocketAdvanceBrowserPage.AlbumCatalogue
                        "AlbumRecord" -> retainedAlbumKey?.let { PocketAdvanceBrowserPage.AlbumRecord(it) }
                            ?: PocketAdvanceBrowserPage.AlbumCatalogue
                        else -> PocketAdvanceBrowserPage.Root
                    }
                    selectedSongKey = (values?.getOrNull(2) as? String)?.takeIf { it.isNotEmpty() }
                    selectedSongIndexHint = ((values?.getOrNull(3) as? Int) ?: 0).coerceAtLeast(0)
                    fun number(index: Int) = ((values?.getOrNull(index) as? Int) ?: 0).coerceAtLeast(0)
                    rootListState = LazyListState(number(4), number(5))
                    songsListState = LazyListState(number(6), number(7))
                    restoredSongsViewportKey = (values?.getOrNull(8) as? String)?.takeIf { it.isNotEmpty() }
                }
            }
        )
    }
}

@Composable
internal fun rememberPocketAdvanceNavigationState(): PocketAdvanceNavigationState =
    rememberSaveable(saver = PocketAdvanceNavigationState.Saver) { PocketAdvanceNavigationState() }

/** Read synchronously: beginDrag raises isDragging before dragBy schedules any progress change. */
internal fun shouldShowPocketAdvanceBrowser(
    navigation: PocketAdvanceNavigationState,
    player: PlayerMorphState
): Boolean = navigation.lcdMode == PocketAdvanceLcdMode.MusicBrowser &&
    player.progress == 1f && player.targetPresentation == PlayerPresentation.Expanded &&
    player.settledPresentation == PlayerPresentation.Expanded && !player.isDragging && !player.isAnimating

/** Reset the visible mode only after a full collapse or when the Advance presentation is left. */
@Composable
internal fun PocketAdvanceNavigationLifecycleEffect(
    navigation: PocketAdvanceNavigationState,
    player: PlayerMorphState,
    identity: PlayerPresentationIdentity
) {
    val collapsed = player.isCollapsedAndIdle
    LaunchedEffect(navigation, identity, collapsed) {
        if (collapsed || identity != PlayerPresentationIdentity.PocketAdvance) navigation.showNowPlaying()
    }
}
