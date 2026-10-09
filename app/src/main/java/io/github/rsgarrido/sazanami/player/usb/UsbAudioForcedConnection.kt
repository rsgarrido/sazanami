package io.github.rsgarrido.sazanami.player.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.os.SystemClock
import kotlinx.coroutines.delay

/** Session 5 only. Android owns detach/claim/release/close; native borrows fd per call. */
internal class UsbAudioForcedConnection(private val connection: UsbDeviceConnection, device: UsbDevice,
    private val context: Context, private val plan: UsbFsProbePlan, private val generation: Long) : UsbAudioForcedTransport {
    private val handles: Map<UsbAudioClaimInterface, UsbInterface> = (0 until device.interfaceCount).associate { position ->
        val iface = device.getInterface(position)
        UsbAudioClaimInterface(position, iface.id, iface.alternateSetting, iface.interfaceClass,
            iface.interfaceSubclass, iface.interfaceProtocol) to iface
    }
    private var owned: UsbAudioForcedClaimRequest? = null
    private var lastRead: UsbFsControlEvidence? = null
    override fun interfaces() = handles.keys.toList()
    override fun snapshot() = UsbFsDiagnostics.inspect(connection.fileDescriptor, plan, generation, probeClaim = false)
        .copy(androidAudio = frameworkSnapshot())
    override fun frameworkSnapshot() = UsbAndroidAudioInspector.inspect(context)

    override fun claim(request: UsbAudioForcedClaimRequest): Boolean {
        require(owned == null && request.force && request.audioControl.isUac2AudioControl &&
            request.audioControl.number == plan.audioControl?.number)
        val resolvedAudioControlInterface = requireNotNull(handles[request.audioControl])
        val claimed = connection.claimInterface(resolvedAudioControlInterface, true)
        if (claimed) owned = request
        return claimed
    }

    override fun release(request: UsbAudioForcedClaimRequest): Boolean {
        require(owned === request)
        try { return connection.releaseInterface(requireNotNull(handles[request.audioControl])) }
        finally { owned = null }
    }

    override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
        val ownership = requireNotNull(owned)
        require(request.interfaceNumber == ownership.audioControl.number &&
            request.entityId == plan.resolution.target?.clockEntityId && buffer.size == request.length)
        lastRead = null
        val evidence = UsbFsDiagnostics.readClock(connection.fileDescriptor, request)
        lastRead = evidence
        evidence.rawBytes.forEachIndexed { index, byte -> buffer[index] = byte.toByte() }
        return evidence.returnValue
    }

    override fun lastControlEvidence() = lastRead
    override fun close() = connection.close()
    override fun monotonicMs() = SystemClock.elapsedRealtime()
    override suspend fun pause(milliseconds: Long) = delay(milliseconds)
}
