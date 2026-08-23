package com.example.cdplaya.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.LibraryFolder
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.AnalyticsRangePreset
import com.example.cdplaya.data.ListeningRankingCategory
import com.example.cdplaya.data.ListeningTrendMetric
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.player.RepeatMode
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.audio.AudioOutputUiState
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.ui.equalizer.EqualizerScreen
import com.example.cdplaya.ui.equalizer.EqualizerScreenState
import com.example.cdplaya.ui.equalizer.EqualizerUiActions
import com.example.cdplaya.ui.home.HomeScreen
import com.example.cdplaya.ui.library.FolderSelectionScreen
import com.example.cdplaya.ui.library.LibraryBrowseSwitcher
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.ui.library.LibraryTab
import com.example.cdplaya.ui.library.LibraryViewMode
import com.example.cdplaya.ui.library.LibraryGridColumns
import com.example.cdplaya.ui.library.LibraryViewOptionsButton
import com.example.cdplaya.ui.library.LibraryViewOptionsSheet
import com.example.cdplaya.ui.library.MusicLibraryContent
import com.example.cdplaya.ui.library.viewCategory
import com.example.cdplaya.ui.navigation.MainDestination
import com.example.cdplaya.ui.queue.QueueSnackbarActions
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenField
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.settings.SettingsScreen
import com.example.cdplaya.ui.settings.DiagnosticsScreen
import com.example.cdplaya.ui.settings.ListeningHistoryImportScreen
import com.example.cdplaya.ui.settings.SpotifyImportUiActions
import com.example.cdplaya.ui.settings.ListeningHistoryReconciliationScreen
import com.example.cdplaya.ui.settings.ListeningHistoryReconciliationUiActions
import com.example.cdplaya.controller.SpotifyImportUiState
import com.example.cdplaya.controller.ListeningHistoryReconciliationUiState
import com.example.cdplaya.ui.state.PlaybackProgress
import com.example.cdplaya.ui.state.PlaybackProgressUiState
import com.example.cdplaya.ui.state.LibraryAppearanceUiState
import com.example.cdplaya.ui.state.LibraryRefreshSummary
import com.example.cdplaya.ui.state.ListeningAnalyticsUiState
import com.example.cdplaya.ui.statistics.StatisticsScreen
import com.example.cdplaya.ui.state.gridColumnCountFor
import com.example.cdplaya.ui.state.modeFor
import com.example.cdplaya.ui.library.LibraryViewCategory
import com.example.cdplaya.ui.library.LibraryViewOption
import kotlinx.coroutines.flow.StateFlow
import com.example.cdplaya.mediaaccess.MediaAccessState
import java.time.LocalDate

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
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    mainDestination: MainDestination,
    selectedLibraryTab: LibraryTab,
    selectedArtistName: String?,
    selectedAlbumFolderPath: String?,
    selectedPlaylistId: Long?,
    searchQuery: String,
    selectedSongSortOption: LibrarySortOption,
    selectedArtistSortOption: LibrarySortOption,
    selectedAlbumSortOption: LibrarySortOption,
    selectedFavoriteSortOption: LibrarySortOption,
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
    onSongSortOptionSelected: (LibrarySortOption) -> Unit,
    onArtistSortOptionSelected: (LibrarySortOption) -> Unit,
    onAlbumSortOptionSelected: (LibrarySortOption) -> Unit,
    onFavoriteSortOptionSelected: (LibrarySortOption) -> Unit,
    onExpandPlayerClick: () -> Unit,
    onMiniPlayerUpNextClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, Boolean) -> Unit,
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
    onBackFromQueue: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onBackFromPlaylist: () -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onMovePlaylistSongUpClick: (PlaylistSong) -> Unit,
    onMovePlaylistSongDownClick: (PlaylistSong) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onSleepTimerClick: () -> Unit,
    recentlyPlayedSongs: List<Song>,
    mostPlayedSongs: List<Song>,
    selectedPlayerTheme: PlayerTheme,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    onPlayerThemeSelected: (PlayerTheme) -> Unit,
    onUpdatePlayerThemeTokenOverride: (PlayerTheme, PlayerThemeTokenField, Color) -> Unit,
    onResetPlayerThemeTokenOverrides: (PlayerTheme) -> Unit,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    onModernArtworkTransitionStyleSelected: (ModernArtworkTransitionStyle) -> Unit,
    selectedModernSeekbarStyle: ModernSeekbarStyle,
    onModernSeekbarStyleSelected: (ModernSeekbarStyle) -> Unit,
    selectedReplayGainMode: ReplayGainMode,
    onReplayGainModeSelected: (ReplayGainMode) -> Unit,
    selectedAudioOffloadPreference: AudioOffloadPreference,
    onAudioOffloadPreferenceSelected: (AudioOffloadPreference) -> Unit,
    audioOutputUiState: AudioOutputUiState,
    equalizerScreenState: EqualizerScreenState,
    equalizerActions: EqualizerUiActions,
    libraryAppearanceUiState: LibraryAppearanceUiState,
    onLibraryViewOptionSelected: (LibraryViewCategory, LibraryViewOption) -> Unit,
    settingsScrollState: ScrollState = rememberScrollState(),
    statisticsListState: LazyListState,
    bottomContentPadding: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    var isLibraryViewOptionsVisible by rememberSaveable {
        mutableStateOf(false)
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
                selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                onPlayerThemeSelected = onPlayerThemeSelected,
                onUpdatePlayerThemeTokenOverride = onUpdatePlayerThemeTokenOverride,
                onResetPlayerThemeTokenOverrides = onResetPlayerThemeTokenOverrides,
                selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                onModernArtworkTransitionStyleSelected = onModernArtworkTransitionStyleSelected,
                selectedModernSeekbarStyle = selectedModernSeekbarStyle,
                onModernSeekbarStyleSelected = onModernSeekbarStyleSelected,
                selectedReplayGainMode = selectedReplayGainMode,
                onReplayGainModeSelected = onReplayGainModeSelected,
                selectedAudioOffloadPreference = selectedAudioOffloadPreference,
                onAudioOffloadPreferenceSelected = onAudioOffloadPreferenceSelected,
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

            AnimatedContent(
                targetState = mainDestination,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1

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
                },
                label = "appShellDestination"
            ) { destination ->
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
                            onOpenLibrary(LibraryTab.ALBUMS)
                            onAlbumSelected(albumKey)
                        },
                        onPinnedArtistClick = { artistName ->
                            onOpenLibrary(LibraryTab.ARTISTS)
                            onArtistSelected(artistName)
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
                        bottomContentPadding = bottomContentPadding
                    )
                } else {
                    val isSearchDestination = destination == MainDestination.SEARCH
                    val isArtistOrAlbumDetail = selectedArtistName != null ||
                            selectedAlbumFolderPath != null
                    val isLibraryDetail = isArtistOrAlbumDetail ||
                            selectedPlaylistId != null
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

                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .animateContentSize()
                    ) {
                        if (!isArtistOrAlbumDetail || !mediaAccessState.hasAudioAccess) {
                            MusicScreenHeader(
                                title = when {
                                    isSearchDestination -> "Search"
                                    selectedLibraryTab == LibraryTab.QUEUE -> "Up Next"
                                    else -> "Library"
                                },
                                onBackClick = null,
                                onSettingsClick = onSettingsClick,
                                modifier = Modifier.statusBarsPadding(),
                                viewModeAction = if (!isSearchDestination &&
                                    !isLibraryDetail &&
                                    selectedLibraryTab.viewCategory() != null
                                ) {
                                    {
                                        LibraryViewOptionsButton(
                                            viewMode = selectedViewMode,
                                            gridColumnCount = selectedGridColumnCount,
                                            onClick = {
                                                isLibraryViewOptionsVisible = true
                                            }
                                        )
                                    }
                                } else {
                                    null
                                },
                                sortAction = {
                                    LibrarySortAction(
                                        selectedLibraryTab = selectedLibraryTab,
                                        selectedArtistName = selectedArtistName,
                                        selectedAlbumFolderPath = selectedAlbumFolderPath,
                                        selectedSongSortOption = selectedSongSortOption,
                                        selectedArtistSortOption = selectedArtistSortOption,
                                        selectedAlbumSortOption = selectedAlbumSortOption,
                                        selectedFavoriteSortOption = selectedFavoriteSortOption,
                                        onSongSortOptionSelected = onSongSortOptionSelected,
                                        onArtistSortOptionSelected = onArtistSortOptionSelected,
                                        onAlbumSortOptionSelected = onAlbumSortOptionSelected,
                                        onFavoriteSortOptionSelected = onFavoriteSortOptionSelected,
                                        ratingFeaturesEnabled = !isSearchDestination
                                    )
                                }
                            )
                        }

                        if (!mediaAccessState.hasAudioAccess) {
                            MediaAccessNotice(
                                state = mediaAccessState,
                                onRequestAudioAccess = onRequestAudioAccess,
                                onRequestArtworkAccess = onRequestArtworkAccess,
                                onOpenAppSettings = onOpenAppSettings,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            if (!isSearchDestination &&
                                !isLibraryDetail &&
                                selectedLibraryTab != LibraryTab.QUEUE
                            ) {
                                LibraryBrowseSwitcher(
                                    selectedTab = selectedLibraryTab,
                                    onTabSelected = { tab ->
                                        onOpenLibrary(tab)
                                    },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            LibrarySearchControl(
                                selectedLibraryTab = selectedLibraryTab,
                                isSearchVisible = isSearchDestination,
                                searchQuery = searchQuery,
                                onSearchQueryChange = onSearchQueryChange
                            )

                            when {
                                isLibraryLoading -> LibraryLoadingNotice(
                                    modifier = Modifier.padding(16.dp)
                                )
                                libraryErrorMessage != null -> LibraryErrorNotice(
                                    message = libraryErrorMessage,
                                    modifier = Modifier.padding(16.dp)
                                )
                                songs.isEmpty() -> EmptyLibraryNotice(
                                    modifier = Modifier.padding(16.dp)
                                )
                                else -> MusicLibraryContent(
                                    selectedLibraryTab = selectedLibraryTab,
                                    songs = songs,
                                    searchQuery = searchQuery,
                                    selectedSongSortOption = selectedSongSortOption,
                                    selectedArtistSortOption = selectedArtistSortOption,
                                    selectedAlbumSortOption = selectedAlbumSortOption,
                                    selectedFavoriteSortOption = selectedFavoriteSortOption,
                                    viewMode = selectedViewMode,
                                    gridColumnCount = selectedGridColumnCount,
                                    selectedArtistName = selectedArtistName,
                                    selectedAlbumFolderPath = selectedAlbumFolderPath,
                                    selectedPlaylistId = selectedPlaylistId,
                                    playlists = playlists,
                                    selectedPlaylistName = selectedPlaylistName,
                                    selectedPlaylistSongs = selectedPlaylistSongs,
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
                                    onBackFromQueue = onBackFromQueue,
                                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                                    onMoveQueueItemUpClick = onMoveQueueItemUpClick,
                                    onMoveQueueItemDownClick = onMoveQueueItemDownClick,
                                    onClearQueueClick = onClearQueueClick,
                                    onCreatePlaylistClick = onCreatePlaylistClick,
                                    onRenamePlaylistClick = onRenamePlaylistClick,
                                    onPlaylistClick = onPlaylistClick,
                                    onDeletePlaylistClick = onDeletePlaylistClick,
                                    onExportPlaylistClick = onExportPlaylistClick,
                                    onImportPlaylistClick = onImportPlaylistClick,
                                    onBackFromPlaylist = onBackFromPlaylist,
                                    onRemovePlaylistSongClick = onRemovePlaylistSongClick,
                                    onMovePlaylistSongUpClick = onMovePlaylistSongUpClick,
                                    onMovePlaylistSongDownClick = onMovePlaylistSongDownClick,
                                    onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
                                    onEditSongTagsClick = onEditSongTagsClick,
                                    ratingFeaturesEnabled = !isSearchDestination,
                                    recentlyPlayedSongs = recentlyPlayedSongs,
                                    recentlyAddedSongs = recentlyAddedLibrarySongs,
                                    mostPlayedSongs = mostPlayedSongs,
                                    bottomContentPadding = bottomContentPadding,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (isLibraryViewOptionsVisible && selectedLibraryTab.viewCategory() != null) {
                LibraryViewOptionsSheet(
                    viewMode = currentLibraryViewMode,
                    gridColumnCount = currentGridColumnCount,
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
