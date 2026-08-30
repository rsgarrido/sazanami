package io.github.rsgarrido.sazanami.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.security.MessageDigest

internal const val CURRENT_ARTWORK_ENRICHMENT_VERSION = 2

internal data class EmbeddedArtworkSource(
    val uri: Uri,
    val displayName: String,
    val dateModifiedEpochSeconds: Long,
    val fileSizeBytes: Long
) {
    val cacheKey: String
        get() = listOf(
            uri.toString(),
            dateModifiedEpochSeconds.toString(),
            fileSizeBytes.toString()
        ).joinToString("|").sha256()
}

internal data class EmbeddedArtworkReference(
    val source: EmbeddedArtworkSource,
    val artworkHash: String,
    val extension: String
) {
    val fileName: String get() = "${source.cacheKey}-$artworkHash.$extension"
}

internal object EmbeddedArtworkContract {
    private const val VERSION_PATH = "v2"
    private const val SOURCE_QUERY = "source"
    private const val DISPLAY_NAME_QUERY = "name"
    private const val MODIFIED_QUERY = "modified"
    private const val SIZE_QUERY = "size"
    private const val PROVIDER_SUFFIX = ".embeddedartwork"

    fun sourceFor(song: Song): EmbeddedArtworkSource? {
        val sourceUri = song.uri
        if (sourceUri.scheme != "content" || sourceUri.authority != "media") return null
        return EmbeddedArtworkSource(
            uri = sourceUri,
            displayName = song.displayName.ifBlank {
                song.filePath.substringAfterLast('/', song.filePath.substringAfterLast('\\'))
            },
            dateModifiedEpochSeconds = song.dateModifiedEpochSeconds,
            fileSizeBytes = song.fileSizeBytes
        )
    }

    fun buildUri(
        packageName: String,
        reference: EmbeddedArtworkReference
    ): Uri {
        return Uri.Builder()
            .scheme("content")
            .authority("$packageName$PROVIDER_SUFFIX")
            .appendPath(VERSION_PATH)
            .appendPath(reference.source.cacheKey)
            .appendPath("${reference.artworkHash}.${reference.extension}")
            .appendQueryParameter(SOURCE_QUERY, reference.source.uri.toString())
            .appendQueryParameter(DISPLAY_NAME_QUERY, reference.source.displayName)
            .appendQueryParameter(
                MODIFIED_QUERY,
                reference.source.dateModifiedEpochSeconds.toString()
            )
            .appendQueryParameter(SIZE_QUERY, reference.source.fileSizeBytes.toString())
            .build()
    }

    fun parse(uri: Uri, expectedAuthority: String? = null): EmbeddedArtworkReference? {
        return runCatching {
            val authority = uri.authority ?: return null
            if (uri.scheme != "content" || !authority.endsWith(PROVIDER_SUFFIX)) return null
            if (expectedAuthority != null && authority != expectedAuthority) return null

            val segments = uri.pathSegments
            if (segments.size != 3 || segments[0] != VERSION_PATH) return null
            val sourceUri = Uri.parse(uri.getQueryParameter(SOURCE_QUERY) ?: return null)
            if (sourceUri.scheme != "content" || sourceUri.authority != "media") return null
            val modified = uri.getQueryParameter(MODIFIED_QUERY)?.toLongOrNull() ?: return null
            val size = uri.getQueryParameter(SIZE_QUERY)?.toLongOrNull() ?: return null
            val artworkName = segments[2]
            val artworkHash = artworkName.substringBeforeLast('.', missingDelimiterValue = "")
            val extension = artworkName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            if (!artworkHash.isSha256() || extension !in ARTWORK_EXTENSIONS) return null

            val source = EmbeddedArtworkSource(
                uri = sourceUri,
                displayName = uri.getQueryParameter(DISPLAY_NAME_QUERY).orEmpty(),
                dateModifiedEpochSeconds = modified,
                fileSizeBytes = size
            )
            if (segments[1] != source.cacheKey) return null
            EmbeddedArtworkReference(source, artworkHash, extension)
        }.getOrNull()
    }

