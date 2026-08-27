package com.example.cdplaya

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningIdentityReconciliationMigrationTest {
    @Test
    fun room11To12_createsOnlyEmptyReconciliationStateAndPreservesExistingHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "reconciliation-11-12-${System.nanoTime()}.db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    migrationsThroughEleven().forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO listening_track_identities VALUES (1,'Local','Artist','Album',NULL,1000,'local','artist','album',NULL,1,1,2)")
                    db.execSQL("INSERT INTO listening_track_identities VALUES (2,'Historical','Artist','Album',NULL,1000,'historical','artist','album',NULL,1,1,2)")
                    db.execSQL("INSERT INTO local_track_bindings VALUES (1,1,'local:1',1,'external','content://1','Music/','local.flac',NULL,1000,1,1000,NULL,'portable:1',1,1,2,NULL)")
                    db.execSQL("INSERT INTO listening_events(id,eventUuid,source,trackIdentityId,localTrackBindingId,playbackSessionId,startedAt,endedAt,attributionAt,timestampEvidence,listenedMs,trackDurationMs,qualifiedAsPlay,qualificationReason,qualificationRuleVersion,qualificationPolicy,endReason,completionClassification,publicationState,sourceEventKey,importBatchId,createdAt) VALUES (1,'imported','spotify_import',2,NULL,NULL,NULL,100,100,'source_end_only',30000,NULL,1,'time_threshold',1,'spotify',NULL,'none','import_published','source-key',NULL,101)")
                    db.execSQL("INSERT INTO listening_import_sources VALUES (1,'fictional-profile','spotify_import','Fictional profile',NULL,1,2)")
                    db.execSQL("INSERT INTO listening_track_external_ids VALUES (1,2,'spotify_import','spotify:fictional',1,2)")
                    db.execSQL("INSERT INTO imported_listening_event_evidence VALUES (1,1,1,'fictional-fingerprint',3,NULL,NULL,'false','created_historical_identity')")
                    db.execSQL("INSERT INTO song_ratings VALUES (2,4,10,11)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { it.writableDatabase }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                DatabaseProvider.MIGRATION_11_12,
                DatabaseProvider.MIGRATION_12_13,
                DatabaseProvider.MIGRATION_13_14,
                DatabaseProvider.MIGRATION_14_15,
                DatabaseProvider.MIGRATION_15_16
            )
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(0L, sqlite.longQuery("SELECT COUNT(*) FROM listening_identity_reconciliations"))
            assertEquals(2L, sqlite.longQuery("SELECT COUNT(*) FROM listening_track_identities"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_events"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_track_external_ids"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM imported_listening_event_evidence"))
            assertEquals(3L, sqlite.longQuery("SELECT duplicateOrdinal FROM imported_listening_event_evidence"))
            assertEquals(4L, sqlite.longQuery("SELECT rating FROM song_ratings WHERE trackIdentityId=2"))
            assertEquals(
                listOf("sourceIdentityId", "targetIdentityId", "reconciledAt"),
                sqlite.query("PRAGMA table_info(`listening_identity_reconciliations`)").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                }
            )
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM pragma_index_list('listening_identity_reconciliations') WHERE name='index_listening_identity_reconciliations_targetIdentityId' AND [unique]=0"))
            assertEquals(2L, sqlite.longQuery("SELECT COUNT(*) FROM pragma_foreign_key_list('listening_identity_reconciliations') WHERE on_delete='RESTRICT'"))

            sqlite.execSQL("INSERT INTO listening_track_identities VALUES (3,'Historical 2','Artist','Album',NULL,1000,'historical 2','artist','album',NULL,1,1,2)")
            sqlite.execSQL("INSERT INTO listening_identity_reconciliations VALUES (2,1,500)")
            sqlite.execSQL("INSERT INTO listening_identity_reconciliations VALUES (3,1,501)")
            assertEquals(2L, sqlite.longQuery("SELECT COUNT(*) FROM listening_identity_reconciliations WHERE targetIdentityId=1"))
            assertTrue(runCatching {
                sqlite.execSQL("INSERT INTO listening_identity_reconciliations VALUES (2,3,502)")
            }.isFailure)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun migrationsThroughEleven() = listOf(
        DatabaseProvider.MIGRATION_1_2,
        DatabaseProvider.MIGRATION_2_3,
        DatabaseProvider.MIGRATION_3_4,
        DatabaseProvider.MIGRATION_4_5,
        DatabaseProvider.MIGRATION_5_6,
        DatabaseProvider.MIGRATION_6_7,
        DatabaseProvider.MIGRATION_7_8,
        DatabaseProvider.MIGRATION_8_9,
        DatabaseProvider.MIGRATION_9_10,
        DatabaseProvider.MIGRATION_10_11
    )
}

private fun SupportSQLiteDatabase.longQuery(sql: String): Long = query(sql).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getLong(0)
}
