package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep

/** Separate USB JNI boundary, unrelated to waveform decoding. The fd is borrowed synchronously;
 * the controller must keep its UsbDeviceConnection open until this call returns.
 */
@Keep
internal object UsbFsDiagnostics {
    private val available: Boolean by lazy {
        runCatching { System.loadLibrary("sazanami_usb_diagnostics"); nativeApiVersion() == UsbFsEvidence.NATIVE_API_VERSION }
            .getOrDefault(false)
    }

    fun inspect(borrowedFd: Int, plan: UsbFsProbePlan, generation: Long,
        probeClaim: Boolean = true): UsbFsOwnershipDiagnostics {
        if (borrowedFd < 0) return UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.INVALID_FD,
            detail = "Android did not expose a valid connection fd; no usbfs ioctl issued.")
        if (!available) return UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.NATIVE_UNAVAILABLE,
            detail = "USB diagnostic library/API unavailable for this build/ABI; no usbfs ioctl issued.")
        return try {
            UsbFsEvidence.decode(nativeInspect(borrowedFd, plan.interfaces.map { it.number }.toIntArray(),
                if (probeClaim) plan.audioControl?.number ?: -1 else -1), plan, generation,
                claimProbeExpected = probeClaim && plan.audioControl != null)
        } catch (_: LinkageError) { UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.NATIVE_UNAVAILABLE,
            detail = "USB diagnostic JNI linkage/result binding unavailable for this build/ABI; no usable result returned.") }
    }

    fun readClock(borrowedFd: Int, request: UsbClockReadRequest): UsbFsControlEvidence {
        check(available) { "USB diagnostic library unavailable" }
        require(borrowedFd >= 0)
        val packet = try { nativeReadClock(borrowedFd, request.requestType, request.request,
            request.value, request.index, request.length) }
        catch (_: LinkageError) { throw IllegalStateException("USB clock JNI unavailable") }
        return UsbFsControlEvidence.decode(packet, request)
    }

    fun configurationControl(borrowedFd: Int, request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence {
        check(available)
        require(borrowedFd >= 0)
        val plan = request.plan
        val packet = try {
            if (request.operation == UsbConfigurationControlOperation.GET_INTERFACE)
                nativeGetAudioStreamingInterface(borrowedFd, plan.clock.audioControl.number, plan.streaming.number)
            else nativeSetClockFrequency(borrowedFd, plan.clock.audioControl.number, plan.streaming.number,
                plan.clock.clockEntityId, requireNotNull(request.permit).originalRateHz, requireNotNull(request.rateHz))
        } catch (_: LinkageError) { throw IllegalStateException("USB configuration JNI unavailable") }
        val operation = if (request.operation == UsbConfigurationControlOperation.GET_INTERFACE)
            UsbFsControlEvidence.GET_INTERFACE else UsbFsControlEvidence.CLOCK_SET
        return UsbConfigurationControlEvidence.decode(request, UsbFsControlEvidence.decode(packet, request.length, operation))
    }

    fun setStreamingAlternate(borrowedFd: Int, request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence {
        check(available)
        require(borrowedFd >= 0)
        val plan = request.plan
        val packet = try { nativeSetAudioStreamingAlternate(borrowedFd, plan.clock.audioControl.number,
            plan.streaming.number, plan.streaming.alternate, plan.idle.alternate, request.alternate) }
        catch (_: LinkageError) { throw IllegalStateException("USB alternate JNI unavailable") }
        require((packet.returnValue >= 0 && packet.errno == 0) || (packet.returnValue < 0 && packet.errno > 0))
        val result = UsbFsIoctlResult(UsbFsIoctlOperation.SETINTERFACE, packet.returnValue, packet.errno,
            UsbFsEvidence.errnoName(packet.errno, packet.errnoName))
        val status = when {
            result.returnValue == 0 -> UsbClockQueryStatus.SUCCESS
            result.returnValue > 0 -> UsbClockQueryStatus.MALFORMED_RESPONSE
            else -> UsbFsControlEvidence(0, result.returnValue, result.errno, result.errnoName, emptyList()).failureStatus()
        }
        return UsbStreamingAlternateEvidence(request, result, status)
    }

    private external fun nativeApiVersion(): Int
    /** Activation-only explanation; keep historical diagnostic idle checks unchanged. */
    fun integratedAvailabilityFailure(): String? {
        if (!available) return "DIRECT_NATIVE_UNAVAILABLE"
        return try { if (nativeSilentTransportIdle()) null else "DIRECT_NATIVE_BUSY" }
        catch (_: Throwable) { "DIRECT_NATIVE_IDLE_CHECK_FAILED" }
    }
    fun silentTransportIdle(): Boolean = available && runCatching { nativeSilentTransportIdle() }.getOrDefault(false)
    private external fun nativeSilentTransportIdle(): Boolean
    fun referencePreflight(): UsbReferencePreflight = try {
        check(available)
        UsbReferencePreflight(nativeReferencePreflight().toList())
    } catch (error: Exception) { UsbReferencePreflight(exceptionType = error.javaClass.simpleName) }
    catch (error: LinkageError) { UsbReferencePreflight(exceptionType = error.javaClass.simpleName) }
    private external fun nativeReferencePreflight(): LongArray
    fun createSilentTransport(borrowedFd: Int, target: UsbSilentTransportPlan, generation: Long, stream: Long,
        mode: UsbPcmDiagnosticMode = UsbPcmDiagnosticMode.ZERO, prefixHandle: Long = 0): Long {
        check(available)
        val plan = target.configurationPlan
        return nativeCreateSilentTransport(borrowedFd, generation, stream, intArrayOf(plan.clock.audioControl.number,
            plan.streaming.number, plan.streaming.alternate, plan.idle.alternate, target.data.address, target.data.attributes,
            target.data.maxPacketSizeRaw, target.data.interval, target.feedback.address, target.feedback.attributes,
            target.feedback.maxPacketSizeRaw, target.feedback.interval, plan.sampleRateHz.toInt(), plan.channels, plan.subslotBytes, plan.validBits, mode.ordinal), prefixHandle)
    }
    fun runSilentTransport(handle: Long, generation: Long, stream: Long): UsbIsoTransportResult =
        UsbIsoNativeStats.decode(nativeRunSilentTransport(handle, generation, stream), generation, stream)
    fun stopSilentTransport(handle: Long, generation: Long, stream: Long, reason: Int) = nativeStopSilentTransport(handle, generation, stream, reason)
    fun destroySilentTransport(handle: Long, generation: Long, stream: Long, connectionClosed: Boolean) =
        nativeDestroySilentTransport(handle, generation, stream, connectionClosed)
    private external fun nativeCreateSilentTransport(borrowedFd: Int, generation: Long, stream: Long, configuration: IntArray, prefixHandle: Long): Long
    private external fun nativeRunSilentTransport(handle: Long, generation: Long, stream: Long): UsbIsoNativeSnapshot
    fun fullProgress(handle: Long,generation: Long,stream: Long): UsbFullProgress {
        val values=nativeFullProgress(handle,generation,stream)
        require(values.size==4+UsbIsoNativeStats.controlFields.size+UsbIsoNativeStats.seekFields.size && values[3] in UsbFullTransportPhase.entries.indices.map(Int::toLong))
        val control=UsbIsoNativeStats.controlFields.zip(values.drop(4).take(17)).toMap()+
            UsbIsoNativeStats.seekFields.zip(values.drop(21)).toMap()
        UsbIsoNativeStats.validateControl(control)
        UsbIsoNativeStats.validateSeek(control)
        require(control.getValue("playbackTransportState")==values[3])
        return UsbFullProgress(values[0],values[1],values[2],UsbFullTransportPhase.entries[values[3].toInt()],control)
    }
    private external fun nativeFullProgress(handle: Long,generation: Long,stream: Long): LongArray
    fun setFullPlayWhenReady(handle: Long,generation: Long,stream: Long,intent: UsbFullControlIntent): Boolean =
        nativeSetFullPlayWhenReady(handle,generation,stream,intent.play,intent.sequence,intent.pauses,intent.resumes,intent.seeks,intent.seekSerial,intent.targetFrame,intent.firstTargetFrame)
    private external fun nativeSetFullPlayWhenReady(handle: Long,generation: Long,stream: Long,play: Boolean,sequence: Long,pauses: Long,resumes: Long,seeks: Long,seekSerial: Long,targetFrame: Long,firstTargetFrame: Long): Boolean
    private external fun nativeStopSilentTransport(handle: Long, generation: Long, stream: Long, reason: Int)
    private external fun nativeDestroySilentTransport(handle: Long, generation: Long, stream: Long, connectionClosed: Boolean): Boolean
    private external fun nativeInspect(borrowedFd: Int, interfaceNumbers: IntArray, audioControlNumber: Int): UsbFsNativeResult
    private external fun nativeReadClock(borrowedFd: Int, requestType: Int, request: Int,
        value: Int, index: Int, length: Int): UsbFsNativeControlResult
    private external fun nativeGetAudioStreamingInterface(borrowedFd: Int, acNumber: Int, asNumber: Int): UsbFsNativeControlResult
    private external fun nativeSetClockFrequency(borrowedFd: Int, acNumber: Int, asNumber: Int, entityId: Int,
        originalRateHz: Long, requestedRateHz: Long): UsbFsNativeControlResult
    private external fun nativeSetAudioStreamingAlternate(borrowedFd: Int, acNumber: Int, asNumber: Int,
        targetAlternate: Int, idleAlternate: Int, requestedAlternate: Int): UsbFsNativeIoctlResult
}

@Keep
internal data class UsbFsNativeIoctlResult(val returnValue: Int, val errno: Int, val errnoName: String)
