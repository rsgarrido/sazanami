package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.*
import org.junit.Assert.*
import org.junit.Test

/** Production callback adapter and reservation/generation contracts; no Android handles or JNI. */
class UsbOutputSelectionTest {
    private val identity = UsbAudioDeviceIdentity("private-bus", 1, 2, 3)
    private class Controller : UsbOutputSelectionController {
        val reservation = UsbOutputReservation()
        val gate = UsbAudioGenerationGate()
        var authorizationError: String? = null
        var validityError: String? = null
        var permission = true
        var workerActive = false
        override fun authorizeIntegratedSelection(owner: UsbActivationOwner, identity: UsbAudioDeviceIdentity?, generation: Long): String? {
            authorizationError?.let { return it }
            if (!permission) return "DIRECT_USB_PERMISSION_FAILED"
            val token = gate.current ?: return "DIRECT_USB_SELECTION_REQUIRED"
            if (token.identity != identity || token.generation != generation) return "DIRECT_USB_SELECTION_CHANGED"
            return if (reservation.acquire(owner, token)) null else "DIRECT_USB_RESERVATION_HELD"
        }
        override fun integratedSelectionFailure(owner: UsbActivationOwner): String? {
            validityError?.let { return it }
            if (!reservation.owns(owner)) return "DIRECT_USB_RESERVATION_LOST"
            return if (gate.current == reservation.token) null else "DIRECT_USB_SELECTION_CHANGED"
        }
        override fun commitIntegratedSelection(owner: UsbActivationOwner): String? =
            if (reservation.commit(owner)) null else "DIRECT_USB_RESERVATION_LOST"
        override fun rollbackIntegratedSelection(owner: UsbActivationOwner) = reservation.rollback(owner, workerActive)
        override fun releaseIntegratedSelection(owner: UsbActivationOwner, cleanupSafe: Boolean) = reservation.release(owner, cleanupSafe, workerActive)
    }

