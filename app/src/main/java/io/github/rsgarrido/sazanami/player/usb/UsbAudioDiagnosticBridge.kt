package io.github.rsgarrido.sazanami.player.usb

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
    @Synchronized fun permissionResult(requestId: String, identity: UsbAudioDeviceIdentity?, granted: Boolean) {
        owner?.permissionResult(requestId, identity, granted)
    }
}
