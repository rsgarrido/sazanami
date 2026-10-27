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
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity

enum class PocketAdvanceLcdMode { NowPlaying, MusicBrowser }

sealed interface PocketAdvanceBrowserPage {
    data object Root : PocketAdvanceBrowserPage
    data object Songs : PocketAdvanceBrowserPage
}

/** Entry order matches Classic Wheel. Availability is temporary until each browser is implemented. */
enum class PocketAdvanceRootEntry {
    NowPlaying, Songs, Artists, Albums;

    val isAvailable: Boolean get() = this == NowPlaying || this == Songs
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
            browserPage == PocketAdvanceBrowserPage.Songs -> browserPage = PocketAdvanceBrowserPage.Root
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

    fun confirmSelection() {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Root) return
        when (selectedRootEntry) {
            PocketAdvanceRootEntry.NowPlaying -> showNowPlaying()
            PocketAdvanceRootEntry.Songs -> browserPage = PocketAdvanceBrowserPage.Songs
            PocketAdvanceRootEntry.Artists, PocketAdvanceRootEntry.Albums -> Unit
        }
    }

    fun activateRootEntry(entry: PocketAdvanceRootEntry) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || browserPage != PocketAdvanceBrowserPage.Root || !entry.isAvailable) return
        selectedRootEntry = entry
        confirmSelection()
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
                    if (state.browserPage == PocketAdvanceBrowserPage.Songs) "Songs" else "Root",
                    state.selectedSongKey.orEmpty(), state.selectedSongIndexHint,
                    state.rootListState.firstVisibleItemIndex, state.rootListState.firstVisibleItemScrollOffset,
                    state.songsListState.firstVisibleItemIndex, state.songsListState.firstVisibleItemScrollOffset,
                    if (state.selectedSongKey == null) "" else {
                        val visible = state.songsListState.layoutInfo.visibleItemsInfo.firstOrNull()
                        ((visible?.takeIf { it.index == state.songsListState.firstVisibleItemIndex }?.key as? String)
                            ?: state.restoredSongsViewportKey).orEmpty()
                    }
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
                    if (values?.getOrNull(1) == "Songs") browserPage = PocketAdvanceBrowserPage.Songs
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
