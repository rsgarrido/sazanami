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
import com.google.common.collect.ImmutableList
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
    private var releasedDetachedGeneration: Long?=null
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
    private var logicalShuffle=false
    private var logicalRepeat=Player.REPEAT_MODE_OFF
    private var timelineConfirmed=true
    private var playlistItems: List<MediaItem>?=null
    private var playlistIndex=-1
    private var playlistDuration=-1L
    private var playlistSeekable=false
    private var playlistData=ImmutableList.of<MediaItemData>()
    var navigationRequested: ((Int,Long?)->Unit)?=null
    var shuffleRequested: ((Boolean)->Unit)?=null
    var repeatRequested: ((Int)->Unit)?=null
    private var automaticGeneration: Long?=null
    private var contextPreparations=0
    var preparationAllowed: ()->Boolean = { false }
    var preparationRequested: ((Long)->Unit)?=null
    var preparationCancelled: (()->Unit)?=null
    var stopRequestedObserver: (()->Unit)?=null
    var playWhenReadyRequested: ((Boolean)->Unit)?=null
    private data class SeekAcknowledgment(val id: Long,val generation: Long,val serial: Long,val positionMs: Long,
        val result: CompletableDeferred<DirectSourceTransitionResult> = CompletableDeferred())
    private var seekAcknowledgment: SeekAcknowledgment?=null
    private val intent get()=pendingSource ?: runRequest?.takeUnless { it.result.isCompleted }
    val intentItems get()=intent?.timeline ?: items
    val intentIndex get()=intent?.index ?: selected
    val intentPositionMs get()=intent?.positionMs ?: seekAcknowledgment?.positionMs ?: evidence.positionMs
    val canCheckpointTimeline get()=timelineConfirmed && pendingSource==null
    val requestedPlaying get()=pendingSource?.playing ?: if(evidence.phase in setOf(IntegratedDirectPhase.STOPPING,
        IntegratedDirectPhase.STOPPED,IntegratedDirectPhase.ENDED,IntegratedDirectPhase.ERROR)) false
        else sourceStart?.playWhenReady ?: initialPlayingIntent
    fun canReplaceTimeline(): Boolean=scope.isActive && sourceAdmissionFailure()==null &&
        (evidence.cleanupSafe || worker?.isCompleted==false)
    fun canPrepareCurrent(generation: Long)=generation==evidence.generation &&
        evidence.phase==IntegratedDirectPhase.IDLE && evidence.cleanupSafe && source!=null &&
        selected in items.indices && contextPreparations==0 && pendingSource==null &&
        worker?.isCompleted!=false && transitionJob?.isCompleted!=false && canReplaceTimeline()
    fun prepareCurrent(generation: Long): Deferred<DirectSourceTransitionResult> {
        verifyApplicationThread()
        if(!canPrepareCurrent(generation)) return CompletableDeferred(DirectSourceTransitionResult(
            ++nextRequestId,DirectSourceTransitionStatus.REJECTED,error="DIRECT_NOT_IDLE"))
        return replaceTimeline(items,selected,true,evidence.positionMs,logicalShuffle,logicalRepeat)
    }
    /** Output admission changes outside native evidence also affect the advertised commands. */
    fun refreshCommandAvailability() {
        verifyApplicationThread()
        invalidateState()
    }
    fun canAdmitTimeline(mediaItems: List<MediaItem>,index: Int,positionMs: Long,repeat: Int): Boolean =
        canReplaceTimeline() && positionMs>=0 && index in mediaItems.indices &&
            mediaItems.all { it.listeningEvidence()!=null } &&
            mediaItems.map { it.listeningEvidence()?.itemInstanceId }.distinct().size==mediaItems.size &&
            librarySong(mediaItems[index].mediaId)!=null && repeat in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL

    private class SourceRequest(
        val id: Long, var timeline: List<MediaItem>, var index: Int, val song: Song?,
        val playing: Boolean, val positionMs: Long, var shuffle: Boolean, var repeat: Int
    ) {
        val item get()=timeline[index]
        val result=CompletableDeferred<DirectSourceTransitionResult>()
        var generation: Long?=null
        fun state(status: DirectSourceTransitionStatus,error: String?=null)=DirectSourceTransitionState(
            id,item.mediaId,item.listeningEvidence()?.itemInstanceId,playing,positionMs,status,error)
    }

    /** Stage A execution contract. It does not mutate a queue or advertise multi-item commands. */
    fun replaceSource(item: MediaItem,playing: Boolean,startPositionMs: Long=0L): Deferred<DirectSourceTransitionResult> {
        return replaceTimeline(listOf(item),0,playing,startPositionMs,false,Player.REPEAT_MODE_OFF)
    }

    /** The service's existing queue supplies order and occurrence IDs; no traversal lives here. */
    fun replaceTimeline(mediaItems: List<MediaItem>,index: Int,playing: Boolean,startPositionMs: Long,
        shuffle: Boolean,repeat: Int): Deferred<DirectSourceTransitionResult> {
        verifyApplicationThread()
        if(mediaItems.isEmpty() || index !in mediaItems.indices ||
            mediaItems.withIndex().any { it.index!=index && it.value.listeningEvidence()==null } ||
            mediaItems.map { it.listeningEvidence()?.itemInstanceId }.distinct().size!=mediaItems.size ||
            repeat !in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL)
            return CompletableDeferred(DirectSourceTransitionResult(++nextRequestId,
                DirectSourceTransitionStatus.REJECTED,error="DIRECT_TIMELINE_INVALID"))
        val item=mediaItems[index]
        val request=SourceRequest(++nextRequestId,mediaItems.toList(),index,librarySong(item.mediaId),playing,startPositionMs,shuffle,repeat)
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
        automaticGeneration=null
        finishSeek(DirectSourceTransitionStatus.SUPERSEDED)
        timelineConfirmed=false
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
                        logicalShuffle=target.shuffle;logicalRepeat=target.repeat
                        acceptSource(target.timeline,target.index,target.positionMs,target.playing,target)
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
        if(request.result.isCompleted) return
        if(evidence.sourceTransition?.requestId==request.id &&
            status in setOf(DirectSourceTransitionStatus.READY,DirectSourceTransitionStatus.COMPLETED))
            timelineConfirmed=true
        if(request.result.complete(DirectSourceTransitionResult(request.id,status,request.generation,error)) &&
            evidence.sourceTransition?.requestId==request.id)
            update(evidence.copy(sourceTransition=request.state(status,error)))
    }

    private fun acceptSource(mediaItems: List<MediaItem>,index: Int,positionMs: Long,playing: Boolean,request: SourceRequest?=null,idleOnly: Boolean=false) {
        // Only logical idle adoption may use the generation whose verified reservation was retired.
        val retiredIdle=idleOnly && releasedDetachedGeneration==evidence.generation
        check((evidence.cleanupSafe || retiredIdle) && worker?.isCompleted!=false)
        items=mediaItems.toList();selected=index
        source=request?.song ?: items.getOrNull(selected)?.mediaId?.let(librarySong)
        startPositionMs=positionMs;initialPlayingIntent=playing
        sourceStart=null;control=null;stoppingForReplacement=false
        update(IntegratedDirectPlaybackState(enabled=true,normalReleased=true,generation=++nextGeneration,
            libraryId=source?.id ?: items.getOrNull(selected)?.mediaId?.toLongOrNull()?.takeIf { idleOnly },
            metadataDurationMs=source?.duration ?: items.getOrNull(selected)?.listeningEvidence()?.reference?.duration ?: 0,
            playWhenReady=playing,idlePositionMs=positionMs,
            queueMediaIds=items.map { it.mediaId },queueIndex=selected,queueShuffle=logicalShuffle,queueRepeat=logicalRepeat,
            sourceTransition=request?.state(DirectSourceTransitionStatus.PREPARING),
            phase=if(source==null && items.isNotEmpty() && !idleOnly) IntegratedDirectPhase.ERROR else IntegratedDirectPhase.IDLE,
            error=if(source==null && items.isNotEmpty() && !idleOnly) "UNSUPPORTED_TRACK" else null))
    }
    private fun update(value: IntegratedDirectPlaybackState, terminalConfirmed: Boolean=false) {
        evidence=value
        listeningChanged(items.getOrNull(selected), value, terminalConfirmed)
        if(evidence===value) changed(value) // A synchronous observer may already own a newer request/state.
        invalidateState()
    }
    fun notice(command: String) { update(evidence.copy(notice="$command is unavailable during the current USB operation")) }
    override fun getState(): State {
        val seekable=evidence.seekCapable && evidence.busy && evidence.phase!=IntegratedDirectPhase.STOPPING
        fun itemData(index: Int,item: MediaItem): MediaItemData {
            val duration=if(index==selected) evidence.durationMs else item.listeningEvidence()?.reference?.duration
            return MediaItemData.Builder(item.listeningEvidence()?.itemInstanceId ?: "${item.mediaId}:$index")
                .setMediaItem(item).setMediaMetadata(item.mediaMetadata).setIsSeekable(index==selected && seekable)
                .setDurationUs(duration?.coerceIn(0,Long.MAX_VALUE/1000)?.times(1000) ?: C.TIME_UNSET).build()
        }
        // Native position polling must not reconstruct every window/evidence bundle in a full queue.
        if(playlistItems!==items || playlistIndex!=selected) {
            playlistData=ImmutableList.copyOf(items.mapIndexed(::itemData))
            playlistItems=items;playlistIndex=selected
        } else if(items.isNotEmpty() && (playlistDuration!=evidence.durationMs || playlistSeekable!=seekable)) {
            playlistData=ImmutableList.copyOf(playlistData.toMutableList().also { it[selected]=itemData(selected,items[selected]) })
        }
        playlistDuration=evidence.durationMs;playlistSeekable=seekable
        val playlist=playlistData
        val playbackState=when(evidence.phase) {
            IntegratedDirectPhase.PREPARING,IntegratedDirectPhase.PAUSING,IntegratedDirectPhase.RESUMING,IntegratedDirectPhase.SEEKING -> Player.STATE_BUFFERING
            IntegratedDirectPhase.PLAYING,IntegratedDirectPhase.PAUSED -> Player.STATE_READY
            IntegratedDirectPhase.ENDED -> Player.STATE_ENDED
            else -> Player.STATE_IDLE
        }
        val commands=Player.Commands.Builder().addAll(*integratedDirectCommands)
        if(items.isEmpty() || !canReplaceTimeline()) commands.remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        if(items.isEmpty() || !canReplaceTimeline() || navigationRequested==null) {
            commands.remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        if(repeatRequested==null || !canReplaceTimeline()) commands.remove(Player.COMMAND_SET_REPEAT_MODE)
        if(shuffleRequested==null || !canReplaceTimeline()) commands.remove(Player.COMMAND_SET_SHUFFLE_MODE)
        val activeControl=control!=null && worker?.isCompleted==false && evidence.busy &&
            evidence.phase!=IntegratedDirectPhase.STOPPING && sourceAdmissionFailure()==null
        val idlePreparation=preparationRequested!=null && preparationAllowed() && canPrepareCurrent(evidence.generation)
        if(!activeControl && !idlePreparation) commands.remove(Player.COMMAND_PLAY_PAUSE)
        if(!activeControl || !evidence.seekCapable) commands.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        return State.Builder().setAvailableCommands(commands.build())
            .setPlaylist(playlist).setCurrentMediaItemIndex(if(playlist.isEmpty()) C.INDEX_UNSET else selected)
            .setShuffleModeEnabled(logicalShuffle).setRepeatMode(logicalRepeat)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(evidence.busy && evidence.phase!=IntegratedDirectPhase.STOPPING && evidence.playWhenReady,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            // A supplier prevents Media3 from extrapolating USB position using its elapsed clock.
            .setContentPositionMs(PositionSupplier { evidence.positionMs })
            .setPlayerError(if(evidence.phase==IntegratedDirectPhase.ERROR) PlaybackException(evidence.error ?: "DIRECT_FAILED",null,PlaybackException.ERROR_CODE_UNSPECIFIED) else null)
            .build()
    }
    /** Reconstructing a logical queue does not acquire a native source or USB resources. */
    fun restoreIdleTimeline(mediaItems: List<MediaItem>,index: Int,positionMs: Long,shuffle: Boolean,repeat: Int,playing: Boolean=false) {
        verifyApplicationThread()
        check((evidence.cleanupSafe || releasedDetachedGeneration==evidence.generation) && worker?.isCompleted!=false && pendingSource==null)
        require(positionMs>=0 && (mediaItems.isEmpty() || index in mediaItems.indices))
        logicalShuffle=shuffle;logicalRepeat=repeat;timelineConfirmed=true;runRequest=null
        acceptSource(mediaItems,if(mediaItems.isEmpty()) 0 else index,positionMs,playing,idleOnly=true)
    }
    fun selectOccurrence(index: Int,positionMs: Long): Deferred<DirectSourceTransitionResult> {
        verifyApplicationThread()
        if(index==selected && pendingSource==null && timelineConfirmed && positionMs==C.TIME_UNSET)
            return CompletableDeferred(DirectSourceTransitionResult(++nextRequestId,DirectSourceTransitionStatus.UNCHANGED,
                evidence.generation.takeIf { worker?.isCompleted==false }))
        return replaceTimeline(intentItems,index,requestedPlaying,if(positionMs==C.TIME_UNSET) 0 else positionMs,logicalShuffle,logicalRepeat)
    }
    fun invalidateAutomaticAdvance() { automaticGeneration=null }
    fun beginContextPreparation() { contextPreparations++;invalidateAutomaticAdvance();invalidateState() }
    fun endContextPreparation() { check(contextPreparations>0);contextPreparations--;invalidateState() }
    fun canAdvanceGeneration(generation: Long)=automaticGeneration==generation && evidence.generation==generation &&
        evidence.phase==IntegratedDirectPhase.ENDED && pendingSource==null && evidence.cleanupSafe &&
        !stopRequested && sourceAdmissionFailure()==null && scope.isActive

    /** Media3's timeline remains the traversal authority, including Repeat One's manual exemption. */
    fun navigate(command: Int,generation: Long?): Deferred<DirectSourceTransitionResult> {
        verifyApplicationThread()
        fun unchanged()=CompletableDeferred(DirectSourceTransitionResult(++nextRequestId,DirectSourceTransitionStatus.UNCHANGED))
        val automatic=generation!=null
        require(if(automatic) command==DIRECT_NATURAL_ADVANCE else command in directAdjacentCommands)
        if(!automatic) invalidateAutomaticAdvance()
        if(automatic && !canAdvanceGeneration(requireNotNull(generation))) return unchanged()
        if(!canReplaceTimeline() || intentItems.isEmpty()) return CompletableDeferred(
            DirectSourceTransitionResult(++nextRequestId,DirectSourceTransitionStatus.REJECTED,error="DIRECT_NAVIGATION_UNAVAILABLE"))
        val timelineItems=intentItems
        val current=intentIndex
        // This may be a pending intention whose full timeline has not yet replaced the old run.
        val timeline=State.Builder().setPlaylist(timelineItems.mapIndexed { index,item ->
            MediaItemData.Builder(item.listeningEvidence()?.itemInstanceId ?: index).setMediaItem(item).build()
        }).build().timeline
        val repeat=if(!automatic && logicalRepeat==Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else logicalRepeat
        val previous=command==Player.COMMAND_SEEK_TO_PREVIOUS || command==Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        val adjacent=if(previous) timeline.getPreviousWindowIndex(current,repeat,false)
            else timeline.getNextWindowIndex(current,repeat,false)
        val resolved=resolveMediaTimelineSeek(current,intentPositionMs,adjacent,C.TIME_UNSET,command)
        // BasePlayer.seekToPrevious() restarts at the first seekable item. Its item-only variant
        // has a no-op boundary; both variants still use Sazanami's strict >3000 ms override.
        val restartAtFirst=command==Player.COMMAND_SEEK_TO_PREVIOUS && adjacent==C.INDEX_UNSET
        if(!automatic && (resolved.seekCommand==Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM || restartAtFirst)) {
            invalidateAutomaticAdvance()
            if(intent==null && evidence.seekCapable && evidence.busy) {
                return seekWithinCurrent(0)
            }
            if(intent==null && evidence.busy) return CompletableDeferred(DirectSourceTransitionResult(
                ++nextRequestId,DirectSourceTransitionStatus.REJECTED,error="DIRECT_SEEK_UNAVAILABLE"))
            return replaceTimeline(timelineItems,current,requestedPlaying,0,logicalShuffle,logicalRepeat)
        }
        if(adjacent==C.INDEX_UNSET) {
            if(automatic) automaticGeneration=null
            // A boundary press must not orphan the accepted checkpoint of an earlier pending
            // command. The newest queue operation can acknowledge that same intended selection.
            return intent?.result ?: unchanged()
        }
        if(automatic) automaticGeneration=null
        return replaceTimeline(timelineItems,adjacent,if(automatic) true else requestedPlaying,0,logicalShuffle,logicalRepeat)
    }

    fun setQueueRepeat(repeat: Int): Boolean {
        if(repeat !in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL || !canReplaceTimeline()) return false
        logicalRepeat=repeat
        pendingSource?.repeat=repeat;runRequest?.repeat=repeat
        update(evidence.copy(queueRepeat=repeat))
        return true
    }

    /** Logical-only edits keep the physical occurrence, control mailbox, generation and attempt. */
    fun setQueueOrder(ids: List<String>,shuffle: Boolean): Boolean {
        val target=intentItems
        val byId=target.associateBy { it.listeningEvidence()?.itemInstanceId }
        if(ids.size!=target.size || ids.distinct().size!=ids.size || ids.toSet()!=byId.keys || !canReplaceTimeline()) return false
        fun reorder(request: SourceRequest?) {
            if(request==null) return
            val id=request.item.listeningEvidence()?.itemInstanceId
            request.timeline=ids.map { checkNotNull(byId[it]) };request.index=ids.indexOf(id);request.shuffle=shuffle
        }
        reorder(pendingSource)
        if(runRequest!==pendingSource) reorder(runRequest?.takeUnless { it.result.isCompleted })
        if(items.map { it.listeningEvidence()?.itemInstanceId }.toSet()==ids.toSet()) {
            val current=items.getOrNull(selected)?.listeningEvidence()?.itemInstanceId
            items=ids.map { checkNotNull(byId[it]) };selected=ids.indexOf(current)
        }
        logicalShuffle=shuffle
        update(evidence.copy(queueMediaIds=items.map { it.mediaId },queueIndex=selected,queueShuffle=shuffle))
        return true
    }

    fun editQueue(updated: List<MediaItem>): Boolean {
        if(!canCheckpointTimeline || !canReplaceTimeline() || items.isEmpty()) return false
        val id=items[selected].listeningEvidence()?.itemInstanceId ?: return false
        val newIndex=updated.indexOfFirst { it.listeningEvidence()?.itemInstanceId==id }
        if(newIndex<0 || updated.any { it.listeningEvidence()==null } ||
            updated.map { it.listeningEvidence()?.itemInstanceId }.distinct().size!=updated.size) return false
        // Replacing the physical source under an existing occurrence is not a metadata edit.
        val current=updated[newIndex]
        if(current.mediaId!=items[selected].mediaId || current.localConfiguration!=items[selected].localConfiguration) return false
        items=updated.toList();selected=newIndex
        update(evidence.copy(queueMediaIds=items.map { it.mediaId },queueIndex=selected))
        return true
    }
    fun restartSelectedFromZero()=replaceTimeline(items,selected,true,0,logicalShuffle,logicalRepeat)
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
        timelineConfirmed=true
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
        if(source==null) source=items.getOrNull(selected)?.mediaId?.let(librarySong)
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
        timelineConfirmed=false
        val start=DirectSourceStart(startPositionMs,initialPlayingIntent);sourceStart=start
        val commands=start.control;control=commands
        val generation=++nextGeneration
        request?.generation=generation
        val preparing=evidence.copy(generation=generation,phase=IntegratedDirectPhase.PREPARING,completedFrames=0,totalFrames=0,sampleRateHz=0,error=null,notice=null,engine=null,cleanupSafe=false,detachedResourcesReleased=false,playWhenReady=start.playWhenReady,stoppedForReplacement=false,
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
                                val ready=!start.positionPending &&
                                    ((accepted.phase==IntegratedDirectPhase.PLAYING && start.playWhenReady) ||
                                        (accepted.phase==IntegratedDirectPhase.PAUSED && !start.playWhenReady))
                                if(ready) timelineConfirmed=true
                                update(accepted)
                                seekAcknowledgment?.takeIf { it.generation==generation &&
                                    (progress.controlCounters["confirmedSeekSerial"] ?: 0L)>=it.serial }?.let {
                                    finishSeek(DirectSourceTransitionStatus.READY)
                                }
                                if(ready)
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
                if(terminal.phase==IntegratedDirectPhase.ENDED) timelineConfirmed=true
                update(terminal.copy(stoppedForReplacement=terminal.phase==IntegratedDirectPhase.STOPPED && stoppingForReplacement), terminalConfirmed=true)
                finishSeek(if(terminal.phase==IntegratedDirectPhase.ENDED) DirectSourceTransitionStatus.COMPLETED
                    else if(terminal.phase==IntegratedDirectPhase.ERROR) DirectSourceTransitionStatus.FAILED
                    else DirectSourceTransitionStatus.CANCELLED,terminal.error)
                request?.let { settle(it,when(terminal.phase) {
                    IntegratedDirectPhase.ENDED -> DirectSourceTransitionStatus.COMPLETED
                    IntegratedDirectPhase.ERROR -> DirectSourceTransitionStatus.FAILED
                    else -> DirectSourceTransitionStatus.CANCELLED
                },terminal.error) }
                if(terminal.phase==IntegratedDirectPhase.ENDED && evidence.generation==generation &&
                    !stopRequested && pendingSource==null && contextPreparations==0) {
                    automaticGeneration=generation
                    // The worker must finish its finally/releaseFocus before a successor joins it.
                    // The callback also lets an early-EOF activation finish its queue transaction.
                    applicationHandler.post {
                        if(canAdvanceGeneration(generation)) navigationRequested?.invoke(DIRECT_NATURAL_ADVANCE,generation)
                    }
                }
            } catch(error: UsbIntegratedStartException) {
                if(evidence.generation==generation) update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error=error.reason,playWhenReady=false,cleanupSafe=error.reason!="DIRECT_RESOURCE_CLEANUP_UNVERIFIED"), terminalConfirmed=true)
                request?.let { settle(it,DirectSourceTransitionStatus.FAILED,error.reason) }
                finishSeek(DirectSourceTransitionStatus.FAILED,error.reason)
            } catch(error: Exception) {
                if(evidence.generation==generation) update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_RESOURCE_CLEANUP_UNVERIFIED",playWhenReady=false,cleanupSafe=false), terminalConfirmed=true)
                request?.let { settle(it,DirectSourceTransitionStatus.FAILED,"DIRECT_RESOURCE_CLEANUP_UNVERIFIED") }
                finishSeek(DirectSourceTransitionStatus.FAILED,"DIRECT_RESOURCE_CLEANUP_UNVERIFIED")
            } catch(error: LinkageError) {
                if(evidence.generation==generation) update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_RESOURCE_CLEANUP_UNVERIFIED",playWhenReady=false,cleanupSafe=false), terminalConfirmed=true)
                request?.let { settle(it,DirectSourceTransitionStatus.FAILED,"DIRECT_RESOURCE_CLEANUP_UNVERIFIED") }
                finishSeek(DirectSourceTransitionStatus.FAILED,"DIRECT_RESOURCE_CLEANUP_UNVERIFIED")
            } finally { commands.finish();if(control===commands)control=null;releaseFocus() }
        }
        worker?.start()
        invalidateState()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // Media3 can still hold cached command availability while consent withdrawal is queued.
        // Pause/stop remain usable for orderly teardown; resuming must revalidate access now.
        if(playWhenReady) sourceAdmissionFailure()?.let {
            return Futures.immediateFailedFuture<Unit>(IllegalStateException(it))
        }
        val commands=control ?: run {
            if(!playWhenReady) {
                playWhenReadyRequested?.invoke(false)
                preparationCancelled?.invoke()
            } else if(preparationAllowed() && canPrepareCurrent(evidence.generation)) {
                playWhenReadyRequested?.invoke(true)
                preparationRequested?.invoke(evidence.generation)
            } else return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_NOT_ACTIVE"))
            invalidateState()
            return Futures.immediateVoidFuture()
        }
        if(!evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING) return Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_NOT_ACTIVE"))
        playWhenReadyRequested?.invoke(playWhenReady)
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
        if(seekCommand in directAdjacentCommands) {
            invalidateAutomaticAdvance()
            navigationRequested?.invoke(seekCommand,null)
            return Futures.immediateVoidFuture()
        }
        if(seekCommand==Player.COMMAND_SEEK_TO_MEDIA_ITEM) {
            if(mediaItemIndex !in items.indices || !canReplaceTimeline()) return unsupported("Queue selection")
            selectOccurrence(mediaItemIndex,positionMs)
            // Acceptance only. Queue transactions await the request-specific native result directly.
            return Futures.immediateVoidFuture()
        }
        if(mediaItemIndex!=selected || seekCommand!=Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM || !evidence.seekCapable || !evidence.busy || evidence.phase==IntegratedDirectPhase.STOPPING)
            return unsupported("Source seek")
        seekWithinCurrent(positionMs)
        return Futures.immediateVoidFuture()
    }
    private fun finishSeek(status: DirectSourceTransitionStatus,error: String?=null) {
        val request=seekAcknowledgment ?: return
        seekAcknowledgment=null
        request.result.complete(DirectSourceTransitionResult(request.id,status,request.generation,error))
    }
    /** Uses the existing seek mailbox and epoch evidence; no source replacement or new attempt. */
    private fun seekWithinCurrent(positionMs: Long): Deferred<DirectSourceTransitionResult> {
        fun rejected(reason: String)=CompletableDeferred(DirectSourceTransitionResult(++nextRequestId,
            DirectSourceTransitionStatus.REJECTED,error=reason))
        sourceAdmissionFailure()?.let { return rejected(it) }
        return try {
            val commands=control ?: return rejected("DIRECT_SEEK_UNAVAILABLE")
            val target=directSeekTargetFrame(positionMs,evidence.sampleRateHz,evidence.totalFrames)
            if(sourceStart?.requestSeek(target)!=true) return rejected("DIRECT_SEEK_UNAVAILABLE")
            val request=SeekAcknowledgment(++nextRequestId,evidence.generation,commands.intent().seekSerial,positionMs)
            val superseded=seekAcknowledgment
            seekAcknowledgment=request
            update(evidence.copy(phase=IntegratedDirectPhase.SEEKING,pendingSeekSerial=commands.intent().seekSerial,notice=null))
            superseded?.result?.complete(DirectSourceTransitionResult(superseded.id,DirectSourceTransitionStatus.SUPERSEDED,superseded.generation))
            request.result
        } catch(error: IllegalArgumentException) { rejected("DIRECT_SEEK_INVALID") }
        catch(error: Exception) {
            update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_SEEK_CONTROL_FAILED",playWhenReady=false),terminalConfirmed=true)
            finishSeek(DirectSourceTransitionStatus.FAILED,"DIRECT_SEEK_CONTROL_FAILED")
            rejected("DIRECT_SEEK_CONTROL_FAILED")
        } catch(error: LinkageError) {
            update(evidence.copy(phase=IntegratedDirectPhase.ERROR,error="DIRECT_SEEK_CONTROL_FAILED",playWhenReady=false),terminalConfirmed=true)
            finishSeek(DirectSourceTransitionStatus.FAILED,"DIRECT_SEEK_CONTROL_FAILED")
            rejected("DIRECT_SEEK_CONTROL_FAILED")
        }
    }
    fun requestStop() {
        verifyApplicationThread()
        stopRequestedObserver?.invoke()
        preparationCancelled?.invoke()
        invalidateAutomaticAdvance()
        finishSeek(DirectSourceTransitionStatus.CANCELLED)
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
    fun detachedResourcesReleased(owner: UsbActivationOwner): Boolean {
        if (worker?.isCompleted==false || transitionJob?.isCompleted==false) return false
        val result=evidence.engine ?: return false
        return result.detachedResourceRelease?.matches(owner,result)==true
    }
    /** A fresh output activation may adopt idle state only after routing released the proven old reservation. */
    fun retireDetachedOwner(owner: UsbActivationOwner) {
        check(detachedResourcesReleased(owner))
        releasedDetachedGeneration=evidence.generation
    }
    override fun handleStop(): ListenableFuture<*> { requestStop(); return Futures.immediateVoidFuture() }
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeatRequested?.invoke(repeatMode)
        return Futures.immediateVoidFuture()
    }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        shuffleRequested?.invoke(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }
    // Broad Media3 playlist editing stays unavailable. The queue runtime admits only checked,
    // occurrence-preserving edits through editQueue; source changes use acknowledged requests.
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
