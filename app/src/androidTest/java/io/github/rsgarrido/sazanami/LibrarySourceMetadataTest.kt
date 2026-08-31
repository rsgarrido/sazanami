package io.github.rsgarrido.sazanami

import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.DatabaseProvider
import io.github.rsgarrido.sazanami.data.toCachedSongEntity
import io.github.rsgarrido.sazanami.data.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySourceMetadataTest {
    @Test
    fun songCacheRoundTripPreservesSourceMetadataAndTimestampUnits() {
        val song = Song(
            id = 41L,
            title = "Track",
            artist = "Artist",
            album = "Album",
            trackNumber = 3,
            duration = 234_567L,
            uri = Uri.parse("content://media/external/audio/media/41"),
            filePath = "/storage/emulated/0/Music/Track.flac",
            folderPath = "/storage/emulated/0/Music",
            albumArtUri = Uri.parse("content://art/41"),
            albumArtist = "Album Artist",
            volumeName = "external_primary",
            displayName = "Track.flac",
            relativePath = "Music/",
            fileSizeBytes = 12_345_678L,
            dateAddedEpochSeconds = 1_700_000_123L,
            dateModifiedEpochSeconds = 1_700_000_456L,
            year = 2024,
            artworkEnrichmentVersion = 1,
            genres = listOf("Rock", "Alternative"),
            composers = listOf("Composer One", "Composer; Two"),
            publisher = "Independent Records",
            bpm = 128,
            embeddedMetadataEnrichmentVersion = 1,
            discNumber = 2,
            discTotal = 3
        )

        assertEquals(song, song.toCachedSongEntity(cachedAt = 999L).toSong())
    }

    @Test
    fun cachedSongRoundTripPreservesEmbeddedArtworkReference() {
        val embeddedArtworkUri = Uri.parse(
            "content://io.github.rsgarrido.sazanami.embeddedartwork/v2/source/art.jpg" +
                    "?source=content%3A%2F%2Fmedia%2Fexternal%2Faudio%2Fmedia%2F41" +
                    "&name=Track.flac&modified=10&size=20"
        )
        val song = Song(
            id = 41L,
            title = "Track",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 100L,
            uri = Uri.parse("content://media/external/audio/media/41"),
            filePath = "/storage/Music/Track.flac",
            folderPath = "/storage/Music",
            albumArtUri = embeddedArtworkUri,
            artworkEnrichmentVersion = 1
        )

        assertEquals(
            embeddedArtworkUri,
            song.toCachedSongEntity(cachedAt = 1L).toSong().albumArtUri
        )
    }

    @Test
    fun migrationFiveToNinePreservesCachedAndUserRowsWithSafeDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "metadata-migration-${System.nanoTime()}.db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `favorite_songs` (`songKey` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`songKey`))
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `playlists` (`playlistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `playlist_songs` (`playlistSongId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `songKey` TEXT NOT NULL, `position` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE)
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)")
                    db.execSQL("CREATE INDEX `index_playlist_songs_songKey` ON `playlist_songs` (`songKey`)")
                    db.execSQL(
                        """
                        CREATE TABLE `song_play_stats` (`songKey` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `firstPlayedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`songKey`))
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX `index_song_play_stats_lastPlayedAt` ON `song_play_stats` (`lastPlayedAt`)")
                    db.execSQL("CREATE INDEX `index_song_play_stats_playCount` ON `song_play_stats` (`playCount`)")
                    db.execSQL(
                        """
                        CREATE TABLE `cached_songs` (`mediaStoreId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `trackNumber` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `uriString` TEXT NOT NULL, `filePath` TEXT NOT NULL, `folderPath` TEXT NOT NULL, `albumArtUriString` TEXT, `albumArtist` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaStoreId`))
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX `index_cached_songs_folderPath` ON `cached_songs` (`folderPath`)")
                    db.execSQL("CREATE INDEX `index_cached_songs_title` ON `cached_songs` (`title`)")
                    db.execSQL("INSERT INTO `cached_songs` VALUES (7, 'Title', 'Artist', 'Album', 1, 120000, 'content://song/7', '/music/7.mp3', '/music', NULL, '', 123)")
                    db.execSQL("INSERT INTO `favorite_songs` VALUES ('favorite-key', 'Title', 'Artist', 'Album', 120000, 111)")
                    db.execSQL("INSERT INTO `playlists` (`playlistId`, `name`, `createdAt`, `updatedAt`) VALUES (9, 'Road trip', 222, 333)")
                    db.execSQL("INSERT INTO `playlist_songs` (`playlistSongId`, `playlistId`, `songKey`, `position`, `title`, `artist`, `album`, `duration`, `addedAt`) VALUES (12, 9, 'playlist-key', 4, 'Track', 'Artist', 'Album', 120000, 444)")
                    db.execSQL("INSERT INTO `song_play_stats` VALUES ('history-key', 'Played', 'Artist', 'Album', 120000, 7, 555, 666)")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.writableDatabase.close()
        helper.close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
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
                DatabaseProvider.MIGRATION_18_19,
                DatabaseProvider.MIGRATION_19_20
            )
            .build()
        try {
            val cursor = database.openHelper.writableDatabase.query(
                "SELECT title, volumeName, displayName, fileSizeBytes, dateAddedEpochSeconds, dateModifiedEpochSeconds, artworkEnrichmentVersion, year, genresJson, embeddedMetadataEnrichmentVersion, discNumber, discTotal FROM cached_songs WHERE mediaStoreId = 7"
            )
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("Title", it.getString(0))
                assertEquals("", it.getString(1))
                assertEquals("", it.getString(2))
                assertEquals(0L, it.getLong(3))
                assertEquals(0L, it.getLong(4))
                assertEquals(0L, it.getLong(5))
                assertEquals(0, it.getInt(6))
                assertTrue(it.isNull(7))
                assertEquals("[]", it.getString(8))
                assertEquals(0, it.getInt(9))
                assertTrue(it.isNull(10))
                assertTrue(it.isNull(11))
            }
            database.openHelper.writableDatabase.query(
                "SELECT referenceKey, songKey, createdAt FROM favorite_songs"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("legacy:favorite-key", it.getString(0))
                assertEquals("favorite-key", it.getString(1))
                assertEquals(111L, it.getLong(2))
            }
            database.openHelper.writableDatabase.query(
                "SELECT playlistSongId, playlistId, position, songKey FROM playlist_songs"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(12L, it.getLong(0))
                assertEquals(9L, it.getLong(1))
                assertEquals(4, it.getInt(2))
                assertEquals("playlist-key", it.getString(3))
            }
            database.openHelper.writableDatabase.query(
                "SELECT referenceKey, playCount, firstPlayedAt, lastPlayedAt FROM song_play_stats"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("legacy:history-key", it.getString(0))
                assertEquals(7, it.getInt(1))
                assertEquals(555L, it.getLong(2))
                assertEquals(666L, it.getLong(3))
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
