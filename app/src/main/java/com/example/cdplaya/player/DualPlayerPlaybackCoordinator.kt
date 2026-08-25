package com.example.cdplaya.player

import android.content.ContentResolver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.example.cdplaya.player.equalizer.EqualizerAudioProcessor
import com.example.cdplaya.player.equalizer.EqualizerDspRuntime
import com.example.cdplaya.player.equalizer.EqualizerRuntimeBridge
import kotlinx.coroutines.CoroutineScope

internal enum class PhysicalPlayerRole(
    val isAudibleAuthority: Boolean,
    val managesAudioFocus: Boolean,
    val handlesAudioBecomingNoisy: Boolean
) {
    ACTIVE(true, true, true),
    STANDBY(false, false, false)
}

/** One long-lived physical playback pipeline whose role can change without sharing audio state. */
internal class PhysicalPlayerPipeline(
    initialRole: PhysicalPlayerRole,
    val player: ExoPlayer,
    val equalizerRuntime: EqualizerDspRuntime,
    val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val audioAttributes: AudioAttributes
) {
    var role: PhysicalPlayerRole = initialRole
        private set
    var baselineVolume: Float = 1f
        private set
    var baselineMediaKey: StandbyMediaKey? = null
        private set

    private var released = false

    fun assignRole(newRole: PhysicalPlayerRole) {
        if (newRole == PhysicalPlayerRole.STANDBY) enforceSilence()
        player.setAudioAttributes(audioAttributes, newRole.managesAudioFocus)
        player.setHandleAudioBecomingNoisy(newRole.handlesAudioBecomingNoisy)
        role = newRole
    }

    fun prepareBaseline(mediaKey: StandbyMediaKey) {
        if (baselineMediaKey == mediaKey) return
        baselineMediaKey = mediaKey
        // Unity is the non-blocking fallback until an exact, key-matched ReplayGain result exists.
        // Standby remains physically muted, and the existing active-track request refines this
        // baseline after promotion if precomputation did not finish.
        baselineVolume = 1f
    }

    fun updateBaseline(mediaKey: StandbyMediaKey?, volume: Float): Boolean {
        if (mediaKey == null) return false
        if (baselineMediaKey != mediaKey) return false
        baselineVolume = volume.coerceIn(0f, 1f)
        return true
    }

    fun baselineFor(mediaKey: StandbyMediaKey?): Float =
        if (mediaKey != null && baselineMediaKey == mediaKey) {
            baselineVolume
        } else {
            1f
        }

    fun enforceSilence() {
        player.volume = 0f
        player.playWhenReady = false
    }

    fun clearForStandbyReuse() {
        enforceSilence()
        player.stop()
        player.clearMediaItems()
        baselineMediaKey = null
        baselineVolume = 1f
    }

    fun release() {
        if (released) return
        released = true
        try {
            player.release()
        } finally {
            EqualizerRuntimeBridge.releaseRuntime(equalizerRuntime)
        }
    }
}

internal data class ActivePlaylistSnapshot(
    val mediaItems: List<MediaItem>,
    val currentMediaItemIndex: Int,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean
)

internal data class StandbyMediaKey(
    val mediaId: String,
    val uri: String
)

internal data class StandbyPreparationPlan(
    val target: MediaItem,
    val playlist: List<MediaItem>,
    val startIndex: Int
) {
    val key: StandbyMediaKey
        get() = checkNotNull(StandbyTargetResolver.key(target))
}

internal object StandbyTargetResolver {
    fun resolve(snapshot: ActivePlaylistSnapshot): MediaItem? =
        resolvePlan(snapshot)?.target

    fun resolvePlan(snapshot: ActivePlaylistSnapshot): StandbyPreparationPlan? {
        if (snapshot.repeatMode == Player.REPEAT_MODE_ONE) return null
        if (snapshot.shuffleModeEnabled) return null
        if (snapshot.currentMediaItemIndex !in snapshot.mediaItems.indices) return null

        val nextIndex = when {
            snapshot.currentMediaItemIndex < snapshot.mediaItems.lastIndex ->
                snapshot.currentMediaItemIndex + 1
            snapshot.repeatMode == Player.REPEAT_MODE_ALL &&
                snapshot.mediaItems.size > 1 -> 0
            else -> return null
        }
        val current = snapshot.mediaItems[snapshot.currentMediaItemIndex]
        val candidate = snapshot.mediaItems[nextIndex]
        if (candidate.mediaId == current.mediaId || !isEligibleLocalItem(candidate)) return null

        return StandbyPreparationPlan(
            target = candidate,
            playlist = snapshot.mediaItems,
            startIndex = nextIndex
        )
    }

