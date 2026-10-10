package io.github.rsgarrido.sazanami.player.usb

/** Offline, plain-text evidence. No serial strings, Android bus paths, music paths, or remote lookup. */
internal object UsbAudioDiagnosticReport {
    fun format(state: UsbAudioDiagnosticState, includeRaw: Boolean = true): String = buildString {
        appendLine("SAZANAMI USB AUDIO DIAGNOSTIC — SESSION 8")
        appendLine("Descriptors, ownership, clock, configuration, silence and deterministic reference PCM diagnostics. Direct USB song playback is not implemented.")
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
        appendLine("Sessions 2/3 query evidence; Session 5 native reads have their own section below.")
        appendClockQueries(state.clockDiagnostics, state.generation)
        section("AudioControl Claim Experiment")
        appendClaimExperiment(state.clockDiagnostics?.claimExperiment, state.generation,
            state.clockDiagnostics?.phase == UsbClockDiagnosticPhase.QUERYING)
        section("USBFS Ownership Diagnostics")
        appendUsbFs(state.usbFsDiagnostics, state.generation)
        section("Android AudioDeviceInfo")
        appendAndroidAudio(state.usbFsDiagnostics, state.generation)
        section("Forced AudioControl Ownership Experiment — Session 5")
        appendForcedExperiment(state.forcedExperiment, state.generation)
        section("USB Configuration Experiment — Session 6")
        appendConfigurationExperiment(state.configurationExperiment, state.generation)
        section("Silent Direct USB Transport — Session 7")
        appendSilentTransport(state.silentExperiment, state.generation)
        section("Deterministic Direct USB PCM — Session 8")
        appendSilentTransport(state.referenceExperiment, state.generation, UsbPcmDiagnosticMode.REFERENCE)
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
        appendLine("Active configuration not queried; all supplied descriptor sets are shown. Bus speed evidence is in USBFS Ownership Diagnostics.")
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
                appendLine("    Interval units/bus capacity are not inferred here; see the separate usbfs speed evidence.")
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
        section("Diagnostic scope / limitations")
        appendLine("Clock selectors list possible input pins; selected clock is unknown.")
        appendLine("Sessions 3/4 use non-forced AC ownership. Explicit Session 5 force-owns only resolved AC through Android, then releases it.")
        appendLine("Session 6 may force-own resolved AC + AS, set only the Clock Source sampling frequency and select only the resolved PCM16 / idle alternates, then restore.")
        appendLine("Session 7 adds bounded all-zero PCM OUT and explicit feedback IN; Session 8 reuses it for a fixed low-level L/R reference on validated endpoints. No HID claim, reset or manual kernel detach/rebind.")
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

    private fun StringBuilder.appendUsbFs(diagnostics: UsbFsOwnershipDiagnostics?, generation: Long) {
        appendLine("Session 4 scope only (separate from Session 5):")
        appendLine("Forced detach attempted: NO")
        appendLine("Native fd is borrowed synchronously; Kotlin retains connection ownership.")
        if (diagnostics == null) { appendLine("Not requested. Use Inspect kernel USB ownership."); return }
        if (diagnostics.generation != generation) { appendLine("Stale generation; prior ownership/speed evidence omitted."); return }
        appendLine("Status: ${diagnostics.status}; generation=${diagnostics.generation}")
        diagnostics.detail?.let { appendLine("Note: $it") }
        if (diagnostics.status in setOf(UsbFsProbeStatus.DEVICE_DISCONNECTED, UsbFsProbeStatus.STALE_GENERATION,
                UsbFsProbeStatus.PERMISSION_LOST, UsbFsProbeStatus.TRANSPORT_ERROR)) {
            appendLine("Ownership evidence is non-authoritative and omitted.")
            return
        }
        diagnostics.plan.warnings.forEach { appendLine("Plan warning: $it") }
        appendLine("Bus speed:")
        diagnostics.speed?.let {
            appendIoctl(it.result)
            appendLine("  raw speed: ${it.rawSpeed ?: "unavailable"}; mapped: ${it.mapped}")
        } ?: appendLine("  unavailable / not yet queried")
        appendLine("Interface driver bindings (descriptor-derived roles; alternates deduplicated):")
        if (diagnostics.driverBindings.isEmpty()) appendLine("  no native binding results returned")
        diagnostics.driverBindings.forEach { binding ->
            appendLine("  Interface ${binding.iface.number} (${binding.iface.roles.joinToString()}); descriptor alternates=${binding.iface.alternates}")
            appendLine("    driver: ${binding.driver?.let { safeText(it, 256) } ?: "not returned"}")
            appendIoctl(binding.result)
            appendLine("    ${UsbFsEvidence.driverExplanation(binding)}")
        }
        appendLine("Raw AudioControl claim probe:")
        appendLine("  Resolved AC interface: ${diagnostics.plan.audioControl?.diagnosticName() ?: "unresolved / not eligible"}")
        appendLine("  Non-forced USBDEVFS_CLAIMINTERFACE only; no disconnect operation or retry.")
        diagnostics.claim?.let { appendIoctl(it) } ?: appendLine("  claim: not attempted")
        appendLine("  ${UsbFsEvidence.claimExplanation(diagnostics)}")
        appendLine("  release attempted: ${if (diagnostics.release != null) "yes" else "no"}")
        diagnostics.release?.let { appendIoctl(it) }
        appendLine("No control requests, clock reads, endpoint I/O or audio data are issued by this native probe.")
        appendLine("These are kernel observations at query time; speed is not inferred from descriptors or marketing.")
    }

    private fun StringBuilder.appendForcedExperiment(result: UsbAudioForcedExperimentResult?, generation: Long) {
        appendLine("AudioStreaming force claim attempted: NO")
        appendLine("HID claim attempted: NO")
        appendLine("SET_CUR issued: NO")
        appendLine("SET_INTERFACE issued: NO")
        appendLine("Audio data transmitted: NO")
        appendLine("No reset, configuration change, manual detach/rebind or Feature Unit queries. No Bit-perfect active state.")
        appendLine("Functional audio recovery: ${UsbFunctionalAudioRecovery.NOT_VERIFIED}; no playback stream is exercised by Session 5.")
        appendLine("Kernel driver rebinding and AudioDeviceInfo continuity do not prove that audio routing or DAC playback is functional.")
        appendLine("HID availability is independent evidence and must not be treated as proof that USB audio playback is restored.")
        appendLine("Observed manual hardware caveat (known test evidence; not automatically detected by the app):")
        appendLine(UsbAudioForcedExperiment.MANUAL_HARDWARE_CAVEAT)
        appendLine(UsbAudioForcedExperiment.MANUAL_DEVICE_STATE_NOTE)
        if (result == null) { appendLine("Not requested. Stop music, inspect Session 4, then use Force-own AudioControl + read clock."); return }
        if (result.generation != generation) { appendLine("Stale generation; Session 5 device evidence omitted."); return }
        appendLine("Experiment: ${result.phase}; generation=${result.generation}")
        appendLine("Resolved AudioControl interface: ${result.plan.audioControl?.diagnosticName() ?: "unresolved"}")
        appendLine("Resolved Clock Source: ${result.plan.resolution.target?.clockEntityId ?: "unresolved"}")
        appendLine("force: true (Session 5 only); claimInterface result: ${result.claimReturned ?: "not observed / not attempted"}")
        result.claimException?.let { appendLine("Claim exception type: ${safeText(it)}; private message omitted") }
        appendLine("Release attempted: ${result.releaseStatus != UsbAudioCleanupStatus.NOT_ATTEMPTED}")
        appendLine("Android releaseInterface result: ${result.releaseReturned ?: "not observed"}; status=${result.releaseStatus}")
        result.releaseException?.let { appendLine("Release exception type: ${safeText(it)}; private message omitted") }
        appendLine("Connection close: ${result.closeStatus}; fd borrowed by JNI, never closed/retained there")
        appendLine("Kernel/framework recovery: ${result.recoveryStatus}; bounded window expired=${result.recoveryWindowExpired}")
        appendLine("Kernel bindings: ${result.kernelRecovery}")
        appendLine("Android USB audio enumeration: ${result.frameworkRecovery}")
        appendLine("Recovery polls: at most ${UsbAudioForcedExperiment.MAX_RECOVERY_POLLS} at ${UsbAudioForcedExperiment.RECOVERY_INTERVAL_MS} ms; window <= ${UsbAudioForcedExperiment.RECOVERY_WINDOW_MS} ms, apart from synchronous observation latency.")
        result.detail?.let { appendLine("Note: $it") }
        result.plan.warnings.forEach { appendLine("Resolution warning: $it") }
        if (result.accessFailure != null) {
            appendLine("Access: ${result.accessFailure}; device/rate evidence omitted. Release/close facts retained.")
            appendLine("44.1 kHz / 44100 Hz — CD milestone: INDETERMINATE")
            return
        }
        appendLine("Before forced ownership (fresh snapshot; USB permission revalidated):")
        appendForcedSnapshot(result.before, generation)
        appendLine("While AudioControl owned:")
        appendForcedSnapshot(result.whileOwned, generation)
        appendLine("Native UAC2 Clock Reads — USBDEVFS_CONTROL, RANGE then frequency CUR then validity CUR:")
        if (result.clocks == null) appendLine("No native clock query set retained; see claim/access/observation outcome.")
        else appendClockQueries(result.clocks, generation)
        val range = result.clocks?.results?.firstOrNull { it.query == UsbClockQuery.FREQUENCY_RANGE }
        appendLine("Session 5 initial milestone rates — solely native Clock Source GET_RANGE:")
        UsbAudioClockControl.milestoneRatesHz.forEach { rate ->
            val support = when (UsbAudioClockControl.assessRate(rate, range)) {
                UsbClockRateSupport.REPRESENTED -> "SUPPORTED"
                UsbClockRateSupport.NOT_REPRESENTED -> "NOT REPRESENTED"
                UsbClockRateSupport.INDETERMINATE -> "INDETERMINATE"
            }
            appendLine(if (rate == 44_100L) "44.1 kHz / 44100 Hz — CD milestone: $support" else "$rate Hz: $support")
        }
        appendLine("After release (immediate and bounded recovery observations, before connection close):")
        if (result.recoveryObservations.isEmpty()) appendLine("No post-release observations retained.")
        result.recoveryObservations.forEachIndexed { index, observation ->
            appendLine("Observation ${index + 1}: ${observation.elapsedMs} ms after release observation began${if (index == 0) " (immediate)" else ""}")
            appendForcedSnapshot(observation.snapshot, generation)
        }
        appendLine("Final Android AudioDeviceInfo after connection close (secondary):")
        result.afterAndroidAudio?.let { audio ->
            appendAndroidAudio(UsbFsOwnershipDiagnostics(generation, result.plan, UsbFsProbeStatus.RECORDED,
                androidAudio = audio), generation)
        } ?: appendLine("Not captured / non-authoritative.")
        appendLine("Kernel baseline bindings restored: ${UsbAudioForcedExperiment.kernelRestored(result)} (AC plus all observed baseline-bound interfaces)")
        appendLine("Framework candidate continuity: ${UsbAudioForcedExperiment.frameworkCandidateReturned(result)}")
        appendLine("ENUMERATION_RECOVERED requires release=true, baseline drivers observed again and one before/after USB output candidate with the same numeric ALSA address/type. It confirms only kernel/framework enumeration evidence.")
        appendLine("It does not verify audio routing, AudioTrack routing, DAC/IEM playback, device-side state, Bit-perfect usability, or whether a physical reconnect is required.")
        appendLine("Framework pending includes missing candidates, unavailable addresses, multiple candidates or incomplete queries; product names alone cannot establish candidate continuity.")
        if (result.claimReturned == true) appendLine("Post-experiment warning: audio routing has not been functionally verified. If audio routes to the phone speaker or the DAC behaves abnormally, physically disconnect and reconnect the DAC before continuing.")
        val beforeAudio = result.before?.androidAudio?.outputs?.singleOrNull()
        val afterAudio = (result.afterAndroidAudio ?: result.after?.androidAudio)?.outputs?.singleOrNull()
        if (beforeAudio != null && afterAudio != null) {
            appendLine("Secondary candidate ID change: ${beforeAudio.id} -> ${afterAudio.id}")
            val capabilitiesChanged = beforeAudio.sampleRates != afterAudio.sampleRates || beforeAudio.encodings != afterAudio.encodings ||
                beforeAudio.channelCounts != afterAudio.channelCounts || beforeAudio.channelMasks != afterAudio.channelMasks ||
                beforeAudio.channelIndexMasks != afterAudio.channelIndexMasks
            appendLine("Secondary capability arrays changed: $capabilitiesChanged")
        }
        appendLine("Snapshots may miss transient remove/add events. No AudioDeviceCallback registered; no automatic repair on incomplete recovery.")
    }

    private fun StringBuilder.appendForcedSnapshot(snapshot: UsbFsOwnershipDiagnostics?, generation: Long) {
        if (snapshot == null) { appendLine("Not captured."); return }
        appendLine("Snapshot status: ${snapshot.status}")
        if (snapshot.generation != generation || snapshot.status != UsbFsProbeStatus.RECORDED) {
            appendLine("Snapshot device values unavailable/non-authoritative."); return
        }
        snapshot.speed?.let { appendIoctl(it.result); appendLine("Bus speed: ${it.mapped}; raw=${it.rawSpeed}") }
        snapshot.driverBindings.forEach { binding ->
            appendLine("Interface ${binding.iface.number} (${binding.iface.roles.joinToString()}): driver=${safeText(binding.driver, 256)}")
            appendIoctl(binding.result)
            appendLine(UsbFsEvidence.driverExplanation(binding))
        }
        appendLine("Android USB audio candidates: ${snapshot.androidAudio?.outputs?.size ?: "unavailable"}")
        appendAndroidAudio(snapshot, generation)
    }

    private fun StringBuilder.appendConfigurationExperiment(result: UsbAudioConfigurationResult?, generation: Long, endpointWork: Boolean = false) {
        appendLine(if (endpointWork) "Configuration evidence from the reused Session 6 transaction; endpoint transport is separate." else "Development configuration experiment only; no stream is exercised.")
        if (!endpointWork) {
            appendLine("Data endpoint used: NO")
            appendLine("Feedback endpoint used: NO")
            appendLine("Audio transmitted: NO")
        } else appendLine("Endpoint activity is recorded by endpoint transport submissions/completions; configuration evidence alone does not establish transport.")
        appendLine("HID claimed: NO; Feature Unit writes: NO; reset/manual reconnect/rebind: NO")
        appendLine("Bit-perfect active: NO")
        appendLine("Functional USB audio recovery: NOT_VERIFIED")
        appendLine("After this configuration experiment, a physical DAC disconnect/reconnect may be required before normal Android USB audio playback works again.")
        appendLine("Known manual test evidence only: ${UsbAudioForcedExperiment.MANUAL_HARDWARE_CAVEAT}")
        if (result == null) { appendLine("Not requested. Stop playback before Configure USB 16/44.1 (no audio)."); return }
        if (result.generation != generation) { appendLine("Stale generation; previous Session 6 transaction evidence omitted."); return }
        appendLine("Generation: ${result.generation}; phase=${result.phase}")
        appendLine("Target configuration: ${result.targetStatus}")
        appendLine("Configuration restoration: ${result.restorationStatus} (alt/clock only; releases/close are separate)")
        result.failedStep?.let { appendLine("Stopped at: $it") }
        result.detail?.let { appendLine("Note: ${safeText(it, 400)}") }
        result.resolution.issues.forEach { appendLine("Resolution issue: ${safeText(it, 400)}") }
        val plan = result.resolution.plan ?: return
        appendLine("Target: PCM stereo; sample rate=${plan.sampleRateHz} Hz; channels=${plan.channels}; subslot=${plan.subslotBytes} bytes; valid bits=${plan.validBits}")
        appendLine("Resolved AC: ${plan.clock.audioControl.diagnosticName()}; Clock Source=${plan.clock.clockEntityId}")
        appendLine("Resolved AS: ${plan.streaming.diagnosticName()}; idle=${plan.idle.diagnosticName()}")
        appendLine("Descriptor-declared AS alternates: ${plan.declaredAlternates.sorted().joinToString()}")
        appendLine("Successful host SETINTERFACE is primary alternate evidence. GET_INTERFACE is advisory; a different declared alternate contradicts selection and blocks clock mutation/target verification.")
        appendLine("Descriptor data endpoint: 0x${usbHex(plan.dataEndpoint)} (${if (endpointWork) "endpoint traffic reported separately" else "never opened/used"})")
        appendLine("Forward configuration and restoration are independent; verified configuration is not audio or transport success.")
        if (result.accessFailure != null) appendLine("Authority: ${result.accessFailure}. The following bounded transaction records are historical only; no current device/configuration conclusion is asserted.")
        appendLine("Before ownership:")
        appendForcedSnapshot(result.before, generation)
        appendLine("AC force=true claim:")
        appendConfigurationOwnership(result.acClaim)
        appendLine("After AC claim, before AS claim:")
        appendForcedSnapshot(result.afterAcClaim, generation)
        appendLine("Fresh Clock Source pre-flight (RANGE, frequency CUR, validity CUR):")
        appendClockQueries(result.preflight, generation)
        appendLine("Original clock captured: ${result.originalClockRateHz ?: "unavailable"} Hz")
        val range = result.preflight?.results?.firstOrNull { it.query == UsbClockQuery.FREQUENCY_RANGE }
        appendLine("Fresh 44100 support: ${UsbAudioClockControl.assessRate(plan.sampleRateHz, range)}")
        appendLine("AS force=true claim:")
        appendConfigurationOwnership(result.asClaim)
        appendLine("Bindings while both audio interfaces owned (all interfaces; no assumed driver name):")
        appendForcedSnapshot(result.whileOwned, generation)
        appendLine("Initial GET_INTERFACE advisory probe (reported value is not assumed to be actual state):")
        appendConfigurationControl(result.originalAlternate)
        appendLine("Explicit idle establishment; resolved idle=${plan.idle.alternate}; attempted=${result.idleSelectionAttempted}:")
        appendAlternateMutation(result.idleMutation)
        appendLine("Target Clock Sampling Frequency SET_CUR; attempted=${result.clockWriteAttempted}:")
        appendConfigurationControl(result.targetClockSet)
        appendLine("Target frequency GET_CUR verification:")
        appendConfigurationClock(result.targetClockReadback, result)
        appendLine("Target Clock Validity observations (<=${UsbAudioConfigurationExperiment.VALIDITY_WINDOW_MS} ms window, <=${UsbAudioConfigurationExperiment.MAX_VALIDITY_RETRIES} retries at ${UsbAudioConfigurationExperiment.VALIDITY_INTERVAL_MS} ms; synchronous ioctl latency may extend a final observation):")
        result.targetValidity.forEach { appendLine("  elapsed=${it.elapsedMs} ms"); appendConfigurationClock(it.result, result) }
        appendLine("Target streaming alternate SETINTERFACE; attempted=${result.alternateSelectionAttempted}:")
        appendAlternateMutation(result.targetMutation)
        appendLine("Clock readbacks while target alternate active:")
        appendConfigurationClock(result.configuredClockReadback, result)
        appendConfigurationClock(result.configuredClockValidity, result)
        appendLine("Restoration — resolved idle SETINTERFACE and advisory GET_INTERFACE (successful setter permits clock restore unless a declared alternate contradicts idle):")
        appendAlternateMutation(result.restorationMutation)
        appendLine("Restoration — original clock target=${result.originalClockRateHz ?: "unavailable"} Hz:")
        appendConfigurationControl(result.originalClockRestore)
        appendConfigurationClock(result.originalClockReadback, result)
        result.originalClockValidity.forEach { appendLine("  restoration validity elapsed=${it.elapsedMs} ms"); appendConfigurationClock(it.result, result) }
        result.restorationNotes.forEach { appendLine("Restoration note: ${safeText(it, 400)}") }
        appendLine("Release AS (first):"); appendConfigurationOwnership(result.asRelease)
        appendLine("Release AC (second):"); appendConfigurationOwnership(result.acRelease)
        appendLine("Connection close: ${result.closeStatus}; native fd remained borrowed")
        appendLine("Kernel bindings: ${result.kernelRecovery}")
        appendLine("Framework enumeration: ${result.frameworkRecovery}")
        appendLine("Kernel/framework summary: ${result.enumerationRecovery?.recoveryStatus ?: "INDETERMINATE"}")
        appendLine("Enumeration recovery observation window expired: ${result.enumerationRecovery?.recoveryWindowExpired ?: false}")
        result.enumerationRecovery?.recoveryObservations?.forEach {
            appendLine("Post-release enumeration observation: ${it.elapsedMs} ms"); appendForcedSnapshot(it.snapshot, generation)
        }
        appendLine("Final Android AudioDeviceInfo after connection close (secondary):")
        result.afterAndroidAudio?.let { appendAndroidAudio(
            UsbFsOwnershipDiagnostics(generation, plan.probe, UsbFsProbeStatus.RECORDED, androidAudio = it), generation) }
        appendLine("Driver rebinding, AudioDeviceInfo continuity, unchanged IDs/ALSA addresses/capabilities and HID controls do not establish functional audio routing or DAC playback.")
    }

    private fun StringBuilder.appendSilentTransport(result: UsbSilentTransportExperimentResult?, generation: Long,
        mode: UsbPcmDiagnosticMode = UsbPcmDiagnosticMode.ZERO) {
        val reference = mode == UsbPcmDiagnosticMode.REFERENCE
        if (result != null && result.generation != generation) {
            appendLine("Stale attachment generation; previous Session ${if (reference) 8 else 7} evidence omitted."); return
        }
        appendLine("Development-only direct USB diagnostic; never starts automatically.")
        appendLine("Target: PCM stereo signed integer 16-bit little-endian / 44100 Hz; bytes/frame=4")
        if (reference) appendReferenceSource(result) else appendLine("Source: DIGITAL ZERO ONLY")
        appendLine("Duration target=${UsbIsoTiming.DURATION_MS} ms; duration stop, no EOF")
        appendLine("Audible signal intentionally generated: ${if (reference) "YES, low-level fixed reference" else "NO"}; DSP/sample modification: NONE")
        appendLine("Functional normal USB playback: NOT_VERIFIED; Bit-perfect song playback: NOT IMPLEMENTED / NOT ASSERTED")
        if (result == null) { appendLine("Not requested. Debug action: ${if (reference) "Play USB L/R reference tone (1 s)" else "Stream silent USB 16/44.1 (1 s)"}"); return }
        appendLine("Attachment generation=${result.generation}; stream generation=${result.streamGeneration}; phase=${result.phase}; result=${result.status}")
        result.detail?.let { appendLine("Note: ${safeText(it, 400)}") }
        result.resolution.issues.forEach { appendLine("Resolution issue: ${safeText(it, 400)}") }
        result.resolution.plan?.let { plan ->
            appendLine("Resolved AC=${plan.configurationPlan.clock.audioControl.number}; AS=${plan.configurationPlan.streaming.number}; target alt=${plan.configurationPlan.streaming.alternate}; idle=${plan.configurationPlan.idle.alternate}")
            listOf("Data OUT" to plan.data, "Explicit feedback IN" to plan.feedback).forEach { (label, endpoint) ->
                appendLine("$label: EP 0x${usbHex(endpoint.address)}; attributes=0x${usbHex(endpoint.attributes)}; wMaxPacketSize=${endpoint.maxPacketSizeRaw}; bInterval=${endpoint.interval}")
            }
        }
        result.timing?.let {
            appendLine("Actual USB speed: HIGH (fresh usbfs evidence); data service interval=${it.dataIntervalUs} us; feedback interval=${it.feedbackIntervalUs} us; endpoint capacity=${it.capacity} bytes/service interval")
            appendLine("Feedback format: ${it.feedbackFormat}; plausibility=44100 Hz +/-${UsbExplicitFeedback.PLAUSIBILITY_PERCENT}%")
            appendLine("Nominal bootstrap <=${UsbIsoTiming.BOOTSTRAP_MS} ms; last-valid feedback holdover <=${it.feedbackHoldoverMs} ms; invalid consecutive limit=${UsbIsoTiming.INVALID_FEEDBACK_LIMIT}")
        }
        appendLine("OUT pool=${UsbIsoTiming.OUT_POOL}; packets/OUT URB=${UsbIsoTiming.OUT_PACKETS}; feedback pool=${UsbIsoTiming.FEEDBACK_POOL}; packets/feedback URB=1")
        appendLine("Scheduler: exact common denominator 8000*65536; nominal numerator 44100*65536, feedback numerator raw16.16*8000; remainder preserved across feedback lock. Feedback changes packetization only.")
        val native = result.transport
        if (native == null) appendLine("Native transport not started / result unavailable.") else {
            appendLine("Transport lifecycle: ${native.state}; stop reason=${native.stopReason}; worker stopped=${native.workerStopped}")
            appendLine("Integrity: ${if (native.valid) "VALID" else "COMPROMISED"}; reasons=${native.integrityReasons.joinToString().ifEmpty { if (native.valid) "none" else "result/authority unavailable" }}")
            native.exceptionType?.let { appendLine("Native boundary exception type: ${safeText(it)}; private message omitted") }
            native.authorityFailure?.let { appendLine("Authority changed: $it; values are historical only") }
            if (!reference) appendLine("PCM all-zero buffer validation: ${if (native.count("outBytesSubmitted") > 0 && native.count("pcmByteOr") == 0L) "PASS" else "UNAVAILABLE/FAIL"}; bytewise OR=${native.count("pcmByteOr")}")
            else {
                appendLine("Source PCM hash: ${native.counters["sourcePcmHash"]?.let(::pcmHashHex) ?: "unavailable"}")
                appendLine("Submitted PCM hash: ${native.counters["submittedPcmHash"]?.let(::pcmHashHex) ?: "unavailable"}")
                val hashesPresent = native.counters.containsKey("sourcePcmHash") && native.counters.containsKey("submittedPcmHash")
                appendLine("Source/submitted hash match: ${hashesPresent && native.count("sourcePcmHash") == native.count("submittedPcmHash")}; source integrity verified=${native.sourceVerified}")
                appendLine("FNV-1a 64-bit over exact ordered PCM bytes, offset cbf29ce484222325, prime 100000001b3, modulo 2^64. Payload hash advances only after successful SUBMITURB; no padding or metadata.")
                appendLine("Generated but unsubmitted frames remain explicit. No completion hash or DAC-internal consumed-data hash exists.")
                appendLine("Bounded GENERATED PCM frame examples (index; L signed; R signed; LE packed L-low L-high R-low R-high):")
                native.pcm.forEach { appendLine("  ${it.index}; ${it.left}; ${it.right}; ${it.bytes.joinToString(" ") { byte -> usbHex(byte) }}") }
            }
            native.counters.forEach { (name, value) -> appendLine("  $name: $value") }
            appendLine("Kernel OUT actual bytes are raw ISO completion evidence, separately from submitted bytes. Successful frames require zero URB/packet status and exact packet length; no DAC-consumption inference.")
            appendLine("Feedback raw examples (bounded):")
            native.feedback.forEach { sample -> appendLine("  stream=${native.streamGeneration}; elapsed=${sample.elapsedUs} us; conservative age=${sample.conservativeAgeUs} us (submission-to-reap bound); raw bytes=${sample.bytes.joinToString(" ") { usbHex(it) }}; raw unsigned=${sample.raw}; actual length=${sample.length}; packet status=${sample.packetStatus}; validity=${sample.validity}; frames/data service interval=${sample.framesPerServiceInterval(native.count("dataIntervalUs")) ?: "not accepted"}; approximate Hz=${sample.rateHz ?: "not accepted"}") }
            val samples = native.count("feedbackSamples")
            if (samples > 0) appendLine("Accepted feedback Hz min=${native.count("minFeedbackRaw") * 8000.0 / 65536.0}; max=${native.count("maxFeedbackRaw") * 8000.0 / 65536.0}; mean=${native.count("sumFeedbackRaw") * 8000.0 / 65536.0 / samples}")
            appendLine("Packet completion examples (first normal samples and bounded errors; every completion inspected):")
            native.packets.forEach { appendLine("  stream=${it.streamGeneration}; submission=${it.submission}; EP=0x${usbHex(it.endpoint)}; URB status=${it.urbStatus}; packet status=${it.packetStatus}; requested=${it.requestedLength}; kernel actual=${it.actualLength}") }
            appendLine("Shutdown accounting complete=${native.shutdownAccounted}; native memory released=${result.nativeMemoryReleased}; connection close required for unreconciled work=${result.connectionCloseRequired}")
            appendLine("Owning-connection close disposed ${result.connectionCloseDisposedUrbs ?: "not needed / count unavailable"} unreconciled URB records; outstanding after successful close/pool release=${result.outstandingAfterConnectionClose ?: "unavailable"}. Pending/completed state and completion frames are not inferred from close disposal.")
            if (result.connectionCloseRequired) appendLine("Endpoint pools retained until owning connection close; no alt/clock restoration or explicit AS/AC release while URBs were unreconciled. Connection close cancellation is not fabricated completion evidence.")
        }
        appendLine("Reused Session 6 configuration/restoration transaction:")
        appendConfigurationExperiment(result.configuration, generation, endpointWork = true)
        appendLine("Kernel recovery=${result.kernelRecovery}; framework enumeration=${result.frameworkRecovery}; functional recovery=NOT_VERIFIED")
        appendLine("Copy this complete report before reconnecting. Do not immediately repeat this diagnostic. A physical reconnect may be needed for normal playback afterward.")
    }

    private fun StringBuilder.appendReferenceSource(result: UsbSilentTransportExperimentResult?) {
        appendLine("Reference: 441 Hz; 100 samples/cycle; immutable signed int16 table; intrinsic peak +/-1024 (about -30 dBFS). No runtime sin, float oscillator, software volume, or DSP.")
        appendLine("Absolute source windows: 0..4409 silence; 4410..13209 LEFT only (8800 frames, 88 cycles); 13210..17619 silence; 17620..26419 RIGHT only (8800 frames, 88 cycles); >=26420 silence, including frames beyond 44100.")
        appendLine("Expected manual observation: silence -> LEFT tone -> gap -> RIGHT tone -> silence. Listening/channel identity: NOT AUTOMATICALLY VERIFIED.")
        appendLine("Generated source bytes -> identical submitted payload bytes -> successful exact-length kernel ISO completions. DAC internal reconstruction is not measured; no song/file/FLAC/Media3 claim.")
        val p = result?.preflight
        appendLine("Reference preflight: ${if (p == null) "NOT ATTEMPTED" else if (p.passed) "PASS" else "FAIL"}; before AC/AS ownership")
        appendLine("Table length=${p?.value(2) ?: "unavailable"}; expected FNV-1a=${pcmHashHex(UsbReferencePreflight.EXPECTED_HASH)}; actual=${p?.value(4)?.let(::pcmHashHex) ?: "unavailable"}; checksum match=${p?.value(4) == UsbReferencePreflight.EXPECTED_HASH}")
        appendLine("Table positive peak=${p?.value(5) ?: "unavailable"}; negative peak=${p?.value(6) ?: "unavailable"}; max abs=${p?.value(7) ?: "unavailable"}")
        appendLine("Packing checks=${p?.value(8) ?: "unavailable"}; channel-plan validation=${p?.value(9) ?: "unavailable"}; deterministic=${p?.value(10) ?: "unavailable"}; active samples/cycle=${p?.value(11) ?: "unavailable"}")
        appendLine("Preflight reason mask=${p?.value(1) ?: "unavailable"}; boundary exception=${p?.exceptionType ?: "none"}")
        if (p != null && !p.passed) appendLine("Reference preflight prevented AC/AS ownership; no reference transport attempted.")
    }

    private fun StringBuilder.appendConfigurationOwnership(ownership: UsbConfigurationAndroidOwnership) {
        appendLine("  attempted=${ownership.attempted}; Android Boolean=${ownership.returned ?: "not observed"}")
        ownership.exceptionType?.let { appendLine("  exception type=${safeText(it)}; private message omitted") }
        ownership.note?.let { appendLine("  ${safeText(it, 300)}") }
    }
    private fun StringBuilder.appendConfigurationControl(evidence: UsbConfigurationControlEvidence?) {
        if (evidence == null) { appendLine("  not issued / no result retained"); return }
        val request = evidence.request
        appendLine("  ${request.operation}: ${evidence.status}; bmRequestType=0x${usbHex(request.requestType)} bRequest=0x${usbHex(request.request)} wValue=0x${usbHex(request.value, 4)} wIndex=0x${usbHex(request.index, 4)} wLength=${request.length}")
        appendLine("  payload: ${request.payload.joinToString(" ") { usbHex(it) }.ifEmpty { "none (read)" }}")
        appendLine("  generation=${evidence.generation ?: "unrecorded"}; order=${evidence.order ?: "unrecorded"}; elapsed=${evidence.elapsedMs ?: "unrecorded"} ms")
        evidence.native?.let {
            appendLine("  USBDEVFS_CONTROL: return=${it.returnValue}; errno=${it.errno}; errno name=${it.errnoName ?: "none"}; requested length=${it.requestedLength}; actual length=${it.actualLength}; packet version=${it.apiVersion}; native operation=${it.operation}")
            appendLine("  raw: ${it.rawBytes.joinToString(" ") { byte -> usbHex(byte) }.ifEmpty { "none" }}")
        }
        evidence.alternate?.let { appendLine("  decoded alternate=$it") }
        if (request.operation == UsbConfigurationControlOperation.GET_INTERFACE) {
            appendLine("  Descriptor validity: ${evidence.descriptorValidation}")
            appendLine("  Declared by descriptors: ${when (evidence.descriptorValidation) {
                UsbAlternateDescriptorValidation.VALID_DECLARED_ALTERNATE -> "YES"
                UsbAlternateDescriptorValidation.INVALID_UNDECLARED_ALTERNATE -> "NO"
                else -> "unavailable"
            }}; authoritative current-alt query evidence: ${if (evidence.authoritative) "YES (query-time evidence only)" else "NO"}")
            if (evidence.descriptorValidation == UsbAlternateDescriptorValidation.INVALID_UNDECLARED_ALTERNATE)
                appendLine("  GET_INTERFACE returned an alternate not declared by the supplied descriptors; the response is treated as non-authoritative.")
        }
        evidence.exceptionType?.let { appendLine("  exception type=${safeText(it)}") }
    }
    private fun StringBuilder.appendStreamingAlternate(evidence: UsbStreamingAlternateEvidence?) {
        if (evidence == null) { appendLine("  not issued / no result retained"); return }
        appendLine("  USBDEVFS_SETINTERFACE: interface=${evidence.request.plan.streaming.number}; alternate=${evidence.request.alternate}; status=${evidence.status}")
        appendLine("  generation=${evidence.generation ?: "unrecorded"}; order=${evidence.order ?: "unrecorded"}; elapsed=${evidence.elapsedMs ?: "unrecorded"} ms")
        evidence.result?.let { appendIoctl(it) }
        evidence.exceptionType?.let { appendLine("  exception type=${safeText(it)}") }
    }
    private fun StringBuilder.appendAlternateMutation(evidence: UsbAlternateMutationResult) {
        appendLine("  Host-issued SETINTERFACE (primary):")
        appendStreamingAlternate(evidence.mutation)
        appendLine("  Device-returned GET_INTERFACE (advisory):")
        appendConfigurationControl(evidence.query)
        val matches = evidence.query?.alternate?.let { it == evidence.mutation?.request?.alternate }
        appendLine("  Matches requested alternate: ${matches ?: "unavailable"}; corroboration=${evidence.corroboration}; established by setter without declared contradiction=${evidence.established}")
    }
    private fun StringBuilder.appendConfigurationClock(query: UsbClockQueryResult?, result: UsbAudioConfigurationResult) {
        if (query == null) { appendLine("  not issued / no result retained"); return }
        if (result.accessFailure != null) appendLine("  historical readback only; authority changed")
        query.currentFrequencyHz?.let { appendLine("  decoded frequency=$it Hz") }
        query.clockValid?.let { appendLine("  decoded clock valid=$it") }
        appendClockQueryResult(query)
    }

