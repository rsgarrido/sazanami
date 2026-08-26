package com.example.cdplaya.player

import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.cdplaya.MainActivity
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.ListeningEventRepository
import com.example.cdplaya.data.ListeningNativeTrackResolver
import com.example.cdplaya.data.local.DatabaseProvider
import com.example.cdplaya.data.preferences.AppPreferencesRepository
import com.example.cdplaya.performance.PerformanceTraceNames
import com.example.cdplaya.performance.tracePerformance
import com.example.cdplaya.player.audio.AdvancedAudioRuntimeBridge
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.audio.AudioRouteCategory
import com.example.cdplaya.player.audio.mapAudioRoute
import com.example.cdplaya.player.audio.mapAudioSourceFormat
import com.example.cdplaya.player.audio.withAudioOffloadPreference
import com.example.cdplaya.player.equalizer.AudioProcessingPolicy
import com.example.cdplaya.player.equalizer.EqualizerAudioProcessor
import com.example.cdplaya.player.equalizer.EqualizerDspRuntime
import com.example.cdplaya.player.equalizer.EqualizerRenderersFactory
import com.example.cdplaya.player.equalizer.EqualizerRuntimeBridge
import com.example.cdplaya.player.equalizer.activeAutomaticHeadroomEnabled
import com.example.cdplaya.player.equalizer.toDspConfiguration
import com.example.cdplaya.player.equalizer.limiter.LimiterConfiguration
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(buildBrowseTree().toMediaItem(), params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = buildBrowseTree().findNode(parentId)?.children.orEmpty()
            val fromIndex = (page * pageSize).coerceAtMost(children.size)
            val toIndex = (fromIndex + pageSize).coerceAtMost(children.size)

            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    children.subList(fromIndex, toIndex).map { it.toMediaItem() },
                    params
                )
            )
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

            val tree = buildBrowseTree()
            val selectedIndex = startIndex.takeIf { it in mediaItems.indices } ?: 0
            val requestedId = mediaItems.getOrNull(selectedIndex)?.mediaId.orEmpty()
            val selectedNode = tree.findNode(requestedId)
            val contextSongs = tree.findParent(requestedId)
                ?.children
                ?.mapNotNull { it.song }
                .orEmpty()
            val selectedSong = selectedNode?.song

            if (selectedSong != null) {
                val playbackContext = contextSongs.ifEmpty { listOf(selectedSong) }
                PlaybackLibraryBridge.playSelectedSong(selectedSong, playbackContext)
                val resolvedItems = playbackContext.map { song -> song.toPlayableMediaItem() }
                val resolvedIndex = resolvedItems.indexOfFirst {
                    it.mediaId == selectedSong.id.toString()
                }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        resolvedItems,
                        resolvedIndex,
                        startPositionMs
                    )
                )
            }

            return super.onSetMediaItems(
                mediaSession,
                controller,
                mediaItems,
                startIndex,
                startPositionMs
            )
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
            )
        )
        physicalPlayers.start(serviceScope)
        sessionPlayer = SmoothPlaybackPlayer(
            initialPhysicalPlayer = player,
            onBaselineVolumeChanged = physicalPlayers::updateActiveBaseline,
            onLogicalCommand = physicalPlayers::onLogicalCommand
        )
        physicalPlayers.attachLogicalPlayer(
            player = sessionPlayer,
            integration = activePlayerIntegration
        )
        appPreferencesRepository = AppPreferencesRepository.getInstance(this)
        audioManager = getSystemService(AudioManager::class.java)
        playerStateStorage = PlayerStateStorage(this)
        val database = DatabaseProvider.getDatabase(this)
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
        observeAudioOffloadPreference()
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
            .build()
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
        mediaSession?.release()
        mediaSession = null
        sessionPlayer.releaseTransitionResources()
        physicalPlayers.release()
        EqualizerRuntimeBridge.release()
        serviceScope.cancel()
        AdvancedAudioRuntimeBridge.disconnect()
        super.onDestroy()
    }

    private fun observeAudioOffloadPreference() {
        serviceScope.launch {
            combine(
                appPreferencesRepository.state
                .filter { preferences -> preferences.isLoaded }
                .map { preferences -> preferences.audioOffloadPreference },
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
            ) { preference, requirements ->
                preference to requirements
            }
                .distinctUntilChanged()
                .collectLatest { (preference, requirements) ->
                    applyAudioProcessingPolicy(
                        userPreference = preference,
                        equalizerEffectivelyActive =
                            requirements.equalizerEffectivelyActive,
                        limiterEffectivelyActive =
                            requirements.limiterEffectivelyActive,
                        comparisonSessionActive =
                            requirements.comparisonSessionActive
                    )
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
            comparisonSessionActive = false
        )
    }

    private fun applyAudioProcessingPolicy(
        userPreference: AudioOffloadPreference,
        equalizerEffectivelyActive: Boolean,
        limiterEffectivelyActive: Boolean,
        comparisonSessionActive: Boolean
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
            // The internal Session 6 overlap path requires two independently decoded PCM
            // pipelines. Offload remains disabled for this crossfade-capable service session.
            val effectiveOffloadPreference = if (physicalPlayers.crossfadeEnabled) {
                AudioOffloadPreference.DISABLED
            } else {
                decision.effectiveOffloadPreference
            }
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

    private fun bindActivePipeline(
        pipeline: PhysicalPlayerPipeline,
        transition: AuthoritativeRoleTransition?
    ) {
        check(physicalPlayers.isActive(pipeline.player))
        activeServiceBinding?.release()
        if (transition != null) {
            // Session 6 intentionally keeps one history authority: the outgoing binding owns
            // the first half, then this single logical transition starts incoming at midpoint.
            // Exact overlapping audible-time accounting remains for the integration session.
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

    private fun buildBrowseTree(): AutoBrowseNode {
        return buildAndroidAutoBrowseTree(PlaybackLibraryBridge.songs)
    }

    private fun AutoBrowseNode.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(children.isNotEmpty())
            .setIsPlayable(song != null)
            .setArtworkUri(song?.albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

}
