package io.github.rsgarrido.sazanami.player.waveform

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class WaveformCache(
    private val directory: File,
    private val memoryEntryLimit: Int = DEFAULT_MEMORY_ENTRY_LIMIT,
    private val maximumDiskBytes: Long = DEFAULT_MAXIMUM_DISK_BYTES,
    private val maintenanceTargetBytes: Long = DEFAULT_MAINTENANCE_TARGET_BYTES
) {
    private val memoryCache = object : LinkedHashMap<String, WaveformData>(
        memoryEntryLimit,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, WaveformData>?
        ): Boolean = size > memoryEntryLimit
    }

    @Synchronized
    fun read(sourceKey: String): WaveformData? {
        if (!isSafeSourceKey(sourceKey)) return null
        memoryCache[sourceKey]?.let { data ->
            runCatching { cacheFile(sourceKey).setLastModified(System.currentTimeMillis()) }
            return data
        }

        val cacheFile = cacheFile(sourceKey)
        if (!cacheFile.isFile) return null

        val data = runCatching {
            DataInputStream(BufferedInputStream(cacheFile.inputStream())).use { input ->
                if (input.readInt() != CACHE_MAGIC) return@use null
                if (input.readInt() != CACHE_FORMAT_VERSION) return@use null
                if (input.readUTF() != sourceKey) return@use null

                val amplitudeCount = input.readInt()
                if (amplitudeCount !in 1..MAX_CACHED_AMPLITUDES) return@use null

                val amplitudes = List(amplitudeCount) {
                    input.readFloat().takeIf(Float::isFinite)?.coerceIn(0f, 1f)
                        ?: return@use null
                }
                WaveformData(amplitudes = amplitudes, sourceKey = sourceKey)
            }
        }.getOrNull()

        if (data == null) {
            cacheFile.delete()
            return null
        }

        runCatching { cacheFile.setLastModified(System.currentTimeMillis()) }
        memoryCache[sourceKey] = data
        return data
    }

    @Synchronized
    fun write(data: WaveformData) {
        if (!isSafeSourceKey(data.sourceKey)) return

        val amplitudes = data.amplitudes
            .takeIf { values -> values.size in 1..MAX_CACHED_AMPLITUDES }
            ?.map { amplitude ->
                if (amplitude.isFinite()) amplitude.coerceIn(0f, 1f) else return
            }
            ?: return

        if (!directory.exists() && !directory.mkdirs()) return

        val cacheFile = cacheFile(data.sourceKey)
        val temporaryFile = File(directory, "${data.sourceKey}.tmp")
        val wroteFile = runCatching {
            DataOutputStream(BufferedOutputStream(temporaryFile.outputStream())).use { output ->
                output.writeInt(CACHE_MAGIC)
                output.writeInt(CACHE_FORMAT_VERSION)
                output.writeUTF(data.sourceKey)
                output.writeInt(amplitudes.size)
                amplitudes.forEach(output::writeFloat)
            }
            if (!temporaryFile.renameTo(cacheFile)) {
                temporaryFile.copyTo(cacheFile, overwrite = true)
                temporaryFile.delete()
            }
        }.isSuccess

        if (wroteFile) {
            memoryCache[data.sourceKey] = WaveformData(amplitudes, data.sourceKey)
        } else {
            temporaryFile.delete()
        }
    }

    @Synchronized
    fun getStats(): WaveformCacheStats {
        val files = cacheFiles()
        return WaveformCacheStats(
            fileCount = files.size,
            totalBytes = files.sumOf { file -> runCatching { file.length() }.getOrDefault(0L) }
        )
    }

    @Synchronized
    fun clear(ensureActive: () -> Unit = {}): WaveformCacheStats {
        memoryCache.clear()
        cacheFiles(includeTemporaryFiles = true).forEach { file ->
            ensureActive()
            runCatching { file.delete() }
        }
        return getStats()
    }

    @Synchronized
    fun maintain(ensureActive: () -> Unit = {}): WaveformCacheStats {
        cacheFiles(includeTemporaryFiles = true)
            .filter { file -> file.extension == TEMPORARY_FILE_EXTENSION }
            .forEach { file ->
                ensureActive()
                runCatching { file.delete() }
            }

        val files = cacheFiles()
        var totalBytes = files.sumOf { file ->
            runCatching { file.length() }.getOrDefault(0L)
        }
        if (totalBytes > maximumDiskBytes) {
            files.sortedBy { file ->
                runCatching { file.lastModified() }.getOrDefault(0L)
            }.forEach { file ->
                ensureActive()
                if (totalBytes <= maintenanceTargetBytes) return@forEach
                val fileBytes = runCatching { file.length() }.getOrDefault(0L)
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    totalBytes = (totalBytes - fileBytes).coerceAtLeast(0L)
                }
            }
        }
        return getStats()
    }

    private fun cacheFiles(includeTemporaryFiles: Boolean = false): List<File> {
        return directory.listFiles()?.filter { file ->
            file.isFile && (
                file.extension == CACHE_FILE_EXTENSION ||
                    (includeTemporaryFiles && file.extension == TEMPORARY_FILE_EXTENSION)
                )
        }.orEmpty()
    }

    private fun cacheFile(sourceKey: String): File {
        return File(directory, "$sourceKey.bin")
    }

    private fun isSafeSourceKey(sourceKey: String): Boolean {
        return sourceKey.length == SHA_256_HEX_LENGTH &&
            sourceKey.all { character -> character in '0'..'9' || character in 'a'..'f' }
    }

    companion object {
        private const val CACHE_MAGIC = 0x57415645
        const val CACHE_FORMAT_VERSION = 1
        private const val MAX_CACHED_AMPLITUDES = 1024
        private const val DEFAULT_MEMORY_ENTRY_LIMIT = 8
        private const val SHA_256_HEX_LENGTH = 64
        private const val CACHE_FILE_EXTENSION = "bin"
        private const val TEMPORARY_FILE_EXTENSION = "tmp"
        const val DEFAULT_MAXIMUM_DISK_BYTES = 64L * 1024L * 1024L
        const val DEFAULT_MAINTENANCE_TARGET_BYTES = DEFAULT_MAXIMUM_DISK_BYTES * 4L / 5L
    }
}

data class WaveformCacheStats(
    val fileCount: Int,
    val totalBytes: Long
)
