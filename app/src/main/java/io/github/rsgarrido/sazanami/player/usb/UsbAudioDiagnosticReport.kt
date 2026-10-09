package io.github.rsgarrido.sazanami.player.usb

/** Offline, plain-text evidence. No serial strings, Android bus paths, music paths, or remote lookup. */
internal object UsbAudioDiagnosticReport {
    fun format(state: UsbAudioDiagnosticState, includeRaw: Boolean = true): String = buildString {
        appendLine("SAZANAMI USB AUDIO DIAGNOSTIC — SESSION 1")
        appendLine("Descriptor inspection only. Direct USB playback is not implemented.")
        appendLine("Service: ${if (state.serviceAvailable) "available" else "unavailable"}")
        appendLine("Status: ${statusText(state.status)}")
        appendLine("Connection generation: ${state.generation}")
        state.message?.let { appendLine("Note: $it") }
        section("Device")
        val selected = state.selectedDevice
        if (selected == null) {
            appendLine(if (state.devices.isEmpty()) "No USB device detected." else "No device selected.")
        } else {
            appendLine("Product: ${safeText(selected.product)}")
            appendLine("Manufacturer: ${safeText(selected.manufacturer)}")
            appendLine("VID: 0x${usbHex(selected.identity.vendorId, 4)}")
            appendLine("PID: 0x${usbHex(selected.identity.productId, 4)}")
            appendLine("Android device ID: ${selected.identity.deviceId}")
            appendLine("Device class: 0x${usbHex(selected.deviceClass)} (${className(selected.deviceClass)})")
            appendLine("Permission: ${if (selected.hasPermission) "granted (current attachment only)" else "not granted"}")
            appendLine("Serial: not queried or included")
        }
        section("Android enumeration")
        if (state.devices.isEmpty()) appendLine("None")
        state.devices.forEach { device ->
            appendLine("${safeText(device.product)}: VID=0x${usbHex(device.identity.vendorId, 4)} " +
                "PID=0x${usbHex(device.identity.productId, 4)} deviceId=${device.identity.deviceId} " +
                "audio interfaces=${device.hasAudioInterface}")
            device.interfaces.forEach { iface ->
                appendLine("  Interface ${iface.number} alternate ${iface.alternate}: " +
                    "${className(iface.interfaceClass, iface.subclass)} " +
                    "class=0x${usbHex(iface.interfaceClass)} subclass=0x${usbHex(iface.subclass)} " +
                    "protocol=0x${usbHex(iface.protocol)} endpoints=${iface.endpoints.size}")
                iface.endpoints.forEach { ep ->
                    appendLine("    EP 0x${usbHex(ep.address)} ${if (ep.address and 0x80 != 0) "IN" else "OUT"} " +
                        "${transferName(ep.attributes and 3)} attributes=0x${usbHex(ep.attributes)} " +
                        "Android maxPacketSize=${ep.maxPacketSize} interval=${ep.interval}")
                }
            }
        }
        val model = state.descriptors
        if (model == null) {
            section("Raw descriptors")
            appendLine("Not read for the current generation. Select a device and grant access.")
            return@buildString
        }
        section("Standard descriptors")
        model.device?.let { d ->
            appendLine("Device: bcdUSB=0x${usbHex(d.usbVersion, 4)} bcdDevice=0x${usbHex(d.deviceVersion, 4)} " +
                "class/subclass/protocol=0x${usbHex(d.deviceClass)}/0x${usbHex(d.subclass)}/0x${usbHex(d.protocol)} " +
                "VID/PID=0x${usbHex(d.vendorId, 4)}/0x${usbHex(d.productId, 4)} configurations=${d.configurationCount}")
        } ?: appendLine("Device descriptor not present in Android raw data.")
        model.configurations.forEach { c ->
            appendLine("Configuration ${c.value} @${c.offset}: wTotalLength=${c.totalLength} " +
                "interfaces=${c.interfaceCount} attributes=0x${usbHex(c.attributes)} bMaxPower(raw)=${c.maxPowerRaw}")
        }
        appendLine("Active configuration/device speed: not queried; all supplied descriptor sets are shown.")
        model.associations.forEach { iad ->
            appendLine("IAD configuration@${iad.configurationOffset ?: "unknown"}: " +
                "first=${iad.firstInterface} count=${iad.interfaceCount} " +
                "class/subclass/protocol=0x${usbHex(iad.functionClass)}/0x${usbHex(iad.subclass)}/0x${usbHex(iad.protocol)}")
        }
        section("Interfaces / alternates / endpoints")
        model.interfaces.forEach { iface ->
            appendLine("${iface.key.diagnosticName()}: ${className(iface.interfaceClass, iface.subclass)} " +
                "class=0x${usbHex(iface.interfaceClass)} subclass=0x${usbHex(iface.subclass)} " +
                "protocol=0x${usbHex(iface.protocol)} (${uacProtocol(iface)}) endpoints=${iface.endpointCount}")
            iface.endpoints.forEach { ep ->
                appendLine("  EP 0x${usbHex(ep.address)} ${if (ep.isIn) "IN" else "OUT"} ${transferName(ep.transferType)} " +
                    "attributes=0x${usbHex(ep.attributes)} " +
                    (if (ep.transferType == 1) "sync=${syncName(ep.synchronizationType)} usage=${usageName(ep.usageType)}" else ""))
                appendLine("    wMaxPacketSize=0x${usbHex(ep.maxPacketSizeRaw, 4)} payload=${ep.packetPayloadBytes} " +
                    "encoded additional transactions=${ep.additionalTransactions} bInterval=${ep.interval}")
                appendLine("    Interval units/bus capacity depend on speed; not inferred.")
                ep.synchronizationAddress?.let { appendLine("    bSynchAddress=0x${usbHex(it)} bRefresh=${ep.refresh}") }
                ep.audio?.let { audio -> appendLine("    UAC2 endpoint attributes=0x${usbHex(audio.attributes)} " +
                    "controls=0x${usbHex(audio.controls)} lockDelayUnits=${audio.lockDelayUnits} lockDelay=${audio.lockDelay}") }
            }
        }
        section("AudioControl / entities")
        model.interfaces.filter { it.isAudioControl }.forEach { iface ->
            val ac = iface.audioControl
            appendLine("${iface.key.diagnosticName()}: bcdADC=${ac?.version?.let { "0x${usbHex(it, 4)}" } ?: "unknown"} " +
                "category=${ac?.category ?: "unknown"} class totalLength=${ac?.totalLength ?: "unknown"} " +
                "controls=${ac?.controls?.let { "0x${usbHex(it)}" } ?: "unknown"}")
            ac?.entities.orEmpty().forEach { e ->
                appendLine("  ${e.kind} #${e.id}: signalSources=${e.signalSources} clockSources=${e.clockSources}")
                e.terminalType?.let { appendLine("    terminalType=0x${usbHex(it, 4)}") }
                e.associatedTerminal?.let { appendLine("    associatedTerminal=$it") }
                e.channelCount?.let { appendLine("    channels=$it channelConfig=0x${hex32(e.channelConfiguration ?: 0)}") }
                e.attributes?.let { appendLine("    attributes=0x${usbHex(it)}") }
                e.controls?.let { appendLine("    controls=0x${hex32(it)}") }
                if (e.kind == UsbAudioEntityKind.CLOCK_SOURCE) {
                    appendLine("    Sampling-frequency / clock-valid control bit pairs: " +
                        "${e.controls?.and(3L) ?: "unknown"} / ${e.controls?.shr(2)?.and(3L) ?: "unknown"}")
                    appendLine("    Current rate, rate range and clock-valid value: not queried")
                }
                if (e.channelControls.isNotEmpty()) appendLine("    master/channel controls=${e.channelControls.joinToString { "0x${hex32(it)}" }}")
            }
        }
        section("AudioStreaming formats")
        model.interfaces.filter { it.isAudioStreaming }.forEach { iface ->
            appendLine(iface.key.diagnosticName())
            iface.audioStreaming?.let { asGeneral ->
                appendLine("  terminalLink=${asGeneral.terminalLink} formatType=${asGeneral.formatType} " +
                    "formats=0x${hex32(asGeneral.formats)} controls=0x${usbHex(asGeneral.controls)} " +
                    "channels=${asGeneral.channelCount} channelConfig=0x${hex32(asGeneral.channelConfiguration)}")
            } ?: appendLine("  AS General unknown/unparsed")
            iface.audioFormat?.let { format ->
                appendLine("  Type ${format.type}: subslot=${format.subslotBytes} bytes; valid bits=${format.validBits}")
            } ?: appendLine("  Type I subslot/valid bits unknown/unparsed")
        }
        val candidates = UsbAudioTopology.candidates(model)
        listOf(UsbAudioPathDirection.PLAYBACK, UsbAudioPathDirection.CAPTURE).forEach { direction ->
            section(if (direction == UsbAudioPathDirection.PLAYBACK) "Playback candidates" else "Capture candidates")
            val paths = candidates.filter { it.direction == direction }
            if (paths.isEmpty()) appendLine("None established from isochronous data endpoint descriptors.")
            paths.forEachIndexed { index, path ->
                appendLine("Candidate ${index + 1}: ${path.interfaceKey.diagnosticName()} EP=0x${usbHex(path.endpointAddress)} terminalLink=${path.terminalLink ?: "unknown"}")
                appendLine("  ${path.associationEvidence}")
                appendLine("  ${path.feedbackEvidence}")
                path.signalPaths.forEach { appendLine("  Signal (backward source links): $it") }
                path.clockPaths.forEach { appendLine("  Clock possibilities: $it") }
                path.warnings.forEach { appendLine("  Warning: $it") }
            }
        }
        section("Sample rates / read-only queries")
        appendLine("Rate range not yet queried. No UAC2 GET_CUR/GET_RANGE or SET_CUR requests were issued.")
        appendLine("Clock selectors list possible input pins; selected clock is unknown.")
        appendLine("No interface claim, SET_INTERFACE, reset, kernel detach, streaming or USB audio data transmission.")
        section("Parser warnings / limitations")
        if (model.warnings.isEmpty()) appendLine("No structural parser warnings.")
        model.warnings.forEach { appendLine(it) }
        appendLine("UAC1/UAC3 and unsupported entity/format layouts are raw evidence only. Implicit feedback is unresolved.")
        appendLine("Candidates are not driver selection or proof of supported playback. No Bit-perfect state is asserted.")
        section("Raw descriptor evidence (${model.byteCount} bytes supplied)")
        if (includeRaw) model.rawDescriptors.forEach { d ->
            appendLine("@${d.offset} type=${d.type?.let { "0x${usbHex(it)}" } ?: "unknown"} " +
                "scope=${d.interfaceKey?.diagnosticName() ?: "device/configuration"}" +
                (d.endpointAddress?.let { " EP=0x${usbHex(it)}" } ?: "") + " — ${d.interpretation}")
            appendLine(d.hex)
        } else appendLine("Use Copy USB audio report for complete descriptor-by-descriptor hex, including unknown/malformed bytes.")
    }

