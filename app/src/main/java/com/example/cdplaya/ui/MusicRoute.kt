package com.example.cdplaya.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.example.cdplaya.viewmodel.MusicViewModel
import com.example.cdplaya.ui.state.displayText
import com.example.cdplaya.ui.playlist.rememberPlaylistExportActions
import com.example.cdplaya.ui.playlist.rememberPlaylistImportActions
import com.example.cdplaya.ui.settings.rememberBackupExportActions
import com.example.cdplaya.ui.settings.rememberBackupRestoreActions
import com.example.cdplaya.ui.settings.SpotifyImportUiActions
import com.example.cdplaya.ui.settings.ListeningHistoryReconciliationUiActions
import com.example.cdplaya.ui.equalizer.EqualizerUiActions
import com.example.cdplaya.ui.equalizer.rememberEqualizerProfilePlatformActions
import com.example.cdplaya.mediaaccess.MediaAccessState
import com.example.cdplaya.mediaaccess.FolderArtworkAccessState
import com.example.cdplaya.data.home.HomePin
import com.example.cdplaya.ui.home.HomePinReplacementDialog
import com.example.cdplaya.ui.home.HomePinUiEnvironment
import com.example.cdplaya.ui.home.LocalHomePinUi
import com.example.cdplaya.ui.home.resolveHomePins
import com.example.cdplaya.ui.ratings.LocalSongRatingUi
import com.example.cdplaya.ui.ratings.SongRatingDialog
import com.example.cdplaya.ui.ratings.SongRatingUiEnvironment
import com.example.cdplaya.ui.playlist.LocalSmartPlaylistUi
import com.example.cdplaya.ui.playlist.SmartPlaylistUiEnvironment
import kotlinx.coroutines.launch

