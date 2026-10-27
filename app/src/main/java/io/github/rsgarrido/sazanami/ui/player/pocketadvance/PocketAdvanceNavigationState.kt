package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
}

/** Entry order matches Classic Wheel. Availability is temporary until each browser is implemented. */
enum class PocketAdvanceRootEntry {
    NowPlaying, Songs, Artists, Albums;

    val isAvailable: Boolean get() = this == NowPlaying
}

/** Browser location outlives LCD toggles and expansion. It never owns playback or morph geometry. */
@Stable
class PocketAdvanceNavigationState(
    initialRootEntry: PocketAdvanceRootEntry = PocketAdvanceRootEntry.NowPlaying
) {
    var lcdMode by mutableStateOf(PocketAdvanceLcdMode.NowPlaying)
        private set

    val browserPage: PocketAdvanceBrowserPage = PocketAdvanceBrowserPage.Root

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
        // The root has no parent page yet. Now Playing's B restores the retained browser location.
        toggleMode()
    }

    fun moveSelectionUp() = moveSelection(-1)

    fun moveSelectionDown() = moveSelection(1)

    private fun moveSelection(delta: Int) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser) return
        val entries = PocketAdvanceRootEntry.entries
        selectedRootEntry = entries[(selectedRootEntry.ordinal + delta + entries.size) % entries.size]
    }

    fun confirmSelection() {
        if (lcdMode == PocketAdvanceLcdMode.MusicBrowser && selectedRootEntry.isAvailable) {
            showNowPlaying()
        }
    }

    fun activateRootEntry(entry: PocketAdvanceRootEntry) {
        if (lcdMode != PocketAdvanceLcdMode.MusicBrowser || !entry.isAvailable) return
        selectedRootEntry = entry
        confirmSelection()
    }

    companion object {
        // Save only the compact browser location. Recreation always starts with safe Now Playing.
        val Saver: Saver<PocketAdvanceNavigationState, String> = Saver(
            save = { it.selectedRootEntry.name },
            restore = { saved ->
                PocketAdvanceNavigationState(
                    PocketAdvanceRootEntry.entries.firstOrNull { it.name == saved }
                        ?: PocketAdvanceRootEntry.NowPlaying
                )
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
