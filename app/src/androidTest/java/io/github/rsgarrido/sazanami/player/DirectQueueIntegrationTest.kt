package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Test
import org.junit.runner.RunWith

/** Existing Room coordinator/runtime and actual Media3 routing, with controlled native completions. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DirectQueueIntegrationTest {
    private val a=song(1)
    private val b=song(2)
    private val c=song(3)
    private fun song(id: Long)=Song(id,"Track $id","Artist","Album",1,10000,
        Uri.parse("content://media/external/audio/media/$id"),"private","private",null)

    private class Physical(items: List<MediaItem>,playing: Boolean) : SimpleBasePlayer(Looper.getMainLooper()) {
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
    private class Run(val uri: Uri,val control: UsbFullPlaybackControl,val progress: (UsbFullProgress)->Unit) {
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
    private class Engine : FullUsbPlaybackEngineClient {
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
    private inner class Fixture(playing: Boolean) {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val items=listOf(a.toPlayableMediaItem("a-first"),b.toPlayableMediaItem("b"),a.toPlayableMediaItem("a-second"))
        var baseSongs=listOf(a,a,b)
        val normal=SmoothPlaybackPlayer(Physical(items,playing))
        val engine=Engine()
        var released=false
        var recreations=0
        var attachmentFailure: String?=null
        val gate=UsbAudioGenerationGate()
        val token=gate.select(UsbAudioDeviceIdentity("fake",1,2,3))!!
        val reservation=UsbOutputReservation()
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
            recreateNormal={ recreations++;Physical(items,false) },selection=UsbOutputSelectionCallbacks { controller })
        val events=mutableListOf<Pair<String?,DirectListeningEvent>>()
        private val translator=DirectListeningEventTranslator()
        val database=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        val repository=PlaybackQueueRepository(database)
        val runtime=Media3PlaybackQueueRuntime(route,{false},{baseSongs},{},{ songs,shuffle ->
            baseSongs=songs;route.setLogicalShuffleModeEnabled(shuffle)
        })
        private var queueSerial=0
        val coordinator=PlaybackQueueCoordinator(repository.asPlaybackQueuePersistence(),RoomPlaybackQueueTrackAccess(database,{listOf(a,b,c)}),
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
        suspend fun start(): Run { route.prepare();val run=engine.next();confirm(run);return run }
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
    private fun check(playing: Boolean=true,block: suspend (Fixture)->Unit)=runBlocking {
        withContext(Dispatchers.Main) {
            val oldSongs=PlaybackLibraryBridge.songs;PlaybackLibraryBridge.updateSongs(listOf(a,b,c))
            val f=Fixture(playing)
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
    private fun terminal(run: Run,safe: Boolean=true,status: UsbSilentStatus=UsbSilentStatus.CANCELLED)=
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
    private fun naturalResult(run: Run): UsbSilentTransportExperimentResult {
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

    @Test fun outputAdoptsCompleteIdleTimelineWithoutPreparingNativeAudio()=check { f ->
        assertEquals(3,f.route.queuePlayer.mediaItemCount);assertEquals(0,f.engine.runs.size)
        assertEquals(2000L,f.route.queuePlayer.currentPosition);assertFalse(f.route.isPlaying)
        assertEquals(listOf("a-first","b","a-second"),f.runtime.captureSnapshot()?.entries?.map { it.entryId })
        assertEquals(listOf("a-first","a-second","b"),f.coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
        f.forwarding { f.route.mediaItemCount==3 }
        assertEquals(0,f.route.currentMediaItemIndex)
    }

    @Test fun currentIdleOccurrenceIsANoOpWithoutClaimingNativeReadiness()=check { f ->
        val result=f.route.selectDirectQueueOccurrence(0).await()
        assertEquals(DirectSourceTransitionStatus.UNCHANGED,result.status);assertNull(result.nativeGeneration)
        assertTrue(PlaybackQueueRuntimeBridge.playQueueEntry(f.originalId,"a-first"))
        assertEquals(0,f.engine.runs.size);assertTrue(f.events.isEmpty());assertFalse(f.route.isPlaying)
    }

    @Test fun exactDuplicateSelectionWaitsForCleanupAndReadyBeforeCheckpointingAndNoOpDoesNotRestart()=check { f ->
        val old=f.start()
        val result=f.scope.async { PlaybackQueueRuntimeBridge.playQueueEntry(f.originalId,"a-second") }
        old.stopped.await();assertFalse(result.isCompleted)
        assertNull(f.coordinator.captureActiveQueueSnapshot())
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals("a-first",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        old.terminal.complete(terminal(old))
        val next=f.engine.next();assertEquals(a.uri,next.uri)
        assertEquals(2,f.route.queuePlayer.currentMediaItemIndex);assertFalse(result.isCompleted)
        f.confirm(next);assertTrue(result.await())
        assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        assertEquals(1,f.engine.maximumActive)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
        assertEquals("a-second",f.events.last().first)
        val count=f.engine.runs.size
        assertTrue(PlaybackQueueRuntimeBridge.playQueueEntry(f.originalId,"a-second"));assertEquals(count,f.engine.runs.size)
        f.forwarding { f.route.currentMediaItemIndex==2 }
        assertEquals("a-second",f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId)
    }

    @Test fun pausedSelectionCannotSubmitAndLaterPlayUsesTheSelectedOccurrence()=check(playing=false) { f ->
        val old=f.start();val result=f.scope.async { f.coordinator.playEntry(f.originalId,"b") }
        old.stopped.await();old.terminal.complete(terminal(old))
        val next=f.engine.next();assertFalse(next.initial.play)
        try { next.submit(1);fail("Paused queue selection admitted PCM") } catch(_: IllegalStateException) {}
        f.confirm(next);assertTrue(result.await())
        assertFalse(f.events.any { it.second==DirectListeningEvent.PLAYING })
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play();assertTrue(next.control.playWhenReady)
        next.submit(1);next.emit(UsbFullTransportPhase.PLAYING,1)
        assertEquals("b",f.events.last().first);assertEquals(b.uri,next.uri)
    }

    @Test fun savedActivationRestoresExactOrderPolicyPositionAndBothInitialIntents() {
        for(playing in listOf(false,true)) check(playing) { f ->
            val old=f.start();val saved=f.saved()
            val activation=f.scope.async { PlaybackQueueRuntimeBridge.switchActiveQueue(saved.queue.queueId) }
            old.stopped.await();assertEquals(f.originalId,f.repository.getActiveQueueId())
            old.terminal.complete(terminal(old));val next=f.engine.next()
            assertFalse(next.initial.play);assertEquals(144000L,next.initial.targetFrame)
            assertFalse(activation.isCompleted);assertEquals(0L,f.route.queuePlayer.currentPosition)
            assertNull(f.coordinator.captureActiveQueueSnapshot())
            f.confirm(next);assertTrue(activation.await())
            val accepted=checkNotNull(f.repository.loadQueue(saved.queue.queueId))
            assertEquals(saved.entries.map { it.entryId },f.runtime.captureSnapshot()?.entries?.map { it.entryId })
            assertEquals(saved.entries.sortedBy { it.baseOrder }.map { it.entryId },f.coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
            assertEquals(saved.queue.currentEntryId,accepted.queue.currentEntryId)
            assertEquals(3000L,accepted.queue.currentPositionMs);assertTrue(accepted.queue.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ALL,accepted.queue.repeatMode)
            assertEquals(playing,next.control.playWhenReady);assertEquals(0,f.recreations)
            f.forwarding { f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId==saved.queue.currentEntryId }
            assertEquals(Player.REPEAT_MODE_ALL,f.route.repeatMode);assertTrue(f.route.shuffleModeEnabled)
        }
    }

    @Test fun rapidSelectionsConvergeOnLastOccurrenceWithoutPreparingTheObsoleteTarget()=check { f ->
        val old=f.start()
        val first=f.scope.async { f.coordinator.playEntry(f.originalId,"b") };old.stopped.await()
        val last=f.scope.async { f.coordinator.playEntry(f.originalId,"a-second") }
        assertFalse(first.await());assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next)
        assertTrue(last.await());assertEquals(a.uri,next.uri)
        assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        old.emit(UsbFullTransportPhase.PLAYING)
        assertEquals("a-second",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
    }

    @Test fun newerSavedActivationSupersedesPendingQueueWithoutCommittingIt()=check { f ->
        val old=f.start();val firstSaved=f.saved(position=1000);val lastSaved=f.saved(position=3000)
        val first=f.scope.async { f.coordinator.switchToQueue(firstSaved.queue.queueId) };old.stopped.await()
        val last=f.scope.async { f.coordinator.switchToQueue(lastSaved.queue.queueId) }
        assertFalse(first.await());assertEquals(f.originalId,f.repository.getActiveQueueId())
        old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next)
        assertTrue(last.await());assertEquals(lastSaved.queue.queueId,f.repository.getActiveQueueId())
        assertEquals(2,f.engine.runs.size)
    }

    @Test fun failedTargetAfterAdmissionKeepsHonestErrorAndNeverOverwritesPreviousSavedQueue()=check { f ->
        val old=f.start();val saved=f.saved();f.engine.startupFailure="DIRECT_SOURCE_FAILED"
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));assertFalse(activation.await())
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals("DIRECT_SOURCE_FAILED",f.route.directState.error)
        assertEquals(saved.queue.currentEntryId,f.route.queuePlayer.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertNull(f.coordinator.captureActiveQueueSnapshot());assertFalse(f.route.isPlaying)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertEquals(1,f.engine.runs.size)
        f.engine.startupFailure=null
        val retry=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        val next=f.engine.next();f.confirm(next);assertTrue(retry.await())
    }

    @Test fun nonseekableSavedPositionFailsWithoutDiscardingItsCheckpoint()=check { f ->
        val old=f.start();val saved=f.saved();f.engine.seekable=false
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val failed=f.engine.next()
        assertTrue(failed.control.stopped);assertEquals(0L,failed.frames)
        failed.terminal.complete(terminal(failed,status=UsbSilentStatus.FAILED));assertFalse(activation.await())
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(3000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
        assertEquals("DIRECT_INITIAL_POSITION_UNSUPPORTED",f.route.directState.error)
    }

    @Test fun unsafeOldCleanupCannotActivateOrResetTheLatch()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old,safe=false));assertFalse(activation.await())
        assertFalse(f.route.directState.cleanupSafe);assertEquals(1,f.engine.runs.size)
        assertFalse(f.coordinator.switchToQueue(saved.queue.queueId));assertEquals(f.originalId,f.repository.getActiveQueueId())
    }

    @Test fun outputSwitchDuringActivationCancelsCommitAndJoinsBeforeAndroidRestoration()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) };old.stopped.await()
        f.route.selectDirect(false);assertFalse(activation.await());assertEquals(0,f.recreations)
        old.terminal.complete(terminal(old));f.settled()
        assertEquals(1,f.recreations);assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(2000L,f.route.currentPosition);assertFalse(f.route.playWhenReady)
        assertEquals(listOf("a-first","b","a-second"),f.runtime.captureSnapshot()?.entries?.map { it.entryId })
    }

    @Test fun returningAndroidRestoresOriginalIdentityAndRetainsAcceptedDirectQueueCheckpoint()=check { f ->
        f.hubOwns(f.originalId)
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next);assertTrue(activation.await())
        next.frames=48000;next.emit(UsbFullTransportPhase.PLAYING,192000)
        f.route.selectDirect(false);assertEquals(0,f.recreations)
        next.terminal.complete(terminal(next));f.settled()
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(4000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
        assertEquals(2000L,f.route.currentPosition);assertFalse(f.route.playWhenReady)
        assertEquals(listOf(a,a,b),f.baseSongs);assertEquals(1,f.engine.maximumActive)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(saved.entries.map { it.entryId },f.repository.loadQueue(saved.queue.queueId)?.entries?.map { it.entryId })
        f.assertRestoredAndroid()
        next.emit(UsbFullTransportPhase.PLAYING,240000)
        f.assertRestoredAndroid()
        assertEquals(4000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
    }

    @Test fun newQueueActivationUsesEstablishedPlayingFirstZeroAndPolicyOffDefaults()=check(playing=false) { f ->
        val old=f.start()
        val creation=f.scope.async { f.coordinator.createAndActivateQueue("New",listOf(b,c,b)) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next()
        assertEquals(b.uri,next.uri);assertTrue(next.initial.play);assertEquals(0L,next.initial.seeks)
        assertFalse(creation.isCompleted);f.confirm(next)
        val created=checkNotNull(creation.await())
        assertEquals(created.entries.first().entryId,created.queue.currentEntryId)
        assertEquals(0L,created.queue.currentPositionMs);assertFalse(created.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.OFF,created.queue.repeatMode)
        assertEquals(created.queue.queueId,f.repository.getActiveQueueId())
    }

    @Test fun commandsPermitExactSelectionButKeepExternalClientsEditingAndTraversalRestricted()=check { f ->
        assertTrue(f.route.queuePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) }
        assertTrue(allowsDirectCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM,true))
        assertFalse(allowsDirectCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM,false))
        for(command in listOf(Player.COMMAND_CHANGE_MEDIA_ITEMS,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SET_REPEAT_MODE,Player.COMMAND_SET_SHUFFLE_MODE))
            assertFalse(f.route.isCommandAvailable(command))
        assertFalse(f.coordinator.removeEntry(f.originalId,"b"))
        assertFalse(f.coordinator.reorderEntry(f.originalId,"a-second",1));assertEquals(0,f.engine.runs.size)
    }

    @Test fun coldDirectReconstructionRestoresPositionPausedWithoutCreatingANativeRun()=check { f ->
        val saved=f.saved()
        f.repository.setActiveQueue(saved.queue.queueId)
        f.route.restoreDirectIdleTimeline(emptyList(),0,0,false,Player.REPEAT_MODE_OFF)
        f.coordinator.initialize()
        assertEquals(saved.queue.queueId,f.coordinator.getActiveQueueId())
        assertEquals(saved.queue.currentEntryId,f.runtime.captureSnapshot()?.currentEntryId)
        assertEquals(3000L,f.route.queuePlayer.currentPosition);assertFalse(f.route.directRequestedPlaying)
        assertEquals(0,f.engine.runs.size);assertTrue(f.events.isEmpty())
        val run=f.start();assertFalse(run.initial.play);assertEquals(144000L,run.initial.targetFrame)
        assertFalse(f.route.queuePlayer.isPlaying)
    }

    @Test fun callerCancellationCannotAbandonCleanupOrCommitAnActivation()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) };old.stopped.await()
        activation.cancel();activation.join()
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal(old))
        val retry=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        val next=f.engine.next();f.confirm(next);assertTrue(retry.await());assertEquals(1,f.engine.maximumActive)
    }

    @Test fun detachDuringSuccessorPreparationCancelsActivationAndNeverStartsAndroidBeforeJoin()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next()
        f.attachmentFailure="DIRECT_USB_SELECTION_CHANGED";f.route.usbSelectionChanged()
        assertFalse(activation.await());assertEquals(0,f.recreations)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        next.terminal.complete(terminal(next));f.settled()
        assertEquals(1,f.recreations);assertEquals(1,f.engine.maximumActive)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
    }

    @Test fun shutdownDuringActivationCancelsItsCommitAndWaitsForNativeCleanup()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) };old.stopped.await()
        val shutdown=f.scope.async { f.route.shutdown() }
        assertFalse(activation.await());assertFalse(shutdown.isCompleted)
        old.terminal.complete(terminal(old));assertTrue(shutdown.await())
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(0,f.recreations)
        assertEquals(DirectListeningEvent.STOPPED,f.events.last().second)
    }

    @Test fun editedOriginalSavedQueueCannotBeOverwrittenByAndroidSnapshotRestoration()=check { f ->
        f.hubOwns(f.originalId)
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next);assertTrue(activation.await())
        assertTrue(f.coordinator.removeEntry(f.originalId,"b"))
        val edited=checkNotNull(f.repository.loadQueue(f.originalId))
        f.route.selectDirect(false);next.terminal.complete(terminal(next));f.settled()
        assertNull(f.repository.getActiveQueueId())
        f.hubOwns(null);assertFalse(f.hub.state.value.queues.any { it.isActive })
        assertEquals(3,f.route.mediaItemCount);assertEquals(2000L,f.route.currentPosition)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(edited.entries,f.repository.loadQueue(f.originalId)?.entries)
        assertEquals(2,f.repository.listQueues().size)
        next.emit(UsbFullTransportPhase.PLAYING,240000)
        f.coordinator.persistActiveQueueSnapshot()
        assertNull(f.coordinator.getActiveQueueId());assertEquals(edited.entries,f.repository.loadQueue(f.originalId)?.entries)
        // Existing successful activation can reconcile the restored, unassigned snapshot safely.
        assertTrue(f.coordinator.switchToQueue(saved.queue.queueId))
        assertEquals(saved.queue.queueId,f.repository.getActiveQueueId())
    }

    @Test fun metadataOnlyArtworkUpdateKeepsTheCurrentRunAndEveryOccurrence()=check { f ->
        f.start();val before=f.route.queuePlayer.currentMediaItem!!
        f.route.updateArtwork(before,Uri.parse("content://art/new"))
        assertEquals(1,f.engine.runs.size);assertEquals(3,f.route.queuePlayer.mediaItemCount)
        assertEquals("a-first",f.route.queuePlayer.currentMediaItem?.listeningEvidence()?.itemInstanceId)
    }

    @Test fun nativeEofKeepsFullTimelineAndNaturalHistoryWithoutRepeatOrAdjacentAdvancement()=check { f ->
        val old=f.start()
        val created=f.scope.async { f.coordinator.createAndActivateQueue("EOF",listOf(b,c)) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next)
        assertNotNull(created.await())
        val ended=naturalResult(next);assertTrue(ended.transport!!.verified);assertTrue(ended.integratedCleanupSafe())
        next.terminal.complete(ended)
        f.forwarding { f.route.playbackState==Player.STATE_ENDED }
        assertEquals(2,f.engine.runs.size);assertEquals(2,f.route.mediaItemCount);assertEquals(0,f.route.currentMediaItemIndex)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun validEarlyEofAcknowledgesQueueActivationWithoutFabricatingPlayback()=check { f ->
        val created=f.scope.async { f.coordinator.createAndActivateQueue("Short",listOf(b,c)) }
        val next=f.engine.next();val ended=naturalResult(next)
        assertTrue(ended.transport!!.verified);next.terminal.complete(ended)
        val queue=checkNotNull(created.await())
        assertEquals(queue.queue.queueId,f.repository.getActiveQueueId())
        assertEquals(IntegratedDirectPhase.ENDED,f.route.directState.phase)
        assertEquals(1,f.engine.runs.size);assertTrue(f.events.isEmpty())
        assertEquals(2,f.route.queuePlayer.mediaItemCount)
    }

    @Test fun existingLibraryContextKeepsItsCompleteTimelineAndCanonicalDuplicateOrderAfterNativeAcknowledgment()=check { f ->
        val old=f.start()
        val songs=listOf(b,a,b)
        val items=listOf(b.toPlayableMediaItem("context-b1"),a.toPlayableMediaItem("context-a"),b.toPlayableMediaItem("context-b2"))
        val canonical=listOf("context-b1","context-b2","context-a")
        assertTrue(PlaybackQueueRuntimeBridge.requestPlaybackContext(songs,items,2,3000,true,canonical,true,PersistedQueueRepeatMode.ONE))
        val context=f.scope.async { f.contextResults.receive() }
        old.stopped.await();assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(1,f.repository.listQueues().size)
        old.terminal.complete(terminal(old));val next=f.engine.next()
        assertEquals(b.uri,next.uri);assertFalse(context.isCompleted);assertEquals(144000L,next.initial.targetFrame)
        f.confirm(next);assertTrue(context.await())
        assertEquals(listOf("context-b1","context-a","context-b2"),f.runtime.captureSnapshot()?.entries?.map { it.entryId })
        assertEquals(canonical,f.coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
        val directQueueId=checkNotNull(f.coordinator.getActiveQueueId())
        assertNotEquals(f.originalId,directQueueId)
        assertEquals("context-b2",f.repository.loadQueue(directQueueId)?.queue?.currentEntryId)
        assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertEquals(2,f.route.queuePlayer.currentMediaItemIndex)
        f.hubOwns(directQueueId)
        next.emit(UsbFullTransportPhase.PLAYING,192000)
        f.route.selectDirect(false)
        f.route.selectDirect(true) // A request during the outstanding return cannot overtake cleanup.
        next.terminal.complete(terminal(next));f.settled();f.assertRestoredAndroid()
        assertEquals(4000L,f.repository.loadQueue(directQueueId)?.queue?.currentPositionMs)
        assertEquals(canonical,f.repository.loadQueue(directQueueId)?.entries?.sortedBy { it.baseOrder }?.map { it.entryId })
        next.emit(UsbFullTransportPhase.PLAYING,240000)
        f.coordinator.persistActiveQueueSnapshot();f.assertRestoredAndroid()
        f.route.selectDirect(true);f.settled();next.emit(UsbFullTransportPhase.PLAYING,288000)
        assertEquals("a-first",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
        f.route.selectDirect(false);f.settled();f.assertRestoredAndroid()
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        f.route.seekToNextMediaItem()
        f.forwarding { f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId=="b" }
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals("b",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(4000L,f.repository.loadQueue(directQueueId)?.queue?.currentPositionMs)
        assertEquals(2,f.repository.listQueues().size)
        val allEntries=f.repository.listQueues().flatMap { checkNotNull(f.repository.loadQueue(it.queueId)).entries }
        assertEquals(allEntries.size,allEntries.map { it.entryId }.distinct().size)
    }

    @Test fun deletedOriginalQueueRestoresAndroidUnassignedWithoutRecreatingItsOccurrences()=check { f ->
        f.hubOwns(f.originalId)
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next()
        f.confirm(next);assertTrue(activation.await());f.hubOwns(saved.queue.queueId)
        assertTrue(f.repository.deleteInactiveQueueIfNotLast(f.originalId))
        next.emit(UsbFullTransportPhase.PLAYING,192000)
        f.route.selectDirect(false);next.terminal.complete(terminal(next));f.settled();f.hubOwns(null)
        assertFalse(f.hub.state.value.queues.any { it.isActive });assertFalse(f.route.playWhenReady)
        assertEquals(3,f.route.mediaItemCount);assertEquals(2000L,f.route.currentPosition)
        next.emit(UsbFullTransportPhase.PLAYING,288000);f.coordinator.persistActiveQueueSnapshot()
        assertNull(f.coordinator.getActiveQueueId());assertNull(f.repository.getActiveQueueId())
        assertNull(f.repository.loadQueue(f.originalId));assertEquals(1,f.repository.listQueues().size)
        assertEquals(saved.entries,f.repository.loadQueue(saved.queue.queueId)?.entries)
        assertEquals(4000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
    }

    @Test fun stoppedOutgoingQueueDoesNotRestoreStalePlayingIntent()=check { f ->
        val old=f.start();f.route.stopDirect();old.terminal.complete(terminal(old))
        withTimeout(5000) { while(f.route.directState.phase!=IntegratedDirectPhase.STOPPED) yield() }
        val saved=f.saved(position=0)
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        val next=f.engine.next();assertFalse(next.initial.play);f.confirm(next);assertTrue(activation.await())
    }
}
