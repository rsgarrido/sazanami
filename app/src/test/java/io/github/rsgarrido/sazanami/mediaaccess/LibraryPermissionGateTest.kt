package io.github.rsgarrido.sazanami.mediaaccess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPermissionGateTest {
    @Test
    fun protectedScanCannotStartWithoutAudioAccess() {
        assertNull(LibraryPermissionGate().tokenOrNull())
    }

    @Test
    fun revocationInvalidatesAnInFlightScanToken() {
        val gate = LibraryPermissionGate()
        gate.updateAccess(true)
        val token = requireNotNull(gate.tokenOrNull())

        gate.updateAccess(false)

        assertFalse(gate.isCurrent(token))
    }

    @Test
    fun restoredAccessGetsANewGeneration() {
        val gate = LibraryPermissionGate()
        gate.updateAccess(true)
        val staleToken = requireNotNull(gate.tokenOrNull())
        gate.updateAccess(false)
        gate.updateAccess(true)
        val restoredToken = requireNotNull(gate.tokenOrNull())

        assertFalse(gate.isCurrent(staleToken))
        assertTrue(gate.isCurrent(restoredToken))
    }
}

