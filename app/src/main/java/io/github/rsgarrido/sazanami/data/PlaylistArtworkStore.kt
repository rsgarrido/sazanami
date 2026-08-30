package io.github.rsgarrido.sazanami.data

import android.content.Context
import android.net.Uri
import io.github.rsgarrido.sazanami.data.visual.VisualAssetIdentity
import io.github.rsgarrido.sazanami.data.visual.VisualAssetOwnerType
import io.github.rsgarrido.sazanami.data.visual.VisualAssetStore
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import java.io.File

/** Playlist compatibility facade over the shared app-owned visual asset store. */
class PlaylistArtworkStore(context: Context) {
    private val applicationContext = context.applicationContext ?: context
    private val visualAssets = VisualAssetStore(applicationContext)

    fun importArtwork(playlistId: Long, source: Uri): String =
        visualAssets.import(
            ownerType = VisualAssetOwnerType.PLAYLIST_IMAGE,
            ownerKey = playlistId.toString(),
            source = source
        ).reference

    fun delete(playlistId: Long, reference: String?) {
        reference ?: return
        visualAssets.delete(
            ownerType = VisualAssetOwnerType.PLAYLIST_IMAGE,
            ownerKey = playlistId.toString(),
            reference = reference
        )
        legacyFileFor(applicationContext, reference)?.delete()
    }

    companion object {
        private const val LEGACY_DIRECTORY_NAME = "playlist_artwork"
        private val LEGACY_REFERENCE = Regex("playlist-[0-9]+-[0-9]+\\.image")

        fun identity(playlistId: Long, reference: String?): VisualAssetIdentity? =
            reference?.takeIf(String::isNotBlank)?.let { revision ->
                VisualAssetIdentity(
                    ownerType = VisualAssetOwnerType.PLAYLIST_IMAGE,
                    ownerKey = playlistId.toString(),
                    revision = revision
                )
            }

        fun fileFor(
            context: Context,
            playlistId: Long,
            reference: String?,
            variant: VisualAssetVariant
        ): File? {
            reference ?: return null
            return VisualAssetStore(context).file(
                ownerType = VisualAssetOwnerType.PLAYLIST_IMAGE,
                ownerKey = playlistId.toString(),
                reference = reference,
                variant = variant
            ) ?: legacyFileFor(context, reference)
        }

        /** Legacy reference reader retained so existing databases and Auto Backup restores work. */
        fun fileFor(context: Context, reference: String?): File? {
            return legacyFileFor(context, reference)
        }

        private fun legacyFileFor(context: Context, reference: String?): File? {
            val safeReference = reference?.takeIf { it.matches(LEGACY_REFERENCE) } ?: return null
            return File(File(context.filesDir, LEGACY_DIRECTORY_NAME), safeReference)
                .takeIf(File::isFile)
        }
    }
}
