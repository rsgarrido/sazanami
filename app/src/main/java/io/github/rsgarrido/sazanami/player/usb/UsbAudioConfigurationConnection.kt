package io.github.rsgarrido.sazanami.player.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

/** One Android connection. Reuses Session 5 AC ownership/reads/snapshots and Session 6 configuration.
 * Session 7 can obtain its typed silence backend only while both resolved interfaces are owned.
 */
internal class UsbAudioConfigurationConnection private constructor(
    private val connection: UsbDeviceConnection,
    private val device: UsbDevice,
    private val plan: UsbAudioConfigurationPlan,
    private val ac: UsbAudioForcedConnection
) : UsbAudioConfigurationTransport {
    constructor(connection: UsbDeviceConnection, device: UsbDevice, context: Context,
        plan: UsbAudioConfigurationPlan, generation: Long) : this(connection, device, plan,
        UsbAudioForcedConnection(connection, device, context, plan.probe, generation))

    private var acOwned = false
    private var asOwned = false
    @Volatile private var silent: UsbNativeSilentTransport? = null
    fun stopSilentTransport() { silent?.requestStop() }
    fun silentBackend(target: UsbSilentTransportPlan, generation: Long, stream: Long,
        accessFailure: () -> UsbClockQueryStatus?): UsbNativeSilentTransport {
        require(acOwned && asOwned && target.configurationPlan === plan && silent == null)
        return UsbNativeSilentTransport(connection.fileDescriptor, target, generation, stream, accessFailure).also { silent = it }
    }
    private val streamingHandle = device.getInterface(plan.asClaim.audioStreaming.position).also {
        require(it.id == plan.streaming.number && it.alternateSetting == plan.idle.alternate && it.interfaceClass == 1 &&
            it.interfaceSubclass == 2 && it.interfaceProtocol == 0x20)
    }
    override fun interfaces() = ac.interfaces()
    override fun snapshot() = ac.snapshot()
    override fun frameworkSnapshot() = ac.frameworkSnapshot()
    override fun read(request: UsbClockReadRequest, buffer: ByteArray) = ac.read(request, buffer)
    override fun lastControlEvidence() = ac.lastControlEvidence()
    override fun close() = ac.close()
    override fun monotonicMs() = ac.monotonicMs()
    override suspend fun pause(milliseconds: Long) = ac.pause(milliseconds)
    override fun connectionValid() = connection.fileDescriptor >= 0
    override fun claim(request: UsbAudioForcedClaimRequest): Boolean {
        require(request === plan.acClaim)
        val claimed = ac.claim(request)
        if (claimed) acOwned = true
        return claimed
    }
    override fun claimStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean {
        require(request === plan.asClaim && request.force && acOwned && !asOwned)
        val resolvedAudioStreamingInterface = streamingHandle
        val claimed = connection.claimInterface(resolvedAudioStreamingInterface, true)
        if (claimed) asOwned = true
        return claimed
    }
    override fun releaseStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean {
        require(request === plan.asClaim && asOwned)
        try { return connection.releaseInterface(streamingHandle) }
        finally { asOwned = false }
    }
    override fun release(request: UsbAudioForcedClaimRequest): Boolean {
        require(request === plan.acClaim && acOwned && !asOwned)
        try { return ac.release(request) }
        finally { acOwned = false }
    }
    override fun control(request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence {
        require(request.plan === plan && acOwned && asOwned)
        return UsbFsDiagnostics.configurationControl(connection.fileDescriptor, request)
    }
    override fun setAlternate(request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence {
        require(request.plan === plan && acOwned && asOwned)
        return UsbFsDiagnostics.setStreamingAlternate(connection.fileDescriptor, request)
    }
}
