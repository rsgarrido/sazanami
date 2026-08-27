package com.example.cdplaya.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.SystemClock
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import com.example.cdplaya.data.EditableSongTags
import com.example.cdplaya.data.DuplicateListeningHistoryResolution
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
import com.example.cdplaya.data.PlaylistFolder
import com.example.cdplaya.data.PlaylistArtworkStore
import com.example.cdplaya.data.PlaylistSong
import com.example.cdplaya.data.PlaylistsRepository
import com.example.cdplaya.data.GeneratedPlaylistState
import com.example.cdplaya.data.PlaylistMembershipBehavior
import com.example.cdplaya.data.PlaylistType
import com.example.cdplaya.data.SmartPlaylistDefinition
import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.SmartPlaylistRepository
import com.example.cdplaya.data.SmartPlaylistResolution
import com.example.cdplaya.data.SmartPlaylistTemplate
import com.example.cdplaya.data.PersistedSongReferenceRows
import com.example.cdplaya.data.ReconciliationGenerationCoordinator
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.SongReferenceIndex
import com.example.cdplaya.data.SongReferenceReconciliationPlanner
import com.example.cdplaya.data.SongReferenceResolution
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.data.stableUiKey
import com.example.cdplaya.data.toSongReference
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
    private val smartPlaylistRepository = SmartPlaylistRepository(
        database = appDatabase,
        eligibleFolderSelection = { folderSelection }
    )
    private val listeningHistoryRepository = ListeningHistoryRepository(
        appDatabase.songPlayStatsDao()
    )
    private val listeningStatsRepository = ListeningStatsRepository(appDatabase)
    private val libraryCacheRepository = LibraryCacheRepository(appDatabase.cachedSongDao())
    private val playlistFileRepository = PlaylistFileRepository(applicationContext)
    private val playlistArtworkStore = PlaylistArtworkStore(applicationContext)
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
    private var playlistFolders: List<PlaylistFolder>
        get() = _uiState.value.playlistFolders
        set(value) = updateState { copy(playlistFolders = value.toList()) }
    private val selectedPlaylistId: Long?
        get() = _uiState.value.selectedPlaylistId
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
                logDuplicateListeningHistoryResolutions(resolved.duplicateResolutions)
                _uiState.update { current ->
                    current.copy(
                        recentlyPlayedSongs = resolved.recentlyPlayed.toList(),
                        mostPlayedSongs = resolved.mostPlayed.toList()
                    )
                }
            }
        }
    }

    private fun logDuplicateListeningHistoryResolutions(
        duplicates: List<DuplicateListeningHistoryResolution>
    ) {
        if (applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return
        }
        duplicates.forEach { duplicate ->
            val retained = duplicate.retainedSong
            val dropped = duplicate.duplicateSong
            Log.w(
                "LibraryIdentity",
                "collection=${duplicate.collection} duplicateUiKey=${dropped.id} " +
                        "stableUiKey=${dropped.stableUiKey()} " +
                        "retainedTrackIdentityId=${duplicate.retainedTrackIdentityId} " +
                        "duplicateTrackIdentityId=${duplicate.duplicateTrackIdentityId}"
            )
            Log.w(
                "LibraryIdentity",
                "role=retained mediaId=${retained.id} uri=${retained.uri} " +
                        "title=${retained.title} artist=${retained.artist} album=${retained.album} " +
                        "folder=${retained.folderPath} membershipKey=${retained.membershipKey()}"
            )
            Log.w(
                "LibraryIdentity",
                "role=duplicate mediaId=${dropped.id} uri=${dropped.uri} " +
                        "title=${dropped.title} artist=${dropped.artist} album=${dropped.album} " +
                        "folder=${dropped.folderPath} membershipKey=${dropped.membershipKey()}"
            )
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
        val activePlaylistId = selectedPlaylistId
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
                        getPlaylistsWithSmartMembership()
            }
            val updatedFavoriteMembershipKeys = updatedUserData.first
            val updatedPlaylists = updatedUserData.second

            val updatedSelectedPlaylistSongs = activePlaylistId?.let { playlistId ->
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

            if (updatedSelectedPlaylistSongs != null && selectedPlaylistId == activePlaylistId) {
                selectedPlaylistSongs = updatedSelectedPlaylistSongs
            }
            publishLibraryData(libraryData, reconcilePlayback = true)
        }
    }

    fun refreshSongsAfterBatchEdit(
        editedSongs: List<Pair<Song, EditableSongTags>>
    ) {
        if (editedSongs.isEmpty()) return
        val activePlaylistId = selectedPlaylistId
        launchProtectedRefresh { scanToken ->
            val updatedUserData = withContext(Dispatchers.IO) {
                editedSongs.forEach { (originalSong, editedTags) ->
                    favoritesRepository.updateSongReferenceAfterTagEdit(originalSong, editedTags)
                    playlistsRepository.updateSongReferencesAfterTagEdit(originalSong, editedTags)
                    listeningHistoryRepository.updateSongReferenceAfterTagEdit(originalSong, editedTags)
                }
                favoritesRepository.getFavoriteMembershipKeys() to
                    getPlaylistsWithSmartMembership()
            }
            val updatedSelectedPlaylistSongs = if (activePlaylistId != null) {
                getResolvedPlaylistSongs(activePlaylistId)
            } else {
                null
            }
            val libraryData = withContext(Dispatchers.IO) {
                scanFreshLibraryAndUpdateCache(
                    folderSelection = folderSelection,
                    forceArtworkRefreshIds = editedSongs.mapTo(mutableSetOf()) { it.first.id },
                    scanToken = scanToken
                )
            }
            if (!permissionGate.isCurrent(scanToken)) return@launchProtectedRefresh
            favoriteMembershipKeys = updatedUserData.first
            playlists = updatedUserData.second
            if (updatedSelectedPlaylistSongs != null && selectedPlaylistId == activePlaylistId) {
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

    fun createPlaylist(playlistName: String, folderId: Long? = null) {
        coroutineScope.launch {
            val wasCreated = playlistsRepository.createPlaylist(playlistName, folderId)

            if (wasCreated) {
                loadPlaylists()
            }
        }
    }

    fun previewSmartPlaylist(
        draft: SmartPlaylistDraft,
        onComplete: (Result<SmartPlaylistResolution>) -> Unit
    ) {
        coroutineScope.launch {
            onComplete(runCatching { smartPlaylistRepository.previewMatchingSongs(draft) })
        }
    }

    fun createSmartPlaylist(
        name: String,
        draft: SmartPlaylistDraft,
        folderId: Long?,
        template: SmartPlaylistTemplate?,
        onComplete: (Result<SmartPlaylistDefinition>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                val created = if (template == null) {
                    smartPlaylistRepository.createSmartPlaylist(name, draft, folderId)
                } else {
                    smartPlaylistRepository.createGeneratedPlaylist(
                        name = name,
                        templateKey = template.key,
                        draft = template.draft,
                        membershipMode = template.membershipMode,
                        refreshPolicy = template.refreshPolicy,
                        refreshIntervalMillis = template.refreshIntervalMillis,
                        folderId = folderId
                    )
                }
                checkNotNull(created) { "A playlist with that name already exists." }
            }
            if (result.isSuccess) loadPlaylists()
            onComplete(result)
        }
    }

    fun updateSmartPlaylist(
        playlistId: Long,
        draft: SmartPlaylistDraft,
        onComplete: (Result<SmartPlaylistDefinition>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                checkNotNull(
                    smartPlaylistRepository.updateSmartPlaylistDefinition(playlistId, draft)
                ) { "This Smart Playlist is no longer available." }
            }
            if (result.isSuccess) {
                loadPlaylists()
                reloadSelectedPlaylistIfCurrent(playlistId)
            }
            onComplete(result)
        }
    }

    fun loadSmartPlaylistData(
        playlistId: Long,
        onComplete: (Result<SmartPlaylistUiData>) -> Unit
    ) {
        coroutineScope.launch {
            onComplete(runCatching {
                SmartPlaylistUiData(
                    definition = checkNotNull(
                        smartPlaylistRepository.loadSmartPlaylistDefinition(playlistId)
                    ) { "This Smart Playlist definition is unavailable." },
                    behavior = smartPlaylistRepository.getMembershipBehavior(playlistId),
                    generatedState = smartPlaylistRepository.loadGeneratedPlaylistState(playlistId)
                )
            })
        }
    }

    fun refreshGeneratedPlaylist(
        playlistId: Long,
        onComplete: (Result<SmartPlaylistResolution>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                smartPlaylistRepository.refreshGeneratedSnapshot(playlistId)
            }
            if (result.isSuccess) {
                loadPlaylists()
                reloadSelectedPlaylistIfCurrent(playlistId)
            }
            onComplete(result)
        }
    }

    fun resolveSmartPlaylist(
        playlistId: Long,
        onComplete: (Result<SmartPlaylistResolution>) -> Unit
    ) {
        coroutineScope.launch {
            onComplete(runCatching { smartPlaylistRepository.resolveFinalMembership(playlistId) })
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

                if (selectedPlaylistId == playlist.playlistId) {
                    selectedPlaylistName = trimmedName
                }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        coroutineScope.launch {
            playlistsRepository.deletePlaylist(playlist.playlistId)
            appPreferencesRepository.removeHomePinsForPlaylist(playlist.playlistId)
            withContext(Dispatchers.IO) {
                playlistArtworkStore.delete(playlist.artworkReference)
            }
            loadPlaylists()

            if (selectedPlaylistId == playlist.playlistId) {
                clearSelectedPlaylist()
            }
        }
    }

    fun changePlaylistArtwork(
        playlist: Playlist,
        source: Uri,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        coroutineScope.launch {
            val result = runCatching {
                val newReference = withContext(Dispatchers.IO) {
                    playlistArtworkStore.importArtwork(playlist.playlistId, source)
                }
                try {
                    playlistsRepository.setCustomArtwork(
                        playlistId = playlist.playlistId,
                        artworkReference = newReference
                    )
                } catch (failure: Throwable) {
                    withContext(Dispatchers.IO) {
                        playlistArtworkStore.delete(newReference)
                    }
                    throw failure
                }
                withContext(Dispatchers.IO) {
                    playlistArtworkStore.delete(playlist.artworkReference)
                }
                loadPlaylists()
            }.onFailure { failure ->
                Log.e("PlaylistArtwork", "Unable to change playlist artwork", failure)
            }
            onComplete(result)
        }
    }

    fun createPlaylistWithSongs(
        playlistName: String,
        initialSongs: List<Song>,
        onComplete: (Result<Playlist>) -> Unit = {}
    ) {
        coroutineScope.launch {
            val result = runCatching {
                checkNotNull(
                    playlistsRepository.createPlaylist(
                        name = playlistName,
                        initialSongs = initialSongs
                    )
                ) { "Unable to create playlist." }
            }
            if (result.isSuccess) loadPlaylists()
            onComplete(result)
        }
    }

    fun createPlaylistFolder(name: String) {
        coroutineScope.launch {
            if (playlistsRepository.createPlaylistFolder(name)) loadPlaylists()
        }
    }

    fun renamePlaylistFolder(folder: PlaylistFolder, newName: String) {
        coroutineScope.launch {
            if (playlistsRepository.renamePlaylistFolder(folder.folderId, newName)) {
                loadPlaylists()
            }
        }
    }

    fun deletePlaylistFolder(folder: PlaylistFolder) {
        coroutineScope.launch {
            playlistsRepository.deletePlaylistFolder(folder.folderId)
            loadPlaylists()
        }
    }

    fun movePlaylistToFolder(playlist: Playlist, folderId: Long?) {
        coroutineScope.launch {
            if (playlistsRepository.movePlaylistToFolder(playlist.playlistId, folderId)) {
                loadPlaylists()
            }
        }
    }

    fun resetPlaylistArtwork(
        playlist: Playlist,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        coroutineScope.launch {
            val result = runCatching {
                playlistsRepository.resetArtwork(playlist.playlistId)
                withContext(Dispatchers.IO) {
                    playlistArtworkStore.delete(playlist.artworkReference)
                }
                loadPlaylists()
            }.onFailure { failure ->
                Log.e("PlaylistArtwork", "Unable to reset playlist artwork", failure)
            }
            onComplete(result)
        }
    }

    fun loadSelectedPlaylist(playlist: Playlist) {
        updateState {
            copy(
                selectedPlaylistId = playlist.playlistId,
                selectedPlaylistName = playlist.name,
                selectedPlaylistSongs = emptyList(),
                isSelectedPlaylistLoading = true
            )
        }
        coroutineScope.launch {
            val result = runCatching {
                getResolvedPlaylistSongs(playlist.playlistId)
            }
            _uiState.update { current ->
                if (current.selectedPlaylistId != playlist.playlistId) {
                    current
                } else {
                    val rows = result.getOrDefault(emptyList()).toList()
                    current.copy(
                        playlists = if (result.isSuccess) {
                            current.playlists.map { existing ->
                                if (existing.playlistId == playlist.playlistId) {
                                    existing.withResolvedRows(rows)
                                } else existing
                            }
                        } else current.playlists,
                        selectedPlaylistSongs = rows,
                        isSelectedPlaylistLoading = false
                    )
                }
            }
        }
    }

    fun clearSelectedPlaylist() {
        updateState {
            copy(
                selectedPlaylistId = null,
                selectedPlaylistName = LibraryUiState.DEFAULT_PLAYLIST_NAME,
                selectedPlaylistSongs = emptyList(),
                isSelectedPlaylistLoading = false
            )
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

    fun preparePlaylistQueueSongs(
        playlist: Playlist,
        onPrepared: (Result<List<Song>>) -> Unit
    ) {
        coroutineScope.launch {
            val result = runCatching {
                getResolvedPlaylistSongs(playlist.playlistId)
                    .mapNotNull(PlaylistSong::resolvedSong)
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

                    playlists = getPlaylistsWithSmartMembership()

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

            if (selectedPlaylistId == playlist.playlistId) {
                val updatedRows = getResolvedPlaylistSongs(playlist.playlistId)
                if (selectedPlaylistId == playlist.playlistId) {
                    selectedPlaylistSongs = updatedRows
                }
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
            val updatedRows = getResolvedPlaylistSongs(playlistSong.playlistId)
            if (selectedPlaylistId == playlistSong.playlistId) {
                selectedPlaylistSongs = updatedRows
            }
        }
    }

    fun reorderPlaylistSongs(
        playlistId: Long,
        orderedPlaylistSongIds: List<Long>
    ) {
        val currentRows = selectedPlaylistSongs
        if (
            selectedPlaylistId != playlistId ||
            orderedPlaylistSongIds.size != currentRows.size ||
            orderedPlaylistSongIds.toSet() != currentRows
                .mapTo(mutableSetOf(), PlaylistSong::playlistSongId)
        ) {
            return
        }

        val rowsById = currentRows.associateBy(PlaylistSong::playlistSongId)
        selectedPlaylistSongs = orderedPlaylistSongIds.mapIndexed { position, playlistSongId ->
            checkNotNull(rowsById[playlistSongId]).copy(position = position)
        }

        coroutineScope.launch {
            val didReorder = playlistsRepository.reorderPlaylistSongs(
                playlistId = playlistId,
                orderedPlaylistSongIds = orderedPlaylistSongIds
            )
            if (!didReorder) {
                val authoritativeRows = getResolvedPlaylistSongs(playlistId)
                if (selectedPlaylistId == playlistId) {
                    selectedPlaylistSongs = authoritativeRows
                }
                return@launch
            }
            loadPlaylists()
            val updatedRows = getResolvedPlaylistSongs(playlistId)
            if (selectedPlaylistId == playlistId) {
                selectedPlaylistSongs = updatedRows
            }
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
                playlists = getPlaylistsWithSmartMembership(),
                playlistFolders = playlistsRepository.getPlaylistFolders()
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
        playlistFolders = restoredData.playlistFolders
        clearSelectedPlaylist()
        if (folderSelectionChanged) {
            reloadSongsAfterFolderChange()
        } else {
            reconcileUserSongReferences(songs, songReferenceIndex)
        }
    }

    private fun reloadSongsAfterFolderChange() {
        launchProtectedRefresh { scanToken ->
            withContext(Dispatchers.IO) {
                smartPlaylistRepository.invalidateLibraryEligibility()
            }
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
                playlistFolders = current.playlistFolders,
                selectedPlaylistId = current.selectedPlaylistId,
                selectedPlaylistName = current.selectedPlaylistName,
                selectedPlaylistSongs = current.selectedPlaylistSongs,
                isSelectedPlaylistLoading = current.isSelectedPlaylistLoading,
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
        loadPlaylists()
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
        val activePlaylistId = selectedPlaylistId
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
                    val selectedPlaylistRows = activePlaylistId?.let { playlistId ->
                        if (smartPlaylistRepository.getMembershipBehavior(playlistId) ==
                            PlaylistMembershipBehavior.MANUAL
                        ) {
                            playlistsRepository.getPlaylistSongs(playlistId)
                        } else {
                            getResolvedPlaylistSongs(playlistId)
                        }
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
                    selectedPlaylistSongs = if (current.selectedPlaylistId == activePlaylistId) {
                        reconciled.selectedPlaylistSongs?.toList()
                            ?: current.selectedPlaylistSongs
                    } else {
                        current.selectedPlaylistSongs
                    }
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
            val refreshedPlaylists = getPlaylistsWithSmartMembership()
            val refreshedFolders = playlistsRepository.getPlaylistFolders()
            updateState {
                copy(
                    playlists = refreshedPlaylists,
                    playlistFolders = refreshedFolders
                )
            }
        }
    }

    private suspend fun getResolvedPlaylistSongs(playlistId: Long): List<PlaylistSong> {
        val behavior = smartPlaylistRepository.getMembershipBehavior(playlistId)
        if (behavior != PlaylistMembershipBehavior.MANUAL) {
            return smartPlaylistRepository.resolveFinalMembership(playlistId).songs
                .mapIndexed { position, song -> song.toDerivedPlaylistSong(playlistId, position) }
        }
        val rows = withContext(Dispatchers.IO) {
            playlistsRepository.getPlaylistSongs(playlistId)
        }
        return withContext(Dispatchers.Default) {
            resolvePlaylistRows(rows, songReferenceIndex, visibleSongMembershipKeys)
        }
    }

    private suspend fun getPlaylistsWithSmartMembership(): List<Playlist> {
        return playlistsRepository.getPlaylists(songs).map { playlist ->
            if (playlist.type == PlaylistType.MANUAL) return@map playlist
            runCatching {
                val behavior = smartPlaylistRepository.getMembershipBehavior(playlist.playlistId)
                val generated = smartPlaylistRepository.loadGeneratedPlaylistState(playlist.playlistId)
                val resolution = smartPlaylistRepository.resolveFinalMembership(playlist.playlistId)
                playlist.copy(
                    songCount = resolution.count,
                    totalDuration = resolution.songs.sumOf { it.duration.coerceAtLeast(0L) },
                    membershipBehavior = behavior,
                    generatedTemplateKey = generated?.templateKey,
                    generatedLastRefreshedAt = generated?.lastRefreshedAt
                        ?: resolution.resolvedAt.takeIf { resolution.generatedSnapshot },
                    songMembershipKeys = resolution.songs.mapTo(linkedSetOf(), Song::membershipKey),
                    automaticArtworkSongs = resolution.songs.distinctBy { song ->
                        Triple(
                            song.albumArtist.ifBlank { song.artist }.lowercase(),
                            song.album.lowercase(),
                            song.folderPath.lowercase()
                        )
                    }.take(4)
                )
            }.getOrElse {
                playlist.copy(
                    membershipBehavior = PlaylistMembershipBehavior.USER_SMART_LIVE,
                    smartResolutionError = "Some rules are not supported by this app version."
                )
            }
        }
    }

    private fun reloadSelectedPlaylistIfCurrent(playlistId: Long) {
        if (selectedPlaylistId != playlistId) return
        coroutineScope.launch {
            val rows = runCatching { getResolvedPlaylistSongs(playlistId) }.getOrDefault(emptyList())
            if (selectedPlaylistId == playlistId) selectedPlaylistSongs = rows
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
    val playlists: List<Playlist>,
    val playlistFolders: List<PlaylistFolder>
)

data class SmartPlaylistUiData(
    val definition: SmartPlaylistDefinition,
    val behavior: PlaylistMembershipBehavior,
    val generatedState: GeneratedPlaylistState?
)

private fun Song.toDerivedPlaylistSong(playlistId: Long, position: Int): PlaylistSong =
    PlaylistSong(
        playlistSongId = -(position.toLong() + 1L),
        playlistId = playlistId,
        songKey = membershipKey(),
        position = position,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        reference = toSongReference(),
        resolvedSong = this
    )

private fun Playlist.withResolvedRows(rows: List<PlaylistSong>): Playlist {
    val resolvedSongs = rows.mapNotNull(PlaylistSong::resolvedSong)
    return copy(
        songCount = rows.size,
        totalDuration = rows.sumOf { it.duration.coerceAtLeast(0L) },
        songMembershipKeys = resolvedSongs.mapTo(linkedSetOf(), Song::membershipKey),
        automaticArtworkSongs = resolvedSongs.distinctBy { song ->
            Triple(
                song.albumArtist.ifBlank { song.artist }.lowercase(),
                song.album.lowercase(),
                song.folderPath.lowercase()
            )
        }.take(4)
    )
}

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
