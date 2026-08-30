package io.github.rsgarrido.sazanami.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

open class EmbeddedArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        return when (EmbeddedArtworkContract.parse(uri, expectedAuthority = uri.authority)
            ?.extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Embedded artwork is read-only")
        val providerContext = context ?: throw FileNotFoundException("Provider is unavailable")
        val reference = EmbeddedArtworkContract.parse(
            uri = uri,
            expectedAuthority = "${providerContext.packageName}.embeddedartwork"
        ) ?: throw FileNotFoundException("Malformed embedded artwork URI")
        val artworkFile = resolveArtworkFile(reference)
            ?: throw FileNotFoundException("Embedded artwork is unavailable")
        return ParcelFileDescriptor.open(artworkFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    internal open fun resolveArtworkFile(reference: EmbeddedArtworkReference) =
        EmbeddedArtworkResolver(requireNotNull(context)).resolveReference(reference)

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
}
