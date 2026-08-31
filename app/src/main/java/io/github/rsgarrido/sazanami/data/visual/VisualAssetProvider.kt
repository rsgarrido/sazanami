package io.github.rsgarrido.sazanami.data.visual

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.rsgarrido.sazanami.data.PlaylistArtworkStore
import java.io.FileNotFoundException

/** Read-only bridge that lets Android Auto load Sazanami's app-owned artwork. */
class VisualAssetProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        parse(uri)?.let { "image/*" }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Visual assets are read-only")
        val providerContext = context ?: throw FileNotFoundException("Provider is unavailable")
        val reference = parse(uri) ?: throw FileNotFoundException("Malformed visual asset URI")
        val expectedAuthority = "${providerContext.packageName}.$AUTHORITY_SUFFIX"
        if (uri.authority != expectedAuthority) {
            throw FileNotFoundException("Unexpected visual asset authority")
        }

        val file = when (reference.ownerType) {
            VisualAssetOwnerType.PLAYLIST_IMAGE -> {
                val playlistId = reference.ownerKey.toLongOrNull()
                    ?: throw FileNotFoundException("Invalid playlist artwork owner")
                PlaylistArtworkStore.fileFor(
                    context = providerContext,
                    playlistId = playlistId,
                    reference = reference.revision,
                    variant = reference.variant
                )
            }
            else -> VisualAssetStore(providerContext).file(
                ownerType = reference.ownerType,
                ownerKey = reference.ownerKey,
                reference = reference.revision,
                variant = reference.variant
            )
        } ?: throw FileNotFoundException("Visual asset is unavailable")

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun parse(uri: Uri): VisualAssetUriReference? {
        val parts = uri.pathSegments
        if (parts.size != 4) return null
        val ownerType = VisualAssetOwnerType.entries.firstOrNull { owner ->
            owner.cacheNamespace == parts[0]
        } ?: return null
        val variant = VisualAssetVariant.entries.firstOrNull { candidate ->
            candidate.name.equals(parts[3], ignoreCase = true)
        } ?: return null
        return VisualAssetUriReference(
            ownerType = ownerType,
            ownerKey = parts[1],
            revision = parts[2],
            variant = variant
        )
    }

    private data class VisualAssetUriReference(
        val ownerType: VisualAssetOwnerType,
        val ownerKey: String,
        val revision: String,
        val variant: VisualAssetVariant
    )

    companion object {
        private const val AUTHORITY_SUFFIX = "visualassets"

        fun uriFor(
            packageName: String,
            identity: VisualAssetIdentity,
            variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL
        ): Uri = Uri.Builder()
            .scheme("content")
            .authority("$packageName.$AUTHORITY_SUFFIX")
            .appendPath(identity.ownerType.cacheNamespace)
            .appendPath(identity.ownerKey)
            .appendPath(identity.revision)
            .appendPath(variant.name.lowercase())
            .build()
    }
}
