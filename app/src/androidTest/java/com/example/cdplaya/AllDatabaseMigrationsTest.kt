package com.example.cdplaya

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.DatabaseProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllDatabaseMigrationsTest {
    @Test
    fun everySupportedDatabaseVersionMigratesToCurrentWithoutDestructiveReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val migrations = allMigrations()

        for (startVersion in 1..14) {
            val databaseName = "all-migrations-$startVersion-${System.nanoTime()}.db"
            val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(startVersion) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `database_marker` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
                        )
                        migrations.filter { it.endVersion <= startVersion }.forEach { migration ->
                            migration.migrate(db)
                        }
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

            val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .addMigrations(*migrations.toTypedArray())
                .build()
            try {
                database.openHelper.writableDatabase
                assertTrue(database.isOpen)
                assertTrue(database.openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'smart_playlist_definitions'"
                ).use { it.moveToFirst() })
                assertTrue(database.openHelper.writableDatabase.query(
                    "PRAGMA table_info(cached_songs)"
                ).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "year") found = true
                    }
                    found
                })
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    private fun allMigrations(): List<Migration> = listOf(
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
        DatabaseProvider.MIGRATION_14_15
    )
}
