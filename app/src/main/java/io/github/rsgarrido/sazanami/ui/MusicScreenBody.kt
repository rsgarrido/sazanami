package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.LibraryFolder
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerScreen
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerScreenState
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerUiActions
import io.github.rsgarrido.sazanami.ui.home.HomeScreen
import io.github.rsgarrido.sazanami.ui.library.FolderSelectionScreen
import io.github.rsgarrido.sazanami.ui.library.LibraryBrowseSwitcher
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibrarySortStateSaver
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterState
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.SearchCategory
import io.github.rsgarrido.sazanami.ui.library.RatedSongFilter
import io.github.rsgarrido.sazanami.ui.library.RatedSongFilterRow
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.library.LibraryGridColumns
import io.github.rsgarrido.sazanami.ui.library.LibraryViewOptionsButton
import io.github.rsgarrido.sazanami.ui.library.LibraryViewOptionsSheet
import io.github.rsgarrido.sazanami.ui.library.MusicLibraryContent
import io.github.rsgarrido.sazanami.ui.library.libraryContentPresentation
import io.github.rsgarrido.sazanami.ui.library.libraryContentTopPadding
import io.github.rsgarrido.sazanami.ui.library.libraryContentTransitionSpec
import io.github.rsgarrido.sazanami.ui.library.LibrarySelectionHeaderContent
import io.github.rsgarrido.sazanami.ui.library.LocalLibrarySelectionUi
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedTransitionHost
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkDetailSourceScopes
import io.github.rsgarrido.sazanami.ui.library.LibrarySharedArtworkSourceScope
import io.github.rsgarrido.sazanami.ui.library.normalizeRatedSongFilterForQuickRateMode
import io.github.rsgarrido.sazanami.ui.library.viewCategory
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.queue.QueueSnackbarActions
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenField
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.settings.SettingsScreen
import io.github.rsgarrido.sazanami.ui.settings.DiagnosticsScreen
import io.github.rsgarrido.sazanami.ui.settings.ListeningHistoryImportScreen
import io.github.rsgarrido.sazanami.ui.settings.SpotifyImportUiActions
import io.github.rsgarrido.sazanami.ui.settings.ListeningHistoryReconciliationScreen
import io.github.rsgarrido.sazanami.ui.settings.ListeningHistoryReconciliationUiActions
import io.github.rsgarrido.sazanami.controller.SpotifyImportUiState
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgress
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryAppearanceUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryRefreshSummary
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import io.github.rsgarrido.sazanami.ui.statistics.StatisticsScreen
import io.github.rsgarrido.sazanami.ui.state.gridColumnCountFor
import io.github.rsgarrido.sazanami.ui.state.modeFor
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryViewOption
import kotlinx.coroutines.flow.StateFlow
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessState
import java.time.LocalDate

private const val LibraryDetailChromeDurationMillis = 280

