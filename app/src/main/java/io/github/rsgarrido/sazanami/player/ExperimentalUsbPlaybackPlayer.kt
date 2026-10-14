package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import io.github.rsgarrido.sazanami.player.usb.*
import android.os.Handler
import kotlinx.coroutines.*

/** One authoritative Player for the session. Normal commands retain the existing SmoothPlaybackPlayer. */
@OptIn(UnstableApi::class)
internal class ExperimentalUsbPlaybackPlayer(
    private val normal: SmoothPlaybackPlayer, private val scope: CoroutineScope, usb: io.github.rsgarrido.sazanami.player.usb.FullUsbPlaybackEngineClient,
    private val releaseNormal: suspend ()->Boolean, private val recreateNormal: ()->Player,
    private val acquireFocus: ()->Boolean={true}, private val releaseFocus: ()->Unit={},
    private val predecessorReleased: ()->Boolean={true},
    private val selection: UsbOutputSelectionCallbacks=UsbOutputSelectionCallbacks { null }
) : ForwardingSimpleBasePlayer(normal) {
    var directState=IntegratedDirectPlaybackState();private set
    private var selectedDirect=false
    private var normalReleaseSafe=false
    private var smoothEnabled=true
    private var shuffleEnabled=false
    private var modeJob: Job?=null
    private var switching: DirectOutputSwitch?=null
    private var safetyExitRequested=false
    private var selectionOwner: UsbActivationOwner?=null
    private var selectionHandedOff=false
    private var activationSerial=0L
    private var normalReleaseStarted=false
    private val modeHandler=Handler(normal.applicationLooper)
    private var safetyExitHandled=false
    private var savedItems: List<MediaItem> = emptyList()
    private var savedIndex=0
    private var savedPosition=0L
    private var savedVolume=1f
    private var savedParameters=PlaybackParameters.DEFAULT
    private var shuttingDown=false
    private val direct=DirectUsbMediaPlayer(scope,usb,changed={ value ->
        if(selectedDirect && switching != DirectOutputSwitch.TO_USB) {
            directState=value.copy(switching=switching,normalReleased=normalReleaseSafe,cleanupSafe=value.cleanupSafe && normalReleaseSafe,
                error=if(normalReleaseSafe) value.error else directState.error ?: "NORMAL_BACKEND_RELEASE_UNVERIFIED",
                activation=directState.activation)
            IntegratedDirectPlaybackBridge.publish(this,directState)
        }
    },acquireFocus=acquireFocus,releaseFocus=releaseFocus)
    private fun publish() {
        directState=directState.copy(activation=directState.activation?.copy(output=when {
            switching!=null -> UsbActivationOutput.SWITCHING
            selectedDirect && normalReleaseSafe && selectionHandedOff -> UsbActivationOutput.DIRECT
            selectedDirect -> UsbActivationOutput.BLOCKED
            else -> UsbActivationOutput.ANDROID
        }))
        IntegratedDirectPlaybackBridge.publish(this,directState)
    }
    private fun stage(value: UsbActivationStage) {
        directState=directState.copy(activation=directState.activation?.copy(stage=value))
    }
    private fun failure(reason: String, exception: Exception?=null) {
        val previous=directState.activation
        directState=directState.copy(error=reason,activation=previous?.copy(
            failure=previous.failure ?: reason,failureStage=previous.failureStage ?: previous.stage,
            exceptionClass=previous.exceptionClass ?: exception?.javaClass?.simpleName))
    }
    private fun rollback(owner: UsbActivationOwner): Boolean {
        val safe=runCatching { selection.rollback(owner) }.getOrDefault(false)
        if(directState.activation?.serial==owner.serial) {
            directState=directState.copy(activation=directState.activation?.copy(reservation=
                if(safe) UsbActivationReservation.RELEASED else UsbActivationReservation.RELEASE_UNVERIFIED))
        }
        if(safe && selectionOwner===owner) { selectionOwner=null;selectionHandedOff=false }
        return safe
    }
    private fun launchSwitch(target: DirectOutputSwitch, block: suspend ()->Unit) {
        val serial=directState.activation?.serial
        val job=scope.launch(start=CoroutineStart.LAZY) { block() }
        modeJob=job
        // A cancelled lazy job may never enter its body/finally. Keep rollback on the player looper.
        job.invokeOnCompletion { cause ->
            if(cause!=null) modeHandler.post {
                if(switching!=target || directState.activation?.serial!=serial) return@post
                if(target==DirectOutputSwitch.TO_USB && !selectionHandedOff) {
                    selectionOwner?.let(::rollback)
                    if(!normalReleaseStarted && !shuttingDown) {
                        val restored=runCatching { setPlayer(normal) }.isSuccess
                        selectedDirect=!restored
                    }
                }
                switching=null;modeJob=null
                failure("DIRECT_SWITCH_CANCELLED")
                directState=directState.copy(enabled=selectedDirect,normalReleased=selectedDirect && normalReleaseSafe,
                    switching=null,phase=IntegratedDirectPhase.ERROR,
                    cleanupSafe=!selectedDirect,activation=directState.activation?.copy(restoration=
                        if(selectedDirect) UsbActivationRestoration.BLOCKED else UsbActivationRestoration.NOT_REQUIRED))
                publish()
            }
        }
        job.start()
    }
    fun selectDirect(enabled: Boolean, identity: UsbAudioDeviceIdentity?=null, generation: Long=0) {
        if(shuttingDown || switching!=null || modeJob?.isCompleted==false) return
        if(!enabled && !selectedDirect) {
            // Only an owned, uncommitted lease can be abandoned without native cleanup.
            selectionOwner?.takeIf { !selectionHandedOff }?.let { rollback(it);publish() }
            return
        }
        if(enabled==selectedDirect) return
        if(enabled) {
            val owner=UsbActivationOwner(++activationSerial)
            directState=directState.copy(error=null,activation=UsbActivationEvidence(owner.serial))
            if(!scope.isActive || !predecessorReleased()) {
                failure(if(!scope.isActive) "DIRECT_SERVICE_UNAVAILABLE" else "DIRECT_PREDECESSOR_CLEANUP_PENDING");publish();return
            }
            stage(UsbActivationStage.PREFLIGHT)
            val rejected=try { selection.authorize(owner,identity,generation) } catch(error: Exception) {
                // No rollback of someone else's lease; the controller enforces owner identity.
                val abandoned=runCatching { selection.rollback(owner) }.getOrDefault(false)
                if(abandoned) directState=directState.copy(activation=directState.activation?.copy(reservation=UsbActivationReservation.RELEASED))
                failure("DIRECT_AUTHORIZATION_FAILED",error);publish();return
            }
            if(rejected!=null) { failure(rejected);publish();return }
            selectionOwner=owner;selectionHandedOff=false;normalReleaseStarted=false;normalReleaseSafe=false
            directState=directState.copy(activation=directState.activation?.copy(reservation=UsbActivationReservation.RESERVED))
            stage(UsbActivationStage.SNAPSHOT)
            try {
                savedItems=(0 until normal.mediaItemCount).map(normal::getMediaItemAt)
                savedIndex=normal.currentMediaItemIndex.coerceAtLeast(0);savedPosition=normal.currentPosition.coerceAtLeast(0)
                savedVolume=normal.volume;savedParameters=normal.playbackParameters
            } catch(error: Exception) {
                failure("DIRECT_NORMAL_SNAPSHOT_FAILED",error);rollback(owner);publish();return
            }
            safetyExitHandled=false;safetyExitRequested=false
            switching=DirectOutputSwitch.TO_USB
            stage(UsbActivationStage.SELECT_DIRECT)
            try { setPlayer(direct);selectedDirect=true } catch(error: Exception) {
                selectedDirect=!runCatching { setPlayer(normal) }.isSuccess
                switching=null
                failure("DIRECT_BACKEND_SELECTION_FAILED",error);rollback(owner)
                directState=directState.copy(enabled=selectedDirect,cleanupSafe=!selectedDirect);publish();return
            }
            directState=direct.evidence.copy(enabled=true,switching=switching,normalReleased=false,cleanupSafe=false,
                activation=directState.activation)
            publish()
            var restoreAfterFailure=false
            launchSwitch(DirectOutputSwitch.TO_USB) {
                try {
                    stage(UsbActivationStage.RELEASE_NORMAL);normalReleaseStarted=true
                    // Stop and join normal renderers before allowing native source/USB work.
                    val released=try { normal.stop();releaseNormal() } catch(cancelled: CancellationException) { throw cancelled }
                        catch(error: Exception) { failure("NORMAL_BACKEND_RELEASE_UNVERIFIED",error);false }
                    normalReleaseSafe=released
                    directState=directState.copy(normalReleased=released,cleanupSafe=released)
                    if(!released) {
                        failure("NORMAL_BACKEND_RELEASE_UNVERIFIED");rollback(owner)
                        directState=directState.copy(phase=IntegratedDirectPhase.ERROR,
                            activation=directState.activation?.copy(restoration=UsbActivationRestoration.BLOCKED))
                    } else if(shuttingDown) {
                        stage(UsbActivationStage.SHUTDOWN);rollback(owner)
                    } else {
                        stage(UsbActivationStage.SELECT_DIRECT)
                        val invalid=selection.failure(owner)
                        val commitFailure=invalid ?: if(safetyExitRequested) "DIRECT_USB_SELECTION_CHANGED" else selection.commit(owner)
                        if(commitFailure!=null) {
                            failure(commitFailure);rollback(owner)
                            restoreAfterFailure=true
                        } else {
                            selectionHandedOff=true
                            directState=direct.evidence.copy(enabled=true,normalReleased=true,switching=switching,
                                activation=directState.activation?.copy(stage=UsbActivationStage.ACTIVE,reservation=UsbActivationReservation.PROTECTED))
                        }
                    }
                } catch(error: Exception) {
                    failure(if(error is CancellationException) "DIRECT_SWITCH_CANCELLED" else "DIRECT_BACKEND_SELECTION_FAILED",error)
                    if(!selectionHandedOff) rollback(owner)
                    directState=directState.copy(phase=IntegratedDirectPhase.ERROR,
                        activation=directState.activation?.copy(restoration=UsbActivationRestoration.BLOCKED))
                    restoreAfterFailure=normalReleaseSafe && !shuttingDown && error !is CancellationException
                } finally {
                    switching=null;modeJob=null
                    directState=directState.copy(switching=null);publish()
                }
                if(restoreAfterFailure && !shuttingDown) { safetyExitHandled=true;selectDirect(false) }
            }
        } else {
            switching=DirectOutputSwitch.TO_ANDROID
            stage(UsbActivationStage.RESTORE_ANDROID)
            directState=directState.copy(switching=switching,error=null,
                activation=directState.activation?.copy(restoration=UsbActivationRestoration.PENDING))
            publish()
            launchSwitch(DirectOutputSwitch.TO_ANDROID) {
                var restorationStarted=false
                var restorationFailure="NORMAL_BACKEND_RECREATION_FAILED"
                try {
                    if(!normalReleaseSafe || !direct.stopAndJoin()) {
                        failure("DIRECT_RESOURCE_CLEANUP_UNVERIFIED")
                        directState=directState.copy(phase=IntegratedDirectPhase.ERROR,cleanupSafe=false,
                            activation=directState.activation?.copy(restoration=UsbActivationRestoration.BLOCKED,
                                restorationFailure="DIRECT_RESOURCE_CLEANUP_UNVERIFIED"))
                        return@launchSwitch
                    }
                    if(shuttingDown) return@launchSwitch
                    restorationStarted=true
                    val restored=recreateNormal()
                    normal.rebindPhysicalPlayer(restored,baselineVolume=savedVolume,logicalPlayWhenReady=false)
                    normal.setSmoothPlaybackEnabled(smoothEnabled);normal.setLogicalShuffleModeEnabled(shuffleEnabled);normal.playbackParameters=savedParameters
                    if(savedItems.isNotEmpty()) normal.setMediaItems(savedItems,savedIndex.coerceAtMost(savedItems.lastIndex),savedPosition)
                    setPlayer(normal)
                    selectionOwner?.let { owner ->
                        val released=if(selectionHandedOff) selection.release(owner,cleanupSafe=true) else selection.rollback(owner)
                        if(!released) {
                            restorationFailure="DIRECT_RESERVATION_RELEASE_UNVERIFIED"
                            throw IllegalStateException()
                        }
                        selectionOwner=null;selectionHandedOff=false
                    }
                    selectedDirect=false
                    val evidence=directState.activation?.copy(reservation=if(directState.activation?.reservation==UsbActivationReservation.NOT_ACQUIRED)
                        UsbActivationReservation.NOT_ACQUIRED else UsbActivationReservation.RELEASED,restoration=UsbActivationRestoration.COMPLETED)
                    directState=IntegratedDirectPlaybackState(activation=evidence)
                } catch(error: Exception) {
                    runCatching { setPlayer(direct) }
                    if(restorationStarted) normalReleaseSafe=runCatching { releaseNormal() }.getOrDefault(false)
                    else if(error is CancellationException) restorationFailure="DIRECT_SWITCH_CANCELLED"
                    failure(restorationFailure,error)
                    directState=directState.copy(phase=IntegratedDirectPhase.ERROR,normalReleased=normalReleaseSafe,cleanupSafe=false,
                        activation=directState.activation?.copy(restoration=UsbActivationRestoration.BLOCKED,
                            restorationFailure=restorationFailure))
                } finally {
                    switching=null;modeJob=null
                    directState=directState.copy(switching=null);publish()
                }
            }
        }
    }
    /** Attachment/permission loss never silently selects another DAC. */
    fun usbSelectionChanged() {
        val owner=selectionOwner ?: return
        if(!selectedDirect || shuttingDown || safetyExitHandled) return
        val invalid=selection.failure(owner) ?: return
        if(switching==DirectOutputSwitch.TO_USB) safetyExitRequested=true
        else if(switching==null) {
            stage(UsbActivationStage.ATTACHMENT_LOST);failure(invalid)
            safetyExitHandled=true;selectDirect(false)
        }
    }
    override fun handleSetMediaItems(mediaItems: List<MediaItem>,startIndex: Int,startPositionMs: Long): com.google.common.util.concurrent.ListenableFuture<*> {
        if(!predecessorReleased() || modeJob?.isCompleted==false) return com.google.common.util.concurrent.Futures.immediateFailedFuture<Unit>(IllegalStateException("DIRECT_SWITCH_PENDING"))
        return super.handleSetMediaItems(mediaItems,startIndex,startPositionMs)
    }
    override fun handlePrepare(): com.google.common.util.concurrent.ListenableFuture<*> {
        if(!predecessorReleased() || modeJob?.isCompleted==false || (selectedDirect && (!normalReleaseSafe || !selectionHandedOff || (directState.phase==IntegratedDirectPhase.ERROR && !directState.cleanupSafe)))) return com.google.common.util.concurrent.Futures.immediateVoidFuture()
        return super.handlePrepare()
    }
    fun stopDirect() { if(selectedDirect) direct.requestStop() }
    fun toggleDirectPlayback() { if(selectedDirect && selectionHandedOff && switching==null && normalReleaseSafe && direct.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) direct.playWhenReady=!direct.playWhenReady }
    fun pauseDirectPlayback() { if(selectedDirect && selectionHandedOff && switching==null && normalReleaseSafe && direct.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) direct.pause() }
    fun seekDirectPlayback(positionMs: Long) {
        if(selectedDirect && selectionHandedOff && switching==null && normalReleaseSafe && direct.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) direct.seekTo(positionMs)
        else if(selectedDirect) direct.notice("Source seek")
    }
    fun rejectDirectCommand(command: String) { if(selectedDirect) direct.notice(command) }
    suspend fun shutdown(): Boolean {
        shuttingDown=true;modeJob?.join()
        val safe=direct.stopAndJoin()
        selectionOwner?.let { owner ->
            stage(UsbActivationStage.SHUTDOWN)
            if(!selectionHandedOff) rollback(owner)
            else if(runCatching { selection.release(owner,safe) }.getOrDefault(false)) {
                selectionOwner=null;selectionHandedOff=false
                directState=directState.copy(activation=directState.activation?.copy(reservation=UsbActivationReservation.RELEASED))
            }
        }
        publish()
        return safe && (!selectedDirect || normalReleaseSafe) && selectionOwner==null
    }
    fun releaseTransitionResources()=normal.releaseTransitionResources()
    fun setSmoothPlaybackEnabled(enabled: Boolean) { smoothEnabled=enabled;if(!selectedDirect) normal.setSmoothPlaybackEnabled(enabled) }
    fun setLogicalShuffleModeEnabled(enabled: Boolean) { shuffleEnabled=enabled;if(!selectedDirect) normal.setLogicalShuffleModeEnabled(enabled) }
    fun markNextShuffleModeCommandExternal() { if(!selectedDirect) normal.markNextShuffleModeCommandExternal() }
    fun updateArtwork(item: MediaItem,uri: Uri?) { if(selectedDirect) direct.updateArtwork(item,uri) else normal.updateArtwork(item,uri) }
}
