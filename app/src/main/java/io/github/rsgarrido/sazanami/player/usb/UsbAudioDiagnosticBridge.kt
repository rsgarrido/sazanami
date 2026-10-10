package io.github.rsgarrido.sazanami.player.usb

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Narrow process-local diagnostics bridge, following AdvancedAudioRuntimeBridge.
 * Only the service-owned controller publishes. UI commands are dispatched onto its looper.
 * No Player/session commands or Activity-owned USB handles are introduced.
 */
internal object UsbAudioDiagnosticBridge {
    private var owner: UsbAudioController? = null
    private val mutableState = MutableStateFlow(UsbAudioDiagnosticState())
    val state: StateFlow<UsbAudioDiagnosticState> = mutableState.asStateFlow()

    @Synchronized fun attach(controller: UsbAudioController) { owner = controller }
    @Synchronized fun publish(controller: UsbAudioController, state: UsbAudioDiagnosticState) {
        if (owner === controller) mutableState.value = state
    }
    @Synchronized fun detach(controller: UsbAudioController) {
        if (owner === controller) {
            owner = null
            mutableState.value = UsbAudioDiagnosticState()
        }
    }
    @Synchronized fun refresh() { owner?.refresh() }
    @Synchronized fun select(identity: UsbAudioDeviceIdentity) { owner?.select(identity) }
    @Synchronized fun requestPermission(identity: UsbAudioDeviceIdentity) { owner?.requestPermission(identity) }
    @Synchronized fun queryClockCapabilities(identity: UsbAudioDeviceIdentity) { owner?.queryClockCapabilities(identity) }
    @Synchronized fun testAudioControlClaim(identity: UsbAudioDeviceIdentity) { owner?.testAudioControlClaim(identity) }
    @Synchronized fun inspectKernelOwnership(identity: UsbAudioDeviceIdentity) { owner?.inspectKernelOwnership(identity) }
    @Synchronized fun forceOwnAudioControl(identity: UsbAudioDeviceIdentity) { owner?.forceOwnAudioControl(identity) }
    @Synchronized fun configurePcm16(identity: UsbAudioDeviceIdentity) { owner?.configurePcm16(identity) }
    @Synchronized fun streamSilentPcm16(identity: UsbAudioDeviceIdentity) { owner?.streamSilentPcm16(identity) }
    @Synchronized fun playReferencePcm16(identity: UsbAudioDeviceIdentity) { owner?.playReferencePcm16(identity) }
    @Synchronized fun playFlacPrefix(identity: UsbAudioDeviceIdentity, generation: Long, uri: Uri) { owner?.playFlacPrefix(identity, generation, uri) }
    @Synchronized fun playFullFlac(identity: UsbAudioDeviceIdentity, generation: Long, uri: Uri) { owner?.playFullFlac(identity,generation,uri) }
    @Synchronized fun stopFullFlac(identity: UsbAudioDeviceIdentity, generation: Long, stream: Long) { owner?.stopFullFlac(identity,generation,stream) }
    @Synchronized fun permissionResult(requestId: String, identity: UsbAudioDeviceIdentity?, granted: Boolean) {
        owner?.permissionResult(requestId, identity, granted)
    }
}
