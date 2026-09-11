package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import io.github.rsgarrido.sazanami.data.FolderId
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibrarySortStateSaver
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterState
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterStateSaver
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkSourceScope
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.restoreFolderBrowseSelection
import io.github.rsgarrido.sazanami.ui.library.saveFolderBrowseSelection
import io.github.rsgarrido.sazanami.ui.library.SearchCategory
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.navigation.PlaybackLaunchContext
import io.github.rsgarrido.sazanami.ui.navigation.playbackLaunchContextSaver
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState

internal enum class DetailEntryOrigin {
    LIBRARY,
    SEARCH,
    HOME_PINNED
}

internal fun detailReturnDestination(origin: DetailEntryOrigin): MainDestination = when (origin) {
    DetailEntryOrigin.LIBRARY -> MainDestination.LIBRARY
    DetailEntryOrigin.SEARCH -> MainDestination.SEARCH
    DetailEntryOrigin.HOME_PINNED -> MainDestination.HOME
}

internal fun DetailEntryOrigin.sharedArtworkSourceScope(): LibrarySharedArtworkSourceScope =
    when (this) {
        DetailEntryOrigin.HOME_PINNED -> LibrarySharedArtworkSourceScope.HOME_PINNED
        DetailEntryOrigin.LIBRARY,
        DetailEntryOrigin.SEARCH -> LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
    }

@Stable
class MusicNavigationState internal constructor(
    val mainDestination: MutableState<MainDestination>,
    val selectedLibraryTab: MutableState<LibraryTab>,
    val playbackLaunchContext: MutableState<PlaybackLaunchContext>,
    val selectedArtistName: MutableState<String?>,
    val selectedAlbumKey: MutableState<String?>,
    val selectedGenreKey: MutableState<String?>,
    val selectedPlaylistId: MutableState<Long?>,
    val searchQuery: MutableState<String>,
    val selectedSongFilterState: MutableState<LibrarySongFilterState>,
    val selectedSongSortState: MutableState<LibrarySortState>,
    val selectedArtistSortState: MutableState<LibrarySortState>,
    val selectedAlbumSortState: MutableState<LibrarySortState>,
    val selectedFavoriteSortState: MutableState<LibrarySortState>,
    val selectedFolderId: MutableState<FolderId?> = mutableStateOf(null),
    val searchCategory: MutableState<SearchCategory> = mutableStateOf(SearchCategory.ALL),
    internal val albumDetailOrigin: MutableState<DetailEntryOrigin> =
        mutableStateOf(DetailEntryOrigin.LIBRARY),
    internal val artistDetailOrigin: MutableState<DetailEntryOrigin> =
        mutableStateOf(DetailEntryOrigin.LIBRARY),
    internal val playlistDetailOrigin: MutableState<DetailEntryOrigin> =
        mutableStateOf(DetailEntryOrigin.LIBRARY)
) {
    private fun currentDetailOrigin(): DetailEntryOrigin = when (mainDestination.value) {
        MainDestination.SEARCH -> DetailEntryOrigin.SEARCH
        else -> DetailEntryOrigin.LIBRARY
    }

    fun openAlbum(key: String) {
        openAlbum(key, currentDetailOrigin())
    }

    internal fun openAlbum(key: String, origin: DetailEntryOrigin) {
        albumDetailOrigin.value = origin
        selectedAlbumKey.value = key
        selectedLibraryTab.value = LibraryTab.ALBUMS
    }

    fun openArtist(name: String) {
        openArtist(name, currentDetailOrigin())
    }

    internal fun openArtist(name: String, origin: DetailEntryOrigin) {
        artistDetailOrigin.value = origin
        selectedArtistName.value = name
        selectedLibraryTab.value = LibraryTab.ARTISTS
    }

    fun openPlaylist(id: Long) {
        openPlaylist(id, currentDetailOrigin())
    }

    fun openFolder(id: FolderId) {
        selectedFolderId.value = id
        selectedLibraryTab.value = LibraryTab.FOLDERS
    }

    fun clearFolder() {
        selectedFolderId.value = null
    }

    internal fun openPlaylist(id: Long, origin: DetailEntryOrigin) {
        playlistDetailOrigin.value = origin
        selectedPlaylistId.value = id
        selectedLibraryTab.value = LibraryTab.PLAYLISTS
    }

    fun openPinnedAlbum(key: String) {
        openAlbum(key, DetailEntryOrigin.HOME_PINNED)
        mainDestination.value = MainDestination.LIBRARY
    }

    fun openPinnedArtist(name: String) {
        openArtist(name, DetailEntryOrigin.HOME_PINNED)
        mainDestination.value = MainDestination.LIBRARY
    }

    fun openPinnedPlaylist(id: Long) {
        openPlaylist(id, DetailEntryOrigin.HOME_PINNED)
        mainDestination.value = MainDestination.LIBRARY
    }

    fun clearAlbum() {
        selectedAlbumKey.value = null
        albumDetailOrigin.value = DetailEntryOrigin.LIBRARY
    }

    fun clearArtist() {
        selectedArtistName.value = null
        artistDetailOrigin.value = DetailEntryOrigin.LIBRARY
    }

    fun clearPlaylist() {
        selectedPlaylistId.value = null
        playlistDetailOrigin.value = DetailEntryOrigin.LIBRARY
    }

    fun closeAlbum() {
        val returnDestination = detailReturnDestination(albumDetailOrigin.value)
        clearAlbum()
        if (selectedArtistName.value != null) selectedLibraryTab.value = LibraryTab.ARTISTS
        mainDestination.value = returnDestination
    }

    fun closeArtist() {
        val returnDestination = detailReturnDestination(artistDetailOrigin.value)
        clearArtist()
        mainDestination.value = returnDestination
    }

    fun closePlaylist() {
        val returnDestination = detailReturnDestination(playlistDetailOrigin.value)
        clearPlaylist()
        mainDestination.value = returnDestination
    }
}

