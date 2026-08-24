package com.example.cdplaya.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.SystemClock
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import com.example.cdplaya.data.EditableSongTags
import com.example.cdplaya.data.FavoritesRepository
import com.example.cdplaya.data.FolderSelection
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.LibraryFolder
import com.example.cdplaya.data.LibraryRefreshEngine
import com.example.cdplaya.data.ListeningHistoryRepository
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.LibraryCacheRepository
import com.example.cdplaya.data.MusicLibraryData
import com.example.cdplaya.data.MediaLibraryAccessException
import com.example.cdplaya.data.stableKey
import com.example.cdplaya.data.MusicRepository
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.PlaylistsRepository
import com.example.cdplaya.data.PersistedSongReferenceRows
import com.example.cdplaya.data.ReconciliationGenerationCoordinator
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.SongReferenceIndex
import com.example.cdplaya.data.SongReferenceReconciliationPlanner
import com.example.cdplaya.data.SongReferenceResolution
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.data.sortSongsByDateAddedDescending
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.preferences.AppPreferencesRepository
import com.example.cdplaya.data.backup.BackupRepository
import com.example.cdplaya.data.playlistfile.M3uExportResult
import com.example.cdplaya.data.playlistfile.PlaylistFileRepository
import com.example.cdplaya.data.playlistfile.PlaylistImportResult
import com.example.cdplaya.data.playlistfile.PreparedPlaylistExport
import com.example.cdplaya.data.playlistfile.defaultImportedPlaylistName
import com.example.cdplaya.player.PlaybackController
import com.example.cdplaya.player.PlaybackLibraryBridge
import com.example.cdplaya.performance.PerformanceTraceNames
import com.example.cdplaya.performance.tracePerformance
import com.example.cdplaya.mediaaccess.LibraryPermissionGate
import com.example.cdplaya.ui.state.LibraryUiState
import com.example.cdplaya.ui.state.libraryUiState
import com.example.cdplaya.ui.state.toUiSummary
import com.example.cdplaya.ui.library.SongRatingFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal suspend fun <T> runLibraryScanOffMain(block: suspend () -> T): T {
    return withContext(Dispatchers.IO) { block() }
}

internal class LibraryPublicationTracker {
    private var lastSnapshot: MusicLibraryData? = null

    fun shouldPublish(snapshot: MusicLibraryData): Boolean {
        if (snapshot == lastSnapshot) return false
        lastSnapshot = snapshot
        return true
    }

    fun reset() {
        lastSnapshot = null
    }
}

