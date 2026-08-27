package com.example.cdplaya

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongRatingMigrationTest {
    @Test
    fun migrationNineToTenPreservesExistingDataAndCreatesConstrainedRatingTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "song-rating-9-10-${System.nanoTime()}.db"
        val throughNine = listOf(
            DatabaseProvider.MIGRATION_1_2,
            DatabaseProvider.MIGRATION_2_3,
            DatabaseProvider.MIGRATION_3_4,
            DatabaseProvider.MIGRATION_4_5,
            DatabaseProvider.MIGRATION_5_6,
            DatabaseProvider.MIGRATION_6_7,
            DatabaseProvider.MIGRATION_7_8,
            DatabaseProvider.MIGRATION_8_9
        )
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    throughNine.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO database_marker VALUES (1, 'preserved')")
                    db.execSQL(
                        """
                        INSERT INTO listening_track_identities VALUES
                        (1, 'Title', 'Artist', 'Album', NULL, 1000, 'title', 'artist', 'album', NULL, 1, 10, 10)
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO local_track_bindings VALUES " +
                            "(1, 1, 'local:one', 1, 'external', 'content://one', 'Music/', " +
                            "'one.flac', NULL, 100, 1, 1000, NULL, 'portable:one', 1, 10, 20, NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO legacy_listening_baselines VALUES " +
                            "(1, 4, 10, 20, 'legacy:one', 30)"
                    )
                    db.execSQL(
                        "INSERT INTO listening_events VALUES " +
                            "(1, 'event-one', 'cdplaya', 1, 1, 'session-one', 40, 50, 10, " +
                            "1000, 1, 'time_threshold', 1, 'stopped', NULL, NULL, 50)"
                    )
                    db.execSQL(
                        "INSERT INTO favorite_songs VALUES " +
                            "('favorite:one', 'one', 'Title', 'Artist', 'Album', 1000, 1, 1, " +
                            "'external', 'content://one', 'Music/', 'one.flac', 100, 1, " +
                            "'Artist', 'portable:one', 1)"
                    )
                    db.execSQL("INSERT INTO playlists VALUES (1, 'Preserved playlist', 1, 2)")
                    db.execSQL(
                        "INSERT INTO playlist_songs " +
                            "(playlistSongId, playlistId, songKey, position, title, artist, album, duration, addedAt) " +
                            "VALUES (1, 1, 'one', 0, 'Title', 'Artist', 'Album', 1000, 3)"
                    )
                    db.execSQL(
                        "INSERT INTO cached_songs VALUES " +
                            "(1, 'Title', 'Artist', 'Album', 1, 1000, 'content://one', " +
                            "'/music/one.flac', '/music', NULL, 'Artist', 'external', 'one.flac', " +
                            "'Music/', 100, 1, 1, 1, 99)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { it.writableDatabase }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                DatabaseProvider.MIGRATION_9_10,
                DatabaseProvider.MIGRATION_10_11,
                DatabaseProvider.MIGRATION_11_12,
                DatabaseProvider.MIGRATION_12_13,
                DatabaseProvider.MIGRATION_13_14,
                DatabaseProvider.MIGRATION_14_15,
                DatabaseProvider.MIGRATION_15_16
            )
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM database_marker WHERE name = 'preserved'"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_track_identities"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM local_track_bindings"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM legacy_listening_baselines"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM listening_events"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM favorite_songs"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM playlists"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM playlist_songs"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM cached_songs"))
            assertEquals(0L, sqlite.longQuery("SELECT COUNT(*) FROM song_ratings"))
            assertEquals(
                1L,
                sqlite.longQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_song_ratings_rating'"
                )
            )
            assertThrows(SQLiteConstraintException::class.java) {
                sqlite.execSQL("INSERT INTO song_ratings VALUES (999, 5, 20, 20)")
            }
            sqlite.execSQL(
                "INSERT INTO listening_track_identities VALUES " +
                    "(2, 'Cascade', 'Artist', 'Album', NULL, 1000, 'cascade', 'artist', " +
                    "'album', NULL, 1, 10, 10)"
            )
            sqlite.execSQL("INSERT INTO song_ratings VALUES (2, 5, 20, 20)")
            sqlite.execSQL("DELETE FROM listening_track_identities WHERE id = 2")
            assertEquals(0L, sqlite.longQuery("SELECT COUNT(*) FROM song_ratings"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun SupportSQLiteDatabase.longQuery(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