    private fun StringBuilder.appendIoctl(result: UsbFsIoctlResult) {
        appendLine("    USBDEVFS_${result.operation}: return=${result.returnValue} errno=${result.errno} errno name=${result.errnoName ?: if (result.succeeded) "none (successful call)" else "unknown/unavailable"}")
    }

    private fun StringBuilder.appendAndroidAudio(diagnostics: UsbFsOwnershipDiagnostics?, generation: Long) {
        appendLine("SECONDARY framework evidence; no unique association with the selected UsbDevice is asserted.")
        appendLine("AudioDeviceInfo values do not override USB descriptors, clock results or direct USB rate assessments.")
        appendLine("AudioDeviceInfo presence, the same ID, ALSA address or capability arrays do not prove that an active playback stream will route to that device.")
        if (diagnostics == null) { appendLine("Not requested."); return }
        if (diagnostics.generation != generation || diagnostics.status in setOf(UsbFsProbeStatus.DEVICE_DISCONNECTED,
                UsbFsProbeStatus.STALE_GENERATION, UsbFsProbeStatus.PERMISSION_LOST, UsbFsProbeStatus.TRANSPORT_ERROR)) {
            appendLine("Prior/non-authoritative Android audio candidates omitted.")
            return
        }
        val audio = diagnostics.androidAudio
        if (audio == null) { appendLine("No Android USB output snapshot returned yet."); return }
        audio.detail?.let { appendLine(it) }
        if (!audio.querySucceeded) { appendLine("Framework enumeration query unavailable; device absence is not inferred."); return }
        if (audio.outputs.isEmpty()) appendLine("No USB output candidates reported by Android.")
        audio.outputs.forEachIndexed { index, output ->
            val type = when (output.type) { 11 -> "TYPE_USB_DEVICE"; 12 -> "TYPE_USB_ACCESSORY"; 22 -> "TYPE_USB_HEADSET"; else -> "UNKNOWN_USB_TYPE" }
            appendLine("USB output candidate ${index + 1}: ${safeText(output.product)}")
            appendLine("  type: ${output.type} ($type); Android audio device ID: ${output.id}")
            appendLine("  address: ${UsbFsEvidence.safeAudioAddress(output.safeAddress) ?: "omitted / not a recognized numeric ALSA address"}")
            appendLine("  sample rates: ${output.sampleRates}${if (output.sampleRates.isEmpty()) " (Android reports arbitrary rates; no explicit rate list)" else ""}")
            appendLine("  encodings: ${output.encodings}${if (output.encodings.isEmpty()) " (Android reports arbitrary encodings; no explicit list)" else ""}")
            appendLine("  channel counts: ${output.channelCounts}${if (output.channelCounts.isEmpty()) " (Android reports arbitrary counts; no explicit list)" else ""}")
            appendLine("  channel masks: ${output.channelMasks.joinToString(prefix = "[", postfix = "]") { "0x${hex32(it.toLong() and 0xffffffffL)}" }}")
            appendLine("  channel index masks: ${output.channelIndexMasks.joinToString(prefix = "[", postfix = "]") { "0x${hex32(it.toLong() and 0xffffffffL)}" }}")
        }
    }

