package com.example.cdplaya.player

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal enum class LogicalPlaybackCommand {
    PLAY_PAUSE,
    STOP,
    SEEK,
    PLAYLIST_MUTATION,
    NAVIGATION_POLICY,
    PLAYBACK_PARAMETERS
}

internal enum class LogicalPlaybackCommandOrigin {
    EXTERNAL,
    CROSSFADE_HANDOFF_INTERNAL
}

internal data class LogicalPlaybackCommandEvent(
    val command: LogicalPlaybackCommand,
    val origin: LogicalPlaybackCommandOrigin =
        LogicalPlaybackCommandOrigin.EXTERNAL,
    val preservesCurrentMediaItem: Boolean = false,
    val transactionId: Long? = null,
    val playlistOperation: LogicalPlaylistMutationOperation? = null,
    val navigationOperation: LogicalNavigationPolicyOperation? = null,
    val navigationValue: String? = null
)

internal data class SmoothPlaybackMediaIdentity(
    val mediaId: String,
    val uri: String
)

/** Logical command policy for session-local, identity-scoped cosmetic Pause/Resume. */
internal class SmoothPlayPauseResumePolicy {
    private var resumableIdentity: SmoothPlaybackMediaIdentity? = null

    fun onExplicitPause(
        identity: SmoothPlaybackMediaIdentity?,
        canArmSmoothResume: Boolean
    ) {
        resumableIdentity = identity.takeIf { canArmSmoothResume }
    }

    fun onExplicitPlay(identity: SmoothPlaybackMediaIdentity?): Boolean {
        val matches = identity != null && identity == resumableIdentity
        resumableIdentity = null
        return matches
    }

    fun onNewPlaybackAttempt() {
        resumableIdentity = null
    }
}

internal fun MediaItem?.smoothPlaybackIdentity(): SmoothPlaybackMediaIdentity? {
    val item = this ?: return null
    val uri = item.localConfiguration?.uri?.toString().orEmpty()
    if (item.mediaId.isBlank() && uri.isBlank()) return null
    return SmoothPlaybackMediaIdentity(item.mediaId, uri)
}

internal class CrossfadeHandoffPlaylistMutationClassifier {
    private data class HandoffScope(
        val currentMediaId: String,
        var sourceTransactionId: Long? = null,
        var crossfadeCompleted: Boolean = false
    )

    private var handoffScope: HandoffScope? = null

    fun begin(currentMediaId: String) {
        handoffScope = HandoffScope(currentMediaId)
    }

    fun transactionId(): Long? = handoffScope?.sourceTransactionId

    fun accept(
        currentMediaId: String?,
        claim: ClaimedInternalPlaylistMutation
    ): Boolean {
        val scope = handoffScope ?: return false
        if (
            currentMediaId != scope.currentMediaId ||
            claim.currentMediaId != scope.currentMediaId
        ) {
            return false
        }
        val existingId = scope.sourceTransactionId
        if (existingId != null && existingId != claim.transactionId) return false
        scope.sourceTransactionId = claim.transactionId
        return true
    }

    fun markCrossfadeCompleted() {
        val scope = handoffScope ?: return
        scope.crossfadeCompleted = true
        if (scope.sourceTransactionId == null) {
            scope.sourceTransactionId =
                LogicalPlaylistMutationTransactions.activeTransactionIdFor(
                    scope.currentMediaId
                )
        }
    }

    fun shouldCloseAfterClaim(): Boolean {
        val scope = handoffScope ?: return true
        val transactionId = scope.sourceTransactionId ?: return false
        return scope.crossfadeCompleted &&
            !LogicalPlaylistMutationTransactions.isActive(transactionId)
    }

    fun shouldCloseAtCrossfadeCompletion(): Boolean {
        val scope = handoffScope ?: return true
        val transactionId = scope.sourceTransactionId ?: return true
        return !LogicalPlaylistMutationTransactions.isActive(transactionId)
    }

    fun end(abortSourceTransaction: Boolean, reason: String): Long? {
        val scope = handoffScope
        val transactionId = scope?.sourceTransactionId ?:
            scope?.currentMediaId?.let(
                LogicalPlaylistMutationTransactions::activeTransactionIdFor
            )
        handoffScope = null
        if (abortSourceTransaction && transactionId != null) {
            LogicalPlaylistMutationTransactions.abort(transactionId, reason)
        }
        return transactionId
    }
}

