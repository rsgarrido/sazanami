package io.github.rsgarrido.sazanami.player.usb

internal data class UsbAudioConfigurationResolution(val plan: UsbAudioConfigurationPlan? = null,
    val issues: List<String> = emptyList())

/** Shared descriptor-selected format; Session 6 defaults to PCM16/44.1. Keys/entities are descriptor-derived. */
internal class UsbAudioConfigurationPlan private constructor(
    val probe: UsbFsProbePlan,
    val clock: UsbAudioClockTarget,
    val streaming: UsbInterfaceKey,
    val idle: UsbInterfaceKey,
    val dataEndpoint: Int,
    val acClaim: UsbAudioForcedClaimRequest,
    val asClaim: UsbAudioStreamingClaimRequest,
    val declaredAlternates: Set<Int>,
    val pcmFormat: UsbDirectFormat
) {
    val selectedFormat=UsbSelectedPcmFormat(pcmFormat,streaming)
    val sampleRateHz get()=pcmFormat.sampleRateHz
    val channels get()=pcmFormat.channelCount
    val subslotBytes get()=pcmFormat.subslotBytes
    val validBits get()=pcmFormat.validBits

    companion object {
        fun resolve(model: UsbAudioDescriptorModel, android: List<UsbAndroidInterfaceSnapshot>,
            sampleRateHz: Long = UsbDirectPcmFormat.REFERENCE_RATE_HZ, bitsPerSample: Long = 16, channelCount: Long = 2): UsbAudioConfigurationResolution {
            val pcmFormat=UsbDirectPcmFormat.resolve(sampleRateHz,channelCount,bitsPerSample)
                ?: return UsbAudioConfigurationResolution(issues = listOf("Unsupported direct PCM format."))
            val probe = UsbFsProbePlan.create(model, android)
            val clock = probe.resolution.target ?: return UsbAudioConfigurationResolution(issues = probe.warnings)
            fun blocked(detail: String) = UsbAudioConfigurationResolution(issues = probe.warnings + detail)
            if ((clock.controls and 3L) != 3L) return blocked("Clock sampling frequency is not advertised as readable/writable; no force ownership attempted.")
            if (((clock.controls shr 2) and 3L) !in listOf(1L, 3L)) return blocked("Readable Clock Validity is required.")
            val facts = android.mapIndexed { position, iface -> UsbAudioClaimInterface(position, iface.number,
                iface.alternate, iface.interfaceClass, iface.subclass, iface.protocol) }
            val ac = UsbAudioForcedClaimRequest.resolve(probe, facts) ?: return blocked("No unique matching Android AudioControl handle.")
            val matches = model.interfaces.filter { iface ->
                val general = iface.audioStreaming
                val format = iface.audioFormat
                iface.isAudioStreaming && iface.protocol == 0x20 && iface.key.alternate in 1..255 &&
                    iface.key in clock.playbackPaths && general?.formatType == 1 &&
                    general.formats == 1L && general.channelCount == pcmFormat.channelCount && // PCM only; no unresolved format selector.
                    format?.type == 1 && format.subslotBytes == pcmFormat.subslotBytes && format.validBits == pcmFormat.validBits &&
                    iface.endpoints.count { !it.isIn && it.transferType == 1 && it.usageType == 0 } == 1
            }
            val stream = matches.singleOrNull() ?: return blocked(
                "Expected one unambiguous stereo PCM Type I / ${pcmFormat.subslotBytes}-byte / ${pcmFormat.validBits}-valid-bit alternate; found ${matches.size}.")
            val association = model.associations.singleOrNull { it.configurationOffset == stream.key.configurationOffset &&
                it.functionClass == 1 && stream.key.number in it.firstInterface until (it.firstInterface + it.interfaceCount) }
                ?: return blocked("No unique Audio function association for configuration ownership.")
            val functionStreams = model.interfaces.filter { it.isAudioStreaming && it.key.configurationOffset == association.configurationOffset &&
                it.key.number in association.firstInterface until (association.firstInterface + association.interfaceCount) }.map { it.key.number }.distinct()
            if (functionStreams != listOf(stream.key.number)) return blocked(
                "Configuration requires one AS interface in the Audio function; unclaimed streams could share the Clock Source.")
            val alternates = model.interfaces.filter { it.key.configurationOffset == stream.key.configurationOffset &&
                it.key.number == stream.key.number }
            if (alternates.any { !it.isAudioStreaming || it.protocol != 0x20 || it.key.alternate !in 0..255 ||
                    it.endpointCount != it.endpoints.size } || alternates.map { it.key.alternate }.distinct().size != alternates.size)
                return blocked("AudioStreaming alternate descriptors are structurally inconsistent or duplicated.")
            val idle = alternates.singleOrNull { it.endpointCount == 0 && it.endpoints.isEmpty() }
                ?: return blocked("No unique descriptor-declared zero-endpoint AudioStreaming idle alternate.")
            if (stream.key.number == clock.audioControl.number || probe.interfaces.singleOrNull {
                it.number == stream.key.number
            }?.roles != listOf(UsbFsInterfaceRole.AUDIOSTREAMING)) return blocked("Streaming interface role/function is ambiguous.")
            val idleHandle = facts.singleOrNull { it.number == idle.key.number && it.alternate == idle.key.alternate &&
                it.interfaceClass == 1 && it.subclass == 2 && it.protocol == 0x20 }
                ?: return blocked("No unique matching Android AudioStreaming idle handle.")
            if (facts.count { it.number == stream.key.number && it.alternate == stream.key.alternate &&
                    it.interfaceClass == 1 && it.subclass == 2 && it.protocol == 0x20 } != 1 ||
                facts.any { it.number == stream.key.number && (it.interfaceClass != 1 || it.subclass != 2 || it.protocol != 0x20) })
                return blocked("Android streaming alternates do not uniquely match the resolved UAC2 interface.")
            val data = stream.endpoints.single { !it.isIn && it.transferType == 1 && it.usageType == 0 }
            if (data.address !in 1..15 || data.packetPayloadBytes == 0) return blocked("Playback data endpoint descriptor is invalid.")
            return UsbAudioConfigurationResolution(UsbAudioConfigurationPlan(probe, clock, stream.key, idle.key,
                data.address, ac, UsbAudioStreamingClaimRequest.fromIdle(idleHandle), alternates.map { it.key.alternate }.toSet(), pcmFormat))
        }
    }

    /** Validated UAC2 AS idle facts. Adapter accepts only the request instance from its plan. */
    internal class UsbAudioStreamingClaimRequest private constructor(val audioStreaming: UsbAudioClaimInterface) {
        val force: Boolean get() = true
        companion object {
            internal fun fromIdle(iface: UsbAudioClaimInterface): UsbAudioStreamingClaimRequest {
                require(iface.interfaceClass == 1 && iface.subclass == 2 && iface.protocol == 0x20 && iface.alternate in 0..255)
                return UsbAudioStreamingClaimRequest(iface)
            }
        }
    }
}

