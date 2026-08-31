package io.github.rsgarrido.sazanami.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.knownDiscNumber
import io.github.rsgarrido.sazanami.data.SongReferenceResolution
import io.github.rsgarrido.sazanami.data.SongReferenceResolver
import io.github.rsgarrido.sazanami.data.toSongReference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainRepository
import io.github.rsgarrido.sazanami.player.replaygain.replayGainVolumeMultiplier
import io.github.rsgarrido.sazanami.player.replaygain.selectReplayGainDb
import io.github.rsgarrido.sazanami.player.audio.AdvancedAudioRuntimeBridge
import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.tracePerformance
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.random.Random

private data class PendingExternalPlaybackSelection(
    val selectedSongId: Long,
    val playbackContext: List<Song>
)

class PlaybackController(
    context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val musicPlayer = MusicPlayer(context)
    private val playerStateStorage = PlayerStateStorage(context)
    private val playbackQueueManager = PlaybackQueueManager()
    private val playbackNavigationHistory = PlaybackNavigationHistory()
    private val upcomingPlaylistBuilder = UpcomingPlaylistBuilder()
    private val checkpointPolicy = PlaybackStateCheckpointPolicy()
    private val replayGainRepository = ReplayGainRepository()
    private val restoredShuffleMode = playerStateStorage.getShuffleMode()
    private var librarySongs: List<Song> = emptyList()
    private var playbackContextSongs: List<Song> = emptyList()
    private var pendingExternalPlaybackSelection: PendingExternalPlaybackSelection? = null
    private var replayGainMode: ReplayGainMode = ReplayGainMode.OFF
    private var replayGainRequestId = 0
    private val _uiState = MutableStateFlow(
        PlaybackUiState.Disconnected.copy(
            isShuffleEnabled = restoredShuffleMode.isEnabled,
            repeatMode = playerStateStorage.getRepeatMode()
        )
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressUiState.Empty)
    val progressState: StateFlow<PlaybackProgressUiState> = _progressState.asStateFlow()

    private val _audioOutputState = MutableStateFlow(AudioOutputUiState())
    val audioOutputState: StateFlow<AudioOutputUiState> = _audioOutputState.asStateFlow()
    private var advancedAudioRuntimeCollectionJob: Job? = null
    private var advancedAudioRuntimeCollectionStartCount = 0

    private val playbackQueue: MutableList<Song>
        get() = playbackQueueManager.playbackQueue
    private var currentSong: Song?
        get() = _uiState.value.currentSong
        set(value) {
            _uiState.update { state -> state.copy(currentSong = value) }
            publishDerivedPlaybackState()
        }
    private var isPlaying: Boolean
        get() = _uiState.value.isPlaying
        set(value) = _uiState.update { state -> state.copy(isPlaying = value) }
    private var shuffleMode: PlaybackShuffleMode = restoredShuffleMode
        set(value) {
            field = value
            _uiState.update { state -> state.copy(isShuffleEnabled = value.isEnabled) }
            PlaybackLibraryBridge.notifyPlaybackPolicyChanged(value.isEnabled, repeatMode)
        }
    private val isShuffleEnabled: Boolean
        get() = shuffleMode.isEnabled
    private var repeatMode: RepeatMode
        get() = _uiState.value.repeatMode
        set(value) {
            _uiState.update { state -> state.copy(repeatMode = value) }
            PlaybackLibraryBridge.notifyPlaybackPolicyChanged(shuffleMode.isEnabled, value)
        }
    private var upcomingSongsValue: List<Song> = emptyList()
    private var upcomingSongs: List<Song>
        get() = upcomingSongsValue
        set(value) {
            upcomingSongsValue = value.toList()
            publishDerivedPlaybackState()
        }
    private var currentPosition: Int
        get() = _progressState.value.currentPosition
        set(value) = _progressState.update { state -> state.copy(currentPosition = value) }
    private var duration: Int
        get() = _progressState.value.duration
        set(value) = _progressState.update { state -> state.copy(duration = value) }
    private var isPlayerConnected: Boolean
        get() = _uiState.value.isConnected
        set(value) = _uiState.update { state -> state.copy(isConnected = value) }

    private fun publishDerivedPlaybackState() {
        _uiState.update { state ->
            val queuedSongs = playbackQueue.toList()
            val queuedSongCount = playbackQueueManager.getQueuedSongCountExcludingCurrent(
                currentSongId = state.currentSong?.id
            )
            state.copy(
                queuedSongs = queuedSongs,
                previousHistoryCount = playbackNavigationHistory.getPreviousSongIds().size,
                forwardHistoryCount = playbackNavigationHistory.getNextSongIds().size,
                previousPreviewSong = playbackNavigationHistory.peekPreviousSong(),
                nextPreviewSong = playbackNavigationHistory.peekNextSong()
                    ?: queuedSongs.firstOrNull()
                    ?: upcomingSongsValue.firstOrNull(),
                // The UI's "coming up" list excludes items already represented by the queue.
                // Keep that computation out of progress updates.
                upcomingSongs = upcomingSongsValue.drop(queuedSongCount)
            )
        }
    }

    private val progressHandler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (currentSong != null) {
                val updatedPosition = musicPlayer.getCurrentPosition()

                currentPosition = updatedPosition
                duration = musicPlayer.getDuration()

                val nowMillis = SystemClock.elapsedRealtime()
                if (checkpointPolicy.shouldCheckpoint(isPlaying, nowMillis)) {
                    savePlayerState()
                }

                progressHandler.postDelayed(this, 500)
            }
        }
    }

    init {
        PlaybackLibraryBridge.register(this)
        startAdvancedAudioRuntimeCollection()
    }

    private fun startAdvancedAudioRuntimeCollection() {
        if (advancedAudioRuntimeCollectionJob != null) return
        advancedAudioRuntimeCollectionStartCount += 1
        advancedAudioRuntimeCollectionJob = coroutineScope.launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            AdvancedAudioRuntimeBridge.state.collect { runtime ->
                _audioOutputState.update { current ->
                    current.copy(
                        sourceFormat = runtime.sourceFormat,
                        routeInfo = runtime.routeInfo,
                        offloadState = runtime.offloadState,
                        equalizerRuntimeState =
                            runtime.equalizerRuntimeState,
                        audioSessionId = runtime.audioSessionId,
                        isPlayerConnected = runtime.isPlayerConnected
                    )
                }
            }
        }
    }

    fun connect() {
        tracePerformance(PerformanceTraceNames.PLAYBACK_CONNECT) {
            musicPlayer.connect {
                isPlayerConnected = true

                if (librarySongs.isNotEmpty()) {
                    restoreOrAdoptPlayerState()
                }
            }
        }

        musicPlayer.onSongCompleted = {
            handleSongCompleted()
        }

        musicPlayer.onPlaybackStateChanged = { playerIsPlaying ->
            val wasPlaying = isPlaying
            isPlaying = playerIsPlaying
            if (wasPlaying && !playerIsPlaying) {
                savePlayerState()
            }
        }

        musicPlayer.onCurrentSongChanged = { songId ->
            handleServiceSongChanged(songId)
        }
    }

    fun setReplayGainMode(mode: ReplayGainMode) {
        replayGainMode = mode
        _audioOutputState.update { state -> state.copy(replayGainMode = mode) }
        applyReplayGainForCurrentSong()
    }

    fun setLibrarySongs(songs: List<Song>) {
        librarySongs = songs

        if (isPlayerConnected) {
            restoreOrAdoptPlayerState()
        }
    }

    fun handleLibrarySongsChanged(updatedSongs: List<Song>) {
        librarySongs = updatedSongs
        val previousCurrentSong = currentSong
        val refreshedCurrentSong = previousCurrentSong?.let { song ->
            replacementSong(song, updatedSongs)
        }

        if (previousCurrentSong != null && refreshedCurrentSong == null) {
            musicPlayer.stop()
            musicPlayer.setVolume(1f)
            currentSong = null
            isPlaying = false
            currentPosition = 0
            duration = 0
            upcomingSongs = emptyList()
        } else if (refreshedCurrentSong != null) {
            currentSong = refreshedCurrentSong
        }

        tracePerformance(PerformanceTraceNames.PLAYBACK_QUEUE_REPLACEMENT) {
            playbackQueueManager.replaceQueue(
                replaceSongReferences(playbackQueue.toList(), updatedSongs)
            )
        }
        publishDerivedPlaybackState()
        playbackNavigationHistory.replacePreviousSongs(
            replaceSongReferences(playbackNavigationHistory.getPreviousSongs(), updatedSongs)
        )
        playbackNavigationHistory.replaceNextSongs(
            replaceSongReferences(playbackNavigationHistory.getNextSongs(), updatedSongs)
        )
        playbackContextSongs = replaceSongReferences(playbackContextSongs, updatedSongs)
        upcomingSongs = replaceSongReferences(upcomingSongs, updatedSongs)

        if (playbackContextSongs.isEmpty()) {
            playbackContextSongs = librarySongs
        }

        if (currentSong != null) {
            if (currentSong != previousCurrentSong) {
                tracePerformance(PerformanceTraceNames.PLAYBACK_METADATA_REPLACEMENT) {
                    musicPlayer.updateCurrentSongMetadata(requireNotNull(currentSong))
                }
            }
            syncServicePlaylistKeepingCurrent()
        }

        savePlayerState()
    }

    fun playSongsFromContext(
        playbackContext: List<Song>,
        shuffleMode: PlaybackShuffleMode
    ) {
        if (playbackContext.isEmpty()) {
            return
        }

        this.shuffleMode = shuffleMode
        playbackNavigationHistory.clearAll()

        val songToPlay = if (shuffleMode.usesDynamicSongShuffle && playbackContext.size > 1) {
            playbackContext[Random.nextInt(playbackContext.size)]
        } else {
            playbackContext.first()
        }

        playSelectedSong(
            song = songToPlay,
            playbackContext = playbackContext,
            addCurrentToHistory = false,
            clearForwardHistory = false,
            preserveSpecializedShuffleMode = true
        )
    }

    internal fun prepareExternalPlaybackSelection(
        song: Song,
        playbackContext: List<Song>
    ) {
        // MediaSession.Callback.onSetMediaItems is a resolver callback. The session applies the
        // returned playlist after the callback completes, so only stage logical controller state
        // here and never call MusicPlayer.setMediaItems/prepare/play from this path.
        if (
            shuffleMode == PlaybackShuffleMode.ALBUMS ||
            shuffleMode == PlaybackShuffleMode.ALBUMS_AND_SONGS
        ) {
            shuffleMode = PlaybackShuffleMode.OFF
        }
        pendingExternalPlaybackSelection = PendingExternalPlaybackSelection(
            selectedSongId = song.id,
            playbackContext = playbackContext.ifEmpty { listOf(song) }
        )
    }

    fun playSelectedSong(
        song: Song,
        playbackContext: List<Song>? = null,
        addCurrentToHistory: Boolean = true,
        clearForwardHistory: Boolean = true,
        preserveSpecializedShuffleMode: Boolean = false
    ) {
        val previousSong = currentSong

        if (playbackContext != null) {
            if (
                !preserveSpecializedShuffleMode &&
                (
                        shuffleMode == PlaybackShuffleMode.ALBUMS ||
                                shuffleMode == PlaybackShuffleMode.ALBUMS_AND_SONGS
                        )
            ) {
                shuffleMode = PlaybackShuffleMode.OFF
            }
            playbackNavigationHistory.clearAll()
        } else {
            if (addCurrentToHistory && previousSong != null && previousSong.id != song.id) {
                playbackNavigationHistory.addPreviousSong(previousSong)
            }

            if (clearForwardHistory) {
                playbackNavigationHistory.clearForwardHistory()
            }
        }

        playbackContextSongs = playbackContext ?: getPlaybackSourceSongs()

        startSongPlayback(
            song = song,
            playlist = buildPlaybackPlaylist(song)
        )
    }

    fun togglePlayPause() {
        if (musicPlayer.isPlayWhenReady()) {
            musicPlayer.pause()
            isPlaying = false
            savePlayerState()
        } else {
            musicPlayer.resume()
            isPlaying = true
        }
    }

    fun pausePlayback() {
        musicPlayer.pause()
        isPlaying = false
        savePlayerState()
    }

    fun skipToPrevious() {
        if (musicPlayer.getCurrentPosition() > PREVIOUS_RESTART_THRESHOLD_MS) {
            seekTo(0)
            return
        }

        playPreviousSong()
    }

    fun skipToNext() {
        playNextSong()
    }

    fun seekTo(position: Int) {
        musicPlayer.seekTo(position)
        currentPosition = position
        savePlayerState()
    }

    fun getCurrentPositionForLyrics(): Long =
        musicPlayer.getCurrentPosition().coerceAtLeast(0).toLong()

    fun toggleShuffle() {
        setSongShuffleEnabled(shuffleMode == PlaybackShuffleMode.OFF)
    }

    fun setSongShuffleEnabled(enabled: Boolean) {
        val target = if (enabled) PlaybackShuffleMode.SONGS else PlaybackShuffleMode.OFF
        if (shuffleMode == target) return
        shuffleMode = target
        playbackNavigationHistory.clearAll()
        syncServicePlaylistKeepingCurrent(preserveExistingShuffleOrder = false)
        savePlayerState()
    }

    fun cycleRepeatMode() {
        setRepeatModeFromExternalController(
            when (repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        )
    }

    fun setRepeatModeFromExternalController(mode: RepeatMode) {
        if (repeatMode == mode) return
        repeatMode = mode
        syncServicePlaylistKeepingCurrent()
        savePlayerState()
    }

    fun addSongToQueue(song: Song) {
        playbackQueueManager.addSongToQueue(song)
        syncServicePlaylistKeepingCurrent()
        savePlayerState()
    }

    fun addSongToPlayNext(song: Song) {
        playbackQueueManager.addSongToPlayNext(song)
        syncServicePlaylistKeepingCurrent()
        savePlayerState()
    }

    fun removeSongFromQueue(index: Int) {
        if (playbackQueueManager.removeSongFromQueue(index)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun moveQueuedSongUp(index: Int) {
        if (playbackQueueManager.moveQueuedSongUp(index)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun moveQueuedSongDown(index: Int) {
        if (playbackQueueManager.moveQueuedSongDown(index)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun clearQueue() {
        if (playbackQueueManager.clearQueue()) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun addSongsToPlayNext(songs: List<Song>) {
        if (playbackQueueManager.addSongsToPlayNext(songs)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun addSongsToQueue(songs: List<Song>) {
        if (playbackQueueManager.addSongsToQueue(songs)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun removeFirstMatchingSongsFromQueue(songs: List<Song>) {
        if (playbackQueueManager.removeFirstMatchingSongsFromQueue(songs)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun removeLastMatchingSongsFromQueue(songs: List<Song>) {
        if (playbackQueueManager.removeLastMatchingSongsFromQueue(songs)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun removeLastMatchingSongFromQueue(song: Song) {
        if (playbackQueueManager.removeLastMatchingSongFromQueue(song)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun removeFirstMatchingSongFromQueue(song: Song) {
        if (playbackQueueManager.removeFirstMatchingSongFromQueue(song)) {
            syncServicePlaylistKeepingCurrent()
            savePlayerState()
        }
    }

    fun savePlayerState() {
        playerStateStorage.saveState(
            currentSongId = currentSong?.id,
            currentPosition = musicPlayer.getCurrentPosition(),
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            previousSongIds = playbackNavigationHistory.getPreviousSongIds(),
            nextSongIds = playbackNavigationHistory.getNextSongIds(),
            queueSongIds = playbackQueueManager.getQueuedSongIds(),
            playbackContextSongIds = playbackContextSongs.map { song -> song.id }
        )
        checkpointPolicy.recordCheckpoint(SystemClock.elapsedRealtime())
    }

    fun release() {
        advancedAudioRuntimeCollectionJob?.cancel()
        advancedAudioRuntimeCollectionJob = null
        savePlayerState()
        progressHandler.removeCallbacks(progressRunnable)
        musicPlayer.release()
        upcomingSongsValue = emptyList()
        _progressState.value = PlaybackProgressUiState.Empty
        _audioOutputState.value = AudioOutputUiState()
        _uiState.value = PlaybackUiState.Disconnected.copy(
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode
        )
        PlaybackLibraryBridge.unregister(this)
    }

    private fun restoreOrAdoptPlayerState() {
        // Android Auto, Assistant, notifications, or Bluetooth may have started the service before
        // the phone UI exists. In that case the live Media3 session is authoritative; restoring the
        // persisted snapshot over it would rebuild the active decoder and briefly interrupt audio.
        if (adoptLivePlayerState()) return

        musicPlayer.setShuffleEnabled(shuffleMode.usesDynamicSongShuffle)
        musicPlayer.setRepeatMode(repeatMode)
        restorePlayerState()
    }

    private fun adoptLivePlayerState(): Boolean {
        val live = musicPlayer.adoptLiveSession(librarySongs) ?: return false
        val songsById = librarySongs.associateBy(Song::id)

        currentSong = live.currentSong
        currentPosition = live.currentPosition
        duration = live.duration
        isPlaying = live.isPlaying

        shuffleMode = playerStateStorage.getShuffleMode()
        repeatMode = live.repeatMode

        val persistedContext = playerStateStorage.getPlaybackContextSongIds()
            .mapNotNull(songsById::get)
        playbackContextSongs = persistedContext
            .takeIf { context ->
                context.any { song -> song.id == live.currentSong.id }
            }
            ?: live.playlist

        playbackQueueManager.replaceQueue(
            playerStateStorage.getQueueSongIds().mapNotNull(songsById::get)
        )
        playbackNavigationHistory.replacePreviousSongs(
            playerStateStorage.getPreviousSongIds().mapNotNull(songsById::get)
        )
        playbackNavigationHistory.replaceNextSongs(
            playerStateStorage.getNextSongIds().mapNotNull(songsById::get)
        )
        upcomingSongs = live.upcomingSongs

        applyReplayGainForCurrentSong()
        startProgressUpdates()
        return true
    }

    private fun restorePlayerState() {
        val savedSongId = playerStateStorage.getCurrentSongId() ?: return

        val restoredSong = librarySongs.firstOrNull { song ->
            song.id == savedSongId
        }
        if (restoredSong == null) {
            val songsById = librarySongs.associateBy { song -> song.id }
            playbackQueueManager.replaceQueue(
                playerStateStorage.getQueueSongIds().mapNotNull(songsById::get)
            )
            playbackNavigationHistory.replacePreviousSongs(
                playerStateStorage.getPreviousSongIds().mapNotNull(songsById::get)
            )
            playbackNavigationHistory.replaceNextSongs(
                playerStateStorage.getNextSongIds().mapNotNull(songsById::get)
            )
            playbackContextSongs = playerStateStorage.getPlaybackContextSongIds()
                .mapNotNull(songsById::get)
                .ifEmpty { librarySongs }
            savePlayerState()
            return
        }

        currentSong = restoredSong
        duration = restoredSong.duration.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        currentPosition = playerStateStorage.getCurrentPosition().coerceIn(0, duration)
        isPlaying = false

        shuffleMode = playerStateStorage.getShuffleMode()
        repeatMode = playerStateStorage.getRepeatMode()

        val restoredPlaybackContextSongs = playerStateStorage
            .getPlaybackContextSongIds()
            .mapNotNull { savedId ->
                librarySongs.firstOrNull { song -> song.id == savedId }
            }

        playbackContextSongs = if (restoredPlaybackContextSongs.isNotEmpty()) {
            restoredPlaybackContextSongs
        } else {
            librarySongs
        }

        playbackQueueManager.replaceQueue(
            playerStateStorage.getQueueSongIds().mapNotNull { savedId ->
                librarySongs.firstOrNull { song -> song.id == savedId }
            }
        )

        playbackNavigationHistory.replacePreviousSongs(
            playerStateStorage.getPreviousSongIds().mapNotNull { savedId ->
                librarySongs.firstOrNull { song -> song.id == savedId }
            }
        )

        playbackNavigationHistory.replaceNextSongs(
            playerStateStorage.getNextSongIds().mapNotNull { savedId ->
                librarySongs.firstOrNull { song -> song.id == savedId }
            }
        )

        musicPlayer.playSong(
            song = restoredSong,
            shouldStart = false,
            startPosition = currentPosition,
            playlist = buildPlaybackPlaylist(restoredSong)
        )

        musicPlayer.setShuffleEnabled(shuffleMode.usesDynamicSongShuffle)
        musicPlayer.setRepeatMode(repeatMode)
        applyReplayGainForCurrentSong()

        startProgressUpdates()
    }

    private fun playNextSong() {
        val playbackSourceSongs = getPlaybackSourceSongs()

        if (playbackSourceSongs.isEmpty()) {
            return
        }

        val nextHistorySong = playbackNavigationHistory.popNextSong()

        if (nextHistorySong != null) {
            playNavigationSong(
                song = nextHistorySong,
                orderedUpcomingSongs = upcomingSongs.removeFirstMatching(nextHistorySong),
                addCurrentToHistory = true,
                clearForwardHistory = false
            )
            return
        }

        if (playNextQueuedSong()) {
            return
        }

        val nextUpcomingSong = upcomingSongs.firstOrNull()

        if (nextUpcomingSong != null) {
            playNavigationSong(
                song = nextUpcomingSong,
                orderedUpcomingSongs = upcomingSongs.drop(1),
                addCurrentToHistory = true,
                clearForwardHistory = true
            )
            return
        }

        if (repeatMode != RepeatMode.ALL) {
            return
        }

        val repeatedUpcomingSongs = refreshUpcomingSongs(
            startSong = currentSong ?: return,
            preserveExistingShuffleOrder = false
        )

        val repeatedNextSong = repeatedUpcomingSongs.firstOrNull()

        if (repeatedNextSong == null) {
            currentSong?.let { song ->
                playNavigationSong(
                    song = song,
                    orderedUpcomingSongs = emptyList(),
                    addCurrentToHistory = false,
                    clearForwardHistory = false
                )
            }
            return
        }

        playNavigationSong(
            song = repeatedNextSong,
            orderedUpcomingSongs = repeatedUpcomingSongs.drop(1),
            addCurrentToHistory = true,
            clearForwardHistory = true
        )
    }

    private fun playPreviousSong() {
        val departedSong = currentSong ?: return
        val previousSong = playbackNavigationHistory
            .popPreviousSongAndPushCurrent(departedSong)
            ?: return

        playNavigationSong(
            song = previousSong,
            orderedUpcomingSongs = listOf(departedSong) + upcomingSongs,
            addCurrentToHistory = false,
            clearForwardHistory = false
        )
    }

    private fun playNextQueuedSong(): Boolean {
        val nextQueuedSong = playbackQueueManager.removeNextQueuedSong()
            ?: return false

        playNavigationSong(
            song = nextQueuedSong,
            orderedUpcomingSongs = upcomingSongs.removeFirstMatching(nextQueuedSong),
            addCurrentToHistory = true,
            clearForwardHistory = true
        )

        savePlayerState()

        return true
    }

    private fun handleSongCompleted() {
        val playbackSourceSongs = getPlaybackSourceSongs()

        when (repeatMode) {
            RepeatMode.ONE -> {
                currentSong?.let { song ->
                    playNavigationSong(
                        song = song,
                        orderedUpcomingSongs = upcomingSongs,
                        addCurrentToHistory = false,
                        clearForwardHistory = false
                    )
                }
            }

            RepeatMode.ALL -> {
                playNextSong()
            }

            RepeatMode.OFF -> {
                if (isShuffleEnabled) {
                    playNextSong()
                    return
                }

                val currentIndex = playbackSourceSongs.indexOfFirst { song ->
                    song.id == currentSong?.id
                }

                if (currentIndex == playbackSourceSongs.lastIndex) {
                    isPlaying = false
                    currentPosition = duration
                    savePlayerState()
                } else {
                    playNextSong()
                }
            }
        }
    }

    private fun handleServiceSongChanged(songId: Long?) {
        val newSong = librarySongs.firstOrNull { song ->
            song.id == songId
        } ?: return

        val pendingExternal = pendingExternalPlaybackSelection
            ?.takeIf { pending -> pending.selectedSongId == newSong.id }
        if (pendingExternal != null) {
            pendingExternalPlaybackSelection = null
            playbackNavigationHistory.clearAll()
            playbackQueueManager.replaceQueue(emptyList())
            playbackContextSongs = pendingExternal.playbackContext

            val live = musicPlayer.adoptLiveSession(librarySongs)
            currentSong = newSong
            currentPosition = live?.currentPosition ?: musicPlayer.getCurrentPosition()
            duration = live?.duration ?: newSong.duration
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
            isPlaying = live?.isPlaying ?: musicPlayer.isPlaying()
            upcomingSongs = live?.upcomingSongs ?: pendingExternal.playbackContext
                .dropWhile { song -> song.id != newSong.id }
                .drop(1)

            musicPlayer.synchronizeNavigationPolicy(
                shuffleEnabled = shuffleMode.usesDynamicSongShuffle,
                repeatMode = repeatMode,
                origin = ControllerSynchronizationOrigin.EXTERNAL
            )
            applyReplayGainForCurrentSong()
            startProgressUpdates()
            savePlayerState()
            return
        }

        if (currentSong?.id == newSong.id) {
            musicPlayer.synchronizeNavigationPolicy(
                shuffleEnabled = shuffleMode.usesDynamicSongShuffle,
                repeatMode = repeatMode,
                origin =
                    ControllerSynchronizationOrigin.CROSSFADE_HANDOFF_INTERNAL
            )
            applyReplayGainForCurrentSong()
            return
        }

        currentSong?.let { previousSong ->
            playbackNavigationHistory.addPreviousSong(previousSong)
            playbackNavigationHistory.clearForwardHistory()
        }

        currentSong = newSong
        currentPosition = musicPlayer.getCurrentPosition()
        duration = newSong.duration.toInt()
        isPlaying = musicPlayer.isPlaying()

        if (playbackQueue.firstOrNull()?.id == newSong.id) {
            playbackQueue.removeAt(0)
        }

        syncServicePlaylistKeepingCurrent(
            playlistSynchronizationOrigin =
                ControllerSynchronizationOrigin.CROSSFADE_HANDOFF_INTERNAL
        )
        applyReplayGainForCurrentSong()

        startProgressUpdates()
        savePlayerState()
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    private fun buildPlaybackPlaylist(startSong: Song): List<Song> {
        val refreshedUpcomingSongs = refreshUpcomingSongs(
            startSong = startSong,
            preserveExistingShuffleOrder = false
        )

        return listOf(startSong) + refreshedUpcomingSongs
    }

    private fun playNavigationSong(
        song: Song,
        orderedUpcomingSongs: List<Song>,
        addCurrentToHistory: Boolean,
        clearForwardHistory: Boolean
    ) {
        val previousSong = currentSong

        if (addCurrentToHistory && previousSong != null && previousSong.id != song.id) {
            playbackNavigationHistory.addPreviousSong(previousSong)
        }

        if (clearForwardHistory) {
            playbackNavigationHistory.clearForwardHistory()
        }

        upcomingSongs = orderedUpcomingSongs

        startSongPlayback(
            song = song,
            playlist = listOf(song) + orderedUpcomingSongs
        )
    }

    private fun startSongPlayback(
        song: Song,
        playlist: List<Song>
    ) {
        currentSong = song
        isPlaying = true
        currentPosition = 0
        duration = song.duration.toInt()

        musicPlayer.playSong(
            song = song,
            playlist = playlist
        )

        musicPlayer.setShuffleEnabled(shuffleMode.usesDynamicSongShuffle)
        musicPlayer.setRepeatMode(repeatMode)
        applyReplayGainForCurrentSong()

        startProgressUpdates()
        savePlayerState()
    }

    private fun List<Song>.removeFirstMatching(song: Song): List<Song> {
        val matchingIndex = indexOfFirst { candidate ->
            candidate.id == song.id
        }

        if (matchingIndex == -1) {
            return this
        }

        return take(matchingIndex) + drop(matchingIndex + 1)
    }

    private fun refreshUpcomingSongs(
        startSong: Song,
        preserveExistingShuffleOrder: Boolean
    ): List<Song> {
        val refreshedUpcomingSongs = upcomingPlaylistBuilder.buildUpcomingPlaylistAfterCurrent(
            startSong = startSong,
            playbackSourceSongs = getPlaybackSourceSongs(),
            queuedSongsAfterCurrent = playbackQueueManager.getQueuedSongsAfterCurrent(
                currentSongId = startSong.id
            ),
            currentUpcomingSongs = upcomingSongs,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            preserveExistingShuffleOrder = preserveExistingShuffleOrder
        )

        upcomingSongs = refreshedUpcomingSongs

        return refreshedUpcomingSongs
    }

    private fun syncServicePlaylistKeepingCurrent(
        preserveExistingShuffleOrder: Boolean = true,
        playlistSynchronizationOrigin: ControllerSynchronizationOrigin =
            ControllerSynchronizationOrigin.EXTERNAL
    ) {
        publishDerivedPlaybackState()
        val song = currentSong ?: return

        val refreshedUpcomingSongs = refreshUpcomingSongs(
            startSong = song,
            preserveExistingShuffleOrder = preserveExistingShuffleOrder
        )

        musicPlayer.updateUpcomingPlaylist(
            upcomingSongs = refreshedUpcomingSongs,
            origin = playlistSynchronizationOrigin
        )

        musicPlayer.synchronizeNavigationPolicy(
            shuffleEnabled = shuffleMode.usesDynamicSongShuffle,
            repeatMode = repeatMode,
            origin = playlistSynchronizationOrigin
        )
    }

    private fun applyReplayGainForCurrentSong() {
        val song = currentSong
        val requestedMode = replayGainMode

        replayGainRequestId += 1
        val requestId = replayGainRequestId

        if (song == null || requestedMode == ReplayGainMode.OFF) {
            musicPlayer.setVolume(1f)
            _audioOutputState.update { state ->
                state.copy(replayGainDb = null, appliedVolumeMultiplier = 1f)
            }
            return
        }

        val requestedIsAlbumPlaybackContext = isAlbumPlaybackContextForSong(song)

        musicPlayer.setVolume(1f)
        _audioOutputState.update { state ->
            state.copy(replayGainDb = null, appliedVolumeMultiplier = 1f)
        }

        coroutineScope.launch {
            val replayGainInfo = replayGainRepository.getReplayGainInfo(song)

            val volumeMultiplier = replayGainVolumeMultiplier(
                replayGainInfo = replayGainInfo,
                replayGainMode = requestedMode,
                isAlbumPlaybackContext = requestedIsAlbumPlaybackContext
            )
            val gainDb = selectReplayGainDb(
                replayGainInfo = replayGainInfo,
                replayGainMode = requestedMode,
                isAlbumPlaybackContext = requestedIsAlbumPlaybackContext
            )

            val isStillCurrentRequest = replayGainRequestId == requestId
            val isStillSameSong = currentSong?.id == song.id
            val isStillSameMode = replayGainMode == requestedMode
            val isStillSamePlaybackContext =
                isAlbumPlaybackContextForSong(song) == requestedIsAlbumPlaybackContext

            if (
                isStillCurrentRequest &&
                isStillSameSong &&
                isStillSameMode &&
                isStillSamePlaybackContext
            ) {
                musicPlayer.setVolume(volumeMultiplier)
                _audioOutputState.update { state ->
                    state.copy(
                        replayGainDb = gainDb,
                        appliedVolumeMultiplier = volumeMultiplier
                    )
                }
            }
        }
    }

    /** Prepares a target-scoped baseline without changing the current logical player's volume. */
    internal fun prepareReplayGainBaseline(
        songId: Long,
        onPrepared: (Float) -> Unit
    ): Boolean {
        val song = sequenceOf(
            playbackContextSongs,
            playbackQueue.toList(),
            upcomingSongsValue,
            librarySongs
        ).flatten().firstOrNull { candidate -> candidate.id == songId }
            ?: return false
        val requestedMode = replayGainMode
        val requestedIsAlbumPlaybackContext = isAlbumPlaybackContextForSong(song)
        if (requestedMode == ReplayGainMode.OFF) {
            onPrepared(1f)
            return true
        }

        coroutineScope.launch {
            val replayGainInfo = runCatching {
                replayGainRepository.getReplayGainInfo(song)
            }.getOrNull() ?: return@launch
            val volumeMultiplier = replayGainVolumeMultiplier(
                replayGainInfo = replayGainInfo,
                replayGainMode = requestedMode,
                isAlbumPlaybackContext = requestedIsAlbumPlaybackContext
            )
            if (
                replayGainMode == requestedMode &&
                isAlbumPlaybackContextForSong(song) ==
                requestedIsAlbumPlaybackContext
            ) {
                onPrepared(volumeMultiplier)
            }
        }
        return true
    }

    private fun isAlbumPlaybackContextForSong(song: Song): Boolean {
        if (playbackContextSongs.size <= 1) return false
        if (playbackContextSongs.none { contextSong -> contextSong.id == song.id }) return false

        val currentAlbumTitle = song.album.ifBlank { "Unknown Album" }
        if (playbackContextSongs.any { contextSong ->
                !contextSong.album.ifBlank { "Unknown Album" }
                    .equals(currentAlbumTitle, ignoreCase = true)
            }
        ) {
            return false
        }

        val folderGroups = playbackContextSongs.groupBy(Song::folderPath)
        if (folderGroups.size == 1) return true

        val normalizedParents = folderGroups.keys
            .map { path ->
                path.trim()
                    .replace('\\', '/')
                    .trimEnd('/')
                    .substringBeforeLast('/', missingDelimiterValue = "")
                    .lowercase(Locale.ROOT)
            }
            .distinct()
        if (normalizedParents.size != 1 || normalizedParents.single().isBlank()) return false

        val folderDiscNumbers = folderGroups.values.map { folderSongs ->
            folderSongs.mapNotNull(Song::knownDiscNumber).distinct().singleOrNull()
        }
        if (folderDiscNumbers.any { it == null }) return false
        if (folderDiscNumbers.filterNotNull().distinct().size != folderGroups.size) return false

        val artistEvidence = folderGroups.values.mapNotNull(::albumPlaybackArtistEvidence).distinct()
        return artistEvidence.size <= 1
    }

    private fun albumPlaybackArtistEvidence(songs: List<Song>): String? {
        val albumArtists = songs.map { it.albumArtist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (albumArtists.size == 1) return albumArtists.single().lowercase(Locale.ROOT)

        val trackArtists = songs.map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
        return trackArtists.singleOrNull()?.lowercase(Locale.ROOT)
    }

    private fun getPlaybackSourceSongs(): List<Song> {
        return if (playbackContextSongs.isNotEmpty()) {
            playbackContextSongs
        } else {
            librarySongs
        }
    }

    companion object {
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000
    }
}

internal fun replacementSong(song: Song, updatedSongs: List<Song>): Song? {
    updatedSongs.firstOrNull { candidate ->
        candidate.id == song.id &&
                (song.volumeName.isBlank() || candidate.volumeName == song.volumeName)
    }?.let { return it }
    return when (val resolution = SongReferenceResolver.resolve(song.toSongReference(), updatedSongs)) {
        is SongReferenceResolution.Resolved -> resolution.song
        else -> null
    }
}

internal fun replaceSongReferences(
    songs: List<Song>,
    updatedSongs: List<Song>
): List<Song> {
    return songs.mapNotNull { song -> replacementSong(song, updatedSongs) }
}
