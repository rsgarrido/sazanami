package io.github.rsgarrido.sazanami.player.usb

/** Offline, plain-text evidence. No serial strings, Android bus paths, music paths, or remote lookup. */
internal object UsbAudioDiagnosticReport {
    fun format(state: UsbAudioDiagnosticState, includeRaw: Boolean = true): String = buildString {
        appendLine("SAZANAMI USB AUDIO DIAGNOSTIC — SESSION 3")
        appendLine("Descriptors and read-only clock diagnostics. Direct USB playback is not implemented.")
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
        section("UAC2 Clock Queries")
        appendClockQueries(state.clockDiagnostics, state.generation)
        section("AudioControl Claim Experiment")
        appendClaimExperiment(state.clockDiagnostics?.claimExperiment, state.generation,
            state.clockDiagnostics?.phase == UsbClockDiagnosticPhase.QUERYING)
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
                    appendLine("    Descriptors contain no current/range/validity values; see UAC2 Clock Queries for read evidence.")
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
        section("Read-only scope / limitations")
        appendLine("Clock selectors list possible input pins; selected clock is unknown.")
        appendLine("Only the explicit experiment may temporarily claim resolved AudioControl with force=false.")
        appendLine("No AudioStreaming/HID claim, SET_INTERFACE, reset, kernel detach, streaming or USB audio data transmission.")
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

    private fun StringBuilder.appendClockQueries(diagnostics: UsbAudioClockDiagnostics?, currentGeneration: Long) {
        if (diagnostics == null) {
            appendLine("Not requested for this generation. Use Query UAC2 clock capabilities.")
            appendLine("Rate range not yet queried. No UAC2 class-control reads issued for this generation.")
            appendLine("No SET request is implemented. The separate AudioControl claim experiment requires an explicit action.")
            return
        }
        if (diagnostics.generation != currentGeneration) {
            appendLine("Stale generation; prior clock values omitted.")
            return
        }
        appendLine("Query status: ${diagnostics.phase}; generation=${diagnostics.generation}")
        appendLine("Playback candidates examined: ${diagnostics.resolution.playbackCandidateCount}")
        val target = diagnostics.resolution.target
        if (target == null) appendLine("AudioControl / Clock Source: unresolved; no target chosen.") else {
            appendLine("AudioControl interface: ${target.audioControl.number} alternate ${target.audioControl.alternate} " +
                "configuration@${target.audioControl.configurationOffset}")
            appendLine("Clock Source entity: ${target.clockEntityId}")
            appendLine("Shared playback paths: ${target.playbackPaths.joinToString { it.diagnosticName() }}")
        }
        val issued = diagnostics.results.any { result -> result.attempts.any { it.returnedByteCount != null } }
        appendLine("Claim mode: ${diagnostics.claimMode}; automatic interface claim fallback is disabled.")
        appendLine(when {
            diagnostics.claimMode == UsbClockClaimMode.AUDIOCONTROL_NON_FORCED ->
                "Operation used temporary non-forced AudioControl ownership; see the experiment for query comparison and release status."
            diagnostics.phase == UsbClockDiagnosticPhase.COMPLETE -> "Claim needed: no, these reads completed without claiming any interface."
            issued -> "Claim needed: unknown for unavailable reads; no AC/AS/HID interface was claimed."
            else -> "Claim needed: not tested; no control reads completed."
        })
        diagnostics.resolution.issues.forEach { appendLine("Resolution warning: $it") }
        val frequency = diagnostics.results.firstOrNull { it.query == UsbClockQuery.CURRENT_FREQUENCY }
        val validity = diagnostics.results.firstOrNull { it.query == UsbClockQuery.CLOCK_VALIDITY }
        val range = diagnostics.results.firstOrNull { it.query == UsbClockQuery.FREQUENCY_RANGE }
        diagnostics.claimExperiment?.let { experiment ->
            diagnostics.results.forEach { result ->
                val preferred = experiment.preferredStage(result.query)
                appendLine("Preferred ${result.query} evidence: ${when {
                    preferred == null -> "no successful current result"
                    preferred === experiment.claimed -> "Stage B, while AudioControl claimed"
                    else -> "Stage A, unclaimed"
                }}")
            }
        }
        appendLine("Current clock sampling frequency: ${frequency?.currentFrequencyHz?.let { "$it Hz" } ?: "unavailable"}")
        appendLine("Clock valid: ${validity?.clockValid?.toString() ?: "unavailable"}")
        appendLine("Sampling frequency ranges (reported MIN/MAX/RES, Hz):")
        if (range?.ranges.isNullOrEmpty()) appendLine("  unavailable / not yet returned")
        range?.ranges.orEmpty().forEachIndexed { i, tuple ->
            val semantics = when {
                tuple.minimumHz == tuple.maximumHz && tuple.resolutionHz == 0L -> "discrete singleton"
                tuple.minimumHz < tuple.maximumHz && tuple.resolutionHz == 1L -> "integer-Hz interval"
                tuple.minimumHz < tuple.maximumHz && tuple.resolutionHz > 1L -> "stepped interval"
                else -> "semantics indeterminate"
            }
            appendLine("  #${i + 1} min=${tuple.minimumHz} max=${tuple.maximumHz} res=${tuple.resolutionHz} ($semantics)")
        }
        appendLine("Initial milestone rate assessment:")
        UsbAudioClockControl.milestoneRatesHz.forEach { rate ->
            val support = when (UsbAudioClockControl.assessRate(rate, range)) {
                UsbClockRateSupport.REPRESENTED -> "supported by returned range"
                UsbClockRateSupport.NOT_REPRESENTED -> "not represented by returned range"
                UsbClockRateSupport.INDETERMINATE -> "indeterminate (query/semantics incomplete)"
            }
            appendLine("  $rate Hz: $support")
        }
        appendLine("Query results / raw control-response evidence:")
        diagnostics.results.forEach { result ->
            val label = when (result.query) {
                UsbClockQuery.CURRENT_FREQUENCY -> "GET_CUR sampling frequency"
                UsbClockQuery.FREQUENCY_RANGE -> "GET_RANGE sampling frequency"
                UsbClockQuery.CLOCK_VALIDITY -> "GET_CUR clock validity"
            }
            appendLine("  $label: ${result.status}")
            result.detail?.let { appendLine("    $it") }
            result.declaredSubrangeCount?.let { appendLine("    Declared subranges: $it") }
            result.attempts.forEach { attempt ->
                val request = attempt.request
                appendLine("    ${if (request.rangeHeaderOnly) "RANGE header" else "Read"}: bmRequestType=0x${usbHex(request.requestType)} " +
                    "bRequest=0x${usbHex(request.request)} wValue=0x${usbHex(request.value, 4)} " +
                    "wIndex=0x${usbHex(request.index, 4)} wLength=${request.length} returned=${attempt.returnedByteCount ?: "not issued/no byte count"} ${attempt.status}")
                appendLine("    Raw: ${attempt.rawBytes.joinToString(" ") { usbHex(it) }.ifEmpty { "none" }}")
                attempt.detail?.let { appendLine("    $it") }
            }
        }
        appendLine("Values describe the Clock Source at query time, not track rate, PCM format support, or Bit-perfect playback.")
        appendLine("Only endpoint-zero device-to-host Clock Source reads are allowed; no writes or streaming.")
    }

