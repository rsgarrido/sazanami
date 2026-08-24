package com.example.cdplaya.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cdplaya.controller.LibraryController
import com.example.cdplaya.controller.SongRatingUiController
import com.example.cdplaya.controller.ListeningAnalyticsController
import com.example.cdplaya.controller.SleepTimerController
import com.example.cdplaya.controller.DefaultSpotifyListeningHistoryImportOperations
import com.example.cdplaya.controller.ListeningHistoryImportFile
import com.example.cdplaya.controller.SpotifyListeningHistoryImportController
import com.example.cdplaya.controller.DefaultListeningHistoryReconciliationOperations
import com.example.cdplaya.controller.ListeningHistoryReconciliationController
import com.example.cdplaya.controller.LinkedHistoricalReconciliation
import com.example.cdplaya.controller.ReconciliationReviewTab
import com.example.cdplaya.data.LocalReconciliationTarget
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.EditableSongTags
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.TagEditorRepository
import com.example.cdplaya.data.TagEditorResult
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.AnalyticsRangePreset
import com.example.cdplaya.data.AnalyticsRangeSelection
import com.example.cdplaya.data.AnalyticsZoneIdProvider
import com.example.cdplaya.data.ListeningAnalyticsRangeResolver
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.SongRatingRepository
import com.example.cdplaya.data.ListeningTrendMetric
import com.example.cdplaya.data.ListeningRankingCategory
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.preferences.AppPreferencesRepository
import com.example.cdplaya.data.home.HomePin
import com.example.cdplaya.data.backup.AppBackup
import com.example.cdplaya.data.backup.BackupExportResult
import com.example.cdplaya.data.backup.BackupRepository
import com.example.cdplaya.data.backup.BackupRestoreResult
import com.example.cdplaya.data.backup.BackupRestoreSummary
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.DatabaseProvider
import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyImportSourceProfileService
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportPreviewer
import com.example.cdplaya.BuildConfig
import com.example.cdplaya.lyrics.LocalLyricsServices
import com.example.cdplaya.lyrics.LyricsPlaybackController
import com.example.cdplaya.lyrics.LyricsPositionSource
import com.example.cdplaya.data.playlistfile.M3uExportResult
import com.example.cdplaya.data.playlistfile.PlaylistImportResult
import com.example.cdplaya.data.playlistfile.PreparedPlaylistExport
import com.example.cdplaya.player.PlaybackController
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.equalizer.EqualizerMode
import com.example.cdplaya.player.equalizer.EqualizerRuntimeState
import com.example.cdplaya.player.equalizer.parametric.ParametricFilter
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.player.PlaybackShuffleMode
import com.example.cdplaya.ui.equalizer.EqualizerUiController
import com.example.cdplaya.ui.equalizer.EqualizerImportPreviewState
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenField
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenOverrides
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.theme.customizationOptions
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import kotlinx.coroutines.launch
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
import com.example.cdplaya.ui.state.LibraryAppearanceUiState
import com.example.cdplaya.ui.state.LibraryCategoryAppearance
import com.example.cdplaya.ui.state.PlayerAppearanceUiState
import com.example.cdplaya.ui.state.category
import com.example.cdplaya.ui.library.LibraryViewCategory
import com.example.cdplaya.ui.library.LibraryViewOption
import com.example.cdplaya.ui.library.SongRatingFilter
import com.example.cdplaya.ui.library.viewCategory
import com.example.cdplaya.ui.home.HomeCustomizationUiState
import com.example.cdplaya.ui.player.theme.applyOverrides
import com.example.cdplaya.ui.player.theme.defaultTokens

class MusicViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val appDatabase: AppDatabase = DatabaseProvider.getDatabase(appContext)

    private val appPreferencesRepository = AppPreferencesRepository.getInstance(appContext)
    private val tagEditorRepository = TagEditorRepository()
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
            )
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
    fun toggleReconciliationItem(sourceId: Long) =
        listeningHistoryReconciliationController.toggleExpanded(sourceId)
    fun toggleLinkedReconciliationGroup(targetIdentityId: Long) =
        listeningHistoryReconciliationController.toggleLinkedGroup(targetIdentityId)
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
            modernSeekbarStyle = preferences.modernSeekbarStyle,
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
        }
    )

    private val listeningHistoryReconciliationController =
        ListeningHistoryReconciliationController(
            operations = DefaultListeningHistoryReconciliationOperations(
                database = appDatabase,
                currentSongs = { libraryController.uiState.value.songs }
            ),
            scope = viewModelScope
        )

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

    fun toggleFavorite(song: Song) {
        libraryController.toggleFavorite(song)
    }

    fun createPlaylist(playlistName: String) {
        libraryController.createPlaylist(playlistName)
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

    fun loadSelectedPlaylist(playlist: Playlist) {
        libraryController.loadSelectedPlaylist(playlist)
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

    fun movePlaylistSongUp(playlistSong: PlaylistSong) {
        libraryController.movePlaylistSongUp(playlistSong)
    }

    fun movePlaylistSongDown(playlistSong: PlaylistSong) {
        libraryController.movePlaylistSongDown(playlistSong)
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

    override fun onCleared() {
        listeningAnalyticsController.release()
        equalizerUiController.release()
        playbackController.release()
        sleepTimerController.release()
        super.onCleared()
    }
}
