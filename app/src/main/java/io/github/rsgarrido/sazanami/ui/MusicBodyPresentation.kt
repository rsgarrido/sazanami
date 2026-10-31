package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.controller.SpotifyImportUiState
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.FolderBrowseIndex
import io.github.rsgarrido.sazanami.data.FolderId
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.LibraryFolder
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.preferences.AppAppearance
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessState
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerScreenState
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerUiActions
import io.github.rsgarrido.sazanami.ui.library.FolderBrowseScrollStateHolder
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.LibrarySelectionUiEnvironment
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryViewOption
import io.github.rsgarrido.sazanami.ui.library.folderBrowseBackDestination
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenField
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.playlist.PlaylistSnackbarActions
import io.github.rsgarrido.sazanami.ui.queue.QueueSnackbarActions
import io.github.rsgarrido.sazanami.ui.settings.ListeningHistoryReconciliationUiActions
import io.github.rsgarrido.sazanami.ui.settings.SpotifyImportUiActions
import io.github.rsgarrido.sazanami.ui.state.LibraryAppearanceUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryRefreshSummary
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/**
 * Prepare body-only arguments and callbacks before PlayerMorphHost. The returned content is invoked
 * at the existing body position with the final animated padding. Supplied state and scroll owners
 * stay with MusicScreen/navigation; this factory neither registers effects nor collects playback.
 */
