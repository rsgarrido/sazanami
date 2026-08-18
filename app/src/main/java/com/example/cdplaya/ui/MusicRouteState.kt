package com.example.cdplaya.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.ui.library.LibraryTab
import com.example.cdplaya.ui.navigation.MainDestination
import com.example.cdplaya.ui.navigation.PlaybackLaunchContext
import com.example.cdplaya.ui.navigation.playbackLaunchContextSaver
import com.example.cdplaya.ui.player.PlayerMorphState
import com.example.cdplaya.ui.player.rememberPlayerMorphState

@Stable
class MusicNavigationState internal constructor(
    val mainDestination: MutableState<MainDestination>,
    val selectedLibraryTab: MutableState<LibraryTab>,
    val playbackLaunchContext: MutableState<PlaybackLaunchContext>,
    val selectedArtistName: MutableState<String?>,
    val selectedAlbumFolderPath: MutableState<String?>,
    val selectedPlaylistId: MutableState<Long?>,
    val searchQuery: MutableState<String>,
    val selectedSongSortOption: MutableState<LibrarySortOption>,
    val selectedArtistSortOption: MutableState<LibrarySortOption>,
    val selectedAlbumSortOption: MutableState<LibrarySortOption>,
    val selectedFavoriteSortOption: MutableState<LibrarySortOption>
)

@Composable
fun rememberMusicNavigationState(): MusicNavigationState {
    val mainDestination = rememberSaveable { mutableStateOf(MainDestination.HOME) }
    val selectedLibraryTab = rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    val playbackLaunchContext = rememberSaveable(stateSaver = playbackLaunchContextSaver) {
        mutableStateOf<PlaybackLaunchContext>(PlaybackLaunchContext.Home)
    }
    val selectedArtistName = rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAlbumFolderPath = rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPlaylistId = rememberSaveable { mutableStateOf<Long?>(null) }
    val searchQuery = rememberSaveable { mutableStateOf("") }
    val selectedSongSortOption = rememberSaveable {
        mutableStateOf(LibrarySortOption.TITLE)
    }
    val selectedArtistSortOption = rememberSaveable {
        mutableStateOf(LibrarySortOption.NAME)
    }
    val selectedAlbumSortOption = rememberSaveable {
        mutableStateOf(LibrarySortOption.TITLE)
    }
    val selectedFavoriteSortOption = rememberSaveable {
        mutableStateOf(LibrarySortOption.TITLE)
    }
    return remember(
        mainDestination,
        selectedLibraryTab,
        playbackLaunchContext,
        selectedArtistName,
        selectedAlbumFolderPath,
        selectedPlaylistId,
        searchQuery,
        selectedSongSortOption,
        selectedArtistSortOption,
        selectedAlbumSortOption,
        selectedFavoriteSortOption
    ) {
        MusicNavigationState(
            mainDestination,
            selectedLibraryTab,
            playbackLaunchContext,
            selectedArtistName,
            selectedAlbumFolderPath,
            selectedPlaylistId,
            searchQuery,
            selectedSongSortOption,
            selectedArtistSortOption,
            selectedAlbumSortOption,
            selectedFavoriteSortOption
        )
    }
}

enum class MusicPrimaryDestination {
    FOLDERS,
    SETTINGS,
    DIAGNOSTICS,
    EQUALIZER,
    STATISTICS,
    LISTENING_HISTORY_IMPORT
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
