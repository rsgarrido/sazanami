package io.github.rsgarrido.sazanami.player.usb

/** deviceName is Android's transient bus key, used for matching only; never copied to reports. */
internal data class UsbAudioDeviceIdentity(
    val deviceName: String,
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int
)

internal data class UsbAndroidEndpointSnapshot(
    val address: Int,
    val attributes: Int,
    val maxPacketSize: Int,
    val interval: Int
)

internal data class UsbAndroidInterfaceSnapshot(
    val number: Int,
    val alternate: Int,
    val interfaceClass: Int,
    val subclass: Int,
    val protocol: Int,
    val endpoints: List<UsbAndroidEndpointSnapshot>
)

internal data class UsbAudioDeviceSnapshot(
    val identity: UsbAudioDeviceIdentity,
    val manufacturer: String?,
    val product: String?,
    val deviceClass: Int,
    val interfaces: List<UsbAndroidInterfaceSnapshot>,
    val hasPermission: Boolean
) {
    val hasAudioInterface: Boolean get() = interfaces.any { it.interfaceClass == 1 }
}

internal enum class UsbAudioDiagnosticStatus {
    SERVICE_UNAVAILABLE, NO_DEVICE, SELECT_DEVICE, PERMISSION_REQUIRED,
    REQUESTING_PERMISSION, PERMISSION_DENIED, OPENING, DESCRIPTORS_READ, ERROR
}

internal data class UsbAudioDiagnosticState(
    val serviceAvailable: Boolean = false,
    val status: UsbAudioDiagnosticStatus = UsbAudioDiagnosticStatus.SERVICE_UNAVAILABLE,
    val devices: List<UsbAudioDeviceSnapshot> = emptyList(),
    val selectedIdentity: UsbAudioDeviceIdentity? = null,
    val generation: Long = 0,
    val descriptors: UsbAudioDescriptorModel? = null,
    val message: String? = null
) {
    val selectedDevice: UsbAudioDeviceSnapshot?
        get() = devices.firstOrNull { it.identity == selectedIdentity }
}

internal data class UsbAudioInspectionToken(val identity: UsbAudioDeviceIdentity, val generation: Long)

internal data class UsbAudioPermissionRequest(val id: String, val token: UsbAudioInspectionToken) {
    fun matches(resultId: String, identity: UsbAudioDeviceIdentity?, gate: UsbAudioGenerationGate): Boolean =
        id == resultId && token.identity == identity && gate.accepts(token)
}

/** Pure stale-work gate. Android controller serializes calls; tests need no USB hardware. */
internal class UsbAudioGenerationGate {
    var generation: Long = 0
        private set
    var current: UsbAudioInspectionToken? = null
        private set

    fun select(identity: UsbAudioDeviceIdentity?): UsbAudioInspectionToken? {
        generation++
        current = identity?.let { UsbAudioInspectionToken(it, generation) }
        return current
    }

    fun accepts(token: UsbAudioInspectionToken): Boolean = current == token
}