    @Test fun presentControllerNullAuthorizationPreservesSuccessAndExactInputs() {
        val controller = Controller();val token = controller.gate.select(identity)!!;val owner = UsbActivationOwner(1)
        val callbacks = UsbOutputSelectionCallbacks { controller }
        assertNull(callbacks.authorize(owner, identity, token.generation))
        assertTrue(controller.reservation.owns(owner));assertEquals(token, controller.reservation.token)
    }
    @Test fun presentControllerAuthorizationErrorIsNotReplaced() {
        val controller = Controller().apply { authorizationError = "DIRECT_USB_PERMISSION_FAILED" }
        assertEquals("DIRECT_USB_PERMISSION_FAILED", UsbOutputSelectionCallbacks { controller }.authorize(UsbActivationOwner(1), identity, 1))
        assertNull(controller.reservation.token)
    }
    @Test fun absentControllerAuthorizationReturnsServiceUnavailable() {
        assertEquals("DIRECT_SERVICE_UNAVAILABLE", UsbOutputSelectionCallbacks { null }.authorize(UsbActivationOwner(1), identity, 1))
    }
    @Test fun presentControllerNullValidityRemainsHealthyAfterHandoff() {
        val controller = Controller();val token = controller.gate.select(identity)!!;val owner = UsbActivationOwner(1)
        val callbacks = UsbOutputSelectionCallbacks { controller }
        assertNull(callbacks.authorize(owner, identity, token.generation));assertNull(callbacks.commit(owner))
        assertNull(callbacks.failure(owner));assertTrue(controller.reservation.handedOff)
    }
    @Test fun presentControllerValidityErrorIsNotReplaced() {
        val controller = Controller().apply { validityError = "DIRECT_USB_DEVICE_UNAVAILABLE" }
        assertEquals("DIRECT_USB_DEVICE_UNAVAILABLE", UsbOutputSelectionCallbacks { controller }.failure(UsbActivationOwner(1)))
    }
    @Test fun absentControllerValidityReturnsServiceUnavailable() {
        assertEquals("DIRECT_SERVICE_UNAVAILABLE", UsbOutputSelectionCallbacks { null }.failure(UsbActivationOwner(1)))
    }
    @Test fun onlyAcquiringAttemptCanRollbackAndOldAttemptCannotReleaseANewerReservation() {
        val reservation = UsbOutputReservation();val token = UsbAudioInspectionToken(identity, 1)
        val old = UsbActivationOwner(1);val sameSerialDifferentOwner = UsbActivationOwner(1);val next = UsbActivationOwner(2)
        assertTrue(reservation.acquire(old, token))
        assertFalse(reservation.rollback(sameSerialDifferentOwner, false))
        assertFalse(reservation.rollback(old, workerActive = true))
        assertTrue(reservation.rollback(old, false));assertNull(reservation.token)
        assertTrue(reservation.acquire(next, token.copy(generation = 2)))
        assertFalse(reservation.rollback(old, false));assertFalse(reservation.release(old, true, false))
        assertTrue(reservation.owns(next))
    }
    @Test fun handedOffOrUnsafeWorkerReservationCannotBeAbandoned() {
        val reservation = UsbOutputReservation();val owner = UsbActivationOwner(1)
        reservation.acquire(owner, UsbAudioInspectionToken(identity, 1));assertTrue(reservation.commit(owner))
        assertFalse(reservation.rollback(owner, false))
        assertFalse(reservation.release(owner, cleanupSafe = false, workerActive = false))
        assertFalse(reservation.release(owner, cleanupSafe = true, workerActive = true))
        assertTrue(reservation.owns(owner))
        assertTrue(reservation.release(owner, cleanupSafe = true, workerActive = false))
    }
    @Test fun rejectedActivationThenDetachReconnectCanReserveWithoutRestartAndOldPermissionIsRejected() {
        val controller = Controller();val callbacks = UsbOutputSelectionCallbacks { controller }
        val first = controller.gate.select(identity)!!;val oldRequest = UsbAudioPermissionRequest("old", first)
        val owner = UsbActivationOwner(1)
        assertNull(callbacks.authorize(owner, identity, first.generation))
        // A failure before handoff abandons only its own reservation, even after detach invalidation.
        controller.gate.select(null);assertTrue(callbacks.rollback(owner))
        val reattached = controller.gate.select(identity)!! // Same IDs, genuinely new generation.
        assertFalse(oldRequest.matches("old", identity, controller.gate))
        val next = UsbActivationOwner(2)
        assertEquals("DIRECT_USB_SELECTION_CHANGED", callbacks.authorize(next, identity, first.generation))
        assertNull(controller.reservation.token)
        assertNull(callbacks.authorize(next, identity, reattached.generation))
        assertNull(callbacks.failure(next))
    }
    @Test fun permissionDenialThenCurrentGrantCanAuthorizeButStaleGrantCannot() {
        val controller = Controller();val callbacks = UsbOutputSelectionCallbacks { controller }
        val token = controller.gate.select(identity)!!;val request = UsbAudioPermissionRequest("current", token)
        controller.permission = false
        assertEquals("DIRECT_USB_PERMISSION_FAILED", callbacks.authorize(UsbActivationOwner(1), identity, token.generation))
        assertNull(controller.reservation.token)
        assertFalse(request.matches("stale", identity, controller.gate))
        assertTrue(request.matches("current", identity, controller.gate))
        controller.permission = true // Synthetic current Android grant; platform handling stays in the controller.
        assertNull(callbacks.authorize(UsbActivationOwner(2), identity, token.generation))
    }
    @Test fun activationReportKeepsFailureAndRecoveryDistinctWithoutPrivateInputs() {
        val evidence = UsbActivationEvidence(2, stage = UsbActivationStage.RESTORE_ANDROID,
            failureStage = UsbActivationStage.SELECT_DIRECT, failure = "DIRECT_USB_SELECTION_CHANGED",
            output = UsbActivationOutput.ANDROID, reservation = UsbActivationReservation.RELEASED,
            restoration = UsbActivationRestoration.COMPLETED)
        val report = formatUsbActivationEvidence(evidence)
        for (value in listOf("attempt=2", "SELECT_DIRECT", "DIRECT_USB_SELECTION_CHANGED", "ANDROID", "RELEASED", "COMPLETED")) assertTrue(value, report.contains(value))
        assertFalse(report.contains(identity.deviceName));assertFalse(report.contains("content://"));assertTrue(report.lines().size <= 7)
    }
}
