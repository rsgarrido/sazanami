package io.github.rsgarrido.sazanami.player

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*

/** Media3 control/state adapter only. The existing controller owns all source and USB engine work. */
@OptIn(UnstableApi::class)
internal class DirectUsbMediaPlayer(
    private val scope: CoroutineScope, private val usb: FullUsbPlaybackEngineClient,
    private val changed: (IntegratedDirectPlaybackState)->Unit,
    private val acquireFocus: ()->Boolean={true},private val releaseFocus: ()->Unit={},
    private val librarySong: (String)->Song?={ id -> PlaybackLibraryBridge.songs.firstOrNull { it.id.toString()==id } }
) : SimpleBasePlayer(Looper.getMainLooper()) {
    var evidence=IntegratedDirectPlaybackState(enabled=true,normalReleased=true); private set
    private var items: List<MediaItem> = emptyList()
    private var selected=0
    private var source: Song?=null
    private var worker: Job?=null
    private var stopRequested=false
    private var nextGeneration=0L
    private fun update(value: IntegratedDirectPlaybackState) { evidence=value; changed(value); invalidateState() }
    fun notice(command: String) { update(evidence.copy(notice="$command is unavailable in Session 14")) }
    override fun getState(): State {
        val playlist=items.mapIndexed { index,item -> MediaItemData.Builder(item.listeningEvidence()?.itemInstanceId ?: "${item.mediaId}:$index")
            .setMediaItem(item).setMediaMetadata(item.mediaMetadata).setIsSeekable(false)
            .setDurationUs(if(index==selected) evidence.durationMs.coerceAtMost(Long.MAX_VALUE/1000)*1000 else C.TIME_UNSET).build() }
        val playbackState=when(evidence.phase) {
            IntegratedDirectPhase.PREPARING -> Player.STATE_BUFFERING
            IntegratedDirectPhase.PLAYING -> Player.STATE_READY
            IntegratedDirectPhase.ENDED -> Player.STATE_ENDED
            else -> Player.STATE_IDLE
        }
        return State.Builder().setAvailableCommands(Player.Commands.Builder().addAll(*integratedDirectCommands).build())
            .setPlaylist(playlist).setCurrentMediaItemIndex(if(playlist.isEmpty()) C.INDEX_UNSET else selected)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(evidence.phase in setOf(IntegratedDirectPhase.PREPARING,IntegratedDirectPhase.PLAYING),Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            // A supplier prevents Media3 from extrapolating USB position using its elapsed clock.
            .setContentPositionMs(PositionSupplier { evidence.positionMs })
            .setPlayerError(if(evidence.phase==IntegratedDirectPhase.ERROR) PlaybackException(evidence.error ?: "DIRECT_FAILED",null,PlaybackException.ERROR_CODE_UNSPECIFIED) else null)
            .build()
    }
    override fun handleSetMediaItems(mediaItems: List<MediaItem>,startIndex: Int,startPositionMs: Long): ListenableFuture<*> {
        if(evidence.busy || worker?.isCompleted==false) { notice("Track replacement while direct owns USB; Stop first"); return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_BUSY")) }
        items=mediaItems.toList();selected=if(items.isEmpty()) 0 else startIndex.coerceIn(0,items.lastIndex)
        source=items.getOrNull(selected)?.mediaId?.let(librarySong)
        update(IntegratedDirectPlaybackState(enabled=true,normalReleased=true,generation=++nextGeneration,
            libraryId=source?.id,metadataDurationMs=source?.duration ?: 0,
            phase=if(source==null && items.isNotEmpty()) IntegratedDirectPhase.ERROR else IntegratedDirectPhase.IDLE,
            error=if(source==null && items.isNotEmpty()) "UNSUPPORTED_TRACK" else null))
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> {
        if(evidence.busy || worker?.isCompleted==false) return Futures.immediateVoidFuture()
        if(!evidence.cleanupSafe) { notice("Restart blocked until USB cleanup is verified"); return Futures.immediateVoidFuture() }
        val song=source ?: run { update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="UNSUPPORTED_TRACK")); return Futures.immediateVoidFuture() }
        if(!acquireFocus()) { update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_AUDIO_FOCUS_DENIED",cleanupSafe=true));return Futures.immediateVoidFuture() }
        stopRequested=false
        val generation=++nextGeneration
        update(evidence.copy(generation=generation,phase=IntegratedDirectPhase.PREPARING,completedFrames=0,totalFrames=0,sampleRateHz=0,error=null,notice=null,engine=null,cleanupSafe=false))
        worker=scope.launch {
            try {
                val result=usb.runIntegratedFull(song.uri) { progress ->
                    if(evidence.generation==generation && !stopRequested) update(evidence.copy(completedFrames=progress.completedFrames,
                        totalFrames=progress.totalFrames,sampleRateHz=progress.sampleRateHz,phase=if(progress.completedFrames>0) IntegratedDirectPhase.PLAYING else IntegratedDirectPhase.PREPARING))
                }
                if(evidence.generation!=generation) return@launch
                update(integratedTerminalState(evidence,result))
            } catch(error: UsbIntegratedStartException) {
                update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error=error.reason,cleanupSafe=error.reason!="DIRECT_RESOURCE_CLEANUP_UNVERIFIED"))
            } catch(error: Exception) {
                update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_RESOURCE_CLEANUP_UNVERIFIED",cleanupSafe=false))
            } finally { releaseFocus() }
        }
        return Futures.immediateVoidFuture()
    }
    fun requestStop() { if(evidence.busy) { stopRequested=true; update(evidence.copy(phase=IntegratedDirectPhase.STOPPING)); usb.stopIntegratedFull() } else if(worker?.isCompleted!=false) update(evidence.copy(phase=IntegratedDirectPhase.STOPPED)) }
    suspend fun stopAndJoin(): Boolean { requestStop(); withContext(NonCancellable) { worker?.join() }; return evidence.cleanupSafe }
    override fun handleStop(): ListenableFuture<*> { requestStop(); return Futures.immediateVoidFuture() }
    // No CHANGE_MEDIA_ITEMS capability: only single-item library selection is supported.
    override fun handleAddMediaItems(index: Int,mediaItems: List<MediaItem>)=unsupported("Queue edit")
    override fun handleMoveMediaItems(fromIndex: Int,toIndex: Int,newIndex: Int)=unsupported("Queue edit")
    override fun handleReplaceMediaItems(fromIndex: Int,toIndex: Int,mediaItems: List<MediaItem>)=unsupported("Queue edit")
    override fun handleRemoveMediaItems(fromIndex: Int,toIndex: Int)=unsupported("Queue edit")
    private fun unsupported(command: String): ListenableFuture<*> { notice(command);return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_UNSUPPORTED_COMMAND")) }
    fun updateArtwork(item: MediaItem,uri: android.net.Uri?) {
        if(items.getOrNull(selected)?.mediaId!=item.mediaId) return
        items=items.toMutableList().also { it[selected]=it[selected].buildUpon().setMediaMetadata(it[selected].mediaMetadata.buildUpon().setArtworkUri(uri).build()).build() }
        invalidateState()
    }
}
