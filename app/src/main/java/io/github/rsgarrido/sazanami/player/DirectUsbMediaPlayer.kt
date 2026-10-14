package io.github.rsgarrido.sazanami.player

import android.os.Looper
import android.os.Handler
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
    private val listeningChanged: (MediaItem?, IntegratedDirectPlaybackState, Boolean)->Unit={ _, _, _ -> },
    private val sourceAdmissionFailure: ()->String?={ null }
) : SimpleBasePlayer(Looper.getMainLooper()) {
    var evidence=IntegratedDirectPlaybackState(enabled=true,normalReleased=true); private set
    private var items: List<MediaItem> = emptyList()
    private var selected=0
    private var source: Song?=null
    private var worker: Job?=null
    private var stopRequested=false
    private var nextGeneration=0L
    private var control: UsbFullPlaybackControl?=null
    private var sourceStart: DirectSourceStart?=null
    private var startPositionMs=0L
    private var initialPlayingIntent=true
    private var stoppingForReplacement=false
    private var nextRequestId=0L
    private var pendingSource: SourceRequest?=null
    private var runRequest: SourceRequest?=null
    private var transitionJob: Job?=null
    private val applicationHandler=Handler(applicationLooper)

    private class SourceRequest(
        val id: Long, val item: MediaItem, val song: Song?, val playing: Boolean, val positionMs: Long
    ) {
        val result=CompletableDeferred<DirectSourceTransitionResult>()
        var generation: Long?=null
        fun state(status: DirectSourceTransitionStatus,error: String?=null)=DirectSourceTransitionState(
            id,item.mediaId,item.listeningEvidence()?.itemInstanceId,playing,positionMs,status,error)
    }

    /** Stage A execution contract. It does not mutate a queue or advertise multi-item commands. */
    fun replaceSource(item: MediaItem,playing: Boolean,startPositionMs: Long=0L): Deferred<DirectSourceTransitionResult> {
        verifyApplicationThread()
        val request=SourceRequest(++nextRequestId,item,librarySong(item.mediaId),playing,startPositionMs)
        val rejected=when {
            startPositionMs<0L -> "DIRECT_INITIAL_POSITION_INVALID"
            request.song==null -> "UNSUPPORTED_TRACK"
            item.listeningEvidence()==null -> "DIRECT_SOURCE_IDENTITY_MISSING"
            !scope.isActive -> "DIRECT_SERVICE_UNAVAILABLE"
            !evidence.cleanupSafe && worker?.isCompleted!=false -> "DIRECT_RESOURCE_CLEANUP_UNVERIFIED"
            else -> sourceAdmissionFailure()
        }
        if(rejected!=null) {
            request.result.complete(DirectSourceTransitionResult(request.id,DirectSourceTransitionStatus.REJECTED,error=rejected))
            return request.result
        }
        val supersededPending=pendingSource
        val supersededRun=runRequest
        pendingSource=request
        request.result.invokeOnCompletion {
            if(request.result.isCancelled) {
                val cancel={
                    val ownsPending=pendingSource===request
                    if(ownsPending) pendingSource=null
                    if(ownsPending || runRequest===request) stopRun(forReplacement=false)
                    if(evidence.sourceTransition?.requestId==request.id)
                        update(evidence.copy(sourceTransition=request.state(DirectSourceTransitionStatus.CANCELLED)))
                }
                if(Looper.myLooper()==applicationLooper) cancel() else applicationHandler.post { cancel() }
            }
        }
        update(evidence.copy(sourceTransition=request.state(DirectSourceTransitionStatus.WAITING_FOR_CLEANUP)))
        if(request.result.isCompleted) {
            supersededPending?.let { settle(it,DirectSourceTransitionStatus.SUPERSEDED) }
            supersededRun?.let { settle(it,DirectSourceTransitionStatus.SUPERSEDED) }
            return request.result
        }
        stopRun(forReplacement=true)
        if(transitionJob?.isCompleted!=false) {
            // Assign before starting: Main.immediate may execute through the first suspension inline.
            transitionJob=scope.launch(start=CoroutineStart.LAZY) {
                withContext(NonCancellable) {
                    while(pendingSource!=null) {
                        val predecessor=worker
                        predecessor?.join() // Never cancel/abandon the native cleanup owner.
                        if(worker===predecessor) worker=null
                        val target=pendingSource ?: break
                        pendingSource=null
                        if(target.result.isCompleted) continue
                        val failure=when {
                            !scope.isActive -> "DIRECT_SERVICE_UNAVAILABLE"
                            !evidence.cleanupSafe -> "DIRECT_RESOURCE_CLEANUP_UNVERIFIED"
                            else -> sourceAdmissionFailure()
                        }
                        if(failure!=null) {
                            settle(target,DirectSourceTransitionStatus.FAILED,failure)
                            continue
                        }
                        runRequest=target
                        acceptSource(listOf(target.item),0,target.positionMs,target.playing,target)
                        // A synchronous observer may supersede/cancel a just-accepted request.
                        if(!target.result.isCompleted && pendingSource==null) startRun(target)
                    }
                }
            }
            transitionJob?.start()
        }
        // Complete observers only after transferring request ownership; they may reenter selection.
        supersededPending?.let { settle(it,DirectSourceTransitionStatus.SUPERSEDED) }
        supersededRun?.let { settle(it,DirectSourceTransitionStatus.SUPERSEDED) }
        return request.result
    }

    private fun settle(request: SourceRequest,status: DirectSourceTransitionStatus,error: String?=null) {
        if(request.result.complete(DirectSourceTransitionResult(request.id,status,request.generation,error)) &&
            evidence.sourceTransition?.requestId==request.id)
            update(evidence.copy(sourceTransition=request.state(status,error)))
    }

    private fun acceptSource(mediaItems: List<MediaItem>,index: Int,positionMs: Long,playing: Boolean,request: SourceRequest?=null) {
        check(evidence.cleanupSafe && worker?.isCompleted!=false)
        items=mediaItems.toList();selected=index
        source=request?.song ?: items.getOrNull(selected)?.mediaId?.let(librarySong)
        startPositionMs=positionMs;initialPlayingIntent=playing
        sourceStart=null;control=null;stoppingForReplacement=false
        update(IntegratedDirectPlaybackState(enabled=true,normalReleased=true,generation=++nextGeneration,
            libraryId=source?.id,metadataDurationMs=source?.duration ?: 0,playWhenReady=playing,
            sourceTransition=request?.state(DirectSourceTransitionStatus.PREPARING),
            phase=if(source==null && items.isNotEmpty()) IntegratedDirectPhase.ERROR else IntegratedDirectPhase.IDLE,
            error=if(source==null && items.isNotEmpty()) "UNSUPPORTED_TRACK" else null))
    }
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
        if(!evidence.cleanupSafe && worker?.isCompleted!=false) {
            notice("Track replacement blocked until USB cleanup is verified")
            return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_RESOURCE_CLEANUP_UNVERIFIED"))
        }
        if(pendingSource!=null || transitionJob?.isCompleted==false) return unsupported("Track replacement pending")
        if(evidence.busy || worker?.isCompleted==false) { notice("Track replacement while direct owns USB; Stop first"); return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_BUSY")) }
        if(mediaItems.size>1) return unsupported("Queue replacement")
        val position=if(startPositionMs==C.TIME_UNSET) 0L else startPositionMs
        if(position<0L) return Futures.immediateFailedFuture<Unit>(IllegalArgumentException("DIRECT_INITIAL_POSITION_INVALID"))
        sourceAdmissionFailure()?.let { return Futures.immediateFailedFuture<Unit>(IllegalStateException(it)) }
        runRequest=null
        acceptSource(mediaItems,0,position,true)
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> {
        if(pendingSource!=null || transitionJob?.isCompleted==false) return Futures.immediateVoidFuture()
        if(evidence.busy || worker?.isCompleted==false) return Futures.immediateVoidFuture()
        if(!evidence.cleanupSafe) { notice("Restart blocked until USB cleanup is verified"); return Futures.immediateVoidFuture() }
        startRun()
        return Futures.immediateVoidFuture()
    }
    private fun startRun(request: SourceRequest?=null) {
        val admission=if(scope.isActive) sourceAdmissionFailure() else "DIRECT_SERVICE_UNAVAILABLE"
        val song=source ?: run {
            update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="UNSUPPORTED_TRACK",playWhenReady=false),terminalConfirmed=true)
            request?.let { settle(it,DirectSourceTransitionStatus.FAILED,"UNSUPPORTED_TRACK") }
            return
        }
        val failure=admission ?: if(!acquireFocus()) "DIRECT_AUDIO_FOCUS_DENIED" else null
        if(failure!=null) {
            update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error=failure,playWhenReady=false),terminalConfirmed=true)
            request?.let { settle(it,DirectSourceTransitionStatus.FAILED,failure) }
            return
        }
        check(evidence.cleanupSafe && worker?.isCompleted!=false)
        stopRequested=false
        stoppingForReplacement=false
        val start=DirectSourceStart(startPositionMs,initialPlayingIntent);sourceStart=start
        val commands=start.control;control=commands
        val generation=++nextGeneration
        request?.generation=generation
        val preparing=evidence.copy(generation=generation,phase=IntegratedDirectPhase.PREPARING,completedFrames=0,totalFrames=0,sampleRateHz=0,error=null,notice=null,engine=null,cleanupSafe=false,playWhenReady=start.playWhenReady,stoppedForReplacement=false,
            nativeTransportPhase=UsbFullTransportPhase.STARTING,pauseRequests=0,resumeRequests=0,nativePauseCounters=emptyMap(),
            sourcePositionFrame=0,sourceEpoch=0,seekCapable=false,pendingSeekSerial=0)
        worker=scope.launch(start=CoroutineStart.LAZY) {
            try {
                if(!stopRequested) update(preparing)
                if(stopRequested) {
                    // A synchronous observer cancelled startup before the engine acquired anything.
                    update(evidence.copy(generation=generation,phase=IntegratedDirectPhase.STOPPED,
                        playWhenReady=false,cleanupSafe=true,stoppedForReplacement=stoppingForReplacement),terminalConfirmed=true)
                    request?.let { settle(it,DirectSourceTransitionStatus.CANCELLED) }
                    return@launch
                }
                sourceAdmissionFailure()?.let { reason ->
                    update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error=reason,
                        playWhenReady=false,cleanupSafe=true),terminalConfirmed=true)
                    request?.let { settle(it,DirectSourceTransitionStatus.FAILED,reason) }
                    return@launch
                }
                val result=usb.runIntegratedFull(song.uri,commands) { progress ->
                    if(evidence.generation==generation && !stopRequested) {
                        try {
                            start.stagePosition(progress)
                            if(start.failure!=null) {
                                stopRequested=true
                                update(evidence.copy(phase=IntegratedDirectPhase.STOPPING,playWhenReady=false,error=start.failure))
                            } else {
                                val before=if(start.positionPending) evidence.copy(pendingSeekSerial=commands.intent().seekSerial) else evidence
                                val accepted=integratedProgressState(before,progress,start.playWhenReady)
                                start.confirmPosition(progress)
                                if(start.failure!=null) {
                                    stopRequested=true
                                    update(evidence.copy(phase=IntegratedDirectPhase.STOPPING,playWhenReady=false,error=start.failure))
                                    return@runIntegratedFull
                                }
                                update(accepted)
                                if(!start.positionPending &&
                                    ((accepted.phase==IntegratedDirectPhase.PLAYING && start.playWhenReady) ||
                                        (accepted.phase==IntegratedDirectPhase.PAUSED && !start.playWhenReady)))
                                    request?.let { settle(it,DirectSourceTransitionStatus.READY) }
                            }
                        }
                        catch(error: IllegalArgumentException) { commands.fail(error);update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_PROGRESS_INVALID",playWhenReady=false), terminalConfirmed=true) }
                        catch(error: Exception) { commands.fail(error);stopRequested=true;update(evidence.copy(phase=IntegratedDirectPhase.STOPPING,error="DIRECT_INITIAL_CONTROL_FAILED",playWhenReady=false)) }
                    }
                }
                if(evidence.generation!=generation) return@launch
                var terminal=integratedTerminalState(evidence,result)
                if(start.failure!=null || (start.positionPending && terminal.phase==IntegratedDirectPhase.ENDED))
                    terminal=terminal.copy(phase=IntegratedDirectPhase.ERROR,error=start.failure ?: "DIRECT_INITIAL_POSITION_UNSATISFIED")
                update(terminal.copy(stoppedForReplacement=terminal.phase==IntegratedDirectPhase.STOPPED && stoppingForReplacement), terminalConfirmed=true)
                request?.let { settle(it,when(terminal.phase) {
                    IntegratedDirectPhase.ENDED -> DirectSourceTransitionStatus.COMPLETED
                    IntegratedDirectPhase.ERROR -> DirectSourceTransitionStatus.FAILED
                    else -> DirectSourceTransitionStatus.CANCELLED
                },terminal.error) }
            } catch(error: UsbIntegratedStartException) {
                if(evidence.generation==generation) update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error=error.reason,playWhenReady=false,cleanupSafe=error.reason!="DIRECT_RESOURCE_CLEANUP_UNVERIFIED"), terminalConfirmed=true)
                request?.let { settle(it,DirectSourceTransitionStatus.FAILED,error.reason) }
            } catch(error: Exception) {
                if(evidence.generation==generation) update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_RESOURCE_CLEANUP_UNVERIFIED",playWhenReady=false,cleanupSafe=false), terminalConfirmed=true)
                request?.let { settle(it,DirectSourceTransitionStatus.FAILED,"DIRECT_RESOURCE_CLEANUP_UNVERIFIED") }
            } catch(error: LinkageError) {
                if(evidence.generation==generation) update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_RESOURCE_CLEANUP_UNVERIFIED",playWhenReady=false,cleanupSafe=false), terminalConfirmed=true)
                request?.let { settle(it,DirectSourceTransitionStatus.FAILED,"DIRECT_RESOURCE_CLEANUP_UNVERIFIED") }
            } finally { commands.finish();if(control===commands)control=null;releaseFocus() }
        }
        worker?.start()
        invalidateState()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val commands=control ?: return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_NOT_ACTIVE"))
        if(!evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING) return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_NOT_ACTIVE"))
        return try {
            val start=sourceStart ?: return unsupported("Playback intent")
            val already=start.playWhenReady==playWhenReady
            if(!start.requestPlay(playWhenReady)) {
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
            if(sourceStart?.requestSeek(target)!=true) return unsupported("Terminal source seek")
            update(evidence.copy(phase=IntegratedDirectPhase.SEEKING,pendingSeekSerial=commands.intent().seekSerial,notice=null))
            Futures.immediateVoidFuture()
        } catch(error: IllegalArgumentException) {Futures.immediateFailedFuture<Unit>(error)}
        catch(error: Exception) {update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_SEEK_CONTROL_FAILED",playWhenReady=false), terminalConfirmed=true);Futures.immediateFailedFuture<Unit>(error)}
        catch(error: LinkageError) {update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_SEEK_CONTROL_FAILED",playWhenReady=false), terminalConfirmed=true);Futures.immediateFailedFuture<Unit>(error)}
    }
    fun requestStop() {
        verifyApplicationThread()
        val pending=pendingSource;pendingSource=null
        val running=runRequest
        stopRun(forReplacement=false)
        pending?.let { settle(it,DirectSourceTransitionStatus.CANCELLED) }
        running?.let { settle(it,DirectSourceTransitionStatus.CANCELLED) }
    }
    private fun stopRun(forReplacement: Boolean) {
        if(evidence.busy || worker?.isCompleted==false) {
            if(stopRequested) {
                if(!forReplacement) stoppingForReplacement=false
                return
            }
            stopRequested=true
            stoppingForReplacement=forReplacement
            try { control?.stop() }
            catch(error: Exception) { control?.recordFailure(error) }
            catch(error: LinkageError) { control?.recordFailure(error) }
            finally {
                update(evidence.copy(phase=IntegratedDirectPhase.STOPPING,playWhenReady=false))
                try { usb.stopIntegratedFull() }
                catch(error: Exception) { control?.recordFailure(error) }
                catch(error: LinkageError) { control?.recordFailure(error) }
            }
        } else if(!forReplacement && worker?.isCompleted!=false && evidence.phase!=IntegratedDirectPhase.ERROR)
            update(evidence.copy(phase=IntegratedDirectPhase.STOPPED,playWhenReady=false,stoppedForReplacement=false), terminalConfirmed=true)
    }
    suspend fun stopAndJoin(): Boolean {
        requestStop()
        withContext(NonCancellable) { transitionJob?.join();worker?.join() }
        return evidence.cleanupSafe
    }
    override fun handleStop(): ListenableFuture<*> { requestStop(); return Futures.immediateVoidFuture() }
    // No CHANGE_MEDIA_ITEMS capability: only single-item library selection is supported.
    override fun handleAddMediaItems(index: Int,mediaItems: List<MediaItem>)=unsupported("Queue edit")
    override fun handleMoveMediaItems(fromIndex: Int,toIndex: Int,newIndex: Int)=unsupported("Queue edit")
    override fun handleReplaceMediaItems(fromIndex: Int,toIndex: Int,mediaItems: List<MediaItem>)=unsupported("Queue edit")
    override fun handleRemoveMediaItems(fromIndex: Int,toIndex: Int)=unsupported("Queue edit")
    private fun unsupported(command: String): ListenableFuture<*> { notice(command);return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_UNSUPPORTED_COMMAND")) }
    fun updateArtwork(item: MediaItem,uri: android.net.Uri?) {
        if(items.getOrNull(selected)?.mediaId!=item.mediaId) return
        if(items.getOrNull(selected)?.listeningEvidence()?.itemInstanceId!=item.listeningEvidence()?.itemInstanceId) return
        items=items.toMutableList().also { it[selected]=it[selected].buildUpon().setMediaMetadata(it[selected].mediaMetadata.buildUpon().setArtworkUri(uri).build()).build() }
        invalidateState()
    }
}