@Composable
internal fun MusicScreenBody(
    songs: List<Song>,
    mediaAccessState: MediaAccessState,
    isLibraryLoading: Boolean,
    isLibraryRefreshing: Boolean,
    lastLibraryRefreshSummary: LibraryRefreshSummary?,
    libraryErrorMessage: String?,
    onRequestAudioAccess: () -> Unit,
    onRequestArtworkAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    currentSong: Song?,
    isPlayerConnected: Boolean,
    previousHistoryCount: Int,
    forwardHistoryCount: Int,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    playbackProgressUiState: StateFlow<PlaybackProgressUiState>,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    libraryFolders: List<LibraryFolder>,
    folderSelectionMode: FolderSelectionMode,
    selectedLibraryFolders: Set<String>,
    excludedLibraryFolders: Set<String>,
    favoriteMembershipKeys: Set<String>,
    unresolvedFavoriteCount: Int,
    unresolvedPlaylistRowCount: Int,
    unresolvedListeningHistoryCount: Int,
    playlists: List<Playlist>,
    playlistFolders: List<PlaylistFolder>,
    selectedPlaylistStateId: Long?,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    isSelectedPlaylistLoading: Boolean,
    mainDestination: MainDestination,
    selectedLibraryTab: LibraryTab,
    selectedArtistName: String?,
    selectedAlbumKey: String?,
    selectedGenreKey: String?,
    selectedPlaylistId: Long?,
    albumSharedArtworkSourceScope: LibrarySharedArtworkSourceScope,
    artistSharedArtworkSourceScope: LibrarySharedArtworkSourceScope,
    playlistSharedArtworkSourceScope: LibrarySharedArtworkSourceScope,
    searchQuery: String,
    searchCategory: SearchCategory,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    selectedSongFilterState: LibrarySongFilterState,
    selectedSongSortState: LibrarySortState,
    selectedArtistSortState: LibrarySortState,
    selectedAlbumSortState: LibrarySortState,
    selectedFavoriteSortState: LibrarySortState,
    recentlyAddedLibrarySongs: List<Song>,
    recentlyAddedSongIds: Set<Long>,
    isPlayerExpanded: Boolean,
    isFolderScreenVisible: Boolean,
    isSettingsScreenVisible: Boolean,
    isDiagnosticsScreenVisible: Boolean,
    isEqualizerScreenVisible: Boolean,
    isStatisticsScreenVisible: Boolean,
    isListeningHistoryImportVisible: Boolean,
    isListeningHistoryReconciliationVisible: Boolean,
    spotifyImportUiState: SpotifyImportUiState,
    reconciliationUiState: ListeningHistoryReconciliationUiState,
    reconciliationActions: ListeningHistoryReconciliationUiActions,
    spotifyImportActions: SpotifyImportUiActions,
    listeningAnalyticsUiState: ListeningAnalyticsUiState,
    queueSnackbarActions: QueueSnackbarActions,
    onSettingsClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onStatisticsBackClick: () -> Unit,
    onListeningAnalyticsPresetSelected: (AnalyticsRangePreset) -> Unit,
    onListeningAnalyticsCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    onRetryListeningAnalytics: () -> Unit,
    onListeningAnalyticsTrendMetricSelected: (ListeningTrendMetric) -> Unit,
    onListeningAnalyticsRankingCategorySelected: (ListeningRankingCategory) -> Unit,
    onOpenLibrary: (LibraryTab) -> Unit,
    onPinnedAlbumSelected: (String) -> Unit,
    onPinnedArtistSelected: (String) -> Unit,
    onPinnedPlaylistSelected: (Playlist) -> Unit,
    onFolderBackClick: () -> Unit,
    onSettingsBackClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onListeningHistoryImportClick: () -> Unit,
    onListeningHistoryReconciliationClick: () -> Unit,
    onDiagnosticsBackClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEqualizerBackClick: () -> Unit,
    onLibraryFoldersClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onScanLibraryClick: () -> Unit,
    onLibraryFolderToggle: (String) -> Unit,
    onSelectAllLibraryFolders: () -> Unit,
    onClearSelectedLibraryFolders: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSongFilterStateChanged: (LibrarySongFilterState) -> Unit,
    onSongSortStateChanged: (LibrarySortState) -> Unit,
    onArtistSortStateChanged: (LibrarySortState) -> Unit,
    onAlbumSortStateChanged: (LibrarySortState) -> Unit,
    onFavoriteSortStateChanged: (LibrarySortState) -> Unit,
    onExpandPlayerClick: () -> Unit,
    onMiniPlayerUpNextClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onArtistSelected: (String) -> Unit,
    onBackFromArtist: () -> Unit,
    onAlbumSelected: (String) -> Unit,
    onBackFromAlbum: () -> Unit,
    onGenreSelected: (String) -> Unit,
    onBackFromGenre: () -> Unit,
    onBackFromQueue: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    onCreatePlaylistClick: (Long?) -> Unit,
    onCreatePlaylistFolderClick: (String) -> Unit,
    onRenamePlaylistFolderClick: (PlaylistFolder, String) -> Unit,
    onDeletePlaylistFolderClick: (PlaylistFolder) -> Unit,
    onMovePlaylistToFolderClick: (Playlist, Long?) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onAddPlaylistToQueueClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangePlaylistArtwork: (Playlist, Uri) -> Unit,
    onResetPlaylistArtwork: (Playlist) -> Unit,
    onBackFromPlaylist: () -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onReorderPlaylistSongs: (Long, List<Long>) -> Unit,
    onAddSongsToCurrentPlaylistClick: (Playlist, List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    onEditAlbumMetadataClick: (io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup) -> Unit,
    onBatchMetadataClick: () -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onSleepTimerClick: () -> Unit,
    recentlyPlayedSongs: List<Song>,
    mostPlayedSongs: List<Song>,
    selectedPlayerTheme: PlayerTheme,
    selectedAppFont: AppFont,
    onAppFontSelected: (AppFont) -> Unit,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    onPlayerThemeSelected: (PlayerTheme) -> Unit,
    onUpdatePlayerThemeTokenOverride: (PlayerTheme, PlayerThemeTokenField, Color) -> Unit,
    onResetPlayerThemeTokenOverrides: (PlayerTheme) -> Unit,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    onModernArtworkTransitionStyleSelected: (ModernArtworkTransitionStyle) -> Unit,
    selectedModernPlayerAppearance: ModernPlayerAppearance,
    onModernPlayerAppearanceChanged: (ModernPlayerAppearance) -> Unit,
    onResetModernPlayerAppearance: () -> Unit,
    selectedReplayGainMode: ReplayGainMode,
    onReplayGainModeSelected: (ReplayGainMode) -> Unit,
    selectedAudioOffloadPreference: AudioOffloadPreference,
    onAudioOffloadPreferenceSelected: (AudioOffloadPreference) -> Unit,
    smoothPlayPauseEnabled: Boolean,
    onSmoothPlayPauseEnabledChanged: (Boolean) -> Unit,
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledChanged: (Boolean) -> Unit,
    crossfadeDurationMs: Int,
    onCrossfadeDurationMsChanged: (Int) -> Unit,
    preserveAlbumTransitions: Boolean,
    onPreserveAlbumTransitionsChanged: (Boolean) -> Unit,
    audioOutputUiState: AudioOutputUiState,
    equalizerScreenState: EqualizerScreenState,
    equalizerActions: EqualizerUiActions,
    libraryAppearanceUiState: LibraryAppearanceUiState,
    onLibraryViewOptionSelected: (LibraryViewCategory, LibraryViewOption) -> Unit,
    settingsScrollState: ScrollState = rememberScrollState(),
    homeListState: LazyListState,
    statisticsListState: LazyListState,
    bottomContentPadding: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val searchStateHolder = rememberSaveableStateHolder()
    var isLibraryViewOptionsVisible by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedRatedSortState by rememberSaveable(stateSaver = LibrarySortStateSaver) {
        mutableStateOf(
            LibrarySortState(LibrarySortOption.RATING, LibrarySortDirection.DESCENDING)
        )
    }
    var selectedAddedSortState by rememberSaveable(stateSaver = LibrarySortStateSaver) {
        mutableStateOf(
            LibrarySortState(LibrarySortOption.DATE_ADDED, LibrarySortDirection.DESCENDING)
        )
    }
    var selectedRatedFilter by rememberSaveable {
        mutableStateOf(RatedSongFilter.ALL)
    }
    val selectedCollectionSortState = when (selectedLibraryTab) {
        LibraryTab.RATED -> selectedRatedSortState
        LibraryTab.RECENTLY_ADDED -> selectedAddedSortState
        else -> selectedSongSortState
    }
    val ratingUi = LocalSongRatingUi.current
    val activeRatedFilter = normalizeRatedSongFilterForQuickRateMode(
        filter = selectedRatedFilter,
        quickRateActive = ratingUi.quickRateMode
    )
    LaunchedEffect(selectedLibraryTab) {
        if (selectedLibraryTab != LibraryTab.RATED && ratingUi.quickRateMode) {
            ratingUi.onQuickRateModeChanged(false)
        }
    }
    LaunchedEffect(ratingUi.quickRateMode, selectedRatedFilter) {
        if (selectedRatedFilter != activeRatedFilter) {
            selectedRatedFilter = activeRatedFilter
        }
    }

    when {
        isStatisticsScreenVisible -> {
            StatisticsScreen(
                state = listeningAnalyticsUiState,
                onBackClick = onStatisticsBackClick,
                onPresetSelected = onListeningAnalyticsPresetSelected,
                onCustomRangeSelected = onListeningAnalyticsCustomRangeSelected,
                onRetry = onRetryListeningAnalytics,
                onTrendMetricSelected = onListeningAnalyticsTrendMetricSelected,
                onRankingCategorySelected = onListeningAnalyticsRankingCategorySelected,
                librarySongs = songs,
                listState = statisticsListState,
                modifier = modifier.fillMaxSize()
            )
        }

        isFolderScreenVisible -> {
            FolderSelectionScreen(
                libraryFolders = libraryFolders,
                folderSelectionMode = folderSelectionMode,
                selectedLibraryFolders = selectedLibraryFolders,
                excludedLibraryFolders = excludedLibraryFolders,
                onBackClick = onFolderBackClick,
                onFolderToggle = onLibraryFolderToggle,
                onSelectAllClick = onSelectAllLibraryFolders,
                onClearSelectionClick = onClearSelectedLibraryFolders,
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }

        isListeningHistoryImportVisible -> {
            ListeningHistoryImportScreen(
                state = spotifyImportUiState,
                actions = spotifyImportActions,
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }

        isListeningHistoryReconciliationVisible -> {
            ListeningHistoryReconciliationScreen(
                state = reconciliationUiState,
                actions = reconciliationActions,
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }

        isDiagnosticsScreenVisible -> {
            PlaybackProgress(playbackProgressUiState) { progress ->
                DiagnosticsScreen(
                    librarySongCount = songs.size,
                    selectedFolderCount = selectedLibraryFolders.size,
                    selectedPlayerTheme = selectedPlayerTheme,
                    selectedReplayGainMode = selectedReplayGainMode,
                    audioOutputUiState = audioOutputUiState,
                    isPlaybackConnected = isPlayerConnected,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = progress.currentPosition,
                    duration = progress.duration,
                    queueCount = queuedSongs.size,
                    upcomingCount = upcomingSongs.size,
                    previousCount = previousHistoryCount,
                    forwardCount = forwardHistoryCount,
                    unresolvedFavoriteCount = unresolvedFavoriteCount,
                    unresolvedPlaylistRowCount = unresolvedPlaylistRowCount,
                    unresolvedListeningHistoryCount = unresolvedListeningHistoryCount,
                    onBackClick = onDiagnosticsBackClick,
                    modifier = modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
        }

        isEqualizerScreenVisible -> {
            EqualizerScreen(
                state = equalizerScreenState,
                actions = equalizerActions.copy(onBack = onEqualizerBackClick),
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }

        isSettingsScreenVisible -> {
            SettingsScreen(
                totalSongCount = songs.size,
                availableFolderCount = libraryFolders.count { folder -> folder.parentPath == null },
                folderSelectionMode = folderSelectionMode,
                selectedFolderCount = selectedLibraryFolders.size,
                excludedFolderCount = excludedLibraryFolders.size,
                isLibraryRefreshing = isLibraryRefreshing,
                lastLibraryRefreshSummary = lastLibraryRefreshSummary,
                libraryErrorMessage = libraryErrorMessage,
                onBackClick = onSettingsBackClick,
                onLibraryFoldersClick = onLibraryFoldersClick,
                onScanLibraryClick = onScanLibraryClick,
                onExportBackupClick = onExportBackupClick,
                onRestoreBackupClick = onRestoreBackupClick,
                onListeningHistoryImportClick = onListeningHistoryImportClick,
                onListeningHistoryReconciliationClick =
                    onListeningHistoryReconciliationClick,
                onDiagnosticsClick = onDiagnosticsClick,
                equalizerSummary = equalizerScreenState.settingsSummary,
                onEqualizerClick = onEqualizerClick,
                isSleepTimerActive = isSleepTimerActive,
                sleepTimerDisplayText = sleepTimerDisplayText,
                onSleepTimerClick = onSleepTimerClick,
                selectedPlayerTheme = selectedPlayerTheme,
                selectedAppFont = selectedAppFont,
                onAppFontSelected = onAppFontSelected,
                selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                onPlayerThemeSelected = onPlayerThemeSelected,
                onUpdatePlayerThemeTokenOverride = onUpdatePlayerThemeTokenOverride,
                onResetPlayerThemeTokenOverrides = onResetPlayerThemeTokenOverrides,
                selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                onModernArtworkTransitionStyleSelected = onModernArtworkTransitionStyleSelected,
                selectedModernPlayerAppearance = selectedModernPlayerAppearance,
                onModernPlayerAppearanceChanged = onModernPlayerAppearanceChanged,
                onResetModernPlayerAppearance = onResetModernPlayerAppearance,
                previewSong = currentSong,
                selectedReplayGainMode = selectedReplayGainMode,
                onReplayGainModeSelected = onReplayGainModeSelected,
                selectedAudioOffloadPreference = selectedAudioOffloadPreference,
                onAudioOffloadPreferenceSelected = onAudioOffloadPreferenceSelected,
                smoothPlayPauseEnabled = smoothPlayPauseEnabled,
                onSmoothPlayPauseEnabledChanged = onSmoothPlayPauseEnabledChanged,
                crossfadeEnabled = crossfadeEnabled,
                onCrossfadeEnabledChanged = onCrossfadeEnabledChanged,
                crossfadeDurationMs = crossfadeDurationMs,
                onCrossfadeDurationMsChanged = onCrossfadeDurationMsChanged,
                preserveAlbumTransitions = preserveAlbumTransitions,
                onPreserveAlbumTransitionsChanged =
                    onPreserveAlbumTransitionsChanged,
                scrollState = settingsScrollState,
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }

        else -> {
            val currentLibraryViewMode = libraryAppearanceUiState.modeFor(selectedLibraryTab)
            val currentGridColumnCount =
                libraryAppearanceUiState.gridColumnCountFor(selectedLibraryTab)

            LibrarySharedTransitionHost(
                targetState = MusicShellTransitionState(
                    destination = mainDestination,
                    artistName = selectedArtistName,
                    albumKey = selectedAlbumKey,
                    playlistId = selectedPlaylistId,
                    albumSharedArtworkSourceScope = albumSharedArtworkSourceScope,
                    artistSharedArtworkSourceScope = artistSharedArtworkSourceScope,
                    playlistSharedArtworkSourceScope = playlistSharedArtworkSourceScope
                ),
                detailSourceScopes = { shellState ->
                    LibrarySharedArtworkDetailSourceScopes(
                        album = shellState.albumSharedArtworkSourceScope,
                        artist = shellState.artistSharedArtworkSourceScope,
                        playlist = shellState.playlistSharedArtworkSourceScope
                    )
                },
                contentKey = MusicShellTransitionState::destination,
                transitionSpec = {
                    if (targetState.destination == initialState.destination) {
                        EnterTransition.None.togetherWith(ExitTransition.None)
                    } else {
                        val direction = if (
                            targetState.destination.ordinal > initialState.destination.ordinal
                        ) 1 else -1

                        (fadeIn(tween(190)) +
                                scaleIn(tween(210), initialScale = 0.985f) +
                                slideInHorizontally(tween(210)) { width ->
                                    direction * width / 28
                                })
                            .togetherWith(
                                fadeOut(tween(145)) +
                                        scaleOut(tween(170), targetScale = 0.995f) +
                                        slideOutHorizontally(tween(175)) { width ->
                                            -direction * width / 36
                                        }
                            )
                    }
                },
                label = "appShellDestination"
            ) { shellState ->
                val destination = shellState.destination
                if (destination == MainDestination.HOME) {
                    HomeScreen(
                        mediaAccessState = mediaAccessState,
                        isLibraryLoading = isLibraryLoading,
                        libraryErrorMessage = libraryErrorMessage,
                        onRequestAudioAccess = onRequestAudioAccess,
                        onRequestArtworkAccess = onRequestArtworkAccess,
                        onOpenAppSettings = onOpenAppSettings,
                        recentlyPlayedSongs = recentlyPlayedSongs,
                        recentlyAddedSongs = recentlyAddedLibrarySongs,
                        favoriteSongs = songs.filter { song ->
                            song.membershipKey() in favoriteMembershipKeys
                        },
                        currentSongId = currentSong?.id,
                        songCount = songs.size,
                        albumCount = songs
                            .mapTo(mutableSetOf()) { song -> song.folderPath }
                            .size,
                        artistCount = songs
                            .mapTo(mutableSetOf()) { song ->
                                song.artist.ifBlank { "Unknown Artist" }
                            }
                            .size,
                        playlistCount = playlists.size,
                        onSettingsClick = onSettingsClick,
                        onStatisticsClick = onStatisticsClick,
                        onOpenLibrary = { tab ->
                            onOpenLibrary(tab)
                        },
                        onPinnedSongClick = { song ->
                            onSongClick(song, songs)
                        },
                        onPinnedAlbumClick = { albumKey ->
                            onPinnedAlbumSelected(albumKey)
                        },
                        onPinnedArtistClick = { artistName ->
                            onPinnedArtistSelected(artistName)
                        },
                        onPinnedPlaylistClick = { playlist ->
                            onPinnedPlaylistSelected(playlist)
                        },
                        onRecentlyPlayedSongClick = { song ->
                            onSongClick(song, recentlyPlayedSongs)
                        },
                        onRecentlyAddedSongClick = { song ->
                            onSongClick(song, recentlyAddedLibrarySongs)
                        },
                        onFavoriteSongClick = { song ->
                            onSongClick(
                                song,
                                songs.filter { candidate ->
                                    candidate.membershipKey() in favoriteMembershipKeys
                                }
                            )
                        },
                        modifier = modifier,
                        listState = homeListState,
                        bottomContentPadding = bottomContentPadding
                    )
                } else {
                    val isGroupedLibraryDetail = shellState.artistName != null ||
                            shellState.albumKey != null ||
                            selectedGenreKey != null
                    val isLibraryDetail = isGroupedLibraryDetail ||
                            shellState.playlistId != null
                    val isSearchDestination = destination == MainDestination.SEARCH && !isLibraryDetail
                    val selectedViewMode = if (isSearchDestination) {
                        LibraryViewMode.LIST
                    } else {
                        currentLibraryViewMode
                    }
                    val selectedGridColumnCount = if (isSearchDestination) {
                        LibraryGridColumns.DEFAULT
                    } else {
                        currentGridColumnCount
                    }
                    val selectionUi = LocalLibrarySelectionUi.current
                    val showSelectionHeader = shouldShowLibrarySelectionHeader(
                        selectionActive = selectionUi.state.isActive,
                        isLibraryDetail = isLibraryDetail,
                        hasAudioAccess = mediaAccessState.hasAudioAccess,
                        bindingMatchesSelection = selectionUi.headerState.binding?.entity ==
                            selectionUi.state.entity
                    )
                    val choreographLibraryDetailChrome =
                        shouldChoreographLibraryDetailChrome(
                            destination = destination,
                            selectedLibraryTab = selectedLibraryTab,
                            selectedArtistName = shellState.artistName,
                            selectedAlbumKey = shellState.albumKey,
                            selectedPlaylistId = shellState.playlistId,
                            artistSourceScope = shellState.artistSharedArtworkSourceScope,
                            albumSourceScope = shellState.albumSharedArtworkSourceScope,
                            playlistSourceScope = shellState.playlistSharedArtworkSourceScope
                        )
                    val composeLibraryChrome = !isLibraryDetail ||
                            choreographLibraryDetailChrome ||
                            !mediaAccessState.hasAudioAccess

                    LibraryDetailChromeLayout(
                        chromeVisible = !choreographLibraryDetailChrome,
                        composeChrome = composeLibraryChrome,
                        modifier = modifier.fillMaxSize(),
                        chrome = {
                            Crossfade(
                                targetState = showSelectionHeader,
                                animationSpec = tween(180),
                                label = "librarySelectionHeader"
                            ) { selectionHeaderVisible ->
                                if (selectionHeaderVisible) {
                                    LibrarySelectionHeaderContent(
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                } else {
                                    MusicScreenHeader(
                                        title = when {
                                            isSearchDestination -> "Search"
                                            selectedLibraryTab == LibraryTab.QUEUE -> "Up Next"
                                            else -> "Library"
                                        },
                                        onBackClick = null,
                                        onSettingsClick = onSettingsClick,
                                        modifier = Modifier.statusBarsPadding(),
                                        batchMetadataAction = if (!isSearchDestination &&
                                            selectedLibraryTab == LibraryTab.SONGS &&
                                            songs.size >= 2
                                        ) {
                                            {
                                                AppShellIconButton(
                                                    onClick = onBatchMetadataClick,
                                                    imageVector = Icons.Filled.EditNote,
                                                    contentDescription =
                                                        "Select tracks to edit metadata"
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        viewModeAction = if (!isSearchDestination &&
                                            selectedLibraryTab.viewCategory() != null
                                        ) {
                                            {
                                                LibraryViewOptionsButton(
                                                    viewMode = selectedViewMode,
                                                    gridColumnCount = selectedGridColumnCount,
                                                    adaptiveGrid = selectedLibraryTab ==
                                                        LibraryTab.PLAYLISTS,
                                                    onClick = {
                                                        isLibraryViewOptionsVisible = true
                                                    }
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        organizeAction =
                                            if (!shouldOfferLibraryOrganize(destination)) null else {
                                                {
                                                    LibraryOrganizeAction(
                                                        songs = songs,
                                                        selectedLibraryTab = selectedLibraryTab,
                                                        selectedArtistName =
                                                            shellState.artistName,
                                                        selectedAlbumKey = shellState.albumKey,
                                                        selectedSongSortState =
                                                            selectedCollectionSortState,
                                                        selectedArtistSortState =
                                                            selectedArtistSortState,
                                                        selectedAlbumSortState =
                                                            selectedAlbumSortState,
                                                        selectedFavoriteSortState =
                                                            selectedFavoriteSortState,
                                                        selectedSongFilterState =
                                                            selectedSongFilterState,
                                                        onSongSortStateChanged = { state ->
                                                            when (selectedLibraryTab) {
                                                                LibraryTab.RATED ->
                                                                    selectedRatedSortState = state
                                                                LibraryTab.RECENTLY_ADDED ->
                                                                    selectedAddedSortState = state
                                                                else ->
                                                                    onSongSortStateChanged(state)
                                                            }
                                                        },
                                                        onArtistSortStateChanged =
                                                            onArtistSortStateChanged,
                                                        onAlbumSortStateChanged =
                                                            onAlbumSortStateChanged,
                                                        onFavoriteSortStateChanged =
                                                            onFavoriteSortStateChanged,
                                                        onSongFilterStateChanged =
                                                            onSongFilterStateChanged,
                                                        songFiltersEnabled = !isSearchDestination,
                                                        ratingFeaturesEnabled =
                                                            !isSearchDestination
                                                    )
                                                }
                                            }
                                    )
                                }
                            }

                            if (mediaAccessState.hasAudioAccess &&
                                !isSearchDestination &&
                                selectedLibraryTab != LibraryTab.QUEUE
                            ) {
                                LibraryBrowseSwitcher(
                                    selectedTab = selectedLibraryTab,
                                    onTabSelected = { tab ->
                                        onOpenLibrary(tab)
                                    },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                if (selectedLibraryTab == LibraryTab.RATED) {
                                    RatedSongFilterRow(
                                        selectedFilter = activeRatedFilter,
                                        quickRateActive = ratingUi.quickRateMode,
                                        onFilterSelected = { selectedRatedFilter = it },
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    ) { collectionContentTopPadding ->
                        val nonSearchContentTopPadding = if (isSearchDestination) {
                            0.dp
                        } else {
                            libraryContentTopPadding(
                                chromeTopPadding = collectionContentTopPadding,
                                visibleTab = selectedLibraryTab
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (isSearchDestination) {
                                        collectionContentTopPadding
                                    } else {
                                        0.dp
                                    }
                                )
                        ) {
                        if (!mediaAccessState.hasAudioAccess) {
                            MediaAccessNotice(
                                state = mediaAccessState,
                                onRequestAudioAccess = onRequestAudioAccess,
                                onRequestArtworkAccess = onRequestArtworkAccess,
                                onOpenAppSettings = onOpenAppSettings,
                                modifier = Modifier
                                    .padding(top = collectionContentTopPadding)
                                    .padding(16.dp)
                            )
                        } else {
                            LibrarySearchControl(
                                selectedLibraryTab = selectedLibraryTab,
                                isSearchVisible = isSearchDestination,
                                searchQuery = searchQuery,
                                onSearchQueryChange = onSearchQueryChange
                            )

                            when {
                                isLibraryLoading -> LibraryLoadingNotice(
                                    modifier = Modifier
                                        .padding(top = nonSearchContentTopPadding)
                                        .padding(16.dp)
                                )
                                libraryErrorMessage != null -> LibraryErrorNotice(
                                    message = libraryErrorMessage,
                                    modifier = Modifier
                                        .padding(top = nonSearchContentTopPadding)
                                        .padding(16.dp)
                                )
                                isSearchDestination -> searchStateHolder.SaveableStateProvider("search-results") {
                                  io.github.rsgarrido.sazanami.ui.library.LibrarySearchContent(
                                    songs = songs,
                                    playlists = playlists,
                                    query = searchQuery,
                                    category = searchCategory,
                                    onCategoryChange = onSearchCategoryChange,
                                    currentSong = currentSong,
                                    recentlyAddedSongIds = recentlyAddedSongIds,
                                    favoriteMembershipKeys = favoriteMembershipKeys,
                                    onSongClick = onSongClick,
                                    onPlaySongsClick = onPlaySongsClick,
                                    onPlayNextSongsClick = { label, tracks -> queueSnackbarActions.playNextSongs(label, tracks) },
                                    onAddSongsToQueueClick = { label, tracks -> queueSnackbarActions.addSongsToQueue(label, tracks) },
                                    onPlayNextClick = { queueSnackbarActions.playNext(it) },
                                    onAddToQueueClick = { queueSnackbarActions.addToQueue(it) },
                                    onToggleFavoriteClick = onToggleFavoriteClick,
                                    onAddToPlaylistClick = onAddToPlaylistClick,
                                    onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                                    onEditSongTagsClick = onEditSongTagsClick,
                                    onAlbumSelected = onAlbumSelected,
                                    onArtistSelected = onArtistSelected,
                                    onPlaylistSelected = onPlaylistClick,
                                    onAddPlaylistToQueueClick = onAddPlaylistToQueueClick,
                                    onExportPlaylistClick = onExportPlaylistClick,
                                    bottomContentPadding = bottomContentPadding,
                                    modifier = Modifier.weight(1f)
                                )
                                }
                                songs.isEmpty() -> EmptyLibraryNotice(
                                    modifier = Modifier
                                        .padding(top = nonSearchContentTopPadding)
                                        .padding(16.dp)
                                )
                                else -> AnimatedContent(
                                    targetState = selectedLibraryTab,
                                    modifier = Modifier.weight(1f),
                                    transitionSpec = {
                                        libraryContentTransitionSpec(
                                            initialTab = initialState,
                                            targetTab = targetState,
                                            initialPresentation = libraryContentPresentation(
                                                viewMode = libraryAppearanceUiState.modeFor(
                                                    initialState
                                                ),
                                                gridColumnCount = libraryAppearanceUiState
                                                    .gridColumnCountFor(initialState),
                                                adaptiveGrid = initialState == LibraryTab.PLAYLISTS
                                            ),
                                            targetPresentation = libraryContentPresentation(
                                                viewMode = libraryAppearanceUiState.modeFor(
                                                    targetState
                                                ),
                                                gridColumnCount = libraryAppearanceUiState
                                                    .gridColumnCountFor(targetState),
                                                adaptiveGrid = targetState == LibraryTab.PLAYLISTS
                                            )
                                        )
                                    },
                                    label = "libraryTabContent"
                                ) { visibleLibraryTab ->
                                    val visibleViewMode = libraryAppearanceUiState.modeFor(
                                        visibleLibraryTab
                                    )
                                    val visibleGridColumnCount = libraryAppearanceUiState
                                        .gridColumnCountFor(visibleLibraryTab)
                                    val visibleContentTopPadding = libraryContentTopPadding(
                                        chromeTopPadding = collectionContentTopPadding,
                                        visibleTab = visibleLibraryTab
                                    )
                                    val usesArtworkDetailHost = visibleLibraryTab ==
                                            LibraryTab.ARTISTS ||
                                            visibleLibraryTab == LibraryTab.ALBUMS ||
                                            visibleLibraryTab == LibraryTab.PLAYLISTS
                                    MusicLibraryContent(
                                        selectedLibraryTab = visibleLibraryTab,
                                    songs = songs,
                                    searchQuery = if (destination == MainDestination.SEARCH) "" else searchQuery,
                                    selectedSongFilterState = if (isSearchDestination) {
                                        LibrarySongFilterState()
                                    } else {
                                        selectedSongFilterState
                                    },
                                    selectedSongSortState = selectedCollectionSortState,
                                    selectedRatedFilter = activeRatedFilter,
                                    selectedArtistSortState = selectedArtistSortState,
                                    selectedAlbumSortState = selectedAlbumSortState,
                                    selectedFavoriteSortState = selectedFavoriteSortState,
                                    viewMode = visibleViewMode,
                                    gridColumnCount = visibleGridColumnCount,
                                    selectedArtistName = shellState.artistName,
                                    selectedAlbumKey = shellState.albumKey,
                                    selectedGenreKey = selectedGenreKey,
                                    selectedPlaylistId = shellState.playlistId,
                                    playlists = playlists,
                                    playlistFolders = playlistFolders,
                                    selectedPlaylistStateId = selectedPlaylistStateId,
                                    selectedPlaylistName = selectedPlaylistName,
                                    selectedPlaylistSongs = selectedPlaylistSongs,
                                    isSelectedPlaylistLoading = isSelectedPlaylistLoading,
                                    currentSong = currentSong,
                                    recentlyAddedSongIds = recentlyAddedSongIds,
                                    favoriteMembershipKeys = favoriteMembershipKeys,
                                    queuedSongs = queuedSongs,
                                    upcomingSongs = upcomingSongs,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onSongClick = onSongClick,
                                    onPlaySongsClick = onPlaySongsClick,
                                    onPlayNextClick = { song ->
                                        queueSnackbarActions.playNext(song)
                                    },
                                    onAddToQueueClick = { song ->
                                        queueSnackbarActions.addToQueue(song)
                                    },
                                    onPlayNextSongsClick = { label, songsToAdd ->
                                        queueSnackbarActions.playNextSongs(label, songsToAdd)
                                    },
                                    onAddSongsToQueueClick = { label, songsToAdd ->
                                        queueSnackbarActions.addSongsToQueue(label, songsToAdd)
                                    },
                                    onToggleFavoriteClick = onToggleFavoriteClick,
                                    onAddToPlaylistClick = onAddToPlaylistClick,
                                    onArtistSelected = onArtistSelected,
                                    onBackFromArtist = onBackFromArtist,
                                    onAlbumSelected = onAlbumSelected,
                                    onBackFromAlbum = onBackFromAlbum,
                                    onGenreSelected = onGenreSelected,
                                    onBackFromGenre = onBackFromGenre,
                                    onBackFromQueue = onBackFromQueue,
                                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                                    onMoveQueueItemUpClick = onMoveQueueItemUpClick,
                                    onMoveQueueItemDownClick = onMoveQueueItemDownClick,
                                    onClearQueueClick = onClearQueueClick,
                                    onCreatePlaylistClick = onCreatePlaylistClick,
                                    onCreatePlaylistFolderClick = onCreatePlaylistFolderClick,
                                    onRenamePlaylistFolderClick = onRenamePlaylistFolderClick,
                                    onDeletePlaylistFolderClick = onDeletePlaylistFolderClick,
                                    onMovePlaylistToFolderClick = onMovePlaylistToFolderClick,
                                    onRenamePlaylistClick = onRenamePlaylistClick,
                                    onPlaylistClick = onPlaylistClick,
                                    onDeletePlaylistClick = onDeletePlaylistClick,
                                    onExportPlaylistClick = onExportPlaylistClick,
                                    onAddPlaylistToQueueClick = onAddPlaylistToQueueClick,
                                    onImportPlaylistClick = onImportPlaylistClick,
                                    onChangePlaylistArtwork = onChangePlaylistArtwork,
                                    onResetPlaylistArtwork = onResetPlaylistArtwork,
                                    onBackFromPlaylist = onBackFromPlaylist,
                                    onRemovePlaylistSongClick = onRemovePlaylistSongClick,
                                    onReorderPlaylistSongs = onReorderPlaylistSongs,
                                    onAddSongsToCurrentPlaylistClick =
                                        onAddSongsToCurrentPlaylistClick,
                                    onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                                    onEditAlbumMetadataClick = onEditAlbumMetadataClick,
                                    onEditSongTagsClick = onEditSongTagsClick,
                                    onClearSongFilters = {
                                        onSongFilterStateChanged(selectedSongFilterState.clear())
                                    },
                                    ratingFeaturesEnabled = !isSearchDestination,
                                    recentlyPlayedSongs = recentlyPlayedSongs,
                                    recentlyAddedSongs = recentlyAddedLibrarySongs,
                                    mostPlayedSongs = mostPlayedSongs,
                                    collectionContentTopPadding =
                                        visibleContentTopPadding,
                                    bottomContentPadding = bottomContentPadding,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (usesArtworkDetailHost) {
                                                    Modifier
                                                } else {
                                                    Modifier.padding(
                                                        top = visibleContentTopPadding
                                                    )
                                                }
                                            )
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }

            if (isLibraryViewOptionsVisible && selectedLibraryTab.viewCategory() != null) {
                LibraryViewOptionsSheet(
                    viewMode = currentLibraryViewMode,
                    gridColumnCount = currentGridColumnCount,
                    adaptiveGrid = selectedLibraryTab == LibraryTab.PLAYLISTS,
                    onOptionSelected = { option ->
                        selectedLibraryTab.viewCategory()?.let { category ->
                            onLibraryViewOptionSelected(category, option)
                        }
                        isLibraryViewOptionsVisible = false
                    },
                    onDismissRequest = {
                        isLibraryViewOptionsVisible = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryDetailChromeLayout(
    chromeVisible: Boolean,
    composeChrome: Boolean,
    modifier: Modifier = Modifier,
    chrome: @Composable () -> Unit,
    content: @Composable (Dp) -> Unit
) {
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val chromeTransitionProgress by animateFloatAsState(
        targetValue = if (chromeVisible) 0f else 1f,
        animationSpec = tween(
            durationMillis = LibraryDetailChromeDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "libraryDetailChromeProgress"
    )
    val chromeHeight = with(LocalDensity.current) { chromeHeightPx.toDp() }

    Box(modifier = modifier) {
        // Collection children always receive the measured final chrome inset. Detail children
        // consume no inset, so both sides keep stable bounds throughout their shared transition.
        content(if (composeChrome) chromeHeight else 0.dp)

        if (composeChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { measuredSize ->
                        if (chromeHeightPx != measuredSize.height) {
                            chromeHeightPx = measuredSize.height
                        }
                    }
                    .graphicsLayer {
                        translationY = -chromeHeightPx * chromeTransitionProgress
                        alpha = 1f - chromeTransitionProgress
                    }
            ) {
                chrome()
            }
        }
    }
}

internal fun shouldChoreographLibraryDetailChrome(
    destination: MainDestination,
    selectedLibraryTab: LibraryTab,
    selectedArtistName: String?,
    selectedAlbumKey: String?,
    selectedPlaylistId: Long?,
    artistSourceScope: LibrarySharedArtworkSourceScope,
    albumSourceScope: LibrarySharedArtworkSourceScope,
    playlistSourceScope: LibrarySharedArtworkSourceScope
): Boolean {
    if (destination != MainDestination.LIBRARY) return false

    return when (selectedLibraryTab) {
        LibraryTab.ARTISTS -> selectedArtistName != null &&
                artistSourceScope == LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
        LibraryTab.ALBUMS -> selectedAlbumKey != null &&
                albumSourceScope == LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
        LibraryTab.PLAYLISTS -> selectedPlaylistId != null &&
                playlistSourceScope == LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION
        else -> false
    }
}

internal fun shouldOfferLibraryOrganize(destination: MainDestination): Boolean =
    destination != MainDestination.SEARCH

private data class MusicShellTransitionState(
    val destination: MainDestination,
    val artistName: String?,
    val albumKey: String?,
    val playlistId: Long?,
    val albumSharedArtworkSourceScope: LibrarySharedArtworkSourceScope,
    val artistSharedArtworkSourceScope: LibrarySharedArtworkSourceScope,
    val playlistSharedArtworkSourceScope: LibrarySharedArtworkSourceScope
)

internal fun shouldShowLibrarySelectionHeader(
    selectionActive: Boolean,
    isLibraryDetail: Boolean,
    hasAudioAccess: Boolean,
    bindingMatchesSelection: Boolean
): Boolean = selectionActive &&
    !isLibraryDetail &&
    hasAudioAccess &&
    bindingMatchesSelection
