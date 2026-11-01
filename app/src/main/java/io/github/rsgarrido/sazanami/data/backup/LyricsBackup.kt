package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.local.LyricsOverrideEntity
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LyricsStorageEpochEntity
import io.github.rsgarrido.sazanami.lyrics.*
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class BackupLyricsOverride(
    val id: String,
    val reference: BackupSongReference,
    val sourceBase64: String,
    val revision: String,
    val checksum: String,
    val timing: String,
    val originKind: String = "",
    val originRevision: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

object LyricsBackup {
    /** Caller includes this in the same Room transaction as the rest of backup restoration. */
    suspend fun restoreWithinTransaction(database: AppDatabase, validated: List<LyricsOverrideEntity>) {
        database.lyricsDao().deleteOverrides()
        validated.forEach { database.lyricsDao().putOverride(it) }
        database.lyricsDao().putEpoch(LyricsStorageEpochEntity(generation = java.util.UUID.randomUUID().toString()))
    }
    fun export(rows: List<LyricsOverrideEntity>): List<BackupLyricsOverride> = rows.map { row ->
        BackupLyricsOverride(row.id, LyricsOverrideStore.decodeReference(row.referenceJson).toBackupSongReference(),
            Base64.getEncoder().encodeToString(row.sourceBytes), row.revision, row.checksum, row.timing,
            row.originId.substringBefore(':'), row.originRevision, row.createdAt, row.updatedAt)
    }.also { validate(it) }

    /** Validate the entire batch before the caller starts its restore transaction. Never truncate. */
    fun validate(rows: List<BackupLyricsOverride>): List<LyricsOverrideEntity> {
        require(rows.size <= LyricsEditingLimits.OVERRIDES) { "Too many saved lyrics in backup." }
        require(rows.map { it.id }.distinct().size == rows.size) { "Duplicate saved lyrics identity." }
        val editor = LyricsDocumentEditor()
        val parser = LrcParser()
        var total = 0L
        return rows.map { row ->
            require(row.id.isNotBlank() && row.id.length <= 128 && row.revision.isNotBlank() && row.revision.length <= 128) { "Invalid saved lyrics identity." }
            require(row.sourceBase64.length <= ((LyricsEditingLimits.DOCUMENT_BYTES + 2) / 3) * 4) { "Saved lyrics exceed the document limit." }
            require(row.originKind.length <= 128 && row.originRevision.length <= 256 && row.createdAt >= 0 && row.updatedAt >= row.createdAt) { "Invalid saved lyrics metadata." }
            val ref = row.reference
            require(listOf(ref.relativePath, ref.displayName, ref.title, ref.artist, ref.album, ref.albumArtist, ref.portableKey, ref.legacyStableKey).all { it.length <= 4096 }) { "Invalid saved lyrics track reference." }
            require(ref.displayName.isNotBlank() && !ref.relativePath.isAbsolutePathLike()) { "Saved lyrics need a portable file reference." }
            require(ref.fileSizeBytes >= 0 && ref.duration >= 0 && ref.portableKeyVersion > 0) { "Invalid saved lyrics file evidence." }
            val bytes = Base64.getDecoder().decode(row.sourceBase64)
            total += bytes.size
            require(total <= LyricsEditingLimits.TOTAL_OVERRIDE_BYTES) { "Saved lyrics exceed the aggregate backup limit." }
            val source = editor.parse(bytes)
            editor.validate(source)
            require(source.revision == row.checksum) { "Saved lyrics checksum mismatch." }
            require(SourcedLyrics(parser.project(source), LyricsSource.LocalOverride(row.id)).timing.name == row.timing) { "Saved lyrics timing mismatch." }
            LyricsOverrideEntity(row.id, LyricsOverrideStore.encodeReference(ref.toSongReference()), null, bytes,
                row.revision, row.checksum, row.timing, row.originKind, row.originRevision, row.createdAt, row.updatedAt, "PENDING")
        }
    }
}