/** Fresh RANGE/CUR proof is required to create either the target or restoration write. */
internal class UsbConfigurationMutationPermit private constructor(val plan: UsbAudioConfigurationPlan, val originalRateHz: Long) {
    companion object {
        fun fromPreflight(plan: UsbAudioConfigurationPlan, generation: Long,
            preflight: UsbAudioClockDiagnostics): UsbConfigurationMutationPermit? {
            if (preflight.generation != generation || preflight.resolution.target != plan.clock ||
                preflight.results.size != UsbClockQuery.entries.size || preflight.results.any { it.status != UsbClockQueryStatus.SUCCESS }) return null
            val range = preflight.results.singleOrNull { it.query == UsbClockQuery.FREQUENCY_RANGE }
            val original = preflight.results.singleOrNull { it.query == UsbClockQuery.CURRENT_FREQUENCY }?.currentFrequencyHz ?: return null
            if (original !in 1L..0xffffffffL || UsbAudioClockControl.assessRate(plan.sampleRateHz, range) != UsbClockRateSupport.REPRESENTED ||
                UsbAudioClockControl.assessRate(original, range) != UsbClockRateSupport.REPRESENTED) return null
            return UsbConfigurationMutationPermit(plan, original)
        }
    }
}

internal enum class UsbConfigurationControlOperation { GET_INTERFACE, TARGET_CLOCK_SET, ORIGINAL_CLOCK_RESTORE }
internal class UsbConfigurationControlRequest private constructor(val operation: UsbConfigurationControlOperation,
    val plan: UsbAudioConfigurationPlan, val permit: UsbConfigurationMutationPermit? = null) {
    val rateHz: Long? get() = when (operation) {
        UsbConfigurationControlOperation.GET_INTERFACE -> null
        UsbConfigurationControlOperation.TARGET_CLOCK_SET -> plan.sampleRateHz
        UsbConfigurationControlOperation.ORIGINAL_CLOCK_RESTORE -> requireNotNull(permit).originalRateHz
    }
    val requestType: Int get() = if (operation == UsbConfigurationControlOperation.GET_INTERFACE) 0x81 else 0x21
    val request: Int get() = if (operation == UsbConfigurationControlOperation.GET_INTERFACE) 0x0A else 0x01
    val value: Int get() = if (operation == UsbConfigurationControlOperation.GET_INTERFACE) 0 else 0x0100
    val index: Int get() = if (operation == UsbConfigurationControlOperation.GET_INTERFACE) plan.streaming.number
        else (plan.clock.clockEntityId shl 8) or plan.clock.audioControl.number
    val length: Int get() = if (operation == UsbConfigurationControlOperation.GET_INTERFACE) 1 else 4
    val payload: List<Int> get() = rateHz?.let { rate -> (0..3).map { ((rate shr (8 * it)) and 0xffL).toInt() } }.orEmpty()
    companion object {
        fun getInterface(plan: UsbAudioConfigurationPlan) = UsbConfigurationControlRequest(UsbConfigurationControlOperation.GET_INTERFACE, plan)
        fun targetClock(permit: UsbConfigurationMutationPermit) = UsbConfigurationControlRequest(UsbConfigurationControlOperation.TARGET_CLOCK_SET, permit.plan, permit)
        fun restoreClock(permit: UsbConfigurationMutationPermit) = UsbConfigurationControlRequest(UsbConfigurationControlOperation.ORIGINAL_CLOCK_RESTORE, permit.plan, permit)
    }
}

