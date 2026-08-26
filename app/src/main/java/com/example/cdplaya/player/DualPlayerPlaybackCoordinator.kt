package com.example.cdplaya.player

import android.content.ContentResolver
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    var baselineExact: Boolean = false
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
        baselineExact = false
    }

    fun updateBaseline(mediaKey: StandbyMediaKey?, volume: Float): Boolean {
        if (mediaKey == null) return false
        if (baselineMediaKey != mediaKey) return false
        baselineVolume = volume.coerceIn(0f, 1f)
        baselineExact = true
        return true
    }

    fun baselineFor(mediaKey: StandbyMediaKey?): Float =
        if (mediaKey != null && baselineMediaKey == mediaKey) {
            baselineVolume
        } else {
            1f
        }

    fun hasExactBaselineFor(mediaKey: StandbyMediaKey?): Boolean =
        mediaKey != null && baselineMediaKey == mediaKey && baselineExact

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
        baselineExact = false
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

internal class HandlerCrossfadeScheduler(
    private val handler: Handler
) : CrossfadeScheduler {
    override fun schedule(
        delayMillis: Long,
        action: () -> Unit
    ): CrossfadeCancellation {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return CrossfadeCancellation { handler.removeCallbacks(runnable) }
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
    val status: StandbyPreparationStatus = StandbyPreparationStatus.EMPTY,
    val generation: Long = 0L
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
    private var nextGeneration = 0L

    fun synchronize(snapshot: ActivePlaylistSnapshot) {
        output.enforceSilence()
        val plan = StandbyTargetResolver.resolvePlan(snapshot)
        val targetKey = plan?.key

        if (plan == null || targetKey == null) {
            failedTarget = null
            if (state.status != StandbyPreparationStatus.EMPTY) {
                CrossfadeTrace.log("STANDBY CLEAR reason=no_next")
            }
            clear()
            return
        }
        if (targetKey == failedTarget || plan == state.plan) return

        failedTarget = null
        clear()
        output.enforceSilence()
        state = StandbyPreparationState(
            plan = plan,
            status = StandbyPreparationStatus.PREPARING,
            generation = ++nextGeneration
        )
        CrossfadeTrace.log(
            "STANDBY PREPARING targetMediaId=${plan.target.mediaId} " +
                "generation=${state.generation}"
        )
        if (!output.prepare(plan)) {
            CrossfadeTrace.log(
                "STANDBY FAILED targetMediaId=${plan.target.mediaId} reason=prepare_rejected"
            )
            failedTarget = targetKey
            output.clear()
            state = StandbyPreparationState()
        }
    }

    fun onReady() {
        if (state.status == StandbyPreparationStatus.PREPARING) {
            state = state.copy(status = StandbyPreparationStatus.READY)
            CrossfadeTrace.log(
                "STANDBY READY targetMediaId=${state.mediaItem?.mediaId.orEmpty()} " +
                    "generation=${state.generation}"
            )
        }
    }

    fun onError() {
        CrossfadeTrace.log(
            "STANDBY FAILED targetMediaId=${state.mediaItem?.mediaId.orEmpty()} " +
                "reason=player_error"
        )
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
    val incomingMediaItem: MediaItem,
    val affectsListeningHistory: Boolean = true
)

internal interface ActivePlayerIntegration {
    fun unbind(pipeline: PhysicalPlayerPipeline)
    fun bind(
        pipeline: PhysicalPlayerPipeline,
        transition: AuthoritativeRoleTransition?
    )

    fun onCrossfadeIncomingAudible(incomingMediaItem: MediaItem) = Unit
    fun onCrossfadeLogicalHandoff(incomingMediaItem: MediaItem) = Unit
    fun onCrossfadeCompleted(outgoingMediaItem: MediaItem?) = Unit
    fun onCrossfadeCancelled(
        outgoingMediaItem: MediaItem?,
        incomingMediaItem: MediaItem,
        survivingMediaItem: MediaItem?
    ) = Unit
}

internal fun interface StandbyBaselinePreparer {
    fun prepare(mediaItem: MediaItem, onPrepared: (Float) -> Unit): Boolean
}

internal data class CrossfadeRuntimeConfiguration(
    val enabled: Boolean,
    val durationMillis: Long,
    val preserveAlbumTransitions: Boolean
) {
    fun normalized(): CrossfadeRuntimeConfiguration = copy(
        durationMillis = durationMillis.coerceIn(
            CrossfadeTransitionCoordinator.MIN_CROSSFADE_DURATION_MILLIS,
            CrossfadeTransitionCoordinator.MAX_CROSSFADE_DURATION_MILLIS
        )
    )

    companion object {
        val DISABLED = CrossfadeRuntimeConfiguration(
            enabled = false,
            durationMillis = CrossfadeTransitionCoordinator.DEFAULT_CROSSFADE_DURATION_MILLIS,
            preserveAlbumTransitions = true
        )
        val TEST_ENABLED = DISABLED.copy(enabled = true)
    }
}

private data class ActiveCrossfade(
    val outgoing: PhysicalPlayerPipeline,
    val incoming: PhysicalPlayerPipeline,
    val plan: StandbyPreparationPlan,
    val outgoingMediaItem: MediaItem?,
    val durationMillis: Long,
    var logicalHandoffComplete: Boolean = false,
    var incomingAudibleSignalled: Boolean = false
)

private data class PendingInternalNavigationCallback(
    val mediaId: String,
    val operation: LogicalNavigationPolicyOperation,
    val value: String
)

/** Owns reusable A/B roles, optional controlled overlap, and non-overlap fallback. */
internal class DualPlayerPlaybackCoordinator(
    initialActive: PhysicalPlayerPipeline,
    initialStandby: PhysicalPlayerPipeline,
    private val standbyBaselinePreparer: StandbyBaselinePreparer =
        StandbyBaselinePreparer { _, _ -> false },
    crossfadeClock: CrossfadeClock = CrossfadeClock {
        SystemClock.elapsedRealtime()
    },
    crossfadeScheduler: CrossfadeScheduler = CrossfadeScheduler { _, _ ->
        CrossfadeCancellation {}
    },
    initialCrossfadeConfiguration: CrossfadeRuntimeConfiguration =
        CrossfadeRuntimeConfiguration.TEST_ENABLED
) {
    var active: PhysicalPlayerPipeline = initialActive
        private set
    var standby: PhysicalPlayerPipeline = initialStandby
        private set

    private var logicalPipeline: PhysicalPlayerPipeline = initialActive

    private var logicalPlayer: LogicalPlayerRoleBinding? = null
    private var activeIntegration: ActivePlayerIntegration? = null
    private var released = false
    private var handoffInProgress = false
    private var activeListener: Player.Listener? = null
    private var standbyListener: Player.Listener? = null
    private var activeCrossfade: ActiveCrossfade? = null
    private var requestedBaselineGeneration = 0L
    private var crossfadeCancelledMediaKey: StandbyMediaKey? = null
    private var crossfadeConfiguration = initialCrossfadeConfiguration.normalized()
    private var lastEligibilityTraceSignature: String? = null
    private val pendingInternalNavigationCallbacks =
        mutableListOf<PendingInternalNavigationCallback>()

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

    private val crossfadeTransition = CrossfadeTransitionCoordinator(
        output = object : CrossfadeTransitionOutput {
            override fun snapshot(): CrossfadePlaybackSnapshot =
                crossfadeSnapshot()

            override fun onCrossfadeStart(): Boolean = beginCrossfade()

            override fun onCrossfadeEnvelope(
                outgoingEnvelope: Float,
                incomingEnvelope: Float,
                progress: Float
            ) {
                applyCrossfadeEnvelope(outgoingEnvelope, incomingEnvelope)
            }

            override fun onLogicalMidpoint(): Boolean =
                performCrossfadeLogicalHandoff()

            override fun onCrossfadeComplete(): Boolean =
                completeCrossfadePromotion()

            override fun onCrossfadeCancelled(logicallyHandedOff: Boolean) {
                resolveCancelledCrossfade(logicallyHandedOff)
            }
        },
        clock = crossfadeClock,
        scheduler = crossfadeScheduler,
        durationMillis = crossfadeConfiguration.durationMillis
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
        get() = logicalPipeline.player

    val crossfadeState: CrossfadeTransitionState
        get() = crossfadeTransition.state

    val crossfadeEnabled: Boolean
        get() = crossfadeConfiguration.enabled

    val standbyPreparationState: StandbyPreparationState
        get() = standbyPreparation.state

    fun attachLogicalPlayer(
        player: LogicalPlayerRoleBinding,
        integration: ActivePlayerIntegration
    ) {
        check(logicalPlayer == null)
        logicalPlayer = player
        activeIntegration = integration
        crossfadeTransition.reevaluate()
    }

    fun start(scope: CoroutineScope) {
        selectActiveTelemetry()
        EqualizerRuntimeBridge.start(active.equalizerRuntime, scope)
        EqualizerRuntimeBridge.start(standby.equalizerRuntime, scope)
    }

    fun updateActiveBaseline(volume: Float) {
        if (activeCrossfade != null) return
        val mediaKey = logicalPipeline.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
            ?: return
        // A selected/restored item can replace the item previously owned by this physical
        // pipeline without an A/B role change. Always re-key before applying the exact result.
        logicalPipeline.prepareBaseline(mediaKey)
        logicalPipeline.updateBaseline(mediaKey = mediaKey, volume = volume)
        CrossfadeTrace.log(
            "REPLAYGAIN ACTIVE mediaId=${mediaKey.mediaId} ready=true " +
                "baseline=${logicalPipeline.baselineVolume}"
        )
        crossfadeTransition.reevaluate()
    }

    fun updateStandbyBaseline(
        mediaKey: StandbyMediaKey,
        volume: Float
    ): Boolean = updateStandbyBaseline(
        mediaKey = mediaKey,
        generation = standbyPreparation.state.generation,
        volume = volume
    )

    private fun updateStandbyBaseline(
        mediaKey: StandbyMediaKey,
        generation: Long,
        volume: Float
    ): Boolean {
        val updated =
            standbyPreparation.state.key == mediaKey &&
                standbyPreparation.state.generation == generation &&
                standby.updateBaseline(mediaKey, volume)
        if (updated) {
            CrossfadeTrace.log(
                "REPLAYGAIN STANDBY mediaId=${mediaKey.mediaId} ready=true " +
                    "baseline=${standby.baselineVolume} generation=$generation"
            )
            crossfadeTransition.reevaluate()
        } else {
            CrossfadeTrace.log(
                "REPLAYGAIN STANDBY_IGNORED mediaId=${mediaKey.mediaId} " +
                    "generation=$generation currentGeneration=${standbyPreparation.state.generation}"
            )
        }
        return updated
    }

    fun forEachPipeline(action: (PhysicalPlayerPipeline) -> Unit) {
        action(active)
        action(standby)
    }

    fun isActive(player: Player): Boolean = logicalPipeline.player === player

    fun synchronizeStandby() {
        if (released || handoffInProgress) return
        standbyPreparation.synchronize(activeSnapshot())
        requestStandbyBaselineIfNeeded()
        crossfadeTransition.reevaluate()
    }

    fun onLogicalCommand(command: LogicalPlaybackCommand) =
        onLogicalCommand(LogicalPlaybackCommandEvent(command = command))

    fun onLogicalCommand(event: LogicalPlaybackCommandEvent) {
        if (released) return
        val command = event.command
        if (
            command == LogicalPlaybackCommand.PLAYLIST_MUTATION &&
            event.origin ==
            LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL
        ) {
            CrossfadeTrace.log(
                "COMMAND PLAYLIST_MUTATION " +
                    "tx=${event.transactionId ?: "none"} " +
                    "origin=${event.origin.name} " +
                    "operation=${event.playlistOperation?.name ?: "UNKNOWN"} " +
                    "action=keep_crossfade"
            )
            return
        }
        if (
            command == LogicalPlaybackCommand.NAVIGATION_POLICY &&
            event.origin ==
            LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL
        ) {
            val operation = event.navigationOperation
            val value = event.navigationValue
            val currentMediaId = logicalPipeline.player.currentMediaItem?.mediaId
            if (operation != null && value != null && currentMediaId != null) {
                pendingInternalNavigationCallbacks +=
                    PendingInternalNavigationCallback(currentMediaId, operation, value)
            }
            val logicalMediaKey = logicalPipeline.player.currentMediaItem
                ?.let(StandbyTargetResolver::key)
            CrossfadeTrace.log(
                "COMMAND NAVIGATION_POLICY " +
                    "tx=${event.transactionId ?: "none"} " +
                    "origin=${event.origin.name} " +
                    "operation=${event.navigationOperation?.name ?: "UNKNOWN"} " +
                    "value=${event.navigationValue ?: "unknown"} " +
                    "action=keep_crossfade futureCrossfadeSuppressed=" +
                    (logicalMediaKey != null &&
                        logicalMediaKey == crossfadeCancelledMediaKey)
            )
            return
        }
        if (command == LogicalPlaybackCommand.NAVIGATION_POLICY) {
            pendingInternalNavigationCallbacks.clear()
        }
        val preservesFutureCrossfade = when (command) {
            LogicalPlaybackCommand.PLAY_PAUSE,
            LogicalPlaybackCommand.NAVIGATION_POLICY,
            LogicalPlaybackCommand.PLAYBACK_PARAMETERS -> true
            else -> false
        }
        if (!crossfadeTransition.hasStarted && preservesFutureCrossfade) {
            // CDPlaya routinely reapplies Play, Repeat, Shuffle, and playback parameters while
            // starting/restoring a track. Before overlap, these commands must update eligibility
            // rather than permanently suppressing crossfade for the whole current media item.
            CrossfadeTrace.log(
                "COMMAND ${command.name} origin=${event.origin.name} " +
                    "operation=${event.navigationOperation?.name ?: "NONE"} " +
                    "value=${event.navigationValue ?: "none"} " +
                    "action=keep_future_crossfade"
            )
            return
        }
        if (!crossfadeTransition.hasStarted && command == LogicalPlaybackCommand.SEEK) {
            // The ensuing position-discontinuity callback will reschedule from the new position.
            crossfadeCancelledMediaKey = null
            crossfadeTransition.cancel(permanent = false, traceReason = "seek_reschedule")
            CrossfadeTrace.log("COMMAND SEEK action=reschedule_after_discontinuity")
            return
        }
        if (
            !crossfadeTransition.hasStarted &&
            command == LogicalPlaybackCommand.PLAYLIST_MUTATION &&
            event.preservesCurrentMediaItem
        ) {
            crossfadeCancelledMediaKey = null
            crossfadeTransition.cancel(
                permanent = false,
                traceReason = "playlist_mutation_current_preserved"
            )
            CrossfadeTrace.log(
                "COMMAND PLAYLIST_MUTATION origin=${event.origin.name} " +
                    "operation=${event.playlistOperation?.name ?: "UNKNOWN"} " +
                    "action=reschedule_current_preserved"
            )
            return
        }
        crossfadeCancelledMediaKey = active.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
        crossfadeTransition.cancel(
            permanent = true,
            traceReason = "logical_command_${event.origin.name.lowercase()}_" +
                command.name.lowercase()
        )
        CrossfadeTrace.log(
            "COMMAND ${command.name} origin=${event.origin.name} " +
                "operation=" +
                (event.playlistOperation?.name ?:
                    event.navigationOperation?.name ?: "NONE") + " " +
                "value=${event.navigationValue ?: "none"} " +
                "action=cancel_current mediaId=" +
                crossfadeCancelledMediaKey?.mediaId.orEmpty()
        )
        if (command != LogicalPlaybackCommand.PLAY_PAUSE) {
            synchronizeStandby()
        }
    }

    fun updateCrossfadeConfiguration(configuration: CrossfadeRuntimeConfiguration) {
        if (released) return
        val normalized = configuration.normalized()
        val wasEnabled = crossfadeConfiguration.enabled
        crossfadeConfiguration = normalized
        if (!wasEnabled && normalized.enabled) {
            // Disabling during an overlap suppresses the abandoned transition for the current
            // item. A later explicit enable is a fresh policy decision for that same item.
            crossfadeCancelledMediaKey = null
        }
        CrossfadeTrace.log(
            "CONFIG enabled=${normalized.enabled} durationMs=${normalized.durationMillis} " +
                "preserveAlbumTransitions=${normalized.preserveAlbumTransitions}"
        )
        crossfadeTransition.updateDuration(normalized.durationMillis)
        if (wasEnabled && !normalized.enabled) {
            crossfadeTransition.cancel(
                permanent = true,
                traceReason = "preference_disabled"
            )
            crossfadeTransition.reset()
        } else if (!crossfadeTransition.hasStarted) {
            crossfadeTransition.reset()
        }
        synchronizeStandby()
    }

    internal fun selectActiveTelemetry() {
        selectTelemetry(logicalPipeline)
    }

    private fun selectTelemetry(pipeline: PhysicalPlayerPipeline) {
        EqualizerRuntimeBridge.selectTelemetryRuntime(pipeline.equalizerRuntime)
    }

    internal fun attemptNaturalHandoffForTest(): Boolean =
        attemptNaturalHandoff()

    internal fun markStandbyReadyForTest() {
        standbyPreparation.onReady()
    }

    internal fun markStandbyFailedForTest() {
        standbyPreparation.onError()
    }

    private fun requestStandbyBaselineIfNeeded() {
        val state = standbyPreparation.state
        val plan = state.plan ?: return
        if (state.generation == 0L || requestedBaselineGeneration == state.generation) return
        requestedBaselineGeneration = state.generation
        val generation = state.generation
        val key = plan.key
        val accepted = standbyBaselinePreparer.prepare(plan.target) { volume ->
            if (!released) {
                updateStandbyBaseline(key, generation, volume)
            }
        }
        CrossfadeTrace.log(
            "REPLAYGAIN REQUEST mediaId=${key.mediaId} generation=$generation " +
                "accepted=$accepted"
        )
        if (!accepted) requestedBaselineGeneration = 0L
    }

    private fun crossfadeSnapshot(): CrossfadePlaybackSnapshot {
        val context = activeCrossfade
        val outgoing = context?.outgoing ?: active
        val incoming = context?.incoming ?: standby
        val outgoingItemKey = outgoing.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
        val expectedPlan = if (context == null) {
            StandbyTargetResolver.resolvePlan(activeSnapshot())
        } else {
            context.plan
        }
        val preparationMatches = if (context == null) {
            standbyPreparation.state.status == StandbyPreparationStatus.READY &&
                standbyPreparation.state.plan == expectedPlan
        } else {
            true
        }
        val incomingKey = incoming.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
        val duration = outgoing.player.duration
        val position = outgoing.player.currentPosition
        val transitionDurationMillis = context?.durationMillis
            ?: crossfadeConfiguration.durationMillis
        val targetIsValid = if (context != null) {
            context.plan == expectedPlan
        } else {
            expectedPlan != null &&
                preparationMatches &&
                outgoingItemKey != crossfadeCancelledMediaKey
        }
        val albumDecision = if (context == null && expectedPlan != null) {
            NaturalAlbumTransitionPolicy.evaluate(
                    playlist = expectedPlan.playlist,
                    currentIndex = active.player.currentMediaItemIndex,
                    nextIndex = expectedPlan.startIndex
                )
        } else {
            AlbumTransitionDecision(
                preserve = false,
                reason = if (context != null) "overlap_already_active" else "no_next"
            )
        }
        val preserveNaturalAlbumTransition =
            context == null &&
                crossfadeConfiguration.preserveAlbumTransitions &&
                albumDecision.preserve
        val incomingBaselineExact = expectedPlan != null &&
            incoming.hasExactBaselineFor(expectedPlan.key)
        val outgoingBaselineExact = outgoing.hasExactBaselineFor(outgoingItemKey)
        val repeatOne = outgoing.player.repeatMode == Player.REPEAT_MODE_ONE
        val shuffleEnabled = outgoing.player.shuffleModeEnabled
        val pipelinesValid =
            duration != C.TIME_UNSET &&
                logicalPlayer != null &&
                activeIntegration != null &&
                outgoing.player.playerError == null &&
                incoming.player.playerError == null &&
                incoming.player.playbackState == Player.STATE_READY
        val cancelledByInteraction = outgoingItemKey == crossfadeCancelledMediaKey
        val targetMatches =
            targetIsValid &&
                expectedPlan != null &&
                incomingKey == expectedPlan.key
        val fullyEligible = crossfadeConfiguration.enabled &&
            CrossfadeEligibility.isEligible(
                CrossfadeEligibilityInput(
                    durationMillis = duration,
                    crossfadeDurationMillis = transitionDurationMillis,
                    standbyPrepared = preparationMatches,
                    targetMatches = targetMatches,
                    incomingBaselineExact = incomingBaselineExact,
                    outgoingBaselineExact = outgoingBaselineExact,
                    repeatOne = repeatOne,
                    shuffleEnabled = shuffleEnabled,
                    pipelinesValid = pipelinesValid,
                    cancelledByInteraction = cancelledByInteraction,
                    preserveNaturalAlbumTransition = preserveNaturalAlbumTransition
                )
            )
        val outgoingProgressing =
            outgoing.player.isPlaying &&
                outgoing.player.playbackState == Player.STATE_READY
        val remainingMillis = if (duration == C.TIME_UNSET) C.TIME_UNSET else duration - position
        val traceReason = when {
            !crossfadeConfiguration.enabled -> "disabled"
            transitionDurationMillis !in
                CrossfadeTransitionCoordinator.MIN_CROSSFADE_DURATION_MILLIS..
                CrossfadeTransitionCoordinator.MAX_CROSSFADE_DURATION_MILLIS ->
                "invalid_duration"
            duration == C.TIME_UNSET || duration <= 0L -> "invalid_duration"
            duration <= transitionDurationMillis -> "track_too_short"
            repeatOne -> "repeat_one"
            shuffleEnabled -> "shuffle_policy"
            expectedPlan == null -> "no_next"
            standbyPreparation.state.plan == null -> "standby_missing"
            standbyPreparation.state.plan != expectedPlan -> "standby_mismatch"
            standbyPreparation.state.status != StandbyPreparationStatus.READY ->
                "standby_not_ready"
            cancelledByInteraction -> "cancelled_by_interaction"
            incomingKey == null -> "standby_missing"
            incomingKey != expectedPlan.key || !targetMatches -> "standby_mismatch"
            !incomingBaselineExact || !outgoingBaselineExact -> "replaygain_not_ready"
            preserveNaturalAlbumTransition -> "preserve_album"
            !pipelinesValid -> "invalid_pipeline"
            !fullyEligible -> "unknown_eligibility_gate"
            !outgoingProgressing -> "outgoing_not_progressing"
            context == null && remainingMillis > transitionDurationMillis -> "outside_window"
            else -> "eligible"
        }
        val traceSignature = listOf(
            outgoingItemKey?.mediaId,
            expectedPlan?.target?.mediaId,
            incomingKey?.mediaId,
            traceReason,
            standbyPreparation.state.status,
            incomingBaselineExact,
            outgoingBaselineExact,
            outgoing.player.playbackState,
            incoming.player.playbackState,
            outgoing.player.playWhenReady,
            outgoing.player.isPlaying,
            incoming.player.playWhenReady,
            incoming.player.isPlaying,
            crossfadeConfiguration
        ).joinToString("|")
        if (traceSignature != lastEligibilityTraceSignature) {
            lastEligibilityTraceSignature = traceSignature
            val decision = when (traceReason) {
                "eligible" -> "ELIGIBLE"
                "outside_window" -> "WAIT"
                else -> "SKIP"
            }
            CrossfadeTrace.log(
                "$decision reason=$traceReason mediaId=${outgoingItemKey?.mediaId.orEmpty()} " +
                    "index=${outgoing.player.currentMediaItemIndex} positionMs=$position " +
                    "durationMs=$duration remainingMs=$remainingMillis playbackState=" +
                    "${outgoing.player.playbackState} playWhenReady=${outgoing.player.playWhenReady} " +
                    "isPlaying=${outgoing.player.isPlaying} targetMediaId=" +
                    "${incomingKey?.mediaId.orEmpty()} expectedNextMediaId=" +
                    "${expectedPlan?.target?.mediaId.orEmpty()} targetMatches=$targetMatches " +
                    "prepared=$preparationMatches standbyPlaybackState=" +
                    "${incoming.player.playbackState} standbyPlayWhenReady=" +
                    "${incoming.player.playWhenReady} standbyIsPlaying=${incoming.player.isPlaying} " +
                    "incomingBaselineReady=$incomingBaselineExact incomingBaseline=" +
                    "${incoming.baselineVolume} outgoingBaselineReady=$outgoingBaselineExact " +
                    "outgoingBaseline=${outgoing.baselineVolume} repeatMode=" +
                    "${outgoing.player.repeatMode} shuffleMode=$shuffleEnabled " +
                    "nextTargetAvailable=${expectedPlan != null} preservedAlbumTransition=" +
                    "$preserveNaturalAlbumTransition albumReason=${albumDecision.reason}"
            )
        }
        return CrossfadePlaybackSnapshot(
            eligible = fullyEligible,
            durationMillis = duration,
            positionMillis = position,
            outgoingProgressing = outgoingProgressing,
            incomingProgressing = context == null ||
                (
                    incoming.player.isPlaying &&
                        incoming.player.playbackState == Player.STATE_READY
                    )
        )
    }

    private fun beginCrossfade(): Boolean {
        if (released) return rejectCrossfadeStart("released")
        if (handoffInProgress) return rejectCrossfadeStart("handoff_in_progress")
        if (activeCrossfade != null) return rejectCrossfadeStart("overlap_already_active")
        val logical = logicalPlayer ?: return rejectCrossfadeStart("logical_player_missing")
        if (!logical.logicalPlayWhenReady) {
            return rejectCrossfadeStart("logical_play_intent_false")
        }
        val expectedPlan = StandbyTargetResolver.resolvePlan(activeSnapshot())
            ?: return rejectCrossfadeStart("no_next")
        val incomingPlan = standbyPreparation.consumeReady(expectedPlan)
            ?: return rejectCrossfadeStart("standby_not_ready")
        val incomingItem = standby.player.currentMediaItem
            ?: return rejectCrossfadeStart("standby_missing")
        if (
            !standby.hasExactBaselineFor(incomingPlan.key) ||
            standby.player.playbackState != Player.STATE_READY ||
            standby.player.playerError != null ||
            StandbyTargetResolver.key(incomingItem) != incomingPlan.key
        ) {
            return rejectCrossfadeStart("standby_validation_changed")
        }

        val outgoing = active
        val incoming = standby
        return try {
            handoffInProgress = true
            incoming.player.repeatMode = outgoing.player.repeatMode
            incoming.player.shuffleModeEnabled = outgoing.player.shuffleModeEnabled
            incoming.player.playbackParameters = outgoing.player.playbackParameters
            incoming.player.trackSelectionParameters =
                outgoing.player.trackSelectionParameters
            incoming.player.volume = 0f
            activeCrossfade = ActiveCrossfade(
                outgoing = outgoing,
                incoming = incoming,
                plan = incomingPlan,
                outgoingMediaItem = outgoing.player.currentMediaItem,
                durationMillis = crossfadeTransition.currentDurationMillis
            )
            logical.setCrossfadeVolumeControlActive(true)
            incoming.player.playWhenReady = true
            incoming.player.playerError == null
        } catch (_: RuntimeException) {
            activeCrossfade = null
            handoffInProgress = false
            incoming.enforceSilence()
            logical.setCrossfadeVolumeControlActive(false)
            rejectCrossfadeStart("runtime_exception")
        }
    }

    private fun rejectCrossfadeStart(reason: String): Boolean {
        CrossfadeTrace.log("FALLBACK reason=$reason")
        return false
    }

    private fun applyCrossfadeEnvelope(
        outgoingEnvelope: Float,
        incomingEnvelope: Float
    ) {
        val context = activeCrossfade ?: return
        context.outgoing.player.volume =
            context.outgoing.baselineVolume * outgoingEnvelope
        context.incoming.player.volume =
            context.incoming.baselineVolume * incomingEnvelope
        if (incomingEnvelope > 0f && !context.incomingAudibleSignalled) {
            context.incomingAudibleSignalled = true
            activeIntegration?.onCrossfadeIncomingAudible(context.plan.target)
        }
    }

    private fun performCrossfadeLogicalHandoff(): Boolean {
        val context = activeCrossfade ?: return false
        if (context.logicalHandoffComplete) return true
        val logical = logicalPlayer ?: return false
        val integration = activeIntegration ?: return false
        return try {
            logical.beginCrossfadeHandoffPlaylistSync(
                currentMediaId = context.plan.target.mediaId,
                expectedUpcomingMediaIds = context.plan.playlist
                    .drop(context.plan.startIndex + 1)
                    .map { item -> item.mediaId }
            )
            logical.beginCrossfadeHandoffNavigationSync(
                currentMediaId = context.plan.target.mediaId
            )
            integration.unbind(context.outgoing)
            logicalPipeline = context.incoming
            logical.rebindPhysicalPlayerForCrossfade(
                newPhysicalPlayer = context.incoming.player,
                baselineVolume = context.incoming.baselineVolume,
                logicalPlayWhenReady = true
            )
            selectTelemetry(context.incoming)
            integration.bind(
                context.incoming,
                AuthoritativeRoleTransition(
                    outgoingMediaItem = context.outgoingMediaItem,
                    incomingMediaItem = context.plan.target,
                    affectsListeningHistory = false
                )
            )
            integration.onCrossfadeLogicalHandoff(context.plan.target)
            context.logicalHandoffComplete = true
            true
        } catch (_: RuntimeException) {
            logical.endCrossfadeHandoffPlaylistSync()
            logical.endCrossfadeHandoffNavigationSync()
            runCatching { integration.unbind(context.incoming) }
            logicalPipeline = context.outgoing
            runCatching {
                logical.rebindPhysicalPlayerForCrossfade(
                    context.outgoing.player,
                    context.outgoing.baselineVolume,
                    logicalPlayWhenReady = true
                )
                selectTelemetry(context.outgoing)
                integration.bind(context.outgoing, transition = null)
            }
            false
        }
    }

    private fun completeCrossfadePromotion(): Boolean {
        val context = activeCrossfade ?: return false
        val logical = logicalPlayer ?: return false
        val integration = activeIntegration ?: return false
        return try {
            context.outgoing.player.volume = 0f
            context.incoming.player.volume = context.incoming.baselineVolume
            integration.onCrossfadeCompleted(context.outgoingMediaItem)
            detachRoleListeners()
            context.outgoing.assignRole(PhysicalPlayerRole.STANDBY)
            context.incoming.assignRole(PhysicalPlayerRole.ACTIVE)
            active = context.incoming
            standby = context.outgoing
            logicalPipeline = context.incoming
            attachRoleListeners()
            context.outgoing.clearForStandbyReuse()
            logical.finishCrossfadeVolumeControl(context.incoming.baselineVolume)
            logical.completeCrossfadeHandoffPlaylistSync()
            logical.completeCrossfadeHandoffNavigationSync()
            activeCrossfade = null
            handoffInProgress = false
            crossfadeCancelledMediaKey = null
            runCatching {
                standbyPreparation.synchronize(activeSnapshot())
                requestStandbyBaselineIfNeeded()
            }
            traceNewActive()
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun resolveCancelledCrossfade(logicallyHandedOff: Boolean) {
        val context = activeCrossfade ?: return
        val logical = logicalPlayer ?: return
        val integration = activeIntegration ?: return
        crossfadeCancelledMediaKey = context.outgoing.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
        val authoritative = if (logicallyHandedOff) {
            context.incoming
        } else {
            context.outgoing
        }
        val nonAuthoritative = if (authoritative === context.incoming) {
            context.outgoing
        } else {
            context.incoming
        }

        runCatching {
            integration.unbind(logicalPipeline)
            detachRoleListeners()
            nonAuthoritative.enforceSilence()
            runCatching {
                integration.onCrossfadeCancelled(
                    outgoingMediaItem = context.outgoingMediaItem,
                    incomingMediaItem = context.plan.target,
                    survivingMediaItem = authoritative.player.currentMediaItem
                )
            }
            nonAuthoritative.assignRole(PhysicalPlayerRole.STANDBY)
            authoritative.assignRole(PhysicalPlayerRole.ACTIVE)
            active = authoritative
            standby = nonAuthoritative
            logicalPipeline = authoritative
            logical.rebindPhysicalPlayerForCrossfade(
                authoritative.player,
                authoritative.baselineVolume,
                logicalPlayWhenReady = logical.logicalPlayWhenReady
            )
            selectTelemetry(authoritative)
            integration.bind(
                authoritative,
                when {
                    authoritative === context.incoming &&
                        !context.logicalHandoffComplete ->
                        AuthoritativeRoleTransition(
                            context.outgoingMediaItem,
                            context.plan.target,
                            affectsListeningHistory = false
                        )
                    authoritative === context.outgoing &&
                        context.logicalHandoffComplete &&
                        context.outgoingMediaItem != null ->
                        AuthoritativeRoleTransition(
                            context.plan.target,
                            context.outgoingMediaItem,
                            affectsListeningHistory = false
                        )
                    else -> null
                }
            )
            nonAuthoritative.clearForStandbyReuse()
            authoritative.player.volume = authoritative.baselineVolume
            logical.finishCrossfadeVolumeControl(authoritative.baselineVolume)
            attachRoleListeners()
        }
        logical.endCrossfadeHandoffPlaylistSync()
        logical.endCrossfadeHandoffNavigationSync()
        activeCrossfade = null
        handoffInProgress = false
        runCatching {
            standbyPreparation.synchronize(activeSnapshot())
            requestStandbyBaselineIfNeeded()
        }
        traceNewActive()
    }

    private fun traceNewActive() {
        val key = active.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
        CrossfadeTrace.log(
            "NEW_ACTIVE mediaId=${key?.mediaId.orEmpty()} " +
                "futureCrossfadeSuppressed=${key != null && key == crossfadeCancelledMediaKey}"
        )
    }

    private fun consumeInternalNavigationCallback(
        mediaId: String?,
        operation: LogicalNavigationPolicyOperation,
        value: String
    ): Boolean {
        val index = pendingInternalNavigationCallbacks.indexOfFirst { pending ->
            pending.mediaId == mediaId &&
                pending.operation == operation &&
                pending.value == value
        }
        if (index < 0) {
            pendingInternalNavigationCallbacks.clear()
            return false
        }
        pendingInternalNavigationCallbacks.removeAt(index)
        CrossfadeTrace.log(
            "NAV_POLICY CALLBACK origin=CROSSFADE_HANDOFF_INTERNAL " +
                "operation=${operation.name} value=$value action=keep_crossfade"
        )
        return true
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
            logicalPipeline = incoming
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
            crossfadeCancelledMediaKey = null
            crossfadeTransition.reset()
            synchronizeStandbyAfterHandoff()
            true
        } catch (_: RuntimeException) {
            runCatching { integration.unbind(incoming) }
            detachRoleListeners()
            active = outgoing
            standby = incoming
            logicalPipeline = outgoing
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
                if (!isCurrent()) return
                if (activeCrossfade != null) {
                    crossfadeTransition.reevaluate()
                } else {
                    synchronizeStandby()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!isCurrent()) return
                if (activeCrossfade != null) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "active_media_item_transition"
                    )
                } else {
                    crossfadeCancelledMediaKey = null
                    crossfadeTransition.reset()
                    synchronizeStandby()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!isCurrent()) return
                val currentKey = activeAtBinding.player.currentMediaItem
                    ?.let(StandbyTargetResolver::key)
                if (activeCrossfade != null) {
                    crossfadeCancelledMediaKey = currentKey
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "position_discontinuity_$reason"
                    )
                } else if (currentKey == crossfadeCancelledMediaKey) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "cancelled_item_discontinuity_$reason"
                    )
                } else {
                    crossfadeCancelledMediaKey = null
                    crossfadeTransition.reset()
                    CrossfadeTrace.log(
                        "RESCHEDULE reason=position_discontinuity_$reason mediaId=" +
                            currentKey?.mediaId.orEmpty()
                    )
                    synchronizeStandby()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (!isCurrent()) return
                if (
                    consumeInternalNavigationCallback(
                        activeAtBinding.player.currentMediaItem?.mediaId,
                        LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
                        navigationRepeatModeTraceValue(repeatMode)
                    )
                ) {
                    crossfadeTransition.reevaluate()
                    return
                }
                val currentKey = activeAtBinding.player.currentMediaItem
                    ?.let(StandbyTargetResolver::key)
                if (activeCrossfade != null) {
                    crossfadeCancelledMediaKey = currentKey
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "repeat_mode_changed_during_overlap"
                    )
                } else if (currentKey == crossfadeCancelledMediaKey) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "repeat_mode_changed_after_overlap_cancel"
                    )
                } else {
                    crossfadeCancelledMediaKey = null
                    crossfadeTransition.reset()
                }
                synchronizeStandby()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (!isCurrent()) return
                if (
                    consumeInternalNavigationCallback(
                        activeAtBinding.player.currentMediaItem?.mediaId,
                        LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
                        shuffleModeEnabled.toString()
                    )
                ) {
                    crossfadeTransition.reevaluate()
                    return
                }
                val currentKey = activeAtBinding.player.currentMediaItem
                    ?.let(StandbyTargetResolver::key)
                if (activeCrossfade != null) {
                    crossfadeCancelledMediaKey = currentKey
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "shuffle_mode_changed_during_overlap"
                    )
                } else if (currentKey == crossfadeCancelledMediaKey) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        traceReason = "shuffle_mode_changed_after_overlap_cancel"
                    )
                } else {
                    crossfadeCancelledMediaKey = null
                    crossfadeTransition.reset()
                }
                synchronizeStandby()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isCurrent()) crossfadeTransition.reevaluate()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCurrent()) crossfadeTransition.reevaluate()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (
                    isCurrent() &&
                    !playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    if (!crossfadeTransition.completeAtNaturalEnd()) {
                        CrossfadeTrace.log("FALLBACK reason=crossfade_not_active_at_eos")
                        attemptNaturalHandoff()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isCurrent()) return
                val incomingCanContinue = activeCrossfade?.incoming?.player
                    ?.let { player ->
                        player.playbackState == Player.STATE_READY &&
                            player.playerError == null
                    } == true
                crossfadeTransition.cancel(
                    permanent = true,
                    resolveAsLogicallyHandedOff = incomingCanContinue,
                    traceReason = "outgoing_player_error"
                )
            }
        }.also(activeAtBinding.player::addListener)

        val standbyAtBinding = standby
        standbyListener = object : Player.Listener {
            private fun isCurrent() = !released && standby === standbyAtBinding

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!isCurrent()) return
                val context = activeCrossfade
                if (
                    context?.incoming === standbyAtBinding &&
                    mediaItem?.let(StandbyTargetResolver::key) != context.plan.key
                ) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        resolveAsLogicallyHandedOff = false,
                        traceReason = "standby_target_changed"
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCurrent()) return
                if (playbackState == Player.STATE_READY) {
                    standbyPreparation.onReady()
                    requestStandbyBaselineIfNeeded()
                } else if (activeCrossfade?.incoming === standbyAtBinding) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        resolveAsLogicallyHandedOff = false,
                        traceReason = "incoming_not_ready"
                    )
                    return
                }
                crossfadeTransition.reevaluate()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!isCurrent()) return
                if (activeCrossfade?.incoming !== standbyAtBinding && playWhenReady) {
                    standbyPreparation.enforceSilence()
                }
                crossfadeTransition.reevaluate()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isCurrent()) return
                if (activeCrossfade?.incoming !== standbyAtBinding && isPlaying) {
                    standbyPreparation.enforceSilence()
                } else if (
                    activeCrossfade?.incoming === standbyAtBinding &&
                    !isPlaying
                ) {
                    // A READY standby can briefly remain non-playing after playWhenReady is
                    // enabled. Let the transition state machine apply its bounded startup
                    // grace period instead of collapsing every real-device overlap here.
                    crossfadeTransition.reevaluate()
                    return
                }
                crossfadeTransition.reevaluate()
            }

            override fun onVolumeChanged(volume: Float) {
                if (
                    isCurrent() &&
                    activeCrossfade?.incoming !== standbyAtBinding &&
                    volume != 0f
                ) {
                    standbyPreparation.enforceSilence()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isCurrent()) return
                if (activeCrossfade?.incoming === standbyAtBinding) {
                    crossfadeTransition.cancel(
                        permanent = true,
                        resolveAsLogicallyHandedOff = false,
                        traceReason = "incoming_player_error"
                    )
                } else {
                    standbyPreparation.onError()
                }
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
        logicalPlayer?.endCrossfadeHandoffPlaylistSync()
        logicalPlayer?.endCrossfadeHandoffNavigationSync()
        pendingInternalNavigationCallbacks.clear()
        crossfadeTransition.release()
        detachRoleListeners()
        activeIntegration?.unbind(logicalPipeline)
        active.enforceSilence()
        standby.enforceSilence()
        standbyPreparation.release()
        try {
            standby.release()
        } finally {
            active.release()
        }
    }
}
