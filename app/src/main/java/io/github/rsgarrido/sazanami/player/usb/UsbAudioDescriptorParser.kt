package io.github.rsgarrido.sazanami.player.usb

/**
 * Parses USB standard and Audio Class 2.0 descriptor facts, without I/O.
 * Field meanings follow USB-IF Audio Devices 2.0 sections 4.7, 4.9 and 4.10.
 * Unknown/UAC1/UAC3 layouts remain raw evidence, never interpreted as UAC2.
 */
internal object UsbAudioDescriptorParser {
    fun parse(bytes: ByteArray): UsbAudioDescriptorModel {
        val warnings = mutableListOf<String>()
        val raw = mutableListOf<UsbRawDescriptor>()
        val configurations = mutableListOf<UsbConfigurationDescriptor>()
        val associations = mutableListOf<UsbInterfaceAssociation>()
        val interfaces = mutableListOf<UsbInterfaceDescriptor>()
        var device: UsbDeviceDescriptor? = null
        var configurationOffset: Int? = null
        var configurationEnd: Int? = null
        var currentInterface: Int? = null
        var currentEndpoint: Int? = null
        var offset = 0

        fun warn(message: String) { warnings += "offset $offset: $message" }
        fun updateInterface(transform: (UsbInterfaceDescriptor) -> UsbInterfaceDescriptor) {
            currentInterface?.let { interfaces[it] = transform(interfaces[it]) }
        }

        while (offset < bytes.size) {
            val remaining = bytes.size - offset
            val length = bytes[offset].toInt() and 0xff
            val type = if (remaining >= 2) bytes[offset + 1].toInt() and 0xff else null
            if (type != 2 && configurationEnd?.let { offset >= it } == true) {
                configurationOffset = null
                configurationEnd = null
                currentInterface = null
                currentEndpoint = null
                warn("descriptor outside the preceding configuration's wTotalLength; scope cleared")
            }
            val key = currentInterface?.let { interfaces[it].key }
            val endpoint = currentInterface?.let { i ->
                currentEndpoint?.let { interfaces[i].endpoints[it].address }
            }
            if (length < 2 || length > remaining) {
                val reason = if (length < 2) "invalid descriptor length $length" else
                    "truncated descriptor: declared $length bytes, only $remaining remain"
                warn("$reason; parsing stopped, remaining bytes retained")
                raw += UsbRawDescriptor(offset, type, key, endpoint,
                    rawHex(bytes, offset, remaining, type), reason)
                break
            }
            if (type != 2 && configurationEnd?.let { offset + length > it } == true) {
                warn("descriptor crosses configuration wTotalLength; interface scope cleared")
                configurationOffset = null
                configurationEnd = null
                currentInterface = null
                currentEndpoint = null
            }
            val d = Descriptor(bytes, offset, length)
            var interpretation = "unrecognized descriptor preserved"
            fun requireLength(minimum: Int): Boolean {
                if (length >= minimum) return true
                warn("descriptor type 0x${usbHex(type!!)} requires $minimum bytes, has $length")
                interpretation = "malformed descriptor preserved"
                return false
            }
            when (type) {
                1 -> {
                    configurationOffset = null
                    configurationEnd = null
                    currentInterface = null
                    currentEndpoint = null
                    if (requireLength(18)) {
                        device = UsbDeviceDescriptor(d.u16(2), d.u8(4), d.u8(5), d.u8(6),
                            d.u16(8), d.u16(10), d.u16(12), d.u8(17))
                        interpretation = "Device (serial string is not queried)"
                    }
                }
                2 -> {
                    if (configurationEnd?.let { offset < it } == true) warn("new configuration starts before preceding wTotalLength")
                    currentInterface = null
                    currentEndpoint = null
                    configurationOffset = offset
                    configurationEnd = null
                    if (requireLength(9)) {
                        val config = UsbConfigurationDescriptor(offset, d.u8(5), d.u16(2),
                            d.u8(4), d.u8(7), d.u8(8))
                        configurations += config
                        if (config.totalLength >= length && config.totalLength <= remaining) {
                            configurationEnd = offset + config.totalLength
                        }
                        if (config.totalLength < length || config.totalLength > remaining) {
                            warn("configuration wTotalLength=${config.totalLength} exceeds available data or is too small")
                            if (config.totalLength < length) configurationOffset = null
                        }
                        interpretation = "Configuration ${config.value}"
                    }
                }
                11 -> {
                    currentInterface = null
                    currentEndpoint = null
                    if (requireLength(8)) {
                        associations += UsbInterfaceAssociation(configurationOffset, d.u8(2),
                            d.u8(3), d.u8(4), d.u8(5), d.u8(6))
                        if (d.u8(3) == 0 || d.u8(2) + d.u8(3) > 256) warn("invalid IAD interface range")
                        interpretation = "Interface Association"
                    }
                }
                4 -> {
                    // Even a malformed new interface must end the preceding descriptor scope.
                    currentInterface = null
                    currentEndpoint = null
                    if (requireLength(9)) {
                        val iface = UsbInterfaceDescriptor(
                            UsbInterfaceKey(configurationOffset, d.u8(2), d.u8(3)),
                            d.u8(4), d.u8(5), d.u8(6), d.u8(7))
                        if (interfaces.any { it.key == iface.key }) warn("duplicate interface/alternate key ${iface.key.diagnosticName()}")
                        interfaces += iface
                        currentInterface = interfaces.lastIndex
                        interpretation = "Interface ${iface.key.number} alternate ${iface.key.alternate}"
                    }
                }
                5 -> {
                    currentEndpoint = null
                    if (requireLength(7)) {
                        if (currentInterface == null) warn("endpoint without interface; not associated")
                        else {
                            val ep = UsbEndpointDescriptor(d.u8(2), d.u8(3), d.u16(4), d.u8(6),
                                if (length >= 9) d.u8(7) else null,
                                if (length >= 9) d.u8(8) else null)
                            updateInterface { it.copy(endpoints = it.endpoints + ep) }
                            currentEndpoint = interfaces[currentInterface!!].endpoints.lastIndex
                            if (ep.address and 0x0f == 0) warn("endpoint zero appears in an interface")
                            interpretation = "Endpoint 0x${usbHex(ep.address)}"
                        }
                    }
                }
                0x24 -> {
                    currentEndpoint = null
                    if (requireLength(3)) {
                        val iface = currentInterface?.let { interfaces[it] }
                        val subtype = d.u8(2)
                        when {
                            iface == null -> warn("class-specific interface descriptor without interface")
                            iface.isAudioControl && subtype == 1 -> {
                                if (requireLength(5)) {
                                    val version = d.u16(3)
                                    val uac2 = version == 0x0200
                                    if (uac2 && requireLength(9)) {
                                        updateInterface { it.copy(audioControl = UsbAudioControlDescriptor(
                                            version, d.u8(5), d.u16(6), d.u8(8),
                                            it.audioControl?.entities.orEmpty())) }
                                        if (iface.protocol != 0x20) warn("UAC2 header disagrees with interface protocol 0x${usbHex(iface.protocol)}")
                                    } else if (!uac2) {
                                        updateInterface { it.copy(audioControl = UsbAudioControlDescriptor(version)) }
                                    }
                                    interpretation = "AudioControl header bcdADC=0x${usbHex(version, 4)}"
                                }
                            }
                            iface.isAudioControl && isUac2(iface) -> {
                                val minimum = when (subtype) {
                                    2 -> 17; 3 -> 12; 6 -> 10; 10 -> 8; 11 -> 7; 12 -> 7
                                    else -> null
                                }
                                if (minimum != null && requireLength(minimum)) {
                                    var entity: UsbAudioEntity? = null
                                    when (subtype) {
                                        2 -> entity = UsbAudioEntity(d.u8(3), UsbAudioEntityKind.INPUT_TERMINAL,
                                            clockSources = listOf(d.u8(7)), terminalType = d.u16(4),
                                            associatedTerminal = d.u8(6), channelCount = d.u8(8),
                                            channelConfiguration = d.u32(9), controls = d.u16(14).toLong())
                                        3 -> entity = UsbAudioEntity(d.u8(3), UsbAudioEntityKind.OUTPUT_TERMINAL,
                                            signalSources = listOf(d.u8(7)), clockSources = listOf(d.u8(8)),
                                            terminalType = d.u16(4), associatedTerminal = d.u8(6),
                                            controls = d.u16(9).toLong())
                                        6 -> {
                                            if ((length - 6) % 4 != 0) warn("Feature Unit controls have invalid length")
                                            else entity = UsbAudioEntity(d.u8(3), UsbAudioEntityKind.FEATURE_UNIT,
                                                signalSources = listOf(d.u8(4)),
                                                channelControls = (5 until length - 1 step 4).map(d::u32))
                                        }
                                        10 -> entity = UsbAudioEntity(d.u8(3), UsbAudioEntityKind.CLOCK_SOURCE,
                                            associatedTerminal = d.u8(6), attributes = d.u8(4), controls = d.u8(5).toLong())
                                        11 -> {
                                            val pins = d.u8(4)
                                            if (pins == 0 || length < 7 + pins) warn("Clock Selector pin array truncated or empty")
                                            else entity = UsbAudioEntity(d.u8(3), UsbAudioEntityKind.CLOCK_SELECTOR,
                                                clockSources = (0 until pins).map { d.u8(5 + it) },
                                                controls = d.u8(5 + pins).toLong())
                                        }
                                        12 -> entity = UsbAudioEntity(d.u8(3), UsbAudioEntityKind.CLOCK_MULTIPLIER,
                                            clockSources = listOf(d.u8(4)), controls = d.u8(5).toLong())
                                    }
                                    entity?.let { parsed ->
                                        if (parsed.id == 0) warn("entity ID zero is invalid")
                                        updateInterface { existing ->
                                            val ac = existing.audioControl ?: UsbAudioControlDescriptor(null)
                                            existing.copy(audioControl = ac.copy(entities = ac.entities + parsed))
                                        }
                                        interpretation = "UAC2 ${parsed.kind} entity ${parsed.id}"
                                    }
                                }
                            }
                            iface.isAudioStreaming && iface.protocol == 0x20 -> {
                                when (subtype) {
                                    1 -> if (requireLength(16)) {
                                        updateInterface { it.copy(audioStreaming = UsbAudioStreamingDescriptor(
                                            d.u8(3), d.u8(4), d.u8(5), d.u32(6), d.u8(10), d.u32(11))) }
                                        interpretation = "UAC2 AS General"
                                    }
                                    2 -> if (requireLength(4)) {
                                        if (d.u8(3) == 1 && requireLength(6)) {
                                            val format = UsbAudioFormatDescriptor(1, d.u8(4), d.u8(5))
                                            updateInterface { it.copy(audioFormat = format) }
                                            if (format.subslotBytes !in 1..4 || format.validBits !in 1..(format.subslotBytes * 8)) {
                                                warn("invalid Type I subslot/valid-bit resolution")
                                            }
                                            interpretation = "UAC2 Format Type I"
                                        } else interpretation = "unsupported Format Type preserved"
                                    }
                                }
                            }
                            else -> interpretation = "class-specific descriptor (layout not interpreted)"
                        }
                    }
                }
                0x25 -> if (requireLength(3)) {
                    val index = currentInterface
                    val epIndex = currentEndpoint
                    val iface = index?.let { interfaces[it] }
                    if (iface?.isAudioStreaming == true && iface.protocol == 0x20 && epIndex != null && d.u8(2) == 1) {
                        if (requireLength(8)) {
                            val audio = UsbAudioEndpointDescriptor(d.u8(3), d.u8(4), d.u8(5), d.u16(6))
                            updateInterface { existing -> existing.copy(endpoints = existing.endpoints.mapIndexed { i, ep ->
                                if (i == epIndex) ep.copy(audio = audio) else ep
                            }) }
                            interpretation = "UAC2 class-specific endpoint"
                        }
                    } else warn("class-specific endpoint not associated with a known UAC2 endpoint")
                }
            }
            val finalKey = currentInterface?.let { interfaces[it].key }
            val finalEndpoint = currentInterface?.let { i -> currentEndpoint?.let { interfaces[i].endpoints[it].address } }
            raw += UsbRawDescriptor(offset, type, finalKey, finalEndpoint,
                rawHex(bytes, offset, length, type), interpretation)
            offset += length
        }
        if (bytes.isEmpty()) warnings += "No raw descriptors returned by Android."
        interfaces.forEach { iface ->
            if (iface.endpointCount != iface.endpoints.size) warnings +=
                "${iface.key.diagnosticName()}: advertised ${iface.endpointCount} endpoints, parsed ${iface.endpoints.size}"
            val entities = iface.audioControl?.entities.orEmpty()
            entities.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
                warnings += "${iface.key.diagnosticName()}: duplicate entity ID $it; topology ambiguous"
            }
            val ids = entities.map { it.id }.toSet()
            entities.forEach { entity ->
                (entity.signalSources + entity.clockSources).forEach { reference ->
                    if (reference == 0 || reference !in ids) warnings +=
                        "${iface.key.diagnosticName()}: entity ${entity.id} references unresolved entity $reference (missing, malformed, or unparsed)"
                }
            }
            if (iface.audioFormat != null && iface.audioStreaming != null &&
                iface.audioFormat.type != iface.audioStreaming.formatType) {
                warnings += "${iface.key.diagnosticName()}: AS General and Format Type disagree"
            }
        }
        return UsbAudioDescriptorModel(bytes.size, device, configurations.toList(), associations.toList(),
            interfaces.toList(), raw.toList(), warnings.toList())
    }

    private fun isUac2(iface: UsbInterfaceDescriptor): Boolean =
        iface.audioControl?.version?.let { it == 0x0200 } ?: (iface.protocol == 0x20)

    private fun rawHex(bytes: ByteArray, offset: Int, length: Int, type: Int?): String =
        if (type == 3) "[string descriptor omitted for privacy]" else
            (offset until offset + length).joinToString(" ") { usbHex(bytes[it].toInt() and 0xff) }

    /** Only constructed after the entire descriptor is bounded; each field is checked as well. */
    private class Descriptor(private val bytes: ByteArray, private val offset: Int, private val length: Int) {
        fun u8(index: Int): Int {
            require(index in 0 until length)
            return bytes[offset + index].toInt() and 0xff
        }
        fun u16(index: Int): Int = u8(index) or (u8(index + 1) shl 8)
        fun u32(index: Int): Long = (0..3).fold(0L) { value, i -> value or (u8(index + i).toLong() shl (8 * i)) }
    }
}
