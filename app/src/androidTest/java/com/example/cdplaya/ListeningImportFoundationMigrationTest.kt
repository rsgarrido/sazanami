package com.example.cdplaya

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportFoundationMigrationTest {
    @Test
    fun room10To11_preservesNativeAndMapsLegacyImportedOwnership() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "listening-import-10-11-${System.nanoTime()}.db"
        val oldMigrations = migrationsThroughTen()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    oldMigrations.forEach { it.migrate(db) }
                    db.execSQL("""
                        INSERT INTO listening_track_identities VALUES
                        (1,'Native','Artist','Album',NULL,1000,'native','artist','album',NULL,1,1,1),
                        (2,'Imported','Artist','Album',NULL,1000,'imported','artist','album',NULL,1,1,1)
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO listening_events
                        (id,eventUuid,source,trackIdentityId,localTrackBindingId,playbackSessionId,
                         startedAt,endedAt,listenedMs,trackDurationMs,qualifiedAsPlay,
                         qualificationReason,qualificationRuleVersion,endReason,sourceEventKey,importBatchId,createdAt)
                        VALUES
                        (1,'native','cdplaya',1,NULL,'session',100,200,100,1000,1,'natural_end',1,'natural_end',NULL,NULL,200),
                        (2,'legacy-import','spotify_import',2,NULL,NULL,300,400,100,1000,0,'none',1,'unknown','legacy-key',44,400)
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { it.writableDatabase }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(DatabaseProvider.MIGRATION_10_11).build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(2L, sqlite.longQuery("SELECT COUNT(*) FROM listening_events"))
            assertEquals(100L, sqlite.longQuery("SELECT attributionAt FROM listening_events WHERE eventUuid='native'"))
            assertEquals("native_natural", sqlite.stringQuery("SELECT completionClassification FROM listening_events WHERE eventUuid='native'"))
            assertEquals("import_published", sqlite.stringQuery("SELECT publicationState FROM listening_events WHERE eventUuid='legacy-import'"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_import_sources"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_import_batches"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_import_batch_events"))
            assertEquals(0L, sqlite.longQuery("SELECT COUNT(*) FROM imported_listening_event_evidence"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_import_sources WHERE accountIdentityDigest IS NULL"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun migrationsThroughTen(): List<Migration> = listOf(
        DatabaseProvider.MIGRATION_1_2, DatabaseProvider.MIGRATION_2_3,
        DatabaseProvider.MIGRATION_3_4, DatabaseProvider.MIGRATION_4_5,
        DatabaseProvider.MIGRATION_5_6, DatabaseProvider.MIGRATION_6_7,
        DatabaseProvider.MIGRATION_7_8, DatabaseProvider.MIGRATION_8_9,
        DatabaseProvider.MIGRATION_9_10
    )
}

private fun SupportSQLiteDatabase.longQuery(sql: String): Long = query(sql).use { cursor ->
    check(cursor.moveToFirst()); cursor.getLong(0)
}

private fun SupportSQLiteDatabase.stringQuery(sql: String): String = query(sql).use { cursor ->
    check(cursor.moveToFirst()); cursor.getString(0)
}
