package io.github.rsgarrido.sazanami.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import io.github.rsgarrido.sazanami.data.EditableSongTags
import io.github.rsgarrido.sazanami.data.BatchArtworkReference
import io.github.rsgarrido.sazanami.data.BatchMetadataEditorState
import io.github.rsgarrido.sazanami.data.BatchMetadataOperationState
import io.github.rsgarrido.sazanami.data.BatchMetadataPlan
import io.github.rsgarrido.sazanami.data.deriveBatchMetadataEditorState
import io.github.rsgarrido.sazanami.data.LibraryFolder
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.PlayerTheme
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
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphHost
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.mini.DefaultMiniPlayerMorphCallbacks
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultMorphMinimumDragRangePx
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultMorphMetadataOwner
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.defaultMorphMetadataOwner
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.classicwheel.classicWheelMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ownsNowPlayingMorphContent
import io.github.rsgarrido.sazanami.ui.player.retrorack.resolveRetroRackMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.retrorack.retroRackMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.resolvePocketFlipMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketflip.resolvePocketFlipSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketflip.pocketFlipMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.resolvePocketCassetteMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.resolvePocketCassetteSharedGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.pocketCassetteMorphTravelDistance
import io.github.rsgarrido.sazanami.ui.player.classicwheel.resolveClassicWheelSharedGeometry
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgress
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
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerScreenState
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerUiActions
import io.github.rsgarrido.sazanami.ui.queue.rememberQueueSnackbarActions
import io.github.rsgarrido.sazanami.ui.tageditor.DiscardTagChangesDialog
import io.github.rsgarrido.sazanami.ui.tageditor.BatchMetadataEditorScreen
import io.github.rsgarrido.sazanami.ui.tageditor.BatchMetadataEditorContext
import io.github.rsgarrido.sazanami.ui.tageditor.BatchMetadataExecutionScreen
import io.github.rsgarrido.sazanami.ui.tageditor.BatchSongSelectionScreen
import io.github.rsgarrido.sazanami.ui.tageditor.TagEditorScreen
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
import kotlin.math.abs
import java.time.LocalDate


