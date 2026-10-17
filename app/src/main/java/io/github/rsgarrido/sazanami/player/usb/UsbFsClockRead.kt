package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep

@Keep
internal data class UsbFsNativeControlResult(val requestedLength: Int, val returnValue: Int,
    val errno: Int, val errnoName: String, val rawBytes: ByteArray,
    val apiVersion: Int = UsbFsEvidence.NATIVE_API_VERSION,
    val operation: Int = UsbFsControlEvidence.CLOCK_READ,
    val actualLength: Int = rawBytes.size)

internal data class UsbFsControlEvidence(val requestedLength: Int, val returnValue: Int,
    val errno: Int, val errnoName: String?, val rawBytes: List<Int>,
    val apiVersion: Int = UsbFsEvidence.NATIVE_API_VERSION, val operation: Int = CLOCK_READ,
    val actualLength: Int = rawBytes.size) {
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
        const val CLOCK_READ = 1
        const val GET_INTERFACE = 2
        const val CLOCK_SET = 3
        fun decode(packet: UsbFsNativeControlResult, request: UsbClockReadRequest): UsbFsControlEvidence = decode(packet, request.length)

        fun decode(packet: UsbFsNativeControlResult, requestedLength: Int, expectedOperation: Int = CLOCK_READ): UsbFsControlEvidence {
            // A one-byte GET_INTERFACE response is serialized only for an exact one-byte return.
            val expectedActual = if (expectedOperation == GET_INTERFACE && packet.returnValue != 1) 0
                else packet.returnValue.coerceIn(0, requestedLength)
            require(packet.apiVersion == UsbFsEvidence.NATIVE_API_VERSION && packet.operation == expectedOperation &&
                packet.requestedLength == requestedLength && packet.actualLength == expectedActual &&
                packet.rawBytes.size == packet.actualLength &&
                (if (packet.returnValue < 0) packet.errno > 0 else packet.errno == 0)) {
                "Malformed native control evidence"
            }
            return UsbFsControlEvidence(packet.requestedLength, packet.returnValue, packet.errno,
                UsbFsEvidence.errnoName(packet.errno, packet.errnoName), packet.rawBytes.map { it.toInt() and 0xff },
                packet.apiVersion, packet.operation, packet.actualLength)
        }
    }
}

/** Metadata accompanies the existing pure clock decoder, without changing Android transports. */
internal interface UsbClockNativeReadTransport : UsbClockReadTransport {
    fun lastControlEvidence(): UsbFsControlEvidence?
}