internal class CrossfadeHandoffNavigationPolicyClassifier {
    private data class HandoffScope(
        val currentMediaId: String,
        val sourceTransactionIds: MutableSet<Long> = linkedSetOf(),
        var crossfadeCompleted: Boolean = false
    )

    private var handoffScope: HandoffScope? = null

    fun begin(currentMediaId: String) {
        handoffScope = HandoffScope(currentMediaId)
    }

    fun transactionId(): Long? =
        handoffScope?.sourceTransactionIds?.firstOrNull()

    fun accept(
        currentMediaId: String?,
        claim: ClaimedInternalNavigationPolicyCommand
    ): Boolean {
        val scope = handoffScope ?: return false
        if (
            currentMediaId != scope.currentMediaId ||
            claim.currentMediaId != scope.currentMediaId
        ) {
            return false
        }
        scope.sourceTransactionIds += claim.transactionId
        return true
    }

    fun markCrossfadeCompleted() {
        val scope = handoffScope ?: return
        scope.crossfadeCompleted = true
        scope.sourceTransactionIds +=
            LogicalNavigationPolicyTransactions.activeTransactionIdsFor(
                scope.currentMediaId
            )
    }

    fun shouldCloseAfterClaim(): Boolean {
        val scope = handoffScope ?: return true
        if (scope.sourceTransactionIds.isEmpty()) return false
        return scope.crossfadeCompleted &&
            scope.sourceTransactionIds.none(
                LogicalNavigationPolicyTransactions::isActive
            )
    }

    fun shouldCloseAtCrossfadeCompletion(): Boolean {
        val scope = handoffScope ?: return true
        if (scope.sourceTransactionIds.isEmpty()) return true
        return scope.sourceTransactionIds.none(
            LogicalNavigationPolicyTransactions::isActive
        )
    }

    fun end(abortSourceTransaction: Boolean, reason: String): Long? {
        val scope = handoffScope
        val transactionIds = linkedSetOf<Long>()
        scope?.sourceTransactionIds?.let(transactionIds::addAll)
        scope?.currentMediaId?.let { currentMediaId ->
            transactionIds +=
                LogicalNavigationPolicyTransactions.activeTransactionIdsFor(
                    currentMediaId
                )
        }
        handoffScope = null
        if (abortSourceTransaction) {
            transactionIds.forEach { transactionId ->
                LogicalNavigationPolicyTransactions.abort(transactionId, reason)
            }
        }
        return transactionIds.firstOrNull()
    }
}

/** Narrow role-switch contract so physical A/B authority is testable without replacing Player. */
internal interface LogicalPlayerRoleBinding {
    val logicalPlayWhenReady: Boolean

    fun rebindPhysicalPlayer(
        newPhysicalPlayer: Player,
        baselineVolume: Float,
        logicalPlayWhenReady: Boolean
    )

    fun rebindPhysicalPlayerForCrossfade(
        newPhysicalPlayer: Player,
        baselineVolume: Float,
        logicalPlayWhenReady: Boolean
    )

    fun setCrossfadeVolumeControlActive(active: Boolean)
    fun finishCrossfadeVolumeControl(baselineVolume: Float)

    fun beginCrossfadeHandoffPlaylistSync(
        currentMediaId: String,
        expectedUpcomingMediaIds: List<String>
    ) = Unit

    fun endCrossfadeHandoffPlaylistSync() = Unit

    fun completeCrossfadeHandoffPlaylistSync() =
        endCrossfadeHandoffPlaylistSync()

    fun beginCrossfadeHandoffNavigationSync(currentMediaId: String) = Unit
    fun endCrossfadeHandoffNavigationSync() = Unit
    fun completeCrossfadeHandoffNavigationSync() =
        endCrossfadeHandoffNavigationSync()

    fun activateReboundPhysicalPlayer()
}

