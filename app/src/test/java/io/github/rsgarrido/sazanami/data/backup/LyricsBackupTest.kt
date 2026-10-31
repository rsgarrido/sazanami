package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.local.LyricsOverrideEntity
import io.github.rsgarrido.sazanami.lyrics.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class LyricsBackupTest {
    @Test fun sourceBytesPortableReferenceAndCommittedRevisionRoundTripThroughArchive() {
        val text = "[xx:Keep]\r\n[offset:-100]\r\n[00:01.234]<00:01.234>Paper <00:01.800>stars"
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val row = row(bytes)
        val backup = AppBackup(createdAt = 1, lyricsOverrides = LyricsBackup.export(listOf(row)))
        val output = ByteArrayOutputStream()
        BackupArchive.write(backup, output)
        val decoded = BackupArchive.read(ByteArrayInputStream(output.toByteArray()))
        val restored = LyricsBackup.validate(decoded.lyricsOverrides).single()
        assertArrayEquals(bytes, restored.sourceBytes)
        assertEquals(row.id, restored.id)
        assertEquals(row.revision, restored.revision)
        assertNull(restored.boundTrackKey)
        assertEquals("PENDING", restored.reconciliation)
        val reference = LyricsOverrideStore.decodeReference(restored.referenceJson)
        assertNull(reference.mediaStoreId)
        assertEquals("", reference.contentUri)
        assertEquals("", reference.volumeName)
        assertEquals("Music/", reference.relativePath)
        assertEquals(LyricsTiming.WORD.name, restored.timing)
        val json = AppBackupJson.encodeBackup(backup)
        assertFalse(json.contains("lyrics_drafts"))
        assertFalse(json.contains("device-uri"))
    }

    @Test fun schema17AndOlderBackupsMigrateWithNoOverrides() {
        val previous = AppBackup(schemaVersion = 17, createdAt = 1)
        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(previous))
        assertEquals(18, decoded.schemaVersion)
        assertTrue(decoded.lyricsOverrides.isEmpty())
    }

    @Test fun checksumEncodingEmptyAndDuplicateIdentitiesAreRejected() {
        val valid = LyricsBackup.export(listOf(row("Plain original".toByteArray()))).single()
        listOf(valid.copy(checksum = "wrong"), valid.copy(sourceBase64 = "not base64!"),
            valid.copy(sourceBase64 = ""), valid.copy(timing = "WORD"), valid.copy(updatedAt = 0)).forEach { invalid ->
            reject { LyricsBackup.validate(listOf(invalid)) }
        }
        reject { LyricsBackup.validate(listOf(valid, valid)) }
    }

    @Test fun aggregateAndPerDocumentPayloadLimitsRejectRatherThanOmitRecords() {
        val text = ("x".repeat(1000) + "\n").repeat(250)
        val valid = LyricsBackup.export(listOf(row(text.toByteArray()))).single()
        reject { LyricsBackup.validate((0..34).map { valid.copy(id = "id-$it") }) }
        reject { LyricsBackup.validate(listOf(valid.copy(sourceBase64 = "A".repeat(400_000)))) }
        reject { LyricsBackup.validate((0..LyricsEditingLimits.OVERRIDES).map { valid.copy(id = "id-$it") }) }
    }

    @Test fun schemaNewerThanSupportedIsRejectedRatherThanReadAsOldBackup() {
        val json = AppBackupJson.encodeBackup(AppBackup(createdAt = 1)).replace("\"schemaVersion\":18", "\"schemaVersion\":19")
        reject { AppBackupJson.decodeBackup(json) }
    }

    private fun row(bytes: ByteArray): LyricsOverrideEntity {
        val source = LrcParser().parseSource(bytes)
        val reference = SongReference(mediaStoreId = 7, volumeName = "primary", contentUri = "content://device-uri/7",
            displayName = "synthetic.flac", relativePath = "Music/", duration = 10_000, fileSizeBytes = 1024)
        return LyricsOverrideEntity("id", LyricsOverrideStore.encodeReference(reference), "qualified", bytes, "commit-revision",
            source.revision, SourcedLyrics(LrcParser().project(source), LyricsSource.LocalOverride("id")).timing.name,
            "embedded:device-uri", "original-revision", 1, 2, "BOUND")
    }
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected invalid backup rejection") } catch (_: IllegalArgumentException) { }
    }
}
