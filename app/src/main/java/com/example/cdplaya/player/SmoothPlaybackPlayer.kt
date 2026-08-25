package com.example.cdplaya.player

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The single logical Player exposed by PlaybackService to MediaLibrarySession. */
@OptIn(UnstableApi::class)
internal class SmoothPlaybackPlayer(
    private val physicalPlayer: Player,
    clock: PlaybackTransitionClock = PlaybackTransitionClock {
        SystemClock.elapsedRealtime()
    },
    scheduler: PlaybackTransitionScheduler = HandlerPlaybackTransitionScheduler(
        Handler(physicalPlayer.applicationLooper)
    )
) : ForwardingSimpleBasePlayer(physicalPlayer), Player.Listener {

    private var logicalPlayWhenReadyChangeReason =
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var logicalUnmuteVolume = physicalPlayer.volume.takeIf { it > 0f } ?: 1f
    private var releasedTransitionResources = false
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
        initialPhysicalPlayWhenReady = physicalPlayer.playWhenReady,
        initialAudible = physicalPlayer.isPlaying,
        initialBaselineVolume = physicalPlayer.volume
    )

    init {
        physicalPlayer.addListener(this)
    }

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
        transitionCoordinator.setBaselineVolume(volume)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        transitionCoordinator.onImmediateStop()
        return super.handleStop()
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseTransitionResources()
        return super.handleRelease()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        transitionCoordinator.onAudibilityChanged(isPlaying)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        transitionCoordinator.onPhysicalPlayWhenReadyChanged(playWhenReady)
        if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            logicalPlayWhenReadyChangeReason = reason
            transitionCoordinator.onSystemPlayWhenReadyChanged(playWhenReady)
            invalidateState()
        }
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
            transitionCoordinator.bypassForSafety()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        transitionCoordinator.bypassForSafety()
    }

    fun setSmoothPlaybackEnabled(enabled: Boolean) {
        transitionCoordinator.setEnabled(enabled)
        invalidateState()
    }

    fun releaseTransitionResources() {
        if (releasedTransitionResources) return
        releasedTransitionResources = true
        physicalPlayer.removeListener(this)
        transitionCoordinator.release()
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
