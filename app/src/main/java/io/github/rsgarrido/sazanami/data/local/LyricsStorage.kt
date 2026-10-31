package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lyrics_overrides", indices = [Index(value = ["boundTrackKey"])])
data class LyricsOverrideEntity(
    @PrimaryKey val id: String,
    val referenceJson: String,
    val boundTrackKey: String?,
    val sourceBytes: ByteArray,
    val revision: String,
    val checksum: String,
    val timing: String,
    val originId: String,
    val originRevision: String,
    val createdAt: Long,
    val updatedAt: Long,
    val reconciliation: String
)

@Entity(tableName = "lyrics_drafts")
data class LyricsDraftEntity(
    @PrimaryKey val trackKey: String,
    val referenceJson: String,
    val sourceBytes: ByteArray,
    val stateJson: String,
    val updatedAt: Long
)

/** Restore changes the epoch atomically with the overrides; pre-restore editors cannot commit. */
@Entity(tableName = "lyrics_storage_epoch")
data class LyricsStorageEpochEntity(@PrimaryKey val id: Int = 1, val generation: String)

data class LyricsOverrideBinding(val id: String, val referenceJson: String, val boundTrackKey: String?, val reconciliation: String)
data class LyricsStorageBudget(val count: Int, val bytes: Long)

@Dao
interface LyricsDao {
    @Query("SELECT id, referenceJson, boundTrackKey, reconciliation FROM lyrics_overrides") suspend fun bindings(): List<LyricsOverrideBinding>
    @Query("SELECT id, referenceJson, boundTrackKey, reconciliation FROM lyrics_overrides WHERE boundTrackKey = :key OR boundTrackKey IS NULL")
    suspend fun bindingsFor(key: String): List<LyricsOverrideBinding>
    @Query("UPDATE lyrics_overrides SET referenceJson = :reference, boundTrackKey = :key, reconciliation = :status WHERE id = :id")
    suspend fun bind(id: String, reference: String, key: String?, status: String)
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(length(sourceBytes)), 0) AS bytes FROM lyrics_overrides WHERE id != :exceptId")
    suspend fun overrideBudget(exceptId: String): LyricsStorageBudget
    @Query("SELECT * FROM lyrics_overrides ORDER BY id") suspend fun overrides(): List<LyricsOverrideEntity>
    @Query("SELECT * FROM lyrics_overrides WHERE id = :id") suspend fun overrideById(id: String): LyricsOverrideEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOverride(row: LyricsOverrideEntity)
    @Query("DELETE FROM lyrics_overrides WHERE id = :id") suspend fun deleteOverride(id: String)
    @Query("DELETE FROM lyrics_overrides") suspend fun deleteOverrides()
    @Query("SELECT * FROM lyrics_drafts WHERE trackKey = :key") suspend fun draft(key: String): LyricsDraftEntity?
    @Query("SELECT * FROM lyrics_drafts ORDER BY updatedAt DESC") suspend fun drafts(): List<LyricsDraftEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(row: LyricsDraftEntity)
    @Query("DELETE FROM lyrics_drafts WHERE trackKey = :key") suspend fun deleteDraft(key: String)
    @Query("SELECT generation FROM lyrics_storage_epoch WHERE id = 1") suspend fun epoch(): String?
    @Query("SELECT generation FROM lyrics_storage_epoch WHERE id = 1") fun observeEpoch(): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEpoch(row: LyricsStorageEpochEntity)
}

val LYRICS_MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `lyrics_overrides` (
            `id` TEXT NOT NULL, `referenceJson` TEXT NOT NULL, `boundTrackKey` TEXT,
            `sourceBytes` BLOB NOT NULL, `revision` TEXT NOT NULL, `checksum` TEXT NOT NULL,
            `timing` TEXT NOT NULL, `originId` TEXT NOT NULL, `originRevision` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `reconciliation` TEXT NOT NULL,
            PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_overrides_boundTrackKey` ON `lyrics_overrides` (`boundTrackKey`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `lyrics_drafts` (
            `trackKey` TEXT NOT NULL, `referenceJson` TEXT NOT NULL, `sourceBytes` BLOB NOT NULL,
            `stateJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`trackKey`))""")
        db.execSQL("CREATE TABLE IF NOT EXISTS `lyrics_storage_epoch` (`id` INTEGER NOT NULL, `generation` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT OR IGNORE INTO `lyrics_storage_epoch` (`id`, `generation`) VALUES (1, 'initial')")
    }
}
