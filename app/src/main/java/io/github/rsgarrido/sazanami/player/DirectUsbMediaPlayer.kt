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
    private val librarySong: (String)->Song?={ id -> PlaybackLibraryBridge.songs.firstOrNull { it.id.toString()==id } },
    private val listeningChanged: (MediaItem?, IntegratedDirectPlaybackState, Boolean)->Unit={ _, _, _ -> }
) : SimpleBasePlayer(Looper.getMainLooper()) {
    var evidence=IntegratedDirectPlaybackState(enabled=true,normalReleased=true); private set
    private var items: List<MediaItem> = emptyList()
    private var selected=0
    private var source: Song?=null
    private var worker: Job?=null
    private var stopRequested=false
    private var nextGeneration=0L
    private var control: UsbFullPlaybackControl?=null
    private fun update(value: IntegratedDirectPlaybackState, terminalConfirmed: Boolean=false) {
        evidence=value
        listeningChanged(items.getOrNull(selected), value, terminalConfirmed)
        changed(value)
        invalidateState()
    }
    fun notice(command: String) { update(evidence.copy(notice="$command is unavailable in Session 17")) }
    override fun getState(): State {
        val playlist=items.mapIndexed { index,item -> MediaItemData.Builder(item.listeningEvidence()?.itemInstanceId ?: "${item.mediaId}:$index")
            .setMediaItem(item).setMediaMetadata(item.mediaMetadata).setIsSeekable(index==selected && evidence.seekCapable && evidence.busy && evidence.phase!=IntegratedDirectPhase.STOPPING)
            .setDurationUs(if(index==selected) evidence.durationMs.coerceAtMost(Long.MAX_VALUE/1000)*1000 else C.TIME_UNSET).build() }
        val playbackState=when(evidence.phase) {
            IntegratedDirectPhase.PREPARING,IntegratedDirectPhase.PAUSING,IntegratedDirectPhase.RESUMING,IntegratedDirectPhase.SEEKING -> Player.STATE_BUFFERING
            IntegratedDirectPhase.PLAYING,IntegratedDirectPhase.PAUSED -> Player.STATE_READY
            IntegratedDirectPhase.ENDED -> Player.STATE_ENDED
            else -> Player.STATE_IDLE
        }
        val commands=Player.Commands.Builder().addAll(*integratedDirectCommands)
        if(worker?.isCompleted!=false || !evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING) commands.remove(Player.COMMAND_PLAY_PAUSE)
        if(worker?.isCompleted!=false || !evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING || !evidence.seekCapable) commands.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        return State.Builder().setAvailableCommands(commands.build())
            .setPlaylist(playlist).setCurrentMediaItemIndex(if(playlist.isEmpty()) C.INDEX_UNSET else selected)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(evidence.busy && evidence.phase!=IntegratedDirectPhase.STOPPING && evidence.playWhenReady,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
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
        val commands=UsbFullPlaybackControl();control=commands
        val generation=++nextGeneration
        update(evidence.copy(generation=generation,phase=IntegratedDirectPhase.PREPARING,completedFrames=0,totalFrames=0,sampleRateHz=0,error=null,notice=null,engine=null,cleanupSafe=false,playWhenReady=true,
            nativeTransportPhase=UsbFullTransportPhase.STARTING,pauseRequests=0,resumeRequests=0,nativePauseCounters=emptyMap(),
            sourcePositionFrame=0,sourceEpoch=0,seekCapable=false,pendingSeekSerial=0))
        worker=scope.launch {
            try {
                val result=usb.runIntegratedFull(song.uri,commands) { progress ->
                    if(evidence.generation==generation && !stopRequested) {
                        try { update(integratedProgressState(evidence,progress,commands.playWhenReady)) }
                        catch(error: IllegalArgumentException) { commands.fail(error);update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_PROGRESS_INVALID",playWhenReady=false), terminalConfirmed=true) }
                    }
                }
                if(evidence.generation!=generation) return@launch
                update(integratedTerminalState(evidence,result), terminalConfirmed=true)
            } catch(error: UsbIntegratedStartException) {
                update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error=error.reason,cleanupSafe=error.reason!="DIRECT_RESOURCE_CLEANUP_UNVERIFIED"), terminalConfirmed=true)
            } catch(error: Exception) {
                update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_RESOURCE_CLEANUP_UNVERIFIED",cleanupSafe=false), terminalConfirmed=true)
            } finally { commands.finish();if(control===commands)control=null;releaseFocus() }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val commands=control ?: return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_NOT_ACTIVE"))
        if(!evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING) return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_NOT_ACTIVE"))
        return try {
            val already=commands.playWhenReady==playWhenReady
            if(!commands.request(playWhenReady)) {
                update(evidence.copy(phase=IntegratedDirectPhase.STOPPING,playWhenReady=false))
                return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_TERMINAL"))
            }
            val intent=commands.intent()
            update(evidence.copy(playWhenReady=playWhenReady,pauseRequests=intent.pauses,resumeRequests=intent.resumes,
                phase=if(already || evidence.phase in setOf(IntegratedDirectPhase.PREPARING,IntegratedDirectPhase.SEEKING)) evidence.phase else
                if(playWhenReady) IntegratedDirectPhase.RESUMING else IntegratedDirectPhase.PAUSING))
            Futures.immediateVoidFuture()
        } catch(error: Exception) {
            update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_CONTROL_FAILED",playWhenReady=false), terminalConfirmed=true);Futures.immediateFailedFuture<Unit>(error)
        } catch(error: LinkageError) {
            update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_CONTROL_FAILED",playWhenReady=false), terminalConfirmed=true);Futures.immediateFailedFuture<Unit>(error)
        }
    }
    override fun handleSeek(mediaItemIndex: Int,positionMs: Long,seekCommand: Int): ListenableFuture<*> {
        if(mediaItemIndex!=selected || seekCommand!=Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM || !evidence.seekCapable || !evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING)
            return unsupported("Source seek")
        return try {
            val commands=control ?: return unsupported("Source seek")
            val target=directSeekTargetFrame(positionMs,evidence.sampleRateHz,evidence.totalFrames)
            if(!commands.seek(target)) return unsupported("Terminal source seek")
            update(evidence.copy(phase=IntegratedDirectPhase.SEEKING,pendingSeekSerial=commands.intent().seekSerial,notice=null))
            Futures.immediateVoidFuture()
        } catch(error: IllegalArgumentException) {Futures.immediateFailedFuture<Unit>(error)}
        catch(error: Exception) {update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_SEEK_CONTROL_FAILED",playWhenReady=false), terminalConfirmed=true);Futures.immediateFailedFuture<Unit>(error)}
        catch(error: LinkageError) {update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_SEEK_CONTROL_FAILED",playWhenReady=false), terminalConfirmed=true);Futures.immediateFailedFuture<Unit>(error)}
    }
    fun requestStop() {
        if(evidence.busy || worker?.isCompleted==false) {
            stopRequested=true
            try { control?.stop() }
            finally { update(evidence.copy(phase=IntegratedDirectPhase.STOPPING,playWhenReady=false));usb.stopIntegratedFull() }
        } else if(worker?.isCompleted!=false) update(evidence.copy(phase=IntegratedDirectPhase.STOPPED,playWhenReady=false), terminalConfirmed=true)
    }
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
