package io.github.rsgarrido.sazanami.player

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

internal data class FragmentedMp4SourceIdentity(
    val uri: String,
    val fileLength: Long,
    val lastModified: Long?
)

internal class FragmentedMp4SeekIndexCache(private val maxEntries: Int = 16) {
    private data class CachedValue(val index: FragmentedMp4SeekIndex?)

    private val values = object : LinkedHashMap<FragmentedMp4SourceIdentity, CachedValue>(
        maxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<FragmentedMp4SourceIdentity, CachedValue>
        ): Boolean = size > maxEntries
    }

    init {
        require(maxEntries > 0)
    }

    @Synchronized
    fun getOrBuild(
        identity: FragmentedMp4SourceIdentity,
        builder: () -> FragmentedMp4SeekIndex?
    ): FragmentedMp4SeekIndex? {
        values[identity]?.let { return it.index }
        return builder().also { index -> values[identity] = CachedValue(index) }
    }
}

/** Resolves and caches small, validated seek indexes for local MP4-family sources. */
internal class LocalFragmentedMp4SeekIndexProvider(
    context: Context,
    private val cache: FragmentedMp4SeekIndexCache = FragmentedMp4SeekIndexCache()
) {
    private val resolver = context.applicationContext.contentResolver

    @Synchronized
    fun indexFor(uri: Uri): FragmentedMp4SeekIndex? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) {
            return null
        }
        val metadata = readMetadata(uri)
        if (!isMp4FamilyCandidate(uri, metadata.displayName, metadata.mimeType)) return null
        val descriptor = runCatching { resolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
            ?: return null
        return try {
            descriptor.use descriptorUse@ { assetDescriptor ->
                val baseOffset = assetDescriptor.startOffset.coerceAtLeast(0L)
                val duplicate = runCatching {
                    ParcelFileDescriptor.dup(assetDescriptor.fileDescriptor)
                }.getOrNull() ?: return@descriptorUse null
                ParcelFileDescriptor.AutoCloseInputStream(duplicate).use inputUse@ { input ->
                    val channel = input.channel
                    val fileLength = assetDescriptor.length.takeIf { it >= 0L }
                        ?: assetDescriptor.parcelFileDescriptor.statSize
                            .takeIf { it > baseOffset }
                            ?.minus(baseOffset)
                        ?: runCatching { channel.size() }.getOrNull()
                            ?.takeIf { it > baseOffset }
                            ?.minus(baseOffset)
                        ?: return@inputUse null
                    if (fileLength <= 0L) return@inputUse null
                    val identity = FragmentedMp4SourceIdentity(
                        uri = uri.toString(),
                        fileLength = fileLength,
                        lastModified = metadata.lastModified
                    )
                    cache.getOrBuild(identity) {
                        val expectedDurationUs = metadata.durationMs
                            ?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
                            ?.times(1_000L)
                        FragmentedMp4SeekIndexParser.parse(
                            reader = FileChannelMp4Reader(channel, baseOffset),
                            fileLength = fileLength,
                            expectedDurationUs = expectedDurationUs
                        )
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readMetadata(uri: Uri): SourceMetadata {
        val file = uri.path
            ?.let { path -> File(path) }
            ?.takeIf { uri.scheme == ContentResolver.SCHEME_FILE }
        return SourceMetadata(
            displayName = queryString(uri, OpenableColumns.DISPLAY_NAME) ?: file?.name,
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
            lastModified = queryLong(uri, MediaStore.MediaColumns.DATE_MODIFIED)
                ?: file?.lastModified()?.takeIf { it > 0L },
            durationMs = queryLong(uri, MediaStore.Audio.Media.DURATION)
        )
    }

    private fun queryLong(uri: Uri, column: String): Long? =
        queryValue(uri, column) { cursor, index ->
            if (cursor.isNull(index)) null else cursor.getLong(index)
        }

    private fun queryString(uri: Uri, column: String): String? =
        queryValue(uri, column) { cursor, index ->
            if (cursor.isNull(index)) null else cursor.getString(index)
        }

    private fun <T> queryValue(
        uri: Uri,
        column: String,
        reader: (Cursor, Int) -> T?
    ): T? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return runCatching {
            resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(column)
                if (index < 0) null else reader(cursor, index)
            }
        }.getOrNull()
    }

    private fun isMp4FamilyCandidate(uri: Uri, displayName: String?, mimeType: String?): Boolean {
        val extension = (displayName ?: uri.lastPathSegment.orEmpty())
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension == "m4a" || extension == "mp4") return true
        val normalizedMime = mimeType?.lowercase().orEmpty()
        return normalizedMime.contains("mp4") || normalizedMime.contains("m4a")
    }

    private data class SourceMetadata(
        val displayName: String?,
        val mimeType: String?,
        val lastModified: Long?,
        val durationMs: Long?
    )
}
