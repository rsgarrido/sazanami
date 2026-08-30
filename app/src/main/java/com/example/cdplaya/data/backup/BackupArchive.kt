package com.example.cdplaya.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupArchive {
    private const val METADATA_ENTRY = "app_backup.json"
    private const val MAX_BACKUP_BYTES = 128L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L

    fun write(backup: AppBackup, output: OutputStream) {
        val payloads = backup.visualAssetPayloads.associateBy { it.metadata }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(METADATA_ENTRY))
            zip.write(AppBackupJson.encodeBackup(backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            backup.visualAssets.forEach { metadata ->
                val payload = payloads[metadata] ?: return@forEach
                zip.writeEntry(metadata.thumbnailEntry, payload.thumbnailBytes)
                zip.writeEntry(metadata.displayEntry, payload.displayBytes)
            }
        }
    }

    fun read(input: InputStream): AppBackup {
        val bytes = input.readBounded(MAX_BACKUP_BYTES)
        if (!bytes.isZipArchive()) {
            return AppBackupJson.decodeBackup(ByteArrayInputStream(bytes))
        }

        val entries = linkedMapOf<String, ByteArray>()
        var extractedBytes = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name !in entries) {
                        val entryBytes = zip.readBounded(MAX_ENTRY_BYTES)
                        extractedBytes += entryBytes.size
                        if (extractedBytes > MAX_BACKUP_BYTES) {
                            throw IOException("Backup package expands beyond the supported size.")
                        }
                        entries[entry.name] = entryBytes
                    }
                    zip.closeEntry()
                }
            }
        } catch (failure: IOException) {
            throw IllegalArgumentException("Invalid Sazanami backup package.", failure)
        }

        val metadataBytes = entries[METADATA_ENTRY]
            ?: throw IllegalArgumentException("Backup package is missing $METADATA_ENTRY.")
        val backup = AppBackupJson.decodeBackup(ByteArrayInputStream(metadataBytes))
        val payloads = backup.visualAssets.mapNotNull { metadata ->
            val thumbnail = entries[metadata.thumbnailEntry] ?: return@mapNotNull null
            val display = entries[metadata.displayEntry] ?: return@mapNotNull null
            BackupVisualAssetPayload(metadata, thumbnail, display)
        }
        return backup.copy(visualAssetPayloads = payloads)
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun InputStream.readBounded(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Backup data is too large.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.isZipArchive(): Boolean =
        size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4B.toByte() &&
                this[2] == 0x03.toByte() && this[3] == 0x04.toByte()
}
