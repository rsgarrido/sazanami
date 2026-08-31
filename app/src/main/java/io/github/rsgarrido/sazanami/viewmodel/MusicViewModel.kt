package io.github.rsgarrido.sazanami.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rsgarrido.sazanami.controller.LibraryController
import io.github.rsgarrido.sazanami.controller.SongRatingUiController
import io.github.rsgarrido.sazanami.controller.ListeningAnalyticsController
import io.github.rsgarrido.sazanami.controller.SleepTimerController
import io.github.rsgarrido.sazanami.controller.PlaybackQueueUiController
import io.github.rsgarrido.sazanami.controller.RoomPlaybackQueueUiOperations
import io.github.rsgarrido.sazanami.controller.DefaultSpotifyListeningHistoryImportOperations
import io.github.rsgarrido.sazanami.controller.ListeningHistoryImportFile
import io.github.rsgarrido.sazanami.controller.SpotifyListeningHistoryImportController
import io.github.rsgarrido.sazanami.controller.DefaultListeningHistoryReconciliationOperations
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationController
import io.github.rsgarrido.sazanami.controller.LinkedHistoricalReconciliation
import io.github.rsgarrido.sazanami.controller.ReconciliationAlbumKey
import io.github.rsgarrido.sazanami.controller.ReconciliationBrowseMode
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewTab
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewFilter
import io.github.rsgarrido.sazanami.controller.ReconciliationSortOption
import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.ListeningHistoryAutomaticReconciler
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.ArtistIdentity
import io.github.rsgarrido.sazanami.data.EditableSongTags
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.TagEditorRepository
import io.github.rsgarrido.sazanami.data.TagEditorResult
import io.github.rsgarrido.sazanami.data.BatchMetadataExecutor
import io.github.rsgarrido.sazanami.data.BatchMetadataPlan
import io.github.rsgarrido.sazanami.data.LibraryBatchTargetResolver
import io.github.rsgarrido.sazanami.data.BatchCapabilityReader
import io.github.rsgarrido.sazanami.data.BatchTargetPatchWriter
import io.github.rsgarrido.sazanami.data.BatchMetadataOperationController
import io.github.rsgarrido.sazanami.data.BatchArtworkPreparer
import io.github.rsgarrido.sazanami.data.BatchPlanExecutor
import io.github.rsgarrido.sazanami.data.BatchSuccessfulTargetScanner
import io.github.rsgarrido.sazanami.data.BatchSuccessfulTargetRefresher
import io.github.rsgarrido.sazanami.data.BatchPostWriteStageResult
import io.github.rsgarrido.sazanami.data.BatchPostWriteStageStatus
import io.github.rsgarrido.sazanami.data.PreferencesBatchInterruptionStore
import io.github.rsgarrido.sazanami.data.DEFAULT_BATCH_REFRESH_TIMEOUT_MS
import io.github.rsgarrido.sazanami.data.scanBatchMetadataFiles
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.AnalyticsZoneIdProvider
import io.github.rsgarrido.sazanami.data.ListeningAnalyticsRangeResolver
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.SongRatingRepository
import io.github.rsgarrido.sazanami.data.SmartPlaylistDefinition
import io.github.rsgarrido.sazanami.data.SmartPlaylistDraft
import io.github.rsgarrido.sazanami.data.SmartPlaylistResolution
import io.github.rsgarrido.sazanami.data.SmartPlaylistTemplate
import io.github.rsgarrido.sazanami.controller.SmartPlaylistUiData
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.data.home.HomePin
import io.github.rsgarrido.sazanami.data.backup.AppBackup
import io.github.rsgarrido.sazanami.data.backup.BackupExportResult
import io.github.rsgarrido.sazanami.data.backup.BackupRepository
import io.github.rsgarrido.sazanami.data.backup.BackupRestoreResult
import io.github.rsgarrido.sazanami.data.backup.BackupRestoreSummary
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.DatabaseProvider
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyExtendedStreamingParser
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyImportSourceProfileService
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportPreviewer
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.lyrics.LocalLyricsServices
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackController
import io.github.rsgarrido.sazanami.lyrics.LyricsPositionSource
import io.github.rsgarrido.sazanami.data.playlistfile.M3uExportResult
import io.github.rsgarrido.sazanami.data.playlistfile.PlaylistImportResult
import io.github.rsgarrido.sazanami.data.playlistfile.PreparedPlaylistExport
import io.github.rsgarrido.sazanami.player.PlaybackController
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerUiController
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerImportPreviewState
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenField
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.customizationOptions
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import io.github.rsgarrido.sazanami.ui.state.LibraryAppearanceUiState
import io.github.rsgarrido.sazanami.ui.state.LibraryCategoryAppearance
import io.github.rsgarrido.sazanami.ui.state.PlayerAppearanceUiState
import io.github.rsgarrido.sazanami.ui.state.category
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryViewOption
import io.github.rsgarrido.sazanami.ui.library.SongRatingFilter
import io.github.rsgarrido.sazanami.ui.library.viewCategory
import io.github.rsgarrido.sazanami.ui.home.HomeCustomizationUiState
import io.github.rsgarrido.sazanami.ui.player.theme.applyOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import kotlin.coroutines.resume

class MusicViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val appDatabase: AppDatabase = DatabaseProvider.getDatabase(appContext)
    private val historyAutomaticReconciler = ListeningHistoryAutomaticReconciler(appDatabase)
    private var refreshOpenHistoryReconciliation: () -> Unit = {}

    private val appPreferencesRepository = AppPreferencesRepository.getInstance(appContext)
    private val tagEditorRepository = TagEditorRepository()
    private val batchMetadataExecutor = BatchMetadataExecutor(
        resolver = LibraryBatchTargetResolver(),
        capabilityReader = BatchCapabilityReader { song ->
            tagEditorRepository.readTags(song).capabilities
        },
        writer = BatchTargetPatchWriter { song, edits, artworkEdit ->
            tagEditorRepository.writeExplicitMetadataPatch(
                context = appContext,
                song = song,
                edits = edits,
                artworkEdit = artworkEdit
            )
        }
    )
    private val listeningImportRepository = ListeningImportRepository(appDatabase)
    private val spotifyImportSourceProfiles = SpotifyImportSourceProfileService(listeningImportRepository)
    private val spotifyImportParser = SpotifyExtendedStreamingParser()
    private val spotifyImportController = SpotifyListeningHistoryImportController(
        operations = DefaultSpotifyListeningHistoryImportOperations(
            repository = listeningImportRepository,
            previewer = SpotifyListeningHistoryImportPreviewer(
                repository = listeningImportRepository,
                sourceProfiles = spotifyImportSourceProfiles,
                parser = spotifyImportParser
            ),
            executor = SpotifyListeningHistoryImportExecutor(
                repository = listeningImportRepository,
                sourceProfiles = spotifyImportSourceProfiles,
                parser = spotifyImportParser,
                createdAppVersion = BuildConfig.VERSION_NAME
            ),
            reconcilePublishedHistory = {
                val result = historyAutomaticReconciler.reconcile(
                    libraryController.uiState.value.songs
                )
                if (result.newlyLinked > 0) {
                    refreshOpenHistoryReconciliation()
                }
            }
        ),
        scope = viewModelScope
    )
    val spotifyImportUiState = spotifyImportController.state

    val listeningHistoryReconciliationUiState
        get() = listeningHistoryReconciliationController.state

    fun enterListeningHistoryReconciliation() = listeningHistoryReconciliationController.enter()
    fun retryListeningHistoryReconciliation() = listeningHistoryReconciliationController.retry()
    fun selectReconciliationTab(tab: ReconciliationReviewTab) =
        listeningHistoryReconciliationController.selectTab(tab)
    fun selectReconciliationBrowseMode(mode: ReconciliationBrowseMode) =
        listeningHistoryReconciliationController.selectBrowseMode(mode)
    fun updateReconciliationBrowseQuery(query: String) =
        listeningHistoryReconciliationController.updateBrowseQuery(query)
    fun selectReconciliationSort(option: ReconciliationSortOption) =
        listeningHistoryReconciliationController.selectSort(option)
    fun selectReconciliationReviewFilter(filter: ReconciliationReviewFilter) =
        listeningHistoryReconciliationController.selectReviewFilter(filter)
    fun toggleReconciliationItem(sourceId: Long) =
        listeningHistoryReconciliationController.toggleExpanded(sourceId)
    fun toggleReconciliationAlbum(key: ReconciliationAlbumKey) =
        listeningHistoryReconciliationController.toggleAlbum(key)
    fun toggleReconciliationArtist(key: String) =
        listeningHistoryReconciliationController.toggleArtist(key)
    fun toggleReconciliationSelection(sourceId: Long) =
        listeningHistoryReconciliationController.toggleSelected(sourceId)
    fun selectReconciliationItems(sourceIds: List<Long>) =
        listeningHistoryReconciliationController.selectReviewItems(sourceIds)
    fun clearReconciliationSelection() = listeningHistoryReconciliationController.clearSelection()
    fun requestLinkSelectedReconciliations() =
        listeningHistoryReconciliationController.requestLinkSelected()
    fun skipReconciliationItem(sourceId: Long) =
        listeningHistoryReconciliationController.skip(sourceId)
    fun chooseReconciliationTarget(sourceIds: List<Long>, target: LocalReconciliationTarget) =
        listeningHistoryReconciliationController.chooseTarget(sourceIds, target)
    fun openReconciliationSearch(sourceIds: List<Long>) =
        listeningHistoryReconciliationController.openSearch(sourceIds)
    fun updateReconciliationSearch(query: String) =
        listeningHistoryReconciliationController.updateSearchQuery(query)
    fun closeReconciliationSearch() = listeningHistoryReconciliationController.closeSearch()
    fun requestReconciliationUnlink(item: LinkedHistoricalReconciliation) =
        listeningHistoryReconciliationController.requestUnlink(item)
    fun cancelReconciliationConfirmation() =
        listeningHistoryReconciliationController.cancelConfirmation()
    fun confirmReconciliationChange() = listeningHistoryReconciliationController.confirm()
    fun clearReconciliationMessage() = listeningHistoryReconciliationController.clearMessage()

    fun enterSpotifyImport() = spotifyImportController.enterWorkflow()
    fun selectSpotifyImportFiles(files: List<ListeningHistoryImportFile>) =
        spotifyImportController.selectFiles(files)
    fun analyzeSpotifyImport() = spotifyImportController.analyze()
    fun cancelSpotifyImportAnalysis() = spotifyImportController.cancelAnalysis()
    fun executeSpotifyImport() = spotifyImportController.importHistory()
    fun cancelSpotifyImport() = spotifyImportController.cancelImport()
    fun retrySpotifyImport() = spotifyImportController.retry()
    fun changeSpotifyImportFiles() = spotifyImportController.returnToSelectedFiles()
    fun cleanStaleSpotifyImport() = spotifyImportController.cleanStaleImport()
    fun resetSpotifyImport() = spotifyImportController.reset()
    private val listeningAnalyticsController = ListeningAnalyticsController(
        repository = ListeningStatsRepository(appDatabase),
        rangeResolver = ListeningAnalyticsRangeResolver(
            clock = Clock.systemUTC(),
            zoneIdProvider = AnalyticsZoneIdProvider(ZoneId::systemDefault)
        ),
        scope = viewModelScope
    )
    val listeningAnalyticsUiState = listeningAnalyticsController.state

    private val songRatingUiController = SongRatingUiController(
        repository = SongRatingRepository(appDatabase),
        scope = viewModelScope
    )
    val songRatingUiState = songRatingUiController.state

    fun openSongRating(song: Song) = songRatingUiController.open(song)
    fun closeSongRating() = songRatingUiController.close()
    fun selectSongRating(value: Int) = songRatingUiController.selectRating(value)
    fun saveSongRating() = songRatingUiController.save()
    fun clearSongRating() = songRatingUiController.clear()
    fun setSongRatingDirect(song: Song, value: Int?) =
        songRatingUiController.setDirectRating(song, value)
    fun selectSongRatingFilter(filter: SongRatingFilter) =
        libraryController.selectSongRatingFilter(filter)

    fun setListeningAnalyticsActive(active: Boolean) {
        listeningAnalyticsController.setActive(active)
    }

    fun selectListeningAnalyticsPreset(preset: AnalyticsRangePreset) {
        listeningAnalyticsController.selectRange(AnalyticsRangeSelection.Preset(preset))
    }

    fun selectListeningAnalyticsCustomRange(startDate: LocalDate, endDateInclusive: LocalDate) {
        listeningAnalyticsController.selectRange(
            AnalyticsRangeSelection.Custom(startDate, endDateInclusive)
        )
    }

    fun retryListeningAnalytics() {
        listeningAnalyticsController.retry()
    }

    fun selectListeningAnalyticsTrendMetric(metric: ListeningTrendMetric) {
        listeningAnalyticsController.selectTrendMetric(metric)
    }

    fun selectListeningAnalyticsRankingCategory(category: ListeningRankingCategory) {
        listeningAnalyticsController.selectRankingCategory(category)
    }

    val playerAppearanceUiState = appPreferencesRepository.state.map { preferences ->
        val selectedTheme = preferences.selectedPlayerTheme
        val overrides = preferences.playerThemeTokenOverrides[selectedTheme]
            ?: PlayerThemeTokenOverrides()
        PlayerAppearanceUiState(
            selectedTheme = selectedTheme,
            themeTokens = selectedTheme.defaultTokens().applyOverrides(overrides),
            modernArtworkTransitionStyle = preferences.modernArtworkTransitionStyle,
            modernPlayerAppearance = preferences.modernPlayerAppearance,
            replayGainMode = preferences.replayGainMode,
            isLoaded = preferences.isLoaded
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerAppearanceUiState())

    val libraryAppearanceUiState = appPreferencesRepository.state.map { preferences ->
        LibraryAppearanceUiState(
            songs = LibraryCategoryAppearance(
                preferences.songsViewMode,
                preferences.songsGridColumnCount
            ),
            albums = LibraryCategoryAppearance(
                preferences.albumsViewMode,
                preferences.albumsGridColumnCount
            ),
            artists = LibraryCategoryAppearance(
                preferences.artistsViewMode,
                preferences.artistsGridColumnCount
            ),
            playlists = LibraryCategoryAppearance(
                preferences.playlistsViewMode,
                preferences.playlistsGridColumnCount
            ),
            isLoaded = preferences.isLoaded
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryAppearanceUiState())

    val homeCustomizationUiState = appPreferencesRepository.state.map { preferences ->
        HomeCustomizationUiState(
            pins = preferences.homePins,
            showRecentlyAddedOnHome = preferences.showRecentlyAddedOnHome,
            isLoaded = preferences.isLoaded
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeCustomizationUiState())

    val audioOffloadPreference = appPreferencesRepository.state
        .map { preferences -> preferences.audioOffloadPreference }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AudioOffloadPreference.DISABLED
        )

    val smoothPlayPauseEnabled = appPreferencesRepository.state
        .map { preferences -> preferences.smoothPlayPauseEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val crossfadeEnabled = appPreferencesRepository.state
        .map { preferences -> preferences.crossfadeEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val crossfadeDurationMs = appPreferencesRepository.state
        .map { preferences -> preferences.crossfadeDurationMs }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5_000)

    val preserveAlbumTransitions = appPreferencesRepository.state
        .map { preferences -> preferences.preserveAlbumTransitions }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun selectPlayerTheme(playerTheme: PlayerTheme) {
        viewModelScope.launch { appPreferencesRepository.setSelectedPlayerTheme(playerTheme) }
    }

    fun selectModernArtworkTransitionStyle(style: ModernArtworkTransitionStyle) {
        viewModelScope.launch {
            appPreferencesRepository.setModernArtworkTransitionStyle(style)
        }
    }

    fun selectModernSeekbarStyle(style: ModernSeekbarStyle) {
        viewModelScope.launch { appPreferencesRepository.setModernSeekbarStyle(style) }
    }

    fun updateModernPlayerAppearance(
        appearance: io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
    ) {
        viewModelScope.launch {
            appPreferencesRepository.setModernPlayerAppearance(appearance)
        }
    }

    fun selectModernWaveformSize(size: io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize) {
        viewModelScope.launch { appPreferencesRepository.setModernWaveformSize(size) }
    }

    fun selectModernWaveformDensity(
        density: io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformDensity
    ) {
        viewModelScope.launch { appPreferencesRepository.setModernWaveformDensity(density) }
    }

    fun selectModernSeekbarColorMode(
        mode: io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarColorMode
    ) {
        viewModelScope.launch { appPreferencesRepository.setModernSeekbarColorMode(mode) }
    }

    fun selectModernBackgroundStyle(
        style: io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
    ) {
        viewModelScope.launch { appPreferencesRepository.setModernBackgroundStyle(style) }
    }

    fun selectModernBlurStrength(
        strength: io.github.rsgarrido.sazanami.ui.player.modern.ModernBlurStrength
    ) {
        viewModelScope.launch { appPreferencesRepository.setModernBlurStrength(strength) }
    }

    fun selectModernDimmingStrength(
        strength: io.github.rsgarrido.sazanami.ui.player.modern.ModernDimmingStrength
    ) {
        viewModelScope.launch { appPreferencesRepository.setModernDimmingStrength(strength) }
    }

    fun resetModernPlayerAppearance() {
        viewModelScope.launch { appPreferencesRepository.resetModernPlayerAppearance() }
    }

    fun updatePlayerThemeTokenOverride(
        playerTheme: PlayerTheme,
        field: PlayerThemeTokenField,
        color: Color
    ) {
        if (playerTheme.customizationOptions().none { option -> option.field == field }) {
            return
        }

        val currentOverrides = appPreferencesRepository.state.value
            .playerThemeTokenOverrides[playerTheme] ?: PlayerThemeTokenOverrides()
        val updatedOverrides = when (field) {
            PlayerThemeTokenField.SHELL -> currentOverrides.copy(shellColor = color)
            PlayerThemeTokenField.ACCENT -> currentOverrides.copy(accentColor = color)
            PlayerThemeTokenField.DISPLAY_BACKGROUND -> {
                currentOverrides.copy(displayBackgroundColor = color)
            }

            PlayerThemeTokenField.DISPLAY_TEXT -> currentOverrides.copy(displayTextColor = color)
            PlayerThemeTokenField.SECONDARY_ACCENT -> {
                currentOverrides.copy(secondaryAccentColor = color)
            }
        }

        viewModelScope.launch {
            appPreferencesRepository.setThemeTokenOverrides(playerTheme, updatedOverrides)
        }
    }

    fun resetPlayerThemeTokenOverrides(playerTheme: PlayerTheme) {
        viewModelScope.launch { appPreferencesRepository.clearThemeTokenOverrides(playerTheme) }
    }

    fun getPlayerThemeTokenOverrides(playerTheme: PlayerTheme): PlayerThemeTokenOverrides {
        return if (playerTheme.customizationOptions().isEmpty()) {
            PlayerThemeTokenOverrides()
        } else {
            appPreferencesRepository.state.value.playerThemeTokenOverrides[playerTheme]
                ?: PlayerThemeTokenOverrides()
        }
    }

    fun selectReplayGainMode(replayGainMode: ReplayGainMode) {
        viewModelScope.launch { appPreferencesRepository.setReplayGainMode(replayGainMode) }
    }

    fun selectAudioOffloadPreference(preference: AudioOffloadPreference) {
        viewModelScope.launch {
            appPreferencesRepository.setAudioOffloadPreference(preference)
        }
    }

    fun setSmoothPlayPauseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.setSmoothPlayPauseEnabled(enabled)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDurationMs(durationMs: Int) {
        viewModelScope.launch {
            appPreferencesRepository.setCrossfadeDurationMs(durationMs)
        }
    }

    fun setPreserveAlbumTransitions(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.setPreserveAlbumTransitions(enabled)
        }
    }

    fun selectLibraryViewOption(category: LibraryViewCategory, option: LibraryViewOption) {
        val current = libraryAppearanceUiState.value.category(category)
        viewModelScope.launch {
            appPreferencesRepository.setLibraryView(
                category = category,
                mode = option.viewMode,
                gridColumnCount = option.gridColumnCount ?: current.gridColumnCount
            )
        }
    }

    fun addHomePin(pin: HomePin) {
        viewModelScope.launch { appPreferencesRepository.addHomePin(pin) }
    }

    fun replaceHomePin(index: Int, pin: HomePin) {
        viewModelScope.launch { appPreferencesRepository.replaceHomePin(index, pin) }
    }

    fun removeHomePin(pinId: String) {
        viewModelScope.launch { appPreferencesRepository.removeHomePin(pinId) }
    }

    fun moveHomePin(pinId: String, offset: Int) {
        viewModelScope.launch { appPreferencesRepository.moveHomePin(pinId, offset) }
    }

    fun setShowRecentlyAddedOnHome(show: Boolean) {
        viewModelScope.launch { appPreferencesRepository.setShowRecentlyAddedOnHome(show) }
    }

    fun readEditableSongTags(song: Song): EditableSongTags = tagEditorRepository.readTags(song)

    fun getUnsupportedTagEditingMessage(song: Song): String? =
        tagEditorRepository.getUnsupportedEditingMessage(song)

    suspend fun writeTagsAndArtwork(
        song: Song,
        editedTags: EditableSongTags,
        artworkUri: Uri?
    ): TagEditorResult = tagEditorRepository.writeTagsAndArtwork(
        context = appContext,
        song = song,
        editedTags = editedTags,
        artworkUri = artworkUri
    )

    fun beginBatchMetadata(
        plan: BatchMetadataPlan,
        songs: List<Song>,
        artworkUri: Uri?,
        requiresWritePermission: Boolean
    ) = batchMetadataOperationController.begin(
        plan,
        songs,
        artworkUri,
        requiresWritePermission
    )

    fun consumeBatchPermissionRequest(operationId: String, batchIndex: Int): List<Uri>? =
        batchMetadataOperationController.consumePermissionRequest(operationId, batchIndex)

    fun reportBatchPermissionResult(
        operationId: String,
        batchIndex: Int,
        granted: Boolean,
        reason: String? = null
    ) = batchMetadataOperationController.onPermissionResult(
        operationId,
        batchIndex,
        granted,
        reason
    )

    fun cancelBatchMetadata() = batchMetadataOperationController.cancel()

    fun retryFailedBatchMetadata(songs: List<Song>) =
        batchMetadataOperationController.retryFailed(songs)

    fun continueUnprocessedBatchMetadata(songs: List<Song>) =
        batchMetadataOperationController.continueUnprocessed(songs)

    fun retryBatchMetadataRefresh() = batchMetadataOperationController.retryPostWrite()

    fun dismissBatchMetadataOperation() = batchMetadataOperationController.dismiss()
    private val playbackController = PlaybackController(
        context = appContext,
        coroutineScope = viewModelScope
    )
    private val lyricsPlaybackController = LyricsPlaybackController(
        repository = LocalLyricsServices.shared(appContext).repository,
        playbackState = playbackController.uiState,
        positionSource = LyricsPositionSource(
            playbackController::getCurrentPositionForLyrics
        ),
        scope = viewModelScope
    )
    val lyricsPlaybackUiState = lyricsPlaybackController.uiState

    private val sleepTimerController = SleepTimerController(
        coroutineScope = viewModelScope,
        onTimerFinished = {
            playbackController.pausePlayback()
        }
    )

    private val _mediaAccessFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mediaAccessFailures = _mediaAccessFailures.asSharedFlow()

    private val libraryController = LibraryController(
        context = appContext,
        appDatabase = appDatabase,
        playbackController = playbackController,
        coroutineScope = viewModelScope,
        onMediaAccessFailure = {
            _mediaAccessFailures.tryEmit(Unit)
        },
        onLibraryPublished = { songs ->
            val result = historyAutomaticReconciler.reconcile(songs)
            if (result.newlyLinked > 0) {
                refreshOpenHistoryReconciliation()
            }
        }
    )

    private val playbackQueueUiController = PlaybackQueueUiController(
        operations = RoomPlaybackQueueUiOperations(
            repository = PlaybackQueueRepository(appDatabase),
            playbackController = playbackController,
            database = appDatabase,
            catalogSongs = { libraryController.uiState.value.songs }
        ),
        scope = viewModelScope
    )
    internal val playbackQueueHubUiState = playbackQueueUiController.state

    internal fun selectPlaybackQueue(queueId: String) =
        playbackQueueUiController.selectQueue(queueId)

    internal fun switchSelectedPlaybackQueue() =
        playbackQueueUiController.switchSelectedQueue()

    internal fun createPlaybackQueueFromCurrent() =
        playbackQueueUiController.createQueueFromCurrent()

    internal fun renamePlaybackQueue(queueId: String, name: String) =
        playbackQueueUiController.renameQueue(queueId, name)

    internal fun deletePlaybackQueue(queueId: String) =
        playbackQueueUiController.deleteQueue(queueId)

    internal fun clearPlaybackQueueMessage() = playbackQueueUiController.clearMessage()

    internal fun playInNewQueue(displayName: String, songs: List<Song>) =
        playbackQueueUiController.playInNewQueue(displayName, songs)

    internal fun addToInactiveQueue(queueId: String, songs: List<Song>) =
        playbackQueueUiController.addToInactiveQueue(queueId, songs)

    internal fun removePlaybackQueueEntry(queueId: String, entryId: String) =
        playbackQueueUiController.removeEntry(queueId, entryId)

    internal fun reorderPlaybackQueueEntry(
        queueId: String,
        entryId: String,
        toPlaybackOrder: Int
    ) = playbackQueueUiController.reorderEntry(queueId, entryId, toPlaybackOrder)

    private val batchMetadataOperationController = BatchMetadataOperationController(
        scope = viewModelScope,
        artworkPreparer = BatchArtworkPreparer { uri ->
            withContext(Dispatchers.IO) {
                tagEditorRepository.prepareBatchArtwork(appContext, uri)
            }
        },
        executor = BatchPlanExecutor { plan, songs, artwork, cancellation, onProgress ->
            withContext(Dispatchers.IO) {
                batchMetadataExecutor.execute(plan, songs, artwork, cancellation, onProgress)
            }
        },
        scanner = BatchSuccessfulTargetScanner { songs ->
            scanBatchMetadataFiles(appContext, songs)
        },
        refresher = BatchSuccessfulTargetRefresher { songs ->
            refreshBatchMetadataLibrary(songs)
        },
        interruptionStore = PreferencesBatchInterruptionStore(appContext)
    )

    val batchMetadataOperationState = batchMetadataOperationController.state

    private val listeningHistoryReconciliationController =
        ListeningHistoryReconciliationController(
            operations = DefaultListeningHistoryReconciliationOperations(
                database = appDatabase,
                currentSongs = { libraryController.uiState.value.songs }
            ),
            scope = viewModelScope
        )

    init {
        refreshOpenHistoryReconciliation =
            listeningHistoryReconciliationController::onExternalReconciliationMutation
    }

    val libraryUiState = libraryController.uiState
    val playbackUiState = playbackController.uiState
    val playbackProgressUiState = playbackController.progressState
    val audioOutputUiState = playbackController.audioOutputState
    val sleepTimerUiState = sleepTimerController.uiState
    private val equalizerRuntimeState = audioOutputUiState
        .map { state -> state.equalizerRuntimeState }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            EqualizerRuntimeState()
        )
    private val equalizerUiController = EqualizerUiController(
        preferencesRepository = appPreferencesRepository,
        runtimeState = equalizerRuntimeState,
        scope = viewModelScope
    )
    internal val equalizerScreenState = equalizerUiController.state

    private val backupRepository = libraryController.createBackupRepository()

    init {
        viewModelScope.launch {
            appPreferencesRepository.state
                .filter { preferences -> preferences.isLoaded }
                .map { preferences -> preferences.replayGainMode }
                .distinctUntilChanged()
                .collectLatest(playbackController::setReplayGainMode)
        }
        playbackController.connect()
        libraryController.loadSavedUserData()
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerController.startTimer(minutes)
    }

    fun cancelSleepTimer() {
        sleepTimerController.cancelTimer()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerUiController.setEnabled(enabled)
    }

    fun setEqualizerMode(mode: EqualizerMode) {
        equalizerUiController.setMode(mode)
    }

    fun previewEqualizerBandGain(
        index: Int,
        gainDb: Double
    ) {
        equalizerUiController.previewBandGain(index, gainDb)
    }

    fun commitEqualizerBandGain(
        index: Int,
        gainDb: Double
    ) {
        equalizerUiController.commitBandGain(index, gainDb)
    }

    fun cancelEqualizerBandGainPreview(
        index: Int,
        gainDb: Double
    ) {
        equalizerUiController.cancelBandGainPreview(
            index,
            gainDb
        )
    }

    fun previewEqualizerPreamp(preampDb: Double) {
        equalizerUiController.previewPreamp(preampDb)
    }

    fun commitEqualizerPreamp(preampDb: Double) {
        equalizerUiController.commitPreamp(preampDb)
    }

    fun cancelEqualizerPreampPreview(preampDb: Double) {
        equalizerUiController.cancelPreampPreview(preampDb)
    }

    fun setEqualizerAutomaticHeadroomEnabled(
        enabled: Boolean
    ) {
        equalizerUiController
            .setAutomaticHeadroomEnabled(enabled)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        equalizerUiController.setLimiterEnabled(enabled)
    }

    fun previewLimiterCeiling(ceilingDbfs: Double) {
        equalizerUiController.previewLimiterCeiling(ceilingDbfs)
    }

    fun commitLimiterCeiling(ceilingDbfs: Double) {
        equalizerUiController.commitLimiterCeiling(ceilingDbfs)
    }

    fun cancelLimiterCeilingPreview(ceilingDbfs: Double) {
        equalizerUiController
            .cancelLimiterCeilingPreview(ceilingDbfs)
    }

    fun resetLimiterMeters() {
        equalizerUiController.resetLimiterMeters()
    }

    fun applyBuiltInEqualizerPreset(index: Int) {
        equalizerUiController.applyBuiltInPreset(index)
    }

    fun applyUserEqualizerPreset(presetId: String) {
        equalizerUiController.applyUserPreset(presetId)
    }

    fun saveUserEqualizerPreset(name: String) {
        equalizerUiController.saveUserPreset(name)
    }

    fun renameUserEqualizerPreset(
        presetId: String,
        name: String
    ) {
        equalizerUiController.renameUserPreset(presetId, name)
    }

    fun deleteUserEqualizerPreset(presetId: String) {
        equalizerUiController.deleteUserPreset(presetId)
    }

    fun selectParametricFilter(filterId: String?) {
        equalizerUiController.selectParametricFilter(filterId)
    }

    fun addParametricFilter() {
        equalizerUiController.addParametricFilter()
    }

    fun previewParametricFilter(filter: ParametricFilter) {
        equalizerUiController.previewParametricFilter(filter)
    }

    fun commitParametricFilter(filter: ParametricFilter) {
        equalizerUiController.commitParametricFilter(filter)
    }

    fun cancelParametricFilterPreview(
        filter: ParametricFilter
    ) {
        equalizerUiController
            .cancelParametricFilterPreview(filter)
    }

    fun moveParametricFilter(
        filterId: String,
        destinationIndex: Int
    ) {
        equalizerUiController.moveParametricFilter(
            filterId,
            destinationIndex
        )
    }

    fun deleteParametricFilter(filterId: String) {
        equalizerUiController.deleteParametricFilter(filterId)
    }

    fun applyParametricFlatPreset() {
        equalizerUiController.applyParametricFlatPreset()
    }

    fun applyParametricUserPreset(presetId: String) {
        equalizerUiController.applyParametricUserPreset(presetId)
    }

    fun saveParametricUserPreset(name: String) {
        equalizerUiController.saveParametricUserPreset(name)
    }

    fun renameParametricUserPreset(
        presetId: String,
        name: String
    ) {
        equalizerUiController.renameParametricUserPreset(
            presetId,
            name
        )
    }

    fun deleteParametricUserPreset(presetId: String) {
        equalizerUiController.deleteParametricUserPreset(presetId)
    }

    fun resetEqualizerToFlat() {
        equalizerUiController.resetToFlat()
    }

    fun setEqualizerComparisonBypassed(bypassed: Boolean) {
        equalizerUiController.setComparisonBypassed(bypassed)
    }

    fun closeEqualizerScreen() {
        equalizerUiController.closeScreen()
    }

    internal fun openEqualizerImportPreview(
        text: String,
        sourceName: String?
    ) {
        equalizerUiController.openImportPreview(text, sourceName)
    }

    internal fun dismissEqualizerImportPreview() {
        equalizerUiController.dismissImportPreview()
    }

    internal fun updateEqualizerImportPreview(
        transform: (EqualizerImportPreviewState) ->
        EqualizerImportPreviewState
    ) {
        equalizerUiController.updateImportPreview(transform)
    }

    internal fun replaceWithImportedEqualizerProfile() {
        equalizerUiController.replaceWithImportedProfile()
    }

    internal fun saveImportedEqualizerProfile(apply: Boolean) {
        equalizerUiController.saveImportedProfile(apply)
    }

    fun loadSongs() {
        libraryController.loadSongs()
    }

    fun onMediaAccessGranted() {
        if (libraryController.setMediaAccessGranted(true)) {
            libraryController.loadSongs()
        }
    }

    fun onMediaAccessRevoked() {
        libraryController.setMediaAccessGranted(false)
    }

    fun refreshArtwork() {
        libraryController.refreshArtwork()
    }

    fun setFolderArtworkTreeUri(uri: Uri?) {
        libraryController.setFolderArtworkTreeUri(uri)
    }

    fun refreshFolderArtwork() {
        libraryController.refreshFolderArtwork()
    }

    fun scanLibrary() {
        libraryController.scanLibrary()
    }

    fun savePlayerState() {
        playbackController.savePlayerState()
    }

    fun playSelectedSong(
        song: Song,
        playbackContext: List<Song>
    ) {
        playbackController.playSelectedSong(
            song = song,
            playbackContext = playbackContext
        )
    }

    fun playSongsFromContext(
        playbackContext: List<Song>,
        shuffleMode: PlaybackShuffleMode
    ) {
        playbackController.playSongsFromContext(
            playbackContext = playbackContext,
            shuffleMode = shuffleMode
        )
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun skipToPrevious() {
        playbackController.skipToPrevious()
    }

    fun skipToNext() {
        playbackController.skipToNext()
    }

    fun seekTo(position: Int) {
        playbackController.seekTo(position)
        lyricsPlaybackController.onSeek(position.toLong())
    }

    fun setLyricsVisible(visible: Boolean) {
        lyricsPlaybackController.setVisible(visible)
    }

    fun suspendLyricsAutoFollow() {
        lyricsPlaybackController.suspendAutoFollow()
    }

    fun returnLyricsToCurrentLine() {
        lyricsPlaybackController.returnToCurrentLine()
    }

    fun rescanLyrics() {
        lyricsPlaybackController.rescan()
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackController.cycleRepeatMode()
    }

    fun addSongToQueue(song: Song) {
        playbackController.addSongToQueue(song)
    }

    fun addSongToPlayNext(song: Song) {
        playbackController.addSongToPlayNext(song)
    }

    fun removeFirstMatchingSongFromQueue(song: Song) {
        playbackController.removeFirstMatchingSongFromQueue(song)
    }

    fun removeLastMatchingSongFromQueue(song: Song) {
        playbackController.removeLastMatchingSongFromQueue(song)
    }

    fun removeSongFromQueue(index: Int) {
        playbackController.removeSongFromQueue(index)
    }

    fun moveQueuedSongUp(index: Int) {
        playbackController.moveQueuedSongUp(index)
    }

    fun moveQueuedSongDown(index: Int) {
        playbackController.moveQueuedSongDown(index)
    }

    fun clearQueue() {
        playbackController.clearQueue()
    }

    fun addSongsToPlayNext(songs: List<Song>) {
        playbackController.addSongsToPlayNext(songs)
    }

    fun addSongsToQueue(songs: List<Song>) {
        playbackController.addSongsToQueue(songs)
    }

    fun removeFirstMatchingSongsFromQueue(songs: List<Song>) {
        playbackController.removeFirstMatchingSongsFromQueue(songs)
    }

    fun removeLastMatchingSongsFromQueue(songs: List<Song>) {
        playbackController.removeLastMatchingSongsFromQueue(songs)
    }

    fun toggleLibraryFolder(folderPath: String) {
        libraryController.toggleLibraryFolder(folderPath)
    }

    fun selectAllLibraryFolders() {
        libraryController.selectAllLibraryFolders()
    }

    fun clearSelectedLibraryFolders() {
        libraryController.clearSelectedLibraryFolders()
    }

    fun toggleInitialLibraryFolder(folderPath: String) {
        libraryController.toggleInitialLibraryFolder(folderPath)
    }

    fun clearInitialLibraryFolders() {
        libraryController.clearInitialLibraryFolders()
    }

    fun confirmInitialLibraryFolderSelection() {
        libraryController.confirmInitialLibraryFolderSelection()
    }

    fun toggleFavorite(song: Song) {
        libraryController.toggleFavorite(song)
    }

    fun createPlaylist(playlistName: String, folderId: Long? = null) {
        libraryController.createPlaylist(playlistName, folderId)
    }

    fun previewSmartPlaylist(
        draft: SmartPlaylistDraft,
        onComplete: (Result<SmartPlaylistResolution>) -> Unit
    ) = libraryController.previewSmartPlaylist(draft, onComplete)

    fun createSmartPlaylist(
        name: String,
        draft: SmartPlaylistDraft,
        folderId: Long?,
        template: SmartPlaylistTemplate?,
        onComplete: (Result<SmartPlaylistDefinition>) -> Unit
    ) = libraryController.createSmartPlaylist(name, draft, folderId, template, onComplete)

    fun updateSmartPlaylist(
        playlistId: Long,
        draft: SmartPlaylistDraft,
        onComplete: (Result<SmartPlaylistDefinition>) -> Unit
    ) = libraryController.updateSmartPlaylist(playlistId, draft, onComplete)

    fun loadSmartPlaylistData(
        playlistId: Long,
        onComplete: (Result<SmartPlaylistUiData>) -> Unit
    ) = libraryController.loadSmartPlaylistData(playlistId, onComplete)

    fun refreshGeneratedPlaylist(
        playlistId: Long,
        onComplete: (Result<SmartPlaylistResolution>) -> Unit
    ) = libraryController.refreshGeneratedPlaylist(playlistId, onComplete)

    fun resolveSmartPlaylist(
        playlistId: Long,
        onComplete: (Result<SmartPlaylistResolution>) -> Unit
    ) = libraryController.resolveSmartPlaylist(playlistId, onComplete)

    fun createPlaylistWithSongs(
        playlistName: String,
        initialSongs: List<Song>,
        onComplete: (Result<Playlist>) -> Unit = {}
    ) {
        libraryController.createPlaylistWithSongs(playlistName, initialSongs, onComplete)
    }

    fun createPlaylistFolder(name: String) {
        libraryController.createPlaylistFolder(name)
    }

    fun renamePlaylistFolder(folder: PlaylistFolder, newName: String) {
        libraryController.renamePlaylistFolder(folder, newName)
    }

    fun deletePlaylistFolder(folder: PlaylistFolder) {
        libraryController.deletePlaylistFolder(folder)
    }

    fun movePlaylistToFolder(playlist: Playlist, folderId: Long?) {
        libraryController.movePlaylistToFolder(playlist, folderId)
    }

    fun renamePlaylist(
        playlist: Playlist,
        newName: String
    ) {
        libraryController.renamePlaylist(
            playlist = playlist,
            newName = newName
        )
    }

    fun deletePlaylist(playlist: Playlist) {
        libraryController.deletePlaylist(playlist)
    }

    fun changePlaylistArtwork(
        playlist: Playlist,
        source: Uri,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        libraryController.changePlaylistArtwork(playlist, source, onComplete)
    }

    fun resetPlaylistArtwork(
        playlist: Playlist,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        libraryController.resetPlaylistArtwork(playlist, onComplete)
    }

    fun changeArtistPicture(
        identity: ArtistIdentity,
        source: Uri,
        onComplete: (Result<Unit>) -> Unit = {}
    ) = libraryController.changeArtistPicture(identity, source, onComplete)

    fun removeArtistPicture(
        identity: ArtistIdentity,
        onComplete: (Result<Unit>) -> Unit = {}
    ) = libraryController.removeArtistPicture(identity, onComplete)

    fun loadSelectedPlaylist(playlist: Playlist) {
        libraryController.loadSelectedPlaylist(playlist)
    }

    fun clearSelectedPlaylist() {
        libraryController.clearSelectedPlaylist()
    }

    fun preparePlaylistExport(
        playlist: Playlist,
        onPrepared: (Result<PreparedPlaylistExport>) -> Unit
    ) {
        libraryController.preparePlaylistExport(
            playlist = playlist,
            onPrepared = onPrepared
        )
    }

    fun exportM3uPlaylist(
        uri: Uri,
        songs: List<Song>,
        onExported: (Result<M3uExportResult>) -> Unit
    ) {
        libraryController.exportM3uPlaylist(
            uri = uri,
            songs = songs,
            onExported = onExported
        )
    }

    fun importM3uPlaylist(
        uri: Uri,
        onImported: (Result<PlaylistImportResult>) -> Unit
    ) {
        libraryController.importM3uPlaylist(
            uri = uri,
            onImported = onImported
        )
    }

    fun exportBackup(
        uri: Uri,
        onExported: (Result<BackupExportResult>) -> Unit
    ) {
        viewModelScope.launch {
            onExported(
                runCatching {
                    backupRepository.writeBackupToUri(uri)
                }
            )
        }
    }

    fun readBackupFromUri(
        uri: Uri,
        onRead: (Result<AppBackup>) -> Unit
    ) {
        viewModelScope.launch {
            onRead(
                runCatching {
                    backupRepository.readBackupFromUri(uri)
                }
            )
        }
    }

    fun summarizeBackupRestore(backup: AppBackup): BackupRestoreSummary {
        return backupRepository.summarizeRestore(backup)
    }

    fun restoreBackup(
        backup: AppBackup,
        onRestored: (Result<BackupRestoreResult>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val restoreResult = backupRepository.restoreBackup(backup)

                libraryController.refreshAfterBackupRestore()

                restoreResult
            }

            onRestored(result)
        }
    }

    fun addSongToPlaylist(
        playlist: Playlist,
        song: Song
    ) {
        libraryController.addSongToPlaylist(
            playlist = playlist,
            song = song
        )
    }

    fun addSongsToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        libraryController.addSongsToPlaylist(
            playlist = playlist,
            songs = songs
        )
    }

    fun removePlaylistSong(playlistSong: PlaylistSong) {
        libraryController.removePlaylistSong(playlistSong)
    }

    fun reorderPlaylistSongs(
        playlistId: Long,
        orderedPlaylistSongIds: List<Long>
    ) {
        libraryController.reorderPlaylistSongs(
            playlistId = playlistId,
            orderedPlaylistSongIds = orderedPlaylistSongIds
        )
    }

    fun preparePlaylistQueueSongs(
        playlist: Playlist,
        onPrepared: (Result<List<Song>>) -> Unit
    ) {
        libraryController.preparePlaylistQueueSongs(
            playlist = playlist,
            onPrepared = onPrepared
        )
    }

    fun refreshSongsAfterTagEdit(
        originalSong: Song,
        editedTags: EditableSongTags
    ) {
        libraryController.refreshSongsAfterTagEdit(
            originalSong = originalSong,
            editedTags = editedTags
        )
    }

    private suspend fun refreshBatchMetadataLibrary(
        songs: List<Song>
    ): BatchPostWriteStageResult {
        val refreshed = try {
            withContext(Dispatchers.IO) {
                songs.distinctBy { it.membershipKey() }.map { song ->
                    song to tagEditorRepository.readTags(song)
                }
            }
        } catch (exception: Exception) {
            return BatchPostWriteStageResult(
                BatchPostWriteStageStatus.FAILED,
                exception.message ?: "Updated metadata could not be reread for library refresh."
            )
        }
        val completion = withTimeoutOrNull(DEFAULT_BATCH_REFRESH_TIMEOUT_MS) {
            suspendCancellableCoroutine<Result<Unit>> { continuation ->
                libraryController.refreshSongsAfterBatchEdit(refreshed) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        }
        return when {
            completion == null -> BatchPostWriteStageResult(
                BatchPostWriteStageStatus.TIMED_OUT,
                "The library refresh did not finish within " +
                    "${DEFAULT_BATCH_REFRESH_TIMEOUT_MS / 1_000} seconds. " +
                    "Metadata writes remain verified."
            )
            completion.isSuccess -> BatchPostWriteStageResult.Success
            else -> BatchPostWriteStageResult(
                BatchPostWriteStageStatus.FAILED,
                completion.exceptionOrNull()?.message ?: "The library refresh failed."
            )
        }
    }

    override fun onCleared() {
        listeningAnalyticsController.release()
        equalizerUiController.release()
        playbackController.release()
        sleepTimerController.release()
        super.onCleared()
    }
}
