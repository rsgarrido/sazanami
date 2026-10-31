package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.exclude
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import android.content.Intent
import android.net.Uri
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.EditableSongTags
import io.github.rsgarrido.sazanami.data.BatchArtworkReference
import io.github.rsgarrido.sazanami.data.BatchMetadataEditorState
import io.github.rsgarrido.sazanami.data.BatchMetadataOperationState
import io.github.rsgarrido.sazanami.data.BatchMetadataPlan
import io.github.rsgarrido.sazanami.data.deriveBatchMetadataEditorState
import io.github.rsgarrido.sazanami.data.LibraryFolder
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.buildFolderBrowseIndex
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import io.github.rsgarrido.sazanami.data.preferences.AppAppearance
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.TagEditorResult
import io.github.rsgarrido.sazanami.data.buildGenreCollections
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.rememberNowPlayingMoreActionDispatcher
import io.github.rsgarrido.sazanami.ui.player.supportsNowPlayingMore
import io.github.rsgarrido.sazanami.ui.player.NowPlayingFavoriteFeedbackQueue
import io.github.rsgarrido.sazanami.ui.player.NowPlayingFavoriteFeedbackEffect
import io.github.rsgarrido.sazanami.ui.player.NowPlayingTrackInfoOverlay
import io.github.rsgarrido.sazanami.ui.player.NowPlayingArtworkViewerOverlay
import io.github.rsgarrido.sazanami.ui.player.rememberNowPlayingArtworkViewerOpener
import io.github.rsgarrido.sazanami.ui.player.supportsNowPlayingArtworkViewer
import io.github.rsgarrido.sazanami.ui.player.rememberNowPlayingTrackInfoOpener
import io.github.rsgarrido.sazanami.ui.player.rememberNowPlayingWorkflowOpeners
import io.github.rsgarrido.sazanami.ui.player.resolveNowPlayingAlbumKey
import io.github.rsgarrido.sazanami.ui.library.FolderBrowseScrollStateHolder
import io.github.rsgarrido.sazanami.ui.library.folderBrowseBackDestination
import io.github.rsgarrido.sazanami.ui.library.resolveFolderBrowseSelection
import io.github.rsgarrido.sazanami.ui.library.isAlbumGroupAvailable
import io.github.rsgarrido.sazanami.ui.library.metadataEditingSongs
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.navigation.PlaybackLaunchContext
import io.github.rsgarrido.sazanami.ui.navigation.capturePlaybackLaunchContext
import io.github.rsgarrido.sazanami.ui.navigation.playbackLaunchContextSaver
import io.github.rsgarrido.sazanami.ui.navigation.withValidDetails
import io.github.rsgarrido.sazanami.ui.playlist.rememberPlaylistSnackbarActions
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenField
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.theme.SazanamiAccent
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.modern.defaultPlayerLayout
import io.github.rsgarrido.sazanami.ui.player.pockethorizon.rememberPocketHorizonNavigationState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphHost
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import io.github.rsgarrido.sazanami.ui.player.resolvePlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.tabletLandscapePlayerCandidate
import io.github.rsgarrido.sazanami.ui.player.TabletLandscapePlayerContext
import io.github.rsgarrido.sazanami.ui.player.rememberTabletLandscapePlayerEnvironment
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.rememberPocketAdvanceNavigationState
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.LocalPocketAdvancePalette
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePalette
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationViewport
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerPresentationBoundary
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryAppearanceUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryRefreshSummary
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import io.github.rsgarrido.sazanami.ui.statistics.ListeningAnalyticsVisibilityEffect
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryViewOption
import io.github.rsgarrido.sazanami.ui.library.LocalLibrarySelectionUi
import io.github.rsgarrido.sazanami.ui.library.LibrarySelectionHeaderState
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerScreenState
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerUiActions
import io.github.rsgarrido.sazanami.ui.queue.rememberQueueSnackbarActions
import io.github.rsgarrido.sazanami.ui.tageditor.BatchMetadataEditorContext
import io.github.rsgarrido.sazanami.ui.tageditor.rememberTagEditorActions
import io.github.rsgarrido.sazanami.ui.tageditor.rememberBatchMetadataActions
import io.github.rsgarrido.sazanami.controller.SpotifyImportUiState
import io.github.rsgarrido.sazanami.ui.settings.SpotifyImportUiActions
import io.github.rsgarrido.sazanami.ui.settings.ListeningHistoryReconciliationUiActions
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessState
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import java.time.LocalDate