@Composable
internal fun prepareMusicBodyPresentation(
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
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    libraryFolders: List<LibraryFolder>,
    folderSelectionMode: FolderSelectionMode,
    selectedLibraryFolders: Set<String>,
    excludedLibraryFolders: Set<String>,
    onScanLibraryClick: () -> Unit,
    onLibraryFolderToggle: (String) -> Unit,
    onSelectAllLibraryFolders: () -> Unit,
    onClearSelectedLibraryFolders: () -> Unit,
    favoriteMembershipKeys: Set<String>,
    unresolvedFavoriteCount: Int,
    unresolvedPlaylistRowCount: Int,
    unresolvedListeningHistoryCount: Int,
    onToggleFavoriteClick: (Song) -> Unit,
    playlists: List<Playlist>,
    playlistFolders: List<PlaylistFolder>,
    selectedPlaylistStateId: Long?,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    isSelectedPlaylistLoading: Boolean,
    onCreatePlaylistFolderClick: (String) -> Unit,
    onRenamePlaylistFolderClick: (PlaylistFolder, String) -> Unit,
    onDeletePlaylistFolderClick: (PlaylistFolder) -> Unit,
    onMovePlaylistToFolderClick: (Playlist, Long?) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangePlaylistArtwork: (Playlist, Uri) -> Unit,
    onResetPlaylistArtwork: (Playlist) -> Unit,
    onExportBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onReorderPlaylistSongs: (Long, List<Long>) -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    recentlyPlayedSongs: List<Song>,
    recentlyAddedLibrarySongs: List<Song>,
    selectedPlayerTheme: PlayerTheme,
    selectedAppFont: AppFont,
    onAppFontSelected: (AppFont) -> Unit,
    selectedAppAppearance: AppAppearance,
    onAppAppearanceSelected: (AppAppearance) -> Unit,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    onPlayerThemeSelected: (PlayerTheme) -> Unit,
    selectedTabletLandscapeTheme: TabletLandscapeTheme = TabletLandscapeTheme.NORMAL_PLAYER,
    onTabletLandscapeThemeSelected: (TabletLandscapeTheme) -> Unit = {},
    pocketAdvanceAppearance: PocketAdvanceAppearance = PocketAdvanceAppearance.Default,
    pocketHorizonAppearance: PocketHorizonAppearance = PocketHorizonAppearance.Default,
    onPocketAdvanceConsoleFinishSelected: (PocketAdvanceConsoleFinish) -> Unit = {},
    onPocketHorizonConsoleFinishSelected: (PocketHorizonConsoleFinish) -> Unit = {},
    onPocketAdvanceLcdPaletteSelected: (PocketAdvanceLcdPalette) -> Unit = {},
    onPocketHorizonLcdPaletteSelected: (PocketHorizonLcdPalette) -> Unit = {},
    onPocketAdvanceControlsFinishSelected: (PocketAdvanceControlsFinish) -> Unit = {},
    onPocketHorizonWaveTreatmentSelected: (PocketHorizonWaveTreatment) -> Unit = {},
    onResetPocketAdvanceAppearance: () -> Unit = {},
    onResetPocketHorizonAppearance: () -> Unit = {},
    onUpdatePlayerThemeTokenOverride: (PlayerTheme, PlayerThemeTokenField, Color) -> Unit,
    onResetPlayerThemeTokenOverrides: (PlayerTheme) -> Unit,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    onModernArtworkTransitionStyleSelected: (ModernArtworkTransitionStyle) -> Unit,
    selectedModernPlayerAppearance: ModernPlayerAppearance,
    activeModernAppearanceChoice: ModernAppearanceChoice,
    selectedModernLandscapeLayout: ModernLandscapeLayout = ModernLandscapeLayout.BALANCED,
    onModernLandscapeLayoutSelected: (ModernLandscapeLayout) -> Unit = {},
    onModernAppearanceChoiceSelected: (ModernAppearanceChoice) -> Unit,
    onModernPlayerAppearanceEdited: ((ModernPlayerAppearance) -> ModernPlayerAppearance) -> Unit,
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
    mostPlayedSongs: List<Song>,
    listeningAnalyticsUiState: ListeningAnalyticsUiState,
    showNotCountedPlays: Boolean,
    onShowNotCountedPlaysChanged: (Boolean) -> Unit,
    onListeningAnalyticsPresetSelected: (AnalyticsRangePreset) -> Unit,
    onListeningAnalyticsCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    onRetryListeningAnalytics: () -> Unit,
    onListeningAnalyticsTrendMetricSelected: (ListeningTrendMetric) -> Unit,
    onListeningAnalyticsRankingCategorySelected: (ListeningRankingCategory) -> Unit,
    spotifyImportUiState: SpotifyImportUiState,
    reconciliationUiState: ListeningHistoryReconciliationUiState,
    reconciliationActions: ListeningHistoryReconciliationUiActions,
    spotifyImportActions: SpotifyImportUiActions,
    navigationState: MusicNavigationState,
    librarySelectionUi: LibrarySelectionUiEnvironment,
    isPlayerExpanded: Boolean,
    folderBrowseIndex: FolderBrowseIndex,
    folderBrowseScrollStateHolder: FolderBrowseScrollStateHolder,
    resolvedFolderId: FolderId?,
    recentlyAddedSongIds: Set<Long>,
    settingsHelpNavigation: SettingsHelpNavigation,
    settingsScrollState: ScrollState,
    homeListState: LazyListState,
    statisticsListState: LazyListState,
    queueSnackbarActions: QueueSnackbarActions,
    playlistSnackbarActions: PlaylistSnackbarActions,
    addPlaylistToQueue: (Playlist) -> Unit,
    onCloseSettings: () -> Unit,
    clearPlaylistSelection: (Boolean) -> Unit,
    onRecordPlaybackLaunchContext: () -> Unit,
    onExpandPlayer: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowAddToPlaylist: (Song) -> Unit,
    onShowBulkAddToPlaylist: (List<Song>) -> Unit,
    onShowCreatePlaylist: (Long?) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    onEditAlbumMetadataClick: (LibraryAlbumGroup) -> Unit,
    onBatchMetadataClick: () -> Unit,
    folderScreenVisible: MutableState<Boolean>,
    settingsScreenVisible: MutableState<Boolean>,
    tipsHelpScreenVisible: MutableState<Boolean>,
    aboutScreenVisible: MutableState<Boolean>,
    diagnosticsScreenVisible: MutableState<Boolean>,
    equalizerScreenVisible: MutableState<Boolean>,
    statisticsScreenVisible: MutableState<Boolean>,
    listeningHistoryImportVisible: MutableState<Boolean>,
    listeningHistoryReconciliationVisible: MutableState<Boolean>
): @Composable (Dp) -> Unit {
    return { bottomContentPadding ->
        var mainDestination by navigationState.mainDestination
        var selectedLibraryTab by navigationState.selectedLibraryTab
        var selectedArtistName by navigationState.selectedArtistName
        var selectedAlbumKey by navigationState.selectedAlbumKey
        var selectedGenreKey by navigationState.selectedGenreKey
        var selectedPlaylistId by navigationState.selectedPlaylistId
        var selectedFolderId by navigationState.selectedFolderId
        var searchQuery by navigationState.searchQuery
        var selectedSongFilterState by navigationState.selectedSongFilterState
        var selectedSongSortState by navigationState.selectedSongSortState
        var selectedArtistSortState by navigationState.selectedArtistSortState
        var selectedAlbumSortState by navigationState.selectedAlbumSortState
        var selectedFavoriteSortState by navigationState.selectedFavoriteSortState
        var isFolderScreenVisible by folderScreenVisible
        var isSettingsScreenVisible by settingsScreenVisible
        var isTipsHelpScreenVisible by tipsHelpScreenVisible
        var isAboutScreenVisible by aboutScreenVisible
        var isDiagnosticsScreenVisible by diagnosticsScreenVisible
        var isEqualizerScreenVisible by equalizerScreenVisible
        var isStatisticsScreenVisible by statisticsScreenVisible
        var isListeningHistoryImportVisible by listeningHistoryImportVisible
        var isListeningHistoryReconciliationVisible by listeningHistoryReconciliationVisible
        MusicScreenBody(
            songs = songs,
            mediaAccessState = mediaAccessState,
            isLibraryLoading = isLibraryLoading,
            isLibraryRefreshing = isLibraryRefreshing,
            lastLibraryRefreshSummary = lastLibraryRefreshSummary,
            libraryErrorMessage = libraryErrorMessage,
            onRequestAudioAccess = onRequestAudioAccess,
            onRequestArtworkAccess = onRequestArtworkAccess,
            onOpenAppSettings = onOpenAppSettings,
            currentSong = currentSong,
            isPlayerConnected = isPlayerConnected,
            previousHistoryCount = previousHistoryCount,
            forwardHistoryCount = forwardHistoryCount,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            playbackProgressUiState = playbackProgressUiState,
            queuedSongs = queuedSongs,
            upcomingSongs = upcomingSongs,
            libraryFolders = libraryFolders,
            folderSelectionMode = folderSelectionMode,
            selectedLibraryFolders = selectedLibraryFolders,
            excludedLibraryFolders = excludedLibraryFolders,
            favoriteMembershipKeys = favoriteMembershipKeys,
            unresolvedFavoriteCount = unresolvedFavoriteCount,
            unresolvedPlaylistRowCount = unresolvedPlaylistRowCount,
            unresolvedListeningHistoryCount = unresolvedListeningHistoryCount,
            playlists = playlists,
            playlistFolders = playlistFolders,
            selectedPlaylistStateId = selectedPlaylistStateId,
            selectedPlaylistName = selectedPlaylistName,
            selectedPlaylistSongs = selectedPlaylistSongs,
            isSelectedPlaylistLoading = isSelectedPlaylistLoading,
            mainDestination = mainDestination,
            selectedLibraryTab = selectedLibraryTab,
            folderBrowseIndex = folderBrowseIndex,
            folderBrowseScrollStateHolder = folderBrowseScrollStateHolder,
            selectedFolderId = resolvedFolderId,
            selectedArtistName = selectedArtistName,
            selectedAlbumKey = selectedAlbumKey,
            selectedGenreKey = selectedGenreKey,
            selectedPlaylistId = selectedPlaylistId,
            albumSharedArtworkSourceScope =
                navigationState.albumDetailOrigin.value.sharedArtworkSourceScope(),
            artistSharedArtworkSourceScope =
                navigationState.artistDetailOrigin.value.sharedArtworkSourceScope(),
            playlistSharedArtworkSourceScope =
                navigationState.playlistDetailOrigin.value.sharedArtworkSourceScope(),
            searchQuery = searchQuery,
            searchCategory = navigationState.searchCategory.value,
            onSearchCategoryChange = { navigationState.searchCategory.value = it },
            selectedSongFilterState = selectedSongFilterState,
            selectedSongSortState = selectedSongSortState,
            selectedArtistSortState = selectedArtistSortState,
            selectedAlbumSortState = selectedAlbumSortState,
            selectedFavoriteSortState = selectedFavoriteSortState,
            recentlyAddedSongIds = recentlyAddedSongIds,
            isPlayerExpanded = isPlayerExpanded,
            isFolderScreenVisible = isFolderScreenVisible,
            isSettingsScreenVisible = isSettingsScreenVisible,
            isTipsHelpScreenVisible = isTipsHelpScreenVisible,
            isAboutScreenVisible = isAboutScreenVisible,
            settingsHelpNavigation = settingsHelpNavigation,
            isDiagnosticsScreenVisible = isDiagnosticsScreenVisible,
            isEqualizerScreenVisible =
                isEqualizerScreenVisible,
            isStatisticsScreenVisible = isStatisticsScreenVisible,
            isListeningHistoryImportVisible = isListeningHistoryImportVisible,
            isListeningHistoryReconciliationVisible =
                isListeningHistoryReconciliationVisible,
            spotifyImportUiState = spotifyImportUiState,
            reconciliationUiState = reconciliationUiState,
            reconciliationActions = reconciliationActions.copy(
                onBack = {
                    isListeningHistoryReconciliationVisible = false
                    isSettingsScreenVisible = true
                }
            ),
            spotifyImportActions = spotifyImportActions.copy(
                onDone = {
                    spotifyImportActions.onDone()
                    isListeningHistoryImportVisible = false
                    isSettingsScreenVisible = true
                },
                onBack = {
                    isListeningHistoryImportVisible = false
                    isSettingsScreenVisible = true
                }
            ),
            listeningAnalyticsUiState = listeningAnalyticsUiState,
            showNotCountedPlays = showNotCountedPlays,
            onShowNotCountedPlaysChanged = onShowNotCountedPlaysChanged,
            onStatisticsClick = { isStatisticsScreenVisible = true },
            onStatisticsBackClick = { isStatisticsScreenVisible = false },
            onListeningAnalyticsPresetSelected = onListeningAnalyticsPresetSelected,
            onListeningAnalyticsCustomRangeSelected =
                onListeningAnalyticsCustomRangeSelected,
            onRetryListeningAnalytics = onRetryListeningAnalytics,
            onListeningAnalyticsTrendMetricSelected =
                onListeningAnalyticsTrendMetricSelected,
            onListeningAnalyticsRankingCategorySelected =
                onListeningAnalyticsRankingCategorySelected,
            statisticsListState = statisticsListState,
            homeListState = homeListState,
            queueSnackbarActions = queueSnackbarActions,
            onSettingsClick = {
                librarySelectionUi.onClear()
                isSettingsScreenVisible = true
            },
            onOpenLibrary = { tab ->
                if (librarySelectionUi.state.entity != tab.selectionEntity()) {
                    librarySelectionUi.onClear()
                }
                selectedLibraryTab = tab
                if (tab != LibraryTab.FOLDERS) navigationState.clearFolder()
                navigationState.clearArtist()
                navigationState.clearAlbum()
                selectedGenreKey = null
                clearPlaylistSelection(false)
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            },
            onPinnedAlbumSelected = { albumKey ->
                librarySelectionUi.onClear()
                navigationState.clearArtist()
                selectedGenreKey = null
                clearPlaylistSelection(false)
                searchQuery = ""
                navigationState.openPinnedAlbum(albumKey)
            },
            onPinnedArtistSelected = { artistName ->
                librarySelectionUi.onClear()
                navigationState.clearAlbum()
                selectedGenreKey = null
                clearPlaylistSelection(false)
                searchQuery = ""
                navigationState.openPinnedArtist(artistName)
            },
            onPinnedPlaylistSelected = { playlist ->
                librarySelectionUi.onClear()
                navigationState.clearArtist()
                navigationState.clearAlbum()
                selectedGenreKey = null
                clearPlaylistSelection(false)
                searchQuery = ""
                navigationState.openPinnedPlaylist(playlist.playlistId)
                onPlaylistSelected(playlist)
            },
            onFolderBackClick = {
                isFolderScreenVisible = false
                isSettingsScreenVisible = true
            },
            onSettingsBackClick = {
                onCloseSettings()
            },
            onDiagnosticsClick = {
                isSettingsScreenVisible = false
                isDiagnosticsScreenVisible = true
            },
            onListeningHistoryImportClick = {
                spotifyImportActions.onEnter()
                isSettingsScreenVisible = false
                isListeningHistoryImportVisible = true
            },
            onListeningHistoryReconciliationClick = {
                reconciliationActions.onEnter()
                isSettingsScreenVisible = false
                isListeningHistoryReconciliationVisible = true
            },
            onDiagnosticsBackClick = {
                isDiagnosticsScreenVisible = false
                isSettingsScreenVisible = true
            },
            onEqualizerClick = {
                isSettingsScreenVisible = false
                isEqualizerScreenVisible = true
            },
            onEqualizerBackClick = {
                equalizerActions.onBack()
                isEqualizerScreenVisible = false
                isSettingsScreenVisible = true
            },
            onLibraryFoldersClick = {
                isSettingsScreenVisible = false
                isFolderScreenVisible = true
            },
            onExportBackupClick = onExportBackupClick,
            onRestoreBackupClick = onRestoreBackupClick,
            onScanLibraryClick = onScanLibraryClick,
            onLibraryFolderToggle = onLibraryFolderToggle,
            onSelectAllLibraryFolders = onSelectAllLibraryFolders,
            onClearSelectedLibraryFolders = onClearSelectedLibraryFolders,
            onSearchQueryChange = { query ->
                searchQuery = query
            },
            onSongFilterStateChanged = { state ->
                selectedSongFilterState = state
            },
            onSongSortStateChanged = { state ->
                selectedSongSortState = state
            },
            onArtistSortStateChanged = { state ->
                selectedArtistSortState = state
            },
            onAlbumSortStateChanged = { state ->
                selectedAlbumSortState = state
            },
            onFavoriteSortStateChanged = { state ->
                selectedFavoriteSortState = state
            },
            onExpandPlayerClick = {
                librarySelectionUi.onClear()
                onExpandPlayer()
            },
            onMiniPlayerUpNextClick = {
                librarySelectionUi.onClear()
                selectedLibraryTab = LibraryTab.QUEUE
                navigationState.clearArtist()
                navigationState.clearAlbum()
                selectedGenreKey = null
                clearPlaylistSelection(false)
                mainDestination = MainDestination.LIBRARY
            },
            onSongClick = { song, playbackContext ->
                onRecordPlaybackLaunchContext()
                onSongClick(song, playbackContext)
            },
            onPlaySongsClick = { playbackContext, shuffleMode ->
                onRecordPlaybackLaunchContext()
                onPlaySongsClick(playbackContext, shuffleMode)
            },
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onSeekChange = onSeekChange,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
            onToggleFavoriteClick = onToggleFavoriteClick,
            onAddToPlaylistClick = onShowAddToPlaylist,
            onAddSongsToPlaylistClick = onShowBulkAddToPlaylist,
            onFolderSelected = { folderId ->
                librarySelectionUi.onClear()
                navigationState.openFolder(folderId)
            },
            onBackFromFolder = {
                librarySelectionUi.onClear()
                selectedFolderId = folderBrowseBackDestination(
                    folderBrowseIndex,
                    selectedFolderId
                )
            },
            onArtistSelected = { artistName ->
                librarySelectionUi.onClear()
                navigationState.openArtist(artistName)
            },
            onBackFromArtist = {
                navigationState.closeArtist()
            },
            onAlbumSelected = { albumKey ->
                librarySelectionUi.onClear()
                navigationState.openAlbum(albumKey)
            },
            onBackFromAlbum = {
                navigationState.closeAlbum()
            },
            onGenreSelected = { genreKey ->
                librarySelectionUi.onClear()
                selectedGenreKey = genreKey
            },
            onBackFromGenre = {
                selectedGenreKey = null
            },
            onBackFromQueue = {
                selectedLibraryTab = LibraryTab.SONGS
                mainDestination = MainDestination.LIBRARY
            },
            onRemoveFromQueueClick = onRemoveFromQueueClick,
            onMoveQueueItemUpClick = onMoveQueueItemUpClick,
            onMoveQueueItemDownClick = onMoveQueueItemDownClick,
            onClearQueueClick = onClearQueueClick,
            onCreatePlaylistClick = onShowCreatePlaylist,
            onCreatePlaylistFolderClick = onCreatePlaylistFolderClick,
            onRenamePlaylistFolderClick = onRenamePlaylistFolderClick,
            onDeletePlaylistFolderClick = onDeletePlaylistFolderClick,
            onMovePlaylistToFolderClick = onMovePlaylistToFolderClick,
            onRenamePlaylistClick = onRenamePlaylistClick,
            onPlaylistClick = { playlist ->
                librarySelectionUi.onClear()
                navigationState.openPlaylist(playlist.playlistId)
                onPlaylistSelected(playlist)
            },
            onDeletePlaylistClick = onDeletePlaylistClick,
            onExportPlaylistClick = onExportPlaylistClick,
            onAddPlaylistToQueueClick = addPlaylistToQueue,
            onImportPlaylistClick = onImportPlaylistClick,
            onChangePlaylistArtwork = onChangePlaylistArtwork,
            onResetPlaylistArtwork = onResetPlaylistArtwork,
            onBackFromPlaylist = {
                clearPlaylistSelection(true)
            },
            onRemovePlaylistSongClick = { playlistSong ->
                playlistSnackbarActions.removePlaylistSong(playlistSong)
            },
            onReorderPlaylistSongs = onReorderPlaylistSongs,
            onAddSongsToCurrentPlaylistClick = { playlist, songs ->
                playlistSnackbarActions.addSongsToPlaylist(playlist, songs)
            },
            onEditSongTagsClick = onEditSongTagsClick,
            onEditAlbumMetadataClick = onEditAlbumMetadataClick,
            onBatchMetadataClick = onBatchMetadataClick,
            isSleepTimerActive = isSleepTimerActive,
            sleepTimerDisplayText = sleepTimerDisplayText,
            onSleepTimerClick = onShowSleepTimer,
            recentlyPlayedSongs = recentlyPlayedSongs,
            recentlyAddedLibrarySongs = recentlyAddedLibrarySongs,
            mostPlayedSongs = mostPlayedSongs,
            selectedPlayerTheme = selectedPlayerTheme,
            selectedAppFont = selectedAppFont,
            onAppFontSelected = onAppFontSelected,
            selectedAppAppearance = selectedAppAppearance,
            onAppAppearanceSelected = onAppAppearanceSelected,
            selectedPlayerThemeTokens = selectedPlayerThemeTokens,
            onPlayerThemeSelected = onPlayerThemeSelected,
            selectedTabletLandscapeTheme = selectedTabletLandscapeTheme,
            onTabletLandscapeThemeSelected = onTabletLandscapeThemeSelected,
            pocketAdvanceAppearance = pocketAdvanceAppearance,
            pocketHorizonAppearance = pocketHorizonAppearance,
            onPocketAdvanceConsoleFinishSelected = onPocketAdvanceConsoleFinishSelected,
            onPocketHorizonConsoleFinishSelected = onPocketHorizonConsoleFinishSelected,
            onPocketAdvanceLcdPaletteSelected = onPocketAdvanceLcdPaletteSelected,
            onPocketHorizonLcdPaletteSelected = onPocketHorizonLcdPaletteSelected,
            onPocketAdvanceControlsFinishSelected = onPocketAdvanceControlsFinishSelected,
            onPocketHorizonWaveTreatmentSelected = onPocketHorizonWaveTreatmentSelected,
            onResetPocketAdvanceAppearance = onResetPocketAdvanceAppearance,
            onResetPocketHorizonAppearance = onResetPocketHorizonAppearance,
            onUpdatePlayerThemeTokenOverride = onUpdatePlayerThemeTokenOverride,
            onResetPlayerThemeTokenOverrides = onResetPlayerThemeTokenOverrides,
            selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
            onModernArtworkTransitionStyleSelected = onModernArtworkTransitionStyleSelected,
            selectedModernPlayerAppearance = selectedModernPlayerAppearance,
            activeModernAppearanceChoice = activeModernAppearanceChoice,
            selectedModernLandscapeLayout = selectedModernLandscapeLayout,
            onModernLandscapeLayoutSelected = onModernLandscapeLayoutSelected,
            onModernAppearanceChoiceSelected = onModernAppearanceChoiceSelected,
            onModernPlayerAppearanceEdited = onModernPlayerAppearanceEdited,
            onResetModernPlayerAppearance = onResetModernPlayerAppearance,
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
            audioOutputUiState = audioOutputUiState,
            equalizerScreenState =
                equalizerScreenState,
            equalizerActions = equalizerActions.copy(
                onBack = {
                    equalizerActions.onBack()
                    isEqualizerScreenVisible = false
                    isSettingsScreenVisible = true
                }
            ),
            libraryAppearanceUiState = libraryAppearanceUiState,
            onLibraryViewOptionSelected = onLibraryViewOptionSelected,
            settingsScrollState = settingsScrollState,
            bottomContentPadding = bottomContentPadding,
            modifier = Modifier.fillMaxSize()
        )
    }
}
