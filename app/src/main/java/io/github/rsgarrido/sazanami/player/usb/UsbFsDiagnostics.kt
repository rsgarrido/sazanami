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

    fun inspect(borrowedFd: Int, plan: UsbFsProbePlan, generation: Long): UsbFsOwnershipDiagnostics {
        if (borrowedFd < 0) return UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.INVALID_FD,
            detail = "Android did not expose a valid connection fd; no usbfs ioctl issued.")
        if (!available) return UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.NATIVE_UNAVAILABLE,
            detail = "USB diagnostic library/API unavailable for this build/ABI; no usbfs ioctl issued.")
        return try {
            UsbFsEvidence.decode(nativeInspect(borrowedFd, plan.interfaces.map { it.number }.toIntArray(),
                plan.audioControl?.number ?: -1), plan, generation)
        } catch (_: LinkageError) { UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.NATIVE_UNAVAILABLE,
            detail = "USB diagnostic JNI linkage/result binding unavailable for this build/ABI; no usable result returned.") }
    }

    private external fun nativeApiVersion(): Int
    private external fun nativeInspect(borrowedFd: Int, interfaceNumbers: IntArray, audioControlNumber: Int): UsbFsNativeResult
}