    private fun StringBuilder.appendClaimExperiment(experiment: UsbAudioClaimExperimentResult?, generation: Long, querying: Boolean) {
        if (experiment == null) {
            appendLine("Not requested. Use Test AudioControl claim + clock reads.")
            appendLine("No forced claim was attempted.")
            return
        }
        if (experiment.generation != generation) {
            appendLine("Stale generation; prior experiment evidence omitted.")
            return
        }
        appendLine("Experiment: ${if (querying) "QUERYING" else "FINISHED"}; generation=${experiment.generation}")
        val target = experiment.resolution.target
        appendLine("Resolved AC interface: ${target?.audioControl?.diagnosticName() ?: "unresolved"}")
        appendLine("Resolved Clock Source: ${target?.clockEntityId ?: "unresolved"}")
        appendLine("Baseline policy: fresh unclaimed stage on the same temporary connection and generation before claim.")
        appendLine("force: ${experiment.force}")
        appendLine("Claim status: ${experiment.claimStatus}")
        appendLine("claimInterface result: ${when (experiment.claimReturned) { true -> "success (true)"; false -> "failure (false), cause unknown"; null -> "not observed / not attempted" }}")
        experiment.claimRequest?.audioControl?.let {
            appendLine("Android claim target: interface ${it.number} alternate ${it.alternate} class=${it.interfaceClass} subclass=${it.subclass} protocol=0x${usbHex(it.protocol)}")
        }
        appendLine("release attempted: ${if (experiment.releaseStatus == UsbAudioCleanupStatus.NOT_ATTEMPTED) "no" else "yes"}")
        appendLine("release result: ${experiment.releaseStatus}")
        appendLine("connection close result: ${experiment.closeStatus}")
        experiment.detail?.let { appendLine("Note: $it") }
        experiment.resolution.issues.forEach { appendLine("Resolution warning: $it") }
        appendLine("No forced claim was attempted. No AudioStreaming or HID interface was claimed.")
        if (experiment.accessFailure != null) appendLine("Access check: ${experiment.accessFailure}; stage values are historical evidence, not authoritative current values.")
        appendLine()
        appendLine("Stage A — Unclaimed:")
        experiment.unclaimed?.let { appendClockQueries(it, generation) } ?: appendLine("No baseline query result retained.")
        appendLine()
        appendLine("Stage B — While AudioControl claimed:")
        experiment.claimed?.let { appendClockQueries(it, generation) } ?: appendLine("No claimed query set issued/retained; see claim/access status.")
        appendLine()
        appendLine("Comparison: ${experiment.comparison()}")
        appendLine(when (experiment.comparison()) {
            UsbAudioClaimComparison.NOT_ATTEMPTED -> "Claim comparison not completed."
            UsbAudioClaimComparison.BASELINE_SUCCEEDED -> "Unclaimed reads succeeded already; claiming AC was not necessary for those baseline reads."
            UsbAudioClaimComparison.CLAIM_UNAVAILABLE -> "Read capability remains unresolved; non-forced AC ownership was unavailable. The cause of claim failure is unknown."
            UsbAudioClaimComparison.ACCESS_CHANGED -> "Access/generation changed; no current accessibility conclusion can be drawn."
            UsbAudioClaimComparison.CLAIM_IMPROVED_READS -> "At least one request rejected/failed unclaimed succeeded while AC was claimed. Ownership is associated with changed query accessibility in this experiment; this does not prove the underlying cause."
            UsbAudioClaimComparison.CLAIM_DID_NOT_RESOLVE -> "The previously rejected/failed requests also failed while AC was claimed; claiming AC alone did not resolve those failures."
            UsbAudioClaimComparison.INDETERMINATE -> "Evidence is incomplete or mixed; inspect the per-stage request results."
        })
        appendLine("This is temporary diagnostic ownership and read-only clock evidence. Direct USB playback is not implemented.")
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
