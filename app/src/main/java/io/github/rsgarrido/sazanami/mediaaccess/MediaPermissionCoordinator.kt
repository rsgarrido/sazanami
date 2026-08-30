package io.github.rsgarrido.sazanami.mediaaccess

internal enum class MediaPermissionRequest {
    AUDIO
}

internal enum class MediaAccessEffect {
    LOAD_LIBRARY,
    REVOKE_LIBRARY_ACCESS
}

internal class MediaPermissionCoordinator {
    private var lastState: MediaAccessState? = null
    private var activeRequest: MediaPermissionRequest? = null

    fun onStateEvaluated(state: MediaAccessState): List<MediaAccessEffect> {
        val previous = lastState
        lastState = state
        return when {
            state.hasAudioAccess && previous?.hasAudioAccess != true ->
                listOf(MediaAccessEffect.LOAD_LIBRARY)
            !state.hasAudioAccess && previous?.hasAudioAccess == true ->
                listOf(MediaAccessEffect.REVOKE_LIBRARY_ACCESS)
            else -> emptyList()
        }
    }

    fun beginRequest(request: MediaPermissionRequest): Boolean {
        if (activeRequest != null) return false
        activeRequest = request
        return true
    }

    fun finishRequest(request: MediaPermissionRequest) {
        if (activeRequest == request) activeRequest = null
    }
}
