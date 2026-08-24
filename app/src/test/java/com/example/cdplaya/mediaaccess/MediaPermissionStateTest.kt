package com.example.cdplaya.mediaaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionStateTest {
    @Test
    fun initialAudioPermissionIsRequestable() {
        val state = evaluate()
        assertEquals(PermissionAccess.REQUESTABLE, state.audioAccess)
        assertFalse(state.audioPermissionRequested)
    }

    @Test
    fun denialWithRationaleCanBeRequestedAgain() {
        val state = evaluate(
            requested = setOf(MediaPermissions.READ_MEDIA_AUDIO),
            rationale = setOf(MediaPermissions.READ_MEDIA_AUDIO)
        )
        assertEquals(PermissionAccess.DENIED, state.audioAccess)
        assertTrue(state.audioPermissionRequested)
    }

    @Test
    fun denialWithoutRationaleAfterRequestIsPermanent() {
        val state = evaluate(
            requested = setOf(MediaPermissions.READ_MEDIA_AUDIO),
            permanentlyDenied = setOf(MediaPermissions.READ_MEDIA_AUDIO)
        )
        assertEquals(PermissionAccess.PERMANENTLY_DENIED, state.audioAccess)
    }

    @Test
    fun artworkPermissionIsNotRequiredWhenAudioIsGranted() {
        val state = evaluate(granted = setOf(MediaPermissions.READ_MEDIA_AUDIO))
        assertTrue(state.hasAudioAccess)
        assertTrue(state.hasArtworkAccess)
        assertEquals(PermissionAccess.NOT_REQUIRED, state.artworkAccess)
    }

    private fun evaluate(
        granted: Set<String> = emptySet(),
        requested: Set<String> = emptySet(),
        rationale: Set<String> = emptySet(),
        permanentlyDenied: Set<String> = emptySet()
    ) = MediaAccessPolicy.evaluate(
        sdkInt = 33,
        grantedPermissions = granted,
        requestedPermissions = requested,
        permissionsWithRationale = rationale,
        permanentlyDeniedPermissions = permanentlyDenied
    )
}