class LibraryController(
    context: Context,
    private val appDatabase: AppDatabase,
    private val playbackController: PlaybackController,
    private val coroutineScope: CoroutineScope,
    private val onMediaAccessFailure: () -> Unit = {}
) {
    private val applicationContext = context.applicationContext

    private val appPreferencesRepository = AppPreferencesRepository.getInstance(applicationContext)
    private val favoritesRepository = FavoritesRepository(appDatabase.favoriteSongDao())
    private val playlistsRepository = PlaylistsRepository(appDatabase.playlistDao())
    private val listeningHistoryRepository = ListeningHistoryRepository(
        appDatabase.songPlayStatsDao()
    )
    private val listeningStatsRepository = ListeningStatsRepository(appDatabase)
    private val libraryCacheRepository = LibraryCacheRepository(appDatabase.cachedSongDao())
    private val playlistFileRepository = PlaylistFileRepository(applicationContext)
    private var refreshJob: Job? = null
    private var reconciliationJob: Job? = null
    private val reconciliationCoordinator = ReconciliationGenerationCoordinator()
    private var songReferenceIndex: SongReferenceIndex = SongReferenceIndex.EMPTY
    private var visibleSongMembershipKeys: Set<String> = emptySet()
    private val historyLibrarySnapshot = MutableStateFlow(
        IndexedLibrarySnapshot(SongReferenceIndex.EMPTY, emptySet())
    )
    private var libraryPublishCount = 0L
    private var libraryScanCount = 0L
    private val publicationTracker = LibraryPublicationTracker()
    private val libraryScanMutex = Mutex()
    private val permissionGate = LibraryPermissionGate()
    private var folderArtworkTreeUri: Uri? = null

    internal fun createBackupRepository(): BackupRepository = BackupRepository(
        context = applicationContext,
        favoritesRepository = favoritesRepository,
        playlistsRepository = playlistsRepository,
        listeningHistoryRepository = listeningHistoryRepository,
        appDatabase = appDatabase,
        appPreferencesRepository = appPreferencesRepository
    )

    private val _uiState = MutableStateFlow(LibraryUiState.Empty)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun selectSongRatingFilter(filter: SongRatingFilter) {
        _uiState.update { current -> current.copy(songRatingFilter = filter) }
    }

    private var lastLibraryRefreshResult: com.example.cdplaya.data.LibraryRefreshResult? = null

    private var songs: List<Song>
        get() = _uiState.value.songs
        set(value) = updateState { copy(songs = value.toList()) }
    private val libraryFolders: List<LibraryFolder>
        get() = _uiState.value.folders
    private var folderSelection: FolderSelection = FolderSelection.All
        set(value) {
            field = value.copy(customFolders = value.customFolders.toSet())
            updateState {
                copy(
                    folderSelectionMode = field.mode,
                    selectedFolders = field.customFolders,
                    excludedFolders = field.excludedFolders
                )
            }
        }
    private val selectedLibraryFolders: Set<String>
        get() = folderSelection.customFolders
    private var favoriteMembershipKeys: Set<String>
        get() = _uiState.value.favoriteMembershipKeys
        set(value) = updateState { copy(favoriteMembershipKeys = value.toSet()) }
    private var playlists: List<Playlist>
        get() = _uiState.value.playlists
        set(value) = updateState { copy(playlists = value.toList()) }
    private var selectedPlaylistName: String
        get() = _uiState.value.selectedPlaylistName
        set(value) = updateState { copy(selectedPlaylistName = value) }
    private var selectedPlaylistSongs: List<PlaylistSong>
        get() = _uiState.value.selectedPlaylistSongs
        set(value) = updateState { copy(selectedPlaylistSongs = value.toList()) }
    private inline fun updateState(transform: LibraryUiState.() -> LibraryUiState) {
        _uiState.update { current -> current.transform() }
    }

    init {
        coroutineScope.launch {
            collectProductionListeningHistory(
                history = listeningStatsRepository.observeProductionHistory(),
                library = historyLibrarySnapshot
            ) { resolved ->
                _uiState.update { current ->
                    current.copy(
                        recentlyPlayedSongs = resolved.recentlyPlayed.toList(),
                        mostPlayedSongs = resolved.mostPlayed.toList()
                    )
                }
            }
        }
    }

    fun loadSavedUserData() {
        loadFavoriteMembershipKeys()
        loadPlaylists()
    }

    fun setMediaAccessGranted(granted: Boolean): Boolean {
        val changed = permissionGate.updateAccess(granted)
        if (!changed) return false
        if (!granted) {
            refreshJob?.cancel()
            reconciliationJob?.cancel()
            publicationTracker.reset()
            songReferenceIndex = SongReferenceIndex.EMPTY
            visibleSongMembershipKeys = emptySet()
            historyLibrarySnapshot.value = IndexedLibrarySnapshot(
                SongReferenceIndex.EMPTY,
                emptySet()
            )
            updateState {
                copy(
                    songs = emptyList(),
                    folders = emptyList(),
                    recentlyAddedSongs = emptyList(),
                    hasPublishedInitialLibraryState = false,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null
                )
            }
            PlaybackLibraryBridge.updateSongs(emptyList())
        }
        return true
    }

    fun setFolderArtworkTreeUri(uri: Uri?) {
        folderArtworkTreeUri = uri
    }

    fun refreshFolderArtwork() {
        val scanToken = permissionGate.tokenOrNull() ?: return
        if (songs.isNotEmpty()) {
            updateState { copy(isRefreshing = true, errorMessage = null) }
        }
        coroutineScope.launch {
            try {
                val libraryData = withContext(Dispatchers.IO) {
                    libraryScanMutex.withLock {
                        if (!permissionGate.isCurrent(scanToken)) throw CancellationException()
                        val cachedSongs = libraryCacheRepository.getAllCachedSongs()
                        val updatedSongs = MusicRepository(applicationContext).applyFolderArtwork(
                            songs = cachedSongs,
                            folderArtworkTreeUri = folderArtworkTreeUri
                        )
                        if (updatedSongs != cachedSongs) {
                            libraryCacheRepository.replaceCachedSongs(updatedSongs)
                        }
                        com.example.cdplaya.data.buildMusicLibraryData(
                            allSongs = updatedSongs,
                            folderSelection = folderSelection
                        )
                    }
                }
                if (permissionGate.isCurrent(scanToken)) {
                    publishLibraryData(libraryData, reconcilePlayback = true)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (permissionGate.isCurrent(scanToken)) {
                    updateState {
                        copy(
                            isRefreshing = false,
                            errorMessage = exception.message?.let { "Artwork refresh failed: $it" }
                                ?: "Artwork refresh failed."
                        )
                    }
                }
            }
        }
    }

    fun loadSongs() {
        launchProtectedRefresh { scanToken ->
            val startupStartedAt = SystemClock.elapsedRealtime()
            val preferencesStartedAt = SystemClock.elapsedRealtime()
            val savedPreferences = appPreferencesRepository.awaitLoadedState()
            val savedSelection = FolderSelection.fromStored(
                storedMode = savedPreferences.folderSelectionMode.name,
                storedFolders = savedPreferences.selectedLibraryFolders
            )
            tracePerformance(PerformanceTraceNames.PREFERENCES_READY) { Unit }
            debugLibraryTiming(
                "startup-preferences-ready elapsedMs=" +
                        "${SystemClock.elapsedRealtime() - preferencesStartedAt} " +
                        "sinceLoadStartMs=${SystemClock.elapsedRealtime() - startupStartedAt}"
            )
            folderSelection = savedSelection

            val cacheProbeStartedAt = SystemClock.elapsedRealtime()
            val hasCachedSongs = withContext(Dispatchers.IO) {
                libraryCacheRepository.hasCachedSongs()
            }
            debugLibraryTiming(
                "startup-cache-probe elapsedMs=${SystemClock.elapsedRealtime() - cacheProbeStartedAt} " +
                        "hasCache=$hasCachedSongs"
            )

            if (hasCachedSongs) {
                val cachedLibraryData = loadCachedLibraryDataForPublication(savedSelection)

                if (permissionGate.isCurrent(scanToken)) {
                    val publicationStartedAt = SystemClock.elapsedRealtime()
                    publishLibraryData(
                        libraryData = cachedLibraryData,
                        reconcilePlayback = false,
                        traceName = PerformanceTraceNames.CACHE_FIRST_PUBLICATION
                    )
                    debugLibraryTiming(
                        "startup-cache-first-ready elapsedMs=" +
                                "${SystemClock.elapsedRealtime() - publicationStartedAt} " +
                                "sinceLoadStartMs=${SystemClock.elapsedRealtime() - startupStartedAt} " +
                                "songs=${cachedLibraryData.songs.size}"
                    )
                }
            }

            val freshLibraryData = withContext(Dispatchers.IO) {
                scanFreshLibraryAndUpdateCache(savedSelection, scanToken = scanToken)
            }

            if (permissionGate.isCurrent(scanToken)) {
                publishLibraryData(
                    libraryData = freshLibraryData,
                    reconcilePlayback = hasCachedSongs
                )
            }
        }
    }

    fun refreshArtwork() {
        val idsToRefresh = songs.mapTo(mutableSetOf(), Song::id)
        if (idsToRefresh.isEmpty()) return
        launchProtectedRefresh { scanToken ->
            val libraryData = withContext(Dispatchers.IO) {
                scanFreshLibraryAndUpdateCache(
                    folderSelection = folderSelection,
                    forceArtworkRefreshIds = idsToRefresh,
                    scanToken = scanToken
                )
            }
            if (permissionGate.isCurrent(scanToken)) {
                publishLibraryData(libraryData, reconcilePlayback = true)
            }
        }
    }

    fun scanLibrary() {
        launchProtectedRefresh { scanToken ->
            val libraryData = withContext(Dispatchers.IO) {
                scanFreshLibraryAndUpdateCache(
                    folderSelection = folderSelection,
                    scanToken = scanToken
                )
            }
            if (permissionGate.isCurrent(scanToken)) {
                publishLibraryData(libraryData, reconcilePlayback = true)
            }
        }
    }

    fun toggleLibraryFolder(folderPath: String) {
        val updatedSelection = folderSelection.toggle(
            folderPath = folderPath,
            availableFolders = libraryFolders.map { it.path }
        )
        folderSelection = updatedSelection

        coroutineScope.launch {
            appPreferencesRepository.setLibraryFolderSelection(updatedSelection)
        }
        reloadSongsAfterFolderChange()
    }

    fun selectAllLibraryFolders() {
        val updatedSelection = FolderSelection.All
        folderSelection = updatedSelection

        coroutineScope.launch {
            appPreferencesRepository.setLibraryFolderSelection(updatedSelection)
        }
        reloadSongsAfterFolderChange()
    }

    fun clearSelectedLibraryFolders() {
        val updatedSelection = FolderSelection(FolderSelectionMode.CUSTOM, emptySet())
        folderSelection = updatedSelection

        coroutineScope.launch {
            appPreferencesRepository.setLibraryFolderSelection(updatedSelection)
        }
        reloadSongsAfterFolderChange()
    }

    fun refreshSongsAfterTagEdit(
        originalSong: Song,
        editedTags: EditableSongTags
    ) {
        launchProtectedRefresh { scanToken ->
            val updatedUserData = withContext(Dispatchers.IO) {
                favoritesRepository.updateSongReferenceAfterTagEdit(
                    originalSong = originalSong,
                    editedTags = editedTags
                )
                playlistsRepository.updateSongReferencesAfterTagEdit(
                    originalSong = originalSong,
                    editedTags = editedTags
                )
                listeningHistoryRepository.updateSongReferenceAfterTagEdit(
                    originalSong = originalSong,
                    editedTags = editedTags
                )
                favoritesRepository.getFavoriteMembershipKeys() to
                        playlistsRepository.getPlaylists()
            }
            val updatedFavoriteMembershipKeys = updatedUserData.first
            val updatedPlaylists = updatedUserData.second

            val selectedPlaylistId = selectedPlaylistSongs.firstOrNull()?.playlistId

            val updatedSelectedPlaylistSongs = selectedPlaylistId?.let { playlistId ->
                getResolvedPlaylistSongs(playlistId)
            }

            val libraryData = withContext(Dispatchers.IO) {
                scanFreshLibraryAndUpdateCache(
                    folderSelection = folderSelection,
                    forceArtworkRefreshIds = setOf(originalSong.id),
                    scanToken = scanToken
                )
            }

            if (!permissionGate.isCurrent(scanToken)) return@launchProtectedRefresh
            favoriteMembershipKeys = updatedFavoriteMembershipKeys
            playlists = updatedPlaylists

            if (updatedSelectedPlaylistSongs != null) {
                selectedPlaylistSongs = updatedSelectedPlaylistSongs
            }
            publishLibraryData(libraryData, reconcilePlayback = true)
        }
    }

    fun toggleFavorite(song: Song) {
        val membershipKey = song.membershipKey()
        val shouldFavorite = membershipKey !in favoriteMembershipKeys

        favoriteMembershipKeys = if (shouldFavorite) {
            favoriteMembershipKeys + membershipKey
        } else {
            favoriteMembershipKeys - membershipKey
        }

        coroutineScope.launch {
            if (shouldFavorite) {
                favoritesRepository.addFavorite(song)
            } else {
                favoritesRepository.removeFavorite(song)
            }
        }
    }

    fun createPlaylist(playlistName: String) {
        coroutineScope.launch {
            val wasCreated = playlistsRepository.createPlaylist(playlistName)

            if (wasCreated) {
                loadPlaylists()
            }
        }
    }

    fun renamePlaylist(
        playlist: Playlist,
        newName: String
    ) {
        coroutineScope.launch {
            val trimmedName = newName.trim()

            val wasRenamed = playlistsRepository.renamePlaylist(
                playlistId = playlist.playlistId,
                newName = trimmedName
            )

            if (wasRenamed) {
                loadPlaylists()

                val renamedPlaylistWasSelected =
                    selectedPlaylistSongs.any { playlistSong ->
                        playlistSong.playlistId == playlist.playlistId
                    }

                if (renamedPlaylistWasSelected) {
                    selectedPlaylistName = trimmedName
                }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        coroutineScope.launch {
            playlistsRepository.deletePlaylist(playlist.playlistId)
            loadPlaylists()

            val deletedPlaylistWasSelected =
                selectedPlaylistName == playlist.name ||
                        selectedPlaylistSongs.any { playlistSong ->
                            playlistSong.playlistId == playlist.playlistId
                        }

            if (deletedPlaylistWasSelected) {
                selectedPlaylistName = "Playlist"
                selectedPlaylistSongs = emptyList()
            }
        }
    }

    fun loadSelectedPlaylist(playlist: Playlist) {
        coroutineScope.launch {
            selectedPlaylistName = playlist.name
            selectedPlaylistSongs = getResolvedPlaylistSongs(playlist.playlistId)
        }
    }

    fun preparePlaylistExport(
        playlist: Playlist,
        onPrepared: (Result<PreparedPlaylistExport>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                val playlistSongs = getResolvedPlaylistSongs(playlist.playlistId)
                val exportableSongs = playlistSongs.mapNotNull(PlaylistSong::resolvedSong)

                PreparedPlaylistExport(
                    playlistName = playlist.name,
                    songs = exportableSongs,
                    unavailableSongCount = playlistSongs.size - exportableSongs.size
                )
            }

            onPrepared(result)
        }
    }

    fun exportM3uPlaylist(
        uri: Uri,
        songs: List<Song>,
        onExported: (Result<M3uExportResult>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                playlistFileRepository.exportM3uPlaylist(
                    uri = uri,
                    songs = songs
                )
            }

            onExported(result)
        }
    }

    fun importM3uPlaylist(
        uri: Uri,
        onImported: (Result<PlaylistImportResult>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                val fileImportResult = playlistFileRepository.importM3uPlaylist(
                    uri = uri,
                    librarySongs = songs
                )

                if (fileImportResult.matchedSongs.isEmpty()) {
                    PlaylistImportResult(
                        playlistName = null,
                        importedSongCount = 0,
                        unmatchedEntryCount = fileImportResult.unmatchedEntryCount
                    )
                } else {
                    val importedPlaylist = playlistsRepository.createPlaylistWithUniqueName(
                        preferredName = defaultImportedPlaylistName(
                            fileImportResult.sourceDisplayName
                        ),
                        songs = fileImportResult.matchedSongs
                    )

                    playlists = playlistsRepository.getPlaylists()

                    PlaylistImportResult(
                        playlistName = importedPlaylist.name,
                        importedSongCount = fileImportResult.matchedSongCount,
                        unmatchedEntryCount = fileImportResult.unmatchedEntryCount
                    )
                }
            }

            onImported(result)
        }
    }

    fun addSongToPlaylist(
        playlist: Playlist,
        song: Song
    ) {
        addSongsToPlaylist(
            playlist = playlist,
            songs = listOf(song)
        )
    }

    fun addSongsToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        if (songs.isEmpty()) {
            return
        }

        coroutineScope.launch {
            playlistsRepository.addSongsToPlaylist(
                playlistId = playlist.playlistId,
                songs = songs
            )

            loadPlaylists()

            val addedToSelectedPlaylist =
                selectedPlaylistSongs.any { playlistSong ->
                    playlistSong.playlistId == playlist.playlistId
                }

            if (addedToSelectedPlaylist) {
                selectedPlaylistSongs = getResolvedPlaylistSongs(playlist.playlistId)
            }
        }
    }

    fun removePlaylistSong(playlistSong: PlaylistSong) {
        coroutineScope.launch {
            playlistsRepository.removePlaylistSong(
                playlistId = playlistSong.playlistId,
                playlistSongId = playlistSong.playlistSongId
            )

            loadPlaylists()
            selectedPlaylistSongs = getResolvedPlaylistSongs(playlistSong.playlistId)
        }
    }

    fun movePlaylistSongUp(playlistSong: PlaylistSong) {
        coroutineScope.launch {
            playlistsRepository.movePlaylistSongUp(
                playlistId = playlistSong.playlistId,
                playlistSongId = playlistSong.playlistSongId
            )

            loadPlaylists()
            selectedPlaylistSongs = getResolvedPlaylistSongs(
                playlistSong.playlistId
            )
        }
    }

    fun movePlaylistSongDown(playlistSong: PlaylistSong) {
        coroutineScope.launch {
            playlistsRepository.movePlaylistSongDown(
                playlistId = playlistSong.playlistId,
                playlistSongId = playlistSong.playlistSongId
            )

            loadPlaylists()
            selectedPlaylistSongs = getResolvedPlaylistSongs(
                playlistSong.playlistId
            )
        }
    }

    internal suspend fun refreshAfterBackupRestore() {
        val restoredData = withContext(Dispatchers.IO) {
            BackupRestoredUserData(
                folderSelection = appPreferencesRepository.awaitLoadedState().let { preferences ->
                    FolderSelection.fromStored(
                        storedMode = preferences.folderSelectionMode.name,
                        storedFolders = preferences.selectedLibraryFolders
                    )
                },
                favoriteMembershipKeys = favoritesRepository.getFavoriteMembershipKeys(),
                playlists = playlistsRepository.getPlaylists()
            )
        }
        val resolvedFolderSelection = restoredData.folderSelection.copy(
            customFolders = resolveRestoredFolderSelections(
                restoredData.folderSelection.customFolders
            ),
            excludedFolders = resolveRestoredFolderSelections(
                restoredData.folderSelection.excludedFolders
            )
        )
        if (resolvedFolderSelection != restoredData.folderSelection) {
            appPreferencesRepository.setLibraryFolderSelection(resolvedFolderSelection)
        }
        val folderSelectionChanged = folderSelection != resolvedFolderSelection

        folderSelection = resolvedFolderSelection
        favoriteMembershipKeys = restoredData.favoriteMembershipKeys
        playlists = restoredData.playlists
        selectedPlaylistName = "Playlist"
        selectedPlaylistSongs = emptyList()
        if (folderSelectionChanged) {
            reloadSongsAfterFolderChange()
        } else {
            reconcileUserSongReferences(songs, songReferenceIndex)
        }
    }

    private fun reloadSongsAfterFolderChange() {
        launchProtectedRefresh { scanToken ->
            val hasCachedSongs = withContext(Dispatchers.IO) {
                libraryCacheRepository.hasCachedSongs()
            }

            if (hasCachedSongs) {
                val cachedLibraryData =
                    loadCachedLibraryDataForPublication(folderSelection)

                if (permissionGate.isCurrent(scanToken)) {
                    publishLibraryData(
                        libraryData = cachedLibraryData,
                        reconcilePlayback = true
                    )
                }
                return@launchProtectedRefresh
            }

            val freshLibraryData = withContext(Dispatchers.IO) {
                scanFreshLibraryAndUpdateCache(
                    folderSelection = folderSelection,
                    scanToken = scanToken
                )
            }

            if (permissionGate.isCurrent(scanToken)) {
                publishLibraryData(
                    libraryData = freshLibraryData,
                    reconcilePlayback = true
                )
            }
        }
    }

    private fun launchProtectedRefresh(block: suspend (scanToken: Long) -> Unit) {
        val scanToken = permissionGate.tokenOrNull() ?: return
        refreshJob?.cancel()
        updateState {
            copy(
                isLoading = songs.isEmpty(),
                isRefreshing = songs.isNotEmpty(),
                errorMessage = null
            )
        }
        refreshJob = coroutineScope.launch {
            try {
                block(scanToken)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: MediaLibraryAccessException) {
                if (permissionGate.isCurrent(scanToken)) {
                    updateState {
                        copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "Audio access is no longer available."
                        )
                    }
                    onMediaAccessFailure()
                }
            } catch (exception: Exception) {
                if (permissionGate.isCurrent(scanToken)) {
                    updateState {
                        copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = exception.message
                                ?.let { "Library query failed: $it" }
                                ?: "Library query failed."
                        )
                    }
                }
            }
        }
    }

    private suspend fun publishLibraryData(
        libraryData: MusicLibraryData,
        reconcilePlayback: Boolean,
        traceName: String = PerformanceTraceNames.LIBRARY_PUBLICATION
    ) {
        if (!publicationTracker.shouldPublish(libraryData)) {
            updateState {
                copy(
                    lastRefreshSummary = lastLibraryRefreshResult?.toUiSummary(),
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null
                )
            }
            return
        }
        val indexStartedAt = SystemClock.elapsedRealtime()
        val indexedSnapshot = withContext(Dispatchers.Default) {
            tracePerformance(PerformanceTraceNames.LIBRARY_INDEX_CONSTRUCTION) {
                IndexedLibrarySnapshot(
                    index = SongReferenceIndex.build(libraryData.referenceSongs),
                    visibleMembershipKeys = libraryData.songs.mapTo(mutableSetOf()) {
                        it.membershipKey()
                    }
                )
            }
        }
        debugLibraryTiming(
            "final-library-index elapsedMs=${SystemClock.elapsedRealtime() - indexStartedAt} " +
                    "referenceSongs=${libraryData.referenceSongs.size} " +
                    "visibleSongs=${libraryData.songs.size}"
        )
        val publicationStartedAt = SystemClock.elapsedRealtime()
        publishLibrarySnapshot(libraryData, reconcilePlayback, indexedSnapshot, traceName)
        debugLibraryTiming(
            "library-publication-complete trace=$traceName elapsedMs=" +
                    "${SystemClock.elapsedRealtime() - publicationStartedAt} " +
                    "songs=${libraryData.songs.size}"
        )
    }

    private fun publishLibrarySnapshot(
        libraryData: MusicLibraryData,
        reconcilePlayback: Boolean,
        indexedSnapshot: IndexedLibrarySnapshot,
        traceName: String
    ) = tracePerformance(traceName) {
        songReferenceIndex = indexedSnapshot.index
        visibleSongMembershipKeys = indexedSnapshot.visibleMembershipKeys
        historyLibrarySnapshot.value = indexedSnapshot
        libraryPublishCount += 1
        val publishedSongs = libraryData.songs.toList()
        _uiState.update { current ->
            libraryUiState(
                songs = publishedSongs,
                folders = libraryData.libraryFolders,
                folderSelectionMode = folderSelection.mode,
                selectedFolders = folderSelection.customFolders,
                excludedFolders = folderSelection.excludedFolders,
                favoriteMembershipKeys = current.favoriteMembershipKeys,
                playlists = current.playlists,
                selectedPlaylistName = current.selectedPlaylistName,
                selectedPlaylistSongs = current.selectedPlaylistSongs,
                recentlyPlayedSongs = current.recentlyPlayedSongs,
                mostPlayedSongs = current.mostPlayedSongs,
                recentlyAddedSongs = sortSongsByDateAddedDescending(publishedSongs),
                songRatingFilter = current.songRatingFilter,
                unresolvedFavoriteCount = current.unresolvedFavoriteCount,
                unresolvedPlaylistRowCount = current.unresolvedPlaylistRowCount,
                unresolvedListeningHistoryCount = current.unresolvedListeningHistoryCount,
                lastRefreshResult = lastLibraryRefreshResult,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null
            )
        }
        PlaybackLibraryBridge.updateSongs(publishedSongs)
        reconcileUserSongReferences(publishedSongs, indexedSnapshot.index)

        if (reconcilePlayback) {
            playbackController.handleLibrarySongsChanged(publishedSongs)
        } else {
            playbackController.setLibrarySongs(publishedSongs)
        }
    }

    private suspend fun scanFreshLibraryAndUpdateCache(
        folderSelection: FolderSelection,
        forceArtworkRefreshIds: Set<Long> = emptySet(),
        scanToken: Long
    ): MusicLibraryData = runLibraryScanOffMain {
        libraryScanMutex.withLock {
            if (!permissionGate.isCurrent(scanToken)) throw CancellationException()
            libraryScanCount += 1
            val scanNumber = libraryScanCount
            val repository = MusicRepository(applicationContext)
            val cacheReadStartedAt = SystemClock.elapsedRealtime()
            val cachedSongs = libraryCacheRepository.getAllCachedSongs()
            debugLibraryTiming(
                "cache-read elapsedMs=${SystemClock.elapsedRealtime() - cacheReadStartedAt} " +
                        "songs=${cachedSongs.size} scan=$scanNumber"
            )

            val startedAt = SystemClock.elapsedRealtime()
            val indexSongs = repository.queryLibraryIndex()
            LibraryRefreshEngine.fallbackForIncompleteScan(cachedSongs, indexSongs)?.let { fallback ->
                return@withLock com.example.cdplaya.data.buildMusicLibraryData(
                    allSongs = fallback.songs,
                    folderSelection = folderSelection
                )
            }
            checkNotNull(indexSongs)
            if (!permissionGate.isCurrent(scanToken)) throw CancellationException()

            // A brand-new library should become usable as soon as the cheap MediaStore index is
            // available. Persist and publish that base snapshot before per-file artwork enrichment.
            if (cachedSongs.isEmpty()) {
                libraryCacheRepository.replaceCachedSongs(indexSongs)
                val indexedLibraryData = com.example.cdplaya.data.buildMusicLibraryData(
                    allSongs = indexSongs,
                    folderSelection = folderSelection
                )
                publishLibraryData(
                    libraryData = indexedLibraryData,
                    reconcilePlayback = false,
                    traceName = PerformanceTraceNames.INITIAL_INDEX_PUBLICATION
                )
                updateState { copy(isLoading = false, isRefreshing = true) }
            }

            val refreshResult = tracePerformance(PerformanceTraceNames.LIBRARY_ENRICHMENT) {
                repository.refreshLibrary(
                    cachedSongs = cachedSongs,
                    forceArtworkRefreshIds = forceArtworkRefreshIds,
                    indexSongsOverride = indexSongs,
                    folderArtworkTreeUri = folderArtworkTreeUri
                )
            }
            if (!permissionGate.isCurrent(scanToken)) throw CancellationException()
            lastLibraryRefreshResult = refreshResult

            if (applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Log.d(
                    "LibraryRefresh",
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                            "reused=${refreshResult.reusedCount} added=${refreshResult.addedCount} " +
                            "updated=${refreshResult.updatedCount} moved=${refreshResult.movedCount} " +
                            "removed=${refreshResult.removedCount} enriched=${refreshResult.enrichmentCount} " +
                            "artworkRepairs=${refreshResult.artworkRepairCount} " +
                            "complete=${refreshResult.successfulCompleteScan}"
                )
            }

            if (refreshResult.successfulCompleteScan && refreshResult.requiresCacheWrite) {
                val cacheWriteStartedAt = SystemClock.elapsedRealtime()
                libraryCacheRepository.replaceCachedSongs(refreshResult.songs)
                debugLibraryTiming(
                    "cache-write elapsedMs=${SystemClock.elapsedRealtime() - cacheWriteStartedAt} " +
                            "songs=${refreshResult.songs.size} scan=$scanNumber"
                )
            } else {
                debugLibraryTiming("cache-write elapsedMs=0 songs=0 scan=$scanNumber skipped=true")
            }
            val folderDiscoveryStartedAt = SystemClock.elapsedRealtime()
            val libraryData = com.example.cdplaya.data.buildMusicLibraryData(
                allSongs = refreshResult.songs,
                folderSelection = folderSelection
            )
            debugLibraryTiming(
                "folder-discovery elapsedMs=" +
                        "${SystemClock.elapsedRealtime() - folderDiscoveryStartedAt} " +
                        "folders=${libraryData.libraryFolders.size} scan=$scanNumber"
            )
            debugLibraryTiming(
                "scan-complete elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                        "scan=$scanNumber token=$scanToken"
            )
            libraryData
        }
    }

    private suspend fun loadCachedLibraryDataForPublication(
        folderSelection: FolderSelection
    ): MusicLibraryData = runLibraryScanOffMain {
        val cacheReadStartedAt = SystemClock.elapsedRealtime()
        val cachedSongs = libraryCacheRepository.getAllCachedSongs()
        debugLibraryTiming(
            "cache-publication-read elapsedMs=" +
                    "${SystemClock.elapsedRealtime() - cacheReadStartedAt} songs=${cachedSongs.size}"
        )

        // Do not stat every embedded-artwork cache file before publishing the Room snapshot.
        // EmbeddedArtworkProvider can reconstruct a missing file on demand for visible artwork,
        // while the fresh MediaStore reconciliation immediately following this publication will
        // validate/repair stale references in the background. A missing cover must never delay
        // getting the user's songs onto Home.
        debugLibraryTiming(
            "cache-publication-artwork-preflight skipped=true songs=${cachedSongs.size}"
        )

        val libraryBuildStartedAt = SystemClock.elapsedRealtime()
        val libraryData = com.example.cdplaya.data.buildMusicLibraryData(
            allSongs = cachedSongs,
            folderSelection = folderSelection
        )
        debugLibraryTiming(
            "cache-publication-library-build elapsedMs=" +
                    "${SystemClock.elapsedRealtime() - libraryBuildStartedAt} " +
                    "songs=${libraryData.songs.size} folders=${libraryData.libraryFolders.size}"
        )
        libraryData
    }

    private fun loadFavoriteMembershipKeys() {
        coroutineScope.launch {
            favoriteMembershipKeys = favoritesRepository.getFavoriteMembershipKeys()
        }
    }

    private fun reconcileUserSongReferences(
        currentSongs: List<Song>,
        index: SongReferenceIndex
    ) {
        val generation = reconciliationCoordinator.nextGeneration()
        reconciliationJob?.cancel()
        val selectedPlaylistId = selectedPlaylistSongs.firstOrNull()?.playlistId
        val visibleMembershipKeys = currentSongs.mapTo(mutableSetOf()) { it.membershipKey() }
        reconciliationJob = coroutineScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val reconciled = reconciliationCoordinator.runLatest(generation) {
                val persistedRows = withContext(Dispatchers.IO) {
                    PersistedSongReferenceRows(
                        favorites = favoritesRepository.loadReferenceRows(),
                        playlistRows = playlistsRepository.loadReferenceRows(),
                        historyRows = listeningHistoryRepository.loadReferenceRows()
                    )
                }
                val plan = withContext(Dispatchers.Default) {
                    tracePerformance(PerformanceTraceNames.RECONCILIATION_PLAN) {
                        SongReferenceReconciliationPlanner.plan(index, persistedRows)
                    }
                }
                val storedResults = withContext(Dispatchers.IO) {
                    appDatabase.withTransaction {
                        favoritesRepository.applyReferenceBackfill(plan.favorites)
                        playlistsRepository.applyReferenceBackfill(plan.playlists)
                        listeningHistoryRepository.applyReferenceBackfill(plan.history)
                    }
                    val selectedPlaylistRows = selectedPlaylistId?.let {
                        playlistsRepository.getPlaylistSongs(it)
                    }
                    selectedPlaylistRows
                }
                val mappedResults = withContext(Dispatchers.Default) {
                    storedResults?.let { rows ->
                        resolvePlaylistRows(rows, index, visibleMembershipKeys)
                    }
                }
                ReferenceReconciliationData(
                    favoriteMembershipKeys = plan.favorites.result.resolvedMembershipKeys,
                    selectedPlaylistSongs = mappedResults,
                    unresolvedFavorites = plan.favorites.result.unresolvedCount +
                            plan.favorites.result.ambiguousCount,
                    unresolvedPlaylistRows = plan.playlists.result.unresolvedCount +
                            plan.playlists.result.ambiguousCount,
                    unresolvedHistoryRows = plan.history.result.unresolvedCount +
                            plan.history.result.ambiguousCount,
                    inspectedRows = plan.inspectedRowCount,
                    writes = plan.writeCount,
                    favoriteInspected = plan.favorites.result.inspectedCount,
                    favoriteWrites = plan.favorites.result.backfilledCount,
                    playlistInspected = plan.playlists.result.inspectedCount,
                    playlistWrites = plan.playlists.result.backfilledCount,
                    historyInspected = plan.history.result.inspectedCount,
                    historyWrites = plan.history.result.backfilledCount
                )
            }
            if (reconciled == null || !reconciliationCoordinator.isCurrent(generation)) return@launch
            _uiState.update { current ->
                current.copy(
                    favoriteMembershipKeys = reconciled.favoriteMembershipKeys.toSet(),
                    unresolvedFavoriteCount = reconciled.unresolvedFavorites,
                    unresolvedPlaylistRowCount = reconciled.unresolvedPlaylistRows,
                    unresolvedListeningHistoryCount = reconciled.unresolvedHistoryRows,
                    selectedPlaylistSongs = reconciled.selectedPlaylistSongs?.toList()
                        ?: current.selectedPlaylistSongs
                )
            }
            if (applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Log.d(
                    "SongReferenceReconciliation",
                    "generation=$generation publish=$libraryPublishCount indexBuilds=1 active=1 " +
                            "favorites=${reconciled.favoriteInspected}/${reconciled.favoriteWrites} " +
                            "playlists=${reconciled.playlistInspected}/${reconciled.playlistWrites} " +
                            "history=${reconciled.historyInspected}/${reconciled.historyWrites} " +
                            "total=${reconciled.inspectedRows}/${reconciled.writes} " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
                )
            }
        }
    }

    private fun loadPlaylists() {
        coroutineScope.launch {
            playlists = playlistsRepository.getPlaylists()
        }
    }

    private suspend fun getResolvedPlaylistSongs(playlistId: Long): List<PlaylistSong> {
        val rows = withContext(Dispatchers.IO) {
            playlistsRepository.getPlaylistSongs(playlistId)
        }
        return withContext(Dispatchers.Default) {
            resolvePlaylistRows(rows, songReferenceIndex, visibleSongMembershipKeys)
        }
    }

    private fun resolvePlaylistRows(
        rows: List<PlaylistSong>,
        index: SongReferenceIndex,
        visibleMembershipKeys: Set<String>
    ): List<PlaylistSong> = rows.map { row ->
        val resolved = (index.resolve(row.reference) as? SongReferenceResolution.Resolved)?.song
            ?.takeIf { it.membershipKey() in visibleMembershipKeys }
        row.copy(resolvedSong = resolved)
    }

    private fun resolveRestoredFolderSelections(restored: Set<String>): Set<String> {
        if (restored.isEmpty()) return emptySet()
        val available = libraryFolders.map { it.path }.toSet()
        return restored.mapNotNullTo(mutableSetOf()) { storedPath ->
            if (storedPath in available) return@mapNotNullTo storedPath
            val token = storedPath.replace('\\', '/').trim().trim('/')
            if (token.isBlank()) return@mapNotNullTo null
            val matches = available.filter { candidate ->
                val normalizedCandidate = candidate.replace('\\', '/').trimEnd('/')
                normalizedCandidate.equals(token, ignoreCase = true) ||
                        normalizedCandidate.endsWith("/$token", ignoreCase = true)
            }
            matches.singleOrNull()
        }
    }

    private fun debugLibraryTiming(message: String) {
        if (applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("LibraryTiming", message)
        }
    }
}

private data class BackupRestoredUserData(
    val folderSelection: FolderSelection,
    val favoriteMembershipKeys: Set<String>,
    val playlists: List<Playlist>
)

private data class ReferenceReconciliationData(
    val favoriteMembershipKeys: Set<String>,
    val selectedPlaylistSongs: List<PlaylistSong>?,
    val unresolvedFavorites: Int,
    val unresolvedPlaylistRows: Int,
    val unresolvedHistoryRows: Int,
    val inspectedRows: Int,
    val writes: Int,
    val favoriteInspected: Int,
    val favoriteWrites: Int,
    val playlistInspected: Int,
    val playlistWrites: Int,
    val historyInspected: Int,
    val historyWrites: Int
)