    fun key(mediaItem: MediaItem): StandbyMediaKey? {
        val localConfiguration = mediaItem.localConfiguration ?: return null
        return StandbyMediaKey(
            mediaId = mediaItem.mediaId,
            uri = localConfiguration.uri.toString()
        )
    }

    private fun isEligibleLocalItem(mediaItem: MediaItem): Boolean {
        val scheme = mediaItem.localConfiguration?.uri?.scheme
            ?.lowercase()
            ?: return false
        return scheme == ContentResolver.SCHEME_CONTENT ||
            scheme == ContentResolver.SCHEME_FILE
    }
}

internal interface StandbyPreparationOutput {
    fun enforceSilence()
    fun prepare(plan: StandbyPreparationPlan): Boolean
    fun clear()
}

internal enum class StandbyPreparationStatus {
    EMPTY,
    PREPARING,
    READY
}

internal data class StandbyPreparationState(
    val plan: StandbyPreparationPlan? = null,
    val status: StandbyPreparationStatus = StandbyPreparationStatus.EMPTY
) {
    val mediaItem: MediaItem?
        get() = plan?.target
    val key: StandbyMediaKey?
        get() = plan?.key
}

/** Deterministic state machine for one silent, authoritative upcoming-item preparation. */
internal class StandbyPreparationController(
    private val output: StandbyPreparationOutput
) {
    var state = StandbyPreparationState()
        private set

    private var failedTarget: StandbyMediaKey? = null

    fun synchronize(snapshot: ActivePlaylistSnapshot) {
        output.enforceSilence()
        val plan = StandbyTargetResolver.resolvePlan(snapshot)
        val targetKey = plan?.key

        if (plan == null || targetKey == null) {
            failedTarget = null
            clear()
            return
        }
        if (targetKey == failedTarget || plan == state.plan) return

        failedTarget = null
        clear()
        output.enforceSilence()
        state = StandbyPreparationState(
            plan = plan,
            status = StandbyPreparationStatus.PREPARING
        )
        if (!output.prepare(plan)) {
            failedTarget = targetKey
            output.clear()
            state = StandbyPreparationState()
        }
    }

    fun onReady() {
        if (state.status == StandbyPreparationStatus.PREPARING) {
            state = state.copy(status = StandbyPreparationStatus.READY)
        }
    }

    fun onError() {
        failedTarget = state.key
        output.clear()
        state = StandbyPreparationState()
    }

    fun consumeReady(expectedPlan: StandbyPreparationPlan): StandbyPreparationPlan? {
        if (state.status != StandbyPreparationStatus.READY) return null
        if (state.plan != expectedPlan) return null
        return state.plan.also { state = StandbyPreparationState() }
    }

    fun enforceSilence() = output.enforceSilence()

    fun release() {
        failedTarget = null
        clear()
    }

    private fun clear() {
        if (state.status != StandbyPreparationStatus.EMPTY) output.clear()
        state = StandbyPreparationState()
    }
}

internal data class AuthoritativeRoleTransition(
    val outgoingMediaItem: MediaItem?,
    val incomingMediaItem: MediaItem
)

internal interface ActivePlayerIntegration {
    fun unbind(pipeline: PhysicalPlayerPipeline)
    fun bind(
        pipeline: PhysicalPlayerPipeline,
        transition: AuthoritativeRoleTransition?
    )
}

