package io.github.rsgarrido.sazanami.player.usb

/** Integrated playback always requires both persisted consent and per-session authorization. */
internal enum class UsbPlaybackOperation { INTEGRATED_PLAYBACK, DEVELOPER_EXPERIMENT }
internal object UsbPlaybackPolicy {
    fun allows(operation: UsbPlaybackOperation, debug: Boolean, authorized: Boolean = false,
        experimentalOptIn: Boolean = false): Boolean =
        when (operation) {
            UsbPlaybackOperation.INTEGRATED_PLAYBACK -> experimentalOptIn && authorized
            UsbPlaybackOperation.DEVELOPER_EXPERIMENT -> debug
        }

    fun integratedAccessFailure(experimentalOptIn: Boolean): String? =
        if (allows(UsbPlaybackOperation.INTEGRATED_PLAYBACK, debug = false,
                authorized = true, experimentalOptIn = experimentalOptIn)) null
        else "DIRECT_EXPERIMENTAL_OPT_IN_REQUIRED"
}

/** Descriptor evidence only; clock, speed and actual track configuration are checked later. */
internal fun UsbAudioDiagnosticState.integratedCandidate(): UsbSilentTransportResolution? {
    val model = descriptors ?: return null
    val device = selectedDevice ?: return null
    return listOf(44100L to 16L, 48000L to 16L, 48000L to 24L, 96000L to 24L)
        .map { (rate, bits) -> UsbSilentTransportPlan.resolve(model, device.interfaces, rate, bits, 2) }
        .firstOrNull { it.plan != null }
}

internal fun UsbAudioDiagnosticState.activationFailure(identity: UsbAudioDeviceIdentity?, expectedGeneration: Long): String? = when {
    !serviceAvailable -> "DIRECT_SERVICE_UNAVAILABLE"
    devices.none { it.hasAudioInterface } -> "DIRECT_USB_DEVICE_UNAVAILABLE"
    identity == null || selectedDevice == null -> "DIRECT_USB_SELECTION_REQUIRED"
    selectedIdentity != identity || generation != expectedGeneration -> "DIRECT_USB_SELECTION_CHANGED"
    status == UsbAudioDiagnosticStatus.REQUESTING_PERMISSION -> "DIRECT_USB_PERMISSION_PENDING"
    !requireNotNull(selectedDevice).hasPermission -> "DIRECT_USB_PERMISSION_FAILED"
    status == UsbAudioDiagnosticStatus.OPENING -> "DIRECT_USB_INSPECTION_RUNNING"
    diagnosticOperationRunning -> "DIRECT_USB_OPERATION_RUNNING"
    status != UsbAudioDiagnosticStatus.DESCRIPTORS_READ -> "DIRECT_USB_INSPECTION_REQUIRED"
    integratedCandidate() == null -> "DIRECT_USB_INCOMPATIBLE"
    else -> null
}
