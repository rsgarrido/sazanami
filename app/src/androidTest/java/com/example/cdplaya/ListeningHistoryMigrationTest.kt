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
class ListeningHistoryMigrationTest {
    @Test
    fun migrationEightToNinePreservesEveryLegacyAggregateWithoutSyntheticEvents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "listening-history-8-9-${System.nanoTime()}.db"
        createVersionEightDatabase(databaseName)

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                DatabaseProvider.MIGRATION_8_9,
                DatabaseProvider.MIGRATION_9_10,
                DatabaseProvider.MIGRATION_10_11,
                DatabaseProvider.MIGRATION_11_12,
                DatabaseProvider.MIGRATION_12_13,
                DatabaseProvider.MIGRATION_13_14,
                DatabaseProvider.MIGRATION_14_15
            )
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(3L, sqlite.longQuery("SELECT COUNT(*) FROM song_play_stats"))
            assertEquals(3L, sqlite.longQuery("SELECT COUNT(*) FROM listening_track_identities"))
            assertEquals(3L, sqlite.longQuery("SELECT COUNT(*) FROM local_track_bindings"))
            assertEquals(3L, sqlite.longQuery("SELECT COUNT(*) FROM legacy_listening_baselines"))
            assertEquals(0L, sqlite.longQuery("SELECT COUNT(*) FROM listening_events"))
            assertEquals(
                0L,
                sqlite.longQuery(
                    """
                    SELECT COUNT(*)
                    FROM song_play_stats s
                    LEFT JOIN legacy_listening_baselines b
                      ON b.legacyReferenceKey = s.referenceKey
                    WHERE b.trackIdentityId IS NULL
                       OR b.historicalPlayCount != s.playCount
                       OR b.firstKnownPlayedAt != s.firstPlayedAt
                       OR b.lastKnownPlayedAt != s.lastPlayedAt
                    """.trimIndent()
                )
            )

            sqlite.query(
                """
                SELECT b.legacyReferenceKey, b.historicalPlayCount,
                       b.firstKnownPlayedAt, b.lastKnownPlayedAt,
                       i.titleSnapshot, i.artistSnapshot
                FROM legacy_listening_baselines b
                JOIN listening_track_identities i ON i.id = b.trackIdentityId
                ORDER BY b.legacyReferenceKey
                """.trimIndent()
            ).use { cursor ->
                val rows = mutableListOf<List<Any>>()
                while (cursor.moveToNext()) {
                    rows += listOf(
                        cursor.getString(0),
                        cursor.getInt(1),
                        cursor.getLong(2),
                        cursor.getLong(3),
                        cursor.getString(4),
                        cursor.getString(5)
                    )
                }
                assertEquals(
                    listOf(
                        listOf("local:first", 2, 100L, 200L, "L'été – Song!", "Beyoncé"),
                        listOf("local:second", 11, 300L, 900L, "L'été – Song!", "Beyoncé"),
                        listOf("portable:incomplete", 37, 1_000L, 4_000L, "曲（Live）", "演奏者")
                    ),
                    rows
                )
            }
            assertEquals(
                2L,
                sqlite.longQuery(
                    "SELECT COUNT(*) FROM listening_track_identities WHERE normalizedTitle = 'l''été – song!'"
                )
            )
            assertEquals(
                1L,
                sqlite.longQuery(
                    "SELECT COUNT(*) FROM local_track_bindings WHERE referenceKey = 'portable:incomplete' AND mediaStoreId IS NULL"
                )
            )

            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM favorite_songs WHERE referenceKey = 'local:favorite'"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM playlists WHERE playlistId = 7 AND name = 'Road & Rail'"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM playlist_songs WHERE playlistSongId = 8 AND playlistId = 7"))
            assertEquals(1L, sqlite.longQuery("SELECT COUNT(*) FROM cached_songs WHERE mediaStoreId = 99 AND title = 'Cached'"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersionEightDatabase(databaseName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val migrations = listOf(
            DatabaseProvider.MIGRATION_1_2,
            DatabaseProvider.MIGRATION_2_3,
            DatabaseProvider.MIGRATION_3_4,
            DatabaseProvider.MIGRATION_4_5,
            DatabaseProvider.MIGRATION_5_6,
            DatabaseProvider.MIGRATION_6_7,
            DatabaseProvider.MIGRATION_7_8
        )
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    migrations.forEach { it.migrate(db) }
                    seedLegacyRows(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }
    }

    private fun seedLegacyRows(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO favorite_songs VALUES
            ('local:favorite', 'favorite-song', 'Favorite', 'Artist', 'Album', 100000, 10,
             4, 'external_primary', 'content://favorite/4', 'Music/', 'Favorite.flac',
             1000, 20, 'Artist', 'portable:favorite', 1)
            """.trimIndent()
        )
        db.execSQL("INSERT INTO playlists VALUES (7, 'Road & Rail', 10, 20)")
        db.execSQL(
            """
            INSERT INTO playlist_songs VALUES
            (8, 7, 'playlist-song', 0, 'Playlist Song', 'Artist', 'Album', 110000, 30,
             5, 'external_primary', 'content://playlist/5', 'Music/', 'Playlist.flac',
             1100, 21, 'Artist', 'portable:playlist', 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO cached_songs VALUES
            (99, 'Cached', 'Artist', 'Album', 1, 120000, 'content://cached/99',
             '/storage/Music/Cached.flac', '/storage/Music', NULL, 'Artist', 40,
             'external_primary', 'Cached.flac', 'Music/', 1200, 50, 60, 0)
            """.trimIndent()
        )
        insertHistory(
            db = db,
            referenceKey = "local:first",
            songKey = "legacy-first",
            title = "L'été – Song!",
            artist = "Beyoncé",
            album = "Déjà Vu",
            playCount = 2,
            firstPlayedAt = 100,
            lastPlayedAt = 200,
            mediaStoreId = "41",
            contentUri = "content://media/external/audio/41",
            displayName = "L'été – Song!.flac"
        )
        insertHistory(
            db = db,
            referenceKey = "local:second",
            songKey = "legacy-second",
            title = "L'été – Song!",
            artist = "Beyoncé",
            album = "Déjà Vu",
            playCount = 11,
            firstPlayedAt = 300,
            lastPlayedAt = 900,
            mediaStoreId = "42",
            contentUri = "content://media/external/audio/42",
            displayName = "duplicate.flac"
        )
        insertHistory(
            db = db,
            referenceKey = "portable:incomplete",
            songKey = "legacy-unicode",
            title = "曲（Live）",
            artist = "演奏者",
            album = "夜",
            playCount = 37,
            firstPlayedAt = 1_000,
            lastPlayedAt = 4_000,
            mediaStoreId = "NULL",
            contentUri = "",
            displayName = ""
        )
    }

    private fun insertHistory(
        db: SupportSQLiteDatabase,
        referenceKey: String,
        songKey: String,
        title: String,
        artist: String,
        album: String,
        playCount: Int,
        firstPlayedAt: Long,
        lastPlayedAt: Long,
        mediaStoreId: String,
        contentUri: String,
        displayName: String
    ) {
        db.execSQL(
            """
            INSERT INTO song_play_stats
            (referenceKey, songKey, title, artist, album, duration, playCount,
             firstPlayedAt, lastPlayedAt, mediaStoreId, volumeName, contentUri,
             relativePath, displayName, fileSizeBytes, dateModifiedEpochSeconds,
             albumArtist, portableKey, portableKeyVersion)
            VALUES (?, ?, ?, ?, ?, 180000, ?, ?, ?, $mediaStoreId, ?, ?, ?, ?, 12345, 67890, ?, ?, 1)
            """.trimIndent(),
            arrayOf(
                referenceKey,
                songKey,
                title,
                artist,
                album,
                playCount,
                firstPlayedAt,
                lastPlayedAt,
                if (mediaStoreId == "NULL") "" else "external_primary",
                contentUri,
                if (contentUri.isBlank()) "" else "Music/Unicode & Punctuation/",
                displayName,
                artist,
                "portable:v1:$songKey"
            )
        )
    }

    private fun SupportSQLiteDatabase.longQuery(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
