package io.github.rsgarrido.sazanami.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticBridge
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI observes immutable service state. This bridge owns no player, job, FD or USB handle. */
internal object IntegratedDirectPlaybackBridge {
    private var owner: ExperimentalUsbPlaybackPlayer?=null
    private val mutableState=MutableStateFlow(IntegratedDirectPlaybackState())
    val state=mutableState.asStateFlow()
    private val attempts=IntegratedDirectAttemptEvidence()
    val latestAttempt=attempts.latest
    val reportState get()=state.value.takeIf { it.error!=null || it.switching!=null } ?: latestAttempt.value ?: state.value
    val directSelected get()=state.value.enabled
    fun attach(player: ExperimentalUsbPlaybackPlayer) { owner=player; publish(player,player.directState) }
    fun detach(player: ExperimentalUsbPlaybackPlayer) { if(owner===player) { owner=null; mutableState.value=IntegratedDirectPlaybackState() } }
    fun publish(player: ExperimentalUsbPlaybackPlayer,value: IntegratedDirectPlaybackState) {
        if(owner===player) { attempts.record(value); mutableState.value=value.copy(serviceAvailable=true) }
    }
    fun select(enabled: Boolean,
        identity: UsbAudioDeviceIdentity?=UsbAudioDiagnosticBridge.state.value.selectedIdentity,
        generation: Long=UsbAudioDiagnosticBridge.state.value.generation) {
        owner?.selectDirect(enabled, identity, generation)
    }
    fun stop() { owner?.stopDirect() }
    fun canReplaceTimeline()=owner?.canReplaceDirectTimeline()==true
    fun togglePlayPause() { owner?.toggleDirectPlayback() }
    fun pause() { owner?.pauseDirectPlayback() }
    fun seek(positionMs: Long) { owner?.seekDirectPlayback(positionMs) }
    fun next() { owner?.requestDirectNavigation(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
    fun previous() { owner?.requestDirectNavigation(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) }
    fun shuffle(enabled: Boolean) { owner?.requestDirectShuffle(enabled) }
    fun repeat(mode: Int) { owner?.requestDirectRepeat(mode) }
    @OptIn(UnstableApi::class)
    fun start() { owner?.restartDirectPlayback() }
    fun reject(command: String) { owner?.rejectDirectCommand(command) }
}
