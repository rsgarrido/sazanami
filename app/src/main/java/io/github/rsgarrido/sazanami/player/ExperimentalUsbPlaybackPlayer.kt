package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import io.github.rsgarrido.sazanami.BuildConfig
import kotlinx.coroutines.*

/** One authoritative Player for the session. Normal commands retain the existing SmoothPlaybackPlayer. */
@OptIn(UnstableApi::class)
internal class ExperimentalUsbPlaybackPlayer(
    private val normal: SmoothPlaybackPlayer, private val scope: CoroutineScope, usb: io.github.rsgarrido.sazanami.player.usb.FullUsbPlaybackEngineClient,
    private val releaseNormal: suspend ()->Boolean, private val recreateNormal: ()->Player,
    private val acquireFocus: ()->Boolean={true}, private val releaseFocus: ()->Unit={},
    private val predecessorReleased: ()->Boolean={true}
) : ForwardingSimpleBasePlayer(normal) {
    var directState=IntegratedDirectPlaybackState();private set
    private var selectedDirect=false
    private var normalReleaseSafe=false
    private var smoothEnabled=true
    private var shuffleEnabled=false
    private var modeJob: Job?=null
    private var savedItems: List<MediaItem> = emptyList()
    private var savedIndex=0
    private var savedPosition=0L
    private var savedVolume=1f
    private var savedParameters=PlaybackParameters.DEFAULT
    private var shuttingDown=false
    private val direct=DirectUsbMediaPlayer(scope,usb,changed={ value ->
        if(selectedDirect) {
            directState=value.copy(normalReleased=normalReleaseSafe,cleanupSafe=value.cleanupSafe && normalReleaseSafe)
            IntegratedDirectPlaybackBridge.publish(this,directState)
        }
    },acquireFocus=acquireFocus,releaseFocus=releaseFocus)
    fun selectDirect(enabled: Boolean) {
        if(shuttingDown || !predecessorReleased() || !BuildConfig.DEBUG || modeJob?.isCompleted==false || enabled==selectedDirect) return
        if(enabled) {
            savedItems=(0 until normal.mediaItemCount).map(normal::getMediaItemAt)
            savedIndex=normal.currentMediaItemIndex.coerceAtLeast(0);savedPosition=normal.currentPosition.coerceAtLeast(0)
            savedVolume=normal.volume;savedParameters=normal.playbackParameters
            selectedDirect=true;setPlayer(direct)
            directState=IntegratedDirectPlaybackState(enabled=true,cleanupSafe=false,notice="Releasing normal audio engines")
            IntegratedDirectPlaybackBridge.publish(this,directState)
            normal.stop() // Cancels smooth timers; release below joins both physical playback engines.
            modeJob=scope.launch {
                val released=runCatching { releaseNormal() }.getOrDefault(false);normalReleaseSafe=released
                directState=IntegratedDirectPlaybackState(enabled=true,normalReleased=released,cleanupSafe=released,
                    phase=if(released) IntegratedDirectPhase.IDLE else IntegratedDirectPhase.ERROR,error=if(released) null else "NORMAL_BACKEND_RELEASE_UNVERIFIED")
                IntegratedDirectPlaybackBridge.publish(this@ExperimentalUsbPlaybackPlayer,directState)
            }
        } else modeJob=scope.launch {
            if(!normalReleaseSafe || !direct.stopAndJoin()) { rejectDirectCommand("Normal backend blocked by incomplete USB restoration");return@launch }
            val restored=try { recreateNormal() } catch (_: Exception) { normalReleaseSafe=false;directState=directState.copy(phase=IntegratedDirectPhase.ERROR,error="NORMAL_BACKEND_RECREATION_FAILED",cleanupSafe=false);IntegratedDirectPlaybackBridge.publish(this@ExperimentalUsbPlaybackPlayer,directState);return@launch }
            normal.rebindPhysicalPlayer(restored,baselineVolume=savedVolume,logicalPlayWhenReady=false)
            normal.setSmoothPlaybackEnabled(smoothEnabled);normal.setLogicalShuffleModeEnabled(shuffleEnabled);normal.playbackParameters=savedParameters
            if(savedItems.isNotEmpty()) normal.setMediaItems(savedItems,savedIndex.coerceAtMost(savedItems.lastIndex),savedPosition)
            selectedDirect=false;setPlayer(normal)
            directState=directState.copy(enabled=false,normalReleased=false)
            IntegratedDirectPlaybackBridge.publish(this@ExperimentalUsbPlaybackPlayer,directState)
        }
    }
    override fun handleSetMediaItems(mediaItems: List<MediaItem>,startIndex: Int,startPositionMs: Long): com.google.common.util.concurrent.ListenableFuture<*> {
        if(!predecessorReleased() || modeJob?.isCompleted==false) return com.google.common.util.concurrent.Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_SWITCH_PENDING"))
        return super.handleSetMediaItems(mediaItems,startIndex,startPositionMs)
    }
    override fun handlePrepare(): com.google.common.util.concurrent.ListenableFuture<*> {
        if(!predecessorReleased() || modeJob?.isCompleted==false || (selectedDirect && !normalReleaseSafe)) return com.google.common.util.concurrent.Futures.immediateVoidFuture()
        return super.handlePrepare()
    }
    fun stopDirect() { if(selectedDirect) direct.requestStop() }
    fun toggleDirectPlayback() { if(selectedDirect && normalReleaseSafe && direct.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) direct.playWhenReady=!direct.playWhenReady }
    fun pauseDirectPlayback() { if(selectedDirect && normalReleaseSafe && direct.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) direct.pause() }
    fun seekDirectPlayback(positionMs: Long) {
        if(selectedDirect && normalReleaseSafe && direct.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) direct.seekTo(positionMs)
        else if(selectedDirect) direct.notice("Source seek")
    }
    fun rejectDirectCommand(command: String) { if(selectedDirect) direct.notice(command) }
    suspend fun shutdown(): Boolean { shuttingDown=true;val safe=direct.stopAndJoin();modeJob?.join();return safe }
    fun releaseTransitionResources()=normal.releaseTransitionResources()
    fun setSmoothPlaybackEnabled(enabled: Boolean) { smoothEnabled=enabled;if(!selectedDirect) normal.setSmoothPlaybackEnabled(enabled) }
    fun setLogicalShuffleModeEnabled(enabled: Boolean) { shuffleEnabled=enabled;if(!selectedDirect) normal.setLogicalShuffleModeEnabled(enabled) }
    fun markNextShuffleModeCommandExternal() { if(!selectedDirect) normal.markNextShuffleModeCommandExternal() }
    fun updateArtwork(item: MediaItem,uri: Uri?) { if(selectedDirect) direct.updateArtwork(item,uri) else normal.updateArtwork(item,uri) }
}
