package com.example.cdplaya.mediaaccess

internal object MediaPermissions {
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES" // Legacy policy constant; no longer requested.
}

internal data class MediaAccessRequirements(
    val requiredAudioPermissions: Set<String>,
    val optionalArtworkPermissions: Set<String>
)

internal enum class PermissionAccess {
    GRANTED,
    REQUESTABLE,
    DENIED,
    PERMANENTLY_DENIED,
    NOT_REQUIRED
}

internal data class MediaAccessState(
    val requirements: MediaAccessRequirements,
    val audioAccess: PermissionAccess,
    val artworkAccess: PermissionAccess,
    val audioPermissionRequested: Boolean,
    val artworkPermissionRequested: Boolean
) {
    val hasAudioAccess: Boolean
        get() = audioAccess == PermissionAccess.GRANTED ||
                audioAccess == PermissionAccess.NOT_REQUIRED

    val hasArtworkAccess: Boolean
        get() = artworkAccess == PermissionAccess.GRANTED ||
                artworkAccess == PermissionAccess.NOT_REQUIRED
}

internal object MediaAccessPolicy {
    private const val ANDROID_13_API = 33
    private const val RUNTIME_PERMISSIONS_API = 23

    fun requirementsFor(
        sdkInt: Int,
        queriesStandaloneArtwork: Boolean = false
    ): MediaAccessRequirements = when {
        sdkInt >= ANDROID_13_API -> MediaAccessRequirements(
            requiredAudioPermissions = setOf(MediaPermissions.READ_MEDIA_AUDIO),
            optionalArtworkPermissions = emptySet()
        )
        sdkInt >= RUNTIME_PERMISSIONS_API -> MediaAccessRequirements(
            requiredAudioPermissions = setOf(MediaPermissions.READ_EXTERNAL_STORAGE),
            optionalArtworkPermissions = emptySet()
        )
        else -> MediaAccessRequirements(
            requiredAudioPermissions = emptySet(),
            optionalArtworkPermissions = emptySet()
        )
    }

    fun evaluate(
        sdkInt: Int,
        grantedPermissions: Set<String>,
        requestedPermissions: Set<String>,
        permissionsWithRationale: Set<String>,
        permanentlyDeniedPermissions: Set<String> = emptySet(),
        queriesStandaloneArtwork: Boolean = false
    ): MediaAccessState {
        val requirements = requirementsFor(sdkInt, queriesStandaloneArtwork)
        val audioAccess = accessFor(
            requiredPermissions = requirements.requiredAudioPermissions,
            grantedPermissions = grantedPermissions,
            requestedPermissions = requestedPermissions,
            permissionsWithRationale = permissionsWithRationale,
            permanentlyDeniedPermissions = permanentlyDeniedPermissions
        )
        val artworkAccess = if (
            requirements.optionalArtworkPermissions ==
            requirements.requiredAudioPermissions
        ) {
            audioAccess
        } else {
            accessFor(
                requiredPermissions = requirements.optionalArtworkPermissions,
                grantedPermissions = grantedPermissions,
                requestedPermissions = requestedPermissions,
                permissionsWithRationale = permissionsWithRationale,
                permanentlyDeniedPermissions = permanentlyDeniedPermissions
            )
        }
        return MediaAccessState(
            requirements = requirements,
            audioAccess = audioAccess,
            artworkAccess = artworkAccess,
            audioPermissionRequested =
                requirements.requiredAudioPermissions.any(requestedPermissions::contains),
            artworkPermissionRequested =
                requirements.optionalArtworkPermissions.any(requestedPermissions::contains)
        )
    }

    private fun accessFor(
        requiredPermissions: Set<String>,
        grantedPermissions: Set<String>,
        requestedPermissions: Set<String>,
        permissionsWithRationale: Set<String>,
        permanentlyDeniedPermissions: Set<String>
    ): PermissionAccess {
        if (requiredPermissions.isEmpty()) return PermissionAccess.NOT_REQUIRED
        if (requiredPermissions.all(grantedPermissions::contains)) {
            return PermissionAccess.GRANTED
        }
        if (requiredPermissions.none(requestedPermissions::contains)) {
            return PermissionAccess.REQUESTABLE
        }
        if (requiredPermissions.any(permissionsWithRationale::contains)) {
            return PermissionAccess.DENIED
        }
        if (requiredPermissions.any(permanentlyDeniedPermissions::contains)) {
            return PermissionAccess.PERMANENTLY_DENIED
        }
        return PermissionAccess.DENIED
    }
}
