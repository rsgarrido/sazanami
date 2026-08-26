package com.example.cdplaya.ui

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
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.example.cdplaya.data.EditableSongTags
import com.example.cdplaya.data.LibraryFolder
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistFolder
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.TagEditorResult
import com.example.cdplaya.player.RepeatMode
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.audio.AudioOutputUiState
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.player.PlaybackShuffleMode
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.ui.library.LibraryTab
import com.example.cdplaya.ui.navigation.MainDestination
import com.example.cdplaya.ui.navigation.PlaybackLaunchContext
import com.example.cdplaya.ui.navigation.capturePlaybackLaunchContext
import com.example.cdplaya.ui.navigation.playbackLaunchContextSaver
import com.example.cdplaya.ui.navigation.withValidDetails
import com.example.cdplaya.ui.playlist.rememberPlaylistSnackbarActions
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenField
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.rememberPlayerLyricsTransitionState
import com.example.cdplaya.ui.player.PlayerMorphHost
import com.example.cdplaya.ui.player.playerEndpointInput
import com.example.cdplaya.ui.player.PlayerBoundsMeasurement
import com.example.cdplaya.ui.player.mini.DefaultMiniPlayerMorphCallbacks
import com.example.cdplaya.ui.player.modern.DefaultMorphMinimumDragRangePx
import com.example.cdplaya.ui.player.modern.DefaultMorphMetadataOwner
import com.example.cdplaya.ui.player.modern.DefaultPlayerMorphBounds
import com.example.cdplaya.ui.player.modern.defaultMorphMetadataOwner
import com.example.cdplaya.ui.player.modern.resolveDefaultPlayerMorphGeometry
import com.example.cdplaya.ui.player.classicwheel.classicWheelMorphTravelDistance
import com.example.cdplaya.ui.player.classicwheel.resolveClassicWheelMorphGeometry
import com.example.cdplaya.ui.player.classicwheel.ClassicWheelMorphBounds
import com.example.cdplaya.ui.player.retrorack.resolveRetroRackMorphGeometry
import com.example.cdplaya.ui.player.retrorack.retroRackMorphTravelDistance
import com.example.cdplaya.ui.player.retrorack.RetroRackMorphBounds
import com.example.cdplaya.ui.player.pocketflip.PocketFlipMorphBounds
import com.example.cdplaya.ui.player.pocketflip.resolvePocketFlipMorphGeometry
import com.example.cdplaya.ui.player.pocketflip.resolvePocketFlipSharedGeometry
import com.example.cdplaya.ui.player.pocketflip.pocketFlipMorphTravelDistance
import com.example.cdplaya.ui.player.pocketcassette.PocketCassetteMorphBounds
import com.example.cdplaya.ui.player.pocketcassette.resolvePocketCassetteMorphGeometry
import com.example.cdplaya.ui.player.pocketcassette.resolvePocketCassetteSharedGeometry
import com.example.cdplaya.ui.player.pocketcassette.pocketCassetteMorphTravelDistance
import com.example.cdplaya.ui.player.classicwheel.resolveClassicWheelSharedGeometry
import com.example.cdplaya.ui.state.PlaybackProgress
import com.example.cdplaya.ui.state.PlaybackProgressUiState
import com.example.cdplaya.ui.state.LibraryAppearanceUiState
import com.example.cdplaya.ui.state.LibraryRefreshSummary
import com.example.cdplaya.ui.state.ListeningAnalyticsUiState
import com.example.cdplaya.ui.statistics.ListeningAnalyticsVisibilityEffect
import com.example.cdplaya.data.AnalyticsRangePreset
import com.example.cdplaya.data.ListeningRankingCategory
import com.example.cdplaya.data.ListeningTrendMetric
import com.example.cdplaya.ui.library.LibraryViewCategory
import com.example.cdplaya.ui.library.LibraryViewOption
import com.example.cdplaya.ui.equalizer.EqualizerScreenState
import com.example.cdplaya.ui.equalizer.EqualizerUiActions
import com.example.cdplaya.ui.queue.rememberQueueSnackbarActions
import com.example.cdplaya.ui.tageditor.DiscardTagChangesDialog
import com.example.cdplaya.ui.tageditor.TagEditorScreen
import com.example.cdplaya.ui.tageditor.rememberTagEditorActions
import com.example.cdplaya.controller.SpotifyImportUiState
import com.example.cdplaya.ui.settings.SpotifyImportUiActions
import com.example.cdplaya.ui.settings.ListeningHistoryReconciliationUiActions
import com.example.cdplaya.controller.ListeningHistoryReconciliationUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.cdplaya.mediaaccess.MediaAccessState
import com.example.cdplaya.lyrics.LyricsPlaybackUiState
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
    selectedModernSeekbarStyle: ModernSeekbarStyle,
    onModernSeekbarStyleSelected: (ModernSeekbarStyle) -> Unit,
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
    val navigationState = rememberMusicNavigationState()
    var mainDestination by navigationState.mainDestination
    var selectedLibraryTab by navigationState.selectedLibraryTab
    var playbackLaunchContext by navigationState.playbackLaunchContext
    var selectedArtistName by navigationState.selectedArtistName
    var selectedAlbumFolderPath by navigationState.selectedAlbumFolderPath
    var selectedPlaylistId by navigationState.selectedPlaylistId
    var searchQuery by navigationState.searchQuery
    var selectedSongSortOption by navigationState.selectedSongSortOption
    var selectedArtistSortOption by navigationState.selectedArtistSortOption
    var selectedAlbumSortOption by navigationState.selectedAlbumSortOption
    var selectedFavoriteSortOption by navigationState.selectedFavoriteSortOption

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
    var isCreatePlaylistDialogVisible by overlayState.isCreatePlaylistDialogVisible
    var playlistCreationFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isSleepTimerDialogVisible by overlayState.isSleepTimerDialogVisible
    var songPendingPlaylistAdd by remember { mutableStateOf<Song?>(null) }
    var songsPendingPlaylistAdd by remember { mutableStateOf<List<Song>>(emptyList()) }
    var songPendingTagEdit by remember { mutableStateOf<Song?>(null) }

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
            selectedAlbumFolderPath = selectedAlbumFolderPath,
            selectedArtistName = selectedArtistName,
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
            albumFolderPaths = songs.mapTo(mutableSetOf()) { song -> song.folderPath },
            artistNames = songs.mapTo(mutableSetOf()) { song ->
                song.artist.ifBlank { "Unknown Artist" }
            },
            playlistIds = playlists.mapTo(mutableSetOf()) { playlist -> playlist.playlistId }
        )

        lyricsTransitionState.snapToExpanded()
        selectedArtistName = null
        selectedAlbumFolderPath = null
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
                selectedAlbumFolderPath = validContext.folderPath
                searchQuery = ""
                mainDestination = MainDestination.LIBRARY
            }

            is PlaybackLaunchContext.ArtistDetail -> {
                selectedLibraryTab = LibraryTab.ARTISTS
                selectedArtistName = validContext.artistName
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
                isExpandedUpNextSheetVisible ||
                playerMorphState.shouldConsumeBack ||
                isFolderScreenVisible ||
                isDiagnosticsScreenVisible ||
                isEqualizerScreenVisible ||
                isStatisticsScreenVisible ||
                isListeningHistoryImportVisible ||
                isListeningHistoryReconciliationVisible ||
                isSettingsScreenVisible ||
                selectedArtistName != null ||
                selectedAlbumFolderPath != null ||
                selectedPlaylistId != null ||
                mainDestination != MainDestination.HOME
    ) {
        when {
            songPendingTagEdit != null -> {
                requestCloseTagEditor()
            }

            isLyricsVisible -> {
                lyricsTransitionState.returnToExpanded()
            }

            isExpandedUpNextSheetVisible -> {
                isExpandedUpNextSheetVisible = false
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

            selectedAlbumFolderPath != null -> {
                selectedAlbumFolderPath = null
                if (selectedArtistName != null) {
                    selectedLibraryTab = LibraryTab.ARTISTS
                }
            }

            selectedArtistName != null -> {
                selectedArtistName = null
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
            val shouldShowBottomMiniPlayer = currentSong != null &&
                    !isFolderScreenVisible &&
                    !isDiagnosticsScreenVisible &&
                    !isEqualizerScreenVisible &&
                    !isStatisticsScreenVisible &&
                    !isListeningHistoryImportVisible &&
                    !isListeningHistoryReconciliationVisible &&
                    !isSettingsScreenVisible &&
                    selectedSongForTagEdit == null
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
                isTagEditorVisible = selectedSongForTagEdit != null
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

            if (selectedSongForTagEdit != null) {
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
                    selectedAlbumFolderPath = selectedAlbumFolderPath,
                    selectedPlaylistId = selectedPlaylistId,
                    searchQuery = searchQuery,
                    selectedSongSortOption = selectedSongSortOption,
                    selectedArtistSortOption = selectedArtistSortOption,
                    selectedAlbumSortOption = selectedAlbumSortOption,
                    selectedFavoriteSortOption = selectedFavoriteSortOption,
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
                        selectedAlbumFolderPath = null
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
                    onSongSortOptionSelected = { option ->
                        selectedSongSortOption = option
                    },
                    onArtistSortOptionSelected = { option ->
                        selectedArtistSortOption = option
                    },
                    onAlbumSortOptionSelected = { option ->
                        selectedAlbumSortOption = option
                    },
                    onFavoriteSortOptionSelected = { option ->
                        selectedFavoriteSortOption = option
                    },
                    onExpandPlayerClick = {
                        playerMorphState.expand()
                    },
                    onMiniPlayerUpNextClick = {
                        selectedLibraryTab = LibraryTab.QUEUE
                        selectedArtistName = null
                        selectedAlbumFolderPath = null
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
                    onAlbumSelected = { albumFolderPath ->
                        selectedAlbumFolderPath = albumFolderPath
                        selectedLibraryTab = LibraryTab.ALBUMS
                    },
                    onBackFromAlbum = {
                        selectedAlbumFolderPath = null
                        if (selectedArtistName != null) {
                            selectedLibraryTab = LibraryTab.ARTISTS
                        }
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
                    selectedModernSeekbarStyle = selectedModernSeekbarStyle,
                    onModernSeekbarStyleSelected = onModernSeekbarStyleSelected,
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
                            selectedAlbumFolderPath = null
                            clearPlaylistSelection()
                            mainDestination = MainDestination.LIBRARY
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
                        selectedAlbumFolderPath = null
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

            if (selectedSongForTagEdit == null) {
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
                    selectedModernSeekbarStyle = selectedModernSeekbarStyle,
                    playerEndpointBounds = playerEndpointBounds,
                    defaultMorphBounds = defaultMorphBounds,
                    classicMorphBounds = classicMorphBounds,
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