@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MusicScreen(
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
    previousPreviewSong: Song?,
    nextPreviewSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    playbackProgressUiState: StateFlow<PlaybackProgressUiState>,
    lyricsPlaybackUiState: LyricsPlaybackUiState,
    snackbarHostState: SnackbarHostState,
    onUndoAddToQueueClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onLyricsVisibilityChanged: (Boolean) -> Unit,
    onSuspendLyricsAutoFollow: () -> Unit,
    onReturnLyricsToCurrentLine: () -> Unit,
    onRescanLyrics: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    queuedSongs: List<Song>,
    upcomingSongs: List<Song>,
    playbackQueueHubUiState: PlaybackQueueHubUiState,
    onPlaybackQueueSelected: (String) -> Unit,
    onSwitchSelectedPlaybackQueue: () -> Unit,
    onCreatePlaybackQueueFromCurrent: () -> Unit,
    onRenamePlaybackQueue: (String, String) -> Unit,
    onDeletePlaybackQueue: (String) -> Unit,
    onRemovePlaybackQueueEntry: (String, String) -> Unit = { _, _ -> },
    onPlayPlaybackQueueEntry: (String, String) -> Unit = { _, _ -> },
    onUndoPlaybackQueueEntryRemoval: () -> Unit = {},
    onClearPlaybackQueueEntryRemovalUndo: () -> Unit = {},
    onReorderPlaybackQueueEntry: (String, String, Int) -> Unit = { _, _, _ -> },
    onClearPlaybackQueueMessage: () -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onUndoPlayNextClick: (Song) -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onMoveQueueItemUpClick: (Int) -> Unit,
    onMoveQueueItemDownClick: (Int) -> Unit,
    onClearQueueClick: () -> Unit,
    onPlayNextSongsClick: (List<Song>) -> Unit,
    onAddSongsToQueueClick: (List<Song>) -> Unit,
    onUndoPlayNextSongsClick: (List<Song>) -> Unit,
    onUndoAddSongsToQueueClick: (List<Song>) -> Unit,
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
    onCreatePlaylistClick: (String, Long?) -> Unit,
    onCreatePlaylistWithSongsClick: (String, List<Song>) -> Unit,
    onCreatePlaylistFolderClick: (String) -> Unit,
    onRenamePlaylistFolderClick: (PlaylistFolder, String) -> Unit,
    onDeletePlaylistFolderClick: (PlaylistFolder) -> Unit,
    onMovePlaylistToFolderClick: (Playlist, Long?) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onPreparePlaylistQueueSongs: (Playlist, (Result<List<Song>>) -> Unit) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangePlaylistArtwork: (Playlist, Uri) -> Unit,
    onResetPlaylistArtwork: (Playlist) -> Unit,
    onExportBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onPlaylistCleared: () -> Unit,
    onAddSongToPlaylistClick: (Playlist, Song) -> Unit,
    onAddSongsToPlaylistClick: (Playlist, List<Song>) -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onReorderPlaylistSongs: (Long, List<Long>) -> Unit,
    onTagsEdited: (Song, EditableSongTags) -> Unit,
    onReadEditableSongTags: (Song) -> EditableSongTags,
    onGetUnsupportedTagEditingMessage: (Song) -> String?,
    onWriteTagsAndArtwork: suspend (Song, EditableSongTags, Uri?) -> TagEditorResult,
    batchMetadataOperationState: BatchMetadataOperationState?,
    onBeginBatchMetadata: (BatchMetadataPlan, List<Song>, Uri?, Boolean) -> Unit,
    onConsumeBatchPermissionRequest: (String, Int) -> List<Uri>?,
    onBatchPermissionResult: (String, Int, Boolean, String?) -> Unit,
    onCancelBatchMetadata: () -> Unit,
    onRetryFailedBatchMetadata: (List<Song>) -> Unit,
    onContinueUnprocessedBatchMetadata: (List<Song>) -> Unit,
    onRetryBatchMetadataRefresh: () -> Unit,
    onDismissBatchMetadata: () -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onStartSleepTimerClick: (Int) -> Unit,
    onCancelSleepTimerClick: () -> Unit,
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
    onListeningAnalyticsActiveChanged: (Boolean) -> Unit,
    onListeningAnalyticsPresetSelected: (AnalyticsRangePreset) -> Unit,
    onListeningAnalyticsCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    onRetryListeningAnalytics: () -> Unit,
    onListeningAnalyticsTrendMetricSelected: (ListeningTrendMetric) -> Unit,
    onListeningAnalyticsRankingCategorySelected: (ListeningRankingCategory) -> Unit,
    spotifyImportUiState: SpotifyImportUiState,
    reconciliationUiState: ListeningHistoryReconciliationUiState,
    reconciliationActions: ListeningHistoryReconciliationUiActions,
    spotifyImportActions: SpotifyImportUiActions,
    playbackState: PlaybackUiState? = null,
    libraryState: LibraryUiState? = null
) {
    val appShellAccent = rememberAppShellAccent(
        playerTheme = selectedPlayerTheme,
        tokens = selectedPlayerThemeTokens
    )
    val horizonStyle = remember(pocketHorizonAppearance) { io.github.rsgarrido.sazanami.ui.player.pockethorizon.PocketHorizonStyle.from(pocketHorizonAppearance) }
    val advancePalette = remember(pocketAdvanceAppearance) { PocketAdvancePalette.from(pocketAdvanceAppearance) }
    val directStatusVisible = if (io.github.rsgarrido.sazanami.BuildConfig.DEBUG) {
        val direct by io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackBridge.state.collectAsState()
        direct.enabled
    } else false
    // Resolve against the very same viewport that hosts both endpoints, including the USB banner.
    CompositionLocalProvider(
        LocalAppShellAccent provides appShellAccent,
        LocalPocketAdvancePalette provides advancePalette,
        io.github.rsgarrido.sazanami.ui.player.pockethorizon.LocalPocketHorizonStyle provides horizonStyle
    ) {
    Column(modifier = modifier.fillMaxSize().appShellBackground()) {
        if (directStatusVisible) {
            io.github.rsgarrido.sazanami.ui.settings.IntegratedDirectPlaybackBanner(
                modifier = Modifier.statusBarsPadding()
            )
        }
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().then(
                if (directStatusVisible) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                else Modifier
            )
        ) {
    val context = LocalContext.current
    val favoriteFeedbackQueue = remember { NowPlayingFavoriteFeedbackQueue() }
    NowPlayingFavoriteFeedbackEffect(favoriteFeedbackQueue, snackbarHostState)
    val addPlaylistToQueueFailedText = stringResource(R.string.queue_add_playlist_failed)
    val librarySelectionSource = stringResource(R.string.library_selection_source)
    val librarySelectionUi = LocalLibrarySelectionUi.current
    val librarySelectionHeaderState = remember { LibrarySelectionHeaderState() }
    val navigationState = rememberMusicNavigationState()
    val pocketAdvanceNavigationState = rememberPocketAdvanceNavigationState()
    val pocketHorizonNavigationState = rememberPocketHorizonNavigationState()
    var mainDestination by navigationState.mainDestination
    var selectedLibraryTab by navigationState.selectedLibraryTab
    var playbackLaunchContext by navigationState.playbackLaunchContext
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
    val folderBrowseIndex = remember(songs) { buildFolderBrowseIndex(songs) }
    val folderBrowseScrollStateHolder = remember { FolderBrowseScrollStateHolder() }
    val canValidateFolderSelection = !isLibraryLoading && !isLibraryRefreshing
    val resolvedFolderId = if (canValidateFolderSelection) {
        resolveFolderBrowseSelection(folderBrowseIndex, selectedFolderId)
    } else {
        selectedFolderId
    }

    LaunchedEffect(folderBrowseIndex, selectedFolderId, canValidateFolderSelection) {
        if (canValidateFolderSelection && selectedFolderId != resolvedFolderId) {
            selectedFolderId = resolvedFolderId
        }
    }
    LaunchedEffect(folderBrowseIndex, canValidateFolderSelection) {
        if (canValidateFolderSelection) {
            folderBrowseScrollStateHolder.retainFolderIds(folderBrowseIndex.nodesById.keys)
        }
    }

    val overlayState = rememberMusicOverlayState()
    val settingsHelpNavigation = remember(overlayState) { SettingsHelpNavigation(overlayState) }
    val settingsScrollState = rememberScrollState()
    val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val statisticsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val playerMorphState = overlayState.playerMorphState
    val isPlayerExpanded = playerMorphState.isExpandedOrTransitioning
    var isLyricsVisible by rememberSaveable { mutableStateOf(false) }
    val lyricsTransitionState = rememberPlayerLyricsTransitionState(
        initiallyLyricsVisible = isLyricsVisible,
        onCompositionVisibilityChanged = { isLyricsVisible = it }
    )
    var isFolderScreenVisible by overlayState.isFolderScreenVisible
    var isSettingsScreenVisible by overlayState.isSettingsScreenVisible
    var isTipsHelpScreenVisible by overlayState.isTipsHelpScreenVisible
    var isAboutScreenVisible by overlayState.isAboutScreenVisible
    var isDiagnosticsScreenVisible by overlayState.isDiagnosticsScreenVisible
    var isEqualizerScreenVisible by overlayState.isEqualizerScreenVisible
    var isStatisticsScreenVisible by overlayState.isStatisticsScreenVisible
    var isListeningHistoryImportVisible by overlayState.isListeningHistoryImportVisible
    var isListeningHistoryReconciliationVisible by
    overlayState.isListeningHistoryReconciliationVisible
    var isExpandedUpNextSheetVisible by overlayState.isExpandedUpNextSheetVisible
    var isQueueHubVisible by overlayState.isQueueHubVisible
    val isNowPlayingMoreVisible by overlayState.isNowPlayingMoreVisible
    val isTrackInformationVisible by overlayState.isTrackInformationVisible
    var isCreatePlaylistDialogVisible by overlayState.isCreatePlaylistDialogVisible
    var playlistCreationFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isSleepTimerDialogVisible by overlayState.isSleepTimerDialogVisible
    var songPendingPlaylistAdd by remember { mutableStateOf<Song?>(null) }
    var songsPendingPlaylistAdd by remember { mutableStateOf<List<Song>>(emptyList()) }
    var songPendingTagEdit by remember { mutableStateOf<Song?>(null) }
    var isBatchSongSelectionVisible by remember { mutableStateOf(false) }
    var isBatchPreparationInProgress by remember { mutableStateOf(false) }
    var batchMetadataEditorState by remember {
        mutableStateOf<BatchMetadataEditorState?>(null)
    }
    var batchMetadataEditorContext by remember {
        mutableStateOf<BatchMetadataEditorContext>(BatchMetadataEditorContext.SongSelection)
    }

    var isTagSaveInProgress by remember { mutableStateOf(false) }
    var hasUnsavedTagChanges by remember { mutableStateOf(false) }
    var isDiscardTagChangesDialogVisible by remember { mutableStateOf(false) }
    var selectedArtworkUriForTagEdit by remember { mutableStateOf<Uri?>(null) }

    val playerEnvironment = if (selectedTabletLandscapeTheme != TabletLandscapeTheme.NORMAL_PLAYER)
        rememberTabletLandscapePlayerEnvironment() else null
    val playerDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val persistentInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).let {
        if (directStatusVisible) it.exclude(WindowInsets.statusBars) else it
    }
    val insetLeft = persistentInsets.getLeft(playerDensity, layoutDirection)
    val insetRight = persistentInsets.getRight(playerDensity, layoutDirection)
    val insetTop = persistentInsets.getTop(playerDensity)
    val insetBottom = persistentInsets.getBottom(playerDensity)
    val usablePlayerSize = with(playerDensity) {
        DpSize(
            (maxWidth - (insetLeft + insetRight).toDp()).coerceAtLeast(0.dp),
            (maxHeight - (insetTop + insetBottom).toDp()).coerceAtLeast(0.dp)
        )
    }
    val requestedPlayerPresentation = resolvePlayerPresentation(
        selectedPlayerTheme, selectedPlayerThemeTokens,
        context = playerEnvironment?.let {
            TabletLandscapePlayerContext(it.deviceKind, it.smallestScreenWidthDp, usablePlayerSize)
        },
        landscapeCandidate = tabletLandscapePlayerCandidate(
            selectedTabletLandscapeTheme, selectedPlayerThemeTokens, playerDensity.fontScale
        )
    )
    val playerBoundary = rememberPlayerPresentationBoundary(
        requested = requestedPlayerPresentation,
        viewport = PlayerPresentationViewport(
            constraints.maxWidth, constraints.maxHeight, playerDensity.density, playerDensity.fontScale,
            insetLeft, insetTop, insetRight, insetBottom
        ),
        morphState = playerMorphState,
        resetPresentationOverlays = {
            lyricsTransitionState.snapToExpanded()
            overlayState.dismissNowPlayingMore()
            overlayState.dismissTrackInformation()
            overlayState.dismissArtworkViewer()
        }
    )
    // App accents/settings continue using the primary selection, independently of this pair.
    val resolvedPlayerPresentation = playerBoundary.presentation
    val canPresentNowPlayingMore = playerBoundary.inputEnabled && supportsNowPlayingMore(resolvedPlayerPresentation.identity) &&
            playerMorphState.targetPresentation == PlayerPresentation.Expanded &&
            !isLyricsVisible && !lyricsTransitionState.lyricsOwnsInput &&
            songPendingTagEdit == null && batchMetadataEditorState == null &&
            songPendingPlaylistAdd == null && songsPendingPlaylistAdd.isEmpty()
    val onTrackInfoClick = rememberNowPlayingTrackInfoOpener(currentSong, overlayState, canPresentNowPlayingMore)
    val workflowOpeners = rememberNowPlayingWorkflowOpeners(currentSong, canPresentNowPlayingMore) { target ->
        songPendingPlaylistAdd = target
    }
    val onOpenSleepTimer: () -> Unit = remember(overlayState) {
        { overlayState.isSleepTimerDialogVisible.value = true }
    }
    NowPlayingTrackInfoOverlay(overlayState, canPresentNowPlayingMore)
    val canPresentArtworkViewer = canPresentNowPlayingMore && supportsNowPlayingArtworkViewer(resolvedPlayerPresentation.identity)
    val onViewArtwork = rememberNowPlayingArtworkViewerOpener(currentSong, overlayState, lyricsTransitionState, canPresentArtworkViewer)
    NowPlayingArtworkViewerOverlay(overlayState, canPresentArtworkViewer)
    val nowPlayingMoreTarget = overlayState.currentNowPlayingMoreTarget(currentSong)
        ?.takeIf { canPresentNowPlayingMore }
    LaunchedEffect(
        currentSong?.membershipKey(),
        overlayState.nowPlayingMoreTarget,
        isNowPlayingMoreVisible,
        canPresentNowPlayingMore
    ) {
        overlayState.reconcileNowPlayingMore(currentSong, canPresentNowPlayingMore)
    }

    LaunchedEffect(isLyricsVisible) {
        onLyricsVisibilityChanged(isLyricsVisible)
    }
    LaunchedEffect(currentSong?.id) {
        if (currentSong == null) lyricsTransitionState.snapToExpanded()
    }
    ListeningAnalyticsVisibilityEffect(
        isVisible = isStatisticsScreenVisible,
        onActiveChanged = onListeningAnalyticsActiveChanged
    )

    val tagEditorActions = rememberTagEditorActions(
        snackbarHostState = snackbarHostState,
        onGetUnsupportedEditingMessage = onGetUnsupportedTagEditingMessage,
        onWriteTagsAndArtwork = onWriteTagsAndArtwork,
        onTagsSaved = { originalSong, editedTags ->
            onTagsEdited(originalSong, editedTags)
        },
        onSavingChanged = { isSaving ->
            isTagSaveInProgress = isSaving
        },
        onCloseEditor = {
            songPendingTagEdit = null
            isTagSaveInProgress = false
            hasUnsavedTagChanges = false
            selectedArtworkUriForTagEdit = null
        }
    )

    val batchMetadataActions = rememberBatchMetadataActions(
        state = batchMetadataOperationState,
        songs = songs,
        onBegin = onBeginBatchMetadata,
        onConsumePermissionRequest = onConsumeBatchPermissionRequest,
        onPermissionResult = onBatchPermissionResult,
        onCancel = onCancelBatchMetadata,
        onRetryFailed = onRetryFailedBatchMetadata,
        onContinueUnprocessed = onContinueUnprocessedBatchMetadata,
        onRetryRefresh = onRetryBatchMetadataRefresh,
        onDismiss = onDismissBatchMetadata
    )

    val queueSnackbarActions = rememberQueueSnackbarActions(
        snackbarHostState = snackbarHostState,
        onAddToQueueClick = onAddToQueueClick,
        onUndoAddToQueueClick = onUndoAddToQueueClick,
        onPlayNextClick = onPlayNextClick,
        onUndoPlayNextClick = onUndoPlayNextClick,
        onPlayNextSongsClick = onPlayNextSongsClick,
        onUndoPlayNextSongsClick = onUndoPlayNextSongsClick,
        onAddSongsToQueueClick = onAddSongsToQueueClick,
        onUndoAddSongsToQueueClick = onUndoAddSongsToQueueClick
    )
    val addPlaylistToQueue: (Playlist) -> Unit = { playlist ->
        onPreparePlaylistQueueSongs(playlist) { result ->
            result.onSuccess { customOrderSongs ->
                queueSnackbarActions.addSongsToQueue(playlist.name, customOrderSongs)
            }.onFailure {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(addPlaylistToQueueFailedText)
                }
            }
        }
    }

    val playlistSnackbarActions = rememberPlaylistSnackbarActions(
        snackbarHostState = snackbarHostState,
        onAddSongToPlaylistClick = onAddSongToPlaylistClick,
        onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
        onRemovePlaylistSongClick = onRemovePlaylistSongClick
    )

    val artworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selectedUri ->
        if (selectedUri != null) {
            selectedArtworkUriForTagEdit = selectedUri
        }
    }

    val batchArtworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri ->
        if (selectedUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            batchMetadataEditorState = batchMetadataEditorState?.replaceArtwork(
                BatchArtworkReference(
                    identity = selectedUri.toString(),
                    previewUri = selectedUri.toString()
                )
            )
        }
    }

    val prepareBatchMetadataEditor: (List<Song>, BatchMetadataEditorContext) -> Unit =
        { selectedSongs, editorContext ->
            if (!isBatchPreparationInProgress && selectedSongs.isNotEmpty()) {
                isBatchPreparationInProgress = true
                coroutineScope.launch {
                    val editorState = runCatching {
                        withContext(Dispatchers.IO) {
                            deriveBatchMetadataEditorState(
                                songs = selectedSongs,
                                readTags = onReadEditableSongTags
                            )
                        }
                    }.getOrElse {
                        isBatchPreparationInProgress = false
                        snackbarHostState.showSnackbar(
                            "Could not prepare the selected metadata."
                        )
                        return@launch
                    }
                    batchMetadataEditorContext = editorContext
                    batchMetadataEditorState = editorState
                    isBatchPreparationInProgress = false
                    isBatchSongSelectionVisible = false
                }
            }
        }

    val recentlyAddedSongIds = queueSnackbarActions.recentlyAddedSongIds

    fun requestCloseTagEditor() {
        if (isTagSaveInProgress) {
            return
        }

        if (hasUnsavedTagChanges || selectedArtworkUriForTagEdit != null) {
            isDiscardTagChangesDialogVisible = true
        } else {
            songPendingTagEdit = null
            hasUnsavedTagChanges = false
            selectedArtworkUriForTagEdit = null
        }
    }

    fun closeBatchMetadataResults() {
        batchMetadataActions.closeResults()
        batchMetadataEditorState = null
        val albumContext = batchMetadataEditorContext as? BatchMetadataEditorContext.Album
        if (albumContext != null && !isAlbumGroupAvailable(albumContext.albumKey, songs)) {
            navigationState.clearAlbum()
            selectedLibraryTab = LibraryTab.ALBUMS
        }
        batchMetadataEditorContext = BatchMetadataEditorContext.SongSelection
    }

    val onEditSongTagsClick: (Song) -> Unit = remember {
        { song ->
            isTagSaveInProgress = false
            hasUnsavedTagChanges = false
            isDiscardTagChangesDialogVisible = false
            selectedArtworkUriForTagEdit = null
            songPendingTagEdit = song
        }
    }
    val latestPrepareBatchMetadataEditor = rememberUpdatedState(prepareBatchMetadataEditor)
    val onEditAlbumMetadataClick: (LibraryAlbumGroup) -> Unit = remember {
        { album ->
            latestPrepareBatchMetadataEditor.value(
                album.metadataEditingSongs(),
                BatchMetadataEditorContext.Album(
                    albumKey = album.key,
                    title = album.title,
                    artworkUri = album.songs.firstOrNull()?.albumArtUri?.toString()
                )
            )
        }
    }
    val onBatchMetadataClick: () -> Unit = remember {
        { isBatchSongSelectionVisible = true }
    }

    // Prepare these slots outside PlayerMorphHost; invoke them at the original screen/dialog positions.
    val metadataEditorContent: @Composable () -> Unit = {
        MusicMetadataPresentation(
            songForTagEdit = songPendingTagEdit,
            batchEditorState = batchMetadataEditorState,
            batchExecutionState = batchMetadataOperationState,
            batchEditorContext = batchMetadataEditorContext,
            currentSongId = currentSong?.id,
            isTagSaveInProgress = isTagSaveInProgress,
            selectedArtworkUri = selectedArtworkUriForTagEdit,
            tagEditorActions = tagEditorActions,
            batchMetadataActions = batchMetadataActions,
            onReadEditableSongTags = onReadEditableSongTags,
            onGetUnsupportedTagEditingMessage = onGetUnsupportedTagEditingMessage,
            onRequestCloseTagEditor = ::requestCloseTagEditor,
            onChooseTagArtwork = { artworkPickerLauncher.launch("image/*") },
            onUnsavedTagChangesChanged = { hasUnsavedTagChanges = it },
            onBatchEditorStateChanged = { batchMetadataEditorState = it },
            onChooseBatchArtwork = { batchArtworkPickerLauncher.launch(arrayOf("image/*")) },
            onCloseBatchEditor = {
                batchMetadataEditorState = null
                batchMetadataEditorContext = BatchMetadataEditorContext.SongSelection
            },
            onCloseBatchResults = ::closeBatchMetadataResults
        )
    }
    val metadataDialogContent: @Composable () -> Unit = {
        MusicMetadataDialogs(
            isBatchSongSelectionVisible = isBatchSongSelectionVisible,
            songs = songs,
            isBatchPreparationInProgress = isBatchPreparationInProgress,
            isDiscardTagChangesDialogVisible = isDiscardTagChangesDialogVisible,
            onDismissBatchSelection = {
                if (!isBatchPreparationInProgress) isBatchSongSelectionVisible = false
            },
            onContinueBatchSelection = { selectedSongs ->
                prepareBatchMetadataEditor(selectedSongs, BatchMetadataEditorContext.SongSelection)
            },
            onDismissTagDiscard = { isDiscardTagChangesDialogVisible = false },
            onConfirmTagDiscard = {
                isDiscardTagChangesDialogVisible = false
                hasUnsavedTagChanges = false
                selectedArtworkUriForTagEdit = null
                songPendingTagEdit = null
            }
        )
    }

    fun closeSettings() {
        isSettingsScreenVisible = false
        coroutineScope.launch {
            settingsScrollState.scrollTo(0)
        }
    }

    fun recordPlaybackLaunchContext() {
        playbackLaunchContext = capturePlaybackLaunchContext(
            mainDestination = mainDestination,
            selectedLibraryTab = selectedLibraryTab,
            selectedAlbumKey = selectedAlbumKey,
            selectedArtistName = selectedArtistName,
            selectedGenreKey = selectedGenreKey,
            selectedPlaylistId = selectedPlaylistId,
            searchQuery = searchQuery,
            selectedFolderId = selectedFolderId
        )
    }

    fun clearPlaylistSelection(returnToOrigin: Boolean = false) {
        val hadSelection = selectedPlaylistId != null || selectedPlaylistStateId != null
        if (returnToOrigin) {
            navigationState.closePlaylist()
        } else {
            navigationState.clearPlaylist()
        }
        if (hadSelection) {
            onPlaylistCleared()
        }
    }

    val onOpenCurrentArtistClick = rememberNowPlayingArtistNavigation(currentSong, songs) { artistName ->
        navigateToNowPlayingArtist(
            name = artistName,
            navigationState = navigationState,
            resetLyrics = lyricsTransitionState::snapToExpanded,
            collapsePlayer = playerMorphState::collapse,
            clearLibrarySelection = librarySelectionUi.onClear,
            clearPlaylistSelection = { clearPlaylistSelection() }
        )
    }

    fun openCurrentAlbum(song: Song) {
        val albumKey = resolveNowPlayingAlbumKey(song, songs)
        if (albumKey != null) {
            lyricsTransitionState.snapToExpanded()
            playerMorphState.collapse()
            navigationState.clearArtist()
            navigationState.openAlbum(albumKey, DetailEntryOrigin.LIBRARY)
            selectedGenreKey = null
            clearPlaylistSelection()
            searchQuery = ""
            mainDestination = MainDestination.LIBRARY
        }
    }

    val onNowPlayingMoreAction = rememberNowPlayingMoreActionDispatcher(
        currentSong = currentSong,
        librarySongs = songs,
        favoriteMembershipKeys = favoriteMembershipKeys,
        canPresent = canPresentNowPlayingMore,
        overlayState = overlayState,
        onToggleFavorite = onToggleFavoriteClick,
        onOpenAlbum = ::openCurrentAlbum,
        onOpenLyrics = lyricsTransitionState::openLyrics,
        onFavoriteFeedback = favoriteFeedbackQueue::emit,
        onOpenArtist = onOpenCurrentArtistClick,
        onTrackInfoClick = onTrackInfoClick,
        onAddToPlaylist = workflowOpeners.addToPlaylist,
        onRateSong = workflowOpeners.rateSong,
        onOpenSleepTimer = onOpenSleepTimer
    )

    val onShowAddToPlaylist: (Song) -> Unit = remember {
        { song -> songPendingPlaylistAdd = song }
    }
    val onShowBulkAddToPlaylist: (List<Song>) -> Unit = remember {
        { targets -> songsPendingPlaylistAdd = targets }
    }
    val onShowCreatePlaylist: (Long?) -> Unit = remember {
        { folderId ->
            playlistCreationFolderId = folderId
            isCreatePlaylistDialogVisible = true
        }
    }
    // Player policy uses the effective More target and playlist presence, not their presentation details.
    val isNowPlayingMorePresented = nowPlayingMoreTarget != null
    val hasPendingPlaylistAdd = songPendingPlaylistAdd != null
    val hasPendingBulkPlaylistAdd = songsPendingPlaylistAdd.isNotEmpty()
    val transientOverlayContent: @Composable () -> Unit = {
        MusicTransientOverlays(
            playerPresentation = resolvedPlayerPresentation.identity,
            nowPlayingMoreTarget = nowPlayingMoreTarget,
            favoriteMembershipKeys = favoriteMembershipKeys,
            songs = songs,
            onDismissNowPlayingMore = overlayState::dismissNowPlayingMore,
            onNowPlayingMoreAction = onNowPlayingMoreAction,
            isExpandedUpNextSheetVisible = isExpandedUpNextSheetVisible,
            queuedSongs = queuedSongs,
            upcomingSongs = upcomingSongs,
            isShuffleEnabled = isShuffleEnabled,
            onDismissExpandedUpNextSheet = { isExpandedUpNextSheetVisible = false },
            onRemoveFromQueueClick = onRemoveFromQueueClick,
            onMoveQueueItemUpClick = onMoveQueueItemUpClick,
            onMoveQueueItemDownClick = onMoveQueueItemDownClick,
            onClearQueueClick = onClearQueueClick,
            isQueueHubVisible = isQueueHubVisible,
            playbackQueueHubUiState = playbackQueueHubUiState,
            onDismissQueueHub = { isQueueHubVisible = false },
            onPlaybackQueueSelected = onPlaybackQueueSelected,
            onSwitchSelectedPlaybackQueue = onSwitchSelectedPlaybackQueue,
            onCreatePlaybackQueueFromCurrent = onCreatePlaybackQueueFromCurrent,
            onRenamePlaybackQueue = onRenamePlaybackQueue,
            onDeletePlaybackQueue = onDeletePlaybackQueue,
            onRemovePlaybackQueueEntry = onRemovePlaybackQueueEntry,
            onPlayPlaybackQueueEntry = onPlayPlaybackQueueEntry,
            onUndoPlaybackQueueEntryRemoval = onUndoPlaybackQueueEntryRemoval,
            onClearPlaybackQueueEntryRemovalUndo = onClearPlaybackQueueEntryRemovalUndo,
            onReorderPlaybackQueueEntry = onReorderPlaybackQueueEntry,
            onClearPlaybackQueueMessage = onClearPlaybackQueueMessage,
            isCreatePlaylistDialogVisible = isCreatePlaylistDialogVisible,
            createPlaylistFolderId = playlistCreationFolderId,
            playlists = playlists,
            onDismissCreatePlaylistDialog = {
                isCreatePlaylistDialogVisible = false
                playlistCreationFolderId = null
            },
            onCreatePlaylistClick = onCreatePlaylistClick,
            onCreatePlaylistWithSongsClick = onCreatePlaylistWithSongsClick,
            songPendingPlaylistAdd = songPendingPlaylistAdd,
            onDismissAddToPlaylistDialog = { songPendingPlaylistAdd = null },
            onAddSongToPlaylistClick = { playlist, song ->
                playlistSnackbarActions.addSongToPlaylist(playlist, song)
            },
            songsPendingPlaylistAdd = songsPendingPlaylistAdd,
            onDismissBulkAddToPlaylistDialog = { songsPendingPlaylistAdd = emptyList() },
            onAddSongsToPlaylistClick = { playlist, targets ->
                playlistSnackbarActions.addSongsToPlaylist(playlist, targets)
                librarySelectionUi.onClear()
            },
            isSleepTimerDialogVisible = isSleepTimerDialogVisible,
            isSleepTimerActive = isSleepTimerActive,
            sleepTimerDisplayText = sleepTimerDisplayText,
            onStartSleepTimerClick = onStartSleepTimerClick,
            onCancelSleepTimerClick = onCancelSleepTimerClick,
            onDismissSleepTimerDialog = { isSleepTimerDialogVisible = false }
        )
    }

    fun restorePlaybackLaunchContext() {
        val validContext = playbackLaunchContext.withValidDetails(
            albumKeys = buildLibraryAlbumGroups(songs).mapTo(mutableSetOf()) { album -> album.key },
            artistNames = songs.mapTo(mutableSetOf()) { song ->
                song.artist.ifBlank { "Unknown Artist" }
            },
            genreKeys = buildGenreCollections(songs).mapTo(mutableSetOf()) { genre ->
                genre.key
            },
            playlistIds = playlists.mapTo(mutableSetOf()) { playlist -> playlist.playlistId },
            folderBrowseIndex = folderBrowseIndex
        )

        lyricsTransitionState.snapToExpanded()
        val restoredAlbumOrigin = navigationState.albumDetailOrigin.value
        val restoredArtistOrigin = navigationState.artistDetailOrigin.value
        val restoredPlaylistOrigin = navigationState.playlistDetailOrigin.value
        navigationState.clearArtist()
        navigationState.clearAlbum()
        navigationState.clearFolder()
        selectedGenreKey = null
        clearPlaylistSelection()

        when (validContext) {
            PlaybackLaunchContext.Home -> {
                mainDestination = MainDestination.HOME
            }

            is PlaybackLaunchContext.LibrarySection -> {
                selectedLibraryTab = validContext.tab
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.AlbumDetail -> {
                navigationState.openAlbum(validContext.albumKey, restoredAlbumOrigin)
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.ArtistDetail -> {
                navigationState.openArtist(validContext.artistName, restoredArtistOrigin)
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.GenreDetail -> {
                selectedLibraryTab = LibraryTab.GENRES
                selectedGenreKey = validContext.genreKey
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.PlaylistDetail -> {
                selectedLibraryTab = LibraryTab.PLAYLISTS
                playlists.firstOrNull { playlist ->
                    playlist.playlistId == validContext.playlistId
                }?.let { playlist ->
                    navigationState.openPlaylist(playlist.playlistId, restoredPlaylistOrigin)
                    onPlaylistSelected(playlist)
                }
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.FolderDetail -> {
                navigationState.openFolder(validContext.folderId)
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.Search -> {
                selectedLibraryTab = LibraryTab.SONGS
                searchQuery = validContext.query
                mainDestination = MainDestination.SEARCH
            }
        }
    }

    BackHandler(
        enabled = songPendingTagEdit != null ||
                batchMetadataEditorState != null ||
                batchMetadataOperationState != null ||
                isExpandedUpNextSheetVisible ||
                isQueueHubVisible ||
                isNowPlayingMoreVisible ||
                isTrackInformationVisible ||
                overlayState.isArtworkViewerVisible.value ||
                playerMorphState.shouldConsumeBack ||
                isFolderScreenVisible ||
                isDiagnosticsScreenVisible ||
                isEqualizerScreenVisible ||
                isStatisticsScreenVisible ||
                isListeningHistoryImportVisible ||
                isListeningHistoryReconciliationVisible ||
                isSettingsScreenVisible ||
                isTipsHelpScreenVisible ||
                isAboutScreenVisible ||
                selectedArtistName != null ||
                selectedAlbumKey != null ||
                selectedGenreKey != null ||
                selectedPlaylistId != null ||
                (mainDestination == MainDestination.LIBRARY &&
                        selectedLibraryTab == LibraryTab.FOLDERS &&
                        selectedFolderId != null) ||
                librarySelectionUi.state.isActive ||
                mainDestination != MainDestination.HOME
    ) {
        when {
            batchMetadataOperationState is BatchMetadataOperationState.Running ||
                    batchMetadataOperationState is BatchMetadataOperationState.Preparing ||
                    batchMetadataOperationState is BatchMetadataOperationState.AwaitingPermission ||
                    batchMetadataOperationState is BatchMetadataOperationState.PostProcessing -> {
                batchMetadataActions.cancel()
            }

            batchMetadataOperationState is BatchMetadataOperationState.Complete ||
                    batchMetadataOperationState is BatchMetadataOperationState.Interrupted -> {
                closeBatchMetadataResults()
            }

            batchMetadataEditorState != null -> {
                batchMetadataEditorState = null
                batchMetadataEditorContext = BatchMetadataEditorContext.SongSelection
            }

            songPendingTagEdit != null -> {
                requestCloseTagEditor()
            }

            isTrackInformationVisible -> {
                overlayState.dismissTrackInformation()
            }

            overlayState.isArtworkViewerVisible.value -> {
                overlayState.dismissArtworkViewer()
            }

            isNowPlayingMoreVisible -> {
                overlayState.dismissNowPlayingMore()
            }

            isLyricsVisible -> {
                lyricsTransitionState.returnToExpanded()
            }

            isExpandedUpNextSheetVisible -> {
                isExpandedUpNextSheetVisible = false
            }

            isQueueHubVisible -> {
                isQueueHubVisible = false
            }

            librarySelectionUi.state.isActive -> {
                librarySelectionUi.onClear()
            }

            playerMorphState.shouldConsumeBack -> {
                playerMorphState.collapse()
                restorePlaybackLaunchContext()
            }

            isFolderScreenVisible -> {
                isFolderScreenVisible = false
                isSettingsScreenVisible = true
            }

            isDiagnosticsScreenVisible -> {
                isDiagnosticsScreenVisible = false
                isSettingsScreenVisible = true
            }

            isEqualizerScreenVisible -> {
                equalizerActions.onBack()
                isEqualizerScreenVisible = false
                isSettingsScreenVisible = true
            }

            isStatisticsScreenVisible -> {
                isStatisticsScreenVisible = false
            }

            isListeningHistoryImportVisible -> {
                isListeningHistoryImportVisible = false
                isSettingsScreenVisible = true
            }

            isListeningHistoryReconciliationVisible -> {
                isListeningHistoryReconciliationVisible = false
                isSettingsScreenVisible = true
            }

            isSettingsScreenVisible -> {
                closeSettings()
            }
            isTipsHelpScreenVisible -> {
                settingsHelpNavigation.backFromTips()
            }
            isAboutScreenVisible -> {
                settingsHelpNavigation.backFromAbout()
            }

            mainDestination == MainDestination.LIBRARY &&
                    selectedLibraryTab == LibraryTab.FOLDERS &&
                    selectedFolderId != null -> {
                selectedFolderId = folderBrowseBackDestination(
                    folderBrowseIndex,
                    selectedFolderId
                )
            }

            selectedAlbumKey != null -> {
                navigationState.closeAlbum()
            }

            selectedArtistName != null -> {
                navigationState.closeArtist()
            }

            selectedGenreKey != null -> {
                selectedGenreKey = null
            }

            selectedPlaylistId != null -> {
                clearPlaylistSelection(returnToOrigin = true)
            }

            mainDestination != MainDestination.HOME -> {
                mainDestination = MainDestination.HOME
            }
        }
    }

    val appShellChartAccent = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        rememberAppShellAccent(
            playerTheme = selectedPlayerTheme,
            tokens = selectedPlayerThemeTokens,
            fallbackAccent = SazanamiAccent,
            minimumContrast = ChartAccentMinimumContrast,
            contrastSurface = MaterialTheme.colorScheme.surfaceContainerLow
        )
    } else {
        appShellAccent
    }
    val bodyPresentation = prepareMusicBodyPresentation(
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
        onSongClick = onSongClick,
        onPlaySongsClick = onPlaySongsClick,
        onPlayPauseClick = onPlayPauseClick,
        onPreviousClick = onPreviousClick,
        onNextClick = onNextClick,
        onSeekChange = onSeekChange,
        onShuffleClick = onShuffleClick,
        onRepeatClick = onRepeatClick,
        queuedSongs = queuedSongs,
        upcomingSongs = upcomingSongs,
        onRemoveFromQueueClick = onRemoveFromQueueClick,
        onMoveQueueItemUpClick = onMoveQueueItemUpClick,
        onMoveQueueItemDownClick = onMoveQueueItemDownClick,
        onClearQueueClick = onClearQueueClick,
        libraryFolders = libraryFolders,
        folderSelectionMode = folderSelectionMode,
        selectedLibraryFolders = selectedLibraryFolders,
        excludedLibraryFolders = excludedLibraryFolders,
        onScanLibraryClick = onScanLibraryClick,
        onLibraryFolderToggle = onLibraryFolderToggle,
        onSelectAllLibraryFolders = onSelectAllLibraryFolders,
        onClearSelectedLibraryFolders = onClearSelectedLibraryFolders,
        favoriteMembershipKeys = favoriteMembershipKeys,
        unresolvedFavoriteCount = unresolvedFavoriteCount,
        unresolvedPlaylistRowCount = unresolvedPlaylistRowCount,
        unresolvedListeningHistoryCount = unresolvedListeningHistoryCount,
        onToggleFavoriteClick = onToggleFavoriteClick,
        playlists = playlists,
        playlistFolders = playlistFolders,
        selectedPlaylistStateId = selectedPlaylistStateId,
        selectedPlaylistName = selectedPlaylistName,
        selectedPlaylistSongs = selectedPlaylistSongs,
        isSelectedPlaylistLoading = isSelectedPlaylistLoading,
        onCreatePlaylistFolderClick = onCreatePlaylistFolderClick,
        onRenamePlaylistFolderClick = onRenamePlaylistFolderClick,
        onDeletePlaylistFolderClick = onDeletePlaylistFolderClick,
        onMovePlaylistToFolderClick = onMovePlaylistToFolderClick,
        onRenamePlaylistClick = onRenamePlaylistClick,
        onDeletePlaylistClick = onDeletePlaylistClick,
        onExportPlaylistClick = onExportPlaylistClick,
        onImportPlaylistClick = onImportPlaylistClick,
        onChangePlaylistArtwork = onChangePlaylistArtwork,
        onResetPlaylistArtwork = onResetPlaylistArtwork,
        onExportBackupClick = onExportBackupClick,
        onRestoreBackupClick = onRestoreBackupClick,
        onPlaylistSelected = onPlaylistSelected,
        onReorderPlaylistSongs = onReorderPlaylistSongs,
        isSleepTimerActive = isSleepTimerActive,
        sleepTimerDisplayText = sleepTimerDisplayText,
        recentlyPlayedSongs = recentlyPlayedSongs,
        recentlyAddedLibrarySongs = recentlyAddedLibrarySongs,
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
        onPreserveAlbumTransitionsChanged = onPreserveAlbumTransitionsChanged,
        audioOutputUiState = audioOutputUiState,
        equalizerScreenState = equalizerScreenState,
        equalizerActions = equalizerActions,
        libraryAppearanceUiState = libraryAppearanceUiState,
        onLibraryViewOptionSelected = onLibraryViewOptionSelected,
        mostPlayedSongs = mostPlayedSongs,
        listeningAnalyticsUiState = listeningAnalyticsUiState,
        showNotCountedPlays = showNotCountedPlays,
        onShowNotCountedPlaysChanged = onShowNotCountedPlaysChanged,
        onListeningAnalyticsPresetSelected = onListeningAnalyticsPresetSelected,
        onListeningAnalyticsCustomRangeSelected = onListeningAnalyticsCustomRangeSelected,
        onRetryListeningAnalytics = onRetryListeningAnalytics,
        onListeningAnalyticsTrendMetricSelected = onListeningAnalyticsTrendMetricSelected,
        onListeningAnalyticsRankingCategorySelected = onListeningAnalyticsRankingCategorySelected,
        spotifyImportUiState = spotifyImportUiState,
        reconciliationUiState = reconciliationUiState,
        reconciliationActions = reconciliationActions,
        spotifyImportActions = spotifyImportActions,
        navigationState = navigationState,
        librarySelectionUi = librarySelectionUi,
        isPlayerExpanded = isPlayerExpanded,
        folderBrowseIndex = folderBrowseIndex,
        folderBrowseScrollStateHolder = folderBrowseScrollStateHolder,
        resolvedFolderId = resolvedFolderId,
        recentlyAddedSongIds = recentlyAddedSongIds,
        settingsHelpNavigation = settingsHelpNavigation,
        settingsScrollState = settingsScrollState,
        homeListState = homeListState,
        statisticsListState = statisticsListState,
        queueSnackbarActions = queueSnackbarActions,
        playlistSnackbarActions = playlistSnackbarActions,
        addPlaylistToQueue = addPlaylistToQueue,
        onCloseSettings = ::closeSettings,
        clearPlaylistSelection = ::clearPlaylistSelection,
        onRecordPlaybackLaunchContext = ::recordPlaybackLaunchContext,
        onExpandPlayer = playerMorphState::expand,
        onShowSleepTimer = onOpenSleepTimer,
        onShowAddToPlaylist = onShowAddToPlaylist,
        onShowBulkAddToPlaylist = onShowBulkAddToPlaylist,
        onShowCreatePlaylist = onShowCreatePlaylist,
        onEditSongTagsClick = onEditSongTagsClick,
        onEditAlbumMetadataClick = onEditAlbumMetadataClick,
        onBatchMetadataClick = onBatchMetadataClick,
        folderScreenVisible = overlayState.isFolderScreenVisible,
        settingsScreenVisible = overlayState.isSettingsScreenVisible,
        tipsHelpScreenVisible = overlayState.isTipsHelpScreenVisible,
        aboutScreenVisible = overlayState.isAboutScreenVisible,
        diagnosticsScreenVisible = overlayState.isDiagnosticsScreenVisible,
        equalizerScreenVisible = overlayState.isEqualizerScreenVisible,
        statisticsScreenVisible = overlayState.isStatisticsScreenVisible,
        listeningHistoryImportVisible = overlayState.isListeningHistoryImportVisible,
        listeningHistoryReconciliationVisible = overlayState.isListeningHistoryReconciliationVisible
    )

    val selectedSongForTagEdit = songPendingTagEdit
    val selectedBatchEditorState = batchMetadataEditorState
    val selectedBatchExecutionState = batchMetadataOperationState
    val isLibrarySelectionActive = librarySelectionUi.state.isActive
    val shouldShowBottomMiniPlayer = currentSong != null &&
            !isLibrarySelectionActive &&
            !isFolderScreenVisible &&
            !isDiagnosticsScreenVisible &&
            !isEqualizerScreenVisible &&
            !isStatisticsScreenVisible &&
            !isListeningHistoryImportVisible &&
            !isListeningHistoryReconciliationVisible &&
            !isSettingsScreenVisible &&
            !isTipsHelpScreenVisible &&
            !isAboutScreenVisible &&
            selectedSongForTagEdit == null &&
            selectedBatchEditorState == null &&
            selectedBatchExecutionState == null
    val shouldShowBottomNavigation = shouldShowPrimaryBottomNavigation(
        isPlayerExpanded = isPlayerExpanded,
        isFolderScreenVisible = isFolderScreenVisible,
        isDiagnosticsScreenVisible = isDiagnosticsScreenVisible,
        isEqualizerScreenVisible = isEqualizerScreenVisible,
        isStatisticsScreenVisible = isStatisticsScreenVisible,
        isListeningHistoryImportVisible = isListeningHistoryImportVisible,
        isListeningHistoryReconciliationVisible =
            isListeningHistoryReconciliationVisible,
        isSettingsScreenVisible = isSettingsScreenVisible ||
            isTipsHelpScreenVisible || isAboutScreenVisible,
        isTagEditorVisible = selectedSongForTagEdit != null ||
                selectedBatchEditorState != null ||
                selectedBatchExecutionState != null,
        isLibrarySelectionActive = isLibrarySelectionActive
    )
    val shouldShowMetadataEditor = selectedBatchExecutionState != null ||
        selectedBatchEditorState != null || selectedSongForTagEdit != null
    val shouldComposePlayerOverlays = selectedSongForTagEdit == null && selectedBatchEditorState == null
    val navigationBarInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val onOpenMiniUpNext: () -> Unit = {
        selectedLibraryTab = LibraryTab.QUEUE
        navigationState.clearArtist()
        navigationState.clearAlbum()
        clearPlaylistSelection()
        mainDestination = MainDestination.LIBRARY
    }
    val onShowQueueHub: () -> Unit = { isQueueHubVisible = true }
    val onOpenLyricsSettings: () -> Unit = {
        lyricsTransitionState.snapToExpanded()
        playerMorphState.collapse()
        isSettingsScreenVisible = true
    }
    val onShowExpandedMore: () -> Unit = {
        if (canPresentNowPlayingMore) {
            currentSong?.let(overlayState::openNowPlayingMore)
        }
    }
    val onMainDestinationSelected: (MainDestination) -> Unit = { destination ->
        val targetEntity = when (destination) {
            MainDestination.SEARCH -> LibrarySelectionEntity.SONG
            MainDestination.LIBRARY -> selectedLibraryTab.selectionEntity()
            else -> null
        }
        if (librarySelectionUi.state.entity != targetEntity) {
            librarySelectionUi.onClear()
        }
        navigationState.clearArtist()
        navigationState.clearAlbum()
        selectedGenreKey = null
        clearPlaylistSelection()
        if (destination == MainDestination.SEARCH) {
            selectedLibraryTab = LibraryTab.SONGS
        }
        if (destination != MainDestination.SEARCH) {
            searchQuery = ""
        }
        mainDestination = destination
    }

    CompositionLocalProvider(
        LocalAppShellAccent provides appShellAccent,
        LocalAppShellChartAccent provides appShellChartAccent,
        LocalLibrarySelectionUi provides librarySelectionUi.copy(
            headerState = librarySelectionHeaderState,
            onPlayNext = { selectedSongs ->
                queueSnackbarActions.playNextSongs(librarySelectionSource, selectedSongs)
            },
            onAddToQueue = { selectedSongs ->
                queueSnackbarActions.addSongsToQueue(librarySelectionSource, selectedSongs)
            }
        )
    ) {
                val shellLayout = musicShellLayout(
                    maxWidth, maxHeight, topInsetAlreadyReserved = directStatusVisible
                )
                val defaultLayout = defaultPlayerLayout(
                    maxWidth, maxHeight, selectedModernPlayerAppearance,
                    topInsetAlreadyReserved = directStatusVisible,
                    preferredLandscapeLayout = selectedModernLandscapeLayout
                )
                val targetBottomContentPadding = shellLayout.bottomContentPadding(
                    compactNavigationBarInset = navigationBarInset,
                    navigationVisible = shouldShowBottomNavigation,
                    miniPlayerVisible = shouldShowBottomMiniPlayer,
                    selectionActive = isLibrarySelectionActive,
                    sleepTimerActive = isSleepTimerActive
                ) + if (resolvedPlayerPresentation.identity == PlayerPresentationIdentity.PocketHorizon && shouldShowBottomMiniPlayer)
                    io.github.rsgarrido.sazanami.ui.player.pockethorizon.pocketHorizonMiniExtraHeight(playerDensity.fontScale) else 0.dp
                val playerPresentation = prepareMusicPlayerPresentation(
                    songs = songs,
                    currentSong = currentSong,
                    previousPreviewSong = previousPreviewSong,
                    nextPreviewSong = nextPreviewSong,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    playbackProgressUiState = playbackProgressUiState,
                    lyricsPlaybackUiState = lyricsPlaybackUiState,
                    onSongClick = onSongClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    onSuspendLyricsAutoFollow = onSuspendLyricsAutoFollow,
                    onReturnLyricsToCurrentLine = onReturnLyricsToCurrentLine,
                    onRescanLyrics = onRescanLyrics,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    queuedSongs = queuedSongs,
                    upcomingSongs = upcomingSongs,
                    playbackQueueHubUiState = playbackQueueHubUiState,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    isSleepTimerActive = isSleepTimerActive,
                    sleepTimerDisplayText = sleepTimerDisplayText,
                    playerPresentation = resolvedPlayerPresentation,
                    pocketAdvanceNavigationState = pocketAdvanceNavigationState,
                    pocketHorizonNavigationState = pocketHorizonNavigationState,
                    playbackState = playbackState,
                    libraryState = libraryState,
                    songSortState = selectedSongSortState,
                    isLibraryReady = !isLibraryLoading && !isLibraryRefreshing && libraryErrorMessage == null,
                    artistSortState = selectedArtistSortState,
                    albumSortState = selectedAlbumSortState,
                    playerPresentationEpoch = playerBoundary.epoch,
                    playerPresentationInputEnabled = playerBoundary.inputEnabled,
                    selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                    selectedModernPlayerAppearance = selectedModernPlayerAppearance,
                    playerMorphState = playerMorphState,
                    lyricsTransitionState = lyricsTransitionState,
                    isLyricsVisible = isLyricsVisible,
                    isExpandedUpNextSheetVisible = isExpandedUpNextSheetVisible,
                    isQueueHubVisible = isQueueHubVisible,
                    isNowPlayingMorePresented = isNowPlayingMorePresented,
                    isCreatePlaylistDialogVisible = isCreatePlaylistDialogVisible,
                    hasPendingPlaylistAdd = hasPendingPlaylistAdd,
                    hasPendingBulkPlaylistAdd = hasPendingBulkPlaylistAdd,
                    isSleepTimerDialogVisible = isSleepTimerDialogVisible,
                    isArtworkViewerVisible = overlayState.isArtworkViewerVisible.value,
                    shouldShowBottomMiniPlayer = shouldShowBottomMiniPlayer,
                    shouldShowMetadataEditor = shouldShowMetadataEditor,
                    shouldComposePlayerOverlays = shouldComposePlayerOverlays,
                    targetBottomContentPadding = targetBottomContentPadding,
                    bodyPresentation = bodyPresentation,
                    metadataEditorContent = metadataEditorContent,
                    metadataDialogContent = metadataDialogContent,
                    transientOverlayContent = transientOverlayContent,
                    bottomNavigationContent = {
                        AppNavigationChrome(
                            layout = shellLayout,
                            visible = shouldShowBottomNavigation,
                            selectedDestination = mainDestination,
                            onDestinationSelected = onMainDestinationSelected
                        )
                    },
                    onOpenMiniUpNext = onOpenMiniUpNext,
                    onShowQueueHub = onShowQueueHub,
                    onOpenSleepTimer = onOpenSleepTimer,
                    onOpenLyricsSettings = onOpenLyricsSettings,
                    onShowExpandedMore = onShowExpandedMore,
                    onOpenCurrentAlbumClick = ::openCurrentAlbum,
                    onOpenCurrentArtistClick = onOpenCurrentArtistClick,
                    onTrackInfoClick = onTrackInfoClick,
                    onViewArtwork = onViewArtwork,
                    shellLayout = shellLayout,
                    defaultPlayerLayout = defaultLayout,
                )
                PlayerMorphHost(
                    morphState = playerMorphState,
                    modifier = Modifier.fillMaxSize(),
                    measurementIdentity = playerBoundary.epoch,
                    layoutBoundary = shellLayout to
                        defaultLayout.takeIf {
                            resolvedPlayerPresentation.identity == PlayerPresentationIdentity.Existing(PlayerTheme.DEFAULT)
                        }
                ) { playerEndpointBounds ->
                    playerPresentation(playerEndpointBounds)
                }
            }
        }
    }
}
}

internal fun dismissExpandedPlayerPresentation(
    resetLyricsPresentation: () -> Unit,
    collapsePlayer: () -> Unit
) {
    resetLyricsPresentation()
    collapsePlayer()
}

internal fun LibraryTab.selectionEntity(): LibrarySelectionEntity? = when (this) {
    LibraryTab.SONGS,
    LibraryTab.FAVORITES,
    LibraryTab.RATED,
    LibraryTab.RECENTLY_ADDED,
    LibraryTab.RECENTLY_PLAYED,
    LibraryTab.MOST_PLAYED,
    LibraryTab.FOLDERS -> LibrarySelectionEntity.SONG
    LibraryTab.ALBUMS -> LibrarySelectionEntity.ALBUM
    else -> null
}

internal fun shouldShowPrimaryBottomNavigation(
    isPlayerExpanded: Boolean,
    isFolderScreenVisible: Boolean,
    isDiagnosticsScreenVisible: Boolean,
    isEqualizerScreenVisible: Boolean,
    isStatisticsScreenVisible: Boolean,
    isListeningHistoryImportVisible: Boolean,
    isListeningHistoryReconciliationVisible: Boolean,
    isSettingsScreenVisible: Boolean,
    isTagEditorVisible: Boolean,
    isLibrarySelectionActive: Boolean = false
): Boolean = !isPlayerExpanded &&
        !isFolderScreenVisible &&
        !isDiagnosticsScreenVisible &&
        !isEqualizerScreenVisible &&
        !isStatisticsScreenVisible &&
        !isListeningHistoryImportVisible &&
        !isListeningHistoryReconciliationVisible &&
        !isSettingsScreenVisible &&
        !isTagEditorVisible &&
        !isLibrarySelectionActive
