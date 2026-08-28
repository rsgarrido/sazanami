package com.example.cdplaya

import androidx.room.Room
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
class SmartPlaylistMigrationTest {
    @Test
    fun migrationPreservesManualMembershipAndSeedsLegacySmartDefinition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "smart-playlist-migration-${System.nanoTime()}.db"
        val oldMigrations = listOf(
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
            DatabaseProvider.MIGRATION_13_14
        )
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    oldMigrations.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO playlists(playlistId,name,type,artworkMode,folderId,createdAt,updatedAt) VALUES(1,'Manual','MANUAL','AUTOMATIC',NULL,1,2)")
                    db.execSQL("INSERT INTO playlists(playlistId,name,type,artworkMode,folderId,createdAt,updatedAt) VALUES(2,'Smart','SMART','AUTOMATIC',NULL,3,4)")
                    db.execSQL("INSERT INTO playlist_songs(playlistId,songKey,position,title,artist,album,duration,addedAt,mediaStoreId,volumeName,contentUri,relativePath,displayName,fileSizeBytes,dateModifiedEpochSeconds,albumArtist,portableKey,portableKeyVersion) VALUES(1,'manual-key',0,'Song','Artist','Album',100,5,NULL,'','','','',0,0,'','',1)")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                DatabaseProvider.MIGRATION_14_15,
                DatabaseProvider.MIGRATION_15_16,
                DatabaseProvider.MIGRATION_16_17,
                DatabaseProvider.MIGRATION_17_18
            )
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(1L, sqlite.longValue("SELECT COUNT(*) FROM playlist_songs WHERE playlistId=1"))
            assertEquals(0L, sqlite.longValue("SELECT COUNT(*) FROM playlist_songs WHERE playlistId=2"))
            assertEquals("[]", sqlite.stringValue("SELECT rulesJson FROM smart_playlist_definitions WHERE playlistId=2"))
            assertEquals(1L, sqlite.longValue("SELECT isDirty FROM smart_playlist_resolution_states WHERE playlistId=2"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.longValue(sql: String): Long =
    query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.stringValue(sql: String): String =
    query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

