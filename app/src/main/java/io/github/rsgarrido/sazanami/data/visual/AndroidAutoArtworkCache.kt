package io.github.rsgarrido.sazanami.data.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Bridges artwork that is readable only through Sazanami's persisted URI grants to external media
 * controllers such as Android Auto.
 *
 * Catalog construction registers an opaque app-private mapping only. The image is decoded and
 * materialized lazily when the exported visual-asset provider is actually asked to open it, so a
 * large library does not have to pre-render every folder cover during Android Auto startup.
 */
class AndroidAutoArtworkCache(context: Context) {
    private val appContext = context.applicationContext ?: context

    fun externallyReadableUri(source: Uri?): Uri? {
        source ?: return null
        if (isAppOwnedArtworkUri(source, appContext.packageName)) return source
        if (source.scheme != "content") return null

        val mimeType = runCatching { appContext.contentResolver.getType(source) }.getOrNull()
        if (mimeType != null && !mimeType.startsWith("image/")) return null

        val cacheKey = cacheKey(source.toString())
        return synchronized(CACHE_LOCK) {
            if (!registerSource(appContext, cacheKey, source)) return@synchronized null
            providerUri(appContext.packageName, cacheKey)
        }
    }

    companion object {
        private const val CACHE_DIRECTORY = "android_auto_artwork"
        private const val SOURCE_DIRECTORY = "android_auto_artwork_sources"
        private const val PROVIDER_PATH = "library-artwork"
        private const val MAX_SOURCE_BYTES = 25L * 1024L * 1024L
        private const val MAX_SOURCE_REFERENCE_BYTES = 16L * 1024L
        private const val MAXIMUM_DIMENSION_PX = 384
        private const val WEBP_QUALITY = 88
        private val CACHE_KEY = Regex("[0-9a-f]{64}")
        private val CACHE_LOCK = Any()

        internal fun providerUri(packageName: String, cacheKey: String): Uri {
            require(CACHE_KEY.matches(cacheKey))
            return Uri.Builder()
                .scheme("content")
                .authority("$packageName.visualassets")
                .appendPath(PROVIDER_PATH)
                .appendPath("$cacheKey.webp")
                .build()
        }

        internal fun resolveProviderFile(context: Context, uri: Uri): File? {
            val cacheKey = providerCacheKey(context.packageName, uri) ?: return null
            return synchronized(CACHE_LOCK) {
                val source = registeredSource(context, cacheKey) ?: return@synchronized null
                val target = cacheFile(context, cacheKey)
                val currentMetadata = readSourceMetadata(context, source)
                val cachedMetadata = readCachedMetadata(context, cacheKey)
                if (
                    target.isFile &&
                    target.length() > 0L &&
                    cachedMetadata != null &&
                    cachedMetadata == currentMetadata
                ) {
                    target.setLastModified(System.currentTimeMillis())
                    return@synchronized target
                }

                if (!materialize(context, source, target)) return@synchronized null
                writeCachedMetadata(context, cacheKey, currentMetadata)
                target.takeIf { it.isFile && it.length() > 0L }
            }
        }

        internal fun isProviderUri(packageName: String, uri: Uri): Boolean =
            providerCacheKey(packageName, uri) != null

        internal fun cacheKey(source: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }

        private fun registerSource(context: Context, cacheKey: String, source: Uri): Boolean {
            val directory = sourceDirectory(context)
            if (!directory.exists() && !directory.mkdirs()) return false
            val target = sourceFile(directory, cacheKey)
            val sourceText = source.toString()
            if (target.isFile && runCatching { target.readText() }.getOrNull() == sourceText) {
                target.setLastModified(System.currentTimeMillis())
                return true
            }

            val temporary = File(directory, ".$cacheKey-${System.nanoTime()}.tmp")
            return try {
                temporary.writeText(sourceText)
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                }
                target.isFile
            } catch (_: IOException) {
                false
            } finally {
                temporary.delete()
            }
        }

        private fun registeredSource(context: Context, cacheKey: String): Uri? {
            val sourceFile = sourceFile(sourceDirectory(context), cacheKey)
            if (!sourceFile.isFile || sourceFile.length() > MAX_SOURCE_REFERENCE_BYTES) return null
            val raw = runCatching { sourceFile.readText() }.getOrNull()?.trim().orEmpty()
            if (raw.isBlank()) return null
            val source = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
            if (source.scheme != "content" || cacheKey(source.toString()) != cacheKey) return null
            return source
        }