/** Owns reusable A/B roles and performs only non-overlapping natural-boundary handoff. */
internal class DualPlayerPlaybackCoordinator(
    initialActive: PhysicalPlayerPipeline,
    initialStandby: PhysicalPlayerPipeline
) {
    var active: PhysicalPlayerPipeline = initialActive
        private set
    var standby: PhysicalPlayerPipeline = initialStandby
        private set

    private var logicalPlayer: LogicalPlayerRoleBinding? = null
    private var activeIntegration: ActivePlayerIntegration? = null
    private var released = false
    private var handoffInProgress = false
    private var activeListener: Player.Listener? = null
    private var standbyListener: Player.Listener? = null

    private val standbyPreparation = StandbyPreparationController(
        output = object : StandbyPreparationOutput {
            override fun enforceSilence() = standby.enforceSilence()

            override fun prepare(plan: StandbyPreparationPlan): Boolean {
                return try {
                    standby.enforceSilence()
                    standby.prepareBaseline(plan.key)
                    standby.player.setMediaItems(plan.playlist, plan.startIndex, 0L)
                    standby.player.prepare()
                    true
                } catch (_: RuntimeException) {
                    false
                }
            }

            override fun clear() = standby.clearForStandbyReuse()
        }
    )

    init {
        require(initialActive.role == PhysicalPlayerRole.ACTIVE)
        require(initialStandby.role == PhysicalPlayerRole.STANDBY)
        require(initialActive.player !== initialStandby.player)
        require(initialActive.equalizerRuntime !== initialStandby.equalizerRuntime)
        require(initialActive.equalizerAudioProcessor !== initialStandby.equalizerAudioProcessor)
        attachRoleListeners()
        standbyPreparation.enforceSilence()
        synchronizeStandby()
    }

    val logicalPhysicalPlayer: ExoPlayer
        get() = active.player

    val standbyPreparationState: StandbyPreparationState
        get() = standbyPreparation.state

    fun attachLogicalPlayer(
        player: LogicalPlayerRoleBinding,
        integration: ActivePlayerIntegration
    ) {
        check(logicalPlayer == null)
        logicalPlayer = player
        activeIntegration = integration
    }

    fun start(scope: CoroutineScope) {
        selectActiveTelemetry()
        EqualizerRuntimeBridge.start(active.equalizerRuntime, scope)
        EqualizerRuntimeBridge.start(standby.equalizerRuntime, scope)
    }

    fun updateActiveBaseline(volume: Float) {
        val mediaKey = active.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
            ?: return
        if (active.baselineMediaKey == null) active.prepareBaseline(mediaKey)
        active.updateBaseline(mediaKey = mediaKey, volume = volume)
    }

    fun updateStandbyBaseline(
        mediaKey: StandbyMediaKey,
        volume: Float
    ): Boolean =
        standbyPreparation.state.key == mediaKey &&
            standby.updateBaseline(mediaKey, volume)

    fun forEachPipeline(action: (PhysicalPlayerPipeline) -> Unit) {
        action(active)
        action(standby)
    }

    fun isActive(player: Player): Boolean = active.player === player

    fun synchronizeStandby() {
        if (released || handoffInProgress) return
        standbyPreparation.synchronize(activeSnapshot())
    }

    internal fun selectActiveTelemetry() {
        EqualizerRuntimeBridge.selectTelemetryRuntime(active.equalizerRuntime)
    }

    internal fun attemptNaturalHandoffForTest(): Boolean =
        attemptNaturalHandoff()

    internal fun markStandbyReadyForTest() {
        standbyPreparation.onReady()
    }

    internal fun markStandbyFailedForTest() {
        standbyPreparation.onError()
    }

    private fun attemptNaturalHandoff(): Boolean {
        if (released || handoffInProgress) return false
        val logical = logicalPlayer ?: return resumeActiveFallback()
        val integration = activeIntegration ?: return resumeActiveFallback()
        if (!logical.logicalPlayWhenReady) return false

        val expectedPlan = StandbyTargetResolver.resolvePlan(activeSnapshot())
            ?: return resumeActiveFallback()
        val incomingPlan = standbyPreparation.consumeReady(expectedPlan)
            ?: return resumeActiveFallback()
        val incomingItem = standby.player.currentMediaItem
            ?: return resumeActiveFallback()
        if (
            standby.player.playbackState != Player.STATE_READY ||
            standby.player.playerError != null ||
            StandbyTargetResolver.key(incomingItem) != incomingPlan.key
        ) {
            return resumeActiveFallback()
        }

        handoffInProgress = true
        val outgoing = active
        val incoming = standby
        val outgoingItem = outgoing.player.currentMediaItem
        val logicalIntent = logical.logicalPlayWhenReady
        return try {
            outgoing.enforceSilence()
            integration.unbind(outgoing)
            incoming.player.repeatMode = outgoing.player.repeatMode
            incoming.player.shuffleModeEnabled = outgoing.player.shuffleModeEnabled
            incoming.player.playbackParameters = outgoing.player.playbackParameters
            incoming.player.trackSelectionParameters =
                outgoing.player.trackSelectionParameters
            detachRoleListeners()
            outgoing.assignRole(PhysicalPlayerRole.STANDBY)
            incoming.assignRole(PhysicalPlayerRole.ACTIVE)
            active = incoming
            standby = outgoing
            attachRoleListeners()
            selectActiveTelemetry()

            logical.rebindPhysicalPlayer(
                newPhysicalPlayer = incoming.player,
                baselineVolume = incoming.baselineFor(incomingPlan.key),
                logicalPlayWhenReady = logicalIntent
            )
            integration.bind(
                pipeline = incoming,
                transition = AuthoritativeRoleTransition(
                    outgoingMediaItem = outgoingItem,
                    incomingMediaItem = incomingPlan.target
                )
            )
            logical.activateReboundPhysicalPlayer()
            check(incoming.player.playerError == null) {
                "Incoming physical player failed during role promotion"
            }
            outgoing.clearForStandbyReuse()
            synchronizeStandbyAfterHandoff()
            true
        } catch (_: RuntimeException) {
            runCatching { integration.unbind(incoming) }
            detachRoleListeners()
            active = outgoing
            standby = incoming
            incoming.assignRole(PhysicalPlayerRole.STANDBY)
            outgoing.assignRole(PhysicalPlayerRole.ACTIVE)
            attachRoleListeners()
            selectActiveTelemetry()
            logical.rebindPhysicalPlayer(
                newPhysicalPlayer = outgoing.player,
                baselineVolume = outgoing.baselineFor(
                    outgoing.player.currentMediaItem?.let(StandbyTargetResolver::key)
                ),
                logicalPlayWhenReady = logicalIntent
            )
            integration.bind(outgoing, transition = null)
            logical.activateReboundPhysicalPlayer()
            synchronizeStandbyAfterHandoff()
            false
        } finally {
            handoffInProgress = false
        }
    }

    private fun resumeActiveFallback(): Boolean {
        if (!released) active.player.playWhenReady = true
        return false
    }

    private fun synchronizeStandbyAfterHandoff() {
        handoffInProgress = false
        synchronizeStandby()
        handoffInProgress = true
    }

    private fun activeSnapshot() = ActivePlaylistSnapshot(
        mediaItems = List(active.player.mediaItemCount) { index ->
            active.player.getMediaItemAt(index)
        },
        currentMediaItemIndex = active.player.currentMediaItemIndex,
        repeatMode = active.player.repeatMode,
        shuffleModeEnabled = active.player.shuffleModeEnabled
    )

    private fun attachRoleListeners() {
        val activeAtBinding = active
        activeListener = object : Player.Listener {
            private fun isCurrent() = !released && active === activeAtBinding

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (isCurrent()) synchronizeStandby()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isCurrent()) synchronizeStandby()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (isCurrent()) synchronizeStandby()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (isCurrent()) synchronizeStandby()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (
                    isCurrent() &&
                    !playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    attemptNaturalHandoff()
                }
            }
        }.also(activeAtBinding.player::addListener)

        val standbyAtBinding = standby
        standbyListener = object : Player.Listener {
            private fun isCurrent() = !released && standby === standbyAtBinding

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isCurrent() && playbackState == Player.STATE_READY) {
                    standbyPreparation.onReady()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isCurrent() && playWhenReady) standbyPreparation.enforceSilence()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCurrent() && isPlaying) standbyPreparation.enforceSilence()
            }

            override fun onVolumeChanged(volume: Float) {
                if (isCurrent() && volume != 0f) standbyPreparation.enforceSilence()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isCurrent()) standbyPreparation.onError()
            }
        }.also(standbyAtBinding.player::addListener)
    }

    private fun detachRoleListeners() {
        activeListener?.let(active.player::removeListener)
        standbyListener?.let(standby.player::removeListener)
        activeListener = null
        standbyListener = null
    }

    fun release() {
        if (released) return
        released = true
        detachRoleListeners()
        activeIntegration?.unbind(active)
        standbyPreparation.release()
        try {
            standby.release()
        } finally {
            active.release()
        }
    }
}
