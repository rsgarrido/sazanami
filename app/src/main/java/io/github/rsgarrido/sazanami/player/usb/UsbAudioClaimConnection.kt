package io.github.rsgarrido.sazanami.player.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface

/** Android adapter: the single claim call uses a literal false, never supplied by the UI. */
internal class UsbAudioClaimConnection(private val connection: UsbDeviceConnection, device: UsbDevice) : UsbAudioClaimTransport {
    private val controlReads = UsbAudioClockConnection(connection)
    private val handles: Map<UsbAudioClaimInterface, UsbInterface> = (0 until device.interfaceCount).associate { position ->
        val iface = device.getInterface(position)
        UsbAudioClaimInterface(position, iface.id, iface.alternateSetting, iface.interfaceClass,
            iface.interfaceSubclass, iface.interfaceProtocol) to iface
    }

    override fun interfaces(): List<UsbAudioClaimInterface> = handles.keys.toList()

    override fun claim(request: UsbAudioControlClaimRequest): Boolean {
        require(!request.force && request.audioControl.isUac2AudioControl)
        val iface = requireNotNull(handles[request.audioControl])
        return connection.claimInterface(iface, false)
    }

    override fun release(request: UsbAudioControlClaimRequest): Boolean =
        connection.releaseInterface(requireNotNull(handles[request.audioControl]))

    override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int = controlReads.read(request, buffer)
    override fun close() = connection.close()
}