    fun isCurrentReferenceFor(uri: Uri?, song: Song): Boolean {
        if (uri == null || !isEmbeddedArtworkUri(uri)) return true
        val reference = parse(uri) ?: return false
        val currentSource = sourceFor(song) ?: return false
        return reference.source.cacheKey == currentSource.cacheKey
    }

    fun isEmbeddedArtworkUri(uri: Uri?): Boolean {
        return uri?.scheme == "content" &&
                uri.authority?.endsWith(PROVIDER_SUFFIX) == true
    }
}

internal class EmbeddedArtworkResolver(private val context: Context) {
    private val cacheDirectory: File
        get() = File(context.cacheDir, CACHE_DIRECTORY)

    fun resolve(song: Song): Uri? {
        val source = EmbeddedArtworkContract.sourceFor(song) ?: return null
        val reference = synchronized(cacheLock) {
            ensureCacheDirectory()
            findCachedReference(source)
                ?: if (noArtworkMarker(source).isFile) {
                    null
                } else {
                    extractAndCache(source, song.filePath)
                }
        }
        return reference?.let { EmbeddedArtworkContract.buildUri(context.packageName, it) }
    }

    fun isMaterialized(uri: Uri?): Boolean {
        if (!EmbeddedArtworkContract.isEmbeddedArtworkUri(uri)) return true
        val reference = uri?.let { EmbeddedArtworkContract.parse(it) } ?: return false
        return File(cacheDirectory, reference.fileName).isFile
    }

    fun requiresReconstruction(song: Song): Boolean {
        val uri = song.albumArtUri
        if (!EmbeddedArtworkContract.isEmbeddedArtworkUri(uri)) return false
        val reference = uri?.let { EmbeddedArtworkContract.parse(it) } ?: return false
        return !File(cacheDirectory, reference.fileName).isFile
    }

    fun invalidate(song: Song) {
        val source = EmbeddedArtworkContract.sourceFor(song) ?: return
        synchronized(cacheLock) {
            ensureCacheDirectory()
            cacheDirectory.listFiles()
                ?.filter { it.name.startsWith(source.cacheKey) }
                ?.forEach { it.delete() }
        }
    }

    fun resolveReference(reference: EmbeddedArtworkReference): File? {
        return synchronized(cacheLock) {
            ensureCacheDirectory()
            val expected = File(cacheDirectory, reference.fileName)
            if (expected.isFile) return@synchronized expected
            if (noArtworkMarker(reference.source).isFile) return@synchronized null

            val extracted = extractArtwork(reference.source, legacyFilePath = null)
                ?: run {
                    writeNoArtworkMarker(reference.source)
                    return@synchronized null
                }
            if (extracted.bytes.sha256() != reference.artworkHash) return@synchronized null
            writeArtworkFile(expected, extracted.bytes)
            expected.takeIf { it.isFile }
        }
    }

    private fun findCachedReference(source: EmbeddedArtworkSource): EmbeddedArtworkReference? {
        val prefix = "${source.cacheKey}-"
        val file = cacheDirectory.listFiles()?.firstOrNull { candidate ->
            candidate.isFile && candidate.name.startsWith(prefix) &&
                    candidate.extension.lowercase() in ARTWORK_EXTENSIONS
        } ?: return null
        val artworkHash = file.name.removePrefix(prefix).substringBeforeLast('.')
        if (!artworkHash.isSha256()) return null
        return EmbeddedArtworkReference(source, artworkHash, file.extension.lowercase())
    }

    private fun extractAndCache(
        source: EmbeddedArtworkSource,
        legacyFilePath: String?
    ): EmbeddedArtworkReference? {
        val extracted = extractArtwork(source, legacyFilePath)
        if (extracted == null) {
            writeNoArtworkMarker(source)
            return null
        }
        val reference = EmbeddedArtworkReference(
            source = source,
            artworkHash = extracted.bytes.sha256(),
            extension = extracted.extension
        )
        writeArtworkFile(File(cacheDirectory, reference.fileName), extracted.bytes)
        return reference.takeIf { File(cacheDirectory, it.fileName).isFile }
    }

    private fun extractArtwork(
        source: EmbeddedArtworkSource,
        legacyFilePath: String?
    ): ExtractedArtwork? {
        readThroughContentResolver(source)?.let { return it }
        val legacyFile = legacyFilePath?.let(::File)
            ?.takeIf { it.isFile }
            ?: return null
        return readArtwork(legacyFile, legacyFile.extension)
    }

