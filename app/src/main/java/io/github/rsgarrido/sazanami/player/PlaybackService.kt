package io.github.rsgarrido.sazanami.player

import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionError
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import io.github.rsgarrido.sazanami.MainActivity
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.ListeningEventRepository
import io.github.rsgarrido.sazanami.data.ListeningNativeTrackResolver
import io.github.rsgarrido.sazanami.data.local.DatabaseProvider
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.performance.PerformanceTraceNames
import io.github.rsgarrido.sazanami.performance.tracePerformance
import io.github.rsgarrido.sazanami.player.audio.AdvancedAudioRuntimeBridge
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.audio.AudioRouteCategory
import io.github.rsgarrido.sazanami.player.audio.mapAudioRoute
import io.github.rsgarrido.sazanami.player.audio.mapAudioSourceFormat
import io.github.rsgarrido.sazanami.player.audio.withAudioOffloadPreference
import io.github.rsgarrido.sazanami.player.equalizer.AudioProcessingPolicy
import io.github.rsgarrido.sazanami.player.equalizer.CrossfadeOffloadPolicy
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerAudioProcessor
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerDspRuntime
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRenderersFactory
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.activeAutomaticHeadroomEnabled
import io.github.rsgarrido.sazanami.player.equalizer.toDspConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var physicalPlayers: DualPlayerPlaybackCoordinator
    private val player: ExoPlayer
        get() = physicalPlayers.logicalPhysicalPlayer
    private lateinit var sessionPlayer: SmoothPlaybackPlayer
    private lateinit var playerStateStorage: PlayerStateStorage
    private lateinit var listeningAdapter: PlaybackServiceListeningAdapter
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appPreferencesRepository: AppPreferencesRepository
    private lateinit var androidAutoCatalogRepository: AndroidAutoCatalogRepository
    @Volatile
    private var androidAutoCatalogSnapshot: AndroidAutoCatalogSnapshot = AndroidAutoCatalogSnapshot.EMPTY
    private var servicePlaybackContextSongs: List<Song> = emptyList()
    private lateinit var audioManager: AudioManager
    private var isRemotePlayback = false
    private var activeServiceBinding: ActiveServiceBinding? = null
    private val checkpointHandler = Handler(Looper.getMainLooper())
    private val checkpointRunnable = object : Runnable {
        override fun run() {
            saveServicePlaybackState()
            if (::physicalPlayers.isInitialized && player.isPlaying) {
                checkpointHandler.postDelayed(
                    this,
                    PlaybackStateCheckpointPolicy.DEFAULT_INTERVAL_MILLIS
                )
            }
        }
    }
    private val activePlayerIntegration = object : ActivePlayerIntegration {
        override fun unbind(pipeline: PhysicalPlayerPipeline) {
            unbindActivePipeline(pipeline)
        }

        override fun bind(
            pipeline: PhysicalPlayerPipeline,
            transition: AuthoritativeRoleTransition?
        ) {
            bindActivePipeline(pipeline, transition)
        }

        override fun onCrossfadeIncomingAudible(incomingMediaItem: MediaItem) {
            listeningAdapter.onCrossfadeIncomingAudible(incomingMediaItem)
        }

        override fun onCrossfadeLogicalHandoff(incomingMediaItem: MediaItem) {
            listeningAdapter.onCrossfadeLogicalHandoff(incomingMediaItem)
        }

        override fun onCrossfadeCompleted(outgoingMediaItem: MediaItem?) {
            listeningAdapter.onCrossfadeCompleted(outgoingMediaItem)
        }

        override fun onCrossfadeCancelled(
            outgoingMediaItem: MediaItem?,
            incomingMediaItem: MediaItem,
            survivingMediaItem: MediaItem?
        ) {
            listeningAdapter.onCrossfadeCancelled(
                outgoingItem = outgoingMediaItem,
                incomingItem = incomingMediaItem,
                survivingItem = survivingMediaItem
            )
        }
    }

    /** All authoritative service callbacks are identity-gated to one physical role. */
    private inner class ActiveServiceBinding(
        val pipeline: PhysicalPlayerPipeline
    ) {
        private var released = false

        private fun isAuthoritative(): Boolean =
            !released &&
                    ::physicalPlayers.isInitialized &&
                    physicalPlayers.isActive(pipeline.player)

        private val playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!isAuthoritative()) return
                saveServicePlaybackState()
                AdvancedAudioRuntimeBridge.updateSourceFormat(null)
                val mappedReason = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ->
                        ListeningMediaTransitionReason.REPEAT
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ->
                        ListeningMediaTransitionReason.AUTOMATIC
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
                        ListeningMediaTransitionReason.SEEK
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ->
                        ListeningMediaTransitionReason.PLAYLIST_CHANGED
                    else -> return
                }
                listeningAdapter.onMediaItemTransition(
                    mediaItem,
                    mappedReason,
                    pipeline.player.isPlaying
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isAuthoritative()) return
                checkpointHandler.removeCallbacks(checkpointRunnable)
                if (isPlaying) {
                    checkpointHandler.postDelayed(
                        checkpointRunnable,
                        PlaybackStateCheckpointPolicy.DEFAULT_INTERVAL_MILLIS
                    )
                } else {
                    saveServicePlaybackState()
                }
                listeningAdapter.onIsPlayingChanged(
                    pipeline.player.currentMediaItem,
                    isPlaying
                )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!isAuthoritative()) return
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    saveServicePlaybackState()
                }
                val isWithinSameItem =
                    oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                val isSeek = reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                if (isWithinSameItem && isSeek) {
                    listeningAdapter.onPositionDiscontinuity(
                        pipeline.player.currentMediaItem
                    )
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (isAuthoritative()) saveServicePlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isAuthoritative()) return
                when (playbackState) {
                    Player.STATE_ENDED -> listeningAdapter.onNaturalEnd(
                        pipeline.player.currentMediaItem
                    )
                    Player.STATE_IDLE -> listeningAdapter.onStopped()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isAuthoritative()) listeningAdapter.onError()
            }

            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                if (!isAuthoritative()) return
                isRemotePlayback =
                    deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
                publishAudioRoute()
            }
        }

        private val analyticsListener = object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                if (!isAuthoritative()) return
                tracePerformance(PerformanceTraceNames.AUDIO_INPUT_FORMAT_CHANGED) {
                    AdvancedAudioRuntimeBridge.updateSourceFormat(
                        mapAudioSourceFormat(format)
                    )
                }
            }

            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (isAuthoritative()) {
                    AdvancedAudioRuntimeBridge.updateAudioSessionId(
                        audioSessionId.takeIf { it > 0 }
                    )
                }
            }
        }

        private val offloadListener = object : ExoPlayer.AudioOffloadListener {
            override fun onOffloadedPlayback(isOffloadedPlayback: Boolean) {
                if (!isAuthoritative()) return
                tracePerformance(PerformanceTraceNames.AUDIO_OFFLOAD_STATE_CHANGED) {
                    AdvancedAudioRuntimeBridge.updateOffloadPlayback(
                        isOffloadedPlayback
                    )
                }
            }

            override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
                if (!isAuthoritative()) return
                tracePerformance(PerformanceTraceNames.AUDIO_OFFLOAD_SLEEPING_CHANGED) {
                    AdvancedAudioRuntimeBridge.updateSleepingForOffload(
                        isSleepingForOffload
                    )
                }
            }
        }

        fun attach() {
            pipeline.player.addListener(playerListener)
            pipeline.player.addAnalyticsListener(analyticsListener)
            pipeline.player.addAudioOffloadListener(offloadListener)
        }

        fun release() {
            if (released) return
            released = true
            pipeline.player.removeListener(playerListener)
            pipeline.player.removeAnalyticsListener(analyticsListener)
            pipeline.player.removeAudioOffloadListener(offloadListener)
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            publishAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            publishAudioRoute()
        }
    }

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val result = super.onConnect(session, controller)
            if (!result.isAccepted) return result
            val commands = result.availableSessionCommands.buildUpon()
                .add(AUTO_TOGGLE_SHUFFLE_COMMAND)
                .add(AUTO_TOGGLE_REPEAT_ALL_COMMAND)
                .build()
            return MediaSession.ConnectionResult.accept(
                commands,
                result.availablePlayerCommands
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceBackgroundFuture {
            val catalog = loadAndroidAutoCatalog()
            LibraryResult.ofItem(buildBrowseTree(catalog).toMediaItem(), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceBackgroundFuture {
            val item = buildBrowseTree(loadAndroidAutoCatalog()).findNode(mediaId)
            if (item != null) {
                LibraryResult.ofItem(item.toMediaItem(), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceBackgroundFuture {
            val children = buildBrowseTree(loadAndroidAutoCatalog())
                .findNode(parentId)
                ?.children
                .orEmpty()
            val fromIndex = (page * pageSize).coerceAtMost(children.size)
            val toIndex = (fromIndex + pageSize).coerceAtMost(children.size)
            LibraryResult.ofItemList(
                children.subList(fromIndex, toIndex).map { it.toMediaItem() },
                params
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = serviceBackgroundFuture {
            val catalog = loadAndroidAutoCatalog()
            val resultCount = AndroidAutoSearchResolver.searchSongs(query, catalog).size
            session.notifySearchResultChanged(browser, query, resultCount, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceBackgroundFuture {
            val results = AndroidAutoSearchResolver.searchSongs(
                query = query,
                catalog = loadAndroidAutoCatalog()
            )
            val fromIndex = (page * pageSize).coerceAtMost(results.size)
            val toIndex = (fromIndex + pageSize).coerceAtMost(results.size)
            LibraryResult.ofItemList(
                results.subList(fromIndex, toIndex).map { song ->
                    song.toAndroidAutoSearchMediaItem(query)
                },
                params
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                AUTO_TOGGLE_SHUFFLE_ACTION -> serviceFuture {
                    toggleAndroidAutoShuffle()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                AUTO_TOGGLE_REPEAT_ALL_ACTION -> serviceFuture {
                    toggleAndroidAutoRepeatAll()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.all { it.localConfiguration != null }) {
                return super.onSetMediaItems(
                    mediaSession,
                    controller,
                    mediaItems,
                    startIndex,
                    startPositionMs
                )
            }
            return serviceFuture {
                resolveAndroidAutoMediaItems(
                    mediaItems = mediaItems,
                    startIndex = startIndex,
                    startPositionMs = startPositionMs
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val activePipeline = createPhysicalPlayerPipeline(
            role = PhysicalPlayerRole.ACTIVE,
            audioAttributes = audioAttributes
        )
        val standbyPipeline = try {
            createPhysicalPlayerPipeline(
                role = PhysicalPlayerRole.STANDBY,
                audioAttributes = audioAttributes
            )
        } catch (error: RuntimeException) {
            activePipeline.release()
            throw error
        }
        physicalPlayers = DualPlayerPlaybackCoordinator(
            initialActive = activePipeline,
            initialStandby = standbyPipeline,
            standbyBaselinePreparer = StandbyBaselinePreparer { mediaItem, result ->
                PlaybackLibraryBridge.prepareReplayGainBaseline(
                    mediaId = mediaItem.mediaId,
                    onPrepared = result
                )
            },
            crossfadeScheduler = HandlerCrossfadeScheduler(
                Handler(activePipeline.player.applicationLooper)
            ),
            initialCrossfadeConfiguration = CrossfadeRuntimeConfiguration.DISABLED
        )
        physicalPlayers.start(serviceScope)
        sessionPlayer = SmoothPlaybackPlayer(
            initialPhysicalPlayer = player,
            onBaselineVolumeChanged = physicalPlayers::updateActiveBaseline,
            onLogicalCommand = { event ->
                physicalPlayers.onLogicalCommand(event)
            }
        )
        physicalPlayers.attachLogicalPlayer(
            player = sessionPlayer,
            integration = activePlayerIntegration
        )
        appPreferencesRepository = AppPreferencesRepository.getInstance(this)
        audioManager = getSystemService(AudioManager::class.java)
        playerStateStorage = PlayerStateStorage(this)
        val database = DatabaseProvider.getDatabase(this)
        androidAutoCatalogRepository = AndroidAutoCatalogRepository(
            context = this,
            database = database,
            preferencesRepository = appPreferencesRepository
        )
        listeningAdapter = PlaybackServiceListeningAdapter(
            trackResolver = ListeningNativeTrackResolver(database),
            eventRepository = ListeningEventRepository(database.listeningEventDao())
        )
        bindActivePipeline(activePipeline, transition = null)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, checkpointHandler)
        applyAudioOffloadPreference(AudioOffloadPreference.DISABLED)
        AdvancedAudioRuntimeBridge.onPlayerConnected(AudioOffloadPreference.DISABLED)
        isRemotePlayback = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        publishAudioRoute()
        observePlaybackAudioPreferences()
        observeEqualizerPreferences()
        observeEqualizerRuntimeState()
        observeSmoothPlaybackPreference()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, libraryCallback)
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(
                buildAndroidAutoMediaButtonPreferences(
                    shuffleEnabled = playerStateStorage.getShuffleMode().isEnabled,
                    repeatMode = playerStateStorage.getRepeatMode()
                )
            )
            .build()
        PlaybackLibraryBridge.registerPlaybackPolicyListener { shuffleEnabled, repeatMode ->
            serviceScope.launch {
                updateAndroidAutoMediaButtonPreferences(shuffleEnabled, repeatMode)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        checkpointHandler.removeCallbacks(checkpointRunnable)
        saveServicePlaybackState()
        activeServiceBinding?.release()
        activeServiceBinding = null
        listeningAdapter.closeGracefully()
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        PlaybackLibraryBridge.unregisterPlaybackPolicyListener()
        mediaSession?.release()
        mediaSession = null
        sessionPlayer.releaseTransitionResources()
        physicalPlayers.release()
        EqualizerRuntimeBridge.release()
        serviceScope.cancel()
        AdvancedAudioRuntimeBridge.disconnect()
        super.onDestroy()
    }

    private fun observePlaybackAudioPreferences() {
        serviceScope.launch {
            combine(
                appPreferencesRepository.state
                    .filter { preferences -> preferences.isLoaded },
                EqualizerRuntimeBridge.state
                    .map { state ->
                        AudioProcessingRequirements(
                            equalizerEffectivelyActive =
                                state.effectivelyActive &&
                                        !state.limiterEffectivelyActive,
                            limiterEffectivelyActive =
                                state.limiterRequestedEnabled ||
                                        state.limiterEffectivelyActive,
                            comparisonSessionActive =
                                state.comparisonSessionActive
                        )
                    }
            ) { preferences, requirements ->
                PlaybackAudioRuntimePreferences(
                    userOffloadPreference = preferences.audioOffloadPreference,
                    requirements = requirements,
                    crossfade = CrossfadeRuntimeConfiguration(
                        enabled = preferences.crossfadeEnabled,
                        durationMillis = preferences.crossfadeDurationMs.toLong(),
                        preserveAlbumTransitions =
                            preferences.preserveAlbumTransitions
                    ).normalized()
                )
            }
                .distinctUntilChanged()
                .collectLatest { runtime ->
                    CrossfadeTrace.log(
                        "PREFERENCES enabled=${runtime.crossfade.enabled} " +
                                "durationMs=${runtime.crossfade.durationMillis} " +
                                "preserveAlbumTransitions=" +
                                runtime.crossfade.preserveAlbumTransitions
                    )
                    if (runtime.crossfade.enabled) {
                        // Decode both pipelines before overlap can become eligible.
                        applyAudioProcessingPolicy(runtime, crossfadeEnabled = true)
                        physicalPlayers.updateCrossfadeConfiguration(runtime.crossfade)
                    } else {
                        // Collapse an active overlap before restoring normal offload policy.
                        physicalPlayers.updateCrossfadeConfiguration(runtime.crossfade)
                        applyAudioProcessingPolicy(runtime, crossfadeEnabled = false)
                    }
                }
        }
    }

    private fun observeSmoothPlaybackPreference() {
        serviceScope.launch {
            appPreferencesRepository.state
                .filter { preferences -> preferences.isLoaded }
                .map { preferences -> preferences.smoothPlayPauseEnabled }
                .distinctUntilChanged()
                .collectLatest(sessionPlayer::setSmoothPlaybackEnabled)
        }
    }

    private fun observeEqualizerRuntimeState() {
        serviceScope.launch {
            EqualizerRuntimeBridge.state
                .collectLatest(
                    AdvancedAudioRuntimeBridge::updateEqualizerRuntimeState
                )
        }
    }

    private fun observeEqualizerPreferences() {
        serviceScope.launch {
            appPreferencesRepository.state
                .filter { preferences -> preferences.isLoaded }
                .map { preferences ->
                    preferences.equalizerPreferences
                }
                .distinctUntilChanged()
                .collectLatest { equalizerPreferences ->
                    EqualizerRuntimeBridge.requestConfiguration(
                        configuration =
                            equalizerPreferences
                                .toDspConfiguration(),
                        automaticHeadroomEnabled =
                            equalizerPreferences
                                .activeAutomaticHeadroomEnabled,
                        mode = equalizerPreferences.mode,
                        limiterConfiguration =
                            LimiterConfiguration(
                                enabled =
                                    equalizerPreferences
                                        .limiterEnabled,
                                ceilingDbfs =
                                    equalizerPreferences
                                        .limiterCeilingDbfs
                            )
                    )
                }
        }
    }

    private fun applyAudioOffloadPreference(
        preference: AudioOffloadPreference
    ) {
        applyAudioProcessingPolicy(
            userPreference = preference,
            equalizerEffectivelyActive = false,
            limiterEffectivelyActive = false,
            comparisonSessionActive = false,
            crossfadeEnabled = false
        )
    }

    private fun applyAudioProcessingPolicy(
        runtime: PlaybackAudioRuntimePreferences,
        crossfadeEnabled: Boolean
    ) {
        applyAudioProcessingPolicy(
            userPreference = runtime.userOffloadPreference,
            equalizerEffectivelyActive =
                runtime.requirements.equalizerEffectivelyActive,
            limiterEffectivelyActive =
                runtime.requirements.limiterEffectivelyActive,
            comparisonSessionActive =
                runtime.requirements.comparisonSessionActive,
            crossfadeEnabled = crossfadeEnabled
        )
    }

    private fun applyAudioProcessingPolicy(
        userPreference: AudioOffloadPreference,
        equalizerEffectivelyActive: Boolean,
        limiterEffectivelyActive: Boolean,
        comparisonSessionActive: Boolean,
        crossfadeEnabled: Boolean
    ) {
        tracePerformance(PerformanceTraceNames.AUDIO_OFFLOAD_PREFERENCE_APPLIED) {
            val decision = AudioProcessingPolicy.evaluate(
                userOffloadPreference = userPreference,
                equalizerEffectivelyActive =
                    equalizerEffectivelyActive,
                limiterEffectivelyActive =
                    limiterEffectivelyActive,
                comparisonSessionActive =
                    comparisonSessionActive
            )
            val effectiveOffloadPreference = CrossfadeOffloadPolicy.effectivePreference(
                normalPreference = decision.effectiveOffloadPreference,
                crossfadeEnabled = crossfadeEnabled
            )
            CrossfadeTrace.log(
                "OFFLOAD crossfadeEnabled=$crossfadeEnabled effectivePreference=" +
                        effectiveOffloadPreference
            )
            physicalPlayers.forEachPipeline { pipeline ->
                val updatedParameters = pipeline.player.trackSelectionParameters
                    .withAudioOffloadPreference(
                        effectiveOffloadPreference
                    )
                if (pipeline.player.trackSelectionParameters != updatedParameters) {
                    pipeline.player.trackSelectionParameters = updatedParameters
                }
            }
            AdvancedAudioRuntimeBridge.updateOffloadPreference(
                effectiveOffloadPreference
            )
        }
    }

    private data class AudioProcessingRequirements(
        val equalizerEffectivelyActive: Boolean,
        val limiterEffectivelyActive: Boolean,
        val comparisonSessionActive: Boolean
    )

    private data class PlaybackAudioRuntimePreferences(
        val userOffloadPreference: AudioOffloadPreference,
        val requirements: AudioProcessingRequirements,
        val crossfade: CrossfadeRuntimeConfiguration
    )

    private fun bindActivePipeline(
        pipeline: PhysicalPlayerPipeline,
        transition: AuthoritativeRoleTransition?
    ) {
        check(physicalPlayers.isActive(pipeline.player))
        activeServiceBinding?.release()
        if (transition?.affectsListeningHistory == true) {
            listeningAdapter.onMediaItemTransition(
                transition.incomingMediaItem,
                ListeningMediaTransitionReason.AUTOMATIC,
                isPlaying = pipeline.player.isPlaying
            )
        }
        AdvancedAudioRuntimeBridge.updateSourceFormat(null)
        AdvancedAudioRuntimeBridge.updateAudioSessionId(null)
        AdvancedAudioRuntimeBridge.updateOffloadPlayback(false)
        AdvancedAudioRuntimeBridge.updateSleepingForOffload(false)
        activeServiceBinding = ActiveServiceBinding(pipeline).also { it.attach() }
        isRemotePlayback =
            pipeline.player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        publishAudioRoute()
        saveServicePlaybackState()
    }

    private fun unbindActivePipeline(pipeline: PhysicalPlayerPipeline) {
        val binding = activeServiceBinding ?: return
        if (binding.pipeline !== pipeline) return
        binding.release()
        activeServiceBinding = null
        checkpointHandler.removeCallbacks(checkpointRunnable)
    }

    private fun publishAudioRoute() {
        val route = if (isRemotePlayback) {
            mapAudioRoute(deviceType = null, isLocalPlayback = false)
        } else {
            mapAudioRoute(
                deviceType = resolveLocalMediaRouteType(),
                isLocalPlayback = true
            )
        }
        if (AdvancedAudioRuntimeBridge.state.value.routeInfo != route) {
            tracePerformance(PerformanceTraceNames.AUDIO_ROUTE_CHANGED) {
                AdvancedAudioRuntimeBridge.updateRouteInfo(route)
            }
        }
    }

    private fun resolveLocalMediaRouteType(): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            return audioManager.getAudioDevicesForAttributes(attributes)
                .firstOrNull()
                ?.type
        }

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (outputs.size == 1) return outputs.single().type
        val hasBuiltInSpeaker = outputs.any { device ->
            mapAudioRoute(device.type, isLocalPlayback = true).category ==
                    AudioRouteCategory.BUILT_IN_SPEAKER
        }
        val hasExternalRoute = outputs.any { device ->
            val category = mapAudioRoute(device.type, isLocalPlayback = true).category
            category != AudioRouteCategory.BUILT_IN_SPEAKER &&
                    category != AudioRouteCategory.UNKNOWN
        }
        return if (hasBuiltInSpeaker && !hasExternalRoute) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            null
        }
    }

    private fun saveServicePlaybackState() {
        if (
            !::physicalPlayers.isInitialized ||
            !::playerStateStorage.isInitialized
        ) {
            return
        }
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        playerStateStorage.saveServicePlaybackState(
            currentSongId = songId,
            currentPosition = player.currentPosition
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt(),
            repeatMode = repeatMode
        )
    }

    private fun createPhysicalPlayerPipeline(
        role: PhysicalPlayerRole,
        audioAttributes: AudioAttributes
    ): PhysicalPlayerPipeline {
        val runtime: EqualizerDspRuntime =
            EqualizerRuntimeBridge.createRuntime()
        val processor = EqualizerAudioProcessor(runtime)
        val renderersFactory = EqualizerRenderersFactory(
            context = this,
            equalizerAudioProcessor = processor
        )
        return try {
            val physicalPlayer = ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(
                    audioAttributes,
                    role.managesAudioFocus
                )
                .setHandleAudioBecomingNoisy(
                    role.handlesAudioBecomingNoisy
                )
                .setPauseAtEndOfMediaItems(true)
                .build()
            if (role == PhysicalPlayerRole.STANDBY) {
                physicalPlayer.volume = 0f
                physicalPlayer.playWhenReady = false
            }
            PhysicalPlayerPipeline(
                initialRole = role,
                player = physicalPlayer,
                equalizerRuntime = runtime,
                equalizerAudioProcessor = processor,
                audioAttributes = audioAttributes
            )
        } catch (error: RuntimeException) {
            EqualizerRuntimeBridge.releaseRuntime(runtime)
            throw error
        }
    }

    private suspend fun loadAndroidAutoCatalog(): AndroidAutoCatalogSnapshot {
        val snapshot = androidAutoCatalogRepository.loadSnapshot()
        androidAutoCatalogSnapshot = snapshot
        return snapshot
    }

    private fun buildBrowseTree(catalog: AndroidAutoCatalogSnapshot): AutoBrowseNode {
        return buildAndroidAutoBrowseTree(
            songs = catalog.songs,
            rootTitle = getString(R.string.app_name),
            playlists = catalog.playlists,
            artistArtworkUris = catalog.artistArtworkUris
        )
    }

    private fun AutoBrowseNode.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            browsableChildrenStyle?.let { style ->
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    style.toMedia3ContentStyle()
                )
            }
            playableChildrenStyle?.let { style ->
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    style.toMedia3ContentStyle()
                )
            }
        }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setArtworkUri(artworkUri)
        if (!extras.isEmpty) metadataBuilder.setExtras(extras)

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun AutoBrowseContentStyle.toMedia3ContentStyle(): Int = when (this) {
        AutoBrowseContentStyle.LIST -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        AutoBrowseContentStyle.GRID -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    }

    private fun Song.toAndroidAutoSearchMediaItem(query: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId("song:search:$id")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery(query)
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist.ifBlank { "Unknown Artist" })
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private suspend fun resolveAndroidAutoMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): MediaSession.MediaItemsWithStartPosition {
        val catalog = loadAndroidAutoCatalog()
        if (catalog.songs.isEmpty() || mediaItems.isEmpty()) {
            return MediaSession.MediaItemsWithStartPosition(
                mediaItems,
                startIndex.coerceAtLeast(0),
                startPositionMs
            )
        }

        val selectedIndex = startIndex.takeIf { it in mediaItems.indices } ?: 0
        val requestedItem = mediaItems[selectedIndex]
        val preferredSongId = requestedItem.mediaId.substringAfterLast(':').toLongOrNull()
        val searchRequest = requestedItem.toAndroidAutoSearchRequest()
        val hasSearchRequest = !searchRequest.query.isNullOrBlank() ||
                !searchRequest.title.isNullOrBlank() ||
                !searchRequest.artist.isNullOrBlank() ||
                !searchRequest.album.isNullOrBlank() ||
                !searchRequest.playlist.isNullOrBlank() ||
                !searchRequest.genre.isNullOrBlank()

        val match = when {
            hasSearchRequest && preferredSongId != null -> {
                val query = searchRequest.query
                val clickedSongIsInResults = query.isNullOrBlank() ||
                        AndroidAutoSearchResolver.searchSongs(query, catalog).any { song ->
                            song.id == preferredSongId
                        }
                if (clickedSongIsInResults) {
                    AndroidAutoSearchResolver.resolveSongSelection(preferredSongId, catalog)
                } else {
                    null
                }
            }
            else -> null
        } ?: if (hasSearchRequest) {
            AndroidAutoSearchResolver.resolvePlayback(
                request = searchRequest,
                catalog = catalog,
                preferredSongId = preferredSongId ?: playerStateStorage.getCurrentSongId()
            )
        } else {
            resolveBrowseSelection(
                requestedMediaId = requestedItem.mediaId,
                catalog = catalog
            ) ?: preferredSongId?.let { songId ->
                AndroidAutoSearchResolver.resolveSongSelection(songId, catalog)
            }
        } ?: AndroidAutoSearchResolver.resolvePlayback(
            request = AndroidAutoSearchRequest(),
            catalog = catalog,
            preferredSongId = playerStateStorage.getCurrentSongId()
        )

        if (match == null) {
            return MediaSession.MediaItemsWithStartPosition(
                mediaItems,
                selectedIndex,
                startPositionMs
            )
        }

        servicePlaybackContextSongs = match.songs
        val selectedSong = match.selectedSong
        val controllerHandledSelection = PlaybackLibraryBridge.playSelectedSong(
            song = selectedSong,
            playbackContext = match.songs
        )
        val bridgeShuffleEnabled = PlaybackLibraryBridge.currentShuffleEnabled()
        val logicalShuffleMode = if (
            bridgeShuffleEnabled ?: playerStateStorage.getShuffleMode().isEnabled
        ) {
            PlaybackShuffleMode.SONGS
        } else {
            PlaybackShuffleMode.OFF
        }
        if (!controllerHandledSelection) {
            playerStateStorage.saveServicePlaybackContext(
                playbackContextSongIds = match.songs.map(Song::id),
                shuffleMode = logicalShuffleMode
            )
        }

        val orderedSongs = if (!controllerHandledSelection && logicalShuffleMode.isEnabled) {
            buildList {
                add(selectedSong)
                addAll(match.songs.filterNot { song -> song.id == selectedSong.id }.shuffled())
            }
        } else {
            match.songs
        }
        val resolvedIndex = orderedSongs.indexOfFirst { song -> song.id == selectedSong.id }
            .coerceAtLeast(0)
        return MediaSession.MediaItemsWithStartPosition(
            orderedSongs.map { song -> song.toPlayableMediaItem() },
            resolvedIndex,
            startPositionMs
        )
    }

    private fun resolveBrowseSelection(
        requestedMediaId: String,
        catalog: AndroidAutoCatalogSnapshot
    ): AndroidAutoPlaybackMatch? {
        val tree = buildBrowseTree(catalog)
        val selectedNode = tree.findNode(requestedMediaId) ?: return null
        val selectedSong = selectedNode.song ?: return null
        val contextSongs = tree.findParent(requestedMediaId)
            ?.children
            ?.mapNotNull(AutoBrowseNode::song)
            .orEmpty()
            .ifEmpty { listOf(selectedSong) }
        val selectedIndex = contextSongs.indexOfFirst { song -> song.id == selectedSong.id }
            .coerceAtLeast(0)
        return AndroidAutoPlaybackMatch(contextSongs, selectedIndex)
    }

    @Suppress("DEPRECATION")
    private fun MediaItem.toAndroidAutoSearchRequest(): AndroidAutoSearchRequest {
        val extras = requestMetadata.extras
        val query = requestMetadata.searchQuery
        return AndroidAutoSearchRequest(
            query = query,
            title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)
                ?: query?.let { mediaMetadata.title?.toString() },
            artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                ?: query?.let { mediaMetadata.artist?.toString() },
            album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                ?: query?.let { mediaMetadata.albumTitle?.toString() },
            playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST),
            genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)
        )
    }

    private suspend fun toggleAndroidAutoShuffle() {
        val currentlyEnabled = PlaybackLibraryBridge.currentShuffleEnabled()
            ?: playerStateStorage.getShuffleMode().isEnabled
        val enable = !currentlyEnabled
        if (!PlaybackLibraryBridge.setSongShuffleEnabled(enable)) {
            applyServiceOnlySongShuffle(enable)
        }
        updateAndroidAutoMediaButtonPreferences(
            shuffleEnabled = enable,
            repeatMode = PlaybackLibraryBridge.currentRepeatMode()
                ?: playerStateStorage.getRepeatMode()
        )
    }

    private suspend fun applyServiceOnlySongShuffle(enabled: Boolean) {
        val catalog = androidAutoCatalogSnapshot.takeIf { it.songs.isNotEmpty() }
            ?: loadAndroidAutoCatalog()
        val currentSongId = sessionPlayer.currentMediaItem?.mediaId?.toLongOrNull()
        val context = resolveServicePlaybackContext(catalog, currentSongId)
        val mode = if (enabled) PlaybackShuffleMode.SONGS else PlaybackShuffleMode.OFF
        playerStateStorage.saveServiceShuffleMode(mode)
        if (context.isEmpty() || currentSongId == null) return

        servicePlaybackContextSongs = context
        playerStateStorage.saveServicePlaybackContext(
            playbackContextSongIds = context.map(Song::id),
            shuffleMode = mode
        )
        val currentContextIndex = context.indexOfFirst { song -> song.id == currentSongId }
        if (currentContextIndex < 0) return
        val upcomingSongs = if (enabled) {
            context.filterNot { song -> song.id == currentSongId }.shuffled()
        } else {
            context.drop(currentContextIndex + 1)
        }
        val currentIndex = sessionPlayer.currentMediaItemIndex
        if (currentIndex < 0) return

        // Never replace the current MediaItem just to change logical shuffle order. Replacing the
        // active item flushes/re-prepares the decoder on some devices and creates an audible gap.
        val replaceFromIndex = currentIndex + 1
        val existingUpcomingIds = (replaceFromIndex until sessionPlayer.mediaItemCount)
            .map { index -> sessionPlayer.getMediaItemAt(index).mediaId }
        val requestedUpcomingIds = upcomingSongs.map { song -> song.id.toString() }
        if (existingUpcomingIds == requestedUpcomingIds) return

        sessionPlayer.replaceMediaItems(
            replaceFromIndex,
            sessionPlayer.mediaItemCount,
            upcomingSongs.map { song -> song.toPlayableMediaItem() }
        )
    }

    private fun resolveServicePlaybackContext(
        catalog: AndroidAutoCatalogSnapshot,
        currentSongId: Long?
    ): List<Song> {
        if (
            servicePlaybackContextSongs.isNotEmpty() &&
            (currentSongId == null || servicePlaybackContextSongs.any { song -> song.id == currentSongId })
        ) {
            return servicePlaybackContextSongs
        }
        val songsById = catalog.songs.associateBy(Song::id)
        val persisted = playerStateStorage.getPlaybackContextSongIds()
            .mapNotNull(songsById::get)
        if (persisted.isNotEmpty()) return persisted

        val timelineSongs = (0 until player.mediaItemCount)
            .mapNotNull { index -> player.getMediaItemAt(index).mediaId.toLongOrNull() }
            .mapNotNull(songsById::get)
        if (timelineSongs.isNotEmpty()) return timelineSongs
        val currentSong = currentSongId?.let { songId -> songsById[songId] }
        return currentSong?.let { song -> listOf(song) }.orEmpty()
    }

    private fun toggleAndroidAutoRepeatAll() {
        val current = PlaybackLibraryBridge.currentRepeatMode()
            ?: playerStateStorage.getRepeatMode()
        val enable = current != RepeatMode.ALL
        if (!PlaybackLibraryBridge.setRepeatAllEnabled(enable)) {
            player.repeatMode = if (enable) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            saveServicePlaybackState()
        }
        updateAndroidAutoMediaButtonPreferences(
            shuffleEnabled = PlaybackLibraryBridge.currentShuffleEnabled()
                ?: playerStateStorage.getShuffleMode().isEnabled,
            repeatMode = if (enable) RepeatMode.ALL else RepeatMode.OFF
        )
    }

    private fun buildAndroidAutoMediaButtonPreferences(
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode
    ): List<CommandButton> = listOf(
        CommandButton.Builder(
            if (shuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(if (shuffleEnabled) "Shuffle on" else "Shuffle")
            .setSessionCommand(AUTO_TOGGLE_SHUFFLE_COMMAND)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(
            if (repeatMode == RepeatMode.ALL) {
                CommandButton.ICON_REPEAT_ALL
            } else {
                CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName(if (repeatMode == RepeatMode.ALL) "Repeat all on" else "Repeat all")
            .setSessionCommand(AUTO_TOGGLE_REPEAT_ALL_COMMAND)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    )

    private fun updateAndroidAutoMediaButtonPreferences(
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode
    ) {
        mediaSession?.setMediaButtonPreferences(
            buildAndroidAutoMediaButtonPreferences(shuffleEnabled, repeatMode)
        )
    }

    private fun <T> serviceFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (error: Throwable) {
                future.setException(error)
            }
        }
        return future
    }

    /** Avoids legacy MediaBrowser root requests deadlocking the service main thread on I/O. */
    private fun <T> serviceBackgroundFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (error: Throwable) {
                future.setException(error)
            }
        }
        return future
    }

}


private const val AUTO_TOGGLE_SHUFFLE_ACTION =
    "io.github.rsgarrido.sazanami.action.AUTO_TOGGLE_SHUFFLE"
private const val AUTO_TOGGLE_REPEAT_ALL_ACTION =
    "io.github.rsgarrido.sazanami.action.AUTO_TOGGLE_REPEAT_ALL"
private val AUTO_TOGGLE_SHUFFLE_COMMAND = SessionCommand(AUTO_TOGGLE_SHUFFLE_ACTION, Bundle.EMPTY)
private val AUTO_TOGGLE_REPEAT_ALL_COMMAND = SessionCommand(AUTO_TOGGLE_REPEAT_ALL_ACTION, Bundle.EMPTY)
