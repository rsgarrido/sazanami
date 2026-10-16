package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual routing adapter, fake physical player and USB engine; no JNI or hardware. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class UsbOutputSwitchingTest {
    @Test fun liveDiagnosticCaptureWhilePlayingAndPausedDoesNotChangeRoutingOrControls() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        val store = UsbTransportTelemetryStore();val key = UsbTelemetrySession(1, 2);store.begin(key)
        for(phase in listOf(UsbFullTransportPhase.PLAYING, UsbFullTransportPhase.PAUSED)) {
            if(phase == UsbFullTransportPhase.PAUSED) f.route.pauseDirectPlayback()
            f.engine.progress(UsbFullProgress(44100, 441000, 44100, phase))
            val out = if(phase == UsbFullTransportPhase.PAUSED) 0L else 3L
            val frames = if(out == 0L) 0L else 132L
            val values = mapOf("version" to 1L, "generation" to 1L, "stream" to 2L,
                "capturedAtUs" to 100000L, "readAtUs" to 100100L, "transportState" to 4L,
                "playbackPhase" to phase.ordinal.toLong(), "sampleRateHz" to 44100L,
                "channels" to 2L, "bits" to 16L, "bytesPerFrame" to 4L, "startedAtUs" to 1000L,
                "outPool" to 21L, "outSubmitted" to 10L, "outCompleted" to 10L - out,
                "outOutstanding" to out, "outFree" to 21L - out, "pendingPayloadFrames" to frames,
                "pendingPayloadDurationUs" to frames * 1000000 / 44100, "nominalCoverageUs" to 21000L,
                "pcmDepth" to 8192L, "pcmCapacity" to 16384L, "pcmMinDepth" to 4096L,
                "pcmShortageDepth" to -1L, "pcmShortageRequested" to -1L, "pcmShortageAgeUs" to -1L)
            val words = LongArray(UsbTransportTelemetrySchema.WORDS)
            UsbTransportTelemetrySchema.fields.forEachIndexed { i, name -> words[i] = values[name] ?: 0L }
            val snapshot = UsbTransportTelemetrySchema.decode(words, key)
            store.bind(key) { snapshot }
            val before = f.route.directState.copy();val intent = f.engine.control.intent()
            val item = f.route.currentMediaItem;val normalStops = f.physical.stopped
            val starts = f.engine.starts;val stops = f.engine.stops;val recreations = f.recreations
            repeat(3) {
                val report = withContext(Dispatchers.IO) { formatUsbTransportTelemetry(store.capture()) }
                assertTrue(report.contains("playback=${phase.name}"));assertEquals(before, f.route.directState)
                assertEquals(intent, f.engine.control.intent());assertEquals(item, f.route.currentMediaItem)
                assertEquals(starts, f.engine.starts);assertEquals(stops, f.engine.stops)
                assertEquals(recreations, f.recreations);assertEquals(normalStops, f.physical.stopped)
            }
        }
        store.finish(key);assertEquals("TERMINAL", store.capture().status)
    }

    @Test fun activeDirectRuntimeRequiresNativeAcknowledgmentAndKeepsEditingGuarded() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        val playing = CompletableDeferred<Unit>()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playing.complete(Unit)
            }
        }
        f.route.addListener(listener)
        try {
            // The forwarding player's state refresh follows the wrapped player's posted onEvents.
            f.engine.progress(UsbFullProgress(44100, 441000, 44100, UsbFullTransportPhase.PLAYING))
            withTimeout(5000) { playing.await() }
        } finally {
            f.route.removeListener(listener)
        }
        assertTrue(f.route.isPlaying)
        val intent = f.engine.control.intent()
        val original = f.route.currentMediaItem
        var callbacks = 0
        val runtime = Media3PlaybackQueueRuntime(f.route, { false }, { listOf(song) },
            { callbacks++ }, { _, _ -> callbacks++ })
        assertTrue(runtime.canReplaceTimeline())
        assertFalse(runtime.removeEntry("anything"));assertFalse(runtime.moveEntry("anything",0))
        try {
            runtime.replaceTimeline(PlaybackQueueRestoration("other", emptyList(), "other-entry", 0,
                true, false, io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode.OFF))
            fail("Direct queue activation must require the asynchronous native contract")
        } catch (_: IllegalStateException) {
            // The existing service runtime cannot bypass the Direct adapter's command boundary.
        }
        assertEquals(0, callbacks)
        assertEquals(intent, f.engine.control.intent())
        assertEquals(original, f.route.currentMediaItem)
        assertEquals(1, f.engine.starts)
        assertEquals(0, f.engine.stops)
        assertTrue(f.route.isPlaying)
    }

    @Test fun idleTimelineAdoptionAndNativeSelectionPreserveAndroidSnapshot() = checkRoute { f ->
        val androidUi = io.github.rsgarrido.sazanami.ui.state.PlaybackUiState(currentSong = song, isPlaying = true)
        val original = f.route.currentMediaItem!!
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.forwarding { f.route.currentMediaItem == original }
        assertEquals(song.id.toString(),f.route.currentMediaItem?.mediaId)
        assertEquals(original.listeningEvidence()?.itemInstanceId,f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertEquals(song,directPlaybackUiState(androidUi, f.route.directState, listOf(song)).currentSong)
        assertEquals(2000L,f.route.currentPosition);assertFalse(f.route.isPlaying)
        assertEquals(0, f.engine.starts)

        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        val directUi = directPlaybackUiState(androidUi, f.route.directState, listOf(song))
        assertEquals(song, directUi.currentSong)
        assertFalse(directUi.isPlaying)
        assertEquals(true, directUi.playPauseIntent)
        assertEquals(1, f.engine.starts)

        f.engine.terminal.complete(terminal(true).copy(status = UsbSilentStatus.USB_TRANSPORT_FAILED))
        withTimeout(5000) { while (f.route.directState.phase != IntegratedDirectPhase.ERROR) yield() }
        f.route.selectDirect(false);settled(f)
        f.forwarding { f.route.currentMediaItem == original }
        assertEquals(song.id.toString(), f.route.currentMediaItem?.mediaId)
        assertEquals(2000L, f.route.currentPosition)
        assertFalse(f.route.playWhenReady)
        f.route.selectDirect(true);settled(f)
        f.forwarding { f.route.currentMediaItem == original }
        // Re-selecting an existing adapter reports its real item, without starting another engine.
        assertEquals(f.route.currentMediaItem?.mediaId?.toLong(), f.route.directState.libraryId)
        assertEquals(song, directPlaybackUiState(directUi, f.route.directState, listOf(song)).currentSong)
        assertEquals(1, f.engine.starts)
    }

    private val song = Song(12, "Track", "Artist", "Album", 1, 10000,
        Uri.parse("content://media/external/audio/media/12"), "private", "private", null)

    private class Physical(item: MediaItem?, playing: Boolean) : SimpleBasePlayer(Looper.getMainLooper()) {
        var stopped = 0
        var snapshotFails = false
        var stateValue = State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(if (item == null) emptyList() else listOf(MediaItemData.Builder("original").setMediaItem(item).setDurationUs(10_000_000).build()))
            .setCurrentMediaItemIndex(if (item == null) C.INDEX_UNSET else 0).setContentPositionMs(PositionSupplier {
                if (snapshotFails) error("private URI must not appear in diagnostics")
                if (item == null) 0L else 2000L
            }).setPlaybackState(if (item == null) Player.STATE_IDLE else Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
        override fun getState(): State = stateValue
        override fun handleStop(): ListenableFuture<*> {
            stopped++;stateValue = stateValue.buildUpon().setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleSetMediaItems(items: List<MediaItem>, index: Int, position: Long): ListenableFuture<*> {
            stateValue = stateValue.buildUpon().setPlaylist(items.mapIndexed { i, item ->
                MediaItemData.Builder("restored-$i").setMediaItem(item).setDurationUs(10_000_000).build()
            }).setCurrentMediaItemIndex(index).setContentPositionMs(position).build()
            invalidateState();return Futures.immediateVoidFuture()
        }
        override fun handleSetVolume(volume: Float): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleSetPlaybackParameters(parameters: PlaybackParameters): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }

    private class Engine : FullUsbPlaybackEngineClient {
        class Run(val uri: Uri,val terminal: CompletableDeferred<UsbSilentTransportExperimentResult>)
        val started = Channel<Run>(Channel.UNLIMITED)
        var starts = 0
        var stops = 0
        lateinit var progress: (UsbFullProgress) -> Unit
        lateinit var control: UsbFullPlaybackControl
        var terminal = CompletableDeferred<UsbSilentTransportExperimentResult>()
        override suspend fun runIntegratedFull(uri: Uri, control: UsbFullPlaybackControl, progress: (UsbFullProgress) -> Unit): UsbSilentTransportExperimentResult {
            starts++;this.control = control;this.progress = progress;control.bind({true}, {})
            started.send(Run(uri,terminal))
            return terminal.await()
        }
        override fun stopIntegratedFull() { stops++ }
    }

    private fun terminal(safe: Boolean) = UsbSilentTransportExperimentResult(1, 2, UsbSilentTransportResolution(), status = UsbSilentStatus.CANCELLED,
        configuration = UsbAudioConfigurationResult(1, UsbAudioConfigurationResolution(),
            targetStatus = UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
            acClaim = UsbConfigurationAndroidOwnership(true, true), asClaim = UsbConfigurationAndroidOwnership(true, true),
            restorationStatus = if (safe) UsbConfigurationRestorationStatus.RESTORED else UsbConfigurationRestorationStatus.FAILED,
            asRelease = UsbConfigurationAndroidOwnership(true, true), acRelease = UsbConfigurationAndroidOwnership(true, true),
            closeStatus = UsbAudioCleanupStatus.SUCCEEDED),
        transport = UsbIsoTransportResult(1, 2, mapOf("workerStopped" to 1L, "outstandingUrbsFinal" to 0L, "framesCompleted" to 100L)),
        originalFlacFdClosed = true, flacSource = UsbFlacSourceResult(mapOf("totalSamples" to 441000L, "producerJoined" to 1L)))

    private inner class Fixture(playing: Boolean = true, emptyQueue: Boolean = false) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val physical = Physical(if (emptyQueue) null else song.toPlayableMediaItem(), playing)
        val normal = SmoothPlaybackPlayer(physical)
        val engine = Engine()
        var released = CompletableDeferred<Boolean>()
        var releases = 0
        var recreations = 0
        var authorizations = 0
        var authorizationFailure: String? = null
        var attachmentFailure: String? = null
        var recreationFails = false
        var predecessorSafe = true
        val reservation = UsbOutputReservation()
        val gate = UsbAudioGenerationGate()
        val identity = UsbAudioDeviceIdentity("fake", 1, 2, 3)
        var token = gate.select(identity)!!
        val reservationReleased get() = reservation.token == null
        var commitFails: String? = null
        val listeningTrace = mutableListOf<String>()
        val listeningEvents = mutableListOf<DirectListeningEvent>()
        val listeningItems = mutableListOf<String?>()
        private val listeningTranslator = DirectListeningEventTranslator()
        private val listeningObserver = object : DirectPlaybackListeningObserver {
            override fun onActivated() {
                listeningTrace += "activated"
                listeningTranslator.activate()
            }
            override fun onObservation(item: MediaItem?, state: IntegratedDirectPlaybackState, terminalConfirmed: Boolean) {
                val itemId = item?.listeningEvidence()?.itemInstanceId
                listeningItems += itemId
                listeningEvents += listeningTranslator.observe(itemId, state, terminalConfirmed)
                if (terminalConfirmed) listeningTrace += "terminal:${state.phase}"
            }
            override fun onDeactivated() {
                listeningTrace += "deactivated"
                listeningTranslator.deactivate()
            }
        }
        private val controller = object : UsbOutputSelectionController {
            override fun authorizeIntegratedSelection(owner: UsbActivationOwner, identity: UsbAudioDeviceIdentity?, generation: Long): String? {
                authorizations++;authorizationFailure?.let { return it }
                if (!gate.accepts(token) || (identity != null && (identity != token.identity || generation != token.generation)))
                    return "DIRECT_USB_SELECTION_CHANGED"
                return if (reservation.acquire(owner, token)) null else "DIRECT_USB_RESERVATION_HELD"
            }
            override fun integratedSelectionFailure(owner: UsbActivationOwner): String? {
                attachmentFailure?.let { return it }
                if (!reservation.owns(owner)) return "DIRECT_USB_RESERVATION_LOST"
                return if (gate.accepts(requireNotNull(reservation.token))) null else "DIRECT_USB_SELECTION_CHANGED"
            }
            override fun commitIntegratedSelection(owner: UsbActivationOwner): String? {
                commitFails?.let { return it }
                return if (reservation.commit(owner)) null else "DIRECT_USB_RESERVATION_LOST"
            }
            override fun rollbackIntegratedSelection(owner: UsbActivationOwner) = reservation.rollback(owner, engine.starts > 0 && !engine.terminal.isCompleted)
            override fun releaseIntegratedSelection(owner: UsbActivationOwner, cleanupSafe: Boolean) = reservation.release(owner, cleanupSafe,
                engine.starts > 0 && !engine.terminal.isCompleted)
        }
        val route = ExperimentalUsbPlaybackPlayer(normal, scope, engine,
            releaseNormal = { releases++;released.await() },
            recreateNormal = { listeningTrace += "recreate";recreations++;if (recreationFails) error("restore failed");Physical(if (emptyQueue) null else song.toPlayableMediaItem(), false) },
            predecessorReleased = { predecessorSafe },
            selection = UsbOutputSelectionCallbacks { controller }).also {
                it.attachDirectListeningObserver(listeningObserver)
            }
        suspend fun close() {
            engine.terminal.complete(terminal(true));released.complete(true)
            route.shutdown();route.releaseTransitionResources();route.release();scope.cancel()
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
    }
    private fun checkRoute(playing: Boolean = true, emptyQueue: Boolean = false, block: suspend (Fixture) -> Unit) = runBlocking {
        withContext(Dispatchers.Main) {
            val oldSongs = PlaybackLibraryBridge.songs
            PlaybackLibraryBridge.updateSongs(listOf(song))
            val f = Fixture(playing, emptyQueue)
            try { block(f) } finally { f.close();PlaybackLibraryBridge.updateSongs(oldSongs) }
        }
    }

    private suspend fun settled(f: Fixture) {
        withTimeout(5000) { while (f.route.directState.switching != null) yield() }
    }

    private fun detachedTerminal(f: Fixture,owner: UsbActivationOwner=requireNotNull(f.reservation.owner)): UsbSilentTransportExperimentResult {
        val result=terminal(false).copy(status=UsbSilentStatus.NON_AUTHORITATIVE,nativeExecutionJoined=true,
            connectionCloseRequired=true,outstandingAfterConnectionClose=0,
            configuration=terminal(false).configuration!!.copy(accessFailure=UsbClockQueryStatus.DEVICE_DISCONNECTED,
                restorationStatus=UsbConfigurationRestorationStatus.INDETERMINATE),
            flacSource=UsbFlacSourceResult(mapOf("generation" to f.token.generation,"streamGeneration" to 2L,
                "totalSamples" to 441000L,"sampleRate" to 44100L,"producerStopped" to 1L,"producerJoined" to 1L,"decoderReleased" to 1L,
                "nativeFlacFdClosed" to 1L,"queueReleased" to 1L)))
        return result.copy(detachedResourceRelease=UsbDetachedResourceRelease.verified(owner,f.token,result,true,
            sourceAbsent=false,inputAbsent=false,connectionAbsent=false,nativeRegistriesIdle=true))
    }
    private suspend fun detachAndRecover(f: Fixture,phase: UsbFullTransportPhase?) {
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        withTimeout(5000) { f.engine.started.receive() }
        if(phase!=null) {
            f.engine.progress(UsbFullProgress(44100,441000,44100))
            if(phase==UsbFullTransportPhase.PAUSED) f.route.pauseDirectPlayback()
            f.engine.progress(UsbFullProgress(44100,441000,44100,phase))
        }
        val finalResult=detachedTerminal(f);val oldProgress=f.engine.progress
        f.attachmentFailure="DIRECT_USB_DEVICE_UNAVAILABLE";f.gate.select(null)
        repeat(2) { f.route.usbSelectionChanged();f.route.selectDirect(false) }
        assertEquals(0,f.recreations);assertFalse(f.reservationReleased)
        assertEquals(DirectOutputSwitch.TO_ANDROID,f.route.directState.switching)
        f.engine.terminal.complete(finalResult);settled(f)
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM) && f.route.currentMediaItem?.mediaId==song.id.toString() }
        assertEquals(1,f.recreations);assertTrue(f.reservationReleased)
        assertFalse(f.route.isDirectSelected);assertFalse(f.route.playWhenReady);assertEquals(2000L,f.route.currentPosition)
        assertTrue(f.route.directState.detachedResourcesReleased);assertFalse(f.route.directState.cleanupSafe)
        assertSame(finalResult,f.route.directState.engine)
        assertEquals(UsbConfigurationRestorationStatus.INDETERMINATE,finalResult.configuration!!.restorationStatus)
        oldProgress(UsbFullProgress(88200,441000,44100,UsbFullTransportPhase.FAILED));f.route.usbSelectionChanged();f.route.selectDirect(false)
        assertFalse(f.engine.terminal.complete(terminal(false)))
        assertEquals(1,f.recreations);assertFalse(f.route.isDirectSelected)
        // Reconnection uses a fresh reservation, with idle adoption rather than reusing the old native run.
        f.token=f.gate.select(f.identity)!!;f.attachmentFailure=null
        f.route.selectDirect(true);settled(f)
        assertTrue("Reactivation failed: ${f.route.directState.error}; reservation=${f.route.directState.activation?.reservation}",
            f.route.directState.confirmedDirect)
        assertEquals(1,f.engine.starts)
        assertNull(f.route.directState.activation?.failure)
        assertNull(f.route.directState.engine)
        assertFalse(finalResult.integratedCleanupSafe()) // The old DAC restoration is still unverified.
        assertFalse(f.route.directState.detachedResourcesReleased)
        oldProgress(UsbFullProgress(88200,441000,44100,UsbFullTransportPhase.FAILED))
        assertEquals(IntegratedDirectPhase.IDLE,f.route.directState.phase)
    }
    @Test fun detachPlayingWaitsForDisposalThenRestoresAndroidOnce()=checkRoute { detachAndRecover(it,UsbFullTransportPhase.PLAYING) }
    @Test fun detachPausedWaitsForDisposalThenRestoresAndroidOnce()=checkRoute { detachAndRecover(it,UsbFullTransportPhase.PAUSED) }
    @Test fun detachPreparingWaitsForDisposalThenRestoresAndroidOnce()=checkRoute { detachAndRecover(it,null) }

    @Test fun mismatchedDetachedProofCannotReleaseCurrentReservation()=checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        withTimeout(5000) { f.engine.started.receive() }
        val obsolete=detachedTerminal(f,UsbActivationOwner(f.reservation.owner!!.serial))
        f.attachmentFailure="DIRECT_USB_DEVICE_UNAVAILABLE";f.route.usbSelectionChanged()
        f.engine.terminal.complete(obsolete);settled(f)
        assertTrue(f.route.isDirectSelected);assertFalse(f.reservationReleased);assertEquals(0,f.recreations)
        assertEquals("DIRECT_RESOURCE_CLEANUP_UNVERIFIED",f.route.directState.error)
    }
    @Test fun detachDuringAndroidSwitchUsesLateDisposalAndShutdownDoesNotRecreateAndroid()=checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        withTimeout(5000) { f.engine.started.receive() }
        val terminal=detachedTerminal(f)
        f.route.selectDirect(false);f.attachmentFailure="DIRECT_USB_DEVICE_UNAVAILABLE";f.route.usbSelectionChanged()
        val shutdown=f.scope.async(start=CoroutineStart.UNDISPATCHED) { f.route.shutdown() }
        assertFalse(shutdown.isCompleted);assertEquals(0,f.recreations)
        f.engine.terminal.complete(terminal)
        assertTrue(withTimeout(5000) { shutdown.await() });assertTrue(f.reservationReleased);assertEquals(0,f.recreations)
    }
    @Test fun predecessorKeepsCommandsUnavailableUntilItsFinalJoinedAcknowledgment()=checkRoute { f ->
        val previous=DirectTeardownLease();f.predecessorSafe=false;f.route.predecessorCleanupCompleted()
        assertFalse(f.route.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
        assertFalse(f.route.isCommandAvailable(Player.COMMAND_PREPARE))
        assertEquals("DIRECT_PREDECESSOR_CLEANUP_PENDING",f.route.playerError?.message)
        assertFalse(f.route.playWhenReady)
        val waiting=f.scope.launch(start=CoroutineStart.UNDISPATCHED) {
            if(previous.awaitSafe()) { f.predecessorSafe=true;f.route.predecessorCleanupCompleted() }
        }
        assertFalse(waiting.isCompleted);assertEquals(0,f.releases)
        previous.complete(true);waiting.join()
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM) && f.route.isCommandAvailable(Player.COMMAND_PREPARE) }
        assertNull(f.route.playerError)
        previous.complete(false);assertTrue(previous.safe==true)
    }
    @Test fun cancelledShutdownWaiterPublishesFinalDetachedLeaseOnlyAfterResourceJoin()=checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        withTimeout(5000) { f.engine.started.receive() }
        val finalResult=detachedTerminal(f);val lease=DirectTeardownLease()
        val shutdown=f.scope.launch(start=CoroutineStart.UNDISPATCHED) { lease.complete(f.route.shutdown()) }
        shutdown.cancel()
        val recreated=f.scope.async(start=CoroutineStart.UNDISPATCHED) { lease.awaitSafe() }
        assertNull(lease.safe);assertFalse(recreated.isCompleted);assertFalse(f.reservationReleased)
        f.engine.terminal.complete(finalResult)
        assertTrue(withTimeout(5000) { recreated.await() });shutdown.join()
        assertTrue(f.reservationReleased);assertEquals(0,f.recreations)
        assertTrue(f.route.shutdown())
    }

    @Test fun internalReplacementKeepsProtectedDirectOutputAndOriginalAndroidRestoration() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        val replacementSong=song.copy(id=13,title="Replacement",uri=Uri.parse("content://media/external/audio/media/13"))
        PlaybackLibraryBridge.updateSongs(listOf(song,replacementSong))
        f.route.setMediaItem(song.toPlayableMediaItem("source-a"));f.route.prepare()
        val old=withTimeout(5000) { f.engine.started.receive() }
        f.engine.progress(UsbFullProgress(44100,441000,44100))
        val attachment=f.reservation.token
        val activationSerial=f.route.directState.activation?.serial
        val result=f.route.replaceDirectSource(replacementSong.toPlayableMediaItem("source-b"),false)
        assertTrue(f.route.directState.enabled);assertFalse(f.reservationReleased)
        assertEquals(0,f.recreations);assertEquals(1,f.engine.starts)
        f.engine.terminal=CompletableDeferred()
        old.terminal.complete(terminal(true))
        val next=withTimeout(5000) { f.engine.started.receive() }
        assertEquals(replacementSong.uri,next.uri);assertSame(attachment,f.reservation.token)
        assertEquals(activationSerial,f.route.directState.activation?.serial);assertTrue(f.reservation.handedOff)
        assertFalse(f.engine.control.playWhenReady)
        f.engine.progress(UsbFullProgress(0,441000,44100,UsbFullTransportPhase.PAUSED))
        assertEquals(DirectSourceTransitionStatus.READY,result.await().status)
        assertEquals(1,f.listeningEvents.count { it==DirectListeningEvent.TRANSITION })
        assertEquals(0,f.listeningEvents.count { it==DirectListeningEvent.STOPPED })
        assertEquals(0,f.recreations);assertFalse(f.reservationReleased)
        f.route.selectDirect(false)
        next.terminal.complete(terminal(true));settled(f)
        assertEquals(1,f.recreations);assertTrue(f.reservationReleased)
        assertEquals(song.id.toString(),f.route.currentMediaItem?.mediaId)
        assertEquals(2000L,f.route.currentPosition);assertFalse(f.route.playWhenReady)
    }

    @Test fun outputSwitchSupersedesPendingReplacementAndJoinsBeforeAndroidRecreation() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        val old=withTimeout(5000) { f.engine.started.receive() }
        val pending=f.route.replaceDirectSource(song.toPlayableMediaItem("pending"),false,3000)
        f.route.selectDirect(false);yield()
        assertEquals(DirectSourceTransitionStatus.CANCELLED,pending.await().status)
        assertEquals(0,f.recreations);assertFalse(f.reservationReleased)
        old.terminal.complete(terminal(true));settled(f)
        assertEquals(1,f.engine.starts);assertEquals(1,f.recreations)
        assertTrue(f.reservationReleased)
    }

    @Test fun detachmentWhileReplacementWaitsCancelsTargetAndRetainsUnsafeOwnership() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        val old=withTimeout(5000) { f.engine.started.receive() }
        val pending=f.route.replaceDirectSource(song.toPlayableMediaItem("pending"),true)
        f.attachmentFailure="DIRECT_USB_SELECTION_CHANGED"
        f.route.usbSelectionChanged();yield()
        assertEquals(DirectSourceTransitionStatus.CANCELLED,pending.await().status)
        old.terminal.complete(terminal(false));settled(f)
        assertEquals(1,f.engine.starts);assertEquals(0,f.recreations)
        assertFalse(f.reservationReleased);assertFalse(f.route.directState.cleanupSafe)
        assertTrue(f.route.directState.enabled)
    }

    @Test fun outputSwitchDuringSuccessorPreparationJoinsBothRunsBeforeRestoringAndroid() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare()
        val old=withTimeout(5000) { f.engine.started.receive() }
        val replacement=f.route.replaceDirectSource(song.toPlayableMediaItem("preparing-b"),false)
        f.engine.terminal=CompletableDeferred()
        old.terminal.complete(terminal(true))
        val next=withTimeout(5000) { f.engine.started.receive() }
        assertEquals(2,f.engine.starts);assertFalse(replacement.isCompleted)
        f.route.selectDirect(false);yield()
        assertEquals(DirectSourceTransitionStatus.CANCELLED,replacement.await().status)
        assertEquals(0,f.recreations);assertFalse(f.reservationReleased)
        next.terminal.complete(terminal(true));settled(f)
        assertEquals(1,f.recreations);assertTrue(f.reservationReleased)
        assertEquals(song.id.toString(),f.route.currentMediaItem?.mediaId)
        assertEquals(2000L,f.route.currentPosition)
    }

    @Test fun destructionDuringReplacementCancelsTargetButRetainsListeningUntilJoin() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem("source-a"));f.route.prepare()
        val old=withTimeout(5000) { f.engine.started.receive() }
        f.engine.progress(UsbFullProgress(44100,441000,44100))
        val pending=f.route.replaceDirectSource(song.toPlayableMediaItem("pending"),false)
        val shutdown=f.scope.async { f.route.shutdown() };yield()
        assertEquals(DirectSourceTransitionStatus.CANCELLED,pending.await().status)
        assertFalse(shutdown.isCompleted);assertFalse(f.listeningTrace.contains("deactivated"))
        old.terminal.complete(terminal(true));assertTrue(shutdown.await())
        assertEquals(1,f.engine.starts);assertEquals(0,f.recreations)
        assertEquals(DirectListeningEvent.STOPPED,f.listeningEvents.last())
        assertTrue(f.reservationReleased)
    }

    @Test fun listeningOwnershipStartsAfterReleaseAndEndsAfterTerminalBeforeAndroidRebind() = checkRoute { f ->
        f.route.selectDirect(true)
        assertTrue(f.listeningTrace.isEmpty())
        f.released.complete(true);settled(f)
        assertEquals(listOf("activated"), f.listeningTrace)
        assertTrue(f.listeningEvents.isEmpty())
        val original = song.toPlayableMediaItem("listening-original")
        f.route.setMediaItem(original);f.route.prepare();yield()
        assertTrue(f.listeningEvents.isEmpty())
        val oldProgress = f.engine.progress
        oldProgress(UsbFullProgress(44100, 441000, 44100))
        assertEquals(listOf(DirectListeningEvent.PLAYING), f.listeningEvents)
        f.route.selectDirect(false);yield()
        assertEquals(listOf(DirectListeningEvent.PLAYING, DirectListeningEvent.SUSPENDED), f.listeningEvents)
        assertEquals(0, f.recreations)
        f.engine.terminal.complete(terminal(true));settled(f)
        assertEquals(listOf("activated", "terminal:STOPPED", "deactivated", "recreate"), f.listeningTrace)
        assertEquals(DirectListeningEvent.STOPPED, f.listeningEvents.last())
        assertTrue(f.listeningItems.all { it == "listening-original" })
        val recorded = f.listeningEvents.toList()
        oldProgress(UsbFullProgress(88200, 441000, 44100))
        f.route.stopDirect()
        assertEquals(recorded, f.listeningEvents)
        assertFalse(f.route.playWhenReady)
    }

    @Test fun joinedFailureAfterProvisionalStoppingReachesErrorExactlyOnce() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem("failed-item"));f.route.prepare();yield()
        f.engine.progress(UsbFullProgress(44100, 441000, 44100))
        f.engine.progress(UsbFullProgress(44100, 441000, 44100, UsbFullTransportPhase.FAILED))
        assertEquals(IntegratedDirectPhase.STOPPING, f.route.directState.phase)
        assertEquals(listOf(DirectListeningEvent.PLAYING, DirectListeningEvent.SUSPENDED), f.listeningEvents)
        f.engine.terminal.complete(terminal(true).copy(status = UsbSilentStatus.USB_TRANSPORT_FAILED))
        withTimeout(5000) { while (f.route.directState.phase != IntegratedDirectPhase.ERROR) yield() }
        assertEquals(DirectListeningEvent.ERROR, f.listeningEvents.last())
        assertFalse(f.listeningEvents.contains(DirectListeningEvent.STOPPED))
        f.route.stopDirect();yield()
        assertEquals(1, f.listeningEvents.count { it == DirectListeningEvent.ERROR })
        assertFalse(f.listeningEvents.contains(DirectListeningEvent.STOPPED))
    }

    @Test fun shutdownRetainsListeningOwnershipUntilWorkerTerminal() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem("shutdown-item"));f.route.prepare();yield()
        f.engine.progress(UsbFullProgress(44100, 441000, 44100))
        val shutdown = f.scope.async { f.route.shutdown() }
        yield()
        assertFalse(shutdown.isCompleted)
        assertFalse(f.listeningTrace.contains("deactivated"))
        f.engine.terminal.complete(terminal(true))
        assertTrue(shutdown.await())
        assertEquals(listOf("activated", "terminal:STOPPED", "deactivated"), f.listeningTrace)
        assertEquals(listOf(DirectListeningEvent.PLAYING, DirectListeningEvent.SUSPENDED, DirectListeningEvent.STOPPED), f.listeningEvents)
        assertEquals(0, f.recreations)
    }

    @Test fun activationWaitsForReleaseIgnoresRepeatTapsAndDoesNotPlay() = checkRoute { f ->
        assertFalse(f.route.directState.enabled)
        f.route.selectDirect(true);f.route.selectDirect(true);f.route.selectDirect(false)
        assertEquals(1, f.authorizations);assertEquals(1, f.releases)
        assertEquals(DirectOutputSwitch.TO_USB, f.route.directState.switching)
        assertFalse(f.route.directState.confirmedDirect)
        f.route.prepare();assertEquals(0, f.engine.starts)
        f.released.complete(true);settled(f)
        assertTrue(f.route.directState.confirmedDirect);assertEquals(0, f.engine.starts)
        f.route.selectDirect(false);settled(f)
        assertFalse(f.route.directState.enabled);assertTrue(f.reservationReleased)
        assertEquals(1, f.recreations);assertEquals("12", f.route.currentMediaItem?.mediaId)
        assertEquals(2000L, f.route.currentPosition);assertFalse(f.route.playWhenReady)
    }

    @Test fun failedPrerequisiteLeavesAndroidUntouchedAndFailedReleaseCannotRestore() = checkRoute { f ->
        f.authorizationFailure = "DIRECT_USB_PERMISSION_FAILED"
        f.route.selectDirect(true)
        assertFalse(f.route.directState.enabled);assertEquals(0, f.releases);assertEquals(0, f.physical.stopped)
        f.authorizationFailure = null;f.route.selectDirect(true);f.released.complete(false);settled(f)
        assertFalse(f.route.directState.confirmedDirect)
        assertEquals("NORMAL_BACKEND_RELEASE_UNVERIFIED", f.route.directState.error)
        f.route.selectDirect(false);settled(f)
        assertEquals(0, f.recreations);assertFalse(f.route.directState.cleanupSafe)
        assertTrue(f.reservationReleased)
    }

    @Test fun returnDuringPreparationWaitsForJoinedEngineAndCleanupFailureBlocksAndroid() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);yield()
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        assertEquals(IntegratedDirectPhase.PREPARING, f.route.directState.phase)
        f.route.selectDirect(false);yield()
        assertEquals(DirectOutputSwitch.TO_ANDROID, f.route.directState.switching)
        assertEquals(0, f.recreations)
        f.engine.terminal.complete(terminal(false));settled(f)
        assertTrue(f.route.directState.enabled);assertFalse(f.route.directState.cleanupSafe)
        assertEquals("DIRECT_RESOURCE_CLEANUP_UNVERIFIED", f.route.directState.error)
        assertEquals(0, f.recreations);assertFalse(f.reservationReleased)
    }

    @Test fun returnDuringPauseAndSeekUsesSameStopJoinAndPreservesAndroidSnapshot() = checkRoute(playing = false) { f ->
        f.released.complete(true);f.route.selectDirect(true);yield()
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        f.engine.progress(UsbFullProgress(44100, 441000, 44100, UsbFullTransportPhase.PLAYING,
            mapOf("sourceSeekCapable" to 1L)))
        f.route.pauseDirectPlayback()
        f.engine.progress(UsbFullProgress(44100, 441000, 44100, UsbFullTransportPhase.PAUSED,
            mapOf("sourceSeekCapable" to 1L)))
        f.route.seekDirectPlayback(5000)
        assertEquals(IntegratedDirectPhase.SEEKING, f.route.directState.phase)
        f.route.selectDirect(false);yield();assertEquals(0, f.recreations)
        f.engine.terminal.complete(terminal(true));settled(f)
        assertFalse(f.route.directState.enabled);assertEquals(1, f.recreations)
        assertEquals(2000L, f.route.currentPosition);assertFalse(f.route.playWhenReady)
    }

    @Test fun attachmentLossDuringActivationExitsOnlyAfterNormalReleaseAndSafeCleanup() = checkRoute { f ->
        f.route.selectDirect(true);f.attachmentFailure = "DIRECT_USB_DEVICE_UNAVAILABLE"
        f.route.usbSelectionChanged();assertEquals(0, f.recreations)
        f.released.complete(true);settled(f)
        assertFalse(f.route.directState.enabled);assertEquals(1, f.recreations)
        assertTrue(f.reservationReleased)
    }

    @Test fun recreationFailureRemainsVisibleAndFreshRouteDefaultsToAndroid() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);yield()
        f.recreationFails = true;f.route.selectDirect(false);settled(f)
        assertTrue(f.route.directState.enabled);assertTrue(f.reservationReleased)
        assertEquals("NORMAL_BACKEND_RECREATION_FAILED", f.route.directState.error)
        val fresh = Fixture()
        try { assertFalse(fresh.route.directState.enabled);assertEquals(0, fresh.engine.starts) }
        finally { fresh.close() }
    }

    @Test fun failedNativePlaybackWithVerifiedCleanupRemainsSelectedUntilExplicitReturn() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);yield()
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        f.engine.terminal.complete(terminal(true).copy(status = UsbSilentStatus.USB_TRANSPORT_FAILED))
        withTimeout(5000) { while (f.route.directState.phase != IntegratedDirectPhase.ERROR) yield() }
        assertTrue(f.route.directState.enabled);assertTrue(f.route.directState.cleanupSafe)
        assertEquals(IntegratedDirectPhase.ERROR, f.route.directState.phase)
        assertEquals(0, f.recreations)
        f.route.selectDirect(false);settled(f)
        assertFalse(f.route.directState.enabled);assertEquals(1, f.recreations)
    }

    @Test fun shutdownWaitsForPendingSwitchAndDoesNotRecreateAndroid() = checkRoute { f ->
        f.route.selectDirect(true)
        val shutdown = f.scope.async { f.route.shutdown() }
        assertFalse(shutdown.isCompleted)
        f.released.complete(true);settled(f)
        assertTrue(shutdown.await());assertEquals(0, f.recreations)
        f.route.selectDirect(false);assertTrue(f.route.directState.enabled)
    }

    @Test fun detachedBridgeOwnerCannotOverwriteReplacementServiceState() = checkRoute { f ->
        IntegratedDirectPlaybackBridge.attach(f.route)
        IntegratedDirectPlaybackBridge.detach(f.route)
        val fresh = Fixture()
        try {
            IntegratedDirectPlaybackBridge.attach(fresh.route)
            IntegratedDirectPlaybackBridge.publish(f.route, IntegratedDirectPlaybackState(enabled = true, normalReleased = true))
            assertFalse(IntegratedDirectPlaybackBridge.state.value.enabled)
            IntegratedDirectPlaybackBridge.detach(f.route)
            assertTrue(IntegratedDirectPlaybackBridge.state.value.serviceAvailable)
        } finally { IntegratedDirectPlaybackBridge.detach(fresh.route);fresh.close() }
    }

    @Test fun emptyQueueActivatesThroughProductionCallbacksWithoutAutoplayAndReturnsToEmptyAndroid() = checkRoute(playing = false, emptyQueue = true) { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        assertTrue(f.route.directState.confirmedDirect);assertTrue(f.reservation.handedOff)
        assertEquals(0, f.route.mediaItemCount);assertEquals(0, f.engine.starts)
        assertEquals(UsbActivationReservation.PROTECTED, f.route.directState.activation!!.reservation)
        f.route.selectDirect(false);settled(f)
        assertFalse(f.route.directState.enabled);assertEquals(0, f.route.mediaItemCount)
        assertTrue(f.reservationReleased);assertEquals(UsbActivationRestoration.COMPLETED, f.route.directState.activation!!.restoration)
    }

    @Test fun failedSnapshotRollsBackOwnReservationAndReconnectCanActivateWithoutServiceRestart() = checkRoute { f ->
        f.physical.snapshotFails = true
        try {
            f.route.selectDirect(true, f.identity, f.token.generation)
            assertFalse(f.route.directState.enabled);assertTrue(f.reservationReleased)
            assertEquals("DIRECT_NORMAL_SNAPSHOT_FAILED", f.route.directState.error)
            assertEquals(0, f.releases);assertEquals(0, f.engine.starts)
            val evidence = f.route.directState.activation!!
            assertEquals(UsbActivationStage.SNAPSHOT, evidence.failureStage)
            assertEquals(UsbActivationOutput.ANDROID, evidence.output)
            assertEquals(UsbActivationReservation.RELEASED, evidence.reservation)
            assertFalse(formatUsbActivationEvidence(evidence).contains("private URI"))
        } finally { f.physical.snapshotFails = false }
        val old = f.token
        f.gate.select(null);f.token = f.gate.select(f.identity)!!
        f.route.selectDirect(true, f.identity, old.generation)
        assertEquals("DIRECT_USB_SELECTION_CHANGED", f.route.directState.error);assertTrue(f.reservationReleased)
        f.released.complete(true);f.route.selectDirect(true, f.identity, f.token.generation);settled(f)
        assertTrue(f.route.directState.confirmedDirect);assertNull(f.route.directState.activation!!.failure)
    }

    @Test fun failedHandoffRollsBackBeforeNativeWorkAndPreservesFailureAfterAndroidRestoration() = checkRoute { f ->
        f.commitFails = "DIRECT_USB_SELECTION_CHANGED"
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        assertFalse(f.route.directState.enabled);assertTrue(f.reservationReleased)
        assertEquals(0, f.engine.starts);assertEquals(1, f.recreations)
        val evidence = f.route.directState.activation!!
        assertEquals("DIRECT_USB_SELECTION_CHANGED", evidence.failure)
        assertEquals(UsbActivationStage.SELECT_DIRECT, evidence.failureStage)
        assertEquals(UsbActivationOutput.ANDROID, evidence.output)
        assertEquals(UsbActivationRestoration.COMPLETED, evidence.restoration)
    }

    @Test fun detachDuringNativeWorkRetainsProtectedReservationWhenCleanupFails() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.setMediaItem(song.toPlayableMediaItem());f.route.prepare();yield()
        f.gate.select(null);f.route.usbSelectionChanged();yield()
        assertTrue(f.reservation.handedOff);assertFalse(f.reservationReleased)
        f.token = f.gate.select(f.identity)!!
        f.engine.terminal.complete(terminal(false));settled(f)
        assertFalse(f.reservationReleased);assertEquals(0, f.recreations)
        val evidence = f.route.directState.activation!!
        assertEquals(UsbActivationReservation.PROTECTED, evidence.reservation)
        assertEquals(UsbActivationRestoration.BLOCKED, evidence.restoration)
        assertEquals("DIRECT_RESOURCE_CLEANUP_UNVERIFIED", evidence.restorationFailure)
    }

    @Test fun cancellationBeforeHandoffRollsBackUsbButDoesNotClaimUnverifiedAndroidRestoration() = checkRoute { f ->
        f.route.selectDirect(true);assertFalse(f.reservationReleased)
        f.scope.cancel();settled(f)
        assertTrue(f.reservationReleased);assertEquals(0, f.engine.starts)
        assertEquals("DIRECT_SWITCH_CANCELLED", f.route.directState.activation!!.failure)
        assertEquals(UsbActivationRestoration.BLOCKED, f.route.directState.activation!!.restoration)
        assertEquals(0, f.recreations)
    }

    @Test fun cancelledSecondActivationCannotReuseTheFirstNormalReleaseConfirmation() = checkRoute { f ->
        f.released.complete(true);f.route.selectDirect(true);settled(f)
        f.route.selectDirect(false);settled(f);assertEquals(1, f.recreations)
        f.released = CompletableDeferred()
        f.route.selectDirect(true);f.scope.cancel();settled(f)
        assertFalse(f.route.directState.normalReleased);assertTrue(f.reservationReleased)
        assertEquals(UsbActivationRestoration.BLOCKED, f.route.directState.activation!!.restoration)
        f.route.selectDirect(false);settled(f)
        assertEquals(1, f.recreations);assertEquals(0, f.engine.starts)
    }
}
