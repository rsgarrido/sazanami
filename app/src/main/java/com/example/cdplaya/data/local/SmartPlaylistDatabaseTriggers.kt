package com.example.cdplaya.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object SmartPlaylistDatabaseTriggers {
    fun install(db: SupportSQLiteDatabase) {
        installForTable(db, "cached_songs", SmartPlaylistDependencies.LIBRARY)
        installForTable(db, "song_ratings", SmartPlaylistDependencies.RATINGS)
        listOf(
            "listening_events",
            "legacy_listening_baselines",
            "local_track_bindings",
            "listening_identity_reconciliations"
        ).forEach { table ->
            installForTable(db, table, SmartPlaylistDependencies.LISTENING)
        }
    }

    private fun installForTable(db: SupportSQLiteDatabase, table: String, mask: Int) {
        listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
            val suffix = operation.lowercase()
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `smart_playlist_dirty_${table}_$suffix`
                AFTER $operation ON `$table`
                BEGIN
                    UPDATE smart_playlist_resolution_states
                    SET isDirty = 1
                    WHERE playlistId IN (
                        SELECT playlistId
                        FROM smart_playlist_definitions
                        WHERE (dependencyMask & $mask) != 0
                    );
                END
                """.trimIndent()
            )
        }
    }
}

