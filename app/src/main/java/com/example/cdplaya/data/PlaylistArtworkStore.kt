package com.example.cdplaya.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException

/** Owns durable custom playlist images copied from a one-shot system image picker result. */
class PlaylistArtworkStore(context: Context) {
    private val applicationContext = context.applicationContext ?: context
    private val artworkDirectory = File(applicationContext.filesDir, DIRECTORY_NAME)

    fun importArtwork(playlistId: Long, source: Uri): String {
        val mimeType = applicationContext.contentResolver.getType(source)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw IOException("The selected file is not an image.")
        }

        if (!artworkDirectory.exists() && !artworkDirectory.mkdirs()) {
            throw IOException("Unable to prepare playlist artwork storage.")
        }

        val nonce = System.nanoTime().toString().removePrefix("-")
        val reference = "playlist-$playlistId-$nonce.image"
        val destination = File(artworkDirectory, reference)
        val temporary = File(artworkDirectory, "$reference.tmp")

        try {
            val input = applicationContext.contentResolver.openInputStream(source)
                ?: throw IOException("Unable to open the selected image.")
            input.use { stream ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_ARTWORK_BYTES) {
                            throw IOException("The selected image is too large.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("The selected image could not be read.")
            }
            if (!temporary.renameTo(destination)) {
                throw IOException("Unable to save playlist artwork.")
            }
        } catch (failure: Throwable) {
            temporary.delete()
            destination.delete()
            throw failure
        }

        return reference
    }

    fun delete(reference: String?) {
        fileFor(applicationContext, reference)?.delete()
    }

    companion object {
        private const val DIRECTORY_NAME = "playlist_artwork"
        private const val MAX_ARTWORK_BYTES = 25L * 1024L * 1024L
        private val VALID_REFERENCE = Regex("playlist-[0-9]+-[0-9]+\\.image")

        fun fileFor(context: Context, reference: String?): File? {
            val safeReference = reference?.takeIf { it.matches(VALID_REFERENCE) } ?: return null
            val file = File(File(context.filesDir, DIRECTORY_NAME), safeReference)
            return file.takeIf(File::isFile)
        }
    }
}