    private fun readThroughContentResolver(source: EmbeddedArtworkSource): ExtractedArtwork? {
        val extension = source.displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()

        when (val frameworkResult = readThroughFrameworkMetadata(source)) {
            is FrameworkArtworkResult.Read -> return frameworkResult.artwork
            FrameworkArtworkResult.Failed -> Unit
        }

        runCatching {
            context.contentResolver.openFileDescriptor(source.uri, "r")?.use { descriptor ->
                readArtwork(
                    file = File("/proc/self/fd/${descriptor.fd}"),
                    extension = extension
                )
            }
        }.getOrNull()?.let { return it }

        val safeExtension = extension.takeIf { it.matches(SAFE_EXTENSION) } ?: "audio"
        val temporaryFile = runCatching {
            File.createTempFile("embedded_source_", ".$safeExtension", cacheDirectory)
        }.getOrNull() ?: return null
        return try {
            val copied = context.contentResolver.openInputStream(source.uri)?.use { input ->
                temporaryFile.outputStream().use { output -> input.copyTo(output) }
                true
            } == true
            if (copied) readArtwork(temporaryFile, extension) else null
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        } finally {
            temporaryFile.delete()
        }
    }

    private fun readThroughFrameworkMetadata(
        source: EmbeddedArtworkSource
    ): FrameworkArtworkResult = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, source.uri)
            val artwork = retriever.embeddedPicture
                ?.takeIf { bytes -> bytes.isNotEmpty() }
                ?.let { bytes ->
                    ExtractedArtwork(
                        bytes = bytes,
                        extension = embeddedArtworkExtension(bytes)
                    )
                }
            FrameworkArtworkResult.Read(artwork)
        } finally {
            retriever.release()
        }
    }.getOrElse { FrameworkArtworkResult.Failed }

    private fun readArtwork(file: File, extension: String): ExtractedArtwork? {
        return try {
            val audioFile = if (extension.isNotBlank()) {
                AudioFileIO.readAs(file, extension)
            } else {
                AudioFileIO.readMagic(file)
            }
            val artwork = audioFile.tag?.firstArtwork ?: return null
            val bytes = artwork.binaryData?.takeIf { it.isNotEmpty() } ?: return null
            ExtractedArtwork(bytes, artworkExtension(artwork.mimeType))
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private fun writeArtworkFile(file: File, bytes: ByteArray) {
        if (file.isFile) return
        val temporary = File(cacheDirectory, "${file.name}.tmp")
        runCatching {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                file.writeBytes(bytes)
            }
        }
        temporary.delete()
    }

    private fun writeNoArtworkMarker(source: EmbeddedArtworkSource) {
        runCatching { noArtworkMarker(source).writeText("no_embedded_artwork") }
    }

    private fun noArtworkMarker(source: EmbeddedArtworkSource): File {
        return File(cacheDirectory, "${source.cacheKey}.no_artwork")
    }

    private fun ensureCacheDirectory() {
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
    }

    private data class ExtractedArtwork(val bytes: ByteArray, val extension: String)

    private sealed interface FrameworkArtworkResult {
        data class Read(val artwork: ExtractedArtwork?) : FrameworkArtworkResult
        data object Failed : FrameworkArtworkResult
    }

    private companion object {
        const val CACHE_DIRECTORY = "embedded_album_art"
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
        val cacheLock = Any()
    }
}

private val ARTWORK_EXTENSIONS = setOf("jpg", "png", "webp")

private fun artworkExtension(mimeType: String?): String = when (mimeType?.lowercase()) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> "jpg"
}

internal fun embeddedArtworkExtension(bytes: ByteArray): String = when {
    bytes.size >= 8 &&
            bytes[0].toInt() and 0xff == 0x89 &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() &&
            bytes[3] == 0x47.toByte() -> "png"
    bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "webp"
    else -> "jpg"
}

private fun String.isSha256(): Boolean = length == 64 && all { it in "0123456789abcdef" }

private fun String.sha256(): String = toByteArray().sha256()

private fun ByteArray.sha256(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
