package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import io.github.rsgarrido.sazanami.controller.*
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.junit.Assert.*

/** Shared Stage B/C Room, Media3, native-control and history fixture. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal abstract class DirectQueueTestFixture {
    protected val a=song(1)
    protected val b=song(2)
    protected val c=song(3)
    private fun song(id: Long)=Song(id,"Track $id","Artist",if(id==3L) "Other Album" else "Album",1,10000,
        Uri.parse("content://media/external/audio/media/$id"),"private","private",null)

    protected class Physical(items: List<MediaItem>,playing: Boolean) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var value=State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items.map { MediaItemData.Builder(it.listeningEvidence()!!.itemInstanceId).setMediaItem(it).setDurationUs(10_000_000).build() })
            // Keep the initial fake snapshot fixed even when READY/playing.
            .setCurrentMediaItemIndex(0).setContentPositionMs(PositionSupplier { 2000L }).setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
        override fun getState()=value
        override fun handleSetMediaItems(items: List<MediaItem>,index: Int,position: Long): ListenableFuture<*> {
            value=value.buildUpon().setPlaylist(items.map { MediaItemData.Builder(it.listeningEvidence()!!.itemInstanceId).setMediaItem(it).setDurationUs(10_000_000).build() })
                .setCurrentMediaItemIndex(index).setContentPositionMs(position).build()
            invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleStop(): ListenableFuture<*> {
            value=value.buildUpon().setPlaybackState(Player.STATE_IDLE).setPlayWhenReady(false,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleSetPlayWhenReady(playing: Boolean): ListenableFuture<*> {
            value=value.buildUpon().setPlayWhenReady(playing,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleSetRepeatMode(repeat: Int): ListenableFuture<*> {
            value=value.buildUpon().setRepeatMode(repeat).build();invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleSetVolume(volume: Float): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleSetPlaybackParameters(parameters: PlaybackParameters): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handlePrepare(): ListenableFuture<*> {
            value=value.buildUpon().setPlaybackState(Player.STATE_READY).build();invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleSetShuffleModeEnabled(shuffle: Boolean): ListenableFuture<*> {
            value=value.buildUpon().setShuffleModeEnabled(shuffle).build();invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleSeek(index: Int,position: Long,command: Int): ListenableFuture<*> {
            value=value.buildUpon().setCurrentMediaItemIndex(index)
                .setContentPositionMs(if(position==C.TIME_UNSET) 0L else position).build()
            invalidateState();return Futures.immediateVoidFuture()
        }
    }
    protected class Run(val uri: Uri,val control: UsbFullPlaybackControl,val progress: (UsbFullProgress)->Unit) {
        val terminal=CompletableDeferred<UsbSilentTransportExperimentResult>()
        val stopped=CompletableDeferred<Unit>()
        lateinit var initial: UsbFullControlIntent
        var frames=0L
        var position=0L
        var epoch=0L
        var serial=0L
        fun emit(phase: UsbFullTransportPhase,position: Long=this.position,epoch: Long=this.epoch,serial: Long=this.serial) {
            this.position=position;this.epoch=epoch;this.serial=serial
            progress(UsbFullProgress(frames,480000,48000,phase,mapOf("sourceSeekCapable" to 1L,
                "sourcePositionFrame" to position,"seekSourceEpoch" to epoch,"confirmedSeekSerial" to serial,"seekConfirmed" to epoch)))
        }
        fun submit(frames: Long) {
            check(!control.stopped && control.playWhenReady && control.intent().seekSerial<=serial)
            this.frames+=frames
        }
    }
    protected class Engine : FullUsbPlaybackEngineClient {
        val runs=mutableListOf<Run>()
        val started=Channel<Run>(Channel.UNLIMITED)
        var active=0
        var maximumActive=0
        var seekable=true
        var startupFailure: String?=null
        override suspend fun runIntegratedFull(uri: Uri,control: UsbFullPlaybackControl,progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult {
            startupFailure?.let { throw UsbIntegratedStartException(it) }
            val run=Run(uri,control,progress);runs+=run;active++;maximumActive=maxOf(active,maximumActive)
            try {
                progress(UsbFullProgress(0,480000,48000,UsbFullTransportPhase.STARTING,
                    mapOf("sourceSeekCapable" to if(seekable) 1L else 0L,"sourcePositionFrame" to 0L)))
                control.bind({true},{ run.stopped.complete(Unit) });run.initial=control.intent()
                started.send(run)
                return withContext(NonCancellable) { run.terminal.await() }
            } finally { control.finish();active-- }
        }
        override fun stopIntegratedFull() { runs.lastOrNull()?.stopped?.complete(Unit) }
        suspend fun next()=withTimeout(5000) { started.receive() }
    }
    protected inner class Fixture(private val playing: Boolean,androidPlayerFactory: (() -> Player)?=null) {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val items=listOf(a.toPlayableMediaItem("a-first"),b.toPlayableMediaItem("b"),a.toPlayableMediaItem("a-second"))
        var baseSongs=listOf(a,a,b)
        val normal=SmoothPlaybackPlayer(Physical(items,playing))
        val engine=Engine()
        var released=false
        var recreations=0
        var attachmentFailure: String?=null
        val gate=UsbAudioGenerationGate()
        val identity=UsbAudioDeviceIdentity("fake",1,2,3)
        var token=gate.select(identity)!!
        val reservation=UsbOutputReservation()
        // Direct-specific fixtures must opt in explicitly; production callbacks remain default-off.
        var experimentalOptIn=true
        private val controller=object : UsbOutputSelectionController {
            override fun authorizeIntegratedSelection(owner: UsbActivationOwner,identity: UsbAudioDeviceIdentity?,generation: Long)=
                if(reservation.acquire(owner,token)) null else "DIRECT_USB_RESERVATION_HELD"
            override fun integratedSelectionFailure(owner: UsbActivationOwner)=attachmentFailure ?:
                if(reservation.owns(owner)) null else "DIRECT_USB_RESERVATION_LOST"
            override fun commitIntegratedSelection(owner: UsbActivationOwner)=if(reservation.commit(owner)) null else "DIRECT_USB_RESERVATION_LOST"
            override fun rollbackIntegratedSelection(owner: UsbActivationOwner)=reservation.rollback(owner,engine.active>0)
            override fun releaseIntegratedSelection(owner: UsbActivationOwner,cleanupSafe: Boolean)=reservation.release(owner,cleanupSafe,engine.active>0)
        }
        val route=ExperimentalUsbPlaybackPlayer(normal,scope,engine,releaseNormal={ released=true;true },
            recreateNormal={
                kotlin.check(engine.active==0) { "Android recreation must follow the native owner join" }
                recreations++;androidPlayerFactory?.invoke() ?: Physical(items,false)
            },selection=UsbOutputSelectionCallbacks(experimentalOptIn={ experimentalOptIn }) { controller })
        val events=mutableListOf<Pair<String?,DirectListeningEvent>>()
        private val translator=DirectListeningEventTranslator()
        val database=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        val repository=PlaybackQueueRepository(database)
        val runtime=Media3PlaybackQueueRuntime(route,{false},{baseSongs},{},{ songs,shuffle ->
            baseSongs=songs;route.setLogicalShuffleModeEnabled(shuffle)
        })
        private var queueSerial=0
        var beforeResolve: (suspend ()->Unit)?=null
        var beforeIdentify: (suspend ()->Unit)?=null
        var resolutionUnavailable=false
        private val roomTracks=RoomPlaybackQueueTrackAccess(database,{listOf(a,b,c)})
        private val tracks=object : PlaybackQueueTrackAccess {
            override suspend fun identify(items: List<LivePlaybackQueueItem>): List<IdentifiedLivePlaybackQueueItem> {
                beforeIdentify?.invoke()
                return roomTracks.identify(items)
            }
            override suspend fun resolve(entries: List<io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity>): List<ResolvedPlaybackQueueItem> {
                beforeResolve?.invoke()
                return if(resolutionUnavailable) emptyList() else roomTracks.resolve(entries)
            }
        }
        var beforeCheckpoint: (suspend (String)->Unit)?=null
        private val roomPersistence=repository.asPlaybackQueuePersistence()
        private val persistence=object : PlaybackQueuePersistence by roomPersistence {
            override suspend fun updateSavedPlaybackState(queueId: String,currentEntryId: String?,currentPositionMs: Long,
                shuffleEnabled: Boolean,repeatMode: PersistedQueueRepeatMode) {
                beforeCheckpoint?.invoke(queueId)
                roomPersistence.updateSavedPlaybackState(queueId,currentEntryId,currentPositionMs,shuffleEnabled,repeatMode)
            }
        }
        val coordinator=PlaybackQueueCoordinator(persistence,tracks,
            runtime,{ "Queue $it" },queueIdFactory={ "queue-${++queueSerial}" },onActiveQueueChanged=PlaybackQueueRuntimeBridge::updateActiveQueueId)
        lateinit var originalId: String
        val contextResults=Channel<Boolean>(Channel.UNLIMITED)
        private val routeRevisions=MutableStateFlow(0L)
        val hub by lazy {
            route.addListener(object : Player.Listener {
                override fun onEvents(player: Player,events: Player.Events) { routeRevisions.value++ }
            })
            PlaybackQueueUiController(object : PlaybackQueueUiOperations {
                override fun observeQueues()=observeQueueOwnershipChanges(repository.observeQueues(),PlaybackQueueRuntimeBridge.revisions)
                override fun observeLiveActiveQueue()=combine(PlaybackQueueRuntimeBridge.revisions,routeRevisions,IntegratedDirectPlaybackBridge.state) { _, _, _ ->
                    val snapshot=coordinator.captureActiveQueueSnapshot() ?: return@combine null
                    val index=snapshot.entries.indexOfFirst { it.entryId==snapshot.currentEntryId }
                    LiveActiveQueueForUi(snapshot.entries.drop(index.coerceAtLeast(0)).mapNotNull {
                        item -> listOf(a,b,c).firstOrNull { it.id==item.evidence.reference.mediaStoreId }
                    },snapshot.entries.map { it.entryId },snapshot.currentEntryId,snapshot.shuffleEnabled,coordinator.getActiveQueueId())
                }
                override suspend fun listQueues()=repository.listQueues()
                override suspend fun getActiveQueueId()=repository.getActiveQueueId()
                override suspend fun loadQueue(queueId: String): LoadedQueueForUi? {
                    val saved=repository.loadQueue(queueId) ?: return null
                    val resolved=RoomPlaybackQueueTrackAccess(database,{listOf(a,b,c)}).resolve(saved.entries)
                        .associateBy { it.persistedEntry.entryId }
                    return LoadedQueueForUi(saved.queue,saved.entries.map { LoadedQueueEntryForUi(it,resolved[it.entryId]?.song) })
                }
                override suspend fun switchQueue(queueId: String)=coordinator.switchToQueue(queueId)
                override suspend fun createQueueFromCurrent()=coordinator.createQueueFromCurrent()?.queue?.queueId
                override suspend fun renameQueue(queueId: String,name: String)=repository.renameQueue(queueId,name)
                override suspend fun deleteQueue(queueId: String)=repository.deleteInactiveQueueIfNotLast(queueId)
            },scope)
        }
        suspend fun hubOwns(queueId: String?) {
            val controller=hub
            withTimeout(5000) { while(controller.state.value.isLoading || controller.state.value.activeQueueId!=queueId) yield() }
        }
        suspend fun assertRestoredAndroid() {
            forwarding { route.currentMediaItem?.listeningEvidence()?.itemInstanceId=="a-first" }
            hubOwns(originalId)
            val snapshot=checkNotNull(coordinator.captureActiveQueueSnapshot())
            assertEquals(originalId,coordinator.getActiveQueueId());assertEquals(originalId,repository.getActiveQueueId())
            assertEquals(originalId,PlaybackQueueRuntimeBridge.getActiveQueueId())
            assertEquals(listOf("a-first","b","a-second"),snapshot.entries.map { it.entryId })
            assertEquals(snapshot.entries.map { it.entryId },(0 until route.mediaItemCount).map { route.getMediaItemAt(it).listeningEvidence()!!.itemInstanceId })
            assertEquals("a-first",snapshot.currentEntryId);assertEquals(2000L,snapshot.currentPositionMs)
            assertFalse(route.playWhenReady);assertEquals(a,hub.state.value.queues.single { it.isActive }.currentTrack)
            assertEquals(listOf("a-first","b","a-second"),hub.state.value.activeEntries.map { it.entryId })
        }
        suspend fun initialize() {
            route.attachDirectListeningObserver(object : DirectPlaybackListeningObserver {
                override fun onActivated() { translator.activate() }
                override fun onDeactivated() { translator.deactivate() }
                override fun onObservation(item: MediaItem?,state: IntegratedDirectPlaybackState,terminalConfirmed: Boolean) {
                    val id=item?.listeningEvidence()?.itemInstanceId
                    translator.observe(id,state,terminalConfirmed).forEach { events+=id to it }
                }
            })
            coordinator.initialize();originalId=checkNotNull(coordinator.persistActiveQueueSnapshot())
            PlaybackQueueRuntimeBridge.register(coordinator);IntegratedDirectPlaybackBridge.attach(route)
            PlaybackQueueRuntimeBridge.registerContextRequest(coordinator) { songs,media,index,position,play,base,shuffle,repeat ->
                scope.launch { contextResults.send(coordinator.replacePlaybackContext(songs,media,index,position,play,base,shuffle,repeat)) }
            }
            route.attachQueueOutputObserver(coordinator)
            route.selectDirect(true);settled()
        }
        suspend fun settled() { withTimeout(5000) { while(route.directState.switching!=null) yield() } }
        suspend fun withController(block: suspend (PlaybackController)->Unit) {
            val playback=PlaybackController(InstrumentationRegistry.getInstrumentation().targetContext,scope)
            playback.setLibrarySongs(listOf(a,b,c))
            try { block(playback) } finally { playback.release() }
        }
        suspend fun start(): Run { route.prepare();val run=engine.next();confirm(run);return run }
        suspend fun startAt(index: Int=0,repeat: Int=Player.REPEAT_MODE_OFF): Run {
            assertEquals("Output activation must preserve the fixture's starting intent",playing,route.directRequestedPlaying)
            forwarding { directAdjacentCommands.all(route::isCommandAvailable) }
            // Cold restoration intentionally starts paused. Establish this test's requested
            // intent through the existing acknowledged source-start contract instead.
            val request=route.replaceDirectTimeline(items,index,playing,0,false,repeat)
            val run=engine.next()
            assertEquals("Native preparation must receive the requested intent",playing,run.initial.play)
            assertFalse("STARTING progress must not acknowledge readiness",request.isCompleted)
            assertFalse("Unacknowledged preparation must not permit checkpoints",route.directQueueCheckpointAllowed)
            confirm(run)
            val accepted=request.await()
            assertEquals(DirectSourceTransitionStatus.READY,accepted.status)
            assertEquals(route.directState.generation,accepted.nativeGeneration)
            assertTrue(route.directQueueCheckpointAllowed)
            assertEquals(if(playing) IntegratedDirectPhase.PLAYING else IntegratedDirectPhase.PAUSED,route.directState.phase)
            forwarding { route.currentMediaItemIndex==index && route.playbackState==Player.STATE_READY &&
                route.playWhenReady==playing && directAdjacentCommands.all(route::isCommandAvailable) }
            assertEquals(playing,route.isPlaying)
            return run
        }
        suspend fun accepted(entryId: String) {
            withTimeout(5000) { while(coordinator.captureActiveQueueSnapshot()?.currentEntryId!=entryId) yield() }
            forwarding { route.currentMediaItem?.listeningEvidence()?.itemInstanceId==entryId }
        }
        suspend fun navigate(old: Run,command: Int=Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM): Run {
            forwarding { route.isCommandAvailable(command) }
            when(command) {
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> route.seekToNextMediaItem()
                Player.COMMAND_SEEK_TO_NEXT -> route.seekToNext()
                Player.COMMAND_SEEK_TO_PREVIOUS -> route.seekToPrevious()
                else -> route.seekToPreviousMediaItem()
            }
            old.stopped.await();old.terminal.complete(terminal(old))
            return engine.next().also { confirm(it) }
        }
        fun confirm(run: Run) {
            if(run.initial.seekSerial>0) run.emit(UsbFullTransportPhase.PAUSED,run.initial.targetFrame,1,run.initial.seekSerial)
            if(run.control.playWhenReady) run.emit(UsbFullTransportPhase.PLAYING)
            else run.emit(UsbFullTransportPhase.PAUSED)
        }
        suspend fun saved(position: Long=3000,shuffle: Boolean=true,repeat: PersistedQueueRepeatMode=PersistedQueueRepeatMode.ALL): PlaybackQueueWithEntries {
            val queue=checkNotNull(coordinator.createInactiveQueue(listOf(a,b,a)))
            val selected=queue.entries.last().entryId
            repository.reorderEntry(queue.queue.queueId,selected,0,updateBaseOrder=false)
            repository.updateSavedPlaybackState(queue.queue.queueId,selected,position,shuffle,repeat)
            return checkNotNull(repository.loadQueue(queue.queue.queueId))
        }
        suspend fun forwarding(predicate: ()->Boolean) {
            val accepted=CompletableDeferred<Unit>()
            val listener=object : Player.Listener {
                override fun onEvents(player: Player,events: Player.Events) { if(predicate()) accepted.complete(Unit) }
            }
            route.addListener(listener)
            try { if(predicate()) accepted.complete(Unit);withTimeout(5000) { accepted.await() } }
            finally { route.removeListener(listener) }
        }
        suspend fun close() {
            route.stopDirect();engine.runs.forEach { it.terminal.complete(terminal(it)) }
            route.shutdown();PlaybackQueueRuntimeBridge.unregister(coordinator);IntegratedDirectPlaybackBridge.detach(route)
            try { route.releaseTransitionResources();route.release() }
            finally { scope.cancel();database.close() }
        }
    }
    protected fun check(playing: Boolean=true,androidPlayerFactory: (() -> Player)?=null,block: suspend (Fixture)->Unit)=runBlocking {
        withContext(Dispatchers.Main) {
            val oldSongs=PlaybackLibraryBridge.songs;PlaybackLibraryBridge.updateSongs(listOf(a,b,c))
            val f=Fixture(playing,androidPlayerFactory)
            var primaryFailure: Throwable?=null
            try { withTimeout(15000) { f.initialize();block(f) } }
            catch(error: Throwable) { primaryFailure=error;throw error }
            finally {
                try { f.close() }
                catch(cleanup: Throwable) {
                    if(primaryFailure!=null) primaryFailure.addSuppressed(cleanup) else throw cleanup
                } finally { PlaybackLibraryBridge.updateSongs(oldSongs) }
            }
        }
    }
    protected fun terminal(run: Run,safe: Boolean=true,status: UsbSilentStatus=UsbSilentStatus.CANCELLED)=
        UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=status,
            configuration=UsbAudioConfigurationResult(1,UsbAudioConfigurationResolution(),
                targetStatus=UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
                acClaim=UsbConfigurationAndroidOwnership(true,true),asClaim=UsbConfigurationAndroidOwnership(true,true),
                restorationStatus=if(safe) UsbConfigurationRestorationStatus.RESTORED else UsbConfigurationRestorationStatus.FAILED,
                asRelease=UsbConfigurationAndroidOwnership(true,true),acRelease=UsbConfigurationAndroidOwnership(true,true),closeStatus=UsbAudioCleanupStatus.SUCCEEDED),
            transport=UsbIsoTransportResult(1,2,mapOf("workerStopped" to 1L,"outstandingUrbsFinal" to 0L,"framesCompleted" to run.frames,
                "seekConfirmed" to run.epoch,"sourcePositionFrame" to run.position)),originalFlacFdClosed=true,
            flacSource=UsbFlacSourceResult(mapOf("totalSamples" to 480000L,"sampleRate" to 48000L,"producerJoined" to 1L)))

    /** Established full-EOF accounting fixture, at this engine's accepted 48 kHz/480000-frame source. */
    protected fun naturalResult(run: Run): UsbSilentTransportExperimentResult {
        val counters=UsbIsoNativeStats.fields.associateWith { 0L }.toMutableMap().apply { putAll(mapOf(
            "formatChannels" to 2L,"formatBitsPerSample" to 16L,"formatBytesPerSample" to 2L,
            "formatBytesPerFrame" to 4L,"formatSubslotBytes" to 2L,"formatValidBits" to 16L,"formatAlternate" to 9L,
            "schedulerNominalRateHz" to 48000L,"playbackTransportState" to 7L,"sourceMode" to 3L,"stopReason" to 14L,
            "started" to 1L,"workerStopped" to 1L,"state" to 7L,"streamInfoTotalSamples" to 480000L,
            "sourceFramesGenerated" to 480000L,"framesScheduled" to 480000L,"framesSubmitted" to 480000L,
            "framesCompleted" to 480000L,"prefixSourceFramesConsumed" to 480000L,"feedbackPacketsValid" to 1L,
            "feedbackScheduledPackets" to 1L,"trueEofReached" to 1L,"producerFinishedNaturally" to 1L,"md5VerificationPass" to 1L,
            "prefixProducerStopped" to 1L,"prefixProducerJoined" to 1L,"prefixInitialPrimedFrames" to 8192L,
            "finalSubmittedSourceFrame" to 479999L,"finalCompletedSourceFrame" to 479999L,"outUrbsSubmitted" to 10001L,
            "outUrbsCompleted" to 10001L,"outPacketsSubmitted" to 80003L,"outPacketsCompleted" to 80003L,
            "outBytesSubmitted" to 1920000L,"kernelOutActualBytes" to 1920000L,"terminalTailOutUrbs" to 1L,
            "terminalTailPackets" to 3L,"terminalTailFrames" to 101L,"remainingFramesAtTerminalTransition" to 101L,
            "terminalTailPacketCompletions" to 3L,"terminalTailFrameCompletions" to 101L,"wholeDecodedPcmHash" to 123L,
            "allEnqueuedPcmHash" to 123L,"sourcePcmHash" to 123L,"submittedPcmHash" to 123L)) }
        return terminal(run).copy(status=UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
            transport=UsbIsoTransportResult(1,2,counters))
    }

}
