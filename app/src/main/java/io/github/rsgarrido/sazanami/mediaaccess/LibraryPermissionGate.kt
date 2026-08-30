package io.github.rsgarrido.sazanami.mediaaccess

internal class LibraryPermissionGate {
    private var generation = 0L
    private var hasAudioAccess = false

    fun updateAccess(granted: Boolean): Boolean {
        if (hasAudioAccess == granted) return false
        hasAudioAccess = granted
        generation += 1
        return true
    }

    fun tokenOrNull(): Long? = generation.takeIf { hasAudioAccess }

    fun isCurrent(token: Long): Boolean = hasAudioAccess && token == generation
}