    fun statusText(status: UsbAudioDiagnosticStatus): String = when (status) {
        UsbAudioDiagnosticStatus.SERVICE_UNAVAILABLE -> "Playback service unavailable"
        UsbAudioDiagnosticStatus.NO_DEVICE -> "No USB device"
        UsbAudioDiagnosticStatus.SELECT_DEVICE -> "Select a USB device"
        UsbAudioDiagnosticStatus.PERMISSION_REQUIRED -> "USB access required"
        UsbAudioDiagnosticStatus.REQUESTING_PERMISSION -> "Waiting for USB permission"
        UsbAudioDiagnosticStatus.PERMISSION_DENIED -> "USB permission denied"
        UsbAudioDiagnosticStatus.OPENING -> "Reading descriptors"
        UsbAudioDiagnosticStatus.DESCRIPTORS_READ -> "Descriptors read (diagnostics only)"
        UsbAudioDiagnosticStatus.ERROR -> "Inspection unavailable"
    }

    private fun StringBuilder.section(title: String) { appendLine(); appendLine(title); appendLine("-".repeat(title.length)) }
    private fun hex32(value: Long): String = value.toString(16).uppercase(java.util.Locale.ROOT).padStart(8, '0')
    private fun safeText(value: String?): String = value?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")?.take(160)?.takeIf { it.isNotBlank() } ?: "unknown"
    private fun className(value: Int, subclass: Int? = null): String = when (value) {
        0 -> "per-interface/composite"
        1 -> when (subclass) { 1 -> "AudioControl"; 2 -> "AudioStreaming"; 3 -> "MIDIStreaming"; else -> "Audio" }
        3 -> "HID"
        else -> "other/non-audio"
    }
    private fun uacProtocol(iface: UsbInterfaceDescriptor): String = if (iface.interfaceClass != 1) "non-audio" else when (iface.protocol) {
        0 -> "UAC1/legacy protocol; not interpreted"; 0x20 -> "UAC2 protocol"; 0x30 -> "UAC3 protocol; not interpreted"; else -> "unknown audio protocol"
    }
    private fun transferName(value: Int): String = listOf("control", "isochronous", "bulk", "interrupt")[value and 3]
    private fun syncName(value: Int): String = listOf("none", "asynchronous", "adaptive", "synchronous")[value and 3]
    private fun usageName(value: Int): String = listOf("data", "explicit feedback", "implicit-feedback data", "reserved")[value and 3]
}
