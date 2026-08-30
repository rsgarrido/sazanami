package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibrarySortStateSaver
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterState
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterStateSaver
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.navigation.PlaybackLaunchContext
import io.github.rsgarrido.sazanami.ui.navigation.playbackLaunchContextSaver
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState

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
    val selectedFavoriteSortState: MutableState<LibrarySortState>
)

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
    val searchQuery = rememberSaveable { mutableStateOf("") }
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
        searchQuery,
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
            selectedFavoriteSortState
        )
    }
}

enum class MusicPrimaryDestination {
    FOLDERS,
    SETTINGS,
    DIAGNOSTICS,
    EQUALIZER,
    STATISTICS,
    LISTENING_HISTORY_IMPORT,
    LISTENING_HISTORY_RECONCILIATION
}
enum class MusicOverlayDestination { UP_NEXT, CREATE_PLAYLIST, SLEEP_TIMER }

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