@Composable
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
    mostPlayedSongs: List<Song>,
    listeningAnalyticsUiState: ListeningAnalyticsUiState,
    onListeningAnalyticsActiveChanged: (Boolean) -> Unit,
    onListeningAnalyticsPresetSelected: (AnalyticsRangePreset) -> Unit,
    onListeningAnalyticsCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    onRetryListeningAnalytics: () -> Unit,
    onListeningAnalyticsTrendMetricSelected: (ListeningTrendMetric) -> Unit,
    onListeningAnalyticsRankingCategorySelected: (ListeningRankingCategory) -> Unit,
    spotifyImportUiState: SpotifyImportUiState,
    reconciliationUiState: ListeningHistoryReconciliationUiState,
    reconciliationActions: ListeningHistoryReconciliationUiActions,
    spotifyImportActions: SpotifyImportUiActions
) {
    val context = LocalContext.current
    val navigationState = rememberMusicNavigationState()
    var mainDestination by navigationState.mainDestination
    var selectedLibraryTab by navigationState.selectedLibraryTab
    var playbackLaunchContext by navigationState.playbackLaunchContext
    var selectedArtistName by navigationState.selectedArtistName
    var selectedAlbumKey by navigationState.selectedAlbumKey
    var selectedGenreKey by navigationState.selectedGenreKey
    var selectedPlaylistId by navigationState.selectedPlaylistId
    var searchQuery by navigationState.searchQuery
    var selectedSongFilterState by navigationState.selectedSongFilterState
    var selectedSongSortState by navigationState.selectedSongSortState
    var selectedArtistSortState by navigationState.selectedArtistSortState
    var selectedAlbumSortState by navigationState.selectedAlbumSortState
    var selectedFavoriteSortState by navigationState.selectedFavoriteSortState

    val overlayState = rememberMusicOverlayState()
    val settingsScrollState = rememberScrollState()
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
    var isDiagnosticsScreenVisible by overlayState.isDiagnosticsScreenVisible
    var isEqualizerScreenVisible by overlayState.isEqualizerScreenVisible
    var isStatisticsScreenVisible by overlayState.isStatisticsScreenVisible
    var isListeningHistoryImportVisible by overlayState.isListeningHistoryImportVisible
    var isListeningHistoryReconciliationVisible by
    overlayState.isListeningHistoryReconciliationVisible
    var isExpandedUpNextSheetVisible by overlayState.isExpandedUpNextSheetVisible
    var isQueueHubVisible by overlayState.isQueueHubVisible
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
                    snackbarHostState.showSnackbar("Unable to add playlist to queue")
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
            selectedAlbumKey = null
            selectedLibraryTab = LibraryTab.ALBUMS
        }
        batchMetadataEditorContext = BatchMetadataEditorContext.SongSelection
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
            searchQuery = searchQuery
        )
    }

    fun clearPlaylistSelection() {
        val hadSelection = selectedPlaylistId != null || selectedPlaylistStateId != null
        selectedPlaylistId = null
        if (hadSelection) {
            onPlaylistCleared()
        }
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
            playlistIds = playlists.mapTo(mutableSetOf()) { playlist -> playlist.playlistId }
        )

        lyricsTransitionState.snapToExpanded()
        selectedArtistName = null
        selectedAlbumKey = null
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
                selectedLibraryTab = LibraryTab.ALBUMS
                selectedAlbumKey = validContext.albumKey
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.ArtistDetail -> {
                selectedLibraryTab = LibraryTab.ARTISTS
                selectedArtistName = validContext.artistName
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
                    selectedPlaylistId = playlist.playlistId
                    onPlaylistSelected(playlist)
                }
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
                playerMorphState.shouldConsumeBack ||
                isFolderScreenVisible ||
                isDiagnosticsScreenVisible ||
                isEqualizerScreenVisible ||
                isStatisticsScreenVisible ||
                isListeningHistoryImportVisible ||
                isListeningHistoryReconciliationVisible ||
                isSettingsScreenVisible ||
                selectedArtistName != null ||
                selectedAlbumKey != null ||
                selectedGenreKey != null ||
                selectedPlaylistId != null ||
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

            isLyricsVisible -> {
                lyricsTransitionState.returnToExpanded()
            }

            isExpandedUpNextSheetVisible -> {
                isExpandedUpNextSheetVisible = false
            }

            isQueueHubVisible -> {
                isQueueHubVisible = false
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

            selectedAlbumKey != null -> {
                selectedAlbumKey = null
                if (selectedArtistName != null) {
                    selectedLibraryTab = LibraryTab.ARTISTS
                }
            }

            selectedArtistName != null -> {
                selectedArtistName = null
            }

            selectedGenreKey != null -> {
                selectedGenreKey = null
            }

            selectedPlaylistId != null -> {
                clearPlaylistSelection()
            }

            mainDestination != MainDestination.HOME -> {
                mainDestination = MainDestination.HOME
            }
        }
    }

    val appShellAccent = rememberAppShellAccent(
        playerTheme = selectedPlayerTheme,
        tokens = selectedPlayerThemeTokens
    )
    CompositionLocalProvider(LocalAppShellAccent provides appShellAccent) {
        PlayerMorphHost(
            morphState = playerMorphState,
            modifier = modifier
                .fillMaxSize()
                .appShellBackground()
        ) { playerEndpointBounds ->
            val defaultMorphBounds = remember { DefaultPlayerMorphBounds() }
            val classicMorphBounds = remember { ClassicWheelMorphBounds() }
            val classicWheelMenuState = remember(
                selectedPlayerTheme,
                playerMorphState.shouldComposeExpanded
            ) {
                ClassicWheelMenuState()
            }
            val retroRackMorphBounds = remember { RetroRackMorphBounds() }
            val pocketFlipMorphBounds = remember { PocketFlipMorphBounds() }
            val pocketCassetteMorphBounds = remember { PocketCassetteMorphBounds() }
            val defaultMorphGeometry = resolveDefaultPlayerMorphGeometry(
                progress = playerMorphState.progress,
                endpointBounds = playerEndpointBounds,
                elementBounds = defaultMorphBounds
            )
            val defaultMorphOwnsVisuals =
                selectedPlayerTheme == PlayerTheme.DEFAULT &&
                        defaultMorphMetadataOwner(
                            isMorphActive = !playerMorphState.isCollapsedAndIdle,
                            geometryReady = defaultMorphGeometry != null
                        ) == DefaultMorphMetadataOwner.Morph
            val classicWheelMorphOwnsVisuals =
                selectedPlayerTheme == PlayerTheme.CLASSIC_WHEEL &&
                        classicWheelMenuState.currentScreen.ownsNowPlayingMorphContent() &&
                        !playerMorphState.isCollapsedAndIdle &&
                        resolveClassicWheelMorphGeometry(
                            playerMorphState.progress,
                            playerEndpointBounds
                        ) != null && resolveClassicWheelSharedGeometry(
                    playerMorphState.progress,
                    classicMorphBounds
                ) != null
            val retroRackMorphOwnsVisuals = selectedPlayerTheme == PlayerTheme.RETRO_RACK &&
                    !playerMorphState.isCollapsedAndIdle &&
                    resolveRetroRackMorphGeometry(playerMorphState.progress, playerEndpointBounds) != null
            val pocketFlipMorphOwnsVisuals =
                selectedPlayerTheme == PlayerTheme.POCKET_FLIP &&
                        !playerMorphState.isCollapsedAndIdle &&
                        resolvePocketFlipMorphGeometry(
                            playerMorphState.progress,
                            playerEndpointBounds
                        ) != null &&
                        resolvePocketFlipSharedGeometry(
                            playerMorphState.progress,
                            pocketFlipMorphBounds
                        ) != null
            val pocketCassetteMorphOwnsVisuals =
                selectedPlayerTheme == PlayerTheme.POCKET_CASSETTE &&
                        !playerMorphState.isCollapsedAndIdle &&
                        resolvePocketCassetteMorphGeometry(
                            playerMorphState.progress,
                            playerEndpointBounds
                        ) != null &&
                        resolvePocketCassetteSharedGeometry(
                            playerMorphState.progress,
                            pocketCassetteMorphBounds
                        ) != null
            val classicMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
                DefaultMiniPlayerMorphCallbacks(
                    onDragStart = {
                        playerMorphState.beginDragWithRange(
                            classicWheelMorphTravelDistance(playerEndpointBounds)
                        )
                    },
                    onDragBy = playerMorphState::dragBy,
                    onDragEnd = playerMorphState::endDrag,
                    onDragCancel = playerMorphState::cancelDrag
                )
            }
            val retroRackMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
                DefaultMiniPlayerMorphCallbacks(
                    onDragStart = { playerMorphState.beginDragWithRange(retroRackMorphTravelDistance(playerEndpointBounds)) },
                    onDragBy = playerMorphState::dragBy,
                    onDragEnd = playerMorphState::endDrag,
                    onDragCancel = playerMorphState::cancelDrag
                )
            }
            val pocketFlipMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
                DefaultMiniPlayerMorphCallbacks(
                    onDragStart = {
                        playerMorphState.beginDragWithRange(
                            pocketFlipMorphTravelDistance(playerEndpointBounds)
                        )
                    },
                    onDragBy = playerMorphState::dragBy,
                    onDragEnd = playerMorphState::endDrag,
                    onDragCancel = playerMorphState::cancelDrag
                )
            }
            val pocketCassetteMiniMorphCallbacks = remember(playerMorphState, playerEndpointBounds) {
                DefaultMiniPlayerMorphCallbacks(
                    onDragStart = {
                        playerMorphState.beginDragWithRange(
                            pocketCassetteMorphTravelDistance(playerEndpointBounds)
                        )
                    },
                    onDragBy = playerMorphState::dragBy,
                    onDragEnd = playerMorphState::endDrag,
                    onDragCancel = playerMorphState::cancelDrag
                )
            }
            val defaultMiniMorphCallbacks = remember(
                playerMorphState,
                playerEndpointBounds
            ) {
                DefaultMiniPlayerMorphCallbacks(
                    onDragStart = {
                        val miniBounds = defaultMorphBounds.miniSurface ?: (
                                playerEndpointBounds.mini as?
                                        PlayerBoundsMeasurement.Measured
                                )?.bounds
                        val expandedBounds = (
                                playerEndpointBounds.expanded as?
                                        PlayerBoundsMeasurement.Measured
                                )?.bounds
                        val travelDistance = if (miniBounds != null &&
                            expandedBounds != null
                        ) {
                            abs(miniBounds.top - expandedBounds.top)
                        } else {
                            DefaultMorphMinimumDragRangePx
                        }
                        playerMorphState.beginDragWithRange(
                            progressRangePx = travelDistance.coerceAtLeast(
                                DefaultMorphMinimumDragRangePx
                            )
                        )
                    },
                    onDragBy = playerMorphState::dragBy,
                    onDragEnd = playerMorphState::endDrag,
                    onDragCancel = playerMorphState::cancelDrag
                )
            }
            val selectedSongForTagEdit = songPendingTagEdit
            val selectedBatchEditorState = batchMetadataEditorState
            val selectedBatchExecutionState = batchMetadataOperationState
            val shouldShowBottomMiniPlayer = currentSong != null &&
                    !isFolderScreenVisible &&
                    !isDiagnosticsScreenVisible &&
                    !isEqualizerScreenVisible &&
                    !isStatisticsScreenVisible &&
                    !isListeningHistoryImportVisible &&
                    !isListeningHistoryReconciliationVisible &&
                    !isSettingsScreenVisible &&
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
                isSettingsScreenVisible = isSettingsScreenVisible,
                isTagEditorVisible = selectedSongForTagEdit != null ||
                        selectedBatchEditorState != null ||
                        selectedBatchExecutionState != null
            )
            LaunchedEffect(shouldShowBottomMiniPlayer) {
                if (!shouldShowBottomMiniPlayer) {
                    playerEndpointBounds.markMiniStale()
                }
            }
            LaunchedEffect(selectedPlayerTheme) {
                playerEndpointBounds.markMiniStale()
                defaultMorphBounds.clearExpanded()
            }
            val navigationBarInset = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
            val bottomContentPadding = navigationBarInset +
                    (if (shouldShowBottomNavigation) AppBottomNavigationHeight else 0.dp) +
                    when {
                        !shouldShowBottomMiniPlayer -> 24.dp
                        isSleepTimerActive -> 176.dp
                        else -> 96.dp
                    }

            if (selectedBatchExecutionState != null) {
                BatchMetadataExecutionScreen(
                    state = selectedBatchExecutionState,
                    onCancel = batchMetadataActions.cancel,
                    onRetryFailed = batchMetadataActions.retryFailed,
                    onContinueUnprocessed = batchMetadataActions.continueUnprocessed,
                    onRetryRefresh = batchMetadataActions.retryRefresh,
                    onDone = ::closeBatchMetadataResults,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            } else if (selectedBatchEditorState != null) {
                BatchMetadataEditorScreen(
                    state = selectedBatchEditorState,
                    context = batchMetadataEditorContext,
                    onStateChanged = { updated -> batchMetadataEditorState = updated },
                    onChooseArtwork = {
                        batchArtworkPickerLauncher.launch(arrayOf("image/*"))
                    },
                    onApply = batchMetadataActions.apply,
                    onBack = {
                        batchMetadataEditorState = null
                        batchMetadataEditorContext = BatchMetadataEditorContext.SongSelection
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            } else if (selectedSongForTagEdit != null) {
                val initialEditableTags = remember(
                    selectedSongForTagEdit.id,
                    selectedSongForTagEdit.filePath
                ) {
                    onReadEditableSongTags(selectedSongForTagEdit)
                }

                val unsupportedTagEditingMessage = remember(
                    selectedSongForTagEdit.id,
                    selectedSongForTagEdit.filePath
                ) {
                    onGetUnsupportedTagEditingMessage(selectedSongForTagEdit)
                }

                TagEditorScreen(
                    song = selectedSongForTagEdit,
                    initialTags = initialEditableTags,
                    isSaving = isTagSaveInProgress,
                    unsupportedMessage = unsupportedTagEditingMessage,
                    isCurrentSong = currentSong?.id == selectedSongForTagEdit.id,
                    selectedArtworkUri = selectedArtworkUriForTagEdit,
                    onChangeArtworkClick = {
                        artworkPickerLauncher.launch("image/*")
                    },
                    onBackClick = {
                        requestCloseTagEditor()
                    },
                    onSaveClick = { editedTags ->
                        tagEditorActions.saveTags(
                            selectedSongForTagEdit,
                            editedTags,
                            selectedArtworkUriForTagEdit
                        )
                    },
                    onUnsavedChangesChanged = { hasChanges ->
                        hasUnsavedTagChanges = hasChanges
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            } else {
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
                    selectedArtistName = selectedArtistName,
                    selectedAlbumKey = selectedAlbumKey,
                    selectedGenreKey = selectedGenreKey,
                    selectedPlaylistId = selectedPlaylistId,
                    searchQuery = searchQuery,
                    selectedSongFilterState = selectedSongFilterState,
                    selectedSongSortState = selectedSongSortState,
                    selectedArtistSortState = selectedArtistSortState,
                    selectedAlbumSortState = selectedAlbumSortState,
                    selectedFavoriteSortState = selectedFavoriteSortState,
                    recentlyAddedSongIds = recentlyAddedSongIds,
                    isPlayerExpanded = isPlayerExpanded,
                    isFolderScreenVisible = isFolderScreenVisible,
                    isSettingsScreenVisible = isSettingsScreenVisible,
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
                    queueSnackbarActions = queueSnackbarActions,
                    onSettingsClick = {
                        isSettingsScreenVisible = true
                    },
                    onOpenLibrary = { tab ->
                        selectedLibraryTab = tab
                        selectedArtistName = null
                        selectedAlbumKey = null
                        selectedGenreKey = null
                        clearPlaylistSelection()
                        searchQuery = ""
                        mainDestination = MainDestination.LIBRARY
                    },
                    onFolderBackClick = {
                        isFolderScreenVisible = false
                        isSettingsScreenVisible = true
                    },
                    onSettingsBackClick = {
                        closeSettings()
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
                        playerMorphState.expand()
                    },
                    onMiniPlayerUpNextClick = {
                        selectedLibraryTab = LibraryTab.QUEUE
                        selectedArtistName = null
                        selectedAlbumKey = null
                        selectedGenreKey = null
                        clearPlaylistSelection()
                        mainDestination = MainDestination.LIBRARY
                    },
                    onSongClick = { song, playbackContext ->
                        recordPlaybackLaunchContext()
                        onSongClick(song, playbackContext)
                    },
                    onPlaySongsClick = { playbackContext, shuffleMode ->
                        recordPlaybackLaunchContext()
                        onPlaySongsClick(playbackContext, shuffleMode)
                    },
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onAddToPlaylistClick = { song ->
                        songPendingPlaylistAdd = song
                    },
                    onAddSongsToPlaylistClick = { songs ->
                        songsPendingPlaylistAdd = songs
                    },
                    onArtistSelected = { artistName ->
                        selectedArtistName = artistName
                    },
                    onBackFromArtist = {
                        selectedArtistName = null
                    },
                    onAlbumSelected = { albumKey ->
                        selectedAlbumKey = albumKey
                        selectedLibraryTab = LibraryTab.ALBUMS
                    },
                    onBackFromAlbum = {
                        selectedAlbumKey = null
                        if (selectedArtistName != null) {
                            selectedLibraryTab = LibraryTab.ARTISTS
                        }
                    },
                    onGenreSelected = { genreKey ->
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
                    onCreatePlaylistClick = { folderId ->
                        playlistCreationFolderId = folderId
                        isCreatePlaylistDialogVisible = true
                    },
                    onCreatePlaylistFolderClick = onCreatePlaylistFolderClick,
                    onRenamePlaylistFolderClick = onRenamePlaylistFolderClick,
                    onDeletePlaylistFolderClick = onDeletePlaylistFolderClick,
                    onMovePlaylistToFolderClick = onMovePlaylistToFolderClick,
                    onRenamePlaylistClick = onRenamePlaylistClick,
                    onPlaylistClick = { playlist ->
                        selectedPlaylistId = playlist.playlistId
                        onPlaylistSelected(playlist)
                    },
                    onDeletePlaylistClick = onDeletePlaylistClick,
                    onExportPlaylistClick = onExportPlaylistClick,
                    onAddPlaylistToQueueClick = addPlaylistToQueue,
                    onImportPlaylistClick = onImportPlaylistClick,
                    onChangePlaylistArtwork = onChangePlaylistArtwork,
                    onResetPlaylistArtwork = onResetPlaylistArtwork,
                    onBackFromPlaylist = {
                        clearPlaylistSelection()
                    },
                    onRemovePlaylistSongClick = { playlistSong ->
                        playlistSnackbarActions.removePlaylistSong(playlistSong)
                    },
                    onReorderPlaylistSongs = onReorderPlaylistSongs,
                    onAddSongsToCurrentPlaylistClick = { playlist, songs ->
                        playlistSnackbarActions.addSongsToPlaylist(playlist, songs)
                    },
                    onEditSongTagsClick = { song ->
                        isTagSaveInProgress = false
                        hasUnsavedTagChanges = false
                        isDiscardTagChangesDialogVisible = false
                        selectedArtworkUriForTagEdit = null
                        songPendingTagEdit = song
                    },
                    onEditAlbumMetadataClick = { album: LibraryAlbumGroup ->
                        prepareBatchMetadataEditor(
                            album.metadataEditingSongs(),
                            BatchMetadataEditorContext.Album(
                                albumKey = album.key,
                                title = album.title,
                                artworkUri = album.songs.firstOrNull()?.albumArtUri?.toString()
                            )
                        )
                    },
                    onBatchMetadataClick = {
                        isBatchSongSelectionVisible = true
                    },
                    isSleepTimerActive = isSleepTimerActive,
                    sleepTimerDisplayText = sleepTimerDisplayText,
                    onSleepTimerClick = {
                        isSleepTimerDialogVisible = true
                    },
                    recentlyPlayedSongs = recentlyPlayedSongs,
                    recentlyAddedLibrarySongs = recentlyAddedLibrarySongs,
                    mostPlayedSongs = mostPlayedSongs,
                    selectedPlayerTheme = selectedPlayerTheme,
                    selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                    onPlayerThemeSelected = onPlayerThemeSelected,
                    onUpdatePlayerThemeTokenOverride = onUpdatePlayerThemeTokenOverride,
                    onResetPlayerThemeTokenOverrides = onResetPlayerThemeTokenOverrides,
                    selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                    onModernArtworkTransitionStyleSelected = onModernArtworkTransitionStyleSelected,
                    selectedModernPlayerAppearance = selectedModernPlayerAppearance,
                    onModernPlayerAppearanceChanged = onModernPlayerAppearanceChanged,
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

            if (shouldShowBottomMiniPlayer) {
                PlaybackProgress(playbackProgressUiState) { progress ->
                    MiniPlayerSection(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        currentPosition = progress.currentPosition,
                        duration = progress.duration,
                        selectedPlayerTheme = selectedPlayerTheme,
                        selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                        playerMorphState = playerMorphState,
                        favoriteMembershipKeys = favoriteMembershipKeys,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekChange = onSeekChange,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                        onExpandClick = {
                            playerMorphState.expand()
                        },
                        onOpenUpNextClick = {
                            selectedLibraryTab = LibraryTab.QUEUE
                            selectedArtistName = null
                            selectedAlbumKey = null
                            clearPlaylistSelection()
                            mainDestination = MainDestination.LIBRARY
                        },
                        onOpenQueueHubClick = {
                            isQueueHubVisible = true
                        },
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        isSleepTimerActive = isSleepTimerActive,
                        sleepTimerDisplayText = sleepTimerDisplayText,
                        onSleepTimerClick = {
                            isSleepTimerDialogVisible = true
                        },
                        onMiniPlayerBoundsChanged = playerEndpointBounds::updateMini,
                        defaultMorphBounds = defaultMorphBounds,
                        classicMorphBounds = classicMorphBounds,
                        retroRackMorphBounds = retroRackMorphBounds,
                        pocketFlipMorphBounds = pocketFlipMorphBounds,
                        pocketCassetteMorphBounds = pocketCassetteMorphBounds,
                        defaultMorphCallbacks = when (selectedPlayerTheme) {
                            PlayerTheme.DEFAULT -> defaultMiniMorphCallbacks
                            PlayerTheme.CLASSIC_WHEEL -> classicMiniMorphCallbacks
                            PlayerTheme.RETRO_RACK -> retroRackMiniMorphCallbacks
                            PlayerTheme.POCKET_FLIP -> pocketFlipMiniMorphCallbacks
                            PlayerTheme.POCKET_CASSETTE -> pocketCassetteMiniMorphCallbacks
                            else -> null
                        },
                        morphOwnsVisuals = defaultMorphOwnsVisuals ||
                                classicWheelMorphOwnsVisuals ||
                                retroRackMorphOwnsVisuals ||
                                pocketFlipMorphOwnsVisuals ||
                                pocketCassetteMorphOwnsVisuals,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = AppBottomNavigationHeight)
                            .playerEndpointInput(playerMorphState.isCollapsedAndIdle)
                    )
                }
            }

            if (shouldShowBottomNavigation) {
                AppBottomNavigation(
                    selectedDestination = mainDestination,
                    onDestinationSelected = { destination ->
                        selectedArtistName = null
                        selectedAlbumKey = null
                        selectedGenreKey = null
                        clearPlaylistSelection()
                        if (destination == MainDestination.SEARCH) {
                            selectedLibraryTab = LibraryTab.SONGS
                        }
                        if (destination != MainDestination.SEARCH) {
                            searchQuery = ""
                        }
                        mainDestination = destination
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }

            if (isBatchSongSelectionVisible) {
                BatchSongSelectionScreen(
                    songs = songs,
                    isPreparing = isBatchPreparationInProgress,
                    onDismiss = {
                        if (!isBatchPreparationInProgress) {
                            isBatchSongSelectionVisible = false
                        }
                    },
                    onContinue = { selectedSongs ->
                        prepareBatchMetadataEditor(
                            selectedSongs,
                            BatchMetadataEditorContext.SongSelection
                        )
                    }
                )
            }

            if (isDiscardTagChangesDialogVisible) {
                DiscardTagChangesDialog(
                    onDismiss = {
                        isDiscardTagChangesDialogVisible = false
                    },
                    onConfirmDiscardClick = {
                        isDiscardTagChangesDialogVisible = false
                        hasUnsavedTagChanges = false
                        selectedArtworkUriForTagEdit = null
                        songPendingTagEdit = null
                    }
                )
            }

            if (selectedSongForTagEdit == null && selectedBatchEditorState == null) {
                MusicScreenOverlays(
                    playerMorphState = playerMorphState,
                    isLyricsVisible = isLyricsVisible,
                    lyricsTransitionState = lyricsTransitionState,
                    currentSong = currentSong,
                    previousPreviewSong = previousPreviewSong,
                    nextPreviewSong = nextPreviewSong,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    playbackProgressUiState = playbackProgressUiState,
                    favoriteMembershipKeys = favoriteMembershipKeys,
                    isExpandedUpNextSheetVisible = isExpandedUpNextSheetVisible,
                    isQueueHubVisible = isQueueHubVisible,
                    playbackQueueHubUiState = playbackQueueHubUiState,
                    queuedSongs = queuedSongs,
                    upcomingSongs = upcomingSongs,
                    isCreatePlaylistDialogVisible = isCreatePlaylistDialogVisible,
                    createPlaylistFolderId = playlistCreationFolderId,
                    songPendingPlaylistAdd = songPendingPlaylistAdd,
                    playlists = playlists,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onSeekChange = onSeekChange,
                    lyricsPlaybackUiState = lyricsPlaybackUiState,
                    onSuspendLyricsAutoFollow = onSuspendLyricsAutoFollow,
                    onReturnLyricsToCurrentLine = onReturnLyricsToCurrentLine,
                    onRescanLyrics = onRescanLyrics,
                    onOpenLyricsSettings = {
                        lyricsTransitionState.snapToExpanded()
                        playerMorphState.collapse()
                        isSettingsScreenVisible = true
                    },
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onCollapseExpandedPlayer = {
                        playerMorphState.collapse()
                        restorePlaybackLaunchContext()
                    },
                    onShowExpandedUpNextSheet = {
                        isExpandedUpNextSheetVisible = true
                    },
                    onShowQueueHub = {
                        isQueueHubVisible = true
                    },
                    onShowExpandedSleepTimer = {
                        isSleepTimerDialogVisible = true
                    },
                    onShowExpandedMore = {
                        playerMorphState.collapse()
                        restorePlaybackLaunchContext()
                        isSettingsScreenVisible = true
                    },
                    onDismissExpandedUpNextSheet = {
                        isExpandedUpNextSheetVisible = false
                    },
                    onDismissQueueHub = {
                        isQueueHubVisible = false
                    },
                    onPlaybackQueueSelected = onPlaybackQueueSelected,
                    onSwitchSelectedPlaybackQueue = onSwitchSelectedPlaybackQueue,
                    onCreatePlaybackQueueFromCurrent = onCreatePlaybackQueueFromCurrent,
                    onRenamePlaybackQueue = onRenamePlaybackQueue,
                    onDeletePlaybackQueue = onDeletePlaybackQueue,
                    onClearPlaybackQueueMessage = onClearPlaybackQueueMessage,
                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                    onMoveQueueItemUpClick = onMoveQueueItemUpClick,
                    onMoveQueueItemDownClick = onMoveQueueItemDownClick,
                    onClearQueueClick = onClearQueueClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onDismissCreatePlaylistDialog = {
                        isCreatePlaylistDialogVisible = false
                        playlistCreationFolderId = null
                    },
                    onCreatePlaylistClick = onCreatePlaylistClick,
                    onCreatePlaylistWithSongsClick = onCreatePlaylistWithSongsClick,
                    onDismissAddToPlaylistDialog = {
                        songPendingPlaylistAdd = null
                    },
                    songsPendingPlaylistAdd = songsPendingPlaylistAdd,
                    onDismissBulkAddToPlaylistDialog = {
                        songsPendingPlaylistAdd = emptyList()
                    },
                    onAddSongToPlaylistClick = { playlist, song ->
                        playlistSnackbarActions.addSongToPlaylist(playlist, song)
                    },
                    onAddSongsToPlaylistClick = { playlist, songs ->
                        playlistSnackbarActions.addSongsToPlaylist(playlist, songs)
                    },
                    isSleepTimerDialogVisible = isSleepTimerDialogVisible,
                    isSleepTimerActive = isSleepTimerActive,
                    sleepTimerDisplayText = sleepTimerDisplayText,
                    onStartSleepTimerClick = onStartSleepTimerClick,
                    onCancelSleepTimerClick = onCancelSleepTimerClick,
                    onDismissSleepTimerDialog = {
                        isSleepTimerDialogVisible = false
                    },
                    selectedPlayerTheme = selectedPlayerTheme,
                    selectedPlayerThemeTokens = selectedPlayerThemeTokens,
                    selectedModernArtworkTransitionStyle = selectedModernArtworkTransitionStyle,
                    selectedModernPlayerAppearance = selectedModernPlayerAppearance,
                    playerEndpointBounds = playerEndpointBounds,
                    defaultMorphBounds = defaultMorphBounds,
                    classicMorphBounds = classicMorphBounds,
                    classicWheelMenuState = classicWheelMenuState,
                    retroRackMorphBounds = retroRackMorphBounds,
                    pocketFlipMorphBounds = pocketFlipMorphBounds,
                    pocketCassetteMorphBounds = pocketCassetteMorphBounds,
                    songs = songs,
                    onSongClick = onSongClick
                )
            }
        }
    }
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
    isTagEditorVisible: Boolean
): Boolean = !isPlayerExpanded &&
        !isFolderScreenVisible &&
        !isDiagnosticsScreenVisible &&
        !isEqualizerScreenVisible &&
        !isStatisticsScreenVisible &&
        !isListeningHistoryImportVisible &&
        !isListeningHistoryReconciliationVisible &&
        !isSettingsScreenVisible &&
        !isTagEditorVisible
