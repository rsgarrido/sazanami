package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueMigrationTest {
    @Test
    fun room19To20AddsOnlyEmptyQueueFoundationAndPreservesExistingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "playback-queues-19-20-${System.nanoTime()}.db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `database_marker` " +
                            "(`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    migrationsThroughNineteen().forEach { it.migrate(db) }
                    db.execSQL(
                        "INSERT INTO artist_picture_assignments " +
                            "(artistKey, normalizedArtistName, assetReference, updatedAt) " +
                            "VALUES ('artist', 'artist', 'asset', 123)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { it.writableDatabase }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(DatabaseProvider.MIGRATION_19_20)
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(
                "asset",
                sqlite.query(
                    "SELECT assetReference FROM artist_picture_assignments WHERE artistKey='artist'"
                ).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getString(0)
                }
            )
            assertEquals(
                setOf("playback_queues", "playback_queue_entries", "playback_queue_state"),
                sqlite.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'playback_queue%'"
                ).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
            )
            assertEquals(0L, sqlite.count("playback_queues"))
            assertEquals(0L, sqlite.count("playback_queue_entries"))
            assertEquals(0L, sqlite.count("playback_queue_state"))
            assertEquals(
                listOf(
                    "queueId", "displayName", "createdAt", "updatedAt", "lastActiveAt",
                    "sourceType", "sourceKey", "currentEntryId", "currentPositionMs",
                    "shuffleEnabled", "repeatMode"
                ),
                sqlite.columns("playback_queues")
            )
            assertEquals(
                listOf(
                    "entryId", "queueId", "trackIdentityId", "localTrackBindingId",
                    "baseOrder", "playbackOrder"
                ),
                sqlite.columns("playback_queue_entries")
            )
            assertEquals(3L, sqlite.countQuery(
                "SELECT COUNT(*) FROM pragma_foreign_key_list('playback_queue_entries')"
            ))
            assertTrue(sqlite.countQuery(
                "SELECT COUNT(*) FROM pragma_index_list('playback_queue_entries') " +
                    "WHERE name='index_playback_queue_entries_queueId_baseOrder' AND [unique]=1"
            ) == 1L)
            assertTrue(sqlite.countQuery(
                "SELECT COUNT(*) FROM pragma_index_list('playback_queue_entries') " +
                    "WHERE name='index_playback_queue_entries_queueId_playbackOrder' AND [unique]=1"
            ) == 1L)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun migrationsThroughNineteen() = listOf(
        DatabaseProvider.MIGRATION_1_2,
        DatabaseProvider.MIGRATION_2_3,
        DatabaseProvider.MIGRATION_3_4,
        DatabaseProvider.MIGRATION_4_5,
        DatabaseProvider.MIGRATION_5_6,
        DatabaseProvider.MIGRATION_6_7,
        DatabaseProvider.MIGRATION_7_8,
        DatabaseProvider.MIGRATION_8_9,
        DatabaseProvider.MIGRATION_9_10,
        DatabaseProvider.MIGRATION_10_11,
        DatabaseProvider.MIGRATION_11_12,
        DatabaseProvider.MIGRATION_12_13,
        DatabaseProvider.MIGRATION_13_14,
        DatabaseProvider.MIGRATION_14_15,
        DatabaseProvider.MIGRATION_15_16,
        DatabaseProvider.MIGRATION_16_17,
        DatabaseProvider.MIGRATION_17_18,
        DatabaseProvider.MIGRATION_18_19
    )
}

private fun SupportSQLiteDatabase.count(table: String): Long =
    countQuery("SELECT COUNT(*) FROM `$table`")

private fun SupportSQLiteDatabase.countQuery(sql: String): Long = query(sql).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getLong(0)
}

private fun SupportSQLiteDatabase.columns(table: String): List<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
    }