@Composable
internal fun MusicRoute(
    musicViewModel: MusicViewModel,
    mediaAccessState: MediaAccessState,
    onRequestAudioAccess: () -> Unit,
    onRequestArtworkAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    folderArtworkAccessState: FolderArtworkAccessState,
    onChooseFolderArtwork: () -> Unit,
    onSkipFolderArtwork: () -> Unit,
    onClearFolderArtwork: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val playbackUiState by musicViewModel.playbackUiState.collectAsStateWithLifecycle()
    val libraryUiState by musicViewModel.libraryUiState.collectAsStateWithLifecycle()
    val sleepTimerUiState by musicViewModel.sleepTimerUiState.collectAsStateWithLifecycle()
    val playerAppearanceUiState by
    musicViewModel.playerAppearanceUiState.collectAsStateWithLifecycle()
    val libraryAppearanceUiState by
    musicViewModel.libraryAppearanceUiState.collectAsStateWithLifecycle()
    val audioOffloadPreference by
    musicViewModel.audioOffloadPreference.collectAsStateWithLifecycle()
    val smoothPlayPauseEnabled by
    musicViewModel.smoothPlayPauseEnabled.collectAsStateWithLifecycle()
    val crossfadeEnabled by
    musicViewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDurationMs by
    musicViewModel.crossfadeDurationMs.collectAsStateWithLifecycle()
    val preserveAlbumTransitions by
    musicViewModel.preserveAlbumTransitions.collectAsStateWithLifecycle()
    val audioOutputUiState by
    musicViewModel.audioOutputUiState.collectAsStateWithLifecycle()
    val equalizerScreenState by
    musicViewModel.equalizerScreenState
        .collectAsStateWithLifecycle()
    val lyricsPlaybackUiState by
    musicViewModel.lyricsPlaybackUiState.collectAsStateWithLifecycle()
    val listeningAnalyticsUiState by
    musicViewModel.listeningAnalyticsUiState.collectAsStateWithLifecycle()
    val songRatingUiState by
    musicViewModel.songRatingUiState.collectAsStateWithLifecycle()
    val spotifyImportUiState by
    musicViewModel.spotifyImportUiState.collectAsStateWithLifecycle()
    val reconciliationUiState by
    musicViewModel.listeningHistoryReconciliationUiState.collectAsStateWithLifecycle()
    val homeCustomizationUiState by
    musicViewModel.homeCustomizationUiState.collectAsStateWithLifecycle()
    val batchMetadataOperationState by
    musicViewModel.batchMetadataOperationState.collectAsStateWithLifecycle()
    if (!playerAppearanceUiState.isLoaded || !libraryAppearanceUiState.isLoaded ||
        !homeCustomizationUiState.isLoaded
    ) return
    val routeScope = rememberCoroutineScope()

    val startupBlocked = !mediaAccessState.hasAudioAccess ||
            !libraryUiState.hasPublishedInitialLibraryState ||
            !folderArtworkAccessState.onboardingComplete
    if (startupBlocked) {
        LibraryStartupScreen(
            mediaAccessState = mediaAccessState,
            initialLibraryReady = libraryUiState.hasPublishedInitialLibraryState,
            folderArtworkOnboardingComplete = folderArtworkAccessState.onboardingComplete,
            onRequestAudioAccess = onRequestAudioAccess,
            onOpenAppSettings = onOpenAppSettings,
            onChooseFolderArtwork = onChooseFolderArtwork,
            onSkipFolderArtwork = onSkipFolderArtwork,
            modifier = modifier
        )
        return
    }

    var pendingHomePin by remember { mutableStateOf<HomePin?>(null) }
    var quickRateMode by remember { mutableStateOf(false) }
    val resolvedHomePins = remember(
        homeCustomizationUiState.pins,
        libraryUiState.songs,
        libraryUiState.playlists
    ) {
        resolveHomePins(
            pins = homeCustomizationUiState.pins,
            songs = libraryUiState.songs,
            playlists = libraryUiState.playlists
        )
    }
    val homePinUiEnvironment = HomePinUiEnvironment(
        pins = resolvedHomePins,
        showRecentlyAddedOnHome = homeCustomizationUiState.showRecentlyAddedOnHome,
        onPinRequested = { pin ->
            if (homeCustomizationUiState.pins.size < HomePin.MAX_COUNT) {
                musicViewModel.addHomePin(pin)
            } else {
                pendingHomePin = pin
            }
        },
        onUnpinRequested = musicViewModel::removeHomePin,
        onMovePinRequested = musicViewModel::moveHomePin,
        onShowRecentlyAddedChanged = musicViewModel::setShowRecentlyAddedOnHome
    )
    val playlistExportActions = rememberPlaylistExportActions(
        snackbarHostState = snackbarHostState,
        onPrepareExport = musicViewModel::preparePlaylistExport,
        onExport = musicViewModel::exportM3uPlaylist
    )
    val playlistImportActions = rememberPlaylistImportActions(
        snackbarHostState = snackbarHostState,
        onImport = musicViewModel::importM3uPlaylist
    )
    val backupExportActions = rememberBackupExportActions(
        snackbarHostState = snackbarHostState,
        onExport = musicViewModel::exportBackup
    )
    val backupRestoreActions = rememberBackupRestoreActions(
        snackbarHostState = snackbarHostState,
        onRead = musicViewModel::readBackupFromUri,
        onSummarize = musicViewModel::summarizeBackupRestore,
        onRestore = musicViewModel::restoreBackup
    )
    val equalizerProfileActions =
        rememberEqualizerProfilePlatformActions(
            snackbarHostState = snackbarHostState,
            currentState = equalizerScreenState
                .durablePreferences.parametricState,
            currentName = equalizerScreenState.presetLabel,
            onImportText =
                musicViewModel::openEqualizerImportPreview
        )

    CompositionLocalProvider(
        LocalSongRatingUi provides SongRatingUiEnvironment(
            state = songRatingUiState,
            filter = libraryUiState.songRatingFilter,
            onOpen = musicViewModel::openSongRating,
            onClose = musicViewModel::closeSongRating,
            onSelectRating = musicViewModel::selectSongRating,
            onSave = musicViewModel::saveSongRating,
            onClear = musicViewModel::clearSongRating,
            onFilterSelected = musicViewModel::selectSongRatingFilter,
            quickRateMode = quickRateMode,
            onQuickRateModeChanged = { quickRateMode = it },
            onSetDirectRating = musicViewModel::setSongRatingDirect
        ),
        LocalSmartPlaylistUi provides SmartPlaylistUiEnvironment(
            onPreview = musicViewModel::previewSmartPlaylist,
            onCreate = musicViewModel::createSmartPlaylist,
            onUpdate = musicViewModel::updateSmartPlaylist,
            onLoad = musicViewModel::loadSmartPlaylistData,
            onRefresh = musicViewModel::refreshGeneratedPlaylist,
            onResolve = musicViewModel::resolveSmartPlaylist
        ),
        LocalHomePinUi provides homePinUiEnvironment,
        LocalFolderArtworkUi provides FolderArtworkUiEnvironment(
            state = folderArtworkAccessState,
            onChooseFolder = onChooseFolderArtwork,
            onClearFolder = onClearFolderArtwork
        )
    ) {
        MusicScreen(
            songs = libraryUiState.songs,
            recentlyPlayedSongs = libraryUiState.recentlyPlayedSongs,
            recentlyAddedLibrarySongs = libraryUiState.recentlyAddedSongs,
            mostPlayedSongs = libraryUiState.mostPlayedSongs,
            mediaAccessState = mediaAccessState,
            isLibraryLoading = libraryUiState.isLoading,
            isLibraryRefreshing = libraryUiState.isRefreshing,
            lastLibraryRefreshSummary = libraryUiState.lastRefreshSummary,
            libraryErrorMessage = libraryUiState.errorMessage,
            onRequestAudioAccess = onRequestAudioAccess,
            onRequestArtworkAccess = onRequestArtworkAccess,
            onOpenAppSettings = onOpenAppSettings,
            currentSong = playbackUiState.currentSong,
            isPlayerConnected = playbackUiState.isConnected,
            previousHistoryCount = playbackUiState.previousHistoryCount,
            forwardHistoryCount = playbackUiState.forwardHistoryCount,
            previousPreviewSong = playbackUiState.previousPreviewSong,
            nextPreviewSong = playbackUiState.nextPreviewSong,
            isPlaying = playbackUiState.isPlaying,
            isShuffleEnabled = playbackUiState.isShuffleEnabled,
            repeatMode = playbackUiState.repeatMode,
            playbackProgressUiState = musicViewModel.playbackProgressUiState,
            lyricsPlaybackUiState = lyricsPlaybackUiState,
            queuedSongs = playbackUiState.queuedSongs,
            upcomingSongs = playbackUiState.upcomingSongs,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
            libraryFolders = libraryUiState.folders,
            folderSelectionMode = libraryUiState.folderSelectionMode,
            selectedLibraryFolders = libraryUiState.selectedFolders,
            excludedLibraryFolders = libraryUiState.excludedFolders,
            favoriteMembershipKeys = libraryUiState.favoriteMembershipKeys,
            unresolvedFavoriteCount = libraryUiState.unresolvedFavoriteCount,
            unresolvedPlaylistRowCount = libraryUiState.unresolvedPlaylistRowCount,
            unresolvedListeningHistoryCount = libraryUiState.unresolvedListeningHistoryCount,
            playlists = libraryUiState.playlists,
            playlistFolders = libraryUiState.playlistFolders,
            selectedPlaylistStateId = libraryUiState.selectedPlaylistId,
            selectedPlaylistName = libraryUiState.selectedPlaylistName,
            selectedPlaylistSongs = libraryUiState.selectedPlaylistSongs,
            isSelectedPlaylistLoading = libraryUiState.isSelectedPlaylistLoading,
            onSongClick = { song, playbackContext ->
                musicViewModel.playSelectedSong(
                    song = song,
                    playbackContext = playbackContext
                )
            },
            onPlaySongsClick = { playbackContext, shuffleMode ->
                musicViewModel.playSongsFromContext(
                    playbackContext = playbackContext,
                    shuffleMode = shuffleMode
                )
            },
            onPlayPauseClick = {
                musicViewModel.togglePlayPause()
            },
            onPreviousClick = {
                musicViewModel.skipToPrevious()
            },
            onNextClick = {
                musicViewModel.skipToNext()
            },
            onSeekChange = { position ->
                musicViewModel.seekTo(position)
            },
            onLyricsVisibilityChanged = musicViewModel::setLyricsVisible,
            onSuspendLyricsAutoFollow = musicViewModel::suspendLyricsAutoFollow,
            onReturnLyricsToCurrentLine = musicViewModel::returnLyricsToCurrentLine,
            onRescanLyrics = musicViewModel::rescanLyrics,
            onShuffleClick = {
                musicViewModel.toggleShuffle()
            },
            onRepeatClick = {
                musicViewModel.cycleRepeatMode()
            },
            onAddToQueueClick = { song ->
                musicViewModel.addSongToQueue(song)
            },
            onPlayNextClick = { song ->
                musicViewModel.addSongToPlayNext(song)
            },
            onUndoPlayNextClick = { song ->
                musicViewModel.removeFirstMatchingSongFromQueue(song)
            },
            onRemoveFromQueueClick = { index ->
                musicViewModel.removeSongFromQueue(index)
            },
            onMoveQueueItemUpClick = { index ->
                musicViewModel.moveQueuedSongUp(index)
            },
            onMoveQueueItemDownClick = { index ->
                musicViewModel.moveQueuedSongDown(index)
            },
            onClearQueueClick = {
                musicViewModel.clearQueue()
            },
            onUndoAddToQueueClick = { song ->
                musicViewModel.removeLastMatchingSongFromQueue(song)
            },
            onPlayNextSongsClick = { songs ->
                musicViewModel.addSongsToPlayNext(songs)
            },
            onAddSongsToQueueClick = { songs ->
                musicViewModel.addSongsToQueue(songs)
            },
            onUndoPlayNextSongsClick = { songs ->
                musicViewModel.removeFirstMatchingSongsFromQueue(songs)
            },
            onUndoAddSongsToQueueClick = { songs ->
                musicViewModel.removeLastMatchingSongsFromQueue(songs)
            },
            onScanLibraryClick = musicViewModel::scanLibrary,
            onLibraryFolderToggle = { folderPath ->
                musicViewModel.toggleLibraryFolder(folderPath)
            },
            onSelectAllLibraryFolders = {
                musicViewModel.selectAllLibraryFolders()
            },
            onClearSelectedLibraryFolders = {
                musicViewModel.clearSelectedLibraryFolders()
            },
            onToggleFavoriteClick = { song ->
                musicViewModel.toggleFavorite(song)
            },
            onCreatePlaylistClick = { playlistName, folderId ->
                musicViewModel.createPlaylist(playlistName, folderId)
            },
            onCreatePlaylistWithSongsClick = { playlistName, initialSongs ->
                musicViewModel.createPlaylistWithSongs(
                    playlistName = playlistName,
                    initialSongs = initialSongs
                ) { result ->
                    routeScope.launch {
                        snackbarHostState.showSnackbar(
                            result.fold(
                                onSuccess = { playlist ->
                                    if (initialSongs.size == 1) {
                                        "\"${initialSongs.first().title}\" added to \"${playlist.name}\""
                                    } else {
                                        "${initialSongs.size} songs added to \"${playlist.name}\""
                                    }
                                },
                                onFailure = { it.message ?: "Unable to create playlist." }
                            )
                        )
                    }
                }
            },
            onCreatePlaylistFolderClick = musicViewModel::createPlaylistFolder,
            onRenamePlaylistFolderClick = musicViewModel::renamePlaylistFolder,
            onDeletePlaylistFolderClick = musicViewModel::deletePlaylistFolder,
            onMovePlaylistToFolderClick = musicViewModel::movePlaylistToFolder,
            onRenamePlaylistClick = { playlist, newName ->
                musicViewModel.renamePlaylist(
                    playlist = playlist,
                    newName = newName
                )
            },
            onDeletePlaylistClick = { playlist ->
                musicViewModel.deletePlaylist(playlist)
            },
            onExportPlaylistClick = playlistExportActions.exportPlaylist,
            onPreparePlaylistQueueSongs = musicViewModel::preparePlaylistQueueSongs,
            onImportPlaylistClick = playlistImportActions.importPlaylist,
            onChangePlaylistArtwork = { playlist, uri ->
                musicViewModel.changePlaylistArtwork(playlist, uri) { result ->
                    routeScope.launch {
                        snackbarHostState.showSnackbar(
                            result.fold(
                                onSuccess = { "Playlist artwork updated." },
                                onFailure = { it.message ?: "Unable to update playlist artwork." }
                            )
                        )
                    }
                }
            },
            onResetPlaylistArtwork = { playlist ->
                musicViewModel.resetPlaylistArtwork(playlist) { result ->
                    routeScope.launch {
                        snackbarHostState.showSnackbar(
                            result.fold(
                                onSuccess = { "Using automatic playlist artwork." },
                                onFailure = { it.message ?: "Unable to reset playlist artwork." }
                            )
                        )
                    }
                }
            },
            onExportBackupClick = backupExportActions.exportBackup,
            onRestoreBackupClick = backupRestoreActions.restoreBackup,
            onPlaylistSelected = { playlist ->
                musicViewModel.loadSelectedPlaylist(playlist)
            },
            onPlaylistCleared = musicViewModel::clearSelectedPlaylist,
            onAddSongToPlaylistClick = { playlist, song ->
                musicViewModel.addSongToPlaylist(
                    playlist = playlist,
                    song = song
                )
            },
            onAddSongsToPlaylistClick = { playlist, songs ->
                musicViewModel.addSongsToPlaylist(
                    playlist = playlist,
                    songs = songs
                )
            },
            onRemovePlaylistSongClick = { playlistSong ->
                musicViewModel.removePlaylistSong(playlistSong)
            },
            onReorderPlaylistSongs = musicViewModel::reorderPlaylistSongs,
            onTagsEdited = { originalSong, editedTags ->
                musicViewModel.refreshSongsAfterTagEdit(
                    originalSong = originalSong,
                    editedTags = editedTags
                )
            },
            isSleepTimerActive = sleepTimerUiState.isActive,
            sleepTimerDisplayText = sleepTimerUiState.displayText(),
            onStartSleepTimerClick = { minutes ->
                musicViewModel.startSleepTimer(minutes)
            },
            onCancelSleepTimerClick = {
                musicViewModel.cancelSleepTimer()
            },
            selectedPlayerTheme = playerAppearanceUiState.selectedTheme,
            selectedPlayerThemeTokens = playerAppearanceUiState.themeTokens,
            onPlayerThemeSelected = { playerTheme ->
                musicViewModel.selectPlayerTheme(playerTheme)
            },
            onUpdatePlayerThemeTokenOverride = musicViewModel::updatePlayerThemeTokenOverride,
            onResetPlayerThemeTokenOverrides = musicViewModel::resetPlayerThemeTokenOverrides,
            selectedModernArtworkTransitionStyle =
                playerAppearanceUiState.modernArtworkTransitionStyle,
            onModernArtworkTransitionStyleSelected =
                musicViewModel::selectModernArtworkTransitionStyle,
            selectedModernSeekbarStyle = playerAppearanceUiState.modernSeekbarStyle,
            onModernSeekbarStyleSelected = musicViewModel::selectModernSeekbarStyle,
            selectedReplayGainMode = playerAppearanceUiState.replayGainMode,
            onReplayGainModeSelected = { replayGainMode ->
                musicViewModel.selectReplayGainMode(replayGainMode)
            },
            selectedAudioOffloadPreference = audioOffloadPreference,
            onAudioOffloadPreferenceSelected = musicViewModel::selectAudioOffloadPreference,
            smoothPlayPauseEnabled = smoothPlayPauseEnabled,
            onSmoothPlayPauseEnabledChanged =
                musicViewModel::setSmoothPlayPauseEnabled,
            crossfadeEnabled = crossfadeEnabled,
            onCrossfadeEnabledChanged = musicViewModel::setCrossfadeEnabled,
            crossfadeDurationMs = crossfadeDurationMs,
            onCrossfadeDurationMsChanged = musicViewModel::setCrossfadeDurationMs,
            preserveAlbumTransitions = preserveAlbumTransitions,
            onPreserveAlbumTransitionsChanged =
                musicViewModel::setPreserveAlbumTransitions,
            audioOutputUiState = audioOutputUiState,
            equalizerScreenState = equalizerScreenState,
            equalizerActions = EqualizerUiActions(
                onBack = musicViewModel::closeEqualizerScreen,
                onEnabledChanged =
                    musicViewModel::setEqualizerEnabled,
                onModeChanged =
                    musicViewModel::setEqualizerMode,
                onPreviewBandGain =
                    musicViewModel::previewEqualizerBandGain,
                onCommitBandGain =
                    musicViewModel::commitEqualizerBandGain,
                onCancelBandGainPreview =
                    musicViewModel::cancelEqualizerBandGainPreview,
                onPreviewPreamp =
                    musicViewModel::previewEqualizerPreamp,
                onCommitPreamp =
                    musicViewModel::commitEqualizerPreamp,
                onCancelPreampPreview =
                    musicViewModel::cancelEqualizerPreampPreview,
                onAutomaticHeadroomChanged =
                    musicViewModel::
                    setEqualizerAutomaticHeadroomEnabled,
                onLimiterEnabledChanged =
                    musicViewModel::setLimiterEnabled,
                onPreviewLimiterCeiling =
                    musicViewModel::previewLimiterCeiling,
                onCommitLimiterCeiling =
                    musicViewModel::commitLimiterCeiling,
                onCancelLimiterCeilingPreview =
                    musicViewModel::
                    cancelLimiterCeilingPreview,
                onResetLimiterMeters =
                    musicViewModel::resetLimiterMeters,
                onApplyBuiltInPreset =
                    musicViewModel::applyBuiltInEqualizerPreset,
                onApplyUserPreset =
                    musicViewModel::applyUserEqualizerPreset,
                onSaveUserPreset =
                    musicViewModel::saveUserEqualizerPreset,
                onRenameUserPreset =
                    musicViewModel::renameUserEqualizerPreset,
                onDeleteUserPreset =
                    musicViewModel::deleteUserEqualizerPreset,
                onSelectParametricFilter =
                    musicViewModel::selectParametricFilter,
                onAddParametricFilter =
                    musicViewModel::addParametricFilter,
                onPreviewParametricFilter =
                    musicViewModel::previewParametricFilter,
                onCommitParametricFilter =
                    musicViewModel::commitParametricFilter,
                onCancelParametricFilterPreview =
                    musicViewModel::cancelParametricFilterPreview,
                onMoveParametricFilter =
                    musicViewModel::moveParametricFilter,
                onDeleteParametricFilter =
                    musicViewModel::deleteParametricFilter,
                onApplyParametricFlatPreset =
                    musicViewModel::applyParametricFlatPreset,
                onApplyParametricUserPreset =
                    musicViewModel::applyParametricUserPreset,
                onSaveParametricUserPreset =
                    musicViewModel::saveParametricUserPreset,
                onRenameParametricUserPreset =
                    musicViewModel::renameParametricUserPreset,
                onDeleteParametricUserPreset =
                    musicViewModel::deleteParametricUserPreset,
                onImportFromFile =
                    equalizerProfileActions.importFromFile,
                onPasteEqText =
                    equalizerProfileActions.pasteEqText,
                onExportCurrentEqText =
                    equalizerProfileActions.exportCurrentText,
                onCopyCurrentEqText =
                    equalizerProfileActions.copyCurrentText,
                onExportCurrentNative =
                    equalizerProfileActions.exportCurrentNative,
                onExportParametricPresetText =
                    equalizerProfileActions.exportPresetText,
                onExportParametricPresetNative =
                    equalizerProfileActions.exportPresetNative,
                onDismissImportPreview =
                    musicViewModel::dismissEqualizerImportPreview,
                onUpdateImportPreview =
                    musicViewModel::updateEqualizerImportPreview,
                onReplaceWithImportedProfile =
                    musicViewModel::
                    replaceWithImportedEqualizerProfile,
                onSaveImportedProfile =
                    musicViewModel::saveImportedEqualizerProfile,
                onResetToFlat =
                    musicViewModel::resetEqualizerToFlat,
                onComparisonBypassedChanged =
                    musicViewModel::
                    setEqualizerComparisonBypassed
            ),
            onReadEditableSongTags = musicViewModel::readEditableSongTags,
            onGetUnsupportedTagEditingMessage = musicViewModel::getUnsupportedTagEditingMessage,
            onWriteTagsAndArtwork = musicViewModel::writeTagsAndArtwork,
            batchMetadataOperationState = batchMetadataOperationState,
            onBeginBatchMetadata = musicViewModel::beginBatchMetadata,
            onConsumeBatchPermissionRequest =
                musicViewModel::consumeBatchPermissionRequest,
            onBatchPermissionResult = musicViewModel::reportBatchPermissionResult,
            onCancelBatchMetadata = musicViewModel::cancelBatchMetadata,
            onRetryFailedBatchMetadata = musicViewModel::retryFailedBatchMetadata,
            onContinueUnprocessedBatchMetadata =
                musicViewModel::continueUnprocessedBatchMetadata,
            onRetryBatchMetadataRefresh = musicViewModel::retryBatchMetadataRefresh,
            onDismissBatchMetadata = musicViewModel::dismissBatchMetadataOperation,
            libraryAppearanceUiState = libraryAppearanceUiState,
            onLibraryViewOptionSelected = musicViewModel::selectLibraryViewOption,
            listeningAnalyticsUiState = listeningAnalyticsUiState,
            onListeningAnalyticsActiveChanged = musicViewModel::setListeningAnalyticsActive,
            onListeningAnalyticsPresetSelected = musicViewModel::selectListeningAnalyticsPreset,
            onListeningAnalyticsCustomRangeSelected =
                musicViewModel::selectListeningAnalyticsCustomRange,
            onRetryListeningAnalytics = musicViewModel::retryListeningAnalytics,
            onListeningAnalyticsTrendMetricSelected =
                musicViewModel::selectListeningAnalyticsTrendMetric,
            onListeningAnalyticsRankingCategorySelected =
                musicViewModel::selectListeningAnalyticsRankingCategory,
            spotifyImportUiState = spotifyImportUiState,
            reconciliationUiState = reconciliationUiState,
            reconciliationActions = ListeningHistoryReconciliationUiActions(
                onEnter = musicViewModel::enterListeningHistoryReconciliation,
                onBack = {},
                onRetry = musicViewModel::retryListeningHistoryReconciliation,
                onTabSelected = musicViewModel::selectReconciliationTab,
                onToggleExpanded = musicViewModel::toggleReconciliationItem,
                onToggleLinkedGroup = musicViewModel::toggleLinkedReconciliationGroup,
                onSkip = musicViewModel::skipReconciliationItem,
                onCandidateSelected = musicViewModel::chooseReconciliationTarget,
                onSearchRequested = musicViewModel::openReconciliationSearch,
                onSearchQueryChanged = musicViewModel::updateReconciliationSearch,
                onSearchDismissed = musicViewModel::closeReconciliationSearch,
                onUnlinkRequested = musicViewModel::requestReconciliationUnlink,
                onConfirmationCancelled = musicViewModel::cancelReconciliationConfirmation,
                onConfirmed = musicViewModel::confirmReconciliationChange,
                onMessageDismissed = musicViewModel::clearReconciliationMessage
            ),
            spotifyImportActions = SpotifyImportUiActions(
                onEnter = musicViewModel::enterSpotifyImport,
                onFilesSelected = musicViewModel::selectSpotifyImportFiles,
                onAnalyze = musicViewModel::analyzeSpotifyImport,
                onCancelAnalysis = musicViewModel::cancelSpotifyImportAnalysis,
                onImport = musicViewModel::executeSpotifyImport,
                onCancelImport = musicViewModel::cancelSpotifyImport,
                onRetry = musicViewModel::retrySpotifyImport,
                onChangeFiles = musicViewModel::changeSpotifyImportFiles,
                onCleanStaleImport = musicViewModel::cleanStaleSpotifyImport,
                onImportMore = musicViewModel::resetSpotifyImport,
                onDone = musicViewModel::resetSpotifyImport,
                onBack = {}
            )
        )
        songRatingUiState.dialog?.let { dialog ->
            SongRatingDialog(
                state = dialog,
                onDismiss = musicViewModel::closeSongRating,
                onRatingSelected = musicViewModel::selectSongRating,
                onSave = musicViewModel::saveSongRating,
                onClear = musicViewModel::clearSongRating
            )
        }
        pendingHomePin?.let { pin ->
            HomePinReplacementDialog(
                currentPins = resolvedHomePins,
                pendingPin = pin,
                onReplace = { index ->
                    musicViewModel.replaceHomePin(index, pin)
                    pendingHomePin = null
                },
                onDismiss = { pendingHomePin = null }
            )
        }
    }
}