/** The single logical Player exposed by PlaybackService to MediaLibrarySession. */
@OptIn(UnstableApi::class)
internal class SmoothPlaybackPlayer(
    initialPhysicalPlayer: Player,
    private val onBaselineVolumeChanged: (Float) -> Unit = {},
    private val onLogicalCommand: (LogicalPlaybackCommandEvent) -> Unit = {},
    clock: PlaybackTransitionClock = PlaybackTransitionClock {
        SystemClock.elapsedRealtime()
    },
    scheduler: PlaybackTransitionScheduler = HandlerPlaybackTransitionScheduler(
        Handler(initialPhysicalPlayer.applicationLooper)
    )
) : ForwardingSimpleBasePlayer(initialPhysicalPlayer), LogicalPlayerRoleBinding {

    private var physicalPlayer: Player = initialPhysicalPlayer

    private var logicalPlayWhenReadyChangeReason =
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var logicalUnmuteVolume =
        initialPhysicalPlayer.volume.takeIf { it > 0f } ?: 1f
    private var releasedTransitionResources = false
    private var crossfadeControlsPhysicalVolume = false
    private val smoothResumePolicy = SmoothPlayPauseResumePolicy()
    private val handoffPlaylistMutationClassifier =
        CrossfadeHandoffPlaylistMutationClassifier()
    private val handoffNavigationPolicyClassifier =
        CrossfadeHandoffNavigationPolicyClassifier()
    private var physicalListener = createPhysicalListener(initialPhysicalPlayer)
    private val transitionCoordinator = SmoothPlaybackTransitionCoordinator(
        output = object : SmoothPlaybackTransitionOutput {
            override fun setPhysicalPlayWhenReady(playWhenReady: Boolean) {
                physicalPlayer.playWhenReady = playWhenReady
            }

            override fun setEffectiveVolume(volume: Float) {
                physicalPlayer.volume = volume
            }
        },
        clock = clock,
        scheduler = scheduler,
        initialPhysicalPlayWhenReady = initialPhysicalPlayer.playWhenReady,
        initialAudible = initialPhysicalPlayer.isPlaying,
        initialBaselineVolume = initialPhysicalPlayer.volume
    )

    init {
        initialPhysicalPlayer.addListener(physicalListener)
    }

    override val logicalPlayWhenReady: Boolean
        get() = transitionCoordinator.logicalPlayWhenReady

    override fun getState(): SimpleBasePlayer.State {
        return super.getState()
            .buildUpon()
            .setPlayWhenReady(
                transitionCoordinator.logicalPlayWhenReady,
                logicalPlayWhenReadyChangeReason
            )
            .setVolume(transitionCoordinator.baselineVolume)
            .setUnmuteVolume(logicalUnmuteVolume)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        emitLogicalCommand(LogicalPlaybackCommand.PLAY_PAUSE)
        logicalPlayWhenReadyChangeReason =
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        if (playWhenReady) {
            transitionCoordinator.requestPlay(
                smoothResume = smoothResumePolicy.onExplicitPlay(
                    physicalPlayer.currentMediaItem.smoothPlaybackIdentity()
                )
            )
        } else {
            smoothResumePolicy.onExplicitPause(
                identity = physicalPlayer.currentMediaItem.smoothPlaybackIdentity(),
                canArmSmoothResume = transitionCoordinator.canArmSmoothResume
            )
            transitionCoordinator.requestPause()
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(
        volume: Float,
        volumeOperationType: @C.VolumeOperationType Int
    ): ListenableFuture<*> {
        if (volume > 0f) {
            logicalUnmuteVolume = volume
        } else if (
            volumeOperationType == C.VOLUME_OPERATION_TYPE_SET_VOLUME &&
            transitionCoordinator.baselineVolume > 0f
        ) {
            logicalUnmuteVolume = transitionCoordinator.baselineVolume
        }
        transitionCoordinator.setBaselineVolume(
            volume = volume,
            applyPhysicalVolume = !crossfadeControlsPhysicalVolume
        )
        onBaselineVolumeChanged(volume)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        emitLogicalCommand(LogicalPlaybackCommand.STOP)
        smoothResumePolicy.onNewPlaybackAttempt()
        transitionCoordinator.onImmediateStop()
        return super.handleStop()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: @Player.Command Int
    ): ListenableFuture<*> {
        emitLogicalCommand(LogicalPlaybackCommand.SEEK)
        val isSeekWithinCurrentAttempt =
            mediaItemIndex == physicalPlayer.currentMediaItemIndex &&
                seekCommand == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
        if (!isSeekWithinCurrentAttempt) {
            invalidateSmoothResumeForNewPlaybackAttempt()
        }
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        invalidateSmoothResumeForNewPlaybackAttempt()
        closeHandoffPlaylistTransaction("external_set_media_items")
        LogicalPlaylistMutationTransactions.abortAll("external_set_media_items")
        emitPlaylistMutation(preservesCurrentMediaItem = false)
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        closeHandoffPlaylistTransaction("external_add_media_items")
        LogicalPlaylistMutationTransactions.abortAll("external_add_media_items")
        emitPlaylistMutation(
            preservesCurrentMediaItem =
                physicalPlayer.currentMediaItemIndex >= 0
        )
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> {
        closeHandoffPlaylistTransaction("external_move_media_items")
        LogicalPlaylistMutationTransactions.abortAll("external_move_media_items")
        emitPlaylistMutation(
            preservesCurrentMediaItem =
                physicalPlayer.currentMediaItemIndex >= 0
        )
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        val currentIndex = physicalPlayer.currentMediaItemIndex
        if (currentIndex in fromIndex until toIndex) {
            invalidateSmoothResumeForNewPlaybackAttempt()
        }
        val claim = LogicalPlaylistMutationTransactions.claimReplaceUpcoming(
            currentMediaId = physicalPlayer.currentMediaItem?.mediaId,
            fromIndex = fromIndex,
            toIndex = toIndex,
            mediaIds = mediaItems.map { item -> item.mediaId }
        )
        val internal = claim != null &&
            handoffPlaylistMutationClassifier.accept(
                currentMediaId = physicalPlayer.currentMediaItem?.mediaId,
                claim = claim
            )
        if (!internal) {
            claim?.let { matched ->
                LogicalPlaylistMutationTransactions.abort(
                    matched.transactionId,
                    "handoff_identity_mismatch"
                )
            }
            closeHandoffPlaylistTransaction("external_replace_media_items")
        }
        emitPlaylistMutation(
            preservesCurrentMediaItem = replacementPreservesCurrentMediaItem(
                fromIndex = fromIndex,
                toIndex = toIndex,
                mediaItems = mediaItems
            ),
            internalClaim = claim.takeIf { internal }
        )
        if (internal) closeCompletedHandoffTransactionAfterClaim()
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int
    ): ListenableFuture<*> {
        val currentIndex = physicalPlayer.currentMediaItemIndex
        if (currentIndex in fromIndex until toIndex) {
            invalidateSmoothResumeForNewPlaybackAttempt()
        }
        val claim = LogicalPlaylistMutationTransactions.claimRemovePrefix(
            currentMediaId = physicalPlayer.currentMediaItem?.mediaId,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
        val internal = claim != null &&
            handoffPlaylistMutationClassifier.accept(
                currentMediaId = physicalPlayer.currentMediaItem?.mediaId,
                claim = claim
            )
        if (!internal) {
            claim?.let { matched ->
                LogicalPlaylistMutationTransactions.abort(
                    matched.transactionId,
                    "handoff_identity_mismatch"
                )
            }
            closeHandoffPlaylistTransaction("external_remove_media_items")
        }
        emitPlaylistMutation(
            preservesCurrentMediaItem = currentIndex >= 0 &&
                currentIndex !in fromIndex until toIndex,
            internalClaim = claim.takeIf { internal }
        )
        if (internal) closeCompletedHandoffTransactionAfterClaim()
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        val claim = LogicalNavigationPolicyTransactions.claimRepeatMode(
            currentMediaId = physicalPlayer.currentMediaItem?.mediaId,
            repeatMode = repeatMode
        )
        val internal = acceptSourceOwnedNavigationClaim(claim)
        if (!internal) {
            claim?.let { matched ->
                LogicalNavigationPolicyTransactions.abort(
                    matched.transactionId,
                    "handoff_identity_mismatch"
                )
            }
            closeHandoffNavigationTransaction("external_set_repeat_mode")
        }
        emitNavigationPolicyCommand(
            claim = claim.takeIf { internal },
            operation = LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
            value = navigationRepeatModeTraceValue(repeatMode)
        )
        if (internal) closeCompletedNavigationTransactionAfterClaim()
        return super.handleSetRepeatMode(repeatMode)
    }

    override fun handleSetShuffleModeEnabled(
        shuffleModeEnabled: Boolean
    ): ListenableFuture<*> {
        val claim = LogicalNavigationPolicyTransactions.claimShuffleMode(
            currentMediaId = physicalPlayer.currentMediaItem?.mediaId,
            enabled = shuffleModeEnabled
        )
        val internal = acceptSourceOwnedNavigationClaim(claim)
        if (!internal) {
            claim?.let { matched ->
                LogicalNavigationPolicyTransactions.abort(
                    matched.transactionId,
                    "handoff_identity_mismatch"
                )
            }
            closeHandoffNavigationTransaction("external_set_shuffle_mode")
        }
        emitNavigationPolicyCommand(
            claim = claim.takeIf { internal },
            operation = LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
            value = shuffleModeEnabled.toString()
        )
        if (internal) closeCompletedNavigationTransactionAfterClaim()
        return super.handleSetShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): ListenableFuture<*> {
        emitLogicalCommand(LogicalPlaybackCommand.PLAYBACK_PARAMETERS)
        return super.handleSetPlaybackParameters(playbackParameters)
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseTransitionResources()
        return super.handleRelease()
    }

    fun setSmoothPlaybackEnabled(enabled: Boolean) {
        if (!enabled) smoothResumePolicy.onNewPlaybackAttempt()
        transitionCoordinator.setEnabled(enabled)
        invalidateState()
    }

    fun releaseTransitionResources() {
        if (releasedTransitionResources) return
        releasedTransitionResources = true
        smoothResumePolicy.onNewPlaybackAttempt()
        closeHandoffPlaylistTransaction("logical_player_release")
        LogicalPlaylistMutationTransactions.abortAll("logical_player_release")
        closeHandoffNavigationTransaction("logical_player_release")
        LogicalNavigationPolicyTransactions.abortAll("logical_player_release")
        physicalPlayer.removeListener(physicalListener)
        transitionCoordinator.release()
    }

    override fun rebindPhysicalPlayer(
        newPhysicalPlayer: Player,
        baselineVolume: Float,
        logicalPlayWhenReady: Boolean
    ) = rebindPhysicalPlayerInternal(
        newPhysicalPlayer = newPhysicalPlayer,
        baselineVolume = baselineVolume,
        logicalPlayWhenReady = logicalPlayWhenReady,
        applyPhysicalVolume = true
    )

    override fun rebindPhysicalPlayerForCrossfade(
        newPhysicalPlayer: Player,
        baselineVolume: Float,
        logicalPlayWhenReady: Boolean
    ) = rebindPhysicalPlayerInternal(
        newPhysicalPlayer = newPhysicalPlayer,
        baselineVolume = baselineVolume,
        logicalPlayWhenReady = logicalPlayWhenReady,
        applyPhysicalVolume = false
    )

    private fun rebindPhysicalPlayerInternal(
        newPhysicalPlayer: Player,
        baselineVolume: Float,
        logicalPlayWhenReady: Boolean,
        applyPhysicalVolume: Boolean
    ) {
        if (releasedTransitionResources) return
        val previousIdentity = physicalPlayer.currentMediaItem.smoothPlaybackIdentity()
        val incomingIdentity = newPhysicalPlayer.currentMediaItem.smoothPlaybackIdentity()
        if (previousIdentity != incomingIdentity) {
            smoothResumePolicy.onNewPlaybackAttempt()
        }
        val isPhysicalSwap = newPhysicalPlayer !== physicalPlayer
        if (isPhysicalSwap) {
            physicalPlayer.removeListener(physicalListener)
            physicalPlayer = newPhysicalPlayer
            physicalListener = createPhysicalListener(newPhysicalPlayer)
        }
        transitionCoordinator.rebindPhysicalPlayer(
            physicalPlayWhenReady = newPhysicalPlayer.playWhenReady,
            isAudible = newPhysicalPlayer.isPlaying,
            baselineVolume = baselineVolume,
            logicalPlayWhenReady = logicalPlayWhenReady,
            applyPhysicalVolume = applyPhysicalVolume
        )
        if (isPhysicalSwap) {
            // ForwardingSimpleBasePlayer observes one coherent incoming state: its delegate is
            // changed only after the logical intent and the role-specific baseline are installed.
            setPlayer(newPhysicalPlayer)
            newPhysicalPlayer.addListener(physicalListener)
        }
        logicalUnmuteVolume = baselineVolume.takeIf { it > 0f } ?: logicalUnmuteVolume
        invalidateState()
    }

    override fun activateReboundPhysicalPlayer() {
        transitionCoordinator.activateReboundPhysicalPlayer()
        invalidateState()
    }

    override fun setCrossfadeVolumeControlActive(active: Boolean) {
        crossfadeControlsPhysicalVolume = active
    }

    override fun finishCrossfadeVolumeControl(baselineVolume: Float) {
        transitionCoordinator.setBaselineVolume(
            volume = baselineVolume,
            applyPhysicalVolume = false
        )
        logicalUnmuteVolume = baselineVolume.takeIf { it > 0f } ?: logicalUnmuteVolume
        crossfadeControlsPhysicalVolume = false
        invalidateState()
    }

    override fun beginCrossfadeHandoffPlaylistSync(
        currentMediaId: String,
        expectedUpcomingMediaIds: List<String>
    ) {
        closeHandoffPlaylistTransaction("superseded_by_new_handoff")
        handoffPlaylistMutationClassifier.begin(
            currentMediaId = currentMediaId
        )
        CrossfadeTrace.log(
            "HANDOFF_TX SCOPE_BEGIN incoming=$currentMediaId " +
                "expectedUpcoming=${expectedUpcomingMediaIds.joinToString(",")}"
        )
    }

    override fun endCrossfadeHandoffPlaylistSync() {
        closeHandoffPlaylistTransaction("handoff_aborted")
    }

    override fun completeCrossfadeHandoffPlaylistSync() {
        handoffPlaylistMutationClassifier.markCrossfadeCompleted()
        if (handoffPlaylistMutationClassifier.shouldCloseAtCrossfadeCompletion()) {
            val transactionId = handoffPlaylistMutationClassifier.end(
                abortSourceTransaction = false,
                reason = "effects_complete"
            )
            CrossfadeTrace.log(
                "HANDOFF_TX COMPLETE id=${transactionId?.toString() ?: "none"}"
            )
        } else {
            CrossfadeTrace.log(
                "HANDOFF_TX WAITING id=" +
                    handoffPlaylistMutationClassifier.transactionId()
            )
        }
    }

    override fun beginCrossfadeHandoffNavigationSync(currentMediaId: String) {
        closeHandoffNavigationTransaction("superseded_by_new_handoff")
        handoffNavigationPolicyClassifier.begin(currentMediaId)
        CrossfadeTrace.log("NAV_POLICY_TX SCOPE_BEGIN incoming=$currentMediaId")
    }

    override fun endCrossfadeHandoffNavigationSync() {
        closeHandoffNavigationTransaction("handoff_aborted")
    }

    override fun completeCrossfadeHandoffNavigationSync() {
        handoffNavigationPolicyClassifier.markCrossfadeCompleted()
        if (handoffNavigationPolicyClassifier.shouldCloseAtCrossfadeCompletion()) {
            val transactionId = handoffNavigationPolicyClassifier.end(
                abortSourceTransaction = false,
                reason = "effects_complete"
            )
            CrossfadeTrace.log(
                "NAV_POLICY_TX COMPLETE id=${transactionId?.toString() ?: "none"}"
            )
        } else {
            CrossfadeTrace.log(
                "NAV_POLICY_TX WAITING id=" +
                    handoffNavigationPolicyClassifier.transactionId()
            )
        }
    }

    private fun emitLogicalCommand(command: LogicalPlaybackCommand) {
        onLogicalCommand(LogicalPlaybackCommandEvent(command = command))
    }

    private fun emitPlaylistMutation(
        preservesCurrentMediaItem: Boolean,
        internalClaim: ClaimedInternalPlaylistMutation? = null
    ) {
        val internal = internalClaim != null
        onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin = if (internal) {
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL
                } else {
                    LogicalPlaybackCommandOrigin.EXTERNAL
                },
                preservesCurrentMediaItem = preservesCurrentMediaItem,
                transactionId = internalClaim?.transactionId,
                playlistOperation = internalClaim?.operation ?:
                    LogicalPlaylistMutationOperation.OTHER
            )
        )
    }

    private fun emitNavigationPolicyCommand(
        claim: ClaimedInternalNavigationPolicyCommand?,
        operation: LogicalNavigationPolicyOperation,
        value: String
    ) {
        onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin = if (claim != null) {
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL
                } else {
                    LogicalPlaybackCommandOrigin.EXTERNAL
                },
                transactionId = claim?.transactionId,
                navigationOperation = operation,
                navigationValue = value
            )
        )
    }

    private fun acceptSourceOwnedNavigationClaim(
        claim: ClaimedInternalNavigationPolicyCommand?
    ): Boolean {
        claim ?: return false
        val currentMediaId = physicalPlayer.currentMediaItem?.mediaId
        if (currentMediaId != claim.currentMediaId) return false
        // The exact command was registered by handleServiceSongChanged before MediaController
        // dispatch. The handoff scope owns cleanup when present, but source provenance remains
        // authoritative for callbacks that arrive just after promotion closes that scope.
        handoffNavigationPolicyClassifier.accept(currentMediaId, claim)
        return true
    }

    private fun closeCompletedHandoffTransactionAfterClaim() {
        if (!handoffPlaylistMutationClassifier.shouldCloseAfterClaim()) return
        val transactionId = handoffPlaylistMutationClassifier.end(
            abortSourceTransaction = false,
            reason = "effects_complete"
        )
        CrossfadeTrace.log("HANDOFF_TX COMPLETE id=$transactionId")
    }

    private fun closeHandoffPlaylistTransaction(reason: String) {
        handoffPlaylistMutationClassifier.end(
            abortSourceTransaction = true,
            reason = reason
        )
    }

    private fun closeCompletedNavigationTransactionAfterClaim() {
        if (handoffNavigationPolicyClassifier.transactionId() == null) return
        if (!handoffNavigationPolicyClassifier.shouldCloseAfterClaim()) return
        val transactionId = handoffNavigationPolicyClassifier.end(
            abortSourceTransaction = false,
            reason = "effects_complete"
        )
        CrossfadeTrace.log("NAV_POLICY_TX COMPLETE id=$transactionId")
    }

    private fun closeHandoffNavigationTransaction(reason: String) {
        handoffNavigationPolicyClassifier.end(
            abortSourceTransaction = true,
            reason = reason
        )
    }

    private fun replacementPreservesCurrentMediaItem(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): Boolean {
        val currentIndex = physicalPlayer.currentMediaItemIndex
        if (currentIndex < 0) return false
        if (currentIndex !in fromIndex until toIndex) return true
        val replacementIndex = currentIndex - fromIndex
        return mediaItems.getOrNull(replacementIndex)?.mediaId ==
            physicalPlayer.currentMediaItem?.mediaId
    }

    private fun invalidateSmoothResumeForNewPlaybackAttempt() {
        smoothResumePolicy.onNewPlaybackAttempt()
        transitionCoordinator.onNewPlaybackAttempt(
            applyPhysicalVolume = !crossfadeControlsPhysicalVolume
        )
    }

    private fun createPhysicalListener(source: Player): Player.Listener =
        object : Player.Listener {
            private fun isCurrent(): Boolean =
                !releasedTransitionResources && physicalPlayer === source

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCurrent()) transitionCoordinator.onAudibilityChanged(isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!isCurrent()) return
                invalidateSmoothResumeForNewPlaybackAttempt()
                invalidateState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!isCurrent()) return
                transitionCoordinator.onPhysicalPlayWhenReadyChanged(playWhenReady)
                if (
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    if (playWhenReady) smoothResumePolicy.onNewPlaybackAttempt()
                    logicalPlayWhenReadyChangeReason = reason
                    transitionCoordinator.onSystemPlayWhenReadyChanged(playWhenReady)
                    invalidateState()
                }
            }

            override fun onPlaybackSuppressionReasonChanged(
                playbackSuppressionReason: Int
            ) {
                if (
                    isCurrent() &&
                    playbackSuppressionReason !=
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE
                ) {
                    transitionCoordinator.bypassForSafety()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isCurrent()) {
                    smoothResumePolicy.onNewPlaybackAttempt()
                    transitionCoordinator.bypassForSafety()
                }
            }
        }
}

private class HandlerPlaybackTransitionScheduler(
    private val handler: Handler
) : PlaybackTransitionScheduler {
    override fun schedule(
        delayMillis: Long,
        action: () -> Unit
    ): TransitionCancellation {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return TransitionCancellation { handler.removeCallbacks(runnable) }
    }
}
