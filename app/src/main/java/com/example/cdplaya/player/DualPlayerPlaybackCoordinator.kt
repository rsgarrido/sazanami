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

internal fun interface StandbyBaselinePreparer {
    fun prepare(mediaItem: MediaItem, onPrepared: (Float) -> Unit): Boolean
}

private data class ActiveCrossfade(
    val outgoing: PhysicalPlayerPipeline,
    val incoming: PhysicalPlayerPipeline,
    val plan: StandbyPreparationPlan,
    val outgoingMediaItem: MediaItem?,
    var logicalHandoffComplete: Boolean = false
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
    val crossfadeEnabled: Boolean = true
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
        scheduler = crossfadeScheduler
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
        if (logicalPipeline.baselineMediaKey == null) {
            logicalPipeline.prepareBaseline(mediaKey)
        }
        logicalPipeline.updateBaseline(mediaKey = mediaKey, volume = volume)
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
        if (updated) crossfadeTransition.reevaluate()
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

    fun onLogicalCommand(command: LogicalPlaybackCommand) {
        if (released) return
        crossfadeCancelledMediaKey = active.player.currentMediaItem
            ?.let(StandbyTargetResolver::key)
        crossfadeTransition.cancel(permanent = true)
        if (command != LogicalPlaybackCommand.PLAY_PAUSE) {
            synchronizeStandby()
        }
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
        val targetIsValid = if (context != null) {
            context.plan == expectedPlan
        } else {
            expectedPlan != null &&
                preparationMatches &&
                outgoingItemKey != crossfadeCancelledMediaKey
        }
        val fullyEligible = crossfadeEnabled &&
            CrossfadeEligibility.isEligible(
                CrossfadeEligibilityInput(
                    durationMillis = duration,
                    standbyPrepared = preparationMatches,
                    targetMatches =
                        targetIsValid &&
                            expectedPlan != null &&
                            incomingKey == expectedPlan.key,
                    incomingBaselineExact = expectedPlan != null &&
                        incoming.hasExactBaselineFor(expectedPlan.key),
                    outgoingBaselineExact =
                        outgoing.hasExactBaselineFor(outgoingItemKey),
                    repeatOne =
                        outgoing.player.repeatMode == Player.REPEAT_MODE_ONE,
                    shuffleEnabled = outgoing.player.shuffleModeEnabled,
                    pipelinesValid =
                        duration != C.TIME_UNSET &&
                            logicalPlayer != null &&
                            activeIntegration != null &&
                            outgoing.player.playerError == null &&
                            incoming.player.playerError == null &&
                            incoming.player.playbackState == Player.STATE_READY,
                    cancelledByInteraction =
                        outgoingItemKey == crossfadeCancelledMediaKey
                )
            )
        return CrossfadePlaybackSnapshot(
            eligible = fullyEligible,
            durationMillis = duration,
            positionMillis = outgoing.player.currentPosition,
            outgoingProgressing =
                outgoing.player.isPlaying &&
                    outgoing.player.playbackState == Player.STATE_READY,
            incomingProgressing = context == null ||
                (
                    incoming.player.isPlaying &&
                        incoming.player.playbackState == Player.STATE_READY
                    )
        )
    }

    private fun beginCrossfade(): Boolean {
        if (released || handoffInProgress || activeCrossfade != null) return false
        val logical = logicalPlayer ?: return false
        if (!logical.logicalPlayWhenReady) return false
        val expectedPlan = StandbyTargetResolver.resolvePlan(activeSnapshot())
            ?: return false
        val incomingPlan = standbyPreparation.consumeReady(expectedPlan)
            ?: return false
        if (
            !standby.hasExactBaselineFor(incomingPlan.key) ||
            standby.player.playbackState != Player.STATE_READY ||
            standby.player.playerError != null ||
            StandbyTargetResolver.key(standby.player.currentMediaItem ?: return false) !=
            incomingPlan.key
        ) {
            return false
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
                outgoingMediaItem = outgoing.player.currentMediaItem
            )
            logical.setCrossfadeVolumeControlActive(true)
            incoming.player.playWhenReady = true
            incoming.player.playerError == null
        } catch (_: RuntimeException) {
            activeCrossfade = null
            handoffInProgress = false
            incoming.enforceSilence()
            logical.setCrossfadeVolumeControlActive(false)
            false
        }
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
    }

    private fun performCrossfadeLogicalHandoff(): Boolean {
        val context = activeCrossfade ?: return false
        if (context.logicalHandoffComplete) return true
        val logical = logicalPlayer ?: return false
        val integration = activeIntegration ?: return false
        return try {
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
                    incomingMediaItem = context.plan.target
                )
            )
            context.logicalHandoffComplete = true
            true
        } catch (_: RuntimeException) {
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
        return try {
            context.outgoing.player.volume = 0f
            context.incoming.player.volume = context.incoming.baselineVolume
            detachRoleListeners()
            context.outgoing.assignRole(PhysicalPlayerRole.STANDBY)
            context.incoming.assignRole(PhysicalPlayerRole.ACTIVE)
            active = context.incoming
            standby = context.outgoing
            logicalPipeline = context.incoming
            attachRoleListeners()
            context.outgoing.clearForStandbyReuse()
            logical.finishCrossfadeVolumeControl(context.incoming.baselineVolume)
            activeCrossfade = null
            handoffInProgress = false
            crossfadeCancelledMediaKey = null
            runCatching {
                standbyPreparation.synchronize(activeSnapshot())
                requestStandbyBaselineIfNeeded()
            }
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
                            context.plan.target
                        )
                    authoritative === context.outgoing &&
                        context.logicalHandoffComplete &&
                        context.outgoingMediaItem != null ->
                        AuthoritativeRoleTransition(
                            context.plan.target,
                            context.outgoingMediaItem
                        )
                    else -> null
                }
            )
            nonAuthoritative.clearForStandbyReuse()
            authoritative.player.volume = authoritative.baselineVolume
            logical.finishCrossfadeVolumeControl(authoritative.baselineVolume)
            attachRoleListeners()
        }
        activeCrossfade = null
        handoffInProgress = false
        runCatching {
            standbyPreparation.synchronize(activeSnapshot())
            requestStandbyBaselineIfNeeded()
        }
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
                    crossfadeTransition.cancel(permanent = true)
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
                crossfadeCancelledMediaKey = activeAtBinding.player.currentMediaItem
                    ?.let(StandbyTargetResolver::key)
                crossfadeTransition.cancel(permanent = true)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (!isCurrent()) return
                crossfadeTransition.cancel(permanent = true)
                synchronizeStandby()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (!isCurrent()) return
                crossfadeTransition.cancel(permanent = true)
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
                    resolveAsLogicallyHandedOff = incomingCanContinue
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
                        resolveAsLogicallyHandedOff = false
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
                        resolveAsLogicallyHandedOff = false
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
                    crossfadeTransition.cancel(
                        permanent = true,
                        resolveAsLogicallyHandedOff = false
                    )
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
                        resolveAsLogicallyHandedOff = false
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