internal data class UsbConfigurationControlEvidence(val request: UsbConfigurationControlRequest,
    val native: UsbFsControlEvidence? = null, val status: UsbClockQueryStatus,
    val alternate: Int? = null, val exceptionType: String? = null,
    val generation: Long? = null, val order: Int? = null, val elapsedMs: Long? = null) {
    val descriptorValidation: UsbAlternateDescriptorValidation get() = when {
        request.operation != UsbConfigurationControlOperation.GET_INTERFACE -> UsbAlternateDescriptorValidation.UNAVAILABLE
        status in setOf(UsbClockQueryStatus.SHORT_RESPONSE, UsbClockQueryStatus.MALFORMED_RESPONSE) -> UsbAlternateDescriptorValidation.MALFORMED
        status != UsbClockQueryStatus.SUCCESS -> if (native?.returnValue?.let { it < 0 } == true)
            UsbAlternateDescriptorValidation.TRANSFER_FAILED else UsbAlternateDescriptorValidation.UNAVAILABLE
        alternate == null || alternate !in 0..255 -> UsbAlternateDescriptorValidation.MALFORMED
        alternate in request.plan.declaredAlternates -> UsbAlternateDescriptorValidation.VALID_DECLARED_ALTERNATE
        else -> UsbAlternateDescriptorValidation.INVALID_UNDECLARED_ALTERNATE
    }
    val authoritative: Boolean get() = descriptorValidation == UsbAlternateDescriptorValidation.VALID_DECLARED_ALTERNATE
    companion object {
        fun decode(request: UsbConfigurationControlRequest, native: UsbFsControlEvidence): UsbConfigurationControlEvidence {
            val status = when {
                native.requestedLength != request.length -> UsbClockQueryStatus.MALFORMED_RESPONSE
                native.returnValue < 0 -> native.failureStatus()
                native.returnValue < request.length -> UsbClockQueryStatus.SHORT_RESPONSE
                native.returnValue > request.length || native.rawBytes.size != request.length -> UsbClockQueryStatus.MALFORMED_RESPONSE
                else -> UsbClockQueryStatus.SUCCESS
            }
            return UsbConfigurationControlEvidence(request, native, status,
                native.rawBytes.singleOrNull()?.takeIf { status == UsbClockQueryStatus.SUCCESS && request.operation == UsbConfigurationControlOperation.GET_INTERFACE })
        }
    }
}

internal class UsbStreamingAlternateRequest private constructor(val plan: UsbAudioConfigurationPlan, val alternate: Int) {
    companion object {
        fun target(plan: UsbAudioConfigurationPlan) = UsbStreamingAlternateRequest(plan, plan.streaming.alternate)
        fun idle(plan: UsbAudioConfigurationPlan) = UsbStreamingAlternateRequest(plan, plan.idle.alternate)
    }
}
internal data class UsbStreamingAlternateEvidence(val request: UsbStreamingAlternateRequest,
    val result: UsbFsIoctlResult? = null, val status: UsbClockQueryStatus, val exceptionType: String? = null,
    val generation: Long? = null, val order: Int? = null, val elapsedMs: Long? = null)

internal enum class UsbAlternateDescriptorValidation { VALID_DECLARED_ALTERNATE, INVALID_UNDECLARED_ALTERNATE, TRANSFER_FAILED, MALFORMED, UNAVAILABLE }
internal enum class UsbAlternateCorroboration { CORROBORATED, INCONSISTENT_UNDECLARED, CONTRADICTORY_DECLARED, UNAVAILABLE }

/** Host mutation, device query and descriptor validation remain separate evidence. */
internal data class UsbAlternateMutationResult(val generation: Long, val mutation: UsbStreamingAlternateEvidence?,
    val query: UsbConfigurationControlEvidence?) {
    val corroboration: UsbAlternateCorroboration get() = when (query?.descriptorValidation) {
        UsbAlternateDescriptorValidation.VALID_DECLARED_ALTERNATE -> if (requireNotNull(query).alternate == mutation?.request?.alternate)
            UsbAlternateCorroboration.CORROBORATED else UsbAlternateCorroboration.CONTRADICTORY_DECLARED
        UsbAlternateDescriptorValidation.INVALID_UNDECLARED_ALTERNATE -> UsbAlternateCorroboration.INCONSISTENT_UNDECLARED
        else -> UsbAlternateCorroboration.UNAVAILABLE
    }
    val established: Boolean get() = mutation?.status == UsbClockQueryStatus.SUCCESS &&
        corroboration != UsbAlternateCorroboration.CONTRADICTORY_DECLARED
}
internal data class UsbConfigurationAndroidOwnership(val attempted: Boolean = false, val returned: Boolean? = null,
    val exceptionType: String? = null, val note: String? = null)