@Composable
fun rememberMusicNavigationState(): MusicNavigationState {
    val mainDestination = rememberSaveable { mutableStateOf(MainDestination.HOME) }
    val selectedLibraryTab = rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    val playbackLaunchContext = rememberSaveable(stateSaver = playbackLaunchContextSaver) {
        mutableStateOf<PlaybackLaunchContext>(PlaybackLaunchContext.Home)
    }
    val selectedArtistName = rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAlbumKey = rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGenreKey = rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPlaylistId = rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedFolderId = rememberSaveable(saver = FolderBrowseSelectionSaver) {
        mutableStateOf<FolderId?>(null)
    }
    val albumDetailOrigin = rememberSaveable {
        mutableStateOf(DetailEntryOrigin.LIBRARY)
    }
    val artistDetailOrigin = rememberSaveable {
        mutableStateOf(DetailEntryOrigin.LIBRARY)
    }
    val playlistDetailOrigin = rememberSaveable {
        mutableStateOf(DetailEntryOrigin.LIBRARY)
    }
    val searchQuery = rememberSaveable { mutableStateOf("") }
    val searchCategory = rememberSaveable { mutableStateOf(SearchCategory.ALL) }
    val selectedSongFilterState = rememberSaveable(stateSaver = LibrarySongFilterStateSaver) {
        mutableStateOf(LibrarySongFilterState())
    }
    val selectedSongSortState = rememberSaveable(stateSaver = LibrarySortStateSaver) {
        mutableStateOf(
            LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING)
        )
    }
    val selectedArtistSortState = rememberSaveable(stateSaver = LibrarySortStateSaver) {
        mutableStateOf(
            LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING)
        )
    }
    val selectedAlbumSortState = rememberSaveable(stateSaver = LibrarySortStateSaver) {
        mutableStateOf(
            LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING)
        )
    }
    val selectedFavoriteSortState = rememberSaveable(stateSaver = LibrarySortStateSaver) {
        mutableStateOf(
            LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING)
        )
    }
    return remember(
        mainDestination,
        selectedLibraryTab,
        playbackLaunchContext,
        selectedArtistName,
        selectedAlbumKey,
        selectedGenreKey,
        selectedPlaylistId,
        selectedFolderId,
        albumDetailOrigin,
        artistDetailOrigin,
        playlistDetailOrigin,
        searchQuery,
        searchCategory,
        selectedSongFilterState,
        selectedSongSortState,
        selectedArtistSortState,
        selectedAlbumSortState,
        selectedFavoriteSortState
    ) {
        MusicNavigationState(
            mainDestination,
            selectedLibraryTab,
            playbackLaunchContext,
            selectedArtistName,
            selectedAlbumKey,
            selectedGenreKey,
            selectedPlaylistId,
            searchQuery,
            selectedSongFilterState,
            selectedSongSortState,
            selectedArtistSortState,
            selectedAlbumSortState,
            selectedFavoriteSortState,
            selectedFolderId,
            searchCategory,
            albumDetailOrigin,
            artistDetailOrigin,
            playlistDetailOrigin
        )
    }
}

