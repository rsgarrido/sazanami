package io.github.rsgarrido.sazanami.mediaaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionCoordinatorTest {
    @Test
    fun oneLibraryLoadIsEmittedForGrantAcrossRepeatedEvaluation() {
        val coordinator = MediaPermissionCoordinator()
        assertEquals(emptyList<MediaAccessEffect>(), coordinator.onStateEvaluated(deniedState()))
        assertEquals(
            listOf(MediaAccessEffect.LOAD_LIBRARY),
            coordinator.onStateEvaluated(grantedState())
        )
        assertEquals(emptyList<MediaAccessEffect>(), coordinator.onStateEvaluated(grantedState()))
    }

    @Test
    fun revocationAndSettingsGrantEmitDeterministicEffects() {
        val coordinator = MediaPermissionCoordinator()
        coordinator.onStateEvaluated(grantedState())
        assertEquals(
            listOf(MediaAccessEffect.REVOKE_LIBRARY_ACCESS),
            coordinator.onStateEvaluated(deniedState())
        )
        assertEquals(
            listOf(MediaAccessEffect.LOAD_LIBRARY),
            coordinator.onStateEvaluated(grantedState())
        )
    }

    @Test
    fun concurrentAudioRequestsAreRejected() {
        val coordinator = MediaPermissionCoordinator()
        assertTrue(coordinator.beginRequest(MediaPermissionRequest.AUDIO))
        assertFalse(coordinator.beginRequest(MediaPermissionRequest.AUDIO))
        coordinator.finishRequest(MediaPermissionRequest.AUDIO)
        assertTrue(coordinator.beginRequest(MediaPermissionRequest.AUDIO))
    }

    private fun grantedState() = MediaAccessPolicy.evaluate(
        sdkInt = 33,
        grantedPermissions = setOf(MediaPermissions.READ_MEDIA_AUDIO),
        requestedPermissions = setOf(MediaPermissions.READ_MEDIA_AUDIO),
        permissionsWithRationale = emptySet()
    )

    private fun deniedState() = MediaAccessPolicy.evaluate(
        sdkInt = 33,
        grantedPermissions = emptySet(),
        requestedPermissions = emptySet(),
        permissionsWithRationale = emptySet()
    )
}
