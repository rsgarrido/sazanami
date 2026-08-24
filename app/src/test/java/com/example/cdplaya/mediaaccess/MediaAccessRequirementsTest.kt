package com.example.cdplaya.mediaaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAccessRequirementsTest {
    @Test
    fun api29Through32RequireLegacyReadPermissionForAudioOnly() {
        (29..32).forEach { sdkInt ->
            val requirements = MediaAccessPolicy.requirementsFor(sdkInt)

            assertEquals(
                "API $sdkInt",
                setOf(MediaPermissions.READ_EXTERNAL_STORAGE),
                requirements.requiredAudioPermissions
            )
            assertTrue(requirements.optionalArtworkPermissions.isEmpty())
        }
    }

    @Test
    fun api33Through36RequireGranularAudioWithoutBroadImagePermission() {
        (33..36).forEach { sdkInt ->
            val requirements = MediaAccessPolicy.requirementsFor(sdkInt)

            assertEquals(
                "API $sdkInt",
                setOf(MediaPermissions.READ_MEDIA_AUDIO),
                requirements.requiredAudioPermissions
            )
            assertTrue(requirements.optionalArtworkPermissions.isEmpty())
            assertTrue(MediaPermissions.READ_EXTERNAL_STORAGE !in requirements.requiredAudioPermissions)
        }
    }
}