        private fun materialize(context: Context, source: Uri, target: File): Boolean {
            val cacheDirectory = cacheDirectory(context)
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return false
            val sourceFile = File(cacheDirectory, ".${target.name}-${System.nanoTime()}.source.tmp")
            val encodedFile = File(cacheDirectory, ".${target.name}-${System.nanoTime()}.webp.tmp")
            return try {
                copySource(context, source, sourceFile)
                val bitmap = decodeBounded(sourceFile, MAXIMUM_DIMENSION_PX)
                try {
                    writeWebp(bitmap, encodedFile)
                } finally {
                    bitmap.recycle()
                }
                if (!encodedFile.renameTo(target)) {
                    encodedFile.copyTo(target, overwrite = true)
                }
                target.isFile && target.length() > 0L
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            } finally {
                sourceFile.delete()
                encodedFile.delete()
            }
        }

        private fun copySource(context: Context, source: Uri, destination: File) {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("Unable to open artwork source")
            input.use { stream ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_SOURCE_BYTES) {
                            throw IOException("Artwork source exceeds cache size limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }

        private fun decodeBounded(source: File, maximumDimensionPx: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Artwork source could not be decoded")
            }

            var sample = 1
            while (
                maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maximumDimensionPx * 2
            ) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: throw IOException("Artwork source could not be decoded")

            val target = boundedVisualAssetSize(
                sourceWidth = decoded.width,
                sourceHeight = decoded.height,
                maximumDimensionPx = maximumDimensionPx
            )
            if (target.width == decoded.width && target.height == decoded.height) return decoded

            val scaled = Bitmap.createScaledBitmap(decoded, target.width, target.height, true)
            decoded.recycle()
            return scaled
        }

        private fun writeWebp(bitmap: Bitmap, destination: File) {
            val format = if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val written = destination.outputStream().buffered().use { output ->
                bitmap.compress(format, WEBP_QUALITY, output)
            }
            if (!written) throw IOException("Unable to encode Android Auto artwork")
        }

        private fun readSourceMetadata(context: Context, source: Uri): SourceMetadata {
            var sizeBytes: Long? = null
            var lastModifiedMillis: Long? = null

            fun read(projection: Array<String>) {
                context.contentResolver.query(source, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { sizeBytes = cursor.getLong(it) }
                    cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { lastModifiedMillis = cursor.getLong(it) }
                }
            }

            runCatching {
                read(arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED))
            }.recoverCatching {
                read(arrayOf(OpenableColumns.SIZE))
            }

            if (sizeBytes == null) {
                sizeBytes = runCatching {
                    context.contentResolver.openAssetFileDescriptor(source, "r")?.use { descriptor ->
                        descriptor.length.takeIf { it >= 0L }
                    }
                }.getOrNull()
            }
            return SourceMetadata(sizeBytes, lastModifiedMillis)
        }

        private fun readCachedMetadata(context: Context, cacheKey: String): SourceMetadata? {
            val file = metadataFile(context, cacheKey)
            if (!file.isFile) return null
            val lines = runCatching { file.readLines() }.getOrNull() ?: return null
            if (lines.size != 2) return null
            return SourceMetadata(
                sizeBytes = lines[0].toLongOrNull()?.takeIf { it >= 0L },
                lastModifiedMillis = lines[1].toLongOrNull()?.takeIf { it >= 0L }
            )
        }

        private fun writeCachedMetadata(
            context: Context,
            cacheKey: String,
            metadata: SourceMetadata
        ) {
            val file = metadataFile(context, cacheKey)
            runCatching {
                file.writeText(
                    "${metadata.sizeBytes ?: -1L}\n${metadata.lastModifiedMillis ?: -1L}"
                )
            }
        }

        private fun providerCacheKey(packageName: String, uri: Uri): String? {
            if (uri.scheme != "content" || uri.authority != "$packageName.visualassets") return null
            val segments = uri.pathSegments
            if (segments.size != 2 || segments[0] != PROVIDER_PATH) return null
            val fileName = segments[1]
            if (!fileName.endsWith(".webp")) return null
            return fileName.removeSuffix(".webp").takeIf(CACHE_KEY::matches)
        }

        private fun isAppOwnedArtworkUri(uri: Uri, packageName: String): Boolean {
            if (uri.scheme != "content") return false
            return uri.authority == "$packageName.embeddedartwork" ||
                    uri.authority == "$packageName.visualassets"
        }

        private fun cacheDirectory(context: Context): File =
            File(context.cacheDir, CACHE_DIRECTORY)

        private fun sourceDirectory(context: Context): File =
            File(context.filesDir, SOURCE_DIRECTORY)

        private fun cacheFile(context: Context, cacheKey: String): File =
            File(cacheDirectory(context), "$cacheKey.webp")

        private fun metadataFile(context: Context, cacheKey: String): File =
            File(cacheDirectory(context), "$cacheKey.meta")

        private fun sourceFile(directory: File, cacheKey: String): File =
            File(directory, "$cacheKey.source")

        private data class SourceMetadata(
            val sizeBytes: Long?,
            val lastModifiedMillis: Long?
        )
    }
}
