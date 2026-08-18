package com.example.cdplaya.data

import com.example.cdplaya.data.backup.AppBackupJson
import com.example.cdplaya.data.backup.BackupSongRatings
import com.example.cdplaya.data.local.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationContractTest {
    @Test
    fun ratingsMilestoneKeepsRoomAndBackupVersionContracts() {
        assertEquals(9, DatabaseProvider.MIGRATION_9_10.startVersion)
        assertEquals(10, DatabaseProvider.MIGRATION_9_10.endVersion)
        assertEquals(10, DatabaseProvider.MIGRATION_10_11.startVersion)
        assertEquals(11, DatabaseProvider.MIGRATION_10_11.endVersion)
        assertEquals(9, AppBackupJson.CURRENT_SCHEMA_VERSION)
        assertEquals(1, BackupSongRatings.CURRENT_FORMAT_VERSION)
    }
}