private val FolderBrowseSelectionSaver = Saver<MutableState<FolderId?>, List<String>>(
    save = { state -> saveFolderBrowseSelection(state.value) },
    restore = { saved -> mutableStateOf(restoreFolderBrowseSelection(saved)) }
)

enum class MusicPrimaryDestination {
    FOLDERS,
    SETTINGS,
    DIAGNOSTICS,
    EQUALIZER,
    STATISTICS,
    LISTENING_HISTORY_IMPORT,
    LISTENING_HISTORY_RECONCILIATION
}
enum class MusicOverlayDestination { UP_NEXT, QUEUE_HUB, CREATE_PLAYLIST, SLEEP_TIMER }

@Stable
class MusicOverlayState internal constructor(
    val playerMorphState: PlayerMorphState,
    private val primaryDestination: MutableState<MusicPrimaryDestination?>,
    private val transientDestination: MutableState<MusicOverlayDestination?>
) {
    val isFolderScreenVisible = destinationState(primaryDestination, MusicPrimaryDestination.FOLDERS)
    val isSettingsScreenVisible = destinationState(primaryDestination, MusicPrimaryDestination.SETTINGS)
    val isDiagnosticsScreenVisible =
        destinationState(primaryDestination, MusicPrimaryDestination.DIAGNOSTICS)
    val isEqualizerScreenVisible =
        destinationState(primaryDestination, MusicPrimaryDestination.EQUALIZER)
    val isStatisticsScreenVisible =
        destinationState(primaryDestination, MusicPrimaryDestination.STATISTICS)
    val isListeningHistoryImportVisible =
        destinationState(primaryDestination, MusicPrimaryDestination.LISTENING_HISTORY_IMPORT)
    val isListeningHistoryReconciliationVisible = destinationState(
        primaryDestination,
        MusicPrimaryDestination.LISTENING_HISTORY_RECONCILIATION
    )
    val isExpandedUpNextSheetVisible =
        destinationState(transientDestination, MusicOverlayDestination.UP_NEXT)
    val isQueueHubVisible =
        destinationState(transientDestination, MusicOverlayDestination.QUEUE_HUB)
    val isCreatePlaylistDialogVisible =
        destinationState(transientDestination, MusicOverlayDestination.CREATE_PLAYLIST)
    val isSleepTimerDialogVisible =
        destinationState(transientDestination, MusicOverlayDestination.SLEEP_TIMER)
}

@Composable
fun rememberMusicOverlayState(): MusicOverlayState {
    val playerMorphState = rememberPlayerMorphState()
    val primaryDestination = rememberSaveable {
        mutableStateOf<MusicPrimaryDestination?>(null)
    }
    val transientDestination = rememberSaveable {
        mutableStateOf<MusicOverlayDestination?>(null)
    }
    return remember(playerMorphState, primaryDestination, transientDestination) {
        MusicOverlayState(playerMorphState, primaryDestination, transientDestination)
    }
}

private fun <T> destinationState(
    destination: MutableState<T?>,
    target: T
): MutableState<Boolean> = object : MutableState<Boolean> {
    override var value: Boolean
        get() = destination.value == target
        set(value) {
            if (value) {
                destination.value = target
            } else if (destination.value == target) {
                destination.value = null
            }
        }

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { nextValue -> value = nextValue }
}
