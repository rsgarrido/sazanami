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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual routing adapter, fake physical player and USB engine; no JNI or hardware. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class UsbOutputSwitchingTest {
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
        var starts = 0
        var stops = 0
        lateinit var progress: (UsbFullProgress) -> Unit
        lateinit var control: UsbFullPlaybackControl
        val terminal = CompletableDeferred<UsbSilentTransportExperimentResult>()
        override suspend fun runIntegratedFull(uri: Uri, control: UsbFullPlaybackControl, progress: (UsbFullProgress) -> Unit): UsbSilentTransportExperimentResult {
            starts++;this.control = control;this.progress = progress;control.bind({true}, {})
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
        val reservation = UsbOutputReservation()
        val gate = UsbAudioGenerationGate()
        val identity = UsbAudioDeviceIdentity("fake", 1, 2, 3)
        var token = gate.select(identity)!!
        val reservationReleased get() = reservation.token == null
        var commitFails: String? = null
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
            recreateNormal = { recreations++;if (recreationFails) error("restore failed");Physical(if (emptyQueue) null else song.toPlayableMediaItem(), false) },
            selection = UsbOutputSelectionCallbacks { controller })
        suspend fun close() {
            engine.terminal.complete(terminal(true));released.complete(true)
            route.shutdown();route.releaseTransitionResources();route.release();scope.cancel()
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
        assertTrue(f.route.directState.enabled);assertFalse(f.reservationReleased)
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
