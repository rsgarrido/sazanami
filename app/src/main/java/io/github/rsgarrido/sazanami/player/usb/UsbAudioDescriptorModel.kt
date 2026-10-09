package io.github.rsgarrido.sazanami.player.usb

/** Pure descriptor facts. No Android handles, selected playback route, or inferred rates. */
internal data class UsbInterfaceKey(
    val configurationOffset: Int?,
    val number: Int,
    val alternate: Int
)

internal data class UsbDeviceDescriptor(
    val usbVersion: Int,
    val deviceClass: Int,
    val subclass: Int,
    val protocol: Int,
    val vendorId: Int,
    val productId: Int,
    val deviceVersion: Int,
    val configurationCount: Int
)

internal data class UsbConfigurationDescriptor(
    val offset: Int,
    val value: Int,
    val totalLength: Int,
    val interfaceCount: Int,
    val attributes: Int,
    val maxPowerRaw: Int
)

internal data class UsbInterfaceAssociation(
    val configurationOffset: Int?,
    val firstInterface: Int,
    val interfaceCount: Int,
    val functionClass: Int,
    val subclass: Int,
    val protocol: Int
)

internal data class UsbEndpointDescriptor(
    val address: Int,
    val attributes: Int,
    val maxPacketSizeRaw: Int,
    val interval: Int,
    val refresh: Int? = null,
    val synchronizationAddress: Int? = null,
    val audio: UsbAudioEndpointDescriptor? = null
) {
    val isIn: Boolean get() = address and 0x80 != 0
    val transferType: Int get() = attributes and 3
    val synchronizationType: Int get() = (attributes shr 2) and 3
    val usageType: Int get() = (attributes shr 4) and 3
    val packetPayloadBytes: Int get() = maxPacketSizeRaw and 0x7ff
    val additionalTransactions: Int get() = (maxPacketSizeRaw shr 11) and 3
}

internal data class UsbAudioEndpointDescriptor(
    val attributes: Int,
    val controls: Int,
    val lockDelayUnits: Int,
    val lockDelay: Int
)

internal enum class UsbAudioEntityKind {
    INPUT_TERMINAL, OUTPUT_TERMINAL, FEATURE_UNIT,
    CLOCK_SOURCE, CLOCK_SELECTOR, CLOCK_MULTIPLIER
}

internal data class UsbAudioEntity(
    val id: Int,
    val kind: UsbAudioEntityKind,
    val signalSources: List<Int> = emptyList(),
    val clockSources: List<Int> = emptyList(),
    val terminalType: Int? = null,
    val associatedTerminal: Int? = null,
    val channelCount: Int? = null,
    val channelConfiguration: Long? = null,
    val attributes: Int? = null,
    val controls: Long? = null,
    val channelControls: List<Long> = emptyList()
)

internal data class UsbAudioControlDescriptor(
    val version: Int?,
    val category: Int? = null,
    val totalLength: Int? = null,
    val controls: Int? = null,
    val entities: List<UsbAudioEntity> = emptyList()
)

internal data class UsbAudioStreamingDescriptor(
    val terminalLink: Int,
    val controls: Int,
    val formatType: Int,
    val formats: Long,
    val channelCount: Int,
    val channelConfiguration: Long
)

internal data class UsbAudioFormatDescriptor(
    val type: Int,
    val subslotBytes: Int,
    val validBits: Int
)

internal data class UsbInterfaceDescriptor(
    val key: UsbInterfaceKey,
    val endpointCount: Int,
    val interfaceClass: Int,
    val subclass: Int,
    val protocol: Int,
    val endpoints: List<UsbEndpointDescriptor> = emptyList(),
    val audioControl: UsbAudioControlDescriptor? = null,
    val audioStreaming: UsbAudioStreamingDescriptor? = null,
    val audioFormat: UsbAudioFormatDescriptor? = null
) {
    val isAudioControl: Boolean get() = interfaceClass == 1 && subclass == 1
    val isAudioStreaming: Boolean get() = interfaceClass == 1 && subclass == 2
}

internal data class UsbRawDescriptor(
    val offset: Int,
    val type: Int?,
    val interfaceKey: UsbInterfaceKey?,
    val endpointAddress: Int?,
    val hex: String,
    val interpretation: String
)

internal data class UsbAudioDescriptorModel(
    val byteCount: Int,
    val device: UsbDeviceDescriptor?,
    val configurations: List<UsbConfigurationDescriptor>,
    val associations: List<UsbInterfaceAssociation>,
    val interfaces: List<UsbInterfaceDescriptor>,
    val rawDescriptors: List<UsbRawDescriptor>,
    val warnings: List<String>
)

internal fun usbHex(value: Int, width: Int = 2): String =
    value.toString(16).uppercase(java.util.Locale.ROOT).padStart(width, '0')

internal fun UsbInterfaceKey.diagnosticName(): String =
    "configuration@${configurationOffset ?: "unknown"} interface $number alternate $alternate"
