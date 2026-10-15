package io.github.rsgarrido.sazanami.player.usb

/** Reference identity prevents another attempt (even with the same serial) releasing this lease. */
internal class UsbActivationOwner(val serial: Long)

/** A stale broadcast (including a reused bus ID) cannot revoke a currently present attachment. */
internal fun acceptsUsbDetach(current: UsbAudioInspectionToken?, detached: UsbAudioDeviceIdentity?,
    present: Set<UsbAudioDeviceIdentity>?): Boolean = current != null && detached != null && detached == current.identity &&
        present != null && detached !in present

internal interface UsbOutputSelectionController {
    fun authorizeIntegratedSelection(owner: UsbActivationOwner, identity: UsbAudioDeviceIdentity?, generation: Long): String?
    fun integratedSelectionFailure(owner: UsbActivationOwner): String?
    fun commitIntegratedSelection(owner: UsbActivationOwner): String?
    fun rollbackIntegratedSelection(owner: UsbActivationOwner): Boolean
    fun releaseIntegratedSelection(owner: UsbActivationOwner, cleanupSafe: Boolean): Boolean
}

/** Used by PlaybackService and routing tests. A null result from a present controller is success. */
internal class UsbOutputSelectionCallbacks(private val controller: () -> UsbOutputSelectionController?) {
    fun authorize(owner: UsbActivationOwner, identity: UsbAudioDeviceIdentity?, generation: Long): String? {
        val current = controller()
        return if (current == null) "DIRECT_SERVICE_UNAVAILABLE" else current.authorizeIntegratedSelection(owner, identity, generation)
    }
    fun failure(owner: UsbActivationOwner): String? {
        val current = controller()
        return if (current == null) "DIRECT_SERVICE_UNAVAILABLE" else current.integratedSelectionFailure(owner)
    }
    fun commit(owner: UsbActivationOwner): String? {
        val current = controller()
        return if (current == null) "DIRECT_SERVICE_UNAVAILABLE" else current.commitIntegratedSelection(owner)
    }
    fun rollback(owner: UsbActivationOwner): Boolean {
        val current = controller()
        return current != null && current.rollbackIntegratedSelection(owner)
    }
    fun release(owner: UsbActivationOwner, cleanupSafe: Boolean): Boolean {
        val current = controller()
        return current != null && current.releaseIntegratedSelection(owner, cleanupSafe)
    }
}

/** Controller-owned reservation state; detachment invalidates authority, never cleanup ownership. */
internal class UsbOutputReservation {
    var owner: UsbActivationOwner? = null; private set
    var token: UsbAudioInspectionToken? = null; private set
    var handedOff: Boolean = false; private set
    fun owns(attempt: UsbActivationOwner) = owner === attempt
    fun acquire(attempt: UsbActivationOwner, attachment: UsbAudioInspectionToken): Boolean {
        if (owner != null) return false
        owner = attempt;token = attachment;handedOff = false
        return true
    }
    fun commit(attempt: UsbActivationOwner): Boolean {
        if (!owns(attempt) || handedOff) return false
        handedOff = true
        return true
    }
    fun rollback(attempt: UsbActivationOwner, workerActive: Boolean): Boolean {
        if (!owns(attempt) || handedOff || workerActive) return false
        clear();return true
    }
    fun release(attempt: UsbActivationOwner, cleanupSafe: Boolean, workerActive: Boolean): Boolean {
        if (!owns(attempt) || !cleanupSafe || workerActive) return false
        clear();return true
    }
    private fun clear() { owner = null;token = null;handedOff = false }
}
