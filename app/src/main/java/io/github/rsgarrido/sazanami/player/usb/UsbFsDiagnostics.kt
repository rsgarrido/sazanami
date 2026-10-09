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

    private external fun nativeApiVersion(): Int
    private external fun nativeInspect(borrowedFd: Int, interfaceNumbers: IntArray, audioControlNumber: Int): UsbFsNativeResult
    private external fun nativeReadClock(borrowedFd: Int, requestType: Int, request: Int,
        value: Int, index: Int, length: Int): UsbFsNativeControlResult
}
