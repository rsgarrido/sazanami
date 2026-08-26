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

    fun activateReboundPhysicalPlayer()
}

/** The single logical Player exposed by PlaybackService to MediaLibrarySession. */
@OptIn(UnstableApi::class)
internal class SmoothPlaybackPlayer(
    initialPhysicalPlayer: Player,
    private val onBaselineVolumeChanged: (Float) -> Unit = {},
    private val onLogicalCommand: (LogicalPlaybackCommand) -> Unit = {},
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
        onLogicalCommand(LogicalPlaybackCommand.PLAY_PAUSE)
        logicalPlayWhenReadyChangeReason =
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        if (playWhenReady) {
            transitionCoordinator.requestPlay()
        } else {
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
        onLogicalCommand(LogicalPlaybackCommand.STOP)
        transitionCoordinator.onImmediateStop()
        return super.handleStop()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: @Player.Command Int
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.SEEK)
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.PLAYLIST_MUTATION)
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.PLAYLIST_MUTATION)
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.PLAYLIST_MUTATION)
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.PLAYLIST_MUTATION)
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.PLAYLIST_MUTATION)
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.NAVIGATION_POLICY)
        return super.handleSetRepeatMode(repeatMode)
    }

    override fun handleSetShuffleModeEnabled(
        shuffleModeEnabled: Boolean
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.NAVIGATION_POLICY)
        return super.handleSetShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): ListenableFuture<*> {
        onLogicalCommand(LogicalPlaybackCommand.PLAYBACK_PARAMETERS)
        return super.handleSetPlaybackParameters(playbackParameters)
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseTransitionResources()
        return super.handleRelease()
    }

    fun setSmoothPlaybackEnabled(enabled: Boolean) {
        transitionCoordinator.setEnabled(enabled)
        invalidateState()
    }

    fun releaseTransitionResources() {
        if (releasedTransitionResources) return
        releasedTransitionResources = true
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

    private fun createPhysicalListener(source: Player): Player.Listener =
        object : Player.Listener {
            private fun isCurrent(): Boolean =
                !releasedTransitionResources && physicalPlayer === source

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCurrent()) transitionCoordinator.onAudibilityChanged(isPlaying)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!isCurrent()) return
                transitionCoordinator.onPhysicalPlayWhenReadyChanged(playWhenReady)
                if (
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
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
                if (isCurrent()) transitionCoordinator.bypassForSafety()
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
