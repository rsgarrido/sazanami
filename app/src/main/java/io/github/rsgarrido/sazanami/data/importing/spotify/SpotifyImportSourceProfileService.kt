package io.github.rsgarrido.sazanami.data.importing.spotify

import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.local.ListeningImportSourceEntity
import io.github.rsgarrido.sazanami.data.local.ListeningSource

/** Supplies the locally stable unscoped Spotify profile used when an export has no account ID. */
class SpotifyImportSourceProfileService(
    private val repository: ListeningImportRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun getOrCreateDefault(): ListeningImportSourceEntity {
        val now = nowMillis()
        return repository.getOrCreateSourceProfile(
            ListeningImportSourceEntity(
                stableUuid = DEFAULT_STABLE_UUID,
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                displayLabel = DEFAULT_DISPLAY_LABEL,
                accountIdentityDigest = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    companion object {
        const val DEFAULT_STABLE_UUID = "spotify-default-profile-v1"
        const val DEFAULT_DISPLAY_LABEL = "Spotify"
    }
}
