package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState
import io.github.rsgarrido.sazanami.ui.state.LocalPlaybackStatus
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.resolve
import kotlinx.coroutines.flow.StateFlow

/**
 * Standalone presentation only. No resolver/host registration, playback owner, or collapse simulation.
 * A future host supplies its existing callbacks, seek environment and overlay/input capabilities.
 */
@Composable
internal fun PocketHorizonExpandedPlayer(
    playbackState: PlaybackUiState,
    progressState: StateFlow<PlaybackProgressUiState>,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
    seekEnabled: Boolean = false,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onOpenQueueHubClick: (() -> Unit)? = null,
    onOpenMoreClick: (() -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null,
    onViewArtwork: ((Song) -> Unit)? = null,
    style: PocketHorizonStyle = PocketHorizonStyle.Default,
    libraryState: LibraryUiState = LibraryUiState.Empty,
    songSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING),
    albumSortState: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING),
    artistSortState: LibrarySortState = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING),
    onSongClick: ((Song, List<Song>) -> Unit)? = null,
    navigationState: PocketHorizonNavigationState = rememberPocketHorizonNavigationState(),
    measurements: PocketHorizonEndpointMeasurements? = null,
    collapseGestureModifier: Modifier = Modifier,
    measurementOnly: Boolean = false,
    gestureEnabled: Boolean = inputEnabled
) {
    val enabled = inputEnabled && !navigationState.showControlGuide && playbackState.isConnected && playbackState.currentSong != null
    val showNowPlaying = measurementOnly || navigationState.category == PocketHorizonCategory.NowPlaying
    val library = if (measurementOnly) null else rememberPocketHorizonLibrary(libraryState, songSortState, albumSortState, artistSortState)
    if (library != null) {
        LaunchedEffect(navigationState, library.albumKeys, library.artistKeys, library.songKeys, library.isAuthoritative) {
            navigationState.reconcileLibrary(library.albumByKey.keys, library.artistByKey.keys,
                library.isAuthoritative, library.songByKey.keys)
        }
    }
    // Detail Back is local; the presentation host supplies endpoint/overlay input eligibility.
    BackHandler(!measurementOnly && inputEnabled && navigationState.category != PocketHorizonCategory.NowPlaying) {
        navigationState.back()
    }
    val selectSong: ((Song, List<Song>) -> Unit)? = onSongClick?.let { action ->
        { song, context ->
            if (inputEnabled && navigationState.category != PocketHorizonCategory.NowPlaying) {
                action(song, context)
                navigationState.selectSong(song.stableUiKey())
                // Unit callback confirms dispatch only. Playing/pending/failure remain externally owned.
                navigationState.showNowPlaying()
            }
        }
    }
    val fontScale = LocalDensity.current.fontScale
    val contentKey = pocketHorizonMeasurementKey(playbackState.currentSong, LocalLayoutDirection.current)
    val reports = measurements?.takeIf { it.contentKey == contentKey &&
        showNowPlaying }
    DisposableEffect(measurements, contentKey, reports) { onDispose { measurements?.clear() } }
    val consoleInput = remember { PocketHorizonConsoleInput() }
    CompositionLocalProvider(LocalHorizonConsoleInput provides consoleInput) {
    PocketHorizonShell(modifier, style, reports,
        leftControls = { PocketHorizonControlWing(true, inputEnabled && !measurementOnly, enabled,
            navigationState, consoleInput, onPlayPauseClick, onOpenQueueHubClick, style) },
        rightControls = { PocketHorizonControlWing(false, inputEnabled && !measurementOnly, enabled,
            navigationState, consoleInput, onPlayPauseClick, onOpenQueueHubClick, style) },
        leftShoulder = { PocketHorizonShoulder(true, enabled && !measurementOnly && previousEnabled, onPreviousClick, style) },
        rightShoulder = { PocketHorizonShoulder(false, enabled && !measurementOnly && nextEnabled, onNextClick, style) },
        systemControls = { PocketHorizonSystemControls(inputEnabled && !measurementOnly, navigationState, style) }
    ) { layout ->
        // The plane has a finite height even inside the extreme-small-host scroll fallback. Lazy
        // browsing never inherits the unbounded constraints of Now Playing's own vertical scroll.
        val minimumBody = if (showNowPlaying)
            PocketHorizonLayoutMetrics.MinimumTouchTarget else PocketHorizonLayoutMetrics.browserMinimumHeight(fontScale)
        val planeHeight = maxOf(layout.contentViewportSize.height,
            layout.headerHeight + PocketHorizonLayoutMetrics.HeaderGap + minimumBody)
        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Column(Modifier.width(layout.contentWidth).height(planeHeight)) {
                val actions: @Composable () -> Unit = {
                    PocketHorizonActions(enabled, onOpenQueueHubClick, onOpenMoreClick, onOpenLyrics, style)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = PocketHorizonLayoutMetrics.statusHeight(fontScale)),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // The host's collapse recognizer belongs only to this status region, separate
                    // from hardware controls, category selection, artwork, seeking and lazy lists.
                    Box(Modifier.weight(1f).heightIn(min = PocketHorizonLayoutMetrics.MinimumTouchTarget)
                        .testTag("pocketHorizonCollapseRegion")
                        .horizonAnchor(reports, PocketHorizonAnchor.GestureRegion)
                        .then(if (gestureEnabled) collapseGestureModifier else Modifier),
                        contentAlignment = Alignment.CenterStart) {
                        PocketHorizonStatus(playbackState, style, Modifier.fillMaxWidth())
                    }
                    actions()
                }
                Spacer(Modifier.height(PocketHorizonLayoutMetrics.CategoryGap))
                PocketHorizonCategoryNavigation(navigationState, inputEnabled && !measurementOnly && !navigationState.showControlGuide, style, Modifier.fillMaxWidth())
                Spacer(Modifier.height(PocketHorizonLayoutMetrics.HeaderGap))
                val bodyHeight = maxOf(layout.bodyMinHeight,
                    planeHeight - layout.headerHeight - PocketHorizonLayoutMetrics.HeaderGap)
                Box(Modifier.fillMaxWidth().weight(1f).testTag("pocketHorizonCategoryBody")) {
                    if (showNowPlaying) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            PocketHorizonNowPlaying(
                                playbackState = playbackState,
                                progressState = progressState,
                                layout = layout,
                                enabled = enabled,
                                seekEnabled = seekEnabled,
                                previousEnabled = previousEnabled,
                                nextEnabled = nextEnabled,
                                onPlayPauseClick = onPlayPauseClick,
                                onPreviousClick = onPreviousClick,
                                onNextClick = onNextClick,
                                onSeekChange = onSeekChange,
                                onViewArtwork = onViewArtwork,
                                style = style,
                                modifier = Modifier.heightIn(min = bodyHeight),
                                measurements = reports
                            )
                        }
                    } else {
                        PocketHorizonLibraryBrowser(requireNotNull(library), libraryState, navigationState, inputEnabled && !navigationState.showControlGuide,
                            selectSong, style, Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (navigationState.showControlGuide && inputEnabled && !measurementOnly) {
            BackHandler { navigationState.showControlGuide = false }
            val guideTitle = stringResource(R.string.horizon_guide_title)
            Column(Modifier.fillMaxSize().background(style.bezel.copy(alpha = .97f))
                .pointerInput(Unit) { awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                } }.verticalScroll(rememberScrollState()).padding(16.dp)
                .semantics { paneTitle = guideTitle }
                .testTag("horizonControlGuide")) {
                Text(stringResource(R.string.horizon_guide_title), color = style.accent)
                Text(stringResource(R.string.horizon_guide_body), color = style.text)
            }
        }
    }
    }
    if (!measurementOnly) LaunchedEffect(inputEnabled) { if (!inputEnabled) navigationState.showControlGuide = false }
}

@Composable
private fun PocketHorizonStatus(playbackState: PlaybackUiState, style: PocketHorizonStyle, modifier: Modifier) {
    val status = LocalPlaybackStatus.current ?: playbackState.playbackStatus?.resolve()
        ?: stringResource(when {
            !playbackState.isConnected -> R.string.pocket_horizon_disconnected
            playbackState.currentSong == null -> R.string.pocket_horizon_no_track
            playbackState.isPlaying -> R.string.pocket_horizon_playing
            else -> R.string.pocket_horizon_paused
        })
    val typography = TextStyle(fontFamily = FontFamily.SansSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    Column(modifier.testTag("pocketHorizonStatus").semantics(mergeDescendants = true) {}) {
        Text(status, color = style.text, style = typography, fontSize = 14.sp, lineHeight = 22.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
