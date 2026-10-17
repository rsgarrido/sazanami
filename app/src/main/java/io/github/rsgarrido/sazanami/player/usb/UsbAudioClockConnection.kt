package io.github.rsgarrido.sazanami.player.usb

import android.hardware.usb.UsbDeviceConnection

/** Endpoint-zero reads only. Caller supplies a permitted connection on its serialized IO worker.
 * No claim fallback: Android's negative return value cannot prove that claiming AC is required.
 */
internal class UsbAudioClockConnection(private val connection: UsbDeviceConnection) : UsbClockReadTransport {
    override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
        require(request.requestType == 0xA1 && request.request in 1..2)
        require(buffer.size == request.length && request.value in setOf(0x0100, 0x0200))
        return connection.controlTransfer(request.requestType, request.request, request.value, request.index,
            buffer, buffer.size, CONTROL_TIMEOUT_MS)
    }

    private companion object { const val CONTROL_TIMEOUT_MS = 1_000 }
}