    private fun StringBuilder.appendClockQueries(diagnostics: UsbAudioClockDiagnostics?, currentGeneration: Long) {
        if (diagnostics == null) {
            appendLine("Not requested for this generation. Use Query UAC2 clock capabilities.")
            appendLine("Rate range not yet queried. No UAC2 class-control reads recorded in this query set.")
            appendLine("This query set issues no SET request. Session 6 clock/alternate mutations are reported separately.")
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
            diagnostics.claimMode == UsbClockClaimMode.USB_CONFIGURATION_NATIVE ->
                "Session 6 native pre-flight reads while resolved AC is force-owned; mutations are reported in the configuration section."
            diagnostics.claimMode == UsbClockClaimMode.AUDIOCONTROL_FORCED_NATIVE ->
                "Session 5: these reads were issued only after Android force=true AC ownership; native USBDEVFS_CONTROL evidence follows."
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
        diagnostics.results.forEach { appendClockQueryResult(it) }
        appendLine("Values describe the Clock Source at query time, not track rate, PCM format support, or Bit-perfect playback.")
        appendLine("In this query set: Only endpoint-zero device-to-host Clock Source reads are allowed; no writes or streaming.")
    }

    private fun StringBuilder.appendClockQueryResult(result: UsbClockQueryResult) {
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
            attempt.nativeResult?.let { native ->
                val actualLength = if (native.returnValue >= 0) native.returnValue.toString() else "unavailable (failed ioctl)"
                appendLine("    USBDEVFS_CONTROL: ioctl return=${native.returnValue} errno=${native.errno} errno name=${native.errnoName ?: "none"} requested length=${native.requestedLength} actual length=$actualLength; retained bytes=${native.rawBytes.size}")
            }
            attempt.detail?.let { appendLine("    $it") }
        }
    }

    private fun StringBuilder.appendClaimExperiment(experiment: UsbAudioClaimExperimentResult?, generation: Long, querying: Boolean) {
        appendLine("Session 3 scope only (separate from Session 5):")
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
        appendLine("This is temporary diagnostic ownership and read-only clock evidence. Direct USB song playback is not implemented; Sessions 7/8 transport has separate evidence.")
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
    private fun safeText(value: String?, maxLength: Int = 160): String = value?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")?.take(maxLength)?.takeIf { it.isNotBlank() } ?: "unknown"
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
