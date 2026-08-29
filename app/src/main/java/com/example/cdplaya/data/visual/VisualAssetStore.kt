package com.example.cdplaya.data.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException

data class ImportedVisualAsset(
    val identity: VisualAssetIdentity,
    val reference: String,
    val thumbnailFile: File,
    val displayFile: File
)

/** App-owned visual asset variants. Call disk/bitmap operations from a background dispatcher. */
class VisualAssetStore(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val root = File(appContext.filesDir, ROOT_DIRECTORY)

    fun import(
        ownerType: VisualAssetOwnerType,
        ownerKey: String,
        source: Uri,
        reference: String = newReference(ownerType, ownerKey)
    ): ImportedVisualAsset {
        require(ownerType != VisualAssetOwnerType.PLAYLIST_COLLAGE)
        require(SAFE_KEY.matches(ownerKey))
        val mimeType = appContext.contentResolver.getType(source)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw IOException("The selected file is not an image.")
        }
        val ownerDirectory = ownerDirectory(ownerType, ownerKey)
        if (!ownerDirectory.exists() && !ownerDirectory.mkdirs()) {
            throw IOException("Unable to prepare visual asset storage.")
        }
        val finalDirectory = File(ownerDirectory, reference)
        val stagingDirectory = File(ownerDirectory, ".$reference-${System.nanoTime()}.tmp")
        if (!stagingDirectory.mkdirs()) throw IOException("Unable to stage the selected image.")
        val sourceFile = File(stagingDirectory, "source")
        try {
            copySource(source, sourceFile)
            val orientation = readExifOrientation(sourceFile)
            VisualAssetVariant.entries.forEach { variant ->
                val bitmap = decodeBounded(sourceFile, variant.maximumDimensionPx, orientation)
                try {
                    writeWebp(bitmap, File(stagingDirectory, variant.fileName))
                } finally {
                    bitmap.recycle()
                }
            }
            sourceFile.delete()
            if (finalDirectory.exists() || !stagingDirectory.renameTo(finalDirectory)) {
                throw IOException("Unable to publish the selected image.")
            }
            val identity = VisualAssetIdentity(ownerType, ownerKey, reference)
            return ImportedVisualAsset(
                identity = identity,
                reference = reference,
                thumbnailFile = File(finalDirectory, VisualAssetVariant.THUMBNAIL.fileName),
                displayFile = File(finalDirectory, VisualAssetVariant.DISPLAY.fileName)
            )
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            throw failure
        }
    }

    fun file(
        ownerType: VisualAssetOwnerType,
        ownerKey: String,
        reference: String,
        variant: VisualAssetVariant
    ): File? = safeAssetDirectory(ownerType, ownerKey, reference)
        ?.resolve(variant.fileName)
        ?.takeIf(File::isFile)

    fun delete(ownerType: VisualAssetOwnerType, ownerKey: String, reference: String?) {
        reference ?: return
        safeAssetDirectory(ownerType, ownerKey, reference)?.deleteRecursively()
        ownerDirectory(ownerType, ownerKey).takeIf { it.isDirectory && it.list().isNullOrEmpty() }
            ?.delete()
    }

    private fun copySource(source: Uri, destination: File) {
        val input = appContext.contentResolver.openInputStream(source)
            ?: throw IOException("Unable to open the selected image.")
        input.use { stream ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SOURCE_BYTES) throw IOException("The selected image is too large.")
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun decodeBounded(source: File, maxDimension: Int, orientation: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("The selected image could not be read.")
        }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IOException("The selected image could not be decoded.")
        val oriented = applyOrientation(decoded, orientation)
        val target = boundedVisualAssetSize(oriented.width, oriented.height, maxDimension)
        if (target.width == oriented.width && target.height == oriented.height) return oriented
        val scaled = Bitmap.createScaledBitmap(oriented, target.width, target.height, true)
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    private fun readExifOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun writeWebp(bitmap: Bitmap, destination: File) {
        val format = if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        val written = destination.outputStream().buffered().use { output ->
            bitmap.compress(format, WEBP_QUALITY, output)
        }
        if (!written) throw IOException("Unable to encode the selected image.")
    }

    private fun safeAssetDirectory(
        ownerType: VisualAssetOwnerType,
        ownerKey: String,
        reference: String
    ): File? {
        if (!SAFE_KEY.matches(ownerKey) || !SAFE_REFERENCE.matches(reference)) return null
        return File(ownerDirectory(ownerType, ownerKey), reference)
    }

    private fun ownerDirectory(ownerType: VisualAssetOwnerType, ownerKey: String): File =
        File(File(root, ownerType.cacheNamespace), ownerKey)

    companion object {
        private const val ROOT_DIRECTORY = "visual_assets"
        private const val MAX_SOURCE_BYTES = 25L * 1024L * 1024L
        private const val WEBP_QUALITY = 88
        private val SAFE_KEY = Regex("[A-Za-z0-9_-]+")
        private val SAFE_REFERENCE = Regex("[A-Za-z0-9_.-]+")

        fun newReference(ownerType: VisualAssetOwnerType, ownerKey: String): String =
            "${ownerType.cacheNamespace}-$ownerKey-${System.nanoTime().toString().removePrefix("-")}.image"
    }
}
