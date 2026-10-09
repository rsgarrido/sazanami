package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep

@Keep
internal data class UsbFsNativeControlResult(val requestedLength: Int, val returnValue: Int,
    val errno: Int, val errnoName: String, val rawBytes: ByteArray)

internal data class UsbFsControlEvidence(val requestedLength: Int, val returnValue: Int,
    val errno: Int, val errnoName: String?, val rawBytes: List<Int>) {
    fun failureStatus(): UsbClockQueryStatus = when (errnoName) {
        "EPIPE" -> UsbClockQueryStatus.STALL_LIKE
        "ENODEV" -> UsbClockQueryStatus.DEVICE_DISCONNECTED
        "EBUSY" -> UsbClockQueryStatus.INTERFACE_BUSY
        "EINVAL" -> UsbClockQueryStatus.INVALID_REQUEST
        "EACCES", "EPERM" -> UsbClockQueryStatus.PERMISSION_LOST
        else -> UsbClockQueryStatus.TRANSPORT_ERROR
    }

    fun errorExplanation(): String = "USBDEVFS_CONTROL returned $returnValue; errno=$errno (${errnoName ?: "unknown"}). " +
        when (errnoName) {
            "EPIPE" -> "STALL-like kernel result; not proof that this control is unsupported."
            "ENODEV" -> "Device disconnected; stop further reads and invalidate authority."
            "EBUSY" -> "Interface/control path busy; no capability inferred."
            "EINVAL" -> "Kernel rejected request arguments; no capability inferred."
            "EACCES", "EPERM" -> "USB access denied; no capability inferred."
            else -> "Transport failure; no capability inferred."
        }

    companion object {
        fun decode(packet: UsbFsNativeControlResult, request: UsbClockReadRequest): UsbFsControlEvidence {
            require(packet.requestedLength == request.length &&
                packet.rawBytes.size == packet.returnValue.coerceIn(0, request.length) &&
                (if (packet.returnValue < 0) packet.errno > 0 else packet.errno == 0)) {
                "Malformed native clock-read evidence"
            }
            return UsbFsControlEvidence(packet.requestedLength, packet.returnValue, packet.errno,
                UsbFsEvidence.errnoName(packet.errno, packet.errnoName), packet.rawBytes.map { it.toInt() and 0xff })
        }
    }
}

/** Metadata accompanies the existing pure clock decoder, without changing Android transports. */
internal interface UsbClockNativeReadTransport : UsbClockReadTransport {
    fun lastControlEvidence(): UsbFsControlEvidence?
}
