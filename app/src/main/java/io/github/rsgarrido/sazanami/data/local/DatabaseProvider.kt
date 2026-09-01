package io.github.rsgarrido.sazanami.data.local

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.rsgarrido.sazanami.data.identityNormalized

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        SmartPlaylistDatabaseTriggers.install(db)
                    }
                })
                .build()
                .also { database ->
                    instance = database
                }
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorite_songs` (
                    `songKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`songKey`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `playlistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_songs` (
                    `playlistSongId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `playlistId` INTEGER NOT NULL,
                    `songKey` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songKey` ON `playlist_songs` (`songKey`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `song_play_stats` (
                `songKey` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `album` TEXT NOT NULL,
                `duration` INTEGER NOT NULL,
                `playCount` INTEGER NOT NULL,
                `firstPlayedAt` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songKey`)
            )
            """.trimIndent()
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_song_play_stats_lastPlayedAt` ON `song_play_stats` (`lastPlayedAt`)"
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_song_play_stats_playCount` ON `song_play_stats` (`playCount`)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `cached_songs` (
                `mediaStoreId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `album` TEXT NOT NULL,
                `trackNumber` INTEGER NOT NULL,
                `duration` INTEGER NOT NULL,
                `uriString` TEXT NOT NULL,
                `filePath` TEXT NOT NULL,
                `folderPath` TEXT NOT NULL,
                `albumArtUriString` TEXT,
                `albumArtist` TEXT NOT NULL,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`mediaStoreId`)
            )
            """.trimIndent()
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_songs_folderPath` ON `cached_songs` (`folderPath`)"
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_songs_title` ON `cached_songs` (`title`)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `volumeName` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `displayName` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `relativePath` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `fileSizeBytes` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `dateAddedEpochSeconds` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `dateModifiedEpochSeconds` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE `favorite_songs_new` (
                    `referenceKey` TEXT NOT NULL,
                    `songKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `mediaStoreId` INTEGER,
                    `volumeName` TEXT NOT NULL,
                    `contentUri` TEXT NOT NULL,
                    `relativePath` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `dateModifiedEpochSeconds` INTEGER NOT NULL,
                    `albumArtist` TEXT NOT NULL,
                    `portableKey` TEXT NOT NULL,
                    `portableKeyVersion` INTEGER NOT NULL,
                    PRIMARY KEY(`referenceKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `favorite_songs_new`
                    (`referenceKey`, `songKey`, `title`, `artist`, `album`, `duration`, `createdAt`,
                     `mediaStoreId`, `volumeName`, `contentUri`, `relativePath`, `displayName`,
                     `fileSizeBytes`, `dateModifiedEpochSeconds`, `albumArtist`, `portableKey`, `portableKeyVersion`)
                SELECT 'legacy:' || `songKey`, `songKey`, `title`, `artist`, `album`, `duration`, `createdAt`,
                       NULL, '', '', '', '', 0, 0, '', '', 1
                FROM `favorite_songs`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `favorite_songs`")
            db.execSQL("ALTER TABLE `favorite_songs_new` RENAME TO `favorite_songs`")
            db.execSQL("CREATE INDEX `index_favorite_songs_songKey` ON `favorite_songs` (`songKey`)")

            addReferenceColumns(db, "playlist_songs")

            db.execSQL(
                """
                CREATE TABLE `song_play_stats_new` (
                    `referenceKey` TEXT NOT NULL,
                    `songKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `playCount` INTEGER NOT NULL,
                    `firstPlayedAt` INTEGER NOT NULL,
                    `lastPlayedAt` INTEGER NOT NULL,
                    `mediaStoreId` INTEGER,
                    `volumeName` TEXT NOT NULL,
                    `contentUri` TEXT NOT NULL,
                    `relativePath` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `dateModifiedEpochSeconds` INTEGER NOT NULL,
                    `albumArtist` TEXT NOT NULL,
                    `portableKey` TEXT NOT NULL,
                    `portableKeyVersion` INTEGER NOT NULL,
                    PRIMARY KEY(`referenceKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `song_play_stats_new`
                    (`referenceKey`, `songKey`, `title`, `artist`, `album`, `duration`, `playCount`,
                     `firstPlayedAt`, `lastPlayedAt`, `mediaStoreId`, `volumeName`, `contentUri`,
                     `relativePath`, `displayName`, `fileSizeBytes`, `dateModifiedEpochSeconds`,
                     `albumArtist`, `portableKey`, `portableKeyVersion`)
                SELECT 'legacy:' || `songKey`, `songKey`, `title`, `artist`, `album`, `duration`, `playCount`,
                       `firstPlayedAt`, `lastPlayedAt`, NULL, '', '', '', '', 0, 0, '', '', 1
                FROM `song_play_stats`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `song_play_stats`")
            db.execSQL("ALTER TABLE `song_play_stats_new` RENAME TO `song_play_stats`")
            db.execSQL("CREATE INDEX `index_song_play_stats_songKey` ON `song_play_stats` (`songKey`)")
            db.execSQL("CREATE INDEX `index_song_play_stats_lastPlayedAt` ON `song_play_stats` (`lastPlayedAt`)")
            db.execSQL("CREATE INDEX `index_song_play_stats_playCount` ON `song_play_stats` (`playCount`)")
        }

        private fun addReferenceColumns(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `mediaStoreId` INTEGER")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `volumeName` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `contentUri` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `relativePath` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `displayName` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `fileSizeBytes` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `dateModifiedEpochSeconds` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `albumArtist` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `portableKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `portableKeyVersion` INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `artworkEnrichmentVersion` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createListeningHistoryTables(db)
            migrateLegacyListeningBaselines(db, migratedAt = System.currentTimeMillis())
        }

        private fun createListeningHistoryTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `listening_track_identities` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `titleSnapshot` TEXT NOT NULL,
                    `artistSnapshot` TEXT NOT NULL,
                    `albumSnapshot` TEXT NOT NULL,
                    `albumArtistSnapshot` TEXT,
                    `durationMsSnapshot` INTEGER,
                    `normalizedTitle` TEXT NOT NULL,
                    `normalizedArtist` TEXT NOT NULL,
                    `normalizedAlbum` TEXT NOT NULL,
                    `metadataKey` TEXT,
                    `metadataKeyVersion` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_listening_track_identities_normalizedArtist_normalizedTitle_durationMsSnapshot` ON `listening_track_identities` (`normalizedArtist`, `normalizedTitle`, `durationMsSnapshot`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_listening_track_identities_normalizedAlbum` ON `listening_track_identities` (`normalizedAlbum`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_listening_track_identities_metadataKey` ON `listening_track_identities` (`metadataKey`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_track_bindings` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackIdentityId` INTEGER NOT NULL,
                    `referenceKey` TEXT NOT NULL,
                    `mediaStoreId` INTEGER,
                    `volumeName` TEXT,
                    `contentUri` TEXT,
                    `relativePath` TEXT,
                    `displayName` TEXT,
                    `absolutePath` TEXT,
                    `fileSizeBytes` INTEGER,
                    `dateModifiedEpochSeconds` INTEGER,
                    `durationMsSnapshot` INTEGER,
                    `legacyStableKey` TEXT,
                    `portableKey` TEXT,
                    `portableKeyVersion` INTEGER,
                    `firstSeenAt` INTEGER NOT NULL,
                    `lastSeenAt` INTEGER NOT NULL,
                    `missingSince` INTEGER,
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_local_track_bindings_trackIdentityId` ON `local_track_bindings` (`trackIdentityId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_track_bindings_referenceKey` ON `local_track_bindings` (`referenceKey`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_local_track_bindings_volumeName_mediaStoreId` ON `local_track_bindings` (`volumeName`, `mediaStoreId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_local_track_bindings_portableKey` ON `local_track_bindings` (`portableKey`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `listening_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `eventUuid` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `trackIdentityId` INTEGER NOT NULL,
                    `localTrackBindingId` INTEGER,
                    `playbackSessionId` TEXT,
                    `startedAt` INTEGER NOT NULL,
                    `endedAt` INTEGER NOT NULL,
                    `listenedMs` INTEGER NOT NULL,
                    `trackDurationMs` INTEGER,
                    `qualifiedAsPlay` INTEGER NOT NULL,
                    `qualificationReason` TEXT NOT NULL,
                    `qualificationRuleVersion` INTEGER NOT NULL,
                    `endReason` TEXT NOT NULL,
                    `sourceEventKey` TEXT,
                    `importBatchId` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`localTrackBindingId`) REFERENCES `local_track_bindings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_listening_events_eventUuid` ON `listening_events` (`eventUuid`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_listening_events_playbackSessionId` ON `listening_events` (`playbackSessionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_listening_events_source_sourceEventKey` ON `listening_events` (`source`, `sourceEventKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_trackIdentityId_startedAt` ON `listening_events` (`trackIdentityId`, `startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_localTrackBindingId` ON `listening_events` (`localTrackBindingId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_qualifiedAsPlay_startedAt` ON `listening_events` (`qualifiedAsPlay`, `startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_source_startedAt` ON `listening_events` (`source`, `startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_importBatchId` ON `listening_events` (`importBatchId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `legacy_listening_baselines` (
                    `trackIdentityId` INTEGER NOT NULL,
                    `historicalPlayCount` INTEGER NOT NULL,
                    `firstKnownPlayedAt` INTEGER NOT NULL,
                    `lastKnownPlayedAt` INTEGER NOT NULL,
                    `legacyReferenceKey` TEXT NOT NULL,
                    `migratedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`trackIdentityId`),
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_legacy_listening_baselines_legacyReferenceKey` ON `legacy_listening_baselines` (`legacyReferenceKey`)"
            )
        }

        private fun migrateLegacyListeningBaselines(
            db: SupportSQLiteDatabase,
            migratedAt: Long
        ) {
            db.query("SELECT * FROM `song_play_stats` ORDER BY `referenceKey`").use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.requiredString("title")
                    val artist = cursor.requiredString("artist")
                    val album = cursor.requiredString("album")
                    val duration = cursor.requiredLong("duration")
                    val portableKey = cursor.requiredString("portableKey").ifBlank { null }
                    val identityId = db.insert(
                        "listening_track_identities",
                        SQLiteDatabase.CONFLICT_ABORT,
                        ContentValues().apply {
                            put("titleSnapshot", title)
                            put("artistSnapshot", artist)
                            put("albumSnapshot", album)
                            put("albumArtistSnapshot", cursor.requiredString("albumArtist"))
                            put("durationMsSnapshot", duration)
                            put("normalizedTitle", title.identityNormalized())
                            put("normalizedArtist", artist.identityNormalized())
                            put("normalizedAlbum", album.identityNormalized())
                            if (portableKey == null) putNull("metadataKey") else put("metadataKey", portableKey)
                            put("metadataKeyVersion", cursor.requiredInt("portableKeyVersion"))
                            put("createdAt", migratedAt)
                            put("updatedAt", migratedAt)
                        }
                    )

                    db.insert(
                        "local_track_bindings",
                        SQLiteDatabase.CONFLICT_ABORT,
                        ContentValues().apply {
                            put("trackIdentityId", identityId)
                            put("referenceKey", cursor.requiredString("referenceKey"))
                            cursor.putNullableLong(this, "mediaStoreId")
                            putOptionalString("volumeName", cursor.requiredString("volumeName"))
                            putOptionalString("contentUri", cursor.requiredString("contentUri"))
                            putOptionalString("relativePath", cursor.requiredString("relativePath"))
                            putOptionalString("displayName", cursor.requiredString("displayName"))
                            putNull("absolutePath")
                            put("fileSizeBytes", cursor.requiredLong("fileSizeBytes"))
                            put("dateModifiedEpochSeconds", cursor.requiredLong("dateModifiedEpochSeconds"))
                            put("durationMsSnapshot", duration)
                            putOptionalString("legacyStableKey", cursor.requiredString("songKey"))
                            if (portableKey == null) putNull("portableKey") else put("portableKey", portableKey)
                            put("portableKeyVersion", cursor.requiredInt("portableKeyVersion"))
                            put("firstSeenAt", migratedAt)
                            put("lastSeenAt", migratedAt)
                            putNull("missingSince")
                        }
                    )

                    db.insert(
                        "legacy_listening_baselines",
                        SQLiteDatabase.CONFLICT_ABORT,
                        ContentValues().apply {
                            put("trackIdentityId", identityId)
                            put("historicalPlayCount", cursor.requiredInt("playCount"))
                            put("firstKnownPlayedAt", cursor.requiredLong("firstPlayedAt"))
                            put("lastKnownPlayedAt", cursor.requiredLong("lastPlayedAt"))
                            put("legacyReferenceKey", cursor.requiredString("referenceKey"))
                            put("migratedAt", migratedAt)
                        }
                    )
                }
            }
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `song_ratings` (
                    `trackIdentityId` INTEGER NOT NULL,
                    `rating` INTEGER NOT NULL,
                    `ratedAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`trackIdentityId`),
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_song_ratings_rating` ON `song_ratings` (`rating`)"
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `listening_import_sources` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `stableUuid` TEXT NOT NULL, `sourceType` TEXT NOT NULL,
                    `displayLabel` TEXT NOT NULL, `accountIdentityDigest` TEXT,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX `index_listening_import_sources_stableUuid` ON `listening_import_sources` (`stableUuid`)")
            db.execSQL("CREATE INDEX `index_listening_import_sources_sourceType` ON `listening_import_sources` (`sourceType`)")
            db.execSQL("CREATE UNIQUE INDEX `index_listening_import_sources_sourceType_accountIdentityDigest` ON `listening_import_sources` (`sourceType`, `accountIdentityDigest`)")
            db.execSQL("CREATE INDEX `index_listening_import_sources_displayLabel_id` ON `listening_import_sources` (`displayLabel`, `id`)")

            db.execSQL("""
                INSERT INTO listening_import_sources(stableUuid, sourceType, displayLabel, accountIdentityDigest, createdAt, updatedAt)
                SELECT 'legacy-unscoped:' || source, source, 'Legacy unscoped ' || source, NULL,
                       COALESCE(MIN(createdAt), 0), COALESCE(MAX(createdAt), 0)
                FROM listening_events WHERE source != 'native' GROUP BY source
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `listening_import_batches` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stableUuid` TEXT NOT NULL,
                    `sourceProfileId` INTEGER NOT NULL, `status` TEXT NOT NULL,
                    `parserVersion` INTEGER NOT NULL, `qualificationPolicy` TEXT NOT NULL,
                    `qualificationRuleVersion` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER, `sourceRangeStart` INTEGER, `sourceRangeEnd` INTEGER,
                    `parsedCount` INTEGER NOT NULL, `insertedCount` INTEGER NOT NULL,
                    `duplicateCount` INTEGER NOT NULL, `ignoredCount` INTEGER NOT NULL,
                    `invalidCount` INTEGER NOT NULL, `exactMatchCount` INTEGER NOT NULL,
                    `ambiguousMatchCount` INTEGER NOT NULL, `unmatchedCount` INTEGER NOT NULL,
                    `qualifiedCount` INTEGER NOT NULL, `failureCategory` TEXT,
                    `createdAppVersion` TEXT NOT NULL,
                    FOREIGN KEY(`sourceProfileId`) REFERENCES `listening_import_sources`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX `index_listening_import_batches_stableUuid` ON `listening_import_batches` (`stableUuid`)")
            db.execSQL("CREATE INDEX `index_listening_import_batches_sourceProfileId` ON `listening_import_batches` (`sourceProfileId`)")
            db.execSQL("CREATE INDEX `index_listening_import_batches_status` ON `listening_import_batches` (`status`)")
            db.execSQL("CREATE INDEX `index_listening_import_batches_startedAt` ON `listening_import_batches` (`startedAt`)")
            db.execSQL("""
                INSERT INTO listening_import_batches(
                    stableUuid, sourceProfileId, status, parserVersion, qualificationPolicy,
                    qualificationRuleVersion, startedAt, completedAt, sourceRangeStart, sourceRangeEnd,
                    parsedCount, insertedCount, duplicateCount, ignoredCount, invalidCount,
                    exactMatchCount, ambiguousMatchCount, unmatchedCount, qualifiedCount,
                    failureCategory, createdAppVersion)
                SELECT 'legacy-batch:' || e.source || ':' || e.importBatchId, s.id, 'published', 0,
                       CASE e.source WHEN 'spotify_import' THEN 'spotify' WHEN 'lastfm_import' THEN 'lastfm' ELSE 'other_import' END,
                       MAX(e.qualificationRuleVersion), MIN(e.startedAt), MAX(e.endedAt), MIN(e.startedAt), MAX(e.endedAt),
                       COUNT(*), COUNT(*), 0, 0, 0, 0, 0, COUNT(*),
                       SUM(CASE WHEN e.qualifiedAsPlay = 1 THEN 1 ELSE 0 END), NULL, 'room10-legacy'
                FROM listening_events e JOIN listening_import_sources s ON s.sourceType = e.source
                WHERE e.source != 'native' AND e.importBatchId IS NOT NULL
                GROUP BY e.source, e.importBatchId
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE `listening_events_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventUuid` TEXT NOT NULL,
                    `source` TEXT NOT NULL, `trackIdentityId` INTEGER NOT NULL,
                    `localTrackBindingId` INTEGER, `playbackSessionId` TEXT,
                    `startedAt` INTEGER, `endedAt` INTEGER, `attributionAt` INTEGER NOT NULL,
                    `timestampEvidence` TEXT NOT NULL, `listenedMs` INTEGER NOT NULL,
                    `trackDurationMs` INTEGER, `qualifiedAsPlay` INTEGER NOT NULL,
                    `qualificationReason` TEXT NOT NULL, `qualificationRuleVersion` INTEGER NOT NULL,
                    `qualificationPolicy` TEXT NOT NULL, `endReason` TEXT,
                    `completionClassification` TEXT NOT NULL, `publicationState` TEXT NOT NULL,
                    `sourceEventKey` TEXT, `importBatchId` INTEGER, `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`localTrackBindingId`) REFERENCES `local_track_bindings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO listening_events_new(
                    id,eventUuid,source,trackIdentityId,localTrackBindingId,playbackSessionId,
                    startedAt,endedAt,attributionAt,timestampEvidence,listenedMs,trackDurationMs,
                    qualifiedAsPlay,qualificationReason,qualificationRuleVersion,qualificationPolicy,
                    endReason,completionClassification,publicationState,sourceEventKey,importBatchId,createdAt)
                SELECT id,eventUuid,source,trackIdentityId,localTrackBindingId,playbackSessionId,
                    startedAt,endedAt,startedAt,'native_exact',listenedMs,trackDurationMs,
                    qualifiedAsPlay,qualificationReason,qualificationRuleVersion,
                    CASE source WHEN 'native' THEN 'native' WHEN 'spotify_import' THEN 'spotify' WHEN 'lastfm_import' THEN 'lastfm' ELSE 'other_import' END,
                    endReason,
                    CASE WHEN source = 'native' AND endReason = 'natural_end' THEN 'native_natural' ELSE 'none' END,
                    CASE WHEN source = 'native' THEN 'native' ELSE 'import_published' END,
                    sourceEventKey,importBatchId,createdAt FROM listening_events
            """.trimIndent())
            db.execSQL("DROP TABLE listening_events")
            db.execSQL("ALTER TABLE listening_events_new RENAME TO listening_events")
            db.execSQL("CREATE UNIQUE INDEX `index_listening_events_eventUuid` ON `listening_events` (`eventUuid`)")
            db.execSQL("CREATE UNIQUE INDEX `index_listening_events_playbackSessionId` ON `listening_events` (`playbackSessionId`)")
            db.execSQL("CREATE UNIQUE INDEX `index_listening_events_source_sourceEventKey` ON `listening_events` (`source`, `sourceEventKey`)")
            db.execSQL("CREATE INDEX `index_listening_events_trackIdentityId_attributionAt` ON `listening_events` (`trackIdentityId`, `attributionAt`)")
            db.execSQL("CREATE INDEX `index_listening_events_localTrackBindingId` ON `listening_events` (`localTrackBindingId`)")
            db.execSQL("CREATE INDEX `index_listening_events_qualifiedAsPlay_publicationState_attributionAt` ON `listening_events` (`qualifiedAsPlay`, `publicationState`, `attributionAt`)")
            db.execSQL("CREATE INDEX `index_listening_events_source_publicationState_attributionAt` ON `listening_events` (`source`, `publicationState`, `attributionAt`)")
            db.execSQL("CREATE INDEX `index_listening_events_publicationState_attributionAt` ON `listening_events` (`publicationState`, `attributionAt`)")

            db.execSQL("""
                CREATE TABLE `listening_track_external_ids` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackIdentityId` INTEGER NOT NULL,
                    `sourceType` TEXT NOT NULL, `externalId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                    `lastSeenAt` INTEGER NOT NULL,
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("CREATE INDEX `index_listening_track_external_ids_trackIdentityId` ON `listening_track_external_ids` (`trackIdentityId`)")
            db.execSQL("CREATE UNIQUE INDEX `index_listening_track_external_ids_sourceType_externalId` ON `listening_track_external_ids` (`sourceType`, `externalId`)")
            db.execSQL("""
                CREATE TABLE `imported_listening_event_evidence` (
                    `eventId` INTEGER NOT NULL, `sourceProfileId` INTEGER NOT NULL,
                    `fingerprintVersion` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL,
                    `duplicateOrdinal` INTEGER NOT NULL, `normalizedReasonStart` TEXT,
                    `normalizedReasonEnd` TEXT, `skippedState` TEXT NOT NULL,
                    `matchDispositionAtImport` TEXT NOT NULL, PRIMARY KEY(`eventId`),
                    FOREIGN KEY(`eventId`) REFERENCES `listening_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`sourceProfileId`) REFERENCES `listening_import_sources`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)
            """.trimIndent())
            db.execSQL("CREATE INDEX `index_imported_listening_event_evidence_sourceProfileId` ON `imported_listening_event_evidence` (`sourceProfileId`)")
            db.execSQL("CREATE UNIQUE INDEX `index_imported_listening_event_evidence_sourceProfileId_fingerprintVersion_fingerprint_duplicateOrdinal` ON `imported_listening_event_evidence` (`sourceProfileId`,`fingerprintVersion`,`fingerprint`,`duplicateOrdinal`)")
            db.execSQL("""
                CREATE TABLE `listening_import_batch_events` (`batchId` INTEGER NOT NULL, `eventId` INTEGER NOT NULL,
                    PRIMARY KEY(`batchId`,`eventId`),
                    FOREIGN KEY(`batchId`) REFERENCES `listening_import_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`eventId`) REFERENCES `listening_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("CREATE INDEX `index_listening_import_batch_events_eventId` ON `listening_import_batch_events` (`eventId`)")
            db.execSQL("""
                INSERT INTO listening_import_batch_events(batchId,eventId)
                SELECT b.id,e.id FROM listening_events e JOIN listening_import_batches b
                  ON b.stableUuid = 'legacy-batch:' || e.source || ':' || e.importBatchId
                WHERE e.importBatchId IS NOT NULL
            """.trimIndent())
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `listening_identity_reconciliations` (
                    `sourceIdentityId` INTEGER NOT NULL,
                    `targetIdentityId` INTEGER NOT NULL,
                    `reconciledAt` INTEGER NOT NULL,
                    PRIMARY KEY(`sourceIdentityId`),
                    FOREIGN KEY(`sourceIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`targetIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_listening_identity_reconciliations_targetIdentityId` " +
                        "ON `listening_identity_reconciliations` (`targetIdentityId`)"
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `playlists` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'MANUAL'"
            )
            db.execSQL(
                "ALTER TABLE `playlists` ADD COLUMN `artworkMode` TEXT NOT NULL DEFAULT 'AUTOMATIC'"
            )
            db.execSQL(
                "ALTER TABLE `playlists` ADD COLUMN `artworkReference` TEXT"
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_folders` (
                    `folderId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE `playlists`
                ADD COLUMN `folderId` INTEGER DEFAULT NULL
                    REFERENCES `playlist_folders`(`folderId`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlists_folderId` " +
                        "ON `playlists` (`folderId`)"
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `cached_songs` ADD COLUMN `year` INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `smart_playlist_definitions` (
                    `playlistId` INTEGER NOT NULL,
                    `matchMode` TEXT NOT NULL,
                    `rulesJson` TEXT NOT NULL,
                    `sortField` TEXT NOT NULL,
                    `sortDirection` TEXT NOT NULL,
                    `resultLimit` INTEGER,
                    `definitionVersion` INTEGER NOT NULL,
                    `dependencyMask` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_playlist_definitions_dependencyMask` ON `smart_playlist_definitions` (`dependencyMask`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `smart_playlist_resolution_states` (
                    `playlistId` INTEGER NOT NULL,
                    `isDirty` INTEGER NOT NULL DEFAULT 1,
                    `resolvedAt` INTEGER,
                    `validUntil` INTEGER,
                    `resultCount` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`playlistId`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `smart_playlist_cached_songs` (
                    `playlistId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `mediaStoreId` INTEGER NOT NULL,
                    `volumeName` TEXT NOT NULL,
                    PRIMARY KEY(`playlistId`, `position`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_playlist_cached_songs_mediaStoreId_volumeName` ON `smart_playlist_cached_songs` (`mediaStoreId`, `volumeName`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `generated_playlist_states` (
                    `playlistId` INTEGER NOT NULL,
                    `templateKey` TEXT NOT NULL,
                    `membershipMode` TEXT NOT NULL DEFAULT 'snapshot',
                    `refreshPolicy` TEXT NOT NULL,
                    `refreshIntervalMillis` INTEGER,
                    `lastRefreshedAt` INTEGER,
                    `snapshotVersion` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_generated_playlist_states_templateKey` ON `generated_playlist_states` (`templateKey`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `generated_playlist_songs` (
                    `playlistId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `songKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `mediaStoreId` INTEGER,
                    `volumeName` TEXT NOT NULL DEFAULT '',
                    `contentUri` TEXT NOT NULL DEFAULT '',
                    `relativePath` TEXT NOT NULL DEFAULT '',
                    `displayName` TEXT NOT NULL DEFAULT '',
                    `fileSizeBytes` INTEGER NOT NULL DEFAULT 0,
                    `dateModifiedEpochSeconds` INTEGER NOT NULL DEFAULT 0,
                    `albumArtist` TEXT NOT NULL DEFAULT '',
                    `portableKey` TEXT NOT NULL DEFAULT '',
                    `portableKeyVersion` INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY(`playlistId`, `position`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_generated_playlist_songs_songKey` ON `generated_playlist_songs` (`songKey`)")
            db.execSQL(
                """
                INSERT OR IGNORE INTO smart_playlist_definitions(
                    playlistId, matchMode, rulesJson, sortField, sortDirection,
                    resultLimit, definitionVersion, dependencyMask, updatedAt
                )
                SELECT playlistId, 'ALL', '[]', 'title', 'ASC', NULL, 1,
                       ${SmartPlaylistDependencies.LIBRARY}, updatedAt
                FROM playlists
                WHERE type = 'SMART'
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO smart_playlist_resolution_states(
                    playlistId, isDirty, resolvedAt, validUntil, resultCount
                )
                SELECT playlistId, 1, NULL, NULL, 0
                FROM smart_playlist_definitions
                """.trimIndent()
            )
            SmartPlaylistDatabaseTriggers.install(db)
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `genresJson` TEXT NOT NULL DEFAULT '[]'"
            )
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `embeddedMetadataEnrichmentVersion` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `normalizedGenresJson` TEXT NOT NULL DEFAULT '[]'"
            )
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `composersJson` TEXT NOT NULL DEFAULT '[]'"
            )
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `composerText` TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `publisher` TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `bpm` INTEGER"
            )
            // Cached rows are deliberately re-enriched so the new columns come from current tags.
            db.execSQL(
                "UPDATE `cached_songs` SET `embeddedMetadataEnrichmentVersion` = 0"
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `discNumber` INTEGER"
            )
            db.execSQL(
                "ALTER TABLE `cached_songs` ADD COLUMN `discTotal` INTEGER"
            )
            // Re-read embedded tags once so existing cached libraries gain disc metadata.
            db.execSQL(
                "UPDATE `cached_songs` SET `embeddedMetadataEnrichmentVersion` = 0"
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `artist_picture_assignments` (
                    `artistKey` TEXT NOT NULL,
                    `normalizedArtistName` TEXT NOT NULL,
                    `assetReference` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`artistKey`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_queues` (
                    `queueId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `lastActiveAt` INTEGER NOT NULL,
                    `sourceType` TEXT,
                    `sourceKey` TEXT,
                    `currentEntryId` TEXT,
                    `currentPositionMs` INTEGER NOT NULL,
                    `shuffleEnabled` INTEGER NOT NULL,
                    `repeatMode` TEXT NOT NULL,
                    PRIMARY KEY(`queueId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queues_lastActiveAt` " +
                    "ON `playback_queues` (`lastActiveAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queues_updatedAt` " +
                    "ON `playback_queues` (`updatedAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queues_currentEntryId` " +
                    "ON `playback_queues` (`currentEntryId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queues_sourceType_sourceKey` " +
                    "ON `playback_queues` (`sourceType`, `sourceKey`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_queue_entries` (
                    `entryId` TEXT NOT NULL,
                    `queueId` TEXT NOT NULL,
                    `trackIdentityId` INTEGER NOT NULL,
                    `localTrackBindingId` INTEGER,
                    `baseOrder` INTEGER NOT NULL,
                    `playbackOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`entryId`),
                    FOREIGN KEY(`queueId`) REFERENCES `playback_queues`(`queueId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`trackIdentityId`) REFERENCES `listening_track_identities`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`localTrackBindingId`) REFERENCES `local_track_bindings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_queue_entries_queueId_baseOrder` " +
                    "ON `playback_queue_entries` (`queueId`, `baseOrder`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_queue_entries_queueId_playbackOrder` " +
                    "ON `playback_queue_entries` (`queueId`, `playbackOrder`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queue_entries_trackIdentityId` " +
                    "ON `playback_queue_entries` (`trackIdentityId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queue_entries_localTrackBindingId` " +
                    "ON `playback_queue_entries` (`localTrackBindingId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_queue_state` (
                    `id` INTEGER NOT NULL,
                    `activeQueueId` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`activeQueueId`) REFERENCES `playback_queues`(`queueId`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_queue_state_activeQueueId` " +
                    "ON `playback_queue_state` (`activeQueueId`)"
            )
        }
    }

    private fun Cursor.requiredString(columnName: String): String =
        getString(getColumnIndexOrThrow(columnName))

    private fun Cursor.requiredLong(columnName: String): Long =
        getLong(getColumnIndexOrThrow(columnName))

    private fun Cursor.requiredInt(columnName: String): Int =
        getInt(getColumnIndexOrThrow(columnName))

    private fun Cursor.putNullableLong(values: ContentValues, columnName: String) {
        val index = getColumnIndexOrThrow(columnName)
        if (isNull(index)) values.putNull(columnName) else values.put(columnName, getLong(index))
    }

    private fun ContentValues.putOptionalString(columnName: String, value: String) {
        if (value.isBlank()) putNull(columnName) else put(columnName, value)
    }

    private const val DATABASE_NAME = "sazanami_database"
}
